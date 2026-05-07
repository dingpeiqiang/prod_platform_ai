from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional, AsyncGenerator
from sqlalchemy.orm import Session
import json
import asyncio
import logging
import time
import requests
from dataclasses import dataclass
from app.services.llm_service import llm_service, StreamStats
from app.core.config_loader import config_loader
from app.core.database import get_db
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.agent_executor import AgentExecutor
from app.services.recommendation_engine import get_recommendation_engine
from app.services.admin_service import AdminService
from app.services.history_ai_service import (
    analyze_history,
    apply_generated_data,
    list_available_data,
    get_history_summary
)
from app.harness.observability.llm_call_logger import (
    get_llm_call_logger,
    CallType
)
from app.core.errors import (
    ErrorCategory, ErrorLevel, ErrorCode,
    FrameworkError, get_error_message
)
from app.core.error_handler import error_handler, create_error
from app.mcp_tools import get_toolhub
from app.core.config import get_settings
# 意图处理器注册器 —— 触发所有 handler 装饰器注册
from app.intent import get_intent_registry
from app.intent.base import IntentContext


logger = logging.getLogger("chat_api")


def _truncate(text: str, max_len: int = 200) -> str:
    """截断文本"""
    if not text:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


def _merge_field_recommendations(
    llm_recs: Dict[str, Any],
    engine_recs: Dict[str, Any]
) -> Dict[str, Any]:
    """
    合并两路 fieldRecommendations：
    - llm_recs: LLM 意图识别输出，格式 {"field": [{"value","reason"},...], ...}
      来源 source 标记为 "llm_rule"
    - engine_recs: 推荐引擎输出，格式 {"field": {"items":[...],"strategyUsed":...}, ...}

    合并策略：LLM 推荐优先保留（source=llm_rule），引擎推荐作为补充追加
    同一 value 去重，按 value 去重取 LLM 的 reason
    """
    merged = {}
    max_per_field = config_loader.get_system_config().get("smartRecommend", {}).get("maxRecommendationsPerField", 5)

    # ── 先注入 LLM 推荐 ──
    for field_code, rec_data in llm_recs.items():
        if isinstance(rec_data, list):
            merged[field_code] = {
                "items": [
                    {**item, "source": item.get("source", "llm_rule")}
                    for item in rec_data
                    if isinstance(item, dict) and item.get("value")
                ],
                "strategyUsed": ["llm_rule_inference"],
                "_has_llm": True
            }
        elif isinstance(rec_data, dict) and "items" in rec_data:
            # 兼容已经是引擎格式的情况
            items = [
                {**item, "source": item.get("source", "llm_rule")}
                for item in (rec_data.get("items") or [])
                if isinstance(item, dict)
            ]
            merged[field_code] = {
                "items": items,
                "strategyUsed": rec_data.get("strategyUsed", ["llm_rule_inference"]),
                "_has_llm": True
            }

    # ── 再叠加推荐引擎结果（去重 + 补充） ──
    for field_code, rec_data in engine_recs.items():
        engine_items = []
        if isinstance(rec_data, dict):
            engine_items = rec_data.get("items", [])
        elif isinstance(rec_data, list):
            engine_items = rec_data

        if not engine_items:
            continue

        # 收集该字段已有 LLM 推荐的 value 集合
        existing_values = set()
        existing_items = merged.get(field_code, {}).get("items", [])
        for item in existing_items:
            v = item.get("value") if isinstance(item, dict) else str(item)
            if v:
                existing_values.add(v)

        # 追加引擎中不重复的新推荐
        new_items = []
        for item in engine_items:
            if not isinstance(item, dict):
                item = {"value": str(item), "source": "history"}
            val = item.get("value", "")
            if val and val not in existing_values:
                new_items.append(item)
                existing_values.add(val)

        if field_code in merged:
            # 已有 LLM 推荐 → 追加引擎结果
            merged[field_code]["items"].extend(new_items)
            strategies = merged[field_code].get("strategyUsed", [])
            if "engine_history" not in strategies:
                strategies.append("engine_history")
        else:
            # 该字段没有 LLM 推荐 → 直接用引擎结果
            merged[field_code] = {
                "items": engine_items[:max_per_field] if not new_items else (engine_items[:max_per_field - len(new_items)] + new_items),
                "strategyUsed": rec_data.get("strategyUsed", ["engine"]) if isinstance(rec_data, dict) else ["engine"],
                "_has_llm": False
            }

    # ── 截断每个字段到 max_per_field ──
    for field_code in list(merged.keys()):
        items = merged[field_code].get("items", [])
        if len(items) > max_per_field:
            merged[field_code]["items"] = items[:max_per_field]
        # 清理内部标记
        merged[field_code].pop("_has_llm", None)

    return merged


@dataclass
class ChatStreamStats:
    """聊天流式输出统计信息"""
    total_elapsed: float = 0.0
    intent_elapsed: float = 0.0
    llm_elapsed: float = 0.0
    llm_tokens: int = 0
    llm_chars: int = 0
    llm_tps: float = 0.0
    is_form: bool = False
    error: Optional[str] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "totalElapsed": round(self.total_elapsed, 3),
            "intentElapsed": round(self.intent_elapsed, 3),
            "llmElapsed": round(self.llm_elapsed, 3),
            "llmTokens": self.llm_tokens,
            "llmChars": self.llm_chars,
            "llmTps": round(self.llm_tps, 2),
            "isForm": self.is_form,
            "error": self.error
        }

router = APIRouter(prefix="/api/v1", tags=["chat"])


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    userId: Optional[str] = None


class ChatResponse(BaseModel):
    success: bool
    reply: Optional[str] = None
    intentType: Optional[str] = None
    formCode: Optional[str] = None
    extractedFields: Optional[Dict[str, Any]] = None
    confidence: Optional[float] = None
    reasoning: Optional[str] = None
    message: Optional[str] = None
    method: Optional[str] = None  # 新增：使用的方法 llm/skills/fallback


FALLBACK_RESPONSES = {
    '你好': '你好！我是AI智能助手。我可以帮你填写各种表单（销售订单、请假申请、费用报销等），也可以和你聊天。有什么我可以帮你的吗？',
    '你能做什么': '我可以帮你：\n1. 生成和填写表单（销售订单、请假申请、费用报销等）\n2. 回答你的问题\n3. 和你聊天\n\n你可以直接告诉我需要什么帮助，比如："帮我填一个请假申请"',
    '帮助': '使用指南：\n1. 告诉我需要什么表单，如"帮我填一个销售订单"\n2. 我会自动生成表单\n3. 填写后点击提交\n\n快捷操作可以点击下方按钮！',
    '默认': '我是AI智能助手！我可以帮你填写各种表单。你可以告诉我需要填写什么，比如：\n- "帮我填一个销售订单"\n- "帮我填一个请假申请"\n- "帮我填一个费用报销"'
}


def _strip_json_comments(text: str) -> str:
    result = []
    for line in text.split('\n'):
        stripped = line.strip()
        if stripped.startswith('//'):
            continue
        if stripped.startswith('```'):
            continue
        result.append(line)
    text = '\n'.join(result)
    if text.startswith("```json"):
        text = text[7:]
    if text.startswith("```"):
        text = text[3:]
    if text.endswith("```"):
        text = text[:-3]
    return text.strip()


def _fix_json_newlines(json_str: str) -> str:
    """修复 JSON 字符串值中的裸换行符（MiniMax 等模型常见问题）。
    模型在 JSON 字段值中写入真实换行而非 \\n 转义，导致 json.loads 失败。
    此函数将字符串值内的裸换行替换为 \\n 转义序列。"""
    import re
    result = []
    in_string = False
    escape_next = False
    for ch in json_str:
        if escape_next:
            result.append(ch)
            escape_next = False
            continue
        if ch == '\\':
            result.append(ch)
            escape_next = True
            continue
        if ch == '"' and not escape_next:
            in_string = not in_string
            result.append(ch)
            continue
        if in_string and ch in '\n\r':
            # 字符串值内的裸换行 → \\n
            result.append('\\n')
        else:
            result.append(ch)
    return ''.join(result)


def _build_ontologies_info() -> str:
    ontologies = config_loader.get_all_ontologies()
    info_lines = []
    for form_code, ontology in ontologies.items():
        form_name = ontology.get('formName', form_code)
        description = ontology.get('description', '')
        
        info_lines.append(f"### {form_code} ({form_name})")
        if description:
            info_lines.append(f"描述：{description}")
        
        entities = ontology.get('entities', [])
        for entity in entities:
            entity_name = entity.get('entityName', '')
            fields = entity.get('fields', [])
            if fields:
                field_list = []
                for field in fields:
                    field_info = f"{field.get('fieldName')} ({field.get('fieldCode')})"
                    field_type = field.get('fieldType', '')
                    if field_type:
                        field_info += f" - {field_type}"
                    if field.get('required'):
                        field_info += " [必填]"
                    field_list.append(field_info)
                
                if entity_name:
                    info_lines.append(f"{entity_name}字段：")
                info_lines.extend([f"  - {f}" for f in field_list])
        
        info_lines.append("")
    
    return "\n".join(info_lines)


def _build_scene_keywords() -> str:
    scene_mappings = config_loader.get_scene_mappings()
    keyword_lines = []
    for mapping in scene_mappings:
        scene_code = mapping.get('sceneCode', '')
        keywords = mapping.get('keywords', [])
        if keywords:
            keyword_lines.append(f"{scene_code}: {', '.join(keywords)}")
    return "\n".join(keyword_lines)


def _build_separators() -> str:
    fe_config = config_loader.get_field_extraction_config()
    separators = fe_config.get('separators', ['是', '为', '：', ':', ' '])
    return ", ".join([f"'{s}'" for s in separators])


def _call_skills_only(last_user_message: str, ontologies: Dict) -> ChatResponse:
    form_keywords = ['订单', '请假', '报销', '合同', '项目', '表单', '填写', '生成', '校验', 'API', '演示']
    needs_form = any(keyword in last_user_message for keyword in form_keywords)
    
    if needs_form:
        scene_result = SceneRecognitionSkill.recognize(last_user_message)
        form_code = scene_result["sceneCode"]
        
        if form_code and form_code in ontologies:
            temp_schema = {
                "formCode": form_code,
                "formName": ontologies[form_code].get("formName", ""),
                "fields": []
            }
            extraction_result = FieldExtractionSkill.extract(last_user_message, form_code, temp_schema)
            
            extracted_fields = {}
            if extraction_result["success"]:
                for field in extraction_result["fields"]:
                    extracted_fields[field.get("fieldCode")] = field.get("defaultValue")
            
            return ChatResponse(
                success=True,
                intentType="form",
                formCode=form_code,
                extractedFields=extracted_fields,
                confidence=0.7,
                method="skills"
            )
    
    reply = FALLBACK_RESPONSES['默认']
    for key, value in FALLBACK_RESPONSES.items():
        if key != '默认' and key in last_user_message:
            reply = value
            break
    
    return ChatResponse(
        success=True,
        intentType="chat",
        reply=reply,
        method="skills"
    )


@router.post("/chat/agent/stream")
async def chat_agent_stream(request: ChatRequest):
    """
    流式 Agent 接口：实时展示思考和处理过程
    """
    last_user_message = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            last_user_message = msg.content
            break

    logger.info("[chat/agent/stream] 收到请求 msg=%s", _truncate(last_user_message, 100))

    async def event_generator():
        async for event in AgentExecutor.execute_stream(last_user_message):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"}
    )


@router.post("/chat/agent", response_model=ChatResponse)
async def chat_with_agent(request: ChatRequest):
    """
    LLM 驱动的智能体接口：
    LLM 自主决定调用哪个 Skill 来处理用户请求
    """
    last_user_message = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            last_user_message = msg.content
            break
    
    logger.info("\n" + "*"*60)
    logger.info("[API REQUEST] /chat/agent - 用户消息: %s", last_user_message)
    
    try:
        # 使用 AgentExecutor 让 LLM 决定如何行动
        agent_result = AgentExecutor.execute(last_user_message)
        
        if agent_result.get("success"):
            result_data = agent_result.get("result", {})
            response_obj = ChatResponse(
                success=True,
                intentType="form" if result_data.get("sceneCode") else "chat",
                formCode=result_data.get("sceneCode"),
                confidence=result_data.get("confidence"),
                reasoning=agent_result.get("reasoning"),
                method=f"agent_{result_data.get('method', 'unknown')}"
            )
            logger.info("[API RESPONSE] 成功 - 意图: %s, 表单: %s, 方法: %s", 
                        response_obj.intentType, response_obj.formCode, response_obj.method)
            logger.info("*"*60 + "\n")
            return response_obj
        else:
            logger.warning("[API RESPONSE] 失败 - 原因: %s", agent_result.get("error"))
            return ChatResponse(
                success=False,
                message=agent_result.get("error", "Agent 执行失败"),
                method="agent_error"
            )
            
    except Exception as e:
        logger.exception("[API ERROR] Agent 处理异常: %s", e)
        return ChatResponse(success=False, message=str(e))


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        ontologies = config_loader.get_all_ontologies()
        ontologies_info = _build_ontologies_info()
        scene_keywords = _build_scene_keywords()
        separators = _build_separators()
        
        messages_text = "\n".join([
            f"{msg.role}: {msg.content}"
            for msg in request.messages
        ])
        
        last_user_message = ""
        for msg in reversed(request.messages):
            if msg.role == "user":
                last_user_message = msg.content
                break
        
        # 检查是否需要降级处理
        fallback_enabled = llm_service.fallback_to_rules
        
        if not llm_service.enabled:
            if fallback_enabled:
                logger.info("LLM service is disabled, using skills result")
                skills_result = _call_skills_only(last_user_message, ontologies)
                return skills_result
            else:
                logger.error("LLM service is disabled and fallbackToRules is false")
                return ChatResponse(
                    success=False,
                    message="LLM 服务已禁用，且未启用降级处理",
                    method="llm_disabled_no_fallback"
                )
        
        logger.info("Attempting LLM call for intent recognition (fallbackToRules: %s)", fallback_enabled)
        try:
            intent_prompt_template = config_loader.get_prompt('smart_intent_recognition')
            logger.info("Intent prompt template loaded: %s", intent_prompt_template is not None)
            if intent_prompt_template:
                # 用 str.replace() 替代 .format()，避免 {param} 与工具返回的字段名冲突
                mcp_info_raw = get_toolhub().get_tool_schemas_for_llm()
                intent_prompt = (
                    intent_prompt_template
                    .replace("{ontologies_info}", ontologies_info)
                    .replace("{scene_keywords}", scene_keywords)
                    .replace("{separators}", separators)
                    .replace("{mcp_tools_info}", mcp_info_raw or "")
                    .replace("{messages_text}", messages_text or "[]")
                    .replace("{last_user_message}", last_user_message or "")
                )
                
                logger.info("Calling LLM for intent recognition")
                intent_result = llm_service._call_llm_sync(intent_prompt)
                logger.info("LLM intent result received: %s", intent_result is not None)
                
                if intent_result:
                    try:
                        import json
                        cleaned = _strip_json_comments(intent_result.strip())
                        intent_data = json.loads(cleaned)
                        
                        intent_type = intent_data.get("intentType", "chat")
                        
                        if intent_type == "form":
                            return ChatResponse(
                                success=True,
                                intentType="form",
                                formCode=intent_data.get("formCode"),
                                extractedFields=intent_data.get("extractedFields", {}),
                                confidence=intent_data.get("confidence"),
                                reasoning=intent_data.get("reasoning"),
                                method="llm"
                            )
                        
                        chat_prompt_template = config_loader.get_prompt('smart_chat_response')
                        if chat_prompt_template:
                            chat_prompt = chat_prompt_template.format(
                                ontologies_info=ontologies_info,
                                messages_text=messages_text
                            )
                            
                            chat_reply = llm_service._call_llm_sync(chat_prompt)
                            
                            if chat_reply:
                                return ChatResponse(
                                    success=True,
                                    intentType="chat",
                                    reply=chat_reply.strip(),
                                    method="llm"
                                )
                    except Exception as e:
                        logger.error("LLM intent parsing failed: %s", e)
                        if not fallback_enabled:
                            return ChatResponse(
                                success=False,
                                message=f"LLM 意图解析失败: {str(e)}",
                                method="llm_parse_error_no_fallback"
                            )
                        # 如果启用了降级，才继续
        except Exception as e:
            logger.error("LLM call failed: %s", e)
            if not fallback_enabled:
                return ChatResponse(
                    success=False,
                    message=f"LLM 调用失败: {str(e)}",
                    method="llm_call_error_no_fallback"
                )
        
        # 只有在 fallbackToRules 为 true 时才使用降级
        if fallback_enabled:
            skills_result = _call_skills_only(last_user_message, ontologies)
            skills_result.method = "skills_fallback"
            return skills_result
        else:
            logger.error("LLM processing failed and fallbackToRules is false")
            return ChatResponse(
                success=False,
                message="LLM 处理失败且未启用降级处理",
                method="llm_failed_no_fallback"
            )
        
    except Exception as e:
        logger.error("Chat error: %s", e)
        if not llm_service.fallback_to_rules:
            return ChatResponse(
                success=False,
                message=f"处理请求时发生错误: {str(e)}",
                method="unknown_error_no_fallback"
            )
        return await _fallback_chat(last_user_message, ontologies)


async def _fallback_chat(last_user_message: str, ontologies: Dict):
    result = _call_skills_only(last_user_message, ontologies)
    result.method = "fallback"
    return result






def _sse(data: dict) -> str:
    """格式化 SSE 帧"""
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def _thinking(content: str, result: Any = None, assistant_message_id: str = None) -> str:
    """系统步骤日志（type=thinking），支持结构化结果详情"""
    import uuid
    data = {
        "type": "thinking", 
        "content": content,
        "message_id": str(uuid.uuid4()),  # 生成消息ID，排序值由后端保存时计算
        "assistant_message_id": assistant_message_id  # AI回复消息的ID，用于关联
    }
    if result is not None:
        data["result"] = result
    return _sse(data)


def _reasoning(content: str) -> str:
    """大模型推理过程（type=reasoning），与系统步骤区分"""
    return _sse({"type": "reasoning", "content": content})


async def _stream_chat_reply(
    chat_prompt: str,
    ontologies_info: str,
    messages_text: str
) -> AsyncGenerator[tuple[str, Optional[StreamStats]], None]:
    """
    用真正的 LLM 流式输出聊天回复。
    若 LLM 不可用则降级为 fallback 文本。
    
    Yields:
        (SSE帧, 统计信息) - 统计信息仅在结束时提供
    """
    import uuid
    # 预先生成 AI 回复消息的 ID，用于关联 thinking 消息
    assistant_message_id = str(uuid.uuid4())
    
    chat_prompt_template = config_loader.get_prompt('smart_chat_response')
    if not chat_prompt_template:
        yield _sse({"type": "text_start", "message_id": assistant_message_id}), None
        yield _sse({"type": "text", "content": "好的，请问有什么可以帮助你？"}), None
        yield _sse({"type": "text_end"}), None
        return

    prompt = chat_prompt_template.format(
        ontologies_info=ontologies_info,
        messages_text=messages_text
    )

    yield _thinking("🤖 正在生成回复...", None, assistant_message_id), None
    logger.info("[stream_chat] 开始流式调用，prompt长度=%d", len(prompt))

    # text_start 延迟到首个正文出现时再发，让前端先展示 thinking 折叠区
    final_stats: Optional[StreamStats] = None
    # 用于拼接跨 chunk 的 thinking 标记（静默丢弃，不发送到前端）
    _thinking_buf: str = ""
    _in_thinking: bool = False
    _text_started: bool = False   # text_start 是否已发送
    
    async for text, stats in llm_service.call_llm_stream_with_stats(prompt):
        if stats is not None:
            final_stats = stats
            continue
            
        if not text:
            continue

        # ── 处理 [THINKING]...[/THINKING] 标记 ──────────────────────────
        # thinking 内容是模型内部思考过程，对终端用户无意义，静默丢弃
        remaining = text
        while remaining:
            if _in_thinking:
                end_idx = remaining.find("[/THINKING]")
                if end_idx == -1:
                    # 还在 thinking 区，全部追加到 buf（丢弃）
                    _thinking_buf += remaining
                    remaining = ""
                else:
                    # 找到结束标记，静默丢弃整个 thinking 块
                    _thinking_buf += remaining[:end_idx]
                    logger.debug("[stream_chat] 丢弃 thinking 块 (%d 字符): %s", len(_thinking_buf), _thinking_buf[:100])
                    _thinking_buf = ""
                    _in_thinking = False
                    remaining = remaining[end_idx + len("[/THINKING]"):]
            else:
                start_idx = remaining.find("[THINKING]")
                if start_idx == -1:
                    # 纯正文：确保先发 text_start
                    if remaining.strip():
                        if not _text_started:
                            _text_started = True
                            yield _sse({"type": "text_start", "message_id": assistant_message_id}), None
                        yield _sse({"type": "text", "content": remaining}), None
                        await asyncio.sleep(0)
                    remaining = ""
                else:
                    # start_idx 之前是正文
                    before = remaining[:start_idx]
                    if before.strip():
                        if not _text_started:
                            _text_started = True
                            yield _sse({"type": "text_start", "message_id": assistant_message_id}), None
                        yield _sse({"type": "text", "content": before}), None
                        await asyncio.sleep(0)
                    _in_thinking = True
                    remaining = remaining[start_idx + len("[THINKING]"):]

    # ── 循环结束后的收尾 ──────────────────────────────────────────────────
    # 未闭合的 thinking 块静默丢弃（模型可能未输出结束标记）
    if _in_thinking and _thinking_buf.strip():
        logger.debug("[stream_chat] 丢弃未闭合的 thinking 块 (%d 字符)", len(_thinking_buf))

    # 如果模型只输出了 thinking 没有正文，补发 text_start/text_end 让前端正常结束
    if not _text_started:
        yield _sse({"type": "text_start", "message_id": assistant_message_id}), None

    if final_stats:
        logger.info("[stream_chat] 流式完成: tokens=%d chars=%d thinking_chars=%d chunks=%d elapsed=%.2fs tps=%.1f", 
                    final_stats.token_count, final_stats.char_count,
                    final_stats.thinking_chars,
                    final_stats.chunk_count, final_stats.elapsed, 
                    final_stats.tokens_per_second)
    else:
        logger.info("[stream_chat] 流式完成")
    
    yield _sse({"type": "text_end"}), final_stats


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest, db: Session = Depends(get_db)):
    async def stream_generator():
        start_time = time.time()
        stream_stats = ChatStreamStats()
        fallback_enabled = llm_service.fallback_to_rules
        
        logger.info("=" * 60)
        logger.info("[chat/stream] 收到流式聊天请求")
        logger.info(f"[chat/stream] 时间戳: {start_time:.3f}")
        logger.info(f"[chat/stream] 消息数量: {len(request.messages)}")
        logger.info(f"[chat/stream] fallbackToRules: {fallback_enabled}")
        
        try:
            ontologies = config_loader.get_all_ontologies()
            logger.info(f"[chat/stream] 加载本体数量: {len(ontologies)}")
            
            last_user_message = ""
            for msg in reversed(request.messages):
                if msg.role == "user":
                    last_user_message = msg.content
                    break
            
            logger.info(f"[chat/stream] 最后一条用户消息: {_truncate(last_user_message, 200)}")

            messages_text = "\n".join([
                f"{msg.role}: {msg.content}"
                for msg in request.messages
            ])

            ontologies_info = _build_ontologies_info()
            scene_keywords = _build_scene_keywords()
            separators = _build_separators()

            logger.info(f"[chat/stream] 场景关键词: {scene_keywords}")
            
            # ── Step 1：识别用户意图 ─────────────────────────────────────────
            yield _thinking("🔍 正在分析用户意图...", result={
                "messagesCount": len(request.messages),
                "lastUserMessage": last_user_message[:100] if last_user_message else ""
            })

            if not llm_service.enabled:
                if fallback_enabled:
                    yield _thinking("⚙️ LLM 不可用，切换到 Skills 模式处理")
                else:
                    yield _thinking("❌ LLM 服务已禁用，且未启用降级处理")
                    stream_stats.total_elapsed = time.time() - start_time
                    error = create_error(
                        category=ErrorCategory.LLM.value,
                        code=ErrorCode.LLM_DISABLED,
                        message="LLM 服务已禁用，且未启用降级处理",
                        level=ErrorLevel.CRITICAL.value,
                        recoverable=False,
                        recovery_hint="请在配置中启用 LLM 服务或启用 fallback 降级处理"
                    )
                    error_handler.emit(error)
                    stream_stats.error = error.message
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield _sse(error.to_sse())
                    return

            intent_prompt_template = config_loader.get_prompt('smart_intent_recognition')
            if intent_prompt_template:
                mcp_info_raw = get_toolhub().get_tool_schemas_for_llm()
                intent_prompt = (
                    intent_prompt_template
                    .replace("{ontologies_info}", ontologies_info)
                    .replace("{scene_keywords}", scene_keywords)
                    .replace("{separators}", separators)
                    .replace("{mcp_tools_info}", mcp_info_raw or "")
                    .replace("{messages_text}", messages_text or "[]")
                    .replace("{last_user_message}", last_user_message or "")
                )

                loop = asyncio.get_event_loop()
                _t0 = time.time()
                _retry_count = 0

                yield _thinking("🧠 调用 LLM 进行意图识别...", result={
                    "model": llm_service.llm_config.get("model"),
                    "provider": llm_service.llm_config.get("provider"),
                    "temperature": llm_service.llm_config.get("temperature"),
                    "maxTokens": llm_service.llm_config.get("maxTokens"),
                    "promptLength": len(intent_prompt)
                })

                _llm_error = None
                try:
                    intent_result, intent_reasoning = await loop.run_in_executor(
                        None, llm_service._call_llm_sync_with_reasoning, intent_prompt
                    )

                    # 重试逻辑
                    if not intent_result and intent_reasoning:
                        logger.info("[chat/stream] 🔄 content 为空但 reasoning 有内容，用简化 prompt 重试一次")
                        _retry_count = 1
                        yield _thinking("🔄 模型响应格式异常，正在重试...", result={
                            "retry": True,
                            "originalElapsed": round(time.time() - _t0, 2)
                        })
                        retry_prompt = (
                            intent_prompt
                            + "\n\n---\n**重要提醒**：请直接输出 JSON，不要在 JSON 之外输出任何分析文本。"
                            "你的回答必须是一个合法的 JSON 对象，以 { 开头，以 } 结尾。"
                        )
                        intent_result, _ = await loop.run_in_executor(
                            None, llm_service._call_llm_sync_with_reasoning, retry_prompt
                        )
                        if intent_result:
                            logger.info("[chat/stream] ✅ 重试成功，获得 JSON 响应 (%d chars)", len(intent_result))
                        else:
                            logger.info("[chat/stream] ❌ 重试仍然失败，将走降级流程")

                    intent_elapsed = time.time() - _t0
                    stream_stats.intent_elapsed = intent_elapsed

                    if intent_result:
                        try:
                            cleaned = _strip_json_comments(intent_result.strip())
                            cleaned = _fix_json_newlines(cleaned)
                            intent_data = json.loads(cleaned)
                            intent_type = intent_data.get("intentType", "chat")

                            # 将模型的推理过程传递给前端（仅表单类意图）
                            if intent_reasoning and intent_type in ('form', 'form_update', 'configure'):
                                yield _reasoning(intent_reasoning)

                            # ── Step 2：MCP 工具执行 ────────────────────────────────────
                            tool_calls = intent_data.get("tool_calls", [])
                            tool_results = []
                            extracted = intent_data.get("extractedFields", {})

                            if tool_calls:
                                hub = get_toolhub()
                                for tc in tool_calls:
                                    tool_name = tc.get("name")
                                    tool_args = tc.get("arguments", {})
                                    if tool_name and hub.has_tool(tool_name):
                                        exec_result = hub.execute_sync(tool_name, tool_args)
                                        if exec_result.get("success"):
                                            tool_result = exec_result.get("result", {})
                                            if not isinstance(tool_result, dict) or not tool_result:
                                                tool_result = {k: v for k, v in exec_result.items() if k != "success"}
                                            if isinstance(tool_result, dict):
                                                extracted.update(tool_result)
                                            tool_results.append({"name": tool_name, "success": True, "fields": list(tool_result.keys()) if isinstance(tool_result, dict) else []})
                                        else:
                                            err = exec_result.get("error", "未知错误")
                                            tool_results.append({"name": tool_name, "success": False, "error": str(err)})
                                            error = create_error(
                                                category=ErrorCategory.TOOL.value,
                                                code=ErrorCode.TOOL_EXEC_FAILED,
                                                message=f"工具 {tool_name} 执行失败: {err}",
                                                level=ErrorLevel.WARNING.value,
                                                recoverable=True,
                                                recovery_hint="该工具调用失败不影响其他功能，已跳过",
                                                tool_name=tool_name,
                                                tool_args=tool_args
                                            )
                                            error_handler.emit(error)
                                            yield _sse({
                                                "type": "tool_error",
                                                "tool": tool_name,
                                                "error": str(err),
                                                "error_code": ErrorCode.TOOL_EXEC_FAILED,
                                                "recoverable": True
                                            })
                                    else:
                                        tool_results.append({"name": tool_name, "success": False, "error": "工具不存在"})
                                        error = create_error(
                                            category=ErrorCategory.TOOL.value,
                                            code=ErrorCode.TOOL_NOT_FOUND,
                                            message=f"工具 '{tool_name}' 不存在",
                                            level=ErrorLevel.WARNING.value,
                                            recoverable=True,
                                            recovery_hint="请检查工具名称是否正确",
                                            tool_name=tool_name
                                        )
                                        error_handler.emit(error)
                                        yield _sse({
                                            "type": "tool_error",
                                            "tool": tool_name,
                                            "error": f"工具 '{tool_name}' 不存在",
                                            "error_code": ErrorCode.TOOL_NOT_FOUND,
                                            "recoverable": True
                                        })
                                intent_data["extractedFields"] = extracted

                            yield _thinking(
                                f"🔧 已执行 {len(tool_calls)} 个工具，" + (f"成功 {sum(1 for r in tool_results if r['success'])} 个" if tool_calls else "无工具调用"),
                                result={
                                    "tools": tool_results,
                                    "totalTools": len(tool_calls),
                                    "successCount": sum(1 for r in tool_results if r["success"]),
                                    "failedCount": sum(1 for r in tool_results if not r["success"]),
                                    "extractedFields": list(extracted.keys())
                                }
                            )

                            # ── Step 3：意图识别完成，解析结果 ──────────────────────────
                            form_code = intent_data.get("detectedFormCode") or intent_data.get("formCode") or intent_data.get("form_code")
                            yield _thinking(
                                f"✅ 意图识别完成: {intent_type}" + (f" ({intent_elapsed:.2f}s)" if intent_elapsed else ""),
                                result={
                                    "intentType": intent_type,
                                    "formCode": form_code,
                                    "extractedFields": list(intent_data.get("extractedFields", {}).keys()),
                                    "extractedCount": len(extracted),
                                    "confidence": intent_data.get("confidence"),
                                    "elapsed": round(intent_elapsed, 2),
                                    "retryCount": _retry_count
                                }
                            )

                            # ── Phase 3：意图分发 ─────────────────────────────────────────────
                            _FORM_CODE_TO_INTENT = {
                                "tariff_filing_publicity": "tariff_filing",
                            }
                            if intent_type == "form" and form_code in _FORM_CODE_TO_INTENT:
                                intent_type = _FORM_CODE_TO_INTENT[form_code]
                                logger.info(f"[路由] 表单 %s 使用专属 handler: %s", form_code, intent_type)
                            ctx = IntentContext(
                                intent_data=intent_data,
                                intent_result=intent_result,
                                intent_type=intent_type,
                                confidence=intent_data.get("confidence", 0),
                                ontologies=ontologies,
                                ontologies_info=ontologies_info,
                                scene_keywords=scene_keywords,
                                request=request,
                                db=db,
                                last_user_message=last_user_message,
                                messages_text=messages_text,
                                intent_prompt=intent_prompt,
                                start_time=start_time,
                                stream_stats=stream_stats
                            )
                            async for chunk in get_intent_registry().dispatch(intent_type, ctx):
                                yield chunk
                            return

                        except json.JSONDecodeError as e:
                            logger.warning("意图识别 JSON 解析失败: %s  raw=%s", e,
                                           intent_result[:200] if intent_result else "")
                            _llm_error = f"JSON 解析失败: {str(e)}"
                            yield _thinking(f"❌ JSON 解析失败: {str(e)}", result={
                                "error": str(e),
                                "elapsed": round(time.time() - _t0, 2) if '_t0' in dir() else 0
                            })
                            if not fallback_enabled:
                                stream_stats.total_elapsed = time.time() - start_time
                                error = create_error(
                                    category=ErrorCategory.LLM.value,
                                    code=ErrorCode.LLM_PARSE_ERROR,
                                    message=f"LLM 意图解析失败: {str(e)}",
                                    level=ErrorLevel.ERROR.value,
                                    recoverable=False,
                                    recovery_hint="请稍后重试，或联系管理员检查 AI 服务配置",
                                    raw_response=intent_result[:200] if intent_result else ""
                                )
                                error_handler.emit(error)
                                stream_stats.error = error.message
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield _sse(error.to_sse())
                                return
                        except Exception as e:
                            logger.exception("意图处理异常: %s", e)
                            _llm_error = str(e)
                            yield _thinking(f"❌ 意图处理异常: {str(e)}", result={
                                "error": str(e)
                            })
                            if not fallback_enabled:
                                stream_stats.total_elapsed = time.time() - start_time
                                error = create_error(
                                    category=ErrorCategory.INTENT.value,
                                    code=ErrorCode.INTENT_PARSE,
                                    message=f"意图处理异常: {str(e)}",
                                    level=ErrorLevel.ERROR.value,
                                    recoverable=False,
                                    recovery_hint="请稍后重试"
                                )
                                error_handler.emit(error)
                                stream_stats.error = error.message
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield _sse(error.to_sse())
                                return
                    else:
                        logger.warning("[chat/stream] LLM 返回为空，耗时 %.1fs", intent_elapsed)
                        _llm_error = f"LLM 返回为空（耗时 {intent_elapsed:.1f}s）"
                        yield _thinking(f"❌ LLM 返回为空（耗时 {intent_elapsed:.1f}s）", result={
                            "error": "LLM 返回为空",
                            "elapsed": round(intent_elapsed, 1) if intent_elapsed else 0
                        })
                        if not fallback_enabled:
                            stream_stats.total_elapsed = time.time() - start_time
                            error = create_error(
                                category=ErrorCategory.LLM.value,
                                code=ErrorCode.LLM_EMPTY_RESPONSE,
                                message=f"LLM 返回为空（耗时 {intent_elapsed:.1f}s），且未启用降级处理",
                                level=ErrorLevel.ERROR.value,
                                recoverable=False,
                                recovery_hint="请稍后重试，或联系管理员检查 AI 服务"
                            )
                            error_handler.emit(error)
                            stream_stats.error = error.message
                            yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                            yield _sse(error.to_sse())
                            return
                except Exception as e:
                    logger.exception("LLM 调用异常: %s", e)
                    _llm_error = str(e)
                    yield _thinking(f"❌ LLM 调用失败: {str(e)}", result={
                        "error": str(e)
                    })
                    if not fallback_enabled:
                        stream_stats.total_elapsed = time.time() - start_time
                        error = create_error(
                            category=ErrorCategory.LLM.value,
                            code=ErrorCode.LLM_TIMEOUT,
                            message=f"LLM 调用失败: {str(e)}",
                            level=ErrorLevel.ERROR.value,
                            recoverable=False,
                            recovery_hint="请稍后重试，或联系管理员检查 AI 服务"
                        )
                        error_handler.emit(error)
                        stream_stats.error = error.message
                        yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                        yield _sse(error.to_sse())
                        return

            # ── Fallback（Skills 模式）步骤 ──
            if fallback_enabled:
                form_keywords = ['订单', '请假', '报销', '合同', '项目', '表单', '填写', '生成']
                matched_keywords = [kw for kw in form_keywords if kw in last_user_message]

                yield _thinking("⚙️ Skills 模式处理（LLM 不可用）", result={
                    "mode": "skills",
                    "matchedKeywords": matched_keywords,
                    "error": _llm_error or "LLM 处理失败"
                })

                skills_result = _call_skills_only(last_user_message, ontologies)

                if skills_result.intentType == "form":
                    form_code = skills_result.formCode
                    form_name = ""
                    if form_code and form_code in ontologies:
                        form_name = ontologies[form_code].get("formName", form_code)

                    # Phase 2：表单识别结果
                    extracted = skills_result.extractedFields or {}
                    yield _thinking(f"📋 识别到表单: {form_name or form_code}", result={
                        "formCode": form_code,
                        "formName": form_name,
                        "extractedFields": list(extracted.keys()),
                        "confidence": skills_result.confidence or 0.7
                    })

                    # ── 获取字段推荐 ─────────────────────────────────────
                    field_recommendations = {}
                    try:
                        rec_engine = get_recommendation_engine()
                        ontology_def = ontologies.get(form_code, {})
                        all_field_codes = [
                            f.get("fieldCode")
                            for entity in ontology_def.get("entities", [])
                            for f in entity.get("fields", [])
                        ]
                        rec_result = rec_engine.batch_recommend(
                            form_code=form_code,
                            extracted_fields=extracted,
                            user_input=last_user_message,
                            user_id=None,
                            conversation_context={"messages": [], "extractedFields": extracted, "lastUserMessage": last_user_message},
                            max_per_field=5,
                            db=db,
                            field_codes=all_field_codes if all_field_codes else None
                        )
                        for fc, rec in rec_result.items():
                            if rec.success and rec.recommendations:
                                field_recommendations[fc] = {
                                    "items": [r.to_dict() for r in rec.recommendations],
                                    "strategyUsed": rec.strategy_used,
                                    "totalCandidates": rec.total_candidates,
                                    "processingTimeMs": round(rec.processing_time_ms, 2)
                                }
                        logger.info(f"[chat/stream] 推荐生成完成 fieldCount={len(field_recommendations)}")
                    except Exception as rec_err:
                        logger.warning(f"[chat/stream] 推荐生成失败: {rec_err}")

                    intent_data = {
                        "formCode": form_code,
                        "formName": form_name,
                        "extractedFields": extracted,
                        "confidence": skills_result.confidence or 0.7,
                        "fieldRecommendations": field_recommendations
                    }
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = True
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield _sse({"type": "result",
                                "content": json.dumps(skills_result.dict(), ensure_ascii=False)})
                    yield _sse({"type": "done", "isForm": True, "intentData": intent_data})
                else:
                    yield _thinking("💬 生成聊天回复...", result={
                        "intentType": "chat",
                        "mode": "skills"
                    })
                    reply_text = skills_result.reply or ""
                    yield _sse({"type": "text_start"})
                    chunk_size = 5
                    for i in range(0, len(reply_text), chunk_size):
                        chunk = reply_text[i:i + chunk_size]
                        yield _sse({"type": "text", "content": chunk})
                        await asyncio.sleep(0)
                    yield _sse({"type": "text_end"})
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = False
                    stream_stats.llm_chars = len(reply_text)
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield _sse({"type": "done", "isForm": False})
            else:
                yield _thinking("❌ LLM 处理失败且未启用降级处理", result={
                    "error": _llm_error or "未知错误"
                })
                stream_stats.total_elapsed = time.time() - start_time
                error = create_error(
                    category=ErrorCategory.LLM.value,
                    code=ErrorCode.LLM_UNAVAILABLE,
                    message="LLM 处理流程失败，且未启用降级处理",
                    level=ErrorLevel.ERROR.value,
                    recoverable=False,
                    recovery_hint="请稍后重试，或联系管理员检查 AI 服务"
                )
                error_handler.emit(error)
                stream_stats.error = error.message
                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                yield _sse(error.to_sse())

        except Exception as e:
            logger.exception("Stream error: %s", e)
            # 使用框架错误处理
            error = create_error(
                category=ErrorCategory.SYSTEM.value,
                code="ERR_STREAM_ERROR",
                message=f"Stream 处理异常: {str(e)}",
                level=ErrorLevel.ERROR.value,
                recoverable=False,
                recovery_hint="请刷新页面后重试"
            )
            error_handler.emit(error)
            stream_stats.error = error.message
            stream_stats.total_elapsed = time.time() - start_time
            yield _sse({"type": "stats", "content": stream_stats.to_dict()})
            yield _sse(error.to_sse())

    return StreamingResponse(
        stream_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )



# ── 历史数据维护 API ───────────────────────────────────────────────────────────

class HistoryAnalyzeRequest(BaseModel):
    formCode: str


@router.post("/chat/history/analyze")
async def analyze_history_endpoint(request: HistoryAnalyzeRequest, db: Session = Depends(get_db)):
    """分析指定表单的历史数据质量"""
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[history/analyze] 分析数据质量 form_code=%s", form_code)

    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None, lambda: analyze_history(form_code, db=db)
    )
    return result


class HistoryImportRequest(BaseModel):
    formCode: str


@router.post("/chat/history/import")
async def import_history_endpoint(request: HistoryImportRequest, db: Session = Depends(get_db)):
    """将 JSONL 数据导入到数据库"""
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[history/import] 导入数据 form_code=%s", form_code)

    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None, lambda: apply_generated_data(form_code, db=db)
    )
    return result


@router.get("/chat/history/list")
async def list_history_endpoint():
    """列出可导入的历史数据文件"""
    result = list_available_data()
    return {"success": True, "forms": result}


@router.get("/chat/history/{form_code}/summary")
async def history_summary_endpoint(form_code: str, db: Session = Depends(get_db)):
    """获取表单历史数据简要统计"""
    result = get_history_summary(form_code, db=db)
    return result


# ── 配置部署端点 ────────────────────────────────────────────────────────────────

class DeployConfigRequest(BaseModel):
    config: Dict[str, Any]
    keywords: List[str] = []


@router.post("/chat/deploy-config")
async def deploy_config(request: DeployConfigRequest):
    """
    一键部署新表单配置：
    1. 写入 ontology JSON 文件
    2. 更新 scene_mapping.json
    3. 热重载配置
    """
    config_data = request.config
    keywords = request.keywords

    form_code = config_data.get("formCode", "").strip()
    form_name = config_data.get("formName", "")

    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[deploy-config] 开始部署 form_code=%s form_name=%s", form_code, form_name)

    try:
        # Step 1: 创建 ontology
        ontology_result = AdminService.create_ontology(config_data)
        if not ontology_result.get("success"):
            # 如果已存在，尝试更新
            ontology_result = AdminService.update_ontology(form_code, config_data)
            if not ontology_result.get("success"):
                return {"success": False, "message": ontology_result.get("message", "写入表单配置失败")}

        logger.info("[deploy-config] ontology 写入成功 form_code=%s", form_code)

        # Step 2: 更新 scene_mapping（追加新条目）
        if keywords:
            scene_data = AdminService.get_scene_mappings()
            if scene_data.get("success"):
                mappings = scene_data["data"]

                # 检查是否已存在该 sceneCode
                existing = [m for m in mappings.get("sceneMappings", []) if m.get("sceneCode") == form_code]
                if not existing:
                    # 追加新的 scene mapping
                    new_mapping = {
                        "sceneCode": form_code,
                        "keywords": keywords,
                        "priority": 10
                    }
                    mappings.setdefault("sceneMappings", []).append(new_mapping)

                    update_result = AdminService.update_scene_mappings(mappings)
                    if not update_result.get("success"):
                        logger.warning("[deploy-config] scene_mapping 更新失败: %s", update_result.get("message"))
                    else:
                        logger.info("[deploy-config] scene_mapping 更新成功, 新增关键词: %s", keywords)
                else:
                    logger.info("[deploy-config] scene_mapping 已存在 form_code=%s, 跳过", form_code)

        # Step 3: 热重载 prompts（确保意图识别能识别新关键词）
        config_loader.reload_config("prompts")

        logger.info("[deploy-config] 部署完成 form_code=%s", form_code)

        return {
            "success": True,
            "message": f"表单 '{form_name or form_code}' 部署成功！现在可以直接使用了。",
            "formCode": form_code,
            "formName": form_name
        }

    except Exception as e:
        logger.exception("[deploy-config] 部署失败: %s", e)
        return {"success": False, "message": f"部署失败: {str(e)}"}


# ── 表单删除端点 ───────────────────────────────────────────────────────────────

class DeleteFormRequest(BaseModel):
    formCode: str


@router.post("/chat/delete-form")
async def delete_form_endpoint(request: DeleteFormRequest):
    """删除业务表单（自动备份到版本历史）"""
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[delete-form] 删除表单 form_code=%s", form_code)

    try:
        result = AdminService.delete_ontology(form_code, auto_backup=True)
        return result
    except Exception as e:
        logger.exception("[delete-form] 删除失败: %s", e)
        return {"success": False, "message": f"删除失败: {str(e)}"}


# ── 版本管理 API ──────────────────────────────────────────────────────────────

@router.get("/chat/form-versions/{form_code}")
async def list_form_versions(form_code: str):
    """获取指定表单的版本历史列表"""
    result = AdminService.list_versions(form_code.strip())
    return result


class RollbackFormRequest(BaseModel):
    formCode: str
    versionId: str


@router.post("/chat/rollback-form")
async def rollback_form_endpoint(request: RollbackFormRequest):
    """回退表单到指定版本"""
    form_code = request.formCode.strip()
    version_id = request.versionId.strip()
    
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}
    if not version_id:
        return {"success": False, "message": "versionId 不能为空"}

    logger.info("[rollback-form] 回退 form_code=%s → version_id=%s", form_code, version_id)

    try:
        result = AdminService.rollback_version(form_code, version_id)
        return result
    except Exception as e:
        logger.exception("[rollback-form] 回退失败: %s", e)
        return {"success": False, "message": f"回退失败: {str(e)}"}


async def test_llm_call():
    """
    测试LLM调用端点 - 直接测试大模型调用
    
    直接在浏览器访问: http://localhost:8000/test/llm-call
    或用curl:
    curl -X POST http://localhost:8000/test/llm-call
    """
    import uuid
    import sys
    
    # 立即打印到标准输出（绕过日志系统）
    print("\n" + "=" * 80, flush=True)
    print("🧪 [SYSOUT] 测试端点被调用！", flush=True)
    print(f"时间: {time.strftime('%H:%M:%S')}", flush=True)
    print("=" * 80, flush=True)
    
    # 记录到日志
    logger.warning("⚠️ 测试端点 /test/llm-call 被调用了!")
    
    call_id = str(uuid.uuid4())[:16]
    test_prompt = "请用一句话介绍你自己"
    
    logger.info("=" * 80)
    logger.info("🧪 测试LLM调用开始")
    logger.info("=" * 80)
    
    logger.info(f"测试 Prompt: {test_prompt}")
    logger.info(f"Call ID: {call_id}")
    logger.info(f"LLM Service enabled: {llm_service.enabled}")
    logger.info(f"LLM Service provider: {llm_service.llm_config.get('provider')}")
    logger.info(f"LLM Service model: {llm_service.llm_config.get('model')}")
    logger.info(f"LLM Service baseUrl: {llm_service.llm_config.get('baseUrl')}")
    
    result = llm_service._call_llm_sync(test_prompt)
    
    logger.info("=" * 80)
    logger.info(f"🧪 测试LLM调用完成")
    logger.info(f"Result: {result[:200] if result else 'None'}...")
    logger.info("=" * 80)
    
    # 也在标准输出打印
    print(f"🧪 [SYSOUT] LLM调用结果: {result[:200] if result else 'None'}...", flush=True)
    print("=" * 80 + "\n", flush=True)
    
    return {
        "success": result is not None,
        "call_id": call_id,
        "prompt": test_prompt,
        "result": result,
        "message": "请查看后端控制台日志"
    }

from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional, AsyncGenerator
from sqlalchemy.orm import Session
import json
import asyncio
import logging
import time
from dataclasses import dataclass
from app.services.llm_service import llm_service, StreamStats
from app.core.config_loader import config_loader
from app.core.database import get_db
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.agent_executor import AgentExecutor
from app.services.chat_history_service import ChatHistoryService
from app.services.recommendation_engine import get_recommendation_engine
from app.services.admin_service import AdminService
from app.harness.observability.llm_call_logger import (
    get_llm_call_logger,
    CallType
)


logger = logging.getLogger("chat_api")


def _truncate(text: str, max_len: int = 200) -> str:
    """截断文本"""
    if not text:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


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
                intent_prompt = intent_prompt_template.format(
                    ontologies_info=ontologies_info,
                    scene_keywords=scene_keywords,
                    separators=separators,
                    messages_text=messages_text,
                    last_user_message=last_user_message
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


def _thinking(content: str) -> str:
    """系统步骤日志（type=thinking）"""
    return _sse({"type": "thinking", "content": content})


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
    chat_prompt_template = config_loader.get_prompt('smart_chat_response')
    if not chat_prompt_template:
        yield _sse({"type": "text_start"}), None
        yield _sse({"type": "text", "content": "好的，请问有什么可以帮助你？"}), None
        yield _sse({"type": "text_end"}), None
        return

    prompt = chat_prompt_template.format(
        ontologies_info=ontologies_info,
        messages_text=messages_text
    )

    yield _thinking("🤖 LLM 正在分析思考..."), None
    logger.info("[stream_chat] 开始流式调用，prompt长度=%d", len(prompt))

    # text_start 延迟到首个正文出现时再发，让前端先展示 thinking 折叠区
    final_stats: Optional[StreamStats] = None
    # 用于拼接跨 chunk 的 thinking 标记
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
        # thinking 内容以特殊标记封装，需拆分出来走 thinking 事件通道
        remaining = text
        while remaining:
            if _in_thinking:
                end_idx = remaining.find("[/THINKING]")
                if end_idx == -1:
                    # 还在 thinking 区，全部追加到 buf
                    _thinking_buf += remaining
                    remaining = ""
                else:
                    # 找到结束标记
                    _thinking_buf += remaining[:end_idx]
                    if _thinking_buf.strip():
                        yield _thinking(_thinking_buf.strip()), None
                        logger.debug("[stream_chat] thinking 块: %s", _thinking_buf[:100])
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
                            yield _sse({"type": "text_start"}), None
                        yield _sse({"type": "text", "content": remaining}), None
                        await asyncio.sleep(0)
                    remaining = ""
                else:
                    # start_idx 之前是正文
                    before = remaining[:start_idx]
                    if before.strip():
                        if not _text_started:
                            _text_started = True
                            yield _sse({"type": "text_start"}), None
                        yield _sse({"type": "text", "content": before}), None
                        await asyncio.sleep(0)
                    _in_thinking = True
                    remaining = remaining[start_idx + len("[THINKING]"):]

    # ── 循环结束后的收尾 ──────────────────────────────────────────────────
    # 如果还有未完成的 thinking buf（缺少 [/THINKING] 结束标记），也发出去
    if _in_thinking and _thinking_buf.strip():
        yield _thinking(_thinking_buf.strip()), None
        logger.debug("[stream_chat] 未闭合的 thinking 块: %s", _thinking_buf[:100])

    # 如果模型只输出了 thinking 没有正文，补发 text_start/text_end 让前端正常结束
    if not _text_started:
        yield _sse({"type": "text_start"}), None

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
            
            # ── 第一步：意图识别 ──────────────────────────────────────────
            yield _thinking("🔍 正在分析用户意图...")
            
            if not llm_service.enabled:
                if fallback_enabled:
                    yield _thinking("⚙️ LLM 不可用，切换到 Skills 模式处理")
                else:
                    yield _thinking("❌ LLM 服务已禁用，且未启用降级处理")
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.error = "LLM 服务已禁用，且未启用降级处理"
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield _sse({"type": "error", "content": "LLM 服务已禁用，且未启用降级处理"})
                    return

            intent_prompt_template = config_loader.get_prompt('smart_intent_recognition')
            if intent_prompt_template:
                intent_prompt = intent_prompt_template.format(
                    ontologies_info=ontologies_info,
                    scene_keywords=scene_keywords,
                    separators=separators,
                    messages_text=messages_text,
                    last_user_message=last_user_message
                )

                yield _thinking("🧠 调用 LLM 进行意图识别...")

                # 意图识别需要结构化 JSON，使用同步（非流式）调用
                loop = asyncio.get_event_loop()
                _t0 = time.time()
                
                # 详细的LLM调用日志
                logger.info("┌" + "─" * 78)
                logger.info("│ 🎯 意图识别 - LLM调用")
                logger.info(f"│    Provider: {llm_service.llm_config.get('provider')}")
                logger.info(f"│    Model: {llm_service.llm_config.get('model')}")
                logger.info(f"│    Temperature: {llm_service.llm_config.get('temperature')}")
                logger.info(f"│    Max Tokens: {llm_service.llm_config.get('maxTokens')}")
                logger.info(f"│    Prompt长度: {len(intent_prompt)} 字符")
                logger.info(f"│    Prompt预览: {intent_prompt[:200]}...")
                logger.info("└" + "─" * 78)
                
                try:
                    # 使用带 reasoning 的同步调用，提取模型思考过程
                    intent_result, intent_reasoning = await loop.run_in_executor(
                        None, llm_service._call_llm_sync_with_reasoning, intent_prompt
                    )
                    
                    # 将模型的推理过程传递给前端（如果有）
                    if intent_reasoning:
                        yield _reasoning(intent_reasoning)
                    
                    intent_elapsed = time.time() - _t0
                    stream_stats.intent_elapsed = intent_elapsed
                    
                    # 记录LLM响应
                    if intent_result:
                        logger.info("┌" + "─" * 78)
                        logger.info("│ 📥 意图识别 - LLM响应")
                        logger.info(f"│    耗时: {intent_elapsed:.3f}s")
                        logger.info(f"│    响应长度: {len(intent_result)} 字符")
                        logger.info(f"│    响应预览: {intent_result[:300]}...")
                        logger.info("└" + "─" * 78)
                        
                        try:
                            cleaned = _strip_json_comments(intent_result.strip())
                            intent_data = json.loads(cleaned)
                            intent_type = intent_data.get("intentType", "chat")

                            logger.info("┌" + "─" * 78)
                            logger.info("│ 🔍 意图解析结果")
                            logger.info(f"│    Intent Type: {intent_type}")
                            logger.info(f"│    Form Code: {intent_data.get('formCode', '-')}")
                            logger.info(f"│    Extracted Fields: {list(intent_data.get('extractedFields', {}).keys())}")
                            logger.info(f"│    Confidence: {intent_data.get('confidence', '-')}")
                            logger.info("└" + "─" * 78)
                            yield _thinking(f"✅ 意图识别完成: {intent_type} (耗时 {intent_elapsed:.2f}s)")

                            # ── 表单意图：首次生成 ──────────────────────────────────────
                            if intent_type == "form":
                                form_code = intent_data.get("formCode")
                                form_name = ""
                                if form_code and form_code in ontologies:
                                    form_name = ontologies[form_code].get("formName", form_code)
                                yield _thinking(f"📋 识别到表单类型: {form_name or form_code}")

                                extracted = intent_data.get("extractedFields", {})
                                if extracted:
                                    field_details = [f"{k}={v}" for k, v in extracted.items()]
                                    yield _thinking(f"📝 提取到 {len(extracted)} 个字段:")
                                    yield _thinking(f"   {', '.join(field_details)}")
                                else:
                                    yield _thinking("📝 未提取到具体字段值，将展示空表单供用户填写")

                                confidence = intent_data.get("confidence", 0)
                                yield _thinking(f"📊 识别置信度: {confidence}")

                                # ── 获取历史推荐数据 ──────────────────────────────────────
                                try:
                                    # 获取推荐引擎
                                    recommendation_engine = get_recommendation_engine()

                                    # 构建对话上下文
                                    conversation_context = {
                                        "messages": [
                                            {"role": msg.role, "content": msg.content}
                                            for msg in request.messages
                                        ],
                                        "extractedFields": extracted,
                                        "lastUserMessage": last_user_message
                                    }

                                    # 使用批量推荐
                                    recommendations_result = recommendation_engine.batch_recommend(
                                        form_code=form_code,
                                        extracted_fields=extracted,
                                        user_input=last_user_message,
                                        user_id=request.userId,
                                        conversation_context=conversation_context,
                                        max_per_field=5,
                                        db=db
                                    )

                                    # 整理推荐结果
                                    all_recommendations = {}
                                    for field_code, result in recommendations_result.items():
                                        if result.success and result.recommendations:
                                            all_recommendations[field_code] = {
                                                "values": [r.value for r in result.recommendations],
                                                "source": result.strategy_used,
                                                "totalCandidates": result.total_candidates,
                                                "processingTimeMs": result.processing_time_ms
                                            }

                                    if all_recommendations:
                                        logger.info(f"[chat/stream] 📚 获取到历史推荐: {all_recommendations}")
                                        yield _thinking(f"📚 基于历史数据为 {len(all_recommendations)} 个字段生成推荐")

                                        # 将推荐数据添加到 intent_data
                                        intent_data["fieldRecommendations"] = all_recommendations

                                except Exception as rec_err:
                                    logger.warning(f"[chat/stream] 获取历史推荐失败: {rec_err}")
                                # ── 历史推荐获取完成 ──────────────────────────────────────

                                # 发送统计信息
                                stream_stats.total_elapsed = time.time() - start_time
                                stream_stats.is_form = True
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})

                                yield _sse({"type": "result", "content": intent_result})
                                yield _sse({"type": "done", "isForm": True, "intentType": "form", "intentData": intent_data})
                                return

                            # ── 表单更新意图：增量更新字段 ──────────────────────────────────────
                            elif intent_type == "form_update":
                                detected_form_code = intent_data.get("detectedFormCode", "")
                                form_name = ""
                                if detected_form_code and detected_form_code in ontologies:
                                    form_name = ontologies[detected_form_code].get("formName", detected_form_code)
                                yield _thinking(f"🔄 识别到表单更新请求: {form_name or detected_form_code}")

                                extracted = intent_data.get("extractedFields", {})
                                if extracted:
                                    field_details = [f"{k}={v}" for k, v in extracted.items()]
                                    yield _thinking(f"📝 提取到 {len(extracted)} 个待更新字段:")
                                    yield _thinking(f"   {', '.join(field_details)}")
                                else:
                                    yield _thinking("⚠️ 未提取到任何字段值，请检查用户输入")

                                confidence = intent_data.get("confidence", 0)
                                yield _thinking(f"📊 更新置信度: {confidence}")

                                # 发送统计信息
                                stream_stats.total_elapsed = time.time() - start_time
                                stream_stats.is_form = True
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})

                                yield _sse({"type": "result", "content": intent_result})
                                yield _sse({"type": "done", "isForm": True, "intentType": "form_update", "intentData": intent_data})
                                return

                            # ── 配置意图：AI 对话生成新表单配置 ──────────────────────────────────
                            elif intent_type == "configure":
                                suggested_code = intent_data.get("formCode", "")
                                suggested_name = intent_data.get("formName", "")
                                yield _thinking(f"🛠️ 识别到新业务配置请求: {suggested_name or suggested_code}")
                                yield _thinking("📋 正在通过 AI 生成表单配置...")

                                # 构建 AdminService 对话
                                ai_messages = []
                                for msg in request.messages:
                                    ai_messages.append({
                                        "role": msg.role,
                                        "content": msg.content
                                    })

                                # 调用 AdminService.chat 生成配置
                                loop = asyncio.get_event_loop()
                                ai_result = await loop.run_in_executor(
                                    None,
                                    lambda: AdminService.chat(ai_messages)
                                )

                                if ai_result.get("success") and ai_result.get("hasConfig"):
                                    config_data = ai_result.get("config", {})
                                    validation_errors = ai_result.get("validationErrors", [])
                                    reply_text = ai_result.get("reply", "")

                                    yield _thinking(f"✅ 配置生成完成: {config_data.get('formName', '')} ({config_data.get('formCode', '')})")

                                    if validation_errors:
                                        yield _thinking(f"⚠️ 配置校验问题: {'; '.join(validation_errors)}")

                                    # 生成场景关键词
                                    keywords_result = await loop.run_in_executor(
                                        None,
                                        lambda: AdminService.generate_scene_keywords(
                                            config_data.get("formCode", ""),
                                            config_data.get("formName", ""),
                                            config_data.get("description", "")
                                        )
                                    )

                                    auto_keywords = []
                                    if keywords_result.get("success"):
                                        auto_keywords = keywords_result.get("keywords", [])
                                        yield _thinking(f"🔑 自动生成 {len(auto_keywords)} 个场景关键词")

                                    # 发送 config 事件（携带完整配置 + 关键词）
                                    config_payload = {
                                        "config": config_data,
                                        "keywords": auto_keywords,
                                        "validationErrors": validation_errors,
                                        "reply": reply_text
                                    }

                                    stream_stats.total_elapsed = time.time() - start_time
                                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})

                                    yield _sse({"type": "config", "content": config_payload})
                                    yield _sse({"type": "done", "isForm": False, "intentType": "configure", "intentData": intent_data})
                                    return

                                elif ai_result.get("success"):
                                    # AI 回复了但没有生成配置（纯对话）
                                    reply_text = ai_result.get("reply", "请描述你想创建的表单类型。")
                                    yield _thinking("💬 AI 正在引导用户描述需求...")

                                    stream_stats.total_elapsed = time.time() - start_time
                                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})

                                    yield _sse({"type": "text_start"})
                                    yield _sse({"type": "text", "content": reply_text})
                                    yield _sse({"type": "text_end"})
                                    yield _sse({"type": "done", "isForm": False, "intentType": "configure", "intentData": intent_data})
                                    return

                                else:
                                    error_msg = ai_result.get("reply", "配置生成失败")
                                    yield _thinking(f"❌ 配置生成失败: {error_msg}")

                                    stream_stats.total_elapsed = time.time() - start_time
                                    stream_stats.error = error_msg
                                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                    yield _sse({"type": "error", "content": error_msg})
                                    return

                            # ── 聊天意图，真正流式输出 ──────────────────────────────────────────
                            elif intent_type == "chat":
                                yield _thinking("💬 正在生成回复...")
                                final_llm_stats: Optional[StreamStats] = None
                                async for chunk, stats in _stream_chat_reply(
                                    intent_prompt, ontologies_info, messages_text
                                ):
                                    if stats is not None:
                                        final_llm_stats = stats
                                        continue
                                    yield chunk
                                
                                # 更新统计信息
                                if final_llm_stats:
                                    stream_stats.llm_elapsed = final_llm_stats.elapsed
                                    stream_stats.llm_tokens = final_llm_stats.token_count
                                    stream_stats.llm_chars = final_llm_stats.char_count
                                    stream_stats.llm_tps = final_llm_stats.tokens_per_second
                                
                                stream_stats.total_elapsed = time.time() - start_time
                                stream_stats.is_form = False
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield _sse({"type": "done", "isForm": False})
                                return

                        except json.JSONDecodeError as e:
                            logger.warning("意图识别 JSON 解析失败: %s  raw=%s", e,
                                           intent_result[:200] if intent_result else "")
                            if fallback_enabled:
                                yield _thinking(f"⚠️ 意图解析失败，降级到 Skills")
                            else:
                                yield _thinking(f"❌ 意图解析失败，且未启用降级处理")
                                stream_stats.total_elapsed = time.time() - start_time
                                stream_stats.error = f"LLM 意图解析失败: {str(e)}"
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield _sse({"type": "error", "content": f"LLM 意图解析失败: {str(e)}"})
                                return
                        except Exception as e:
                            logger.exception("意图处理异常: %s", e)
                            if fallback_enabled:
                                yield _thinking(f"⚠️ 处理异常: {e}")
                            else:
                                yield _thinking(f"❌ 处理异常: {e}")
                                stream_stats.total_elapsed = time.time() - start_time
                                stream_stats.error = f"处理异常: {str(e)}"
                                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield _sse({"type": "error", "content": f"处理异常: {str(e)}"})
                                return
                    else:
                        logger.warning("[chat/stream] LLM 返回为空，耗时 %.1fs", intent_elapsed)
                        if fallback_enabled:
                            yield _thinking(f"⚠️ LLM 返回为空（耗时 {intent_elapsed:.1f}s），降级到 Skills")
                        else:
                            yield _thinking(f"❌ LLM 返回为空（耗时 {intent_elapsed:.1f}s），且未启用降级处理")
                            stream_stats.total_elapsed = time.time() - start_time
                            stream_stats.error = "LLM 返回为空，且未启用降级处理"
                            yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                            yield _sse({"type": "error", "content": "LLM 返回为空，且未启用降级处理"})
                            return
                except Exception as e:
                    logger.exception("LLM 调用异常: %s", e)
                    if fallback_enabled:
                        yield _thinking(f"⚠️ LLM 调用失败，降级到 Skills")
                    else:
                        yield _thinking(f"❌ LLM 调用失败，且未启用降级处理")
                        stream_stats.total_elapsed = time.time() - start_time
                        stream_stats.error = f"LLM 调用失败: {str(e)}"
                        yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                        yield _sse({"type": "error", "content": f"LLM 调用失败: {str(e)}"})
                        return

            # ── 只有在启用 fallback 时才使用 Skills ──────────────────────────────────────────
            if fallback_enabled:
                yield _thinking("⚙️ 切换到 Skills 模式处理")

                # 先检查是否包含表单关键词
                form_keywords = ['订单', '请假', '报销', '合同', '项目', '表单', '填写', '生成']
                matched_keywords = [kw for kw in form_keywords if kw in last_user_message]

                if matched_keywords:
                    yield _thinking(f"🔑 检测到业务关键词: {', '.join(matched_keywords)}")

                yield _thinking("🔍 进行场景识别...")
                skills_result = _call_skills_only(last_user_message, ontologies)

                if skills_result.intentType == "form":
                    form_code = skills_result.formCode
                    form_name = ""
                    if form_code and form_code in ontologies:
                        form_name = ontologies[form_code].get("formName", form_code)
                    yield _thinking(f"📋 识别到表单: {form_name or form_code}")

                    extracted = skills_result.extractedFields or {}
                    if extracted:
                        field_names = list(extracted.keys())
                        yield _thinking(f"📝 从用户输入中提取到 {len(extracted)} 个字段: {', '.join(field_names)}")
                    else:
                        yield _thinking("📝 用户未提供具体字段值，将展示空表单")

                    intent_data = {
                        "formCode": form_code,
                        "formName": form_name,
                        "extractedFields": extracted,
                        "confidence": skills_result.confidence or 0.7
                    }
                    
                    # 发送统计信息
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = True
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    
                    yield _sse({"type": "result",
                                "content": json.dumps(skills_result.dict(), ensure_ascii=False)})
                    yield _sse({"type": "done", "isForm": True, "intentData": intent_data})
                else:
                    yield _thinking("💬 生成聊天回复...")
                    reply_text = skills_result.reply or ""
                    yield _sse({"type": "text_start"})
                    # 使用字符块而不是逐字符
                    chunk_size = 5
                    for i in range(0, len(reply_text), chunk_size):
                        chunk = reply_text[i:i + chunk_size]
                        yield _sse({"type": "text", "content": chunk})
                        await asyncio.sleep(0)   # 让出事件循环，不阻塞
                    yield _sse({"type": "text_end"})
                    
                    # 发送统计信息
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = False
                    stream_stats.llm_chars = len(reply_text)
                    yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                    
                    yield _sse({"type": "done", "isForm": False})
            else:
                yield _thinking("❌ LLM 处理流程失败，且未启用降级处理")
                stream_stats.total_elapsed = time.time() - start_time
                stream_stats.error = "LLM 处理流程失败，且未启用降级处理"
                yield _sse({"type": "stats", "content": stream_stats.to_dict()})
                yield _sse({"type": "error", "content": "LLM 处理流程失败，且未启用降级处理"})

        except Exception as e:
            logger.exception("Stream error: %s", e)
            stream_stats.error = str(e)
            stream_stats.total_elapsed = time.time() - start_time
            yield _sse({"type": "stats", "content": stream_stats.to_dict()})
            yield _sse({"type": "error", "content": str(e)})

    return StreamingResponse(
        stream_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


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


class SessionListResponse(BaseModel):
    sessions: List[Dict[str, Any]]
    total: int


class SessionCreateRequest(BaseModel):
    user_id: Optional[str] = None
    title: Optional[str] = None


class SessionCreateResponse(BaseModel):
    success: bool
    session_id: Optional[str] = None
    title: Optional[str] = None
    created_at: Optional[str] = None
    error: Optional[str] = None


class MessageListResponse(BaseModel):
    messages: List[Dict[str, Any]]
    total: int


@router.get("/chat/sessions", response_model=SessionListResponse)
async def get_chat_sessions(
    user_id: Optional[str] = Query(None, description="用户ID"),
    limit: int = Query(50, ge=1, le=100, description="返回数量限制"),
    db: Session = Depends(get_db)
):
    logger.debug("[chat/sessions] 查询会话列表 user_id=%s limit=%d", user_id, limit)
    sessions = ChatHistoryService.get_sessions(user_id=user_id, limit=limit, db=db)
    logger.debug("[chat/sessions] 返回 %d 条会话", len(sessions))
    return SessionListResponse(sessions=sessions, total=len(sessions))


@router.post("/chat/sessions", response_model=SessionCreateResponse)
async def create_chat_session(
    request: SessionCreateRequest,
    db: Session = Depends(get_db)
):
    logger.info("[chat/sessions] 创建会话 user_id=%s title=%s", request.user_id, request.title)
    result = ChatHistoryService.create_session(
        user_id=request.user_id,
        title=request.title,
        db=db
    )
    if result.get("success"):
        logger.info("[chat/sessions] 创建成功 session_id=%s", result.get("session_id"))
        return SessionCreateResponse(
            success=True,
            session_id=result.get("session_id"),
            title=result.get("title"),
            created_at=result.get("created_at")
        )
    logger.warning("[chat/sessions] 创建失败: %s", result.get("error", "未知错误"))
    return SessionCreateResponse(success=False, error=result.get("error", "创建失败"))


@router.get("/chat/sessions/{session_id}/messages", response_model=MessageListResponse)
async def get_chat_messages(
    session_id: str,
    db: Session = Depends(get_db)
):
    logger.debug("[chat/sessions/messages] 查询消息 session_id=%s", session_id)
    messages = ChatHistoryService.get_messages(session_id=session_id, db=db)
    logger.debug("[chat/sessions/messages] 返回 %d 条消息 session_id=%s", len(messages), session_id)
    return MessageListResponse(messages=messages, total=len(messages))


@router.delete("/chat/sessions/{session_id}")
async def delete_chat_session(
    session_id: str,
    db: Session = Depends(get_db)
):
    logger.info("[chat/sessions] 删除会话 session_id=%s", session_id)
    success = ChatHistoryService.delete_session(session_id=session_id, db=db)
    if not success:
        logger.warning("[chat/sessions] 删除失败 session_id=%s", session_id)
    return {"success": success}


@router.patch("/chat/sessions/{session_id}")
async def update_chat_session_title(
    session_id: str,
    title: str = Query(..., description="新的会话标题"),
    db: Session = Depends(get_db)
):
    logger.info("[chat/sessions] 更新标题 session_id=%s title=%s", session_id, title)
    success = ChatHistoryService.update_session_title(session_id=session_id, title=title, db=db)
    if not success:
        logger.warning("[chat/sessions] 更新标题失败 session_id=%s", session_id)
    return {"success": success}


# ── 测试端点 ─────────────────────────────────────────────────────────────────

@router.post("/test/llm-call")
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

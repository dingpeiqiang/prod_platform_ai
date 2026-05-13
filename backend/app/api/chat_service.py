import json
import logging
from typing import Dict, Any, Optional, AsyncGenerator

from app.services.llm_service import llm_service, StreamStats
from app.core.config_loader import config_loader
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.core.errors import ErrorCategory, ErrorLevel, ErrorCode
from app.core.error_handler import create_error, error_handler
from app.mcp_tools import get_toolhub
from app.api.chat_utils import (
    strip_json_comments, fix_json_newlines, fix_incomplete_json, extract_json_from_text,
    build_ontologies_info, build_scene_keywords, build_scene_hierarchy, build_separators,
    FALLBACK_RESPONSES, sse, thinking, reasoning
)

logger = logging.getLogger("chat_service")


def call_skills_only(last_user_message: str, ontologies: Dict) -> Dict:
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

            return {
                "success": True,
                "sceneCode": form_code,
                "formCode": form_code,
                "extractedFields": extracted_fields,
                "confidence": 0.7,
                "method": "skills"
            }

    reply = FALLBACK_RESPONSES['默认']
    for key, value in FALLBACK_RESPONSES.items():
        if key != '默认' and key in last_user_message:
            reply = value
            break

    return {
        "success": True,
        "sceneCode": "",
        "formCode": "",
        "reply": reply,
        "method": "skills"
    }


def build_intent_prompt(messages_text: str, last_user_message: str) -> Optional[str]:
    """构建意图识别的 prompt"""
    intent_prompt_template = config_loader.get_prompt('smart_intent_recognition')
    if not intent_prompt_template:
        return None

    ontologies_info = build_ontologies_info()
    scene_hierarchy = build_scene_hierarchy()
    scene_keywords = build_scene_keywords()
    separators = build_separators()
    mcp_info_raw = get_toolhub().get_tool_schemas_for_llm()

    return (
        intent_prompt_template
        .replace("{ontologies_info}", ontologies_info)
        .replace("{scene_hierarchy}", scene_hierarchy)
        .replace("{scene_keywords}", scene_keywords)
        .replace("{separators}", separators)
        .replace("{mcp_tools_info}", mcp_info_raw or "")
        .replace("{messages_text}", messages_text or "[]")
        .replace("{last_user_message}", last_user_message or "")
    )


def get_scene_prompt_by_code(scene_code: str) -> Optional[str]:
    """
    根据场景编码获取场景提示词内容
    
    流程：场景编码 → 查询场景表获取提示词编码 → 根据提示词编码获取提示词内容
    
    Args:
        scene_code: 场景编码
        
    Returns:
        提示词内容，如果未找到返回 None
    """
    try:
        from app.services.scene_service import SceneService
        from app.core.database import get_db
        
        db_gen = get_db()
        db = next(db_gen)
        try:
            prompt_result = SceneService.get_scene_prompt(scene_code, db)
            if prompt_result["success"]:
                return prompt_result.get("prompt_content")
            else:
                logger.warning(f"[get_scene_prompt_by_code] 获取提示词失败: {prompt_result.get('message')}")
                return None
        finally:
            db.close()
    except Exception as e:
        logger.exception(f"[get_scene_prompt_by_code] 异常 scene_code={scene_code}: {e}")
        return None


def parse_intent_result(intent_result: str) -> Optional[Dict]:
    """解析 LLM 返回的意图识别结果"""
    if not intent_result:
        return None

    try:
        # 先从文本中提取 JSON 部分（处理模型返回额外文本的情况）
        cleaned = extract_json_from_text(intent_result.strip())
        cleaned = strip_json_comments(cleaned)
        cleaned = fix_json_newlines(cleaned)
        # 尝试修复不完整的 JSON（模型返回结果被截断的情况）
        cleaned = fix_incomplete_json(cleaned)
        return json.loads(cleaned)
    except json.JSONDecodeError as e:
        logger.warning("意图识别 JSON 解析失败: %s raw=%s", e, intent_result[:200] if intent_result else "")
        return None
    except Exception as e:
        logger.exception("意图解析异常: %s", e)
        return None


async def execute_tool_calls(intent_data: Dict) -> Dict:
    """执行 MCP 工具调用"""
    tool_calls = intent_data.get("tool_calls", [])
    extracted = intent_data.get("extractedFields", {})
    tool_results = []

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
                    tool_results.append({
                        "name": tool_name,
                        "success": True,
                        "fields": list(tool_result.keys()) if isinstance(tool_result, dict) else []
                    })
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

    intent_data["extractedFields"] = extracted
    return {
        "tool_results": tool_results,
        "extracted": extracted
    }


async def stream_chat_reply(
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
    import asyncio
    assistant_message_id = str(uuid.uuid4())

    chat_prompt_template = config_loader.get_prompt('smart_chat_response')
    if not chat_prompt_template:
        yield sse({"type": "text_start", "message_id": assistant_message_id}), None
        yield sse({"type": "text", "content": "好的，请问有什么可以帮助你？"}), None
        yield sse({"type": "text_end"}), None
        return

    prompt = chat_prompt_template.format(
        ontologies_info=ontologies_info,
        messages_text=messages_text
    )

    yield thinking("🤖 正在生成回复...", None, assistant_message_id), None
    logger.info("[stream_chat] 开始流式调用，prompt长度=%d", len(prompt))

    final_stats: Optional[StreamStats] = None
    _thinking_buf: str = ""
    _in_thinking: bool = False
    _text_started: bool = False

    async for text, stats in llm_service.call_llm_stream_with_stats(prompt):
        if stats is not None:
            final_stats = stats
            continue

        if not text:
            continue

        remaining = text
        while remaining:
            if _in_thinking:
                end_idx = remaining.find("[/THINKING]")
                if end_idx == -1:
                    _thinking_buf += remaining
                    remaining = ""
                else:
                    _thinking_buf += remaining[:end_idx]
                    # 将 thinking 内容转为 reasoning 事件发送给前端
                    if _thinking_buf.strip():
                        yield reasoning(_thinking_buf), None
                    _thinking_buf = ""
                    _in_thinking = False
                    remaining = remaining[end_idx + len("[/THINKING]"):]
            else:
                start_idx = remaining.find("[THINKING]")
                if start_idx == -1:
                    if remaining.strip():
                        if not _text_started:
                            _text_started = True
                            yield sse({"type": "text_start", "message_id": assistant_message_id}), None
                        yield sse({"type": "text", "content": remaining}), None
                        await asyncio.sleep(0)
                    remaining = ""
                else:
                    before = remaining[:start_idx]
                    if before.strip():
                        if not _text_started:
                            _text_started = True
                            yield sse({"type": "text_start", "message_id": assistant_message_id}), None
                        yield sse({"type": "text", "content": before}), None
                        await asyncio.sleep(0)
                    _in_thinking = True
                    remaining = remaining[start_idx + len("[THINKING]"):]

    # 处理未闭合的 thinking 块
    if _in_thinking and _thinking_buf.strip():
        yield reasoning(_thinking_buf), None

    if not _text_started:
        yield sse({"type": "text_start", "message_id": assistant_message_id}), None

    if final_stats:
        logger.info("[stream_chat] 流式完成: tokens=%d chars=%d thinking_chars=%d chunks=%d elapsed=%.2fs tps=%.1f",
                    final_stats.token_count, final_stats.char_count,
                    final_stats.thinking_chars,
                    final_stats.chunk_count, final_stats.elapsed,
                    final_stats.tokens_per_second)
    else:
        logger.info("[stream_chat] 流式完成")

    yield sse({"type": "text_end"}), final_stats



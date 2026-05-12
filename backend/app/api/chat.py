from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional, AsyncGenerator
from sqlalchemy.orm import Session
import json
import asyncio
import logging
import time

from app.services.llm_service import llm_service
from app.core.config_loader import config_loader
from app.core.database import get_db
from app.services.agent_executor import AgentExecutor
from app.services.recommendation_engine import get_recommendation_engine
from app.core.errors import ErrorCategory, ErrorLevel, ErrorCode
from app.core.error_handler import error_handler, create_error
from app.core.config import get_settings
from app.intent import get_intent_registry
from app.intent.base import IntentContext

from app.api.chat_utils import (
    truncate, merge_field_recommendations, strip_json_comments,
    fix_json_newlines, build_ontologies_info, build_scene_keywords,
    build_separators, sse, thinking, reasoning, FALLBACK_RESPONSES
)
from app.api.chat_service import (
    call_skills_only, build_intent_prompt, parse_intent_result,
    execute_tool_calls, get_scene_prompt_by_code
)

logger = logging.getLogger("chat_api")


class ChatStreamStats:
    def __init__(self):
        self.total_elapsed = 0.0
        self.intent_elapsed = 0.0
        self.llm_elapsed = 0.0
        self.llm_tokens = 0
        self.llm_chars = 0
        self.llm_tps = 0.0
        self.is_form = False
        self.error = None

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
    formCode: Optional[str] = None
    formData: Optional[Dict[str, Any]] = None


class ChatResponse(BaseModel):
    success: bool
    reply: Optional[str] = None
    intentType: Optional[str] = None
    formCode: Optional[str] = None
    extractedFields: Optional[Dict[str, Any]] = None
    confidence: Optional[float] = None
    reasoning: Optional[str] = None
    message: Optional[str] = None
    method: Optional[str] = None


@router.post("/chat/agent/stream")
async def chat_agent_stream(request: ChatRequest):
    last_user_message = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            last_user_message = msg.content
            break

    logger.info("[chat/agent/stream] 收到请求 msg=%s", truncate(last_user_message, 100))

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
    last_user_message = ""
    for msg in reversed(request.messages):
        if msg.role == "user":
            last_user_message = msg.content
            break

    logger.info("\n" + "*"*60)
    logger.info("[API REQUEST] /chat/agent - 用户消息: %s", last_user_message)

    try:
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
        messages_text = "\n".join([
            f"{msg.role}: {msg.content}"
            for msg in request.messages
        ])

        last_user_message = ""
        for msg in reversed(request.messages):
            if msg.role == "user":
                last_user_message = msg.content
                break

        fallback_enabled = llm_service.fallback_to_rules

        if not llm_service.enabled:
            if fallback_enabled:
                logger.info("LLM service is disabled, using skills result")
                skills_result = call_skills_only(last_user_message, ontologies)
                return ChatResponse(**skills_result)
            else:
                logger.error("LLM service is disabled and fallbackToRules is false")
                return ChatResponse(
                    success=False,
                    message="LLM 服务已禁用，且未启用降级处理",
                    method="llm_disabled_no_fallback"
                )

        logger.info("Attempting LLM call for intent recognition (fallbackToRules: %s)", fallback_enabled)
        try:
            intent_prompt = build_intent_prompt(messages_text, last_user_message)
            if intent_prompt:
                logger.info("Calling LLM for intent recognition")
                intent_result = llm_service._call_llm_sync(intent_prompt)
                logger.info("LLM intent result received: %s", intent_result is not None)

                if intent_result:
                    intent_data = parse_intent_result(intent_result)
                    if intent_data:
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
                            ontologies_info = build_ontologies_info()
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
                    else:
                        if not fallback_enabled:
                            return ChatResponse(
                                success=False,
                                message="LLM 意图解析失败",
                                method="llm_parse_error_no_fallback"
                            )
            else:
                if not fallback_enabled:
                    return ChatResponse(
                        success=False,
                        message="未找到意图识别模板",
                        method="llm_call_error_no_fallback"
                    )
        except Exception as e:
            logger.error("LLM call failed: %s", e)
            if not fallback_enabled:
                return ChatResponse(
                    success=False,
                    message=f"LLM 调用失败: {str(e)}",
                    method="llm_call_error_no_fallback"
                )

        if fallback_enabled:
            skills_result = call_skills_only(last_user_message, ontologies)
            skills_result["method"] = "skills_fallback"
            return ChatResponse(**skills_result)
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
        skills_result = call_skills_only(last_user_message, {})
        skills_result["method"] = "fallback"
        return ChatResponse(**skills_result)


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

            logger.info(f"[chat/stream] 最后一条用户消息: {truncate(last_user_message, 200)}")

            messages_text = "\n".join([
                f"{msg.role}: {msg.content}"
                for msg in request.messages
            ])

            yield thinking("🔍 正在分析用户意图...", result={
                "messagesCount": len(request.messages),
                "lastUserMessage": last_user_message[:100] if last_user_message else ""
            })

            if not llm_service.enabled:
                if fallback_enabled:
                    yield thinking("⚙️ LLM 不可用，切换到 Skills 模式处理")
                else:
                    yield thinking("❌ LLM 服务已禁用，且未启用降级处理")
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
                    yield sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield sse(error.to_sse())
                    return

            intent_prompt = build_intent_prompt(messages_text, last_user_message)
            if intent_prompt:
                loop = asyncio.get_event_loop()
                _t0 = time.time()
                _retry_count = 0

                yield thinking("🧠 调用 LLM 进行意图识别...", result={
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
                    logger.info(f"[chat/stream] LLM 返回结果: intent_result={len(intent_result) if intent_result else 0} chars, intent_reasoning={len(intent_reasoning) if intent_reasoning else 0} chars")

                    if not intent_result and intent_reasoning:
                        logger.info("[chat/stream] 🔄 content 为空但 reasoning 有内容，用简化 prompt 重试一次")
                        _retry_count = 1
                        yield thinking("🔄 模型响应格式异常，正在重试...", result={
                            "retry": True,
                            "originalElapsed": round(time.time() - _t0, 2)
                        })
                        retry_prompt = intent_prompt + "\n\n---\n**重要提醒**：请直接输出 JSON，不要在 JSON 之外输出任何分析文本。你的回答必须是一个合法的 JSON 对象，以 { 开头，以 } 结尾。"
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
                        intent_data = parse_intent_result(intent_result)
                        if intent_data:
                            intent_type = intent_data.get("intentType", "chat")
                            scene_code = intent_data.get("sceneCode") or intent_data.get("formCode") or intent_data.get("form_code")

                            logger.info(f"[chat/stream] intent_type={intent_type}, scene_code={scene_code}, will_output_reasoning={bool(intent_reasoning)}")
                            if intent_reasoning:
                                yield reasoning(intent_reasoning)

                            scene_prompt_content = None
                            if scene_code:
                                yield thinking(f"🔍 查询场景提示词 scene_code={scene_code}")
                                scene_prompt_content = get_scene_prompt_by_code(scene_code)
                                if scene_prompt_content:
                                    logger.info(f"[chat/stream] 成功获取场景提示词，长度={len(scene_prompt_content)}")
                                    yield thinking(f"✅ 已获取场景提示词")
                                else:
                                    logger.warning(f"[chat/stream] 未找到场景 {scene_code} 的提示词")
                                    yield thinking(f"⚠️ 未找到场景提示词，使用默认处理")

                            if scene_prompt_content and last_user_message:
                                yield thinking(f"🧠 使用场景提示词调用大模型...")
                                try:
                                    scene_response = llm_service._call_llm_sync(last_user_message, system_prompt=scene_prompt_content)
                                    if scene_response:
                                        intent_data["sceneResponse"] = scene_response
                                        logger.info(f"[chat/stream] 场景提示词调用成功，响应长度={len(scene_response)}")
                                        yield thinking(f"✅ 场景大模型调用完成")
                                        yield sse({"type": "text_start"})
                                        yield sse({"type": "text", "content": scene_response})
                                        yield sse({"type": "text_end"})
                                    else:
                                        yield thinking(f"⚠️ 场景大模型返回为空")
                                except Exception as e:
                                    logger.exception(f"[chat/stream] 场景大模型调用失败: {e}")
                                    yield thinking(f"❌ 场景大模型调用失败: {str(e)}")

                            tool_result = await execute_tool_calls(intent_data)
                            tool_results = tool_result["tool_results"]
                            extracted = tool_result["extracted"]

                            yield thinking(
                                f"🔧 已执行 {len(tool_results)} 个工具，" + (f"成功 {sum(1 for r in tool_results if r['success'])} 个" if tool_results else "无工具调用"),
                                result={
                                    "tools": tool_results,
                                    "totalTools": len(tool_results),
                                    "successCount": sum(1 for r in tool_results if r["success"]),
                                    "failedCount": sum(1 for r in tool_results if not r["success"]),
                                    "extractedFields": list(extracted.keys())
                                }
                            )

                            form_code = intent_data.get("detectedFormCode") or intent_data.get("formCode") or intent_data.get("form_code") or scene_code
                            yield thinking(
                                f"✅ 意图识别完成: {intent_type}" + (f" ({intent_elapsed:.2f}s)" if intent_elapsed else ""),
                                result={
                                    "intentType": intent_type,
                                    "formCode": form_code,
                                    "extractedFields": list(intent_data.get("extractedFields", {}).keys()),
                                    "extractedCount": len(extracted),
                                    "confidence": intent_data.get("confidence"),
                                    "elapsed": round(intent_elapsed, 2),
                                    "retryCount": _retry_count,
                                    "hasScenePrompt": scene_prompt_content is not None
                                }
                            )

                            _FORM_CODE_TO_INTENT = {
                                "tariff_filing_publicity": "tariff_filing",
                            }
                            if intent_type == "form" and form_code in _FORM_CODE_TO_INTENT:
                                intent_type = _FORM_CODE_TO_INTENT[form_code]
                                logger.info(f"[路由] 表单 %s 使用专属 handler: %s", form_code, intent_type)

                            if request.formCode:
                                intent_data["form_code"] = request.formCode
                            if request.formData:
                                intent_data["form_data"] = request.formData

                            ctx = IntentContext(
                                intent_data=intent_data,
                                intent_result=intent_result,
                                intent_type=intent_type,
                                confidence=intent_data.get("confidence", 0),
                                ontologies=ontologies,
                                ontologies_info=build_ontologies_info(),
                                scene_keywords=build_scene_keywords(),
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
                        else:
                            _llm_error = "JSON 解析失败"
                            yield thinking(f"❌ JSON 解析失败", result={
                                "error": _llm_error,
                                "elapsed": round(time.time() - _t0, 2) if '_t0' in dir() else 0
                            })
                            if not fallback_enabled:
                                stream_stats.total_elapsed = time.time() - start_time
                                error = create_error(
                                    category=ErrorCategory.LLM.value,
                                    code=ErrorCode.LLM_PARSE_ERROR,
                                    message="LLM 意图解析失败",
                                    level=ErrorLevel.ERROR.value,
                                    recoverable=False,
                                    recovery_hint="请稍后重试，或联系管理员检查 AI 服务配置",
                                    raw_response=intent_result[:200] if intent_result else ""
                                )
                                error_handler.emit(error)
                                stream_stats.error = error.message
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield sse(error.to_sse())
                                return
                    else:
                        logger.warning("[chat/stream] LLM 返回为空，耗时 %.1fs", intent_elapsed)
                        _llm_error = f"LLM 返回为空（耗时 {intent_elapsed:.1f}s）"
                        yield thinking(f"❌ {_llm_error}", result={
                            "error": "LLM 返回为空",
                            "elapsed": round(intent_elapsed, 1) if intent_elapsed else 0,
                            "suggestion": "请稍后重试，或联系管理员检查 AI 服务配置"
                        })
                        if not fallback_enabled:
                            stream_stats.total_elapsed = time.time() - start_time
                            error = create_error(
                                category=ErrorCategory.LLM.value,
                                code=ErrorCode.LLM_EMPTY_RESPONSE,
                                message=_llm_error + "，且未启用降级处理",
                                level=ErrorLevel.ERROR.value,
                                recoverable=False,
                                recovery_hint="请稍后重试，或联系管理员检查 AI 服务",
                                provider=llm_service.llm_config.get('provider'),
                                model=llm_service.llm_config.get('model'),
                                base_url=llm_service.llm_config.get('baseUrl'),
                                elapsed_time=intent_elapsed
                            )
                            error_handler.emit(error)
                            stream_stats.error = error.message
                            yield sse({"type": "stats", "content": stream_stats.to_dict()})
                            yield sse(error.to_sse())
                            return
                except Exception as e:
                    logger.exception("LLM 调用异常: %s", e)
                    _llm_error = str(e)
                    import traceback
                    error_trace = traceback.format_exc()[:500]
                    
                    if '余额不足' in _llm_error or 'quota' in _llm_error.lower():
                        error_code = ErrorCode.LLM_QUOTA_EXCEEDED
                        recovery_hint = "请联系管理员充值 API Key"
                    elif 'rate limit' in _llm_error.lower() or '频繁' in _llm_error:
                        error_code = ErrorCode.LLM_RATE_LIMIT
                        recovery_hint = "请稍后重试，当前调用过于频繁"
                    elif 'timeout' in _llm_error.lower() or '超时' in _llm_error:
                        error_code = ErrorCode.LLM_TIMEOUT
                        recovery_hint = "请稍后重试，服务响应超时"
                    else:
                        error_code = ErrorCode.LLM_UNAVAILABLE
                        recovery_hint = "请稍后重试，或联系管理员检查 AI 服务"
                    
                    yield thinking(f"❌ LLM 调用失败: {str(e)}", result={
                        "error": str(e),
                        "suggestion": recovery_hint
                    })
                    if not fallback_enabled:
                        stream_stats.total_elapsed = time.time() - start_time
                        error = create_error(
                            category=ErrorCategory.LLM.value,
                            code=error_code,
                            message=f"LLM 调用失败: {str(e)}",
                            level=ErrorLevel.ERROR.value,
                            recoverable=False,
                            recovery_hint=recovery_hint,
                            provider=llm_service.llm_config.get('provider'),
                            model=llm_service.llm_config.get('model'),
                            base_url=llm_service.llm_config.get('baseUrl'),
                            error_detail=error_trace,
                            user_input=last_user_message[:200]
                        )
                        error_handler.emit(error)
                        stream_stats.error = error.message
                        yield sse({"type": "stats", "content": stream_stats.to_dict()})
                        yield sse(error.to_sse())
                        return

            if fallback_enabled:
                form_keywords = ['订单', '请假', '报销', '合同', '项目', '表单', '填写', '生成']
                matched_keywords = [kw for kw in form_keywords if kw in last_user_message]

                yield thinking("⚙️ Skills 模式处理（LLM 不可用）", result={
                    "mode": "skills",
                    "matchedKeywords": matched_keywords,
                    "error": _llm_error or "LLM 处理失败"
                })

                skills_result = call_skills_only(last_user_message, ontologies)

                if skills_result["intentType"] == "form":
                    form_code = skills_result["formCode"]
                    form_name = ""
                    if form_code and form_code in ontologies:
                        form_name = ontologies[form_code].get("formName", form_code)

                    extracted = skills_result["extractedFields"] or {}
                    yield thinking(f"📋 识别到表单: {form_name or form_code}", result={
                        "formCode": form_code,
                        "formName": form_name,
                        "extractedFields": list(extracted.keys()),
                        "confidence": skills_result["confidence"] or 0.7
                    })

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
                        "confidence": skills_result["confidence"] or 0.7,
                        "fieldRecommendations": field_recommendations
                    }
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = True
                    yield sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield sse({"type": "result",
                                "content": json.dumps(skills_result, ensure_ascii=False)})
                    yield sse({"type": "done", "isForm": True, "intentData": intent_data})
                else:
                    yield thinking("💬 生成聊天回复...", result={
                        "intentType": "chat",
                        "mode": "skills"
                    })
                    reply_text = skills_result["reply"] or ""
                    yield sse({"type": "text_start"})
                    chunk_size = 5
                    for i in range(0, len(reply_text), chunk_size):
                        chunk = reply_text[i:i + chunk_size]
                        yield sse({"type": "text", "content": chunk})
                        await asyncio.sleep(0)
                    yield sse({"type": "text_end"})
                    stream_stats.total_elapsed = time.time() - start_time
                    stream_stats.is_form = False
                    stream_stats.llm_chars = len(reply_text)
                    yield sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield sse({"type": "done", "isForm": False})
            else:
                yield thinking("❌ LLM 处理失败且未启用降级处理", result={
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
                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                yield sse(error.to_sse())

        except Exception as e:
            logger.exception("Stream error: %s", e)
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
            yield sse({"type": "stats", "content": stream_stats.to_dict()})
            yield sse(error.to_sse())

    return StreamingResponse(
        stream_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


async def test_llm_call():
    import uuid
    import sys

    print("\n" + "=" * 80, flush=True)
    print("🧪 [SYSOUT] 测试端点被调用！", flush=True)
    print(f"时间: {time.strftime('%H:%M:%S')}", flush=True)
    print("=" * 80, flush=True)

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

    print(f"🧪 [SYSOUT] LLM调用结果: {result[:200] if result else 'None'}...", flush=True)
    print("=" * 80 + "\n", flush=True)

    return {
        "success": result is not None,
        "call_id": call_id,
        "prompt": test_prompt,
        "result": result,
        "message": "请查看后端控制台日志"
    }
from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional, AsyncGenerator
from sqlalchemy.orm import Session
import json
import asyncio
from functools import lru_cache
from app.core.logger import get_logger

logger = get_logger(__name__)
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
from app.intent.utils import intent_event, done_event
from app.api.chat_service import (
    call_skills_only, build_intent_prompt, parse_intent_result,
    execute_tool_calls, get_scene_prompt_by_code
)
from app.langchain.workflow_init import workflow_engine
from app.langchain.workflow_engine import WorkflowStatus



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


class CompletionRequest(BaseModel):
    model: Optional[str] = None
    prompt: str
    system_prompt: Optional[str] = None
    temperature: Optional[float] = 0.7
    max_tokens: Optional[int] = 1024
    top_k: Optional[int] = Field(0, ge=0, le=100)
    top_p: Optional[float] = Field(1.0, ge=0.0, le=1.0)
    user_identifier: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "model": "qwen-vl-plus",
                "prompt": "Hello, how are you?",
                "system_prompt": "You are a helpful assistant.",
                "temperature": 0.7,
                "max_tokens": 1024,
                "top_k": 0,
                "top_p": 1.0,
                "user_identifier": "user-123"
            }
        }


@router.post("/chat/completion")
async def chat_completion(request: CompletionRequest, db: Session = Depends(get_db)):
    """单节点LLM调用接口 - 用于工作流编辑器的单节点运行功能"""
    logger.info(f"[chat/completion] ====== 收到请求 ======")
    logger.info(f"[chat/completion] model: {request.model}")
    logger.info(f"[chat/completion] ==== 输入提示词 ====")
    logger.info(f"[chat/completion] {request.prompt}")
    logger.info(f"[chat/completion] ==== 输入结束 ====")
    logger.info(f"[chat/completion] prompt_length: {len(request.prompt)}")
    logger.info(f"[chat/completion] system_prompt: {request.system_prompt[:200] if request.system_prompt else 'None'}")
    logger.debug(f"[chat/completion] temperature: {request.temperature}, type={type(request.temperature)}")
    logger.debug(f"[chat/completion] max_tokens: {request.max_tokens}, type={type(request.max_tokens)}")
    logger.debug(f"[chat/completion] top_k: {request.top_k}, type={type(request.top_k)}")
    logger.debug(f"[chat/completion] top_p: {request.top_p}, type={type(request.top_p)}")
    
    try:
        if not llm_service.enabled:
            logger.warning("[chat/completion] LLM 服务未启用")
            return {"success": False, "message": "LLM 服务未启用"}
        
        # 使用指定的模型配置或默认配置
        provider = llm_service.provider
        custom_provider = None
        
        if request.model:
            from app.services.llm.factory import ProviderFactory
            from app.models.llm_user_config import LLMUserConfig
            
            # 根据模型名称从数据库查询配置
            # 支持两种格式：带前缀的模型ID（如 custom-minimax-m2.7）和不带前缀的模型名称（如 minimax-m2.7）
            model_name = request.model
            if model_name.startswith('custom-'):
                model_name = model_name[7:]  # 移除 custom- 前缀
            
            model_db_config = db.query(LLMUserConfig).filter(
                LLMUserConfig.model == model_name,
                LLMUserConfig.is_active == True
            ).first()
            
            if not model_db_config:
                logger.error(f"[chat/completion] 未找到模型配置: {request.model}")
                raise HTTPException(status_code=400, detail=f"未找到模型配置: {request.model}。请先在'模型配置'面板中添加该模型的配置。")
            
            # 使用数据库配置
            api_key = (model_db_config.api_key or '').strip().strip('`')
            provider_type = model_db_config.provider or 'openai'
            base_url = model_db_config.base_url or ''
            
            logger.info(f"[chat/completion] 从数据库找到模型配置: {request.model}, provider: {provider_type}")
            logger.debug(f"[chat/completion] api_key_exists={bool(api_key)}, base_url={base_url}")
            
            model_config = {
                'provider': provider_type,
                'model': request.model,
                'apiKey': api_key,
                'baseUrl': base_url,
                'authType': model_db_config.auth_type if hasattr(model_db_config, 'auth_type') else 'bearer',
                'authHeader': getattr(model_db_config, 'auth_header', None),
                'apiFormat': getattr(model_db_config, 'api_format', 'openai'),
                'isFullUrl': getattr(model_db_config, 'is_full_url', False),
                'temperature': request.temperature,
                'maxTokens': request.max_tokens
            }
            
            try:
                custom_provider = ProviderFactory.create(model_config['provider'], model_config)
                provider = custom_provider
                logger.info(f"[chat/completion] 使用自定义模型: {request.model}, provider: {provider_type}")
            except Exception as e:
                logger.warning(f"[chat/completion] 创建自定义 Provider 失败，使用默认: {e}")
        
        # 调用 LLM
        response = llm_service.call_with_provider(
            provider,
            request.prompt,
            request.system_prompt,
            request.max_tokens,
            False
        )
        
        if response:
            logger.info(f"[chat/completion] ====== LLM 响应成功 ======")
            logger.info(f"[chat/completion] ==== 输出结果 ====")
            logger.info(f"[chat/completion] {response}")
            logger.info(f"[chat/completion] ==== 输出结束 ====")
            logger.info(f"[chat/completion] 响应长度: {len(response)}")
            return {
                "success": True,
                "result": response,
                "model": request.model or llm_service.llm_config.get('model'),
                "prompt_length": len(request.prompt),
                "response_length": len(response)
            }
        else:
            logger.error(f"[chat/completion] ====== LLM 返回为空 ======")
            logger.error(f"[chat/completion] 输入提示词: {request.prompt[:100]}...")
            raise HTTPException(status_code=500, detail="LLM 调用返回为空，请检查模型配置和网络连接。")
    
    except Exception as e:
        logger.error(f"[chat/completion] 执行失败: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/chat/model/switch")
async def switch_model(model_config: Dict[str, Any]):
    """动态切换模型配置"""
    from app.services.llm.factory import ProviderFactory
    
    try:
        provider_name = model_config.get('provider', 'openai')
        if not ProviderFactory.is_supported(provider_name):
            return {"success": False, "message": f"不支持的 Provider: {provider_name}"}
        
        provider = ProviderFactory.create(provider_name, model_config)
        
        if hasattr(provider, 'call_sync'):
            test_response = provider.call_sync("Hello")
            if test_response is not None:
                return {
                    "success": True,
                    "message": "模型连接成功",
                    "provider": provider_name,
                    "model": model_config.get('model')
                }
        
        return {"success": False, "message": "模型连接测试失败"}
    
    except Exception as e:
        logger.error(f"模型切换失败: {e}")
        return {"success": False, "message": str(e)}


@router.post("/chat/model/test")
async def test_model(model_config: Dict[str, Any]):
    """测试模型配置是否可用"""
    from app.services.llm.factory import ProviderFactory
    
    try:
        provider_name = model_config.get('provider', 'custom')
        logger.info(f"[ModelTest] 开始测试 - Provider: {provider_name}, Model: {model_config.get('model')}")
        
        if not ProviderFactory.is_supported(provider_name):
            return {"success": False, "message": f"不支持的 Provider: {provider_name}"}
        
        provider = ProviderFactory.create(provider_name, model_config)
        
        if hasattr(provider, 'call_sync'):
            try:
                test_response = provider.call_sync("Hello, this is a test message.")
                if test_response is not None:
                    logger.info(f"[ModelTest] 测试成功 - Provider: {provider_name}")
                    return {
                        "success": True,
                        "message": "模型连接测试成功",
                        "provider": provider_name,
                        "model": model_config.get('model'),
                        "response_preview": test_response[:100] if len(test_response) > 100 else test_response
                    }
                else:
                    logger.warning(f"[ModelTest] 测试失败 - 返回值为 None")
                    return {"success": False, "message": "模型连接测试失败: 未能获取有效响应（返回值为 None）"}
            except Exception as call_error:
                error_detail = str(call_error)
                logger.error(f"[ModelTest] call_sync 异常: {error_detail}", exc_info=True)
                raise call_error
        
        return {"success": False, "message": "模型连接测试失败: Provider 不支持同步调用"}
    
    except Exception as e:
        error_message = str(e)
        logger.error(f"[ModelTest] 模型测试失败: {error_message}", exc_info=True)
        
        # 提取更详细的错误信息
        if "401" in error_message or "Unauthorized" in error_message:
            return {
                "success": False, 
                "message": "认证失败: API Key 无效或已过期", 
                "detail": error_message,
                "suggestion": "请检查 API Key 是否正确，或联系服务提供商确认账户状态"
            }
        elif "403" in error_message or "Forbidden" in error_message:
            return {
                "success": False, 
                "message": "权限不足: 该 API Key 没有访问此模型的权限", 
                "detail": error_message,
                "suggestion": "请确认您的账户有访问该模型的权限，或升级账户等级"
            }
        elif "404" in error_message:
            # 提取模型名称
            model_name = model_config.get('model', '未知')
            return {
                "success": False, 
                "message": f"模型 '{model_name}' 不存在或您没有访问权限", 
                "detail": error_message,
                "suggestion": f"请检查模型名称 '{model_name}' 是否正确，或查阅 API 文档确认可用模型列表"
            }
        elif "Connection refused" in error_message or "connect ECONNREFUSED" in error_message:
            return {
                "success": False, 
                "message": "连接失败: 无法连接到指定的服务器", 
                "detail": error_message,
                "suggestion": "请检查 Base URL 是否正确，以及网络连接是否正常"
            }
        elif "timeout" in error_message.lower():
            return {
                "success": False, 
                "message": "连接超时: 服务器响应时间过长", 
                "detail": error_message,
                "suggestion": "请检查网络连接，或稍后重试"
            }
        elif "SSL" in error_message or "certificate" in error_message.lower():
            return {
                "success": False, 
                "message": "SSL 证书错误: 无法建立安全连接", 
                "detail": error_message,
                "suggestion": "请检查系统时间是否正确，或联系 API 提供商确认证书状态"
            }
        elif "HTTP 400" in error_message and ("overdue" in error_message.lower() or "payment" in error_message.lower()):
            return {
                "success": False, 
                "message": "账户欠费或余额不足", 
                "detail": error_message,
                "suggestion": "请检查账户余额并及时充值"
            }
        elif "HTTP" in error_message:
            # 提取 HTTP 状态码
            import re
            match = re.search(r'HTTP (\d+)', error_message)
            status_code = match.group(1) if match else '未知'
            return {
                "success": False, 
                "message": f"HTTP {status_code} 错误", 
                "detail": error_message,
                "suggestion": "请检查配置是否正确，或查阅 API 文档"
            }
        
        return {
            "success": False, 
            "message": f"连接失败: {error_message[:100]}", 
            "detail": error_message,
            "suggestion": "请检查配置是否正确，或查看日志获取更多信息"
        }


@router.get("/chat/model/providers")
async def get_supported_providers():
    from app.services.llm.factory import ProviderFactory
    
    providers = ProviderFactory.get_supported_providers()
    return {"success": True, "providers": providers}


@router.get("/chat/model/default")
async def get_default_model():
    """获取系统默认模型配置"""
    from app.core.config_loader import config_loader
    
    llm_config = config_loader.get_app_config().get('llm', {})
    return {
        "success": True,
        "provider": llm_config.get('provider'),
        "model": llm_config.get('model'),
        "baseUrl": llm_config.get('baseUrl'),
        "temperature": llm_config.get('temperature'),
        "maxTokens": llm_config.get('maxTokens')
    }


@router.get("/chat/model/available")
@lru_cache(maxsize=1)
def _get_available_models_cached():
    """获取可用模型列表（带缓存）"""
    from app.core.config_loader import config_loader
    from app.models.llm_user_config import LLMUserConfig
    from sqlalchemy.orm import Session
    from app.core.database import engine

    llm_config = config_loader.get_app_config().get('llm', {})
    default_provider = llm_config.get('provider', 'custom')
    default_model = llm_config.get('model', '')

    available_models = []
    seen_model_keys = set()

    def add_model(model_info: dict):
        key = f"{model_info.get('provider', '')}:{model_info.get('name', '')}"
        if key and key not in seen_model_keys:
            seen_model_keys.add(key)
            available_models.append(model_info)

    if default_provider and default_model:
        add_model({
            "id": f"{default_provider}-{default_model}",
            "provider": default_provider,
            "providerName": "系统默认",
            "name": default_model,
            "isDefault": True
        })

    try:
        with Session(engine) as db:
            user_configs = db.query(LLMUserConfig).filter(
                LLMUserConfig.is_active == True
            ).limit(50).all()

            provider_names = {
                'custom': '自定义',
                'openai': 'OpenAI',
                'anthropic': 'Anthropic',
                'minimax': 'MiniMax',
                'ollama': 'Ollama',
                'websocket': 'WebSocket'
            }

            for config in user_configs:
                provider_label = provider_names.get(config.provider, config.provider or '自定义')
                model_info = {
                    "id": f"{config.provider}-{config.model}" if config.provider else config.model,
                    "provider": config.provider or 'custom',
                    "providerName": provider_label,
                    "name": config.model,
                    "isDefault": False,
                    "apiKey": bool(config.api_key),
                    "baseUrl": config.base_url
                }
                add_model(model_info)
    except Exception as e:
        logger.warning(f"获取用户模型配置失败: {e}")

    return available_models


async def get_available_models():
    """获取可用的模型列表

    从以下来源聚合模型：
    1. 系统配置中的默认模型
    2. 用户在数据库中保存的自定义模型配置

    使用 LRU 缓存
    """
    models = _get_available_models_cached()
    logger.info(f"[model/available] 返回 {len(models)} 个可用模型")
    return {"success": True, "models": models}


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    userId: Optional[str] = None
    formCode: Optional[str] = None
    formData: Optional[Dict[str, Any]] = None
    modelConfig: Optional[Dict[str, Any]] = None
    workflowResume: Optional[Dict[str, Any]] = None


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
                formCode=result_data.get("formCode"),
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

        if not llm_service.enabled:
            logger.error("LLM service is disabled")
            return ChatResponse(
                success=False,
                message="LLM 服务已禁用",
                method="llm_disabled"
            )

        logger.info("Attempting LLM call for intent recognition")
        try:
            intent_prompt = build_intent_prompt(messages_text, last_user_message)
            if intent_prompt:
                logger.info("Calling LLM for intent recognition")
                intent_result = llm_service._call_llm_sync(intent_prompt)
                logger.info("LLM intent result received: %s", intent_result is not None)

                if intent_result:
                    intent_data = parse_intent_result(intent_result)
                    if intent_data:
                        scene_code = intent_data.get("sceneCode")
                        form_code = intent_data.get("formCode")
                        
                        # 如果有场景编码或表单编码，说明是表单意图
                        if scene_code or form_code:
                            return ChatResponse(
                                success=True,
                                intentType="form",
                                formCode=form_code,
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
                            logger.error("[LLM Error] 未找到智能对话回复模板")
                            return ChatResponse(
                                success=False,
                                message="未找到智能对话回复模板",
                                method="llm_no_template"
                            )
                    else:
                        logger.error("[LLM Error] LLM 意图解析失败，返回结果无法解析为有效 JSON")
                        return ChatResponse(
                            success=False,
                            message="LLM 意图解析失败",
                            method="llm_parse_error"
                        )
            else:
                logger.error("[LLM Error] 未找到意图识别模板")
                return ChatResponse(
                    success=False,
                    message="未找到意图识别模板",
                    method="llm_no_prompt"
                )
        except Exception as e:
            import traceback
            error_trace = traceback.format_exc()
            logger.error("[LLM Error] LLM 调用失败: %s", str(e))
            logger.error("[LLM Error] 错误堆栈:\n%s", error_trace)
            logger.error("[LLM Error] Provider: %s, Model: %s, BaseURL: %s", 
                        llm_service.llm_config.get('provider'),
                        llm_service.llm_config.get('model'),
                        llm_service.llm_config.get('baseUrl'))
            return ChatResponse(
                success=False,
                message=f"LLM 调用失败: {str(e)}",
                method="llm_call_error"
            )

        logger.error("[LLM Error] LLM 处理流程失败，返回结果为空")
        return ChatResponse(
            success=False,
            message="LLM 处理流程失败",
            method="llm_processing_error"
        )

    except Exception as e:
        import traceback
        error_trace = traceback.format_exc()
        logger.error("[Chat Error] 处理请求时发生错误: %s", str(e))
        logger.error("[Chat Error] 错误堆栈:\n%s", error_trace)
        return ChatResponse(
            success=False,
            message=f"处理请求时发生错误: {str(e)}",
            method="chat_error"
        )


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest, db: Session = Depends(get_db)):
    async def stream_generator():
        from app.services.llm.factory import ProviderFactory
        
        start_time = time.time()
        stream_stats = ChatStreamStats()

        logger.info("=" * 60)
        logger.info("[chat/stream] 收到流式聊天请求")
        logger.info(f"[chat/stream] 时间戳: {start_time:.3f}")
        logger.info(f"[chat/stream] 消息数量: {len(request.messages)}")

        current_provider = llm_service.provider
        model_config = request.modelConfig
        
        if model_config:
            # 检查前端配置是否包含敏感信息
            has_api_key = model_config.get('apiKey') or model_config.get('api_key')
            
            if has_api_key:
                # 前端传递了完整配置（包含 API Key）
                try:
                    provider_name = model_config.get('provider', 'openai')
                    current_provider = ProviderFactory.create(provider_name, model_config)
                    logger.info(f"[chat/stream] 使用前端传递的完整模型配置: provider={provider_name}, model={model_config.get('model')}")
                except Exception as e:
                    logger.warning(f"[chat/stream] 前端配置失败，使用缓存配置: {e}")
                    model_config = None
            else:
                # 前端配置不包含 API Key（安全考虑），使用缓存配置
                logger.info(f"[chat/stream] 前端配置不包含 API Key，使用缓存配置: provider={model_config.get('provider')}, model={model_config.get('model')}")
                model_config = None
        
        # 如果没有传递模型配置或配置失败，使用缓存的配置
        if not model_config:
            # 优先使用缓存配置（避免每次查询数据库）
            cached_config = llm_service.get_cached_config(include_api_key=True)
            
            logger.info(f"[chat/stream] 缓存配置状态: enabled={cached_config.get('enabled')}")
            logger.info(f"[chat/stream] 缓存 API Key: {'已设置' if cached_config.get('api_key') else '❌ 为空'} (长度: {len(cached_config.get('api_key', ''))})")
            logger.info(f"[chat/stream] 缓存 BaseURL: {'已设置' if cached_config.get('base_url') else '❌ 为空'}")
            logger.info(f"[chat/stream] 缓存 Model: {cached_config.get('model', '未设置')}")
            
            if cached_config.get('enabled') and cached_config.get('api_key'):
                model_config = cached_config
                provider_name = model_config.get('provider', 'openai')
                current_provider = ProviderFactory.create(provider_name, model_config)
                logger.info(f"[chat/stream] 使用缓存的模型配置: provider={provider_name}, model={model_config.get('model')}")
            else:
                # 缓存配置不完整，尝试从数据库刷新
                logger.info("[chat/stream] 缓存配置不完整，尝试从数据库刷新...")
                if llm_service.refresh_config():
                    cached_config = llm_service.get_cached_config(include_api_key=True)
                    logger.info(f"[chat/stream] 刷新后状态: enabled={cached_config.get('enabled')}")
                    logger.info(f"[chat/stream] 刷新后 API Key: {'已设置' if cached_config.get('api_key') else '❌ 为空'}")
                    if cached_config.get('enabled') and cached_config.get('api_key'):
                        model_config = cached_config
                        provider_name = model_config.get('provider', 'openai')
                        current_provider = ProviderFactory.create(provider_name, model_config)
                        logger.info(f"[chat/stream] 刷新后使用数据库配置: provider={provider_name}, model={model_config.get('model')}")
                    else:
                        logger.warning("[chat/stream] 刷新后配置仍不完整，使用默认配置")
                else:
                    logger.warning("[chat/stream] 刷新配置失败，使用默认配置")

        try:
            workflow_resume = request.workflowResume
            if workflow_resume:
                execution_id = workflow_resume.get("execution_id") or workflow_resume.get("workflow_id", "")
                form_data = workflow_resume.get("form_data", {})

                if execution_id:
                    context = workflow_engine.get_context(execution_id)
                    if context and context.status == WorkflowStatus.WAITING:
                        logger.info(f"[chat/stream] 恢复工作流: {execution_id}, 用户输入: {form_data}")
                        yield thinking("🔄 正在继续工作流处理...")

                        async for event in workflow_engine.resume(execution_id, form_data):
                            yield sse(event)
                            if event.get("type") == "workflow_waiting":
                                logger.info(f"[chat/stream] 工作流再次等待用户输入: {execution_id}")
                                stream_stats.total_elapsed = time.time() - start_time
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield done_event("workflow", is_form=False, intent_data={
                                    "workflow_waiting": {
                                        "execution_id": execution_id,
                                        "waiting_form": event.get("waiting_form"),
                                        "message": event.get("message", ""),
                                    }
                                })
                                return
                            elif event.get("type") == "workflow_complete":
                                logger.info(f"[chat/stream] 工作流恢复完成: {execution_id}")
                                yield thinking("✅ 工作流执行完成")
                                stream_stats.total_elapsed = time.time() - start_time
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield sse({"type": "text_end"})
                                yield done_event("workflow", is_form=False)
                                return
                            elif event.get("type") == "workflow_failed":
                                logger.error(f"[chat/stream] 工作流恢复失败: {execution_id}: {event.get('error')}")
                                yield thinking(f"❌ 工作流执行失败: {event.get('error')}")
                                # 发送文本消息到聊天窗口，让用户看到错误信息
                                yield sse({"type": "text_start"})
                                yield sse({"type": "text", "content": f"抱歉，工具执行失败，无法完成您的请求。错误信息：{event.get('error')}"})
                                yield sse({"type": "text_end"})
                                stream_stats.total_elapsed = time.time() - start_time
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield done_event("workflow", is_form=False)
                                return

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
                logger.error("[chat/stream] LLM 服务已禁用")
                yield thinking("❌ LLM 服务已禁用")
                stream_stats.total_elapsed = time.time() - start_time
                error = create_error(
                    category=ErrorCategory.LLM.value,
                    code=ErrorCode.LLM_DISABLED,
                    message="LLM 服务已禁用",
                    level=ErrorLevel.CRITICAL.value,
                    recoverable=False,
                    recovery_hint="请在配置中启用 LLM 服务"
                )
                error_handler.emit(error)
                stream_stats.error = error.message
                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                yield sse(error.to_sse())
                return

            logger.info("[chat/stream] 开始构建意图识别 Prompt...")
            intent_prompt = build_intent_prompt(messages_text, last_user_message)
            logger.info("[chat/stream] Prompt 构建完成: %s, 长度: %d", 
                        "成功" if intent_prompt else "失败(为空)", 
                        len(intent_prompt) if intent_prompt else 0)
            _llm_error = None  # 移到 if intent_prompt 块外面，确保总是被初始化
            if intent_prompt:
                loop = asyncio.get_event_loop()
                _t0 = time.time()
                _retry_count = 0

                model_info = current_provider.model if hasattr(current_provider, 'model') else llm_service.llm_config.get("model")
                provider_info = model_config.get('provider') if model_config else llm_service.llm_config.get("provider")
                
                yield thinking("🧠 调用 LLM 进行意图识别...", result={
                    "model": model_info,
                    "provider": provider_info,
                    "temperature": llm_service.llm_config.get("temperature"),
                    "maxTokens": llm_service.llm_config.get("maxTokens"),
                    "promptLength": len(intent_prompt),
                    "isDynamic": model_config is not None
                })
                
                # 【新增】发送 prompt 内容到前端（可展开查看）
                yield reasoning(f"📥 Prompt 输入（{len(intent_prompt)} 字符）:\n\n{intent_prompt[:2000]}{'...' if len(intent_prompt) > 2000 else ''}")
                try:
                    intent_result, intent_reasoning = await loop.run_in_executor(
                        None, llm_service.call_with_provider, current_provider, intent_prompt, None, None, True
                    )
                    logger.info(f"[chat/stream] LLM 返回结果: intent_result={len(intent_result) if intent_result else 0} chars, intent_reasoning={len(intent_reasoning) if intent_reasoning else 0} chars")
                    
                    # 【新增】发送 response 内容到前端（可展开查看）
                    if intent_result:
                        response_preview = intent_result[:2000] + ('...' if len(intent_result) > 2000 else '')
                        yield reasoning(f"📤 Response 输出（{len(intent_result)} 字符）:\n\n{response_preview}")

                    if not intent_result and intent_reasoning:
                        logger.info("[chat/stream] 🔄 content 为空但 reasoning 有内容，用简化 prompt 重试一次")
                        _retry_count = 1
                        yield thinking("🔄 模型响应格式异常，正在重试...", result={
                            "retry": True,
                            "originalElapsed": round(time.time() - _t0, 2)
                        })
                        retry_prompt = intent_prompt + "\n\n---\n**重要提醒**：请直接输出 JSON，不要在 JSON 之外输出任何分析文本。你的回答必须是一个合法的 JSON 对象，以 { 开头，以 } 结尾。"
                        intent_result, _ = await loop.run_in_executor(
                            None, llm_service.call_with_provider, current_provider, retry_prompt, None, None, True
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
                            # 直接从意图数据中获取场景编码和表单编码
                            form_code = intent_data.get("formCode") or intent_data.get("form_code")
                            scene_code = intent_data.get("sceneCode")
                            
                            # 根据是否有场景编码判断意图类型
                            # 如果有场景编码，走场景处理流程；否则走聊天流程
                            # 注意：有场景编码不代表一定是表单，也可能是工作流执行等
                            # intent_type 由后续场景处理结果最终决定
                            intent_type = "scene" if scene_code else "chat"

                            logger.info(f"[chat/stream] intent_type={intent_type}, form_code={form_code}, scene_code={scene_code}")
                            
                            # 【修复】优先使用 API 返回的 reasoning，如果没有则从 JSON 内容中提取
                            reasoning_content = intent_reasoning or intent_data.get("reasoning", "")
                            if reasoning_content:
                                yield thinking("🧠 分析用户意图...")
                                yield reasoning(reasoning_content)

                            # 关键逻辑：如果有场景编码，查询场景提示词并处理
                            scene_prompt_content = None
                            scene_handled = False  # 标记场景响应是否已处理完成（必须在外部初始化）
                            
                            if scene_code:
                                yield thinking(f"🔍 查询场景提示词 scene_code={scene_code}")
                                scene_prompt_content = get_scene_prompt_by_code(scene_code)
                                if scene_prompt_content:
                                    logger.info(f"[chat/stream] 成功获取场景提示词，长度={len(scene_prompt_content)}")
                                    yield thinking(f"✅ 已获取场景提示词")
                                else:
                                    # 未找到场景提示词，报错并终止
                                    error_msg = f"未找到场景 {scene_code} 的提示词配置，请检查：\n1. 场景中是否配置了 prompt_code\n2. 对应的提示词是否存在且已启用"
                                    logger.error(f"[chat/stream] {error_msg}")
                                    yield sse({"type": "error", "content": error_msg})
                                    yield done_event("intent_recognition", is_form=False, intent_data=intent_data)
                                    return

                            if scene_prompt_content and last_user_message:
                                # 【调试】打印场景提示词内容
                                logger.info(f"[chat/stream] ===== 场景提示词内容 =====")
                                logger.info(f"[chat/stream] {scene_prompt_content[:500]}...")  # 只打印前500字符
                                logger.info(f"[chat/stream] ================================")
                                
                                yield thinking(f"🧠 使用场景提示词调用大模型...")
                                
                                # 【修复】将历史上下文和当前消息合并
                                # messages_text 包含完整的对话历史，用于保持上下文连贯性
                                scene_input = f"{messages_text}\n\n用户最新消息：{last_user_message}" if messages_text else last_user_message
                                
                                # 【新增】发送场景 prompt 到前端
                                yield reasoning(f"📥 场景 Prompt 输入（{len(scene_prompt_content) + len(scene_input)} 字符）:\n\n系统提示词：{scene_prompt_content[:1000]}{'...' if len(scene_prompt_content) > 1000 else ''}\n\n对话上下文：{scene_input[:1000]}{'...' if len(scene_input) > 1000 else ''}")
                                
                                try:
                                    scene_response = llm_service.call_with_provider(current_provider, scene_input, system_prompt=scene_prompt_content)
                                    if scene_response:
                                        intent_data["sceneResponse"] = scene_response
                                        logger.info(f"[chat/stream] 场景提示词调用成功，响应长度={len(scene_response)}")
                                        yield thinking(f"✅ 场景大模型调用完成")
                                        
                                        # 【新增】发送场景 response 到前端
                                        response_preview = scene_response[:2000] + ('...' if len(scene_response) > 2000 else '')
                                        yield reasoning(f"📤 场景 Response 输出（{len(scene_response)} 字符）:\n\n{response_preview}")
                                        
                                        # 解析场景响应，检查是否需要调用工具
                                        try:
                                            import json
                                            import re
                                            
                                            # 【修复】清理 LLM 返回的 Markdown 代码块标记
                                            cleaned_response = scene_response.strip()
                                            # 移除开头的 ```json 或 ```
                                            cleaned_response = re.sub(r'^```json\s*', '', cleaned_response)
                                            cleaned_response = re.sub(r'^```\s*', '', cleaned_response)
                                            # 移除结尾的 ```
                                            cleaned_response = re.sub(r'\s*```$', '', cleaned_response)
                                            cleaned_response = cleaned_response.strip()
                                            
                                            scene_json = json.loads(cleaned_response)
                                            
                                            # 将场景数据添加到 intent_data
                                            intent_data["sceneData"] = scene_json
                                            
                                            # 【新增】支持 action 字段的场景响应格式
                                            action = scene_json.get("action")
                                            
                                            if action:
                                                # 保存 action 到 intent_data，供后续逻辑使用
                                                intent_data["action"] = action
                                                logger.info(f"[chat/stream] 场景响应 action={action}")
                                                
                                                if action == "ask_user":
                                                    # 需要用户补充信息
                                                    missing_fields = scene_json.get("missing_fields", [])
                                                    message = scene_json.get("message", "")
                                                    
                                                    # 显示消息给用户
                                                    if message:
                                                        yield sse({"type": "text_start"})
                                                        yield sse({"type": "text", "content": message})
                                                        yield sse({"type": "text_end"})
                                                    
                                                    # 标记需要用户响应
                                                    intent_data["needUserResponse"] = True
                                                    intent_data["missingFields"] = missing_fields
                                                    logger.info(f"[chat/stream] 场景要求用户补充信息: {missing_fields}")
                                                    
                                                    # 发送场景数据并返回
                                                    yield sse({"type": "scene_data", "content": scene_response})
                                                    stream_stats.total_elapsed = time.time() - start_time
                                                    yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                                    yield intent_event(intent_type, "awaiting_input", intent_data, is_form=False)
                                                    yield done_event(intent_type, is_form=False, intent_data=intent_data)
                                                    return
                                                
                                                elif action == "call_tool":
                                                    # 需要调用工具
                                                    tool_name = scene_json.get("tool_name")
                                                    tool_args = scene_json.get("tool_args", {})
                                                    message = scene_json.get("message", "")
                                                    
                                                    # 显示消息
                                                    if message:
                                                        yield sse({"type": "text_start"})
                                                        yield sse({"type": "text", "content": message})
                                                        yield sse({"type": "text_end"})
                                                    
                                                    # 添加工具调用到 intent_data
                                                    if "tool_calls" not in intent_data:
                                                        intent_data["tool_calls"] = []
                                                    intent_data["tool_calls"].append({
                                                        "name": tool_name,
                                                        "arguments": tool_args
                                                    })
                                                    logger.info(f"[chat/stream] 场景要求调用工具: {tool_name}")
                                                    # 标记场景已处理，但允许继续执行工具调用
                                                    scene_handled = True
                                                
                                                elif action == "generate_form":
                                                    # 生成表单
                                                    form_code = scene_json.get("formCode")
                                                    extracted_fields = scene_json.get("extractedFields", {})
                                                    message = scene_json.get("message", "")
                                                    
                                                    # 显示消息
                                                    if message:
                                                        yield sse({"type": "text_start"})
                                                        yield sse({"type": "text", "content": message})
                                                        yield sse({"type": "text_end"})
                                                    
                                                    # 设置 formCode 和 extractedFields
                                                    if form_code:
                                                        intent_data["formCode"] = form_code
                                                        intent_data["detectedFormCode"] = form_code
                                                    if extracted_fields:
                                                        intent_data["extractedFields"] = extracted_fields
                                                    
                                                    logger.info(f"[chat/stream] 场景要求生成表单: formCode={form_code}")
                                                    # 标记场景已处理，并且应该直接返回（不继续执行 dispatch）
                                                    scene_handled = True
                                                
                                                elif action == "validate_form":
                                                    # 表单校验
                                                    form_code = scene_json.get("formCode")
                                                    extracted_fields = scene_json.get("extractedFields", {})
                                                    validation_results = scene_json.get("validationResults", [])
                                                    message = scene_json.get("message", "")
                                                    
                                                    # 显示消息
                                                    if message:
                                                        yield sse({"type": "text_start"})
                                                        yield sse({"type": "text", "content": message})
                                                        yield sse({"type": "text_end"})
                                                    
                                                    # 输出校验结果清单（按照校验结果格式说明）
                                                    if validation_results and isinstance(validation_results, list):
                                                        yield sse({"type": "text_start"})
                                                        yield sse({"type": "text", "content": "\n**校验结果清单：**\n\n"})
                                                        
                                                        # 构建表格格式输出
                                                        table_rows = []
                                                        table_rows.append("| 字段名称 | 字段编码 | 校验值 | 校验结果 | 说明 | 优化建议 |")
                                                        table_rows.append("|---------|---------|--------|---------|------|---------|")
                                                        
                                                        for result in validation_results:
                                                            field = result.get("field", "")
                                                            field_name = result.get("fieldName", field)
                                                            value = result.get("value", "")
                                                            val_result = result.get("result", "")
                                                            reason = result.get("reason", "")
                                                            suggestion = result.get("suggestion", "")
                                                            
                                                            # 转换校验结果状态显示
                                                            result_display = {
                                                                "pass": "✅ 通过",
                                                                "warning": "⚠️ 警告",
                                                                "error": "❌ 错误"
                                                            }.get(val_result, val_result)
                                                            
                                                            table_rows.append(f"| {field_name} | {field} | {value} | {result_display} | {reason} | {suggestion} |")
                                                        
                                                        yield sse({"type": "text", "content": "\n".join(table_rows)})
                                                        yield sse({"type": "text_end"})
                                                    
                                                    # 设置 formCode、extractedFields 和 validationResults
                                                    if form_code:
                                                        intent_data["formCode"] = form_code
                                                        intent_data["detectedFormCode"] = form_code
                                                    if extracted_fields:
                                                        intent_data["extractedFields"] = extracted_fields
                                                    if validation_results:
                                                        intent_data["validationResults"] = validation_results
                                                    
                                                    logger.info(f"[chat/stream] 场景要求表单校验: formCode={form_code}, 校验项数={len(validation_results)}")
                                                    # 标记场景已处理
                                                    scene_handled = True
                                            
                                            else:
                                                # 旧格式：直接使用 message 字段
                                                message = scene_json.get("message", "")
                                                if message:
                                                    yield sse({"type": "text_start"})
                                                    yield sse({"type": "text", "content": message})
                                                    yield sse({"type": "text_end"})
                                            
                                            # 直接使用场景响应中的 needUserResponse 值
                                            need_user_response_from_scene = scene_json.get("needUserResponse", False)
                                            if need_user_response_from_scene:
                                                intent_data["needUserResponse"] = True
                                                yield thinking(f"🔍 场景提示词标记需要用户响应")
                                            else:
                                                # 检查是否有缺失字段（支持 missingInfo 和 missingFields 两种格式）
                                                missing_info = scene_json.get("missingInfo", [])
                                                missing_fields = scene_json.get("missingFields", [])
                                                
                                                # 合并两种格式的缺失字段
                                                all_missing_fields = []
                                                all_missing_items = []  # 保存完整的字典对象
                                                
                                                # 优先使用 missingFields（大模型最新返回的格式）
                                                if missing_fields and isinstance(missing_fields, list):
                                                    if len(missing_fields) > 0 and isinstance(missing_fields[0], dict):
                                                        all_missing_items = missing_fields.copy()
                                                        all_missing_fields = [str(item.get("field")) for item in missing_fields if item.get("field")]
                                                    else:
                                                        all_missing_fields = [str(f) for f in missing_fields if f]
                                                elif missing_info and isinstance(missing_info, list):
                                                    if len(missing_info) > 0 and isinstance(missing_info[0], dict):
                                                        all_missing_items = missing_info.copy()
                                                        all_missing_fields = [str(item.get("field")) for item in missing_info if item.get("field")]
                                                    else:
                                                        all_missing_fields = [str(f) for f in missing_info if f]
                                                
                                                logger.info(f"[chat/stream] 解析缺失字段: missingFields={missing_fields}, missingInfo={missing_info}, all_missing_fields={all_missing_fields}")
                                                
                                                if all_missing_fields:
                                                    yield thinking(f"🔍 发现缺失字段: {', '.join(all_missing_fields)}")
                                                    
                                                    # 检查用户输入中是否包含套餐编码
                                                    tariff_code = None
                                                    if last_user_message:
                                                        # 从用户输入中提取套餐编码（P开头后跟数字）
                                                        import re
                                                        match = re.search(r'P\d+', last_user_message)
                                                        if match:
                                                            tariff_code = match.group(0)
                                                            yield thinking(f"✅ 从用户输入中提取到套餐编码: {tariff_code}")
                                                    
                                                    # 如果找到了套餐编码，调用工具查询套餐信息
                                                    if tariff_code:
                                                        yield thinking(f"🔧 调用工具查询套餐信息: {tariff_code}")
                                                        # 添加工具调用到 intent_data
                                                        if "tool_calls" not in intent_data:
                                                            intent_data["tool_calls"] = []
                                                        intent_data["tool_calls"].append({
                                                            "name": "query_tariff_by_code",
                                                            "arguments": {"tariff_code": tariff_code}
                                                        })
                                                    else:
                                                        # 检查是否有必填字段缺失（根据场景响应中的 required=True 判断）
                                                        needs_response = False
                                                        required_fields = []
                                                        
                                                        # 从合并后的 all_missing_items 中提取必填字段名（确保是字符串）
                                                        for item in all_missing_items:
                                                            if isinstance(item, dict):
                                                                field_name = item.get("field")
                                                                if field_name and item.get("required", False):
                                                                    required_fields.append(str(field_name))
                                                                    needs_response = True
                                                        
                                                        # 如果没有必填字段，使用所有缺失字段
                                                        if not needs_response and all_missing_fields:
                                                            needs_response = True
                                                            required_fields = all_missing_fields.copy()
                                                        
                                                        logger.info(f"[chat/stream] 检查必填字段: needs_response={needs_response}, required_fields={required_fields}")
                                                        
                                                        if needs_response or all_missing_fields:
                                                            # 使用场景响应中的 recommendations（如果有）
                                                            user_prompt = scene_json.get("recommendations", [])
                                                            if user_prompt and isinstance(user_prompt, list) and len(user_prompt) > 0:
                                                                yield sse({"type": "text_start"})
                                                                yield sse({"type": "text", "content": str(user_prompt[0])})
                                                                yield sse({"type": "text_end"})
                                                            elif all_missing_items:
                                                                # 使用合并后的 all_missing_items 中的提示信息
                                                                found_prompt = False
                                                                for item in all_missing_items:
                                                                    if isinstance(item, dict):
                                                                        field_name = item.get("field")
                                                                        is_required = item.get("required", False)
                                                                        if is_required or (field_name and str(field_name) in required_fields):
                                                                            prompt_text = item.get("description", item.get("label", item.get("message", "请提供必要信息")))
                                                                            yield sse({"type": "text_start"})
                                                                            yield sse({"type": "text", "content": str(prompt_text)})
                                                                            yield sse({"type": "text_end"})
                                                                            found_prompt = True
                                                                            break
                                                                if not found_prompt:
                                                                    # 回退到默认提示
                                                                    yield sse({"type": "text_start"})
                                                                    yield sse({"type": "text", "content": f"请提供以下必要信息：{', '.join(required_fields)}"})
                                                                    yield sse({"type": "text_end"})
                                                            else:
                                                                # 默认提示
                                                                yield sse({"type": "text_start"})
                                                                yield sse({"type": "text", "content": f"请提供以下必要信息：{', '.join(required_fields)}"})
                                                                yield sse({"type": "text_end"})
                                                        
                                                            # 标记需要用户响应（只要有缺失字段就需要用户响应）
                                                            intent_data["needUserResponse"] = True
                                                            intent_data["missingRequiredFields"] = required_fields
                                                            logger.info(f"[chat/stream] 设置 needUserResponse=True")
                                            
                                            # 发送场景数据给前端
                                            yield sse({"type": "scene_data", "content": scene_response})
                                            
                                        except json.JSONDecodeError:
                                            # 如果不是 JSON 格式，直接作为文本发送
                                            yield sse({"type": "text_start"})
                                            yield sse({"type": "text", "content": scene_response})
                                            yield sse({"type": "text_end"})
                                            # 设置需要用户响应，因为场景回复是对话式的
                                            intent_data["needUserResponse"] = True
                                    else:
                                        yield thinking(f"⚠️ 场景大模型返回为空")
                                except Exception as e:
                                    logger.exception(f"[chat/stream] 场景大模型调用失败: {e}")
                                    yield thinking(f"❌ 场景大模型调用失败: {str(e)}")

                            # 检查是否需要用户响应（在工具调用和表单生成之前）
                            need_user_response = intent_data.get("needUserResponse", False)
                            if need_user_response:
                                logger.info(f"[chat/stream] 需要用户响应，等待用户输入...")
                                yield thinking(f"⏳ 等待用户提供必要信息...")
                                stream_stats.total_elapsed = time.time() - start_time
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield intent_event(intent_type, "awaiting_input", intent_data, is_form=False)
                                yield done_event(intent_type, is_form=False, intent_data=intent_data)
                                return

                            # 执行工具调用（支持流式工作流步骤）
                            tool_results = []
                            extracted = {}
                            workflow_waiting = None
                            
                            tool_calls = intent_data.get("tool_calls", [])
                            if tool_calls:
                                for tc in tool_calls:
                                    tool_name = tc.get("name")
                                    tool_args = tc.get("arguments", {})
                                    
                                    # 特殊处理工作流执行工具，实时发送步骤事件
                                    if tool_name == "execute_workflow":
                                        yield thinking(f"🔄 开始执行工作流: {tool_args.get('workflow_code', 'unknown')}")
                                        workflow_result = _execute_workflow_with_stream(tool_args.get("workflow_code"), tool_args.get("inputs", {}))
                                        async for event in workflow_result:
                                            if event["type"] == "workflow_step":
                                                # 发送工作流步骤事件到前端（会被 useChatStream.js 处理）
                                                yield sse({
                                                    "type": event["step_type"],
                                                    "step": event["step"],
                                                    "name": event["name"],
                                                    "result": event.get("result"),
                                                    "error": event.get("error")
                                                })
                                            elif event["type"] == "workflow_waiting":
                                                workflow_waiting = {
                                                    "execution_id": event.get("execution_id"),
                                                    "waiting_form": event.get("waiting_form"),
                                                    "message": event.get("message"),
                                                }
                                                tool_results.append({
                                                    "name": tool_name,
                                                    "success": True,
                                                    "waiting": True
                                                })
                                            elif event["type"] == "workflow_result":
                                                tool_results.append({
                                                    "name": tool_name,
                                                    "success": event.get("success", True),
                                                    "fields": list(event.get("outputs", {}).keys())
                                                })
                                                if event.get("success"):
                                                    extracted.update(event.get("outputs", {}))
                                                else:
                                                    extracted["error"] = event.get("error")
                                            elif event["type"] == "workflow_complete":
                                                yield sse({"type": "workflow_complete", "workflow_id": event.get("workflow_id")})
                                    else:
                                        # 普通工具调用
                                        hub = get_toolhub()
                                        if hub.has_tool(tool_name):
                                            exec_result = await hub.execute(tool_name, tool_args)
                                            if exec_result.get("success"):
                                                tool_result = exec_result.get("result", {})
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
                                        else:
                                            tool_results.append({"name": tool_name, "success": False, "error": "工具不存在"})

                            intent_data["extractedFields"] = extracted

                            if workflow_waiting:
                                message = workflow_waiting.get("message", "请提供以下信息")
                                execution_id = workflow_waiting.get("execution_id", "")

                                yield thinking(f"⏳ {message}")
                                yield sse({"type": "text_start"})
                                yield sse({"type": "text", "content": message})
                                yield sse({"type": "text_end"})
                                intent_data["workflow_waiting"] = workflow_waiting
                                stream_stats.total_elapsed = time.time() - start_time
                                yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                yield intent_event("workflow", "awaiting_input", intent_data, is_form=False)
                                yield done_event("workflow", is_form=False, intent_data=intent_data)
                                return

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

                            # 【关键】如果场景响应是 call_tool action，工具调用后根据是否有表单编码决定是否生成表单
                            if scene_handled and intent_data.get("action") == "call_tool":
                                has_form_code = bool(intent_data.get("formCode") or intent_data.get("detectedFormCode") or intent_data.get("form_code"))
                                
                                if has_form_code:
                                    logger.info(f"[chat/stream] 工具调用完成，准备生成表单")
                                    
                                    # 检查是否有工具调用成功
                                    success_count = sum(1 for r in tool_results if r["success"])
                                    failed_count = sum(1 for r in tool_results if not r["success"])
                                    
                                    if failed_count > 0:
                                        yield thinking(f"⚠️ {failed_count} 个工具调用失败，将生成空表单供手动填写")
                                    
                                    # 设置 intent_type 为 form，让 FormHandler 处理表单生成
                                    intent_type = "form"
                                    logger.info(f"[chat/stream] 设置 intent_type=form，准备通过 FormHandler 处理")
                                else:
                                    # 非表单场景（如工作流执行），检查是否有工具调用失败
                                    success_count = sum(1 for r in tool_results if r["success"])
                                    failed_count = sum(1 for r in tool_results if not r["success"])
                                    
                                    if failed_count > 0:
                                        # 收集所有错误信息
                                        error_messages = []
                                        for r in tool_results:
                                            if not r["success"]:
                                                error_messages.append(f"• {r['name']}: {r.get('error', '未知错误')}")
                                        error_msg = "\n".join(error_messages)
                                        logger.error(f"[chat/stream] 工具调用失败: {error_msg}")

                                        # 发送具体的错误信息给前端
                                        yield sse({"type": "text_start"})
                                        yield sse({"type": "text", "content": f"工具执行失败:\n{error_msg}"})
                                        yield sse({"type": "text_end"})
                                        
                                        # 更新统计信息并结束对话
                                        stream_stats.total_elapsed = time.time() - start_time
                                        yield sse({"type": "stats", "content": stream_stats.to_dict()})
                                        yield done_event("chat", is_form=False)
                                        return
                                    else:
                                        logger.info(f"[chat/stream] 工具调用完成，无需生成表单（无表单编码）")
                                        # 非表单场景（如工作流执行），走正常对话流程
                                        if intent_type != "chat":
                                            intent_type = "chat"

                            # 【关键】如果场景响应已经处理完成（generate_form action），通过 FormHandler 处理（显示处理步骤）
                            if scene_handled and intent_data.get("action") == "generate_form":
                                logger.info(f"[chat/stream] 场景响应已处理完成，准备通过 FormHandler 生成表单")
                                # 设置 intent_type 为 form，让 FormHandler 处理表单生成（显示处理步骤）
                                intent_type = "form"
                                logger.info(f"[chat/stream] 设置 intent_type=form，准备通过 FormHandler 处理")
                                # 继续执行后续逻辑，不直接返回
                            
                            # 【调试】打印 intent_type 和 intent_data
                            logger.info(f"[chat/stream] 准备分发意图: intent_type={intent_type}, action={intent_data.get('action')}")

                            form_code = intent_data.get("detectedFormCode") or intent_data.get("formCode") or intent_data.get("form_code")
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

                            # 注意：不再用 request.formCode 覆盖 intent_data
                            # formCode 应由 LLM 意图识别或 handler 决定，避免场景编码覆盖表单编码
                            # if request.formCode:
                            #     intent_data["form_code"] = request.formCode
                            if request.formData:
                                intent_data["form_data"] = request.formData

                            # 获取表单名称用于提示
                            form_name = ""
                            if form_code and form_code in ontologies:
                                form_name = ontologies[form_code].get("formName", form_code)
                            
                            # 根据 intent_type 发送相应的处理提示
                            if intent_type == "form":
                                yield thinking(f"正在为你生成 {form_name or form_code} 表单...")
                            else:
                                yield thinking(f"正在为你处理...")
                            
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
                                stream_stats=stream_stats,
                                error_info=intent_data.get("error_info")
                            )
                            async for chunk in get_intent_registry().dispatch(intent_type, ctx):
                                yield chunk
                            return
                        else:
                            _llm_error = "JSON 解析失败"
                            logger.warning(f"[chat/stream] JSON 解析失败，尝试重试")
                            
                            # 【新增】JSON 解析失败时进行重试
                            _retry_count = 1
                            while _retry_count <= 2 and not intent_data:
                                yield thinking(f"🔄 JSON 解析失败，正在重试 ({_retry_count}/2)...", result={
                                    "retry": True,
                                    "retryCount": _retry_count,
                                    "elapsed": round(time.time() - _t0, 2) if '_t0' in dir() else 0
                                })
                                
                                # 构建重试提示词，强调返回完整 JSON
                                retry_prompt = intent_prompt + "\n\n---\n**重要提醒**：请返回完整的 JSON 对象，确保所有引号和括号都正确闭合。你的响应必须是一个合法的 JSON，不能截断。"
                                
                                intent_result, _ = await loop.run_in_executor(
                                    None, llm_service._call_llm_sync_with_reasoning, retry_prompt
                                )
                                
                                if intent_result:
                                    intent_data = parse_intent_result(intent_result)
                                
                                _retry_count += 1
                            
                            if intent_data:
                                # 重试成功，继续处理
                                logger.info("[chat/stream] ✅ JSON 解析重试成功")
                                form_code = intent_data.get("formCode") or intent_data.get("form_code")
                                scene_code = intent_data.get("sceneCode")
                                # 根据是否有场景编码判断意图类型
                                # 注意：有场景编码不代表一定是表单
                                intent_type = "scene" if scene_code else "chat"
                                
                                # 重新构建 ctx 并继续处理
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
                            
                            # 重试仍然失败
                            logger.error("[chat/stream] JSON 解析失败（已重试 %d 次）: %s", _retry_count-1, _llm_error)
                            logger.error("[chat/stream] LLM 返回内容: %s", intent_result[:500] if intent_result else "None")
                            yield thinking(f"❌ JSON 解析失败（已重试 {_retry_count-1} 次）", result={
                                "error": _llm_error,
                                "elapsed": round(time.time() - _t0, 2) if '_t0' in dir() else 0
                            })
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
                        logger.error("[chat/stream] ====== LLM 返回为空 ======")
                        logger.error("[chat/stream] 耗时: %.1fs", intent_elapsed)
                        logger.error("[chat/stream] Provider: %s, Model: %s", 
                                    llm_service.llm_config.get('provider'),
                                    llm_service.llm_config.get('model'))
                        logger.error("[chat/stream] BaseURL: %s", llm_service.llm_config.get('baseUrl'))
                        logger.error("[chat/stream] API Key: %s", "已设置" if llm_service.llm_config.get('apiKey') else "❌ 未设置")
                        logger.error("[chat/stream] ====== 请检查模型配置 ======")
                        
                        api_key_set = bool(llm_service.llm_config.get('apiKey'))
                        _llm_error = f"LLM 返回为空（耗时 {intent_elapsed:.1f}s）"
                        suggestion = "请稍后重试，或联系管理员检查 AI 服务配置"
                        if not api_key_set:
                            _llm_error = "LLM 配置不完整，请先配置 API Key"
                            suggestion = "请访问前端界面，在侧边栏'模型配置'面板中设置 API Key"
                            
                        yield thinking(f"❌ {_llm_error}", result={
                            "error": _llm_error,
                            "elapsed": round(intent_elapsed, 1) if intent_elapsed else 0,
                            "suggestion": suggestion
                        })
                        stream_stats.total_elapsed = time.time() - start_time
                        error = create_error(
                            category=ErrorCategory.LLM.value,
                            code=ErrorCode.LLM_EMPTY_RESPONSE,
                            message=_llm_error,
                            level=ErrorLevel.ERROR.value,
                            recoverable=False,
                            recovery_hint=suggestion,
                            provider=llm_service.llm_config.get('provider'),
                            model=llm_service.llm_config.get('model'),
                            base_url=llm_service.llm_config.get('baseUrl'),
                            api_key_set=api_key_set,
                            elapsed_time=intent_elapsed
                        )
                        error_handler.emit(error)
                        stream_stats.error = error.message
                        yield sse({"type": "stats", "content": stream_stats.to_dict()})
                        yield sse(error.to_sse())
                        return
                except Exception as e:
                    import traceback
                    error_trace = traceback.format_exc()
                    _llm_error = str(e)
                    
                    logger.error("[chat/stream] ====== LLM 调用失败 ======")
                    logger.error("[chat/stream] 错误类型: %s", type(e).__name__)
                    logger.error("[chat/stream] 错误信息: %s", str(e))
                    logger.error("[chat/stream] 错误堆栈:\n%s", error_trace)
                    logger.error("[chat/stream] Provider: %s, Model: %s, BaseURL: %s", 
                                llm_service.llm_config.get('provider'),
                                llm_service.llm_config.get('model'),
                                llm_service.llm_config.get('baseUrl'))
                    logger.error("[chat/stream] 用户输入: %s", last_user_message[:200])
                    
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
                        error_detail=error_trace[:1000],
                        user_input=last_user_message[:200]
                    )
                    error_handler.emit(error)
                    stream_stats.error = error.message
                    yield sse({"type": "stats", "content": stream_stats.to_dict()})
                    yield sse(error.to_sse())
                    return

            # intent_prompt 为空或 LLM 处理失败
            logger.error("[chat/stream] LLM 处理流程失败")
            yield thinking("❌ LLM 处理流程失败", result={
                "error": _llm_error or "未知错误"
            })
            stream_stats.total_elapsed = time.time() - start_time
            error = create_error(
                category=ErrorCategory.LLM.value,
                code=ErrorCode.LLM_UNAVAILABLE,
                message="LLM 处理流程失败",
                level=ErrorLevel.ERROR.value,
                recoverable=False,
                recovery_hint="请稍后重试，或联系管理员检查 AI 服务"
            )
            error_handler.emit(error)
            stream_stats.error = error.message
            yield sse({"type": "stats", "content": stream_stats.to_dict()})
            yield sse(error.to_sse())

        except Exception as e:
            import traceback
            error_trace = traceback.format_exc()
            logger.error("[chat/stream] ====== Stream 处理异常 ======")
            logger.error("[chat/stream] 错误类型: %s", type(e).__name__)
            logger.error("[chat/stream] 错误信息: %s", str(e))
            logger.error("[chat/stream] 错误堆栈:\n%s", error_trace)
            error = create_error(
                category=ErrorCategory.SYSTEM.value,
                code="ERR_STREAM_ERROR",
                message=f"Stream 处理异常: {str(e)}",
                level=ErrorLevel.ERROR.value,
                recoverable=False,
                recovery_hint="请刷新页面后重试",
                error_detail=error_trace[:1000]
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


async def _execute_workflow_with_stream(workflow_code: str, inputs: dict = None) -> AsyncGenerator[dict, None]:
    """
    执行工作流并实时返回步骤事件
    
    Args:
        workflow_code: 工作流编码
        inputs: 输入参数字典
        
    Yields:
        工作流步骤事件，包含步骤开始、完成、失败等信息
    """
    from app.services.workflow_service import WorkflowService
    from app.langchain.workflow_init import workflow_engine
    from app.langchain.workflow_converter import WorkflowConverter
    from app.core.database import SessionLocal
    
    if inputs is None:
        inputs = {}
    
    db = SessionLocal()
    try:
        workflow_result = WorkflowService.get_workflow(workflow_code, db)
        
        if not workflow_result["success"]:
            yield {
                "type": "workflow_result",
                "success": False,
                "error": f"工作流 '{workflow_code}' 不存在或已被禁用"
            }
            return
        
        workflow_data = workflow_result["data"]
        
        if not workflow_data.get("isActive", True):
            yield {
                "type": "workflow_result",
                "success": False,
                "error": f"工作流 '{workflow_code}' 已被禁用"
            }
            return
        
        workflow_def_raw = workflow_data.get("workflowData", {})
        
        if not workflow_def_raw or not workflow_def_raw.get("nodes"):
            yield {
                "type": "workflow_result",
                "success": False,
                "error": f"工作流 '{workflow_code}' 定义不完整"
            }
            return
        
        workflow_id = workflow_data.get("workflowCode", workflow_code)
        workflow_name = workflow_data.get("workflowName", "未命名工作流")
        engine_workflow = WorkflowConverter.convert(workflow_def_raw, workflow_id, workflow_name, db)
        
        workflow_def = workflow_engine._parse_workflow_definition(engine_workflow)
        workflow_engine.register_workflow(workflow_def)
        
        execution_id = None
        outputs = {}
        errors = []
        
        async for event in workflow_engine.run(workflow_id, inputs):
            event_type = event.get("type")
            
            if event_type == "workflow_start":
                execution_id = event.get("workflow_id")
                yield {
                    "type": "workflow_step",
                    "step_type": "workflow_start",
                    "step": execution_id,
                    "name": workflow_name
                }
                
            elif event_type == "step_start":
                yield {
                    "type": "workflow_step",
                    "step_type": "step_start",
                    "step": event.get("step"),
                    "name": event.get("name", "")
                }
                
            elif event_type == "step_complete":
                yield {
                    "type": "workflow_step",
                    "step_type": "step_complete",
                    "step": event.get("step"),
                    "name": event.get("name", ""),
                    "result": event.get("result")
                }
                outputs.update(event.get("result", {}))
                
            elif event_type == "step_failed":
                yield {
                    "type": "workflow_step",
                    "step_type": "step_failed",
                    "step": event.get("step"),
                    "name": event.get("name", ""),
                    "error": event.get("error")
                }
                errors.append(event.get("error", "未知错误"))
                
            elif event_type == "step_skipped":
                yield {
                    "type": "workflow_step",
                    "step_type": "step_skipped",
                    "step": event.get("step"),
                    "name": event.get("name", "")
                }
                
            elif event_type == "workflow_waiting":
                yield {
                    "type": "workflow_waiting",
                    "execution_id": event.get("workflow_id"),
                    "waiting_form": event.get("waiting_form"),
                    "message": event.get("message", "等待用户输入")
                }
                return
                
            elif event_type == "workflow_complete":
                outputs.update(event.get("outputs", {}))
                yield {
                    "type": "workflow_step",
                    "step_type": "workflow_complete",
                    "step": execution_id,
                    "name": workflow_name,
                    "result": outputs
                }
        
        if errors:
            yield {
                "type": "workflow_result",
                "success": False,
                "error": errors[0] if errors else "工作流执行失败",
                "execution_id": execution_id
            }
        else:
            yield {
                "type": "workflow_result",
                "success": True,
                "outputs": outputs,
                "execution_id": execution_id
            }
            
    except Exception as e:
        yield {
            "type": "workflow_result",
            "success": False,
            "error": f"工作流执行失败: {str(e)}"
        }
    finally:
        db.close()
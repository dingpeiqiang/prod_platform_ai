"""
Harness API - 将 Harness Engine 集成到 FastAPI

提供统一的 Agent 接口
"""

from typing import Optional, List, Dict, Any
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
import logging

from ..harness.engine import (
    HarnessEngine, 
    AgentRequest, 
    AgentResponse,
    RequestType,
    get_engine
)
from ..harness.tools import PermissionLevel

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/harness", tags=["Harness"])


# ============== 请求/响应模型 ==============

class HarnessRequest(BaseModel):
    """Harness 请求"""
    request_type: str = Field(..., description="请求类型: form_recognition, field_extraction, form_validation, general_chat, tool_call")
    user_input: str = Field(..., description="用户输入")
    session_id: Optional[str] = Field(None, description="会话ID")
    user_id: Optional[str] = Field(None, description="用户ID")
    user_level: str = Field("public", description="用户权限级别: public, authenticated, admin")
    form_code: Optional[str] = Field(None, description="表单编码")
    form_data: Optional[Dict[str, Any]] = Field(None, description="表单数据")
    schema: Optional[Dict[str, Any]] = Field(None, description="Schema定义")
    context_types: Optional[List[str]] = Field(None, description="上下文类型列表")
    metadata: Optional[Dict[str, Any]] = Field(default_factory=dict, description="元数据")
    
    class Config:
        json_schema_extra = {
            "example": {
                "request_type": "form_recognition",
                "user_input": "帮我填一个请假申请，请假3天",
                "session_id": "sess_123",
                "user_level": "public"
            }
        }


class HarnessResponse(BaseModel):
    """Harness 响应"""
    success: bool
    data: Optional[Any] = None
    error: Optional[str] = None
    warnings: List[str] = Field(default_factory=list)
    tool_calls: List[Dict] = Field(default_factory=list)
    context_used: List[str] = Field(default_factory=list)
    processing_time_ms: Optional[float] = None


class ToolListResponse(BaseModel):
    """工具列表响应"""
    tools: List[Dict]
    categories: List[str]


# ============== API 端点 ==============

@router.post("/process", response_model=HarnessResponse)
async def process_request(request: HarnessRequest):
    """
    处理 Agent 请求
    
    统一的 Agent 处理接口，支持：
    - 表单识别 (form_recognition)
    - 字段提取 (field_extraction)
    - 表单验证 (form_validation)
    - 工具调用 (tool_call)
    - 通用对话 (general_chat)
    """
    try:
        # 获取引擎实例
        engine = get_engine()
        
        # 构建 AgentRequest
        agent_request = AgentRequest(
            request_type=RequestType(request.request_type),
            user_input=request.user_input,
            session_id=request.session_id,
            user_id=request.user_id,
            user_level=PermissionLevel(request.user_level),
            form_code=request.form_code,
            form_data=request.form_data,
            schema=request.schema,
            context_types=request.context_types or ["agents_md", "system_prompt"],
            metadata=request.metadata or {}
        )
        
        # 处理请求
        response = await engine.process(agent_request)
        
        return HarnessResponse(
            success=response.success,
            data=response.data,
            error=response.error,
            warnings=response.warnings,
            tool_calls=response.tool_calls,
            context_used=response.context_used,
            processing_time_ms=response.processing_time_ms
        )
        
    except ValueError as e:
        logger.error(f"Invalid request: {e}")
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.exception("Harness process error")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/recognize", response_model=HarnessResponse)
async def recognize_form(
    user_input: str,
    session_id: Optional[str] = None
):
    """
    识别表单类型
    
    快捷接口，专门用于表单识别
    """
    return await process_request(HarnessRequest(
        request_type="form_recognition",
        user_input=user_input,
        session_id=session_id
    ))


@router.post("/extract", response_model=HarnessResponse)
async def extract_fields(
    user_input: str,
    form_code: str,
    session_id: Optional[str] = None
):
    """
    提取表单字段
    
    快捷接口，专门用于字段提取
    """
    return await process_request(HarnessRequest(
        request_type="field_extraction",
        user_input=user_input,
        form_code=form_code,
        session_id=session_id
    ))


@router.post("/validate", response_model=HarnessResponse)
async def validate_form(
    form_code: str,
    form_data: Dict[str, Any],
    schema: Optional[Dict[str, Any]] = None
):
    """
    验证表单
    
    快捷接口，专门用于表单验证
    """
    return await process_request(HarnessRequest(
        request_type="form_validation",
        user_input="",
        form_code=form_code,
        form_data=form_data,
        schema=schema
    ))


@router.get("/tools", response_model=ToolListResponse)
async def list_tools(
    category: Optional[str] = None,
    user_level: str = "public"
):
    """
    获取可用工具列表
    
    - category: 工具分类过滤 (form, validation, system, data, file, external)
    - user_level: 用户权限级别 (public, authenticated, admin)
    """
    try:
        engine = get_engine()
        tools = engine.tools
        
        if category:
            from ..harness.tools import ToolCategory
            cat = ToolCategory(category)
            result = tools.get_tools_by_category(cat)
        else:
            from ..harness.tools import PermissionLevel as PL
            result = tools.get_tools_for_context(
                "general",
                PermissionLevel(user_level)
            )
        
        return ToolListResponse(
            tools=result,
            categories=[c.value for c in ToolCategory]
        )
        
    except Exception as e:
        logger.exception("List tools error")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/tools/call")
async def call_tool(
    tool_name: str,
    parameters: Dict[str, Any],
    user_level: str = "public"
):
    """
    直接调用工具
    
    - tool_name: 工具名称
    - parameters: 工具参数
    - user_level: 用户权限级别
    """
    try:
        engine = get_engine()
        
        from ..harness.tools import PermissionLevel as PL
        result = engine.tools.execute(
            tool_name,
            PermissionLevel(user_level),
            **parameters
        )
        
        return {"success": True, "result": result}
        
    except PermissionError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        logger.exception("Call tool error")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/traces")
async def get_traces(limit: int = 100):
    """
    获取执行追踪日志
    
    - limit: 返回条数限制
    """
    try:
        engine = get_engine()
        traces = engine.get_traces()
        
        return {
            "total": len(traces),
            "traces": traces[-limit:]
        }
        
    except Exception as e:
        logger.exception("Get traces error")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/traces/clear")
async def clear_traces():
    """清空执行追踪日志"""
    try:
        engine = get_engine()
        engine.clear_traces()
        return {"success": True, "message": "Traces cleared"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def harness_health():
    """Harness 系统健康检查"""
    try:
        engine = get_engine()
        
        # 检查各组件状态
        components = {
            "context": "healthy",
            "guardrails": "healthy",
            "tools": "healthy",
        }
        
        # 统计信息
        stats = {
            "total_tools": len(engine.tools.get_all_tools()),
            "total_traces": len(engine.get_traces()),
        }
        
        return {
            "status": "healthy",
            "components": components,
            "stats": stats
        }
        
    except Exception as e:
        logger.exception("Harness health check error")
        return {
            "status": "unhealthy",
            "error": str(e)
        }


@router.post("/reload")
async def reload_config():
    """重新加载配置"""
    try:
        engine = get_engine()
        engine.reload()
        return {"success": True, "message": "Configuration reloaded"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

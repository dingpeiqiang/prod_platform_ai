# MCP Tools API 路由
# 提供 MCP 工具的查询和调用接口

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
from app.mcp_tools import get_toolhub

router = APIRouter(prefix="/api/v1/mcp", tags=["mcp"])


class ToolCallRequest(BaseModel):
    tool_name: str
    arguments: Optional[Dict[str, Any]] = None


class ToolCallResponse(BaseModel):
    success: bool
    result: Optional[Any] = None
    error: Optional[str] = None


@router.get("/tools")
async def list_tools():
    """
    列出所有可用的 MCP 工具
    """
    hub = get_toolhub()
    tools = hub.list_tools()
    categories = hub.get_categories()
    
    return {
        "success": True,
        "tools": tools,
        "categories": categories,
        "total": len(tools)
    }


@router.get("/tools/{tool_name}")
async def get_tool(tool_name: str):
    """
    获取指定工具的详细信息
    """
    hub = get_toolhub()
    tool = hub.get_tool(tool_name)
    
    if not tool:
        raise HTTPException(status_code=404, detail=f"工具 '{tool_name}' 不存在")
    
    return {
        "success": True,
        "tool": tool.to_mcp_dict()
    }


@router.post("/tools/call", response_model=ToolCallResponse)
async def call_tool(request: ToolCallRequest):
    """
    调用指定的 MCP 工具
    """
    hub = get_toolhub()
    
    if not hub.has_tool(request.tool_name):
        return ToolCallResponse(
            success=False,
            error=f"工具 '{request.tool_name}' 不存在"
        )
    
    arguments = request.arguments or {}
    result = hub.execute_sync(request.tool_name, arguments)
    
    return ToolCallResponse(
        success=result.get("success", False),
        result=result.get("result"),
        error=result.get("error")
    )


@router.get("/tools/schemas")
async def get_tool_schemas():
    """
    获取所有工具的 schema（用于 LLM Prompt 注入）
    """
    hub = get_toolhub()
    return {
        "success": True,
        "schemas": hub.get_tool_schemas_for_llm()
    }


@router.get("/status")
async def get_mcp_status():
    """
    获取 MCP 系统状态
    """
    hub = get_toolhub()
    return {
        "success": True,
        "tool_count": hub.get_tool_count(),
        "categories": hub.get_categories(),
        "tools": [
            {"name": t.name, "category": t.category}
            for t in hub.get_all_tools()
        ]
    }

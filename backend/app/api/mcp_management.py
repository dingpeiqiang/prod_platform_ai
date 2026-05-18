# MCP Management API
# 提供 MCP 工具的管理、监控、测试接口

from fastapi import APIRouter, Query, Depends
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from app.mcp_tools import get_toolhub
from app.core.database import get_db
from app.models.mcp_call_log import MCPCallLog, MCPToolStats
from datetime import datetime, timedelta
import time
import logging
import json

router = APIRouter(prefix="/api/v1/mcp-management", tags=["mcp-management"])
logger = logging.getLogger("mcp_management")


@router.get("/tools")
async def list_mcp_tools(category: Optional[str] = None, db: Session = Depends(get_db)):
    """获取 MCP 工具列表（增强版，包含统计信息）"""
    hub = get_toolhub()
    tools = hub.list_tools()
    
    # 过滤分类
    if category:
        tools = [t for t in tools if t.get("metadata", {}).get("category") == category]
    
    # 从数据库获取最近的统计数据（最近 7 天）
    seven_days_ago = datetime.now() - timedelta(days=7)
    enriched_tools = []
    
    for tool in tools:
        tool_name = tool["name"]
        
        # 查询该工具的统计
        stats_query = db.query(
            MCPCallLog.tool_name,
            MCPCallLog.success,
            MCPCallLog.execution_time_ms
        ).filter(
            MCPCallLog.tool_name == tool_name,
            MCPCallLog.timestamp >= seven_days_ago
        ).all()
        
        total_calls = len(stats_query)
        success_calls = sum(1 for s in stats_query if s.success)
        failed_calls = total_calls - success_calls
        avg_response_time = (
            sum(s.execution_time_ms for s in stats_query if s.execution_time_ms) / total_calls
            if total_calls > 0 else 0
        )
        
        enriched_tools.append({
            **tool,
            "stats": {
                "total_calls": total_calls,
                "success_calls": success_calls,
                "failed_calls": failed_calls,
                "avg_response_time_ms": round(avg_response_time, 2)
            }
        })
    
    return {
        "success": True,
        "tools": enriched_tools,
        "total": len(enriched_tools)
    }


@router.get("/stats")
async def get_mcp_stats(db: Session = Depends(get_db)):
    """获取 MCP 工具整体统计"""
    hub = get_toolhub()
    
    total_tools = hub.get_tool_count()
    categories = hub.get_categories()
    
    # 从数据库查询总体统计（最近 7 天）
    seven_days_ago = datetime.now() - timedelta(days=7)
    
    total_calls = db.query(MCPCallLog).filter(
        MCPCallLog.timestamp >= seven_days_ago
    ).count()
    
    total_success = db.query(MCPCallLog).filter(
        MCPCallLog.timestamp >= seven_days_ago,
        MCPCallLog.success == True
    ).count()
    
    total_failed = total_calls - total_success
    
    # 最近日志数量（最近 100 条）
    recent_logs_count = db.query(MCPCallLog).order_by(
        MCPCallLog.timestamp.desc()
    ).limit(100).count()
    
    return {
        "success": True,
        "data": {
            "total_tools": total_tools,
            "categories": categories,
            "total_calls": total_calls,
            "success_calls": total_success,
            "failed_calls": total_failed,
            "success_rate": (total_success / total_calls * 100) if total_calls > 0 else 0,
            "recent_logs_count": recent_logs_count
        }
    }


@router.post("/tools/{tool_name}/test")
async def test_mcp_tool(tool_name: str, arguments: Dict[str, Any] = {}, db: Session = Depends(get_db)):
    """测试 MCP 工具执行"""
    hub = get_toolhub()
    
    if not hub.has_tool(tool_name):
        return {
            "success": False,
            "error": f"工具 '{tool_name}' 不存在"
        }
    
    start_time = time.time()
    try:
        result = hub.execute_sync(tool_name, arguments)
        elapsed_ms = (time.time() - start_time) * 1000
        
        # 记录调用到数据库
        _record_call_to_db(db, tool_name, result.get("success", False), elapsed_ms, arguments, result)
        
        return {
            **result,
            "execution_time_ms": round(elapsed_ms, 2)
        }
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        _record_call_to_db(db, tool_name, False, elapsed_ms, arguments, None, str(e))
        
        return {
            "success": False,
            "error": str(e),
            "execution_time_ms": round(elapsed_ms, 2)
        }


@router.get("/logs")
async def get_execution_logs(
    tool_name: Optional[str] = None,
    limit: int = Query(default=100, le=500),
    db: Session = Depends(get_db)
):
    """获取工具执行日志"""
    query = db.query(MCPCallLog)
    
    if tool_name:
        query = query.filter(MCPCallLog.tool_name == tool_name)
    
    # 按时间倒序，返回最近的 limit 条
    logs = query.order_by(MCPCallLog.timestamp.desc()).limit(limit).all()
    
    return {
        "success": True,
        "logs": [log.to_dict() for log in logs],
        "total": query.count()
    }


@router.get("/categories")
async def get_mcp_categories():
    """获取 MCP 工具分类列表"""
    hub = get_toolhub()
    categories = hub.get_categories()
    
    # 统计每个分类的工具数量
    category_stats = []
    for cat in categories:
        tools = hub.get_tools_by_category(cat)
        category_stats.append({
            "code": cat,
            "name": _get_category_display_name(cat),
            "count": len(tools)
        })
    
    return {
        "success": True,
        "categories": category_stats
    }


# ========== 内部辅助函数 ==========

def _record_call_to_db(
    db: Session,
    tool_name: str,
    success: bool,
    elapsed_ms: float,
    arguments: Dict[str, Any] = None,
    result: Dict[str, Any] = None,
    error: str = None
):
    """记录工具调用到数据库"""
    try:
        # 获取工具分类
        hub = get_toolhub()
        tool_info = hub.get_tool(tool_name)
        tool_category = tool_info.get("metadata", {}).get("category") if tool_info else None
        
        # 创建日志记录
        log_entry = MCPCallLog(
            tool_name=tool_name,
            tool_category=tool_category,
            success=success,
            execution_time_ms=round(elapsed_ms, 2),
            error_message=error,
            request_args=json.dumps(arguments) if arguments else None,
            response_data=json.dumps(result) if result else None
        )
        
        db.add(log_entry)
        db.commit()
        
        logger.info(f"MCP call logged: {tool_name}, success={success}, time={elapsed_ms:.2f}ms")
        
    except Exception as e:
        logger.error(f"Failed to log MCP call: {e}")
        db.rollback()


def _get_category_display_name(category: str) -> str:
    """获取分类显示名称"""
    category_names = {
        "form": "表单工具",
        "kb": "知识库工具",
        "llm": "LLM 工具",
        "system": "系统工具",
        "tariff": "资费工具",
        "general": "通用工具"
    }
    return category_names.get(category, category)


# ========== 外部工具管理 API ==========

@router.get("/external-tools")
async def list_external_tools(db: Session = Depends(get_db)):
    """获取外部 API 工具列表"""
    from app.models.mcp_call_log import MCPToolDefinition
    
    tools = db.query(MCPToolDefinition).filter(
        MCPToolDefinition.config.isnot(None)  # 有 config 表示是外部工具
    ).all()
    
    return {
        "success": True,
        "tools": [tool.to_dict() for tool in tools],
        "total": len(tools)
    }


@router.post("/external-tools")
async def create_external_tool(tool_data: Dict[str, Any], db: Session = Depends(get_db)):
    """创建外部 API 工具"""
    from app.models.mcp_call_log import MCPToolDefinition
    from app.mcp_tools.tool_registry_manager import ToolRegistryManager
    
    try:
        # 检查是否已存在
        existing = db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_data["tool_name"]
        ).first()
        
        if existing:
            return {"success": False, "error": f"工具 '{tool_data['tool_name']}' 已存在"}
        
        # 创建新记录
        new_tool = MCPToolDefinition(
            tool_name=tool_data["tool_name"],
            tool_code=tool_data.get("tool_code"),
            description=tool_data.get("description"),
            category=tool_data.get("category", "external"),
            is_enabled=tool_data.get("is_enabled", True),
            is_public=tool_data.get("is_public", True),
            input_schema=tool_data.get("input_schema"),
            output_schema=tool_data.get("output_schema"),
            config=tool_data.get("config"),
            metadata=tool_data.get("metadata")
        )
        
        db.add(new_tool)
        db.commit()
        
        # 如果启用，重新加载外部工具
        if new_tool.is_enabled:
            manager = ToolRegistryManager(db)
            manager.sync_tools_from_database()
        
        logger.info(f"Created external tool: {new_tool.tool_name}")
        
        return {
            "success": True,
            "tool": new_tool.to_dict()
        }
        
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to create external tool: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@router.put("/external-tools/{tool_name}")
async def update_external_tool(tool_name: str, tool_data: Dict[str, Any], db: Session = Depends(get_db)):
    """更新外部 API 工具"""
    from app.models.mcp_call_log import MCPToolDefinition
    from app.mcp_tools.tool_registry_manager import ToolRegistryManager
    
    tool = db.query(MCPToolDefinition).filter(
        MCPToolDefinition.tool_name == tool_name
    ).first()
    
    if not tool:
        return {"success": False, "error": f"工具 '{tool_name}' 不存在"}
    
    try:
        # 更新字段
        if "description" in tool_data:
            tool.description = tool_data["description"]
        if "category" in tool_data:
            tool.category = tool_data["category"]
        if "is_enabled" in tool_data:
            tool.is_enabled = tool_data["is_enabled"]
        if "is_public" in tool_data:
            tool.is_public = tool_data["is_public"]
        if "input_schema" in tool_data:
            tool.input_schema = tool_data["input_schema"]
        if "output_schema" in tool_data:
            tool.output_schema = tool_data["output_schema"]
        if "config" in tool_data:
            tool.config = tool_data["config"]
        if "metadata" in tool_data:
            tool.metadata = tool_data["metadata"]
        
        db.commit()
        
        # 重新加载外部工具
        manager = ToolRegistryManager(db)
        manager.sync_tools_from_database()
        
        logger.info(f"Updated external tool: {tool_name}")
        
        return {
            "success": True,
            "tool": tool.to_dict()
        }
        
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to update external tool: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@router.delete("/external-tools/{tool_name}")
async def delete_external_tool(tool_name: str, db: Session = Depends(get_db)):
    """删除外部 API 工具"""
    from app.models.mcp_call_log import MCPToolDefinition
    
    tool = db.query(MCPToolDefinition).filter(
        MCPToolDefinition.tool_name == tool_name
    ).first()
    
    if not tool:
        return {"success": False, "error": f"工具 '{tool_name}' 不存在"}
    
    try:
        db.delete(tool)
        db.commit()
        
        logger.info(f"Deleted external tool: {tool_name}")
        
        return {"success": True, "message": f"工具 '{tool_name}' 已删除"}
        
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to delete external tool: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@router.post("/external-tools/{tool_name}/toggle")
async def toggle_external_tool(tool_name: str, data: Dict[str, bool], db: Session = Depends(get_db)):
    """切换外部 API 工具启用状态"""
    from app.models.mcp_call_log import MCPToolDefinition
    from app.mcp_tools.tool_registry_manager import ToolRegistryManager
    
    tool = db.query(MCPToolDefinition).filter(
        MCPToolDefinition.tool_name == tool_name
    ).first()
    
    if not tool:
        return {"success": False, "error": f"工具 '{tool_name}' 不存在"}
    
    try:
        enabled = data.get("enabled", not tool.is_enabled)
        old_status = tool.is_enabled
        tool.is_enabled = enabled
        db.commit()
        
        # 重新加载外部工具
        if enabled != old_status:
            manager = ToolRegistryManager(db)
            manager.sync_tools_from_database()
        
        logger.info(f"Toggled tool {tool_name}: {old_status} -> {enabled}")
        
        return {
            "success": True,
            "tool": tool.to_dict()
        }
        
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to toggle external tool: {e}", exc_info=True)
        return {"success": False, "error": str(e)}

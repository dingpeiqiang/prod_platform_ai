# MCP Management API
# 提供 MCP 工具的管理、监控、测试接口

from fastapi import APIRouter, Query
from typing import Dict, Any, List, Optional
from app.mcp_tools import get_toolhub
import time
import logging

router = APIRouter(prefix="/api/v1/mcp-management", tags=["mcp-management"])
logger = logging.getLogger("mcp_management")

# 内存存储调用统计（生产环境应使用 Redis/数据库）
_call_stats: Dict[str, Dict[str, Any]] = {}
_execution_logs: List[Dict[str, Any]] = []
MAX_LOG_ENTRIES = 1000


@router.get("/tools")
async def list_mcp_tools(category: Optional[str] = None):
    """获取 MCP 工具列表（增强版，包含统计信息）"""
    hub = get_toolhub()
    tools = hub.list_tools()
    
    # 过滤分类
    if category:
        tools = [t for t in tools if t.get("metadata", {}).get("category") == category]
    
    # 附加统计信息
    enriched_tools = []
    for tool in tools:
        tool_name = tool["name"]
        stats = _call_stats.get(tool_name, {
            "total_calls": 0,
            "success_calls": 0,
            "failed_calls": 0,
            "avg_response_time_ms": 0
        })
        enriched_tools.append({
            **tool,
            "stats": stats
        })
    
    return {
        "success": True,
        "tools": enriched_tools,
        "total": len(enriched_tools)
    }


@router.get("/stats")
async def get_mcp_stats():
    """获取 MCP 工具整体统计"""
    hub = get_toolhub()
    
    total_tools = hub.get_tool_count()
    categories = hub.get_categories()
    
    # 计算总体统计
    total_calls = sum(s["total_calls"] for s in _call_stats.values())
    total_success = sum(s["success_calls"] for s in _call_stats.values())
    total_failed = sum(s["failed_calls"] for s in _call_stats.values())
    
    return {
        "success": True,
        "data": {
            "total_tools": total_tools,
            "categories": categories,
            "total_calls": total_calls,
            "success_calls": total_success,
            "failed_calls": total_failed,
            "success_rate": (total_success / total_calls * 100) if total_calls > 0 else 0,
            "recent_logs_count": len(_execution_logs)
        }
    }


@router.post("/tools/{tool_name}/test")
async def test_mcp_tool(tool_name: str, arguments: Dict[str, Any] = {}):
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
        
        # 记录调用统计
        _record_call(tool_name, result.get("success", False), elapsed_ms)
        
        return {
            **result,
            "execution_time_ms": round(elapsed_ms, 2)
        }
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        _record_call(tool_name, False, elapsed_ms, str(e))
        
        return {
            "success": False,
            "error": str(e),
            "execution_time_ms": round(elapsed_ms, 2)
        }


@router.get("/logs")
async def get_execution_logs(
    tool_name: Optional[str] = None,
    limit: int = Query(default=100, le=500)
):
    """获取工具执行日志"""
    logs = _execution_logs
    
    if tool_name:
        logs = [log for log in logs if log["tool_name"] == tool_name]
    
    # 按时间倒序，返回最近的 limit 条
    logs = sorted(logs, key=lambda x: x["timestamp"], reverse=True)[:limit]
    
    return {
        "success": True,
        "logs": logs,
        "total": len(logs)
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

def _record_call(tool_name: str, success: bool, elapsed_ms: float, error: str = None):
    """记录工具调用"""
    # 更新统计
    if tool_name not in _call_stats:
        _call_stats[tool_name] = {
            "total_calls": 0,
            "success_calls": 0,
            "failed_calls": 0,
            "total_response_time_ms": 0,
            "avg_response_time_ms": 0
        }
    
    stats = _call_stats[tool_name]
    stats["total_calls"] += 1
    if success:
        stats["success_calls"] += 1
    else:
        stats["failed_calls"] += 1
    
    stats["total_response_time_ms"] += elapsed_ms
    stats["avg_response_time_ms"] = stats["total_response_time_ms"] / stats["total_calls"]
    
    # 记录日志
    log_entry = {
        "timestamp": time.time(),
        "tool_name": tool_name,
        "success": success,
        "execution_time_ms": round(elapsed_ms, 2),
        "error": error
    }
    
    _execution_logs.append(log_entry)
    
    # 限制日志数量
    if len(_execution_logs) > MAX_LOG_ENTRIES:
        _execution_logs.pop(0)


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

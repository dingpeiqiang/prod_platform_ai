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
from app.core.logger import get_logger

logger = get_logger(__name__)
import json

router = APIRouter(prefix="/api/v1/mcp-management", tags=["mcp-management"])


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
    
    # 获取工具信息（包含 URL）
    tool_info = hub.get_tool(tool_name)
    
    start_time = time.time()
    try:
        result = hub.execute_sync(tool_name, arguments)
        elapsed_ms = (time.time() - start_time) * 1000
        
        # 记录调用到数据库
        _record_call_to_db(db, tool_name, result.get("success", False), elapsed_ms, arguments, result)
        
        return {
            **result,
            "execution_time_ms": round(elapsed_ms, 2),
            # 包含工具配置信息
            "tool_url": tool_info.url if tool_info else "",
            "tool_name": tool_name
        }
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        _record_call_to_db(db, tool_name, False, elapsed_ms, arguments, None, str(e))
        
        return {
            "success": False,
            "error": str(e),
            "execution_time_ms": round(elapsed_ms, 2),
            "tool_url": tool_info.url if tool_info else "",
            "tool_name": tool_name
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
        tool_category = tool_info.category if tool_info else None
        
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
        "external": "外部API工具",
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


@router.get("/external-tools/{tool_name}")
async def get_external_tool(tool_name: str, db: Session = Depends(get_db)):
    """获取单个外部 API 工具详情"""
    from app.models.mcp_call_log import MCPToolDefinition
    
    tool = db.query(MCPToolDefinition).filter(
        MCPToolDefinition.tool_name == tool_name
    ).first()
    
    if not tool:
        return {"success": False, "error": f"工具 '{tool_name}' 不存在"}
    
    return {
        "success": True,
        "tool": tool.to_dict()
    }


@router.post("/external-tools")
async def create_external_tool(tool_data: Dict[str, Any], db: Session = Depends(get_db)):
    """创建外部 API 工具"""
    from app.models.mcp_call_log import MCPToolDefinition
    from app.mcp_tools.tool_registry_manager import ToolRegistryManager
    
    try:
        # 必填字段验证
        required_fields = ["tool_code", "tool_name", "url"]
        for field in required_fields:
            if not tool_data.get(field):
                return {"success": False, "error": f"缺少必填字段: {field}"}
        
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
            # 外部工具配置字段
            tool_type=tool_data.get("tool_type", "url"),
            protocol=tool_data.get("protocol", "http"),
            request_method=tool_data.get("request_method", "POST").upper(),
            url=tool_data.get("url"),
            auth_type=tool_data.get("auth_type", "none"),
            auth_info=tool_data.get("auth_info"),
            need_summary=tool_data.get("need_summary", False),
            prompt=tool_data.get("prompt"),
            # 保留兼容性字段
            config=tool_data.get("config"),
            extra_metadata=tool_data.get("extra_metadata")
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
        # 更新基本字段
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
        
        # 更新外部工具配置字段（明确的数据库字段）
        if "tool_type" in tool_data:
            tool.tool_type = tool_data["tool_type"]
        if "protocol" in tool_data:
            tool.protocol = tool_data["protocol"]
        if "request_method" in tool_data:
            tool.request_method = tool_data["request_method"].upper()
        if "url" in tool_data:
            tool.url = tool_data["url"]
        if "auth_type" in tool_data:
            tool.auth_type = tool_data["auth_type"]
        if "auth_info" in tool_data:
            tool.auth_info = tool_data["auth_info"]
        if "need_summary" in tool_data:
            tool.need_summary = tool_data["need_summary"]
        if "prompt" in tool_data:
            tool.prompt = tool_data["prompt"]
        
        # 保留对 config 和 extra_metadata 的直接更新支持（兼容性）
        if "config" in tool_data:
            tool.config = tool_data["config"]
        if "extra_metadata" in tool_data:
            tool.extra_metadata = tool_data["extra_metadata"]
        
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


@router.post("/external-tools/import")
async def import_external_tools(import_data: Dict[str, Any], db: Session = Depends(get_db)):
    """
    从 OpenAPI 规范导入外部工具（带版本校验）
    
    Args:
        import_data: 包含 OpenAPI 规范内容的字典
            - spec_content: OpenAPI 规范内容（JSON 或 YAML 格式字符串）
            - category: 可选，工具分类
            - is_enabled: 可选，是否启用，默认 true
    """
    from app.models.mcp_call_log import MCPToolDefinition
    from app.mcp_tools.tool_registry_manager import ToolRegistryManager
    from app.mcp_tools.openapi_parser import generate_tools_from_openapi
    
    spec_content = import_data.get("spec_content", "")
    category = import_data.get("category", "external")
    is_enabled = import_data.get("is_enabled", True)
    
    if not spec_content:
        return {"success": False, "error": "OpenAPI 规范内容不能为空"}
    
    try:
        # 解析 OpenAPI 规范并生成工具定义（带版本校验）
        tools, error_msg = generate_tools_from_openapi(spec_content)
        
        if error_msg:
            return {"success": False, "error": f"规范校验失败: {error_msg}"}
        
        if not tools:
            return {"success": False, "error": "未能从 OpenAPI 规范中解析出任何工具"}
        
        # 批量创建工具
        created_tools = []
        skipped_tools = []
        failed_tools = []
        
        for tool_data in tools:
            # 更新分类和启用状态
            tool_data["category"] = category
            tool_data["is_enabled"] = is_enabled
            
            try:
                # 检查是否已存在
                existing = db.query(MCPToolDefinition).filter(
                    MCPToolDefinition.tool_name == tool_data["tool_name"]
                ).first()
                
                if existing:
                    skipped_tools.append({
                        "tool_name": tool_data["tool_name"],
                        "reason": "工具已存在"
                    })
                    continue
                
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
                    extra_metadata=tool_data.get("extra_metadata"),
                    # 新的明确字段
                    tool_type=tool_data.get("tool_type"),
                    protocol=tool_data.get("protocol"),
                    request_method=tool_data.get("request_method"),
                    url=tool_data.get("url"),
                    auth_type=tool_data.get("auth_type"),
                    auth_info=tool_data.get("auth_info"),
                    need_summary=tool_data.get("need_summary"),
                    prompt=tool_data.get("prompt")
                )
                
                db.add(new_tool)
                created_tools.append(new_tool.to_dict())
                
            except Exception as e:
                failed_tools.append({
                    "tool_name": tool_data["tool_name"],
                    "reason": str(e)
                })
        
        db.commit()
        
        # 如果有创建成功的工具，重新加载外部工具
        if created_tools and is_enabled:
            manager = ToolRegistryManager(db)
            manager.sync_tools_from_database()
        
        logger.info(f"Imported {len(created_tools)} external tools from OpenAPI spec")
        
        return {
            "success": True,
            "message": f"成功导入 {len(created_tools)} 个工具，跳过 {len(skipped_tools)} 个，失败 {len(failed_tools)} 个",
            "created": created_tools,
            "skipped": skipped_tools,
            "failed": failed_tools
        }
        
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to import external tools: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@router.post("/external-tools/parse")
async def parse_openapi_spec(import_data: Dict[str, Any]):
    """
    解析 OpenAPI 规范并预览工具定义（不保存，带版本校验）
    
    Args:
        import_data: 包含 OpenAPI 规范内容的字典
            - spec_content: OpenAPI 规范内容（JSON 或 YAML 格式字符串）
    """
    from app.mcp_tools.openapi_parser import generate_tools_from_openapi, get_supported_versions
    
    spec_content = import_data.get("spec_content", "")
    
    if not spec_content:
        return {"success": False, "error": "OpenAPI 规范内容不能为空"}
    
    try:
        # 解析 OpenAPI 规范并生成工具定义（带版本校验）
        tools, error_msg = generate_tools_from_openapi(spec_content)
        
        if error_msg:
            return {"success": False, "error": f"规范校验失败: {error_msg}"}
        
        if not tools:
            return {"success": False, "error": "未能从 OpenAPI 规范中解析出任何工具"}
        
        return {
            "success": True,
            "tools": tools,
            "total": len(tools),
            "supported_versions": get_supported_versions()
        }
        
    except Exception as e:
        logger.error(f"Failed to parse OpenAPI spec: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@router.get("/external-tools/supported-versions")
async def get_supported_openapi_versions():
    """
    获取支持的 OpenAPI 规范版本列表
    """
    from app.mcp_tools.openapi_parser import get_supported_versions
    
    return {
        "success": True,
        "versions": get_supported_versions(),
        "description": "支持的 OpenAPI 规范版本"
    }

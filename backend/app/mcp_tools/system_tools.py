# 系统工具 MCP 封装
# 包含系统状态、配置、工具管理等功能

from typing import Dict, Any, List
from .tool_hub import get_toolhub, MCPToolHub
from . import kb_tools


# ============================================================
# 工具列表工具
# ============================================================

@get_toolhub().register_decorator
def list_mcp_tools() -> Dict[str, Any]:
    """
    列出所有可用的 MCP 工具
    
    Returns:
        工具列表和分类信息
    """
    hub = get_toolhub()
    return {
        "success": True,
        "tools": hub.list_tools(),
        "categories": hub.get_categories(),
        "total": hub.get_tool_count()
    }


# ============================================================
# 工具执行工具（用于动态调用）
# ============================================================

@get_toolhub().register_decorator
def execute_tool(tool_name: str, arguments: Dict[str, Any] = None) -> Dict[str, Any]:
    """
    执行指定的 MCP 工具
    
    Args:
        tool_name: 工具名称
        arguments: 工具参数
        
    Returns:
        执行结果
    """
    import asyncio
    hub = get_toolhub()
    
    if arguments is None:
        arguments = {}
    
    # 尝试同步执行
    if hub.has_tool(tool_name):
        return hub.execute_sync(tool_name, arguments)
    
    return {
        "success": False,
        "error": f"工具 '{tool_name}' 不存在"
    }


# ============================================================
# 工具注册工具（动态注册新工具）
# ============================================================

@get_toolhub().register_decorator
def register_custom_tool(
    name: str,
    description: str,
    handler_code: str,
    category: str = "custom"
) -> Dict[str, Any]:
    """
    动态注册自定义工具
    
    Args:
        name: 工具名称
        description: 工具描述
        handler_code: 处理函数代码（Python 代码字符串）
        category: 工具分类
        
    Returns:
        注册结果
    """
    try:
        # 注意：实际生产环境中应禁止 exec，这里仅作示例
        # 真实场景应使用预定义的工具注册接口
        return {
            "success": False,
            "error": "动态代码执行已被禁用，请使用 @mcptool 装饰器注册工具"
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }


# ============================================================
# 系统状态工具
# ============================================================

@get_toolhub().register_decorator
def system_status() -> Dict[str, Any]:
    """
    获取系统状态
    
    Returns:
        系统状态信息
    """
    from ..services.llm_service import llm_service
    
    hub = get_toolhub()
    
    return {
        "success": True,
        "system": {
            "llm_enabled": llm_service.enabled,
            "llm_provider": llm_service.llm_config.get("provider", "unknown"),
            "llm_model": llm_service.llm_config.get("model", "unknown"),
            "kb_enabled": kb_tools.KB_API_CONFIG["enabled"],
            "tool_count": hub.get_tool_count(),
            "categories": hub.get_categories()
        }
    }


# ============================================================
# 配置知识库工具
# ============================================================

@get_toolhub().register_decorator
def configure_knowledge_base(
    api_url: str,
    api_key: str,
    model: str = None
) -> Dict[str, Any]:
    """
    配置知识库 API
    
    Args:
        api_url: 知识库 API 地址
        api_key: API 密钥
        model: 可选的模型名称
        
    Returns:
        配置结果
    """
    kb_tools.configure_kb_api(api_url, api_key, model)
    return {
        "success": True,
        "message": "知识库 API 配置成功",
        "enabled": kb_tools.KB_API_CONFIG["enabled"]
    }


# ============================================================
# 帮助工具
# ============================================================

@get_toolhub().register_decorator
def get_help(topic: str = None) -> Dict[str, Any]:
    """
    获取 MCP 工具帮助信息
    
    Args:
        topic: 可选的主题（工具名称、分类等）
        
    Returns:
        帮助信息
    """
    hub = get_toolhub()
    
    if topic:
        # 获取特定工具的帮助
        tool = hub.get_tool(topic)
        if tool:
            return {
                "success": True,
                "tool": {
                    "name": tool.name,
                    "description": tool.description,
                    "category": tool.category,
                    "input_schema": tool.input_schema,
                    "examples": tool.examples
                }
            }
        
        # 获取特定分类的帮助
        tools_in_category = hub.get_tools_by_category(topic)
        if tools_in_category:
            return {
                "success": True,
                "category": topic,
                "tools": [
                    {"name": t.name, "description": t.description}
                    for t in tools_in_category
                ]
            }
        
        return {
            "success": False,
            "error": f"未找到主题: {topic}"
        }
    
    # 返回所有工具和分类
    return {
        "success": True,
        "categories": [
            {
                "name": cat,
                "description": _get_category_description(cat),
                "tool_count": len(hub.get_tools_by_category(cat))
            }
            for cat in hub.get_categories()
        ],
        "all_tools": [
            {"name": t.name, "category": t.category, "description": t.description}
            for t in hub.get_all_tools()
        ]
    }


def _get_category_description(category: str) -> str:
    """获取分类描述"""
    descriptions = {
        "form": "表单相关工具：生成、校验、提交表单",
        "kb": "知识库工具：问答、检索",
        "llm": "LLM 工具：对话、JSON 生成",
        "system": "系统工具：状态、配置、帮助",
        "general": "通用工具"
    }
    return descriptions.get(category, category)

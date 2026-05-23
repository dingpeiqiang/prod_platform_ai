# MCP Tools 模块
# 标准化内部工具为 MCP 协议格式

import logging
from .tool_hub import MCPToolHub, MCPTool, mcptool, get_toolhub

__all__ = ['MCPToolHub', 'MCPTool', 'mcptool', 'get_toolhub']
_logger = logging.getLogger("mcp_tools")


def register_all_tools():
    """注册所有内部 MCP 工具（自动调用各子模块）"""
    # 表单工具
    from . import form_tools
    
    # 知识库工具
    from . import kb_tools
    
    # LLM 工具
    from . import llm_tools
    
    # 系统工具
    from . import system_tools
    
    # 工作流工具
    from . import workflow_tools

    hub = get_toolhub()
    _logger.info(f"[MCP] 已注册 {hub.get_tool_count()} 个内部工具（代码注解）")
    return hub


def register_external_tariff_tools():
    """
    注册资费备案外部 API 工具
    
    query_tariff_by_code / query_tariff_info 实际调用外部 HTTP API，
    按外部 MCP 工具注册（category="external"），与内部工具区分。
    """
    from .tariff_tools import query_tariff_by_code, query_tariff_info

    hub = get_toolhub()

    hub.register(
        name="query_tariff_by_code",
        description=(
            "根据套餐编码（如 P000111、P123456）查询资费套餐详细信息。"
            "当用户提到「备案套餐 X」「套餐编码 X」「我要备案 P000111」时，"
            "**必须**从用户输入中提取 X（如 P000111）作为 tariff_code 参数并调用本工具。"
            "参数 tariff_code 必填，绝不能为空。"
        ),
        handler=query_tariff_by_code,
        category="external",
        input_schema={
            "type": "object",
            "properties": {
                "tariff_code": {
                    "type": "string",
                    "description": "套餐编码，如 P000111、P123456"
                }
            },
            "required": ["tariff_code"]
        }
    )

    hub.register(
        name="query_tariff_info",
        description=(
            "查询套餐核心信息（简化版），返回套餐编码、名称、备案主体、资费费率等核心字段。"
            "当需要快速查询套餐概要时使用，参数 tariff_code 必填。"
        ),
        handler=query_tariff_info,
        category="external",
        input_schema={
            "type": "object",
            "properties": {
                "tariff_code": {
                    "type": "string",
                    "description": "套餐编码"
                }
            },
            "required": ["tariff_code"]
        }
    )

    _logger.info("[MCP] 已注册 2 个外部资费工具（query_tariff_by_code, query_tariff_info）")
    return 2

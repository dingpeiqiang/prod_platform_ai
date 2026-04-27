# MCP Tools 模块
# 标准化内部工具为 MCP 协议格式

from .tool_hub import MCPToolHub, MCPTool, mcptool, get_toolhub

__all__ = ['MCPToolHub', 'MCPTool', 'mcptool', 'get_toolhub']


def register_all_tools():
    """注册所有 MCP 工具（自动调用各子模块）"""
    # 表单工具
    from . import form_tools
    
    # 知识库工具
    from . import kb_tools
    
    # LLM 工具
    from . import llm_tools
    
    # 系统工具
    from . import system_tools
    
    hub = get_toolhub()
    print(f"[MCP] 已注册 {hub.get_tool_count()} 个工具")
    return hub

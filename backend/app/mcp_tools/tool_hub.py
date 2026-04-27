# MCP Tool Hub - 统一工具注册与调度中心
# 管理所有 MCP 工具的注册、查询、执行

import logging
from typing import Any, Callable, Dict, List, Optional
from .tool_def import MCPTool

logger = logging.getLogger("mcp_tools")


class MCPToolHub:
    """
    MCP 工具中心
    
    职责：
    1. 工具注册与注销
    2. 工具查询与发现
    3. 工具执行与调度
    4. MCP 协议兼容
    """
    
    _instance = None
    _tools: Dict[str, MCPTool] = {}
    _categories: Dict[str, List[str]] = {}  # category -> tool_names

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        logger.info("[MCPToolHub] MCP Tool Hub 初始化完成")

    def register(
        self,
        name: str,
        description: str,
        handler: Callable,
        category: str = "general",
        input_schema: Dict[str, Any] = None,
        examples: List[Dict] = None
    ) -> None:
        """
        注册一个 MCP 工具
        
        Args:
            name: 工具名称（唯一标识）
            description: 工具描述（供 LLM 理解用途）
            handler: 处理函数
            category: 工具分类
            input_schema: 参数 schema（可选，自动从 handler 推断）
            examples: 使用示例
        """
        if name in self._tools:
            logger.warning(f"[MCPToolHub] 工具 {name} 已存在，将被覆盖")
        
        tool = MCPTool(
            name=name,
            description=description,
            input_schema=input_schema or {"type": "object", "properties": {}, "required": []},
            handler=handler,
            category=category,
            examples=examples or []
        )
        
        self._tools[name] = tool
        
        # 更新分类索引
        if category not in self._categories:
            self._categories[category] = []
        if name not in self._categories[category]:
            self._categories[category].append(name)
        
        logger.info(f"[MCPToolHub] 注册工具: {name} (category={category})")

    def register_decorator(self, func: Callable) -> Callable:
        """
        注册装饰器（用于 @mcptool 装饰的函数）
        """
        if hasattr(func, '_mcp_tool'):
            tool: MCPTool = func._mcp_tool
            self._tools[tool.name] = tool
            
            if tool.category not in self._categories:
                self._categories[tool.category] = []
            if tool.name not in self._categories[tool.category]:
                self._categories[tool.category].append(tool.name)
            
            logger.info(f"[MCPToolHub] 注册装饰器工具: {tool.name}")
        return func

    def unregister(self, name: str) -> bool:
        """注销工具"""
        if name not in self._tools:
            return False
        
        tool = self._tools.pop(name)
        
        # 从分类中移除
        if tool.category in self._categories:
            self._categories[tool.category].remove(name)
        
        logger.info(f"[MCPToolHub] 注销工具: {name}")
        return True

    def get_tool(self, name: str) -> Optional[MCPTool]:
        """获取指定工具"""
        return self._tools.get(name)

    def get_all_tools(self) -> List[MCPTool]:
        """获取所有工具"""
        return list(self._tools.values())

    def get_tools_by_category(self, category: str) -> List[MCPTool]:
        """获取指定分类的工具"""
        tool_names = self._categories.get(category, [])
        return [self._tools[name] for name in tool_names if name in self._tools]

    def get_categories(self) -> List[str]:
        """获取所有分类"""
        return list(self._categories.keys())

    def list_tools(self) -> List[Dict[str, Any]]:
        """
        列出所有工具（MCP 协议格式）
        
        Returns:
            符合 MCP list_tools 规范的列表
        """
        return [tool.to_mcp_dict() for tool in self._tools.values()]

    def get_tool_schemas_for_llm(self) -> str:
        """
        获取工具列表的文本描述（用于注入 Prompt）
        
        Returns:
            格式化的工具描述字符串
        """
        lines = []
        for tool in self._tools.values():
            props = tool.input_schema.get("properties", {})
            params = ", ".join(props.keys()) if props else "无"
            lines.append(f"- {tool.name}({params}): {tool.description}")
        return "\n".join(lines) if lines else "暂无可用工具"

    async def execute(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """
        执行指定工具
        
        Args:
            name: 工具名称
            arguments: 工具参数
            
        Returns:
            执行结果
        """
        tool = self.get_tool(name)
        if not tool:
            return {
                "success": False,
                "error": f"工具 '{name}' 不存在"
            }
        
        return await tool.execute(arguments)

    def execute_sync(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """
        同步执行指定工具
        """
        tool = self.get_tool(name)
        if not tool:
            return {
                "success": False,
                "error": f"工具 '{name}' 不存在"
            }
        
        try:
            result = tool.handler(**arguments)
            return {
                "success": True,
                "result": result
            }
        except Exception as e:
            logger.exception(f"工具执行失败 [{name}]: {e}")
            return {
                "success": False,
                "error": str(e)
            }

    def has_tool(self, name: str) -> bool:
        """检查工具是否存在"""
        return name in self._tools

    def get_tool_count(self) -> int:
        """获取工具总数"""
        return len(self._tools)

    def clear(self) -> None:
        """清空所有工具（主要用于测试）"""
        self._tools.clear()
        self._categories.clear()
        logger.info("[MCPToolHub] 已清空所有工具")


# 全局单例
_toolhub: Optional[MCPToolHub] = None

def get_toolhub() -> MCPToolHub:
    """获取 ToolHub 单例"""
    global _toolhub
    if _toolhub is None:
        _toolhub = MCPToolHub()
    return _toolhub


# 快捷装饰器
def mcptool(
    name: str = None,
    description: str = None,
    category: str = "general",
    input_schema: Dict[str, Any] = None
):
    """
    MCP 工具装饰器
    
    用法:
        @mcptool(name="my_tool", description="我的工具", category="form")
        def my_tool(param1: str, param2: int):
            '''工具描述'''
            return {"result": param1 + str(param2)}
    """
    def decorator(func: Callable) -> Callable:
        tool_name = name or func.__name__
        tool_desc = description or func.__doc__ or tool_name
        
        # 注册到全局 ToolHub
        hub = get_toolhub()
        hub.register(
            name=tool_name,
            description=tool_desc,
            handler=func,
            category=category,
            input_schema=input_schema
        )
        
        return func
    
    return decorator

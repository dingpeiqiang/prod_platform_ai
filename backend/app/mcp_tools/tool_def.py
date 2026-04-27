# MCP Tool 基类和工具定义
# 符合 MCP 协议标准

import inspect
import logging
from typing import Any, Callable, Dict, List, Optional, get_type_hints
from dataclasses import dataclass, field

logger = logging.getLogger("mcp_tools")


@dataclass
class MCPInputSchema:
    """MCP 工具输入参数 Schema"""
    type: str = "object"
    properties: Dict[str, Dict] = field(default_factory=dict)
    required: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict:
        return {
            "type": self.type,
            "properties": self.properties,
            "required": self.required
        }


@dataclass
class MCPTool:
    """
    MCP 工具定义
    
    符合 Model Context Protocol 工具格式
    """
    name: str
    description: str
    input_schema: Dict[str, Any]
    handler: Callable
    category: str = "general"
    examples: List[Dict] = field(default_factory=list)

    def __post_init__(self):
        if not self.input_schema or self.input_schema == {"type": "object", "properties": {}, "required": []}:
            self.input_schema = self._generate_schema_from_handler()

    def _generate_schema_from_handler(self) -> Dict[str, Any]:
        """从 handler 函数签名自动生成 schema"""
        try:
            sig = inspect.signature(self.handler)
            hints = get_type_hints(self.handler) if self.handler else {}
            
            properties = {}
            required = []
            
            for param_name, param in sig.parameters.items():
                if param_name in ('cls', 'self'):
                    continue
                    
                param_type = hints.get(param_name, param.annotation)
                if param_type == str or param_type == 'str':
                    schema_type = "string"
                elif param_type == int or param_type == 'int':
                    schema_type = "integer"
                elif param_type == float or param_type == 'float':
                    schema_type = "number"
                elif param_type == bool or param_type == 'bool':
                    schema_type = "boolean"
                elif param_type == List or param_type == 'list':
                    schema_type = "array"
                elif param_type == Dict or param_type == 'dict':
                    schema_type = "object"
                else:
                    schema_type = "string"
                
                properties[param_name] = {
                    "type": schema_type,
                    "description": f"参数: {param_name}"
                }
                
                if param.default == inspect.Parameter.empty:
                    required.append(param_name)
            
            return {
                "type": "object",
                "properties": properties,
                "required": required
            }
        except Exception as e:
            logger.warning(f"无法从 handler 生成 schema: {e}")
            return {"type": "object", "properties": {}, "required": []}

    def to_mcp_dict(self) -> Dict[str, Any]:
        """转换为 MCP 协议格式"""
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.input_schema,
            "metadata": {
                "category": self.category,
                "examples": self.examples
            }
        }

    async def execute(self, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """执行工具"""
        try:
            self._validate_arguments(arguments)
            result = self.handler(**arguments)
            
            import asyncio
            if asyncio.iscoroutine(result):
                result = await result
            
            return {
                "success": True,
                "result": result
            }
        except Exception as e:
            logger.exception(f"工具执行失败 [{self.name}]: {e}")
            return {
                "success": False,
                "error": str(e)
            }

    def _validate_arguments(self, arguments: Dict[str, Any]) -> None:
        """验证参数"""
        required = self.input_schema.get("required", [])
        for req in required:
            if req not in arguments:
                raise ValueError(f"缺少必填参数: {req}")


# 简化的装饰器函数
def mcptool(
    name: str = None,
    description: str = None,
    category: str = "general",
    input_schema: Dict[str, Any] = None
):
    """
    MCP 工具装饰器
    
    用法:
        @mcptool(name="my_tool", description="我的工具")
        def my_tool(param1: str, param2: int):
            '''工具处理逻辑'''
            return {"result": param1 + str(param2)}
    """
    def decorator(func: Callable) -> Callable:
        tool_name = name or func.__name__
        tool_desc = description or func.__doc__ or tool_name
        
        tool = MCPTool(
            name=tool_name,
            description=tool_desc,
            input_schema=input_schema or {"type": "object", "properties": {}, "required": []},
            handler=func,
            category=category
        )
        
        func._mcp_tool = tool
        return func
    
    return decorator

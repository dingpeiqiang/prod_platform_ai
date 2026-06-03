# MCP Tool 基类和工具定义
# 符合 MCP 协议标准

import inspect
import re
from app.core.logger import get_logger

logger = get_logger(__name__)
from typing import Any, Callable, Dict, List, Optional, Union, get_type_hints, get_origin, get_args
from dataclasses import dataclass, field



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
    output_schema: Dict[str, Any] = field(default_factory=dict)  # 出参 Schema 定义
    # 外部工具配置字段
    url: str = ""
    request_method: str = "POST"
    protocol: str = "http"
    auth_type: str = "none"

    def __post_init__(self):
        if not self.input_schema or self.input_schema == {"type": "object", "properties": {}, "required": []}:
            self.input_schema = self._generate_schema_from_handler()

    def _get_schema_type(self, param_type) -> str:
        """从 Python 类型获取 schema 类型字符串"""
        origin = get_origin(param_type)

        # 处理 Optional/Union 类型，提取内部真实类型（忽略 None）
        if origin is not None and origin is Union:
            # 获取类型参数，过滤掉 NoneType
            args = get_args(param_type)
            for arg_type in args:
                if arg_type is not type(None):
                    return self._get_schema_type(arg_type)

        if origin is not None:
            if origin is dict or origin is Dict:
                return "object"
            elif origin is list or origin is List:
                return "array"
            elif origin is tuple:
                return "array"

        if param_type is str or param_type == 'str' or param_type == 'String':
            return "string"
        elif param_type is int or param_type == 'int' or param_type == 'Integer':
            return "integer"
        elif param_type is float or param_type == 'float' or param_type == 'Number':
            return "number"
        elif param_type is bool or param_type == 'bool' or param_type == 'Boolean':
            return "boolean"
        elif param_type is list or param_type == 'list' or param_type == 'List':
            return "array"
        elif param_type is dict or param_type == 'dict' or param_type == 'Dict' or param_type == 'Dict[str, Any]':
            return "object"

        return "string"

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
                schema_type = self._get_schema_type(param_type)

                properties[param_name] = {
                    "type": schema_type,
                    "description": f"参数: {param_name}"
                }

                if param.default == inspect.Parameter.empty:
                    required.append(param_name)

            schema = {
                "type": "object",
                "properties": properties,
                "required": required
            }
            logger.info(f"[MCPTool] 为工具 {self.name} 生成的 schema: {schema}")
            return schema
        except Exception as e:
            logger.warning(f"无法从 handler 生成 schema: {e}")
            return {"type": "object", "properties": {}, "required": []}

    def to_mcp_dict(self) -> Dict[str, Any]:
        """转换为 MCP 协议格式"""
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.input_schema,
            "outputSchema": self.output_schema,
            "metadata": {
                "category": self.category,
                "examples": self.examples
            },
            # 外部工具配置字段
            "url": self.url,
            "requestMethod": self.request_method,
            "protocol": self.protocol,
            "authType": self.auth_type
        }

    async def execute(self, arguments: Dict[str, Any]) -> Dict[str, Any]:
        try:
            self._validate_arguments(arguments)
            result = self.handler(**arguments)

            import asyncio
            if asyncio.iscoroutine(result):
                result = await result

            if isinstance(result, dict) and "success" in result:
                return result

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
        """验证参数
        
        校验规则：
        1. 必填参数检查 - 确保所有必填参数都存在
        2. 参数类型检查 - 检查参数类型是否符合 schema 定义
        3. 格式验证 - 支持 email、url、date、datetime、regex 等格式
        4. 范围检查 - 支持 minimum、maximum（数值类型）
        5. 数组检查 - 支持 minItems、maxItems（数组类型）
        6. 枚举值检查 - 支持 enum 枚举值校验
        7. 字符串长度检查 - 支持 minLength、maxLength
        
        Args:
            arguments: 传入的参数字典
            
        Raises:
            ValueError: 参数校验失败时抛出异常
        """
        errors = []
        properties = self.input_schema.get("properties", {})
        
        # 调试日志：查看当前工具的 schema
        logger.info(f"[MCPTool] 工具 {self.name} 的 input_schema: {self.input_schema}")
        logger.info(f"[MCPTool] 工具 {self.name} 收到的参数: {arguments}")
        required = self.input_schema.get("required", [])

        # 1. 必填参数检查
        for req in required:
            if req not in arguments or arguments[req] is None:
                errors.append(f"缺少必填参数: {req}")

        # 2. 参数类型和格式检查
        for param_name, param_value in arguments.items():
            if param_name not in properties:
                continue
                
            prop_def = properties[param_name]
            expected_type = prop_def.get("type", "string")
            param_value = arguments[param_name]
            
            # 跳过 None 值的类型检查（除非字段是必填的）
            if param_value is None:
                continue
            
            # 类型检查
            is_valid, type_error = self._check_type(param_value, expected_type)
            if not is_valid:
                errors.append(f"参数 '{param_name}' 类型错误: 期望 {expected_type}，实际 {type_error}")
                continue
            
            # 根据类型进行特定校验
            if expected_type == "string":
                self._validate_string(param_name, param_value, prop_def, errors)
            elif expected_type == "integer":
                self._validate_integer(param_name, param_value, prop_def, errors)
            elif expected_type == "number":
                self._validate_number(param_name, param_value, prop_def, errors)
            elif expected_type == "array":
                self._validate_array(param_name, param_value, prop_def, errors)
            elif expected_type == "object":
                self._validate_object(param_name, param_value, prop_def, errors)

        if errors:
            raise ValueError("参数校验失败: " + "; ".join(errors))

    def _check_type(self, value: Any, expected_type: str) -> tuple:
        """检查值的类型是否符合预期"""
        type_map = {
            "string": str,
            "integer": int,
            "number": (int, float),
            "boolean": bool,
            "array": list,
            "object": dict,
            "null": type(None)
        }
        
        expected_python_type = type_map.get(expected_type)
        
        if expected_python_type is None:
            return (True, "")
        
        # 处理数值类型的特殊情况（字符串形式的数字）
        if expected_type in ("integer", "number") and isinstance(value, str):
            try:
                if expected_type == "integer":
                    int(value)
                else:
                    float(value)
                return (True, "")
            except ValueError:
                return (False, f"字符串 '{value}' 无法转换为 {expected_type}")
        
        if isinstance(value, expected_python_type):
            return (True, "")
        
        # 数字类型的宽松检查
        if expected_type == "number" and isinstance(value, int):
            return (True, "")
        
        return (False, type(value).__name__)

    def _validate_string(self, param_name: str, value: str, prop_def: dict, errors: list) -> None:
        """校验字符串类型参数"""
        # 最小长度检查
        if "minLength" in prop_def and len(value) < prop_def["minLength"]:
            errors.append(f"参数 '{param_name}' 长度不足: 最少需要 {prop_def['minLength']} 个字符")
        
        # 最大长度检查
        if "maxLength" in prop_def and len(value) > prop_def["maxLength"]:
            errors.append(f"参数 '{param_name}' 长度超限: 最多允许 {prop_def['maxLength']} 个字符")
        
        # 枚举值检查
        if "enum" in prop_def and value not in prop_def["enum"]:
            errors.append(f"参数 '{param_name}' 值不在允许范围内: 可选值为 {prop_def['enum']}")
        
        # 格式验证
        format_type = prop_def.get("format")
        if format_type:
            if not self._check_format(value, format_type):
                errors.append(f"参数 '{param_name}' 格式错误: 期望 {format_type} 格式")

    def _validate_integer(self, param_name: str, value: int, prop_def: dict, errors: list) -> None:
        """校验整数类型参数"""
        # 处理字符串形式的数字
        if isinstance(value, str):
            try:
                value = int(value)
            except ValueError:
                errors.append(f"参数 '{param_name}' 不是有效的整数")
                return
        
        # 最小值检查
        if "minimum" in prop_def and value < prop_def["minimum"]:
            errors.append(f"参数 '{param_name}' 值过小: 最小值为 {prop_def['minimum']}")
        
        # 最大值检查
        if "maximum" in prop_def and value > prop_def["maximum"]:
            errors.append(f"参数 '{param_name}' 值过大: 最大值为 {prop_def['maximum']}")
        
        # 枚举值检查
        if "enum" in prop_def and value not in prop_def["enum"]:
            errors.append(f"参数 '{param_name}' 值不在允许范围内: 可选值为 {prop_def['enum']}")

    def _validate_number(self, param_name: str, value: float, prop_def: dict, errors: list) -> None:
        """校验数字类型参数"""
        # 处理字符串形式的数字
        if isinstance(value, str):
            try:
                value = float(value)
            except ValueError:
                errors.append(f"参数 '{param_name}' 不是有效的数字")
                return
        
        # 最小值检查
        if "minimum" in prop_def and value < prop_def["minimum"]:
            errors.append(f"参数 '{param_name}' 值过小: 最小值为 {prop_def['minimum']}")
        
        # 最大值检查
        if "maximum" in prop_def and value > prop_def["maximum"]:
            errors.append(f"参数 '{param_name}' 值过大: 最大值为 {prop_def['maximum']}")

    def _validate_array(self, param_name: str, value: list, prop_def: dict, errors: list) -> None:
        """校验数组类型参数"""
        # 最小元素数量检查
        if "minItems" in prop_def and len(value) < prop_def["minItems"]:
            errors.append(f"参数 '{param_name}' 元素数量不足: 最少需要 {prop_def['minItems']} 个元素")
        
        # 最大元素数量检查
        if "maxItems" in prop_def and len(value) > prop_def["maxItems"]:
            errors.append(f"参数 '{param_name}' 元素数量超限: 最多允许 {prop_def['maxItems']} 个元素")
        
        # 元素类型检查
        items_def = prop_def.get("items")
        if items_def:
            for idx, item in enumerate(value):
                expected_type = items_def.get("type", "string")
                is_valid, type_error = self._check_type(item, expected_type)
                if not is_valid:
                    errors.append(f"参数 '{param_name}' 的第 {idx+1} 个元素类型错误: 期望 {expected_type}，实际 {type_error}")

    def _validate_object(self, param_name: str, value: dict, prop_def: dict, errors: list) -> None:
        """校验对象类型参数"""
        # 嵌套对象属性检查
        properties_def = prop_def.get("properties", {})
        required_def = prop_def.get("required", [])
        
        for req in required_def:
            if req not in value or value[req] is None:
                errors.append(f"参数 '{param_name}' 缺少嵌套必填属性: {req}")
        
        for nested_name, nested_value in value.items():
            if nested_name in properties_def:
                nested_prop = properties_def[nested_name]
                expected_type = nested_prop.get("type", "string")
                is_valid, type_error = self._check_type(nested_value, expected_type)
                if not is_valid:
                    errors.append(f"参数 '{param_name}.{nested_name}' 类型错误: 期望 {expected_type}，实际 {type_error}")

    def _check_format(self, value: str, format_type: str) -> bool:
        """检查字符串格式"""
        format_patterns = {
            "email": r"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$",
            "url": r"^https?://[^\s/$.?#].[^\s]*$",
            "date": r"^\d{4}-\d{2}-\d{2}$",
            "datetime": r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}:\d{2})?$",
            "uuid": r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            "phone": r"^1[3-9]\d{9}$",
            "ipv4": r"^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        }
        
        pattern = format_patterns.get(format_type)
        if not pattern:
            # 未知格式类型，返回 True（放行）
            return True
        
        return bool(re.match(pattern, value))


# 简化的装饰器函数
def mcptool(
    name: str = None,
    description: str = None,
    category: str = "general",
    input_schema: Dict[str, Any] = None,
    output_schema: Dict[str, Any] = None
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
            category=category,
            output_schema=output_schema or {}
        )

        func._mcp_tool = tool
        return func

    return decorator
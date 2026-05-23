"""
OpenAPI 规范解析器
用于从 OpenAPI 3.0/3.1 规范中提取 API 信息并生成外部工具定义
"""
import json
import yaml
from typing import Dict, Any, List, Optional
import logging

logger = logging.getLogger("openapi_parser")


class OpenAPIParser:
    """OpenAPI 规范解析器"""
    
    def __init__(self, spec: Dict[str, Any]):
        """
        初始化解析器
        
        Args:
            spec: OpenAPI 规范字典（已解析的 JSON/YAML）
        """
        self.spec = spec
        self.version = spec.get("openapi", spec.get("swagger", "3.0"))
        self.title = spec.get("info", {}).get("title", "")
        self.description = spec.get("info", {}).get("description", "")
        self.servers = spec.get("servers", [])
        self.base_path = self._extract_base_path()
    
    def _extract_base_path(self) -> str:
        """提取基础路径"""
        if self.servers:
            return self.servers[0].get("url", "")
        
        # 如果没有 servers 字段，尝试从其他地方获取
        # 检查是否有 basePath（Swagger 2.0 格式）
        if self.spec.get("swagger") == "2.0":
            host = self.spec.get("host", "")
            base_path = self.spec.get("basePath", "")
            schemes = self.spec.get("schemes", ["https"])
            # 使用第一个协议
            scheme = schemes[0] if schemes else "https"
            if host:
                return f"{scheme}://{host}{base_path}"
            return base_path
        
        return ""
    
    def parse_all_endpoints(self) -> List[Dict[str, Any]]:
        """
        解析所有端点并生成工具定义列表
        
        Returns:
            工具定义列表
        """
        endpoints = []
        paths = self.spec.get("paths", {})
        
        for path, methods in paths.items():
            for method, operation in methods.items():
                if method.upper() in ["GET", "POST", "PUT", "DELETE", "PATCH"]:
                    tool_def = self._parse_operation(path, method, operation)
                    if tool_def:
                        endpoints.append(tool_def)
        
        return endpoints
    
    def _parse_operation(self, path: str, method: str, operation: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        解析单个操作并生成工具定义
        
        Args:
            path: API 路径
            method: HTTP 方法
            operation: 操作定义
        
        Returns:
            工具定义字典，如果无法解析返回 None
        """
        try:
            tool_name = self._generate_tool_name(path, method, operation)
            description = operation.get("description", operation.get("summary", ""))
            
            # 解析参数
            parameters = operation.get("parameters", [])
            request_body = operation.get("requestBody", {})
            
            # 生成输入 Schema
            input_schema = self._build_input_schema(parameters, request_body)
            
            # 生成输出 Schema
            responses = operation.get("responses", {})
            output_schema = self._build_output_schema(responses)
            
            # 生成工具配置
            config = self._build_tool_config(path, method)
            
            return {
                "tool_name": tool_name,
                "tool_code": self._generate_tool_code(path, method),
                "description": description,
                "category": "external",
                "is_enabled": True,
                "is_public": True,
                "input_schema": input_schema,
                "output_schema": output_schema,
                "config": config,
                "extra_metadata": {
                    "openapi_operation_id": operation.get("operationId"),
                    "openapi_tags": operation.get("tags", []),
                    "openapi_path": path,
                    "openapi_method": method.upper()
                }
            }
            
        except Exception as e:
            logger.error(f"Failed to parse operation {method} {path}: {e}", exc_info=True)
            return None
    
    def _generate_tool_name(self, path: str, method: str, operation: Dict[str, Any]) -> str:
        """生成工具名称"""
        # 优先使用 operationId
        operation_id = operation.get("operationId")
        if operation_id:
            return operation_id
        
        # 从路径生成名称
        path_parts = [p for p in path.split("/") if p and not p.startswith("{")]
        name_parts = []
        
        for part in path_parts:
            if part:
                name_parts.append(part.replace("_", "").replace("-", ""))
        
        # 添加方法前缀
        prefix = method.lower()
        if name_parts:
            return f"{prefix}_{'_'.join(name_parts)}"
        return f"{prefix}_api"
    
    def _generate_tool_code(self, path: str, method: str) -> str:
        """生成工具编码"""
        path_parts = [p for p in path.split("/") if p and not p.startswith("{")]
        return f"{method.lower()}_{'_'.join(path_parts)}" if path_parts else f"{method.lower()}_api"
    
    def _build_input_schema(self, parameters: List[Dict[str, Any]], request_body: Dict[str, Any]) -> Dict[str, Any]:
        """
        构建输入参数 Schema
        
        Args:
            parameters: 查询参数、路径参数列表
            request_body: 请求体定义
        
        Returns:
            JSON Schema 格式的输入定义
        """
        properties = {}
        required = []
        
        # 处理路径参数和查询参数
        for param in parameters:
            param_name = param.get("name", "")
            param_in = param.get("in", "query")
            
            if param_in in ["query", "path"]:
                prop = self._convert_parameter_to_schema(param)
                if prop:
                    properties[param_name] = prop
                    if param.get("required", False):
                        required.append(param_name)
        
        # 处理请求体
        if request_body:
            content = request_body.get("content", {})
            # 优先处理 application/json
            if "application/json" in content:
                schema = content["application/json"].get("schema", {})
                if schema.get("type") == "object" and schema.get("properties"):
                    for prop_name, prop_def in schema["properties"].items():
                        # 解析属性，处理 $ref 引用
                        resolved_prop = self._resolve_schema(prop_def)
                        properties[prop_name] = resolved_prop
                        if schema.get("required") and prop_name in schema["required"]:
                            required.append(prop_name)
        
        return {
            "type": "object",
            "properties": properties,
            "required": required
        }
    
    def _convert_parameter_to_schema(self, param: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """将参数定义转换为 Schema 属性"""
        # OpenAPI 3.0 版本：类型在 schema 中
        schema = param.get("schema", {})
        param_type = schema.get("type", param.get("type", "string"))
        
        prop = {
            "type": self._convert_type(param_type),
            "description": param.get("description", "")
        }
        
        # 处理数组类型
        if param_type == "array":
            # 尝试获取 items
            items = schema.get("items", param.get("items"))
            if items:
                prop["items"] = self._resolve_schema(items)
        
        # 添加枚举值
        if "enum" in schema:
            prop["enum"] = schema["enum"]
        elif param.get("enum"):
            prop["enum"] = param["enum"]
        
        # 添加默认值
        if "default" in schema:
            prop["default"] = schema["default"]
        elif "default" in param:
            prop["default"] = param["default"]
        
        # 添加格式信息
        if "format" in schema:
            prop["format"] = schema["format"]
        elif param.get("format"):
            prop["format"] = param["format"]
        
        return prop
    
    def _convert_type(self, openapi_type: str) -> str:
        """转换 OpenAPI 类型为 JSON Schema 类型"""
        type_mapping = {
            "integer": "integer",
            "int32": "integer",
            "int64": "integer",
            "number": "number",
            "float": "number",
            "double": "number",
            "string": "string",
            "boolean": "boolean",
            "array": "array",
            "object": "object"
        }
        return type_mapping.get(openapi_type, "string")
    
    def _build_output_schema(self, responses: Dict[str, Any]) -> Dict[str, Any]:
        """
        构建输出 Schema
        
        Args:
            responses: 响应定义
        
        Returns:
            JSON Schema 格式的输出定义
        """
        # 优先查找 200、201 或 default 响应
        success_codes = ["200", "201", "204", "default"]
        
        for code in success_codes:
            if code in responses:
                response = responses[code]
                
                # 优先处理 OpenAPI 3.0 格式 (content)
                content = response.get("content", {})
                if "application/json" in content:
                    schema = content["application/json"].get("schema", {})
                    if schema:
                        return self._resolve_schema(schema)
                
                # 尝试其他常见类型
                content_types = ["application/xml", "text/json", "text/plain", "*/*"]
                for ct in content_types:
                    if ct in content:
                        schema = content[ct].get("schema", {})
                        if schema:
                            return self._resolve_schema(schema)
                
                # 处理 Swagger 2.0 格式 (直接有 schema)
                if "schema" in response:
                    schema = response.get("schema", {})
                    if schema:
                        return self._resolve_schema(schema)
                
                # 如果有描述但没有 schema，创建一个简单的结构
                description = response.get("description", "")
                if description:
                    return {
                        "type": "object",
                        "properties": {
                            "result": {
                                "type": "string",
                                "description": description
                            }
                        },
                        "description": description
                    }
        
        # 如果完全没有响应定义，返回一个默认结构
        return {
            "type": "object",
            "properties": {
                "success": {
                    "type": "boolean",
                    "description": "操作是否成功"
                },
                "data": {
                    "type": "object",
                    "description": "返回的数据"
                },
                "message": {
                    "type": "string",
                    "description": "提示信息"
                }
            },
            "description": "API 响应数据"
        }
    
    def _resolve_schema(self, schema: Dict[str, Any]) -> Dict[str, Any]:
        """
        解析 schema，处理引用 ($ref) 和嵌套结构
        
        Args:
            schema: 原始 schema 定义
        
        Returns:
            解析后的 schema
        """
        if not isinstance(schema, dict):
            return {"type": "object", "properties": {}}
        
        # 处理引用
        if "$ref" in schema:
            ref_path = schema["$ref"]
            schema = self._resolve_ref(ref_path)
        
        # 复制并处理嵌套结构
        result = schema.copy()
        
        # 处理嵌套的 properties
        if "properties" in result and isinstance(result["properties"], dict):
            resolved_props = {}
            for prop_name, prop_schema in result["properties"].items():
                resolved_props[prop_name] = self._resolve_schema(prop_schema)
            result["properties"] = resolved_props
        
        # 处理 items（数组元素）
        if "items" in result:
            result["items"] = self._resolve_schema(result["items"])
        
        # 处理 allOf
        if "allOf" in result:
            combined = {"type": "object", "properties": {}, "required": []}
            for sub_schema in result["allOf"]:
                resolved_sub = self._resolve_schema(sub_schema)
                if "properties" in resolved_sub:
                    combined["properties"].update(resolved_sub["properties"])
                if "required" in resolved_sub:
                    combined["required"] = list(set(combined["required"] + resolved_sub["required"]))
            return combined
        
        # 处理 oneOf/anyOf
        if "oneOf" in result or "anyOf" in result:
            variants = result.get("oneOf", result.get("anyOf", []))
            resolved_variants = [self._resolve_schema(v) for v in variants]
            result["oneOf"] = resolved_variants
            if "anyOf" in result:
                result["anyOf"] = resolved_variants
        
        return result
    
    def _resolve_ref(self, ref_path: str) -> Dict[str, Any]:
        """
        解析 $ref 引用
        
        Args:
            ref_path: 引用路径，如 #/components/schemas/User
        
        Returns:
            解析后的 schema，如果无法解析返回空结构
        """
        # 移除 #/ 前缀
        path = ref_path.lstrip("#/")
        parts = path.split("/")
        
        current = self.spec
        for part in parts:
            # 处理转义字符
            part = part.replace("~1", "/").replace("~0", "~")
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                logger.warning(f"Could not resolve reference: {ref_path}")
                return {"type": "object", "properties": {}}
        
        # 如果解析到的是 schema，返回它
        if isinstance(current, dict):
            return current
        
        return {"type": "object", "properties": {}}
    
    def _build_tool_config(self, path: str, method: str) -> Dict[str, Any]:
        """
        构建工具配置
        
        Args:
            path: API 路径
            method: HTTP 方法
        
        Returns:
            工具配置字典
        """
        full_url = self.base_path + path
        
        # 创建 URL 模板（替换路径参数为双大括号）
        url_template = full_url
        # 直接使用字符串替换，将 {param} 替换为 {{param}}
        # 但需要注意不要重复替换已替换的部分
        # 先检查是否需要替换，避免无限循环
        if "{" in url_template and "}" in url_template and "{{" not in url_template:
            # 没有双大括号，直接替换
            import re
            # 匹配 {param} 但不匹配 {{param}}
            def replace_single_brace(match):
                return "{{" + match.group(1) + "}}"
            
            # 查找并替换所有单对大括号
            url_template = re.sub(r'\{([^}]+)\}', replace_single_brace, url_template)
        elif "{" in url_template and "}" in url_template:
            # 已经有双大括号了，直接返回
            pass
        else:
            # 没有路径参数
            url_template = full_url
        
        return {
            "method": method.upper(),
            "url": url_template,
            "headers": {},
            "timeout_seconds": 30,
            "retry_count": 0,
            "output_mapping": {}
        }


def parse_openapi_spec(content: str) -> Optional[OpenAPIParser]:
    """
    解析 OpenAPI 规范内容
    
    Args:
        content: OpenAPI 规范字符串（JSON 或 YAML 格式）
    
    Returns:
        OpenAPIParser 实例，如果解析失败返回 None
    """
    try:
        # 尝试解析为 JSON
        try:
            spec = json.loads(content)
        except json.JSONDecodeError:
            # 尝试解析为 YAML
            spec = yaml.safe_load(content)
        
        if not isinstance(spec, dict):
            logger.error("OpenAPI spec is not a dictionary")
            return None
        
        # 验证是否为有效的 OpenAPI 规范
        if "openapi" not in spec and "swagger" not in spec:
            logger.error("Not a valid OpenAPI spec")
            return None
        
        return OpenAPIParser(spec)
    
    except Exception as e:
        logger.error(f"Failed to parse OpenAPI spec: {e}", exc_info=True)
        return None


def generate_tools_from_openapi(content: str) -> List[Dict[str, Any]]:
    """
    从 OpenAPI 规范内容生成工具定义列表
    
    Args:
        content: OpenAPI 规范字符串（JSON 或 YAML 格式）
    
    Returns:
        工具定义列表，如果解析失败返回空列表
    """
    parser = parse_openapi_spec(content)
    if not parser:
        return []
    
    return parser.parse_all_endpoints()
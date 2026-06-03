"""
OpenAPI 规范解析器（版本感知）

用于从 OpenAPI/Swagger 规范中提取 API 信息并生成外部工具定义。

=== 支持的规范版本 ===

【推荐】OpenAPI 3.0.x / 3.1.x
- 参数类型定义在 schema 字段中
- 响应使用 content 字段
- 组件定义在 components/schemas
- 基础路径在 servers[0].url

【兼容】Swagger 2.0
- 参数类型直接定义在参数中
- 支持 formData 参数位置
- 组件定义在 definitions
- 基础路径由 host + basePath + schemes 组成

=== 校验流程 ===
1. 检查内容是否为空
2. 快速提取版本并检查是否支持
3. 解析 JSON/YAML
4. 校验结构是否符合规范

=== 版本识别 ===
- OpenAPI 3.x: openapi: "3.0.3"
- Swagger 2.0: swagger: "2.0"
"""
import json
import yaml
import re
from typing import Dict, Any, List, Optional, Tuple
from app.core.logger import get_logger

logger = get_logger(__name__)

# 支持的 OpenAPI 版本
SUPPORTED_VERSIONS = {
    "openapi": ["3.0", "3.1"],  # 支持 3.0.x 和 3.1.x 系列
    "swagger": ["2.0"]           # 兼容 Swagger 2.0
}

# 各版本必填字段定义
REQUIRED_FIELDS = {
    "openapi": {
        "3.0": ["openapi", "info", "paths"],
        "3.1": ["openapi", "info", "paths"]
    },
    "swagger": {
        "2.0": ["swagger", "info", "paths"]
    }
}

# 版本格式正则
OPENAPI_VERSION_PATTERN = re.compile(r"^(\d+\.\d+)(\.\d+)?$")



class OpenAPISpecError(Exception):
    """OpenAPI 规范校验异常"""
    def __init__(self, error_type: str, message: str):
        super().__init__(message)
        self.error_type = error_type
        self.message = message


def parse_version(version_str: str) -> Tuple[str, str, Optional[str]]:
    """
    解析版本字符串
    
    Args:
        version_str: 版本字符串，如 "3.0.3", "2.0", "3.1.0"
    
    Returns:
        (spec_type, major_minor, patch) 元组
        spec_type: "openapi" 或 "swagger"
        major_minor: 主版本号，如 "3.0", "2.0"
        patch: 补丁版本号，如 "3"，可选
    """
    match = OPENAPI_VERSION_PATTERN.match(version_str)
    if not match:
        return None, None, None
    
    major_minor = match.group(1)
    patch = match.group(2)[1:] if match.group(2) else None
    
    # 根据版本号判断规范类型
    if major_minor.startswith("3."):
        return "openapi", major_minor, patch
    elif major_minor == "2.0":
        return "swagger", major_minor, patch
    
    return None, None, None


def validate_version(version_str: str) -> Tuple[bool, Optional[str]]:
    """
    校验版本是否支持
    
    Args:
        version_str: 版本字符串
    
    Returns:
        (is_valid, error_message) 元组
    """
    spec_type, major_minor, patch = parse_version(version_str)
    
    if not spec_type:
        return False, f"无效的版本格式: '{version_str}'。期望格式如 '3.0.3' 或 '2.0'"
    
    if spec_type not in SUPPORTED_VERSIONS:
        return False, f"不支持的规范类型: '{spec_type}'。支持的类型: {list(SUPPORTED_VERSIONS.keys())}"
    
    if major_minor not in SUPPORTED_VERSIONS[spec_type]:
        supported = ", ".join(SUPPORTED_VERSIONS[spec_type])
        return False, f"不支持的 {spec_type} 版本: '{version_str}'。支持的版本: {supported}"
    
    return True, None


def validate_spec_structure(spec: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
    """
    校验规范结构是否符合要求
    
    Args:
        spec: 解析后的规范字典
    
    Returns:
        (is_valid, error_message) 元组
    """
    # 检查版本字段
    version_str = spec.get("openapi", spec.get("swagger"))
    if not version_str:
        return False, "缺少版本字段 'openapi' 或 'swagger'"
    
    # 解析版本
    spec_type, major_minor, _ = parse_version(version_str)
    if not spec_type:
        return False, f"无效的版本格式: '{version_str}'"
    
    # 检查必填字段
    if spec_type in REQUIRED_FIELDS and major_minor in REQUIRED_FIELDS[spec_type]:
        required_fields = REQUIRED_FIELDS[spec_type][major_minor]
        missing_fields = [f for f in required_fields if f not in spec]
        if missing_fields:
            return False, f"缺少必填字段: {', '.join(missing_fields)}"
    
    # 检查 info 字段结构
    info = spec.get("info", {})
    if not isinstance(info, dict):
        return False, "字段 'info' 必须是对象类型"
    
    # 检查 paths 字段结构
    paths = spec.get("paths", {})
    if not isinstance(paths, dict):
        return False, "字段 'paths' 必须是对象类型"
    
    # 检查是否有至少一个路径
    if not paths:
        return False, "字段 'paths' 不能为空"
    
    return True, None


def _extract_version_from_content(content: str) -> Optional[str]:
    """
    从原始内容中快速提取版本字符串（不进行完整解析）
    
    Args:
        content: OpenAPI 规范字符串
    
    Returns:
        版本字符串（如 "3.0.3"），如果未找到返回 None
    """
    # 尝试从内容中匹配版本字段
    # 匹配模式: openapi: "x.y.z" 或 swagger: "x.y"
    version_pattern = re.compile(r'(openapi|swagger)\s*:\s*["\']?([\d.]+)["\']?', re.IGNORECASE)
    match = version_pattern.search(content)
    if match:
        return match.group(2)
    return None


def validate_openapi_spec(content: str) -> Tuple[bool, Optional[str], Optional[Dict[str, Any]]]:
    """
    完整校验 OpenAPI 规范（先检查版本，再校验内容）
    
    校验流程：
    1. 检查内容是否为空
    2. 快速提取版本并检查是否支持
    3. 解析 JSON/YAML
    4. 校验结构是否符合规范
    
    Args:
        content: OpenAPI 规范字符串
    
    Returns:
        (is_valid, error_message, parsed_spec) 元组
    """
    # 步骤1: 检查内容是否为空
    if not content or not content.strip():
        return False, "规范内容不能为空", None
    
    # 步骤2: 先快速提取版本并检查是否支持
    version_str = _extract_version_from_content(content)
    if version_str:
        is_valid, error_msg = validate_version(version_str)
        if not is_valid:
            return False, f"版本校验失败: {error_msg}", None
    else:
        # 如果无法提取版本，继续进行解析（可能是格式不规范但仍可解析）
        pass
    
    # 步骤3: 尝试解析
    try:
        # 先尝试 JSON
        try:
            spec = json.loads(content)
        except json.JSONDecodeError:
            # 再尝试 YAML
            cleaned_content = _clean_yaml_indentation(content)
            spec = yaml.safe_load(cleaned_content)
        
        if not isinstance(spec, dict):
            return False, "解析结果不是有效的 JSON/YAML 对象", None
        
        # 再次确认版本（从解析结果中获取更准确的值）
        parsed_version = spec.get("openapi", spec.get("swagger"))
        if parsed_version:
            is_valid, error_msg = validate_version(parsed_version)
            if not is_valid:
                return False, f"版本校验失败: {error_msg}", None
        
        # 步骤4: 校验结构
        is_valid, error_msg = validate_spec_structure(spec)
        if not is_valid:
            return False, f"结构校验失败: {error_msg}", None
        
        return True, None, spec
    
    except Exception as e:
        return False, f"解析失败: {str(e)}", None


class OpenAPIParser:
    """OpenAPI 规范解析器（支持版本感知）"""
    
    def __init__(self, spec: Dict[str, Any]):
        """
        初始化解析器
        
        Args:
            spec: OpenAPI 规范字典（已解析的 JSON/YAML）
        """
        self.spec = spec
        
        # 确定规范类型和版本
        self._determine_spec_version()
        
        self.title = spec.get("info", {}).get("title", "")
        self.description = spec.get("info", {}).get("description", "")
        self.servers = spec.get("servers", [])
        self.components = spec.get("components", {}).get("schemas", {})
        
        # Swagger 2.0 使用 definitions 而不是 components/schemas
        if self.spec_type == "swagger" and self.major_version == "2.0":
            self.components = spec.get("definitions", {})
        
        self.base_path = self._extract_base_path()
    
    def _determine_spec_version(self):
        """确定规范类型和版本"""
        # 获取版本字符串
        openapi_version = self.spec.get("openapi")
        swagger_version = self.spec.get("swagger")
        
        if openapi_version:
            self.spec_type = "openapi"
            self.version = openapi_version
            # 提取主版本号（如 3.0, 3.1）
            parts = openapi_version.split(".")
            if len(parts) >= 2:
                self.major_version = f"{parts[0]}.{parts[1]}"
            else:
                self.major_version = "3.0"
        elif swagger_version:
            self.spec_type = "swagger"
            self.version = swagger_version
            self.major_version = swagger_version  # 对于 Swagger，主版本就是完整版本
        else:
            # 默认假设为 OpenAPI 3.0
            self.spec_type = "openapi"
            self.version = "3.0.0"
            self.major_version = "3.0"
    
    def _extract_base_path(self) -> str:
        """提取基础路径（版本感知）"""
        # OpenAPI 3.x 使用 servers
        if self.spec_type == "openapi" and self.major_version.startswith("3."):
            if self.servers:
                return self.servers[0].get("url", "")
            return ""
        
        # Swagger 2.0 使用 host + basePath + schemes
        if self.spec_type == "swagger" and self.major_version == "2.0":
            host = self.spec.get("host", "")
            base_path = self.spec.get("basePath", "")
            schemes = self.spec.get("schemes", ["https"])
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
            
            # 验证 URL 是否有效
            full_url = config.get("url", "")
            if not full_url or full_url.strip() == "":
                logger.warning(f"Skipping operation {method} {path}: URL is empty. Ensure servers/host is configured in OpenAPI spec")
                return None
            
            return {
                "tool_name": tool_name,
                "tool_code": self._generate_tool_code(path, method),
                "description": description,
                "category": "external",
                "is_enabled": True,
                "is_public": True,
                "input_schema": input_schema,
                "output_schema": output_schema,
                # 新的明确字段
                "tool_type": "url",
                "protocol": "http",
                "request_method": method.upper(),
                "url": full_url,
                "auth_type": "none",
                "auth_info": "",
                "need_summary": False,
                "prompt": "",
                # 保留兼容性字段
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
        构建输入参数 Schema（版本感知）
        
        Args:
            parameters: 查询参数、路径参数列表
            request_body: 请求体定义
        
        Returns:
            JSON Schema 格式的输入定义
        """
        properties = {}
        required = []
        
        # 处理参数（版本感知）
        for param in parameters:
            param_name = param.get("name", "")
            param_in = param.get("in", "query")
            
            # OpenAPI 3.x: 支持 query, path
            # Swagger 2.0: 支持 query, path, formData
            supported_locations = ["query", "path"]
            if self.spec_type == "swagger" and self.major_version == "2.0":
                supported_locations.append("formData")
            
            if param_in in supported_locations:
                prop = self._convert_parameter_to_schema(param)
                if prop:
                    properties[param_name] = prop
                    if param.get("required", False):
                        required.append(param_name)
        
        # 处理请求体（版本感知）
        if request_body:
            self._parse_request_body(request_body, properties, required)
        
        return {
            "type": "object",
            "properties": properties,
            "required": required
        }
    
    def _parse_request_body(self, request_body: Dict[str, Any], properties: Dict, required: List):
        """
        解析请求体（版本感知）
        
        Args:
            request_body: 请求体定义
            properties: 输出属性字典
            required: 输出必填字段列表
        """
        # OpenAPI 3.x 使用 content 字段
        if self.spec_type == "openapi" and self.major_version.startswith("3."):
            content = request_body.get("content", {})
            # 优先处理 application/json
            if "application/json" in content:
                schema = content["application/json"].get("schema", {})
                self._merge_schema_properties(schema, properties, required)
            elif content:
                # 尝试其他内容类型
                for content_type, media_type in content.items():
                    if media_type.get("schema"):
                        self._merge_schema_properties(media_type["schema"], properties, required)
                        break
        
        # Swagger 2.0 使用 schema 直接在 requestBody 中
        elif self.spec_type == "swagger" and self.major_version == "2.0":
            schema = request_body.get("schema", {})
            self._merge_schema_properties(schema, properties, required)
    
    def _merge_schema_properties(self, schema: Dict[str, Any], properties: Dict, required: List):
        """
        合并 Schema 属性到输入定义
        
        Args:
            schema: Schema 定义
            properties: 输出属性字典
            required: 输出必填字段列表
        """
        if schema.get("type") == "object" and schema.get("properties"):
            for prop_name, prop_def in schema["properties"].items():
                resolved_prop = self._resolve_schema(prop_def)
                properties[prop_name] = resolved_prop
                if schema.get("required") and prop_name in schema["required"]:
                    required.append(prop_name)
        elif "$ref" in schema:
            # 如果是引用，解析引用后合并
            ref_schema = self._resolve_schema(schema)
            if ref_schema:
                self._merge_schema_properties(ref_schema, properties, required)
    
    def _convert_parameter_to_schema(self, param: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """将参数定义转换为 Schema 属性（版本感知）
        
        OpenAPI 3.x: 参数类型定义在 schema 字段中
        Swagger 2.0: 参数类型直接在参数定义中
        """
        # 根据版本获取 schema
        if self.spec_type == "openapi" and self.major_version.startswith("3."):
            # OpenAPI 3.x：类型在 schema 中
            schema = param.get("schema", {})
            param_type = schema.get("type", "string")
            items = schema.get("items")
            enum_values = schema.get("enum")
            default_value = schema.get("default")
            format_value = schema.get("format")
        else:
            # Swagger 2.0：类型直接在参数中
            schema = {}
            param_type = param.get("type", "string")
            items = param.get("items")
            enum_values = param.get("enum")
            default_value = param.get("default")
            format_value = param.get("format")
        
        prop = {
            "type": self._convert_type(param_type),
            "description": param.get("description", "")
        }
        
        # 处理数组类型
        if param_type == "array" and items:
            prop["items"] = self._resolve_schema(items)
        
        # 添加枚举值
        if enum_values:
            prop["enum"] = enum_values
        
        # 添加默认值
        if default_value is not None:
            prop["default"] = default_value
        
        # 添加格式信息
        if format_value:
            prop["format"] = format_value
        
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
        构建输出 Schema（版本感知）
        
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
                schema = self._parse_response_schema(response)
                if schema:
                    return schema
        
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
    
    def _parse_response_schema(self, response: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        解析响应 Schema（版本感知）
        
        OpenAPI 3.x: 使用 content 字段
        Swagger 2.0: 使用 schema 字段直接在响应中
        
        Args:
            response: 响应定义
        
        Returns:
            JSON Schema，如果无法解析返回 None
        """
        # OpenAPI 3.x 使用 content 字段
        if self.spec_type == "openapi" and self.major_version.startswith("3."):
            content = response.get("content", {})
            if content:
                # 优先处理 application/json
                if "application/json" in content:
                    schema = content["application/json"].get("schema")
                    if schema:
                        return self._resolve_schema(schema)
                
                # 尝试其他常见内容类型
                content_types = ["application/xml", "text/json", "text/plain", "*/*"]
                for ct in content_types:
                    if ct in content:
                        schema = content[ct].get("schema")
                        if schema:
                            return self._resolve_schema(schema)
        
        # Swagger 2.0 使用 schema 字段直接在响应中
        elif self.spec_type == "swagger" and self.major_version == "2.0":
            schema = response.get("schema")
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
        
        return None
    
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


def parse_openapi_spec(content: str) -> Tuple[Optional[OpenAPIParser], Optional[str]]:
    """
    解析 OpenAPI 规范内容（带版本校验）
    
    Args:
        content: OpenAPI 规范字符串（JSON 或 YAML 格式）
    
    Returns:
        (parser, error_message) 元组
        parser: OpenAPIParser 实例，如果解析/校验失败返回 None
        error_message: 错误信息，如果成功返回 None
    """
    # 先进行完整校验
    is_valid, error_msg, spec = validate_openapi_spec(content)
    if not is_valid:
        logger.error(f"OpenAPI spec validation failed: {error_msg}")
        return None, error_msg
    
    try:
        return OpenAPIParser(spec), None
    except Exception as e:
        logger.error(f"Failed to create OpenAPIParser: {e}", exc_info=True)
        return None, str(e)


def generate_tools_from_openapi(content: str) -> Tuple[List[Dict[str, Any]], Optional[str]]:
    """
    从 OpenAPI 规范内容生成工具定义列表（带版本校验）
    
    Args:
        content: OpenAPI 规范字符串（JSON 或 YAML 格式）
    
    Returns:
        (tools, error_message) 元组
        tools: 工具定义列表，如果解析/校验失败返回空列表
        error_message: 错误信息，如果成功返回 None
    """
    parser, error_msg = parse_openapi_spec(content)
    if not parser:
        return [], error_msg
    
    return parser.parse_all_endpoints(), None


def get_supported_versions() -> Dict[str, List[str]]:
    """
    获取支持的 OpenAPI 版本信息
    
    Returns:
        支持的版本字典，格式: {"openapi": ["3.0", "3.1"], "swagger": ["2.0"]}
    """
    return SUPPORTED_VERSIONS.copy()


def _clean_yaml_indentation(content: str) -> str:
    """
    清理 YAML 内容中的缩进问题
    
    问题场景：第一行顶级键后面的同级键可能有多余的缩进，
    如:
        openapi: "3.0.3" 
         info:   <- 这里多了一个空格
    
    Args:
        content: 原始 YAML 字符串
    
    Returns:
        清理后的 YAML 字符串
    """
    if not content:
        return content
    
    lines = content.split('\n')
    if len(lines) < 2:
        return content
    
    # 获取第一行（去除首尾空白）
    first_line = lines[0].rstrip()
    
    # 检查第一行是否是顶级键（没有前导空格且包含冒号）
    if first_line and not first_line[0].isspace() and ':' in first_line:
        # 分析第一行的结构
        first_key = first_line.split(':')[0].strip()
        
        # 检查第二行是否有多余的缩进
        second_line = lines[1] if len(lines) > 1 else ''
        
        # 如果第二行以空格开头且看起来是顶级键（包含冒号）
        if second_line and second_line[0].isspace() and ':' in second_line:
            # 获取第二行的内容（去除前导空格）
            stripped_second = second_line.lstrip()
            second_key = stripped_second.split(':')[0].strip()
            
            # 如果看起来是顶级键，尝试修复缩进
            if second_key and second_key.isidentifier():
                # 检查是否所有行都有类似的缩进问题
                # 统计第二行的前导空格数
                leading_spaces = len(second_line) - len(stripped_second)
                
                if leading_spaces > 0:
                    # 尝试去除所有行开头的这些空格
                    cleaned_lines = []
                    for i, line in enumerate(lines):
                        if i == 0:
                            cleaned_lines.append(line)
                        elif len(line) >= leading_spaces and line[:leading_spaces] == ' ' * leading_spaces:
                            cleaned_lines.append(line[leading_spaces:])
                        else:
                            cleaned_lines.append(line)
                    
                    return '\n'.join(cleaned_lines)
    
    return content
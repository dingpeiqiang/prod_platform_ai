"""
外部 API 工具执行器
用于执行通过配置定义的外部 HTTP API 工具
"""
import requests
import json
import time
import logging
from typing import Dict, Any, Optional
from jinja2 import Template

logger = logging.getLogger("external_api_executor")


class ExternalAPIExecutor:
    """外部 API 执行器"""
    
    def __init__(self, tool_config: Dict[str, Any]):
        """
        初始化工具执行器
        
        Args:
            tool_config: 工具配置字典，包含 method, url, headers, params 等
        """
        self.config = tool_config
        self.method = tool_config.get("method", "GET").upper()
        self.base_url = tool_config.get("url", "")
        self.headers_template = tool_config.get("headers", {})
        self.params_template = tool_config.get("params", {})
        self.body_template = tool_config.get("body", None)
        self.timeout = tool_config.get("timeout_seconds", 30)
        self.retry_count = tool_config.get("retry_count", 0)
        self.output_mapping = tool_config.get("output_mapping", {})
    
    def execute(self, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """
        执行外部 API 调用
        
        Args:
            arguments: 用户传入的参数
            
        Returns:
            执行结果字典
        """
        start_time = time.time()
        
        try:
            # 1. 渲染 URL、Headers、Params、Body（支持 Jinja2 模板）
            url = self._render_template(self.base_url, arguments)
            headers = self._render_dict(self.headers_template, arguments)
            params = self._render_dict(self.params_template, arguments)
            
            body = None
            if self.body_template:
                if isinstance(self.body_template, str):
                    body = self._render_template(self.body_template, arguments)
                    # 尝试解析为 JSON
                    try:
                        body = json.loads(body)
                    except:
                        pass
                else:
                    body = self._render_dict(self.body_template, arguments)
            
            # 2. 执行 HTTP 请求（带重试）
            last_error = None
            for attempt in range(self.retry_count + 1):
                try:
                    response = requests.request(
                        method=self.method,
                        url=url,
                        headers=headers,
                        params=params,
                        json=body if self.method in ["POST", "PUT", "PATCH"] else None,
                        timeout=self.timeout
                    )
                    
                    # 检查响应状态
                    response.raise_for_status()
                    
                    # 3. 解析响应并映射输出
                    result_data = response.json() if response.content else {}
                    mapped_result = self._map_output(result_data)
                    
                    elapsed_ms = (time.time() - start_time) * 1000
                    
                    return {
                        "success": True,
                        "result": mapped_result,
                        "execution_time_ms": round(elapsed_ms, 2),
                        "status_code": response.status_code
                    }
                    
                except requests.exceptions.RequestException as e:
                    last_error = str(e)
                    logger.warning(f"Attempt {attempt + 1} failed: {e}")
                    
                    if attempt < self.retry_count:
                        time.sleep(2 ** attempt)  # 指数退避
                    continue
            
            # 所有重试都失败
            elapsed_ms = (time.time() - start_time) * 1000
            return {
                "success": False,
                "error": f"All {self.retry_count + 1} attempts failed. Last error: {last_error}",
                "execution_time_ms": round(elapsed_ms, 2)
            }
            
        except Exception as e:
            elapsed_ms = (time.time() - start_time) * 1000
            logger.error(f"External API execution failed: {e}", exc_info=True)
            return {
                "success": False,
                "error": str(e),
                "execution_time_ms": round(elapsed_ms, 2)
            }
    
    def _render_template(self, template_str: str, context: Dict[str, Any]) -> str:
        """渲染 Jinja2 模板"""
        try:
            template = Template(template_str)
            return template.render(**context)
        except Exception as e:
            logger.error(f"Template rendering failed: {e}")
            return template_str
    
    def _render_dict(self, data: Dict[str, Any], context: Dict[str, Any]) -> Dict[str, Any]:
        """渲染字典中的所有字符串值"""
        result = {}
        for key, value in data.items():
            if isinstance(value, str):
                result[key] = self._render_template(value, context)
            elif isinstance(value, dict):
                result[key] = self._render_dict(value, context)
            elif isinstance(value, list):
                result[key] = [
                    self._render_template(item, context) if isinstance(item, str) else item
                    for item in value
                ]
            else:
                result[key] = value
        return result
    
    def _map_output(self, raw_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        根据 output_mapping 映射输出
        
        支持简单的 JSONPath 语法：
        - "$.field" -> raw_data["field"]
        - "$.nested.field" -> raw_data["nested"]["field"]
        """
        if not self.output_mapping:
            return raw_data
        
        mapped = {}
        for output_key, json_path in self.output_mapping.items():
            value = self._extract_by_path(raw_data, json_path)
            if value is not None:
                mapped[output_key] = value
        
        return mapped
    
    def _extract_by_path(self, data: Dict[str, Any], path: str) -> Any:
        """根据 JSONPath 提取值"""
        if not path.startswith("$."):
            return data.get(path)
        
        # 移除 "$." 前缀
        keys = path[2:].split(".")
        current = data
        
        for key in keys:
            if isinstance(current, dict):
                current = current.get(key)
                if current is None:
                    return None
            else:
                return None
        
        return current


def create_external_tool_handler(tool_definition: Dict[str, Any]):
    """
    创建外部工具处理函数
    
    Args:
        tool_definition: 工具定义（从数据库读取）
        
    Returns:
        可调用的处理函数
    """
    def handler(arguments: Dict[str, Any]) -> Dict[str, Any]:
        config = tool_definition.get("config", {})
        executor = ExternalAPIExecutor(config)
        return executor.execute(arguments)
    
    return handler

"""
HTTP 请求节点

输入参数：
- url: 请求地址（必填）
- method: HTTP 方法（可选，默认 GET）
- headers: 请求头（可选）
- body: 请求体（可选）

输出结果：
- status_code: HTTP 状态码
- response: 响应内容
- headers: 响应头
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import json


@register_node
class HTTPRequestNode(WorkflowNode):
    """HTTP 请求节点"""
    
    name = "workflow.http_request"
    description = "发送 HTTP 请求"
    inputs = {
        "url": {"type": "str", "required": True, "description": "请求地址"},
        "method": {"type": "str", "required": False, "description": "HTTP 方法", "default": "GET"},
        "headers": {"type": "dict", "required": False, "description": "请求头", "default": {}},
        "body": {"type": "dict", "required": False, "description": "请求体", "default": {}}
    }
    outputs = {
        "status_code": {"type": "int", "description": "HTTP 状态码"},
        "response": {"type": "str", "description": "响应内容"},
        "headers": {"type": "dict", "description": "响应头"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        url = kwargs.get("url", "")
        method = kwargs.get("method", "GET").upper()
        headers = kwargs.get("headers", {})
        body = kwargs.get("body", {})
        
        # 记录输入信息
        self._log_input(url=url, method=method, headers=json.dumps(headers), body=json.dumps(body))
        
        # 构建处理逻辑描述
        processing = f"执行 {method} 请求到 URL '{url}'，发送请求体 {list(body.keys())}"
        self._log_processing(processing)
        
        if not url:
            error_msg = "URL is required"
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"url": url, "method": method, "headers": headers, "body": body},
                "processing": processing,
                "output": {"error": error_msg}
            }
        
        try:
            import httpx
            
            async with httpx.AsyncClient() as client:
                if method == "GET":
                    response = await client.get(url, headers=headers, params=body)
                elif method == "POST":
                    response = await client.post(url, headers=headers, json=body)
                elif method == "PUT":
                    response = await client.put(url, headers=headers, json=body)
                elif method == "DELETE":
                    response = await client.delete(url, headers=headers)
                else:
                    error_msg = f"Unsupported method: {method}"
                    self._log_output(success=False, error=error_msg)
                    
                    return {
                        "success": False,
                        "error": error_msg,
                        "input": {"url": url, "method": method, "headers": headers, "body": body},
                        "processing": processing,
                        "output": {"error": error_msg}
                    }
            
            output = {
                "success": True,
                "status_code": response.status_code,
                "response": response.text,
                "headers": dict(response.headers),
                "input": {"url": url, "method": method, "headers": headers, "body": body},
                "processing": processing,
                "output": {"status_code": response.status_code, "response": response.text[:200]}
            }
            
            # 记录输出信息
            self._log_output(success=True, status_code=response.status_code, response=response.text)
            
            return output
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"url": url, "method": method, "headers": headers, "body": body},
                "processing": processing,
                "output": {"error": error_msg}
            }
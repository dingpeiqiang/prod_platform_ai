"""
HTTP 请求节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import json


@register_node
class HTTPRequestNode(WorkflowNode):
    """HTTP 请求节点"""

    name = "workflow.http_request"
    display_name = "HTTP 请求"
    description = "发送 HTTP 请求"
    config_fields = {
        "url": ParamSchema(type="str", required=True, description="请求地址"),
        "method": ParamSchema(type="str", required=False, description="HTTP 方法", default="GET"),
        "headers": ParamSchema(type="dict", required=False, description="请求头", default={}),
        "body": ParamSchema(type="dict", required=False, description="请求体", default={}),
    }
    output_fields = {
        "status_code": ParamSchema(type="int", description="HTTP 状态码"),
        "response": ParamSchema(type="str", description="响应内容"),
        "headers": ParamSchema(type="dict", description="响应头"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        url = execution.get("url", "")
        method = execution.get("method", "GET").upper()
        headers = execution.get("headers", {})
        body = execution.get("body", {})

        self._log_input(url=url, method=method, headers=json.dumps(headers), body=json.dumps(body))
        processing = f"执行 {method} 请求到 URL '{url}'"
        self._log_processing(processing)

        if not url:
            execution.set("status_code", 0)
            execution.set("response", "")
            execution.set("headers", {})
            execution.set("error", "URL is required")
            self._log_output(success=False, error="URL is required")
            return

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
                    execution.set("status_code", 0)
                    execution.set("response", "")
                    execution.set("headers", {})
                    execution.set("error", f"Unsupported method: {method}")
                    self._log_output(success=False, error=f"Unsupported method: {method}")
                    return

            execution.set("status_code", response.status_code)
            execution.set("response", response.text)
            execution.set("headers", dict(response.headers))
            self._log_output(success=True, status_code=response.status_code, response=response.text)

        except Exception as e:
            error_msg = str(e)
            execution.set("status_code", 0)
            execution.set("response", "")
            execution.set("headers", {})
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)
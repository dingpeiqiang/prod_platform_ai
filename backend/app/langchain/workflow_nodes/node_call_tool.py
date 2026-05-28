"""
工具调用节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import json


@register_node
class CallToolNode(WorkflowNode):
    """工具调用节点"""

    name = "workflow.call_tool"
    display_name = "调用工具"
    description = "调用工具执行特定操作"
    config_fields = {
        "tool_name": ParamSchema(type="str", required=True, description="工具名称"),
        "tool_type": ParamSchema(type="str", required=False, description="工具类型"),
        "params": ParamSchema(type="dict", required=False, description="工具参数", default={}),
    }
    output_fields = {
        "result": ParamSchema(type="any", description="工具执行结果"),
        "tool_name": ParamSchema(type="str", description="使用的工具名称"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        tool_name = execution.get("tool_name", "")
        tool_type = execution.get("tool_type", "")
        params = execution.get("params", {})

        self._log_input(tool_name=tool_name, tool_type=tool_type,
                       params=json.dumps(params, ensure_ascii=False))
        processing = f"通过 ToolHub 调用工具 '{tool_name}'，传入参数 {list(params.keys())}"
        self._log_processing(processing)

        try:
            from app.mcp_tools.tool_hub import get_toolhub
            tool_hub = get_toolhub()
            result = tool_hub.execute_sync(tool_name, params)

            execution.set("result", result)
            execution.set("tool_name", tool_name)
            self._log_output(success=True, tool_name=tool_name, result=str(result))

        except Exception as e:
            error_msg = str(e)
            execution.set("result", None)
            execution.set("tool_name", tool_name)
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)
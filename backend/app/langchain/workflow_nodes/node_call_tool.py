import json
from typing import Any, Dict
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class CallToolNode(WorkflowNode):
    name = "workflow.call_tool"
    display_name = "调用工具"
    description = "调用工具执行特定操作"
    config_fields = {
        "tool_name": ParamSchema(type="str", required=True, description="工具名称"),
        "tool_type": ParamSchema(type="str", required=False, description="工具类型"),
        "inputParams": ParamSchema(type="list", required=False, description="输入参数列表", default=[]),
    }
    output_fields = {
        "result": ParamSchema(type="any", description="工具执行结果"),
        "tool_name": ParamSchema(type="str", description="使用的工具名称"),
        "action": ParamSchema(type="str", description="动作类型"),
        "error": ParamSchema(type="str", description="错误信息"),
    }

    @staticmethod
    def _params_list_to_dict(raw_params: Any) -> Dict[str, Any]:
        if isinstance(raw_params, dict):
            return raw_params
        if isinstance(raw_params, list):
            result = {}
            for item in raw_params:
                if isinstance(item, dict):
                    key = item.get("name") or ""
                    val = item.get("value") or item.get("val") or ""
                    if key:
                        result[key] = val
            return result
        return {}

    async def execute(self, execution: DelegateExecution) -> None:
        tool_name = execution.get("tool_name", "")
        tool_type = execution.get("tool_type", "")
        raw_params = execution.get("inputParams", [])
        params = self._params_list_to_dict(raw_params)

        self._log_input(tool_name=tool_name, tool_type=tool_type,
                       params=json.dumps(params, ensure_ascii=False))
        processing = "通过 ToolHub 调用工具 '%s'，传入参数 %s" % (tool_name, list(params.keys()))
        self._log_processing(processing)

        try:
            from app.mcp_tools.tool_hub import get_toolhub
            tool_hub = get_toolhub()
            result = tool_hub.execute_sync(tool_name, params)

            execution.set("result", result)
            execution.set("tool_name", tool_name)
            
            # 检查工具执行是否成功
            if isinstance(result, dict) and result.get("success") == False:
                error_msg = result.get("error", "工具执行失败")
                execution.set("error", error_msg)
                execution.set("action", "error")
                self._log_output(success=False, error=error_msg)
            else:
                self._log_output(success=True, tool_name=tool_name, result=str(result))

        except Exception as e:
            error_msg = str(e)
            execution.set("result", None)
            execution.set("tool_name", tool_name)
            execution.set("error", error_msg)
            execution.set("action", "error")
            self._log_output(success=False, error=error_msg)
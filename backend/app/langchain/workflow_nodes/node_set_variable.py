from typing import Any, Dict
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class SetVariableNode(WorkflowNode):
    name = "workflow.set_variable"
    display_name = "设置变量"
    description = "设置工作流上下文变量"
    has_dynamic_output = True
    config_fields = {
        "variable_name": ParamSchema(type="str", required=True, description="变量名称"),
        "variable_value": ParamSchema(type="any", required=True, description="变量值"),
    }
    output_fields = {
        "variable_name": ParamSchema(type="str", description="变量名称"),
        "variable_value": ParamSchema(type="any", description="变量值"),
        "previous_value": ParamSchema(type="any", description="变量之前的值"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        var_name = execution.get("variable_name", "")
        var_value = execution.get("variable_value", "")

        self._log_input(variable_name=var_name, variable_value=var_value)

        previous_value = execution.get(var_name, "（未设置）")
        processing = "设置变量 '%s'，原值: %s，新值: %s" % (var_name, previous_value, var_value)
        self._log_processing(processing)

        if not var_name:
            self._log_output(success=False, error="variable_name is required")
            return

        execution.set(var_name, var_value)
        execution.set("variable_name", var_name)
        execution.set("variable_value", var_value)
        execution.set("previous_value", previous_value)

        self._log_output(success=True, variable_name=var_name, variable_value=var_value,
                        previous_value=previous_value)

    def get_dynamic_outputs(self, config_data: Dict[str, Any]) -> Dict[str, ParamSchema]:
        var_name = config_data.get("variable_name", "")
        if var_name:
            return {var_name: ParamSchema(type="any", description="动态变量 " + var_name)}
        return {}
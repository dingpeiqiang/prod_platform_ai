"""
设置变量节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class SetVariableNode(WorkflowNode):
    """设置变量节点

    动态输出：用户配置的 variable_name 即为输出变量名。
    """

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
        variable_name = execution.get("variable_name", "")
        variable_value = execution.get("variable_value", "")

        self._log_input(variable_name=variable_name, variable_value=variable_value)

        previous_value = execution.get(variable_name, "（未设置）")
        processing = f"设置变量 '{variable_name}'，原值: {previous_value}，新值: {variable_value}"
        self._log_processing(processing)

        if not variable_name:
            self._log_output(success=False, error="variable_name is required")
            return

        execution.set(variable_name, variable_value)
        execution.set("variable_name", variable_name)
        execution.set("variable_value", variable_value)
        execution.set("previous_value", previous_value)

        self._log_output(success=True, variable_name=variable_name, variable_value=variable_value,
                        previous_value=previous_value)

    def get_dynamic_outputs(self, config_data: Dict[str, Any]) -> Dict[str, ParamSchema]:
        var_name = config_data.get("variable_name", "")
        if var_name:
            return {var_name: ParamSchema(type="any", description=f"动态变量 {var_name}")}
        return {}
"""
询问用户节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class AskUserNode(WorkflowNode):
    """询问用户节点

    通过 action=ask_user 信号通知引擎暂停工作流等待用户输入。
    """

    name = "workflow.ask_user"
    display_name = "询问用户"
    description = "向用户提问并等待输入"
    config_fields = {
        "message": ParamSchema(type="str", required=True, description="提示消息"),
        "required_fields": ParamSchema(type="list", required=False, description="必填字段列表", default=[]),
    }
    output_fields = {
        "action": ParamSchema(type="str", description="动作类型（固定为 ask_user，触发引擎暂停）"),
        "message": ParamSchema(type="str", description="提示消息"),
        "required_fields": ParamSchema(type="list", description="必填字段列表"),
        "waiting_for_input": ParamSchema(type="bool", description="是否等待用户输入"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        message = execution.get("message", "请提供信息")
        required_fields = execution.get("required_fields", [])

        self._log_input(message=message, required_fields=required_fields)
        processing = f"构建用户询问消息，要求用户提供 {len(required_fields)} 个必填字段"
        self._log_processing(processing)

        execution.set("action", "ask_user")
        execution.set("message", message)
        execution.set("required_fields", required_fields)
        execution.set("waiting_for_input", True)

        self._log_output(action="ask_user", message=message, required_fields=required_fields)
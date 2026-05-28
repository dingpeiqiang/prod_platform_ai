"""
工作流开始节点

输入参数：无

输出结果：
- status: 状态 (started)
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class WorkflowStartNode(WorkflowNode):
    """工作流开始节点"""

    name = "workflow.start"
    display_name = "开始"
    description = "开始工作流"
    config_fields = {}
    output_fields = {
        "status": ParamSchema(type="str", description="状态"),
        "message": ParamSchema(type="str", description="消息"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        processing = "工作流开始执行"
        self._log_processing(processing)

        execution.set("status", "started")
        execution.set("message", "工作流已开始")

        self._log_output(success=True, status="started", message="工作流已开始")
"""
工作流结束节点

输入参数：无

输出结果：
- status: 状态 (completed)
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class WorkflowEndNode(WorkflowNode):
    """工作流结束节点"""

    name = "workflow.end"
    display_name = "结束"
    description = "结束工作流"
    config_fields = {}
    output_fields = {
        "status": ParamSchema(type="str", description="状态"),
        "message": ParamSchema(type="str", description="消息"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        processing = "工作流执行完成"
        self._log_processing(processing)

        execution.set("status", "completed")
        execution.set("message", "工作流已完成")

        self._log_output(success=True, status="completed", message="工作流已完成")
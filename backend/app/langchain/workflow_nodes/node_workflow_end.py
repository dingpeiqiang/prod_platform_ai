"""
工作流结束节点

输入参数：无

输出结果：
- status: 状态 (completed)
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class WorkflowEndNode(WorkflowNode):
    """工作流结束节点"""
    
    name = "workflow.end"
    description = "结束工作流"
    inputs = {}
    outputs = {
        "status": {"type": "str", "description": "状态"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        processing = "工作流执行完成"
        self._log_processing(processing)
        
        result = {
            "success": True,
            "status": "completed",
            "message": "工作流已完成",
            "input": {},
            "processing": processing,
            "output": {"status": "completed", "message": "工作流已完成"}
        }
        
        self._log_output(success=True, status="completed", message="工作流已完成")
        
        return result
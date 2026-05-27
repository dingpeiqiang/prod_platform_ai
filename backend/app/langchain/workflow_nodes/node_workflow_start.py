"""
工作流开始节点

输入参数：无

输出结果：
- status: 状态 (started)
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class WorkflowStartNode(WorkflowNode):
    """工作流开始节点"""
    
    name = "workflow.start"
    description = "开始工作流"
    inputs = {}
    outputs = {
        "status": {"type": "str", "description": "状态"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        processing = "工作流开始执行"
        self._log_processing(processing)
        
        result = {
            "success": True,
            "status": "started",
            "message": "工作流已开始",
            "input": {},
            "processing": processing,
            "output": {"status": "started", "message": "工作流已开始"}
        }
        
        self._log_output(success=True, status="started", message="工作流已开始")
        
        return result
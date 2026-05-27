"""
询问用户节点

输入参数：
- message: 提示消息（必填）
- required_fields: 必填字段列表（可选）

输出结果：
- message: 提示消息
- required_fields: 必填字段列表
- waiting_for_input: 是否等待用户输入（始终为 True）
"""
from typing import Dict, Any, List
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class AskUserNode(WorkflowNode):
    """询问用户节点"""
    
    name = "workflow.ask_user"
    description = "向用户提问并等待输入"
    inputs = {
        "message": {"type": "str", "required": True, "description": "提示消息"},
        "required_fields": {"type": "list", "required": False, "description": "必填字段列表", "default": []}
    }
    outputs = {
        "message": {"type": "str", "description": "提示消息"},
        "required_fields": {"type": "list", "description": "必填字段列表"},
        "waiting_for_input": {"type": "bool", "description": "是否等待用户输入"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        message = kwargs.get("message", "请提供信息")
        required_fields = kwargs.get("required_fields", [])
        
        # 记录输入信息
        self._log_input(message=message, required_fields=required_fields)
        
        # 构建处理逻辑描述
        processing = f"构建用户询问消息，要求用户提供 {len(required_fields)} 个必填字段"
        self._log_processing(processing)
        
        result = {
            "action": "ask_user",
            "message": message,
            "required_fields": required_fields,
            "waiting_for_input": True,
            "input": {"message": message, "required_fields": required_fields},
            "processing": processing,
            "output": {"message": message, "required_fields": required_fields, "waiting_for_input": True}
        }
        
        # 记录输出信息
        self._log_output(action="ask_user", message=message, required_fields=required_fields)
        
        return result
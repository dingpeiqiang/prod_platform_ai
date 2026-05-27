"""
处理缺失字段节点

输入参数：无（从上下文获取数据）

输出结果：
- action: 动作类型（ask_user 或 continue）
- missing_fields: 缺失字段列表
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class HandleMissingFieldsNode(WorkflowNode):
    """处理缺失字段节点"""
    
    name = "workflow.handle_missing_fields"
    description = "根据校验结果处理缺失字段"
    inputs = {}
    outputs = {
        "action": {"type": "str", "description": "动作类型"},
        "missing_fields": {"type": "list", "description": "缺失字段列表"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "根据校验结果处理缺失字段"
        self._log_processing(processing)
        
        # 从上下文获取校验结果
        validate_result = context.step_results.get("validate_form", {})
        if not validate_result:
            validate_result = context.outputs.get("validation_result", {})
        
        validation_results = validate_result.get("validationResults", [])
        
        # 提取错误字段
        missing_fields = []
        for result in validation_results:
            if result.get("result") == "error":
                missing_fields.append({
                    "field": result.get("field"),
                    "fieldName": result.get("fieldName"),
                    "reason": result.get("reason"),
                    "suggestion": result.get("suggestion")
                })
        
        if missing_fields:
            result = {
                "success": True,
                "action": "ask_user",
                "missing_fields": missing_fields,
                "message": f"请补充以下 {len(missing_fields)} 个必填字段",
                "input": {},
                "processing": processing,
                "output": {
                    "action": "ask_user",
                    "missing_field_count": len(missing_fields)
                }
            }
            
            # 记录输出信息
            self._log_output(success=True, action="ask_user", missing_field_count=len(missing_fields))
            
            return result
        
        result = {
            "success": True,
            "action": "continue",
            "missing_fields": [],
            "message": "所有字段校验通过",
            "input": {},
            "processing": processing,
            "output": {
                "action": "continue",
                "missing_field_count": 0
            }
        }
        
        # 记录输出信息
        self._log_output(success=True, action="continue", message="所有字段校验通过")
        
        return result
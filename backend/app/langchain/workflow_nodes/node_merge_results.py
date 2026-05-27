"""
结果合并节点

输入参数：无（从上下文获取数据）

输出结果：
- action: 动作类型
- formCode: 表单编码
- form_schema: 表单结构
- extractedFields: 提取的字段数据
- validationResults: 校验结果
- summary: 校验摘要
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class MergeResultsNode(WorkflowNode):
    """结果合并节点"""
    
    name = "workflow.merge_results"
    description = "合并表单生成和校验结果"
    inputs = {}
    outputs = {
        "action": {"type": "str", "description": "动作类型"},
        "formCode": {"type": "str", "description": "表单编码"},
        "form_schema": {"type": "dict", "description": "表单结构"},
        "extractedFields": {"type": "dict", "description": "提取的字段数据"},
        "validationResults": {"type": "list", "description": "校验结果"},
        "summary": {"type": "dict", "description": "校验摘要"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "合并表单生成和校验结果"
        self._log_processing(processing)
        
        # 从上下文获取数据
        form_result = context.step_results.get("generate_form", {})
        if not form_result:
            form_result = context.outputs.get("form_result", {})
        
        validate_result = context.step_results.get("validate_form", {})
        if not validate_result:
            validate_result = context.outputs.get("validation_result", {})
        
        result = {
            "success": True,
            "action": form_result.get("action", "generate_form"),
            "formCode": form_result.get("formCode") or form_result.get("ontology_code"),
            "form_schema": form_result.get("form_schema"),
            "extractedFields": form_result.get("extractedFields", {}),
            "validationResults": validate_result.get("validationResults", []),
            "summary": validate_result.get("summary"),
            "message": validate_result.get("message", form_result.get("message")),
            "input": {},
            "processing": processing,
            "output": {
                "action": form_result.get("action", "generate_form"),
                "formCode": form_result.get("formCode") or form_result.get("ontology_code"),
                "field_count": len(form_result.get("extractedFields", {})),
                "validation_errors": validate_result.get("summary", {}).get("errors", 0)
            }
        }
        
        # 记录输出信息
        self._log_output(
            success=True,
            formCode=result["formCode"],
            action=result["action"],
            field_count=len(result["extractedFields"])
        )
        
        return result
"""
资费备案 - 结果合并节点

输入参数：无（从上下文获取数据）

输出结果：
- action: 动作类型
- formCode: 表单编码
- extractedFields: 提取的字段
- validationResults: 校验结果
- summary: 校验摘要
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class TariffMergeResultsNode(WorkflowNode):
    """资费备案结果合并节点"""
    
    name = "tariff.merge_results"
    description = "合并资费备案表单和校验结果"
    _legacy = True
    inputs = {}
    outputs = {
        "action": {"type": "str", "description": "动作类型"},
        "formCode": {"type": "str", "description": "表单编码"},
        "extractedFields": {"type": "dict", "description": "提取的字段"},
        "validationResults": {"type": "list", "description": "校验结果"},
        "summary": {"type": "dict", "description": "校验摘要"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "合并资费备案表单和校验结果"
        self._log_processing(processing)
        
        # 从上下文获取数据
        form_result = context.step_results.get("generate_form", {})
        validate_result = context.step_results.get("validate_form", {})
        
        result = {
            "success": True,
            "action": form_result.get("action", "generate_form"),
            "formCode": form_result.get("formCode"),
            "extractedFields": form_result.get("extractedFields", {}),
            "validationResults": validate_result.get("validationResults", []),
            "summary": validate_result.get("summary"),
            "message": validate_result.get("message", form_result.get("message")),
            "input": {},
            "processing": processing,
            "output": {
                "formCode": form_result.get("formCode"),
                "field_count": len(form_result.get("extractedFields", {})),
                "validation_errors": validate_result.get("summary", {}).get("errors", 0)
            }
        }
        
        self._log_output(
            success=True,
            formCode=result["formCode"],
            field_count=len(result["extractedFields"])
        )
        
        return result
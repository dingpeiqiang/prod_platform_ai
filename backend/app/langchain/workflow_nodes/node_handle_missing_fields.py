"""
处理缺失字段节点

输入参数：无（从上下文获取数据）

输出结果：
- action: 动作类型（ask_user 或 continue）
- missing_fields: 缺失字段列表
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class HandleMissingFieldsNode(WorkflowNode):
    """处理缺失字段节点"""

    name = "workflow.handle_missing_fields"
    display_name = "处理缺失字段"
    description = "根据校验结果处理缺失字段"
    config_fields = {
        "validate_form_step": ParamSchema(type="str", required=False, description="校验表单步骤ID", default="validate_form"),
    }
    output_fields = {
        "action": ParamSchema(type="str", description="动作类型"),
        "missing_fields": ParamSchema(type="list", description="缺失字段列表"),
        "message": ParamSchema(type="str", description="消息"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        processing = "根据校验结果处理缺失字段"
        self._log_processing(processing)

        context = execution.context

        validate_form_step = execution.get("validate_form_step", "validate_form")

        validate_result = context.step_results.get(validate_form_step, {})
        if not validate_result:
            validate_result = context.outputs.get("validation_result", {})

        validation_results = validate_result.get("validationResults", [])

        missing_fields = []
        for result in validation_results:
            if result.get("result") == "error":
                missing_fields.append({
                    "field": result.get("field"),
                    "fieldName": result.get("fieldName"),
                    "reason": result.get("reason"),
                    "suggestion": result.get("suggestion"),
                })

        if missing_fields:
            execution.set("action", "ask_user")
            execution.set("missing_fields", missing_fields)
            execution.set("message", f"请补充以下 {len(missing_fields)} 个必填字段")

            self._log_output(success=True, action="ask_user", missing_field_count=len(missing_fields))
        else:
            execution.set("action", "continue")
            execution.set("missing_fields", [])
            execution.set("message", "所有字段校验通过")

            self._log_output(success=True, action="continue", message="所有字段校验通过")
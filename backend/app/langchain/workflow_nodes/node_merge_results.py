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
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class MergeResultsNode(WorkflowNode):
    """结果合并节点"""

    name = "workflow.merge_results"
    display_name = "合并结果"
    description = "合并表单生成和校验结果"
    config_fields = {
        "generate_form_step": ParamSchema(type="str", required=False, description="生成表单步骤ID", default="generate_form"),
        "validate_form_step": ParamSchema(type="str", required=False, description="校验表单步骤ID", default="validate_form"),
    }
    output_fields = {
        "action": ParamSchema(type="str", description="动作类型"),
        "formCode": ParamSchema(type="str", description="表单编码"),
        "form_schema": ParamSchema(type="object", description="表单结构"),
        "extractedFields": ParamSchema(type="object", description="提取的字段数据"),
        "validationResults": ParamSchema(type="list", description="校验结果"),
        "summary": ParamSchema(type="object", description="校验摘要"),
        "message": ParamSchema(type="str", description="消息"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        processing = "合并表单生成和校验结果"
        self._log_processing(processing)

        context = execution.context

        generate_form_step = execution.get("generate_form_step", "generate_form")
        validate_form_step = execution.get("validate_form_step", "validate_form")

        form_result = context.step_results.get(generate_form_step, {})
        if not form_result:
            form_result = context.outputs.get("form_result", {})

        validate_result = context.step_results.get(validate_form_step, {})
        if not validate_result:
            validate_result = context.outputs.get("validation_result", {})

        form_code = form_result.get("formCode") or form_result.get("ontology_code", "")
        extracted_fields = form_result.get("extractedFields", {})
        validation_results = validate_result.get("validationResults", [])
        summary = validate_result.get("summary")

        execution.set("action", form_result.get("action", "generate_form"))
        execution.set("formCode", form_code)
        execution.set("form_schema", form_result.get("form_schema"))
        execution.set("extractedFields", extracted_fields)
        execution.set("validationResults", validation_results)
        execution.set("summary", summary)
        execution.set("message", validate_result.get("message", form_result.get("message")))

        self._log_output(
            success=True,
            formCode=form_code,
            action=execution.variables.get("action"),
            field_count=len(extracted_fields),
        )
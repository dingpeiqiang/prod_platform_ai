"""
表单验证节点

输入参数：
- (从上下文获取)

输出结果：
- validationResults: 校验结果列表
- summary: 校验摘要（通过数、警告数、错误数）
- message: 校验消息
"""
from typing import Dict, Any, List, Optional
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import re


@register_node
class ValidateFormNode(WorkflowNode):
    """表单验证节点"""

    name = "workflow.validate_form"
    display_name = "表单验证"
    description = "验证表单数据"
    config_fields = {
        "generate_form_step": ParamSchema(type="str", required=False, description="生成表单步骤ID", default="generate_form"),
    }
    output_fields = {
        "validationResults": ParamSchema(type="list", description="校验结果列表"),
        "summary": ParamSchema(type="object", description="校验摘要"),
        "message": ParamSchema(type="str", description="校验消息"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        context = execution.context

        generate_form_step = execution.get("generate_form_step", "generate_form")

        form_result = context.step_results.get(generate_form_step, {})
        if not form_result:
            form_result = context.outputs.get("form_result", {})

        form_data = form_result.get("extractedFields", {})
        ontology_code = form_result.get("ontologyCode") or form_result.get("formCode", "")

        self._log_input(form_data=form_data, ontology_code=ontology_code)

        processing = f"校验表单数据，共 {len(form_data)} 个字段"
        self._log_processing(processing)

        if not form_data:
            error_msg = "No form data to validate"
            self._log_output(success=False, error=error_msg)
            execution.set("validationResults", [])
            execution.set("summary", {"total": 0, "passed": 0, "warnings": 0, "errors": 0})
            execution.set("message", error_msg)
            return

        try:
            from app.config.config_loader import config_loader

            ontology = config_loader.get_ontology(ontology_code) if ontology_code else None

            validation_results = self._validate_fields(form_data, ontology)

            passed = sum(1 for r in validation_results if r["result"] == "pass")
            warnings = sum(1 for r in validation_results if r["result"] == "warning")
            errors = sum(1 for r in validation_results if r["result"] == "error")

            summary = {
                "total": len(validation_results),
                "passed": passed,
                "warnings": warnings,
                "errors": errors,
            }

            message = self._generate_validation_message(passed, warnings, errors)

            execution.set("validationResults", validation_results)
            execution.set("summary", summary)
            execution.set("message", message)

            self._log_output(success=errors == 0, passed=passed, warnings=warnings, errors=errors, message=message)

        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            execution.set("validationResults", [])
            execution.set("summary", {"total": 0, "passed": 0, "warnings": 0, "errors": 0})
            execution.set("message", error_msg)

    def _validate_fields(self, form_data: Dict[str, Any], ontology: Optional[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """验证字段"""
        results = []

        if not ontology:
            for field_code, value in form_data.items():
                results.append({
                    "field": field_code,
                    "fieldName": field_code,
                    "value": value,
                    "result": "pass",
                    "reason": "无校验规则",
                    "suggestion": "",
                })
            return results

        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                field_name = field.get("fieldName", field_code)
                value = form_data.get(field_code, "")

                result, reason, suggestion = self._validate_single_field(field_code, value, field)

                results.append({
                    "field": field_code,
                    "fieldName": field_name,
                    "value": value,
                    "result": result,
                    "reason": reason,
                    "suggestion": suggestion,
                })

        return results

    def _validate_single_field(self, field_code: str, value: Any, field_def: Dict[str, Any]) -> tuple:
        """验证单个字段"""
        required = field_def.get("required", False)

        if required and (value is None or value == "" or (isinstance(value, str) and value.strip() == "")):
            return ("error", "此字段不能为空", "")

        if value is None or value == "" or (isinstance(value, str) and value.strip() == ""):
            return ("pass", "未填写", "")

        validation = field_def.get("validation", {})

        if validation.get("pattern"):
            pattern = validation["pattern"]
            if not re.match(pattern, str(value)):
                return ("error", validation.get("pattern_error", "格式不正确"), "")

        options = field_def.get("options", [])
        if options:
            valid_values = []
            for opt in options:
                if isinstance(opt, str):
                    valid_values.append(opt)
                elif isinstance(opt, dict):
                    valid_values.append(opt.get("value", ""))

            if str(value) not in valid_values:
                return ("error", "值不在有效选项中", f"有效值: {', '.join(valid_values[:5])}...")

        if validation.get("max_length"):
            max_len = validation["max_length"]
            if len(str(value)) > max_len:
                return ("error", f"长度超过限制", f"最大长度{max_len}字符")

        if validation.get("type") == "number":
            try:
                float(value)
            except (ValueError, TypeError):
                return ("error", "类型不正确，应为数字", "")

        return ("pass", "校验通过", "")

    def _generate_validation_message(self, passed: int, warnings: int, errors: int) -> str:
        """生成校验消息"""
        if errors == 0 and warnings == 0:
            return f"表单校验完成，全部{passed}个字段通过！"
        elif errors > 0:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，{errors}个错误，请修正后提交。"
        else:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，请确认后提交。"
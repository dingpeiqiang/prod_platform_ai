"""
资费备案 - 表单验证节点

输入参数：无（从上下文获取数据）

输出结果：
- validationResults: 校验结果列表
- summary: 校验摘要
- message: 校验消息
"""
from typing import Dict, Any, List
from app.langchain.workflow_nodes import WorkflowNode, register_node
import re


@register_node
class TariffValidateFormNode(WorkflowNode):
    """资费备案表单验证节点"""
    
    name = "tariff.validate_form"
    description = "验证资费备案表单数据"
    inputs = {}
    outputs = {
        "validationResults": {"type": "list", "description": "校验结果列表"},
        "summary": {"type": "dict", "description": "校验摘要"},
        "message": {"type": "str", "description": "校验消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "验证资费备案表单数据"
        self._log_processing(processing)
        
        # 从上下文获取数据
        form_result = context.step_results.get("generate_form", {})
        form_data = form_result.get("extractedFields", {})
        
        if not form_data:
            result = {
                "success": False,
                "error": "No form data to validate",
                "input": {},
                "processing": processing,
                "output": {"error": "No form data to validate"}
            }
            
            self._log_output(success=False, error="No form data to validate")
            return result
        
        validation_results = self._validate_fields(form_data)
        
        passed = sum(1 for r in validation_results if r["result"] == "pass")
        warnings = sum(1 for r in validation_results if r["result"] == "warning")
        errors = sum(1 for r in validation_results if r["result"] == "error")
        
        result = {
            "success": errors == 0,
            "validationResults": validation_results,
            "summary": {
                "total": len(validation_results),
                "passed": passed,
                "warnings": warnings,
                "errors": errors
            },
            "message": self._generate_validation_message(validation_results),
            "input": {},
            "processing": processing,
            "output": {
                "passed": passed,
                "warnings": warnings,
                "errors": errors
            }
        }
        
        self._log_output(
            success=errors == 0,
            passed=passed,
            warnings=warnings,
            errors=errors
        )
        
        return result
    
    def _validate_fields(self, fields: Dict[str, Any]) -> List[Dict[str, Any]]:
        """验证字段"""
        from app.config.tariff_rules_loader import load_tariff_rules
        
        results = []
        validation_rules = load_tariff_rules().get("validation_rules", {})
        
        for field_code, rule in validation_rules.items():
            value = fields.get(field_code, "")
            field_name = self._get_field_display_name(field_code)
            
            result, reason, suggestion = self._validate_field(field_code, value, rule)
            
            results.append({
                "field": field_code,
                "fieldName": field_name,
                "value": value,
                "result": result,
                "reason": reason,
                "suggestion": suggestion
            })
        
        return results
    
    def _get_field_display_name(self, field_code: str) -> str:
        """获取字段显示名称"""
        field_name_mapping = {
            "bossid": "省内套餐编码",
            "seq_no": "序列号",
            "reporter": "备案主体",
            "action_type": "操作类型",
            "type1": "一级分类",
            "type2": "二级分类",
            "name": "资费名称",
            "tariff_attr": "资费属性",
            "applicable_people": "适用范围",
            "applicable_area": "适用地区",
            "valid_period": "有效期限",
            "channel": "销售渠道",
            "duration": "在网要求",
            "unsubscribe": "退订方式",
            "responsibility": "违约责任",
            "online_day": "上线日期",
            "offline_day": "下线日期",
            "fees": "资费标准",
            "fees_unit": "资费单位"
        }
        return field_name_mapping.get(field_code, field_code)
    
    def _validate_field(self, field_code: str, value: Any, rule: Dict[str, Any]) -> tuple:
        """验证单个字段"""
        from app.config.tariff_rules_loader import get_enum_values
        
        required = rule.get("required", False)
        
        if required and not value:
            return ("error", rule.get("empty_error", "此字段不能为空"), rule.get("empty_suggestion", ""))
        
        if not value and not required:
            if rule.get("empty_warning"):
                return ("warning", rule["empty_warning"], "")
            return ("pass", "未填写", "")
        
        if "pattern" in rule:
            pattern = rule["pattern"]
            if not re.match(pattern, str(value)):
                return ("error", rule.get("pattern_error", "格式不正确"), "")
        
        if "enum" in rule:
            enum_name = rule["enum"]
            enum_values = get_enum_values(enum_name)
            valid_values = [item.get("value") for item in enum_values]
            if str(value) not in valid_values:
                labels = [f"{item.get('value')}({item.get('label')})" for item in enum_values]
                return ("error", rule.get("enum_error", "值无效"), f"有效值: {', '.join(labels)}")
        
        if "max_length" in rule:
            max_len = rule["max_length"]
            if len(str(value)) > max_len:
                return ("error", rule.get("length_error", f"长度超过限制"), f"最大长度{max_len}字符")
        
        if "type" in rule:
            field_type = rule["type"]
            if field_type == "number":
                try:
                    float(value)
                except (ValueError, TypeError):
                    return ("error", rule.get("type_error", "类型不正确"), "")
        
        return ("pass", "校验通过", "")
    
    def _generate_validation_message(self, results: List[Dict[str, Any]]) -> str:
        """生成校验消息"""
        passed = sum(1 for r in results if r["result"] == "pass")
        warnings = sum(1 for r in results if r["result"] == "warning")
        errors = sum(1 for r in results if r["result"] == "error")
        
        if errors == 0 and warnings == 0:
            return f"表单校验完成，全部{passed}个字段通过！"
        elif errors > 0:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，{errors}个错误，请修正后提交。"
        else:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，请确认后提交。"
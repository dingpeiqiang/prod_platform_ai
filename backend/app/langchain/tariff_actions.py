"""
资费备案动作处理器

这些动作可以被工作流引擎调用，实现资费备案的各个业务步骤：
1. 解析用户输入 → 提取套餐编码
2. 查询套餐信息 → 调用 MCP 工具
3. 生成表单数据 → 字段映射和默认值
4. 表单校验 → 验证所有字段
"""
from typing import Dict, Any
from app.core.logger import get_logger

logger = get_logger(__name__)
import re

from app.config.tariff_rules_loader import (
    get_default_value,
    get_validation_rule,
    get_field_mapping,
    get_extraction_rule,
    get_tariff_code_patterns,
    get_enum_values,
    load_tariff_rules
)
from app.mcp_tools.tariff_tools import query_tariff_by_code



async def action_parse_input(context: Any, **kwargs) -> Dict[str, Any]:
    """解析用户输入，提取套餐编码"""
    user_input = context.inputs.get("user_input", "")
    
    # 提取套餐编码
    tariff_code = _extract_tariff_code(user_input)
    
    if not tariff_code:
        if any(keyword in user_input for keyword in ["套餐", "资费", "备案"]):
            return {
                "action": "ask_user",
                "tariff_code": None,
                "missing_fields": [{"field": "bossid", "reason": "请提供套餐编码"}],
                "message": "请提供要备案的套餐编码"
            }
        else:
            return {
                "action": "chat",
                "tariff_code": None,
                "message": _get_chat_response(user_input)
            }
    
    # 从用户输入提取其他字段
    fields = _generate_default_fields()
    fields = _extract_from_input(fields, user_input)
    fields["bossid"] = tariff_code
    
    return {
        "action": "continue",
        "tariff_code": tariff_code,
        "extracted_fields": fields,
        "message": f"提取到套餐编码: {tariff_code}"
    }


async def action_query_tariff(context: Any, **kwargs) -> Dict[str, Any]:
    """查询套餐信息"""
    tariff_code = context.outputs.get("tariff_code")
    
    if not tariff_code:
        return {"success": False, "error": "未提供套餐编码"}
    
    try:
        result = query_tariff_by_code(tariff_code)
        return result
    except Exception as e:
        logger.error(f"查询套餐失败: {e}")
        return {"success": False, "error": str(e)}


async def action_generate_form(context: Any, **kwargs) -> Dict[str, Any]:
    """生成表单数据"""
    query_result = context.step_results.get("query_tariff", {})
    extracted_fields = context.outputs.get("extracted_fields", {})
    
    if query_result.get("success"):
        # 使用工具返回的数据填充表单
        fields = _map_tool_result(query_result)
        # 合并提取的字段
        fields.update(extracted_fields)
    else:
        # 使用默认值
        fields = _generate_default_fields()
        fields.update(extracted_fields)
    
    return {
        "formCode": "tariff_filing_publicity",
        "extractedFields": fields,
        "message": query_result.get("success") and "套餐信息查询成功" or "使用默认表单"
    }


async def action_validate_form(context: Any, **kwargs) -> Dict[str, Any]:
    """验证表单数据"""
    form_result = context.step_results.get("generate_form", {})
    form_data = form_result.get("extractedFields", {})
    
    validation_results = _validate_fields(form_data)
    
    passed = sum(1 for r in validation_results if r["result"] == "pass")
    warnings = sum(1 for r in validation_results if r["result"] == "warning")
    errors = sum(1 for r in validation_results if r["result"] == "error")
    
    return {
        "validationResults": validation_results,
        "summary": {
            "total": len(validation_results),
            "passed": passed,
            "warnings": warnings,
            "errors": errors
        },
        "message": _generate_validation_message(validation_results)
    }


async def action_merge_results(context: Any, **kwargs) -> Dict[str, Any]:
    """合并最终结果"""
    form_result = context.step_results.get("generate_form", {})
    validate_result = context.step_results.get("validate_form", {})
    
    return {
        "action": form_result.get("action", "generate_form"),
        "formCode": form_result.get("formCode"),
        "extractedFields": form_result.get("extractedFields", {}),
        "validationResults": validate_result.get("validationResults", []),
        "summary": validate_result.get("summary"),
        "message": validate_result.get("message", form_result.get("message"))
    }


def _extract_tariff_code(user_input: str) -> str:
    """提取套餐编码"""
    patterns = get_tariff_code_patterns()
    for pattern in patterns:
        match = re.search(pattern, user_input)
        if match:
            code = match.group(0).strip()
            if len(code) >= 3:
                return code
    return ""


def _get_chat_response(user_input: str) -> str:
    """处理普通聊天"""
    greetings = ["你好", "您好", "嗨", "hello", "hi"]
    if any(greeting in user_input.lower() for greeting in greetings):
        return "您好！我是资费备案助手，可以帮您完成资费套餐的备案申请。"
    return "请问有什么可以帮助您的？您可以提供套餐编码进行备案。"


def _generate_default_fields() -> Dict[str, Any]:
    """生成默认字段值"""
    rules = load_tariff_rules()
    default_values = rules.get("default_values", {})
    
    fields = {}
    for field_code, default_value in default_values.items():
        fields[field_code] = default_value
    
    # 添加基础字段
    base_fields = ["bossid", "name", "fees", "online_day", "offline_day"]
    for field in base_fields:
        if field not in fields:
            fields[field] = ""
    
    return fields


def _extract_from_input(fields: Dict[str, Any], user_input: str) -> Dict[str, Any]:
    """从用户输入提取字段"""
    extraction_rules = load_tariff_rules().get("extraction_rules", {})
    
    for field_code, rule in extraction_rules.items():
        if "keywords" in rule:
            for keyword_item in rule["keywords"]:
                keyword = keyword_item.get("keyword")
                value = keyword_item.get("value")
                if keyword and keyword in user_input:
                    fields[field_code] = value
                    break
        
        if "pattern" in rule:
            pattern = rule["pattern"]
            match = re.search(pattern, user_input)
            if match:
                if field_code == "online_day" and "format" in rule:
                    try:
                        year, month, day = match.groups()
                        fields[field_code] = rule["format"].format(int(year), int(month), int(day))
                    except (ValueError, IndexError):
                        pass
                else:
                    fields[field_code] = match.group(1)
    
    return fields


def _map_tool_result(tool_result: Dict[str, Any]) -> Dict[str, Any]:
    """映射工具返回结果"""
    fields = _generate_default_fields()
    field_mapping = load_tariff_rules().get("field_mapping", {})
    
    for target_field, source_fields in field_mapping.items():
        for source_field in source_fields:
            if source_field in tool_result and tool_result[source_field]:
                fields[target_field] = tool_result[source_field]
                break
    
    return fields


def _validate_fields(fields: Dict[str, Any]) -> list:
    """验证字段"""
    results = []
    validation_rules = load_tariff_rules().get("validation_rules", {})
    
    for field_code, rule in validation_rules.items():
        value = fields.get(field_code, "")
        field_name = _get_field_display_name(field_code)
        
        result, reason, suggestion = _validate_field(field_code, value, rule)
        
        results.append({
            "field": field_code,
            "fieldName": field_name,
            "value": value,
            "result": result,
            "reason": reason,
            "suggestion": suggestion
        })
    
    return results


def _get_field_display_name(field_code: str) -> str:
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


def _validate_field(field_code: str, value: Any, rule: Dict[str, Any]) -> tuple:
    """验证单个字段"""
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


def _generate_validation_message(results: list) -> str:
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

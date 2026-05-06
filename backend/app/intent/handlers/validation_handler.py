# ValidationHandler - 表单校验意图处理器
# 在表单提交时通过 LLM 校验 ruleDescription 规则、字段一致性和业务语义

import logging
from typing import AsyncGenerator, Dict, Any

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, done_event
from ...skills.validation_skill import ValidationSkill

logger = logging.getLogger("intent.validation_handler")


def _build_suggestion(issue: Dict, field_value: Any) -> str:
    """
    根据规则引擎错误类型生成优化建议
    """
    error_code = issue.get("error_code", "")
    field_name = issue.get("field_name", "")
    message = issue.get("message", "")

    if error_code == "ERR_VAL_REQUIRED":
        return f"请填写「{field_name}」字段"

    if error_code == "ERR_VAL_RANGE":
        if "最小" in message or "不能小于" in message:
            return f"「{field_name}」的值不在允许范围内，请检查数值"
        return f"请调整「{field_name}」的值到有效范围内"

    if error_code == "ERR_VAL_FORMAT":
        if "邮箱" in message:
            return f"「{field_name}」格式不正确，请输入有效的邮箱地址"
        if "手机" in message or "电话" in message:
            return f"「{field_name}」格式不正确，请输入有效的手机号"
        if "日期" in message:
            return f"「{field_name}」格式不正确，请使用 YYYY-MM-DD 格式"
        return f"「{field_name}」格式不符合要求，请检查输入格式"

    if error_code == "ERR_VAL_RULE_FAIL":
        if "可选列表" in message or "枚举" in message:
            # 尝试从 issue.rule_options 获取带中文的选项
            options_list = issue.get("rule_options", [])
            if options_list:
                options_str = ", ".join(str(v) for v in options_list)
                return f"请选择：{options_str}"
            # 兼容旧版：从 message 中提取
            import re
            match = re.search(r'可选值[：:]?\s*\[?(.*?)\]?$', message)
            if match:
                options = match.group(1)
                return f"请选择：{options}"
            return f"请从「{field_name}」的可选列表中选择一个值"
        return f"「{field_name}」的值不符合规则要求，请修正"

    if "不能为空" in message:
        return f"请填写「{field_name}」字段"

    if "超出" in message or "超过" in message:
        return f"「{field_name}」的值超出范围，请调整"

    # 默认建议
    if field_value is not None and field_value != "":
        return f"请修正「{field_name}」的值"
    return f"请完善「{field_name}」字段的填写"


def _build_llm_suggestion(field_name: str, message: str, field_code: str) -> str:
    """
    根据 LLM 校验错误生成具体优化建议
    """
    import re

    # 缺少必填字段
    if "缺少必填" in message or "必填" in message:
        # 提取生成规则
        rule_match = re.search(r'生成规则[：:]\s*([^，,。]+)', message)
        if rule_match:
            rule = rule_match.group(1)
            example_match = re.search(r'示例[：:]\s*(\S+)', message)
            if example_match:
                return f"必填字段，请按规则生成。示例：{example_match.group(1)}"
            return f"必填字段，请按「{rule}」规则生成"
        return f"请填写「{field_name}」字段"

    # 枚举值无效
    if "值" in message and ("无效" in message or "应从" in message):
        # 提取可选值列表（支持带中文标签的格式）
        match = re.search(r'应从以下选项中选择[：:]\s*(.+?)(?：|$|\n)', message)
        if match:
            options_str = match.group(1).strip()
            # 截断过长的选项列表（保留前5个）
            options_list = [o.strip() for o in re.split(r'[,，]', options_str) if o.strip()]
            if len(options_list) > 5:
                options_str = "、".join(options_list[:5]) + "..."
            else:
                options_str = "、".join(options_list)
            return f"请选择：{options_str}"
        match = re.search(r'选项[：:]\s*(.+?)(?：|$|\n)', message)
        if match:
            options_str = match.group(1).strip()
            return f"请选择：{options_str}"
        return f"「{field_name}」的值无效，请从选项中选择"

    # 格式错误
    if "格式错误" in message or ("不符合" in message and "格式" in message):
        # 提取正确格式
        format_match = re.search(r'(YYYYMMDD|YYYY-MM-DD|\d{8}|[\w]+格式)', message)
        if format_match:
            correct_format = format_match.group(1)
            if "YYYYMMDD" in correct_format:
                return f"请改用 8 位数字格式，如 20240101"
            if "YYYY-MM-DD" in correct_format:
                return f"请使用日期格式，如 2024-01-01"
            return f"「{field_name}」格式不正确，请修正"
        return f"「{field_name}」格式不正确，请修正"

    # 编码不规范
    if "省份编码" in message or "地市编码" in message:
        return f"应使用标准编码（如 SC 表示四川），全国用 000"

    # 数值范围
    if "超出" in message or "大于" in message or "小于" in message:
        return f"请调整「{field_name}」的值到有效范围内"

    # 默认
    return f"请修正「{field_name}」的填写"


class ValidationHandler(BaseIntentHandler):
    """表单校验意图 —— 基于 ruleDescription 的 LLM 智能校验"""

    intent_type = "validate"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """
        处理表单校验请求，分步骤执行：
        1. 规则引擎校验（快速过滤）
        2. LLM 智能校验（语义+一致性，含模型思考过程）

        两步都执行完后统一汇总结果，输出结构化错误信息。

        IntentContext.intent_data 需要包含：
        - form_code: str 表单代码
        - form_data: Dict 用户提交的表单数据 {"fieldCode": value}
        """
        # 从 intent_data 中提取 form_code 和 form_data
        _raw_form_data = (
            ctx.intent_data.get("form_data")
            or ctx.intent_data.get("formData")
            or ctx.intent_data.get("extractedFields")
            or {}
        )
        form_code = (
            ctx.intent_data.get("form_code")
            or ctx.intent_data.get("formCode")
            or ctx.intent_data.get("detectedFormCode")
            or "unknown"
        )
        form_data = _raw_form_data if isinstance(_raw_form_data, dict) else {}
        logger.info(f"[ValidationHandler] intent_data keys: {list(ctx.intent_data.keys())}, form_code={form_code}, form_data size={len(form_data)}")

        # ═══════════════════════════════════════════════════════════
        # 汇总收集器（两步都执行完后统一输出）
        # ═══════════════════════════════════════════════════════════
        structured_errors = []  # 结构化错误列表
        rule_engine_passed = False

        # ═══════════════════════════════════════════════════════════
        # 步骤 1/2：规则引擎校验
        # ═══════════════════════════════════════════════════════════
        yield thinking("🔍 步骤 1/2：执行规则引擎校验...")

        # 委托 ValidationSkill 执行规则引擎校验（同步）
        rule_result = ValidationSkill.validate_form_from_ontology(form_code, form_data)

        if not rule_result.get("valid"):
            # 使用结构化的 issues 数组
            issues = rule_result.get("issues", [])
            for issue in issues:
                field_value = form_data.get(issue.get("field", ""))
                structured_errors.append({
                    "field": issue.get("field_name", ""),           # 字段中文名
                    "fieldCode": issue.get("field", ""),            # 字段代码
                    "message": issue.get("message", ""),            # 错误信息
                    "source": "rule_engine",                         # 来源
                    "errorCode": issue.get("error_code", ""),       # 错误码
                    "suggestion": _build_suggestion(issue, field_value)  # 优化建议
                })
            yield thinking(f"❌ 步骤 1/2：规则引擎校验失败，发现 {len(structured_errors)} 个问题")
        else:
            rule_engine_passed = True
            yield thinking("✅ 步骤 1/2：规则引擎校验通过")

        # ═══════════════════════════════════════════════════════════
        # 步骤 2/2：LLM 智能校验（包含模型思考过程）
        # ═══════════════════════════════════════════════════════════
        yield thinking("🤖 步骤 2/2：调用 AI 模型进行智能校验...")
        yield thinking("💭 AI 正在分析数据合理性、字段一致性、业务语义...")

        # 委托 ValidationSkill 执行 LLM 智能校验
        llm_result, reasoning_chunks = ValidationSkill.validate_with_ontology(
            form_code, form_data
        )

        # 先流式发送模型思考过程（让用户看到 AI 在思考）
        for chunk in reasoning_chunks:
            yield sse({
                "type": "reasoning",
                "content": chunk,
                "step": "llm_validation"
            })

        llm_warnings = []
        if llm_result.get("valid"):
            llm_errors = llm_result.get("errors", [])
            llm_warnings = llm_result.get("warnings", [])
            yield thinking(f"✅ 步骤 2/2：AI 智能校验通过（{len(llm_errors)} 个错误，{len(llm_warnings)} 个警告）")
        else:
            # LLM 错误也需要结构化
            llm_errors = llm_result.get("errors", [])
            for err_msg in llm_errors:
                # 尝试从【字段名(code)】格式中提取字段名
                import re
                bracket_match = re.search(r'【(.+?)】', err_msg)
                if bracket_match:
                    # 提取【】中的内容，如"序列号(seq_no)"或"二级分类(type2)"
                    bracket_content = bracket_match.group(1)
                    field_match = re.match(r'^(.+?)\(([^)]+)\)$', bracket_content)
                    if field_match:
                        field_name = field_match.group(1)
                        field_code = field_match.group(2)
                        message = err_msg.replace(bracket_match.group(0), "").strip()
                    else:
                        field_name = bracket_content
                        field_code = ""
                        message = err_msg.replace(bracket_match.group(0), "").strip()
                else:
                    field_name = "未知字段"
                    field_code = ""
                    message = err_msg

                # 精简 message（只保留核心错误描述）
                message = re.sub(r'^(值|字段|输入).*?[：:]\s*', '', message)
                # 如果包含选项列表，保留完整选项
                if "应从以下选项中选择" in message or "选项" in message:
                    # 提取完整选项列表（不截断）
                    options_match = re.search(r'应从以下选项中选择[：:]\s*(.+?)$', message, re.DOTALL)
                    if options_match:
                        options_part = options_match.group(1).strip()
                        base_part = message[:message.find("应从以下选项中选择")].strip()
                        message = base_part + "，应从以下选项中选择：" + options_part
                else:
                    # 截断过长的 message
                    if len(message) > 80:
                        # 尝试在句号处截断
                        truncate_at = message[:80].rfind('，')
                        if truncate_at > 20:
                            message = message[:truncate_at] + "..."
                message = message.strip('。，、')

                structured_errors.append({
                    "field": field_name,
                    "fieldCode": field_code,
                    "message": message,
                    "source": "llm_validation",
                    "errorCode": "ERR_LLM_VALIDATION",
                    "suggestion": _build_llm_suggestion(field_name, message, field_code)
                })
            llm_warnings = llm_result.get("warnings", [])
            yield thinking(f"❌ 步骤 2/2：AI 智能校验未通过，发现 {len(llm_errors)} 个问题")

        # ═══════════════════════════════════════════════════════════
        # 汇总输出：两步都执行完后统一输出结果
        # ═══════════════════════════════════════════════════════════
        if structured_errors:
            yield thinking(f"📋 校验汇总：共发现 {len(structured_errors)} 个错误，{len(llm_warnings)} 个警告")
            yield sse({
                "type": "validation_fail",
                "form_code": form_code,
                "step": "all",
                "errors": structured_errors,
                "warnings": llm_warnings,
                "rule_engine_passed": rule_engine_passed
            })
        else:
            yield thinking(f"✅ 校验通过（{len(llm_warnings)} 个警告）")
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "step": "all",
                "errors": [],
                "warnings": llm_warnings,
                "rule_engine_passed": rule_engine_passed
            })

        yield done_event(intent_type="validate", is_form=False)
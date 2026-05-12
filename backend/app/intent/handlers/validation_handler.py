# ValidationHandler - 表单校验意图处理器
# 在表单提交时通过 LLM 校验 ruleDescription 规则、字段一致性和业务语义

import logging
import re
import time
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
        match = re.search(r'应从以下选项中选择[：:]\s*(.+?)(?:$|\n)', message)
        if match:
            options_str = match.group(1).strip()
            # 截断过长的选项列表（保留前5个）
            options_list = [o.strip() for o in re.split(r'[,，]', options_str) if o.strip()]
            if len(options_list) > 5:
                options_str = "、".join(options_list[:5]) + "..."
            else:
                options_str = "、".join(options_list)
            return f"请选择：{options_str}"
        match = re.search(r'选项[：:]\s*(.+?)(?:$|\n)', message)
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
        处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（规则引擎 + LLM 校验）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
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

        # Fallback：如果 intent_data 中没有 form_data，尝试从用户消息文本中解析
        if not form_data and ctx.last_user_message:
            form_data = self._parse_form_data_from_message(ctx.last_user_message)
            if form_data:
                logger.info(f"[ValidationHandler] 从消息文本解析到 formData: {list(form_data.keys())}")

        logger.info(f"[ValidationHandler] intent_data keys: {list(ctx.intent_data.keys())}, form_code={form_code}, form_data size={len(form_data)}")

        # ═══ Phase 1：识别 ══════════════════════════════════════════
        yield thinking(f"📋 识别到校验任务，表单类型：{form_code}", result={
            "formCode": form_code,
            "fieldCount": len(form_data)
        })

        # ═══ Phase 2：执行 ══════════════════════════════════════════
        # ── Step 1：规则引擎校验 ───────────────────────────────────
        _t0 = time.time()

        rule_result = ValidationSkill.validate_form_from_ontology(form_code, form_data)
        rule_elapsed_ms = round((time.time() - _t0) * 1000, 1)

        fields_checked = list(form_data.keys())
        structured_errors = []
        rule_engine_passed = True

        if not rule_result.get("valid"):
            rule_engine_passed = False
            issues = rule_result.get("issues", [])
            for issue in issues:
                field_value = form_data.get(issue.get("field", ""))
                structured_errors.append({
                    "field": issue.get("field_name", ""),
                    "fieldCode": issue.get("field", ""),
                    "message": issue.get("message", ""),
                    "source": "rule_engine",
                    "errorCode": issue.get("error_code", ""),
                    "suggestion": _build_suggestion(issue, field_value)
                })

        yield thinking(
            f"🔍 Step 1/2：规则引擎校验{'通过' if rule_engine_passed else f'失败，{len(structured_errors)} 个问题'}",
            result={
                "formCode": form_code,
                "fieldsChecked": fields_checked,
                "fieldCount": len(fields_checked),
                "passed": rule_engine_passed,
                "issues": rule_result.get("issues", []),
                "issueCount": len(structured_errors),
                "elapsedMs": rule_elapsed_ms
            }
        )

        # ── Step 2：LLM 智能校验 ───────────────────────────────────
        from app.services.llm_service import llm_service

        _t1 = time.time()
        llm_result, reasoning_chunks = ValidationSkill.validate_with_ontology(form_code, form_data)
        llm_elapsed = time.time() - _t1

        # 先流式发送模型思考过程（内嵌到 Step 2 下）
        for chunk in reasoning_chunks:
            yield sse({"type": "reasoning", "content": chunk, "step": "llm_validation"})

        llm_errors_raw = llm_result.get("errors", [])
        llm_warnings = llm_result.get("warnings", [])

        yield thinking(
            f"🤖 Step 2/2：AI 智能校验{'通过' if not llm_errors_raw else f'发现 {len(llm_errors_raw)} 个错误，{len(llm_warnings)} 个警告'}",
            result={
                "model": llm_service.llm_config.get("model"),
                "provider": llm_service.llm_config.get("provider"),
                "elapsed": round(llm_elapsed, 2),
                "llmErrors": len(llm_errors_raw),
                "llmWarnings": len(llm_warnings),
                "ruleEnginePassed": rule_engine_passed,
                "reasoningChunks": len(reasoning_chunks)
            }
        )

        # ── Step 3：汇总输出 ──────────────────────────────────────
        # 将 LLM 原始错误转结构化
        llm_structured_errors = []
        import re as regex_mod
        for err_msg in llm_errors_raw:
            bracket_match = regex_mod.search(r'【(.+?)】', err_msg)
            if bracket_match:
                bracket_content = bracket_match.group(1)
                field_match = regex_mod.match(r'^(.+?)\(([^)]+)\)$', bracket_content)
                if field_match:
                    field_name = field_match.group(1)
                    field_code = field_match.group(2)
                    message = err_msg.replace(bracket_match.group(0), "").strip()
                else:
                    field_name = bracket_content
                    field_code = ""
                    message = err_msg.replace(bracket_match.group(0), "").strip()
            else:
                # 尝试匹配 "字段名（字段编码）：错误描述" 格式（中文括号，如 费用说明（description）：...）
                paren_match = regex_mod.match(r'^([^（(]+)[（(]([^）)]+)[）)][：:]\s*(.*)', err_msg)
                if paren_match:
                    field_name = paren_match.group(1).strip()
                    field_code = paren_match.group(2).strip()
                    message = paren_match.group(3).strip()
                else:
                    # 尝试匹配 "字段名：" 格式（无括号，AI 简化输出）
                    field_prefix_match = regex_mod.match(r'^([^\s：:]{1,10})[：:]\s*(.*)', err_msg)
                    if field_prefix_match:
                        field_name = field_prefix_match.group(1)
                        field_code = ""
                        message = field_prefix_match.group(2).strip()
                    else:
                        field_name = "未知字段"
                        field_code = ""
                        message = err_msg

            message = regex_mod.sub(r'^(值|字段|输入).*?[：:]\s*', '', message)
            if "应从以下选项中选择" in message or "选项" in message:
                options_match = regex_mod.search(r'应从以下选项中选择[：:]\s*(.+?)$', message, regex_mod.DOTALL)
                if options_match:
                    options_part = options_match.group(1).strip()
                    base_part = message[:message.find("应从以下选项中选择")].strip()
                    message = base_part + "，应从以下选项中选择：" + options_part
            else:
                if len(message) > 80:
                    truncate_at = message[:80].rfind('，')
                    if truncate_at > 20:
                        message = message[:truncate_at] + "..."
            message = message.strip('。，、')

            llm_structured_errors.append({
                "field": field_name,
                "fieldCode": field_code,
                "message": message,
                "source": "llm_validation",
                "errorCode": "ERR_LLM_VALIDATION",
                "suggestion": _build_llm_suggestion(field_name, message, field_code)
            })

        all_errors = structured_errors + llm_structured_errors
        total_errors = len(all_errors)

        yield thinking(
            f"📋 校验汇总：共 {total_errors} 个错误，{len(llm_warnings)} 个警告{'（全部通过）' if total_errors == 0 else ''}",
            result={
                "totalErrors": total_errors,
                "totalWarnings": len(llm_warnings),
                "ruleEngineErrors": len(structured_errors),
                "llmErrors": len(llm_structured_errors),
                "passed": total_errors == 0,
                "errors": all_errors,
                "warnings": llm_warnings
            }
        )

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        # 最终 SSE 事件（前端面板使用）
        if all_errors:
            yield sse({
                "type": "validation_fail",
                "form_code": form_code,
                "step": "all",
                "errors": all_errors,
                "warnings": llm_warnings,
                "rule_engine_passed": rule_engine_passed
            })
        else:
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "step": "all",
                "errors": [],
                "warnings": llm_warnings,
                "rule_engine_passed": rule_engine_passed
            })

        # 输出统计信息
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield done_event(intent_type="validate", is_form=False)

    @staticmethod
    def _parse_form_data_from_message(message: str) -> Dict[str, str]:
        """
        从用户消息文本中解析表单字段数据。
        支持两种格式：
          - 字段名(字段编码)：显示名[code]   （枚举字段，code 在方括号中）
          - 字段名(字段编码)：值               （普通字段）
        返回 {字段编码: 值} 字典。
        """
        form_data = {}

        # 匹配 "字段编码)：...label[code]" 格式（code 在方括号中）
        # 例如：请假类型(leave_type)：年假[annual]
        # 提取 code：匹配 ) 后面直到最后一个 [ 之间的一切 + 最后一个 ] 内的值
        pattern_brackets = r'([a-zA-Z_][a-zA-Z0-9_]*)\)[：:]\s*.*\[([^\]]+)\]'
        for match in re.finditer(pattern_brackets, message):
            field_code = match.group(1).strip()
            field_value = match.group(2).strip()
            if field_code and field_value:
                form_data[field_code] = field_value

        # 匹配 "字段名(字段编码)：值" 格式（无方括号，即非枚举字段）
        # 例如：请假天数(leave_days)：3
        pattern_simple = r'([a-zA-Z_][a-zA-Z0-9_]*)\)[：:]\s*([^\n[（(（]+?)(?![^\[]*\[)'
        for match in re.finditer(pattern_simple, message):
            field_code = match.group(1).strip()
            field_value = match.group(2).strip()
            # 跳过已有字段（已通过方括号格式解析的）
            if field_code and field_value and field_code not in form_data:
                form_data[field_code] = field_value

        return form_data
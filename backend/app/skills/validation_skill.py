# ValidationSkill - 表单校验技能
# 提供字段级、表单级、基于本体的 LLM 智能校验能力

import logging
from typing import Dict, Any, List, Optional, Tuple

from app.core.config_loader import config_loader
from app.services.validation_service import validation_engine
from app.services.llm_service import llm_service

logger = logging.getLogger("validation_skill")


class ValidationSkill:
    """表单校验技能 - 支持规则引擎校验 + LLM 智能校验"""

    @classmethod
    def validate_field(
        cls,
        field_value: Any,
        rules: List[Dict],
        field_code: str = "",
        field_name: str = ""
    ) -> Dict[str, Any]:
        """
        单字段校验（基于规则引擎）

        Args:
            field_value: 字段值
            rules: 规则列表 [{"rule_type": "min", "rule_value": 0, "message": "..."}]
            field_code: 字段代码（用于生成规范 issues）
            field_name: 字段中文名

        Returns:
            {"success": bool, "valid": bool, "errors": [str], "issues": [ValidationIssue]}
        """
        result = validation_engine.validate_field(field_value, rules)

        # 转换为规范 issues 格式
        issues = []
        for err_msg in result.errors:
            issues.append({
                "field": field_code,
                "field_name": field_name,
                "error_code": cls._detect_error_code(err_msg, rules),
                "level": "error",
                "message": err_msg
            })

        return {
            "success": True,
            "valid": result.valid,
            "errors": result.errors,
            "issues": issues
        }

    @classmethod
    def _detect_error_code(cls, err_msg: str, rules: List[Dict]) -> str:
        """根据错误消息推断错误码"""
        msg_lower = err_msg.lower()
        for rule in rules:
            rule_type = rule.get("rule_type", "")

            if rule_type == "minLength":
                return "ERR_VAL_RANGE"
            elif rule_type == "maxLength":
                return "ERR_VAL_RANGE"
            elif rule_type in ("min", "minimum"):
                return "ERR_VAL_RANGE"
            elif rule_type in ("max", "maximum"):
                return "ERR_VAL_RANGE"
            elif rule_type == "pattern":
                return "ERR_VAL_FORMAT"
            elif rule_type == "email":
                return "ERR_VAL_FORMAT"
            elif rule_type == "phone":
                return "ERR_VAL_FORMAT"
            elif rule_type == "idCard":
                return "ERR_VAL_FORMAT"
            elif rule_type == "url":
                return "ERR_VAL_FORMAT"
            elif rule_type == "enum":
                return "ERR_VAL_RULE_FAIL"
            elif rule_type == "dateMin":
                return "ERR_VAL_RANGE"
            elif rule_type == "dateMax":
                return "ERR_VAL_RANGE"

        if "不能为空" in err_msg or "必填" in err_msg:
            return "ERR_VAL_REQUIRED"
        elif "格式" in err_msg or "格式不正确" in msg_lower or "格式错误" in msg_lower:
            return "ERR_VAL_FORMAT"
        elif "超出" in err_msg or "范围" in msg_lower or "超过" in msg_lower:
            return "ERR_VAL_RANGE"
        elif "不在可" in err_msg or "枚举" in msg_lower or "选项" in msg_lower:
            return "ERR_VAL_RULE_FAIL"

        return "ERR_VAL_RULE_FAIL"

    @classmethod
    def _build_rules_from_field(cls, field: Dict) -> List[Dict]:
        """
        从字段定义构建规则列表，包含枚举校验

        自动从 options 生成 enum 规则，从 ruleDescription 推断规则
        """
        rules = list(field.get("rules", []))
        field_type = field.get("fieldType", "")
        options = field.get("options", [])

        # ══ 枚举/选择类字段：自动添加 enum 规则 ══
        if field_type == "select" and options:
            option_values = []
            option_labels = []  # 带中文标签的选项列表
            for o in options:
                if isinstance(o, dict):
                    value = o.get("value", "")
                    label = o.get("label", value)
                    option_values.append(value)
                    # 生成 "值(标签)" 格式
                    if label != value:
                        option_labels.append(f"{value}({label})")
                    else:
                        option_labels.append(str(value))
                else:
                    option_values.append(str(o))
                    option_labels.append(str(o))

            if option_values:
                rules.append({
                    "rule_type": "enum",
                    "rule_value": option_values,
                    "rule_options": option_labels,  # 新增：带中文的选项列表
                    "message": f"值必须在可选列表中，可选值：{', '.join(option_labels)}"
                })

        return rules

    @classmethod
    def validate_form(
        cls,
        form_data: Dict[str, Any],
        fields: List[Dict]
    ) -> Dict[str, Any]:
        """
        表单校验（基于规则引擎），返回规范化结构

        Args:
            form_data: 表单数据
            fields: 字段定义列表（包含 fieldCode, fieldName, rules, options 等）

        Returns:
            {"success": bool, "valid": bool, "errors": [str], "warnings": [], "issues": [ValidationIssue]}
        """
        all_errors = []
        all_warnings = []
        all_issues = []

        for field_def in fields:
            field_code = field_def.get("fieldCode", "")
            field_name = field_def.get("fieldName", field_code)
            required = field_def.get("required", False)
            rule_desc = field_def.get("ruleDescription", "")

            # 从字段定义构建完整规则（包含自动生成的 enum 规则）
            rules = cls._build_rules_from_field(field_def)

            field_value = form_data.get(field_code)

            # ══ 必填检查 ══
            if required and (field_value is None or field_value == ""):
                err_msg = f"{field_name} 不能为空"
                all_errors.append(err_msg)
                all_issues.append({
                    "field": field_code,
                    "field_name": field_name,
                    "error_code": "ERR_VAL_REQUIRED",
                    "level": "error",
                    "message": err_msg,
                    "rule_description": rule_desc
                })
                continue

            if field_value is not None and field_value != "":
                # ══ 逐规则校验 ══
                result = validation_engine.validate_field(field_value, rules)
                for err_msg in result.errors:
                    all_errors.append(err_msg)
                    # 检查是否有 enum 规则的 rule_options
                    rule_options = None
                    for rule in rules:
                        if rule.get("rule_type") == "enum" and rule.get("rule_options"):
                            rule_options = rule.get("rule_options")
                            break
                    all_issues.append({
                        "field": field_code,
                        "field_name": field_name,
                        "error_code": cls._detect_error_code(err_msg, rules),
                        "level": "error",
                        "message": err_msg,
                        "rule_description": rule_desc,
                        "rule_options": rule_options
                    })

        return {
            "success": True,
            "valid": len(all_errors) == 0,
            "errors": all_errors,
            "warnings": all_warnings,
            "issues": all_issues
        }

    @classmethod
    def validate_form_from_ontology(
        cls,
        form_code: str,
        form_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        从本体加载字段定义，执行规则引擎校验

        自动为 select 类型字段从 options 生成 enum 规则，
        并从 ruleDescription 中推断额外规则（min/max/length 等）

        Returns:
            {"success": bool, "valid": bool, "errors": [str], "warnings": [], "issues": []}
        """
        ontology = config_loader.get_ontology(form_code)
        if not ontology:
            logger.warning(f"[ValidationSkill] 未找到本体 form_code={form_code}")
            return {
                "success": False,
                "valid": False,
                "errors": [f"未找到表单 {form_code} 的本体定义，无法进行规则校验"],
                "warnings": [],
                "issues": []
            }

        fields = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields.append(field)

        return cls.validate_form(form_data, fields)

    @classmethod
    def validate_with_ontology(
        cls,
        form_code: str,
        form_data: Dict[str, Any]
    ) -> Tuple[Dict[str, Any], List[str]]:
        """
        基于本体的 LLM 智能校验

        从本体加载字段定义（包含 ruleDescription + options），
        使用 LLM 理解自然语言规则并进行智能校验。

        Args:
            form_code: 表单类型代码
            form_data: 表单数据

        Returns:
            (result_dict, reasoning_chunks_list)
            - result_dict: {"success", "valid", "errors", "warnings", "method"}
            - reasoning_chunks_list: LLM 模型思考过程列表
        """
        ontology = config_loader.get_ontology(form_code)
        if not ontology:
            logger.warning(f"[ValidationSkill] 未找到本体 form_code={form_code}")
            return {
                "success": False,
                "valid": False,
                "errors": [f"未找到表单 {form_code} 的本体定义，无法进行智能校验"],
                "warnings": [],
                "method": "fallback"
            }, []

        fields = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields.append(field)

        if not fields:
            return {
                "success": True,
                "valid": True,
                "errors": [],
                "warnings": ["表单字段定义为空"],
                "method": "fallback"
            }, []

        # 构建 LLM Prompt（包含完整的 fieldDescription 和 options）
        prompt = cls._build_llm_prompt(form_code, form_data, fields)

        logger.info(f"[ValidationSkill] LLM校验开始 form_code={form_code} 字段数={len(fields)}")

        # 调用 LLM 并获取模型思考过程
        reasoning_chunks = []
        try:
            response, reasoning = llm_service._call_llm_sync_with_reasoning(prompt, max_tokens=2048)

            # 收集模型思考过程
            if reasoning:
                # reasoning 可能是一个长字符串，按句子分割
                import re
                sentences = re.split(r'[。\n]', reasoning)
                for sent in sentences:
                    sent = sent.strip()
                    if sent and len(sent) > 2:
                        reasoning_chunks.append(sent + "。")

        except Exception as e:
            logger.warning(f"[ValidationSkill] LLM 调用失败: {e}")
            return {
                "success": False,
                "valid": True,
                "errors": [],
                "warnings": ["智能校验服务暂时不可用，已跳过"],
                "method": "fallback"
            }, []

        if not response:
            return {
                "success": False,
                "valid": True,
                "errors": [],
                "warnings": ["智能校验返回为空，已跳过"],
                "method": "fallback"
            }, reasoning_chunks

        # 解析 LLM 返回
        parsed = llm_service._extract_json(response)
        if not parsed:
            logger.warning(f"[ValidationSkill] JSON 解析失败: {response[:200]}")
            return {
                "success": True,
                "valid": True,
                "errors": [],
                "warnings": ["智能校验结果解析异常，已跳过"],
                "method": "fallback"
            }, reasoning_chunks

        logger.info(f"[ValidationSkill] LLM校验完成 valid={parsed.get('valid')} "
                    f"errors={len(parsed.get('errors', []))} "
                    f"warnings={len(parsed.get('warnings', []))}")

        return {
            "success": True,
            "valid": parsed.get("valid", True),
            "errors": parsed.get("errors", []),
            "warnings": parsed.get("warnings", []),
            "method": "llm"
        }, reasoning_chunks

    @classmethod
    def _build_llm_prompt(
        cls,
        form_code: str,
        form_data: Dict[str, Any],
        fields: List[Dict]
    ) -> str:
        """构建 LLM 校验 Prompt，传递完整的字段定义"""

        # 构建字段定义文本
        field_defs = []
        for f in fields:
            field_code = f.get("fieldCode", "")
            field_name = f.get("fieldName", field_code)
            field_type = f.get("fieldType", "input")
            required = f.get("required", False)
            rule_desc = f.get("ruleDescription", "")
            options = f.get("options", [])

            parts = [f"- {field_name}（{field_code}）"]
            parts.append(f"  类型: {field_type}")
            parts.append(f"  必填: {'是' if required else '否'}")

            # ══ 规则描述：这是 AI 校验的核心依据 ══
            if rule_desc:
                parts.append(f"  规则: {rule_desc}")

            # ══ 选项列表：用于校验枚举类字段的值是否在允许范围内 ══
            if options:
                option_labels = []
                for o in options[:10]:
                    if isinstance(o, dict):
                        label = o.get("label", o.get("value", ""))
                        value = o.get("value", "")
                        option_labels.append(f"{label}({value})" if label != value else label)
                    else:
                        option_labels.append(str(o))
                if option_labels:
                    parts.append(f"  可选值: {', '.join(option_labels)}")

            field_defs.append("\n".join(parts))

        field_defs_text = "\n\n".join(field_defs)

        # 构建提交数据（限制大字段长度）
        data_to_validate = {}
        for k, v in form_data.items():
            if isinstance(v, str) and len(v) > 500:
                data_to_validate[k] = v[:500] + "..."
            else:
                data_to_validate[k] = v

        import json
        form_data_text = json.dumps(data_to_validate, ensure_ascii=False, indent=2)

        return f"""你是一个严格的表单校验助手。请根据字段定义校验用户提交的表单数据。

## 表单类型：{form_code}

## 字段定义：
{field_defs_text}

## 用户提交的数据：
{form_data_text}

## 校验要求：
1. **规则校验**：检查数据是否符合 ruleDescription 中描述的规则
2. **枚举校验**：检查 select 类型字段的值是否在可选值列表中
3. **一致性校验**：检查字段间的逻辑关系（如结束日期必须晚于开始日期、数量不能为负等）
4. **业务语义校验**：检查数据的业务合理性（如金额是否合理、请假天数是否超过限制等）

## 重要说明：
- 请仔细核对每个字段的值与可选值列表是否匹配
- ruleDescription 中的规则是硬性要求，必须严格遵守
- 枚举类字段（如请假类型、部门）只能选择提供的选项

## 输出格式（仅输出 JSON，不要其他内容）：
{{"valid": true或false, "errors": ["错误描述1", "错误描述2"], "warnings": ["警告描述1"]}}

- valid=false 表示有必须修复的错误
- warnings 是建议但不阻塞提交的问题
- error 消息要具体，指出：哪个字段、什么问题、正确值应该是什么
- 错误信息必须使用中文，描述友好易懂"""

    @classmethod
    def validate_batch(
        cls,
        items: List[Dict[str, Any]],
        rules: List[Dict]
    ) -> List[Dict[str, Any]]:
        """
        批量字段校验

        Args:
            items: [{"fieldCode": "xxx", "fieldValue": "xxx"}, ...]
            rules: 规则列表

        Returns:
            [{"fieldCode": "xxx", "valid": bool, "errors": [...]}, ...]
        """
        results = []
        for item in items:
            field_code = item.get("fieldCode", "")
            field_value = item.get("fieldValue")
            result = validation_engine.validate_field(field_value, rules)
            results.append({
                "fieldCode": field_code,
                "valid": result.valid,
                "errors": result.errors
            })
        return results
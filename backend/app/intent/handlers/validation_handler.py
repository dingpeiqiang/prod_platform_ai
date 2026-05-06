# ValidationHandler - 表单校验意图处理器
# 在表单提交时通过 LLM 校验 ruleDescription 规则、字段一致性和业务语义

import json
import logging
from typing import AsyncGenerator, Dict, Any

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, done_event
from ...services.llm_service import llm_service
from ...core.config_loader import config_loader

logger = logging.getLogger("intent.validation_handler")


class ValidationHandler(BaseIntentHandler):
    """表单校验意图 —— 基于 ruleDescription 的 LLM 智能校验"""

    intent_type = "validate"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """
        处理表单校验请求

        IntentContext.intent_data 需要包含：
        - form_code: str 表单代码
        - form_data: Dict 用户提交的表单数据 {"fieldCode": value}
        """
        form_code = ctx.intent_data.get("form_code", "unknown")
        form_data = ctx.intent_data.get("form_data", {})

        yield thinking("🔍 开始智能校验...")

        # 直接从本体加载字段定义（确保 ruleDescription 完整）
        ontology = config_loader.get_ontology(form_code)
        if not ontology:
            logger.warning(f"[ValidationHandler] 未找到本体 form_code={form_code}")
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "warnings": [f"未找到表单 {form_code} 的本体定义，已跳过智能校验"]
            })
            yield done_event(intent_type="validate", is_form=False)
            return

        # 从本体构建 fields 列表
        fields = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields.append(field)

        if not fields:
            logger.warning(f"[ValidationHandler] 本体 {form_code} 没有定义任何字段")
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "warnings": ["表单字段定义为空，已跳过智能校验"]
            })
            yield done_event(intent_type="validate", is_form=False)
            return

        # 构建 LLM 校验 Prompt
        prompt = self._build_validation_prompt(form_code, form_data, fields)
        logger.info(f"[ValidationHandler] 开始校验 form_code={form_code} 本体字段数={len(fields)}")

        try:
            # 调用 LLM 进行校验
            response = llm_service._call_llm(prompt, max_tokens=2048)

            if not response:
                logger.warning("[ValidationHandler] LLM 调用返回空，使用兜底逻辑")
                yield sse({
                    "type": "validation_pass",
                    "form_code": form_code,
                    "warnings": ["智能校验服务暂时不可用，已跳过"]
                })
                yield done_event(intent_type="validate", is_form=False)
                return

            # 解析 LLM 返回
            result = self._parse_validation_result(response)

            if result.get("valid", False):
                error_count = len(result.get("errors", []))
                warning_count = len(result.get("warnings", []))
                yield thinking(f"✅ 校验通过（{error_count} 个错误，{warning_count} 个警告）")
                yield sse({
                    "type": "validation_pass",
                    "form_code": form_code,
                    "errors": result.get("errors", []),
                    "warnings": result.get("warnings", [])
                })
            else:
                errors = result.get("errors", [])
                yield thinking(f"❌ 校验未通过，发现 {len(errors)} 个问题")
                for err in errors:
                    yield thinking(f"   • {err}")

                yield sse({
                    "type": "validation_fail",
                    "form_code": form_code,
                    "errors": errors,
                    "warnings": result.get("warnings", [])
                })

        except Exception as e:
            logger.exception(f"[ValidationHandler] 校验异常: {e}")
            yield thinking("⚠️ 校验过程出现异常，已跳过智能校验")
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "warnings": ["智能校验暂时不可用"]
            })

        yield done_event(intent_type="validate", is_form=False)

    def _build_validation_prompt(
        self,
        form_code: str,
        form_data: Dict[str, Any],
        fields: list
    ) -> str:
        """构建校验 Prompt"""

        # 构建字段定义文本
        field_defs = []
        for f in fields:
            field_code = f.get("fieldCode", "")
            field_name = f.get("fieldName", field_code)
            field_type = f.get("fieldType", "input")
            required = f.get("required", False)
            rule_desc = f.get("ruleDescription", "")
            options = f.get("options", [])

            parts = [f"- {field_name} ({field_code})"]
            parts.append(f"  类型: {field_type}")
            parts.append(f"  必填: {'是' if required else '否'}")
            if rule_desc:
                parts.append(f"  规则: {rule_desc}")
            if options:
                option_labels = []
                for o in options[:10]:  # 最多10个选项
                    if isinstance(o, dict):
                        option_labels.append(o.get("label", o.get("value", "")))
                    else:
                        option_labels.append(str(o))
                if option_labels:
                    parts.append(f"  可选值: {', '.join(option_labels)}")

            field_defs.append("\n".join(parts))

        field_defs_text = "\n\n".join(field_defs)

        # 构建提交数据文本（限制大字段长度）
        data_to_validate = {}
        for k, v in form_data.items():
            if isinstance(v, str) and len(v) > 500:
                data_to_validate[k] = v[:500] + "..."
            else:
                data_to_validate[k] = v

        form_data_text = json.dumps(data_to_validate, ensure_ascii=False, indent=2)

        prompt = f"""你是一个严格的表单校验助手。请根据字段定义校验用户提交的表单数据。

## 表单类型：{form_code}

## 字段定义：
{field_defs_text}

## 用户提交的数据：
{form_data_text}

## 校验要求：
1. **规则校验**：检查数据是否符合 fieldDescription 中描述的规则
2. **一致性校验**：检查字段间的逻辑关系（如结束日期必须晚于开始日期、数量不能为负等）
3. **业务语义校验**：检查数据的业务合理性（如金额是否合理、理由是否充分）

## 输出格式（仅输出 JSON，不要其他内容）：
{{"valid": true或false, "errors": ["错误描述1", "错误描述2"], "warnings": ["警告描述1"]}}

- 当有必须修复的错误时 valid=false
- warnings 是建议修复但不阻塞提交的问题
- error 消息要具体，指出具体问题和允许的范围"""

        return prompt

    def _parse_validation_result(self, response: str) -> Dict[str, Any]:
        """解析 LLM 返回的 JSON 响应"""

        parsed = llm_service._extract_json(response)

        if not parsed:
            logger.warning(f"[ValidationHandler] JSON 解析失败，响应: {response[:200]}...")
            # 兜底：解析失败时默认通过
            return {
                "valid": True,
                "errors": [],
                "warnings": ["智能校验结果解析异常，已跳过"]
            }

        # 确保返回格式正确
        return {
            "valid": parsed.get("valid", True),
            "errors": parsed.get("errors", []),
            "warnings": parsed.get("warnings", [])
        }
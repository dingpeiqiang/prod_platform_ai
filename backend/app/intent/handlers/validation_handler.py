# ValidationHandler - 表单校验意图处理器
# 在表单提交时通过 LLM 校验 ruleDescription 规则、字段一致性和业务语义

import logging
from typing import AsyncGenerator, Dict, Any

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, done_event
from ...skills.validation_skill import ValidationSkill

logger = logging.getLogger("intent.validation_handler")


class ValidationHandler(BaseIntentHandler):
    """表单校验意图 —— 基于 ruleDescription 的 LLM 智能校验"""

    intent_type = "validate"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """
        处理表单校验请求，分步骤执行：
        1. 规则引擎校验（快速过滤）
        2. LLM 智能校验（语义+一致性，含模型思考过程）

        IntentContext.intent_data 需要包含：
        - form_code: str 表单代码
        - form_data: Dict 用户提交的表单数据 {"fieldCode": value}
        """
        form_code = ctx.intent_data.get("form_code", "unknown")
        form_data = ctx.intent_data.get("form_data", {})

        # ═══════════════════════════════════════════════════════════
        # 步骤 1/2：规则引擎校验
        # ═══════════════════════════════════════════════════════════
        yield thinking("🔍 步骤 1/2：执行规则引擎校验...")

        # 委托 ValidationSkill 执行规则引擎校验（同步）
        rule_result = ValidationSkill.validate_form_from_ontology(form_code, form_data)

        if not rule_result.get("valid"):
            errors = rule_result.get("errors", [])
            yield thinking(f"❌ 步骤 1/2：规则引擎校验失败，发现 {len(errors)} 个问题")
            for err in errors:
                yield thinking(f"   • {err}")

            yield sse({
                "type": "validation_fail",
                "form_code": form_code,
                "step": "rule_engine",
                "errors": errors,
                "warnings": rule_result.get("warnings", [])
            })
            yield done_event(intent_type="validate", is_form=False)
            return

        yield thinking("✅ 步骤 1/2：规则引擎校验通过")

        # ═══════════════════════════════════════════════════════════
        # 步骤 2/2：LLM 智能校验（包含模型思考过程）
        # ═══════════════════════════════════════════════════════════
        yield thinking("🤖 步骤 2/2：调用 AI 模型进行智能校验...")
        yield thinking("💭 AI 正在分析数据合理性、字段一致性、业务语义...")

        # 委托 ValidationSkill 执行 LLM 智能校验（会返回 reasoning）
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

        if llm_result.get("valid"):
            errors = llm_result.get("errors", [])
            warnings = llm_result.get("warnings", [])
            yield thinking(f"✅ 步骤 2/2：AI 智能校验通过（{len(errors)} 个错误，{len(warnings)} 个警告）")

            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "step": "llm_validation",
                "errors": errors,
                "warnings": warnings
            })
        else:
            errors = llm_result.get("errors", [])
            yield thinking(f"❌ 步骤 2/2：AI 智能校验未通过，发现 {len(errors)} 个问题")
            for err in errors:
                yield thinking(f"   • {err}")

            yield sse({
                "type": "validation_fail",
                "form_code": form_code,
                "step": "llm_validation",
                "errors": errors,
                "warnings": llm_result.get("warnings", [])
            })

        yield done_event(intent_type="validate", is_form=False)
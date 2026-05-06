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
        处理表单校验请求

        IntentContext.intent_data 需要包含：
        - form_code: str 表单代码
        - form_data: Dict 用户提交的表单数据 {"fieldCode": value}

        委托 ValidationSkill 执行实际校验，保持 IntentHandler 只负责 SSE 事件
        """
        form_code = ctx.intent_data.get("form_code", "unknown")
        form_data = ctx.intent_data.get("form_data", {})

        yield thinking("🔍 开始智能校验...")

        # 委托给 ValidationSkill 执行校验
        result = ValidationSkill.validate_with_ontology(form_code, form_data)

        if result.get("success"):
            if result.get("valid"):
                errors = result.get("errors", [])
                warnings = result.get("warnings", [])
                method = result.get("method", "llm")
                yield thinking(f"✅ 校验通过（{len(errors)} 个错误，{len(warnings)} 个警告）[方法: {method}]")
                yield sse({
                    "type": "validation_pass",
                    "form_code": form_code,
                    "errors": errors,
                    "warnings": warnings
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
        else:
            # 校验失败（LLM 服务异常等）
            warnings = result.get("warnings", [])
            if warnings:
                for w in warnings:
                    yield thinking(f"⚠️ {w}")
            yield sse({
                "type": "validation_pass",
                "form_code": form_code,
                "errors": [],
                "warnings": warnings
            })

        yield done_event(intent_type="validate", is_form=False)
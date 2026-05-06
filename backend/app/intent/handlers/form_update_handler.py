# FormUpdateHandler - 表单更新意图处理器
# 对应 chat.py 第890-915行

import time
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, intent_event, done_event


class FormUpdateHandler(BaseIntentHandler):
    """表单更新意图 —— 增量更新字段"""

    intent_type = "form_update"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（提取字段）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
        """
        detected_form_code = ctx.intent_data.get("detectedFormCode", "")
        form_name = ""
        if detected_form_code and detected_form_code in ctx.ontologies:
            form_name = ctx.ontologies[detected_form_code].get("formName", detected_form_code)

        extracted = ctx.intent_data.get("extractedFields", {})
        confidence = ctx.intent_data.get("confidence", 0)

        # ═══ Phase 1：识别 ══════════════════════════════════════════
        yield thinking(f"📋 识别到表单更新「{form_name or detected_form_code}」", result={
            "formCode": detected_form_code,
            "formName": form_name or detected_form_code
        })

        # ═══ Phase 2：执行 ══════════════════════════════════════════
        yield thinking(
            f"📝 提取到 {len(extracted)} 个待更新字段" if extracted else "⚠️ 未提取到任何字段值",
            result={
                "extractedFields": list(extracted.keys()),
                "extractedCount": len(extracted),
                "confidence": confidence,
                "confidenceLevel": "high" if confidence >= 0.8 else "medium" if confidence >= 0.5 else "low"
            }
        )

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "update", ctx.intent_result, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)
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
        detected_form_code = ctx.intent_data.get("detectedFormCode", "")
        form_name = ""
        if detected_form_code and detected_form_code in ctx.ontologies:
            form_name = ctx.ontologies[detected_form_code].get("formName", detected_form_code)
        yield thinking(f"🔄 识别到表单更新请求: {form_name or detected_form_code}")

        extracted = ctx.intent_data.get("extractedFields", {})
        if extracted:
            field_details = [f"{k}={v}" for k, v in extracted.items()]
            yield thinking(f"📝 提取到 {len(extracted)} 个待更新字段:")
            yield thinking(f"   {', '.join(field_details)}")
        else:
            yield thinking("⚠️ 未提取到任何字段值，请检查用户输入")

        confidence = ctx.intent_data.get("confidence", 0)
        yield thinking(f"📊 更新置信度: {confidence}")

        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "update", ctx.intent_result, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)

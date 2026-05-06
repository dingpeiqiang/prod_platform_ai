# FormHandler - 表单首次生成意图处理器
# 对应 chat.py 第802-887行

import time
import logging
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, merge_field_recommendations, intent_event, done_event
from ...services.recommendation_engine import get_recommendation_engine

logger = logging.getLogger("intent.form_handler")


class FormHandler(BaseIntentHandler):
    """表单首次生成意图 —— 识别表单类型 + 推荐引擎 + SSE 输出"""

    intent_type = "form"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        form_code = ctx.intent_data.get("formCode")
        form_name = ""
        if form_code and form_code in ctx.ontologies:
            form_name = ctx.ontologies[form_code].get("formName", form_code)

        extracted = ctx.intent_data.get("extractedFields", {})
        confidence = ctx.intent_data.get("confidence", 0)

        # ── Phase 1：表单识别 ──────────────────────────────────────────
        yield thinking(f"📋 识别到表单「{form_name or form_code}」", result={
            "formCode": form_code,
            "formName": form_name or form_code,
            "extractedFields": list(extracted.keys()),
            "extractedCount": len(extracted),
            "confidence": confidence,
            "confidenceLevel": "high" if confidence >= 0.8 else "medium" if confidence >= 0.5 else "low"
        })

        # ── Phase 2：历史推荐（可选） ─────────────────────────────────
        try:
            recommendation_engine = get_recommendation_engine()
            conversation_context = {
                "messages": [
                    {"role": msg.role, "content": msg.content}
                    for msg in ctx.request.messages
                ],
                "extractedFields": extracted,
                "lastUserMessage": ctx.last_user_message
            }

            ontology_def = ctx.ontologies.get(form_code, {})
            all_field_codes = []
            for entity in ontology_def.get("entities", []):
                for f in entity.get("fields", []):
                    all_field_codes.append(f.get("fieldCode"))

            recommendations_result = recommendation_engine.batch_recommend(
                form_code=form_code,
                extracted_fields=extracted,
                user_input=ctx.last_user_message,
                user_id=ctx.request.userId,
                conversation_context=conversation_context,
                max_per_field=5,
                db=ctx.db,
                field_codes=all_field_codes if all_field_codes else None
            )

            all_recommendations = {}
            for field_code, result in recommendations_result.items():
                if result.success and result.recommendations:
                    all_recommendations[field_code] = {
                        "items": [r.to_dict() for r in result.recommendations],
                        "strategyUsed": result.strategy_used,
                        "totalCandidates": result.total_candidates,
                        "processingTimeMs": round(result.processing_time_ms, 2)
                    }

            if all_recommendations:
                field_summary = {k: len(v.get("items", [])) for k, v in all_recommendations.items()}
                yield thinking(f"📚 为 {len(all_recommendations)} 个字段生成推荐", result={
                    "fieldCount": len(all_recommendations),
                    "fieldSummary": field_summary
                })

                llm_recs = ctx.intent_data.get("fieldRecommendations", {})
                merged_recs = merge_field_recommendations(llm_recs, all_recommendations)
                ctx.intent_data["fieldRecommendations"] = merged_recs
                logger.debug(f"[chat/stream] 🔀 合并后推荐字段数: {len(merged_recs)}")
        except Exception as rec_err:
            logger.warning(f"[chat/stream] 获取历史推荐失败: {rec_err}")

        # ── 内部自动化（不作为用户可见步骤） ───────────────────────────
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "generate", ctx.intent_result, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)

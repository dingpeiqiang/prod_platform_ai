# FormHandler - 表单首次生成意图处理器
# 对应 chat.py 第802-887行

import time
import logging
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, merge_field_recommendations, intent_event, done_event
from ...services.recommendation_engine import get_recommendation_engine
from ...services.ai_inference_service import get_ai_inference_service
from ...core.config_loader import config_loader

logger = logging.getLogger("intent.form_handler")


class FormHandler(BaseIntentHandler):
    """表单首次生成意图 —— 识别表单类型 + 推荐引擎 + SSE 输出"""

    intent_type = "form"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（字段提取 + 推荐引擎）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
        """
        form_code = ctx.intent_data.get("formCode")
        form_name = ""
        if form_code and form_code in ctx.ontologies:
            form_name = ctx.ontologies[form_code].get("formName", form_code)

        extracted = ctx.intent_data.get("extractedFields", {})
        confidence = ctx.intent_data.get("confidence", 0)

        # ═══ Phase 1：识别 ══════════════════════════════════════════
        yield thinking(f"📋 识别到表单「{form_name or form_code}」", result={
            "formCode": form_code,
            "formName": form_name or form_code,
            "confidence": confidence,
            "confidenceLevel": "high" if confidence >= 0.8 else "medium" if confidence >= 0.5 else "low"
        })

        # ═══ Phase 2：执行 ══════════════════════════════════════════
        # ── Step 1：AI 字段推断（单独调用 LLM）──────────────────────
        yield thinking("🧠 调用 AI 进行字段推断...")
        
        try:
            ai_service = get_ai_inference_service()
            inferred_fields = ai_service.infer_fields(
                form_code=form_code,
                user_input=ctx.last_user_message or "",
                context={
                    "userId": ctx.request.userId if ctx.request and hasattr(ctx.request, 'userId') else None,
                    "sessionId": ctx.request.sessionId if ctx.request and hasattr(ctx.request, 'sessionId') else None,
                }
            )
            
            # 合并 LLM 意图识别的结果和 AI 推断的结果
            # AI 推断的优先级更高（因为是基于本体的完整推断）
            extracted = {**extracted, **inferred_fields}
            ctx.intent_data["extractedFields"] = extracted
            
            yield thinking(
                f"✅ AI 推断完成，共 {len(inferred_fields)} 个字段",
                result={
                    "inferredFields": list(inferred_fields.keys()),
                    "inferredCount": len(inferred_fields),
                    "sample": dict(list(inferred_fields.items())[:5])  # 前5个字段样本
                }
            )
        except Exception as e:
            logger.warning(f"[form_handler] AI 推断失败: {e}，使用 LLM 意图识别的结果")
            yield thinking(f"⚠️ AI 推断失败，使用已有数据")

        # ── Step 2：历史推荐 ───────────────────────────────────────
        try:
            recommendation_engine = get_recommendation_engine()
            # 处理 request 可能为 None 的情况（REST API 调用）
            messages_list = []
            if ctx.request and hasattr(ctx.request, 'messages'):
                messages_list = [{"role": msg.role, "content": msg.content} for msg in ctx.request.messages]
            
            conversation_context = {
                "messages": messages_list,
                "extractedFields": extracted,
                "lastUserMessage": ctx.last_user_message
            }

            ontology_def = ctx.ontologies.get(form_code, {})
            all_field_codes = [f.get("fieldCode") for entity in ontology_def.get("entities", []) for f in entity.get("fields", [])]

            rec_config = config_loader.get_recommendation_config()
            max_recs = rec_config.get('recommendationLimit', 3)

            recommendations_result = recommendation_engine.batch_recommend(
                form_code=form_code,
                extracted_fields=extracted,
                user_input=ctx.last_user_message,
                user_id=ctx.request.userId if ctx.request and hasattr(ctx.request, 'userId') else None,
                conversation_context=conversation_context,
                max_per_field=max_recs,
                db=ctx.db,
                field_codes=all_field_codes if all_field_codes else None
            )

            all_recommendations = {}
            for field_code, rec_result in recommendations_result.items():
                if rec_result.success and rec_result.recommendations:
                    top_recs = rec_result.recommendations[:max_recs]
                    all_recommendations[field_code] = {
                        "items": [r.to_dict() for r in top_recs],
                        "strategyUsed": rec_result.strategy_used,
                        "totalCandidates": rec_result.total_candidates,
                        "processingTimeMs": round(rec_result.processing_time_ms, 2)
                    }

            field_summary = {k: len(v.get("items", [])) for k, v in all_recommendations.items()}
            total_recs = sum(field_summary.values())
            yield thinking(
                f"📚 为 {len(all_recommendations)} 个字段生成推荐，共 {total_recs} 条（每字段最多{max_recs}条）",
                result={
                    "fieldCount": len(all_recommendations),
                    "fieldSummary": field_summary,
                    "totalRecommendations": total_recs,
                    "maxPerField": max_recs
                }
            )

            llm_recs = ctx.intent_data.get("fieldRecommendations", {})
            merged_recs = merge_field_recommendations(llm_recs, all_recommendations)
            ctx.intent_data["fieldRecommendations"] = merged_recs
            logger.debug(f"[form_handler] 🔀 合并后推荐字段数: {len(merged_recs)}")
        except Exception as rec_err:
            logger.warning(f"[form_handler] 获取历史推荐失败: {rec_err}")

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "generate", ctx.intent_data, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)

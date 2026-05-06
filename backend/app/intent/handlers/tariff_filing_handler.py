# TariffFilingHandler - 资费备案公示表单处理器
#
# 注意：MCP 工具调用已由 chat.py 的 LLM 意图识别阶段处理。
# LLM 根据用户输入决定是否调用 query_tariff_by_code 等 MCP 工具，
# chat.py 执行工具并将结果合并到 extractedFields 后，才 dispatch 到这里。
# 本 handler 只负责：识别 formCode + 继续标准表单填充流程。
#
# LLM 决策流程（chat.py）：
#   用户: "我要备案套餐 P000111"
#   → LLM 返回: intentType="form", formCode="tariff_filing_publicity", tool_calls=[{name:"query_tariff_by_code", arguments:{tariff_code:"P000111"}}]
#   → chat.py 执行 MCP → 合并结果到 extractedFields
#   → dispatch("form", ctx) → FormHandler 处理

import time
import logging
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, ask_user, sse, merge_field_recommendations, intent_event, done_event
from ...services.recommendation_engine import get_recommendation_engine
from ...skills.field_extraction import FieldExtractionSkill

logger = logging.getLogger("intent.tariff_filing_handler")


class TariffFilingHandler(BaseIntentHandler):
    """
    资费备案公示表单意图处理器

    formCode: tariff_filing_publicity
    触发词：备案、套餐编码(P开头)、资费备案公示 等

    注意：MCP 工具调用由 chat.py LLM 意图识别阶段处理（见 smart_intent_recognition.txt）
    本 handler 只负责表单字段填充和推荐引擎整合。
    """

    intent_type = "tariff_filing"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（字段提取 + 推荐引擎）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
        """
        form_code = "tariff_filing_publicity"
        form_name = "资费备案公示"

        extracted = ctx.intent_data.get("extractedFields", {})

        # ═══ Phase 1：识别 ══════════════════════════════════════════
        has_mcp_fill = bool(extracted.get("bossid"))
        yield thinking(f"📋 识别到「{form_name}」", result={
            "formCode": form_code,
            "formName": form_name,
            "extractedFields": list(extracted.keys()),
            "extractedCount": len(extracted),
            "mcpPrefilled": has_mcp_fill,
            "mcpTariffCode": extracted.get("bossid") if has_mcp_fill else None
        })

        # ── 关键字段缺失检查（提前返回，不输出空白表单）──────────────
        missing_info = FieldExtractionSkill.check_missing_required(form_code, extracted)
        if missing_info:
            yield ask_user(
                f"⚠️ {missing_info['message']}\n\n"
                f"👉 {missing_info['hint']}"
            )
            yield sse({"type": "missing_field", **missing_info})
            ctx.intent_data["_missing_required"] = missing_info["field"]
            return

        # ═══ Phase 2：执行 ══════════════════════════════════════════
        # ── Step 1：历史推荐（可选）─────────────────────────────────
        try:
            recommendation_engine = get_recommendation_engine()
            conversation_context = {
                "messages": [{"role": msg.role, "content": msg.content} for msg in ctx.request.messages],
                "extractedFields": extracted,
                "lastUserMessage": ctx.last_user_message
            }

            ontology_def = ctx.ontologies.get(form_code, {})
            all_field_codes = [f.get("fieldCode") for entity in ontology_def.get("entities", []) for f in entity.get("fields", [])]

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
        except Exception as rec_err:
            logger.warning(f"[TariffFilingHandler] 获取历史推荐失败: {rec_err}")

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "generate", ctx.intent_result, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)

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
from ..utils import thinking, sse, merge_field_recommendations, intent_event, done_event
from ...services.recommendation_engine import get_recommendation_engine

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
        form_code = "tariff_filing_publicity"
        form_name = "资费备案公示"

        yield thinking(f"📋 识别到表单类型: {form_name}")

        extracted = ctx.intent_data.get("extractedFields", {})

        # 如果 extractedFields 已有数据（来自 chat.py 的 tool_calls 合并结果），展示信息
        if extracted:
            field_count = len(extracted)
            # 检查是否已有 seq_no（套餐编码）
            if "seq_no" in extracted or "name" in extracted:
                code = extracted.get("seq_no", extracted.get("name", ""))
                yield thinking(f"✅ 已通过 MCP 查询自动填充表单数据: {code}")
                yield thinking(f"📝 预填充 {field_count} 个字段")
            else:
                yield thinking(f"📝 提取到 {field_count} 个字段")
        else:
            yield thinking("📝 未提取到字段值，将展示空表单供手动填写")

        # ── 获取历史推荐（与 FormHandler 相同逻辑）─────────────────────────
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
                logger.info(f"[TariffFilingHandler] 📚 获取到历史推荐: {all_recommendations}")
                yield thinking(f"📚 基于历史数据为 {len(all_recommendations)} 个字段生成推荐")

                llm_recs = ctx.intent_data.get("fieldRecommendations", {})
                merged_recs = merge_field_recommendations(llm_recs, all_recommendations)
                ctx.intent_data["fieldRecommendations"] = merged_recs

        except Exception as rec_err:
            logger.warning(f"[TariffFilingHandler] 获取历史推荐失败: {rec_err}")

        # ── 输出表单事件 ────────────────────────────────────────────────
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        yield intent_event("form", "generate", ctx.intent_result, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)
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
        yield thinking("🧠 正在分析字段推断...")
        
        try:
            ai_service = get_ai_inference_service()
            
            # 获取 prompt 内容用于日志记录
            ontology = config_loader.get_ontology(form_code)
            if ontology:
                inference_prompt = ai_service._build_inference_prompt(
                    ontology=ontology,
                    user_input=ctx.last_user_message or "",
                    context={
                        "userId": ctx.request.userId if ctx.request and hasattr(ctx.request, 'userId') else None,
                        "sessionId": ctx.request.sessionId if ctx.request and hasattr(ctx.request, 'sessionId') else None,
                    }
                )
                logger.debug(f"[form_handler] AI 推断 Prompt 输入（{len(inference_prompt)} 字符）: {inference_prompt[:2000]}")
            
            inference_result = ai_service.infer_fields(
                form_code=form_code,
                user_input=ctx.last_user_message or "",
                context={
                    "userId": ctx.request.userId if ctx.request and hasattr(ctx.request, 'userId') else None,
                    "sessionId": ctx.request.sessionId if ctx.request and hasattr(ctx.request, 'sessionId') else None,
                }
            )
            
            inferred_fields = inference_result.get("extractedFields", {})
            reasoning = inference_result.get("reasoning", "")
            
            logger.debug(f"[form_handler] AI 推断 Response 输出（{len(str(inference_result))} 字符）: {str(inference_result)[:2000]}")
            
            # 如果有推理过程，发送给前端显示（附加到上一个 thinking 步骤）
            if reasoning:
                from ..utils import reasoning as reasoning_event
                yield reasoning_event(reasoning)
            
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
        logger.info(f"[form_handler] 🚀 开始 Step 2：历史推荐")
        try:
            # 先获取字段列表用于日志
            ontology_def = ctx.ontologies.get(form_code, {})
            all_field_codes = [f.get("fieldCode") for entity in ontology_def.get("entities", []) for f in entity.get("fields", [])]
            
            logger.info(f"[form_handler] 📋 字段列表已获取: {len(all_field_codes)} 个字段")
            logger.debug(f"[form_handler] 字段代码: {all_field_codes[:10]}...")  # 只显示前10个
            
            logger.info(f"[form_handler] 📤 准备发送 thinking 事件: 📚 正在查询历史推荐数据...")
            yield thinking("📚 正在查询历史推荐数据...", result={
                "step": "recommendation_engine_init",
                "formCode": form_code,
                "fieldCount": len(all_field_codes)
            })
            logger.info(f"[form_handler] ✅ thinking 事件已发送")
            
            recommendation_engine = get_recommendation_engine()
            logger.info(f"[form_handler] 🔧 推荐引擎实例已获取")
            
            # 处理 request 可能为 None 的情况（REST API 调用）
            messages_list = []
            if ctx.request and hasattr(ctx.request, 'messages'):
                messages_list = [{"role": msg.role, "content": msg.content} for msg in ctx.request.messages]
                logger.info(f"[form_handler] 📨 消息列表: {len(messages_list)} 条")
            else:
                logger.warning(f"[form_handler] ⚠️ ctx.request 为 None 或没有 messages 属性")
            
            conversation_context = {
                "messages": messages_list,
                "extractedFields": extracted,
                "lastUserMessage": ctx.last_user_message
            }
            logger.info(f"[form_handler] 💬 对话上下文已构建")

            rec_config = config_loader.get_recommendation_config()
            max_recs = rec_config.get('recommendationLimit', 3)
            logger.info(f"[form_handler] ⚙️ 推荐配置: max_recs={max_recs}")
            
            logger.info(f"[form_handler] 📚 开始批量推荐 form_code={form_code}, 字段数={len(all_field_codes)}")
            
            # 发送正在执行的策略信息
            logger.info(f"[form_handler] 📤 准备发送 thinking 事件: 🔧 推荐引擎执行中...")
            yield thinking("🔧 推荐引擎执行中...", result={
                "step": "recommendation_engine_execute",
                "strategies": ["ai_recommend", "frequency", "user_personalized", "time_decay", "static"],
                "maxPerField": max_recs,
                "maxHistoryRecords": rec_config.get('historyQueryLimit', 1000)
            })
            logger.info(f"[form_handler] ✅ thinking 事件已发送")

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
            logger.info(f"[form_handler] ✅ batch_recommend 执行完成，返回 {len(recommendations_result)} 个字段的结果")

            all_recommendations = {}
            strategy_summary = {}  # 统计各策略使用情况
            strategy_detail_stats = {}  # 策略详细统计
            
            logger.info(f"[form_handler] 🔄 开始处理推荐结果...")
            for field_code, rec_result in recommendations_result.items():
                if rec_result.success and rec_result.recommendations:
                    top_recs = rec_result.recommendations[:max_recs]
                    all_recommendations[field_code] = {
                        "items": [r.to_dict() for r in top_recs],
                        "strategyUsed": rec_result.strategy_used,
                        "totalCandidates": rec_result.total_candidates,
                        "processingTimeMs": round(rec_result.processing_time_ms, 2)
                    }
                    
                    # 【修复】strategy_used 是 List[str]，需要遍历统计
                    strategies = rec_result.strategy_used or ["unknown"]
                    for strategy in strategies:
                        strategy_summary[strategy] = strategy_summary.get(strategy, 0) + 1
                        if strategy not in strategy_detail_stats:
                            strategy_detail_stats[strategy] = {
                                "fieldCount": 0,
                                "totalCandidates": 0,
                                "avgProcessingTime": 0,
                                "totalProcessingTime": 0
                            }
                        strategy_detail_stats[strategy]["fieldCount"] += 1
                        strategy_detail_stats[strategy]["totalCandidates"] += rec_result.total_candidates
                        strategy_detail_stats[strategy]["totalProcessingTime"] += rec_result.processing_time_ms
            
            # 计算平均处理时间
            for strategy in strategy_detail_stats:
                stats = strategy_detail_stats[strategy]
                if stats["fieldCount"] > 0:
                    stats["avgProcessingTime"] = round(stats["totalProcessingTime"] / stats["fieldCount"], 2)
                del stats["totalProcessingTime"]  # 删除中间值，保留平均时间
            
            field_summary = {k: len(v.get("items", [])) for k, v in all_recommendations.items()}
            total_recs = sum(field_summary.values())
            total_processing_time = sum(r.processing_time_ms for r in recommendations_result.values() if r.success)
            
            logger.info(f"[form_handler] 📊 推荐结果汇总: {len(all_recommendations)} 个字段, {total_recs} 条推荐")
            logger.info(f"[form_handler] 📈 策略使用统计: {strategy_summary}")
            
            # 构建策略摘要
            strategy_details = ", ".join([f"{k}: {v}个字段" for k, v in strategy_summary.items()])
            
            logger.info(f"[form_handler] 📤 准备发送 thinking 事件: 📚 为 {len(all_recommendations)} 个字段生成推荐...")
            yield thinking(
                f"📚 为 {len(all_recommendations)} 个字段生成推荐，共 {total_recs} 条",
                result={
                    "step": "recommendation_complete",
                    "fieldCount": len(all_recommendations),
                    "fieldSummary": field_summary,
                    "totalRecommendations": total_recs,
                    "maxPerField": max_recs,
                    "strategySummary": strategy_summary,
                    "strategyDetails": strategy_details,
                    "strategyStats": strategy_detail_stats,
                    "processingTimeMs": round(total_processing_time, 2)
                }
            )
            logger.info(f"[form_handler] ✅ thinking 事件已发送")
            
            # 【新增】发送推荐引擎处理详情
            if all_recommendations:
                from ..utils import reasoning as reasoning_event
                
                # 策略图标映射
                strategy_icons = {
                    "ai_recommend": "🤖",
                    "frequency": "📊",
                    "user_personalized": "👤",
                    "time_decay": "⏰",
                    "static": "📁",
                    "context": "🔗",
                    "llm_extraction": "🧠"
                }
                
                # 构建策略执行详情
                strategy_lines = []
                for strategy, stats in sorted(strategy_detail_stats.items()):
                    icon = strategy_icons.get(strategy, "📋")
                    strategy_lines.append(f"{icon} *{strategy}*: {stats['fieldCount']}个字段, {stats['totalCandidates']}个候选, {stats['avgProcessingTime']}ms/字段")
                
                # 构建推荐理由摘要
                reason_lines = []
                for field_code, rec_data in list(all_recommendations.items())[:8]:  # 显示前8个字段
                    items = rec_data.get("items", [])
                    strategies = rec_data.get("strategyUsed", ["unknown"])
                    strategy_str = strategies[0] if isinstance(strategies, list) and len(strategies) > 0 else (strategies if isinstance(strategies, str) else "unknown")
                    if items:
                        top_value = items[0].get("value", "")
                        source = items[0].get("source", "")
                        reason_lines.append(f"- `{field_code}`: {top_value} (来源:{source}, {strategy_str})")
                
                # 构建完整的推荐引擎处理步骤
                reasoning_text = "📊 推荐引擎处理详情\n\n"
                reasoning_text += "策略执行统计:\n"
                reasoning_text += "\n".join(strategy_lines) + "\n\n"
                reasoning_text += "推荐结果摘要:\n"
                if reason_lines:
                    reasoning_text += "\n".join(reason_lines)
                    if len(all_recommendations) > 8:
                        reasoning_text += f"\n... 还有 {len(all_recommendations) - 8} 个字段的推荐"
                else:
                    reasoning_text += "暂无详细推荐信息"
                reasoning_text += f"\n\n---\n总耗时: *{total_processing_time:.2f}ms*"
                
                yield reasoning_event(reasoning_text)
            else:
                # 如果没有推荐结果，也给出提示
                yield thinking("⚠️ 未找到历史推荐数据，将使用默认值")

            llm_recs = ctx.intent_data.get("fieldRecommendations", {})
            merged_recs = merge_field_recommendations(llm_recs, all_recommendations)
            ctx.intent_data["fieldRecommendations"] = merged_recs
            logger.debug(f"[form_handler] 🔀 合并后推荐字段数: {len(merged_recs)}")
        except Exception as rec_err:
            logger.exception(f"[form_handler] ❌ 获取历史推荐失败: {rec_err}")
            logger.exception(f"[form_handler] 异常类型: {type(rec_err).__name__}")
            import traceback
            logger.error(f"[form_handler] 堆栈跟踪:\n{traceback.format_exc()}")
            # 【修复】即使出错也要告诉用户
            yield thinking(f"⚠️ 推荐引擎异常: {str(rec_err)[:100]}")

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = True
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
        
        # 【新增】发送最终提示，告诉用户即将生成表单
        yield thinking(f"✅ 准备生成 {form_name or form_code} 表单...")

        yield intent_event("form", "generate", ctx.intent_data, is_form=True)
        yield done_event("form", is_form=True, intent_data=ctx.intent_data)

# ConfigureHandler - 新业务配置意图处理器
# 对应 chat.py 第1097-1213行

import asyncio
import time
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, reasoning, intent_event, done_event
from ...services.admin_service import AdminService


class ConfigureHandler(BaseIntentHandler):
    """新业务配置意图 —— AI 对话生成新表单配置"""

    intent_type = "configure"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（生成配置）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
        """
        suggested_code = ctx.intent_data.get("formCode", "")
        suggested_name = ctx.intent_data.get("formName", "")

        ai_messages = [{"role": msg.role, "content": msg.content} for msg in ctx.request.messages]

        loop = asyncio.get_event_loop()
        ai_result = await loop.run_in_executor(None, lambda: AdminService.chat(ai_messages))

        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time

        if ai_result.get("success") and ai_result.get("hasConfig"):
            config_data = ai_result.get("config", {})
            validation_errors = ai_result.get("validationErrors", [])
            reply_text = ai_result.get("reply", "")
            config_reasoning = ai_result.get("reasoning")
            field_count = sum(len(e.get("fields", [])) for e in config_data.get("entities", []))

            # ═══ Phase 1：识别 ══════════════════════════════════════
            yield thinking(f"🛠️ 识别到新业务配置请求: {suggested_name or suggested_code}", result={
                "suggestedCode": suggested_code,
                "suggestedName": suggested_name
            })

            # ═══ Phase 2：执行 ══════════════════════════════════════
            # 生成场景关键词
            keywords_result = await loop.run_in_executor(
                None,
                lambda: AdminService.generate_scene_keywords(
                    config_data.get("formCode", ""),
                    config_data.get("formName", ""),
                    config_data.get("description", "")
                )
            )
            auto_keywords = []
            if keywords_result.get("success"):
                auto_keywords = keywords_result.get("keywords", [])
                if keywords_result.get("reasoning"):
                    yield reasoning(keywords_result.get("reasoning"))

            if config_reasoning:
                yield reasoning(config_reasoning)

            yield thinking(
                f"✅ 配置生成完成: {config_data.get('formName', '')} ({field_count} 个字段)",
                result={
                    "formName": config_data.get("formName", ""),
                    "formCode": config_data.get("formCode", ""),
                    "fieldCount": field_count,
                    "entityCount": len(config_data.get("entities", [])),
                    "validationErrors": validation_errors,
                    "keywordCount": len(auto_keywords),
                    "keywords": auto_keywords
                }
            )

            # ═══ Phase 3：输出 ══════════════════════════════════════
            desc = f"已为您生成 **{config_data.get('formName', '')}** 表单配置，包含 {field_count} 个字段。"
            yield sse({"type": "text_start"})
            for i in range(0, len(desc), 3):
                yield sse({"type": "text", "content": desc[i:i+3]})
                await asyncio.sleep(0.02)
            if reply_text:
                for i in range(0, len(reply_text), 3):
                    yield sse({"type": "text", "content": reply_text[i:i+3]})
                    await asyncio.sleep(0.02)
            yield sse({"type": "text_end"})

            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield intent_event("configure", "generate", {
                "config": config_data,
                "keywords": auto_keywords,
                "validationErrors": validation_errors,
                "reply": reply_text
            }, is_form=False)
            yield done_event("configure", is_form=False, intent_data=ctx.intent_data)

        elif ai_result.get("success"):
            # AI 引导用户补充需求
            reply_text = ai_result.get("reply", "请描述你想创建的表单类型。")
            config_reasoning = ai_result.get("reasoning")

            # ═══ Phase 1：识别（引导模式） ══════════════════════════
            yield thinking("💬 正在引导您描述需求...", result={
                "mode": "guide",
                "reply": reply_text
            })

            if config_reasoning:
                yield reasoning(config_reasoning)

            # ═══ Phase 3：输出 ══════════════════════════════════════
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield sse({"type": "text_start"})
            for i in range(0, len(reply_text), 3):
                yield sse({"type": "text", "content": reply_text[i:i+3]})
                await asyncio.sleep(0)
            yield sse({"type": "text_end"})
            yield done_event("configure", is_form=False, intent_data=ctx.intent_data)

        else:
            error_msg = ai_result.get("reply", "配置生成失败")

            # ── 输出错误 ────────────────────────────────────────────
            yield thinking(f"❌ 配置生成失败: {error_msg}", result={
                "success": False,
                "error": error_msg
            })

            ctx.stream_stats.error = error_msg
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield sse({"type": "error", "content": error_msg})
            yield done_event("configure", is_form=False, intent_data=ctx.intent_data)

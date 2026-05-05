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
        suggested_code = ctx.intent_data.get("formCode", "")
        suggested_name = ctx.intent_data.get("formName", "")
        yield thinking(f"🛠️ 识别到新业务配置请求: {suggested_name or suggested_code}")
        yield thinking("📋 正在通过 AI 生成表单配置...")

        # 构建 AdminService 对话
        ai_messages = []
        for msg in ctx.request.messages:
            ai_messages.append({
                "role": msg.role,
                "content": msg.content
            })

        # 调用 AdminService.chat 生成配置
        loop = asyncio.get_event_loop()
        ai_result = await loop.run_in_executor(
            None,
            lambda: AdminService.chat(ai_messages)
        )

        if ai_result.get("success") and ai_result.get("hasConfig"):
            config_data = ai_result.get("config", {})
            validation_errors = ai_result.get("validationErrors", [])
            reply_text = ai_result.get("reply", "")
            config_reasoning = ai_result.get("reasoning")

            yield thinking(f"✅ 配置生成完成: {config_data.get('formName', '')} ({config_data.get('formCode', '')})")

            # 发送配置生成的模型推理过程
            if config_reasoning:
                yield reasoning(config_reasoning)

            # 流式输出配置描述文字
            desc = f"已为您生成 **{config_data.get('formName', '')}** 表单配置，包含 {sum(len(e.get('fields', [])) for e in config_data.get('entities', []))} 个字段。"
            yield sse({"type": "text_start"})
            for i in range(0, len(desc), 3):
                yield sse({"type": "text", "content": desc[i:i+3]})
                await asyncio.sleep(0.02)
            if reply_text:
                for i in range(0, len(reply_text), 3):
                    yield sse({"type": "text", "content": reply_text[i:i+3]})
                    await asyncio.sleep(0.02)
            yield sse({"type": "text_end"})

            if validation_errors:
                yield thinking(f"⚠️ 配置校验问题: {'; '.join(validation_errors)}")

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
            keywords_reasoning = None
            if keywords_result.get("success"):
                auto_keywords = keywords_result.get("keywords", [])
                keywords_reasoning = keywords_result.get("reasoning")
                yield thinking(f"🔑 自动生成 {len(auto_keywords)} 个场景关键词")
                if keywords_reasoning:
                    yield reasoning(keywords_reasoning)

            # 发送 config 事件（携带完整配置 + 关键词）
            config_payload = {
                "config": config_data,
                "keywords": auto_keywords,
                "validationErrors": validation_errors,
                "reply": reply_text
            }

            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

            yield intent_event("configure", "generate", config_payload, is_form=False)
            yield done_event("configure", is_form=False, intent_data=ctx.intent_data)

        elif ai_result.get("success"):
            # AI 回复了但没有生成配置（纯对话）
            reply_text = ai_result.get("reply", "请描述你想创建的表单类型。")
            config_reasoning = ai_result.get("reasoning")
            yield thinking("💬 AI 正在引导用户描述需求...")

            if config_reasoning:
                yield reasoning(config_reasoning)

            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

            yield sse({"type": "text_start"})
            chunk_size = 3
            for i in range(0, len(reply_text), chunk_size):
                chunk = reply_text[i:i + chunk_size]
                yield sse({"type": "text", "content": chunk})
                await asyncio.sleep(0)
            yield sse({"type": "text_end"})
            yield done_event("configure", is_form=False, intent_data=ctx.intent_data)

        else:
            error_msg = ai_result.get("reply", "配置生成失败")
            yield thinking(f"❌ 配置生成失败: {error_msg}")

            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            ctx.stream_stats.error = error_msg
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield sse({"type": "error", "content": error_msg})

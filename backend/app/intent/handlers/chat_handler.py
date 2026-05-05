# ChatHandler - 纯聊天意图处理器
# 对应 chat.py 第1216-1238行

import time
from typing import AsyncGenerator, Optional

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, stream_chat_reply, done_event
from ...services.llm_service import StreamStats


class ChatHandler(BaseIntentHandler):
    """纯聊天意图 —— LLM 流式输出回复"""

    intent_type = "chat"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        yield thinking("💬 正在生成回复...")
        final_llm_stats: Optional[StreamStats] = None

        async for chunk, stats in stream_chat_reply(
            ctx.intent_prompt,
            ctx.ontologies_info,
            ctx.messages_text
        ):
            if stats is not None:
                final_llm_stats = stats
                continue
            yield chunk

        # 更新统计信息
        if final_llm_stats:
            ctx.stream_stats.llm_elapsed = final_llm_stats.elapsed
            ctx.stream_stats.llm_tokens = final_llm_stats.token_count
            ctx.stream_stats.llm_chars = final_llm_stats.char_count
            ctx.stream_stats.llm_tps = final_llm_stats.tokens_per_second

        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = False
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
        yield done_event("chat", is_form=False)

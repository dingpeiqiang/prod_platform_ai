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
        """处理步骤规范：

        ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
        ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑（LLM 流式回复）
        ═══ Phase 3：输出 (Output)       —— SSE 事件输出
        """
        # ═══ Phase 1：识别 ══════════════════════════════════════════
        
        # 如果有错误信息，生成友好的错误回复
        if ctx.error_info:
            friendly_response = self._generate_friendly_error_response(ctx.error_info)
            yield friendly_response
            # ═══ Phase 3：输出 ══════════════════════════════════════════
            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            ctx.stream_stats.is_form = False
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield done_event("chat", is_form=False)
            return
        
        yield thinking("💬 正在生成回复...")

        # ═══ Phase 2：执行 ══════════════════════════════════════════
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

        # ═══ Phase 3：输出 ══════════════════════════════════════════
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        ctx.stream_stats.is_form = False
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
        yield done_event("chat", is_form=False)
    
    def _generate_friendly_error_response(self, error_info: str) -> str:
        """
        根据错误信息生成友好的用户回复
        """
        # 检测错误类型并生成相应的友好回复
        if "由于目标计算机积极拒绝" in error_info or "connection refused" in error_info.lower():
            friendly_msg = "😔 抱歉，当前服务暂时无法连接，请稍后重试。如果问题持续存在，请联系管理员。"
        elif "Max retries exceeded" in error_info:
            friendly_msg = "😔 服务请求超时，请稍后再试。"
        elif "Failed to establish a new connection" in error_info:
            friendly_msg = "😔 无法建立连接，请检查网络或稍后重试。"
        else:
            # 通用错误回复
            friendly_msg = f"😔 处理过程中遇到问题：\n{error_info}\n\n请稍后重试，或联系管理员获取帮助。"
        
        return sse({"type": "message", "content": friendly_msg})

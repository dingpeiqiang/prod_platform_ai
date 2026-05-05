"""
OpenAI Compatible Provider

流式调用
"""
import json
import logging
import time
import requests
from typing import Optional, Tuple, AsyncGenerator

from .base import StreamStats, StreamBuffer

logger = logging.getLogger("llm.openai")
_SENTINEL = object()


def _build_messages(prompt, system_prompt):
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    return messages


async def call_stream(client, model, prompt, system_prompt=None, llm_config=None,
                      use_buffer=True) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
    """OpenAI 兼容流式调用"""
    import asyncio

    messages = _build_messages(prompt, system_prompt)
    buffer_size = (llm_config.get('bufferSize', 20) if llm_config else 20)
    flush_interval = (llm_config.get('flushInterval', 0.05) if llm_config else 0.05)

    stats = StreamStats(start_time=time.time())

    try:
        loop = asyncio.get_event_loop()

        def _do_stream():
            return client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=(llm_config.get('temperature', 0.3) if llm_config else 0.3),
                max_tokens=(llm_config.get('maxTokens', 2048) if llm_config else 2048),
                stream=True
            )

        stream = await loop.run_in_executor(None, _do_stream)
        buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

        for chunk in stream:
            delta = chunk.choices[0].delta.content if chunk.choices else None
            if delta:
                stats.token_count += 1
                stats.char_count += len(delta)
                if use_buffer and buffer:
                    flushed = buffer.add(delta)
                    if flushed:
                        stats.chunk_count += 1
                        yield flushed, None
                else:
                    yield delta, None
            await asyncio.sleep(0)

        if buffer:
            remaining = buffer.flush()
            if remaining:
                yield remaining, None

        stats.end_time = time.time()
        yield "", stats

    except Exception as e:
        stats.end_time = time.time()
        stats.error_count += 1
        logger.exception("[openai-stream] error: %s", e)
        yield "", stats


def call_sync(client, model, prompt, system_prompt=None, llm_config=None, max_tokens=None) -> Optional[str]:
    """OpenAI 兼容同步调用"""
    messages = _build_messages(prompt, system_prompt)
    try:
        response = client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=(llm_config.get('temperature', 0.3) if llm_config else 0.3),
            max_tokens=(max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048))
        )
        return response.choices[0].message.content
    except Exception as e:
        logger.error("[openai] sync call failed: %s", e)
        return None
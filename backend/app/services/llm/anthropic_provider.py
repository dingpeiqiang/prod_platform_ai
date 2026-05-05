"""
Anthropic Claude Provider

流式调用
"""
import json
import logging
import time
import requests
from typing import Optional, Tuple, AsyncGenerator

from .base import StreamStats, StreamBuffer

logger = logging.getLogger("llm.anthropic")
_SENTINEL = object()


def _build_messages(prompt, system_prompt):
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    return messages


async def call_stream(base_url, api_key, model, prompt, system_prompt=None, llm_config=None,
                      use_buffer=True) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
    """Anthropic Claude 流式调用"""
    import asyncio

    buffer_size = (llm_config.get('bufferSize', 20) if llm_config else 20)
    flush_interval = (llm_config.get('flushInterval', 0.05) if llm_config else 0.05)

    url = f"{base_url}/v1/messages"
    headers = {
        "Content-Type": "application/json",
        "anthropic-version": "2023-06-01",
        "x-api-key": api_key
    }
    body = {
        "model": model,
        "max_tokens": (llm_config.get('maxTokens', 2048) if llm_config else 2048),
        "messages": [{"role": "user", "content": prompt}]
    }

    stats = StreamStats(start_time=time.time())
    loop = asyncio.get_event_loop()
    token_queue = asyncio.Queue()

    def _stream_worker():
        try:
            resp = requests.post(url, json=body, headers=headers, stream=True, timeout=180)
            if resp.status_code != 200:
                loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)
                return
            for raw_line in resp.iter_lines():
                if not raw_line:
                    continue
                try:
                    line_text = raw_line.decode('utf-8')
                    if not line_text.startswith('data:'):
                        continue
                    chunk = json.loads(line_text[5:].strip())
                    event_type = chunk.get('type', '')
                    if event_type == 'content_block_delta':
                        delta = chunk.get('delta', {})
                        if delta.get('type') == 'text_delta':
                            text = delta.get('text', '')
                            if text:
                                loop.call_soon_threadsafe(token_queue.put_nowait, text)
                    elif event_type == 'message_delta':
                        pass
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
        except Exception as e:
            logger.exception("[anthropic-stream] worker error: %s", e)
            stats.error_count += 1
        finally:
            loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)

    try:
        loop.run_in_executor(None, _stream_worker)
        buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

        while True:
            item = await token_queue.get()
            if item is _SENTINEL:
                if buffer:
                    remaining = buffer.flush()
                    if remaining:
                        yield remaining, None
                break

            stats.token_count += 1
            stats.char_count += len(item)
            if use_buffer and buffer:
                flushed = buffer.add(item)
                if flushed:
                    stats.chunk_count += 1
                    yield flushed, None
            else:
                yield item, None

        stats.end_time = time.time()
        yield "", stats

    except Exception as e:
        stats.end_time = time.time()
        stats.error_count += 1
        logger.exception("[anthropic-stream] async error: %s", e)
        yield "", stats
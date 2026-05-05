"""
Ollama Provider

同步调用 + 流式调用
"""
import json
import logging
import time
import requests
from typing import Optional, Tuple, AsyncGenerator

from .base import StreamStats, StreamBuffer, extract_json

logger = logging.getLogger("llm.ollama")
_SENTINEL = object()


def _build_messages(prompt, system_prompt):
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    return messages


def call_sync(base_url, model, prompt, system_prompt=None, llm_config=None, max_tokens=None,
              thinking_enabled=False) -> Optional[str]:
    """同步调用 Ollama（非流式）"""
    messages = _build_messages(prompt, system_prompt)
    url = f"{base_url}/api/chat"
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    payload = {
        "model": model, "messages": messages, "stream": False,
        "thinking": thinking_enabled,
        "options": {
            "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
            "num_predict": effective_max_tokens
        }
    }
    try:
        response = requests.post(url, json=payload, timeout=180)
        if response.status_code != 200:
            return None
        result = response.json()
        message = result.get('message', {})
        content = message.get('content', '')
        thinking = message.get('thinking')
        if not content.strip() and thinking and thinking.strip():
            extracted = extract_json(thinking)
            if extracted:
                return json.dumps(extracted, ensure_ascii=False)
            return None
        return content or None
    except Exception as e:
        logger.error("[ollama] sync call failed: %s", e)
        return None


async def call_stream(base_url, model, prompt, system_prompt=None, llm_config=None,
                      use_buffer=True) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
    """Ollama 流式调用"""
    import asyncio

    messages = _build_messages(prompt, system_prompt)
    url = f"{base_url}/api/chat"
    thinking_enabled = (llm_config.get('thinking', False) if llm_config else False)
    buffer_size = (llm_config.get('bufferSize', 20) if llm_config else 20)
    flush_interval = (llm_config.get('flushInterval', 0.05) if llm_config else 0.05)

    payload = {
        "model": model, "messages": messages, "stream": True,
        "thinking": thinking_enabled,
        "options": {
            "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
            "num_predict": (llm_config.get('maxTokens', 2048) if llm_config else 2048)
        }
    }

    stats = StreamStats(start_time=time.time())
    thinking_buffer = []

    loop = asyncio.get_event_loop()
    token_queue = asyncio.Queue()

    def _stream_worker():
        try:
            resp = requests.post(url, json=payload, stream=True, timeout=180)
            if resp.status_code != 200:
                loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)
                return
            for raw_line in resp.iter_lines():
                if not raw_line:
                    continue
                try:
                    chunk = json.loads(raw_line)
                except json.JSONDecodeError:
                    continue
                thinking = chunk.get('thinking')
                if thinking:
                    thinking_buffer.append(thinking)
                    loop.call_soon_threadsafe(
                        token_queue.put_nowait,
                        f"[THINKING]{thinking}[/THINKING]"
                    )
                token = chunk.get('message', {}).get('content', '')
                if token:
                    loop.call_soon_threadsafe(token_queue.put_nowait, token)
                if chunk.get('done', False):
                    break
        except Exception as e:
            logger.exception("[ollama-stream] stream worker error: %s", e)
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
            if item.startswith("[THINKING]") and item.endswith("[/THINKING]"):
                stats.thinking_chars += len(item) - 21
                yield item, None
                continue

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
        logger.exception("[ollama-stream] async error: %s", e)
        yield "", stats


def call_with_reasoning(base_url, model, prompt, system_prompt=None, llm_config=None,
                        max_tokens=None) -> Tuple[Optional[str], Optional[str]]:
    """Ollama 同步调用，同时提取 thinking"""
    messages = _build_messages(prompt, system_prompt)
    url = f"{base_url}/api/chat"
    thinking_enabled = (llm_config.get('thinking', False) if llm_config else False)
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    payload = {
        "model": model, "messages": messages, "stream": False,
        "thinking": thinking_enabled,
        "options": {
            "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
            "num_predict": effective_max_tokens
        }
    }
    try:
        response = requests.post(url, json=payload, timeout=180)
        if response.status_code != 200:
            return None, None
        result = response.json()
        message = result.get('message', {})
        content = message.get('content', '')
        thinking = message.get('thinking')
        if not content.strip() and thinking and thinking.strip():
            extracted = extract_json(thinking)
            if extracted:
                return json.dumps(extracted, ensure_ascii=False), thinking
            return None, thinking
        return content, thinking or None
    except Exception as e:
        logger.error("[ollama] call_with_reasoning failed: %s", e)
        return None, None
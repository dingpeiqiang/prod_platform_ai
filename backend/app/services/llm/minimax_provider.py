"""
MiniMax / Custom API Provider

同步调用 + 流式同步调用 + 带 reasoning 的同步调用
"""
import asyncio
import json
import logging
import time
import uuid
import requests
from typing import Dict, Optional, Tuple, AsyncGenerator

from .base import StreamStats, StreamBuffer
from app.core.config import get_settings

from .base import StreamStats, StreamBuffer, extract_json
from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger("llm.minimax")

_SENTINEL = object()

# MiniMax 模型上下文限制
_MODEL_CONTEXT_LIMIT = 196608
# 保留余量给输出
_DEFAULT_MAX_INPUT_TOKENS = 180000


def _estimate_tokens(text: str) -> int:
    """估算文本token数（中英文混合：≈字符数/2）"""
    if not text:
        return 0
    return len(text) // 2


def _truncate_messages(messages: list, max_input_tokens: int = _DEFAULT_MAX_INPUT_TOKENS) -> list:
    """截断消息以满足输入token限制"""
    total_tokens = sum(_estimate_tokens(m.get('content', '')) for m in messages)
    if total_tokens <= max_input_tokens:
        return messages

    # 计算系统消息token（通常需要保留）
    system_tokens = 0
    system_msg = None
    other_messages = []
    for m in messages:
        if m.get('role') == 'system':
            system_msg = m
            system_tokens = _estimate_tokens(m.get('content', ''))
        else:
            other_messages.append(m)

    # 可用的token额度
    available = max_input_tokens - system_tokens - _estimate_tokens(messages[-1].get('content', '')) if messages else max_input_tokens
    if available < 0:
        # 系统消息太大，截断它
        if system_msg:
            max_chars = (max_input_tokens - 1000) * 2  # 留1K给用户消息
            if max_chars > 0:
                system_msg = {"role": "system", "content": system_msg['content'][:max_chars] + "\n[内容已截断]"}
            else:
                system_msg = {"role": "system", "content": "你是一个AI助手。"}
        available = max_input_tokens - _estimate_tokens(messages[-1].get('content', '')) if messages else max_input_tokens

    # 截断最后一条用户消息
    if other_messages and available > 0:
        last_msg = other_messages[-1]
        max_chars = available * 2
        content = last_msg.get('content', '')
        if _estimate_tokens(content) > available:
            last_msg = {**last_msg, "content": content[:max_chars] + "\n[输入过长已截断]"}
            other_messages[-1] = last_msg

    result = []
    if system_msg:
        result.append(system_msg)
    result.extend(other_messages)
    return result


def _build_messages(prompt, system_prompt):
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    return messages


def call_sync(base_url, model, api_key, prompt, system_prompt=None, max_tokens=None,
              llm_config=None, thinking_enabled=False) -> Optional[str]:
    """同步调用（非流式）"""
    if not base_url or not api_key:
        return None
    messages = _build_messages(prompt, system_prompt)
    max_input = llm_config.get('maxInputTokens', _DEFAULT_MAX_INPUT_TOKENS) if llm_config else _DEFAULT_MAX_INPUT_TOKENS
    messages = _truncate_messages(messages, max_input)
    url = f"{base_url}/chat/completions"
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    headers = {"Content-Type": "application/json", "token": api_key}
    payload = {
        "stream": False, "messages": messages, "model": model,
        "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
        "max_tokens": effective_max_tokens
    }
    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=180)
        if resp.status_code != 200:
            return None
        result = resp.json()
        content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
        reasoning = result.get('choices', [{}])[0].get('message', {}).get('reasoning', '') or ''
        if not content.strip() and reasoning.strip():
            extracted = extract_json(reasoning)
            if extracted:
                return json.dumps(extracted, ensure_ascii=False)
            return None
        return content or None
    except Exception as e:
        logger.error("[minimax] sync call failed: %s", e)
        return None


def call_stream_sync(base_url, model, api_key, prompt, system_prompt=None, max_tokens=None,
                     llm_config=None) -> Optional[str]:
    """同步流式调用，收集全部内容"""
    if not base_url or not api_key:
        return None
    messages = _build_messages(prompt, system_prompt)
    max_input = llm_config.get('maxInputTokens', _DEFAULT_MAX_INPUT_TOKENS) if llm_config else _DEFAULT_MAX_INPUT_TOKENS
    messages = _truncate_messages(messages, max_input)
    url = f"{base_url}/chat/completions"
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    headers = {"Content-Type": "application/json", "token": api_key}
    payload = {
        "stream": True, "messages": messages, "model": model,
        "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
        "max_tokens": effective_max_tokens
    }
    try:
        resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=300)
        if resp.status_code != 200:
            return None
        content_parts, reasoning_parts = [], []
        for raw_line in resp.iter_lines():
            if not raw_line:
                continue
            try:
                line_text = raw_line.decode('utf-8')
                if not line_text.startswith('data:'):
                    continue
                data_str = line_text[5:].strip()
                if data_str == '[DONE]':
                    break
                chunk = json.loads(data_str)
                delta = chunk.get('choices', [{}])[0].get('delta', {})
                reasoning = delta.get('reasoning_content', '') or delta.get('reasoning', '')
                if reasoning:
                    reasoning_parts.append(reasoning)
                content = delta.get('content', '')
                if content:
                    content_parts.append(content)
            except (json.JSONDecodeError, UnicodeDecodeError):
                continue
        result = ''.join(content_parts)
        reasoning_text = ''.join(reasoning_parts)
        if not result.strip() and reasoning_text.strip():
            extracted = extract_json(reasoning_text)
            if extracted:
                return json.dumps(extracted, ensure_ascii=False)
            return None
        return result or None
    except Exception as e:
        logger.error("[minimax] stream sync call failed: %s", e)
        return None


def call_with_reasoning(base_url, model, api_key, prompt, system_prompt=None, max_tokens=None,
                        llm_config=None) -> Tuple[Optional[str], Optional[str]]:
    """同步调用，同时返回 (content, reasoning)"""
    if not base_url or not api_key:
        return None, None
    messages = _build_messages(prompt, system_prompt)
    max_input = llm_config.get('maxInputTokens', _DEFAULT_MAX_INPUT_TOKENS) if llm_config else _DEFAULT_MAX_INPUT_TOKENS
    messages = _truncate_messages(messages, max_input)
    url = f"{base_url}/chat/completions"
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    headers = {"Content-Type": "application/json", "token": api_key}
    payload = {
        "stream": False, "messages": messages, "model": model,
        "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
        "max_tokens": effective_max_tokens
    }
    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=180)
        if resp.status_code != 200:
            return None, None
        result = resp.json()
        content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
        reasoning = (
            result.get('choices', [{}])[0].get('message', {}).get('reasoning', '') or
            result.get('choices', [{}])[0].get('message', {}).get('reasoning_content', '')
        )
        if not content or not content.strip():
            if reasoning and reasoning.strip():
                extracted = extract_json(reasoning)
                if extracted:
                    return json.dumps(extracted, ensure_ascii=False), reasoning
                return None, reasoning
            return None, None
        return content, reasoning or None
    except Exception as e:
        logger.error("[minimax] call_with_reasoning failed: %s", e)
        return None, None


async def call_stream(base_url, model, api_key, prompt, system_prompt=None, max_tokens=None,
                      llm_config=None) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
    """MiniMax/Custom API 异步流式调用"""
    messages = _build_messages(prompt, system_prompt)
    max_input = llm_config.get('maxInputTokens', _DEFAULT_MAX_INPUT_TOKENS) if llm_config else _DEFAULT_MAX_INPUT_TOKENS
    messages = _truncate_messages(messages, max_input)
    url = f"{base_url}/chat/completions"
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    headers = {"Content-Type": "application/json", "token": api_key}
    payload = {
        "stream": True, "messages": messages, "model": model,
        "temperature": (llm_config.get('temperature', 0.3) if llm_config else 0.3),
        "max_tokens": effective_max_tokens
    }

    stats = StreamStats(start_time=time.time())
    thinking_buffer = []
    loop = asyncio.get_event_loop()
    token_queue = asyncio.Queue()

    def _worker():
        try:
            resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=300)
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
                    data_str = line_text[5:].strip()
                    if data_str == '[DONE]':
                        break
                    chunk = json.loads(data_str)
                    delta = chunk.get('choices', [{}])[0].get('delta', {})
                    reasoning = delta.get('reasoning_content', '') or delta.get('reasoning', '')
                    if reasoning:
                        thinking_buffer.append(reasoning)
                        loop.call_soon_threadsafe(
                            token_queue.put_nowait, f"[THINKING]{reasoning}[/THINKING]"
                        )
                    content = delta.get('content', '')
                    if content:
                        loop.call_soon_threadsafe(token_queue.put_nowait, content)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
        except Exception as e:
            logger.error("[minimax-stream] worker error: %s", e)
            stats.error_count += 1
        finally:
            loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)

    try:
        loop.run_in_executor(None, _worker)
        buffer_size = (llm_config.get('bufferSize', 20) if llm_config else 20)
        flush_interval = (llm_config.get('flushInterval', 0.05) if llm_config else 0.05)
        buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval)

        while True:
            item = await token_queue.get()
            if item is _SENTINEL:
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
            flushed = buffer.add(item)
            if flushed:
                stats.chunk_count += 1
                yield flushed, None

        stats.end_time = time.time()
        yield "", stats
    except Exception as e:
        stats.end_time = time.time()
        stats.error_count += 1
        logger.exception("[minimax-stream] async error: %s", e)
        yield "", stats
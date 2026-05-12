"""
WebSocket API Provider
支持 wss:// 协议的 LLM API
"""
import asyncio
import json
import logging
import time
import uuid
from typing import Dict, Optional, Tuple, AsyncGenerator
import websockets

from .base import StreamStats, StreamBuffer, extract_json
from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger("llm.websocket")

_SENTINEL = object()

_DEFAULT_MAX_INPUT_TOKENS = 180000


def _estimate_tokens(text: str) -> int:
    if not text:
        return 0
    return len(text) // 2


def _truncate_messages(messages: list, max_input_tokens: int = _DEFAULT_MAX_INPUT_TOKENS) -> list:
    total_tokens = sum(_estimate_tokens(m.get('content', '')) for m in messages)
    if total_tokens <= max_input_tokens:
        return messages

    system_tokens = 0
    system_msg = None
    other_messages = []
    for m in messages:
        if m.get('role') == 'system':
            system_msg = m
            system_tokens = _estimate_tokens(m.get('content', ''))
        else:
            other_messages.append(m)

    available = max_input_tokens - system_tokens - (_estimate_tokens(messages[-1].get('content', '')) if messages else 0)
    if available < 0:
        if system_msg:
            max_chars = (max_input_tokens - 1000) * 2
            if max_chars > 0:
                system_msg = {"role": "system", "content": system_msg['content'][:max_chars] + "\n[内容已截断]"}
            else:
                system_msg = {"role": "system", "content": "你是一个AI助手。"}
        available = max_input_tokens - (_estimate_tokens(messages[-1].get('content', '')) if messages else 0)

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


async def call_stream(base_url: str, model: str, api_key: str, prompt: str, 
                      system_prompt=None, max_tokens=None, llm_config=None) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
    """WebSocket 异步流式调用"""
    messages = _build_messages(prompt, system_prompt)
    max_input = llm_config.get('maxInputTokens', _DEFAULT_MAX_INPUT_TOKENS) if llm_config else _DEFAULT_MAX_INPUT_TOKENS
    messages = _truncate_messages(messages, max_input)
    effective_max_tokens = max_tokens or (llm_config.get('maxTokens', 2048) if llm_config else 2048)
    
    payload = {
        "top_p": llm_config.get('topP', 0.95) if llm_config else 0.95,
        "top_k": llm_config.get('topK', 10) if llm_config else 10,
        "temperature": llm_config.get('temperature', 0.5) if llm_config else 0.5,
        "history": messages[:-1],
        "prompt": messages[-1]['content'],
        "chatWindowId": 6,
        "user_id": 123456,
        "conversation_id": f"user_id+{str(uuid.uuid4())[:8]}",
        "chatMessageId": int(time.time() * 1000),
        "chatSessionId": 9
    }

    logger.debug(f"[websocket] Connecting to: {base_url}")
    logger.debug(f"[websocket] Payload: {json.dumps(payload)[:500]}...")

    stats = StreamStats(start_time=time.time())
    
    try:
        async with websockets.connect(
            base_url,
            extra_headers={"token": api_key},
            ping_interval=30,
            ping_timeout=60,
            close_timeout=10
        ) as websocket:
            await websocket.send(json.dumps(payload))
            logger.debug("[websocket] Message sent")
            
            buffer_size = llm_config.get('bufferSize', 20) if llm_config else 20
            flush_interval = llm_config.get('flushInterval', 0.05) if llm_config else 0.05
            buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval)

            while True:
                try:
                    response = await asyncio.wait_for(websocket.recv(), timeout=300)
                    
                    if not response:
                        continue
                        
                    try:
                        data = json.loads(response)
                    except json.JSONDecodeError:
                        logger.debug(f"[websocket] Non-JSON response: {response[:100]}")
                        yield response, None
                        continue
                    
                    if 'error' in data:
                        error_msg = data.get('error', {}).get('message', 'Unknown error')
                        logger.error(f"[websocket] API error: {error_msg}")
                        raise Exception(f"API 调用失败: {error_msg}")
                    
                    content = data.get('text', '')
                    if content:
                        stats.char_count += len(content)
                        stats.token_count += 1
                        flushed = buffer.add(content)
                        if flushed:
                            stats.chunk_count += 1
                            yield flushed, None
                    
                    finish_reason = data.get('finish_reason')
                    if finish_reason:
                        remaining = buffer.flush()
                        if remaining:
                            yield remaining, None
                        break
                        
                except asyncio.TimeoutError:
                    logger.error("[websocket] Connection timeout")
                    raise Exception("WebSocket 连接超时")
                except websockets.exceptions.ConnectionClosed:
                    logger.error("[websocket] Connection closed")
                    break

        stats.end_time = time.time()
        yield "", stats
        
    except websockets.exceptions.InvalidStatusCode as e:
        logger.error(f"[websocket] Invalid status code: {e}")
        raise Exception(f"WebSocket 连接失败: {e}")
    except Exception as e:
        stats.end_time = time.time()
        stats.error_count += 1
        logger.exception(f"[websocket] Error: {e}")
        yield "", stats


def call_sync(base_url: str, model: str, api_key: str, prompt: str, 
              system_prompt=None, max_tokens=None, llm_config=None) -> Optional[str]:
    """WebSocket 同步调用"""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    
    try:
        result = []
        
        async def collect():
            nonlocal result
            async for chunk, _ in call_stream(base_url, model, api_key, prompt, 
                                             system_prompt, max_tokens, llm_config):
                if chunk:
                    result.append(chunk)
        
        loop.run_until_complete(collect())
        return ''.join(result) if result else None
    finally:
        loop.close()
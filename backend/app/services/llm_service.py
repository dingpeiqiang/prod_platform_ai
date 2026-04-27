import json
import logging
import time
import requests
from typing import Dict, Any, Optional, List, AsyncGenerator, Tuple
from dataclasses import dataclass
from app.core.config import get_settings
from app.core.config_loader import config_loader
from app.harness.observability.llm_call_logger import (
    get_llm_call_logger,
    CallType,
    log_llm_call_start,
    log_llm_call_complete,
    log_llm_call_fail
)

settings = get_settings()

# 结构化日志：只输出关键信息，不打印全量 prompt
logger = logging.getLogger("llm_service")

# 获取LLM调用日志记录器
llm_call_logger = get_llm_call_logger()


@dataclass
class StreamStats:
    """流式输出统计信息"""
    start_time: float = 0.0
    end_time: float = 0.0
    token_count: int = 0
    char_count: int = 0
    chunk_count: int = 0
    thinking_chars: int = 0
    error_count: int = 0
    
    @property
    def elapsed(self) -> float:
        return self.end_time - self.start_time if self.end_time > 0 else time.time() - self.start_time
    
    @property
    def tokens_per_second(self) -> float:
        return self.token_count / self.elapsed if self.elapsed > 0 else 0.0
    
    @property
    def chars_per_second(self) -> float:
        return self.char_count / self.elapsed if self.elapsed > 0 else 0.0


class StreamBuffer:
    """流式输出缓冲区 - 批量发送 token 以减少开销"""
    
    def __init__(self, buffer_size: int = 10, flush_interval: float = 0.05):
        """
        初始化缓冲区
        
        Args:
            buffer_size: 缓冲区大小（字符数）
            flush_interval: 强制刷新间隔（秒）
        """
        self.buffer: List[str] = []
        self.buffer_size = buffer_size
        self.flush_interval = flush_interval
        self.last_flush_time = time.time()
        self.total_chars = 0
        self.total_chunks = 0
    
    def add(self, text: str) -> Optional[str]:
        """
        添加文本到缓冲区
        
        Returns:
            如果需要刷新，返回缓冲区内容；否则返回 None
        """
        if not text:
            return None
        
        self.buffer.append(text)
        self.total_chars += len(text)
        
        # 检查是否需要刷新
        current_size = sum(len(t) for t in self.buffer)
        current_time = time.time()
        
        if current_size >= self.buffer_size or (current_time - self.last_flush_time) >= self.flush_interval:
            return self.flush()
        
        return None
    
    def flush(self) -> Optional[str]:
        """
        强制刷新缓冲区
        
        Returns:
            缓冲区内容，如果为空返回 None
        """
        if not self.buffer:
            return None
        
        result = ''.join(self.buffer)
        self.buffer = []
        self.last_flush_time = time.time()
        self.total_chunks += 1
        return result
    
    def __len__(self) -> int:
        return sum(len(t) for t in self.buffer)


def _truncate(text: str, max_len: int = 200) -> str:
    """截断长文本用于日志展示"""
    if len(text) <= max_len:
        return text
    return text[:max_len] + f"...[共{len(text)}字符]"


class LLMService:
    _instance = None
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance
    
    def __init__(self):
        logger.info("=" * 60)
        logger.info("LLM Service 初始化中...")
        self.app_config = config_loader.get_app_config()
        self.llm_config = self.app_config.get('llm', {})
        self.enabled = self.llm_config.get('enabled', False)
        self.fallback_to_rules = self.llm_config.get('fallbackToRules', True)
        self._client = None
        
        logger.info(f"LLM 配置加载: enabled={self.enabled}, provider={self.llm_config.get('provider')}, fallbackToRules={self.fallback_to_rules}")
        
        if self.enabled:
            logger.info(f"  - 模型: {self.llm_config.get('model')}")
            logger.info(f"  - 基础URL: {self.llm_config.get('baseUrl')}")
            logger.info(f"  - 温度: {self.llm_config.get('temperature')}")
            logger.info(f"  - 最大token: {self.llm_config.get('maxTokens')}")
            logger.info(f"  - 缓冲区大小: {self.llm_config.get('bufferSize')}")
        
        self._init_client()
        logger.info("LLM Service 初始化完成")
        logger.info("=" * 60)
    
    def _get_base_url(self) -> str:
        base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else None
        return base_url or self.llm_config.get('baseUrl', 'http://localhost:11434')

    def _get_model(self) -> str:
        model = settings.LLM_MODEL.strip() if settings.LLM_MODEL else None
        return model or self.llm_config.get('model', 'qwen2.5:14b-instruct-q4_K_M')

    def _build_messages(self, prompt: str, system_prompt: Optional[str]) -> List[Dict]:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        return messages

    def _init_client(self):
        if not self.enabled:
            return
        
        try:
            provider = self.llm_config.get('provider', 'openai')
            if provider == 'ollama':
                self._client = None  # Ollama 使用 requests 直接调用
                logger.info("Ollama client initialized (base_url=%s, model=%s)",
                            self._get_base_url(), self._get_model())
            elif provider == 'custom':
                self._client = None  # Custom 使用 requests 直接调用
                logger.info("Custom API client initialized (base_url=%s, model=%s)",
                            self._get_base_url(), self._get_model())
            else:
                import openai
                self._client = openai.OpenAI(
                    api_key=settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                    base_url=settings.LLM_BASE_URL or self.llm_config.get('baseUrl')
                )
                logger.info("OpenAI-compatible client initialized for provider: %s", provider)
        except ImportError:
            logger.warning("OpenAI not installed, LLM service disabled")
            self.enabled = False
        except Exception as e:
            logger.error("LLM client init failed: %s, falling back to rules", e)
            if not self.fallback_to_rules:
                self.enabled = False

    # ------------------------------------------------------------------ #
    #  Ollama 同步调用（非流式，用于意图识别等结构化输出场景）             #
    # ------------------------------------------------------------------ #
    def _call_ollama(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        """调用 Ollama API（非流式）

        Returns:
            None if failed, otherwise the response content string.
            Thinking content is logged at INFO level when present.
        """
        if not self.enabled:
            return None

        try:
            base_url = self._get_base_url()
            model = self._get_model()
            messages = self._build_messages(prompt, system_prompt)
            url = f"{base_url}/api/chat"
            thinking_enabled = self.llm_config.get('thinking', False)
            payload = {
                "model": model,
                "messages": messages,
                "stream": False,
                "thinking": thinking_enabled,
                "options": {
                    "temperature": self.llm_config.get('temperature', 0.3),
                    "num_predict": self.llm_config.get('maxTokens', 2048)
                }
            }

            if thinking_enabled:
                logger.info("[Ollama] thinking 功能已启用 (model=%s)", model)

            logger.info("[Ollama] 请求 model=%s url=%s", model, url)
            logger.debug("[Ollama] prompt(前200字)=%s", _truncate(prompt, 200))
            t0 = time.time()
            response = requests.post(url, json=payload, timeout=180)

            if response.status_code != 200:
                logger.error("[Ollama] 响应错误 status=%s body=%s",
                             response.status_code, response.text[:300])
                return None

            result = response.json()
            message = result.get('message', {})
            content = message.get('content', '')
            thinking = message.get('thinking')
            is_done = result.get('done', False)
            done_reason = result.get('done_reason', '')
            elapsed = time.time() - t0

            if thinking:
                logger.info("[Ollama] 思考过程:\n%s", thinking)
                logger.info("[Ollama] 思考过程结束")

            logger.info("[Ollama] 响应 elapsed=%.2fs done=%s reason=%s content_len=%d",
                        elapsed, is_done, done_reason, len(content))

            if not is_done:
                logger.warning("[Ollama] 响应不完整! elapsed=%.2fs content=%s",
                              elapsed, _truncate(content, 300) if content else "(空)")
                return None

            if not content.strip():
                logger.warning("[Ollama] 响应为空! elapsed=%.2fs raw_response=%s",
                              elapsed, _truncate(json.dumps(result, ensure_ascii=False), 500))
                return None

            logger.info("[Ollama] 响应成功 elapsed=%.2fs chars=%d preview=%s",
                        elapsed, len(content), _truncate(content, 200))
            logger.debug("[Ollama] 完整响应=%s", content)
            return content

        except requests.exceptions.ConnectionError as e:
            logger.error("[Ollama] 连接失败: %s (请确认 ollama serve 已启动)", e)
            return None
        except Exception as e:
            logger.exception("[Ollama] 调用异常: %s", e)
            return None

    # ------------------------------------------------------------------ #
    #  Ollama 真正的流式调用                                              #
    # ------------------------------------------------------------------ #
    async def _call_ollama_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """
        调用 Ollama 流式 API，批量 yield 文本内容以减少开销。
        将 requests iter_lines 放入线程池，通过 asyncio.Queue 传回 token，
        彻底避免阻塞事件循环。

        Args:
            prompt: 提示词
            system_prompt: 系统提示词
            use_buffer: 是否使用缓冲区批量发送

        Yields:
            (文本内容, 统计信息) - 统计信息仅在结束时提供
        """
        if not self.enabled:
            return

        import asyncio

        base_url = self._get_base_url()
        model = self._get_model()
        messages = self._build_messages(prompt, system_prompt)
        url = f"{base_url}/api/chat"
        thinking_enabled = self.llm_config.get('thinking', False)
        buffer_size = self.llm_config.get('bufferSize', 20)
        flush_interval = self.llm_config.get('flushInterval', 0.05)
        
        payload = {
            "model": model,
            "messages": messages,
            "stream": True,
            "thinking": thinking_enabled,
            "options": {
                "temperature": self.llm_config.get('temperature', 0.3),
                "num_predict": self.llm_config.get('maxTokens', 2048)
            }
        }

        if thinking_enabled:
            logger.info("[Ollama-stream] thinking 功能已启用 (model=%s)", model)

        logger.info("[Ollama-stream] 开始请求 model=%s url=%s buffer=%d", 
                    model, url, buffer_size if use_buffer else 0)
        logger.debug("[Ollama-stream] prompt(前200字)=%s", _truncate(prompt, 200))
        
        stats = StreamStats(start_time=time.time())
        thinking_buffer: List[str] = []

        loop = asyncio.get_event_loop()
        token_queue: asyncio.Queue = asyncio.Queue()
        _SENTINEL = object()

        def _stream_worker():
            """在线程池中执行阻塞的 requests iter_lines"""
            try:
                resp = requests.post(url, json=payload, stream=True, timeout=180)
                if resp.status_code != 200:
                    logger.error("[Ollama-stream] 响应错误 status=%s body=%s",
                                 resp.status_code, resp.text[:300])
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

            except requests.exceptions.ConnectionError as e:
                logger.error("[Ollama-stream] 连接失败: %s (请确认 ollama serve 已启动)", e)
                stats.error_count += 1
            except Exception as e:
                logger.exception("[Ollama-stream] 流式读取异常: %s", e)
                stats.error_count += 1
            finally:
                loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)

        try:
            loop.run_in_executor(None, _stream_worker)
            logger.info("[Ollama-stream] 流式工作线程已启动")

            buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

            while True:
                item = await token_queue.get()
                if item is _SENTINEL:
                    logger.debug("[Ollama-stream] 收到结束哨兵")
                    # 刷新剩余内容
                    if buffer:
                        remaining = buffer.flush()
                        if remaining:
                            yield remaining, None
                    break

                stats.token_count += 1
                
                # 处理思考内容
                if item.startswith("[THINKING]") and item.endswith("[/THINKING]"):
                    thinking_content = item[10:-11]
                    stats.thinking_chars += len(thinking_content)
                    yield item, None
                    continue

                stats.char_count += len(item)
                
                if stats.token_count % 50 == 0:
                    logger.debug("[Ollama-stream] 接收中: tokens=%d chars=%d tps=%.1f", 
                                stats.token_count, stats.char_count, stats.tokens_per_second)

                if use_buffer and buffer:
                    # 使用缓冲区批量发送
                    flushed = buffer.add(item)
                    if flushed:
                        stats.chunk_count += 1
                        yield flushed, None
                else:
                    # 不使用缓冲区，直接发送
                    yield item, None

            stats.end_time = time.time()
            total_thinking = ''.join(thinking_buffer)
            if total_thinking:
                logger.info("[Ollama-stream] 思考过程:\n%s", total_thinking)
                logger.info("[Ollama-stream] 思考过程结束")
            
            logger.info("[Ollama-stream] 完成 tokens=%d chars=%d chunks=%d elapsed=%.2fs tps=%.1f cps=%.1f",
                        stats.token_count, stats.char_count, stats.chunk_count, 
                        stats.elapsed, stats.tokens_per_second, stats.chars_per_second)
            
            # 最后发送统计信息
            yield "", stats

        except Exception as e:
            stats.end_time = time.time()
            stats.error_count += 1
            logger.exception("[Ollama-stream] 异步处理异常: %s", e)
            yield "", stats

    # ------------------------------------------------------------------ #
    #  OpenAI 兼容 真正的流式调用                                        #
    # ------------------------------------------------------------------ #
    async def _call_openai_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """调用 OpenAI 兼容接口流式输出"""
        if not self.enabled or not self._client:
            return

        import asyncio

        messages = self._build_messages(prompt, system_prompt)
        buffer_size = self.llm_config.get('bufferSize', 20)
        flush_interval = self.llm_config.get('flushInterval', 0.05)
        
        logger.info("[OpenAI-stream] 请求 model=%s buffer=%d prompt=%s",
                    self.llm_config.get('model'), buffer_size if use_buffer else 0, 
                    _truncate(prompt, 80))
        
        stats = StreamStats(start_time=time.time())

        try:
            loop = asyncio.get_event_loop()

            def _do_stream():
                return self._client.chat.completions.create(
                    model=self.llm_config.get('model', 'gpt-4'),
                    messages=messages,
                    temperature=self.llm_config.get('temperature', 0.3),
                    max_tokens=self.llm_config.get('maxTokens', 2048),
                    stream=True
                )

            stream = await loop.run_in_executor(None, _do_stream)
            buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

            for chunk in stream:
                delta = chunk.choices[0].delta.content if chunk.choices else None
                if delta:
                    stats.token_count += 1
                    stats.char_count += len(delta)
                    
                    if stats.token_count % 50 == 0:
                        logger.debug("[OpenAI-stream] 接收中: tokens=%d chars=%d tps=%.1f", 
                                    stats.token_count, stats.char_count, stats.tokens_per_second)
                    
                    if use_buffer and buffer:
                        flushed = buffer.add(delta)
                        if flushed:
                            stats.chunk_count += 1
                            yield flushed, None
                    else:
                        yield delta, None
                
                await asyncio.sleep(0)

            # 刷新剩余内容
            if buffer:
                remaining = buffer.flush()
                if remaining:
                    yield remaining, None

            stats.end_time = time.time()
            logger.info("[OpenAI-stream] 完成 tokens=%d chars=%d chunks=%d elapsed=%.2fs tps=%.1f cps=%.1f",
                        stats.token_count, stats.char_count, stats.chunk_count,
                        stats.elapsed, stats.tokens_per_second, stats.chars_per_second)
            
            yield "", stats

        except Exception as e:
            stats.end_time = time.time()
            stats.error_count += 1
            logger.exception("[OpenAI-stream] 流式调用异常: %s", e)
            yield "", stats

    # ------------------------------------------------------------------ #
    #  自定义 API 流式调用                                               #
    # ------------------------------------------------------------------ #
    async def _call_custom_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """
        调用自定义 API 流式输出，批量 yield 文本内容以减少开销。
        将 requests iter_lines 放入线程池，通过 asyncio.Queue 传回 token，
        彻底避免阻塞事件循环。

        Args:
            prompt: 提示词
            system_prompt: 系统提示词
            use_buffer: 是否使用缓冲区批量发送

        Yields:
            (文本内容, 统计信息) - 统计信息仅在结束时提供
        """
        if not self.enabled:
            return

        import asyncio

        base_url = self._get_base_url()
        model = self._get_model()
        api_key = settings.LLM_API_KEY or self.llm_config.get('apiKey')
        messages = self._build_messages(prompt, system_prompt)
        url = f"{base_url}/chat/completions"
        buffer_size = self.llm_config.get('bufferSize', 20)
        flush_interval = self.llm_config.get('flushInterval', 0.05)
        
        payload = {
            "stream": True,
            "messages": messages,
            "model": model,
            "temperature": self.llm_config.get('temperature', 0.3),
            "max_tokens": self.llm_config.get('maxTokens', 2048)
        }
        
        headers = {
            "Content-Type": "application/json",
            "token": api_key
        }

        logger.info("[Custom-stream] 开始请求 model=%s url=%s buffer=%d", 
                    model, url, buffer_size if use_buffer else 0)
        logger.debug("[Custom-stream] prompt(前200字)=%s", _truncate(prompt, 200))
        
        stats = StreamStats(start_time=time.time())

        loop = asyncio.get_event_loop()
        token_queue: asyncio.Queue = asyncio.Queue()
        _SENTINEL = object()

        def _stream_worker():
            """在线程池中执行阻塞的 requests iter_lines"""
            try:
                resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=180)
                if resp.status_code != 200:
                    logger.error("[Custom-stream] 响应错误 status=%s body=%s",
                                 resp.status_code, resp.text[:300])
                    loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)
                    return

                for raw_line in resp.iter_lines():
                    if not raw_line:
                        continue

                    try:
                        line_text = raw_line.decode('utf-8')
                        if not line_text.startswith('data: '):
                            continue

                        data_str = line_text[6:].strip()
                        if data_str == '[DONE]':
                            break

                        chunk = json.loads(data_str)
                        delta = chunk.get('choices', [{}])[0].get('delta', {})

                        # 处理思考内容：兼容 reasoning_content（DeepSeek-R1）和 reasoning（minimax-m2.7）
                        reasoning = delta.get('reasoning_content', '') or delta.get('reasoning', '')
                        if reasoning:
                            loop.call_soon_threadsafe(
                                token_queue.put_nowait,
                                f"[THINKING]{reasoning}[/THINKING]"
                            )

                        # 正文 content
                        content = delta.get('content', '')
                        if content:
                            loop.call_soon_threadsafe(token_queue.put_nowait, content)

                    except (json.JSONDecodeError, UnicodeDecodeError):
                        continue

            except requests.exceptions.ConnectionError as e:
                logger.error("[Custom-stream] 连接失败: %s", e)
                stats.error_count += 1
            except Exception as e:
                logger.exception("[Custom-stream] 流式读取异常: %s", e)
                stats.error_count += 1
            finally:
                loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)

        try:
            loop.run_in_executor(None, _stream_worker)
            logger.info("[Custom-stream] 流式工作线程已启动")
            
            buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

            while True:
                item = await token_queue.get()
                if item is _SENTINEL:
                    logger.debug("[Custom-stream] 收到结束哨兵")
                    # 刷新剩余内容
                    if buffer:
                        remaining = buffer.flush()
                        if remaining:
                            yield remaining, None
                    break

                stats.token_count += 1

                # ── thinking 块：直接透传，不经过缓冲区 ──
                if item.startswith("[THINKING]") and item.endswith("[/THINKING]"):
                    thinking_content = item[10:-11]
                    stats.thinking_chars += len(thinking_content)
                    logger.debug("[Custom-stream] thinking 块 len=%d", len(thinking_content))
                    yield item, None
                    continue

                stats.char_count += len(item)
                
                if stats.token_count % 50 == 0:
                    logger.debug("[Custom-stream] 接收中: tokens=%d chars=%d tps=%.1f", 
                                stats.token_count, stats.char_count, stats.tokens_per_second)

                if use_buffer and buffer:
                    flushed = buffer.add(item)
                    if flushed:
                        stats.chunk_count += 1
                        yield flushed, None
                else:
                    yield item, None

            stats.end_time = time.time()
            logger.info("[Custom-stream] 完成 tokens=%d chars=%d thinking_chars=%d chunks=%d elapsed=%.2fs tps=%.1f cps=%.1f",
                        stats.token_count, stats.char_count, stats.thinking_chars,
                        stats.chunk_count, stats.elapsed, stats.tokens_per_second, stats.chars_per_second)
            
            yield "", stats

        except Exception as e:
            stats.end_time = time.time()
            stats.error_count += 1
            logger.exception("[Custom-stream] 异步处理异常: %s", e)
            yield "", stats

    # ------------------------------------------------------------------ #
    #  Anthropic Claude 流式调用                                         #
    # ------------------------------------------------------------------ #
    async def _call_anthropic_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """
        调用 Anthropic Claude API 流式输出。

        Claude 使用不同的 API 格式：
        - endpoint: /v1/messages
        - header: anthropic-version: 2023-06-01
        - streaming: server-sent events
        """
        if not self.enabled:
            return

        import asyncio

        base_url = self._get_base_url()
        api_key = settings.LLM_API_KEY or self.llm_config.get('apiKey')
        model = self._get_model()
        buffer_size = self.llm_config.get('bufferSize', 20)
        flush_interval = self.llm_config.get('flushInterval', 0.05)

        url = f"{base_url}/v1/messages"
        headers = {
            "Content-Type": "application/json",
            "anthropic-version": "2023-06-01",
            "x-api-key": api_key
        }

        messages = []
        if system_prompt:
            messages.append({"role": "user", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        payload = {
            "model": model,
            "messages": messages,
            "max_tokens": self.llm_config.get('maxTokens', 2048),
            "stream": True,
            "temperature": self.llm_config.get('temperature', 0.3)
        }

        logger.info("[Anthropic-stream] 请求 model=%s buffer=%d url=%s", 
                    model, buffer_size if use_buffer else 0, url)
        logger.debug("[Anthropic-stream] prompt(前200字)=%s", _truncate(prompt, 200))
        
        stats = StreamStats(start_time=time.time())

        loop = asyncio.get_event_loop()
        token_queue: asyncio.Queue = asyncio.Queue()
        _SENTINEL = object()

        def _stream_worker():
            """在线程池中执行阻塞的 requests 流式请求"""
            try:
                resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=180)
                if resp.status_code != 200:
                    logger.error("[Anthropic-stream] 响应错误 status=%s body=%s",
                                 resp.status_code, resp.text[:500])
                    loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)
                    return

                for raw_line in resp.iter_lines():
                    if not raw_line:
                        continue

                    try:
                        line_text = raw_line.decode('utf-8')
                        if not line_text.startswith('data: '):
                            continue

                        data_str = line_text[6:].strip()
                        if data_str == '[DONE]':
                            break

                        chunk = json.loads(data_str)

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

            except requests.exceptions.ConnectionError as e:
                logger.error("[Anthropic-stream] 连接失败: %s", e)
                stats.error_count += 1
            except Exception as e:
                logger.exception("[Anthropic-stream] 流式读取异常: %s", e)
                stats.error_count += 1
            finally:
                loop.call_soon_threadsafe(token_queue.put_nowait, _SENTINEL)

        try:
            loop.run_in_executor(None, _stream_worker)
            logger.info("[Anthropic-stream] 流式工作线程已启动")
            
            buffer = StreamBuffer(buffer_size=buffer_size, flush_interval=flush_interval) if use_buffer else None

            while True:
                item = await token_queue.get()
                if item is _SENTINEL:
                    logger.debug("[Anthropic-stream] 收到结束哨兵")
                    # 刷新剩余内容
                    if buffer:
                        remaining = buffer.flush()
                        if remaining:
                            yield remaining, None
                    break

                stats.token_count += 1
                stats.char_count += len(item)
                
                if stats.token_count % 50 == 0:
                    logger.debug("[Anthropic-stream] 接收中: tokens=%d chars=%d tps=%.1f", 
                                stats.token_count, stats.char_count, stats.tokens_per_second)

                if use_buffer and buffer:
                    flushed = buffer.add(item)
                    if flushed:
                        stats.chunk_count += 1
                        yield flushed, None
                else:
                    yield item, None

            stats.end_time = time.time()
            logger.info("[Anthropic-stream] 完成 tokens=%d chars=%d chunks=%d elapsed=%.2fs tps=%.1f cps=%.1f",
                        stats.token_count, stats.char_count, stats.chunk_count,
                        stats.elapsed, stats.tokens_per_second, stats.chars_per_second)
            
            yield "", stats

        except Exception as e:
            stats.end_time = time.time()
            stats.error_count += 1
            logger.exception("[Anthropic-stream] 异步处理异常: %s", e)
            yield "", stats

    # ------------------------------------------------------------------ #
    #  统一流式入口                                                       #
    # ------------------------------------------------------------------ #
    async def call_llm_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True,
        return_stats: bool = False
    ) -> AsyncGenerator[str, None]:
        """
        统一流式调用入口，批量 yield 文本（使用缓冲区）。
        provider=ollama 使用 Ollama 流式；
        provider=anthropic 使用 Claude 流式；
        其余使用 OpenAI 兼容流式。
        
        注意：这是保持向后兼容性的方法，只返回文本内容。
        如果需要统计信息，请使用 call_llm_stream_with_stats。
        """
        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            async for text, _ in self._call_ollama_stream(prompt, system_prompt, use_buffer):
                if text:
                    yield text
        elif provider == 'anthropic':
            async for text, _ in self._call_anthropic_stream(prompt, system_prompt, use_buffer):
                if text:
                    yield text
        elif provider == 'custom':
            async for text, _ in self._call_custom_stream(prompt, system_prompt, use_buffer):
                if text:
                    yield text
        else:
            async for text, _ in self._call_openai_stream(prompt, system_prompt, use_buffer):
                if text:
                    yield text
    
    async def call_llm_stream_with_stats(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """
        统一流式调用入口，返回文本内容和统计信息。
        
        Yields:
            (文本内容, 统计信息) - 统计信息仅在最后一个 yield 时提供
        """
        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            async for item in self._call_ollama_stream(prompt, system_prompt, use_buffer):
                yield item
        elif provider == 'anthropic':
            async for item in self._call_anthropic_stream(prompt, system_prompt, use_buffer):
                yield item
        elif provider == 'custom':
            async for item in self._call_custom_stream(prompt, system_prompt, use_buffer):
                yield item
        else:
            async for item in self._call_openai_stream(prompt, system_prompt, use_buffer):
                yield item

    # ------------------------------------------------------------------ #
    #  同步调用（用于结构化输出，如意图识别）                             #
    # ------------------------------------------------------------------ #
    #  自定义 API 同步调用                                                #
    # ------------------------------------------------------------------ #
    def _call_custom(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        """调用自定义 API（非流式）
        
        Returns:
            None if failed, otherwise the response content string.
        """
        import uuid
        
        if not self.enabled:
            logger.warning("[Custom] LLM服务未启用，无法调用")
            return None

        base_url = self._get_base_url()
        model = self._get_model()
        api_key = settings.LLM_API_KEY or self.llm_config.get('apiKey')
        messages = self._build_messages(prompt, system_prompt)
        url = f"{base_url}/chat/completions"
        call_id = str(uuid.uuid4())[:16]
        
        # 使用新的LLM调用日志系统
        call_record = llm_call_logger.start_call(
            call_id=call_id,
            call_type=CallType.SYNC,
            provider='custom',
            model=model,
            system_prompt=system_prompt,
            user_prompt=prompt,
            messages=messages,
            temperature=self.llm_config.get('temperature', 0.3),
            max_tokens=self.llm_config.get('maxTokens', 2048),
            metadata={
                'url': url,
                'api_key_length': len(api_key) if api_key else 0,
            }
        )
        
        t0 = time.time()
        
        try:
            payload = {
                "stream": False,
                "messages": messages,
                "model": model,
                "temperature": self.llm_config.get('temperature', 0.3),
                "max_tokens": self.llm_config.get('maxTokens', 2048)
            }
            
            headers = {
                "Content-Type": "application/json",
                "token": api_key
            }

            logger.info("[Custom] 🚀 开始调用 API")
            logger.info(f"[Custom]   Call ID: {call_id}")
            logger.info(f"[Custom]   URL: {url}")
            logger.info(f"[Custom]   Model: {model}")
            logger.info(f"[Custom]   API Key长度: {len(api_key) if api_key else 0}")
            logger.info(f"[Custom]   消息数量: {len(messages)}")
            logger.debug(f"[Custom]   Headers: token=***" + str(len(api_key)) if api_key else "None")
            logger.debug(f"[Custom]   Payload: {json.dumps(payload, ensure_ascii=False)[:500]}")
            
            response = requests.post(url, json=payload, headers=headers, timeout=180)
            elapsed = time.time() - t0
            
            logger.info(f"[Custom] 📥 收到响应")
            logger.info(f"[Custom]   Status: {response.status_code}")
            logger.info(f"[Custom]   Elapsed: {elapsed:.3f}s")
            
            if response.status_code != 200:
                logger.error(f"[Custom] ❌ HTTP错误!")
                logger.error(f"[Custom]   Status: {response.status_code}")
                logger.error(f"[Custom]   Headers: {dict(response.headers)}")
                logger.error(f"[Custom]   Body: {response.text[:500]}")
                
                call_record.elapsed_seconds = elapsed
                llm_call_logger.fail_call(
                    Exception(f"HTTP {response.status_code}: {response.text[:200]}"),
                    metadata={'status_code': response.status_code, 'response_body': response.text[:500]}
                )
                return None

            result = response.json()
            logger.debug(f"[Custom]   Raw Response: {json.dumps(result, ensure_ascii=False)[:800]}")
            
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            reasoning = result.get('choices', [{}])[0].get('message', {}).get('reasoning', '')
            
            call_record.elapsed_seconds = elapsed
            call_record.response_content = content
            call_record.response_object = result
            call_record.char_count = len(content) if content else 0

            # 记录 reasoning（minimax-m2.7 等模型通过此字段返回思考过程）
            if reasoning:
                logger.info("[Custom] 💭 Reasoning:\n%s", reasoning)

            if not content.strip():
                logger.warning(f"[Custom] ⚠️ 响应内容为空!")
                logger.warning(f"[Custom]   Raw: {json.dumps(result, ensure_ascii=False)[:500]}")
                
                llm_call_logger.complete_call(
                    response_content="",
                    token_count=0,
                    metadata={'empty_response': True, 'has_reasoning': bool(reasoning)}
                )
                return None

            logger.info(f"[Custom] ✅ 调用成功!")
            logger.info(f"[Custom]   Elapsed: {elapsed:.3f}s")
            logger.info(f"[Custom]   Chars: {len(content)}")
            logger.info(f"[Custom]   Preview: {content[:300]}...")
            
            llm_call_logger.complete_call(
                response_content=content,
                token_count=len(content),  # 使用字符数作为近似
                metadata={'response_model': 'custom', 'has_reasoning': bool(reasoning)}
            )
            
            return content

        except requests.exceptions.ConnectionError as e:
            logger.error(f"[Custom] ❌ 连接失败: {e}")
            call_record.elapsed_seconds = time.time() - t0
            llm_call_logger.fail_call(e, metadata={'error_category': 'connection'})
            return None
        except requests.exceptions.Timeout as e:
            logger.error(f"[Custom] ❌ 请求超时: {e}")
            call_record.elapsed_seconds = time.time() - t0
            llm_call_logger.fail_call(e, metadata={'error_category': 'timeout'})
            return None
        except json.JSONDecodeError as e:
            logger.error(f"[Custom] ❌ JSON解析失败: {e}")
            call_record.elapsed_seconds = time.time() - t0
            llm_call_logger.fail_call(e, metadata={'error_category': 'json_decode'})
            return None
        except Exception as e:
            logger.exception(f"[Custom] ❌ 未知异常: {e}")
            call_record.elapsed_seconds = time.time() - t0
            llm_call_logger.fail_call(e, metadata={'error_category': 'unknown'})
            return None

    # ------------------------------------------------------------------ #
    def _call_llm_sync(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        if not self.enabled:
            return None
        
        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            return self._call_ollama(prompt, system_prompt)
        if provider == 'custom':
            return self._call_custom(prompt, system_prompt)
        
        if not self._client:
            return None
        
        try:
            messages = self._build_messages(prompt, system_prompt)
            response = self._client.chat.completions.create(
                model=self.llm_config.get('model', 'gpt-4'),
                messages=messages,
                temperature=self.llm_config.get('temperature', 0.3),
                max_tokens=self.llm_config.get('maxTokens', 2048)
            )
            return response.choices[0].message.content
        except Exception as e:
            logger.error("[OpenAI] 同步调用失败: %s", e)
            return None

    def _call_llm(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        return self._call_llm_sync(prompt, system_prompt)

    def _call_llm_sync_with_reasoning(
        self, prompt: str, system_prompt: Optional[str] = None
    ) -> Tuple[Optional[str], Optional[str]]:
        """
        同步调用 LLM，同时返回 (content, reasoning)。
        reasoning 是模型的思考过程（minimax-m2.7 的 reasoning 字段、DeepSeek-R1 的 reasoning_content 等）。
        保持向后兼容：原有 _call_llm_sync 不受影响。
        """
        if not self.enabled:
            return None, None

        provider = self.llm_config.get('provider', 'openai')
        if provider == 'custom':
            return self._call_custom_with_reasoning(prompt, system_prompt)
        if provider == 'ollama':
            return self._call_ollama_with_reasoning(prompt, system_prompt)

        # OpenAI 兼容（暂不提取 reasoning_content）
        content = self._call_llm_sync(prompt, system_prompt)
        return content, None

    def _call_custom_with_reasoning(
        self, prompt: str, system_prompt: Optional[str] = None
    ) -> Tuple[Optional[str], Optional[str]]:
        """自定义 API 同步调用，同时提取 reasoning"""
        import uuid

        if not self.enabled:
            return None, None

        base_url = self._get_base_url()
        model = self._get_model()
        api_key = settings.LLM_API_KEY or self.llm_config.get('apiKey')
        messages = self._build_messages(prompt, system_prompt)
        url = f"{base_url}/chat/completions"

        payload = {
            "stream": False,
            "messages": messages,
            "model": model,
            "temperature": self.llm_config.get('temperature', 0.3),
            "max_tokens": self.llm_config.get('maxTokens', 2048)
        }
        headers = {
            "Content-Type": "application/json",
            "token": api_key
        }

        try:
            response = requests.post(url, json=payload, headers=headers, timeout=180)
            if response.status_code != 200:
                logger.error("[Custom-sync] HTTP %s", response.status_code)
                return None, None

            result = response.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            reasoning = (
                result.get('choices', [{}])[0].get('message', {}).get('reasoning', '')
                or result.get('choices', [{}])[0].get('message', {}).get('reasoning_content', '')
            )

            if reasoning:
                logger.info("[Custom-sync] 💭 Reasoning (%d chars)", len(reasoning))

            if not content or not content.strip():
                return None, reasoning or None

            return content, reasoning or None

        except Exception as e:
            logger.error("[Custom-sync] 调用异常: %s", e)
            return None, None

    def _call_ollama_with_reasoning(
        self, prompt: str, system_prompt: Optional[str] = None
    ) -> Tuple[Optional[str], Optional[str]]:
        """Ollama 同步调用，同时提取 thinking"""
        if not self.enabled:
            return None, None

        base_url = self._get_base_url()
        model = self._get_model()
        messages = self._build_messages(prompt, system_prompt)
        url = f"{base_url}/api/chat"
        thinking_enabled = self.llm_config.get('thinking', False)
        payload = {
            "model": model,
            "messages": messages,
            "stream": False,
            "thinking": thinking_enabled,
            "options": {
                "temperature": self.llm_config.get('temperature', 0.3),
                "num_predict": self.llm_config.get('maxTokens', 2048)
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

            if thinking:
                logger.info("[Ollama-sync] 💭 Thinking (%d chars)", len(thinking))

            if not content or not content.strip():
                return None, thinking or None

            return content, thinking or None

        except Exception as e:
            logger.error("[Ollama-sync] 调用异常: %s", e)
            return None, None

    def _extract_json(self, text: str) -> Optional[Dict]:
        try:
            start = text.find('{')
            end = text.rfind('}') + 1
            if start != -1 and end > start:
                json_str = text[start:end]
                return json.loads(json_str)
        except Exception as e:
            logger.error("JSON extraction failed: %s", e)
        return None
    
    def recognize_intent(self, user_input: str, form_types: List[str]) -> Optional[Dict]:
        if not self.enabled:
            return None
        
        prompt_template = config_loader.get_prompt('intent_recognition')
        if not prompt_template:
            return None
        
        form_types_str = '\n'.join([f"- {ft}" for ft in form_types])
        prompt = prompt_template.format(
            form_types=form_types_str,
            user_input=user_input
        )
        
        result = self._call_llm(prompt)
        if result:
            return self._extract_json(result)
        return None
    
    def extract_fields(self, user_input: str, form_schema: Dict) -> Optional[Dict]:
        if not self.enabled:
            return None
        
        prompt_template = config_loader.get_prompt('field_extraction')
        if not prompt_template:
            return None
        
        schema_str = json.dumps(form_schema, ensure_ascii=False, indent=2)
        prompt = prompt_template.format(
            form_schema=schema_str,
            user_input=user_input
        )
        
        result = self._call_llm(prompt)
        if result:
            return self._extract_json(result)
        return None


llm_service = LLMService()

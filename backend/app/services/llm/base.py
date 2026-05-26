"""
LLM Provider 基础类型与共享工具
"""
import json
import re
import logging
import time
from typing import Dict, Any, Optional, List, Tuple, AsyncGenerator

logger = logging.getLogger("llm.base")


class StreamStats:
    """流式输出统计信息"""
    def __init__(self, start_time: float = 0.0):
        self.start_time = start_time
        self.end_time: float = 0.0
        self.token_count: int = 0
        self.char_count: int = 0
        self.chunk_count: int = 0
        self.thinking_chars: int = 0
        self.error_count: int = 0

    @property
    def elapsed(self) -> float:
        return self.end_time - self.start_time if self.end_time > 0 else time.time() - self.start_time

    @property
    def tokens_per_second(self) -> float:
        return self.token_count / self.elapsed if self.elapsed > 0 else 0.0

    @property
    def chars_per_second(self) -> float:
        return self.char_count / self.elapsed if self.elapsed > 0 else 0.0

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典格式"""
        return {
            "elapsed": round(self.elapsed, 3),
            "tokenCount": self.token_count,
            "charCount": self.char_count,
            "chunkCount": self.chunk_count,
            "thinkingChars": self.thinking_chars,
            "errorCount": self.error_count,
            "tokensPerSecond": round(self.tokens_per_second, 2),
            "charsPerSecond": round(self.chars_per_second, 2)
        }


class StreamBuffer:
    """流式输出缓冲区 - 批量发送 token 以减少开销"""

    def __init__(self, buffer_size: int = 10, flush_interval: float = 0.05):
        self.buffer: List[str] = []
        self.buffer_size = buffer_size
        self.flush_interval = flush_interval
        self.last_flush_time = 0.0
        self.total_chars = 0
        self.total_chunks = 0

    def add(self, text: str) -> Optional[str]:
        if not text:
            return None
        self.buffer.append(text)
        self.total_chars += len(text)
        current_size = sum(len(t) for t in self.buffer)
        import time
        current_time = time.time()
        if current_size >= self.buffer_size or (current_time - self.last_flush_time) >= self.flush_interval:
            return self.flush()
        return None

    def flush(self) -> Optional[str]:
        if not self.buffer:
            return None
        result = ''.join(self.buffer)
        self.buffer = []
        import time
        self.last_flush_time = time.time()
        self.total_chunks += 1
        return result

    def __len__(self) -> int:
        return sum(len(t) for t in self.buffer)


def normalize_base_url(raw_url: str, provider_name: str = "") -> str:
    """规范化 base_url，防止配置错误导致的 URL 拼接异常

    常见问题：
    - 前后空格或反引号
    - 末尾多余的斜杠
    - 用户误将完整端点 URL 填入（如 .../chat/completions）
    - 路径段重复（如 /v1/openai/v1）
    """
    if not raw_url:
        return ""

    url = raw_url.strip().strip("`\"'")

    url = url.rstrip("/")

    original_url = url

    if url.endswith("/chat/completions"):
        original = url
        url = url[: -len("/chat/completions")]
        logger.warning(
            "[URL规范化] base_url 末尾包含 /chat/completions，已自动去除\n"
            "  原始值: %s\n  修正值: %s\n  提示: 请在前端模型配置中更新 base_url，只填写到 /v1 即可",
            original, url
        )

    segments = url.rstrip("/").split("/")
    if len(segments) > 3:
        seen_segments = set()
        new_segments = segments[:3]
        has_duplicate = False
        
        for seg in segments[3:]:
            if seg and seg in seen_segments:
                has_duplicate = True
                logger.debug(f"[URL规范化] 跳过重复路径段: {seg}")
            else:
                new_segments.append(seg)
                if seg:
                    seen_segments.add(seg)
        
        if has_duplicate:
            new_url = "/".join(new_segments)
            logger.warning(
                "[URL规范化] base_url 存在重复路径段，已自动修复\n"
                "  原始值: %s\n  修正值: %s\n  提示: 正确的 OpenAI 兼容 base_url 通常以 /v1 结尾",
                url, new_url
            )
            url = new_url

    if provider_name:
        logger.debug("[URL规范化] provider=%s, 原始=%s, 最终=%s", provider_name, original_url, url)
    else:
        logger.debug("[URL规范化] 原始=%s, 最终=%s", original_url, url)

    return url


def extract_json(text: str) -> Optional[Dict]:
    """从 LLM 输出中提取 JSON，支持多种容错策略"""
    if not text:
        return None

    for strategy_idx in range(4):
        try:
            cleaned = text
            if strategy_idx >= 1:
                cleaned = text.replace('“', '"').replace('”', '"').replace('‘', "'").replace('’', "'")

            start = cleaned.find('{')
            end = cleaned.rfind('}') + 1
            if start == -1 or end <= start:
                continue

            json_str = cleaned[start:end]
            if strategy_idx >= 2:
                json_str = re.sub(r',\s*([}\]])', r'\1', json_str)

            if strategy_idx == 3:
                import time
                _start_time = time.time()
                in_string = False
                escape_next = False
                stack = []
                for ch in json_str:
                    if escape_next:
                        escape_next = False
                        continue
                    if ch == '\\' and in_string:
                        escape_next = True
                        continue
                    if ch == '"' and not escape_next:
                        in_string = not in_string
                        continue
                    if in_string:
                        continue
                    if ch in ('{', '['):
                        stack.append(ch)
                    elif ch == '}' and stack and stack[-1] == '{':
                        stack.pop()
                    elif ch == ']' and stack and stack[-1] == '[':
                        stack.pop()
                if in_string:
                    json_str += '"'
                while stack:
                    opener = stack.pop()
                    json_str = json_str.rstrip().rstrip(',')
                    json_str += '}' if opener == '{' else ']'

            return json.loads(json_str)
        except Exception as e:
            logger.debug("JSON extraction strategy %d failed: %s", strategy_idx, e)

    return None
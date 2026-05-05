"""
LLM Provider 基础类型与共享工具
"""
import json
import re
import logging
from typing import Dict, Any, Optional, List, Tuple, AsyncGenerator

logger = logging.getLogger("llm.base")


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
        return self.end_time - self.start_time if self.end_time > 0 else 0.0

    @property
    def tokens_per_second(self) -> float:
        return self.token_count / self.elapsed if self.elapsed > 0 else 0.0

    @property
    def chars_per_second(self) -> float:
        return self.char_count / self.elapsed if self.elapsed > 0 else 0.0


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
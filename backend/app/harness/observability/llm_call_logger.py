"""
大模型(LLM)调用专用日志模块

提供详细的LLM调用追踪，包括：
- 请求/响应的完整记录
- Token使用统计
- 耗时分析
- 错误追踪
"""

from typing import Dict, Any, Optional, List
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import json
import logging
import time
import traceback

logger = logging.getLogger("llm_call")


class CallType(Enum):
    """LLM调用类型"""
    SYNC = "sync"              # 同步调用
    STREAM = "stream"          # 流式调用
    INTENT_RECOGNITION = "intent"   # 意图识别
    CHAT_RESPONSE = "chat"    # 聊天回复
    FIELD_EXTRACTION = "field" # 字段提取


class CallStatus(Enum):
    """调用状态"""
    STARTED = "STARTED"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    TIMEOUT = "TIMEOUT"


@dataclass
class LLMCallRecord:
    """LLM调用记录"""
    call_id: str
    call_type: CallType
    provider: str
    model: str
    timestamp: datetime
    status: CallStatus
    
    # 请求信息
    system_prompt: Optional[str] = None
    user_prompt: Optional[str] = None
    full_prompt: Optional[str] = None
    messages: Optional[List[Dict]] = None
    temperature: float = 0.3
    max_tokens: int = 2048
    
    # 响应信息
    response_content: Optional[str] = None
    response_object: Optional[Dict] = None
    stream_chunks: List[str] = field(default_factory=list)
    
    # 统计信息
    token_count: int = 0
    char_count: int = 0
    chunk_count: int = 0
    elapsed_seconds: float = 0.0
    tokens_per_second: float = 0.0
    chars_per_second: float = 0.0
    
    # 错误信息
    error_type: Optional[str] = None
    error_message: Optional[str] = None
    error_traceback: Optional[str] = None
    
    # 元数据
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    def to_dict(self) -> Dict:
        return {
            "call_id": self.call_id,
            "call_type": self.call_type.value,
            "provider": self.provider,
            "model": self.model,
            "timestamp": self.timestamp.isoformat(),
            "status": self.status.value,
            "system_prompt": self._truncate(self.system_prompt, 500),
            "user_prompt": self._truncate(self.user_prompt, 500),
            "full_prompt": self._truncate(self.full_prompt, 1000),
            "messages": self._truncate_messages(self.messages),
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "response_content": self._truncate(self.response_content, 1000),
            "response_object": self.response_object,
            "stream_chunks_count": len(self.stream_chunks),
            "token_count": self.token_count,
            "char_count": self.char_count,
            "chunk_count": self.chunk_count,
            "elapsed_seconds": round(self.elapsed_seconds, 3),
            "tokens_per_second": round(self.tokens_per_second, 2),
            "chars_per_second": round(self.chars_per_second, 2),
            "error_type": self.error_type,
            "error_message": self._truncate(self.error_message, 500),
            "metadata": self.metadata
        }
    
    @staticmethod
    def _truncate(text: Optional[str], max_len: int) -> Optional[str]:
        if not text:
            return None
        if len(text) <= max_len:
            return text
        return text[:max_len] + f"... [truncated, total {len(text)} chars]"
    
    @staticmethod
    def _truncate_messages(messages: Optional[List[Dict]]) -> Optional[List[Dict]]:
        if not messages:
            return None
        truncated = []
        for msg in messages:
            truncated.append({
                "role": msg.get("role"),
                "content": LLMCallRecord._truncate(msg.get("content"), 200)
            })
        return truncated


class LLMCallLogger:
    """大模型调用日志记录器"""
    
    def __init__(self, enable_console: bool = True, enable_file: bool = True):
        self.enable_console = enable_console
        self.enable_file = enable_file
        self._current_call: Optional[LLMCallRecord] = None
        self._call_history: List[LLMCallRecord] = []
        self._max_history = 1000
    
    def start_call(
        self,
        call_id: str,
        call_type: CallType,
        provider: str,
        model: str,
        system_prompt: Optional[str] = None,
        user_prompt: Optional[str] = None,
        full_prompt: Optional[str] = None,
        messages: Optional[List[Dict]] = None,
        temperature: float = 0.3,
        max_tokens: int = 2048,
        metadata: Optional[Dict] = None
    ) -> LLMCallRecord:
        """开始一次LLM调用"""
        self._current_call = LLMCallRecord(
            call_id=call_id,
            call_type=call_type,
            provider=provider,
            model=model,
            timestamp=datetime.now(),
            status=CallStatus.STARTED,
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            full_prompt=full_prompt,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
            metadata=metadata or {}
        )
        
        self._log_call_start()
        return self._current_call
    
    def add_stream_chunk(self, chunk: str):
        """添加流式输出的一个chunk"""
        if self._current_call:
            self._current_call.stream_chunks.append(chunk)
            self._current_call.char_count += len(chunk)
            self._current_call.chunk_count += 1
    
    def complete_call(
        self,
        response_content: Optional[str] = None,
        token_count: int = 0,
        metadata: Optional[Dict] = None
    ):
        """完成一次LLM调用"""
        if not self._current_call:
            logger.warning("complete_call called without start_call")
            return
        
        call = self._current_call
        call.response_content = response_content
        call.token_count = token_count
        call.status = CallStatus.SUCCESS
        
        if call.elapsed_seconds > 0:
            call.tokens_per_second = token_count / call.elapsed_seconds if token_count > 0 else 0
            call.chars_per_second = call.char_count / call.elapsed_seconds if call.elapsed_seconds > 0 else 0
        
        if metadata:
            call.metadata.update(metadata)
        
        self._log_call_complete()
        self._add_to_history()
        self._current_call = None
    
    def fail_call(self, error: Exception, metadata: Optional[Dict] = None):
        """记录一次失败的LLM调用"""
        if not self._current_call:
            logger.warning("fail_call called without start_call")
            return
        
        call = self._current_call
        call.status = CallStatus.FAILED
        call.error_type = type(error).__name__
        call.error_message = str(error)
        call.error_traceback = traceback.format_exc()
        
        if metadata:
            call.metadata.update(metadata)
        
        self._log_call_failed()
        self._add_to_history()
        self._current_call = None
    
    def get_current_call(self) -> Optional[LLMCallRecord]:
        """获取当前正在进行的调用"""
        return self._current_call
    
    def get_call_history(self, limit: int = 100) -> List[LLMCallRecord]:
        """获取调用历史"""
        return self._call_history[-limit:]
    
    def _add_to_history(self):
        """添加到历史记录"""
        self._call_history.append(self._current_call)
        if len(self._call_history) > self._max_history:
            self._call_history = self._call_history[-self._max_history:]
    
    def _log_call_start(self):
        """记录调用开始"""
        call = self._current_call
        logger.info("")
        logger.info("┌" + "─" * 78)
        logger.info(f"│ 🚀 LLM调用开始")
        logger.info(f"│    Call ID: {call.call_id}")
        logger.info(f"│    Type: {call.call_type.value}")
        logger.info(f"│    Provider: {call.provider}")
        logger.info(f"│    Model: {call.model}")
        logger.info(f"│    Temperature: {call.temperature}")
        logger.info(f"│    Max Tokens: {call.max_tokens}")
        
        if call.messages:
            logger.info(f"│    Messages Count: {len(call.messages)}")
            for i, msg in enumerate(call.messages):
                role = msg.get('role', 'unknown')
                content = msg.get('content', '')
                preview = self._truncate_for_display(content, 100)
                logger.info(f"│      [{i}] {role}: {preview}")
        
        if call.system_prompt:
            logger.info(f"│    System Prompt: {self._truncate_for_display(call.system_prompt, 200)}")
        
        if call.user_prompt:
            logger.info(f"│    User Prompt: {self._truncate_for_display(call.user_prompt, 200)}")
        
        if call.full_prompt:
            logger.info(f"│    Full Prompt Preview: {self._truncate_for_display(call.full_prompt, 200)}")
        
        logger.info("└" + "─" * 78)
    
    def _log_call_complete(self):
        """记录调用完成"""
        call = self._current_call
        logger.info("")
        logger.info("┌" + "─" * 78)
        logger.info(f"│ ✅ LLM调用成功")
        logger.info(f"│    Call ID: {call.call_id}")
        logger.info(f"│    Status: {call.status.value}")
        logger.info(f"│    Elapsed: {call.elapsed_seconds:.3f}s")
        
        if call.token_count > 0:
            logger.info(f"│    Tokens: {call.token_count} | TPS: {call.tokens_per_second:.2f}")
        
        if call.char_count > 0:
            logger.info(f"│    Chars: {call.char_count} | CPS: {call.chars_per_second:.2f}")
        
        if call.chunk_count > 0:
            logger.info(f"│    Stream Chunks: {call.chunk_count}")
        
        if call.response_content:
            logger.info(f"│    Response Preview: {self._truncate_for_display(call.response_content, 300)}")
        
        if call.metadata:
            logger.info(f"│    Metadata: {json.dumps(call.metadata, ensure_ascii=False)[:200]}")
        
        logger.info("└" + "─" * 78)
        logger.info("")
    
    def _log_call_failed(self):
        """记录调用失败"""
        call = self._current_call
        logger.error("")
        logger.error("┌" + "─" * 78)
        logger.error(f"│ ❌ LLM调用失败")
        logger.error(f"│    Call ID: {call.call_id}")
        logger.error(f"│    Status: {call.status.value}")
        logger.error(f"│    Error Type: {call.error_type}")
        logger.error(f"│    Error Message: {call.error_message}")
        
        if call.elapsed_seconds > 0:
            logger.error(f"│    Elapsed: {call.elapsed_seconds:.3f}s")
        
        if call.error_traceback:
            logger.error(f"│    Traceback: {call.error_traceback[:500]}")
        
        if call.metadata:
            logger.error(f"│    Metadata: {json.dumps(call.metadata, ensure_ascii=False)[:200]}")
        
        logger.error("└" + "─" * 78)
        logger.error("")
    
    @staticmethod
    def _truncate_for_display(text: Optional[str], max_len: int) -> str:
        """截断文本用于显示"""
        if not text:
            return "(empty)"
        text = text.replace('\n', ' ').replace('\r', ' ')
        if len(text) <= max_len:
            return text
        return text[:max_len] + f"... [total {len(text)} chars]"
    
    def export_history_json(self, limit: int = 100) -> str:
        """导出历史记录为JSON"""
        records = self.get_call_history(limit)
        return json.dumps([r.to_dict() for r in records], ensure_ascii=False, indent=2)


# 全局实例
_llm_call_logger: Optional[LLMCallLogger] = None


def get_llm_call_logger() -> LLMCallLogger:
    """获取LLM调用日志记录器全局实例"""
    global _llm_call_logger
    if _llm_call_logger is None:
        _llm_call_logger = LLMCallLogger()
    return _llm_call_logger


# 便捷函数
def log_llm_call_start(**kwargs) -> LLMCallRecord:
    """开始一次LLM调用日志"""
    return get_llm_call_logger().start_call(**kwargs)


def log_llm_call_complete(**kwargs):
    """完成一次LLM调用日志"""
    get_llm_call_logger().complete_call(**kwargs)


def log_llm_call_fail(**kwargs):
    """记录LLM调用失败"""
    get_llm_call_logger().fail_call(**kwargs)

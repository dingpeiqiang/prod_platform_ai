"""
结构化日志
"""

from typing import Dict, Any, Optional, List
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import json
import logging
import uuid

# 使用主应用配置的 logger
logger = logging.getLogger("harness.observability")


class LogLevel(Enum):
    DEBUG = "DEBUG"
    INFO = "INFO"
    WARNING = "WARNING"
    ERROR = "ERROR"
    CRITICAL = "CRITICAL"


@dataclass
class LogEntry:
    id: str
    timestamp: datetime
    level: LogLevel
    message: str
    context: Dict[str, Any] = field(default_factory=dict)
    session_id: Optional[str] = None
    user_id: Optional[str] = None
    trace_id: Optional[str] = None
    span_id: Optional[str] = None
    component: str = "harness"
    duration_ms: Optional[float] = None

    def to_dict(self) -> Dict:
        return {
            "id": self.id,
            "timestamp": self.timestamp.isoformat(),
            "level": self.level.value,
            "message": self.message,
            "context": self.context,
            "session_id": self.session_id,
            "user_id": self.user_id,
            "trace_id": self.trace_id,
            "span_id": self.span_id,
            "component": self.component,
            "duration_ms": self.duration_ms
        }


class AgentLogger:
    """AI Agent 结构化日志"""

    def __init__(
        self,
        max_entries: int = 10000,
        enable_console: bool = True,
        component: str = "harness"
    ):
        self.max_entries = max_entries
        self.enable_console = enable_console
        self.default_component = component
        self._logs: List[LogEntry] = []
        self._current_context: Dict[str, Any] = {}
        self._session_id: Optional[str] = None
        self._user_id: Optional[str] = None
        self._trace_id: Optional[str] = None
        self._span_id: Optional[str] = None

    def set_context(
        self,
        session_id: Optional[str] = None,
        user_id: Optional[str] = None,
        trace_id: Optional[str] = None,
        **kwargs
    ):
        if session_id:
            self._session_id = session_id
        if user_id:
            self._user_id = user_id
        if trace_id:
            self._trace_id = trace_id
        self._current_context.update(kwargs)

    def clear_context(self):
        self._current_context.clear()

    def log(
        self,
        level: LogLevel,
        message: str,
        context: Optional[Dict[str, Any]] = None,
        session_id: Optional[str] = None,
        **kwargs
    ) -> LogEntry:
        full_context = {**self._current_context}
        if context:
            full_context.update(context)
        
        entry = LogEntry(
            id=str(uuid.uuid4())[:16],
            timestamp=datetime.now(),
            level=level,
            message=message,
            context=full_context,
            session_id=session_id or self._session_id,
            user_id=self._user_id,
            trace_id=self._trace_id,
            span_id=self._span_id,
            component=kwargs.get("component", self.default_component),
            duration_ms=kwargs.get("duration_ms")
        )
        
        self._logs.append(entry)
        self._trim_logs()
        
        if self.enable_console:
            self._console_output(entry)
        
        return entry

    def debug(self, message: str, **kwargs):
        return self.log(LogLevel.DEBUG, message, **kwargs)

    def info(self, message: str, **kwargs):
        return self.log(LogLevel.INFO, message, **kwargs)

    def warning(self, message: str, **kwargs):
        return self.log(LogLevel.WARNING, message, **kwargs)

    def error(self, message: str, **kwargs):
        return self.log(LogLevel.ERROR, message, **kwargs)

    def critical(self, message: str, **kwargs):
        return self.log(LogLevel.CRITICAL, message, **kwargs)

    def query(
        self,
        session_id: Optional[str] = None,
        level: Optional[LogLevel] = None,
        limit: int = 100
    ) -> List[LogEntry]:
        results = self._logs
        if session_id:
            results = [r for r in results if r.session_id == session_id]
        if level:
            results = [r for r in results if r.level == level]
        results = sorted(results, key=lambda x: x.timestamp, reverse=True)
        return results[:limit]

    def export_logs(self, session_id: Optional[str] = None) -> str:
        logs = self._logs if not session_id else self.query(session_id=session_id)
        return json.dumps([log.to_dict() for log in logs], ensure_ascii=False, indent=2)

    def clear(self):
        self._logs.clear()

    def _trim_logs(self):
        if len(self._logs) > self.max_entries:
            self._logs = self._logs[-self.max_entries:]

    def _console_output(self, entry: LogEntry):
        log_msg = f"[{entry.timestamp.strftime('%H:%M:%S')}] {entry.level.value} [{entry.component}] {entry.message}"
        if entry.context:
            log_msg += f" | {json.dumps(entry.context, ensure_ascii=False)}"
        
        if entry.level == LogLevel.DEBUG:
            logger.debug(log_msg)
        elif entry.level == LogLevel.INFO:
            logger.info(log_msg)
        elif entry.level == LogLevel.WARNING:
            logger.warning(log_msg)
        else:
            logger.error(log_msg)


_agent_logger: Optional[AgentLogger] = None


def get_agent_logger() -> AgentLogger:
    global _agent_logger
    if _agent_logger is None:
        _agent_logger = AgentLogger()
    return _agent_logger

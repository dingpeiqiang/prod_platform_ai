"""
分布式追踪
"""

from typing import Dict, Any, Optional, List, Callable
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import uuid
import time
import threading

from collections import defaultdict


class SpanStatus(Enum):
    """Span 状态"""
    OK = "ok"
    ERROR = "error"
    TIMEOUT = "timeout"


@dataclass
class Span:
    """追踪 Span"""
    span_id: str
    trace_id: str
    name: str
    start_time: datetime
    end_time: Optional[datetime] = None
    status: SpanStatus = SpanStatus.OK
    parent_span_id: Optional[str] = None
    tags: Dict[str, str] = field(default_factory=dict)
    logs: List[Dict] = field(default_factory=list)
    duration_ms: Optional[float] = None
    component: str = "harness"

    def finish(self, status: SpanStatus = SpanStatus.OK):
        self.end_time = datetime.now()
        self.status = status
        self.duration_ms = (self.end_time - self.start_time).total_seconds() * 1000

    def add_tag(self, key: str, value: str):
        self.tags[key] = value

    def add_log(self, message: str, attributes: Optional[Dict] = None):
        self.logs.append({
            "timestamp": datetime.now().isoformat(),
            "message": message,
            "attributes": attributes or {}
        })

    def to_dict(self) -> Dict:
        return {
            "span_id": self.span_id,
            "trace_id": self.trace_id,
            "name": self.name,
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "status": self.status.value,
            "parent_span_id": self.parent_span_id,
            "tags": self.tags,
            "logs": self.logs,
            "duration_ms": self.duration_ms,
            "component": self.component
        }


class Tracer:
    """
    分布式追踪器
    
    功能：
    1. 创建追踪 Span
    2. 层级关系管理
    3. 追踪导出
    4. 性能分析
    """

    def __init__(self, service_name: str = "harness"):
        self.service_name = service_name
        self._spans: Dict[str, Span] = {}
        self._trace_spans: Dict[str, List[str]] = defaultdict(list)  # trace_id -> span_ids
        self._lock = threading.Lock()

    def start_span(
        self,
        name: str,
        trace_id: Optional[str] = None,
        parent_span_id: Optional[str] = None,
        component: str = "harness"
    ) -> Span:
        """开始一个新的 Span"""
        trace_id = trace_id or str(uuid.uuid4())
        span_id = str(uuid.uuid4())[:16]
        
        span = Span(
            span_id=span_id,
            trace_id=trace_id,
            name=name,
            start_time=datetime.now(),
            parent_span_id=parent_span_id,
            component=component
        )
        
        with self._lock:
            self._spans[span_id] = span
            self._trace_spans[trace_id].append(span_id)
        
        return span

    def finish_span(self, span: Span, status: SpanStatus = SpanStatus.OK):
        """结束 Span"""
        span.finish(status)

    def get_trace(self, trace_id: str) -> List[Span]:
        """获取追踪的所有 Span"""
        with self._lock:
            span_ids = self._trace_spans.get(trace_id, [])
            spans = [self._spans[sid] for sid in span_ids if sid in self._spans]
            return sorted(spans, key=lambda s: s.start_time)

    def get_span(self, span_id: str) -> Optional[Span]:
        """获取单个 Span"""
        return self._spans.get(span_id)

    def export_trace(self, trace_id: str) -> Dict:
        """导出追踪"""
        spans = self.get_trace(trace_id)
        if not spans:
            return {}
        
        return {
            "trace_id": trace_id,
            "service_name": self.service_name,
            "start_time": spans[0].start_time.isoformat() if spans else None,
            "end_time": spans[-1].end_time.isoformat() if spans[-1].end_time else None,
            "total_duration_ms": sum(s.duration_ms or 0 for s in spans),
            "span_count": len(spans),
            "spans": [s.to_dict() for s in spans]
        }

    def export_traces(
        self,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 100
    ) -> List[Dict]:
        """导出追踪列表"""
        with self._lock:
            traces = []
            for trace_id, span_ids in self._trace_spans.items():
                if not span_ids:
                    continue
                first_span = self._spans.get(span_ids[0])
                if not first_span:
                    continue
                
                # 时间过滤
                if start_time and first_span.start_time < start_time:
                    continue
                if end_time and first_span.start_time > end_time:
                    continue
                
                traces.append(self.export_trace(trace_id))
            
            traces.sort(key=lambda t: t.get("start_time", ""), reverse=True)
            return traces[:limit]

    def analyze_trace(self, trace_id: str) -> Dict[str, Any]:
        """分析追踪性能"""
        spans = self.get_trace(trace_id)
        
        if not spans:
            return {}
        
        # 找出最慢的 Span
        slowest = max(spans, key=lambda s: s.duration_ms or 0) if spans else None
        
        # 找出有错误的 Span
        errors = [s for s in spans if s.status == SpanStatus.ERROR]
        
        # 构建 Span 树
        span_tree = self._build_span_tree(spans)
        
        return {
            "trace_id": trace_id,
            "span_count": len(spans),
            "total_duration_ms": sum(s.duration_ms or 0 for s in spans),
            "slowest_span": slowest.to_dict() if slowest else None,
            "error_count": len(errors),
            "errors": [s.to_dict() for s in errors],
            "span_tree": span_tree
        }

    def _build_span_tree(self, spans: List[Span]) -> Dict:
        """构建 Span 树"""
        span_dict = {s.span_id: s for s in spans}
        children = defaultdict(list)
        
        for span in spans:
            if span.parent_span_id:
                children[span.parent_span_id].append(span)
        
        def build_node(span: Span) -> Dict:
            return {
                "span_id": span.span_id,
                "name": span.name,
                "duration_ms": span.duration_ms,
                "status": span.status.value,
                "children": [build_node(child) for child in children[span.span_id]]
            }
        
        # 找到根 Span
        roots = [s for s in spans if not s.parent_span_id]
        return {
            "roots": [build_node(root) for root in roots]
        }

    def clear_old_traces(self, max_age_hours: int = 24):
        """清理旧追踪"""
        import time
        
        with self._lock:
            cutoff = datetime.now().timestamp() - max_age_hours * 3600
            to_remove = []
            
            for trace_id, span_ids in self._trace_spans.items():
                if span_ids:
                    first_span = self._spans.get(span_ids[0])
                    if first_span and first_span.start_time.timestamp() < cutoff:
                        to_remove.append(trace_id)
            
            for trace_id in to_remove:
                span_ids = self._trace_spans.pop(trace_id, [])
                for span_id in span_ids:
                    self._spans.pop(span_id, None)

    def get_stats(self) -> Dict[str, Any]:
        """获取统计"""
        with self._lock:
            return {
                "total_traces": len(self._trace_spans),
                "total_spans": len(self._spans),
                "service_name": self.service_name
            }


_tracer: Optional[Tracer] = None


def get_tracer(service_name: str = "harness") -> Tracer:
    global _tracer
    if _tracer is None:
        _tracer = Tracer(service_name)
    return _tracer


# 上下文管理器
class trace:
    """Span 追踪上下文管理器"""
    
    def __init__(self, name: str, tracer: Optional[Tracer] = None, component: str = "harness"):
        self.name = name
        self.tracer = tracer or get_tracer()
        self.component = component
        self.span: Optional[Span] = None
    
    def __enter__(self) -> Span:
        self.span = self.tracer.start_span(self.name, component=self.component)
        return self.span
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.span:
            status = SpanStatus.ERROR if exc_type else SpanStatus.OK
            self.span.add_log("exception", {"type": str(exc_type), "message": str(exc_val)}) if exc_type else None
            self.tracer.finish_span(self.span, status)
        return False

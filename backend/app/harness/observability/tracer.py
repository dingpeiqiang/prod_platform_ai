"""
分布式追踪 - 支持数据库持久化
"""

from typing import Dict, Any, Optional, List, Callable
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import uuid
import threading
import time

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
    分布式追踪器 - 支持数据库持久化
    
    功能：
    1. 创建追踪 Span
    2. 层级关系管理
    3. 追踪导出
    4. 性能分析
    5. 数据库持久化
    """

    def __init__(self, service_name: str = "harness", use_database: bool = True):
        self.service_name = service_name
        self.use_database = use_database
        self._spans: Dict[str, Span] = {}
        self._trace_spans: Dict[str, List[str]] = defaultdict(list)  # trace_id -> span_ids
        self._lock = threading.Lock()
        
        # 延迟导入数据库相关模块
        self._db_session = None
        self._Trace = None
        self._Span = None
        self._SpanStatusDB = None
        self._db_initialized = False
        self._db_init_error = None

    def _init_db(self):
        """延迟初始化数据库连接"""
        if self._db_initialized or not self.use_database:
            return
        
        if self._db_init_error:
            # 之前初始化失败过，不再尝试
            return
        
        try:
            from sqlalchemy import create_engine
            from sqlalchemy.orm import sessionmaker
            from app.core.config import get_settings
            from app.models.trace_model import Trace, Span as DBSpan, SpanStatus as DBSpanStatus
            
            settings = get_settings()
            engine = create_engine(settings.DATABASE_URL, pool_timeout=5, connect_args={"connect_timeout": 5})
            SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
            
            # 创建表（如果不存在）
            from app.models.trace_model import Base
            Base.metadata.create_all(bind=engine)
            
            self._db_session = SessionLocal()
            self._Trace = Trace
            self._Span = DBSpan
            self._SpanStatusDB = DBSpanStatus
            self._db_initialized = True
        except Exception as e:
            # 如果数据库初始化失败，回退到内存存储
            self._db_init_error = str(e)
            self.use_database = False

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
        """结束 Span 并保存到数据库"""
        span.finish(status)
        
        # 保存到数据库（后台异步执行，不阻塞主线程）
        if self.use_database:
            threading.Thread(target=self._save_to_db_async, args=(span,), daemon=True).start()

    def _save_to_db_async(self, span: Span):
        """异步保存 Span 到数据库"""
        try:
            self._init_db()
            
            if self._db_session and self._Span and self._Trace:
                session = self._db_session()
                try:
                    # 检查是否已存在该 trace
                    db_trace = session.query(self._Trace).filter(
                        self._Trace.id == span.trace_id
                    ).first()
                    
                    if not db_trace:
                        db_trace = self._Trace(
                            id=span.trace_id,
                            service_name=self.service_name,
                            start_time=span.start_time,
                            end_time=span.end_time,
                            total_duration_ms=span.duration_ms,
                            span_count=1
                        )
                        session.add(db_trace)
                    else:
                        db_trace.end_time = span.end_time
                        db_trace.span_count += 1
                        if db_trace.total_duration_ms:
                            db_trace.total_duration_ms += span.duration_ms or 0
                        else:
                            db_trace.total_duration_ms = span.duration_ms
                    
                    # 保存 span
                    db_span = self._Span(
                        id=span.span_id,
                        trace_id=span.trace_id,
                        parent_span_id=span.parent_span_id,
                        name=span.name,
                        component=span.component,
                        start_time=span.start_time,
                        end_time=span.end_time,
                        duration_ms=span.duration_ms,
                        status=self._SpanStatusDB(span.status.value),
                        tags=span.tags,
                        logs=span.logs
                    )
                    session.add(db_span)
                    
                    session.commit()
                except Exception as e:
                    session.rollback()
                finally:
                    session.close()
        except Exception as e:
            # 数据库保存失败不影响业务
            pass

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
        
        # 如果内存中没有，尝试从数据库加载
        if not spans and self.use_database:
            spans = self._load_trace_from_db(trace_id)
        
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

    def _load_trace_from_db(self, trace_id: str) -> List[Span]:
        """从数据库加载追踪"""
        try:
            self._init_db()
            
            if self._db_session and self._Span:
                session = self._db_session()
                try:
                    db_spans = session.query(self._Span).filter(
                        self._Span.trace_id == trace_id
                    ).order_by(self._Span.start_time).all()
                    
                    spans = []
                    for db_span in db_spans:
                        span = Span(
                            span_id=db_span.id,
                            trace_id=db_span.trace_id,
                            name=db_span.name,
                            start_time=db_span.start_time,
                            end_time=db_span.end_time,
                            duration_ms=db_span.duration_ms,
                            status=SpanStatus(db_span.status.value),
                            parent_span_id=db_span.parent_span_id,
                            component=db_span.component,
                            tags=db_span.tags or {},
                            logs=db_span.logs or []
                        )
                        spans.append(span)
                    
                    return spans
                finally:
                    session.close()
        except Exception as e:
            pass
        
        return []

    def export_traces(
        self,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 100
    ) -> List[Dict]:
        """导出追踪列表"""
        traces = []
        
        # 首先从内存加载（快速）
        with self._lock:
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
        
        # 如果内存中没有，尝试从数据库加载
        if not traces and self.use_database:
            traces = self._load_traces_from_db(start_time, end_time, limit)
        
        traces.sort(key=lambda t: t.get("start_time", ""), reverse=True)
        traces = traces[:limit]
        
        return traces

    def _load_traces_from_db(
        self,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 100
    ) -> List[Dict]:
        """从数据库加载追踪列表"""
        try:
            self._init_db()
            
            if self._db_session and self._Trace:
                session = self._db_session()
                try:
                    query = session.query(self._Trace)
                    
                    if start_time:
                        query = query.filter(self._Trace.start_time >= start_time)
                    if end_time:
                        query = query.filter(self._Trace.start_time <= end_time)
                    
                    db_traces = query.order_by(self._Trace.start_time.desc()).limit(limit).all()
                    
                    traces = []
                    for db_trace in db_traces:
                        trace_data = db_trace.to_dict()
                        trace_data["spans"] = []
                        
                        # 加载关联的 spans
                        db_spans = session.query(self._Span).filter(
                            self._Span.trace_id == db_trace.id
                        ).order_by(self._Span.start_time).all()
                        
                        for db_span in db_spans:
                            span_dict = db_span.to_dict()
                            trace_data["spans"].append(span_dict)
                        
                        traces.append(trace_data)
                    
                    return traces
                finally:
                    session.close()
        except Exception as e:
            pass
        
        return []

    def analyze_trace(self, trace_id: str) -> Dict[str, Any]:
        """分析追踪性能"""
        spans = self.get_trace(trace_id)
        
        # 如果内存中没有，尝试从数据库加载
        if not spans and self.use_database:
            spans = self._load_trace_from_db(trace_id)
        
        if not spans:
            return {}
        
        slowest = max(spans, key=lambda s: s.duration_ms or 0) if spans else None
        errors = [s for s in spans if s.status == SpanStatus.ERROR]
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
        db_stats = self._get_db_stats() if self.use_database else {}
        
        with self._lock:
            memory_stats = {
                "memory_total_traces": len(self._trace_spans),
                "memory_total_spans": len(self._spans)
            }
        
        return {
            "total_traces": db_stats.get("total_traces", 0) + memory_stats["memory_total_traces"],
            "total_spans": db_stats.get("total_spans", 0) + memory_stats["memory_total_spans"],
            "service_name": self.service_name,
            "use_database": self.use_database,
            "db_initialized": self._db_initialized,
            **memory_stats,
            **db_stats
        }

    def _get_db_stats(self) -> Dict[str, Any]:
        """从数据库获取统计"""
        try:
            self._init_db()
            
            if self._db_session and self._Trace and self._Span:
                session = self._db_session()
                try:
                    total_traces = session.query(self._Trace).count()
                    total_spans = session.query(self._Span).count()
                    return {
                        "db_total_traces": total_traces,
                        "db_total_spans": total_spans
                    }
                finally:
                    session.close()
        except Exception as e:
            pass
        
        return {}


_tracer: Optional[Tracer] = None


def get_tracer(service_name: str = "harness", use_database: bool = False) -> Tracer:
    """获取全局追踪器实例"""
    global _tracer
    if _tracer is None:
        _tracer = Tracer(service_name, use_database=use_database)
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
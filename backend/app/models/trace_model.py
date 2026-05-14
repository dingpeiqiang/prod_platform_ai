"""
追踪数据模型 - 用于持久化追踪数据到数据库
"""

from sqlalchemy import Column, String, DateTime, Float, Text, JSON, Enum
from sqlalchemy.ext.declarative import declarative_base
from datetime import datetime
from enum import Enum as PyEnum

Base = declarative_base()


class SpanStatus(PyEnum):
    OK = "ok"
    ERROR = "error"
    TIMEOUT = "timeout"


class Trace(Base):
    """追踪记录"""
    __tablename__ = "traces"
    
    id = Column(String(36), primary_key=True)  # trace_id
    service_name = Column(String(100), default="harness")
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime)
    total_duration_ms = Column(Float)
    span_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.now)
    
    def to_dict(self):
        return {
            "trace_id": self.id,
            "service_name": self.service_name,
            "start_time": self.start_time.isoformat() if self.start_time else None,
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "total_duration_ms": self.total_duration_ms,
            "span_count": self.span_count
        }


class Span(Base):
    """追踪 Span"""
    __tablename__ = "spans"
    
    id = Column(String(36), primary_key=True)  # span_id
    trace_id = Column(String(36), nullable=False)
    parent_span_id = Column(String(36))
    name = Column(String(200), nullable=False)
    component = Column(String(100), default="harness")
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime)
    duration_ms = Column(Float)
    status = Column(Enum(SpanStatus), default=SpanStatus.OK)
    tags = Column(JSON, default={})
    logs = Column(JSON, default=[])
    created_at = Column(DateTime, default=datetime.now)
    
    def to_dict(self):
        return {
            "span_id": self.id,
            "trace_id": self.trace_id,
            "parent_span_id": self.parent_span_id,
            "name": self.name,
            "component": self.component,
            "start_time": self.start_time.isoformat() if self.start_time else None,
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "duration_ms": self.duration_ms,
            "status": self.status.value if self.status else None,
            "tags": self.tags,
            "logs": self.logs
        }
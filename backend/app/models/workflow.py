from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text, ForeignKey
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship
from app.core.database import Base


class Workflow(Base):
    """工作流主表"""
    __tablename__ = "workflows"

    id = Column(Integer, primary_key=True, index=True)
    workflow_code = Column(String(100), unique=True, index=True, nullable=False)
    workflow_name = Column(String(200), nullable=False)
    description = Column(Text)
    category = Column(String(50), default='general')
    tags = Column(JSON, nullable=False, default=list)
    priority = Column(Integer, default=10)
    is_active = Column(Boolean, default=True)
    
    # 工作流配置
    workflow_data = Column(JSON, nullable=False, default=dict)  # 存储完整的工作流配置（节点、边等）
    version = Column(Integer, default=1)
    
    # 统计信息
    execution_count = Column(Integer, default=0)
    last_execution_at = Column(DateTime(timezone=True), nullable=True)
    last_execution_status = Column(String(20), nullable=True)  # success, failed, pending
    
    # 公共字段
    created_by = Column(String(100))
    updated_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 关联历史
    history = relationship("WorkflowHistory", back_populates="workflow", cascade="all, delete-orphan")
    executions = relationship("WorkflowExecution", back_populates="workflow", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "workflowCode": self.workflow_code,
            "workflowName": self.workflow_name,
            "description": self.description,
            "category": self.category,
            "tags": self.tags,
            "priority": self.priority,
            "isActive": self.is_active,
            "workflowData": self.workflow_data,
            "version": self.version,
            "executionCount": self.execution_count,
            "lastExecutionAt": self.last_execution_at.isoformat() if self.last_execution_at else None,
            "lastExecutionStatus": self.last_execution_status,
            "createdBy": self.created_by,
            "updatedBy": self.updated_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }


class WorkflowHistory(Base):
    """工作流版本历史表"""
    __tablename__ = "workflow_history"

    id = Column(Integer, primary_key=True, index=True)
    workflow_id = Column(Integer, ForeignKey("workflows.id"), nullable=False, index=True)
    workflow_code = Column(String(100), nullable=False, index=True)
    version = Column(Integer, nullable=False)
    
    # 快照数据
    workflow_name = Column(String(200), nullable=False)
    description = Column(Text)
    workflow_data = Column(JSON, nullable=False, default=dict)
    category = Column(String(50))
    tags = Column(JSON, nullable=False, default=list)
    priority = Column(Integer)
    is_active = Column(Boolean)
    
    # 变更记录
    change_note = Column(Text)
    created_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    
    workflow = relationship("Workflow", back_populates="history")

    def to_dict(self):
        return {
            "id": self.id,
            "workflowId": self.workflow_id,
            "workflowCode": self.workflow_code,
            "version": self.version,
            "workflowName": self.workflow_name,
            "description": self.description,
            "workflowData": self.workflow_data,
            "category": self.category,
            "tags": self.tags,
            "priority": self.priority,
            "isActive": self.is_active,
            "changeNote": self.change_note,
            "createdBy": self.created_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None
        }


class WorkflowExecution(Base):
    """工作流执行记录表"""
    __tablename__ = "workflow_executions"

    id = Column(Integer, primary_key=True, index=True)
    workflow_id = Column(Integer, ForeignKey("workflows.id"), nullable=False, index=True)
    workflow_code = Column(String(100), nullable=False, index=True)
    execution_id = Column(String(100), unique=True, index=True, nullable=False)
    
    # 执行信息
    status = Column(String(20), nullable=False, default='pending')  # pending, running, success, failed, cancelled
    start_time = Column(DateTime(timezone=True))
    end_time = Column(DateTime(timezone=True))
    duration_seconds = Column(Integer)
    
    # 输入输出
    input_data = Column(JSON, nullable=False, default=dict)
    output_data = Column(JSON, nullable=True)
    error_message = Column(Text, nullable=True)
    
    # 执行日志
    execution_logs = Column(JSON, nullable=False, default=list)
    
    # 元数据
    triggered_by = Column(String(100))
    trigger_type = Column(String(20), default='manual')  # manual, scheduled, api, event
    notes = Column(Text)
    
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    workflow = relationship("Workflow", back_populates="executions")

    def to_dict(self):
        return {
            "id": self.id,
            "workflowId": self.workflow_id,
            "workflowCode": self.workflow_code,
            "executionId": self.execution_id,
            "status": self.status,
            "startTime": self.start_time.isoformat() if self.start_time else None,
            "endTime": self.end_time.isoformat() if self.end_time else None,
            "durationSeconds": self.duration_seconds,
            "inputData": self.input_data,
            "outputData": self.output_data,
            "errorMessage": self.error_message,
            "executionLogs": self.execution_logs,
            "triggeredBy": self.triggered_by,
            "triggerType": self.trigger_type,
            "notes": self.notes,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

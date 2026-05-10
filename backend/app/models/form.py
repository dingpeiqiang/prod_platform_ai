from sqlalchemy import Column, Integer, String, Text, DateTime, JSON, Boolean
from sqlalchemy.sql import func
from app.core.database import Base


class FormTemplate(Base):
    __tablename__ = "form_templates"
    
    id = Column(Integer, primary_key=True, index=True)
    form_code = Column(String(100), unique=True, index=True, nullable=False)
    form_name = Column(String(200), nullable=False)
    schema = Column(JSON, nullable=False)
    version = Column(Integer, default=1)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    is_active = Column(Boolean, default=True)


class FormInstance(Base):
    __tablename__ = "form_instances"
    
    id = Column(Integer, primary_key=True, index=True)
    form_id = Column(String(50), unique=True, index=True, nullable=False)
    template_id = Column(Integer, nullable=False)
    data = Column(JSON, nullable=False)
    version = Column(Integer, default=1)
    status = Column(String(50), default="draft")
    user_id = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    submitted_at = Column(DateTime(timezone=True), nullable=True)


class FormHistory(Base):
    __tablename__ = "form_history"
    
    id = Column(Integer, primary_key=True, index=True)
    form_instance_id = Column(Integer, nullable=False)
    field_code = Column(String(100), nullable=False)
    field_value = Column(Text, nullable=False)
    user_id = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


# Ontology类已迁移到 app/models/ontology.py


class SystemLog(Base):
    __tablename__ = "system_logs"

    id = Column(Integer, primary_key=True, index=True)
    log_type = Column(String(50), nullable=False)
    module = Column(String(100), nullable=False)
    message = Column(Text, nullable=False)
    user_id = Column(String(100), nullable=True)
    log_metadata = Column(JSON, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


# ChatSession / ChatMessage 已迁移到 v2，见 app/models/chat_v2.py

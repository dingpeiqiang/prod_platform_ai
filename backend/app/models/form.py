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


class Ontology(Base):
    __tablename__ = "ontologies"
    
    id = Column(Integer, primary_key=True, index=True)
    ontology_code = Column(String(100), unique=True, index=True, nullable=False)
    ontology_name = Column(String(200), nullable=False)
    entities = Column(JSON, nullable=False)
    version = Column(Integer, default=1)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    is_active = Column(Boolean, default=True)


class SystemLog(Base):
    __tablename__ = "system_logs"

    id = Column(Integer, primary_key=True, index=True)
    log_type = Column(String(50), nullable=False)
    module = Column(String(100), nullable=False)
    message = Column(Text, nullable=False)
    user_id = Column(String(100), nullable=True)
    log_metadata = Column(JSON, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class ChatSession(Base):
    __tablename__ = "chat_sessions"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(String(100), unique=True, index=True, nullable=False)
    user_id = Column(String(100), nullable=True)
    title = Column(String(200), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())


class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(String(100), index=True, nullable=False)
    role = Column(String(20), nullable=False)
    content = Column(Text, nullable=False)
    intent_type = Column(String(50), nullable=True)
    form_code = Column(String(100), nullable=True)
    extracted_fields = Column(JSON, nullable=True)
    confidence = Column(String(10), nullable=True)
    reasoning = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

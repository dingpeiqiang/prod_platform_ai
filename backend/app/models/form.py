from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text, ForeignKey
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship
from app.core.database import Base


class Form(Base):
    __tablename__ = "forms"

    id = Column(Integer, primary_key=True, index=True)
    form_code = Column(String(100), unique=True, index=True, nullable=False)
    form_name = Column(String(200), nullable=False)
    description = Column(Text)
    category = Column(String(50), default="general")
    
    # 表单结构
    entities = Column(JSON, default=list)  # 实体列表
    layout = Column(JSON, default=dict)  # 布局配置
    validation_rules = Column(JSON, default=list)  # 验证规则
    
    # 关联
    ontology_code = Column(String(100))  # 关联的本体
    
    # 状态
    is_active = Column(Boolean, default=True)
    version = Column(Integer, default=1)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def to_dict(self):
        return {
            "formCode": self.form_code,
            "formName": self.form_name,
            "description": self.description,
            "category": self.category,
            "entities": self.entities,
            "layout": self.layout,
            "validationRules": self.validation_rules,
            "ontologyCode": self.ontology_code,
            "isActive": self.is_active,
            "version": self.version,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }


class FormTemplate(Base):
    __tablename__ = "form_templates"

    id = Column(Integer, primary_key=True, index=True)
    form_code = Column(String(100), index=True, nullable=False)
    version = Column(Integer, default=1)
    schema_data = Column(JSON, default=dict)
    is_active = Column(Boolean, default=True)
    created_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class FormInstance(Base):
    __tablename__ = "form_instances"

    id = Column(Integer, primary_key=True, index=True)
    template_id = Column(Integer, ForeignKey("form_templates.id"), index=True)
    user_id = Column(String(100), index=True)
    session_id = Column(String(100), index=True)
    data = Column(JSON, default=dict)
    status = Column(String(50), default="draft")  # draft, submitted, cancelled
    submitted_at = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())


class FormHistory(Base):
    __tablename__ = "form_history"

    id = Column(Integer, primary_key=True, index=True)
    form_instance_id = Column(Integer, ForeignKey("form_instances.id"), index=True)
    field_code = Column(String(100), index=True, nullable=False)
    field_value = Column(Text)
    user_id = Column(String(100), index=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

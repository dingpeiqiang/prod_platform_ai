from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text, ForeignKey
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship
from app.core.database import Base


class Prompt(Base):
    """提示词主表"""
    __tablename__ = "prompts"

    id = Column(Integer, primary_key=True, index=True)
    code = Column(String(100), unique=True, index=True, nullable=False)  # 提示词编码
    name = Column(String(200), nullable=False)  # 提示词名称
    description = Column(Text)  # 描述
    category = Column(String(50), default="general")  # 分类：general, form, tool, qa等
    content = Column(Text, nullable=False)  # Markdown格式的提示词内容
    variables = Column(JSON, default=list)  # 模板变量定义：[{"name": "", "description": "", "default": ""}]
    tools = Column(JSON, default=list)  # 可用工具：[{"code": "", "name": "", "description": ""}]
    is_template = Column(Boolean, default=False)  # 是否为模板
    version = Column(Integer, default=1)
    is_active = Column(Boolean, default=True)
    created_by = Column(String(100))
    updated_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    # 关联版本历史
    versions = relationship("PromptVersion", back_populates="prompt", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "code": self.code,
            "name": self.name,
            "description": self.description,
            "category": self.category,
            "content": self.content,
            "variables": self.variables,
            "tools": self.tools,
            "is_template": self.is_template,
            "version": self.version,
            "is_active": self.is_active,
            "createdBy": self.created_by,
            "updatedBy": self.updated_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }


class PromptVersion(Base):
    """提示词版本历史"""
    __tablename__ = "prompt_versions"

    id = Column(Integer, primary_key=True, index=True)
    prompt_id = Column(Integer, ForeignKey("prompts.id"), nullable=False)
    version = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    variables = Column(JSON, default=list)
    tools = Column(JSON, default=list)
    change_note = Column(Text)  # 变更说明
    created_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    prompt = relationship("Prompt", back_populates="versions")

    def to_dict(self):
        return {
            "id": self.id,
            "promptId": self.prompt_id,
            "version": self.version,
            "content": self.content,
            "variables": self.variables,
            "tools": self.tools,
            "changeNote": self.change_note,
            "createdBy": self.created_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None
        }


class PromptTemplate(Base):
    """预设模板库"""
    __tablename__ = "prompt_templates"

    id = Column(Integer, primary_key=True, index=True)
    code = Column(String(100), unique=True, index=True, nullable=False)
    name = Column(String(200), nullable=False)
    description = Column(Text)
    category = Column(String(50), default="general")
    content = Column(Text, nullable=False)  # 模板内容
    variables = Column(JSON, default=list)  # 变量定义
    tools = Column(JSON, default=list)  # 推荐工具
    tags = Column(JSON, default=list)  # 标签
    is_builtin = Column(Boolean, default=False)  # 是否内置
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    def to_dict(self):
        return {
            "id": self.id,
            "code": self.code,
            "name": self.name,
            "description": self.description,
            "category": self.category,
            "content": self.content,
            "variables": self.variables,
            "tools": self.tools,
            "tags": self.tags,
            "isBuiltin": self.is_builtin,
            "isActive": self.is_active,
            "createdAt": self.created_at.isoformat() if self.created_at else None
        }

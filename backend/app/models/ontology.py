from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text
from sqlalchemy.sql import func
from app.core.database import Base


class Ontology(Base):
    """
    本体定义 - AI 原生架构的权威数据源
    
    设计理念：
    - 本体本身就是 Schema/Class，定义数据结构和约束
    - 不再需要引用“表单”，本体是独立且完整的
    - ontology_code 和 ontology_name 是唯一标识
    """
    __tablename__ = "ontologies"

    id = Column(Integer, primary_key=True, index=True)
    ontology_code = Column(String(100), unique=True, index=True, nullable=False)
    ontology_name = Column(String(200), nullable=False)
    category = Column(String(100), default="general")
    entities = Column(JSON, nullable=False)  # 实体定义（字段结构）
    description = Column(Text)
    version = Column(Integer, default=1)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def to_dict(self):
        """转换为字典格式（API 响应）"""
        return {
            "ontologyCode": self.ontology_code,
            "ontologyName": self.ontology_name,
            "category": self.category,
            "description": self.description,
            "entities": self.entities,
            "version": self.version,
            "isActive": self.is_active,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

    def to_ontology_format(self):
        """转换为本体格式（用于推荐引擎等）"""
        return {
            "ontologyCode": self.ontology_code,
            "ontologyName": self.ontology_name,
            "description": self.description,
            "entities": self.entities
        }
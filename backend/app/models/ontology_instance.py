"""
本体实例模型（Ontology Instance）

这是 AI 原生架构的核心数据模型：
- Ontology = Schema/Class（本体定义）
- OntologyInstance = Object/Record（本体实例）

取代了旧的 FormInstance 概念，符合"表单实例即本体实例"的设计理念。
"""
from sqlalchemy import Column, Integer, String, JSON, DateTime, ForeignKey, Text
from sqlalchemy.sql import func
from app.core.database import Base


class OntologyInstance(Base):
    """
    本体实例 - 存储符合本体约束的实际业务数据
    
    设计理念：
    - 本体(Ontology)定义数据结构和约束（类似 Class）
    - 本体实例(OntologyInstance)是具体的数据记录（类似 Object）
    - 不再需要"表单(Form)"这个中间层
    """
    __tablename__ = "ontology_instances"

    id = Column(Integer, primary_key=True, index=True)
    ontology_code = Column(String(100), index=True, nullable=False)  # 本体编码（原 form_code）
    user_id = Column(String(100), index=True)
    session_id = Column(String(100), index=True)
    data = Column(JSON, default=dict)  # 实例数据（符合本体约束的字段值）
    status = Column(String(50), default="draft")  # draft, submitted, cancelled
    submitted_at = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    def to_dict(self):
        """转换为字典格式（兼容旧 API）"""
        return {
            "id": self.id,
            "ontologyCode": self.ontology_code,
            "formCode": self.ontology_code,  # 兼容旧代码
            "userId": self.user_id,
            "sessionId": self.session_id,
            "data": self.data,
            "status": self.status,
            "submittedAt": self.submitted_at.isoformat() if self.submitted_at else None,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }


class OntologyInstanceHistory(Base):
    """
    本体实例历史 - 记录每个字段的变更历史
    
    用于：
    - 推荐系统（基于历史数据推荐）
    - 审计追踪
    - 数据分析
    """
    __tablename__ = "ontology_instance_history"

    id = Column(Integer, primary_key=True, index=True)
    form_instance_id = Column(Integer, ForeignKey("ontology_instances.id"), index=True)  # 保持字段名兼容
    field_code = Column(String(100), index=True, nullable=False)
    field_value = Column(Text)
    user_id = Column(String(100), index=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    
    def to_dict(self):
        return {
            "id": self.id,
            "formInstanceId": self.form_instance_id,
            "fieldCode": self.field_code,
            "fieldValue": self.field_value,
            "userId": self.user_id,
            "createdAt": self.created_at.isoformat() if self.created_at else None
        }



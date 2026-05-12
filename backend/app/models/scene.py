from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text, ForeignKey
from sqlalchemy.sql import func
from sqlalchemy.orm import relationship
from app.core.database import Base


class Scene(Base):
    """场景主表"""
    __tablename__ = "scenes"

    id = Column(Integer, primary_key=True, index=True)
    scene_code = Column(String(100), unique=True, index=True, nullable=False)
    scene_name = Column(String(200), nullable=False)
    description = Column(Text)
    keywords = Column(JSON, nullable=False, default=list)
    priority = Column(Integer, default=10)
    is_active = Column(Boolean, default=True)
    
    # 业务字段
    intent_type = Column(String(50))
    prompt_code = Column(String(100), index=True)  # 提示词编码
    action_type = Column(String(50))
    required_tools = Column(JSON, nullable=False, default=list)
    available_tools = Column(JSON, nullable=False, default=list)
    pre_action_steps = Column(JSON, nullable=False, default=list)
    post_action_steps = Column(JSON, nullable=False, default=list)
    
    # 树形结构字段
    type = Column(String(20), nullable=False, default='scene', index=True)  # center/business/scene
    parent_id = Column(Integer, ForeignKey('scenes.id'), nullable=True, index=True)
    
    # 通用配置字段 - 用于存储中心域和业务域的特定信息
    config = Column(JSON, nullable=False, default=dict)  # 灵活的配置字段
    
    # 公共字段（与其他模型保持一致）
    version = Column(Integer, default=1)
    created_by = Column(String(100))
    updated_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 自关联关系
    parent = relationship('Scene', remote_side=[id], backref='children')
    
    # 关联版本历史
    history = relationship("SceneHistory", back_populates="scene", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "sceneCode": self.scene_code,
            "sceneName": self.scene_name,
            "description": self.description,
            "keywords": self.keywords,
            "priority": self.priority,
            "isActive": self.is_active,
            "intentType": self.intent_type,
            "promptCode": self.prompt_code,
            "actionType": self.action_type,
            "requiredTools": self.required_tools,
            "availableTools": self.available_tools,
            "preActionSteps": self.pre_action_steps,
            "postActionSteps": self.post_action_steps,
            "type": self.type,
            "parentId": self.parent_id,
            "config": self.config,
            "version": self.version,
            "createdBy": self.created_by,
            "updatedBy": self.updated_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

    def to_tree_node(self):
        """转换为树节点格式"""
        return {
            "id": self.id,
            "sceneCode": self.scene_code,
            "sceneName": self.scene_name,
            "description": self.description,
            "keywords": self.keywords,
            "type": self.type,
            "priority": self.priority,
            "isActive": self.is_active,
            "promptCode": self.prompt_code,
            "config": self.config,
            "children": []
        }


class SceneHistory(Base):
    """场景版本历史表"""
    __tablename__ = "scene_history"

    id = Column(Integer, primary_key=True, index=True)
    scene_id = Column(Integer, ForeignKey("scenes.id"), nullable=False, index=True)
    scene_code = Column(String(100), nullable=False, index=True)  # 冗余字段，方便查询
    version = Column(Integer, nullable=False)
    
    # 场景数据快照
    scene_name = Column(String(200), nullable=False)
    description = Column(Text)
    keywords = Column(JSON, nullable=False, default=list)
    priority = Column(Integer, default=10)
    is_active = Column(Boolean, default=True)
    intent_type = Column(String(50))
    prompt_code = Column(String(100))  # 提示词编码快照
    action_type = Column(String(50))
    required_tools = Column(JSON, nullable=False, default=list)
    available_tools = Column(JSON, nullable=False, default=list)
    pre_action_steps = Column(JSON, nullable=False, default=list)
    post_action_steps = Column(JSON, nullable=False, default=list)
    type = Column(String(20), nullable=False, default='scene')
    parent_id = Column(Integer, nullable=True)
    config = Column(JSON, nullable=False, default=dict)  # 配置信息快照
    
    # 变更记录
    change_note = Column(Text)  # 变更说明
    created_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    scene = relationship("Scene", back_populates="history")

    def to_dict(self):
        return {
            "id": self.id,
            "sceneId": self.scene_id,
            "sceneCode": self.scene_code,
            "version": self.version,
            "sceneName": self.scene_name,
            "description": self.description,
            "keywords": self.keywords,
            "priority": self.priority,
            "isActive": self.is_active,
            "intentType": self.intent_type,
            "promptCode": self.prompt_code,
            "actionType": self.action_type,
            "requiredTools": self.required_tools,
            "availableTools": self.available_tools,
            "preActionSteps": self.pre_action_steps,
            "postActionSteps": self.post_action_steps,
            "type": self.type,
            "parentId": self.parent_id,
            "config": self.config,
            "changeNote": self.change_note,
            "createdBy": self.created_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None
        }

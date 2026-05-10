from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text
from sqlalchemy.sql import func
from app.core.database import Base
from app.models.scene_enums import IntentType, ActionType


class Scene(Base):
    __tablename__ = "scenes"

    id = Column(Integer, primary_key=True, index=True)
    scene_code = Column(String(100), unique=True, index=True, nullable=False)
    scene_name = Column(String(200), nullable=False)
    description = Column(Text)
    keywords = Column(JSON, nullable=False, default=list)
    priority = Column(Integer, default=10)
    is_active = Column(Boolean, default=True)
    intent_type = Column(String(50))
    form_code = Column(String(100))
    action_type = Column(String(50), default="form_generation")
    action_prompt_file = Column(String(255))
    required_tools = Column(JSON, default=list)
    available_tools = Column(JSON, default=list)
    pre_action_steps = Column(JSON, default=list)
    post_action_steps = Column(JSON, default=list)
    version = Column(Integer, default=1)
    created_by = Column(String(100))
    updated_by = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def to_dict(self):
        return {
            "sceneCode": self.scene_code,
            "sceneName": self.scene_name,
            "description": self.description,
            "keywords": self.keywords,
            "priority": self.priority,
            "isActive": self.is_active,
            "intentType": self.intent_type,
            "formCode": self.form_code,
            "actionType": self.action_type,
            "actionPromptFile": self.action_prompt_file,
            "requiredTools": self.required_tools,
            "availableTools": self.available_tools,
            "preActionSteps": self.pre_action_steps,
            "postActionSteps": self.post_action_steps,
            "version": self.version,
            "createdBy": self.created_by,
            "updatedBy": self.updated_by,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

    def to_scene_mapping_format(self):
        """转换为 scene_mapping.json 的格式"""
        return {
            "sceneCode": self.scene_code,
            "sceneName": self.scene_name,
            "description": self.description,
            "keywords": self.keywords,
            "priority": self.priority,
            "isActive": self.is_active,
            "intentType": self.intent_type,
            "formCode": self.form_code,
            "actionType": self.action_type,
            "actionPrompt": self.action_prompt_file,
            "requiredTools": self.required_tools,
            "preActionSteps": self.pre_action_steps,
            "postActionSteps": self.post_action_steps
        }

from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text
from sqlalchemy.sql import func
from app.core.database import Base


class Tool(Base):
    __tablename__ = "tools"

    id = Column(Integer, primary_key=True, index=True)
    tool_code = Column(String(100), unique=True, index=True, nullable=False)
    tool_name = Column(String(200), nullable=False)
    description = Column(Text)
    category = Column(String(50), default="general")  # general, api, database, file, etc.
    
    # 工具配置
    tool_type = Column(String(50), default="custom")  # custom, http, python, mcp
    config = Column(JSON, default=dict)  # 工具的配置信息
    
    # 参数定义
    parameters = Column(JSON, default=list)  # 参数定义列表
    return_schema = Column(JSON, default=dict)  # 返回值schema
    
    # 执行相关
    endpoint = Column(String(500))  # 如果是API工具，endpoint
    handler = Column(String(200))  # 处理函数名
    is_async = Column(Boolean, default=True)
    
    # 状态
    is_active = Column(Boolean, default=True)
    version = Column(Integer, default=1)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def to_dict(self):
        return {
            "toolCode": self.tool_code,
            "toolName": self.tool_name,
            "description": self.description,
            "category": self.category,
            "toolType": self.tool_type,
            "config": self.config,
            "parameters": self.parameters,
            "returnSchema": self.return_schema,
            "endpoint": self.endpoint,
            "handler": self.handler,
            "isAsync": self.is_async,
            "isActive": self.is_active,
            "version": self.version,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

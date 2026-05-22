"""
LLM 用户配置模型
存储用户的模型配置信息
"""
from sqlalchemy import Column, Integer, String, Boolean, Float, Text, DateTime, func
from app.core.database import Base


class LLMUserConfig(Base):
    """用户 LLM 配置表"""
    __tablename__ = "llm_user_configs"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    
    # 用户标识（可以是 user_id、session_id 或 device_id）
    user_identifier = Column(String(100), nullable=False, index=True, comment="用户标识")
    
    # LLM 配置
    provider = Column(String(50), nullable=False, default="custom", comment="Provider 类型")
    model = Column(String(100), nullable=False, comment="模型名称")
    api_key = Column(Text, nullable=True, comment="API Key（加密存储）")
    base_url = Column(Text, nullable=True, comment="Base URL")
    auth_type = Column(String(20), nullable=False, default="bearer", comment="认证类型: bearer, token, api_key, custom")
    auth_header = Column(String(50), nullable=True, comment="自定义认证头名称")
    api_format = Column(String(50), nullable=False, default="openai", comment="API 格式: openai, anthropic, gemini")
    is_full_url = Column(Boolean, nullable=False, default=False, comment="是否使用完整 URL")
    
    # 高级配置
    temperature = Column(Float, nullable=False, default=0.3, comment="温度参数")
    max_tokens = Column(Integer, nullable=False, default=2048, comment="最大 token 数")
    thinking = Column(Boolean, nullable=False, default=False, comment="是否启用思考模式")
    max_input_tokens = Column(Integer, nullable=True, default=180000, comment="最大输入 token 数")
    
    # 元数据
    is_active = Column(Boolean, nullable=False, default=True, comment="是否为当前激活配置")
    config_name = Column(String(100), nullable=True, comment="配置名称（可选）")
    
    # 时间戳
    created_at = Column(DateTime(timezone=True), server_default=func.now(), comment="创建时间")
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), comment="更新时间")
    last_used_at = Column(DateTime(timezone=True), nullable=True, comment="最后使用时间")
    
    def to_dict(self):
        """转换为字典（不包含敏感信息）"""
        return {
            "id": self.id,
            "user_identifier": self.user_identifier,
            "provider": self.provider,
            "model": self.model,
            # api_key 不返回，需要时单独获取
            "base_url": self.base_url,
            "auth_type": self.auth_type,
            "auth_header": self.auth_header,
            "api_format": self.api_format,
            "is_full_url": self.is_full_url,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "thinking": self.thinking,
            "max_input_tokens": self.max_input_tokens,
            "is_active": self.is_active,
            "config_name": self.config_name,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
            "last_used_at": self.last_used_at.isoformat() if self.last_used_at else None,
        }
    
    def to_full_dict(self):
        """转换为完整字典（包含敏感信息）"""
        data = self.to_dict()
        data["api_key"] = self.api_key
        return data

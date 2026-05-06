"""
chat_v2 Models - 通用聊天架构
与业务完全解耦，消息扩展通过 metadata 表插入。
"""
from sqlalchemy import Column, Integer, String, Text, DateTime, JSON, ForeignKey, UniqueConstraint
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.core.database import Base


class ChatSessionV2(Base):
    """通用会话表"""
    __tablename__ = "chat_sessions_v2"

    id          = Column(Integer, primary_key=True, index=True)
    session_id  = Column(String(64), unique=True, index=True, nullable=False)
    user_id     = Column(String(100), index=True, nullable=True)
    title       = Column(String(200), nullable=True)
    context_tags = Column(JSON, nullable=True)   # ["sales", "urgent"]
    session_meta = Column(JSON, nullable=True)   # {source: "web", channel: "api"}
    status       = Column(String(20), default='active')   # active / archived
    created_at  = Column(DateTime(timezone=True), server_default=func.now())
    updated_at  = Column(DateTime(timezone=True), onupdate=func.now())

    messages = relationship(
        "ChatMessageV2",
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="ChatMessageV2.created_at.asc()"
    )


class ChatMessageV2(Base):
    """通用消息表 - 核心字段，与业务解耦"""
    __tablename__ = "chat_messages_v2"

    id          = Column(Integer, primary_key=True, index=True)
    message_id  = Column(String(64), unique=True, index=True, nullable=False)  # 外部 UUID
    session_id  = Column(String(64), ForeignKey("chat_sessions_v2.session_id", ondelete="CASCADE"), index=True, nullable=False)
    role        = Column(String(20), nullable=False)   # user / assistant / system
    content     = Column(Text, nullable=False)
    content_type = Column(String(20), default='text')  # text / markdown / json / form
    parent_id   = Column(String(64), nullable=True)    # 父消息 ID（多轮树状）
    created_at  = Column(DateTime(timezone=True), server_default=func.now())

    session  = relationship("ChatSessionV2", back_populates="messages")
    msg_metadata = relationship(
        "ChatMessageMetadata",
        back_populates="message",
        cascade="all, delete-orphan"
    )

    def get_meta(self, key: str, default=None):
        """读取某条 metadata 值"""
        for m in self.msg_metadata:
            if m.key == key:
                return m.value
        return default

    def to_dict(self, include_metadata: bool = True) -> dict:
        """序列化，带 metadata"""
        result = {
            "message_id":   self.message_id,
            "session_id":   self.session_id,
            "role":         self.role,
            "content":      self.content,
            "content_type": self.content_type,
            "parent_id":    self.parent_id,
            "created_at":   self.created_at.isoformat() if self.created_at else None,
        }
        if include_metadata:
            result["metadata"] = {m.key: m.value for m in self.msg_metadata}
        return result


class ChatMessageMetadata(Base):
    """消息 KV 扩展表 - 插入式业务字段"""
    __tablename__ = "chat_message_metadata"

    id          = Column(Integer, primary_key=True, index=True)
    message_id  = Column(String(64), ForeignKey("chat_messages_v2.message_id", ondelete="CASCADE"), index=True, nullable=False)
    key         = Column(String(100), index=True, nullable=False)
    value       = Column(Text, nullable=True)
    created_at  = Column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (
        UniqueConstraint('message_id', 'key', name='uq_message_key'),
    )

    message = relationship("ChatMessageV2", back_populates="msg_metadata")
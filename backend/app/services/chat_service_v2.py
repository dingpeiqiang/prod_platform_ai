"""
ChatServiceV2 - 通用聊天服务

与业务完全解耦，通过 metadata 表支持任意业务扩展。
所有业务特定字段（如 intent_type / form_code / extracted_fields）
均作为 metadata.key 插入，不污染核心消息表结构。
"""
import uuid
import json
import logging
from datetime import datetime
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import or_, func

from app.models.chat_v2 import ChatSessionV2, ChatMessageV2, ChatMessageMetadata

logger = logging.getLogger("chat_service_v2")


class ChatServiceV2:
    """通用聊天服务（v2 架构）"""

    # ── 会话操作 ──────────────────────────────────────────────

    @classmethod
    def create_session(
        cls,
        user_id: str = None,
        title: str = None,
        context_tags: List[str] = None,
        metadata: Dict[str, Any] = None,
        db: Session = None
    ) -> Optional[Dict[str, Any]]:
        """创建新会话"""
        if not db:
            return None
        try:
            session_id = str(uuid.uuid4())
            session = ChatSessionV2(
                session_id=session_id,
                user_id=user_id,
                title=title or f"新会话 {datetime.now().strftime('%Y-%m-%d %H:%M')}",
                context_tags=context_tags,
                metadata=metadata or {}
            )
            db.add(session)
            db.commit()
            db.refresh(session)
            logger.info("[ChatServiceV2] 创建会话 session_id=%s user_id=%s", session_id, user_id)
            return cls._session_to_dict(session)
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 创建会话失败 user_id=%s: %s", user_id, e)
            return None

    @classmethod
    def get_sessions(
        cls,
        user_id: str = None,
        status: str = 'active',
        limit: int = 50,
        db: Session = None
    ) -> List[Dict[str, Any]]:
        """查询会话列表，按 updated_at 倒序"""
        if not db:
            return []
        try:
            query = db.query(ChatSessionV2)
            if user_id:
                query = query.filter(ChatSessionV2.user_id == user_id)
            if status:
                query = query.filter(ChatSessionV2.status == status)
            sessions = query.order_by(ChatSessionV2.updated_at.desc()).limit(limit).all()
            return [cls._session_to_dict(s) for s in sessions]
        except Exception as e:
            logger.exception("[ChatServiceV2] 查询会话列表失败: %s", e)
            return []

    @classmethod
    def get_session(cls, session_id: str, db: Session = None) -> Optional[Dict[str, Any]]:
        """查询单个会话"""
        if not db:
            return None
        try:
            s = db.query(ChatSessionV2).filter(ChatSessionV2.session_id == session_id).first()
            return cls._session_to_dict(s) if s else None
        except Exception as e:
            logger.exception("[ChatServiceV2] 查询会话失败: %s", e)
            return None

    @classmethod
    def update_session(
        cls,
        session_id: str,
        title: str = None,
        context_tags: List[str] = None,
        metadata: Dict[str, Any] = None,
        status: str = None,
        db: Session = None
    ) -> bool:
        """更新会话"""
        if not db:
            return False
        try:
            session = db.query(ChatSessionV2).filter(ChatSessionV2.session_id == session_id).first()
            if not session:
                return False
            if title is not None:
                session.title = title
            if context_tags is not None:
                session.context_tags = context_tags
            if metadata is not None:
                session.metadata = metadata
            if status is not None:
                session.status = status
            db.commit()
            logger.info("[ChatServiceV2] 更新会话 session_id=%s", session_id)
            return True
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 更新会话失败: %s", e)
            return False

    @classmethod
    def delete_session(cls, session_id: str, db: Session = None) -> bool:
        """删除会话（级联删除 messages + metadata）"""
        if not db:
            return False
        try:
            session = db.query(ChatSessionV2).filter(ChatSessionV2.session_id == session_id).first()
            if session:
                db.delete(session)
                db.commit()
                logger.info("[ChatServiceV2] 删除会话 session_id=%s", session_id)
                return True
            return False
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 删除会话失败: %s", e)
            return False

    # ── 消息操作 ──────────────────────────────────────────────

    @classmethod
    def save_message(
        cls,
        session_id: str,
        role: str,
        content: str,
        content_type: str = 'text',
        metadata: Dict[str, Any] = None,   # ← 所有业务扩展全走这里
        parent_id: str = None,
        user_id: str = None,
        db: Session = None
    ) -> Optional[Dict[str, Any]]:
        """
        保存消息 + metadata。
        metadata 示例（表单业务）：
          {
            "intent_type": "form",
            "form_code": "sales_order",
            "extracted_fields": {"customer": "张三", "amount": 1000},
            "confidence": "0.95",
            "reasoning": "提取到客户名和金额...",
            "model": "minimax-4",
            "latency_ms": 1234
          }
        """
        if not db:
            return None
        try:
            # 确保会话存在（不存在则自动创建）
            session = db.query(ChatSessionV2).filter(ChatSessionV2.session_id == session_id).first()
            if not session:
                session = ChatSessionV2(
                    session_id=session_id,
                    user_id=user_id,
                    title=f"会话 {datetime.now().strftime('%Y-%m-%d %H:%M')}"
                )
                db.add(session)
                logger.debug("[ChatServiceV2] 自动创建会话 session_id=%s", session_id)

            message_id = str(uuid.uuid4())
            message = ChatMessageV2(
                message_id=message_id,
                session_id=session_id,
                role=role,
                content=content,
                content_type=content_type,
                parent_id=parent_id
            )
            db.add(message)

            # 批量写入 metadata（如果有）
            if metadata:
                for key, value in metadata.items():
                    meta = ChatMessageMetadata(
                        message_id=message_id,
                        key=key,
                        value=json.dumps(value) if isinstance(value, (dict, list)) else str(value) if value is not None else None
                    )
                    db.add(meta)

            # 更新会话 updated_at
            session.updated_at = func.now()

            db.commit()
            db.refresh(message)
            logger.debug("[ChatServiceV2] 保存消息 session_id=%s msg_id=%s role=%s",
                         session_id, message_id, role)
            return {"message_id": message_id, "session_id": session_id}
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 保存消息失败: %s", e)
            return None

    @classmethod
    def get_messages(
        cls,
        session_id: str,
        limit: int = 200,
        before_ts: datetime = None,  # 分页游标
        include_metadata: bool = True,
        db: Session = None
    ) -> List[Dict[str, Any]]:
        """获取会话的所有消息（含 metadata）"""
        if not db:
            return []
        try:
            query = db.query(ChatMessageV2).filter(ChatMessageV2.session_id == session_id)
            if before_ts:
                query = query.filter(ChatMessageV2.created_at < before_ts)
            messages = query.order_by(ChatMessageV2.created_at.asc()).limit(limit).all()

            if include_metadata:
                # 批量加载 metadata（避免 N+1）
                msg_ids = [m.message_id for m in messages]
                meta_map: Dict[str, List[ChatMessageMetadata]] = {}
                if msg_ids:
                    metas = db.query(ChatMessageMetadata).filter(
                        ChatMessageMetadata.message_id.in_(msg_ids)
                    ).all()
                    for m in metas:
                        meta_map.setdefault(m.message_id, []).append(m)

                result = []
                for msg in messages:
                    d = cls._message_to_dict(msg, False)
                    d["metadata"] = {
                        m.key: m.value for m in meta_map.get(msg.message_id, [])
                    }
                    result.append(d)
                return result
            else:
                return [cls._message_to_dict(m, False) for m in messages]
        except Exception as e:
            logger.exception("[ChatServiceV2] 获取消息失败: %s", e)
            return []

    @classmethod
    def get_message(cls, message_id: str, db: Session = None) -> Optional[Dict[str, Any]]:
        """获取单条消息详情（含 metadata）"""
        if not db:
            return None
        try:
            msg = db.query(ChatMessageV2).options(
                joinedload(ChatMessageV2.metadata)
            ).filter(ChatMessageV2.message_id == message_id).first()
            if not msg:
                return None
            return cls._message_to_dict(msg, True)
        except Exception as e:
            logger.exception("[ChatServiceV2] 获取消息详情失败: %s", e)
            return None

    @classmethod
    def delete_message(cls, message_id: str, db: Session = None) -> bool:
        """删除单条消息（metadata 随 CASCADE 自动删除）"""
        if not db:
            return False
        try:
            msg = db.query(ChatMessageV2).filter(ChatMessageV2.message_id == message_id).first()
            if msg:
                db.delete(msg)
                db.commit()
                return True
            return False
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 删除消息失败: %s", e)
            return False

    # ── 搜索 ─────────────────────────────────────────────────

    @classmethod
    def search_messages(
        cls,
        query_text: str,
        user_id: str = None,
        session_id: str = None,
        limit: int = 20,
        db: Session = None
    ) -> List[Dict[str, Any]]:
        """全文搜索消息内容"""
        if not db:
            return []
        try:
            q = db.query(ChatMessageV2).options(
                joinedload(ChatMessageV2.metadata)
            )
            q = q.filter(ChatMessageV2.content.ilike(f"%{query_text}%"))
            if session_id:
                q = q.filter(ChatMessageV2.session_id == session_id)
            if user_id:
                q = q.join(ChatSessionV2).filter(ChatSessionV2.user_id == user_id)
            messages = q.order_by(ChatMessageV2.created_at.desc()).limit(limit).all()
            return [cls._message_to_dict(m, True) for m in messages]
        except Exception as e:
            logger.exception("[ChatServiceV2] 搜索消息失败: %s", e)
            return []

    # ── 统计 ─────────────────────────────────────────────────

    @classmethod
    def get_session_stats(cls, session_id: str, db: Session = None) -> Dict[str, Any]:
        """会话统计：消息数 / 用户消息数 / AI消息数"""
        if not db:
            return {}
        try:
            total = db.query(ChatMessageV2).filter(ChatMessageV2.session_id == session_id).count()
            user_msgs = db.query(ChatMessageV2).filter(
                ChatMessageV2.session_id == session_id,
                ChatMessageV2.role == 'user'
            ).count()
            ai_msgs = db.query(ChatMessageV2).filter(
                ChatMessageV2.session_id == session_id,
                ChatMessageV2.role == 'assistant'
            ).count()
            return {
                "total": total,
                "user_messages": user_msgs,
                "assistant_messages": ai_msgs
            }
        except Exception as e:
            logger.exception("[ChatServiceV2] 获取统计失败: %s", e)
            return {}

    # ── 工具 ─────────────────────────────────────────────────

    @classmethod
    def _session_to_dict(cls, s: ChatSessionV2) -> Dict[str, Any]:
        return {
            "session_id":   s.session_id,
            "user_id":      s.user_id,
            "title":        s.title,
            "context_tags": s.context_tags or [],
            "metadata":     s.metadata or {},
            "status":       s.status,
            "created_at":   s.created_at.isoformat() if s.created_at else None,
            "updated_at":   s.updated_at.isoformat() if s.updated_at else None,
        }

    @classmethod
    def _message_to_dict(cls, m: ChatMessageV2, include_metadata: bool = True) -> Dict[str, Any]:
        result = {
            "message_id":   m.message_id,
            "session_id":   m.session_id,
            "role":         m.role,
            "content":      m.content,
            "content_type": m.content_type,
            "parent_id":    m.parent_id,
            "created_at":   m.created_at.isoformat() if m.created_at else None,
        }
        if include_metadata and m.metadata:
            result["metadata"] = {x.key: x.value for x in m.metadata}
        elif include_metadata:
            result["metadata"] = {}
        return result
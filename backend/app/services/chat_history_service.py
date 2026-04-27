import uuid
import logging
from typing import List, Dict, Any, Optional
from datetime import datetime
from sqlalchemy.orm import Session
from app.models.form import ChatSession, ChatMessage

logger = logging.getLogger("chat_history_service")


class ChatHistoryService:

    @classmethod
    def create_session(cls, user_id: str = None, title: str = None, db: Session = None) -> Optional[Dict[str, Any]]:
        if not db:
            return None

        try:
            session_id = str(uuid.uuid4())
            session = ChatSession(
                session_id=session_id,
                user_id=user_id,
                title=title or f"聊天 {datetime.now().strftime('%Y-%m-%d %H:%M')}"
            )
            db.add(session)
            db.commit()
            db.refresh(session)
            logger.info("[ChatHistoryService] 创建会话成功 session_id=%s user_id=%s", session_id, user_id)

            return {
                "success": True,
                "session_id": session.session_id,
                "title": session.title,
                "created_at": session.created_at.isoformat() if session.created_at else None
            }
        except Exception as e:
            db.rollback()
            logger.exception("[ChatHistoryService] 创建会话失败 user_id=%s: %s", user_id, e)
            return {"success": False, "error": str(e)}

    @classmethod
    def save_message(
        cls,
        session_id: str,
        role: str,
        content: str,
        intent_type: str = None,
        form_code: str = None,
        extracted_fields: Dict = None,
        confidence: str = None,
        reasoning: str = None,
        user_id: str = None,
        db: Session = None
    ) -> Optional[Dict[str, Any]]:
        if not db:
            return None

        try:
            session = db.query(ChatSession).filter(ChatSession.session_id == session_id).first()
            if not session:
                session = ChatSession(
                    session_id=session_id,
                    user_id=user_id,
                    title=f"聊天 {datetime.now().strftime('%Y-%m-%d %H:%M')}"
                )
                db.add(session)
                logger.debug("[ChatHistoryService] 自动创建会话 session_id=%s", session_id)

            message = ChatMessage(
                session_id=session_id,
                role=role,
                content=content,
                intent_type=intent_type,
                form_code=form_code,
                extracted_fields=extracted_fields,
                confidence=confidence,
                reasoning=reasoning
            )
            db.add(message)
            db.commit()
            db.refresh(message)
            logger.debug("[ChatHistoryService] 保存消息 session_id=%s role=%s msg_id=%s",
                         session_id, role, message.id)

            return {
                "success": True,
                "message_id": message.id,
                "session_id": session_id
            }
        except Exception as e:
            db.rollback()
            logger.exception("[ChatHistoryService] 保存消息失败 session_id=%s: %s", session_id, e)
            return {"success": False, "error": str(e)}

    @classmethod
    def get_sessions(cls, user_id: str = None, limit: int = 50, db: Session = None) -> List[Dict[str, Any]]:
        if not db:
            return []

        try:
            query = db.query(ChatSession)
            if user_id:
                query = query.filter(ChatSession.user_id == user_id)

            sessions = query.order_by(ChatSession.updated_at.desc()).limit(limit).all()

            return [
                {
                    "session_id": s.session_id,
                    "title": s.title,
                    "user_id": s.user_id,
                    "created_at": s.created_at.isoformat() if s.created_at else None,
                    "updated_at": s.updated_at.isoformat() if s.updated_at else None
                }
                for s in sessions
            ]
        except Exception as e:
            logger.exception("[ChatHistoryService] 查询会话列表失败 user_id=%s: %s", user_id, e)
            return []

    @classmethod
    def get_messages(cls, session_id: str, db: Session = None) -> List[Dict[str, Any]]:
        if not db:
            return []

        try:
            messages = db.query(ChatMessage).filter(
                ChatMessage.session_id == session_id
            ).order_by(ChatMessage.created_at.asc()).all()

            return [
                {
                    "id": m.id,
                    "role": m.role,
                    "content": m.content,
                    "intent_type": m.intent_type,
                    "form_code": m.form_code,
                    "extracted_fields": m.extracted_fields,
                    "confidence": m.confidence,
                    "reasoning": m.reasoning,
                    "created_at": m.created_at.isoformat() if m.created_at else None
                }
                for m in messages
            ]
        except Exception as e:
            logger.exception("[ChatHistoryService] 查询消息失败 session_id=%s: %s", session_id, e)
            return []

    @classmethod
    def delete_session(cls, session_id: str, db: Session = None) -> bool:
        if not db:
            return False

        try:
            db.query(ChatMessage).filter(ChatMessage.session_id == session_id).delete()
            db.query(ChatSession).filter(ChatSession.session_id == session_id).delete()
            db.commit()
            logger.info("[ChatHistoryService] 删除会话 session_id=%s", session_id)
            return True
        except Exception as e:
            db.rollback()
            logger.exception("[ChatHistoryService] 删除会话失败 session_id=%s: %s", session_id, e)
            return False

    @classmethod
    def update_session_title(cls, session_id: str, title: str, db: Session = None) -> bool:
        if not db:
            return False

        try:
            session = db.query(ChatSession).filter(ChatSession.session_id == session_id).first()
            if session:
                session.title = title
                db.commit()
                logger.info("[ChatHistoryService] 更新标题 session_id=%s title=%s", session_id, title)
                return True
            logger.warning("[ChatHistoryService] 会话不存在 session_id=%s", session_id)
            return False
        except Exception as e:
            db.rollback()
            logger.exception("[ChatHistoryService] 更新标题失败 session_id=%s: %s", session_id, e)
            return False
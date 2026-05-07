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
                session_metadata=metadata or {}
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
            logger.info(f"[ChatServiceV2] 查询到 {len(sessions)} 个会话")
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
                session.session_metadata = metadata
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
        db: Session = None,
        step_type: str = None  # 处理步骤类型：thinking / reasoning / action
    ) -> Optional[Dict[str, Any]]:
        """
        保存消息 + metadata。
        
        Args:
            session_id: 会话ID
            role: 用户角色（user/assistant/system）
            content: 消息内容
            content_type: 内容类型（text/markdown/json/form/thinking）
            metadata: 业务扩展字段
            parent_id: 父消息ID
            user_id: 用户ID
            step_type: 处理步骤类型（用于标识中间步骤）
        
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

            # 获取当前会话的最大 sort_order，确保消息顺序正确
            max_sort_order = db.query(func.max(ChatMessageV2.sort_order)).filter(
                ChatMessageV2.session_id == session_id
            ).scalar()
            sort_order = (max_sort_order or 0) + 1

            # 验证 content 不为空
            if not content or not str(content).strip():
                logger.warning("[ChatServiceV2] 消息内容为空，拒绝保存 session_id=%s", session_id)
                return None
            
            message_id = str(uuid.uuid4())
            message = ChatMessageV2(
                message_id=message_id,
                session_id=session_id,
                role=role,
                content=str(content).strip(),
                content_type=content_type,
                parent_id=parent_id,
                sort_order=sort_order,
                created_at=datetime.now()
            )
            db.add(message)

            # 批量写入 metadata（如果有）
            if metadata:
                for key, value in metadata.items():
                    # 优化：统一使用 JSON 序列化，避免类型判断
                    # 对于 None 值，存储为 null 字符串
                    serialized_value = json.dumps(value, ensure_ascii=False) if value is not None else None
                    meta = ChatMessageMetadata(
                        message_id=message_id,
                        meta_key=key,
                        value=serialized_value
                    )
                    db.add(meta)
            
            # 处理步骤类型（thinking/reasoning/action）作为独立消息块标识
            if step_type:
                meta = ChatMessageMetadata(
                    message_id=message_id,
                    meta_key="step_type",
                    value=step_type
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
    def save_step_message(
        cls,
        session_id: str,
        step_type: str,
        content: str,
        parent_id: str = None,
        user_id: str = None,
        db: Session = None
    ) -> Optional[Dict[str, Any]]:
        """
        保存处理步骤消息（作为独立消息块）
        
        Args:
            session_id: 会话ID
            step_type: 步骤类型（thinking/reasoning/action）
            content: 步骤内容
            parent_id: 关联的父消息ID
            user_id: 用户ID
        
        Returns:
            {"message_id": ..., "session_id": ...}
        """
        metadata = {
            "step_type": step_type
        }
        
        return cls.save_message(
            session_id=session_id,
            role="system",
            content=content,
            content_type="thinking",
            metadata=metadata,
            parent_id=parent_id,
            user_id=user_id,
            db=db
        )

    @classmethod
    def get_messages(
        cls,
        session_id: str,
        limit: int = 200,
        before_ts: datetime = None,  # 向前翻页游标
        after_ts: datetime = None,   # 向后翻页游标
        include_metadata: bool = True,
        db: Session = None
    ) -> Dict[str, Any]:
        """
        获取会话的消息列表（支持滚动加载）
        
        Args:
            session_id: 会话ID
            limit: 返回数量限制
            before_ts: 向前翻页，获取此时间之前的消息
            after_ts: 向后翻页，获取此时间之后的消息
            include_metadata: 是否包含metadata
        
        Returns:
            {
                "messages": 消息列表（按时间升序排列）,
                "total": 会话总消息数,
                "has_more_before": 是否有更早的消息,
                "has_more_after": 是否有更新的消息
            }
        """
        if not db:
            return {"messages": [], "total": 0, "has_more_before": False, "has_more_after": False}
        try:
            # 获取会话总消息数（用于滚动加载判断）
            total_count = db.query(ChatMessageV2).filter(ChatMessageV2.session_id == session_id).count()

            # 构建查询条件
            query = db.query(ChatMessageV2).filter(ChatMessageV2.session_id == session_id)
            
            # 双向分页支持
            if before_ts:
                query = query.filter(ChatMessageV2.created_at < before_ts)
            if after_ts:
                query = query.filter(ChatMessageV2.created_at > after_ts)

            # 使用 sort_order 排序，确保消息顺序正确
            # sort_order 是会话内递增的整数，保证消息顺序绝对正确
            messages = query.order_by(
                ChatMessageV2.sort_order.asc()
            ).limit(limit + 1).all()

            # 判断是否还有更多数据
            has_more = len(messages) > limit
            if has_more:
                messages = messages[:-1]  # 移除多余的一条用于判断

            # 判断翻页方向
            has_more_before = False
            has_more_after = False
            if before_ts:
                # 向前翻页模式
                has_more_before = has_more
            elif after_ts:
                # 向后翻页模式
                has_more_after = has_more
            else:
                # 默认模式（从最新或最早开始）
                if total_count > len(messages):
                    has_more_after = True

            logger.info(f"[ChatServiceV2] 查询到 {len(messages)} 条消息 for session {session_id}, total={total_count}")

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

                result_messages = []
                for msg in messages:
                    d = cls._message_to_dict(msg, False)
                    # 优化：反序列化 JSON 值
                    metadata_dict = {}
                    for m_meta in meta_map.get(msg.message_id, []):
                        try:
                            metadata_dict[m_meta.meta_key] = json.loads(m_meta.value) if m_meta.value is not None else None
                        except (json.JSONDecodeError, TypeError):
                            metadata_dict[m_meta.meta_key] = m_meta.value
                    d["metadata"] = metadata_dict
                    result_messages.append(d)
            else:
                result_messages = [cls._message_to_dict(m, False) for m in messages]

            return {
                "messages": result_messages,
                "total": total_count,
                "has_more_before": has_more_before,
                "has_more_after": has_more_after
            }
        except Exception as e:
            logger.exception("[ChatServiceV2] 获取消息失败: %s", e)
            return {"messages": [], "total": 0, "has_more_before": False, "has_more_after": False}

    @classmethod
    def get_message(cls, message_id: str, db: Session = None) -> Optional[Dict[str, Any]]:
        """获取单条消息详情（含 metadata）"""
        if not db:
            return None
        try:
            msg = db.query(ChatMessageV2).options(
                joinedload(ChatMessageV2.msg_metadata)
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

    @classmethod
    def save_messages_batch(
        cls,
        session_id: str,
        messages: List[Dict[str, Any]],
        user_id: str = None,
        db: Session = None
    ) -> Dict[str, Any]:
        """
        批量保存消息（真正的批量插入优化）
        
        Args:
            session_id: 会话ID
            messages: 消息列表，每条消息包含: role, content, content_type, metadata, parent_id
            user_id: 用户ID
        
        Returns:
            {
                "success": 是否成功,
                "count": 保存的消息数,
                "message_ids": 生成的消息ID列表
            }
        """
        if not db or not messages:
            return {"success": False, "count": 0, "message_ids": []}
        
        try:
            # 确保会话存在
            session = db.query(ChatSessionV2).filter(ChatSessionV2.session_id == session_id).first()
            if not session:
                session = ChatSessionV2(
                    session_id=session_id,
                    user_id=user_id,
                    title=f"会话 {datetime.now().strftime('%Y-%m-%d %H:%M')}"
                )
                db.add(session)

            # 获取当前会话的最大 sort_order，确保消息顺序正确
            max_sort_order = db.query(func.max(ChatMessageV2.sort_order)).filter(
                ChatMessageV2.session_id == session_id
            ).scalar()
            current_sort_order = max_sort_order or 0

            # 批量创建消息对象
            message_objects = []
            metadata_objects = []
            message_ids = []
            
            # 记录起始时间
            base_time = datetime.now()

            for msg_data in messages:
                # 跳过 content 为空的消息
                content = msg_data.get('content', '')
                if not content or not str(content).strip():
                    logger.warning("[ChatServiceV2] 跳过空内容消息 session_id=%s", session_id)
                    continue
                
                message_id = str(uuid.uuid4())
                message_ids.append(message_id)
                
                # 递增 sort_order，确保消息顺序正确
                current_sort_order += 1
                
                message = ChatMessageV2(
                    message_id=message_id,
                    session_id=session_id,
                    role=msg_data.get('role', 'user'),
                    content=str(content).strip(),
                    content_type=msg_data.get('content_type', 'text'),
                    parent_id=msg_data.get('parent_id'),
                    sort_order=current_sort_order,
                    created_at=base_time
                )
                message_objects.append(message)

                # 处理 metadata
                metadata = msg_data.get('metadata', {})
                for key, value in metadata.items():
                    serialized_value = json.dumps(value, ensure_ascii=False) if value is not None else None
                    meta = ChatMessageMetadata(
                        message_id=message_id,
                        meta_key=key,
                        value=serialized_value
                    )
                    metadata_objects.append(meta)

            # 批量插入消息
            if message_objects:
                db.add_all(message_objects)

            # 批量插入 metadata
            if metadata_objects:
                db.add_all(metadata_objects)

            # 更新会话时间
            session.updated_at = func.now()

            db.commit()
            logger.info(f"[ChatServiceV2] 批量保存消息 session_id={session_id} count={len(message_objects)}")
            return {
                "success": True,
                "count": len(message_objects),
                "message_ids": message_ids
            }
        
        except Exception as e:
            db.rollback()
            logger.exception("[ChatServiceV2] 批量保存消息失败: %s", e)
            return {"success": False, "count": 0, "message_ids": []}

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
                joinedload(ChatMessageV2.msg_metadata)
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
            "metadata":     s.session_metadata or {},
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
        if include_metadata and m.msg_metadata:
            # 优化：反序列化 JSON 值，恢复原始数据类型
            metadata_dict = {}
            for x in m.msg_metadata:
                try:
                    # 尝试解析 JSON，如果失败则保持原字符串
                    metadata_dict[x.meta_key] = json.loads(x.value) if x.value is not None else None
                except (json.JSONDecodeError, TypeError):
                    # 如果不是有效的 JSON，直接返回字符串
                    metadata_dict[x.meta_key] = x.value
            result["metadata"] = metadata_dict
        elif include_metadata:
            result["metadata"] = {}
        return result
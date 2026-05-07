"""
chat_v2 API - 通用聊天架构端点
前缀: /api/v2/chat
与 v1 完全解耦，业务扩展全走 metadata
"""
import logging
from datetime import datetime
from typing import List, Dict, Any, Optional
from fastapi import APIRouter, Depends, Query, Body, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.services.chat_service_v2 import ChatServiceV2

logger = logging.getLogger("chat_v2_api")
router = APIRouter(prefix="/api/v1/chat", tags=["chat"])


# ── Request / Response Models ──────────────────────────────────

class SessionCreateRequest(BaseModel):
    user_id:      Optional[str] = None
    title:        Optional[str] = None
    context_tags: Optional[List[str]] = None
    metadata:     Optional[Dict[str, Any]] = None


class SessionUpdateRequest(BaseModel):
    title:        Optional[str] = None
    context_tags: Optional[List[str]] = None
    metadata:     Optional[Dict[str, Any]] = None
    status:       Optional[str] = None


class MessageCreateRequest(BaseModel):
    role:         str = Field(..., description="user / assistant / system")
    content:      str = Field(..., description="消息正文")
    content_type: str = Field(default='text', description="text / markdown / json / form")
    metadata:     Optional[Dict[str, Any]] = Field(default=None, description="业务扩展字段")
    parent_id:    Optional[str] = Field(default=None, description="父消息 ID")


class SessionResponse(BaseModel):
    session_id:   str
    user_id:      Optional[str]
    title:        Optional[str]
    context_tags: List[str]
    metadata:     Dict[str, Any]
    status:       str
    created_at:   Optional[str]
    updated_at:   Optional[str]


class MessageResponse(BaseModel):
    message_id:   str
    session_id:   str
    role:         str
    content:      str
    content_type: str
    parent_id:    Optional[str]
    created_at:   Optional[str]
    metadata:     Dict[str, Any]


class SessionListResponse(BaseModel):
    sessions: List[Dict[str, Any]]
    total: int


class MessageListResponse(BaseModel):
    messages: List[Dict[str, Any]]
    total: int


# ── 会话 API ──────────────────────────────────────────────────

@router.get("/sessions", response_model=SessionListResponse)
async def get_sessions(
    user_id: Optional[str] = Query(None, description="用户ID"),
    status: str = Query('active', description="会话状态"),
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db)
):
    """查询会话列表"""
    sessions = ChatServiceV2.get_sessions(user_id=user_id, status=status, limit=limit, db=db)
    return SessionListResponse(sessions=sessions, total=len(sessions))


@router.post("/sessions", response_model=SessionResponse)
async def create_session(
    request: SessionCreateRequest,
    db: Session = Depends(get_db)
):
    """创建新会话"""
    result = ChatServiceV2.create_session(
        user_id=request.user_id,
        title=request.title,
        context_tags=request.context_tags,
        metadata=request.metadata,
        db=db
    )
    if result:
        return SessionResponse(**result)
    return {"error": "创建失败"}


@router.get("/sessions/{session_id}", response_model=SessionResponse)
async def get_session(
    session_id: str,
    db: Session = Depends(get_db)
):
    """获取会话详情"""
    s = ChatServiceV2.get_session(session_id, db=db)
    if s:
        return SessionResponse(**s)
    return {"error": "会话不存在"}


@router.patch("/sessions/{session_id}", response_model=SessionResponse)
async def update_session(
    session_id: str,
    request: SessionUpdateRequest,
    db: Session = Depends(get_db)
):
    """更新会话（标题 / tags / metadata / status）"""
    ChatServiceV2.update_session(
        session_id=session_id,
        title=request.title,
        context_tags=request.context_tags,
        metadata=request.metadata,
        status=request.status,
        db=db
    )
    s = ChatServiceV2.get_session(session_id, db=db)
    return SessionResponse(**s) if s else {"error": "更新失败"}


@router.delete("/sessions/{session_id}")
async def delete_session(
    session_id: str,
    db: Session = Depends(get_db)
):
    """删除会话（级联删除消息和 metadata）"""
    success = ChatServiceV2.delete_session(session_id, db=db)
    return {"success": success}


# ── 消息 API ──────────────────────────────────────────────────

@router.get("/sessions/{session_id}/messages", response_model=MessageListResponse)
async def get_messages(
    session_id: str,
    limit: int = Query(200, ge=1, le=500),
    before_ts: Optional[str] = Query(None, description="ISO 时间戳，分页游标"),
    include_metadata: bool = Query(True),
    db: Session = Depends(get_db)
):
    """获取会话的所有消息（含 metadata）"""
    before_dt = None
    if before_ts:
        try:
            before_dt = datetime.fromisoformat(before_ts.replace('Z', '+00:00'))
        except ValueError:
            pass
    messages = ChatServiceV2.get_messages(
        session_id=session_id,
        limit=limit,
        before_ts=before_dt,
        include_metadata=include_metadata,
        db=db
    )
    return MessageListResponse(messages=messages, total=len(messages))


@router.post("/sessions/{session_id}/messages", response_model=Dict[str, Any])
async def create_message(
    session_id: str,
    request: MessageCreateRequest,
    db: Session = Depends(get_db)
):
    """发送消息（保存到数据库）"""
    result = ChatServiceV2.save_message(
        session_id=session_id,
        role=request.role,
        content=request.content,
        content_type=request.content_type,
        metadata=request.metadata,
        parent_id=request.parent_id,
        db=db
    )
    if result:
        logger.info("[chat_v2] 保存消息 session=%s role=%s", session_id, request.role)
        return {"success": True, **result}
    raise HTTPException(500, detail="保存失败")


@router.get("/sessions/{session_id}/messages/{message_id}", response_model=MessageResponse)
async def get_message(
    session_id: str,
    message_id: str,
    db: Session = Depends(get_db)
):
    """获取单条消息详情（含 metadata）"""
    m = ChatServiceV2.get_message(message_id, db=db)
    if m:
        return MessageResponse(**m)
    return {"error": "消息不存在"}


@router.delete("/sessions/{session_id}/messages/{message_id}")
async def delete_message(
    session_id: str,
    message_id: str,
    db: Session = Depends(get_db)
):
    """删除单条消息"""
    success = ChatServiceV2.delete_message(message_id, db=db)
    return {"success": success}


# ── 搜索 API ──────────────────────────────────────────────────

@router.get("/messages/search")
async def search_messages(
    q: str = Query(..., description="搜索关键词"),
    user_id: Optional[str] = Query(None),
    session_id: Optional[str] = Query(None),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db)
):
    """全文搜索消息"""
    results = ChatServiceV2.search_messages(
        query_text=q,
        user_id=user_id,
        session_id=session_id,
        limit=limit,
        db=db
    )
    return {"results": results, "total": len(results)}


# ── 统计 API ──────────────────────────────────────────────────

@router.get("/sessions/{session_id}/stats")
async def get_session_stats(
    session_id: str,
    db: Session = Depends(get_db)
):
    """会话统计"""
    return ChatServiceV2.get_session_stats(session_id, db=db)
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
router = APIRouter(prefix="/api/v2/chat", tags=["chat-v2"])


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
    message_id:   Optional[str] = Field(default=None, description="消息 ID（前端传入，不传则自动生成）")


class BatchMessageCreateRequest(BaseModel):
    messages: List[MessageCreateRequest] = Field(..., description="消息列表")


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
    has_more_before: bool = False
    has_more_after: bool = False


# ── 会话 API ──────────────────────────────────────────────────

@router.get("/sessions", response_model=SessionListResponse)
async def get_sessions(
    user_id: Optional[str] = Query(None, description="用户ID"),
    status: str = Query('active', description="会话状态"),
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db)
):
    """查询会话列表"""
    logger.info(f"[chat_v2] 查询会话列表 user_id={user_id}, status={status}, limit={limit}")
    sessions = ChatServiceV2.get_sessions(user_id=user_id, status=status, limit=limit, db=db)
    logger.info(f"[chat_v2] 返回 {len(sessions)} 个会话")
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
    before_ts: Optional[str] = Query(None, description="向前翻页：获取此时间之前的消息"),
    after_ts: Optional[str] = Query(None, description="向后翻页：获取此时间之后的消息"),
    include_metadata: bool = Query(True),
    db: Session = Depends(get_db)
):
    """获取会话的消息列表（支持滚动加载）"""
    logger.info(f"[chat_v2] 获取消息 session_id={session_id}, limit={limit}, before_ts={before_ts}, after_ts={after_ts}")
    
    before_dt = None
    if before_ts:
        try:
            before_dt = datetime.fromisoformat(before_ts.replace('Z', '+00:00'))
        except ValueError:
            pass
    
    after_dt = None
    if after_ts:
        try:
            after_dt = datetime.fromisoformat(after_ts.replace('Z', '+00:00'))
        except ValueError:
            pass
    
    result = ChatServiceV2.get_messages(
        session_id=session_id,
        limit=limit,
        before_ts=before_dt,
        after_ts=after_dt,
        include_metadata=include_metadata,
        db=db
    )
    
    logger.info(f"[chat_v2] 返回 {len(result['messages'])} 条消息，总数 {result['total']}")
    return MessageListResponse(
        messages=result['messages'],
        total=result['total'],
        has_more_before=result['has_more_before'],
        has_more_after=result['has_more_after']
    )


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
        message_id=request.message_id,  # 传递前端传入的消息ID
        db=db
    )
    if result:
        logger.info("[chat_v2] 保存消息 session=%s role=%s", session_id, request.role)
        return {"success": True, **result}
    raise HTTPException(500, detail="保存失败")


@router.post("/sessions/{session_id}/messages/batch", response_model=Dict[str, Any])
async def create_messages_batch(
    session_id: str,
    request: BatchMessageCreateRequest,
    db: Session = Depends(get_db)
):
    """批量保存消息（真正的批量插入优化）"""
    # 转换为字典列表
    messages_data = [
        {
            "role": msg.role,
            "content": msg.content,
            "content_type": msg.content_type,
            "metadata": msg.metadata,
            "parent_id": msg.parent_id
        }
        for msg in request.messages
    ]
    
    result = ChatServiceV2.save_messages_batch(
        session_id=session_id,
        messages=messages_data,
        db=db
    )
    
    if result["success"]:
        logger.info("[chat_v2] 批量保存消息 session=%s count=%d", session_id, result["count"])
        return {
            "success": True,
            "count": result["count"],
            "message_ids": result["message_ids"]
        }
    else:
        logger.warning("[chat_v2] 批量保存消息失败 session=%s", session_id)
        raise HTTPException(500, detail="批量保存失败")


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
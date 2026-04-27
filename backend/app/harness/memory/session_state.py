"""
会话状态管理

功能：
- 会话状态存储
- 状态查询与更新
- 会话生命周期管理
- 断点创建与恢复
"""

from typing import Dict, Any, Optional, List
from dataclasses import dataclass, field
from datetime import datetime, timedelta
import json
import logging
import uuid

logger = logging.getLogger(__name__)


@dataclass
class SessionState:
    """
    会话状态
    
    存储单个会话的状态信息
    """
    session_id: str
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
    user_id: Optional[str] = None
    _state: Dict[str, Any] = field(default_factory=dict)
    _history: List[Dict] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    def __post_init__(self):
        if not self.session_id:
            self.session_id = str(uuid.uuid4())
    
    def set(self, key: str, value: Any, record_history: bool = True):
        """设置状态值"""
        if record_history:
            self._history.append({
                "action": "set",
                "key": key,
                "old_value": self._state.get(key),
                "new_value": value,
                "timestamp": datetime.now().isoformat()
            })
        
        self._state[key] = value
        self.updated_at = datetime.now()
    
    def get(self, key: str, default: Any = None) -> Any:
        """获取状态值"""
        return self._state.get(key, default)
    
    def delete(self, key: str) -> bool:
        """删除状态值"""
        if key in self._state:
            del self._state[key]
            self.updated_at = datetime.now()
            return True
        return False
    
    def update(self, data: Dict[str, Any], record_history: bool = True):
        """批量更新状态"""
        for key, value in data.items():
            self.set(key, value, record_history)
    
    def get_all(self) -> Dict[str, Any]:
        """获取所有状态"""
        return self._state.copy()
    
    def get_history(self, limit: int = 100) -> List[Dict]:
        """获取历史记录"""
        return self._history[-limit:]
    
    def clear(self, keep_metadata: bool = True):
        """清空状态"""
        self._state.clear()
        if not keep_metadata:
            self._history.clear()
        self.updated_at = datetime.now()
    
    def to_dict(self) -> Dict:
        """转换为字典"""
        return {
            "session_id": self.session_id,
            "created_at": self.created_at.isoformat(),
            "updated_at": self.updated_at.isoformat(),
            "user_id": self.user_id,
            "state": self._state,
            "metadata": self.metadata
        }
    
    @classmethod
    def from_dict(cls, data: Dict) -> "SessionState":
        """从字典创建"""
        session = cls(
            session_id=data.get("session_id", str(uuid.uuid4())),
            user_id=data.get("user_id"),
            metadata=data.get("metadata", {})
        )
        
        if "created_at" in data:
            session.created_at = datetime.fromisoformat(data["created_at"])
        if "updated_at" in data:
            session.updated_at = datetime.fromisoformat(data["updated_at"])
        if "state" in data:
            session._state = data["state"]
        
        return session
    
    def age_seconds(self) -> float:
        """获取会话年龄（秒）"""
        return (datetime.now() - self.updated_at).total_seconds()


class SessionManager:
    """
    会话管理器
    
    管理多个会话的生命周期
    
    使用示例：
    ```python
    manager = SessionManager()
    
    # 创建会话
    session = manager.create_session(user_id="user123")
    
    # 设置状态
    session.set("current_form", "leave")
    session.set("extracted_fields", {"days": 3})
    
    # 获取会话
    session = manager.get_session(session.id)
    
    # 查询会话
    sessions = manager.find_sessions(user_id="user123")
    ```
    """

    def __init__(
        self,
        max_age_hours: int = 24,
        max_sessions: int = 10000,
        auto_cleanup_interval: int = 3600
    ):
        """
        初始化会话管理器
        
        Args:
            max_age_hours: 会话最大存活时间（小时）
            max_sessions: 最大会话数
            auto_cleanup_interval: 自动清理间隔（秒）
        """
        self._sessions: Dict[str, SessionState] = {}
        self.max_age_hours = max_age_hours
        self.max_sessions = max_sessions
        self._last_cleanup = datetime.now()
        
        logger.info(f"SessionManager initialized (max_age={max_age_hours}h, max={max_sessions})")

    def create_session(
        self,
        user_id: Optional[str] = None,
        session_id: Optional[str] = None,
        metadata: Optional[Dict] = None
    ) -> SessionState:
        """
        创建新会话
        
        Args:
            user_id: 用户 ID
            session_id: 指定会话 ID（可选）
            metadata: 元数据
            
        Returns:
            SessionState: 新会话
        """
        # 检查会话数限制
        self._cleanup_if_needed()
        
        session = SessionState(
            session_id=session_id or str(uuid.uuid4()),
            user_id=user_id,
            metadata=metadata or {}
        )
        
        self._sessions[session.session_id] = session
        logger.debug(f"Session created: {session.session_id}")
        
        return session

    def get_session(self, session_id: str) -> Optional[SessionState]:
        """获取会话"""
        session = self._sessions.get(session_id)
        
        if session and session.age_seconds() > self.max_age_hours * 3600:
            # 会话已过期
            self.delete_session(session_id)
            return None
        
        return session

    def get_or_create(
        self,
        session_id: str,
        user_id: Optional[str] = None
    ) -> SessionState:
        """获取或创建会话"""
        session = self.get_session(session_id)
        
        if session is None:
            session = self.create_session(session_id=session_id, user_id=user_id)
        
        return session

    def delete_session(self, session_id: str) -> bool:
        """删除会话"""
        if session_id in self._sessions:
            del self._sessions[session_id]
            logger.debug(f"Session deleted: {session_id}")
            return True
        return False

    def find_sessions(
        self,
        user_id: Optional[str] = None,
        min_age_seconds: float = 0,
        max_age_seconds: float = float("inf")
    ) -> List[SessionState]:
        """查询会话"""
        results = []
        
        for session in self._sessions.values():
            # 用户过滤
            if user_id and session.user_id != user_id:
                continue
            
            # 年龄过滤
            age = session.age_seconds()
            if age < min_age_seconds or age > max_age_seconds:
                continue
            
            results.append(session)
        
        return results

    def get_active_sessions(self, active_threshold_seconds: float = 3600) -> List[SessionState]:
        """获取活跃会话"""
        return self.find_sessions(min_age_seconds=0, max_age_seconds=active_threshold_seconds)

    def cleanup_old_sessions(self) -> int:
        """清理过期会话"""
        old_count = len(self._sessions)
        
        self._sessions = {
            sid: session
            for sid, session in self._sessions.items()
            if session.age_seconds() <= self.max_age_hours * 3600
        }
        
        deleted = old_count - len(self._sessions)
        if deleted > 0:
            logger.info(f"Cleaned up {deleted} expired sessions")
        
        return deleted

    def _cleanup_if_needed(self):
        """必要时进行清理"""
        now = datetime.now()
        
        # 检查是否需要清理
        if (now - self._last_cleanup).total_seconds() > 3600:
            if len(self._sessions) >= self.max_sessions:
                self.cleanup_old_sessions()
            self._last_cleanup = now

    def get_stats(self) -> Dict[str, Any]:
        """获取统计信息"""
        now = datetime.now()
        
        active_count = sum(
            1 for s in self._sessions.values()
            if s.age_seconds() < 3600
        )
        
        return {
            "total_sessions": len(self._sessions),
            "active_sessions": active_count,
            "max_sessions": self.max_sessions,
            "max_age_hours": self.max_age_hours,
            "utilization": len(self._sessions) / self.max_sessions if self.max_sessions > 0 else 0
        }

    def export_session(self, session_id: str) -> Optional[str]:
        """导出会话为 JSON"""
        session = self.get_session(session_id)
        if session:
            return json.dumps(session.to_dict(), ensure_ascii=False, indent=2)
        return None

    def import_session(self, json_str: str) -> Optional[SessionState]:
        """从 JSON 导入会话"""
        try:
            data = json.loads(json_str)
            session = SessionState.from_dict(data)
            self._sessions[session.session_id] = session
            return session
        except Exception as e:
            logger.error(f"Failed to import session: {e}")
            return None


# 全局会话管理器
_session_manager: Optional[SessionManager] = None


def get_session_manager() -> SessionManager:
    """获取全局会话管理器"""
    global _session_manager
    if _session_manager is None:
        _session_manager = SessionManager()
    return _session_manager

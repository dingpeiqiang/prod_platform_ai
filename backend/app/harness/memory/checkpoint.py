"""
断点管理

功能：
- 状态快照
- 断点创建与恢复
- 版本控制
- 自动保存
"""

from typing import Dict, Any, Optional, List, Callable
from dataclasses import dataclass, field
from datetime import datetime
import json
import logging
import uuid

logger = logging.getLogger(__name__)


@dataclass
class Checkpoint:
    """断点"""
    id: str
    name: str
    timestamp: datetime = field(default_factory=datetime.now)
    state: Dict[str, Any] = field(default_factory=dict)
    metadata: Dict[str, Any] = field(default_factory=dict)
    parent_id: Optional[str] = None
    version: int = 1

    def to_dict(self) -> Dict:
        return {
            "id": self.id,
            "name": self.name,
            "timestamp": self.timestamp.isoformat(),
            "state": self.state,
            "metadata": self.metadata,
            "parent_id": self.parent_id,
            "version": self.version
        }

    @classmethod
    def from_dict(cls, data: Dict) -> "Checkpoint":
        checkpoint = cls(
            id=data["id"],
            name=data["name"],
            state=data.get("state", {}),
            metadata=data.get("metadata", {}),
            parent_id=data.get("parent_id"),
            version=data.get("version", 1)
        )
        if "timestamp" in data:
            checkpoint.timestamp = datetime.fromisoformat(data["timestamp"])
        return checkpoint


class CheckpointManager:
    """
    断点管理器
    
    功能：
    1. 创建断点（状态快照）
    2. 恢复到指定断点
    3. 断点历史管理
    4. 自动保存
    
    使用示例：
    ```python
    manager = CheckpointManager()
    
    # 创建断点
    checkpoint = manager.create_checkpoint(
        name="before_form_edit",
        state={"form": form_data, "cursor": position}
    )
    
    # 修改状态
    form_data["field"] = "new_value"
    
    # 恢复到断点
    restored = manager.restore(checkpoint.id)
    
    # 列出历史
    history = manager.get_history(limit=10)
    ```
    """

    def __init__(
        self,
        max_checkpoints: int = 100,
        auto_save: bool = True,
        auto_save_interval: int = 60
    ):
        """
        初始化断点管理器
        
        Args:
            max_checkpoints: 最大断点数
            auto_save: 是否自动保存
            auto_save_interval: 自动保存间隔（秒）
        """
        self.max_checkpoints = max_checkpoints
        self.auto_save = auto_save
        self.auto_save_interval = auto_save_interval
        
        self._checkpoints: Dict[str, Checkpoint] = {}
        self._entity_checkpoints: Dict[str, List[str]] = {}  # entity_id -> checkpoint_ids
        self._last_auto_save: Optional[datetime] = None

    def create_checkpoint(
        self,
        name: str,
        state: Dict[str, Any],
        entity_id: Optional[str] = None,
        parent_id: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> Checkpoint:
        """
        创建断点
        
        Args:
            name: 断点名称
            state: 要保存的状态
            entity_id: 关联的实体 ID
            parent_id: 父断点 ID
            metadata: 元数据
            
        Returns:
            Checkpoint: 创建的断点
        """
        # 生成 ID
        checkpoint_id = str(uuid.uuid4())[:16]
        
        # 计算版本号
        version = 1
        if entity_id and entity_id in self._entity_checkpoints:
            existing = self._entity_checkpoints[entity_id]
            if existing:
                last_cp = self._checkpoints.get(existing[-1])
                if last_cp:
                    version = last_cp.version + 1
        
        checkpoint = Checkpoint(
            id=checkpoint_id,
            name=name,
            state=self._deep_copy(state),
            metadata=metadata or {},
            parent_id=parent_id,
            version=version
        )
        
        self._checkpoints[checkpoint_id] = checkpoint
        
        # 更新实体索引
        if entity_id:
            if entity_id not in self._entity_checkpoints:
                self._entity_checkpoints[entity_id] = []
            self._entity_checkpoints[entity_id].append(checkpoint_id)
        
        # 清理旧断点
        self._cleanup_old_checkpoints()
        
        logger.debug(f"Checkpoint created: {checkpoint_id} ({name})")
        return checkpoint

    def restore(
        self,
        checkpoint_id: str,
        target_state: Optional[Dict[str, Any]] = None
    ) -> Optional[Dict[str, Any]]:
        """
        恢复到指定断点
        
        Args:
            checkpoint_id: 断点 ID
            target_state: 目标状态（用于写入恢复的数据）
            
        Returns:
            Optional[Dict]: 恢复的状态，如果不存在返回 None
        """
        checkpoint = self._checkpoints.get(checkpoint_id)
        
        if not checkpoint:
            logger.warning(f"Checkpoint not found: {checkpoint_id}")
            return None
        
        restored_state = self._deep_copy(checkpoint.state)
        
        # 如果提供了目标状态，写入恢复的数据
        if target_state is not None:
            target_state.clear()
            target_state.update(restored_state)
        
        logger.info(f"Restored checkpoint: {checkpoint_id} ({checkpoint.name})")
        return restored_state

    def get_checkpoint(self, checkpoint_id: str) -> Optional[Checkpoint]:
        """获取断点"""
        return self._checkpoints.get(checkpoint_id)

    def get_entity_checkpoints(
        self,
        entity_id: str,
        limit: int = 10
    ) -> List[Checkpoint]:
        """获取实体的断点历史"""
        checkpoint_ids = self._entity_checkpoints.get(entity_id, [])
        checkpoints = []
        
        for cp_id in checkpoint_ids[-limit:]:
            cp = self._checkpoints.get(cp_id)
            if cp:
                checkpoints.append(cp)
        
        return checkpoints

    def get_history(
        self,
        limit: int = 20,
        entity_id: Optional[str] = None
    ) -> List[Checkpoint]:
        """获取断点历史"""
        if entity_id:
            return self.get_entity_checkpoints(entity_id, limit)
        
        # 全局历史
        checkpoints = sorted(
            self._checkpoints.values(),
            key=lambda c: c.timestamp,
            reverse=True
        )
        return checkpoints[:limit]

    def delete_checkpoint(self, checkpoint_id: str) -> bool:
        """删除断点"""
        if checkpoint_id in self._checkpoints:
            checkpoint = self._checkpoints[checkpoint_id]
            
            # 从实体索引中移除
            for entity_id, ids in self._entity_checkpoints.items():
                if checkpoint_id in ids:
                    ids.remove(checkpoint_id)
            
            del self._checkpoints[checkpoint_id]
            logger.debug(f"Checkpoint deleted: {checkpoint_id}")
            return True
        return False

    def delete_entity_checkpoints(self, entity_id: str) -> int:
        """删除实体的所有断点"""
        checkpoint_ids = self._entity_checkpoints.get(entity_id, [])
        count = len(checkpoint_ids)
        
        for cp_id in checkpoint_ids:
            if cp_id in self._checkpoints:
                del self._checkpoints[cp_id]
        
        del self._entity_checkpoints[entity_id]
        return count

    def diff(
        self,
        checkpoint_id1: str,
        checkpoint_id2: str
    ) -> Optional[Dict[str, Any]]:
        """
        比较两个断点的差异
        
        Returns:
            Dict: {
                "added": 新增的键,
                "removed": 删除的键,
                "changed": 修改的键及新值,
                "unchanged": 未改变的键
            }
        """
        cp1 = self._checkpoints.get(checkpoint_id1)
        cp2 = self._checkpoints.get(checkpoint_id2)
        
        if not cp1 or not cp2:
            return None
        
        state1, state2 = cp1.state, cp2.state
        
        keys1 = set(state1.keys())
        keys2 = set(state2.keys())
        
        return {
            "added": list(keys2 - keys1),
            "removed": list(keys1 - keys2),
            "changed": {
                k: {"from": state1.get(k), "to": state2.get(k)}
                for k in keys1 & keys2
                if state1.get(k) != state2.get(k)
            },
            "unchanged": list(keys1 & keys2 - set(
                k for k in keys1 & keys2 if state1.get(k) != state2.get(k)
            ))
        }

    def export_checkpoint(self, checkpoint_id: str) -> Optional[str]:
        """导出具点为 JSON"""
        checkpoint = self._checkpoints.get(checkpoint_id)
        if checkpoint:
            return json.dumps(checkpoint.to_dict(), ensure_ascii=False, indent=2)
        return None

    def import_checkpoint(self, json_str: str) -> Optional[Checkpoint]:
        """从 JSON 导入断点"""
        try:
            data = json.loads(json_str)
            checkpoint = Checkpoint.from_dict(data)
            self._checkpoints[checkpoint.id] = checkpoint
            
            entity_id = checkpoint.metadata.get("entity_id")
            if entity_id:
                if entity_id not in self._entity_checkpoints:
                    self._entity_checkpoints[entity_id] = []
                if checkpoint.id not in self._entity_checkpoints[entity_id]:
                    self._entity_checkpoints[entity_id].append(checkpoint.id)
            
            return checkpoint
        except Exception as e:
            logger.error(f"Failed to import checkpoint: {e}")
            return None

    def _cleanup_old_checkpoints(self):
        """清理旧断点"""
        total = len(self._checkpoints)
        
        if total <= self.max_checkpoints:
            return
        
        # 删除最老的断点
        to_delete = total - self.max_checkpoints
        
        # 按时间排序
        sorted_checkpoints = sorted(
            self._checkpoints.items(),
            key=lambda x: x[1].timestamp
        )
        
        for checkpoint_id, _ in sorted_checkpoints[:to_delete]:
            self.delete_checkpoint(checkpoint_id)

    def _deep_copy(self, obj: Any) -> Any:
        """深拷贝"""
        return json.loads(json.dumps(obj))

    def get_stats(self) -> Dict[str, Any]:
        """获取统计信息"""
        return {
            "total_checkpoints": len(self._checkpoints),
            "max_checkpoints": self.max_checkpoints,
            "entities_with_checkpoints": len(self._entity_checkpoints),
            "utilization": len(self._checkpoints) / self.max_checkpoints
        }


# 全局实例
_checkpoint_manager: Optional[CheckpointManager] = None


def get_checkpoint_manager() -> CheckpointManager:
    """获取全局断点管理器"""
    global _checkpoint_manager
    if _checkpoint_manager is None:
        _checkpoint_manager = CheckpointManager()
    return _checkpoint_manager

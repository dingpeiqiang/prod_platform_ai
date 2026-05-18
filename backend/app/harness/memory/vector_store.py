"""
向量记忆库

功能：
- 文本向量化
- 相似度检索
- 记忆存储与查询
- 基于 Embeddings 的上下文检索
- 持久化存储（JSON 文件）
"""

from typing import Dict, Any, Optional, List, Tuple, Callable
from dataclasses import dataclass, field
from datetime import datetime
import json
import hashlib
import logging
import math
import os

logger = logging.getLogger(__name__)


@dataclass
class MemoryEntry:
    """记忆条目"""
    id: str
    content: str
    vector: Optional[List[float]] = None
    metadata: Dict[str, Any] = field(default_factory=dict)
    created_at: datetime = field(default_factory=datetime.now)
    access_count: int = 0
    last_accessed: Optional[datetime] = None
    importance: float = 1.0

    def to_dict(self) -> Dict:
        return {
            "id": self.id,
            "content": self.content,
            "vector": self.vector,
            "metadata": self.metadata,
            "created_at": self.created_at.isoformat(),
            "access_count": self.access_count,
            "last_accessed": self.last_accessed.isoformat() if self.last_accessed else None,
            "importance": self.importance
        }

    @classmethod
    def from_dict(cls, data: Dict) -> 'MemoryEntry':
        """从字典恢复 MemoryEntry"""
        return cls(
            id=data["id"],
            content=data["content"],
            vector=data.get("vector"),
            metadata=data.get("metadata", {}),
            created_at=datetime.fromisoformat(data["created_at"]),
            access_count=data.get("access_count", 0),
            last_accessed=datetime.fromisoformat(data["last_accessed"]) if data.get("last_accessed") else None,
            importance=data.get("importance", 1.0)
        )


class EmbeddingsManager:
    """Embeddings 管理器"""

    def __init__(
        self,
        model: str = "simple",
        dimension: int = 384,
        embeddings_api: Optional[Callable] = None
    ):
        self.model = model
        self.dimension = dimension
        self.embeddings_api = embeddings_api
        self._cache: Dict[str, List[float]] = {}
        self._cache_size = 1000

    def embed(self, text: str, use_cache: bool = True) -> List[float]:
        if not text:
            return [0.0] * self.dimension
        
        if use_cache:
            cache_key = self._get_cache_key(text)
            if cache_key in self._cache:
                return self._cache[cache_key]
        
        if self.embeddings_api:
            vector = self.embeddings_api(text)
        else:
            vector = self._simple_embed(text)
        
        if use_cache:
            self._update_cache(text, vector)
        
        return vector

    def embed_batch(self, texts: List[str], use_cache: bool = True) -> List[List[float]]:
        return [self.embed(text, use_cache) for text in texts]

    def _simple_embed(self, text: str) -> List[float]:
        """简化嵌入 - 使用改进的哈希算法增加区分度"""
        if not text:
            return [0.0] * self.dimension
        
        vector = []
        
        # 使用多种哈希组合增加多样性
        md5_hash = hashlib.md5(text.encode()).digest()
        sha256_hash = hashlib.sha256(text.encode()).digest()
        combined_hash = md5_hash + sha256_hash
        
        for i in range(self.dimension):
            byte_idx = i % len(combined_hash)
            # 使用更复杂的计算增加向量区分度
            base_value = combined_hash[byte_idx] / 255.0
            # 添加多种周期性函数组合
            value = (
                base_value * math.sin(i * 0.1 + combined_hash[byte_idx]) +
                (1 - base_value) * math.cos(i * 0.05 + combined_hash[(i + 7) % len(combined_hash)]) +
                math.sin(i * 0.15) * 0.3
            )
            vector.append(value)
        
        # 归一化
        magnitude = math.sqrt(sum(v * v for v in vector))
        if magnitude > 0:
            vector = [v / magnitude for v in vector]
        
        return vector

    def _get_cache_key(self, text: str) -> str:
        return hashlib.md5(text.encode()).hexdigest()

    def _update_cache(self, text: str, vector: List[float]):
        if len(self._cache) >= self._cache_size:
            keys_to_remove = list(self._cache.keys())[:self._cache_size // 2]
            for key in keys_to_remove:
                del self._cache[key]
        self._cache[self._get_cache_key(text)] = vector

    def clear_cache(self):
        self._cache.clear()


class VectorStore:
    """向量记忆库"""

    def __init__(
        self,
        embeddings_manager: Optional[EmbeddingsManager] = None,
        max_entries: int = 10000,
        similarity_threshold: float = 0.3,  # 降低阈值，提升搜索召回率
        persist_path: str = None,
        auto_save_interval: int = 300  # 自动保存间隔（秒）
    ):
        self.embeddings = embeddings_manager or EmbeddingsManager()
        self.max_entries = max_entries
        self.similarity_threshold = similarity_threshold
        self._store: Dict[str, MemoryEntry] = {}
        self._session_index: Dict[str, List[str]] = {}
        
        # 持久化配置
        self.persist_path = persist_path or os.path.join(
            os.path.dirname(__file__), "..", "..", "data", "vector_store.json"
        )
        self.auto_save_interval = auto_save_interval
        self._last_save_time = 0
        
        # 确保数据目录存在
        os.makedirs(os.path.dirname(self.persist_path), exist_ok=True)
        
        # 加载已保存的数据
        self.load_from_disk()

    def load_from_disk(self) -> bool:
        """从磁盘加载数据"""
        try:
            if os.path.exists(self.persist_path):
                with open(self.persist_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                
                # 恢复存储
                self._store = {}
                for entry_data in data.get("entries", []):
                    try:
                        entry = MemoryEntry.from_dict(entry_data)
                        self._store[entry.id] = entry
                    except Exception as e:
                        logger.warning(f"Failed to load entry: {e}")
                
                # 恢复索引
                self._session_index = data.get("session_index", {})
                
                logger.info(f"Loaded {len(self._store)} entries from {self.persist_path}")
                return True
            else:
                logger.info(f"No existing data file found at {self.persist_path}")
                return False
        except Exception as e:
            logger.error(f"Failed to load data from disk: {e}")
            return False

    def save_to_disk(self) -> bool:
        """保存数据到磁盘"""
        try:
            data = {
                "entries": [entry.to_dict() for entry in self._store.values()],
                "session_index": self._session_index,
                "saved_at": datetime.now().isoformat()
            }
            
            # 先写入临时文件，避免损坏
            temp_path = self.persist_path + ".tmp"
            with open(temp_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
            # 原子替换
            os.replace(temp_path, self.persist_path)
            
            self._last_save_time = datetime.now().timestamp()
            logger.debug(f"Saved {len(self._store)} entries to {self.persist_path}")
            return True
        except Exception as e:
            logger.error(f"Failed to save data to disk: {e}")
            return False

    def maybe_auto_save(self):
        """检查是否需要自动保存"""
        if self.auto_save_interval <= 0:
            return
        
        now = datetime.now().timestamp()
        if now - self._last_save_time >= self.auto_save_interval:
            self.save_to_disk()

    def add(
        self,
        content: str,
        metadata: Optional[Dict[str, Any]] = None,
        session_id: Optional[str] = None,
        importance: float = 1.0
    ) -> str:
        self._cleanup_if_needed()
        
        entry_id = hashlib.md5(f"{content}{datetime.now().isoformat()}".encode()).hexdigest()[:16]
        vector = self.embeddings.embed(content)
        
        entry = MemoryEntry(
            id=entry_id,
            content=content,
            vector=vector,
            metadata=metadata or {},
            importance=importance
        )
        
        self._store[entry_id] = entry
        
        if session_id:
            if session_id not in self._session_index:
                self._session_index[session_id] = []
            self._session_index[session_id].append(entry_id)
        
        logger.debug(f"Memory added: {entry_id}")
        
        # 检查自动保存
        self.maybe_auto_save()
        
        return entry_id

    def add_batch(
        self,
        items: List[Dict[str, Any]],
        session_id: Optional[str] = None
    ) -> List[str]:
        ids = []
        for item in items:
            entry_id = self.add(
                content=item["content"],
                metadata=item.get("metadata"),
                session_id=session_id,
                importance=item.get("importance", 1.0)
            )
            ids.append(entry_id)
        return ids

    def search(
        self,
        query: str,
        top_k: int = 5,
        session_id: Optional[str] = None,
        metadata_filter: Optional[Dict] = None,
        min_similarity: Optional[float] = None
    ) -> List[Tuple[MemoryEntry, float]]:
        query_vector = self.embeddings.embed(query)
        candidates = self._store.values()
        
        if session_id:
            session_ids = set(self._session_index.get(session_id, []))
            candidates = [c for c in candidates if c.id in session_ids]
        
        results = []
        all_candidates = []  # 存储所有候选，用于fallback
        
        for entry in candidates:
            if not entry.vector:
                continue
            
            if metadata_filter:
                if not self._match_metadata(entry.metadata, metadata_filter):
                    continue
            
            similarity = self._cosine_similarity(query_vector, entry.vector)
            entry.access_count += 1
            entry.last_accessed = datetime.now()
            
            threshold = min_similarity or self.similarity_threshold
            if similarity >= threshold:
                results.append((entry, similarity))
            
            # 始终记录，用于fallback
            all_candidates.append((entry, similarity))
        
        # 如果没有达到阈值的结果，返回最相似的几个（fallback机制）
        if len(results) == 0 and len(all_candidates) > 0:
            all_candidates.sort(key=lambda x: (x[1], x[0].importance), reverse=True)
            results = all_candidates[:top_k]
        
        results.sort(key=lambda x: (x[1], x[0].importance), reverse=True)
        return results[:top_k]

    def get(self, entry_id: str) -> Optional[MemoryEntry]:
        entry = self._store.get(entry_id)
        if entry:
            entry.access_count += 1
            entry.last_accessed = datetime.now()
        return entry

    def get_session_memories(
        self,
        session_id: str,
        limit: int = 100
    ) -> List[MemoryEntry]:
        entry_ids = self._session_index.get(session_id, [])
        entries = []
        for entry_id in entry_ids[-limit:]:
            entry = self._store.get(entry_id)
            if entry:
                entries.append(entry)
        return entries

    def delete(self, entry_id: str) -> bool:
        if entry_id in self._store:
            entry = self._store[entry_id]
            for sid, ids in self._session_index.items():
                if entry_id in ids:
                    ids.remove(entry_id)
            del self._store[entry_id]
            
            # 检查自动保存
            self.maybe_auto_save()
            
            return True
        return False

    def delete_session(self, session_id: str) -> int:
        entry_ids = self._session_index.get(session_id, [])
        count = 0
        for entry_id in entry_ids:
            if entry_id in self._store:
                del self._store[entry_id]
                count += 1
        del self._session_index[session_id]
        
        # 检查自动保存
        self.maybe_auto_save()
        
        return count

    def _cleanup_if_needed(self):
        if len(self._store) >= self.max_entries:
            entries = sorted(
                self._store.values(),
                key=lambda e: (e.importance, -e.access_count)
            )
            to_delete = entries[:len(entries) // 5]
            for entry in to_delete:
                self.delete(entry.id)

    def _cosine_similarity(self, v1: List[float], v2: List[float]) -> float:
        dot_product = sum(a * b for a, b in zip(v1, v2))
        magnitude1 = math.sqrt(sum(a * a for a in v1))
        magnitude2 = math.sqrt(sum(b * b for b in v2))
        if magnitude1 == 0 or magnitude2 == 0:
            return 0.0
        return dot_product / (magnitude1 * magnitude2)

    def _match_metadata(self, entry_meta: Dict, filter_meta: Dict) -> bool:
        for key, value in filter_meta.items():
            if key not in entry_meta or entry_meta[key] != value:
                return False
        return True

    def get_stats(self) -> Dict[str, Any]:
        total_access = sum(e.access_count for e in self._store.values())
        return {
            "total_entries": len(self._store),
            "max_entries": self.max_entries,
            "total_sessions": len(self._session_index),
            "total_access_count": total_access,
            "utilization": len(self._store) / self.max_entries if self.max_entries > 0 else 0
        }

    def export_memories(self, session_id: Optional[str] = None) -> str:
        if session_id:
            entries = self.get_session_memories(session_id)
        else:
            entries = list(self._store.values())
        return json.dumps([e.to_dict() for e in entries], ensure_ascii=False, indent=2)

    def close(self):
        """关闭存储，确保数据保存"""
        self.save_to_disk()
        logger.info("VectorStore closed, data saved")


_vector_store: Optional[VectorStore] = None


def get_vector_store() -> VectorStore:
    global _vector_store
    if _vector_store is None:
        _vector_store = VectorStore()
    return _vector_store

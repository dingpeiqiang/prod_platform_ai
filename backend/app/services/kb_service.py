"""
轻量知识库服务
提供文档导入、向量检索、问答功能
"""

from typing import Dict, Any, List, Optional
from pathlib import Path
import logging
from ..harness.memory.vector_store import get_vector_store, MemoryEntry

logger = logging.getLogger(__name__)


class KBService:
    def __init__(self):
        self.vector_store = get_vector_store()

    def add_document(
        self,
        content: str,
        title: str = None,
        source: str = None,
        session_id: str = "kb_global",
        importance: float = 1.0
    ) -> str:
        """添加文档到知识库"""
        metadata = {
            "title": title or "",
            "source": source or "manual",
            "type": "document"
        }
        entry_id = self.vector_store.add(
            content=content,
            metadata=metadata,
            session_id=session_id,
            importance=importance
        )
        logger.info(f"Document added: {entry_id}, title: {title}")
        return entry_id

    def add_documents_from_dir(
        self,
        dir_path: str,
        session_id: str = "kb_global"
    ) -> List[str]:
        """从目录批量导入文档"""
        path = Path(dir_path)
        if not path.exists():
            raise ValueError(f"目录不存在: {dir_path}")

        supported_extensions = (".txt", ".md", ".json")
        entry_ids = []

        for file_path in path.rglob("*"):
            if file_path.suffix.lower() in supported_extensions:
                try:
                    with open(file_path, "r", encoding="utf-8") as f:
                        content = f.read()
                    
                    entry_id = self.add_document(
                        content=content,
                        title=file_path.stem,
                        source=str(file_path),
                        session_id=session_id
                    )
                    entry_ids.append(entry_id)
                    logger.info(f"Imported: {file_path}")
                except Exception as e:
                    logger.error(f"Failed to import {file_path}: {e}")

        return entry_ids

    def search(
        self,
        query: str,
        top_k: int = 5,
        session_id: str = "kb_global",
        min_similarity: float = 0.5
    ) -> List[Dict[str, Any]]:
        """检索相关文档"""
        results = self.vector_store.search(
            query=query,
            top_k=top_k,
            session_id=session_id,
            min_similarity=min_similarity
        )

        return [{
            "id": entry.id,
            "content": entry.content[:500] + "..." if len(entry.content) > 500 else entry.content,
            "title": entry.metadata.get("title", ""),
            "source": entry.metadata.get("source", ""),
            "similarity": round(similarity, 4),
            "importance": entry.importance,
            "created_at": entry.created_at.isoformat()
        } for entry, similarity in results]

    def qa(
        self,
        query: str,
        top_k: int = 3,
        session_id: str = "kb_global"
    ) -> Dict[str, Any]:
        """基于知识库问答"""
        results = self.search(query, top_k=top_k, session_id=session_id)
        
        if not results:
            return {
                "success": True,
                "answer": "未找到相关知识",
                "sources": [],
                "confidence": 0.0
            }

        context = "\n\n".join([f"【{r['title']}】\n{r['content']}" for r in results])
        
        answer = f"根据知识库内容，以下是关于「{query}」的信息：\n\n{context}"

        return {
            "success": True,
            "answer": answer,
            "sources": [{
                "title": r["title"],
                "similarity": r["similarity"]
            } for r in results],
            "confidence": sum(r["similarity"] for r in results) / len(results)
        }

    def delete_document(self, entry_id: str) -> bool:
        """删除文档"""
        return self.vector_store.delete(entry_id)

    def get_document(self, entry_id: str) -> Optional[Dict[str, Any]]:
        """获取文档详情"""
        entry = self.vector_store.get(entry_id)
        if entry:
            return {
                "id": entry.id,
                "content": entry.content,
                "title": entry.metadata.get("title", ""),
                "source": entry.metadata.get("source", ""),
                "created_at": entry.created_at.isoformat(),
                "access_count": entry.access_count
            }
        return None

    def get_stats(self) -> Dict[str, Any]:
        """获取知识库统计信息"""
        return self.vector_store.get_stats()


_kb_service: Optional[KBService] = None


def get_kb_service() -> KBService:
    global _kb_service
    if _kb_service is None:
        _kb_service = KBService()
    return _kb_service
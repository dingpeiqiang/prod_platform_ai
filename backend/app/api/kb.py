"""
知识库 API 路由
"""

from typing import Dict, Any, List, Optional
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from ..services.kb_service import get_kb_service

router = APIRouter(prefix="/api/kb", tags=["知识库"])
kb_service = get_kb_service()


class AddDocumentRequest(BaseModel):
    content: str
    title: Optional[str] = None
    source: Optional[str] = None
    importance: float = 1.0


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    min_similarity: float = 0.5


@router.post("/add", summary="添加文档")
async def add_document(request: AddDocumentRequest) -> Dict[str, Any]:
    try:
        entry_id = kb_service.add_document(
            content=request.content,
            title=request.title,
            source=request.source,
            importance=request.importance
        )
        return {
            "success": True,
            "entry_id": entry_id,
            "message": "文档添加成功"
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/import-dir", summary="从目录导入文档")
async def import_from_dir(dir_path: str = Query(...)) -> Dict[str, Any]:
    try:
        entry_ids = kb_service.add_documents_from_dir(dir_path)
        return {
            "success": True,
            "imported_count": len(entry_ids),
            "entry_ids": entry_ids,
            "message": f"成功导入 {len(entry_ids)} 个文档"
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/search", summary="检索文档")
async def search_documents(request: SearchRequest) -> Dict[str, Any]:
    try:
        results = kb_service.search(
            query=request.query,
            top_k=request.top_k,
            min_similarity=request.min_similarity
        )
        return {
            "success": True,
            "results": results,
            "count": len(results)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/qa", summary="知识库问答")
async def qa(request: SearchRequest) -> Dict[str, Any]:
    try:
        result = kb_service.qa(
            query=request.query,
            top_k=request.top_k
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/document/{entry_id}", summary="获取文档详情")
async def get_document(entry_id: str) -> Dict[str, Any]:
    try:
        document = kb_service.get_document(entry_id)
        if not document:
            raise HTTPException(status_code=404, detail="文档不存在")
        return document
    except HTTPException as e:
        raise e
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/document/{entry_id}", summary="删除文档")
async def delete_document(entry_id: str) -> Dict[str, Any]:
    try:
        success = kb_service.delete_document(entry_id)
        if not success:
            raise HTTPException(status_code=404, detail="文档不存在")
        return {
            "success": True,
            "message": "文档删除成功"
        }
    except HTTPException as e:
        raise e
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stats", summary="获取统计信息")
async def get_stats() -> Dict[str, Any]:
    try:
        stats = kb_service.get_stats()
        return {
            "success": True,
            "data": stats
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
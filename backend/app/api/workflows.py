"""
工作流管理API
"""
from fastapi import APIRouter, Depends, Query, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.core.database import get_db
from app.services.workflow_service import WorkflowService
from pydantic import BaseModel
from typing import List, Dict, Any

router = APIRouter(prefix="/api/workflows", tags=["workflows"])


class WorkflowCreateRequest(BaseModel):
    workflowCode: str
    workflowName: str
    description: Optional[str] = None
    category: Optional[str] = "general"
    tags: Optional[List[str]] = []
    priority: Optional[int] = 10
    isActive: Optional[bool] = True
    workflowData: Optional[Dict[str, Any]] = {}


class WorkflowUpdateRequest(BaseModel):
    workflowName: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    tags: Optional[List[str]] = None
    priority: Optional[int] = None
    isActive: Optional[bool] = None
    workflowData: Optional[Dict[str, Any]] = None
    changeNote: Optional[str] = None


class ExecutionCreateRequest(BaseModel):
    inputData: Optional[Dict[str, Any]] = {}
    triggerType: Optional[str] = "manual"
    notes: Optional[str] = None


class ExecutionUpdateRequest(BaseModel):
    status: Optional[str] = None
    startTime: Optional[str] = None
    endTime: Optional[str] = None
    durationSeconds: Optional[int] = None
    outputData: Optional[Dict[str, Any]] = None
    errorMessage: Optional[str] = None
    executionLogs: Optional[List[Dict[str, Any]]] = None


@router.get("")
async def list_workflows(
    category: Optional[str] = Query(None, description="分类过滤"),
    isActive: Optional[bool] = Query(None, description="启用状态过滤"),
    db: Session = Depends(get_db)
):
    """获取工作流列表"""
    result = WorkflowService.list_workflows(db, category=category, is_active=isActive)
    if not result["success"]:
        raise HTTPException(status_code=500, detail=result["message"])
    return result


@router.get("/categories")
async def get_categories():
    """获取工作流分类列表"""
    return WorkflowService.get_categories()


@router.get("/{workflow_code}")
async def get_workflow(workflow_code: str, db: Session = Depends(get_db)):
    """获取单个工作流详情"""
    result = WorkflowService.get_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.get("/{workflow_code}/history")
async def get_workflow_history(workflow_code: str, db: Session = Depends(get_db)):
    """获取工作流版本历史"""
    result = WorkflowService.get_workflow_history(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.get("/{workflow_code}/executions")
async def list_executions(workflow_code: str, limit: int = Query(50), db: Session = Depends(get_db)):
    """获取工作流执行历史"""
    result = WorkflowService.list_executions(workflow_code, db, limit=limit)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.post("")
async def create_workflow(request: WorkflowCreateRequest, db: Session = Depends(get_db)):
    """创建工作流"""
    result = WorkflowService.create_workflow(request.dict(), db)
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.put("/{workflow_code}")
async def update_workflow(workflow_code: str, request: WorkflowUpdateRequest, db: Session = Depends(get_db)):
    """更新工作流"""
    result = WorkflowService.update_workflow(workflow_code, request.dict(exclude_unset=True), db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.delete("/{workflow_code}")
async def delete_workflow(workflow_code: str, db: Session = Depends(get_db)):
    """删除工作流"""
    result = WorkflowService.delete_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.post("/{workflow_code}/toggle")
async def toggle_workflow(workflow_code: str, db: Session = Depends(get_db)):
    """切换工作流启用状态"""
    result = WorkflowService.toggle_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.post("/{workflow_code}/executions")
async def create_execution(workflow_code: str, request: ExecutionCreateRequest, db: Session = Depends(get_db)):
    """创建工作流执行记录"""
    result = WorkflowService.create_execution(workflow_code, request.dict(), db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result


@router.put("/executions/{execution_id}")
async def update_execution_status(execution_id: str, request: ExecutionUpdateRequest, db: Session = Depends(get_db)):
    """更新执行状态"""
    result = WorkflowService.update_execution_status(execution_id, request.dict(exclude_unset=True), db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    return result

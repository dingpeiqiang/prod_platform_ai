"""
工作流调度器API端点

提供基于自然语言的工作流调度能力
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import logging

from app.engine.llm_workflow_scheduler import get_workflow_scheduler, DynamicWorkflowGenerator

logger = logging.getLogger("scheduler_api")

router = APIRouter(prefix="/api/scheduler", tags=["scheduler"])


class RegisterRequest(BaseModel):
    """注册工作流请求"""
    workflow_id: str
    workflow_def: Dict[str, Any]
    description: str


class ScheduleRequest(BaseModel):
    """调度请求"""
    prompt: str


class GenerateRequest(BaseModel):
    """生成工作流请求"""
    requirements: str


@router.post("/register")
async def register_workflow(request: RegisterRequest):
    """注册工作流到调度器"""
    try:
        scheduler = get_workflow_scheduler()
        scheduler.register_workflow(request.workflow_id, request.workflow_def, request.description)
        
        logger.info(f"工作流已注册: {request.workflow_id}")
        
        return {
            "success": True,
            "message": f"工作流 '{request.workflow_id}' 注册成功",
            "workflow_count": len(scheduler.list_workflows())
        }
    
    except Exception as e:
        logger.error(f"注册工作流失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/register/{workflow_id}")
async def unregister_workflow(workflow_id: str):
    """从调度器移除工作流"""
    try:
        scheduler = get_workflow_scheduler()
        scheduler.unregister_workflow(workflow_id)
        
        return {
            "success": True,
            "message": f"工作流 '{workflow_id}' 已移除"
        }
    
    except Exception as e:
        logger.error(f"移除工作流失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/workflows")
async def list_workflows():
    """获取已注册的工作流列表"""
    scheduler = get_workflow_scheduler()
    workflows = scheduler.list_workflows()
    
    return {
        "success": True,
        "data": workflows,
        "count": len(workflows)
    }


@router.post("/schedule")
async def schedule_workflow(request: ScheduleRequest):
    """通过自然语言调度工作流"""
    try:
        scheduler = get_workflow_scheduler()
        result = await scheduler.schedule_by_prompt(request.prompt)
        
        return result
    
    except Exception as e:
        logger.error(f"调度工作流失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/schedule/choice")
async def schedule_with_choice(request: ScheduleRequest):
    """分析意图并返回可选工作流"""
    try:
        scheduler = get_workflow_scheduler()
        result = await scheduler.schedule_with_choice(request.prompt)
        
        return {
            "success": True,
            **result
        }
    
    except Exception as e:
        logger.error(f"分析意图失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/execute/batch")
async def execute_batch_workflows(workflow_ids: List[str], params_list: Optional[List[Dict[str, Any]]] = None, parallel: bool = False):
    """批量执行工作流"""
    try:
        scheduler = get_workflow_scheduler()
        
        if parallel:
            results = await scheduler.execute_parallel_workflows(workflow_ids, params_list or [])
        else:
            results = await scheduler.execute_sequential_workflows(workflow_ids, params_list or [])
        
        return {
            "success": True,
            "results": results,
            "execution_mode": "parallel" if parallel else "sequential"
        }
    
    except Exception as e:
        logger.error(f"批量执行失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate")
async def generate_workflow(request: GenerateRequest):
    """根据自然语言需求生成工作流"""
    try:
        generator = DynamicWorkflowGenerator()
        workflow_def = await generator.generate_workflow(request.requirements)
        
        if "error" in workflow_def:
            return {
                "success": False,
                "error": workflow_def["error"]
            }
        
        return {
            "success": True,
            "workflow_def": workflow_def
        }
    
    except Exception as e:
        logger.error(f"生成工作流失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/optimize")
async def optimize_workflow(workflow_def: Dict[str, Any]):
    """优化工作流定义"""
    try:
        generator = DynamicWorkflowGenerator()
        optimized = await generator.optimize_workflow(workflow_def)
        
        return {
            "success": True,
            "workflow_def": optimized
        }
    
    except Exception as e:
        logger.error(f"优化工作流失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))
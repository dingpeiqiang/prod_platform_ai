"""
工作流执行API端点

提供工作流的执行接口，支持同步执行、流式执行和用户交互模式
"""

from fastapi import APIRouter, HTTPException, BackgroundTasks
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import asyncio
import json
from app.core.logger import get_logger

logger = get_logger(__name__)

from app.langchain.workflow_engine import WorkflowEngine, WorkflowStatus
from app.langchain.workflow_converter import WorkflowConverter
from app.langchain.workflow_init import workflow_engine


router = APIRouter(prefix="/api/execution", tags=["execution"])


class ExecuteRequest(BaseModel):
    """工作流执行请求"""
    workflow_def: Dict[str, Any]
    inputs: Optional[Dict[str, Any]] = {}


class ExecutionResponse(BaseModel):
    """工作流执行响应"""
    workflow_id: str
    status: str
    outputs: Dict[str, Any]
    waiting_form: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class ResumeRequest(BaseModel):
    """恢复工作流执行请求"""
    workflow_id: str
    form_data: Dict[str, Any]


@router.post("/execute", response_model=ExecutionResponse)
async def execute_workflow(request: ExecuteRequest):
    """执行工作流（支持等待用户输入）
    
    如果工作流包含表单节点，执行到表单节点时会暂停，
    返回 waiting_form 供前端显示表单。
    """
    try:
        converter = WorkflowConverter()
        engine_workflow = converter.convert(request.workflow_def)
        
        workflow_engine.register_workflow(engine_workflow)
        
        events = []
        async for event in workflow_engine.run(engine_workflow.id, request.inputs):
            events.append(event)
            
            if event["type"] == "workflow_waiting":
                return {
                    "workflow_id": event["workflow_id"],
                    "status": "waiting",
                    "outputs": {},
                    "waiting_form": event.get("waiting_form"),
                    "message": event.get("message")
                }
        
        final_event = events[-1] if events else {}
        
        if final_event.get("type") == "workflow_complete":
            return {
                "workflow_id": final_event["workflow_id"],
                "status": "completed",
                "outputs": final_event.get("outputs", {}),
                "waiting_form": None,
                "error": None
            }
        elif final_event.get("type") == "workflow_failed":
            return {
                "workflow_id": final_event["workflow_id"],
                "status": "failed",
                "outputs": {},
                "waiting_form": None,
                "error": final_event.get("error")
            }
        
        return {
            "workflow_id": "",
            "status": "unknown",
            "outputs": {},
            "waiting_form": None,
            "error": "Unknown execution state"
        }
    
    except Exception as e:
        logger.error(f"Workflow execution failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/execute/stream")
async def execute_workflow_streaming(request: ExecuteRequest):
    """流式执行工作流
    
    返回 SSE 流，包含执行过程中的每个步骤事件。
    当遇到需要用户输入的表单时，会发送 workflow_waiting 事件。
    """
    try:
        converter = WorkflowConverter()
        engine_workflow = converter.convert(request.workflow_def)
        
        workflow_engine.register_workflow(engine_workflow)
        
        async def generate():
            async for event in workflow_engine.run(engine_workflow.id, request.inputs):
                yield f"data: {json.dumps(event)}\n\n"
                await asyncio.sleep(0.01)
        
        return StreamingResponse(
            generate(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "Access-Control-Allow-Origin": "*"
            }
        )
    
    except Exception as e:
        logger.error(f"Streaming execution failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/resume", response_model=ExecutionResponse)
async def resume_workflow(request: ResumeRequest):
    """恢复工作流执行（用户提交表单后调用）
    
    Args:
        request.workflow_id: 工作流执行ID（从 waiting 响应中获取）
        request.form_data: 用户提交的表单数据
    
    Returns:
        继续执行后的结果，如果还有表单节点会再次返回 waiting
    """
    try:
        context = workflow_engine.get_context(request.workflow_id)
        
        if not context:
            raise HTTPException(
                status_code=404,
                detail=f"未找到工作流执行上下文: {request.workflow_id}，可能已过期或已完成"
            )
        
        if context.status != WorkflowStatus.WAITING:
            raise HTTPException(
                status_code=400,
                detail=f"工作流不在等待状态，当前状态: {context.status}"
            )
        
        events = []
        async for event in workflow_engine.resume(request.workflow_id, request.form_data):
            events.append(event)
            
            if event["type"] == "workflow_waiting":
                return {
                    "workflow_id": event["workflow_id"],
                    "status": "waiting",
                    "outputs": {},
                    "waiting_form": event.get("waiting_form"),
                    "message": event.get("message")
                }
        
        final_event = events[-1] if events else {}
        
        if final_event.get("type") == "workflow_complete":
            return {
                "workflow_id": final_event["workflow_id"],
                "status": "completed",
                "outputs": final_event.get("outputs", {}),
                "waiting_form": None,
                "error": None
            }
        elif final_event.get("type") == "workflow_failed":
            return {
                "workflow_id": final_event["workflow_id"],
                "status": "failed",
                "outputs": {},
                "waiting_form": None,
                "error": final_event.get("error")
            }
        
        return {
            "workflow_id": request.workflow_id,
            "status": "unknown",
            "outputs": {},
            "waiting_form": None,
            "error": "Unknown execution state"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Workflow resume failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/status/{workflow_id}")
async def get_workflow_status(workflow_id: str):
    """获取工作流执行状态
    
    用于轮询检查工作流是否完成或需要用户输入。
    """
    try:
        context = workflow_engine.get_context(workflow_id)
        
        if not context:
            return {
                "workflow_id": workflow_id,
                "status": "not_found",
                "message": "工作流执行不存在或已过期"
            }
        
        return {
            "workflow_id": context.workflow_id,
            "status": context.status.value if isinstance(context.status, WorkflowStatus) else context.status,
            "waiting_form": context.waiting_form,
            "current_step_id": context.current_step_id,
            "outputs": context.outputs,
            "errors": context.errors,
            "logs": context.logs
        }
    
    except Exception as e:
        logger.error(f"Get workflow status failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/validate")
async def validate_workflow(workflow_def: Dict[str, Any]):
    """验证工作流定义"""
    errors = []
    warnings = []
    
    try:
        nodes = workflow_def.get("nodes", [])
        edges = workflow_def.get("edges", [])
        
        if len(nodes) == 0:
            errors.append({"type": "empty", "message": "工作流为空", "suggestion": "请添加节点"})
            return {"valid": False, "errors": errors, "warnings": warnings}
        
        start_nodes = [n for n in nodes if n.get("type") == "start"]
        if len(start_nodes) == 0:
            errors.append({"type": "missing_start", "message": "缺少开始节点", "suggestion": "请添加开始节点"})
        elif len(start_nodes) > 1:
            errors.append({"type": "multiple_start", "message": f"存在{len(start_nodes)}个开始节点", "suggestion": "工作流只能有一个开始节点"})
        
        end_nodes = [n for n in nodes if n.get("type") == "end"]
        if len(end_nodes) == 0:
            warnings.append({"type": "missing_end", "message": "缺少结束节点", "suggestion": "建议添加结束节点"})
        
        if len(edges) == 0:
            warnings.append({"type": "no_edges", "message": "没有连接线", "suggestion": "请连接节点形成完整的工作流"})
        
        node_ids = {n["id"] for n in nodes}
        connected_ids = set()
        
        for edge in edges:
            if "source" in edge:
                connected_ids.add(edge["source"])
            if "target" in edge:
                connected_ids.add(edge["target"])
        
        for node in nodes:
            if node["id"] not in connected_ids and node.get("type") != "start":
                warnings.append({
                    "type": "isolated_node",
                    "message": f"节点 '{node.get('data', {}).get('label', node['id'])}' 孤立",
                    "suggestion": "请连接该节点"
                })
        
        return {"valid": len(errors) == 0, "errors": errors, "warnings": warnings}
    
    except Exception as e:
        logger.error(f"Workflow validation failed: {e}")
        return {"valid": False, "errors": [{"type": "validation_error", "message": str(e)}], "warnings": []}


@router.get("/node-types")
async def get_node_types():
    """获取支持的节点类型列表"""
    return {
        "success": True,
        "data": [
            {"type": "start", "name": "开始", "description": "工作流入口，可定义输入参数", "icon": "🚀"},
            {"type": "end", "name": "结束", "description": "工作流出口", "icon": "🏁"},
            {"type": "form", "name": "表单", "description": "显示表单供用户填写", "icon": "📋"},
            {"type": "prompt", "name": "提示词", "description": "构建LLM提示词，支持变量替换", "icon": "📝"},
            {"type": "llm", "name": "LLM调用", "description": "调用大语言模型生成响应", "icon": "🤖"},
            {"type": "condition", "name": "条件分支", "description": "根据条件判断执行不同分支", "icon": "🔀"},
            {"type": "loop", "name": "循环", "description": "循环执行一组节点", "icon": "🔄"},
            {"type": "variable", "name": "变量赋值", "description": "设置工作流变量", "icon": "📦"},
            {"type": "http", "name": "HTTP请求", "description": "发送HTTP请求", "icon": "🌐"},
            {"type": "code", "name": "代码执行", "description": "执行Python代码", "icon": "💻"},
            {"type": "tool", "name": "工具调用", "description": "调用外部工具", "icon": "🔧"}
        ]
    }

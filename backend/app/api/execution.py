"""
工作流执行API端点

提供工作流的执行接口，支持同步执行和流式执行两种模式
"""

from fastapi import APIRouter, HTTPException, BackgroundTasks
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import asyncio
import json
import logging

from app.engine.workflow_executor import WorkflowExecutor

logger = logging.getLogger("execution_api")

router = APIRouter(prefix="/api/execution", tags=["execution"])


class ExecuteRequest(BaseModel):
    """工作流执行请求"""
    workflow_def: Dict[str, Any]
    inputs: Optional[Dict[str, Any]] = {}
    use_lcel: Optional[bool] = False


class ExecutionResponse(BaseModel):
    """工作流执行响应"""
    workflow_id: str
    status: str
    outputs: Dict[str, Any]
    error: Optional[str] = None
    node_statuses: Optional[Dict[str, str]] = None


@router.post("/execute", response_model=ExecutionResponse)
async def execute_workflow(request: ExecuteRequest):
    """同步执行工作流"""
    try:
        executor = WorkflowExecutor(request.workflow_def, use_lcel=request.use_lcel)
        context = await executor.execute(request.inputs)
        
        return {
            "workflow_id": context.workflow_id,
            "status": context.status.value,
            "outputs": context.outputs,
            "error": context.error,
            "node_statuses": {k: v.value for k, v in context.node_statuses.items()}
        }
    
    except Exception as e:
        logger.error(f"Workflow execution failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/execute/stream")
async def execute_workflow_streaming(request: ExecuteRequest):
    """流式执行工作流"""
    try:
        executor = WorkflowExecutor(request.workflow_def)
        
        async def generate():
            async for message in executor.execute_streaming(request.inputs):
                yield f"data: {json.dumps(message)}\n\n"
                await asyncio.sleep(0.01)  # 控制流速度
        
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


@router.post("/validate")
async def validate_workflow(workflow_def: Dict[str, Any]):
    """验证工作流定义"""
    errors = []
    warnings = []
    
    try:
        nodes = workflow_def.get("nodes", [])
        edges = workflow_def.get("edges", [])
        
        # 检查节点数量
        if len(nodes) == 0:
            errors.append({"type": "empty", "message": "工作流为空", "suggestion": "请添加节点"})
            return {"valid": False, "errors": errors, "warnings": warnings}
        
        # 检查开始节点
        start_nodes = [n for n in nodes if n.get("type") == "start"]
        if len(start_nodes) == 0:
            errors.append({"type": "missing_start", "message": "缺少开始节点", "suggestion": "请添加开始节点"})
        elif len(start_nodes) > 1:
            errors.append({"type": "multiple_start", "message": f"存在{len(start_nodes)}个开始节点", "suggestion": "工作流只能有一个开始节点"})
        
        # 检查结束节点
        end_nodes = [n for n in nodes if n.get("type") == "end"]
        if len(end_nodes) == 0:
            warnings.append({"type": "missing_end", "message": "缺少结束节点", "suggestion": "建议添加结束节点"})
        
        # 检查连接
        if len(edges) == 0:
            warnings.append({"type": "no_edges", "message": "没有连接线", "suggestion": "请连接节点形成完整的工作流"})
        
        # 检查孤立节点
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
            {
                "type": "start",
                "name": "开始",
                "description": "工作流入口，可定义输入参数",
                "icon": "🚀"
            },
            {
                "type": "end",
                "name": "结束",
                "description": "工作流出口",
                "icon": "🏁"
            },
            {
                "type": "prompt",
                "name": "提示词",
                "description": "构建LLM提示词，支持变量替换",
                "icon": "📝"
            },
            {
                "type": "llm",
                "name": "LLM调用",
                "description": "调用大语言模型生成响应",
                "icon": "🤖"
            },
            {
                "type": "condition",
                "name": "条件分支",
                "description": "根据条件判断执行不同分支",
                "icon": "🔀"
            },
            {
                "type": "loop",
                "name": "循环",
                "description": "循环执行一组节点",
                "icon": "🔄"
            },
            {
                "type": "variable",
                "name": "变量赋值",
                "description": "设置工作流变量",
                "icon": "📦"
            },
            {
                "type": "http",
                "name": "HTTP请求",
                "description": "发送HTTP请求",
                "icon": "🌐"
            },
            {
                "type": "code",
                "name": "代码执行",
                "description": "执行Python代码",
                "icon": "💻"
            },
            {
                "type": "parser",
                "name": "输出解析",
                "description": "解析LLM输出",
                "icon": "📊"
            },
            {
                "type": "tool",
                "name": "工具调用",
                "description": "调用外部工具",
                "icon": "🔧"
            }
        ]
    }
"""
可视化 API
提供工作流执行的可视化数据接口
"""

from fastapi import APIRouter, HTTPException
from fastapi.websockets import WebSocket, WebSocketDisconnect
from typing import Dict, Any, List, Optional
from datetime import datetime
import json

from app.harness.observability.tracer import get_tracer, SpanStatus
from app.services.chat_service_v2 import ChatServiceV2

router = APIRouter(prefix="/api/visualization", tags=["visualization"])

# WebSocket 连接管理
active_connections: List[WebSocket] = []


@router.websocket("/ws/{trace_id}")
async def websocket_endpoint(websocket: WebSocket, trace_id: str):
    """实时推送执行状态"""
    await websocket.accept()
    active_connections.append(websocket)
    
    try:
        while True:
            data = await websocket.receive_text()
            payload = json.loads(data)
            
            if payload.get("action") == "subscribe":
                # 发送当前追踪数据
                tracer = get_tracer()
                trace_data = tracer.export_trace(trace_id)
                await websocket.send_json({
                    "type": "trace_update",
                    "data": trace_data
                })
                
    except WebSocketDisconnect:
        active_connections.remove(websocket)


@router.get("/traces")
async def get_traces(
    limit: int = 20,
    start_time: Optional[str] = None,
    end_time: Optional[str] = None
) -> Dict[str, Any]:
    """获取追踪列表"""
    from datetime import datetime as dt
    start_dt = dt.fromisoformat(start_time) if start_time else None
    end_dt = dt.fromisoformat(end_time) if end_time else None
    
    tracer = get_tracer()
    traces = tracer.export_traces(start_dt, end_dt, limit)
    
    return {
        "success": True,
        "data": traces,
        "count": len(traces)
    }


@router.get("/traces/{trace_id}")
async def get_trace_detail(trace_id: str) -> Dict[str, Any]:
    """获取追踪详情"""
    tracer = get_tracer()
    trace_data = tracer.export_trace(trace_id)
    
    if not trace_data:
        raise HTTPException(status_code=404, detail="Trace not found")
    
    analysis = tracer.analyze_trace(trace_id)
    
    return {
        "success": True,
        "trace": trace_data,
        "analysis": analysis
    }


@router.get("/traces/{trace_id}/flow")
async def get_flow_diagram(trace_id: str) -> Dict[str, Any]:
    """获取工作流流程图数据"""
    tracer = get_tracer()
    trace_data = tracer.export_trace(trace_id)
    
    if not trace_data:
        raise HTTPException(status_code=404, detail="Trace not found")
    
    # 构建流程图数据
    nodes = []
    edges = []
    
    for span in trace_data["spans"]:
        node_id = span["span_id"]
        node_name = span["name"]
        
        # 创建节点
        nodes.append({
            "id": node_id,
            "name": node_name,
            "status": span["status"],
            "duration_ms": span["duration_ms"],
            "start_time": span["start_time"],
            "end_time": span["end_time"],
            "component": span.get("component", "unknown"),
            "tags": span.get("tags", {})
        })
        
        # 创建边（父子关系）
        if span["parent_span_id"]:
            edges.append({
                "from": span["parent_span_id"],
                "to": node_id,
                "label": ""
            })
    
    return {
        "success": True,
        "trace_id": trace_id,
        "nodes": nodes,
        "edges": edges,
        "start_time": trace_data.get("start_time"),
        "end_time": trace_data.get("end_time"),
        "total_duration_ms": trace_data.get("total_duration_ms")
    }


@router.get("/stats")
async def get_stats() -> Dict[str, Any]:
    """获取追踪统计"""
    tracer = get_tracer()
    stats = tracer.get_stats()
    
    return {
        "success": True,
        "data": stats
    }


@router.delete("/traces/{trace_id}")
async def delete_trace(trace_id: str) -> Dict[str, Any]:
    """删除追踪记录"""
    tracer = get_tracer()
    trace_data = tracer.export_trace(trace_id)
    
    if not trace_data:
        raise HTTPException(status_code=404, detail="Trace not found")
    
    # 清理追踪数据（简化实现）
    tracer._trace_spans.pop(trace_id, None)
    
    return {
        "success": True,
        "message": "Trace deleted"
    }


@router.post("/traces/test")
async def test_trace() -> Dict[str, Any]:
    """测试追踪功能"""
    tracer = get_tracer()
    
    # 添加测试追踪
    span1 = tracer.start_span("test_operation", trace_id="test_trace_id", component="test")
    span1.add_tag("status", "success")
    tracer.finish_span(span1, SpanStatus.OK)
    
    # 获取统计
    stats = tracer.get_stats()
    traces = tracer.export_traces()
    
    return {
        "success": True,
        "message": "Test trace added successfully",
        "stats": stats,
        "trace_count": len(traces)
    }

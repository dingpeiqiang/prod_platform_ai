# 工作流 MCP 工具封装
# 将工作流执行能力暴露为标准化 MCP 工具

from typing import Dict, Any, List, Optional
from .tool_hub import mcptool


# ============================================================
# 工作流执行工具
# ============================================================

@mcptool(
    name="execute_workflow",
    description="执行指定的工作流。当需要运行预定义的业务流程时使用，如订单处理、审批流程、数据校验等复杂多步骤任务。",
    category="workflow"
)
def execute_workflow(workflow_code: str, inputs: Dict[str, Any] = None) -> Dict[str, Any]:
    """
    执行工作流
    
    Args:
        workflow_code: 工作流编码（唯一标识）
        inputs: 输入参数字典
        
    Returns:
        执行结果，包含 execution_id、status、outputs 等信息
    """
    from ..services.workflow_service import WorkflowService
    from ..engine.workflow_executor import WorkflowExecutor
    from app.core.database import SessionLocal
    import asyncio
    
    if inputs is None:
        inputs = {}
    
    db = SessionLocal()
    try:
        # 1. 获取工作流定义
        workflow_result = WorkflowService.get_workflow(workflow_code, db)
        
        if not workflow_result["success"]:
            return {
                "success": False,
                "error": f"工作流 '{workflow_code}' 不存在或已被禁用"
            }
        
        workflow_data = workflow_result["data"]
        
        # 检查工作流是否激活
        if not workflow_data.get("isActive", True):
            return {
                "success": False,
                "error": f"工作流 '{workflow_code}' 已被禁用"
            }
        
        # 2. 获取工作流定义（nodes 和 edges）
        workflow_def = workflow_data.get("workflowData", {})
        
        if not workflow_def or not workflow_def.get("nodes"):
            return {
                "success": False,
                "error": f"工作流 '{workflow_code}' 定义不完整"
            }
        
        # 3. 执行工作流
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute(inputs))
        
        # 4. 返回执行结果
        return {
            "success": context.status.value == "completed",
            "result": {
                "execution_id": context.workflow_id,
                "status": context.status.value,
                "outputs": context.outputs,
                "error": context.error,
                "node_statuses": {k: v.value for k, v in context.node_statuses.items()}
            }
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": f"工作流执行失败: {str(e)}"
        }
    finally:
        db.close()


# ============================================================
# 工作流列表工具
# ============================================================

@mcptool(
    name="list_workflows",
    description="列出所有可用的工作流。用于查询系统中有哪些工作流可以执行。",
    category="workflow"
)
def list_workflows(category: str = None, active_only: bool = True) -> Dict[str, Any]:
    """
    列出工作流
    
    Args:
        category: 可选的分类过滤（如 'order', 'approval', 'general'）
        active_only: 是否只返回激活的工作流（默认 True）
        
    Returns:
        工作流列表，包含 code、name、description、category 等信息
    """
    from ..services.workflow_service import WorkflowService
    from app.core.database import SessionLocal
    
    db = SessionLocal()
    try:
        is_active = True if active_only else None
        result = WorkflowService.list_workflows(db, category=category, is_active=is_active)
        
        if not result["success"]:
            return {
                "success": False,
                "error": result.get("message", "获取工作流列表失败")
            }
        
        # 简化返回数据，只保留必要信息
        workflows = []
        for wf in result.get("data", []):
            workflows.append({
                "workflowCode": wf.get("workflowCode"),
                "workflowName": wf.get("workflowName"),
                "description": wf.get("description"),
                "category": wf.get("category"),
                "tags": wf.get("tags", []),
                "priority": wf.get("priority"),
                "version": wf.get("version")
            })
        
        return {
            "success": True,
            "result": {
                "total": result.get("total", 0),
                "workflows": workflows
            }
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": f"获取工作流列表失败: {str(e)}"
        }
    finally:
        db.close()


# ============================================================
# 工作流详情工具
# ============================================================

@mcptool(
    name="get_workflow_detail",
    description="获取指定工作流的详细信息，包括节点定义、输入输出参数说明等。",
    category="workflow"
)
def get_workflow_detail(workflow_code: str) -> Dict[str, Any]:
    """
    获取工作流详情
    
    Args:
        workflow_code: 工作流编码
        
    Returns:
        工作流详细信息
    """
    from ..services.workflow_service import WorkflowService
    from app.core.database import SessionLocal
    
    db = SessionLocal()
    try:
        result = WorkflowService.get_workflow(workflow_code, db)
        
        if not result["success"]:
            return {
                "success": False,
                "error": result.get("message", f"工作流 '{workflow_code}' 不存在")
            }
        
        workflow_data = result["data"]
        
        # 提取关键信息
        workflow_def = workflow_data.get("workflowData", {})
        nodes = workflow_def.get("nodes", [])
        
        # 分析输入输出参数
        input_params = []
        output_params = []
        
        for node in nodes:
            if node.get("type") == "start":
                # 开始节点的输入参数
                start_data = node.get("data", {})
                params = start_data.get("params", [])
                for param in params:
                    input_params.append({
                        "name": param.get("name"),
                        "type": param.get("type", "string"),
                        "required": param.get("required", False),
                        "description": param.get("description", "")
                    })
            
            elif node.get("type") == "end":
                # 结束节点的输出参数
                end_data = node.get("data", {})
                outputs = end_data.get("outputs", [])
                for output in outputs:
                    output_params.append({
                        "name": output.get("name"),
                        "description": output.get("description", "")
                    })
        
        return {
            "success": True,
            "result": {
                "workflowCode": workflow_data.get("workflowCode"),
                "workflowName": workflow_data.get("workflowName"),
                "description": workflow_data.get("description"),
                "category": workflow_data.get("category"),
                "tags": workflow_data.get("tags", []),
                "version": workflow_data.get("version"),
                "isActive": workflow_data.get("isActive"),
                "inputParams": input_params,
                "outputParams": output_params,
                "nodeCount": len(nodes),
                "edgeCount": len(workflow_def.get("edges", []))
            }
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": f"获取工作流详情失败: {str(e)}"
        }
    finally:
        db.close()


# ============================================================
# 工作流执行历史工具
# ============================================================

@mcptool(
    name="get_workflow_execution_history",
    description="查询工作流的执行历史记录。",
    category="workflow"
)
def get_workflow_execution_history(workflow_code: str, limit: int = 10) -> Dict[str, Any]:
    """
    获取工作流执行历史
    
    Args:
        workflow_code: 工作流编码
        limit: 返回记录数量限制（默认 10）
        
    Returns:
        执行历史列表
    """
    from ..services.workflow_service import WorkflowService
    from app.core.database import SessionLocal
    
    db = SessionLocal()
    try:
        result = WorkflowService.list_executions(workflow_code, db, limit=limit)
        
        if not result["success"]:
            return {
                "success": False,
                "error": result.get("message", "获取执行历史失败")
            }
        
        executions = []
        for exec_data in result.get("data", []):
            executions.append({
                "executionId": exec_data.get("executionId"),
                "status": exec_data.get("status"),
                "startTime": exec_data.get("startTime"),
                "endTime": exec_data.get("endTime"),
                "durationSeconds": exec_data.get("durationSeconds"),
                "triggerType": exec_data.get("triggerType"),
                "triggeredBy": exec_data.get("triggeredBy")
            })
        
        return {
            "success": True,
            "result": {
                "total": len(executions),
                "executions": executions
            }
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": f"获取执行历史失败: {str(e)}"
        }
    finally:
        db.close()

"""
工作流管理API
"""
from fastapi import APIRouter, Depends, Query, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.core.database import get_db
from app.services.workflow_service import WorkflowService
from app.services.workflow_generator import get_workflow_generator
from pydantic import BaseModel
from typing import List, Dict, Any

router = APIRouter(prefix="/api/workflows", tags=["workflows"])


class VariableInfo(BaseModel):
    """变量信息"""
    name: str
    type: str
    source: str
    sourceNodeId: Optional[str] = None
    sourceNodeType: Optional[str] = None
    description: Optional[str] = ""
    preview: Optional[str] = ""


class AvailableVariablesResponse(BaseModel):
    """可用变量响应"""
    variables: List[VariableInfo]
    workflowCode: str


class WorkflowCreateRequest(BaseModel):
    workflowCode: str
    workflowName: str
    description: Optional[str] = None
    category: Optional[str] = "general"
    tags: Optional[List[str]] = []
    priority: Optional[int] = 10
    isActive: Optional[bool] = True
    isInLibrary: Optional[bool] = False
    workflowData: Optional[Dict[str, Any]] = {}


class WorkflowUpdateRequest(BaseModel):
    workflowName: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    tags: Optional[List[str]] = None
    priority: Optional[int] = None
    isActive: Optional[bool] = None
    isInLibrary: Optional[bool] = None
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


@router.get("/{workflow_code}/variables")
async def get_workflow_variables(
    workflow_code: str,
    node_id: Optional[str] = Query(None, description="节点ID，用于获取该节点之前可用的变量"),
    type_filter: Optional[str] = Query(None, description="类型过滤：string, number, boolean, object, array"),
    db: Session = Depends(get_db)
):
    """获取工作流可用变量列表
    
    根据工作流定义分析所有可用变量，支持按节点位置和类型过滤。
    如果指定node_id，只返回该节点之前可用的变量。
    """
    result = WorkflowService.get_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    
    workflow_data = result.get("data", {}).get("workflowData", {})
    variables = _analyze_workflow_variables(workflow_data, node_id, type_filter)
    
    return {
        "workflowCode": workflow_code,
        "variables": variables,
        "total": len(variables)
    }


def _analyze_workflow_variables(workflow_data: Dict[str, Any], node_id: Optional[str] = None, 
                                type_filter: Optional[str] = None) -> List[Dict[str, Any]]:
    """分析工作流定义，提取可用变量"""
    variables = []
    
    # 1. 工作流输入参数
    input_params = workflow_data.get("inputs", [])
    for param in input_params:
        var_info = {
            "name": param.get("key", ""),
            "type": param.get("type", "string"),
            "source": "workflow_input",
            "sourceNodeId": None,
            "sourceNodeType": None,
            "description": param.get("label", "") + " (工作流输入)",
            "preview": ""
        }
        if not type_filter or var_info["type"] == type_filter:
            variables.append(var_info)
    
    # 2. 节点输出变量
    nodes = workflow_data.get("nodes", [])
    edges = workflow_data.get("edges", [])
    
    # 如果指定了node_id，找出该节点之前的所有节点
    if node_id:
        preceding_nodes = _find_preceding_nodes(nodes, edges, node_id)
        nodes_to_check = [n for n in nodes if n.get("id") in preceding_nodes]
    else:
        nodes_to_check = nodes
    
    for node in nodes_to_check:
        node_id = node.get("id")
        node_type = node.get("type")
        node_data = node.get("data", {})
        
        # 根据节点类型提取输出变量
        node_variables = _extract_node_variables(node_type, node_data, node_id)
        for var in node_variables:
            if not type_filter or var["type"] == type_filter:
                variables.append(var)
    
    # 3. 系统变量
    system_vars = [
        {
            "name": "__node_output__",
            "type": "any",
            "source": "system",
            "sourceNodeId": None,
            "sourceNodeType": None,
            "description": "前一个节点的输出",
            "preview": ""
        },
        {
            "name": "__output__",
            "type": "any",
            "source": "system",
            "sourceNodeId": None,
            "sourceNodeType": None,
            "description": "当前节点的输出（用于outputs映射）",
            "preview": ""
        }
    ]
    for var in system_vars:
        if not type_filter or var["type"] == type_filter:
            variables.append(var)
    
    # 去重并按名称排序
    seen = set()
    unique_vars = []
    for var in variables:
        key = var["name"]
        if key not in seen:
            seen.add(key)
            unique_vars.append(var)
    
    unique_vars.sort(key=lambda x: x["name"])
    
    return unique_vars


def _find_preceding_nodes(nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]], 
                          target_node_id: str) -> set:
    """找出目标节点之前的所有节点（通过边连接）"""
    preceding = set()
    visited = set()
    queue = [target_node_id]
    
    while queue:
        current_id = queue.pop(0)
        if current_id in visited:
            continue
        visited.add(current_id)
        
        # 找出所有指向当前节点的边
        incoming_edges = [e for e in edges if e.get("target") == current_id]
        for edge in incoming_edges:
            source_id = edge.get("source")
            if source_id and source_id not in visited:
                preceding.add(source_id)
                queue.append(source_id)
    
    return preceding


def _extract_node_variables(node_type: str, node_data: Dict[str, Any], node_id: str) -> List[Dict[str, Any]]:
    """从节点定义中提取输出变量"""
    variables = []
    
    # 根据节点类型提取变量
    output_mappings = node_data.get("outputs", {})
    if output_mappings:
        for var_name, source_expr in output_mappings.items():
            var_info = {
                "name": var_name,
                "type": "any",
                "source": "node_output",
                "sourceNodeId": node_id,
                "sourceNodeType": node_type,
                "description": f"来自{node_type}节点的输出",
                "preview": ""
            }
            variables.append(var_info)
    else:
        # 默认变量名
        default_vars = {
            "prompt": ["prompt"],
            "llm": ["llm_output", "output"],
            "http": ["httpResult"],
            "code": ["codeResult", "result"],
            "parser": ["parsed"],
            "tool": ["toolResult"],
            "variable": [node_data.get("variableName", "result")],
            "condition": ["condition_result"]
        }
        
        if node_type in default_vars:
            for var_name in default_vars[node_type]:
                var_info = {
                    "name": var_name,
                    "type": "any",
                    "source": "node_output",
                    "sourceNodeId": node_id,
                    "sourceNodeType": node_type,
                    "description": f"来自{node_type}节点的输出",
                    "preview": ""
                }
                variables.append(var_info)
    
    return variables


@router.get("/{workflow_code}/node-config-options/{node_id}")
async def get_node_config_options(
    workflow_code: str,
    node_id: str,
    db: Session = Depends(get_db)
):
    """获取节点的配置选项，包括可用的输入输出变量
    
    用于前端节点编辑器的变量选择器，返回该节点可用的输入变量和可配置的输出变量。
    """
    result = WorkflowService.get_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    
    workflow_data = result.get("data", {}).get("workflowData", {})
    
    # 获取该节点之前可用的变量
    available_input_vars = _analyze_workflow_variables(workflow_data, node_id)
    
    # 查找当前节点定义
    nodes = workflow_data.get("nodes", [])
    current_node = next((n for n in nodes if n.get("id") == node_id), None)
    
    # 获取节点的输入输出字段定义
    config_options = _get_node_config_schema(current_node)
    
    return {
        "workflowCode": workflow_code,
        "nodeId": node_id,
        "availableVariables": available_input_vars,
        "configOptions": config_options
    }


def _get_node_config_schema(node: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """获取节点的配置字段定义"""
    if not node:
        return {
            "inputs": [],
            "outputs": []
        }
    
    node_type = node.get("type", "")
    node_data = node.get("data", {})
    
    # 定义各节点类型的输入输出字段
    schema = {
        "prompt": {
            "inputs": [
                {
                    "key": "input",
                    "label": "输入文本",
                    "type": "variable",
                    "required": False,
                    "description": "可以引用前一个节点的输出或其他变量"
                }
            ],
            "outputs": [
                {
                    "key": "output",
                    "label": "渲染后的提示词",
                    "type": "string",
                    "description": "模板渲染后的完整提示词"
                }
            ]
        },
        "llm": {
            "inputs": [
                {
                    "key": "input",
                    "label": "输入内容",
                    "type": "variable",
                    "required": True,
                    "description": "发送给LLM的输入文本"
                }
            ],
            "outputs": [
                {
                    "key": "output",
                    "label": "LLM响应",
                    "type": "string",
                    "description": "LLM返回的文本响应"
                }
            ]
        },
        "http": {
            "inputs": [
                {
                    "key": "url",
                    "label": "URL",
                    "type": "variable",
                    "required": True,
                    "description": "请求URL，支持变量引用"
                },
                {
                    "key": "body",
                    "label": "请求体",
                    "type": "variable",
                    "required": False,
                    "description": "POST请求的body内容"
                }
            ],
            "outputs": [
                {
                    "key": "httpResult",
                    "label": "HTTP响应",
                    "type": "object",
                    "description": "包含status、data、headers字段"
                }
            ]
        },
        "variable": {
            "inputs": [],
            "outputs": [
                {
                    "key": "result",
                    "label": "变量值",
                    "type": "any",
                    "description": "设置的变量值"
                }
            ]
        },
        "condition": {
            "inputs": [
                {
                    "key": "left",
                    "label": "左操作数",
                    "type": "variable",
                    "required": True,
                    "description": "比较的左值"
                },
                {
                    "key": "right",
                    "label": "右操作数",
                    "type": "variable",
                    "required": True,
                    "description": "比较的右值"
                }
            ],
            "outputs": [
                {
                    "key": "condition_result",
                    "label": "条件结果",
                    "type": "boolean",
                    "description": "条件判断结果(true/false)"
                }
            ]
        },
        "code": {
            "inputs": [
                {
                    "key": "input",
                    "label": "输入数据",
                    "type": "variable",
                    "required": False,
                    "description": "代码执行时可用的输入变量"
                }
            ],
            "outputs": [
                {
                    "key": "result",
                    "label": "执行结果",
                    "type": "any",
                    "description": "代码执行返回的result变量"
                }
            ]
        },
        "parser": {
            "inputs": [
                {
                    "key": "input",
                    "label": "输入数据",
                    "type": "variable",
                    "required": True,
                    "description": "需要解析的JSON或文本"
                }
            ],
            "outputs": [
                {
                    "key": "parsed",
                    "label": "解析结果",
                    "type": "object",
                    "description": "解析后的对象"
                }
            ]
        },
        "tool": {
            "inputs": [
                {
                    "key": "params",
                    "label": "工具参数",
                    "type": "variable",
                    "required": False,
                    "description": "工具调用的参数，支持变量引用"
                }
            ],
            "outputs": [
                {
                    "key": "toolResult",
                    "label": "工具执行结果",
                    "type": "object",
                    "description": "工具执行返回的结果"
                }
            ]
        }
    }
    
    # 获取当前节点的显性配置
    existing_inputs = node_data.get("inputs", {})
    existing_outputs = node_data.get("outputs", {})
    
    base_schema = schema.get(node_type, {"inputs": [], "outputs": []})
    
    return {
        "inputs": [
            {**field, "defaultValue": existing_inputs.get(field["key"], "")}
            for field in base_schema["inputs"]
        ],
        "outputs": [
            {**field, "defaultValue": existing_outputs.get(field["key"], "")}
            for field in base_schema["outputs"]
        ],
        "customInputs": existing_inputs,
        "customOutputs": existing_outputs
    }


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


class WorkflowCopyRequest(BaseModel):
    newWorkflowCode: str
    newWorkflowName: Optional[str] = None


@router.post("/{workflow_code}/copy")
async def copy_workflow(workflow_code: str, request: WorkflowCopyRequest, db: Session = Depends(get_db)):
    """复制工作流"""
    result = WorkflowService.copy_workflow(
        source_workflow_code=workflow_code,
        new_workflow_code=request.newWorkflowCode,
        db=db
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    
    # 如果指定了新名称，更新工作流名称
    if request.newWorkflowName:
        update_result = WorkflowService.update_workflow(
            workflow_code=request.newWorkflowCode,
            workflow_data={"workflowName": request.newWorkflowName},
            db=db
        )
        if update_result["success"]:
            result["data"] = update_result["data"]
    
    return result


class WorkflowGenerationRequest(BaseModel):
    requirement: str


@router.post("/generate")
async def generate_workflow(request: WorkflowGenerationRequest):
    """根据用户需求生成工作流"""
    generator = get_workflow_generator()
    result = generator.generate_workflow(request.requirement)
    
    if not result["success"]:
        raise HTTPException(status_code=500, detail=result["message"])
    
    # 验证生成的工作流
    validation = generator.validate_workflow(result["data"])
    
    # 格式化为编辑器可用格式
    editor_format = generator.format_for_editor(result["data"])
    
    return {
        "success": True,
        "data": editor_format,
        "description": result["data"].get("description", ""),
        "validation": validation
    }

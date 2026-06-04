"""
工作流管理API

集成前端工作流编辑器和后端工作流引擎，支持：
1. 工作流CRUD（数据库存储）
2. 工作流执行（通过WorkflowEngine）
3. 工作流格式转换（前端→后端）
"""
from fastapi import APIRouter, Depends, Query, HTTPException
from sqlalchemy.orm import Session
from typing import Optional

from app.core.database import get_db
from app.services.workflow_service import WorkflowService
from app.services.workflow_generator import get_workflow_generator
from app.langchain.workflow_init import workflow_engine
from app.langchain.workflow_converter import WorkflowConverter
from pydantic import BaseModel
from typing import List, Dict, Any
from app.core.logger import get_logger
from app.services.llm.base import extract_json

logger = get_logger(__name__)

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
    keyword: Optional[str] = Query(None, description="关键词搜索（匹配名称、编码、描述）"),
    workflowCode: Optional[str] = Query(None, description="工作流编码精确匹配"),
    tags: Optional[List[str]] = Query(None, description="标签过滤（包含任一标签）"),
    createdBy: Optional[str] = Query(None, description="创建者过滤"),
    minExecutionCount: Optional[int] = Query(None, description="最小执行次数"),
    maxExecutionCount: Optional[int] = Query(None, description="最大执行次数"),
    sortBy: Optional[str] = Query("created_at", description="排序字段：created_at, updated_at, priority, execution_count"),
    sortOrder: Optional[str] = Query("desc", description="排序方向：asc, desc"),
    page: Optional[int] = Query(1, description="页码（从1开始）"),
    pageSize: Optional[int] = Query(20, description="每页数量"),
    db: Session = Depends(get_db)
):
    """
    获取工作流列表（支持按条件检索和分页）
    
    支持的过滤条件：
    - category: 分类过滤
    - isActive: 启用状态过滤
    - keyword: 关键词搜索（匹配名称、编码、描述）
    - workflowCode: 工作流编码精确匹配
    - tags: 标签过滤（包含任一标签）
    - createdBy: 创建者过滤
    - minExecutionCount/maxExecutionCount: 执行次数范围过滤
    
    支持的排序字段：
    - created_at: 创建时间（默认）
    - updated_at: 更新时间
    - priority: 优先级
    - execution_count: 执行次数
    """
    result = WorkflowService.list_workflows(
        db,
        category=category,
        is_active=isActive,
        keyword=keyword,
        workflow_code=workflowCode,
        tags=tags,
        created_by=createdBy,
        min_execution_count=minExecutionCount,
        max_execution_count=maxExecutionCount,
        sort_by=sortBy,
        sort_order=sortOrder,
        page=page,
        page_size=pageSize
    )
    if not result["success"]:
        raise HTTPException(status_code=500, detail=result["message"])
    return result


class PublishRequest(BaseModel):
    """发布请求"""
    user: Optional[str] = None


class BatchPublishRequest(BaseModel):
    """批量发布请求"""
    workflowCodes: List[str]
    user: Optional[str] = None


class RollbackRequest(BaseModel):
    """回滚请求"""
    targetVersion: int
    user: Optional[str] = None


class VersionCompareRequest(BaseModel):
    """版本比较请求"""
    version1: int
    version2: int


@router.post("/{workflow_code}/publish")
async def publish_workflow(
    workflow_code: str,
    request: Optional[PublishRequest] = None,
    db: Session = Depends(get_db)
):
    """
    发布工作流（上线滚动）
    
    将工作流从草稿状态发布为上线状态，支持版本管理和变更记录。
    """
    result = WorkflowService.publish_workflow(
        workflow_code, db, user=request.user if request else None
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.post("/{workflow_code}/unpublish")
async def unpublish_workflow(
    workflow_code: str,
    request: Optional[PublishRequest] = None,
    db: Session = Depends(get_db)
):
    """
    下线工作流
    
    将工作流从上线状态下线，停止对外服务。
    """
    result = WorkflowService.unpublish_workflow(
        workflow_code, db, user=request.user if request else None
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.post("/batch-publish")
async def batch_publish_workflows(
    request: BatchPublishRequest,
    db: Session = Depends(get_db)
):
    """
    批量发布工作流（滚动发布）
    
    支持一次发布多个工作流，返回每个工作流的发布结果。
    """
    result = WorkflowService.batch_publish(
        request.workflowCodes, db, user=request.user
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.post("/{workflow_code}/rollback")
async def rollback_workflow(
    workflow_code: str,
    request: RollbackRequest,
    db: Session = Depends(get_db)
):
    """
    回滚工作流到指定版本
    
    将工作流回滚到历史版本，当前版本会被保存到历史记录中。
    """
    result = WorkflowService.rollback_version(
        workflow_code, request.targetVersion, db, user=request.user
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.post("/{workflow_code}/compare-versions")
async def compare_workflow_versions(
    workflow_code: str,
    request: VersionCompareRequest,
    db: Session = Depends(get_db)
):
    """
    比较两个版本的差异
    
    返回两个版本之间的字段差异信息。
    """
    result = WorkflowService.compare_versions(
        workflow_code, request.version1, request.version2, db
    )
    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])
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
            "id": f"start.{param.get('key', '')}",
            "name": param.get("key", ""),
            "type": param.get("type", "string"),
            "source": "workflow_input",
            "nodeId": "start",
            "nodeType": "start",
            "nodeName": "开始节点",
            "sourceNodeId": None,
            "sourceNodeType": "start",
            "sourceNodeName": "开始节点",
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
    
    # 获取节点名称（优先使用自定义标签，否则使用节点类型）
    node_label = node_data.get("label", node_type)
    
    # 根据节点类型提取变量
    output_mappings = node_data.get("outputParams", {})
    if output_mappings:
        for var_name, source_expr in output_mappings.items():
            var_info = {
                "id": f"{node_id}.{var_name}",
                "name": var_name,
                "type": "any",
                "source": "node_output",
                "nodeId": node_id,
                "nodeType": node_type,
                "nodeName": node_label,
                "sourceNodeId": node_id,
                "sourceNodeType": node_type,
                "sourceNodeName": node_label,
                "description": f"来自{node_label}节点的输出",
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
            "variable": [node_data.get("variableName", node_data.get("label", "result"))],
            "condition": ["condition_result"]
        }
        
        if node_type in default_vars:
            for var_name in default_vars[node_type]:
                var_info = {
                    "id": f"{node_id}.{var_name}",
                    "name": var_name,
                    "type": "any",
                    "source": "node_output",
                    "nodeId": node_id,
                    "nodeType": node_type,
                    "nodeName": node_label,
                    "sourceNodeId": node_id,
                    "sourceNodeType": node_type,
                    "sourceNodeName": node_label,
                    "description": f"来自{node_label}节点的输出",
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


class GenerateValidationRulesRequest(BaseModel):
    """生成校验规则请求"""
    description: str
    inputType: Optional[str] = "text"


@router.post("/generate-validation-rules")
async def generate_validation_rules(request: GenerateValidationRulesRequest):
    """根据自然语言描述生成用户输入校验规则"""
    try:
        from app.services.llm_service import llm_service
        
        system_prompt = """你是一个校验规则生成专家。根据用户的需求描述，生成适用于用户输入校验的规则数组。

可用的规则类型：
1. required - 必填校验，value 可为空
2. minLength - 最小长度，value 为数字
3. maxLength - 最大长度，value 为数字  
4. min - 最小值，value 为数字
5. max - 最大值，value 为数字
6. pattern - 正则匹配，value 为正则表达式字符串
7. email - 邮箱格式，value 可为空
8. phone - 手机号格式，value 可为空
9. url - URL格式，value 可为空
10. enum - 枚举值，value 为数组

请严格按照以下 JSON 格式输出，不要有其他文本：
{"rules": [{"type": "required", "value": null, "message": "中文错误提示"}, ...], "errorMessage": "默认校验失败提示消息"}"""

        prompt = f"""用户需求：{request.description}
输入类型：{request.inputType}

请生成合适的校验规则。"""

        result_text = llm_service._call_llm_sync(prompt, system_prompt=system_prompt)
        
        if not result_text:
            return {
                "success": False,
                "message": "AI生成失败，请重试",
                "rules": [],
                "errorMessage": "您的输入不符合要求，请重新输入"
            }
        
        parsed = extract_json(result_text)
        
        if not parsed or not isinstance(parsed, dict):
            return {
                "success": False,
                "message": "AI返回格式异常",
                "rules": [],
                "errorMessage": "您的输入不符合要求，请重新输入"
            }
        
        rules = parsed.get("rules", [])
        error_message = parsed.get("errorMessage", "您的输入不符合要求，请重新输入")
        
        return {
            "success": True,
            "message": "校验规则生成成功",
            "rules": rules,
            "errorMessage": error_message
        }
    except Exception as e:
        logger.error(f"生成校验规则失败: {e}")
        return {
            "success": False,
            "message": f"生成失败: {str(e)}",
            "rules": [],
            "errorMessage": "您的输入不符合要求，请重新输入"
        }


class WorkflowExecuteRequest(BaseModel):
    """执行工作流请求（支持两种方式）"""
    workflowCode: Optional[str] = None  # 方式1：指定工作流编码
    workflowId: Optional[str] = None    # 方式2：指定工作流ID
    inputParams: Optional[Dict[str, Any]] = {}


@router.post("/execute")
async def execute_workflow_generic(request: WorkflowExecuteRequest, db: Session = Depends(get_db)):
    """通用工作流执行接口（无需硬编码工作流编码）
    
    支持两种调用方式：
    1. 指定工作流编码: {"workflowCode": "tariff_filing", "inputParams": {...}}
    2. 指定工作流ID: {"workflowId": "123", "inputParams": {...}}
    
    执行流程：
    1. 根据编码/ID获取工作流定义
    2. 验证工作流配置
    3. 转换为后端格式
    4. 执行工作流
    """
    # 获取工作流编码
    workflow_code = request.workflowCode
    
    if not workflow_code:
        if request.workflowId:
            # 通过ID查找编码
            result = WorkflowService.get_workflow_by_id(request.workflowId, db)
            if not result["success"]:
                raise HTTPException(status_code=404, detail=result["message"])
            workflow_code = result.get("data", {}).get("workflowCode")
        else:
            raise HTTPException(status_code=400, detail="请提供 workflowCode 或 workflowId")
    
    # 调用原有执行逻辑
    return await _execute_workflow_internal(workflow_code, request.inputParams, db)


@router.post("/{workflow_code}/execute")
async def execute_workflow(workflow_code: str, request: WorkflowExecuteRequest, db: Session = Depends(get_db)):
    """执行指定工作流（保留原接口兼容）"""
    return await _execute_workflow_internal(workflow_code, request.inputParams, db)


async def _execute_workflow_internal(workflow_code: str, input_params: Dict[str, Any], db: Session):
    """内部执行逻辑
    
    将前端编辑器创建的工作流配置转换为后端格式并执行。
    自动从本体加载业务规则（默认值、校验规则、字段映射）。
    """
    # 获取工作流定义
    result = WorkflowService.get_workflow(workflow_code, db)
    if not result["success"]:
        raise HTTPException(status_code=404, detail=result["message"])
    
    workflow_data = result.get("data", {}).get("workflowData", {})
    workflow_name = result.get("data", {}).get("workflowName", workflow_code)
    
    # 验证工作流配置
    validation = WorkflowConverter.validate(workflow_data)
    if not validation["valid"]:
        raise HTTPException(status_code=400, detail={
            "message": "工作流配置验证失败",
            "errors": validation["errors"],
            "warnings": validation["warnings"]
        })
    
    # 将前端格式转换为后端格式（传入db用于节点级本体加载）
    # 本体编码作为表单节点的属性，在转换时自动加载
    backend_config = WorkflowConverter.convert(
        workflow_data, 
        workflow_code, 
        workflow_name, 
        db
    )
    
    # 注册到工作流引擎
    workflow_def = workflow_engine.load_workflow_from_json(backend_config)
    workflow_engine.register_workflow(workflow_def)
    
    # 执行工作流
    try:
        execution_results = []
        async for event in workflow_engine.run(workflow_code, input_params):
            execution_results.append(event)
        
        # 返回结果
        if execution_results:
            last_event = execution_results[-1]
            if last_event["type"] == "workflow_complete":
                return {
                    "success": True,
                    "message": "工作流执行完成",
                    "result": last_event.get("outputs"),
                    "executionLog": execution_results
                }
            elif last_event["type"] == "workflow_failed":
                return {
                    "success": False,
                    "message": "工作流执行失败",
                    "error": last_event.get("error"),
                    "executionLog": execution_results
                }
        
        return {
            "success": False,
            "message": "工作流执行异常",
            "executionLog": execution_results
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"执行工作流失败: {str(e)}")


@router.post("/validate-config")
async def validate_workflow_config(request: Dict[str, Any]):
    """验证工作流配置（前端格式）"""
    workflow_data = request.get("workflowData", {})
    validation = WorkflowConverter.validate(workflow_data)
    
    return validation


@router.post("/convert-format")
async def convert_workflow_format(request: Dict[str, Any]):
    """将前端工作流格式转换为后端格式"""
    workflow_data = request.get("workflowData", {})
    workflow_id = request.get("workflowId", "workflow_001")
    workflow_name = request.get("workflowName", "未命名工作流")
    
    # 先验证
    validation = WorkflowConverter.validate(workflow_data)
    if not validation["valid"]:
        return {
            "success": False,
            "message": "验证失败",
            "errors": validation["errors"],
            "warnings": validation["warnings"]
        }
    
    # 转换格式
    backend_config = WorkflowConverter.convert(workflow_data, workflow_id, workflow_name)
    
    return {
        "success": True,
        "message": "转换成功",
        "backendConfig": backend_config,
        "warnings": validation["warnings"]
    }


class FormNodeExecutionRequest(BaseModel):
    """表单节点执行请求"""
    ontology_code: str
    tool_name: Optional[str] = None
    enable_validation: bool = False
    model: Optional[str] = None
    temperature: float = 0.3
    validation_prompt: Optional[str] = None
    input_variable: Optional[str] = None
    input_data: Optional[Dict[str, Any]] = {}


@router.post("/execute-form-node")
async def execute_form_node(request: FormNodeExecutionRequest):
    """执行表单节点（单节点运行）
    
    该接口用于：
    1. 基于本体生成表单结构
    2. 通过推荐策略初始化表单数据
    3. 执行本体规则校验和大模型智能校验（可选）
    4. 调用 MCP 工具提交表单（可选）
    """
    try:
        from app.engine.workflow_executor import FormNodeExecutor, WorkflowContext
        
        # 创建节点数据
        node_data = {
            "ontology_code": request.ontology_code,
            "ontologyCode": request.ontology_code,
            "tool_type": request.tool_name,
            "toolType": request.tool_name,
            "tool_name": request.tool_name,
            "toolName": request.tool_name,
            "enable_validation": request.enable_validation,
            "enableValidation": request.enable_validation,
            "model": request.model,
            "temperature": request.temperature,
            "validation_prompt": request.validation_prompt,
            "validationPrompt": request.validation_prompt,
            "input_variable": request.input_variable,
            "inputVariable": request.input_variable
        }
        
        # 创建表单节点执行器
        executor = FormNodeExecutor({
            "node_id": "form_node_standalone",
            "type": "form",
            "data": node_data
        })
        
        # 创建执行上下文（单节点执行时使用临时 workflow_id）
        context = WorkflowContext(workflow_id="form-node-execution")
        context.params = request.input_data or {}
        
        # 执行节点
        result = await executor.execute(context, [])
        
        return {
            "success": True,
            "message": "表单节点执行完成",
            "form_schema": context.outputs.get("form_schema"),
            "form_data": context.outputs.get("form_data"),
            "ontology_code": context.outputs.get("ontology_code"),
            "form_validation": context.outputs.get("form_validation"),
            "form_submit_result": context.outputs.get("form_submit_result"),
            "llm_validation": context.outputs.get("llm_validation"),
            "next_nodes": result
        }
        
    except Exception as e:
        return {
            "success": False,
            "message": f"表单节点执行失败: {str(e)}",
            "error": str(e)
        }


class WorkflowResumeRequest(BaseModel):
    """工作流恢复执行请求"""
    workflow_id: str
    form_data: Dict[str, Any]
    form_code: Optional[str] = None
    form_name: Optional[str] = None


@router.post("/resume")
async def resume_workflow(request: WorkflowResumeRequest):
    """恢复工作流执行（表单提交后调用）
    
    该接口用于：
    1. 接收用户提交的表单数据
    2. 恢复处于等待状态的工作流
    3. 继续执行后续节点
    
    前端在收到 workflow_waiting 事件后，应该：
    1. 显示表单让用户填写
    2. 用户提交后调用此接口恢复执行
    """
    try:
        from app.langchain.workflow_init import workflow_engine
        
        logger.info(f"[resume_workflow] 收到表单提交，workflow_id={request.workflow_id}, form_code={request.form_code}")
        
        execution_results = []
        async for event in workflow_engine.resume(request.workflow_id, request.form_data):
            execution_results.append(event)
            logger.info(f"[resume_workflow] 事件: {event.get('type')}")
        
        if execution_results:
            last_event = execution_results[-1]
            if last_event.get("type") == "workflow_complete":
                return {
                    "success": True,
                    "message": "工作流执行完成",
                    "result": last_event.get("outputs"),
                    "executionLog": execution_results
                }
            elif last_event.get("type") == "workflow_failed":
                return {
                    "success": False,
                    "message": "工作流执行失败",
                    "error": last_event.get("error"),
                    "executionLog": execution_results
                }
        
        return {
            "success": True,
            "message": "工作流已恢复",
            "executionLog": execution_results
        }
        
    except ValueError as e:
        logger.warning(f"[resume_workflow] 参数错误: {e}")
        return {
            "success": False,
            "message": str(e),
            "error": "invalid_request"
        }
    except Exception as e:
        logger.exception(f"[resume_workflow] 恢复工作流失败: {e}")
        return {
            "success": False,
            "message": f"恢复工作流失败: {str(e)}",
            "error": str(e)
        }

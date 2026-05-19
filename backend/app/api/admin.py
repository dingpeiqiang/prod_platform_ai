from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
import logging

from app.core.database import get_db
from app.services.admin_service import AdminService
from app.services.scene_service import SceneService
from app.services.scene_prompt_manager import ScenePromptManager
from app.services.prompt_service import PromptService
from app.services.tool_service import ToolService
from app.services.form_service import FormService
from app.services.ontology_service import OntologyService
from app.services.history_ai_service import (
    analyze_history,
    apply_generated_data,
    list_available_data,
    get_history_summary
)

logger = logging.getLogger("admin_api")

router = APIRouter(prefix="/api/v1", tags=["admin"])


class SceneCreateRequest(BaseModel):
    sceneCode: str
    sceneName: str
    description: Optional[str] = None
    keywords: List[str] = []
    priority: int = 10
    isActive: bool = True
    intentType: Optional[str] = None
    actionType: Optional[str] = None
    promptCode: Optional[str] = None
    requiredTools: Optional[List[Any]] = None
    availableTools: Optional[List[Any]] = None
    preActionSteps: Optional[List[Any]] = None
    postActionSteps: Optional[List[Any]] = None
    type: str = "scene"
    parentId: Optional[int] = None
    config: Optional[Dict[str, Any]] = None


class SceneUpdateRequest(BaseModel):
    sceneName: Optional[str] = None
    description: Optional[str] = None
    keywords: Optional[List[str]] = None
    priority: Optional[int] = None
    isActive: Optional[bool] = None
    intentType: Optional[str] = None
    actionType: Optional[str] = None
    promptCode: Optional[str] = None
    requiredTools: Optional[List[Any]] = None
    availableTools: Optional[List[Any]] = None
    preActionSteps: Optional[List[Any]] = None
    postActionSteps: Optional[List[Any]] = None
    type: Optional[str] = None
    parentId: Optional[int] = None
    changeNote: Optional[str] = None
    config: Optional[Dict[str, Any]] = None


class SceneRecognitionTestRequest(BaseModel):
    userInput: str


class ScenePromptSaveRequest(BaseModel):
    promptFile: str
    content: str


class HistoryAnalyzeRequest(BaseModel):
    formCode: str


@router.post("/chat/history/analyze")
async def analyze_history_endpoint(request: HistoryAnalyzeRequest, db: Session = Depends(get_db)):
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[history/analyze] 分析数据质量 form_code=%s", form_code)

    import asyncio
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None, lambda: analyze_history(form_code, db=db)
    )
    return result


class HistoryImportRequest(BaseModel):
    formCode: str


@router.post("/chat/history/import")
async def import_history_endpoint(request: HistoryImportRequest, db: Session = Depends(get_db)):
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[history/import] 导入数据 form_code=%s", form_code)

    import asyncio
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None, lambda: apply_generated_data(form_code, db=db)
    )
    return result


@router.get("/chat/history/list")
async def list_history_endpoint():
    result = list_available_data()
    return {"success": True, "forms": result}


@router.get("/chat/history/{form_code}/summary")
async def history_summary_endpoint(form_code: str, db: Session = Depends(get_db)):
    result = get_history_summary(form_code, db=db)
    return result


class DeployConfigRequest(BaseModel):
    config: Dict[str, Any]
    keywords: List[str] = []


@router.post("/chat/deploy-config")
async def deploy_config(request: DeployConfigRequest, db: Session = Depends(get_db)):
    config_data = request.config
    keywords = request.keywords

    form_code = config_data.get("formCode", "").strip()
    form_name = config_data.get("formName", "")

    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[deploy-config] 开始部署 form_code=%s form_name=%s", form_code, form_name)

    try:
        ontology_result = AdminService.create_ontology(config_data, db=db)
        if not ontology_result.get("success"):
            ontology_result = AdminService.update_ontology(form_code, config_data, db=db)
            if not ontology_result.get("success"):
                return {"success": False, "message": ontology_result.get("message", "写入表单配置失败")}

        logger.info("[deploy-config] ontology 写入成功 form_code=%s", form_code)

        if keywords:
            scene_data = AdminService.get_scene_mappings()
            if scene_data.get("success"):
                mappings = scene_data["data"]

                existing = [m for m in mappings.get("sceneMappings", []) if m.get("sceneCode") == form_code]
                if not existing:
                    new_mapping = {
                        "sceneCode": form_code,
                        "keywords": keywords,
                        "priority": 10
                    }
                    mappings.setdefault("sceneMappings", []).append(new_mapping)

                    update_result = AdminService.update_scene_mappings(mappings)
                    if not update_result.get("success"):
                        logger.warning("[deploy-config] scene_mapping 更新失败: %s", update_result.get("message"))
                    else:
                        logger.info("[deploy-config] scene_mapping 更新成功, 新增关键词: %s", keywords)
                else:
                    logger.info("[deploy-config] scene_mapping 已存在 form_code=%s, 跳过", form_code)

        from app.core.config_loader import config_loader
        config_loader.reload_config("prompts")

        logger.info("[deploy-config] 部署完成 form_code=%s", form_code)

        return {
            "success": True,
            "message": f"表单 '{form_name or form_code}' 部署成功！现在可以直接使用了。",
            "formCode": form_code,
            "formName": form_name
        }

    except Exception as e:
        logger.exception("[deploy-config] 部署失败: %s", e)
        return {"success": False, "message": f"部署失败: {str(e)}"}


class DeleteFormRequest(BaseModel):
    formCode: str


@router.post("/chat/delete-form")
async def delete_form_endpoint(request: DeleteFormRequest, db: Session = Depends(get_db)):
    form_code = request.formCode.strip()
    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}

    logger.info("[delete-form] 删除表单 form_code=%s", form_code)

    try:
        result = AdminService.delete_ontology(form_code, db=db, auto_backup=True)
        return result
    except Exception as e:
        logger.exception("[delete-form] 删除失败: %s", e)
        return {"success": False, "message": f"删除失败: {str(e)}"}


@router.get("/chat/form-versions/{form_code}")
async def list_form_versions(form_code: str):
    result = AdminService.list_versions(form_code.strip())
    return result


class RollbackFormRequest(BaseModel):
    formCode: str
    versionId: str


@router.post("/chat/rollback-form")
async def rollback_form_endpoint(request: RollbackFormRequest):
    form_code = request.formCode.strip()
    version_id = request.versionId.strip()

    if not form_code:
        return {"success": False, "message": "formCode 不能为空"}
    if not version_id:
        return {"success": False, "message": "versionId 不能为空"}

    logger.info("[rollback-form] 回退 form_code=%s → version_id=%s", form_code, version_id)

    try:
        result = AdminService.rollback_version(form_code, version_id)
        return result
    except Exception as e:
        logger.exception("[rollback-form] 回退失败: %s", e)
        return {"success": False, "message": f"回退失败: {str(e)}"}


# ============ 场景管理 API ============

@router.get("/scenes/tree")
async def list_scenes_tree(is_active: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取场景树状结构"""
    result = SceneService.list_scenes_tree(db, is_active=is_active)
    return result


@router.get("/scenes")
async def list_scenes(is_active: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取场景列表"""
    result = SceneService.list_scenes(db, is_active=is_active)
    return result


@router.get("/scenes/{scene_code}")
async def get_scene(scene_code: str, db: Session = Depends(get_db)):
    """获取单个场景详情"""
    result = SceneService.get_scene(scene_code, db)
    return result


@router.post("/scenes")
async def create_scene(request: SceneCreateRequest, db: Session = Depends(get_db)):
    """创建新场景"""
    scene_data = request.dict()
    # 转换字段名（驼峰转下划线）
    scene_data['scene_code'] = scene_data.pop('sceneCode')
    scene_data['scene_name'] = scene_data.pop('sceneName')
    if 'promptCode' in scene_data:
        scene_data['prompt_code'] = scene_data.pop('promptCode')
    scene_data['parent_id'] = scene_data.pop('parentId')
    
    result = SceneService.create_scene(scene_data, db)
    return result


@router.put("/scenes/{scene_code}")
async def update_scene(scene_code: str, request: SceneUpdateRequest, db: Session = Depends(get_db)):
    """更新场景"""
    scene_data = request.dict(exclude_none=True)
    # 转换字段名
    if 'sceneName' in scene_data:
        scene_data['scene_name'] = scene_data.pop('sceneName')
    if 'promptCode' in scene_data:
        scene_data['prompt_code'] = scene_data.pop('promptCode')
    if 'isActive' in scene_data:
        scene_data['is_active'] = scene_data.pop('isActive')
    if 'parentId' in scene_data:
        scene_data['parent_id'] = scene_data.pop('parentId')
    
    result = SceneService.update_scene(scene_code, scene_data, db)
    return result


@router.delete("/scenes/{scene_code}")
async def delete_scene(scene_code: str, db: Session = Depends(get_db)):
    """删除场景"""
    result = SceneService.delete_scene(scene_code, db)
    return result


@router.patch("/scenes/{scene_code}/toggle")
async def toggle_scene(scene_code: str, db: Session = Depends(get_db)):
    """切换场景启用状态"""
    result = SceneService.toggle_active(scene_code, db)
    return result


@router.post("/scenes/test")
async def test_scene_recognition(request: SceneRecognitionTestRequest, db: Session = Depends(get_db)):
    """测试场景识别"""
    result = SceneService.test_scene_recognition(request.userInput, db)
    return result


@router.get("/scenes/stats/summary")
async def get_scene_stats(db: Session = Depends(get_db)):
    """获取场景统计"""
    result = SceneService.get_scene_stats(db)
    return result


@router.get("/scenes/{scene_code}/history")
async def get_scene_history(scene_code: str, db: Session = Depends(get_db)):
    """获取场景版本历史"""
    result = SceneService.get_history(scene_code, db)
    return result


class SceneRollbackRequest(BaseModel):
    version: int


@router.post("/scenes/{scene_code}/rollback")
async def rollback_scene(scene_code: str, request: SceneRollbackRequest, db: Session = Depends(get_db)):
    """回滚场景到指定版本"""
    result = SceneService.rollback_to_version(scene_code, request.version, db)
    return result


# ============ 场景提示词管理 API ============

@router.get("/scenes/prompts/list")
async def list_scene_prompts():
    """列出所有场景提示词"""
    result = ScenePromptManager.list_prompts()
    return result


@router.get("/scenes/prompts/{prompt_name}")
async def get_scene_prompt(prompt_name: str):
    """获取场景提示词内容"""
    content = ScenePromptManager.load_prompt(prompt_name)
    if content is None:
        return {"success": False, "message": f"提示词 {prompt_name} 不存在"}
    return {"success": True, "data": {"content": content}}


@router.post("/scenes/prompts")
async def save_scene_prompt(request: ScenePromptSaveRequest):
    """保存场景提示词"""
    result = ScenePromptManager.save_prompt(request.promptFile, request.content)
    return result


@router.delete("/scenes/prompts/{prompt_name}")
async def delete_scene_prompt(prompt_name: str):
    """删除场景提示词"""
    result = ScenePromptManager.delete_prompt(prompt_name)
    return result


# ============ 提示词管理 API ============

class PromptCreateRequest(BaseModel):
    code: str
    name: str
    description: Optional[str] = None
    category: str = "general"
    content: str = ""
    variables: List[Dict[str, Any]] = []
    tools: List[Dict[str, Any]] = []
    isTemplate: bool = False


class PromptUpdateRequest(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    content: Optional[str] = None
    variables: Optional[List[Dict[str, Any]]] = None
    tools: Optional[List[Dict[str, Any]]] = None
    isTemplate: Optional[bool] = None
    isActive: Optional[bool] = None
    changeNote: Optional[str] = None


class PromptPreviewRequest(BaseModel):
    variables: Optional[Dict[str, Any]] = None


class AIGenerateRequest(BaseModel):
    requirement: str
    category: str = "general"
    useTools: List[Dict[str, Any]] = []


class AIOptimizeRequest(BaseModel):
    content: str


@router.get("/prompts")
async def list_prompts(category: Optional[str] = None, isActive: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取提示词列表"""
    result = PromptService.list_prompts(db, category=category, is_active=isActive)
    return result


@router.get("/prompts/categories")
async def get_prompt_categories():
    """获取提示词分类列表"""
    result = PromptService.get_categories()
    return result


@router.get("/prompts/{code}")
async def get_prompt(code: str, db: Session = Depends(get_db)):
    """获取单个提示词详情"""
    result = PromptService.get_prompt(db, code)
    return result


@router.post("/prompts")
async def create_prompt(request: PromptCreateRequest, db: Session = Depends(get_db)):
    """创建提示词"""
    result = PromptService.create_prompt(db, request.dict())
    return result


@router.put("/prompts/{code}")
async def update_prompt(code: str, request: PromptUpdateRequest, db: Session = Depends(get_db)):
    """更新提示词"""
    result = PromptService.update_prompt(db, code, request.dict())
    return result


@router.delete("/prompts/{code}")
async def delete_prompt(code: str, db: Session = Depends(get_db)):
    """删除提示词"""
    result = PromptService.delete_prompt(db, code)
    return result


@router.get("/prompts/{code}/versions")
async def get_prompt_versions(code: str, db: Session = Depends(get_db)):
    """获取提示词版本历史"""
    result = PromptService.get_versions(db, code)
    return result


@router.post("/prompts/{code}/preview")
async def preview_prompt(code: str, request: PromptPreviewRequest = PromptPreviewRequest(), db: Session = Depends(get_db)):
    """预览提示词（变量替换）"""
    result = PromptService.preview_prompt(db, code, request.variables)
    return result


@router.post("/prompts/ai/generate")
async def generate_with_ai(request: AIGenerateRequest, db: Session = Depends(get_db)):
    """AI辅助生成提示词"""
    result = PromptService.generate_with_ai(db, request.dict())
    return result


@router.post("/prompts/ai/optimize")
async def optimize_with_ai(request: AIOptimizeRequest, db: Session = Depends(get_db)):
    """AI优化提示词"""
    result = PromptService.optimize_prompt(db, request.dict())
    return result


# ============ 工具管理 API ============

class ToolCreateRequest(BaseModel):
    toolCode: str
    toolName: str
    description: Optional[str] = None
    category: str = "general"
    toolType: str = "custom"
    config: Dict[str, Any] = {}
    parameters: List[Dict[str, Any]] = []
    returnSchema: Dict[str, Any] = {}
    endpoint: Optional[str] = None
    handler: Optional[str] = None
    isAsync: bool = True


class ToolUpdateRequest(BaseModel):
    toolName: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    toolType: Optional[str] = None
    config: Optional[Dict[str, Any]] = None
    parameters: Optional[List[Dict[str, Any]]] = None
    returnSchema: Optional[Dict[str, Any]] = None
    endpoint: Optional[str] = None
    handler: Optional[str] = None
    isAsync: Optional[bool] = None
    isActive: Optional[bool] = None


@router.get("/tools")
async def list_tools(category: Optional[str] = None, isActive: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取工具列表"""
    result = ToolService.list_tools(db, category=category, is_active=isActive)
    return result


@router.get("/tools/categories")
async def get_tool_categories():
    """获取工具分类"""
    return {"success": True, "data": ToolService.get_categories()}


@router.get("/tools/{tool_code}")
async def get_tool(tool_code: str, db: Session = Depends(get_db)):
    """获取工具详情"""
    result = ToolService.get_tool(db, tool_code)
    return result


@router.post("/tools")
async def create_tool(request: ToolCreateRequest, db: Session = Depends(get_db)):
    """创建工具"""
    result = ToolService.create_tool(db, request.dict())
    return result


@router.put("/tools/{tool_code}")
async def update_tool(tool_code: str, request: ToolUpdateRequest, db: Session = Depends(get_db)):
    """更新工具"""
    result = ToolService.update_tool(db, tool_code, request.dict())
    return result


@router.delete("/tools/{tool_code}")
async def delete_tool(tool_code: str, db: Session = Depends(get_db)):
    """删除工具"""
    result = ToolService.delete_tool(db, tool_code)
    return result


@router.patch("/tools/{tool_code}/toggle")
async def toggle_tool(tool_code: str, db: Session = Depends(get_db)):
    """切换工具启用状态"""
    result = ToolService.toggle_active(db, tool_code)
    return result


# ============ 表单管理 API ============

class FormCreateRequest(BaseModel):
    formCode: str
    formName: str
    description: Optional[str] = None
    category: str = "general"
    entities: List[Dict[str, Any]] = []
    layout: Dict[str, Any] = {}
    validationRules: List[Dict[str, Any]] = []
    ontologyCode: Optional[str] = None


class FormUpdateRequest(BaseModel):
    formName: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    entities: Optional[List[Dict[str, Any]]] = None
    layout: Optional[Dict[str, Any]] = None
    validationRules: Optional[List[Dict[str, Any]]] = None
    ontologyCode: Optional[str] = None
    isActive: Optional[bool] = None


@router.get("/forms")
async def list_forms(category: Optional[str] = None, isActive: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取表单列表"""
    result = FormService.list_forms(db, category=category, is_active=isActive)
    return result


@router.get("/forms/categories")
async def get_form_categories():
    """获取表单分类"""
    return {"success": True, "data": FormService.get_categories()}


@router.get("/forms/{form_code}")
async def get_form(form_code: str, db: Session = Depends(get_db)):
    """获取表单详情"""
    result = FormService.get_form(db, form_code)
    return result


@router.post("/forms")
async def create_form(request: FormCreateRequest, db: Session = Depends(get_db)):
    """创建表单"""
    result = FormService.create_form(db, request.dict())
    return result


@router.put("/forms/{form_code}")
async def update_form(form_code: str, request: FormUpdateRequest, db: Session = Depends(get_db)):
    """更新表单"""
    result = FormService.update_form(db, form_code, request.dict())
    return result


@router.delete("/forms/{form_code}")
async def delete_form(form_code: str, db: Session = Depends(get_db)):
    """删除表单"""
    result = FormService.delete_form(db, form_code)
    return result


@router.patch("/forms/{form_code}/toggle")
async def toggle_form(form_code: str, db: Session = Depends(get_db)):
    """切换表单启用状态"""
    result = FormService.toggle_active(db, form_code)
    return result


# ============ 本体管理 API ============

class OntologyCreateRequest(BaseModel):
    ontologyCode: str
    ontologyName: str
    category: Optional[str] = "general"
    formCode: Optional[str] = None
    formName: Optional[str] = None
    description: Optional[str] = None
    entities: List[Dict[str, Any]] = []


class OntologyUpdateRequest(BaseModel):
    ontologyName: Optional[str] = None
    category: Optional[str] = None
    formCode: Optional[str] = None
    formName: Optional[str] = None
    description: Optional[str] = None
    entities: Optional[List[Dict[str, Any]]] = None
    isActive: Optional[bool] = None


@router.get("/ontologies")
async def list_ontologies(category: Optional[str] = None, isActive: Optional[bool] = None, db: Session = Depends(get_db)):
    """获取本体列表"""
    result = OntologyService.list_ontologies(db, category=category, is_active=isActive)
    return result


@router.get("/ontologies/categories")
async def get_ontology_categories():
    """获取本体分类"""
    return {"success": True, "data": OntologyService.get_categories()}


@router.get("/ontologies/{ontology_code}")
async def get_ontology(ontology_code: str, db: Session = Depends(get_db)):
    """获取本体详情"""
    result = OntologyService.get_ontology(db, ontology_code)
    return result


@router.post("/ontologies")
async def create_ontology(request: OntologyCreateRequest, db: Session = Depends(get_db)):
    """创建本体"""
    result = OntologyService.create_ontology(db, request.dict())
    return result


@router.put("/ontologies/{ontology_code}")
async def update_ontology(ontology_code: str, request: OntologyUpdateRequest, db: Session = Depends(get_db)):
    """更新本体"""
    result = OntologyService.update_ontology(db, ontology_code, request.dict())
    return result


@router.delete("/ontologies/{ontology_code}")
async def delete_ontology(ontology_code: str, db: Session = Depends(get_db)):
    """删除本体"""
    result = OntologyService.delete_ontology(db, ontology_code)
    return result


@router.patch("/ontologies/{ontology_code}/toggle")
async def toggle_ontology(ontology_code: str, db: Session = Depends(get_db)):
    """切换本体启用状态"""
    result = OntologyService.toggle_active(db, ontology_code)
    return result
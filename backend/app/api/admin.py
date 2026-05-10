from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
import logging

from app.core.database import get_db
from app.services.admin_service import AdminService
from app.services.scene_service import SceneService
from app.services.scene_prompt_manager import ScenePromptManager
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
    formCode: Optional[str] = None
    actionType: str = "form_generation"
    actionPromptFile: Optional[str] = None
    requiredTools: List[str] = []
    availableTools: List[str] = []
    preActionSteps: List[Dict] = []
    postActionSteps: List[Dict] = []


class SceneUpdateRequest(BaseModel):
    sceneName: Optional[str] = None
    description: Optional[str] = None
    keywords: Optional[List[str]] = None
    priority: Optional[int] = None
    isActive: Optional[bool] = None
    intentType: Optional[str] = None
    formCode: Optional[str] = None
    actionType: Optional[str] = None
    actionPromptFile: Optional[str] = None
    requiredTools: Optional[List[str]] = None
    availableTools: Optional[List[str]] = None
    preActionSteps: Optional[List[Dict]] = None
    postActionSteps: Optional[List[Dict]] = None


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

@router.get("/scenes/enums")
async def get_scene_enums():
    """获取场景枚举选项（意图类型、动作类型）"""
    result = SceneService.get_enum_options()
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
    scene_data['intent_type'] = scene_data.pop('intentType')
    scene_data['form_code'] = scene_data.pop('formCode')
    scene_data['action_type'] = scene_data.pop('actionType')
    scene_data['action_prompt_file'] = scene_data.pop('actionPromptFile')
    scene_data['required_tools'] = scene_data.pop('requiredTools')
    scene_data['available_tools'] = scene_data.pop('availableTools')
    scene_data['pre_action_steps'] = scene_data.pop('preActionSteps')
    scene_data['post_action_steps'] = scene_data.pop('postActionSteps')
    
    result = SceneService.create_scene(scene_data, db)
    return result


@router.put("/scenes/{scene_code}")
async def update_scene(scene_code: str, request: SceneUpdateRequest, db: Session = Depends(get_db)):
    """更新场景"""
    scene_data = request.dict(exclude_none=True)
    # 转换字段名
    if 'sceneName' in scene_data:
        scene_data['scene_name'] = scene_data.pop('sceneName')
    if 'intentType' in scene_data:
        scene_data['intent_type'] = scene_data.pop('intentType')
    if 'formCode' in scene_data:
        scene_data['form_code'] = scene_data.pop('formCode')
    if 'actionType' in scene_data:
        scene_data['action_type'] = scene_data.pop('actionType')
    if 'actionPromptFile' in scene_data:
        scene_data['action_prompt_file'] = scene_data.pop('actionPromptFile')
    if 'requiredTools' in scene_data:
        scene_data['required_tools'] = scene_data.pop('requiredTools')
    if 'availableTools' in scene_data:
        scene_data['available_tools'] = scene_data.pop('availableTools')
    if 'preActionSteps' in scene_data:
        scene_data['pre_action_steps'] = scene_data.pop('preActionSteps')
    if 'postActionSteps' in scene_data:
        scene_data['post_action_steps'] = scene_data.pop('postActionSteps')
    if 'isActive' in scene_data:
        scene_data['is_active'] = scene_data.pop('isActive')
    
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
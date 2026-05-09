from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
import logging

from app.core.database import get_db
from app.services.admin_service import AdminService
from app.services.history_ai_service import (
    analyze_history,
    apply_generated_data,
    list_available_data,
    get_history_summary
)

logger = logging.getLogger("admin_api")

router = APIRouter(prefix="/api/v1", tags=["admin"])


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
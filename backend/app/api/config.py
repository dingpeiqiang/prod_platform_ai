from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import logging
from app.core.config_loader import config_loader
from app.services.ontology_service import OntologyService

logger = logging.getLogger("config_api")

router = APIRouter(prefix="/api/v1/config", tags=["config"])


class ReloadConfigRequest(BaseModel):
    configType: Optional[str] = None


class ConfigReloadResponse(BaseModel):
    success: bool
    message: str


class OntologyListResponse(BaseModel):
    success: bool
    ontologies: List[Dict[str, Any]]


class AppConfigResponse(BaseModel):
    success: bool
    config: Dict[str, Any]


@router.post("/reload", response_model=ConfigReloadResponse)
async def reload_config(request: ReloadConfigRequest):
    logger.info("[config/reload] 触发配置重载 configType=%s", request.configType or "all")
    try:
        config_loader.reload_config(request.configType)
        logger.info("[config/reload] 配置重载成功 configType=%s", request.configType or "all")
        return ConfigReloadResponse(
            success=True,
            message=f"配置已重新加载: {request.configType or 'all'}"
        )
    except Exception as e:
        logger.exception("[config/reload] 配置重载失败: %s", e)
        raise HTTPException(status_code=500, detail=f"配置重新加载失败: {str(e)}")


@router.get("/app", response_model=AppConfigResponse)
async def get_app_config():
    logger.debug("[config/app] 获取应用配置")
    try:
        config = config_loader.get_app_config()
        return AppConfigResponse(
            success=True,
            config=config
        )
    except Exception as e:
        logger.exception("[config/app] 获取配置失败: %s", e)
        raise HTTPException(status_code=500, detail=f"获取配置失败: {str(e)}")


@router.get("/ontologies", response_model=OntologyListResponse)
async def list_ontologies():
    logger.debug("[config/ontologies] 获取本体列表")
    try:
        result = OntologyService.get_all_ontologies()
        logger.debug("[config/ontologies] 返回 %d 个本体", len(result.get("ontologies", [])))
        return OntologyListResponse(
            success=result["success"],
            ontologies=result.get("ontologies", [])
        )
    except Exception as e:
        logger.exception("[config/ontologies] 获取本体列表失败: %s", e)
        raise HTTPException(status_code=500, detail=f"获取本体列表失败: {str(e)}")

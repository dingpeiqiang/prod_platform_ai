"""
演示场景一 API - 智查・一键复制(存量配置复用)
前缀: /api/v1/demo/search
"""
from typing import Dict, Any, List, Optional
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from app.core.logger import get_logger
from app.services.demo_data_service import demo_data_service

logger = get_logger(__name__)
router = APIRouter(prefix="/api/v1/demo/search", tags=["演示-智查一键复制"])


class SemanticSearchRequest(BaseModel):
    query: str = Field(..., description="自然语言查询")
    top_k: int = Field(default=5, ge=1, le=20)


class CloneRequest(BaseModel):
    source_package_id: str = Field(..., description="源套餐ID")
    modifications: Optional[Dict[str, Any]] = Field(default=None, description="差异修改字段")


class DiffRequest(BaseModel):
    source_package_id: str = Field(..., description="源套餐ID")
    cloned_package_id: str = Field(..., description="克隆套餐ID")


class SubmitRequest(BaseModel):
    package_id: str = Field(..., description="套餐ID")
    config: Optional[Dict[str, Any]] = Field(default=None, description="修改后的配置")


@router.post("/semantic", summary="语义检索历史配置")
async def semantic_search(request: SemanticSearchRequest) -> Dict[str, Any]:
    """根据自然语言查询检索相似的历史配置方案"""
    logger.info(f"[DemoSearch] 语义检索: {request.query}")

    results = demo_data_service.search_packages_semantic(request.query, top_k=request.top_k)

    return {
        "success": True,
        "query": request.query,
        "results": results,
        "total": len(results),
        "message": f"找到 {len(results)} 个匹配的历史配置方案"
    }


@router.get("/config/{package_id}", summary="获取配置详情")
async def get_config(package_id: str) -> Dict[str, Any]:
    """获取指定套餐的完整配置详情"""
    logger.info(f"[DemoSearch] 获取配置详情: {package_id}")

    config = demo_data_service.get_package_detail(package_id)
    if not config:
        raise HTTPException(status_code=404, detail=f"套餐 {package_id} 不存在")

    return {
        "success": True,
        "config": config
    }


@router.post("/clone", summary="一键克隆配置")
async def clone_config(request: CloneRequest) -> Dict[str, Any]:
    """克隆指定套餐配置,可附带修改字段"""
    logger.info(f"[DemoSearch] 克隆套餐: {request.source_package_id}")

    cloned = demo_data_service.clone_package(
        request.source_package_id,
        request.modifications
    )
    if not cloned:
        raise HTTPException(status_code=404, detail=f"源套餐 {request.source_package_id} 不存在")

    return {
        "success": True,
        "cloned_config": cloned,
        "message": f"已克隆套餐,新套餐ID: {cloned['package_id']}"
    }


@router.post("/diff", summary="配置差异对比")
async def diff_configs(request: DiffRequest) -> Dict[str, Any]:
    """对比源配置与克隆配置的差异"""
    logger.info(f"[DemoSearch] 差异对比: {request.source_package_id} vs {request.cloned_package_id}")

    source_config = demo_data_service.get_package_detail(request.source_package_id)
    target_config = demo_data_service.get_package_detail(request.cloned_package_id)

    if not source_config:
        raise HTTPException(status_code=404, detail=f"源套餐 {request.source_package_id} 不存在")
    if not target_config:
        raise HTTPException(status_code=404, detail=f"克隆套餐 {request.cloned_package_id} 不存在")

    diff_fields = demo_data_service.diff_configs(source_config, target_config)

    return {
        "success": True,
        "source_config": source_config,
        "cloned_config": target_config,
        "diff_fields": diff_fields,
        "diff_count": len(diff_fields),
        "same_count": len([f for f in diff_fields if f["source_value"] == f["target_value"]])
    }


@router.post("/submit", summary="提交新配置(含本体校验)")
async def submit_config(request: SubmitRequest) -> Dict[str, Any]:
    """提交新配置,自动执行本体合规校验"""
    logger.info(f"[DemoSearch] 提交配置: {request.package_id}")

    config = demo_data_service.get_package_detail(request.package_id)
    if not config:
        raise HTTPException(status_code=404, detail=f"套餐 {request.package_id} 不存在")

    if request.config:
        for key, value in request.config.items():
            config[key] = value

    validation = demo_data_service.validate_config(
        {"nodes": [], "config": config},
        ontology_code="home_broadband"
    )

    if not validation["valid"]:
        return {
            "success": False,
            "validation": validation,
            "message": "本体校验未通过,请修正错误后重新提交"
        }

    new_record_id = f"REC_{request.package_id}_{__import__('uuid').uuid4().hex[:8].upper()}"

    return {
        "success": True,
        "record_id": new_record_id,
        "package_id": request.package_id,
        "package_name": config.get("package_name"),
        "validation": validation,
        "message": f"配置已通过本体合规校验,生成单据号: {new_record_id}"
    }

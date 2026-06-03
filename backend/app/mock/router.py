"""
Mock API 路由

提供通用 mock 接口，用于模拟外部系统未提供的 API。
所有 mock 端点前缀: /mock
"""

import logging
from typing import Optional

from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse

from app.mock.service import mock_service

logger = logging.getLogger("mock.router")

router = APIRouter(prefix="/mock", tags=["Mock API"])


# ── 管理端点（必须在通配路由前注册）─────────────────────────────────────────


@router.get("/")
async def mock_list():
    """列出所有可用的 mock 数据"""
    return {
        "success": True,
        "data": mock_service.list_all(),
    }


@router.put("/_admin/{name}")
async def mock_upsert(name: str, request: Request):
    """动态添加/更新 mock 数据

    请求体即为完整的 mock JSON 数据。
    支持 _mock 元数据配置: statusCode, delayMs, headers
    """
    try:
        data = await request.json()
    except Exception:
        return JSONResponse(
            content={"success": False, "message": "请求体必须是有效 JSON"},
            status_code=400,
        )

    file_path = mock_service.save(name, data)
    mock_service.reload(name)
    return {
        "success": True,
        "message": f"Mock 数据 '{name}' 已保存",
        "path": str(file_path),
    }


@router.delete("/_admin/{name}")
async def mock_remove(name: str):
    """删除 mock 数据"""
    import os
    file_path = mock_service._get_file_path(name)
    if file_path.exists():
        os.remove(file_path)
        mock_service._cache.pop(name, None)
        return {"success": True, "message": f"Mock 数据 '{name}' 已删除"}
    return JSONResponse(
        content={"success": False, "message": f"Mock 数据 '{name}' 不存在"},
        status_code=404,
    )


@router.post("/_admin/{name}/reload")
async def mock_reload(name: str):
    """热重载指定 mock 数据"""
    data = mock_service.reload(name)
    if data is None:
        return JSONResponse(
            content={"success": False, "message": f"Mock 数据 '{name}' 不存在"},
            status_code=404,
        )
    return {"success": True, "message": f"Mock 数据 '{name}' 已重新加载"}


# ── 通用 mock 端点 ──────────────────────────────────────────────────────────


@router.get("/{name}")
async def mock_get(
    name: str,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 GET mock 端点

    根据 {name} 加载对应的 JSON 文件返回。
    示例: GET /mock/external_order
    """
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


@router.post("/{name}")
async def mock_post(
    name: str,
    request: Request,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 POST mock 端点

    从请求体接收参数，根据 {name} 加载 JSON 响应。
    示例: POST /mock/submit_order
    """
    try:
        body_json = await request.json()
        logger.debug(f"Mock POST [{name}] 收到请求: {body_json}")
    except Exception:
        body_json = {}
        logger.debug(f"Mock POST [{name}] 收到请求（无 JSON body）")

    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


@router.put("/{name}")
async def mock_put(
    name: str,
    request: Request,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 PUT mock 端点"""
    try:
        body_json = await request.json()
        logger.debug(f"Mock PUT [{name}] 收到请求: {body_json}")
    except Exception:
        body_json = {}
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


@router.delete("/{name}")
async def mock_delete(
    name: str,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 DELETE mock 端点"""
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


# ── 资费备案套餐查询 API ───────────────────────────────────────────────────────

from pydantic import BaseModel


class TariffPackageQuery(BaseModel):
    """资费套餐查询请求体"""
    package_code: Optional[str] = None
    category: Optional[str] = None
    active_only: Optional[bool] = True


@router.post("/tariff/packages")
async def mock_tariff_packages(
    query: TariffPackageQuery,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
):
    """模拟资费备案套餐信息查询
    
    查询资费备案相关的套餐信息，支持按套餐编码、分类等条件过滤。
    
    Args:
        query: 查询条件
            - package_code: 套餐编码（精确匹配）
            - category: 套餐分类（模糊匹配）
            - active_only: 是否只返回激活状态的套餐
        delay: 模拟延迟（毫秒）
    """
    body, code, headers = mock_service.resolve("tariff_packages", delay_ms=delay)
    
    # 应用过滤条件
    if "data" in body:
        filtered = body["data"]
        
        if query.package_code:
            filtered = [p for p in filtered if p.get("packageCode") == query.package_code]
        
        if query.category:
            filtered = [p for p in filtered if query.category.lower() in p.get("category", "").lower()]
        
        if query.active_only:
            filtered = [p for p in filtered if p.get("isActive", True)]
        
        body["data"] = filtered
        body["total"] = len(filtered)
    
    return JSONResponse(content=body, status_code=code, headers=headers)

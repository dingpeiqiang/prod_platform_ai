"""
独立 Mock 服务

提供独立的 Mock API 服务，可使用不同端口运行，不依赖主应用。

启动方式：
    python mock_server.py
    或
    start-mock-server.ps1

默认端口：6174
可通过环境变量 MOCK_SERVER_PORT 自定义端口
"""

import os
import logging
from fastapi import FastAPI, APIRouter, Request, Query
from fastapi.responses import JSONResponse
from typing import Optional

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("mock_server")

# 获取端口配置
PORT = int(os.environ.get("MOCK_SERVER_PORT", "6174"))

app = FastAPI(
    title="Mock API Server",
    version="1.0.0",
    description="独立的 Mock API 服务，用于模拟外部系统接口"
)


# ── Mock 数据服务 ──────────────────────────────────────────────────────────

class MockService:
    def __init__(self):
        self._cache = {}
        self._data_dir = os.path.join(os.path.dirname(__file__), "app", "mock", "data")
        self._load_all()
    
    def _load_all(self):
        """加载所有 mock 数据文件"""
        if not os.path.exists(self._data_dir):
            logger.warning(f"Mock 数据目录不存在: {self._data_dir}")
            return
        
        for filename in os.listdir(self._data_dir):
            if filename.endswith(".json"):
                name = filename[:-5]
                self.reload(name)
    
    def _get_file_path(self, name: str) -> str:
        return os.path.join(self._data_dir, f"{name}.json")
    
    def reload(self, name: str):
        """重新加载指定 mock 数据"""
        try:
            import json
            file_path = self._get_file_path(name)
            if os.path.exists(file_path):
                with open(file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self._cache[name] = data
                logger.debug(f"Mock 数据 '{name}' 已加载")
                return data
            return None
        except Exception as e:
            logger.error(f"加载 Mock 数据 '{name}' 失败: {e}")
            return None
    
    def list_all(self):
        """列出所有可用的 mock 数据名称"""
        return list(self._cache.keys())
    
    def resolve(self, name: str, delay_ms: int = 0, status_code: int = None):
        """解析并返回 mock 数据"""
        data = self._cache.get(name)
        
        if data is None:
            return {"success": False, "message": f"Mock 数据 '{name}' 不存在"}, 404, {}
        
        # 处理 _mock 元数据
        meta = data.get("_mock", {})
        body = {k: v for k, v in data.items() if k != "_mock"}
        
        # 获取状态码
        code = status_code if status_code is not None else meta.get("statusCode", 200)
        
        # 获取延迟配置
        if delay_ms == 0:
            delay_ms = meta.get("delayMs", 0)
        
        # 模拟延迟
        if delay_ms > 0:
            import time
            time.sleep(delay_ms / 1000)
        
        # 获取响应头
        headers = meta.get("headers", {})
        
        return body, code, headers


mock_service = MockService()


# ── 路由定义 ──────────────────────────────────────────────────────────────

# 创建带有 /mock 前缀的 APIRouter
mock_router = APIRouter(prefix="/mock")


@mock_router.get("/")
async def root():
    return {
        "name": "Mock API Server",
        "version": "1.0.0",
        "status": "running",
        "endpoints": mock_service.list_all()
    }


@mock_router.get("/list")
async def mock_list():
    """列出所有可用的 mock 数据"""
    return {
        "success": True,
        "data": mock_service.list_all(),
        "count": len(mock_service.list_all())
    }


@mock_router.post("/_admin/{name}")
async def mock_upsert(name: str, request: Request):
    """动态添加/更新 mock 数据"""
    try:
        data = await request.json()
    except Exception:
        return JSONResponse(
            content={"success": False, "message": "请求体必须是有效 JSON"},
            status_code=400,
        )
    
    import json
    file_path = mock_service._get_file_path(name)
    with open(file_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    
    mock_service.reload(name)
    return {
        "success": True,
        "message": f"Mock 数据 '{name}' 已保存",
        "path": file_path,
    }


@mock_router.delete("/_admin/{name}")
async def mock_remove(name: str):
    """删除 mock 数据"""
    file_path = mock_service._get_file_path(name)
    if os.path.exists(file_path):
        os.remove(file_path)
        mock_service._cache.pop(name, None)
        return {"success": True, "message": f"Mock 数据 '{name}' 已删除"}
    return JSONResponse(
        content={"success": False, "message": f"Mock 数据 '{name}' 不存在"},
        status_code=404,
    )


@mock_router.post("/_admin/{name}/reload")
async def mock_reload(name: str):
    """热重载指定 mock 数据"""
    data = mock_service.reload(name)
    if data is None:
        return JSONResponse(
            content={"success": False, "message": f"Mock 数据 '{name}' 不存在"},
            status_code=404,
        )
    return {"success": True, "message": f"Mock 数据 '{name}' 已重新加载"}


@mock_router.get("/{name}")
async def mock_get(
    name: str,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 GET mock 端点"""
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


@mock_router.post("/{name}")
async def mock_post(
    name: str,
    request: Request,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 POST mock 端点"""
    try:
        body_json = await request.json()
        logger.debug(f"Mock POST [{name}] 收到请求: {body_json}")
    except Exception:
        body_json = {}
    
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


@mock_router.put("/{name}")
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


@mock_router.delete("/{name}")
async def mock_delete(
    name: str,
    delay: int = Query(default=0, description="模拟延迟（毫秒）"),
    status: Optional[int] = Query(default=None, description="强制状态码"),
):
    """通用 DELETE mock 端点"""
    body, code, headers = mock_service.resolve(name, delay_ms=delay, status_code=status)
    return JSONResponse(content=body, status_code=code, headers=headers)


# ── 资费备案套餐查询 API ───────────────────────────────────────────────────

from pydantic import BaseModel

class TariffPackageQuery(BaseModel):
    package_code: Optional[str] = None
    category: Optional[str] = None
    active_only: Optional[bool] = True


@mock_router.post("/tariff/packages")
async def mock_tariff_packages(
    package_code: Optional[str] = Query(default=None, description="Package code filter"),
    category: Optional[str] = Query(default=None, description="Category filter"),
    active_only: Optional[bool] = Query(default=True, description="Only active packages"),
    delay: int = Query(default=0, description="Simulated delay (ms)"),
):
    """Mock tariff package query"""
    body, code, headers = mock_service.resolve("tariff_packages", delay_ms=delay)
    
    if "data" in body:
        filtered = body["data"]
        
        if package_code:
            filtered = [p for p in filtered if p.get("packageCode") == package_code]
        
        if category:
            filtered = [p for p in filtered if category.lower() in p.get("category", "").lower()]
        
        if active_only:
            filtered = [p for p in filtered if p.get("isActive", True)]
        
        body["data"] = filtered
        body["total"] = len(filtered)
    
    return JSONResponse(content=body, status_code=code, headers=headers)


# 注册 mock 路由到 app
app.include_router(mock_router)


if __name__ == "__main__":
    import uvicorn
    
    logger.info(f"============================================")
    logger.info(f"  Mock API Server 启动")
    logger.info(f"============================================")
    logger.info(f"  端口: {PORT}")
    logger.info(f"  数据目录: {mock_service._data_dir}")
    logger.info(f"  可用端点: {mock_service.list_all()}")
    logger.info(f"============================================")
    
    uvicorn.run(
        "mock_server:app",
        host="0.0.0.0",
        port=PORT,
        reload=False,
        workers=1
    )

from fastapi import APIRouter

router = APIRouter(prefix="/api/v1", tags=["health"])


@router.get("/health")
async def health_check():
    """健康检查接口"""
    return {"status": "ok", "service": "ai-form-assistant"}


@router.head("/health")
async def health_check_head():
    """健康检查接口（HEAD请求）"""
    return None
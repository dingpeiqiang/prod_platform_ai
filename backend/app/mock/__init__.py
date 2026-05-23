"""
Mock API 模块

用于模拟外部系统未提供的 API。
通过 MOCK_API_ENABLED 配置开关控制是否启用。
"""

from app.mock.router import router
from app.mock.service import mock_service

__all__ = ["router", "mock_service"]

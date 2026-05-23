"""
Mock 数据服务

从 JSON 文件加载 mock 数据，支持动态路由匹配、延迟模拟、状态码模拟。
"""

import json
import logging
import os
import time
from pathlib import Path
from typing import Any, Dict, Optional

logger = logging.getLogger("mock.service")

# mock 数据目录
MOCK_DATA_DIR = Path(__file__).parent / "data"


class MockService:
    """Mock 数据管理服务"""

    def __init__(self, data_dir: Optional[Path] = None):
        self.data_dir = data_dir or MOCK_DATA_DIR
        self._cache: Dict[str, Any] = {}
        self._ensure_data_dir()

    def _ensure_data_dir(self):
        """确保数据目录存在"""
        os.makedirs(self.data_dir, exist_ok=True)

    def _get_file_path(self, name: str) -> Path:
        """获取 mock 数据文件路径"""
        return self.data_dir / f"{name}.json"

    def load(self, name: str, use_cache: bool = True) -> Optional[Dict[str, Any]]:
        """加载指定名称的 mock 数据

        Args:
            name: mock 数据名称（不含 .json 后缀）
            use_cache: 是否使用缓存（热加载时设为 False）

        Returns:
            mock 数据字典，文件不存在返回 None
        """
        if use_cache and name in self._cache:
            return self._cache[name]

        file_path = self._get_file_path(name)
        if not file_path.exists():
            logger.warning(f"Mock 数据文件不存在: {file_path}")
            return None

        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            if use_cache:
                self._cache[name] = data
            logger.debug(f"已加载 mock 数据: {name}")
            return data
        except (json.JSONDecodeError, IOError) as e:
            logger.error(f"加载 mock 数据失败 {file_path}: {e}")
            return None

    def reload(self, name: str) -> Optional[Dict[str, Any]]:
        """热重载指定 mock 数据（清除缓存后重新加载）"""
        self._cache.pop(name, None)
        return self.load(name, use_cache=False)

    def list_all(self) -> list:
        """列出所有可用的 mock 数据名称"""
        if not self.data_dir.exists():
            return []
        return [
            f.stem
            for f in self.data_dir.glob("*.json")
            if f.is_file()
        ]

    def save(self, name: str, data: Dict[str, Any]) -> Path:
        """保存 mock 数据到文件"""
        file_path = self._get_file_path(name)
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        self._cache.pop(name, None)  # 清除缓存
        logger.info(f"已保存 mock 数据: {name}")
        return file_path

    def resolve(
        self,
        name: str,
        delay_ms: int = 0,
        status_code: Optional[int] = None,
    ) -> tuple:
        """解析 mock 数据，返回 (响应数据, 状态码, 响应头)

        Args:
            name: mock 数据名称
            delay_ms: 模拟延迟（毫秒）
            status_code: 强制覆盖状态码

        Returns:
            (body, status_code, headers)
        """
        data = self.load(name)
        if data is None:
            return {
                "success": False,
                "message": f"Mock 数据 '{name}' 不存在",
                "available": self.list_all(),
            }, 404, {}

        # 模拟延迟
        if delay_ms > 0:
            time.sleep(delay_ms / 1000.0)

        # 从数据中提取配置
        mock_config = data.get("_mock", {})
        status = status_code or mock_config.get("statusCode", 200)
        headers = mock_config.get("headers", {})
        delay = delay_ms or mock_config.get("delayMs", 0)

        if delay > 0 and delay_ms == 0:
            time.sleep(delay / 1000.0)

        # 返回时去掉 _mock 元数据
        body = {k: v for k, v in data.items() if k != "_mock"}
        return body, status, headers


# 全局单例
mock_service = MockService()

# 资费备案 MCP 工具
# 根据套餐编码查询套餐完整信息，供 AI 表单系统自动填充备案字段

import requests
import logging
from typing import Dict, Any, Optional

from .tool_hub import mcptool

logger = logging.getLogger("mcp_tools")

# 套餐查询 API 配置
TARIFF_API_CONFIG = {
    "base_url": "http://localhost:8080",
    "timeout": 10
}

# API 字段名 → 本体字段名 映射（解决 API 字段名与本体不一致的问题）
# key: API 返回的字段名（驼峰转蛇形后）
# value: 本体 ontology 中定义的 fieldCode
_API_TO_ONTO_FIELD_MAP = {
    "tariff_name": "name",          # API: 套餐名称 → 本体: 资费名称
    "tariff_code": None,            # API: 套餐编码 → 本体无此字段，丢弃
    "offline_day": "offline_day",   # 显式声明，避免因 API 无此字段而丢失本体配置
}


def configure_tariff_api(base_url: str = None, timeout: int = None):
    """配置套餐查询 API"""
    if base_url:
        TARIFF_API_CONFIG["base_url"] = base_url.rstrip("/")
    if timeout:
        TARIFF_API_CONFIG["timeout"] = timeout


@mcptool(
    name="query_tariff_by_code",
    description="根据套餐编码（如 P000111、P123456）查询资费套餐详细信息。当用户提到「备案套餐 X」「套餐编码 X」「我要备案 P000111」时，**必须**从用户输入中提取 X（如 P000111）作为 tariff_code 参数并调用本工具。参数 tariff_code 必填，绝不能为空。",
    category="tariff"
)
def query_tariff_by_code(tariff_code: str) -> Dict[str, Any]:
    """
    根据套餐编码查询套餐信息

    Args:
        tariff_code: 套餐编码，如 P000111、P123456

    Returns:
        {
            "success": True/False,
            "error": "错误信息（失败时）",
            "tariff_code": "P000111",
            "tariff_name": "畅享99元套餐",
            "reporter": "JT1",
            "action_type": "A",
            "type1": "1",
            "type2": "1",
            ...（其他字段）
        }
    """
    import time
    req_time = time.time()

    if not tariff_code or not tariff_code.strip():
        logger.warning("[query_tariff_by_code] ⚠️ 套餐编码为空，跳过查询")
        return {"success": False, "error": "套餐编码不能为空"}

    url = f"{TARIFF_API_CONFIG['base_url']}/tariff/query"
    req_body = {"tariff_code": tariff_code.strip()}

    logger.info("[query_tariff_by_code] ┌── 套餐查询请求 ──")
    logger.info("[query_tariff_by_code] │ URL: %s", url)
    logger.info("[query_tariff_by_code] │ 请求体: %s", req_body)
    logger.info("[query_tariff_by_code] │ 超时: %ds", TARIFF_API_CONFIG["timeout"])

    try:
        resp = requests.post(
            url,
            json=req_body,
            headers={"Content-Type": "application/json"},
            timeout=TARIFF_API_CONFIG["timeout"]
        )

        elapsed_ms = (time.time() - req_time) * 1000
        logger.info("[query_tariff_by_code] │ 响应状态: HTTP %d (耗时 %.1fms)", resp.status_code, elapsed_ms)

        if resp.status_code == 200:
            result = resp.json()
            logger.info("[query_tariff_by_code] │ 响应正文: %s", result)

            # 支持两种响应结构：
            #   1. {"success": true, "data": {...}}        （顶层 success）
            #   2. {"code": 200, "data": {"success": true, "data": {...}}}
            data = result.get("data", result)
            api_success = result.get("success", data.get("success", False))

            if api_success:
                inner_data = data.get("data", data)

                # 驼峰 → 蛇形 字段名映射（如 tariffCode → tariff_code）
                def to_snake(name):
                    import re
                    s = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
                    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s).lower()

                mapped_data = {}
                for k, v in inner_data.items():
                    if v is None:
                        continue
                    snake_key = to_snake(k)
                    # 应用字段名映射（API名 → 本体fieldCode）
                    final_key = _API_TO_ONTO_FIELD_MAP.get(snake_key, snake_key)
                    if final_key is not None:          # None 表示丢弃
                        mapped_data[final_key] = v

                returned_fields = list(mapped_data.keys())
                logger.info("[query_tariff_by_code] │ 字段映射: %s", {k: v for k, v in mapped_data.items()})
                logger.info("[query_tariff_by_code] │ 映射后字段: %s", returned_fields)
                logger.info("[query_tariff_by_code] │ 成功返回 %d 个字段: %s", len(returned_fields), returned_fields)
                logger.info("[query_tariff_by_code] └── ✅ 查询成功")
                return {"success": True, **mapped_data}
            else:
                err_msg = result.get("msg") or result.get("error", {}).get("message", "查询失败")
                logger.warning("[query_tariff_by_code] │ API 业务错误: %s", err_msg)
                logger.info("[query_tariff_by_code] └── ❌ 查询失败")
                return {
                    "success": False,
                    "error": err_msg
                }
        else:
            err_body = resp.text[:200]
            logger.warning("[query_tariff_by_code] │ HTTP 错误: %s", err_body)
            logger.info("[query_tariff_by_code] └── ❌ 查询失败")
            return {
                "success": False,
                "error": f"HTTP {resp.status_code}: {err_body}"
            }

    except requests.exceptions.Timeout:
        elapsed_ms = (time.time() - req_time) * 1000
        logger.warning("[query_tariff_by_code] │ ❌ 请求超时 (%.1fms)", elapsed_ms)
        logger.info("[query_tariff_by_code] └── 原因: 套餐查询服务响应超时")
        return {"success": False, "error": "套餐查询请求超时，请检查网络"}
    except requests.exceptions.ConnectionError as e:
        elapsed_ms = (time.time() - req_time) * 1000
        logger.warning("[query_tariff_by_code] │ ❌ 连接失败 (%.1fms): %s", elapsed_ms, e)
        logger.info("[query_tariff_by_code] └── 原因: 无法连接 %s，确认服务已启动", TARIFF_API_CONFIG["base_url"])
        return {"success": False, "error": f"无法连接套餐查询服务，请确认服务已启动"}
    except Exception as e:
        elapsed_ms = (time.time() - req_time) * 1000
        logger.exception("[query_tariff_by_code] │ ❌ 异常 (%.1fms): %s", elapsed_ms, e)
        logger.info("[query_tariff_by_code] └── 查询异常终止")
        return {"success": False, "error": str(e)}


@mcptool(
    name="query_tariff_info",
    description="查询套餐核心信息（简化版），返回套餐编码、名称、备案主体、资费费率等核心字段。当需要快速查询套餐概要时使用，参数 tariff_code 必填。",
    category="tariff"
)
def query_tariff_info(tariff_code: str) -> Dict[str, Any]:
    """
    查询套餐核心信息（简化版）

    Args:
        tariff_code: 套餐编码

    Returns:
        {
            "success": True/False,
            "error": "错误信息（失败时）",
            "tariff_code": "P000111",
            "tariff_name": "畅享99元套餐",
            "reporter": "JT1",
            "fees": 99
        }
    """
    logger.info("[query_tariff_info] 调用 query_tariff_by_code 查询套餐 %s", tariff_code)
    full = query_tariff_by_code(tariff_code)
    if not full.get("success"):
        logger.warning("[query_tariff_info] 底层查询失败: %s", full.get("error"))
        return full

    # 只返回核心字段（字段名已统一为本体名）
    core_fields = ["name", "reporter", "fees", "fees_unit",
                   "type1", "type2", "tariff_attr", "online_day"]
    result = {k: v for k, v in full.items() if k in core_fields}
    logger.info("[query_tariff_info] 返回核心字段 %d 个: %s", len(result), list(result.keys()))
    return result
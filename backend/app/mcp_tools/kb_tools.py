# 知识库问答 MCP 工具封装
# 调用外部知识库 API 进行问答

from typing import Dict, Any, List, Optional
import requests
import logging
from .tool_hub import mcptool

logger = logging.getLogger("mcp_tools")


# ============================================================
# 知识库配置（可从配置文件加载）
# ============================================================

KB_API_CONFIG = {
    "enabled": False,
    "base_url": "",  # 外部知识库 API 地址
    "api_key": "",
    "model": "default",
    "timeout": 30
}


def configure_kb_api(base_url: str, api_key: str, model: str = None):
    """
    配置知识库 API
    
    Args:
        base_url: API 地址
        api_key: API 密钥
        model: 可选的模型名称
    """
    KB_API_CONFIG["enabled"] = True
    KB_API_CONFIG["base_url"] = base_url.rstrip("/")
    KB_API_CONFIG["api_key"] = api_key
    if model:
        KB_API_CONFIG["model"] = model


# ============================================================
# 知识库问答工具
# ============================================================

@mcptool(
    name="kb_qa",
    description="基于企业知识库回答用户问题。当用户询问公司制度、产品信息、操作指南等知识性问题时使用。",
    category="kb"
)
def knowledge_qa(question: str, top_k: int = 5) -> Dict[str, Any]:
    """
    知识库问答
    
    Args:
        question: 用户问题
        top_k: 返回的最相关结果数量
        
    Returns:
        问答结果，包含答案和来源
    """
    if not KB_API_CONFIG["enabled"]:
        return {
            "success": False,
            "error": "知识库 API 未配置",
            "message": "请先配置知识库 API（configure_kb_api）"
        }
    
    try:
        headers = {
            "Authorization": f"Bearer {KB_API_CONFIG['api_key']}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "question": question,
            "top_k": top_k,
            "model": KB_API_CONFIG["model"]
        }
        
        response = requests.post(
            f"{KB_API_CONFIG['base_url']}/qa",
            json=payload,
            headers=headers,
            timeout=KB_API_CONFIG["timeout"]
        )
        
        if response.status_code == 200:
            result = response.json()
            return {
                "success": True,
                "answer": result.get("answer", ""),
                "sources": result.get("sources", []),
                "confidence": result.get("confidence", 0)
            }
        else:
            return {
                "success": False,
                "error": f"API 返回错误: {response.status_code}",
                "message": response.text
            }
            
    except requests.exceptions.Timeout:
        return {
            "success": False,
            "error": "知识库 API 请求超时"
        }
    except requests.exceptions.ConnectionError:
        return {
            "success": False,
            "error": "无法连接知识库 API"
        }
    except Exception as e:
        logger.exception(f"知识库问答失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


# ============================================================
# 知识库检索工具（返回原始结果，不生成答案）
# ============================================================

@mcptool(
    name="kb_search",
    description="在知识库中检索相关内容。返回最相关的文档片段，不生成答案。",
    category="kb"
)
def knowledge_search(query: str, top_k: int = 5) -> Dict[str, Any]:
    """
    知识库检索
    
    Args:
        query: 检索查询
        top_k: 返回结果数量
        
    Returns:
        检索结果列表
    """
    if not KB_API_CONFIG["enabled"]:
        return {
            "success": False,
            "error": "知识库 API 未配置"
        }
    
    try:
        headers = {
            "Authorization": f"Bearer {KB_API_CONFIG['api_key']}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "query": query,
            "top_k": top_k
        }
        
        response = requests.post(
            f"{KB_API_CONFIG['base_url']}/search",
            json=payload,
            headers=headers,
            timeout=KB_API_CONFIG["timeout"]
        )
        
        if response.status_code == 200:
            result = response.json()
            return {
                "success": True,
                "results": result.get("results", []),
                "total": result.get("total", 0)
            }
        else:
            return {
                "success": False,
                "error": f"API 返回错误: {response.status_code}"
            }
            
    except Exception as e:
        logger.exception(f"知识库检索失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


# ============================================================
# 知识库状态检查工具
# ============================================================

@mcptool(
    name="kb_status",
    description="检查知识库 API 的连接状态和可用性。",
    category="kb"
)
def check_kb_status() -> Dict[str, Any]:
    """
    检查知识库状态
    
    Returns:
        状态信息
    """
    if not KB_API_CONFIG["enabled"]:
        return {
            "success": False,
            "enabled": False,
            "message": "知识库 API 未启用"
        }
    
    try:
        headers = {
            "Authorization": f"Bearer {KB_API_CONFIG['api_key']}"
        }
        
        response = requests.get(
            f"{KB_API_CONFIG['base_url']}/health",
            headers=headers,
            timeout=5
        )
        
        if response.status_code == 200:
            return {
                "success": True,
                "enabled": True,
                "status": "healthy",
                "message": "知识库 API 正常运行"
            }
        else:
            return {
                "success": True,
                "enabled": True,
                "status": "degraded",
                "message": f"API 响应异常: {response.status_code}"
            }
            
    except Exception as e:
        return {
            "success": True,
            "enabled": True,
            "status": "unavailable",
            "message": f"连接失败: {str(e)}"
        }

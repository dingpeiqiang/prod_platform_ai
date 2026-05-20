"""
资费备案 API - 基于 LangChain 的智能表单处理端点
前缀: /api/v1/tariff
"""
from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
import json
import logging

from app.core.database import get_db
from app.langchain.tariff_agent import TariffProcessor
from app.core.config_loader import config_loader
from app.mcp_tools.tariff_tools import query_tariff_by_code

logger = logging.getLogger("tariff_filing_api")
router = APIRouter(prefix="/api/v1/tariff", tags=["tariff-filing"])


# ── Request / Response Models ──────────────────────────────────

class TariffRequest(BaseModel):
    user_input: str = Field(..., description="用户输入的查询内容")
    session_id: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class ValidationResult(BaseModel):
    field: str
    fieldName: str
    value: Any
    result: str
    reason: str
    suggestion: str


class TariffResponse(BaseModel):
    success: bool
    action: Optional[str] = None
    message: Optional[str] = None
    formCode: Optional[str] = None
    extractedFields: Optional[Dict[str, Any]] = None
    validationResults: Optional[List[ValidationResult]] = None
    missing_fields: Optional[List[Dict[str, str]]] = None
    tool_calls: Optional[List[Dict[str, Any]]] = None


# ── 全局 Agent 实例 ────────────────────────────────────────────

_tariff_agent = None

def get_tariff_processor() -> TariffProcessor:
    """获取 TariffProcessor 实例（单例模式）"""
    global _tariff_agent
    if _tariff_agent is None:
        _tariff_agent = TariffProcessor()
    return _tariff_agent


# ── API Endpoints ──────────────────────────────────────────────

@router.post("/process", response_model=TariffResponse)
async def process_tariff_request(request: TariffRequest):
    """
    处理资费备案请求 - 完整业务流程
    
    根据用户输入自动执行以下步骤：
    1. 提取套餐编码
    2. 查询套餐信息（调用 MCP 工具）
    3. 生成表单数据
    4. 执行表单校验
    
    返回结果根据业务流程动态决定：
    - ask_user: 需要用户补充信息
    - generate_form: 生成空表单（工具调用失败时）
    - validate_form: 返回表单数据和校验结果
    """
    logger.info(f"[tariff/process] 收到请求: {request.user_input[:100]}")
    
    try:
        processor = get_tariff_processor()
        result = await processor.process(request.user_input)
        
        logger.info(f"[tariff/process] Agent 返回 action={result.get('action')}")
        
        return TariffResponse(
            success=True,
            action=result.get("action"),
            message=result.get("message"),
            formCode=result.get("formCode"),
            extractedFields=result.get("extractedFields"),
            validationResults=result.get("validationResults"),
            missing_fields=result.get("missing_fields"),
            tool_calls=result.get("tool_calls")
        )
    
    except Exception as e:
        logger.error(f"[tariff/process] 处理失败: {e}", exc_info=True)
        return TariffResponse(
            success=False,
            message=f"处理失败，请稍后重试: {str(e)}"
        )


@router.post("/query")
async def query_tariff(request: Dict[str, Any]):
    """
    查询套餐信息 - 直接调用 MCP 工具
    
    Args:
        tariff_code: 套餐编码（如 P000111）
    
    Returns:
        套餐详细信息
    """
    tariff_code = request.get("tariff_code")
    logger.info(f"[tariff/query] 查询套餐: {tariff_code}")
    
    if not tariff_code:
        return {"success": False, "error": "套餐编码不能为空"}
    
    try:
        result = query_tariff_by_code(tariff_code)
        return result
    except Exception as e:
        logger.error(f"[tariff/query] 查询失败: {e}")
        return {"success": False, "error": str(e)}


@router.post("/validate")
async def validate_form(request: Dict[str, Any]):
    """
    验证表单数据
    
    Args:
        form_code: 表单编码
        form_data: 表单数据字典
    
    Returns:
        校验结果
    """
    form_code = request.get("form_code", "tariff_filing_publicity")
    form_data = request.get("form_data", {})
    
    logger.info(f"[tariff/validate] 验证表单: {form_code}, 字段数={len(form_data)}")
    
    try:
        processor = get_tariff_processor()
        validation_results = processor._validate_fields(form_data)
        
        passed = sum(1 for r in validation_results if r["result"] == "pass")
        warnings = sum(1 for r in validation_results if r["result"] == "warning")
        errors = sum(1 for r in validation_results if r["result"] == "error")
        
        return {
            "success": True,
            "formCode": form_code,
            "validationResults": validation_results,
            "summary": {
                "total": len(validation_results),
                "passed": passed,
                "warnings": warnings,
                "errors": errors
            },
            "message": agent._generate_validation_message(validation_results)
        }
    except Exception as e:
        logger.error(f"[tariff/validate] 验证失败: {e}")
        return {"success": False, "error": str(e)}


@router.get("/ontology")
async def get_ontology():
    """
    获取资费备案表单本体定义
    """
    ontology = config_loader.get_ontology("tariff_filing_publicity")
    if not ontology:
        return {"success": False, "error": "未找到资费备案表单本体"}
    
    return {
        "success": True,
        "formCode": ontology.get("formCode"),
        "formName": ontology.get("formName"),
        "description": ontology.get("description"),
        "fields": [
            {
                "fieldCode": f.get("fieldCode"),
                "fieldName": f.get("fieldName"),
                "fieldType": f.get("fieldType"),
                "required": f.get("required", False),
                "options": f.get("enumConfig", {}).get("options", []),
                "ruleDescription": f.get("ruleDescription", "")
            }
            for entity in ontology.get("entities", [])
            for f in entity.get("fields", [])
        ]
    }


@router.get("/fields/default")
async def get_default_fields():
    """
    获取默认字段值
    """
    processor = get_tariff_processor()
    default_fields = processor._generate_default_fields()
    
    return {
        "success": True,
        "fields": default_fields
    }


@router.post("/extract")
async def extract_fields(request: Dict[str, Any]):
    """
    从用户输入中提取字段
    
    Args:
        user_input: 用户输入文本
    
    Returns:
        提取的字段值
    """
    user_input = request.get("user_input", "")
    
    if not user_input:
        return {"success": False, "error": "用户输入不能为空"}
    
    processor = get_tariff_processor()
    
    # 提取套餐编码
    tariff_code = processor._extract_tariff_code(user_input)
    
    # 提取其他字段
    fields = processor._generate_default_fields()
    fields = processor._extract_from_input(fields, user_input)
    
    return {
        "success": True,
        "tariffCode": tariff_code,
        "extractedFields": fields
    }


@router.post("/generate-form")
async def generate_form(request: Dict[str, Any]):
    """
    生成表单数据
    
    Args:
        tariff_code: 套餐编码（可选）
        user_input: 用户输入（可选）
        override_fields: 覆盖字段（可选）
    
    Returns:
        完整的表单数据
    """
    tariff_code = request.get("tariff_code", "")
    user_input = request.get("user_input", "")
    override_fields = request.get("override_fields", {})
    
    logger.info(f"[tariff/generate-form] 生成表单: tariff_code={tariff_code}")
    
    processor = get_tariff_processor()
    
    # 生成默认字段
    fields = processor._generate_default_fields()
    
    # 设置套餐编码
    if tariff_code:
        fields["bossid"] = tariff_code
    
    # 从用户输入提取字段
    if user_input:
        fields = processor._extract_from_input(fields, user_input)
    
    # 应用覆盖字段
    fields.update(override_fields)
    
    # 执行校验
    validation_results = processor._validate_fields(fields)
    
    return {
        "success": True,
        "formCode": "tariff_filing_publicity",
        "extractedFields": fields,
        "validationResults": validation_results,
        "message": processor._generate_validation_message(validation_results)
    }

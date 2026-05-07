from fastapi import APIRouter
from app.schemas.form import (
    ValidationFieldRequest, ValidationFieldResponse,
    ValidationFormRequest, ValidationFormResponse,
    ValidationIssue,
    LLMValidationRequest, LLMValidationResponse, LLMValidationIssue
)
from app.skills.validation_skill import ValidationSkill

router = APIRouter(prefix="/api/v1", tags=["validation"])


@router.post("/validation/field", response_model=ValidationFieldResponse)
async def validate_field(request: ValidationFieldRequest):
    result = ValidationSkill.validate_field(request.fieldValue, request.rules)
    return ValidationFieldResponse(
        success=result.get("success", True),
        valid=result.get("valid", True),
        errors=result.get("errors", []),
        issues=[ValidationIssue(**i) for i in result.get("issues", [])]
    )


@router.post("/validation/form", response_model=ValidationFormResponse)
async def validate_form(request: ValidationFormRequest):
    result = ValidationSkill.validate_form(request.data, request.fields)
    return ValidationFormResponse(
        success=result.get("success", True),
        valid=result.get("valid", True),
        errors=result.get("errors", []),
        warnings=result.get("warnings", []),
        issues=[ValidationIssue(**i) for i in result.get("issues", [])]
    )


@router.post("/validation/llm", response_model=LLMValidationResponse)
async def validate_form_with_llm(request: LLMValidationRequest):
    """
    LLM 智能表单校验

    基于本体的 ruleDescription 和 options，使用 LLM 理解自然语言规则
    进行智能校验，返回结构化的 errors 和 warnings。
    """
    result, reasoning = ValidationSkill.validate_with_ontology(
        request.form_code,
        request.data
    )

    # 转换 errors 为 LLMValidationIssue 格式
    errors = []
    for err in result.get("errors", []):
        if isinstance(err, dict):
            errors.append(LLMValidationIssue(
                field_name=err.get("field_name", err.get("fieldName", "未知字段")),
                field_code=err.get("field", err.get("fieldCode", "")),
                reason=err.get("message", err.get("reason", str(err))),
                level="error"
            ))
        else:
            errors.append(LLMValidationIssue(
                field_name="未知字段",
                field_code="",
                reason=str(err),
                level="error"
            ))

    # 转换 warnings 为 LLMValidationIssue 格式
    warnings = []
    for warn in result.get("warnings", []):
        if isinstance(warn, dict):
            warnings.append(LLMValidationIssue(
                field_name=warn.get("field_name", warn.get("fieldName", "未知字段")),
                field_code=warn.get("field", warn.get("fieldCode", "")),
                reason=warn.get("message", warn.get("reason", str(warn))),
                level="warning"
            ))
        else:
            warnings.append(LLMValidationIssue(
                field_name="未知字段",
                field_code="",
                reason=str(warn),
                level="warning"
            ))

    return LLMValidationResponse(
        success=result.get("success", True),
        valid=result.get("valid", True),
        errors=errors,
        warnings=warnings,
        reasoning=reasoning
    )
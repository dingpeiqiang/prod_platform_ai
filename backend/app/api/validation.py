from fastapi import APIRouter
from app.schemas.form import (
    ValidationFieldRequest, ValidationFieldResponse,
    ValidationFormRequest, ValidationFormResponse,
    ValidationIssue
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

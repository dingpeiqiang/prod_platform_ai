from fastapi import APIRouter
from app.schemas.form import (
    ValidationFieldRequest, ValidationFieldResponse,
    ValidationFormRequest, ValidationFormResponse
)
from app.services.validation_service import validation_engine

router = APIRouter(prefix="/api/v1", tags=["validation"])


@router.post("/validation/field", response_model=ValidationFieldResponse)
async def validate_field(request: ValidationFieldRequest):
    result = validation_engine.validate_field(request.fieldValue, request.rules)
    return ValidationFieldResponse(
        success=True,
        valid=result.valid,
        errors=result.errors
    )


@router.post("/validation/form", response_model=ValidationFormResponse)
async def validate_form(request: ValidationFormRequest):
    result = validation_engine.validate_form(request.data, request.fields)
    return ValidationFormResponse(
        success=True,
        valid=result.valid,
        errors=result.errors
    )

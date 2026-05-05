from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from datetime import datetime


class FieldRule(BaseModel):
    rule_type: str
    rule_value: Any
    message: str


class FormField(BaseModel):
    fieldCode: str
    fieldName: str
    fieldType: str
    required: bool = False
    disabled: bool = False
    hidden: bool = False
    rules: List[FieldRule] = Field(default_factory=list)
    recommend: List[Any] = Field(default_factory=list)  # 兼容字符串和对象: str | {"value","source","reason","confidence"}
    defaultValue: Optional[Any] = None


class FormSchema(BaseModel):
    formCode: str
    formName: str
    version: int = 1
    globalControl: Dict[str, Any] = Field(default_factory=dict)
    fields: List[FormField]


class FormGenerateRequest(BaseModel):
    userInput: str
    formCode: Optional[str] = None
    userId: Optional[str] = None
    extractedFields: Optional[Dict[str, Any]] = None  # LLM 已提取的字段值，优先使用
    fieldRecommendations: Optional[Dict[str, Any]] = None  # 推荐引擎输出（含source/reason/confidence）


class FormGenerateResponse(BaseModel):
    success: bool
    formSchema: FormSchema
    formId: str
    message: Optional[str] = None


class FieldChangeEvent(BaseModel):
    formId: str
    fieldCode: str
    fieldValue: Any
    userId: Optional[str] = None
    version: int


class FormControlEvent(BaseModel):
    formId: str
    controlType: str
    target: Optional[str] = None
    value: Any
    version: int


class FormSubmitRequest(BaseModel):
    formId: str
    data: Dict[str, Any]
    userId: Optional[str] = None
    version: int


class FormSubmitResponse(BaseModel):
    success: bool
    message: str = ""
    formInstanceId: Optional[int] = None


class OntologyConstraintRequest(BaseModel):
    formCode: str


class OntologyConstraintResponse(BaseModel):
    success: bool
    constraints: Dict[str, Any]
    message: Optional[str] = None


class HistoryRecommendRequest(BaseModel):
    formCode: str
    fieldCode: str
    userId: Optional[str] = None


class HistoryRecommendResponse(BaseModel):
    success: bool
    recommendations: List[str]
    message: Optional[str] = None


class OntologyValidateRequest(BaseModel):
    formCode: str
    form_schema: Dict[str, Any]


class OntologyValidateResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[str] = Field(default_factory=list)


class ValidationFieldRequest(BaseModel):
    fieldValue: Any
    rules: List[Dict[str, Any]]


class ValidationFieldResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[str] = Field(default_factory=list)


class ValidationFormRequest(BaseModel):
    data: Dict[str, Any]
    fields: List[Dict[str, Any]]


class ValidationFormResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[str] = Field(default_factory=list)

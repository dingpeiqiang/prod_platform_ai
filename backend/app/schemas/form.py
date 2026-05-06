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
    options: List[Any] = Field(default_factory=list)            # 枚举选项（静态枚举）
    enumConfig: Optional[Dict[str, Any]] = None                 # 枚举配置（外部API枚举或静态枚举定义）


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


class ValidationIssue(BaseModel):
    """校验问题项 - 规范化结构"""
    field: str = ""                           # 字段代码
    field_name: str = ""                      # 字段中文名
    error_code: str = "ERR_VAL_RULE_FAIL"     # 错误码（项目错误码体系）
    level: str = "error"                      # 错误级别：error / warning
    message: str                              # 错误信息


class ValidationFieldResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[str] = Field(default_factory=list)
    issues: List[ValidationIssue] = Field(default_factory=list)  # 详细问题列表


class ValidationFormRequest(BaseModel):
    data: Dict[str, Any]
    fields: List[Dict[str, Any]]


class ValidationFormResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[str] = Field(default_factory=list)
    warnings: List[str] = Field(default_factory=list)
    issues: List[ValidationIssue] = Field(default_factory=list)  # 详细问题列表（含字段+错误码+级别）

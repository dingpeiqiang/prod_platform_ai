"""
本体 MVP API

配置助手：智聊配置 / 合规校验 / 智读批量
运营助手：根因分析 / 风险稽核 / 看板 / 规则配置
"""
from typing import Any, Dict, List, Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services.ontology_mvp_service import OntologyMvpService

router = APIRouter(prefix="/api/v1/ontology-mvp", tags=["ontology-mvp"])


class ChatConfigureRequest(BaseModel):
    text: str = Field(..., description="自然语言配置描述")
    draft: Optional[Dict[str, Any]] = Field(None, description="当前草稿，支持多轮增量")


class ComplianceRequest(BaseModel):
    draft: Dict[str, Any] = Field(..., description="商品配置草稿")


class InferRequest(BaseModel):
    slots: Dict[str, Any] = Field(default_factory=dict)
    draft: Optional[Dict[str, Any]] = None


class BatchDocumentRequest(BaseModel):
    documentText: str = Field("", description="方案文档文本")
    packages: Optional[List[Dict[str, Any]]] = Field(None, description="可选：预抽取套餐段落")


class RootCauseRequest(BaseModel):
    offeringId: str = Field("OF-HF-128", description="异动商品ID")


class RiskAuditRequest(BaseModel):
    offeringIds: Optional[List[str]] = None


class RiskRulesRequest(BaseModel):
    zeroSalesShelfDays: Optional[int] = Field(None, description="长期零销在架天数阈值")
    zeroSalesDaysWindow: Optional[int] = None
    highRiskReviewDays: Optional[int] = None
    lowRevenuePercentile: Optional[float] = None
    ruleVersion: Optional[str] = None


@router.get("/graph")
async def get_graph_summary():
    return OntologyMvpService.get_graph_summary()


@router.get("/meta")
async def get_ontology_meta():
    return OntologyMvpService.get_ontology_meta()


@router.get("/ops/dashboard")
async def get_ops_dashboard():
    return OntologyMvpService.get_ops_dashboard()


@router.post("/config/chat")
async def chat_configure(request: ChatConfigureRequest):
    return OntologyMvpService.chat_configure(request.text, request.draft)


@router.post("/config/infer")
async def infer_fields(request: InferRequest):
    return OntologyMvpService.infer_fields(request.slots, request.draft)


@router.post("/config/compliance")
async def check_compliance(request: ComplianceRequest):
    return OntologyMvpService.check_compliance(request.draft)


@router.post("/config/batch")
async def batch_from_document(request: BatchDocumentRequest):
    return OntologyMvpService.batch_from_document(request.documentText, request.packages)


@router.post("/ops/root-cause")
async def analyze_root_cause(request: RootCauseRequest):
    return OntologyMvpService.analyze_root_cause(request.offeringId)


@router.post("/ops/risk-audit")
async def audit_risks(request: RiskAuditRequest):
    return OntologyMvpService.audit_risks(request.offeringIds)


@router.get("/ops/risk-rules")
async def get_risk_rules():
    return OntologyMvpService.update_risk_rules()


@router.post("/ops/risk-rules")
async def update_risk_rules(request: RiskRulesRequest):
    payload = {k: v for k, v in request.model_dump().items() if v is not None}
    return OntologyMvpService.update_risk_rules(payload)


@router.post("/ops/risk-rules/reset")
async def reset_risk_rules():
    return OntologyMvpService.reset_risk_rules()

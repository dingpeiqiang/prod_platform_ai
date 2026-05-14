from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from app.langchain import FormAgent, TaskAgent, ChatAgent, FormWorkflow, ValidationWorkflow
from app.langchain.chains import FormRecognitionChain, FieldExtractionChain, IntentRecognitionChain
from app.harness.observability.tracer import get_tracer, SpanStatus
import logging
import uuid

logger = logging.getLogger("langchain_api")

router = APIRouter(prefix="/api/v1/langchain", tags=["langchain"])


class ChatRequest(BaseModel):
    message: str
    context: Optional[List[Dict[str, str]]] = None


class ChatResponse(BaseModel):
    success: bool
    response: str
    intent_type: Optional[str] = None


class FormRecognitionRequest(BaseModel):
    user_input: str


class FormRecognitionResponse(BaseModel):
    success: bool
    form_code: str
    form_name: str
    confidence: float
    reason: str


class FieldExtractionRequest(BaseModel):
    form_code: str
    user_input: str


class FieldExtractionResponse(BaseModel):
    success: bool
    extracted_fields: Dict[str, Any]
    reasoning: str


class FormValidationRequest(BaseModel):
    form_code: str
    form_data: Dict[str, Any]


class FormValidationResponse(BaseModel):
    success: bool
    valid: bool
    errors: List[Dict[str, Any]]
    warnings: List[Dict[str, Any]]


class TaskRequest(BaseModel):
    task_type: str
    input_data: str


class TaskResponse(BaseModel):
    success: bool
    result: str


def _start_trace(operation_name: str):
    """开始追踪（线程安全）"""
    tracer = get_tracer()
    span = tracer.start_span(operation_name, trace_id=str(uuid.uuid4()), component="langchain-api")
    return tracer, span


def _finish_trace(tracer, span, success: bool, error_message: str = None):
    """结束追踪（线程安全）"""
    if success:
        span.add_tag("status", "success")
        tracer.finish_span(span, SpanStatus.OK)
    else:
        span.add_tag("status", "error")
        span.add_tag("error", error_message)
        tracer.finish_span(span, SpanStatus.ERROR)


# ==================== Chat API ====================
@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """聊天接口"""
    tracer, span = _start_trace("chat")
    try:
        agent = ChatAgent()
        response = await agent.respond(request.message, request.context)
        _finish_trace(tracer, span, True)
        return ChatResponse(success=True, response=response)
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Chat API error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Intent Recognition API ====================
@router.post("/intent/recognize")
async def recognize_intent(request: FormRecognitionRequest):
    """意图识别"""
    tracer, span = _start_trace("intent/recognize")
    try:
        chain = IntentRecognitionChain()
        result = await chain.run(request.user_input)
        _finish_trace(tracer, span, True)
        return {"success": True, **result}
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Intent recognition error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Form Recognition API ====================
@router.post("/form/recognize", response_model=FormRecognitionResponse)
async def recognize_form(request: FormRecognitionRequest):
    """表单识别"""
    tracer, span = _start_trace("form/recognize")
    try:
        chain = FormRecognitionChain()
        result = await chain.run(request.user_input)
        _finish_trace(tracer, span, True)
        return FormRecognitionResponse(
            success=True,
            form_code=result.get("formCode", "general"),
            form_name=result.get("formName", "通用表单"),
            confidence=result.get("confidence", 0.5),
            reason=result.get("reason", "")
        )
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Form recognition error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Field Extraction API ====================
@router.post("/form/extract", response_model=FieldExtractionResponse)
async def extract_fields(request: FieldExtractionRequest):
    """字段提取"""
    tracer, span = _start_trace("form/extract")
    try:
        chain = FieldExtractionChain(request.form_code)
        result = await chain.run(request.user_input)
        _finish_trace(tracer, span, True)
        return FieldExtractionResponse(
            success=True,
            extracted_fields=result.get("extractedFields", {}),
            reasoning=result.get("reasoning", "")
        )
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Field extraction error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Form Validation API ====================
@router.post("/form/validate", response_model=FormValidationResponse)
async def validate_form(request: FormValidationRequest):
    """表单验证"""
    tracer, span = _start_trace("form/validate")
    try:
        workflow = ValidationWorkflow()
        async for step in workflow.run(request.form_code, request.form_data):
            if step["type"] == "workflow_complete":
                result = step["result"]
                _finish_trace(tracer, span, True)
                return FormValidationResponse(
                    success=True,
                    valid=result.get("valid", False),
                    errors=result.get("errors", []),
                    warnings=result.get("warnings", [])
                )
        _finish_trace(tracer, span, True)
        return FormValidationResponse(success=True, valid=True, errors=[], warnings=[])
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Form validation error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Form Agent API ====================
@router.post("/agent/form")
async def form_agent(request: FormRecognitionRequest):
    """表单Agent处理"""
    tracer, span = _start_trace("agent/form")
    try:
        agent = FormAgent()
        result = await agent.process(request.user_input)
        _finish_trace(tracer, span, True)
        return {"success": True, **result}
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Form agent error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Task Agent API ====================
@router.post("/agent/task")
async def task_agent(request: TaskRequest):
    """任务Agent处理"""
    tracer, span = _start_trace("agent/task")
    try:
        agent = TaskAgent()
        result = await agent.execute_task(request.task_type, request.input_data)
        _finish_trace(tracer, span, True)
        return TaskResponse(success=True, result=result)
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Task agent error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Workflow API ====================
@router.post("/workflow/form")
async def form_workflow(request: FormRecognitionRequest):
    """表单工作流"""
    tracer, span = _start_trace("workflow/form")
    try:
        workflow = FormWorkflow()
        results = []
        async for step in workflow.run(request.user_input):
            results.append(step)
            if step["type"] == "workflow_complete":
                _finish_trace(tracer, span, True)
                return {"success": True, "steps": results, "final_result": step["result"]}
        _finish_trace(tracer, span, True)
        return {"success": True, "steps": results}
    except Exception as e:
        _finish_trace(tracer, span, False, str(e))
        logger.error(f"Form workflow error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Health Check ====================
@router.get("/health")
async def health():
    """健康检查"""
    tracer, span = _start_trace("health_check")
    _finish_trace(tracer, span, True)
    return {"status": "healthy", "service": "langchain-api"}
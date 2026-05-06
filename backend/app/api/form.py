from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.schemas.form import (
    FormGenerateRequest, FormGenerateResponse,
    FormSubmitRequest, FormSubmitResponse,
    OntologyConstraintRequest, OntologyConstraintResponse,
    OntologyValidateRequest, OntologyValidateResponse,
    HistoryRecommendRequest, HistoryRecommendResponse
)
from app.services.form_service import FormService
from app.services.ontology_service import OntologyService
from app.services.history_service import HistoryService
from app.services.validation_service import validation_engine
from app.websocket.manager import manager
from app.models.form import FormInstance, FormTemplate
from app.intent import get_intent_registry
from app.intent.base import IntentContext
from datetime import datetime
import uuid
import logging
import asyncio

logger = logging.getLogger("form_api")

router = APIRouter(prefix="/api/v1", tags=["form"])


@router.post("/form/generate", response_model=FormGenerateResponse)
async def generate_form(request: FormGenerateRequest, db: Session = Depends(get_db)):
    logger.info("[form/generate] 收到请求 form_code=%s user_id=%s user_input=%s",
                request.formCode, request.userId,
                (request.userInput or "")[:100])
    result = FormService.generate_form(
        user_input=request.userInput,
        form_code=request.formCode,
        user_id=request.userId,
        extracted_fields=request.extractedFields,
        field_recommendations=request.fieldRecommendations,
        db=db
    )
    
    if result["success"]:
        adapted_schema = FormService.adapt_for_chat_window(result["formSchema"])
        manager.set_form_state(result["formId"], {"version": 1, "schema": adapted_schema})
        logger.info("[form/generate] 成功 form_id=%s", result["formId"])
        return FormGenerateResponse(
            success=True,
            formSchema=adapted_schema,
            formId=result["formId"]
        )
    
    logger.warning("[form/generate] 失败: %s", result.get("message", "未知错误"))
    return FormGenerateResponse(
        success=False,
        formSchema=None,
        formId="",
        message=result.get("message", "生成表单失败")
    )


@router.post("/form/submit", response_model=FormSubmitResponse)
async def submit_form(request: FormSubmitRequest, db: Session = Depends(get_db)):
    try:
        logger.info("[form/submit] 收到提交 form_id=%s version=%s user_id=%s fields=%s",
                    request.formId, request.version, request.userId,
                    list((request.data or {}).keys()))
        
        # 获取表单状态
        form_state = manager.get_form_state(request.formId)
        if not form_state:
            logger.warning("[form/submit] 表单状态不存在 form_id=%s", request.formId)
            return FormSubmitResponse(
                success=False,
                message="表单已过期，请重新生成表单"
            )
        
        current_version = manager.get_version(request.formId)
        if request.version != current_version:
            logger.warning("[form/submit] 版本不匹配 form_id=%s client_ver=%s server_ver=%s",
                           request.formId, request.version, current_version)
            return FormSubmitResponse(
                success=False,
                message="表单版本不匹配，请刷新后重新填写"
            )
        
        schema = form_state.get("schema", {})
        fields = schema.get("fields", [])
        form_code = schema.get("formCode", "unknown")
        
        # 查出 template_id（FormService.generate_form 已自动 upsert FormTemplate）
        template_id = 0
        try:
            template = db.query(FormTemplate).filter(
                FormTemplate.form_code == form_code,
                FormTemplate.is_active == True
            ).first()
            if template:
                template_id = template.id
                logger.debug("[form/submit] 查到 template_id=%s form_code=%s", template_id, form_code)
            else:
                logger.warning("[form/submit] 未找到 FormTemplate form_code=%s，使用 template_id=0", form_code)
        except Exception as e:
            logger.warning("[form/submit] 查询 FormTemplate 失败: %s", e)
        
        validation_result = validation_engine.validate_form(request.data, fields)
        if not validation_result.valid:
            logger.warning("[form/submit] 规则引擎校验失败 form_id=%s errors=%s",
                           request.formId, validation_result.errors)
            return FormSubmitResponse(
                success=False,
                message="; ".join(validation_result.errors),
                formInstanceId=None
            )

        # ── LLM 智能校验（复用 Intent 机制）──────────────────────
        # ValidationHandler 会直接从本体加载 fields 定义
        registry = get_intent_registry()
        ctx = IntentContext(
            intent_data={
                "form_code": form_code,
                "form_data": request.data
            },
            intent_type="validate",
            intent_result="",
            confidence=1.0,
            ontologies={},
            request=None,
            db=db
        )

        llm_validation_passed = True
        llm_errors = []
        llm_warnings = []

        try:
            import json
            import re
            # 通过 IntentRegistry 分发校验意图
            async for event_str in registry.dispatch("validate", ctx):
                # 解析 SSE 事件：格式为 "data: {json}\n\n"
                try:
                    # 提取 JSON 部分（去掉 "data: " 前缀和尾部换行）
                    json_str = re.sub(r'^data:\s*', '', event_str.strip())
                    event_data = json.loads(json_str)
                except (json.JSONDecodeError, re.error):
                    # 无法解析的事件，跳过
                    continue

                event_type = event_data.get("type", "")
                if event_type == "validation_fail":
                    llm_validation_passed = False
                    llm_errors = event_data.get("errors", [])
                    logger.info(f"[form/submit] ValidationHandler 返回 validation_fail: {llm_errors}")
                elif event_type == "validation_pass":
                    llm_validation_passed = True
                    llm_warnings = event_data.get("warnings", [])
                    logger.info(f"[form/submit] ValidationHandler 返回 validation_pass: warnings={llm_warnings}")

            if not llm_validation_passed:
                logger.warning("[form/submit] LLM智能校验失败 form_id=%s errors=%s",
                               request.formId, llm_errors)
                return FormSubmitResponse(
                    success=False,
                    message="; ".join(llm_errors) if llm_errors else "表单智能校验未通过",
                    formInstanceId=None
                )

            if llm_warnings:
                logger.info("[form/submit] LLM智能校验警告 form_id=%s warnings=%s",
                            request.formId, llm_warnings)

        except Exception as e:
            logger.warning("[form/submit] LLM智能校验异常 form_id=%s error=%s，跳过校验",
                           request.formId, str(e))
            # LLM 校验异常不影响提交（降级处理）
        # ── LLM 智能校验结束 ────────────────────────────────────

        form_instance = FormInstance(
            form_id=request.formId,
            template_id=template_id,
            data=request.data,
            version=current_version,
            status="submitted",
            user_id=request.userId,
            submitted_at=datetime.now()
        )
        db.add(form_instance)
        db.commit()
        db.refresh(form_instance)
        
        for field_code, field_value in (request.data or {}).items():
            HistoryService.save_history(
                form_instance_id=form_instance.id,
                field_code=field_code,
                field_value=field_value,
                user_id=request.userId,
                db=db
            )
        
        manager.increment_version(request.formId)
        logger.info("[form/submit] 提交成功 form_id=%s form_code=%s instance_id=%s",
                    request.formId, form_code, form_instance.id)
        
        return FormSubmitResponse(
            success=True,
            message="表单提交成功",
            formInstanceId=form_instance.id
        )
    except Exception as e:
        logger.exception("[form/submit] 提交失败 form_id=%s error=%s", request.formId, str(e))
        return FormSubmitResponse(
            success=False,
            message=f"提交失败: {str(e)}"
        )


@router.post("/ontology/getFormConstraint", response_model=OntologyConstraintResponse)
async def get_form_constraint(request: OntologyConstraintRequest):
    logger.debug("[ontology/getFormConstraint] form_code=%s", request.formCode)
    result = OntologyService.get_form_constraint(request.formCode)
    if not result["success"]:
        logger.warning("[ontology/getFormConstraint] 未找到约束 form_code=%s", request.formCode)
    return OntologyConstraintResponse(
        success=result["success"],
        constraints=result.get("constraints", {}),
        message=result.get("message")
    )


@router.post("/ontology/validateSchema", response_model=OntologyValidateResponse)
async def validate_schema(request: OntologyValidateRequest):
    logger.debug("[ontology/validateSchema] form_code=%s", request.formCode)
    result = OntologyService.validate_schema(request.formCode, request.form_schema)
    if not result["valid"]:
        logger.warning("[ontology/validateSchema] Schema 校验失败 form_code=%s errors=%s",
                       request.formCode, result.get("errors", []))
    return OntologyValidateResponse(
        success=result["success"],
        valid=result["valid"],
        errors=result.get("errors", [])
    )


@router.post("/history/getRecommendValues", response_model=HistoryRecommendResponse)
async def get_recommend_values(request: HistoryRecommendRequest, db: Session = Depends(get_db)):
    result = HistoryService.get_recommend_values(
        request.formCode,
        request.fieldCode,
        request.userId,
        db
    )
    return HistoryRecommendResponse(
        success=result["success"],
        recommendations=result.get("recommendations", []),
        message=result.get("message")
    )


@router.websocket("/ws/form/{form_id}")
async def websocket_endpoint(websocket: WebSocket, form_id: str):
    await manager.connect(websocket, form_id)
    client_host = websocket.client.host if websocket.client else "unknown"
    logger.info("[ws/form] 客户端连接 form_id=%s client=%s", form_id, client_host)
    
    try:
        form_state = manager.get_form_state(form_id)
        await manager.send_personal_message({
            "type": "init",
            "formId": form_id,
            "version": form_state.get("version", 1),
            "schema": form_state.get("schema")
        }, websocket)
        logger.debug("[ws/form] 已发送 init 消息 form_id=%s version=%s",
                     form_id, form_state.get("version", 1))
        
        while True:
            data = await websocket.receive_json()
            msg_type = data.get("type")
            logger.debug("[ws/form] 收到消息 form_id=%s type=%s", form_id, msg_type)
            
            if msg_type == "fieldChange":
                current_version = manager.get_version(form_id)
                if data.get("version") == current_version:
                    manager.increment_version(form_id)
                    new_version = manager.get_version(form_id)
                    logger.debug("[ws/form] fieldChange form_id=%s field=%s ver=%s->%s",
                                 form_id, data.get("fieldCode"), current_version, new_version)
                    await manager.broadcast_to_form({
                        "type": "fieldChange",
                        "formId": form_id,
                        "fieldCode": data.get("fieldCode"),
                        "fieldValue": data.get("fieldValue"),
                        "version": new_version
                    }, form_id, exclude=websocket)
                else:
                    logger.warning("[ws/form] fieldChange 版本不匹配 form_id=%s client_ver=%s server_ver=%s",
                                   form_id, data.get("version"), current_version)
            
            elif msg_type == "formControl":
                current_version = manager.get_version(form_id)
                manager.increment_version(form_id)
                new_version = manager.get_version(form_id)
                logger.debug("[ws/form] formControl form_id=%s controlType=%s ver=%s->%s",
                             form_id, data.get("controlType"), current_version, new_version)
                await manager.broadcast_to_form({
                    "type": "formControl",
                    "formId": form_id,
                    "controlType": data.get("controlType"),
                    "target": data.get("target"),
                    "value": data.get("value"),
                    "version": new_version
                }, form_id)
            else:
                logger.warning("[ws/form] 未知消息类型 form_id=%s type=%s", form_id, msg_type)
    
    except WebSocketDisconnect:
        manager.disconnect(websocket, form_id)
        logger.info("[ws/form] 客户端断开 form_id=%s client=%s", form_id, client_host)
    except Exception as e:
        logger.exception("[ws/form] 异常 form_id=%s: %s", form_id, e)
        manager.disconnect(websocket, form_id)

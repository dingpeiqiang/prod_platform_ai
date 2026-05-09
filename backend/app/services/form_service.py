from typing import Dict, Any, Optional
import uuid
import logging
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.ontology_service import OntologyService
from app.core.config_loader import config_loader
from app.models.form import FormTemplate
from app.services.recommendations.merger import RecommendationMerger

logger = logging.getLogger("form_service")


def _convert_date_string(date_str: str) -> str:
    if not date_str or not isinstance(date_str, str):
        return date_str

    today = datetime.now()
    date_str_lower = date_str.strip().lower()

    if date_str_lower in ['今天', '今日']:
        return today.strftime('%Y-%m-%d')
    elif date_str_lower in ['明天', '明日']:
        return (today + timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['后天']:
        return (today + timedelta(days=2)).strftime('%Y-%m-%d')
    elif date_str_lower in ['昨天', '昨日']:
        return (today - timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['前天']:
        return (today - timedelta(days=2)).strftime('%Y-%m-%d')

    try:
        datetime.strptime(date_str, '%Y-%m-%d')
        return date_str
    except ValueError:
        pass

    return date_str


class FormService:
    @classmethod
    def generate_form(
        cls,
        user_input: str,
        form_code: str = None,
        user_id: str = None,
        extracted_fields: Dict[str, Any] = None,
        db: Session = None,
        field_recommendations: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        if not form_code:
            scene_result = SceneRecognitionSkill.recognize(user_input)
            form_code = scene_result["sceneCode"]

        logger.info("[FormService] 生成表单 form_code=%s user_id=%s "
                    "pre_extracted_fields=%s",
                    form_code, user_id,
                    list(extracted_fields.keys()) if extracted_fields else [])

        ontology_result = OntologyService.get_form_constraint(form_code)
        if not ontology_result["success"]:
            return {
                "success": False,
                "message": ontology_result.get("message", "获取本体约束失败")
            }

        ontology = ontology_result["constraints"]

        if db:
            try:
                existing = db.query(FormTemplate).filter(
                    FormTemplate.form_code == form_code,
                    FormTemplate.is_active == True
                ).first()
                if not existing:
                    template = FormTemplate(
                        form_code=form_code,
                        form_name=ontology.get("formName", form_code),
                        schema=ontology,
                        version=1,
                        is_active=True
                    )
                    db.add(template)
                    db.commit()
                    db.refresh(template)
                    logger.info("[FormService] 自动创建 FormTemplate form_code=%s id=%s",
                                form_code, template.id)
                else:
                    existing.schema = ontology
                    existing.version = (existing.version or 0) + 1
                    db.commit()
                    logger.debug("[FormService] FormTemplate 已存在 form_code=%s id=%s ver=%s",
                                 form_code, existing.id, existing.version)
            except Exception as e:
                logger.warning("[FormService] FormTemplate upsert 失败 form_code=%s: %s", form_code, e)

        fields = []

        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_dict = {
                    "fieldCode": field["fieldCode"],
                    "fieldName": field["fieldName"],
                    "fieldType": field["fieldType"],
                    "required": field.get("required", False),
                    "disabled": False,
                    "hidden": False,
                    "ruleDescription": field.get("ruleDescription", ""),
                    "recommend": [],
                    "defaultValue": None
                }

                if "rules" in field:
                    field_dict["rules"] = [
                        {"rule_type": r["rule_type"], "rule_value": r["rule_value"], "message": r["message"]}
                        for r in field["rules"]
                    ]
                else:
                    field_dict["rules"] = []

                if "enumConfig" in field:
                    field_dict["enumConfig"] = field["enumConfig"]

                fc = field["fieldCode"]
                merged = RecommendationMerger.merge_recommendations(
                    form_code=form_code,
                    field_code=fc,
                    field_def=field,
                    user_id=user_id,
                    db=db,
                    engine_recommendations=field_recommendations
                )
                field_dict["recommend"] = merged

                fields.append(field_dict)

        temp_schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", ""),
            "fields": fields
        }

        extracted_values: Dict[str, Any] = {}

        extraction_result = FieldExtractionSkill.extract(user_input, form_code, temp_schema)
        if extraction_result["success"]:
            method = extraction_result.get("method", "unknown")
            field_count = len(extraction_result["fields"])
            logger.info("[FormService] 字段提取完成 方法=%s 提取字段数=%d", method, field_count)
            if method == "llm":
                logger.debug("[FormService] LLM提取结果=%s", extraction_result["fields"])
            for ef in extraction_result["fields"]:
                fc = ef.get("fieldCode")
                fv = ef.get("fieldValue", ef.get("defaultValue"))
                if fc and fv is not None:
                    if fc in ['start_date', 'end_date', 'date', 'expense_date']:
                        fv = _convert_date_string(fv)
                        logger.debug("[FormService] Skills日期字段 %s 转换: %s -> %s", fc, ef.get("fieldValue"), fv)
                    extracted_values[fc] = fv

        if extracted_fields:
            for fc, fv in extracted_fields.items():
                if fc in ['success', 'error', 'message', 'result']:
                    continue
                if fv is not None and fv != "":
                    if fc in ['start_date', 'end_date', 'date', 'expense_date']:
                        fv = _convert_date_string(fv)
                        logger.debug("[FormService] 日期字段 %s 转换: %s -> %s", fc, extracted_fields[fc], fv)
                    extracted_values[fc] = fv
                    logger.debug("[FormService] 使用 LLM 提取字段 %s=%s", fc, fv)

        logger.info("[FormService] 最终提取字段: %s", extracted_values)

        for field in fields:
            field_code = field["fieldCode"]

            if field_code in extracted_values:
                field["defaultValue"] = extracted_values[field_code]
            else:
                if field.get("recommend") and len(field["recommend"]) > 0:
                    first_rec = field["recommend"][0]
                    if isinstance(first_rec, dict):
                        field["defaultValue"] = first_rec.get("value", "")
                    else:
                        field["defaultValue"] = first_rec

        form_id = f"form_{uuid.uuid4().hex[:12]}"

        form_schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", ""),
            "version": 1,
            "globalControl": {},
            "fields": fields
        }

        logger.info("[FormService] 表单生成完成 form_id=%s fields_count=%d "
                    "prefilled=%d",
                    form_id, len(fields), len(extracted_values))

        return {
            "success": True,
            "formSchema": form_schema,
            "formId": form_id,
            "sceneMethod": extraction_result.get("method", "unknown"),
            "extractedFields": list(extracted_values.keys())
        }

    @classmethod
    def adapt_for_chat_window(cls, form_schema: Dict[str, Any]) -> Dict[str, Any]:
        adapted_schema = form_schema.copy()

        for field in adapted_schema["fields"]:
            if field["fieldType"] == "select":
                field["fieldType"] = "select"
            elif field["fieldType"] == "textarea":
                field["fieldType"] = "textarea"
            elif field["fieldType"] == "date":
                field["fieldType"] = "date"
            elif field["fieldType"] == "number":
                field["fieldType"] = "number"
            else:
                field["fieldType"] = "input"

        return adapted_schema
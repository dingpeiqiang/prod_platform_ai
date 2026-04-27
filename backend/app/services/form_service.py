from typing import Dict, Any, Optional
import uuid
import logging
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.ontology_service import OntologyService
from app.services.history_service import HistoryService
from app.core.config_loader import config_loader

logger = logging.getLogger("form_service")


def _convert_date_string(date_str: str) -> str:
    """
    将自然语言日期转换为标准日期格式 (YYYY-MM-DD)
    支持：今天、明天、后天、昨天、大前天等
    """
    if not date_str or not isinstance(date_str, str):
        return date_str
    
    today = datetime.now()
    date_str_lower = date_str.strip().lower()
    
    # 直接匹配常见表达
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
    
    # 如果已经是标准格式，直接返回
    try:
        datetime.strptime(date_str, '%Y-%m-%d')
        return date_str
    except ValueError:
        pass
    
    # 其他情况返回原始值
    return date_str


class FormService:
    @classmethod
    def generate_form(
        cls,
        user_input: str,
        form_code: str = None,
        user_id: str = None,
        extracted_fields: Dict[str, Any] = None,
        db: Session = None
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
                    "rules": [],
                    "recommend": [],
                    "defaultValue": None
                }

                if "rules" in field:
                    field_dict["rules"] = [
                        {"rule_type": r["rule_type"], "rule_value": r["rule_value"], "message": r["message"]}
                        for r in field["rules"]
                    ]

                if "options" in field:
                    field_dict["options"] = field["options"]

                recommend_result = HistoryService.get_recommend_values(
                    form_code,
                    field["fieldCode"],
                    user_id,
                    db
                )
                if recommend_result["success"]:
                    field_dict["recommend"] = recommend_result["recommendations"]

                fields.append(field_dict)

        temp_schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", ""),
            "fields": fields
        }

        # ── 字段值合并策略 ────────────────────────────────────────────────
        # 优先级：LLM 传入字段 > FieldExtractionSkill > 历史推荐值
        extracted_values: Dict[str, Any] = {}

        # 1. 先用 FieldExtractionSkill 从 user_input 提取（作为兜底）
        extraction_result = FieldExtractionSkill.extract(user_input, form_code, temp_schema)
        if extraction_result["success"]:
            for ef in extraction_result["fields"]:
                fc = ef.get("fieldCode")
                fv = ef.get("fieldValue", ef.get("defaultValue"))
                if fc and fv is not None:
                    # 对日期字段进行转换
                    if fc in ['start_date', 'end_date', 'date', 'expense_date']:
                        fv = _convert_date_string(fv)
                        logger.debug("[FormService] Skills日期字段 %s 转换: %s -> %s", fc, ef.get("fieldValue"), fv)
                    extracted_values[fc] = fv

        # 2. 用 LLM 传入的字段覆盖（更准确）
        if extracted_fields:
            for fc, fv in extracted_fields.items():
                if fv is not None and fv != "":
                    # 对日期字段进行转换
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
                    field["defaultValue"] = field["recommend"][0]

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

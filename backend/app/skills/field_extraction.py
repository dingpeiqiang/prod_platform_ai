from typing import Dict, Any, List
import re
import logging
from datetime import datetime
from app.core.config_loader import config_loader
from app.services.llm_service import llm_service

logger = logging.getLogger("field_extraction")


class FieldExtractionSkill:

    @classmethod
    def extract(cls, user_input: str, scene_code: str, form_schema: Dict = None) -> Dict[str, Any]:
        if form_schema:
            field_count = len(form_schema.get("fields", []))
            logger.info("[FieldExtraction] LLM提取开始 user_input长度=%d 字段数=%d", len(user_input), field_count)
            logger.debug("[FieldExtraction] user_input=\n%s", user_input[:500])

            llm_result = llm_service.extract_fields(user_input, form_schema)

            logger.info("[FieldExtraction] LLM提取完成 success=%s extractedFields数量=%s",
                        bool(llm_result and 'extractedFields' in llm_result),
                        len(llm_result.get('extractedFields', [])) if llm_result else 0)
            if llm_result and 'extractedFields' in llm_result:
                logger.debug("[FieldExtraction] 提取结果=%s", llm_result['extractedFields'])

            if llm_result and 'extractedFields' in llm_result:
                return {
                    "success": True,
                    "fields": llm_result['extractedFields'],
                    "method": "llm"
                }
        
        fields = []
        ontology = config_loader.get_ontology(scene_code)
        
        if ontology:
            ontology_fields = []
            for entity in ontology.get("entities", []):
                ontology_fields.extend(entity.get("fields", []))
            
            extracted = cls._extract_from_ontology(user_input, ontology_fields)
            
            for field_code, field_value in extracted.items():
                for field in ontology_fields:
                    if field.get("fieldCode") == field_code:
                        fields.append({
                            "fieldCode": field_code,
                            "fieldName": field.get("fieldName", field_code),
                            "fieldType": field.get("fieldType", "input"),
                            "defaultValue": field_value
                        })
                        break
        
        return {
            "success": True,
            "fields": fields,
            "method": "ontology_based"
        }
    
    @classmethod
    def _extract_from_ontology(cls, user_input: str, ontology_fields: List[Dict]) -> Dict[str, Any]:
        extracted = {}
        
        for field in ontology_fields:
            field_code = field.get("fieldCode")
            field_name = field.get("fieldName", "")
            field_type = field.get("fieldType", "input")
            
            if not field_code or not field_name:
                continue
            
            value = cls._extract_field_value(user_input, field_name, field_type)
            if value is not None:
                extracted[field_code] = value
        
        return extracted
    
    @classmethod
    def _extract_field_value(cls, user_input: str, field_name: str, field_type: str) -> Any:
        config = config_loader.get_field_extraction_config()
        separators = config.get('separators', ["是", "为", "：", ":", " "])
        
        for sep in separators:
            pattern = rf"{re.escape(field_name)}{re.escape(sep)}\s*([^\s，,。]+)"
            match = re.search(pattern, user_input)
            if match:
                value = match.group(1).strip()
                if value:
                    return cls._convert_value(value, field_type)
        
        alternative_patterns = [
            rf"{re.escape(field_name)}\s*[是为：:]\s*([^\s，,。]+)",
            rf"{re.escape(field_name)}[:：]\s*([^\s，,。]+)",
        ]
        
        for pattern in alternative_patterns:
            match = re.search(pattern, user_input)
            if match:
                value = match.group(1).strip()
                if value:
                    return cls._convert_value(value, field_type)
        
        return None
    
    @classmethod
    def _convert_value(cls, value: str, field_type: str) -> Any:
        if field_type == "number":
            try:
                cleaned_value = re.sub(r'[元￥$，,]', '', value).strip()
                if "." in cleaned_value:
                    return float(cleaned_value)
                return int(cleaned_value)
            except ValueError:
                return value
        
        elif field_type == "date":
            try:
                date_formats = [
                    "%Y-%m-%d", "%Y/%m/%d", "%Y年%m月%d日",
                    "%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M"
                ]
                for fmt in date_formats:
                    try:
                        dt = datetime.strptime(value, fmt)
                        return dt.strftime("%Y-%m-%d")
                    except ValueError:
                        continue
                return value
            except Exception:
                return value
        
        elif field_type == "select":
            return value
        
        return value
    
    @classmethod
    def extract_smart(cls, user_input: str, form_fields: List[Dict]) -> Dict[str, Any]:
        extracted = {}
        
        for field in form_fields:
            field_code = field.get("fieldCode")
            field_name = field.get("fieldName", "")
            field_type = field.get("fieldType", "input")
            
            value = cls._extract_field_value(user_input, field_name, field_type)
            if value:
                extracted[field_code] = value
        
        return {
            "success": True,
            "fields": [
                {"fieldCode": k, "fieldValue": v}
                for k, v in extracted.items()
            ]
        }

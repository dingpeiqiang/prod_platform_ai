from typing import Dict, Any, List
import logging
from app.core.config_loader import config_loader

logger = logging.getLogger("ontology_service")


class OntologyService:
    
    @classmethod
    def get_form_constraint(cls, form_code: str) -> Dict[str, Any]:
        ontology = config_loader.get_ontology(form_code)
        if ontology:
            logger.debug("[OntologyService] 找到本体约束 form_code=%s", form_code)
            return {
                "success": True,
                "constraints": ontology
            }
        logger.warning("[OntologyService] 未找到本体约束 form_code=%s", form_code)
        return {
            "success": False,
            "constraints": {},
            "message": f"未找到表单代码 {form_code} 的本体约束"
        }
    
    @classmethod
    def get_all_ontologies(cls) -> Dict[str, Any]:
        ontologies = config_loader.get_all_ontologies()
        logger.debug("[OntologyService] 获取所有本体 count=%d", len(ontologies))
        return {
            "success": True,
            "ontologies": [
                {
                    "formCode": ont.get("formCode"),
                    "formName": ont.get("formName"),
                    "description": ont.get("description", "")
                }
                for ont in ontologies.values()
            ]
        }
    
    @classmethod
    def validate_schema(cls, form_code: str, schema: Dict[str, Any]) -> Dict[str, Any]:
        errors = []
        ontology = config_loader.get_ontology(form_code)
        
        if not ontology:
            logger.warning("[OntologyService] validate_schema 本体不存在 form_code=%s", form_code)
            errors.append(f"表单代码 {form_code} 不存在于本体中")
            return {"success": True, "valid": False, "errors": errors}
        
        ontology_fields = []
        for entity in ontology.get("entities", []):
            ontology_fields.extend(entity.get("fields", []))
        
        ontology_field_codes = {f["fieldCode"] for f in ontology_fields}
        schema_field_codes = {f["fieldCode"] for f in schema.get("fields", [])}
        
        for field_code in schema_field_codes - ontology_field_codes:
            errors.append(f"字段 {field_code} 不在本体定义中")
        
        if errors:
            logger.warning("[OntologyService] Schema 校验失败 form_code=%s errors=%s", form_code, errors)
        else:
            logger.debug("[OntologyService] Schema 校验通过 form_code=%s", form_code)
        
        return {
            "success": True,
            "valid": len(errors) == 0,
            "errors": errors
        }

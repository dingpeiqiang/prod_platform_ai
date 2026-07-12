from typing import Dict, Any, List, Optional

from app.core.logger import get_logger
from app.core.config_loader import config_loader

logger = get_logger(__name__)


class OntologyService:
    
    @classmethod
    def get_categories(cls) -> List[Dict[str, str]]:
        return [
            {"code": "general", "name": "通用本体"},
            {"code": "tariff", "name": "资费备案"},
            {"code": "customer", "name": "客户信息"},
            {"code": "business", "name": "业务流程"}
        ]
    
    @classmethod
    def list_ontologies(cls, category: Optional[str] = None, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            ontologies = config_loader.get_all_ontologies()
            
            result = []
            for code, data in ontologies.items():
                ontology_info = {
                    "ontologyCode": code,
                    "ontologyName": data.get("formName", code),
                    "category": data.get("category", "general"),
                    "description": data.get("description", ""),
                    "entities": data.get("entities", []),
                    "isActive": True
                }
                if category and category != "" and ontology_info["category"] != category:
                    continue
                result.append(ontology_info)
            
            return {
                "success": True,
                "data": result
            }
        except Exception as e:
            logger.exception(f"Failed to list ontologies: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def get_ontology(cls, ontology_code: str) -> Dict[str, Any]:
        try:
            ontology = config_loader.get_ontology(ontology_code)
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            
            return {
                "success": True,
                "data": {
                    "ontologyCode": ontology_code,
                    "ontologyName": ontology.get("formName", ontology_code),
                    "category": ontology.get("category", "general"),
                    "description": ontology.get("description", ""),
                    "entities": ontology.get("entities", []),
                    "isActive": True
                }
            }
        except Exception as e:
            logger.exception(f"Failed to get ontology: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def create_ontology(cls, ontology_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        logger.warning("[create_ontology] 本体管理功能已切换为文件数据源，不支持创建本体")
        return {"success": False, "message": "本体管理功能已切换为文件数据源，不支持创建本体"}
    
    @classmethod
    def update_ontology(cls, ontology_code: str, ontology_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        logger.warning("[update_ontology] 本体管理功能已切换为文件数据源，不支持更新本体")
        return {"success": False, "message": "本体管理功能已切换为文件数据源，不支持更新本体"}
    
    @classmethod
    def delete_ontology(cls, ontology_code: str) -> Dict[str, Any]:
        logger.warning("[delete_ontology] 本体管理功能已切换为文件数据源，不支持删除本体")
        return {"success": False, "message": "本体管理功能已切换为文件数据源，不支持删除本体"}
    
    @classmethod
    def toggle_active(cls, ontology_code: str) -> Dict[str, Any]:
        logger.warning("[toggle_active] 本体管理功能已切换为文件数据源，不支持切换本体状态")
        return {"success": False, "message": "本体管理功能已切换为文件数据源，不支持切换本体状态"}
    
    @classmethod
    def get_business_rules(cls, ontology_code: str) -> Dict[str, Any]:
        try:
            ontology = config_loader.get_ontology(ontology_code)
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            
            entities = ontology.get("entities", [])
            
            default_values = {}
            validation_rules = {}
            field_mappings = {}
            business_rules = []
            
            for entity in entities:
                for field in entity.get("fields", []):
                    field_code = field.get("fieldCode")
                    if field_code:
                        if "defaultValue" in field:
                            default_values[field_code] = field["defaultValue"]
                        
                        field_rules = {}
                        if field.get("required"):
                            field_rules["required"] = True
                        if "minLength" in field:
                            field_rules["minLength"] = field["minLength"]
                        if "maxLength" in field:
                            field_rules["maxLength"] = field["maxLength"]
                        if "pattern" in field:
                            field_rules["pattern"] = field["pattern"]
                        if "min" in field:
                            field_rules["min"] = field["min"]
                        if "max" in field:
                            field_rules["max"] = field["max"]
                        if field_rules:
                            validation_rules[field_code] = field_rules
                        
                        if "label" in field:
                            field_mappings[field_code] = field["label"]
            
            business_rules.extend(ontology.get("businessRules", []))
            
            return {
                "success": True,
                "data": {
                    "default_values": default_values,
                    "validation_rules": validation_rules,
                    "field_mappings": field_mappings,
                    "business_rules": business_rules
                }
            }
        except Exception as e:
            logger.exception(f"Failed to get business rules: {e}")
            return {"success": False, "message": str(e)}
    
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
                    "formCode": code,
                    "formName": ont.get("formName", code),
                    "description": ont.get("description", "")
                }
                for code, ont in ontologies.items()
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
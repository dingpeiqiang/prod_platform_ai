from typing import Dict, Any, List, Optional
import logging
from sqlalchemy.orm import Session
from sqlalchemy import desc
from app.models.ontology import Ontology
from app.core.config_loader import config_loader

logger = logging.getLogger("ontology_service")


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
    def list_ontologies(cls, db: Session, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            query = db.query(Ontology)
            if is_active is not None:
                query = query.filter(Ontology.is_active == is_active)
            
            ontologies = query.order_by(desc(Ontology.created_at)).all()
            return {
                "success": True,
                "data": [o.to_dict() for o in ontologies]
            }
        except Exception as e:
            logger.exception(f"Failed to list ontologies: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def get_ontology(cls, db: Session, ontology_code: str) -> Dict[str, Any]:
        try:
            ontology = db.query(Ontology).filter(Ontology.ontology_code == ontology_code).first()
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            return {"success": True, "data": ontology.to_dict()}
        except Exception as e:
            logger.exception(f"Failed to get ontology: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def create_ontology(cls, db: Session, ontology_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            ontology_code = ontology_data.get("ontologyCode")
            if not ontology_code:
                return {"success": False, "message": "本体编码不能为空"}
            
            existing = db.query(Ontology).filter(Ontology.ontology_code == ontology_code).first()
            if existing:
                return {"success": False, "message": f"本体 {ontology_code} 已存在"}
            
            ontology = Ontology(
                ontology_code=ontology_code,
                ontology_name=ontology_data.get("ontologyName", ontology_code),
                form_code=ontology_data.get("formCode"),
                form_name=ontology_data.get("formName"),
                description=ontology_data.get("description"),
                entities=ontology_data.get("entities", [])
            )
            db.add(ontology)
            db.commit()
            db.refresh(ontology)
            
            return {"success": True, "data": ontology.to_dict(), "message": "创建成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create ontology: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def update_ontology(cls, db: Session, ontology_code: str, ontology_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            ontology = db.query(Ontology).filter(Ontology.ontology_code == ontology_code).first()
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            
            if "ontologyName" in ontology_data:
                ontology.ontology_name = ontology_data["ontologyName"]
            if "formCode" in ontology_data:
                ontology.form_code = ontology_data["formCode"]
            if "formName" in ontology_data:
                ontology.form_name = ontology_data["formName"]
            if "description" in ontology_data:
                ontology.description = ontology_data["description"]
            if "entities" in ontology_data:
                ontology.entities = ontology_data["entities"]
            if "isActive" in ontology_data:
                ontology.is_active = ontology_data["isActive"]
            
            ontology.version += 1
            db.commit()
            db.refresh(ontology)
            
            return {"success": True, "data": ontology.to_dict(), "message": "更新成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update ontology: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def delete_ontology(cls, db: Session, ontology_code: str) -> Dict[str, Any]:
        try:
            ontology = db.query(Ontology).filter(Ontology.ontology_code == ontology_code).first()
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            db.delete(ontology)
            db.commit()
            return {"success": True, "message": "删除成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete ontology: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def toggle_active(cls, db: Session, ontology_code: str) -> Dict[str, Any]:
        try:
            ontology = db.query(Ontology).filter(Ontology.ontology_code == ontology_code).first()
            if not ontology:
                return {"success": False, "message": f"本体 {ontology_code} 不存在"}
            ontology.is_active = not ontology.is_active
            db.commit()
            return {"success": True, "data": ontology.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to toggle ontology: {e}")
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

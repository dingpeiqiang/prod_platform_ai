import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc
from app.models.form import Form

logger = logging.getLogger("form_service")


class FormService:
    
    @classmethod
    def get_categories(cls) -> List[Dict[str, str]]:
        return [
            {"code": "general", "name": "通用表单"},
            {"code": "tariff", "name": "资费备案"},
            {"code": "survey", "name": "调查问卷"},
            {"code": "customer", "name": "客户信息"},
            {"code": "project", "name": "项目管理"}
        ]
    
    @classmethod
    def list_forms(cls, db: Session, category: Optional[str] = None, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            query = db.query(Form)
            if category:
                query = query.filter(Form.category == category)
            if is_active is not None:
                query = query.filter(Form.is_active == is_active)
            
            forms = query.order_by(desc(Form.created_at)).all()
            return {
                "success": True,
                "data": [f.to_dict() for f in forms]
            }
        except Exception as e:
            logger.exception(f"Failed to list forms: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def get_form(cls, db: Session, form_code: str) -> Dict[str, Any]:
        try:
            form = db.query(Form).filter(Form.form_code == form_code).first()
            if not form:
                return {"success": False, "message": f"表单 {form_code} 不存在"}
            return {"success": True, "data": form.to_dict()}
        except Exception as e:
            logger.exception(f"Failed to get form: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def create_form(cls, db: Session, form_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            form_code = form_data.get("formCode")
            if not form_code:
                return {"success": False, "message": "表单编码不能为空"}
            
            existing = db.query(Form).filter(Form.form_code == form_code).first()
            if existing:
                return {"success": False, "message": f"表单 {form_code} 已存在"}
            
            form = Form(
                form_code=form_code,
                form_name=form_data.get("formName", form_code),
                description=form_data.get("description"),
                category=form_data.get("category", "general"),
                entities=form_data.get("entities", []),
                layout=form_data.get("layout", {}),
                validation_rules=form_data.get("validationRules", []),
                ontology_code=form_data.get("ontologyCode")
            )
            db.add(form)
            db.commit()
            db.refresh(form)
            
            return {"success": True, "data": form.to_dict(), "message": "创建成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create form: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def update_form(cls, db: Session, form_code: str, form_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            form = db.query(Form).filter(Form.form_code == form_code).first()
            if not form:
                return {"success": False, "message": f"表单 {form_code} 不存在"}
            
            if "formName" in form_data:
                form.form_name = form_data["formName"]
            if "description" in form_data:
                form.description = form_data["description"]
            if "category" in form_data:
                form.category = form_data["category"]
            if "entities" in form_data:
                form.entities = form_data["entities"]
            if "layout" in form_data:
                form.layout = form_data["layout"]
            if "validationRules" in form_data:
                form.validation_rules = form_data["validationRules"]
            if "ontologyCode" in form_data:
                form.ontology_code = form_data["ontologyCode"]
            if "isActive" in form_data:
                form.is_active = form_data["isActive"]
            
            form.version += 1
            db.commit()
            db.refresh(form)
            
            return {"success": True, "data": form.to_dict(), "message": "更新成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update form: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def delete_form(cls, db: Session, form_code: str) -> Dict[str, Any]:
        try:
            form = db.query(Form).filter(Form.form_code == form_code).first()
            if not form:
                return {"success": False, "message": f"表单 {form_code} 不存在"}
            db.delete(form)
            db.commit()
            return {"success": True, "message": "删除成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete form: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def toggle_active(cls, db: Session, form_code: str) -> Dict[str, Any]:
        try:
            form = db.query(Form).filter(Form.form_code == form_code).first()
            if not form:
                return {"success": False, "message": f"表单 {form_code} 不存在"}
            form.is_active = not form.is_active
            db.commit()
            return {"success": True, "data": form.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to toggle form: {e}")
            return {"success": False, "message": str(e)}

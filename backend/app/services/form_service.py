import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc
from app.models.form import Form
import uuid
import json
from datetime import datetime

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
    
    @classmethod
    def generate_form(cls, user_input: str, form_code: str, user_id: Optional[str] = None,
                     extracted_fields: Optional[Dict[str, Any]] = None,
                     field_recommendations: Optional[Dict[str, Any]] = None,
                     db: Session = None) -> Dict[str, Any]:
        """
        生成表单（兼容旧接口）
        
        Args:
            user_input: 用户输入
            form_code: 表单编码
            user_id: 用户ID
            extracted_fields: 已提取的字段
            field_recommendations: 字段推荐
            db: 数据库会话
            
        Returns:
            包含 success, formSchema, formId 的字典
        """
        try:
            from app.services.ontology_service import OntologyService
            
            # 获取本体定义
            ontology_result = OntologyService.get_form_constraint(form_code)
            if not ontology_result["success"]:
                return {
                    "success": False,
                    "message": f"未找到表单 {form_code} 的本体定义"
                }
            
            constraints = ontology_result.get("constraints", {})
            entities = constraints.get("entities", [])
            
            # 构建表单 schema
            fields = []
            for entity in entities:
                for field_def in entity.get("fields", []):
                    field_info = {
                        "fieldCode": field_def.get("fieldCode"),
                        "fieldName": field_def.get("fieldName"),
                        "fieldType": field_def.get("fieldType", "input"),
                        "required": field_def.get("required", False),
                        "disabled": False,
                        "hidden": False,
                        "rules": [],
                        "recommend": [],
                        "defaultValue": None,
                        "options": [],
                        "enumConfig": field_def.get("enumConfig")
                    }
                    
                    # 添加默认值
                    if extracted_fields and field_info["fieldCode"] in extracted_fields:
                        field_info["defaultValue"] = extracted_fields[field_info["fieldCode"]]
                    
                    # 添加推荐
                    if field_recommendations and field_info["fieldCode"] in field_recommendations:
                        rec_data = field_recommendations[field_info["fieldCode"]]
                        if isinstance(rec_data, dict) and "items" in rec_data:
                            field_info["recommend"] = rec_data["items"]
                        elif isinstance(rec_data, list):
                            field_info["recommend"] = rec_data
                    
                    fields.append(field_info)
            
            form_schema = {
                "formCode": form_code,
                "formName": constraints.get("formName", form_code),
                "version": 1,
                "globalControl": {},
                "fields": fields
            }
            
            # 生成表单 ID
            form_id = f"form_{uuid.uuid4().hex[:12]}"
            
            # 注意：不再保存到数据库，FormTemplate 已废弃
            # 表单 Schema 由本体约束（ontology）驱动，通过 config_loader 加载
            # if db:
            #     try:
            #         existing_form = db.query(Form).filter(
            #             Form.form_code == form_code,
            #             Form.is_active == True
            #         ).first()
            #         
            #         if existing_form:
            #             existing_form.version += 1
            #             db.commit()
            #             logger.info(f"[FormService] 更新表单版本 form_code={form_code} version={existing_form.version}")
            #         else:
            #             new_form = Form(
            #                 form_code=form_code,
            #                 form_name=constraints.get("formName", form_code),
            #                 description=constraints.get("description"),
            #                 entities=constraints.get("entities", []),
            #                 ontology_code=form_code,
            #                 is_active=True,
            #                 version=1
            #             )
            #             db.add(new_form)
            #             db.commit()
            #             logger.info(f"[FormService] 创建表单记录 form_code={form_code}")
            #     except Exception as db_err:
            #         logger.warning(f"[FormService] 保存表单记录失败: {db_err}")
            
            return {
                "success": True,
                "formSchema": form_schema,
                "formId": form_id,
                "message": "表单生成成功"
            }
            
        except Exception as e:
            logger.exception(f"[FormService] 生成表单失败: {e}")
            return {
                "success": False,
                "message": f"生成表单时发生错误: {str(e)}"
            }
    
    @classmethod
    def adapt_for_chat_window(cls, form_schema: Dict[str, Any]) -> Dict[str, Any]:
        """
        将表单 schema 适配为聊天窗口所需的格式
        
        Args:
            form_schema: 原始表单 schema
            
        Returns:
            适配后的表单 schema
        """
        # 目前直接返回原 schema，后续可根据需要添加适配逻辑
        return form_schema

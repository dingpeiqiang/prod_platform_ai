"""
表单生成节点

输入参数：
- ontologyCode: 本体编码（必填）
- defaultValues: 默认值（可选）
- fieldMappings: 字段映射（可选）
- validationRules: 校验规则（可选）
- toolName: 工具名称（可选）
- enableRecommendation: 是否启用推荐（可选，默认 True）
- autoSubmit: 是否自动提交（可选，默认 False）

输出结果：
- formCode: 表单编码
- form_schema: 表单结构
- extractedFields: 提取的字段数据
- action: 动作类型（ask_user 或 generate_form）
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import json


@register_node
class GenerateFormNode(WorkflowNode):
    """表单生成节点"""
    
    name = "workflow.generate_form"
    description = "根据本体生成表单"
    inputs = {
        "ontologyCode": {"type": "str", "required": True, "description": "本体编码"},
        "defaultValues": {"type": "dict", "required": False, "description": "默认值", "default": {}},
        "fieldMappings": {"type": "dict", "required": False, "description": "字段映射", "default": {}},
        "validationRules": {"type": "dict", "required": False, "description": "校验规则", "default": {}},
        "toolName": {"type": "str", "required": False, "description": "工具名称"},
        "enableRecommendation": {"type": "bool", "required": False, "description": "启用推荐", "default": True},
        "autoSubmit": {"type": "bool", "required": False, "description": "自动提交", "default": False}
    }
    outputs = {
        "formCode": {"type": "str", "description": "表单编码"},
        "form_schema": {"type": "dict", "description": "表单结构"},
        "extractedFields": {"type": "dict", "description": "提取的字段数据"},
        "action": {"type": "str", "description": "动作类型"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        ontology_code = kwargs.get("ontologyCode", "")
        default_values = kwargs.get("defaultValues", {})
        field_mappings = kwargs.get("fieldMappings", {})
        validation_rules = kwargs.get("validationRules", {})
        tool_name = kwargs.get("toolName", "") or kwargs.get("toolType", "")
        enable_recommendation = kwargs.get("enableRecommendation", True)
        auto_submit = kwargs.get("autoSubmit", False)
        
        # 记录输入信息
        self._log_input(
            ontologyCode=ontology_code,
            defaultValues=json.dumps(default_values, ensure_ascii=False),
            fieldMappings=json.dumps(field_mappings, ensure_ascii=False),
            toolName=tool_name,
            autoSubmit=auto_submit
        )
        
        # 构建处理逻辑描述
        processing = f"根据本体 '{ontology_code}' 生成表单，启用推荐: {enable_recommendation}，自动提交: {auto_submit}"
        self._log_processing(processing)
        
        if not ontology_code:
            error_msg = "ontologyCode is required for form generation"
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"ontologyCode": ontology_code, "defaultValues": default_values},
                "processing": processing,
                "output": {"error": error_msg}
            }
        
        try:
            from app.config.config_loader import config_loader
            
            ontology = config_loader.get_ontology(ontology_code)
            if not ontology:
                error_msg = f"Ontology not found: {ontology_code}"
                self._log_output(success=False, error=error_msg)
                
                return {
                    "success": False,
                    "error": error_msg,
                    "input": {"ontologyCode": ontology_code},
                    "processing": processing,
                    "output": {"error": error_msg}
                }
            
            form_schema = self._build_form_schema(ontology)
            extracted_fields = context.inputs
            
            tool_result = None
            if tool_name:
                tool_result = context.step_results.get("call_tool", {})
                if not tool_result:
                    tool_result = context.outputs.get("tool_result", {})
            
            user_input = context.inputs.get("user_input", "")
            user_id = context.inputs.get("user_id", "default")
            
            form_data = self._initialize_form_data(
                ontology=ontology,
                context=context,
                default_values=default_values,
                extracted_fields=extracted_fields,
                tool_result=tool_result,
                field_mappings=field_mappings,
                ontology_code=ontology_code,
                user_input=user_input,
                user_id=user_id,
                enable_recommendation=enable_recommendation
            )
            
            result = {
                "success": True,
                "action": "generate_form",
                "formCode": ontology_code,
                "form_schema": form_schema,
                "extractedFields": form_data,
                "ontology_code": ontology_code,
                "validation_rules": validation_rules,
                "field_mappings": field_mappings,
                "message": tool_result.get("success") if tool_result else "使用默认表单",
                "input": {
                    "ontologyCode": ontology_code,
                    "defaultValues": default_values,
                    "fieldMappings": field_mappings,
                    "toolName": tool_name,
                    "autoSubmit": auto_submit
                },
                "processing": processing,
                "output": {
                    "formCode": ontology_code,
                    "field_count": len(form_data),
                    "message": tool_result.get("success") if tool_result else "使用默认表单"
                }
            }
            
            if auto_submit:
                result["action"] = "generate_form"
                result["auto_submitted"] = True
                
                # 记录输出信息
                self._log_output(success=True, action="generate_form (自动提交)", formCode=ontology_code, field_count=len(form_data))
                
                return result
            
            result["action"] = "ask_user"
            result["waiting_for_input"] = True
            result["message"] = "请填写并提交表单"
            
            # 记录输出信息
            self._log_output(success=True, action="ask_user", formCode=ontology_code, field_count=len(form_data))
            
            return result
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"ontologyCode": ontology_code, "defaultValues": default_values},
                "processing": processing,
                "output": {"error": error_msg}
            }
    
    def _build_form_schema(self, ontology: Dict[str, Any]) -> Dict[str, Any]:
        """构建表单结构"""
        form_schema = {
            "ontologyCode": ontology.get("ontologyCode", ""),
            "ontologyName": ontology.get("ontologyName", ""),
            "description": ontology.get("description", ""),
            "entities": [],
            "fields": []
        }
        
        for entity in ontology.get("entities", []):
            entity_info = {
                "entityCode": entity.get("entityCode", ""),
                "entityName": entity.get("entityName", ""),
                "fields": []
            }
            
            for field in entity.get("fields", []):
                field_def = {
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType", "string"),
                    "required": field.get("required", False),
                    "default": field.get("default", ""),
                    "description": field.get("description", ""),
                    "options": field.get("options", []),
                    "validation": field.get("validation", {}),
                    "placeholder": field.get("placeholder", "")
                }
                
                entity_info["fields"].append(field_def)
                form_schema["fields"].append(field_def)
            
            form_schema["entities"].append(entity_info)
        
        return form_schema
    
    def _initialize_form_data(self, ontology: Dict[str, Any], context: Any, default_values: Dict[str, Any],
                              extracted_fields: Dict[str, Any], tool_result: Dict[str, Any],
                              field_mappings: Dict[str, Any], ontology_code: str, user_input: str,
                              user_id: str, enable_recommendation: bool) -> Dict[str, Any]:
        """初始化表单数据"""
        form_data = {}
        rec_engine = None
        
        if enable_recommendation:
            try:
                from app.services.recommendation_engine import get_recommendation_engine
                rec_engine = get_recommendation_engine()
            except Exception as e:
                pass
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                if not field_code:
                    continue
                
                value = None
                
                if tool_result and tool_result.get("success"):
                    result_data = tool_result.get("result", tool_result)
                    if field_mappings and field_code in field_mappings:
                        source_fields = field_mappings[field_code]
                        if isinstance(source_fields, str):
                            source_fields = [source_fields]
                        for source_field in source_fields:
                            if source_field in result_data and result_data[source_field]:
                                value = result_data[source_field]
                                break
                    if not value and field_code in result_data:
                        value = result_data[field_code]
                
                if not value and extracted_fields:
                    value = extracted_fields.get(field_code)
                
                if not value:
                    value = context.inputs.get(field_code)
                
                if not value:
                    value = context.outputs.get(field_code)
                
                if not value and rec_engine and ontology_code:
                    try:
                        rec_result = rec_engine.recommend(
                            form_code=ontology_code,
                            field_code=field_code,
                            user_input=user_input,
                            user_id=user_id
                        )
                        if rec_result and rec_result.recommendations and len(rec_result.recommendations) > 0:
                            value = rec_result.recommendations[0].value
                    except Exception as e:
                        pass
                
                if not value:
                    value = default_values.get(field_code)
                
                if not value:
                    value = field.get("default", "")
                
                form_data[field_code] = value
        
        return form_data
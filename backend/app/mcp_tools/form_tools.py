# 表单服务 MCP 工具封装
# 将 FormService 封装为标准化 MCP 工具

from typing import Dict, Any
from ..services.form_service import FormService
from ..skills.scene_recognition import SceneRecognitionSkill
from ..skills.field_extraction import FieldExtractionSkill
from .tool_hub import mcptool


# ============================================================
# 表单生成工具
# ============================================================

@mcptool(
    name="form_generate",
    description="根据用户的自然语言需求生成表单。输入用户的描述（如'帮我填一个请假申请'），自动识别表单类型并生成对应表单。",
    category="form"
)
def generate_form(user_input: str, form_code: str = None, extracted_fields: Dict = None) -> Dict[str, Any]:
    """
    生成表单
    
    Args:
        user_input: 用户的自然语言输入
        form_code: 可选的表单类型代码，不提供则自动识别
        extracted_fields: 可选的预提取字段
        
    Returns:
        生成结果，包含表单 schema 和实例 ID
    """
    result = FormService.generate_form(
        user_input=user_input,
        form_code=form_code,
        extracted_fields=extracted_fields
    )
    return result


# ============================================================
# 场景识别工具
# ============================================================

@mcptool(
    name="scene_recognize",
    description="识别用户输入的场景类型（如请假、报销、订单等）。根据用户描述判断用户想要填写哪种表单。",
    category="form"
)
def recognize_scene(user_input: str) -> Dict[str, Any]:
    """
    识别用户场景
    
    Args:
        user_input: 用户的自然语言输入
        
    Returns:
        识别结果，包含场景代码、置信度等
    """
    result = SceneRecognitionSkill.recognize(user_input)
    return result


# ============================================================
# 字段提取工具
# ============================================================

@mcptool(
    name="field_extract",
    description="从用户输入中提取表单字段值。如从'帮我填一个请假申请，我叫张三，请假3天'中提取姓名和天数。",
    category="form"
)
def extract_fields(
    user_input: str,
    form_code: str,
    schema: Dict = None
) -> Dict[str, Any]:
    """
    提取表单字段
    
    Args:
        user_input: 用户的自然语言输入
        form_code: 表单类型代码
        schema: 可选的表单 schema
        
    Returns:
        提取的字段结果
    """
    if schema is None:
        # 如果没有提供 schema，从 ontology 获取
        from ..services.ontology_service import OntologyService
        ontology_result = OntologyService.get_form_constraint(form_code)
        if ontology_result.get("success"):
            schema = ontology_result.get("constraints", {})
    
    result = FieldExtractionSkill.extract(
        user_input=user_input,
        form_code=form_code,
        schema=schema
    )
    return result


# ============================================================
# 表单校验工具
# ============================================================

@mcptool(
    name="form_validate",
    description="校验表单填写的数据是否符合规则。包括必填检查、格式校验、范围校验等。",
    category="form"
)
def validate_form(form_code: str, form_data: Dict) -> Dict[str, Any]:
    """
    校验表单数据

    Args:
        form_code: 表单类型代码
        form_data: 表单填写的数据

    Returns:
        校验结果
    """
    from ..services.validation_service import validation_engine
    from ..core.config_loader import config_loader

    # 从本体获取字段定义
    ontology = config_loader.get_ontology(form_code)
    if not ontology:
        return {
            "success": False,
            "message": f"未找到表单 {form_code} 的定义"
        }

    # 提取字段定义列表
    fields = []
    for entity in ontology.get("entities", []):
        for field in entity.get("fields", []):
            field_dict = {
                "fieldCode": field.get("fieldCode", ""),
                "fieldName": field.get("fieldName", ""),
                "required": field.get("required", False),
                "rules": field.get("rules", [])
            }
            # 提取 ruleDescription 作为规则描述
            if field.get("ruleDescription"):
                field_dict["ruleDescription"] = field.get("ruleDescription")
            fields.append(field_dict)

    # 执行校验
    result = validation_engine.validate_form(form_data, fields)
    return {
        "success": result.valid,
        "valid": result.valid,
        "errors": result.errors
    }


# ============================================================
# 表单提交工具
# ============================================================

@mcptool(
    name="form_submit",
    description="提交用户填写的表单数据到系统。",
    category="form"
)
def submit_form(form_instance_id: str, form_data: Dict) -> Dict[str, Any]:
    """
    提交表单
    
    Args:
        form_instance_id: 表单实例 ID
        form_data: 表单数据
        
    Returns:
        提交结果
    """
    from ..services.form_service import FormService
    
    result = FormService.submit_form(form_instance_id, form_data)
    return result


# ============================================================
# 可用表单列表工具
# ============================================================

@mcptool(
    name="form_list_templates",
    description="获取系统中所有可用的表单模板列表。返回表单类型代码和名称。",
    category="form"
)
def list_form_templates() -> Dict[str, Any]:
    """
    获取表单模板列表
    
    Returns:
        可用表单列表
    """
    from ..services.ontology_service import OntologyService
    
    result = OntologyService.get_all_ontologies()
    return {
        "success": True,
        "templates": [
            {"form_code": code, "form_name": info.get("formName", code)}
            for code, info in result.items()
        ]
    }

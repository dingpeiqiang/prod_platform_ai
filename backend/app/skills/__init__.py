from .tool_registry import ToolRegistry
from .scene_recognition import SceneRecognitionSkill
from .field_extraction import FieldExtractionSkill
from app.core.config_loader import config_loader


def recognize_scene(user_input: str) -> str:
    result = SceneRecognitionSkill.recognize(user_input)
    return result["sceneCode"]


def extract_fields(user_input: str, form_code: str) -> str:
    ontologies = config_loader.get_all_ontologies()
    ontology = ontologies.get(form_code, {})
    
    temp_schema = {
        "formCode": form_code,
        "formName": ontology.get("formName", ""),
        "fields": []
    }
    
    extraction_result = FieldExtractionSkill.extract(user_input, form_code, temp_schema)
    
    extracted_fields = {}
    if extraction_result["success"]:
        for field in extraction_result["fields"]:
            extracted_fields[field.get("fieldCode")] = field.get("defaultValue")
    
    import json
    return json.dumps(extracted_fields, ensure_ascii=False)


def get_available_forms() -> str:
    ontologies = config_loader.get_all_ontologies()
    
    forms_info = []
    for form_code, ontology in ontologies.items():
        forms_info.append({
            "form_code": form_code,
            "form_name": ontology.get("formName", form_code),
            "description": ontology.get("description", "")
        })
    
    import json
    return json.dumps(forms_info, ensure_ascii=False)


def initialize_tools():
    # 场景识别已经在 scene_recognition.py 中注册
    # 这里可以注册其他工具，或者保持为空让 Skills 自己管理注册
    pass

initialize_tools()

from typing import Dict, Any
from app.core.config_loader import config_loader
from app.services.llm_service import llm_service
from app.skills.tool_registry import ToolRegistry


class SceneRecognitionSkill:
    
    @classmethod
    def _recognize_impl(cls, user_input: str) -> Dict[str, Any]:
        ontologies = config_loader.get_all_ontologies()
        form_types = list(ontologies.keys())
        
        llm_result = llm_service.recognize_intent(user_input, form_types)
        if llm_result and llm_result.get('sceneCode') in form_types:
            scene_code = llm_result['sceneCode']
            ontology = ontologies.get(scene_code, {})
            return {
                "success": True,
                "sceneCode": scene_code,
                "sceneName": ontology.get('formName', scene_code),
                "confidence": llm_result.get('confidence', 0.9),
                "method": "llm"
            }
        
        scene_mappings = config_loader.get_scene_mappings()
        
        for mapping in sorted(scene_mappings, key=lambda x: x.get('priority', 0), reverse=True):
            for keyword in mapping.get('keywords', []):
                if keyword in user_input:
                    return {
                        "success": True,
                        "sceneCode": mapping['sceneCode'],
                        "sceneName": cls._get_scene_name(mapping['sceneCode'], ontologies),
                        "confidence": 0.9,
                        "method": "keyword"
                    }
        
        config = config_loader.get_scene_recognition_config()
        default_scene = config.get('defaultScene', 'generic')
        
        return {
            "success": True,
            "sceneCode": default_scene,
            "sceneName": "通用表单" if default_scene == "generic" else default_scene,
            "confidence": 0.5,
            "method": "default"
        }
    
    @classmethod
    def recognize(cls, user_input: str) -> Dict[str, Any]:
        return cls._recognize_impl(user_input)

    @classmethod
    def _get_scene_name(cls, scene_code: str, ontologies: Dict) -> str:
        ontology = ontologies.get(scene_code, {})
        return ontology.get('formName', scene_code)

# 注册为工具
ToolRegistry.register(
    name="recognize_scene",
    description="根据用户输入识别业务场景（如请假、报销、订单等）",
    func=SceneRecognitionSkill._recognize_impl
)

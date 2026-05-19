import logging
import re
from typing import Dict, Any, Optional, List, Callable

from .data_source import BaseDataSource, DataSourceType, DataSourceFactory

logger = logging.getLogger("database_data_source")


class DatabaseDataSource(BaseDataSource):
    
    def __init__(self, session_factory: Callable):
        self._session_factory = session_factory
        self._cache: Dict[str, Any] = {}
    
    def _get_db_session(self):
        if not self._session_factory:
            raise RuntimeError("[DatabaseDataSource] 数据库会话工厂未设置")
        return self._session_factory()
    
    def load_ontologies(self) -> Dict[str, Any]:
        from app.models.ontology import Ontology
        
        db = self._get_db_session()
        try:
            ontologies = {}
            db_ontologies = db.query(Ontology).filter(Ontology.is_active == True).all()
            
            for o in db_ontologies:
                ontologies[o.ontology_code] = o.to_ontology_format()
            
            self._cache['ontologies'] = ontologies
            logger.info("[DatabaseDataSource] 从数据库加载本体 count=%d", len(ontologies))
            return ontologies
        finally:
            db.close()
    
    def load_scenes(self) -> List[Dict[str, Any]]:
        from app.models.scene import Scene
        from app.models.prompt import Prompt
        
        db = self._get_db_session()
        try:
            scenes = db.query(Scene).filter(Scene.is_active == True).all()
            result = []
            
            for scene in scenes:
                scene_dict = {
                    'sceneCode': scene.scene_code,
                    'sceneName': scene.scene_name,
                    'description': scene.description,
                    'keywords': scene.keywords or [],
                    'priority': scene.priority,
                    'isActive': scene.is_active,
                    'promptCode': scene.prompt_code,
                    'actionPrompt': scene.prompt_code
                }
                
                if scene.prompt_code:
                    prompt = db.query(Prompt).filter(
                        Prompt.code == scene.prompt_code,
                        Prompt.is_active == True
                    ).first()
                    
                    if prompt and prompt.content:
                        match = re.search(r'formCode["\']?\s*[:=]\s*["\']?([a-zA-Z0-9_]+)', prompt.content)
                        if match:
                            scene_dict['formCode'] = match.group(1)
                
                result.append(scene_dict)
            
            self._cache['scenes'] = result
            logger.info("[DatabaseDataSource] 从数据库加载场景 count=%d", len(result))
            return result
        finally:
            db.close()
    
    def load_prompts(self) -> Dict[str, str]:
        from app.models.prompt import Prompt
        
        db = self._get_db_session()
        try:
            prompts = {}
            db_prompts = db.query(Prompt).filter(Prompt.is_active == True).all()
            
            for prompt in db_prompts:
                prompts[prompt.code] = prompt.content
            
            self._cache['prompts'] = prompts
            logger.info("[DatabaseDataSource] 从数据库加载提示词 count=%d", len(prompts))
            return prompts
        finally:
            db.close()
    
    def load_recommendations(self) -> Dict[str, Any]:
        logger.info("[DatabaseDataSource] 推荐配置暂不支持从数据库加载，使用空配置")
        self._cache['recommendations'] = {}
        return {}
    
    def get_ontology(self, form_code: str) -> Optional[Dict[str, Any]]:
        ontologies = self._cache.get('ontologies', {})
        
        if form_code in ontologies:
            return ontologies[form_code]
        
        from app.models.ontology import Ontology
        
        db = self._get_db_session()
        try:
            ontology = db.query(Ontology).filter(
                Ontology.ontology_code == form_code,
                Ontology.is_active == True
            ).first()
            
            if ontology:
                result = ontology.to_ontology_format()
                ontologies[form_code] = result
                return result
            return None
        finally:
            db.close()
    
    def get_scene(self, scene_code: str) -> Optional[Dict[str, Any]]:
        scenes = self._cache.get('scenes', [])
        for scene in scenes:
            if scene.get('sceneCode') == scene_code:
                return scene
        
        from app.models.scene import Scene
        from app.models.prompt import Prompt
        
        db = self._get_db_session()
        try:
            scene = db.query(Scene).filter(
                Scene.scene_code == scene_code,
                Scene.is_active == True
            ).first()
            
            if not scene:
                return None
            
            scene_dict = {
                'sceneCode': scene.scene_code,
                'sceneName': scene.scene_name,
                'description': scene.description,
                'keywords': scene.keywords or [],
                'priority': scene.priority,
                'isActive': scene.is_active,
                'promptCode': scene.prompt_code,
                'actionPrompt': scene.prompt_code
            }
            
            if scene.prompt_code:
                prompt = db.query(Prompt).filter(
                    Prompt.code == scene.prompt_code,
                    Prompt.is_active == True
                ).first()
                
                if prompt and prompt.content:
                    match = re.search(r'formCode["\']?\s*[:=]\s*["\']?([a-zA-Z0-9_]+)', prompt.content)
                    if match:
                        scene_dict['formCode'] = match.group(1)
            
            return scene_dict
        finally:
            db.close()
    
    def get_prompt(self, prompt_name: str) -> Optional[str]:
        prompts = self._cache.get('prompts', {})
        
        if prompt_name in prompts:
            return prompts[prompt_name]
        
        from app.models.prompt import Prompt
        
        db = self._get_db_session()
        try:
            prompt = db.query(Prompt).filter(
                Prompt.code == prompt_name,
                Prompt.is_active == True
            ).first()
            
            if prompt:
                result = prompt.content
                prompts[prompt_name] = result
                return result
            return None
        finally:
            db.close()
    
    def get_recommendation(self, form_code: str, field_code: str) -> List[str]:
        return []
    
    def reload(self):
        self._load_ontologies()
        self._load_scenes()
        self._load_prompts()


DataSourceFactory.register(DataSourceType.DATABASE, DatabaseDataSource)
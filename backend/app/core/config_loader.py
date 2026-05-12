import json
import logging
import os
from pathlib import Path
from typing import Dict, Any, List, Optional
import threading
import time

logger = logging.getLogger("config_loader")



class ConfigLoader:
    _instance = None
    _lock = threading.Lock()
    
    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._initialized = False
        return cls._instance
    
    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        
        self.base_path = Path(__file__).parent.parent.parent / "config"
        self._config_cache: Dict[str, Any] = {}
        self._last_modified: Dict[str, float] = {}
        self._db_session_factory = None
        self._ontology_source = "database"
        self._load_all_configs()
    
    def set_db_session_factory(self, session_factory):
        self._db_session_factory = session_factory
    
    def _load_all_configs(self):
        self._load_app_config()
        self._load_ontologies()
        self._load_recommendations()
        self._load_prompts()
    
    def _load_system_config(self):
        pass
    
    def _load_json(self, path: Path) -> Optional[Dict]:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            logger.debug("[ConfigLoader] 加载 JSON: %s", path.name)
            return data
        except Exception as e:
            logger.error("[ConfigLoader] 加载失败 %s: %s", path, e)
            return None
    
    def _load_text(self, path: Path) -> Optional[str]:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = f.read()
            logger.debug("[ConfigLoader] 加载 TXT: %s (%d 字符)", path.name, len(data))
            return data
        except Exception as e:
            logger.error("[ConfigLoader] 加载失败 %s: %s", path, e)
            return None
    
    def _load_app_config(self):
        path = self.base_path / "app_config.json"
        if path.exists():
            app_config = self._load_json(path)
            self._config_cache['app_config'] = app_config
            self._last_modified['app_config'] = path.stat().st_mtime
            
            if app_config:
                system_config = {}
                for key in ['recommendation', 'smartRecommend', 'sceneRecognition', 'fieldExtraction']:
                    if key in app_config:
                        system_config[key] = app_config[key]
                self._config_cache['system_config'] = system_config
    
    def _load_ontologies(self):
        if self._ontology_source == "database":
            self._load_ontologies_from_db()
        elif self._ontology_source == "hybrid":
            self._load_ontologies_hybrid()
        else:
            self._load_ontologies_from_file()
    
    def _load_ontologies_from_file(self):
        ontologies = {}
        ontologies_path = self.base_path / "ontologies"
        if ontologies_path.exists():
            for file in ontologies_path.glob("*.json"):
                data = self._load_json(file)
                if data and 'formCode' in data:
                    ontologies[data['formCode']] = data
        self._config_cache['ontologies'] = ontologies
        logger.info("[ConfigLoader] 从文件系统加载本体 count=%d", len(ontologies))
    
    def _load_ontologies_from_db(self):
        if not self._db_session_factory:
            logger.warning("[ConfigLoader] 数据库会话工厂未设置，回退到文件系统")
            self._load_ontologies_from_file()
            return
        
        try:
            from app.models.ontology import Ontology
            
            db = self._db_session_factory()
            try:
                ontologies = {}
                db_ontologies = db.query(Ontology).filter(Ontology.is_active == True).all()
                
                for o in db_ontologies:
                    ontologies[o.ontology_code] = o.to_ontology_format()
                
                self._config_cache['ontologies'] = ontologies
                logger.info("[ConfigLoader] 从数据库加载本体 count=%d", len(ontologies))
            finally:
                db.close()
        except Exception as e:
            logger.error("[ConfigLoader] 数据库加载失败，回退到文件系统: %s", e)
            self._load_ontologies_from_file()
    
    def _load_ontologies_hybrid(self):
        self._load_ontologies_from_db()
        db_ontologies = self._config_cache.get('ontologies', {})
        
        file_path = self.base_path / "ontologies"
        if file_path.exists():
            for file in file_path.glob("*.json"):
                data = self._load_json(file)
                if data and 'formCode' in data:
                    code = data['formCode']
                    if code not in db_ontologies:
                        db_ontologies[code] = data
        
        self._config_cache['ontologies'] = db_ontologies
        logger.info("[ConfigLoader] 混合模式加载本体 count=%d", len(db_ontologies))
    
    def _load_recommendations(self):
        path = self.base_path / "templates" / "recommendations.json"
        if path.exists():
            data = self._load_json(path)
            if data:
                self._config_cache['recommendations'] = data.get('recommendations', {})
                self._last_modified['recommendations'] = path.stat().st_mtime
    
    def _load_prompts(self):
        prompts = {}
        prompts_path = self.base_path / "prompts"
        if prompts_path.exists():
            for file in prompts_path.glob("*.txt"):
                prompt_name = file.stem
                prompts[prompt_name] = self._load_text(file)
        
        # 加载场景提示词
        scene_prompts = {}
        scene_prompts_path = self.base_path / "prompts" / "scenes"
        if scene_prompts_path.exists():
            for file in scene_prompts_path.glob("*.txt"):
                prompt_name = file.stem
                scene_prompts[prompt_name] = self._load_text(file)
        
        self._config_cache['prompts'] = prompts
        self._config_cache['scene_prompts'] = scene_prompts
    
    def get_app_config(self) -> Dict[str, Any]:
        return self._config_cache.get('app_config', {})
    
    def get_scene_mappings(self) -> List[Dict]:
        return []
    
    def get_scene_by_code(self, scene_code: str) -> Optional[Dict]:
        return None
    
    def get_all_scenes(self) -> List[Dict]:
        return []
    
    def get_scene_prompt(self, scene_code: str) -> Optional[str]:
        """获取场景提示词"""
        scene = self.get_scene_by_code(scene_code)
        if scene:
            prompt_file = scene.get('actionPrompt')
            if prompt_file:
                scene_prompts = self._config_cache.get('scene_prompts', {})
                # 移除扩展名查找
                from pathlib import Path
                prompt_name = Path(prompt_file).stem
                return scene_prompts.get(prompt_name)
        return None
    
    def get_ontology(self, form_code: str) -> Optional[Dict]:
        return self._config_cache.get('ontologies', {}).get(form_code)
    
    def get_all_ontologies(self) -> Dict[str, Dict]:
        return self._config_cache.get('ontologies', {})
    
    def get_recommendations(self, form_code: str, field_code: str) -> List[str]:
        recommendations = self._config_cache.get('recommendations', {})
        form_recommendations = recommendations.get(form_code, {})
        return form_recommendations.get(field_code, [])
    
    def get_prompt(self, prompt_name: str) -> Optional[str]:
        return self._config_cache.get('prompts', {}).get(prompt_name)
    
    def get_system_config(self) -> Dict[str, Any]:
        return self._config_cache.get('system_config', {})
    
    def get_recommendation_config(self) -> Dict[str, Any]:
        return self._config_cache.get('system_config', {}).get('recommendation', {})
    
    def get_scene_recognition_config(self) -> Dict[str, Any]:
        return self._config_cache.get('system_config', {}).get('sceneRecognition', {})
    
    def get_field_extraction_config(self) -> Dict[str, Any]:
        return self._config_cache.get('system_config', {}).get('fieldExtraction', {})
    
    def reload_config(self, config_type: Optional[str] = None):
        if config_type is None or config_type == 'all':
            self._load_all_configs()
        elif config_type == 'system_config':
            self._load_app_config()
        elif config_type == 'app_config':
            self._load_app_config()
        elif config_type == 'ontologies':
            self._load_ontologies()
        elif config_type == 'recommendations':
            self._load_recommendations()
        elif config_type == 'prompts':
            self._load_prompts()


config_loader = ConfigLoader()

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
        self._load_all_configs()
    
    def _load_all_configs(self):
        self._load_system_config()
        self._load_app_config()
        self._load_scene_mappings()
        self._load_ontologies()
        self._load_recommendations()
        self._load_prompts()
    
    def _load_system_config(self):
        path = self.base_path / "system_config.json"
        if path.exists():
            self._config_cache['system_config'] = self._load_json(path)
            self._last_modified['system_config'] = path.stat().st_mtime
    
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
            self._config_cache['app_config'] = self._load_json(path)
            self._last_modified['app_config'] = path.stat().st_mtime
    
    def _load_scene_mappings(self):
        path = self.base_path / "scenes" / "scene_mapping.json"
        if path.exists():
            self._config_cache['scene_mappings'] = self._load_json(path)
            self._last_modified['scene_mappings'] = path.stat().st_mtime
    
    def _load_ontologies(self):
        ontologies = {}
        ontologies_path = self.base_path / "ontologies"
        if ontologies_path.exists():
            for file in ontologies_path.glob("*.json"):
                data = self._load_json(file)
                if data and 'formCode' in data:
                    ontologies[data['formCode']] = data
        self._config_cache['ontologies'] = ontologies
    
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
        self._config_cache['prompts'] = prompts
    
    def get_app_config(self) -> Dict[str, Any]:
        return self._config_cache.get('app_config', {})
    
    def get_scene_mappings(self) -> List[Dict]:
        return self._config_cache.get('scene_mappings', {}).get('sceneMappings', [])
    
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
            self._load_system_config()
        elif config_type == 'app_config':
            self._load_app_config()
        elif config_type == 'scene_mappings':
            self._load_scene_mappings()
        elif config_type == 'ontologies':
            self._load_ontologies()
        elif config_type == 'recommendations':
            self._load_recommendations()
        elif config_type == 'prompts':
            self._load_prompts()


config_loader = ConfigLoader()

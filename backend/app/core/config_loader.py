import json
import os
from pathlib import Path
from typing import Dict, Any, List, Optional
import threading
import time

from app.core.logger import get_logger

logger = get_logger(__name__)


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
        self._current_data_source_type = 'file'
        self._db_session_factory = None
        self._data_source = None
        
        self._load_app_config()
        self._load_recommendations()
        self._load_prompts()
        self._load_ontologies()
        self._load_scenes()
    
    def get_current_data_source_type(self) -> str:
        return self._current_data_source_type
    
    def set_db_session_factory(self, session_factory):
        self._db_session_factory = session_factory
    
    def switch_data_source(self, source_type):
        from app.core.data_source import DataSourceType
        
        if source_type == DataSourceType.DATABASE:
            if not self._db_session_factory:
                raise RuntimeError("[ConfigLoader] 切换到数据库数据源前，必须先调用 set_db_session_factory 设置会话工厂")
            
            from app.core.database_data_source import DatabaseDataSource
            self._data_source = DatabaseDataSource(self._db_session_factory)
            
            self._config_cache['ontologies'] = self._data_source.load_ontologies()
            self._config_cache['scenes'] = self._data_source.load_scenes()
            self._config_cache['prompts'] = self._data_source.load_prompts()
            self._config_cache['recommendations'] = self._data_source.load_recommendations()
            
            self._current_data_source_type = 'database'
            logger.info("[ConfigLoader] 数据源已切换为 database")
        
        elif source_type == DataSourceType.FILE:
            self._data_source = None
            self._load_app_config()
            self._load_recommendations()
            self._load_prompts()
            self._load_ontologies()
            self._load_scenes()
            self._current_data_source_type = 'file'
            logger.info("[ConfigLoader] 数据源已切换为 file")
        
        else:
            raise ValueError(f"[ConfigLoader] 不支持的数据源类型: {source_type}")
    
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
        
        scene_prompts = {}
        scene_prompts_path = self.base_path / "prompts" / "scenes"
        if scene_prompts_path.exists():
            for file in scene_prompts_path.glob("*.txt"):
                prompt_name = file.stem
                scene_prompts[prompt_name] = self._load_text(file)
        
        self._config_cache['prompts'] = prompts
        self._config_cache['scene_prompts'] = scene_prompts
    
    def _load_ontologies(self):
        ontologies = {}
        ontologies_path = self.base_path / "ontologies"
        if ontologies_path.exists():
            for file in ontologies_path.glob("*.json"):
                data = self._load_json(file)
                if data:
                    ontology_code = file.stem
                    ontologies[ontology_code] = data
                    logger.debug("[ConfigLoader] 加载本体: %s", ontology_code)
        
        self._config_cache['ontologies'] = ontologies
        logger.info("[ConfigLoader] 从文件加载本体 count=%d", len(ontologies))
    
    def _load_scenes(self):
        scenes = []
        scene_mapping_path = self.base_path / "scenes" / "scene_mapping.json"
        if scene_mapping_path.exists():
            data = self._load_json(scene_mapping_path)
            if data:
                scenes = data.get("sceneMappings", [])
        
        versions_path = self.base_path / "versions"
        if versions_path.exists():
            for scene_dir in versions_path.iterdir():
                if scene_dir.is_dir():
                    for file in scene_dir.glob("*.json"):
                        data = self._load_json(file)
                        if data:
                            scene_data = data.get("data", data)
                            scene_code = scene_data.get("sceneCode", scene_dir.name)
                            scenes.append({
                                "sceneCode": scene_code,
                                "sceneName": scene_data.get("sceneName", scene_code),
                                "description": scene_data.get("description", ""),
                                "keywords": scene_data.get("keywords", []),
                                "priority": scene_data.get("priority", 1),
                                "isActive": scene_data.get("isActive", True),
                                "promptCode": scene_data.get("promptCode", scene_code),
                                "actionPrompt": scene_code,
                                "config": scene_data.get("config", {})
                            })
        
        self._config_cache['scenes'] = scenes
        logger.info("[ConfigLoader] 从文件加载场景 count=%d", len(scenes))
    
    def get_app_config(self) -> Dict[str, Any]:
        return self._config_cache.get('app_config', {})
    
    def get_scene_mappings(self) -> List[Dict]:
        return self._config_cache.get('scenes', [])
    
    def get_scene_by_code(self, scene_code: str) -> Optional[Dict]:
        scenes = self._config_cache.get('scenes', [])
        for scene in scenes:
            if scene.get('sceneCode') == scene_code:
                return scene
        return None
    
    def get_all_scenes(self) -> List[Dict]:
        return self.get_scene_mappings()
    
    def get_scene_prompt(self, scene_code: str) -> Optional[str]:
        scene = self.get_scene_by_code(scene_code)
        if scene:
            prompt_file = scene.get('actionPrompt')
            if prompt_file:
                scene_prompts = self._config_cache.get('scene_prompts', {})
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
        prompts = self._config_cache.get('prompts', {})
        if prompt_name in prompts:
            return prompts[prompt_name]
        scene_prompts = self._config_cache.get('scene_prompts', {})
        return scene_prompts.get(prompt_name)
    
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
            self._load_app_config()
            self._load_recommendations()
            self._load_prompts()
            self._load_ontologies()
            self._load_scenes()
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
        elif config_type == 'scenes':
            self._load_scenes()


config_loader = ConfigLoader()
import json
import logging
import os
from pathlib import Path
from typing import Dict, Any, List, Optional
import threading
import time

from .data_source import DataSourceType, DataSourceFactory

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
        self._current_data_source = None
        self._data_source_type = None
        
        self._load_app_config()
        self._load_recommendations()
        self._load_prompts()
        
        self._init_data_source()
    
    def _init_data_source(self):
        app_config = self.get_app_config()
        data_source_config = app_config.get('dataSource', {})
        source_type = data_source_config.get('type', 'file').lower()
        
        try:
            self._data_source_type = DataSourceType(source_type)
            logger.info("[ConfigLoader] 数据源类型: %s", self._data_source_type.value)
            
            if self._data_source_type == DataSourceType.FILE:
                from .file_data_source import FileDataSource
                self._current_data_source = FileDataSource(str(self.base_path))
            elif self._data_source_type == DataSourceType.DATABASE:
                if self._db_session_factory:
                    from .database_data_source import DatabaseDataSource
                    self._current_data_source = DatabaseDataSource(self._db_session_factory)
                else:
                    logger.warning("[ConfigLoader] 数据库数据源需要设置session_factory，暂时使用文件数据源")
                    from .file_data_source import FileDataSource
                    self._current_data_source = FileDataSource(str(self.base_path))
            
            if self._current_data_source:
                self._config_cache['ontologies'] = self._current_data_source.load_ontologies()
                self._config_cache['scenes'] = self._current_data_source.load_scenes()
                
        except ValueError:
            logger.error("[ConfigLoader] 无效的数据源类型: %s，使用文件数据源", source_type)
            from .file_data_source import FileDataSource
            self._current_data_source = FileDataSource(str(self.base_path))
            self._data_source_type = DataSourceType.FILE
    
    def set_db_session_factory(self, session_factory):
        """设置数据库会话工厂并加载本体"""
        self._db_session_factory = session_factory
        
        if self._data_source_type == DataSourceType.DATABASE or \
           self.get_app_config().get('dataSource', {}).get('type') == 'database':
            try:
                from .database_data_source import DatabaseDataSource
                self._current_data_source = DatabaseDataSource(session_factory)
                self._data_source_type = DataSourceType.DATABASE
                self._config_cache['ontologies'] = self._current_data_source.load_ontologies()
                self._config_cache['scenes'] = self._current_data_source.load_scenes()
                logger.info("[ConfigLoader] 切换到数据库数据源")
            except Exception as e:
                logger.error("[ConfigLoader] 初始化数据库数据源失败: %s", e)
                raise
    
    def switch_data_source(self, source_type: DataSourceType):
        """切换数据源类型"""
        if source_type == self._data_source_type:
            logger.info("[ConfigLoader] 数据源类型已为: %s", source_type.value)
            return
        
        self._data_source_type = source_type
        
        if source_type == DataSourceType.FILE:
            from .file_data_source import FileDataSource
            self._current_data_source = FileDataSource(str(self.base_path))
        elif source_type == DataSourceType.DATABASE:
            if not self._db_session_factory:
                raise RuntimeError("[ConfigLoader] 切换到数据库数据源需要先设置session_factory")
            from .database_data_source import DatabaseDataSource
            self._current_data_source = DatabaseDataSource(self._db_session_factory)
        
        self._config_cache['ontologies'] = self._current_data_source.load_ontologies()
        self._config_cache['scenes'] = self._current_data_source.load_scenes()
        logger.info("[ConfigLoader] 已切换数据源类型: %s", source_type.value)
    
    def get_current_data_source_type(self) -> str:
        """获取当前数据源类型"""
        return self._data_source_type.value if self._data_source_type else 'unknown'
    
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
    
    def get_app_config(self) -> Dict[str, Any]:
        return self._config_cache.get('app_config', {})
    
    def get_scene_mappings(self) -> List[Dict]:
        """获取场景映射列表"""
        if self._current_data_source:
            return self._current_data_source.load_scenes()
        return self._config_cache.get('scenes', [])
    
    def get_scene_by_code(self, scene_code: str) -> Optional[Dict]:
        """获取指定场景"""
        if self._current_data_source:
            return self._current_data_source.get_scene(scene_code)
        return None
    
    def get_all_scenes(self) -> List[Dict]:
        """获取所有场景（别名）"""
        return self.get_scene_mappings()
    
    def get_scene_prompt(self, scene_code: str) -> Optional[str]:
        """获取场景提示词"""
        scene = self.get_scene_by_code(scene_code)
        if scene:
            prompt_file = scene.get('actionPrompt')
            if prompt_file:
                scene_prompts = self._config_cache.get('scene_prompts', {})
                from pathlib import Path
                prompt_name = Path(prompt_file).stem
                return scene_prompts.get(prompt_name)
        return None
    
    def get_ontology(self, form_code: str) -> Optional[Dict]:
        if self._current_data_source:
            return self._current_data_source.get_ontology(form_code)
        return self._config_cache.get('ontologies', {}).get(form_code)
    
    def get_all_ontologies(self) -> Dict[str, Dict]:
        if self._current_data_source:
            return self._current_data_source.load_ontologies()
        return self._config_cache.get('ontologies', {})
    
    def get_recommendations(self, form_code: str, field_code: str) -> List[str]:
        if self._current_data_source:
            return self._current_data_source.get_recommendation(form_code, field_code)
        recommendations = self._config_cache.get('recommendations', {})
        form_recommendations = recommendations.get(form_code, {})
        return form_recommendations.get(field_code, [])
    
    def get_prompt(self, prompt_name: str) -> Optional[str]:
        if self._current_data_source:
            return self._current_data_source.get_prompt(prompt_name)
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
            self._load_app_config()
            self._load_recommendations()
            self._load_prompts()
            self._init_data_source()
        elif config_type == 'system_config':
            self._load_app_config()
        elif config_type == 'app_config':
            self._load_app_config()
            self._init_data_source()
        elif config_type == 'ontologies':
            if self._current_data_source:
                self._config_cache['ontologies'] = self._current_data_source.load_ontologies()
        elif config_type == 'recommendations':
            self._load_recommendations()
        elif config_type == 'prompts':
            self._load_prompts()


config_loader = ConfigLoader()
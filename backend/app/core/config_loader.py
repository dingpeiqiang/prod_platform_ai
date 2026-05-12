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
        # 注意：不在这里加载本体，等待 set_db_session_factory 后手动调用 reload_config
        self._load_app_config()
        self._load_recommendations()
        self._load_prompts()
    
    def set_db_session_factory(self, session_factory):
        """设置数据库会话工厂并加载本体"""
        self._db_session_factory = session_factory
        # 设置完数据库会话工厂后，立即加载本体
        try:
            self._load_ontologies()
        except Exception as e:
            logger.error("[ConfigLoader] 初始化本体失败: %s", e)
            raise
    
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
        """加载本体 - 只从数据库查询，无兜底方案"""
        self._load_ontologies_from_db()
    

    def _load_ontologies_from_db(self):
        """从数据库加载本体 - 无兜底，查不到就报错"""
        if not self._db_session_factory:
            raise RuntimeError(
                "[ConfigLoader] 数据库会话工厂未设置，无法加载本体。"
                "请在应用启动时调用 config_loader.set_db_session_factory(session_factory)"
            )
        
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
                
                if len(ontologies) == 0:
                    logger.warning("[ConfigLoader] 数据库中没有任何启用的本体数据")
            finally:
                db.close()
        except Exception as e:
            logger.error("[ConfigLoader] 从数据库加载本体失败: %s", e)
            # 清空缓存并抛出异常，不静默失败
            self._config_cache['ontologies'] = {}
            raise RuntimeError(f"[ConfigLoader] 本体加载失败: {e}") from e
    

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
        """从数据库获取场景映射列表"""
        if not self._db_session_factory:
            logger.warning("[ConfigLoader] 数据库会话工厂未设置，无法查询场景")
            return []
        
        try:
            from app.models.scene import Scene
            
            db = self._db_session_factory()
            try:
                scenes = db.query(Scene).filter(Scene.is_active == True).all()
                result = []
                for scene in scenes:
                    result.append({
                        'sceneCode': scene.scene_code,
                        'sceneName': scene.scene_name,
                        'description': scene.description,
                        'keywords': scene.keywords or [],
                        'priority': scene.priority,
                        'isActive': scene.is_active,
                        'formCode': scene.form_code,
                        'actionPrompt': scene.action_prompt_file
                    })
                return result
            finally:
                db.close()
        except Exception as e:
            logger.error("[ConfigLoader] 查询场景失败: %s", e)
            return []
    
    def get_scene_by_code(self, scene_code: str) -> Optional[Dict]:
        """从数据库获取指定场景"""
        if not self._db_session_factory:
            logger.warning("[ConfigLoader] 数据库会话工厂未设置，无法查询场景")
            return None
        
        try:
            from app.models.scene import Scene
            
            db = self._db_session_factory()
            try:
                scene = db.query(Scene).filter(
                    Scene.scene_code == scene_code,
                    Scene.is_active == True
                ).first()
                
                if scene:
                    return {
                        'sceneCode': scene.scene_code,
                        'sceneName': scene.scene_name,
                        'description': scene.description,
                        'keywords': scene.keywords or [],
                        'priority': scene.priority,
                        'isActive': scene.is_active,
                        'formCode': scene.form_code,
                        'actionPrompt': scene.action_prompt_file
                    }
                return None
            finally:
                db.close()
        except Exception as e:
            logger.error("[ConfigLoader] 查询场景失败: %s", e)
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

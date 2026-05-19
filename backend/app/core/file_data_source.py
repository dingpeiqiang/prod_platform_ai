import json
import logging
from pathlib import Path
from typing import Dict, Any, Optional, List

from .data_source import BaseDataSource, DataSourceType, DataSourceFactory

logger = logging.getLogger("file_data_source")


class FileDataSource(BaseDataSource):
    
    def __init__(self, base_path: Optional[str] = None):
        if base_path:
            self.base_path = Path(base_path)
        else:
            self.base_path = Path(__file__).parent.parent.parent / "config"
        
        self._cache: Dict[str, Any] = {}
        self._load_all()
    
    def _load_all(self):
        self.load_ontologies()
        self.load_scenes()
        self.load_prompts()
        self.load_recommendations()
    
    def _load_json(self, path: Path) -> Optional[Dict]:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            logger.debug("[FileDataSource] 加载 JSON: %s", path.name)
            return data
        except Exception as e:
            logger.error("[FileDataSource] 加载失败 %s: %s", path, e)
            return None
    
    def _load_text(self, path: Path) -> Optional[str]:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                data = f.read()
            logger.debug("[FileDataSource] 加载 TXT: %s (%d 字符)", path.name, len(data))
            return data
        except Exception as e:
            logger.error("[FileDataSource] 加载失败 %s: %s", path, e)
            return None
    
    def load_ontologies(self) -> Dict[str, Any]:
        ontologies = {}
        ontologies_path = self.base_path / "ontologies"
        
        if ontologies_path.exists():
            for file in ontologies_path.glob("*.json"):
                data = self._load_json(file)
                if data:
                    ontology_data = data.get("data", data)
                    form_code = ontology_data.get("formCode", file.stem)
                    ontologies[form_code] = ontology_data
        
        self._cache['ontologies'] = ontologies
        logger.info("[FileDataSource] 从文件加载本体 count=%d", len(ontologies))
        return ontologies
    
    def load_scenes(self) -> List[Dict[str, Any]]:
        scenes = []
        scenes_path = self.base_path / "versions"
        
        if scenes_path.exists():
            for scene_dir in scenes_path.iterdir():
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
                                "actionPrompt": scene_code
                            })
        
        self._cache['scenes'] = scenes
        logger.info("[FileDataSource] 从文件加载场景 count=%d", len(scenes))
        return scenes
    
    def load_prompts(self) -> Dict[str, str]:
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
        
        self._cache['prompts'] = prompts
        self._cache['scene_prompts'] = scene_prompts
        logger.info("[FileDataSource] 从文件加载提示词 prompts=%d, scene_prompts=%d", 
                   len(prompts), len(scene_prompts))
        return {**prompts, **scene_prompts}
    
    def load_recommendations(self) -> Dict[str, Any]:
        path = self.base_path / "templates" / "recommendations.json"
        recommendations = {}
        
        if path.exists():
            data = self._load_json(path)
            if data:
                recommendations = data.get('recommendations', {})
        
        self._cache['recommendations'] = recommendations
        logger.info("[FileDataSource] 从文件加载推荐配置")
        return recommendations
    
    def get_ontology(self, form_code: str) -> Optional[Dict[str, Any]]:
        return self._cache.get('ontologies', {}).get(form_code)
    
    def get_scene(self, scene_code: str) -> Optional[Dict[str, Any]]:
        scenes = self._cache.get('scenes', [])
        for scene in scenes:
            if scene.get('sceneCode') == scene_code:
                return scene
        return None
    
    def get_prompt(self, prompt_name: str) -> Optional[str]:
        prompts = self._cache.get('prompts', {})
        if prompt_name in prompts:
            return prompts[prompt_name]
        
        scene_prompts = self._cache.get('scene_prompts', {})
        return scene_prompts.get(prompt_name)
    
    def get_recommendation(self, form_code: str, field_code: str) -> List[str]:
        recommendations = self._cache.get('recommendations', {})
        form_recommendations = recommendations.get(form_code, {})
        return form_recommendations.get(field_code, [])
    
    def reload(self):
        self._load_all()


DataSourceFactory.register(DataSourceType.FILE, FileDataSource)
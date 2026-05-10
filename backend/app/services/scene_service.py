"""
场景管理服务
"""
import json
import logging
from pathlib import Path
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.models.scene import Scene
from app.core.config_loader import config_loader

logger = logging.getLogger("scene_service")

# 配置根路径
_BASE_DIR = Path(__file__).parent.parent.parent / "config"


class SceneService:

    @classmethod
    def list_scenes(cls, db: Session, is_active: Optional[bool] = None) -> Dict[str, Any]:
        """获取场景列表"""
        try:
            query = db.query(Scene)
            
            if is_active is not None:
                query = query.filter(Scene.is_active == is_active)
            
            scenes = query.order_by(desc(Scene.priority)).all()
            
            return {
                "success": True,
                "total": len(scenes),
                "data": [scene.to_dict() for scene in scenes]
            }
        except Exception as e:
            logger.exception(f"Failed to list scenes: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_scene(cls, scene_code: str, db: Session) -> Dict[str, Any]:
        """获取单个场景"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            return {"success": True, "data": scene.to_dict()}
        except Exception as e:
            logger.exception(f"Failed to get scene {scene_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def create_scene(cls, scene_data: Dict[str, Any], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """创建场景"""
        try:
            scene_code = scene_data.get("sceneCode")
            if not scene_code:
                return {"success": False, "message": "sceneCode is required"}
            
            existing = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if existing:
                return {"success": False, "message": f"Scene {scene_code} already exists"}
            
            scene = Scene(
                scene_code=scene_code,
                scene_name=scene_data.get("sceneName", scene_code),
                description=scene_data.get("description"),
                keywords=scene_data.get("keywords", []),
                priority=scene_data.get("priority", 10),
                is_active=scene_data.get("isActive", True),
                intent_type=scene_data.get("intentType"),
                form_code=scene_data.get("formCode"),
                action_type=scene_data.get("actionType", "form_generation"),
                action_prompt_file=scene_data.get("actionPromptFile"),
                required_tools=scene_data.get("requiredTools", []),
                available_tools=scene_data.get("availableTools", []),
                pre_action_steps=scene_data.get("preActionSteps", []),
                post_action_steps=scene_data.get("postActionSteps", []),
                version=1,
                created_by=user
            )
            
            db.add(scene)
            db.commit()
            db.refresh(scene)
            
            # 同时写入文件
            cls._write_scene_to_file(scene)
            config_loader.reload_config("scene_mappings")
            
            logger.info(f"Created scene: {scene_code}")
            return {"success": True, "data": scene.to_dict(), "message": "Scene created successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create scene: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def update_scene(cls, scene_code: str, scene_data: Dict[str, Any], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """更新场景"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            
            if "sceneName" in scene_data:
                scene.scene_name = scene_data["sceneName"]
            if "description" in scene_data:
                scene.description = scene_data["description"]
            if "keywords" in scene_data:
                scene.keywords = scene_data["keywords"]
            if "priority" in scene_data:
                scene.priority = scene_data["priority"]
            if "isActive" in scene_data:
                scene.is_active = scene_data["isActive"]
            if "intentType" in scene_data:
                scene.intent_type = scene_data["intentType"]
            if "formCode" in scene_data:
                scene.form_code = scene_data["formCode"]
            if "actionType" in scene_data:
                scene.action_type = scene_data["actionType"]
            if "actionPromptFile" in scene_data:
                scene.action_prompt_file = scene_data["actionPromptFile"]
            if "requiredTools" in scene_data:
                scene.required_tools = scene_data["requiredTools"]
            if "availableTools" in scene_data:
                scene.available_tools = scene_data["availableTools"]
            if "preActionSteps" in scene_data:
                scene.pre_action_steps = scene_data["preActionSteps"]
            if "postActionSteps" in scene_data:
                scene.post_action_steps = scene_data["postActionSteps"]
            
            scene.version = (scene.version or 0) + 1
            scene.updated_by = user
            
            db.commit()
            
            # 同时写入文件
            cls._write_scene_to_file(scene)
            config_loader.reload_config("scene_mappings")
            
            logger.info(f"Updated scene: {scene_code}")
            return {"success": True, "data": scene.to_dict(), "message": "Scene updated successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update scene {scene_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def delete_scene(cls, scene_code: str, db: Session) -> Dict[str, Any]:
        """删除场景"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            
            db.delete(scene)
            db.commit()
            
            # 更新文件，移除该场景
            cls._remove_scene_from_file(scene_code)
            config_loader.reload_config("scene_mappings")
            
            logger.info(f"Deleted scene: {scene_code}")
            return {"success": True, "message": f"Scene {scene_code} deleted successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete scene {scene_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def toggle_active(cls, scene_code: str, db: Session) -> Dict[str, Any]:
        """启用/禁用场景"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            
            scene.is_active = not scene.is_active
            db.commit()
            
            cls._write_scene_to_file(scene)
            config_loader.reload_config("scene_mappings")
            
            logger.info(f"Toggled scene {scene_code} active: {scene.is_active}")
            return {"success": True, "data": scene.to_dict(), "message": f"Scene {'activated' if scene.is_active else 'deactivated'} successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to toggle scene {scene_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def test_scene_recognition(cls, user_input: str, db: Session) -> Dict[str, Any]:
        """测试场景识别"""
        try:
            # 获取所有启用的场景
            scenes = db.query(Scene).filter(Scene.is_active == True).all()
            
            # 简单的关键词匹配
            matched_scenes = []
            user_input_lower = user_input.lower()
            
            for scene in scenes:
                for keyword in scene.keywords:
                    if keyword.lower() in user_input_lower:
                        matched_scenes.append({
                            "sceneCode": scene.scene_code,
                            "sceneName": scene.scene_name,
                            "confidence": 0.8,
                            "method": "keyword",
                            "matchedKeyword": keyword
                        })
                        break
            
            # 按优先级排序
            matched_scenes.sort(key=lambda x: -next(s.priority for s in scenes if s.scene_code == x["sceneCode"]))
            
            best_match = matched_scenes[0] if matched_scenes else None
            
            return {
                "success": True,
                "bestMatch": best_match,
                "allMatches": matched_scenes,
                "totalScanned": len(scenes)
            }
        except Exception as e:
            logger.exception(f"Failed to test scene recognition: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_scene_stats(cls, db: Session) -> Dict[str, Any]:
        """获取场景统计"""
        try:
            total = db.query(Scene).count()
            active = db.query(Scene).filter(Scene.is_active == True).count()
            inactive = total - active
            
            scenes = db.query(Scene).all()
            scenes_by_action = {}
            for scene in scenes:
                action_type = scene.action_type or "unknown"
                scenes_by_action[action_type] = scenes_by_action.get(action_type, 0) + 1
            
            return {
                "success": True,
                "data": {
                    "total": total,
                    "active": active,
                    "inactive": inactive,
                    "byActionType": scenes_by_action
                }
            }
        except Exception as e:
            logger.exception(f"Failed to get scene stats: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _write_scene_to_file(cls, scene: Scene):
        """将场景配置写入文件"""
        try:
            scenes_dir = _BASE_DIR / "scenes"
            scenes_dir.mkdir(parents=True, exist_ok=True)
            file_path = scenes_dir / "scene_mapping.json"
            
            # 读取现有配置
            if file_path.exists():
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
            else:
                data = {"sceneMappings": [], "version": "3.0"}
            
            scene_mappings = data.get("sceneMappings", [])
            
            # 更新或添加场景
            scene_data = scene.to_scene_mapping_format()
            found = False
            for i, existing in enumerate(scene_mappings):
                if existing.get("sceneCode") == scene.scene_code:
                    scene_mappings[i] = scene_data
                    found = True
                    break
            
            if not found:
                scene_mappings.append(scene_data)
            
            data["sceneMappings"] = scene_mappings
            
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
        except Exception as e:
            logger.warning(f"Failed to write scene to file: {e}")

    @classmethod
    def _remove_scene_from_file(cls, scene_code: str):
        """从文件中移除场景"""
        try:
            file_path = _BASE_DIR / "scenes" / "scene_mapping.json"
            if not file_path.exists():
                return
            
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            scene_mappings = data.get("sceneMappings", [])
            scene_mappings = [s for s in scene_mappings if s.get("sceneCode") != scene_code]
            data["sceneMappings"] = scene_mappings
            
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
        except Exception as e:
            logger.warning(f"Failed to remove scene from file: {e}")

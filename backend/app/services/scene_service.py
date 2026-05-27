"""
场景管理服务 - 支持三层树形结构（center / business / scene
"""
import json
import logging
from pathlib import Path
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.models.scene import Scene, SceneHistory
from app.core.config_loader import config_loader

logger = logging.getLogger("scene_service")

# 配置根路径
_BASE_DIR = Path(__file__).parent.parent.parent / "config"


class SceneService:

    @classmethod
    def list_scenes_tree(cls, db: Session, is_active: Optional[bool] = None) -> Dict[str, Any]:
        """获取场景树状场景树"""
        try:
            query = db.query(Scene)
            
            if is_active is not None:
                query = query.filter(Scene.is_active == is_active)
            
            all_scenes = query.order_by(desc(Scene.priority)).all()
            
            # 构建树形结构
            tree = cls._build_tree(all_scenes)
            
            return {
                "success": True,
                "total": len(all_scenes),
                "data": tree
            }
        except Exception as e:
            logger.exception(f"Failed to list scenes: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _build_tree(cls, scenes: List[Scene]) -> List[Dict]:
        """构建树形结构"""
        node_map = {}
        tree = []
        
        # 单次遍历完成节点创建和树形构建
        for scene in scenes:
            node = scene.to_tree_node()
            node_map[scene.id] = node
            
            # 同时构建树
            if scene.parent_id is None and scene.type == 'center':
                tree.append(node)
            elif scene.parent_id in node_map:
                node_map[scene.parent_id]["children"].append(node)
        
        return tree

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
    def get_scene_prompt(cls, scene_code: str, db: Session) -> Dict[str, Any]:
        """根据场景编码获取提示词内容
        
        流程：场景编码 → 查询场景表获取提示词编码 → 根据提示词编码获取提示词内容
        
        Args:
            scene_code: 场景编码
            db: 数据库会话
        
        Returns:
            Dict: 包含场景信息和提示词内容
        """
        try:
            logger.debug(f"[get_scene_prompt] 查询场景 scene_code={scene_code}")
            
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                logger.warning(f"[get_scene_prompt] 场景 {scene_code} 不存在")
                return {"success": False, "message": f"场景 {scene_code} 不存在"}
            
            logger.debug(f"[get_scene_prompt] 找到场景: id={scene.id}, name={scene.scene_name}, prompt_code={scene.prompt_code}")
            
            if not scene.is_active:
                logger.warning(f"[get_scene_prompt] 场景 {scene_code} 已禁用")
                return {"success": False, "message": f"场景 {scene_code} 已禁用"}
            
            scene_data = scene.to_dict()
            prompt_code = scene.prompt_code
            
            # 【提示词叠加机制】
            # 最终提示词 = 自动生成部分（工具/工作流） + 用户配置部分（场景提示词）
            
            # 1. 获取用户配置的场景提示词
            user_prompt_content = None
            if prompt_code:
                logger.debug(f"[get_scene_prompt] 使用提示词编码: {prompt_code}")
                user_prompt_content = cls._get_prompt_from_db(prompt_code, db)
                
                if user_prompt_content:
                    logger.info(f"[get_scene_prompt] 成功获取用户配置提示词，长度={len(user_prompt_content)}")
                else:
                    logger.warning(f"[get_scene_prompt] 未找到用户配置提示词内容 prompt_code={prompt_code}")
            
            # 2. 自动生成提示词（基于关联的工具和工作流）
            auto_prompt_content = cls._generate_auto_prompt(scene_data)
            
            # 3. 叠加组合：自动生成部分 + 用户配置部分
            prompt_content = ""
            if auto_prompt_content:
                prompt_content = auto_prompt_content
                logger.info(f"[get_scene_prompt] 自动生成提示词长度: {len(auto_prompt_content)}")
            
            if user_prompt_content:
                if prompt_content:
                    prompt_content = f"{prompt_content}\n\n{user_prompt_content}"
                else:
                    prompt_content = user_prompt_content
                logger.info(f"[get_scene_prompt] 叠加用户配置提示词后长度: {len(prompt_content)}")
            
            # 如果没有任何提示词内容
            if not prompt_content:
                logger.warning(f"[get_scene_prompt] 场景 {scene_code} 未配置提示词且无关联工具/工作流")
            
            return {
                "success": True,
                "scene": scene_data,
                "prompt_code": prompt_code,
                "prompt_content": prompt_content,
                "message": "获取成功"
            }
        except Exception as e:
            logger.exception(f"[get_scene_prompt] 获取场景提示词失败 scene_code={scene_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _get_prompt_from_db(cls, prompt_code: str, db: Session) -> Optional[str]:
        """从数据库查询提示词内容
        
        Args:
            prompt_code: 提示词编码
            db: 数据库会话
        
        Returns:
            提示词内容，如果未找到返回 None
        """
        try:
            from app.models.prompt import Prompt
            
            prompt = db.query(Prompt).filter(
                Prompt.code == prompt_code,
                Prompt.is_active == True
            ).first()
            
            if prompt:
                logger.debug(f"[_get_prompt_from_db] 从数据库找到提示词: {prompt_code}")
                return prompt.content
            
            logger.debug(f"[_get_prompt_from_db] 数据库中未找到提示词: {prompt_code}")
            return None
            
        except Exception as e:
            logger.exception(f"[_get_prompt_from_db] 查询失败 prompt_code={prompt_code}: {e}")
            return None
    
    @classmethod
    def _build_workflow_prompt(cls, workflow_code: str) -> str:
        """构建工作流调用提示词
        
        当场景关联了工作流时，自动拼接此提示词，指导 LLM 调用工作流
        
        Args:
            workflow_code: 工作流编码
            
        Returns:
            工作流调用提示词
        """
        return f"""## 工作流调用指令

识别到当前场景需要执行工作流，请直接输出调用工作流的 JSON：

```json
{{
  "action": "call_tool",
  "tool_name": "execute_workflow",
  "tool_args": {{
    "workflow_code": "{workflow_code}",
    "inputs": {{
      "user_input": "<<用户原始输入>>"
    }}
  }},
  "message": "正在执行工作流..."
}}
```

**替换说明：**
- 将 `<<用户原始输入>>` 替换为实际的用户输入内容

**注意：**
- 不要添加任何解释性文字
- 直接输出 JSON 格式
- 确保 JSON 格式正确"""
    
    @classmethod
    def _generate_auto_prompt(cls, scene_data: Dict[str, Any]) -> str:
        """根据场景关联的工作流自动生成提示词
        
        自动生成逻辑：
        1. 如果关联了多个工作流 → 生成工作流选择指令
        
        Args:
            scene_data: 场景数据
            
        Returns:
            自动生成的提示词内容，如果没有关联工作流则返回空字符串
        """
        prompt_parts = []
        scene_name = scene_data.get("sceneName", "")
        config = scene_data.get("config", {})
        workflows = config.get("workflows", [])
        
        # 如果没有关联任何工作流，返回空
        if not (isinstance(workflows, list) and len(workflows) > 0):
            return ""
        
        # 场景描述
        if scene_name:
            prompt_parts.append(f"你是专业的{scene_name}场景助手。")
            prompt_parts.append(f"场景名称：{scene_name}")
        
        # 工作流调用部分（支持多个）
        if isinstance(workflows, list) and len(workflows) > 0:
            prompt_parts.append("\n## 可用工作流")
            prompt_parts.append("当前场景可用以下工作流：")
            
            for wf in workflows:
                wf_code = wf.get("code", "")
                wf_name = wf.get("name", wf_code)
                wf_desc = wf.get("description", "")
                is_default = wf.get("isDefault", False)
                
                default_mark = " (默认)" if is_default else ""
                prompt_parts.append(f"- {wf_code}{default_mark}: {wf_name}")
                if wf_desc:
                    prompt_parts.append(f"  - {wf_desc}")
            
            # 使用默认工作流
            default_workflow = next((w for w in workflows if w.get("isDefault")), workflows[0])
            default_code = default_workflow.get("code", "")
            workflow_prompt = cls._build_workflow_prompt(default_code)
            prompt_parts.append(workflow_prompt)
        
        return "\n".join(prompt_parts)

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

            # 处理工作流配置（支持多个）
            config = scene_data.get("config", {}).copy()
            workflows = scene_data.get("workflows")
            
            if workflows and isinstance(workflows, list) and len(workflows) > 0:
                config["workflows"] = workflows
                
                # 设置默认工作流
                default_workflow = next((w for w in workflows if w.get("isDefault")), workflows[0])
                config["workflowCode"] = default_workflow.get("code")
                config["workflowConfig"] = default_workflow.get("config", {})

            scene = Scene(
                scene_code=scene_code,
                scene_name=scene_data.get("sceneName", scene_code),
                description=scene_data.get("description"),
                keywords=scene_data.get("keywords", []),
                priority=scene_data.get("priority", 10),
                is_active=scene_data.get("isActive", True),
                prompt_code=scene_data.get("promptCode"),
                type=scene_data.get("type", "scene"),
                parent_id=scene_data.get("parentId"),
                config=config,
                version=1,
                created_by=user
            )
            
            db.add(scene)
            db.commit()
            db.refresh(scene)
            
            # 创建第一个历史版本
            cls._create_history(db, scene, "Initial version", user)
            
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
            
            change_note = scene_data.pop("changeNote", "Update scene")
            
            # 检查是否有内容变更
            has_changes = False
            
            # 处理工作流配置（支持多个）
            workflows = scene_data.pop("workflows", None)
            
            if workflows is not None:
                config = scene.config.copy() if scene.config else {}
                
                if isinstance(workflows, list) and len(workflows) > 0:
                    config["workflows"] = workflows
                    
                    # 设置默认工作流
                    default_workflow = next((w for w in workflows if w.get("isDefault")), workflows[0])
                    config["workflowCode"] = default_workflow.get("code")
                    config["workflowConfig"] = default_workflow.get("config", {})
                else:
                    # 清空工作流配置
                    config.pop("workflows", None)
                    config.pop("workflowCode", None)
                    config.pop("workflowConfig", None)
                
                scene.config = config
                has_changes = True
            
            # 字段映射表
            field_mapping = {
                "sceneName": ("scene_name", str),
                "description": ("description", str),
                "keywords": ("keywords", list),
                "priority": ("priority", int),
                "promptCode": ("prompt_code", str),
                "type": ("type", str),
                "parentId": ("parent_id", int),
                "config": ("config", dict),
            }
            
            for key, value in scene_data.items():
                if key in field_mapping:
                    model_field, _ = field_mapping[key]
                    current_value = getattr(scene, model_field)
                    if current_value != value:
                        setattr(scene, model_field, value)
                        has_changes = True
                elif key == "isActive":
                    scene.is_active = value
                    has_changes = True
            
            if has_changes:
                scene.version = scene.version + 1
                scene.updated_by = user
                cls._create_history(db, scene, change_note, user)
            
            db.commit()
            db.refresh(scene)
            
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
        """删除场景（包括其子节点）"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            
            # 递归删除子节点
            cls._delete_with_children(scene.id, db)
            
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
    def _delete_with_children(cls, scene_id: int, db: Session):
        """递归删除场景及其子节点"""
        # 先找子节点
        children = db.query(Scene).filter(Scene.parent_id == scene_id).all()
        for child in children:
            cls._delete_with_children(child.id, db)
        
        # 再删当前节点
        scene = db.query(Scene).filter(Scene.id == scene_id).first()
        if scene:
            db.delete(scene)

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
    def _match_keyword(cls, item, user_input_lower):
        """检查项目是否匹配关键词"""
        for keyword in item.keywords:
            if keyword.lower() in user_input_lower:
                return keyword
        return None
    
    @classmethod
    def test_scene_recognition(cls, user_input: str, db: Session) -> Dict[str, Any]:
        """三层场景识别 - 先匹配center，再business，最后scene"""
        try:
            all_scenes = db.query(Scene).filter(Scene.is_active == True).all()
            
            centers = [s for s in all_scenes if s.type == 'center']
            businesses = [s for s in all_scenes if s.type == 'business']
            scenes = [s for s in all_scenes if s.type == 'scene']
            
            user_input_lower = user_input.lower()
            
            matched_center = cls._find_best_match(centers, user_input_lower)
            matched_business = cls._find_business(businesses, centers, matched_center, user_input_lower)
            target_scenes = cls._get_target_scenes(scenes, businesses, matched_center, matched_business)
            matched_scenes = cls._match_scenes(target_scenes, user_input_lower)
            
            best_match = matched_scenes[0] if matched_scenes else None
            
            return {
                "success": True,
                "bestMatch": best_match,
                "allMatches": matched_scenes,
                "matchedCenter": matched_center.scene_name if matched_center else None,
                "matchedBusiness": matched_business.scene_name if matched_business else None,
                "totalScanned": len(all_scenes)
            }
        except Exception as e:
            logger.exception(f"Failed to test scene recognition: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def _find_best_match(cls, items, user_input_lower):
        """在列表中找到匹配关键词的第一个项目"""
        for item in items:
            if cls._match_keyword(item, user_input_lower):
                return item
        return None
    
    @classmethod
    def _find_business(cls, businesses, centers, matched_center, user_input_lower):
        """查找匹配的业务域"""
        if matched_center:
            center_businesses = [b for b in businesses if b.parent_id == matched_center.id]
            matched = cls._find_best_match(center_businesses, user_input_lower)
            if matched:
                return matched
        
        return cls._find_best_match(businesses, user_input_lower)
    
    @classmethod
    def _get_target_scenes(cls, scenes, businesses, matched_center, matched_business):
        """获取目标场景列表"""
        if matched_business:
            return [s for s in scenes if s.parent_id == matched_business.id]
        
        if matched_center:
            center_business_ids = {b.id for b in businesses if b.parent_id == matched_center.id}
            target_scenes = [s for s in scenes if s.parent_id in center_business_ids]
            return target_scenes if target_scenes else scenes
        
        return scenes
    
    @classmethod
    def _match_scenes(cls, target_scenes, user_input_lower):
        """匹配场景并返回排序后的结果"""
        matched_scenes = []
        for scene in target_scenes:
            matched_keyword = cls._match_keyword(scene, user_input_lower)
            if matched_keyword:
                matched_scenes.append({
                    "sceneCode": scene.scene_code,
                    "sceneName": scene.scene_name,
                    "type": scene.type,
                    "priority": scene.priority,
                    "confidence": 0.8 + (scene.priority / 100),
                    "method": "keyword",
                    "matchedKeyword": matched_keyword
                })
        
        return sorted(matched_scenes, key=lambda x: -x["priority"])

    @classmethod
    def get_scene_stats(cls, db: Session) -> Dict[str, Any]:
        """获取场景统计"""
        try:
            total = db.query(Scene).count()
            active = db.query(Scene).filter(Scene.is_active == True).count()
            inactive = total - active
            
            # 按类型统计
            center_count = db.query(Scene).filter(Scene.type == 'center').count()
            business_count = db.query(Scene).filter(Scene.type == 'business').count()
            scene_count = db.query(Scene).filter(Scene.type == 'scene').count()
            
            return {
                "success": True,
                "data": {
                    "total": total,
                    "active": active,
                    "inactive": inactive,
                    "byType": {
                        "center": center_count,
                        "business": business_count,
                        "scene": scene_count
                    }
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
                data = {"sceneMappings": [], "version": "4.0"}
            
            scene_mappings = data.get("sceneMappings", [])
            
            # 更新或添加场景
            scene_data = {
                "sceneCode": scene.scene_code,
                "sceneName": scene.scene_name,
                "description": scene.description,
                "keywords": scene.keywords,
                "priority": scene.priority,
                "isActive": scene.is_active,
                "promptCode": scene.prompt_code,
                "type": scene.type,
                "parentId": scene.parent_id,
                "config": scene.config
            }
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

    @classmethod
    def _create_history(cls, db: Session, scene: Scene, change_note: str, user: Optional[str]):
        """创建历史版本记录"""
        history = SceneHistory(
            scene_id=scene.id,
            scene_code=scene.scene_code,
            version=scene.version,
            scene_name=scene.scene_name,
            description=scene.description,
            keywords=scene.keywords,
            priority=scene.priority,
            is_active=scene.is_active,
            prompt_code=scene.prompt_code,
            type=scene.type,
            parent_id=scene.parent_id,
            config=scene.config,
            change_note=change_note,
            created_by=user
        )
        db.add(history)

    @classmethod
    def get_history(cls, scene_code: str, db: Session) -> Dict[str, Any]:
        """获取场景版本历史"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}

            history_list = db.query(SceneHistory).filter(
                SceneHistory.scene_id == scene.id
            ).order_by(desc(SceneHistory.version)).all()

            return {
                "success": True,
                "data": [h.to_dict() for h in history_list]
            }
        except Exception as e:
            logger.exception(f"Failed to get history: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def rollback_to_version(cls, scene_code: str, version: int, db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """回滚到指定版本"""
        try:
            scene = db.query(Scene).filter(Scene.scene_code == scene_code).first()
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}

            # 找到目标历史版本
            target_history = db.query(SceneHistory).filter(
                SceneHistory.scene_id == scene.id,
                SceneHistory.version == version
            ).first()

            if not target_history:
                return {"success": False, "message": f"Version {version} not found"}

            # 回滚数据
            scene.scene_name = target_history.scene_name
            scene.description = target_history.description
            scene.keywords = target_history.keywords
            scene.priority = target_history.priority
            scene.is_active = target_history.is_active
            scene.prompt_code = target_history.prompt_code
            scene.type = target_history.type
            scene.parent_id = target_history.parent_id
            scene.config = target_history.config
            scene.version = scene.version + 1
            scene.updated_by = user

            # 创建新版本记录
            cls._create_history(db, scene, f"Rollback to version {version}", user)
            
            db.commit()
            db.refresh(scene)

            # 同时写入文件
            cls._write_scene_to_file(scene)
            config_loader.reload_config("scene_mappings")

            logger.info(f"Rolled back scene {scene_code} to version {version}")
            return {"success": True, "data": scene.to_dict(), "message": f"Rolled back to version {version} successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to rollback scene: {e}")
            return {"success": False, "message": str(e)}

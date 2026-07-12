from typing import Dict, Any, List, Optional
from pathlib import Path

from app.core.logger import get_logger
from app.core.config_loader import config_loader

logger = get_logger(__name__)


class SceneService:

    @classmethod
    def list_scenes_tree(cls, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            all_scenes = config_loader.get_all_scenes()
            
            if is_active is not None:
                all_scenes = [s for s in all_scenes if s.get('isActive') == is_active]
            
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
    def _build_tree(cls, scenes: List[Dict]) -> List[Dict]:
        node_map = {}
        tree = []
        
        for scene in scenes:
            node = {
                "id": scene.get("sceneCode"),
                "label": scene.get("sceneName", scene.get("sceneCode")),
                "type": scene.get("type", "scene"),
                "sceneCode": scene.get("sceneCode"),
                "sceneName": scene.get("sceneName"),
                "priority": scene.get("priority", 1),
                "isActive": scene.get("isActive", True),
                "children": []
            }
            node_map[scene.get("sceneCode")] = node
        
        for scene in scenes:
            node = node_map.get(scene.get("sceneCode"))
            if node:
                parent_id = scene.get("parentId")
                if parent_id is None and scene.get("type") == 'center':
                    tree.append(node)
                elif isinstance(parent_id, str) and parent_id in node_map:
                    node_map[parent_id]["children"].append(node)
                elif isinstance(parent_id, int):
                    pass
        
        return tree

    @classmethod
    def list_scenes(cls, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            scenes = config_loader.get_all_scenes()
            
            if is_active is not None:
                scenes = [s for s in scenes if s.get('isActive') == is_active]
            
            return {
                "success": True,
                "total": len(scenes),
                "data": scenes
            }
        except Exception as e:
            logger.exception(f"Failed to list scenes: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_scene(cls, scene_code: str) -> Dict[str, Any]:
        try:
            scene = config_loader.get_scene_by_code(scene_code)
            if not scene:
                return {"success": False, "message": f"Scene {scene_code} not found"}
            return {"success": True, "data": scene}
        except Exception as e:
            logger.exception(f"Failed to get scene {scene_code}: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def get_scene_prompt(cls, scene_code: str) -> Dict[str, Any]:
        try:
            logger.debug(f"[get_scene_prompt] 查询场景 scene_code={scene_code}")
            
            scene = config_loader.get_scene_by_code(scene_code)
            if not scene:
                logger.warning(f"[get_scene_prompt] 场景 {scene_code} 不存在")
                return {"success": False, "message": f"场景 {scene_code} 不存在"}
            
            logger.debug(f"[get_scene_prompt] 找到场景: sceneCode={scene.get('sceneCode')}, sceneName={scene.get('sceneName')}, promptCode={scene.get('promptCode')}")
            
            if not scene.get("isActive", True):
                logger.warning(f"[get_scene_prompt] 场景 {scene_code} 已禁用")
                return {"success": False, "message": f"场景 {scene_code} 已禁用"}
            
            scene_data = scene
            prompt_code = scene.get("promptCode")
            
            user_prompt_content = None
            if prompt_code:
                logger.debug(f"[get_scene_prompt] 使用提示词编码: {prompt_code}")
                user_prompt_content = config_loader.get_prompt(prompt_code)
                
                if user_prompt_content:
                    logger.info(f"[get_scene_prompt] 成功获取用户配置提示词，长度={len(user_prompt_content)}")
                else:
                    logger.warning(f"[get_scene_prompt] 未找到用户配置提示词内容 prompt_code={prompt_code}")
            
            auto_prompt_content = cls._generate_auto_prompt(scene_data)
            
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
    def _build_workflow_prompt(cls, workflow_code: str) -> str:
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
        prompt_parts = []
        scene_name = scene_data.get("sceneName", "")
        config = scene_data.get("config", {})
        workflows = config.get("workflows", [])
        
        if not (isinstance(workflows, list) and len(workflows) > 0):
            return ""
        
        if scene_name:
            prompt_parts.append(f"你是专业的{scene_name}场景助手。")
            prompt_parts.append(f"场景名称：{scene_name}")
        
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
            
            default_workflow = next((w for w in workflows if w.get("isDefault")), workflows[0])
            default_code = default_workflow.get("code", "")
            workflow_prompt = cls._build_workflow_prompt(default_code)
            prompt_parts.append(workflow_prompt)
        
        return "\n".join(prompt_parts)

    @classmethod
    def create_scene(cls, scene_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        logger.warning("[create_scene] 场景管理功能已切换为文件数据源，不支持创建场景")
        return {"success": False, "message": "场景管理功能已切换为文件数据源，不支持创建场景"}

    @classmethod
    def update_scene(cls, scene_code: str, scene_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        logger.warning("[update_scene] 场景管理功能已切换为文件数据源，不支持更新场景")
        return {"success": False, "message": "场景管理功能已切换为文件数据源，不支持更新场景"}

    @classmethod
    def delete_scene(cls, scene_code: str) -> Dict[str, Any]:
        logger.warning("[delete_scene] 场景管理功能已切换为文件数据源，不支持删除场景")
        return {"success": False, "message": "场景管理功能已切换为文件数据源，不支持删除场景"}

    @classmethod
    def toggle_active(cls, scene_code: str) -> Dict[str, Any]:
        logger.warning("[toggle_active] 场景管理功能已切换为文件数据源，不支持切换场景状态")
        return {"success": False, "message": "场景管理功能已切换为文件数据源，不支持切换场景状态"}

    @classmethod
    def _match_keyword(cls, scene: Dict, user_input_lower: str) -> Optional[str]:
        keywords = scene.get("keywords", [])
        for keyword in keywords:
            if keyword.lower() in user_input_lower:
                return keyword
        return None
    
    @classmethod
    def test_scene_recognition(cls, user_input: str) -> Dict[str, Any]:
        try:
            all_scenes = [s for s in config_loader.get_all_scenes() if s.get("isActive", True)]
            
            centers = [s for s in all_scenes if s.get("type") == 'center']
            businesses = [s for s in all_scenes if s.get("type") == 'business']
            scenes = [s for s in all_scenes if s.get("type") == 'scene']
            
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
                "matchedCenter": matched_center.get("sceneName") if matched_center else None,
                "matchedBusiness": matched_business.get("sceneName") if matched_business else None,
                "totalScanned": len(all_scenes)
            }
        except Exception as e:
            logger.exception(f"Failed to test scene recognition: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def _find_best_match(cls, items: List[Dict], user_input_lower: str) -> Optional[Dict]:
        for item in items:
            if cls._match_keyword(item, user_input_lower):
                return item
        return None
    
    @classmethod
    def _find_business(cls, businesses: List[Dict], centers: List[Dict], matched_center: Optional[Dict], user_input_lower: str) -> Optional[Dict]:
        if matched_center:
            center_code = matched_center.get("sceneCode")
            center_businesses = [b for b in businesses if b.get("parentId") == center_code]
            matched = cls._find_best_match(center_businesses, user_input_lower)
            if matched:
                return matched
        
        return cls._find_best_match(businesses, user_input_lower)
    
    @classmethod
    def _get_target_scenes(cls, scenes: List[Dict], businesses: List[Dict], matched_center: Optional[Dict], matched_business: Optional[Dict]) -> List[Dict]:
        if matched_business:
            business_code = matched_business.get("sceneCode")
            return [s for s in scenes if s.get("parentId") == business_code]
        
        if matched_center:
            center_code = matched_center.get("sceneCode")
            center_business_codes = {b.get("sceneCode") for b in businesses if b.get("parentId") == center_code}
            target_scenes = [s for s in scenes if s.get("parentId") in center_business_codes]
            return target_scenes if target_scenes else scenes
        
        return scenes
    
    @classmethod
    def _match_scenes(cls, target_scenes: List[Dict], user_input_lower: str) -> List[Dict]:
        matched_scenes = []
        for scene in target_scenes:
            matched_keyword = cls._match_keyword(scene, user_input_lower)
            if matched_keyword:
                matched_scenes.append({
                    "sceneCode": scene.get("sceneCode"),
                    "sceneName": scene.get("sceneName"),
                    "type": scene.get("type", "scene"),
                    "priority": scene.get("priority", 1),
                    "confidence": 0.8 + (scene.get("priority", 1) / 100),
                    "method": "keyword",
                    "matchedKeyword": matched_keyword
                })
        
        return sorted(matched_scenes, key=lambda x: -x["priority"])

    @classmethod
    def get_scene_stats(cls) -> Dict[str, Any]:
        try:
            all_scenes = config_loader.get_all_scenes()
            total = len(all_scenes)
            active = sum(1 for s in all_scenes if s.get("isActive", True))
            inactive = total - active
            
            center_count = sum(1 for s in all_scenes if s.get("type") == 'center')
            business_count = sum(1 for s in all_scenes if s.get("type") == 'business')
            scene_count = sum(1 for s in all_scenes if s.get("type") == 'scene')
            
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
    def get_history(cls, scene_code: str) -> Dict[str, Any]:
        logger.warning("[get_history] 场景历史功能已切换为文件数据源，不支持查看历史")
        return {"success": False, "message": "场景历史功能已切换为文件数据源，不支持查看历史"}

    @classmethod
    def rollback_to_version(cls, scene_code: str, version: int, user: Optional[str] = None) -> Dict[str, Any]:
        logger.warning("[rollback_to_version] 场景回滚功能已切换为文件数据源，不支持回滚")
        return {"success": False, "message": "场景回滚功能已切换为文件数据源，不支持回滚"}
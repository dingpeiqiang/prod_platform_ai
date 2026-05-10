"""
场景提示词管理器
"""
import json
import logging
from pathlib import Path
from typing import Dict, Any, Optional
from datetime import datetime

logger = logging.getLogger("scene_prompt_manager")


class ScenePromptManager:
    
    _base_path: Path = None
    
    @classmethod
    def _get_base_path(cls) -> Path:
        """获取基础路径"""
        if cls._base_path is None:
            cls._base_path = Path(__file__).parent.parent.parent / "config" / "prompts" / "scenes"
        return cls._base_path
    
    @classmethod
    def _ensure_dir(cls):
        """确保目录存在"""
        base_path = cls._get_base_path()
        base_path.mkdir(parents=True, exist_ok=True)
        
        # 确保模板目录存在
        templates_dir = base_path / "_templates"
        templates_dir.mkdir(parents=True, exist_ok=True)
    
    @classmethod
    def load_prompt(cls, prompt_file: str) -> Optional[str]:
        """加载提示词文件"""
        try:
            base_path = cls._get_base_path()
            file_path = base_path / prompt_file
            
            if not file_path.exists():
                logger.warning(f"Prompt file not found: {prompt_file}")
                return None
            
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            logger.debug(f"Loaded prompt: {prompt_file}")
            return content
        except Exception as e:
            logger.exception(f"Failed to load prompt {prompt_file}: {e}")
            return None
    
    @classmethod
    def save_prompt(cls, prompt_file: str, content: str) -> Dict[str, Any]:
        """保存提示词文件"""
        try:
            cls._ensure_dir()
            base_path = cls._get_base_path()
            file_path = base_path / prompt_file
            
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            
            logger.info(f"Saved prompt: {prompt_file}")
            return {"success": True, "message": "Prompt saved successfully"}
        except Exception as e:
            logger.exception(f"Failed to save prompt {prompt_file}: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def delete_prompt(cls, prompt_file: str) -> Dict[str, Any]:
        """删除提示词文件"""
        try:
            base_path = cls._get_base_path()
            file_path = base_path / prompt_file
            
            if file_path.exists():
                file_path.unlink()
                logger.info(f"Deleted prompt: {prompt_file}")
            
            return {"success": True, "message": "Prompt deleted successfully"}
        except Exception as e:
            logger.exception(f"Failed to delete prompt {prompt_file}: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def list_prompts(cls) -> Dict[str, Any]:
        """列出所有提示词文件"""
        try:
            base_path = cls._get_base_path()
            
            if not base_path.exists():
                return {"success": True, "data": []}
            
            prompts = []
            for file_path in base_path.glob("*.txt"):
                if file_path.is_file():
                    stat = file_path.stat()
                    prompts.append({
                        "fileName": file_path.name,
                        "fileSize": stat.st_size,
                        "lastModified": datetime.fromtimestamp(stat.st_mtime).isoformat()
                    })
            
            # 按名称排序
            prompts.sort(key=lambda x: x["fileName"])
            
            return {"success": True, "data": prompts}
        except Exception as e:
            logger.exception(f"Failed to list prompts: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def load_template(cls, template_type: str) -> Optional[str]:
        """加载模板"""
        try:
            template_file = f"{template_type}.txt"
            base_path = cls._get_base_path()
            file_path = base_path / "_templates" / template_file
            
            if not file_path.exists():
                logger.warning(f"Template not found: {template_file}")
                return None
            
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            return content
        except Exception as e:
            logger.exception(f"Failed to load template {template_type}: {e}")
            return None
    
    @classmethod
    def build_prompt(cls, prompt_template: str, context: Dict[str, Any]) -> str:
        """构建提示词，替换变量"""
        try:
            result = prompt_template
            
            # 替换变量
            variables = {
                "{scene_code}": str(context.get("scene_code", "")),
                "{scene_name}": str(context.get("scene_name", "")),
                "{form_code}": str(context.get("form_code", "")),
                "{available_tools}": ", ".join(context.get("available_tools", [])),
                "{user_input}": str(context.get("user_input", "")),
                "{current_date}": datetime.now().strftime("%Y-%m-%d"),
                "{current_time}": datetime.now().strftime("%H:%M:%S"),
            }
            
            for var, value in variables.items():
                result = result.replace(var, value)
            
            # 构建工具信息
            if "tools_info" in context:
                tools_info = context["tools_info"]
                if isinstance(tools_info, str):
                    result = result.replace("{tools_info}", tools_info)
                elif isinstance(tools_info, list):
                    tools_str = "\n".join([f"- {t}" for t in tools_info])
                    result = result.replace("{tools_info}", tools_str)
                else:
                    result = result.replace("{tools_info}", "")
            
            return result
        except Exception as e:
            logger.exception(f"Failed to build prompt: {e}")
            return prompt_template
    
    @classmethod
    def create_prompt_from_template(cls, template_type: str, scene_info: Dict[str, Any]) -> Dict[str, Any]:
        """从模板创建提示词"""
        try:
            template = cls.load_template(template_type)
            if not template:
                return {"success": False, "message": f"Template {template_type} not found"}
            
            prompt_content = cls.build_prompt(template, scene_info)
            
            return {"success": True, "data": prompt_content}
        except Exception as e:
            logger.exception(f"Failed to create prompt from template: {e}")
            return {"success": False, "message": str(e)}

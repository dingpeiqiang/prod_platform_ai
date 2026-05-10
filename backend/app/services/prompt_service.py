import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc
from app.models.prompt import Prompt, PromptVersion, PromptTemplate

logger = logging.getLogger("prompt_service")


class PromptService:
    """提示词管理服务"""

    @classmethod
    def list_prompts(cls, db: Session, category: Optional[str] = None, is_active: Optional[bool] = None) -> Dict[str, Any]:
        """获取提示词列表"""
        try:
            query = db.query(Prompt)
            if category:
                query = query.filter(Prompt.category == category)
            if is_active is not None:
                query = query.filter(Prompt.is_active == is_active)

            prompts = query.order_by(desc(Prompt.created_at)).all()
            return {
                "success": True,
                "data": [p.to_dict() for p in prompts]
            }
        except Exception as e:
            logger.exception(f"Failed to list prompts: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_prompt(cls, db: Session, code: str) -> Dict[str, Any]:
        """获取提示词详情"""
        try:
            prompt = db.query(Prompt).filter(Prompt.code == code).first()
            if not prompt:
                return {"success": False, "message": f"Prompt {code} not found"}

            return {
                "success": True,
                "data": prompt.to_dict()
            }
        except Exception as e:
            logger.exception(f"Failed to get prompt {code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def create_prompt(cls, db: Session, prompt_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        """创建提示词"""
        try:
            code = prompt_data.get("code")
            if not code:
                return {"success": False, "message": "Prompt code is required"}

            existing = db.query(Prompt).filter(Prompt.code == code).first()
            if existing:
                return {"success": False, "message": f"Prompt {code} already exists"}

            prompt = Prompt(
                code=code,
                name=prompt_data.get("name", code),
                description=prompt_data.get("description"),
                category=prompt_data.get("category", "general"),
                content=prompt_data.get("content", ""),
                variables=prompt_data.get("variables", []),
                tools=prompt_data.get("tools", []),
                is_template=prompt_data.get("is_template", False),
                created_by=user
            )

            db.add(prompt)
            db.commit()
            db.refresh(prompt)

            # 创建第一个版本
            cls._create_version(db, prompt, "Initial version", user)

            return {
                "success": True,
                "data": prompt.to_dict(),
                "message": "Prompt created successfully"
            }
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create prompt: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def update_prompt(cls, db: Session, code: str, prompt_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        """更新提示词"""
        try:
            prompt = db.query(Prompt).filter(Prompt.code == code).first()
            if not prompt:
                return {"success": False, "message": f"Prompt {code} not found"}

            change_note = prompt_data.pop("changeNote", "Update prompt")

            # 检查是否有内容变更
            has_changes = False
            for key, value in prompt_data.items():
                field = key
                if field == "name":
                    if prompt.name != value:
                        prompt.name = value
                        has_changes = True
                elif field == "description":
                    if prompt.description != value:
                        prompt.description = value
                        has_changes = True
                elif field == "category":
                    if prompt.category != value:
                        prompt.category = value
                        has_changes = True
                elif field == "content":
                    if prompt.content != value:
                        prompt.content = value
                        has_changes = True
                elif field == "variables":
                    if prompt.variables != value:
                        prompt.variables = value
                        has_changes = True
                elif field == "tools":
                    if prompt.tools != value:
                        prompt.tools = value
                        has_changes = True
                elif field == "is_template":
                    if prompt.is_template != value:
                        prompt.is_template = value
                        has_changes = True
                elif field == "is_active":
                    prompt.is_active = value

            if has_changes:
                prompt.version = prompt.version + 1
                prompt.updated_by = user
                cls._create_version(db, prompt, change_note, user)

            db.commit()
            db.refresh(prompt)

            return {
                "success": True,
                "data": prompt.to_dict(),
                "message": "Prompt updated successfully"
            }
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update prompt: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _create_version(cls, db: Session, prompt: Prompt, change_note: str, user: Optional[str]):
        """创建版本记录"""
        version = PromptVersion(
            prompt_id=prompt.id,
            version=prompt.version,
            content=prompt.content,
            variables=prompt.variables,
            tools=prompt.tools,
            change_note=change_note,
            created_by=user
        )
        db.add(version)

    @classmethod
    def get_versions(cls, db: Session, code: str) -> Dict[str, Any]:
        """获取提示词版本历史"""
        try:
            prompt = db.query(Prompt).filter(Prompt.code == code).first()
            if not prompt:
                return {"success": False, "message": f"Prompt {code} not found"}

            versions = db.query(PromptVersion).filter(
                PromptVersion.prompt_id == prompt.id
            ).order_by(desc(PromptVersion.version)).all()

            return {
                "success": True,
                "data": [v.to_dict() for v in versions]
            }
        except Exception as e:
            logger.exception(f"Failed to get versions: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def preview_prompt(cls, db: Session, code: str, variables: Optional[Dict] = None) -> Dict[str, Any]:
        """预览提示词（变量替换）"""
        try:
            prompt = db.query(Prompt).filter(Prompt.code == code).first()
            if not prompt:
                return {"success": False, "message": f"Prompt {code} not found"}

            content = prompt.content
            variables_dict = variables or {}

            # 变量替换
            for var in prompt.variables:
                var_name = var.get("name")
                if var_name:
                    # 优先用传入的变量值，没有则用默认值
                    var_value = variables_dict.get(var_name, var.get("default", ""))
                    content = content.replace(f"{{{{{var_name}}}}}", str(var_value))
                    content = content.replace(f"{{{{ {var_name} }}}}", str(var_value))

            return {
                "success": True,
                "data": {
                    "content": content,
                    "variables": prompt.variables,
                    "tools": prompt.tools
                }
            }
        except Exception as e:
            logger.exception(f"Failed to preview prompt: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def delete_prompt(cls, db: Session, code: str) -> Dict[str, Any]:
        """删除提示词"""
        try:
            prompt = db.query(Prompt).filter(Prompt.code == code).first()
            if not prompt:
                return {"success": False, "message": f"Prompt {code} not found"}

            db.delete(prompt)
            db.commit()

            return {"success": True, "message": "Prompt deleted successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete prompt: {e}")
            return {"success": False, "message": str(e)}

    # ============ 模板库 ============

    @classmethod
    def list_templates(cls, db: Session, category: Optional[str] = None) -> Dict[str, Any]:
        """获取模板列表"""
        try:
            query = db.query(PromptTemplate).filter(PromptTemplate.is_active == True)
            if category:
                query = query.filter(PromptTemplate.category == category)

            templates = query.order_by(desc(PromptTemplate.created_at)).all()
            return {
                "success": True,
                "data": [t.to_dict() for t in templates]
            }
        except Exception as e:
            logger.exception(f"Failed to list templates: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def generate_with_ai(cls, db: Session, request_data: Dict[str, Any]) -> Dict[str, Any]:
        """AI辅助生成提示词"""
        try:
            requirement = request_data.get("requirement", "")
            category = request_data.get("category", "general")
            use_tools = request_data.get("useTools", [])

            # 构建生成提示词的提示词
            generator_prompt = f"""请根据以下需求，生成一个高质量的提示词：

需求描述：
{requirement}

分类：{category}

可用工具：{use_tools if use_tools else '无'}

请输出Markdown格式的提示词，包含以下部分：
1. 角色定义 (Role)
2. 任务描述 (Task)
3. 输入输出格式 (Format)
4. 约束条件 (Constraints)
5. 示例 (Examples，可选)
6. 工具使用说明 (如果有工具的话)

请直接输出提示词内容，不要有其他解释。"""

            # 这里应该调用LLM，暂时返回模拟结果
            # 实际应用中这里需要集成LLM
            generated_content = cls._generate_mock_prompt(requirement, category, use_tools)

            # 自动提取变量
            variables = cls._extract_variables(generated_content)

            return {
                "success": True,
                "data": {
                    "content": generated_content,
                    "variables": variables,
                    "tools": use_tools
                }
            }
        except Exception as e:
            logger.exception(f"Failed to generate prompt with AI: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _generate_mock_prompt(cls, requirement: str, category: str, tools: List) -> str:
        """模拟生成提示词（实际应该调用LLM）"""
        base_content = f"""# 提示词

## Role
你是一个专业的AI助手，负责处理{category}相关的任务。

## Task
{requirement}

## Format
请按照以下格式输出结果：
```json
{{
  "result": "..."
}}
```

## Constraints
- 确保输出质量
- 遵循输入要求
- 高效完成任务

"""
        if tools:
            base_content += """## Tools
你可以使用以下工具：
"""
            for tool in tools:
                base_content += f"- {tool.get('name', tool.get('code', 'tool'))}: {tool.get('description', '')}\n"

        return base_content

    @classmethod
    def _extract_variables(cls, content: str) -> List[Dict]:
        """从提示词中提取变量（{{变量名}}格式）"""
        import re
        variables = []
        pattern = r'\{\{\s*(\w+)\s*\}\}'
        matches = re.findall(pattern, content)

        seen = set()
        for var_name in matches:
            if var_name not in seen:
                seen.add(var_name)
                variables.append({
                    "name": var_name,
                    "description": f"{var_name} 的值",
                    "default": ""
                })
        return variables

    @classmethod
    def optimize_prompt(cls, db: Session, request_data: Dict[str, Any]) -> Dict[str, Any]:
        """AI优化提示词"""
        try:
            original_content = request_data.get("content", "")

            # 这里应该调用LLM进行优化
            optimized_content = cls._optimize_mock_content(original_content)

            return {
                "success": True,
                "data": {
                    "original": original_content,
                    "optimized": optimized_content
                }
            }
        except Exception as e:
            logger.exception(f"Failed to optimize prompt: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _optimize_mock_content(cls, content: str) -> str:
        """模拟优化内容（实际应该调用LLM）"""
        if not content:
            return content

        # 简单的优化：添加一些改进标记
        optimized = content + "\n\n---\n> 提示：此版本经过优化，建议先测试效果"
        return optimized

    @classmethod
    def get_categories(cls) -> Dict[str, Any]:
        """获取分类列表"""
        categories = [
            {"code": "general", "name": "通用"},
            {"code": "form", "name": "表单生成"},
            {"code": "qa", "name": "问答"},
            {"code": "tool", "name": "工具调用"},
            {"code": "analysis", "name": "分析"},
            {"code": "writing", "name": "写作"}
        ]
        return {
            "success": True,
            "data": categories
        }

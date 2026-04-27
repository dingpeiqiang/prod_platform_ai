"""
上下文管理器 - 管理 AGENTS.md 和项目知识库

为 AI 提供静态上下文（规范/契约）和动态上下文（状态/日志）
"""

from typing import Dict, List, Optional, Any
from pathlib import Path
import json
import logging

logger = logging.getLogger(__name__)


class ContextManager:
    """
    上下文管理器
    
    职责：
    1. 加载和管理项目静态上下文（AGENTS.md、Schema契约等）
    2. 根据任务类型动态注入相关上下文
    3. 管理上下文版本和更新
    """
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        """
        初始化上下文管理器
        
        Args:
            config: 配置字典，支持以下键：
                - agents_md_path: AGENTS.md 文件路径
                - schema_dir: 表单Schema目录
                - prompts_dir: 提示词模板目录
                - constraints_file: 架构约束文件
        """
        self.config = config or {}
        
        # 默认路径（相对于 backend 目录）
        base_path = Path(__file__).parent.parent.parent.parent
        
        self.agents_md_path = Path(
            self.config.get("agents_md_path", base_path / "AGENTS.md")
        )
        self.schema_dir = Path(
            self.config.get("schema_dir", base_path / "config" / "schemas")
        )
        self.prompts_dir = Path(
            self.config.get("prompts_dir", base_path / "config" / "prompts")
        )
        
        # 上下文缓存
        self._contexts: Dict[str, str] = {}
        self._schemas: Dict[str, Dict] = {}
        self._prompt_templates: Dict[str, str] = {}
        
        # 加载所有上下文
        self._load_contexts()
    
    def _load_contexts(self):
        """加载所有上下文文档"""
        # 1. 加载 AGENTS.md
        self._load_agents_md()
        
        # 2. 加载表单Schema
        self._load_schemas()
        
        # 3. 加载提示词模板
        self._load_prompt_templates()
    
    def _load_agents_md(self):
        """加载 AGENTS.md 文档"""
        if self.agents_md_path.exists():
            try:
                with open(self.agents_md_path, "r", encoding="utf-8") as f:
                    self._contexts["agents_md"] = f.read()
                logger.info(f"Loaded AGENTS.md from {self.agents_md_path}")
            except Exception as e:
                logger.warning(f"Failed to load AGENTS.md: {e}")
                self._contexts["agents_md"] = self._get_default_agents_md()
        else:
            logger.info("AGENTS.md not found, using default")
            self._contexts["agents_md"] = self._get_default_agents_md()
    
    def _load_schemas(self):
        """加载表单Schema"""
        if not self.schema_dir.exists():
            logger.warning(f"Schema directory not found: {self.schema_dir}")
            return
        
        for schema_file in self.schema_dir.glob("*.json"):
            try:
                with open(schema_file, "r", encoding="utf-8") as f:
                    schema = json.load(f)
                    form_code = schema_file.stem
                    self._schemas[form_code] = schema
                logger.debug(f"Loaded schema: {form_code}")
            except Exception as e:
                logger.warning(f"Failed to load schema {schema_file}: {e}")
    
    def _load_prompt_templates(self):
        """加载提示词模板"""
        if not self.prompts_dir.exists():
            logger.warning(f"Prompts directory not found: {self.prompts_dir}")
            return
        
        for template_file in self.prompts_dir.glob("*.txt"):
            try:
                with open(template_file, "r", encoding="utf-8") as f:
                    template_name = template_file.stem
                    self._prompt_templates[template_name] = f.read()
                logger.debug(f"Loaded prompt template: {template_name}")
            except Exception as e:
                logger.warning(f"Failed to load prompt {template_file}: {e}")
    
    def get_agents_context(self) -> str:
        """获取 AGENTS.md 上下文"""
        return self._contexts.get("agents_md", "")
    
    def get_schema(self, form_code: str) -> Optional[Dict]:
        """获取指定表单的Schema"""
        return self._schemas.get(form_code)
    
    def get_all_schemas(self) -> Dict[str, Dict]:
        """获取所有表单Schema"""
        return self._schemas.copy()
    
    def get_prompt_template(self, template_name: str) -> Optional[str]:
        """获取提示词模板"""
        return self._prompt_templates.get(template_name)
    
    def inject_context(
        self, 
        base_prompt: str, 
        context_types: List[str],
        include_schemas: Optional[List[str]] = None
    ) -> str:
        """
        将上下文注入到提示词
        
        Args:
            base_prompt: 基础提示词
            context_types: 要注入的上下文类型列表
                - "agents_md": AGENTS.md 文档
                - "system_prompt": 系统提示词
                - "form_codes": 表单编码列表
            include_schemas: 要包含的Schema表单编码
            
        Returns:
            增强后的提示词
        """
        parts = []
        
        for ctx_type in context_types:
            if ctx_type == "agents_md":
                agents_context = self.get_agents_context()
                if agents_context:
                    parts.append(f"## 项目规范 (AGENTS.md)\n\n{agents_context}")
            
            elif ctx_type == "system_prompt":
                parts.append(self._get_system_prompt())
            
            elif ctx_type == "schemas" and include_schemas:
                schema_context = self._build_schema_context(include_schemas)
                if schema_context:
                    parts.append(f"## 表单Schema定义\n\n{schema_context}")
        
        if parts:
            return "\n\n---\n\n".join(parts) + "\n\n---\n\n" + base_prompt
        
        return base_prompt
    
    def _build_schema_context(self, form_codes: List[str]) -> str:
        """构建Schema上下文"""
        lines = []
        for code in form_codes:
            schema = self.get_schema(code)
            if schema:
                lines.append(f"### {code}")
                lines.append(f"```json")
                lines.append(json.dumps(schema, ensure_ascii=False, indent=2))
                lines.append(f"```")
        return "\n".join(lines)
    
    def _get_system_prompt(self) -> str:
        """获取系统提示词"""
        return """你是一个智能表单助手，专门帮助用户填写和管理表单。

核心能力：
1. 识别用户意图（请假、报销、调查问卷等）
2. 提取表单字段值
3. 校验数据合法性
4. 生成符合Schema的表单数据

工作流程：
1. 理解用户需求
2. 确定表单类型
3. 提取/验证字段
4. 返回结构化结果

注意：
- 只处理表单相关任务
- 涉及敏感操作时要求用户确认
- 返回结果必须符合Schema规范"""
    
    def _get_default_agents_md(self) -> str:
        """获取默认的 AGENTS.md 内容"""
        return """# AI Agent 行为规范

## 项目概述
本项目是 AI 驱动的动态表单框架，支持多种表单类型的智能识别和字段提取。

## 核心原则

### 1. 安全优先
- 禁止执行未经授权的操作
- 涉及敏感数据时提示用户确认
- 不返回可能被滥用的信息

### 2. 准确性
- 字段提取必须基于Schema定义
- 数值类型必须符合范围限制
- 必填字段必须全部填充

### 3. 可解释性
- 每个决策都要有依据
- 拒绝请求时要说明原因
- 建议要提供选项

## 表单类型编码

| 编码 | 类型 | 说明 |
|------|------|------|
| leave | 请假申请 | 包含请假类型、天数、原因等 |
| expense | 报销申请 | 包含金额、类别、发票等 |
| survey | 调查问卷 | 包含选择题、填空题等 |

## 错误处理

| 错误类型 | 处理方式 |
|----------|----------|
| Schema不匹配 | 返回具体字段错误 |
| 必填字段缺失 | 列出缺失字段 |
| 数值超范围 | 给出有效范围 |
"""
    
    def reload(self):
        """重新加载所有上下文"""
        self._contexts.clear()
        self._schemas.clear()
        self._prompt_templates.clear()
        self._load_contexts()
        logger.info("Contexts reloaded")

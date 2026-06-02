"""
模型导出模块

AI 原生架构：只使用 Ontology 和 OntologyInstance
Form 相关模型已完全废弃并删除
工具管理模块已迁移到 MCP 管理模块
"""
from app.models.ontology import Ontology
from app.models.ontology_instance import OntologyInstance, OntologyInstanceHistory
from app.models.scene import Scene
from app.models.prompt import Prompt, PromptVersion, PromptTemplate

# 推荐使用的模型（AI 原生架构）
__all__ = [
    "Ontology",              # 本体定义（Schema/Class）
    "OntologyInstance",      # 本体实例（Object/Record）
    "OntologyInstanceHistory", # 本体实例历史
    "Scene",
    "Prompt", "PromptVersion", "PromptTemplate",
]

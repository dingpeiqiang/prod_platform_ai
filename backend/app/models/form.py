"""
表单模型（已废弃 - 完全删除）

⚠️ DEPRECATED: 此模块已完全废弃并删除

设计理念变更：
- AI 原生架构中，没有"表单"概念，只有"本体"和"本体实例"
- Ontology = Schema/Class（定义数据结构和约束）
- OntologyInstance = Object/Record（存储实际数据）
- Forms 表已被删除，不再使用

迁移指南：
- Form → 不再需要，本体定义在 Ontology 表中
- FormInstance → 请使用 OntologyInstance（在 ontology_instance.py 中）
- FormHistory → 请使用 OntologyInstanceHistory（在 ontology_instance.py 中）

注意：此文件保留仅作为占位符，不应被导入或使用。
"""
# 此文件已废弃，请勿导入
# 如需使用本体实例，请从 app.models.ontology_instance 导入
raise ImportError(
    "app.models.form 已废弃！\n"
    "请使用：from app.models.ontology_instance import OntologyInstance, OntologyInstanceHistory"
)

#!/usr/bin/env python3
"""
资费备案初始化脚本

将本体和工作流配置导入到数据库
"""
import os
import yaml
import json

# 添加项目路径
import sys
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.ontology import Ontology
from app.models.workflow import Workflow
from app.core.database import Base, get_db


def init_tariff_filing():
    """初始化资费备案相关配置"""
    # 获取数据库会话
    db = next(get_db())
    
    try:
        # 导入本体配置
        import_ontology(db)
        
        # 导入工作流配置
        import_workflow(db)
        
        print("资费备案配置初始化完成！")
    finally:
        db.close()


def import_ontology(db):
    """导入资费备案本体"""
    ontology_path = os.path.join(os.path.dirname(__file__), "config/ontologies/tariff_filing.yaml")
    
    if not os.path.exists(ontology_path):
        print(f"本体配置文件不存在: {ontology_path}")
        return
    
    with open(ontology_path, 'r', encoding='utf-8') as f:
        ontology_data = yaml.safe_load(f)
    
    # 检查是否已存在
    existing = db.query(Ontology).filter(Ontology.ontology_code == ontology_data["ontologyCode"]).first()
    if existing:
        print(f"本体 {ontology_data['ontologyCode']} 已存在，跳过")
        return
    
    # 创建本体
    ontology = Ontology(
        ontology_code=ontology_data["ontologyCode"],
        ontology_name=ontology_data["ontologyName"],
        category=ontology_data.get("category", "general"),
        description=ontology_data.get("description"),
        entities=ontology_data.get("entities", []),
        version=1
    )
    db.add(ontology)
    db.commit()
    print(f"本体 {ontology_data['ontologyCode']} 导入成功")


def import_workflow(db):
    """导入资费备案工作流（已迁移至数据库管理）"""
    print("工作流配置已迁移至数据库管理，请通过前端编辑器管理工作流")


if __name__ == "__main__":
    init_tariff_filing()
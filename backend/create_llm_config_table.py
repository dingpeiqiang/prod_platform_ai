"""
手动创建 LLM 用户配置表
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

from app.core.database import engine
from app.models.llm_user_config import LLMUserConfig
from sqlalchemy import inspect

def create_table():
    """创建表"""
    print("检查 llm_user_configs 表是否存在...")
    
    inspector = inspect(engine)
    if 'llm_user_configs' in inspector.get_table_names():
        print("✓ 表已存在")
        return
    
    print("创建 llm_user_configs 表...")
    LLMUserConfig.__table__.create(engine)
    print("✓ 表创建成功")
    
    # 验证
    inspector = inspect(engine)
    if 'llm_user_configs' in inspector.get_table_names():
        print("✓ 验证成功：表已创建")
        
        # 显示列信息
        columns = inspector.get_columns('llm_user_configs')
        print(f"\n表结构 ({len(columns)} 个字段):")
        for col in columns:
            print(f"  - {col['name']}: {col['type']}")
    else:
        print("✗ 验证失败：表未创建")

if __name__ == "__main__":
    create_table()


"""
为 scenes 和 scene_history 表添加 config 字段的数据库更新脚本
"""

import sys
import os

# 添加项目根目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import text
from app.core.database import engine

def add_config_field():
    """添加 config 字段"""
    try:
        with engine.connect() as conn:
            trans = conn.begin()
            
            try:
                # 1. 为 scenes 表添加 config 字段
                print("Adding config field to scenes table...")
                add_config_to_scenes = text("""
                    ALTER TABLE scenes 
                    ADD COLUMN config JSON NOT NULL DEFAULT ('{}')
                """)
                conn.execute(add_config_to_scenes)
                print("config field added to scenes table successfully!")
                
                # 2. 为 scene_history 表添加 config 字段
                print("Adding config field to scene_history table...")
                add_config_to_history = text("""
                    ALTER TABLE scene_history 
                    ADD COLUMN config JSON NOT NULL DEFAULT ('{}')
                """)
                conn.execute(add_config_to_history)
                print("config field added to scene_history table successfully!")
                
                trans.commit()
                print("\nDatabase update completed!")
                
            except Exception as e:
                trans.rollback()
                print(f"\nError during database update: {e}")
                raise
                
    except Exception as e:
        print(f"Failed to connect to database: {e}")
        raise

if __name__ == "__main__":
    add_config_field()

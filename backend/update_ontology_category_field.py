import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '.')))

from app.core.database import SessionLocal, engine, Base
from sqlalchemy import text


def update_ontology_table():
    """更新 ontologies 表，添加 category 列"""
    db = SessionLocal()
    try:
        print("开始更新 ontologies 表...")
        
        # 检查 column 是否存在
        check_column_sql = text("""
            SELECT COUNT(*) 
            FROM information_schema.columns 
            WHERE table_name = 'ontologies' 
            AND column_name = 'category'
        """)
        
        result = db.execute(check_column_sql)
        column_exists = result.scalar() > 0
        
        if column_exists:
            print("category 列已存在，跳过添加")
        else:
            # 添加 category 列
            add_column_sql = text("""
                ALTER TABLE ontologies 
                ADD COLUMN category VARCHAR(100) DEFAULT 'general'
            """)
            db.execute(add_column_sql)
            print("成功添加 category 列")
        
        db.commit()
        print("数据库更新完成！")
        
    except Exception as e:
        print(f"更新失败: {e}")
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == "__main__":
    update_ontology_table()

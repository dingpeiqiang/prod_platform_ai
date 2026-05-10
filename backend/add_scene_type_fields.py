from sqlalchemy import create_engine, text
from app.core.config import get_settings

settings = get_settings()

# 创建数据库引擎
engine = create_engine(settings.DATABASE_URL)

try:
    with engine.connect() as connection:
        # 先检查 type 字段是否存在
        check_type_sql = text("SHOW COLUMNS FROM scenes LIKE 'type'")
        type_result = connection.execute(check_type_sql).fetchone()
        
        if not type_result:
            # 添加 type 字段
            print("正在添加 type 字段...")
            add_type_sql = text("ALTER TABLE scenes ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'scene'")
            connection.execute(add_type_sql)
            print("type 字段添加成功！")
        else:
            print("type 字段已存在，无需添加。")
        
        # 再检查 parent_id 字段是否存在
        check_parent_sql = text("SHOW COLUMNS FROM scenes LIKE 'parent_id'")
        parent_result = connection.execute(check_parent_sql).fetchone()
        
        if not parent_result:
            # 添加 parent_id 字段
            print("正在添加 parent_id 字段...")
            add_parent_sql = text("ALTER TABLE scenes ADD COLUMN parent_id INT NULL")
            connection.execute(add_parent_sql)
            print("parent_id 字段添加成功！")
        else:
            print("parent_id 字段已存在，无需添加。")
        
        # 添加外键约束（可选，避免循环引用问题）
        print("\n所有字段添加完成！")
        
        connection.commit()
            
except Exception as e:
    print(f"错误: {e}")

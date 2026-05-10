from sqlalchemy import create_engine, inspect, text
from app.core.config import get_settings

settings = get_settings()

# 创建数据库引擎
engine = create_engine(settings.DATABASE_URL)

try:
    with engine.connect() as conn:
        inspector = inspect(engine)
        columns = [col['name'] for col in inspector.get_columns('scenes')]
        
        # 检查并添加 type 列
        if 'type' not in columns:
            print("添加 type 列...")
            conn.execute(text("ALTER TABLE scenes ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'scene'"))
        
        # 检查并添加 parent_id 列
        if 'parent_id' not in columns:
            print("添加 parent_id 列...")
            conn.execute(text("ALTER TABLE scenes ADD COLUMN parent_id INTEGER"))
        
        # 删除旧的 domain 列
        for col in ['domain1', 'domain2', 'domain3']:
            if col in columns:
                print(f"删除 {col} 列...")
                # SQLite 不支持直接 DROP COLUMN，需要重建表
                print(f"注意：SQLite 不支持直接删除列，请手动更新表结构或忽略此警告")
        
        # 更新现有数据
        print("更新现有数据...")
        conn.execute(text("UPDATE scenes SET type = 'scene' WHERE type IS NULL OR type = ''"))
        conn.commit()
        
        print("数据库更新完成！")
        
except Exception as e:
    print(f"数据库更新失败: {e}")
    import traceback
    traceback.print_exc()

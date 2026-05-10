from sqlalchemy import create_engine, text
from app.core.config import get_settings
from app.models.scene import Scene

settings = get_settings()

# 创建数据库引擎
engine = create_engine(settings.DATABASE_URL)

try:
    with engine.connect() as connection:
        # 检查字段是否存在
        check_sql = text("SHOW COLUMNS FROM scenes LIKE 'domain1'")
        result = connection.execute(check_sql).fetchone()
        
        if not result:
            # 添加字段
            print("正在添加 domain1, domain2, domain3 字段...")
            sql1 = text("ALTER TABLE scenes ADD COLUMN domain1 VARCHAR(100) NULL")
            sql2 = text("ALTER TABLE scenes ADD COLUMN domain2 VARCHAR(100) NULL")
            sql3 = text("ALTER TABLE scenes ADD COLUMN domain3 VARCHAR(100) NULL")
            
            connection.execute(sql1)
            connection.execute(sql2)
            connection.execute(sql3)
            connection.commit()
            print("字段添加成功！")
        else:
            print("字段已存在，无需添加。")
            
except Exception as e:
    print(f"错误: {e}")

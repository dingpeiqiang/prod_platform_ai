from sqlalchemy import create_engine, text
from app.core.config import get_settings

settings = get_settings()

# 创建数据库引擎
engine = create_engine(settings.DATABASE_URL)

try:
    with engine.connect() as connection:
        # 检查表是否存在
        check_table_sql = text("SHOW TABLES LIKE 'scene_versions'")
        table_result = connection.execute(check_table_sql).fetchone()
        
        if not table_result:
            print("正在创建 scene_versions 表...")
            
            # 创建表的 SQL
            create_table_sql = text("""
                CREATE TABLE scene_versions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    scene_id INT NOT NULL,
                    scene_code VARCHAR(100) NOT NULL,
                    version INT NOT NULL,
                    scene_name VARCHAR(200) NOT NULL,
                    description TEXT,
                    keywords JSON NOT NULL,
                    priority INT DEFAULT 10,
                    is_active BOOLEAN DEFAULT TRUE,
                    form_code VARCHAR(100),
                    action_prompt_file VARCHAR(255),
                    type VARCHAR(20) NOT NULL DEFAULT 'scene',
                    parent_id INT,
                    change_note TEXT,
                    created_by VARCHAR(100),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_scene_id (scene_id),
                    INDEX idx_scene_code (scene_code),
                    INDEX idx_version (version),
                    FOREIGN KEY (scene_id) REFERENCES scenes(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
            
            connection.execute(create_table_sql)
            connection.commit()
            print("scene_versions 表创建成功！")
        else:
            print("scene_versions 表已存在，无需创建。")
            
except Exception as e:
    print(f"错误: {e}")

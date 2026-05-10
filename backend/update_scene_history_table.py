from sqlalchemy import create_engine, text
from app.core.config import get_settings

settings = get_settings()

# 创建数据库引擎
engine = create_engine(settings.DATABASE_URL)

try:
    with engine.connect() as connection:
        # 检查旧表是否存在，如果存在则删除
        check_old_table = text("SHOW TABLES LIKE 'scene_versions'")
        old_table_exists = connection.execute(check_old_table).fetchone()
        
        if old_table_exists:
            print("Dropping old scene_versions table...")
            drop_old_table = text("DROP TABLE IF EXISTS scene_versions")
            connection.execute(drop_old_table)
            connection.commit()
            print("Old table dropped successfully!")
        
        # 检查新表是否存在
        check_new_table = text("SHOW TABLES LIKE 'scene_history'")
        new_table_exists = connection.execute(check_new_table).fetchone()
        
        if not new_table_exists:
            print("Creating scene_history table...")
            
            # 创建新表的 SQL
            create_table_sql = text("""
                CREATE TABLE scene_history (
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
            print("scene_history table created successfully!")
        else:
            print("scene_history table already exists, skipping creation.")
            
        print("\nDatabase update completed!")
            
except Exception as e:
    print(f"Error: {e}")

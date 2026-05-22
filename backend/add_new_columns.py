"""
为 llm_user_configs 表添加新字段以支持通用 API 配置
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from sqlalchemy import text
from app.core.database import engine

def add_new_columns():
    """添加新字段到数据库"""
    print("=" * 60)
    print("添加新字段到数据库")
    print("=" * 60)
    
    columns = [
        {
            'name': 'auth_header',
            'definition': "VARCHAR(50) NULL COMMENT '自定义认证头名称'",
            'default': None
        },
        {
            'name': 'api_format',
            'definition': "VARCHAR(50) NOT NULL DEFAULT 'openai' COMMENT 'API 格式: openai, anthropic, gemini'",
            'default': 'openai'
        },
        {
            'name': 'is_full_url',
            'definition': "BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否使用完整 URL'",
            'default': False
        }
    ]
    
    try:
        with engine.connect() as conn:
            for col in columns:
                # 检查字段是否已存在
                result = conn.execute(text("""
                    SELECT COUNT(*) 
                    FROM information_schema.columns 
                    WHERE table_schema = 'aicp' 
                      AND table_name = 'llm_user_configs' 
                      AND column_name = :col_name
                """), {'col_name': col['name']})
                exists = result.scalar()
                
                if exists:
                    print(f"⚠️  {col['name']} 字段已存在，跳过添加")
                else:
                    print(f"添加 {col['name']} 字段...")
                    conn.execute(text(f"""
                        ALTER TABLE llm_user_configs 
                        ADD COLUMN {col['name']} {col['definition']}
                    """))
                    conn.commit()
                    print(f"✅ {col['name']} 字段添加成功！")
        
        print("\n✅ 所有字段添加完成！")
        
    except Exception as e:
        print(f"❌ 添加字段失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    add_new_columns()
    print("=" * 60)

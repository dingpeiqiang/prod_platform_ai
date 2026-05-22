"""
为 llm_user_configs 表添加 auth_type 字段
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from sqlalchemy import text
from app.core.database import SessionLocal, engine

def add_auth_type_column():
    """添加 auth_type 字段到数据库"""
    print("=" * 60)
    print("添加 auth_type 字段到数据库")
    print("=" * 60)
    
    try:
        # 直接执行 SQL 添加字段
        with engine.connect() as conn:
            # 检查字段是否已存在
            result = conn.execute(text("""
                SELECT COUNT(*) 
                FROM information_schema.columns 
                WHERE table_schema = 'aicp' 
                  AND table_name = 'llm_user_configs' 
                  AND column_name = 'auth_type'
            """))
            exists = result.scalar()
            
            if exists:
                print("⚠️  auth_type 字段已存在，跳过添加")
            else:
                print("添加 auth_type 字段...")
                conn.execute(text("""
                    ALTER TABLE llm_user_configs 
                    ADD COLUMN auth_type VARCHAR(20) NOT NULL DEFAULT 'bearer' COMMENT '认证类型: bearer, token, api_key'
                """))
                conn.commit()
                print("✅ 字段添加成功！")
        
        # 更新现有记录的 auth_type（对于公司内部的 minimax 配置）
        db = SessionLocal()
        try:
            db.execute(text("""
                UPDATE llm_user_configs 
                SET auth_type = 'token' 
                WHERE base_url LIKE '%aicp.teamshub.com%'
            """))
            db.commit()
            print("✅ 更新了相关记录的认证类型为 token")
        finally:
            db.close()
            
    except Exception as e:
        print(f"❌ 添加字段失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    add_auth_type_column()
    print("=" * 60)

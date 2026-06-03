"""
迁移脚本：更新 request_method 字段大小
"""
import os
import sys
from sqlalchemy import create_engine, text

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.config import get_settings

def update_request_method_size():
    """更新 request_method 字段大小"""
    settings = get_settings()
    engine = create_engine(settings.DATABASE_URL)
    
    with engine.connect() as conn:
        try:
            print("开始更新 request_method 字段大小...")
            
            # 修改字段大小
            conn.execute(text("""
                ALTER TABLE mcp_tool_definitions 
                MODIFY COLUMN request_method VARCHAR(16) DEFAULT 'POST' COMMENT '请求方法'
            """))
            
            conn.commit()
            print("request_method 字段大小更新成功！")
            
        except Exception as e:
            conn.rollback()
            print(f"更新失败: {e}")
            raise

if __name__ == "__main__":
    update_request_method_size()
"""
迁移脚本：为 mcp_tool_definitions 表添加外部工具配置字段
"""
import os
import sys
from sqlalchemy import create_engine, text

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.config import get_settings

def add_external_tool_fields():
    """添加外部工具配置字段"""
    # 获取配置
    settings = get_settings()
    # 创建数据库连接
    engine = create_engine(settings.DATABASE_URL)
    
    with engine.connect() as conn:
        try:
            # 添加新字段
            print("开始添加外部工具配置字段...")
            
            # 查询现有字段
            result = conn.execute(text("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mcp_tool_definitions'
            """))
            existing_columns = {row[0] for row in result}
            
            # 定义要添加的字段
            columns_to_add = [
                ("tool_type", "VARCHAR(20)", "DEFAULT 'url'", "工具类型"),
                ("protocol", "VARCHAR(10)", "DEFAULT 'http'", "接口协议"),
                ("request_method", "VARCHAR(10)", "DEFAULT 'POST'", "请求方法"),
                ("url", "VARCHAR(500)", "", "接口地址"),
                ("auth_type", "VARCHAR(20)", "DEFAULT 'none'", "鉴权类型"),
                ("auth_info", "TEXT", "", "鉴权信息"),
                ("need_summary", "BOOLEAN", "DEFAULT FALSE", "是否需要归纳总结"),
                ("prompt", "TEXT", "", "工具提示词"),
            ]
            
            # 逐个添加字段
            for col_name, col_type, col_default, col_comment in columns_to_add:
                if col_name not in existing_columns:
                    default_clause = f" {col_default}" if col_default else ""
                    comment_clause = f" COMMENT '{col_comment}'" if col_comment else ""
                    sql = f"ALTER TABLE mcp_tool_definitions ADD COLUMN {col_name} {col_type}{default_clause}{comment_clause}"
                    conn.execute(text(sql))
                    print(f"已添加字段: {col_name}")
                else:
                    print(f"字段 {col_name} 已存在，跳过")
            
            conn.commit()
            print("所有字段添加成功！")
            
        except Exception as e:
            conn.rollback()
            print(f"添加字段失败: {e}")
            raise

if __name__ == "__main__":
    add_external_tool_fields()
"""直接删除数据库中的废弃字段"""
import sys
from app.core.database import engine
from sqlalchemy import text

def remove_deprecated_fields():
    """直接从数据库中删除 form_id 和 template_id 字段"""
    print("=" * 60)
    print("删除废弃字段: form_id, template_id")
    print("=" * 60)
    
    with engine.connect() as conn:
        try:
            # 检查字段是否存在
            result = conn.execute(text("""
                SELECT COLUMN_NAME 
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_NAME = 'form_instances' 
                AND COLUMN_NAME IN ('form_id', 'template_id')
            """))
            
            existing_fields = [row[0] for row in result.fetchall()]
            print(f"当前存在的废弃字段: {existing_fields}")
            
            if not existing_fields:
                print("✅ 没有需要删除的字段")
                return True
            
            # 删除 form_id
            if 'form_id' in existing_fields:
                print("正在删除 form_id 字段...")
                conn.execute(text("ALTER TABLE form_instances DROP COLUMN form_id"))
                conn.commit()
                print("✅ form_id 已删除")
            
            # 删除 template_id
            if 'template_id' in existing_fields:
                print("正在删除 template_id 字段...")
                conn.execute(text("ALTER TABLE form_instances DROP COLUMN template_id"))
                conn.commit()
                print("✅ template_id 已删除")
            
            # 验证删除结果
            result = conn.execute(text("""
                SELECT COLUMN_NAME 
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_NAME = 'form_instances' 
                ORDER BY ORDINAL_POSITION
            """))
            
            remaining_fields = [row[0] for row in result.fetchall()]
            print(f"\n删除后的字段列表:")
            for field in remaining_fields:
                print(f"  - {field}")
            
            print("\n✅ 所有废弃字段已成功删除")
            return True
            
        except Exception as e:
            print(f"❌ 删除失败: {e}")
            import traceback
            traceback.print_exc()
            return False

if __name__ == "__main__":
    success = remove_deprecated_fields()
    sys.exit(0 if success else 1)

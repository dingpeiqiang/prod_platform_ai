"""测试表单提交功能"""
import asyncio
import sys
from pathlib import Path

# 添加 backend 目录到路径
backend_dir = Path(__file__).parent / "backend"
sys.path.insert(0, str(backend_dir))

from sqlalchemy.orm import Session
from app.core.database import SessionLocal, engine
from app.models.form import FormInstance
from datetime import datetime


def test_form_instance_creation():
    """测试创建 FormInstance"""
    print("=" * 60)
    print("测试: 创建 FormInstance")
    print("=" * 60)
    
    db: Session = SessionLocal()
    
    try:
        # 创建一个测试实例
        test_instance = FormInstance(
            form_code="test_form",
            data={"field1": "value1", "field2": "value2"},
            status="submitted",
            user_id="test_user",
            submitted_at=datetime.now()
        )
        
        db.add(test_instance)
        db.commit()
        db.refresh(test_instance)
        
        print(f"✅ 成功创建 FormInstance")
        print(f"   ID: {test_instance.id}")
        print(f"   form_code: {test_instance.form_code}")
        print(f"   status: {test_instance.status}")
        print(f"   data: {test_instance.data}")
        
        # 清理测试数据
        db.delete(test_instance)
        db.commit()
        print(f"✅ 测试数据已清理")
        
        return True
        
    except Exception as e:
        print(f"❌ 创建失败: {e}")
        import traceback
        traceback.print_exc()
        db.rollback()
        return False
        
    finally:
        db.close()


if __name__ == "__main__":
    success = test_form_instance_creation()
    sys.exit(0 if success else 1)

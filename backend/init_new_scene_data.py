
"""
初始化新的场景数据 - 产商品中心 - 资费备案场景
"""

import sys
import os

# 添加项目根目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import text
from app.core.database import engine, SessionLocal
from app.models.scene import Scene


def init_new_scene_data():
    """初始化新的场景数据"""
    print("=" * 60)
    print("开始初始化新场景数据")
    print("=" * 60)

    db = SessionLocal()
    try:
        # 步骤1: 清空现有场景数据
        print("\n[1/4] 清空现有场景数据...")
        db.query(Scene).delete()
        db.commit()
        print("现有场景数据已清空")

        # 步骤2: 创建中心域 - 产商品中心
        print("\n[2/4] 创建中心域...")
        center = Scene(
            scene_code="product_center",
            scene_name="产商品中心",
            description="负责管理各类产商品的中心部门",
            keywords=["产商品", "产品", "商品", "中心"],
            priority=10,
            is_active=True,
            intent_type="center",
            type="center",
            parent_id=None,
            config={
                "department": "产商品中心",
                "owner": "产商品管理部",
                "level": "一级部门"
            },
            version=1,
            created_by="system"
        )
        db.add(center)
        db.flush()  # 获取ID
        print(f"✓ 中心域创建成功: {center.scene_name} (ID: {center.id})")

        # 步骤3: 创建业务域 - 资费备案
        print("\n[3/4] 创建业务域...")
        business = Scene(
            scene_code="tariff_filing",
            scene_name="资费备案",
            description="负责资费备案相关业务",
            keywords=["资费", "备案", "价格"],
            priority=9,
            is_active=True,
            intent_type="business",
            type="business",
            parent_id=center.id,
            config={
                "business_line": "产商品",
                "business_scope": ["资费备案", "价格管理"],
                "manager": "资费管理部"
            },
            version=1,
            created_by="system"
        )
        db.add(business)
        db.flush()  # 获取ID
        print(f"✓ 业务域创建成功: {business.scene_name} (ID: {business.id})")

        # 步骤4: 创建三个具体场景
        print("\n[4/4] 创建具体场景...")
        scenes_data = [
            {
                "scene_code": "tariff_filing_apply",
                "scene_name": "资费备案申请",
                "description": "新资费的备案申请流程",
                "keywords": ["资费备案", "申请", "新资费"],
                "priority": 8,
                "is_active": True,
                "intent_type": "tariff_filing",
                "form_code": "tariff_filing_publicity",
                "type": "scene",
                "parent_id": business.id,
                "config": {
                    "workflow": ["提交申请", "初审", "复核", "公示"],
                    "auto_approve": False,
                    "sla_hours": 48
                }
            },
            {
                "scene_code": "tariff_filing_change",
                "scene_name": "资费备案变更",
                "description": "对已有资费进行变更备案",
                "keywords": ["资费变更", "备案变更", "修改资费"],
                "priority": 7,
                "is_active": True,
                "intent_type": "tariff_filing",
                "form_code": "tariff_filing_publicity",
                "type": "scene",
                "parent_id": business.id,
                "config": {
                    "workflow": ["提交变更", "审核", "变更生效"],
                    "requires_approval": True,
                    "sla_hours": 24
                }
            },
            {
                "scene_code": "tariff_filing_query",
                "scene_name": "资费备案查询",
                "description": "查询已备案的资费信息",
                "keywords": ["资费查询", "备案查询", "查看"],
                "priority": 6,
                "is_active": True,
                "intent_type": "query",
                "form_code": None,
                "type": "scene",
                "parent_id": business.id,
                "config": {
                    "query_type": ["按资费", "按时间", "按状态"],
                    "export_enabled": True
                }
            }
        ]

        for scene_data in scenes_data:
            scene = Scene(
                scene_code=scene_data["scene_code"],
                scene_name=scene_data["scene_name"],
                description=scene_data["description"],
                keywords=scene_data["keywords"],
                priority=scene_data["priority"],
                is_active=scene_data["is_active"],
                intent_type=scene_data["intent_type"],
                form_code=scene_data["form_code"],
                type=scene_data["type"],
                parent_id=scene_data["parent_id"],
                config=scene_data["config"],
                version=1,
                created_by="system"
            )
            db.add(scene)
            db.flush()
            print(f"✓ 场景创建成功: {scene.scene_name} (ID: {scene.id})")

        # 提交所有修改
        db.commit()
        print("\n" + "=" * 60)
        print("🎉 新场景数据初始化完成!")
        print("=" * 60)

        # 显示最终数据
        print("\n最终场景结构:")
        all_scenes = db.query(Scene).order_by(Scene.id).all()
        for s in all_scenes:
            indent = "  " if s.type == "business" else "    " if s.type == "scene" else ""
            print(f"{indent}[{s.id}] {s.scene_code} - {s.scene_name} ({s.type})")
            if s.config:
                print(f"{indent}  配置: {s.config}")

        return True

    except Exception as e:
        db.rollback()
        print(f"\n❌ 初始化失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        db.close()


if __name__ == "__main__":
    init_new_scene_data()

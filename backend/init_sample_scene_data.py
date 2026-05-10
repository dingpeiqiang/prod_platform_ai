
"""
验证数据库验证和场景示例数据初始化脚本
1. 检查数据库字段已更新
2. 插入示例数据（中心域、业务域、场景）
"""

import sys
import os
from datetime import datetime

# 添加项目根目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import text
from app.core.database import engine, SessionLocal
from app.models.scene import Scene

def verify_database_fields():
    """验证数据库字段"""
    print("=" * 60)
    print("检查数据库表结构...")
    print("=" * 60)

    try:
        with engine.connect() as conn:
            # 检查 scenes 表
            print("\n[1] 检查 scenes 表:")
            result = conn.execute(text("DESCRIBE scenes"))
            columns = result.fetchall()
            print("字段列表:")
            for col in columns:
                print(f"  - {col[0]} ({col[1]})")
            has_config = any(col[0] == 'config' for col in columns)
            print(f"\n✅ config 字段存在: {has_config}")

            # 检查 scene_history 表
            print("\n[2] 检查 scene_history 表:")
            result = conn.execute(text("DESCRIBE scene_history"))
            columns = result.fetchall()
            print("字段列表:")
            for col in columns:
                print(f"  - {col[0]} ({col[1]})")
            has_config = any(col[0] == 'config' for col in columns)
            print(f"\n✅ config 字段存在: {has_config}")

        print("\n" + "=" * 60)
        print("数据库字段验证完成！")
        print("=" * 60)
        return True

    except Exception as e:
        print(f"\n❌ 验证失败: {e}")
        return False

def insert_sample_data():
    """插入示例数据"""
    print("\n" + "=" * 60)
    print("开始插入示例数据...")
    print("=" * 60)

    db = SessionLocal()

    try:
        # 先清空现有数据（可选）
        print("\n正在清空现有场景数据...")
        db.query(Scene).delete()
        db.commit()

        # 创建示例数据
        sample_data = [
            # ===== 中心域 =====
            {
                "scene_code": "center_tech",
                "scene_name": "技术中心",
                "description": "技术研发与技术服务中心",
                "type": "center",
                "keywords": ["技术", "研发"],
                "priority": 10,
                "parent_id": None,
                "config": {
                    "department": "技术中心",
                    "owner": "CTO办公室",
                    "contact": "tech@company.com",
                    "level": "一级部门",
                    "is_core": True
                }
            },
            # ===== 业务域 =====
            {
                "scene_code": "business_retail",
                "scene_name": "零售业务",
                "description": "零售业务域",
                "type": "business",
                "keywords": ["零售", "电商"],
                "priority": 8,
                "parent_id": 1,
                "config": {
                    "business_line": "零售",
                    "region": "华东",
                    "manager": "张经理",
                    "business_scope": ["线上", "线下"]
                }
            },
            {
                "scene_code": "business_finance",
                "scene_name": "金融业务",
                "description": "金融业务域",
                "type": "business",
                "keywords": ["金融", "支付"],
                "priority": 9,
                "parent_id": 1,
                "config": {
                    "business_line": "金融",
                    "region": "全国",
                    "manager": "李总监",
                    "is_licensed": True
                }
            },
            # ===== 场景 =====
            {
                "scene_code": "scene_order",
                "scene_name": "订单处理",
                "description": "订单处理和查询场景",
                "type": "scene",
                "keywords": ["订单", "查询"],
                "priority": 7,
                "parent_id": 2,
                "form_code": "tariff_filing_publicity",
                "config": {
                    "workflow": ["接单", "审核", "处理"],
                    "sla_hours": 24,
                    "auto_approve": False
                }
            },
            {
                "scene_code": "scene_payment",
                "scene_name": "支付处理",
                "description": "支付处理与结算场景",
                "type": "scene",
                "keywords": ["支付", "结算"],
                "priority": 10,
                "parent_id": 3,
                "form_code": "external_api_demo",
                "config": {
                    "payment_methods": ["微信", "支付宝", "银联"],
                    "risk_level": "high",
                    "auto_retry": True,
                    "max_retry": 3
                }
            },
            {
                "scene_code": "scene_complaint",
                "scene_name": "投诉处理",
                "description": "客户投诉处理场景",
                "type": "scene",
                "keywords": ["投诉", "处理"],
                "priority": 8,
                "parent_id": 2,
                "config": {
                    "escalation_hours": 4,
                    "priority_routing": True
                }
            }
        ]

        print("\n正在插入示例场景...")

        # 分批次插入数据，处理 parent_id 关联关系
        # 第一层：中心域
        center_data = sample_data[0]
        center = Scene(
            scene_code=center_data["scene_code"],
            scene_name=center_data["scene_name"],
            description=center_data["description"],
            keywords=center_data["keywords"],
            priority=center_data["priority"],
            type=center_data["type"],
            config=center_data["config"],
            is_active=True,
            version=1,
            created_by="system"
        )
        db.add(center)
        db.flush()  # 获得 id

        print(f"  ✓ 插入中心域: {center.scene_name} (ID: {center.id})")

        # 第二层：业务域（ parent_id = 中心 id
        business_map = {}
        for business_data in sample_data[1:3]:
            business = Scene(
                scene_code=business_data["scene_code"],
                scene_name=business_data["scene_name"],
                description=business_data["description"],
                keywords=business_data["keywords"],
                priority=business_data["priority"],
                type=business_data["type"],
                parent_id=center.id,
                config=business_data["config"],
                is_active=True,
                version=1,
                created_by="system"
            )
            db.add(business)
            db.flush()
            business_map[business_data["scene_code"]] = business
            print(f"  ✓ 插入业务域: {business.scene_name} (ID: {business.id})")

        # 第三层：场景
        for scene_data in sample_data[3:]:
            parent_business_code = "business_retail" if scene_data["scene_code"] in ["scene_order", "scene_complaint"] else "business_finance"
            scene = Scene(
                scene_code=scene_data["scene_code"],
                scene_name=scene_data["scene_name"],
                description=scene_data["description"],
                keywords=scene_data["keywords"],
                priority=scene_data["priority"],
                type=scene_data["type"],
                parent_id=business_map[parent_business_code].id,
                form_code=scene_data.get("form_code"),
                config=scene_data["config"],
                is_active=True,
                version=1,
                created_by="system"
            )
            db.add(scene)
            db.flush()
            print(f"  ✓ 插入场景: {scene.scene_name} (ID: {scene.id})")

        db.commit()
        print("\n✅ 示例数据插入成功！")

        # 查询并显示数据
        print("\n" + "=" * 60)
        print("已插入的数据:")
        print("=" * 60)
        all_scenes = db.query(Scene).order_by(Scene.id).all()
        for scene in all_scenes:
            indent = "  " if scene.type == "business" else "    " if scene.type == "scene" else ""
            print(f"{indent}[{scene.id}] {scene.scene_code} - {scene.scene_name} ({scene.type})")
            if scene.config:
                print(f"{indent}  配置: {scene.config}")

        db.close()
        return True

    except Exception as e:
        db.rollback()
        print(f"\n❌ 插入失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        db.close()

if __name__ == "__main__":
    print("\n" + "=" * 60)
    print("场景数据初始化脚本")
    print("=" * 60)

    # 1. 验证数据库
    verify_database_fields()

    # 2. 插入示例数据
    success = insert_sample_data()

    if success:
        print("\n" + "=" * 60)
        print("🎉 初始化全部完成！")
        print("=" * 60)
    else:
        print("\n" + "=" * 60)
        print("❌ 初始化失败！")
        print("=" * 60)
        sys.exit(1)

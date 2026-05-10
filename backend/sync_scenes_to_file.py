
"""
将数据库中的所有场景同步到配置文件
"""

import sys
import os
import json
from pathlib import Path

# 添加项目根目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.database import SessionLocal
from app.models.scene import Scene


def sync_scenes_to_file():
    """同步所有场景到配置文件"""
    print("=" * 60)
    print("同步场景数据到配置文件")
    print("=" * 60)

    db = SessionLocal()
    try:
        # 获取所有场景
        all_scenes = db.query(Scene).order_by(Scene.id).all()
        print(f"\n找到 {len(all_scenes)} 个场景")

        # 构建配置数据
        scene_mappings = []
        for scene in all_scenes:
            scene_data = {
                "sceneCode": scene.scene_code,
                "sceneName": scene.scene_name,
                "description": scene.description,
                "keywords": scene.keywords,
                "priority": scene.priority,
                "isActive": scene.is_active,
                "intentType": scene.intent_type,
                "formCode": scene.form_code,
                "actionType": scene.action_type,
                "actionPrompt": scene.action_prompt_file,
                "type": scene.type,
                "parentId": scene.parent_id,
                "config": scene.config
            }
            scene_mappings.append(scene_data)

        data = {
            "sceneMappings": scene_mappings,
            "version": "4.0"
        }

        # 写入文件
        scenes_dir = Path(__file__).parent / "config" / "scenes"
        scenes_dir.mkdir(parents=True, exist_ok=True)
        file_path = scenes_dir / "scene_mapping.json"

        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        print(f"\n✓ 成功写入 {len(scene_mappings)} 个场景到:")
        print(f"  {file_path}")

        print("\n最终场景结构:")
        for scene in all_scenes:
            indent = "  " if scene.type == "business" else "    " if scene.type == "scene" else ""
            print(f"{indent}[{scene.id}] {scene.scene_code} - {scene.scene_name} ({scene.type})")

        print("\n" + "=" * 60)
        print("🎉 同步完成!")
        print("=" * 60)
        return True

    except Exception as e:
        print(f"\n❌ 同步失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        db.close()


if __name__ == "__main__":
    sync_scenes_to_file()

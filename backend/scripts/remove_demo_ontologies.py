"""
删除演示本体：validation_demo 和 external_api_demo
"""
import sys
project_root = __import__('pathlib').Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from app.core.database import SessionLocal
from app.models.ontology import Ontology
from app.services.admin_service import AdminService

db = SessionLocal()
try:
    # 从数据库删除
    for code in ['validation_demo', 'external_api_demo']:
        ontology = db.query(Ontology).filter(Ontology.ontology_code == code).first()
        if ontology:
            db.delete(ontology)
            print(f"从数据库删除本体: {code}")
    
    db.commit()
    
    # 从文件系统删除
    from pathlib import Path
    ontologies_dir = project_root / "config" / "ontologies"
    for code in ['validation_demo', 'external_api_demo']:
        file_path = ontologies_dir / f"{code}.json"
        if file_path.exists():
            file_path.unlink()
            print(f"从文件系统删除: {file_path}")
    
    # 从场景映射中移除
    AdminService._remove_from_scene_mappings('validation_demo')
    AdminService._remove_from_scene_mappings('external_api_demo')
    
    # 热重载配置
    from app.core.config_loader import config_loader
    config_loader.reload_config("ontologies")
    config_loader.reload_config("scene_mappings")
    
    print("\n删除完成！")
    
except Exception as e:
    db.rollback()
    print(f"删除失败: {e}")
finally:
    db.close()
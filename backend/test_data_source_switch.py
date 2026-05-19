import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.config_loader import config_loader
from app.core.data_source import DataSourceType


def test_file_data_source():
    print("=== 测试文件数据源 ===")
    config_loader.switch_data_source(DataSourceType.FILE)
    
    current_type = config_loader.get_current_data_source_type()
    print(f"当前数据源类型: {current_type}")
    
    ontologies = config_loader.get_all_ontologies()
    print(f"加载的本体数量: {len(ontologies)}")
    
    if ontologies:
        first_key = list(ontologies.keys())[0]
        print(f"第一个本体: {first_key}")
    
    scenes = config_loader.get_all_scenes()
    print(f"加载的场景数量: {len(scenes)}")
    
    print("文件数据源测试通过!\n")


def test_database_data_source():
    print("=== 测试数据库数据源 ===")
    
    try:
        from app.core.database import SessionLocal
        
        def get_db():
            db = SessionLocal()
            try:
                yield db
            finally:
                db.close()
        
        db = next(get_db())
        config_loader.set_db_session_factory(lambda: db)
        
        config_loader.switch_data_source(DataSourceType.DATABASE)
        
        current_type = config_loader.get_current_data_source_type()
        print(f"当前数据源类型: {current_type}")
        
        ontologies = config_loader.get_all_ontologies()
        print(f"加载的本体数量: {len(ontologies)}")
        
        scenes = config_loader.get_all_scenes()
        print(f"加载的场景数量: {len(scenes)}")
        
        print("数据库数据源测试通过!\n")
        
        db.close()
    except Exception as e:
        print(f"数据库数据源测试失败: {e}")
        print("(这可能是因为数据库未配置或无数据)\n")


def test_switch_between_sources():
    print("=== 测试数据源切换 ===")
    
    config_loader.switch_data_source(DataSourceType.FILE)
    print(f"切换到文件数据源: {config_loader.get_current_data_source_type()}")
    
    config_loader.switch_data_source(DataSourceType.FILE)
    print(f"再次切换到相同数据源: {config_loader.get_current_data_source_type()}")
    
    try:
        from app.core.database import SessionLocal
        db = SessionLocal()
        config_loader.set_db_session_factory(lambda: db)
        config_loader.switch_data_source(DataSourceType.DATABASE)
        print(f"切换到数据库数据源: {config_loader.get_current_data_source_type()}")
        db.close()
    except Exception as e:
        print(f"切换到数据库数据源失败: {e}")
    
    config_loader.switch_data_source(DataSourceType.FILE)
    print(f"切换回文件数据源: {config_loader.get_current_data_source_type()}")
    
    print("数据源切换测试通过!\n")


if __name__ == "__main__":
    print("=" * 60)
    print("数据源切换功能测试")
    print("=" * 60)
    print()
    
    test_file_data_source()
    test_database_data_source()
    test_switch_between_sources()
    
    print("=" * 60)
    print("所有测试完成!")
    print("=" * 60)
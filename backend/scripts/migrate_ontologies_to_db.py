"""
将文件系统中的本体配置迁移到数据库

使用方法：
1. 确保数据库已正确配置
2. 运行命令：python -m scripts.migrate_ontologies_to_db
"""
import json
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("migrate_ontologies")


def migrate_ontologies():
    # 添加项目路径到 sys.path
    import sys
    project_root = Path(__file__).parent.parent
    sys.path.insert(0, str(project_root))
    
    from app.core.database import SessionLocal, engine, Base
    
    # 确保表存在
    Base.metadata.create_all(bind=engine)
    
    # 遍历文件系统中的本体
    config_path = project_root / "config" / "ontologies"
    if not config_path.exists():
        logger.error(f"配置目录不存在: {config_path}")
        return
    
    migrated_count = 0
    skipped_count = 0
    
    db = SessionLocal()
    try:
        from app.models.ontology import Ontology
        
        for file in config_path.glob("*.json"):
            try:
                with open(file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                form_code = data.get('formCode')
                if not form_code:
                    logger.warning(f"跳过无 formCode 的文件: {file.name}")
                    skipped_count += 1
                    continue
                
                # 检查是否已存在
                existing = db.query(Ontology).filter(
                    Ontology.ontology_code == form_code
                ).first()
                
                if existing:
                    # 更新现有记录
                    existing.ontology_name = data.get('formName', form_code)
                    existing.form_name = data.get('formName')
                    existing.description = data.get('description')
                    existing.entities = data.get('entities', [])
                    existing.version = (existing.version or 0) + 1
                    logger.info(f"更新本体: {form_code} (版本: {existing.version})")
                else:
                    # 创建新记录
                    ontology = Ontology(
                        ontology_code=form_code,
                        ontology_name=data.get('formName', form_code),
                        form_code=form_code,
                        form_name=data.get('formName'),
                        description=data.get('description'),
                        entities=data.get('entities', []),
                        version=1,
                        is_active=True
                    )
                    db.add(ontology)
                    logger.info(f"创建本体: {form_code}")
                
                migrated_count += 1
                
            except json.JSONDecodeError as e:
                logger.error(f"JSON 解析失败 {file.name}: {e}")
                skipped_count += 1
            except Exception as e:
                logger.error(f"迁移失败 {file.name}: {e}")
                skipped_count += 1
        
        db.commit()
        logger.info(f"\n迁移完成！")
        logger.info(f"成功迁移: {migrated_count} 个本体")
        logger.info(f"跳过: {skipped_count} 个文件")
        
    except Exception as e:
        db.rollback()
        logger.error(f"迁移过程出错: {e}")
    finally:
        db.close()


if __name__ == "__main__":
    migrate_ontologies()
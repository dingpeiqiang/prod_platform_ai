"""
重置 ontologies 表
"""
import sys
project_root = __import__('pathlib').Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from sqlalchemy import text
from app.core.database import engine, Base
from app.models.ontology import Ontology

# 删除旧表
with engine.connect() as conn:
    conn.execute(text('DROP TABLE IF EXISTS ontologies'))
    conn.commit()
    print("旧表已删除")

# 创建新表
Base.metadata.create_all(bind=engine)
print("新表已创建")
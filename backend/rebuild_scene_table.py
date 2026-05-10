from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from app.core.config import get_settings
from app.models.scene import Scene
import os
import shutil
from datetime import datetime

settings = get_settings()

# 备份现有数据库
if os.path.exists('./form.db'):
    backup_name = f'./form_backup_{datetime.now().strftime("%Y%m%d_%H%M%S")}.db'
    shutil.copy('./form.db', backup_name)
    print(f"已备份数据库到: {backup_name}")

# 删除旧数据库
if os.path.exists('./form.db'):
    os.remove('./form.db')

# 重新创建表
from app.core.database import Base, engine
Base.metadata.create_all(bind=engine)
print("数据库表结构重建完成！")

print("\n现在可以重新启动后端服务！")

from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from .config import get_settings
from .config_loader import config_loader

settings = get_settings()

# 从配置文件读取 SQL 日志设置
sql_logging_config = config_loader.get_app_config().get('logging', {}).get('sql', {})
sql_echo = sql_logging_config.get('enableLogging', False)

# 创建数据库引擎，支持 MySQL
engine = create_engine(
    settings.DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=3600,
    echo=sql_echo
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

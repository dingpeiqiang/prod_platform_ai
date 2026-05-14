from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    APP_NAME: str = "AI驱动动态表单底层框架"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = True
    
    # MySQL 数据库配置
    DATABASE_URL: str = "mysql+pymysql://root:password@localhost:3306/form_db?charset=utf8mb4"
    REDIS_URL: str = "redis://localhost:6379/0"
    NEO4J_URL: str = "bolt://localhost:7687"
    NEO4J_USER: str = "neo4j"
    NEO4J_PASSWORD: str = "password"
    
    LLM_API_KEY: str = ""
    LLM_BASE_URL: str = ""
    LLM_MODEL: str = "gpt-4"
    
    WEBSOCKET_HOST: str = "0.0.0.0"
    WEBSOCKET_PORT: int = 8000
    
    class Config:
        env_file = ".env"


@lru_cache()
def get_settings():
    return Settings()

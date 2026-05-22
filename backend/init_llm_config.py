"""
初始化 LLM 配置到数据库（通用配置）
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s.%(msecs)03d [%(levelname)-8s] %(message)s',
    datefmt='%H:%M:%S'
)

from app.core.database import SessionLocal
from app.models.llm_user_config import LLMUserConfig

def init_minimax_config():
    """初始化公司内部部署的 MiniMax 配置（通用配置）"""
    db = SessionLocal()
    
    try:
        # 用户确认的正确配置
        user_identifier = "default-user"
        model_name = "minimax-m2.7"
        api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
        base_url = "https://aicp.teamshub.com/openai/api/v1/openai/v1"
        
        # 通用配置
        auth_type = "token"          # 使用 token 请求头认证
        auth_header = None           # 自定义认证头名称（None 表示使用默认）
        api_format = "openai"        # API 格式
        is_full_url = False          # 是否使用完整 URL
        
        # 检查是否已存在配置
        existing_config = db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == user_identifier,
            LLMUserConfig.model == model_name
        ).first()
        
        if existing_config:
            print(f"⚠️  配置已存在，更新配置...")
            existing_config.provider = "custom"
            existing_config.api_key = api_key
            existing_config.base_url = base_url
            existing_config.auth_type = auth_type
            existing_config.auth_header = auth_header
            existing_config.api_format = api_format
            existing_config.is_full_url = is_full_url
            existing_config.temperature = 0.3
            existing_config.max_tokens = 2048
            existing_config.thinking = False
            existing_config.is_active = True
            existing_config.config_name = "MiniMax M2.7 (内部部署)"
            
            db.commit()
            db.refresh(existing_config)
            config = existing_config
        else:
            new_config = LLMUserConfig(
                user_identifier=user_identifier,
                provider="custom",
                model=model_name,
                api_key=api_key,
                base_url=base_url,
                auth_type=auth_type,
                auth_header=auth_header,
                api_format=api_format,
                is_full_url=is_full_url,
                temperature=0.3,
                max_tokens=2048,
                thinking=False,
                is_active=True,
                config_name="MiniMax M2.7 (内部部署)"
            )
            
            db.add(new_config)
            db.commit()
            db.refresh(new_config)
            config = new_config
        
        print("✅ 配置更新成功！")
        print(f"   ID: {config.id}")
        print(f"   Model: {config.model}")
        print(f"   Base URL: {config.base_url}")
        print(f"   Auth Type: {config.auth_type}")
        print(f"   Auth Header: {config.auth_header}")
        print(f"   API Format: {config.api_format}")
        print(f"   Is Full URL: {config.is_full_url}")
        print(f"   API Key: {'***' if config.api_key else '(空)'}")
        print()
        print("✅ 通用 API 配置已完成！")
        
    except Exception as e:
        db.rollback()
        print(f"❌ 初始化失败: {e}")
        logging.error(f"初始化失败: {e}", exc_info=True)
    finally:
        db.close()

if __name__ == "__main__":
    print("=" * 60)
    print("初始化通用 LLM 配置")
    print("=" * 60)
    init_minimax_config()
    print("=" * 60)

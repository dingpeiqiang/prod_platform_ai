"""
初始化 LLM 配置到数据库（使用项目通用配置机制）
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
from app.core.config import get_settings


def init_llm_config_from_env():
    """
    从环境变量初始化 LLM 配置（推荐方式）
    使用项目中已有的通用配置机制，而非硬编码
    """
    db = SessionLocal()
    
    try:
        settings = get_settings()
        
        # 从环境变量或配置文件获取配置
        user_identifier = "default-user"
        
        # 优先使用环境变量配置（无默认值，强制要求通过环境变量配置）
        model_name = settings.LLM_MODEL.strip() if settings.LLM_MODEL else ""
        api_key = settings.LLM_API_KEY.strip() if settings.LLM_API_KEY else ""
        base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else ""
        

        
        # 验证配置完整性
        if not model_name or not api_key or not base_url:
            print("❌ 配置不完整！请先设置以下环境变量：")
            print("   export LLM_MODEL=\"your-model-name\"")
            print("   export LLM_API_KEY=\"your-api-key\"")
            print("   export LLM_BASE_URL=\"https://your-api-base-url\"")
            print("\n   或者在 .env 文件中配置这些变量")
            return
        
        # 通用配置（从项目规范中获取默认值）
        auth_type = "token"          # 使用 token 请求头认证（项目规范）
        auth_header = None           # 自定义认证头名称（None 表示使用默认）
        api_format = "openai"        # API 格式（项目规范：openai, anthropic, gemini）
        is_full_url = False          # 是否使用完整 URL
        temperature = 0.3            # 温度参数（项目规范默认值）
        max_tokens = 2048           # 最大 token 数（项目规范默认值）
        thinking = False            # 是否启用思考模式
        
        print(f"📋 配置来源:")
        print(f"   - Model: {model_name} (环境变量)")
        print(f"   - API Key: {'***' if api_key else '(空)' } (环境变量)")
        print(f"   - Base URL: {base_url} (环境变量)")
        print()
        
        # 检查是否已存在配置
        existing_config = db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == user_identifier,
            LLMUserConfig.model == model_name
        ).first()
        
        config_data = {
            "provider": "custom",
            "api_key": api_key,
            "base_url": base_url,
            "auth_type": auth_type,
            "auth_header": auth_header,
            "api_format": api_format,
            "is_full_url": is_full_url,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "thinking": thinking,
            "is_active": True,
            "config_name": f"{model_name} (自定义配置)"
        }
        
        if existing_config:
            print(f"⚠️  配置已存在，更新配置...")
            for key, value in config_data.items():
                setattr(existing_config, key, value)
            db.commit()
            db.refresh(existing_config)
            config = existing_config
        else:
            new_config = LLMUserConfig(
                user_identifier=user_identifier,
                model=model_name,
                **config_data
            )
            db.add(new_config)
            db.commit()
            db.refresh(new_config)
            config = new_config
        
        print("✅ 配置更新成功！")
        print_config_summary(config)
        
    except Exception as e:
        db.rollback()
        print(f"❌ 初始化失败: {e}")
        logging.error(f"初始化失败: {e}", exc_info=True)
    finally:
        db.close()


def print_config_summary(config):
    """打印配置摘要"""
    print(f"\n📊 配置摘要:")
    print(f"   ID: {config.id}")
    print(f"   用户标识: {config.user_identifier}")
    print(f"   Provider: {config.provider}")
    print(f"   Model: {config.model}")
    print(f"   Base URL: {config.base_url}")
    print(f"   Auth Type: {config.auth_type}")
    print(f"   Auth Header: {config.auth_header or '默认'}")
    print(f"   API Format: {config.api_format}")
    print(f"   Is Full URL: {config.is_full_url}")
    print(f"   Temperature: {config.temperature}")
    print(f"   Max Tokens: {config.max_tokens}")
    print(f"   Thinking Mode: {'开启' if config.thinking else '关闭'}")
    print(f"   Active: {'是' if config.is_active else '否'}")
    print(f"   Config Name: {config.config_name}")
    print(f"\n✅ 通用 API 配置已完成！")


def show_supported_providers():
    """显示项目支持的 Provider 列表"""
    from app.services.llm.factory import ProviderFactory
    
    providers = ProviderFactory.get_supported_providers()
    print(f"\n🔧 项目支持的 Provider: {', '.join(providers)}")


if __name__ == "__main__":
    print("=" * 60)
    print("初始化通用 LLM 配置")
    print("=" * 60)
    
    # 显示支持的 Provider
    show_supported_providers()
    
    # 初始化配置
    init_llm_config_from_env()
    
    print("=" * 60)
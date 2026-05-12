from typing import Dict, Type, Optional, Any
from app.services.llm.provider import BaseProvider


class ProviderFactory:
    """LLM Provider 工厂类"""
    
    _registry: Dict[str, Type[BaseProvider]] = {}
    
    @classmethod
    def register(cls, provider_name: str) -> Type[BaseProvider]:
        """装饰器：注册 Provider"""
        def decorator(provider_class: Type[BaseProvider]) -> Type[BaseProvider]:
            cls._registry[provider_name.lower()] = provider_class
            return provider_class
        return decorator
    
    @classmethod
    def create(cls, provider_name: str, config: Dict[str, Any]) -> Optional[BaseProvider]:
        """创建 Provider 实例"""
        provider_class = cls._registry.get(provider_name.lower())
        if not provider_class:
            raise ValueError(f"Unknown provider: {provider_name}")
        return provider_class(config)
    
    @classmethod
    def get_supported_providers(cls) -> list:
        """获取支持的 Provider 列表"""
        return list(cls._registry.keys())
    
    @classmethod
    def is_supported(cls, provider_name: str) -> bool:
        """检查 Provider 是否支持"""
        return provider_name.lower() in cls._registry

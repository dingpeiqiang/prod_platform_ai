from abc import ABC, abstractmethod
from typing import Optional, Dict, Any, AsyncGenerator, Tuple
from app.services.llm.base import StreamStats


class BaseProvider(ABC):
    """LLM Provider 抽象基类"""
    
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.api_key = config.get('apiKey', '')
        self.base_url = config.get('baseUrl', '')
        self.model = config.get('model', '')
        self.thinking_enabled = config.get('thinking', False)
    
    @abstractmethod
    def call_sync(self, prompt: str, system_prompt: Optional[str] = None, 
                  max_tokens: Optional[int] = None) -> Optional[str]:
        """同步调用，返回内容"""
        pass
    
    @abstractmethod
    def call_with_reasoning(self, prompt: str, system_prompt: Optional[str] = None, 
                           max_tokens: Optional[int] = None) -> Tuple[Optional[str], Optional[str]]:
        """同步调用，返回 (content, reasoning)"""
        pass
    
    @abstractmethod
    async def call_stream(self, prompt: str, system_prompt: Optional[str] = None, 
                          max_tokens: Optional[int] = None) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """异步流式调用"""
        pass
    
    def _build_messages(self, prompt: str, system_prompt: Optional[str] = None) -> list:
        """构建消息列表（可被子类覆盖）"""
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        return messages
import json
import requests
import asyncio
from typing import Optional, Dict, Any, AsyncGenerator, Tuple
from app.services.llm.provider import BaseProvider
from app.services.llm.factory import ProviderFactory
from app.services.llm.base import StreamStats, extract_json
from app.core.config import get_settings


@ProviderFactory.register('openai')
@ProviderFactory.register('custom')
class OpenAIProvider(BaseProvider):
    """OpenAI 兼容 API Provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        settings = get_settings()
        
        self.api_key = settings.LLM_API_KEY.strip() if settings.LLM_API_KEY else self.api_key
        self.base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else self.base_url
        
        self.temperature = config.get('temperature', 0.3)
        self.max_tokens = config.get('maxTokens', 2048)
        self.max_input_tokens = config.get('maxInputTokens', 180000)
    
    def _get_headers(self) -> Dict[str, str]:
        """获取请求头"""
        headers = {"Content-Type": "application/json"}
        
        if 'dashscope' in self.base_url.lower():
            headers["Authorization"] = f"Bearer {self.api_key}"
        else:
            headers["Authorization"] = f"Bearer {self.api_key}"
        
        return headers
    
    def _build_payload(self, prompt: str, system_prompt: Optional[str] = None, 
                       max_tokens: Optional[int] = None, stream: bool = False) -> Dict[str, Any]:
        """构建请求体"""
        messages = self._build_messages(prompt, system_prompt)
        
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": max_tokens or self.max_tokens,
            "stream": stream
        }
        
        if self.thinking_enabled:
            payload['thinking'] = True
        
        return payload
    
    def call_sync(self, prompt: str, system_prompt: Optional[str] = None, 
                  max_tokens: Optional[int] = None) -> Optional[str]:
        """同步调用"""
        if not self.base_url or not self.api_key:
            return None
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=False)
        headers = self._get_headers()
        
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=180)
            if resp.status_code != 200:
                return None
            
            result = resp.json()
            return result.get('choices', [{}])[0].get('message', {}).get('content', '')
        
        except Exception as e:
            return None
    
    def call_with_reasoning(self, prompt: str, system_prompt: Optional[str] = None, 
                           max_tokens: Optional[int] = None) -> Tuple[Optional[str], Optional[str]]:
        """同步调用，返回 (content, reasoning)"""
        if not self.base_url or not self.api_key:
            return None, None
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=False)
        headers = self._get_headers()
        
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=180)
            
            if resp.status_code != 200:
                error_msg = resp.json().get('error', {}).get('message', f"HTTP {resp.status_code}")
                raise Exception(f"LLM 服务调用失败: {error_msg}")
            
            result = resp.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            reasoning = (
                result.get('choices', [{}])[0].get('message', {}).get('reasoning', '') or
                result.get('choices', [{}])[0].get('message', {}).get('reasoning_content', '')
            )
            
            return content, reasoning
        
        except requests.exceptions.RequestException as e:
            raise Exception(f"网络连接失败: {str(e)}")
        except Exception as e:
            raise
    
    async def call_stream(self, prompt: str, system_prompt: Optional[str] = None, 
                          max_tokens: Optional[int] = None) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """异步流式调用"""
        if not self.base_url or not self.api_key:
            return
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=True)
        headers = self._get_headers()
        
        stats = StreamStats(start_time=asyncio.get_event_loop().time())
        token_queue = asyncio.Queue()
        
        def _worker():
            try:
                resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=300)
                if resp.status_code != 200:
                    return
                
                for raw_line in resp.iter_lines():
                    if not raw_line:
                        continue
                    
                    line_text = raw_line.decode('utf-8')
                    if not line_text.startswith('data:'):
                        continue
                    
                    try:
                        data = json.loads(line_text[5:])
                        delta = data.get('choices', [{}])[0].get('delta', {})
                        content = delta.get('content', '')
                        reasoning = delta.get('reasoning', '')
                        
                        if content:
                            asyncio.get_event_loop().call_soon_threadsafe(
                                token_queue.put_nowait, ('content', content)
                            )
                        if reasoning:
                            asyncio.get_event_loop().call_soon_threadsafe(
                                token_queue.put_nowait, ('reasoning', reasoning)
                            )
                    except json.JSONDecodeError:
                        pass
            except Exception as e:
                pass
            finally:
                asyncio.get_event_loop().call_soon_threadsafe(token_queue.put_nowait, None)
        
        loop = asyncio.get_event_loop()
        loop.run_in_executor(None, _worker)
        
        while True:
            item = await token_queue.get()
            if item is None:
                break
            
            item_type, text = item
            stats.chars += len(text)
            
            if item_type == 'content':
                yield text, None
            else:
                yield text, None
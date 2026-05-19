import json
import logging
import requests
import asyncio
from typing import Optional, Dict, Any, AsyncGenerator, Tuple
from app.services.llm.provider import BaseProvider
from app.services.llm.factory import ProviderFactory
from app.services.llm.base import StreamStats, extract_json
from app.core.config import get_settings

logger = logging.getLogger("llm.openai")


@ProviderFactory.register('openai')
@ProviderFactory.register('custom')
class OpenAIProvider(BaseProvider):
    """OpenAI 兼容 API Provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        settings = get_settings()
        
        # 优先使用 config 中的值，如果没有则使用 .env 的默认值
        self.api_key = config.get('apiKey', '') or (settings.LLM_API_KEY.strip() if settings.LLM_API_KEY else '')
        self.base_url = config.get('baseUrl', '') or (settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else '')
        
        # 如果 config 中没有提供 model，使用 .env 的默认值
        if not self.model and settings.LLM_MODEL:
            self.model = settings.LLM_MODEL.strip()
        
        # 检查配置完整性，给出友好提示
        if not self.api_key or not self.base_url:
            logger.warning(
                "LLM 配置不完整！请通过前端界面配置模型参数。\n"
                "访问 http://localhost:5173，在侧边栏找到'模型配置'面板进行设置。"
            )
        
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
            logger.warning(f"[OpenAIProvider] 配置不完整 - base_url: {bool(self.base_url)}, api_key: {bool(self.api_key)}")
            return None
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=False)
        headers = self._get_headers()
        
        try:
            logger.info(f"[OpenAIProvider] 开始同步调用 - URL: {url}, Model: {self.model}")
            resp = requests.post(url, json=payload, headers=headers, timeout=180)
            
            if resp.status_code != 200:
                error_msg = f"HTTP {resp.status_code}"
                try:
                    error_detail = resp.json().get('error', {}).get('message', '')
                    if error_detail:
                        error_msg += f": {error_detail}"
                except:
                    pass
                logger.error(f"[OpenAIProvider] 请求失败 - {error_msg}")
                raise Exception(error_msg)
            
            result = resp.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            
            if not content:
                logger.warning("[OpenAIProvider] 响应内容为空")
                return None
            
            logger.info(f"[OpenAIProvider] 调用成功 - 内容长度: {len(content)}")
            return content
        
        except requests.exceptions.Timeout:
            error_msg = "请求超时 (180秒)"
            logger.error(f"[OpenAIProvider] {error_msg}")
            raise Exception(error_msg)
        except requests.exceptions.ConnectionError as e:
            error_msg = f"连接失败: {str(e)}"
            logger.error(f"[OpenAIProvider] {error_msg}")
            raise Exception(error_msg)
        except requests.exceptions.RequestException as e:
            error_msg = f"网络请求异常: {str(e)}"
            logger.error(f"[OpenAIProvider] {error_msg}")
            raise Exception(error_msg)
        except Exception as e:
            error_msg = f"未知错误: {str(e)}"
            logger.error(f"[OpenAIProvider] {error_msg}", exc_info=True)
            raise Exception(error_msg)
    
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
            
            # 提取 content
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            
            # 【修复】支持多种 reasoning 字段名（适配不同 API）
            choice = result.get('choices', [{}])[0] if result.get('choices') else {}
            message = choice.get('message', {})
            
            # 尝试从多个可能的位置提取 reasoning
            reasoning = (
                message.get('thinking', '') or  # DashScope thinking 模式
                message.get('reasoning', '') or  # 标准 reasoning 字段
                message.get('reasoning_content', '') or  # 备用字段名
                message.get('thought', '') or  # 其他可能的字段名
                choice.get('thinking', '') or  # 可能在 choice 层级
                choice.get('reasoning', '')    # 可能在 choice 层级
            )
            
            # 记录日志以便调试
            if reasoning:
                import logging
                logger = logging.getLogger("llm.openai")
                logger.info(f"[OpenAIProvider] 成功提取 reasoning（长度: {len(reasoning)}）")
            
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
        
        loop = asyncio.get_event_loop()
        stats = StreamStats(start_time=loop.time())
        token_queue = asyncio.Queue()
        
        def _worker(event_loop):
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
                            event_loop.call_soon_threadsafe(
                                token_queue.put_nowait, ('content', content)
                            )
                        if reasoning:
                            event_loop.call_soon_threadsafe(
                                token_queue.put_nowait, ('reasoning', reasoning)
                            )
                    except json.JSONDecodeError:
                        pass
            except Exception as e:
                pass
            finally:
                event_loop.call_soon_threadsafe(token_queue.put_nowait, None)
        
        loop.run_in_executor(None, _worker, loop)
        
        while True:
            item = await token_queue.get()
            if item is None:
                break
            
            item_type, text = item
            stats.char_count += len(text)
            
            if item_type == 'content':
                yield text, None
            else:
                yield text, None
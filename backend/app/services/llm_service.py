import json
import logging
import time
from typing import Dict, Any, Optional, List, AsyncGenerator, Tuple

from app.core.config import get_settings
from app.core.config_loader import config_loader
from app.services.llm.base import StreamStats, extract_json
from app.services.llm import ollama_provider, openai_provider, anthropic_provider, minimax_provider

settings = get_settings()

logger = logging.getLogger("llm_service")


def _truncate(text: str, max_len: int = 200) -> str:
    if len(text) <= max_len:
        return text
    return text[:max_len] + f"...[共{len(text)}字符]"


class LLMService:
    _instance = None
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance
    
    def __init__(self):
        logger.info("=" * 60)
        logger.info("LLM Service 初始化中...")
        self.app_config = config_loader.get_app_config()
        self.llm_config = self.app_config.get('llm', {})
        self.enabled = self.llm_config.get('enabled', False)
        self.fallback_to_rules = self.llm_config.get('fallbackToRules', True)
        self._client = None
        
        logger.info(f"LLM 配置加载: enabled={self.enabled}, provider={self.llm_config.get('provider')}, fallbackToRules={self.fallback_to_rules}")
        
        if self.enabled:
            logger.info(f"  - 模型: {self.llm_config.get('model')}")
            logger.info(f"  - 基础URL: {self.llm_config.get('baseUrl')}")
            logger.info(f"  - 温度: {self.llm_config.get('temperature')}")
            logger.info(f"  - 最大token: {self.llm_config.get('maxTokens')}")
            logger.info(f"  - 缓冲区大小: {self.llm_config.get('bufferSize')}")
        
        self._init_client()
        logger.info("LLM Service 初始化完成")
        logger.info("=" * 60)
    
    def _get_base_url(self) -> str:
        base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else None
        return base_url or self.llm_config.get('baseUrl', 'http://localhost:11434')

    def _get_model(self) -> str:
        model = settings.LLM_MODEL.strip() if settings.LLM_MODEL else None
        return model or self.llm_config.get('model', 'qwen2.5:14b-instruct-q4_K_M')

    def _build_messages(self, prompt: str, system_prompt: Optional[str]) -> List[Dict]:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        return messages

    def _init_client(self):
        if not self.enabled:
            return
        
        try:
            provider = self.llm_config.get('provider', 'openai')
            if provider == 'ollama' or provider == 'custom':
                self._client = None
                logger.info(f"{provider} client initialized (base_url=%s, model=%s)",
                            self._get_base_url(), self._get_model())
            else:
                import openai
                self._client = openai.OpenAI(
                    api_key=settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                    base_url=settings.LLM_BASE_URL or self.llm_config.get('baseUrl')
                )
                logger.info("OpenAI-compatible client initialized for provider: %s", provider)
        except ImportError:
            logger.warning("OpenAI not installed, LLM service disabled")
            self.enabled = False
        except Exception as e:
            logger.error("LLM client init failed: %s, falling back to rules", e)
            if not self.fallback_to_rules:
                self.enabled = False

    async def call_llm_stream(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True,
        return_stats: bool = False
    ) -> AsyncGenerator[str, None]:
        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            async for text, _ in ollama_provider.call_stream(
                self._get_base_url(), self._get_model(),
                prompt, system_prompt, self.llm_config, use_buffer
            ):
                if text:
                    yield text
        elif provider == 'anthropic':
            async for text, _ in anthropic_provider.call_stream(
                self._get_base_url(), settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                self._get_model(), prompt, system_prompt, self.llm_config, use_buffer
            ):
                if text:
                    yield text
        elif provider == 'custom':
            async for text, _ in minimax_provider.call_stream(
                self._get_base_url(), self._get_model(),
                settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                prompt, system_prompt, max_tokens=None, llm_config=self.llm_config
            ):
                if text:
                    yield text
        else:
            async for text, _ in openai_provider.call_stream(
                self._client, self.llm_config.get('model', 'gpt-4'),
                prompt, system_prompt, self.llm_config, use_buffer
            ):
                if text:
                    yield text
    
    async def call_llm_stream_with_stats(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        use_buffer: bool = True
    ) -> AsyncGenerator[Tuple[str, Optional["StreamStats"]], None]:
        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            async for item in ollama_provider.call_stream(
                self._get_base_url(), self._get_model(),
                prompt, system_prompt, self.llm_config, use_buffer
            ):
                yield item
        elif provider == 'anthropic':
            async for item in anthropic_provider.call_stream(
                self._get_base_url(), settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                self._get_model(), prompt, system_prompt, self.llm_config, use_buffer
            ):
                yield item
        elif provider == 'custom':
            async for item in minimax_provider.call_stream(
                self._get_base_url(), self._get_model(),
                settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                prompt, system_prompt, max_tokens=None, llm_config=self.llm_config
            ):
                yield item
        else:
            async for item in openai_provider.call_stream(
                self._client, self.llm_config.get('model', 'gpt-4'),
                prompt, system_prompt, self.llm_config, use_buffer
            ):
                yield item

    def _call_llm_sync(self, prompt: str, system_prompt: Optional[str] = None, max_tokens: Optional[int] = None) -> Optional[str]:
        if not self.enabled:
            return None

        provider = self.llm_config.get('provider', 'openai')
        if provider == 'ollama':
            return ollama_provider.call_sync(
                self._get_base_url(), self._get_model(),
                prompt, system_prompt, self.llm_config, max_tokens
            )
        if provider == 'custom':
            return minimax_provider.call_stream_sync(
                self._get_base_url(), self._get_model(),
                settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                prompt, system_prompt, max_tokens, self.llm_config
            )

        if not self._client:
            return None

        return openai_provider.call_sync(
            self._client, self.llm_config.get('model', 'gpt-4'),
            prompt, system_prompt, self.llm_config, max_tokens
        )

    def _call_llm(self, prompt: str, system_prompt: Optional[str] = None, max_tokens: Optional[int] = None) -> Optional[str]:
        return self._call_llm_sync(prompt, system_prompt, max_tokens=max_tokens)

    def _extract_json(self, text: str) -> Optional[Dict]:
        return extract_json(text)

    def _call_llm_sync_with_reasoning(
        self, prompt: str, system_prompt: Optional[str] = None, max_tokens: Optional[int] = None
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.enabled:
            return None, None

        provider = self.llm_config.get('provider', 'openai')
        if provider == 'custom':
            return minimax_provider.call_with_reasoning(
                self._get_base_url(), self._get_model(),
                settings.LLM_API_KEY or self.llm_config.get('apiKey'),
                prompt, system_prompt, max_tokens, self.llm_config
            )
        if provider == 'ollama':
            return ollama_provider.call_with_reasoning(
                self._get_base_url(), self._get_model(),
                prompt, system_prompt, self.llm_config, max_tokens
            )

        content = self._call_llm_sync(prompt, system_prompt, max_tokens=max_tokens)
        return content, None

    def recognize_intent(self, user_input: str, form_types: List[str]) -> Optional[Dict]:
        if not self.enabled:
            return None
        
        prompt_template = config_loader.get_prompt('intent_recognition')
        if not prompt_template:
            return None
        
        form_types_str = '\n'.join([f"- {ft}" for ft in form_types])
        prompt = prompt_template.format(
            form_types=form_types_str,
            user_input=user_input
        )
        
        result = self._call_llm(prompt)
        if result:
            return extract_json(result)
        return None
    
    def extract_fields(self, user_input: str, form_schema: Dict) -> Optional[Dict]:
        if not self.enabled:
            logger.warning("[extract_fields] LLM服务未启用，跳过")
            return None

        prompt_template = config_loader.get_prompt('field_extraction')
        if not prompt_template:
            logger.warning("[extract_fields] 未找到 field_extraction prompt 模板")
            return None

        schema_str = json.dumps(form_schema, ensure_ascii=False, indent=2)
        prompt = prompt_template.format(
            form_schema=schema_str,
            user_input=user_input
        )

        logger.info("[extract_fields] 调用LLM prompt长度=%d schema字段数=%d",
                    len(prompt), len(form_schema.get("fields", [])))
        logger.debug("[extract_fields] prompt=\n%s", prompt[:800])

        result = self._call_llm(prompt)
        if result:
            parsed = extract_json(result)
            logger.info("[extract_fields] LLM返回原始长度=%d 解析成功=%s 提取字段数=%d",
                        len(result), bool(parsed), len(parsed.get('extractedFields', [])) if parsed else 0)
            logger.debug("[extract_fields] LLM原始返回=\n%s", result[:500])
            return parsed
        logger.warning("[extract_fields] LLM返回为空")
        return None


llm_service = LLMService()
import time
import logging
from typing import Optional, Dict, Any, Tuple, AsyncGenerator

from app.core.config import get_settings
from app.core.config_loader import config_loader
from app.services.llm.factory import ProviderFactory
from app.services.llm.base import StreamStats, extract_json

logger = logging.getLogger("llm_service")


class LLMService:
    """LLM 服务类 - 支持动态 Provider 扩展"""
    
    def __init__(self):
        logger.info("=" * 60)
        logger.info("LLM Service 初始化中...")
        self.app_config = config_loader.get_app_config()
        self.llm_config = self.app_config.get('llm', {})
        self.enabled = self.llm_config.get('enabled', False)
        self.fallback_to_rules = self.llm_config.get('fallbackToRules', True)
        self._provider = None
        
        provider_name = self.llm_config.get('provider', 'openai')
        logger.info(f"LLM 配置加载: enabled={self.enabled}, provider={provider_name}, fallbackToRules={self.fallback_to_rules}")
        
        if self.enabled:
            logger.info(f"  - 模型: {self.llm_config.get('model')}")
            logger.info(f"  - 基础URL: {self.llm_config.get('baseUrl')}")
            logger.info(f"  - 温度: {self.llm_config.get('temperature')}")
            logger.info(f"  - 最大token: {self.llm_config.get('maxTokens')}")
            logger.info(f"  - 缓冲区大小: {self.llm_config.get('bufferSize')}")
            logger.info(f"  - 思考模式: {self.llm_config.get('thinking')}")
        
        self._init_provider()
        logger.info("LLM Service 初始化完成")
        logger.info("=" * 60)
    
    def _init_provider(self):
        """初始化 Provider"""
        if not self.enabled:
            return
        
        try:
            provider_name = self.llm_config.get('provider', 'openai')
            self._provider = ProviderFactory.create(provider_name, self.llm_config)
            logger.info(f"Provider 初始化成功: {provider_name}")
        except Exception as e:
            logger.error(f"Provider 初始化失败: {e}")
            self._provider = None
    
    @property
    def provider(self):
        """获取当前 Provider"""
        if not self._provider:
            self._init_provider()
        return self._provider
    
    def _get_model(self) -> str:
        """获取模型名称"""
        settings = get_settings()
        return settings.LLM_MODEL.strip() if settings.LLM_MODEL else self.llm_config.get('model', 'gpt-4')
    
    def _get_base_url(self) -> str:
        """获取基础 URL"""
        settings = get_settings()
        base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else None
        return base_url or self.llm_config.get('baseUrl', 'http://localhost:11434')
    
    def _get_api_key(self) -> str:
        """获取 API Key"""
        settings = get_settings()
        return settings.LLM_API_KEY.strip() if settings.LLM_API_KEY else self.llm_config.get('apiKey', '')
    
    async def call_llm_stream(self, prompt: str, system_prompt: Optional[str] = None, 
                              max_tokens: Optional[int] = None, use_buffer: bool = True) -> AsyncGenerator[str, None]:
        """异步流式调用 LLM"""
        if not self.enabled or not self.provider:
            logger.warning("[LLM STREAM] 服务未启用或 Provider 未初始化")
            return
        
        provider_name = self.llm_config.get('provider', 'openai')
        model = self._get_model()
        prompt_len = len(prompt) + len(system_prompt) if system_prompt else len(prompt)
        
        logger.info("[LLM STREAM] ====== START ======")
        logger.info("[LLM STREAM] Provider: %s, Model: %s, MaxTokens: %s, Buffer: %s", 
                   provider_name.upper(), model, max_tokens, use_buffer)
        logger.info("[LLM STREAM] PromptLength: %d", prompt_len)
        
        if system_prompt:
            system_display = system_prompt[:800] if len(system_prompt) > 800 else system_prompt
            logger.info("[LLM STREAM] SystemPrompt: %s%s", 
                       system_display, "..." if len(system_prompt) > 800 else "")
        
        user_prompt_display = prompt[:1500] if len(prompt) > 1500 else prompt
        logger.info("[LLM STREAM] UserPrompt: %s%s", 
                   user_prompt_display, "..." if len(prompt) > 1500 else "")
        
        llm_start = time.time()
        total_chars = 0
        response_buffer = []
        
        try:
            async for text, _ in self.provider.call_stream(prompt, system_prompt, max_tokens):
                if text:
                    total_chars += len(text)
                    response_buffer.append(text)
                    yield text
            
            llm_elapsed = time.time() - llm_start
            full_response = ''.join(response_buffer)
            
            logger.info("[LLM STREAM] ====== COMPLETE ======")
            response_display = full_response[:1000] if len(full_response) > 1000 else full_response
            logger.info("[LLM STREAM] Response: %s%s", 
                       response_display, "..." if len(full_response) > 1000 else "")
            logger.info("[LLM STREAM] Stats: %d chars | %.2fs | %.1f chars/s", 
                       total_chars, llm_elapsed, total_chars/llm_elapsed if llm_elapsed > 0 else 0)
            
        except Exception as e:
            llm_elapsed = time.time() - llm_start
            logger.error("[LLM STREAM] ====== FAILED ======")
            logger.error("[LLM STREAM] Provider: %s, Model: %s", provider_name.upper(), model)
            logger.error("[LLM STREAM] Error: %s", str(e))
            logger.error("[LLM STREAM] Elapsed: %.2fs", llm_elapsed)
            raise
    
    def _call_llm_sync(self, prompt: str, system_prompt: Optional[str] = None, 
                       max_tokens: Optional[int] = None) -> Optional[str]:
        """同步调用 LLM"""
        if not self.enabled or not self.provider:
            logger.warning("[LLM SYNC] 服务未启用或 Provider 未初始化")
            return None
        
        provider_name = self.llm_config.get('provider', 'openai')
        model = self._get_model()
        prompt_len = len(prompt) + len(system_prompt) if system_prompt else len(prompt)
        
        logger.info("[LLM SYNC] ====== START ======")
        logger.info("[LLM SYNC] Provider: %s, Model: %s, MaxTokens: %s", 
                   provider_name.upper(), model, max_tokens)
        logger.info("[LLM SYNC] PromptLength: %d", prompt_len)
        
        if system_prompt:
            system_display = system_prompt[:800] if len(system_prompt) > 800 else system_prompt
            logger.info("[LLM SYNC] SystemPrompt: %s%s", 
                       system_display, "..." if len(system_prompt) > 800 else "")
        
        user_prompt_display = prompt[:1500] if len(prompt) > 1500 else prompt
        logger.info("[LLM SYNC] UserPrompt: %s%s", 
                   user_prompt_display, "..." if len(prompt) > 1500 else "")
        
        llm_start = time.time()
        
        try:
            response = self.provider.call_sync(prompt, system_prompt, max_tokens)
            
            llm_elapsed = time.time() - llm_start
            response_len = len(response) if response else 0
            
            logger.info("[LLM SYNC] ====== COMPLETE ======")
            if response and response.strip():
                response_display = response[:1000] if len(response) > 1000 else response
                logger.info("[LLM SYNC] Response: %s%s", 
                           response_display, "..." if len(response) > 1000 else "")
            else:
                logger.info("[LLM SYNC] Response: (empty)")
            logger.info("[LLM SYNC] Stats: %d chars | %.2fs | %.1f chars/s", 
                       response_len, llm_elapsed, response_len/llm_elapsed if llm_elapsed > 0 else 0)
            
            return response
        
        except Exception as e:
            llm_elapsed = time.time() - llm_start
            logger.error("[LLM SYNC] ====== FAILED ======")
            logger.error("[LLM SYNC] Provider: %s, Model: %s", provider_name.upper(), model)
            logger.error("[LLM SYNC] Error: %s", str(e))
            logger.error("[LLM SYNC] Elapsed: %.2fs", llm_elapsed)
            return None
    
    def _call_llm_sync_with_reasoning(self, prompt: str, system_prompt: Optional[str] = None, 
                                      max_tokens: Optional[int] = None) -> Tuple[Optional[str], Optional[str]]:
        """同步调用 LLM，同时返回内容和推理"""
        if not self.enabled or not self.provider:
            logger.warning("[LLM REASONING] 服务未启用或 Provider 未初始化")
            return None, None
        
        provider_name = self.llm_config.get('provider', 'openai')
        model = self._get_model()
        prompt_len = len(prompt) + len(system_prompt) if system_prompt else len(prompt)
        
        logger.info("[LLM REASONING] ====== START ======")
        logger.info("[LLM REASONING] Provider: %s, Model: %s, Thinking: %s, MaxTokens: %s", 
                   provider_name.upper(), model, self.llm_config.get('thinking'), max_tokens)
        logger.info("[LLM REASONING] PromptLength: %d", prompt_len)
        
        if system_prompt:
            system_display = system_prompt[:800] if len(system_prompt) > 800 else system_prompt
            logger.info("[LLM REASONING] SystemPrompt: %s%s", 
                       system_display, "..." if len(system_prompt) > 800 else "")
        
        user_prompt_display = prompt[:1500] if len(prompt) > 1500 else prompt
        logger.info("[LLM REASONING] UserPrompt: %s%s", 
                   user_prompt_display, "..." if len(prompt) > 1500 else "")
        
        llm_start = time.time()
        
        try:
            content, reasoning = self.provider.call_with_reasoning(prompt, system_prompt, max_tokens)
            
            llm_elapsed = time.time() - llm_start
            content_len = len(content) if content else 0
            reasoning_len = len(reasoning) if reasoning else 0
            
            logger.info("[LLM REASONING] ====== COMPLETE ======")
            if content and content.strip():
                content_display = content[:1000] if len(content) > 1000 else content
                logger.info("[LLM REASONING] Content: %s%s", 
                           content_display, "..." if len(content) > 1000 else "")
                
                if reasoning:
                    reasoning_display = reasoning[:800] if len(reasoning) > 800 else reasoning
                    logger.info("[LLM REASONING] Reasoning: %s%s", 
                               reasoning_display, "..." if len(reasoning) > 800 else "")
            else:
                logger.info("[LLM REASONING] Content: (empty)")
            logger.info("[LLM REASONING] Stats: %d chars | %d reasoning chars | %.2fs", 
                       content_len, reasoning_len, llm_elapsed)
            
            return content, reasoning
        
        except Exception as e:
            llm_elapsed = time.time() - llm_start
            logger.error("[LLM REASONING] ====== FAILED ======")
            logger.error("[LLM REASONING] Provider: %s, Model: %s", provider_name.upper(), model)
            logger.error("[LLM REASONING] Error: %s", str(e))
            logger.error("[LLM REASONING] Elapsed: %.2fs", llm_elapsed)
            return None, None
    
    def _call_llm(self, prompt: str, system_prompt: Optional[str] = None, 
                  max_tokens: Optional[int] = None) -> Optional[str]:
        """统一调用接口"""
        return self._call_llm_sync(prompt, system_prompt, max_tokens=max_tokens)
    
    def _extract_json(self, text: str) -> Optional[Dict]:
        """提取 JSON"""
        return extract_json(text)
    
    @staticmethod
    def get_supported_providers() -> list:
        """获取支持的 Provider 列表"""
        return ProviderFactory.get_supported_providers()
    
    @staticmethod
    def is_provider_supported(provider_name: str) -> bool:
        """检查 Provider 是否支持"""
        return ProviderFactory.is_supported(provider_name)
    
    def extract_fields(self, user_input: str, form_schema: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """从用户输入中提取表单字段"""
        if not self.enabled or not self.provider:
            logger.warning("⚠️ [LLM extract_fields] 服务未启用或 Provider 未初始化")
            return None
        
        import json
        
        field_extraction_prompt = config_loader.get_prompt('field_extraction')
        if not field_extraction_prompt:
            logger.warning("⚠️ [LLM extract_fields] 未找到字段提取提示模板")
            return None
        
        schema_str = json.dumps(form_schema, ensure_ascii=False)
        prompt = field_extraction_prompt.format(
            form_schema=schema_str,
            user_input=user_input
        )
        
        try:
            response = self._call_llm_sync(prompt)
            
            if not response:
                return None
            
            extracted = extract_json(response)
            if extracted and 'extractedFields' in extracted:
                return extracted
            
            return None
        
        except Exception as e:
            logger.error(f"❌ [LLM extract_fields] 提取失败: {e}")
            return None


llm_service = LLMService()
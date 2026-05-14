from typing import Optional, Dict, Any, AsyncGenerator, Union
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
import logging
from app.core.config_loader import config_loader

logger = logging.getLogger("langchain.llm_wrapper")


class LangChainLLM:
    """LangChain LLM 封装类"""
    
    def __init__(self):
        self.app_config = config_loader.get_app_config()
        self.llm_config = self.app_config.get('llm', {})
        self.enabled = self.llm_config.get('enabled', False)
        self._llm: Optional[BaseChatModel] = None
        self._init_llm()
    
    def _init_llm(self):
        """初始化LLM"""
        if not self.enabled:
            logger.warning("LLM service is not enabled")
            return
        
        provider = self.llm_config.get('provider', 'openai').lower()
        model = self.llm_config.get('model', 'gpt-4o')
        base_url = self.llm_config.get('baseUrl')
        api_key = self.llm_config.get('apiKey')
        temperature = self.llm_config.get('temperature', 0.1)
        max_tokens = self.llm_config.get('maxTokens', 4096)
        
        try:
            if provider == 'openai' or provider == 'custom':
                self._llm = ChatOpenAI(
                    model=model,
                    base_url=base_url,
                    api_key=api_key,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    streaming=True
                )
            elif provider == 'anthropic':
                self._llm = ChatAnthropic(
                    model=model,
                    api_key=api_key,
                    temperature=temperature,
                    max_tokens=max_tokens
                )
            else:
                logger.warning(f"Unsupported provider: {provider}, falling back to OpenAI")
                self._llm = ChatOpenAI(
                    model=model,
                    base_url=base_url,
                    api_key=api_key,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    streaming=True
                )
            logger.info(f"LangChain LLM initialized: {provider}/{model}")
        except Exception as e:
            logger.error(f"Failed to initialize LangChain LLM: {e}")
    
    @property
    def llm(self) -> BaseChatModel:
        """获取LLM实例"""
        if not self._llm:
            self._init_llm()
        return self._llm
    
    async def generate(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        """生成响应"""
        if not self._llm:
            return ""
        
        messages = []
        if system_prompt:
            messages.append(SystemMessage(content=system_prompt))
        messages.append(HumanMessage(content=prompt))
        
        try:
            response = await self._llm.ainvoke(messages)
            return response.content
        except Exception as e:
            logger.error(f"LLM generation failed: {e}")
            return ""
    
    async def generate_json(self, prompt: str, system_prompt: Optional[str] = None) -> Dict[str, Any]:
        """生成JSON响应"""
        if not self._llm:
            return {}
        
        parser = JsonOutputParser()
        
        messages = []
        if system_prompt:
            messages.append(SystemMessage(content=system_prompt))
        messages.append(HumanMessage(content=f"{prompt}\n\n{parser.get_format_instructions()}"))
        
        try:
            chain = self._llm | parser
            response = await chain.ainvoke({"input": prompt})
            return response
        except Exception as e:
            logger.error(f"LLM JSON generation failed: {e}")
            return {}
    
    async def stream(self, prompt: str, system_prompt: Optional[str] = None) -> AsyncGenerator[str, None]:
        """流式生成响应"""
        if not self._llm:
            return
        
        messages = []
        if system_prompt:
            messages.append(SystemMessage(content=system_prompt))
        messages.append(HumanMessage(content=prompt))
        
        try:
            async for chunk in self._llm.astream(messages):
                if chunk.content:
                    yield chunk.content
        except Exception as e:
            logger.error(f"LLM streaming failed: {e}")
    
    def create_chain(self, prompt_template: str, output_parser: str = "str"):
        """创建简单的链"""
        if not self._llm:
            return None
        
        prompt = ChatPromptTemplate.from_template(prompt_template)
        
        if output_parser == "json":
            parser = JsonOutputParser()
        else:
            parser = StrOutputParser()
        
        return prompt | self._llm | parser


_llm_wrapper: Optional[LangChainLLM] = None


def get_langchain_llm() -> LangChainLLM:
    """获取全局LangChain LLM实例"""
    global _llm_wrapper
    if _llm_wrapper is None:
        _llm_wrapper = LangChainLLM()
    return _llm_wrapper

# IntentHandlerRegistry - 意图处理器注册器
# 单例模式 + @intent_handler 装饰器，参考 MCPToolHub 的 @mcptool 模式

import logging
from typing import Type, Dict, Optional, AsyncGenerator, Any

from .base import BaseIntentHandler, IntentContext

logger = logging.getLogger("intent_registry")


class IntentHandlerRegistry:
    """
    意图处理器注册中心（单例）

    职责：
    1. 处理器的注册与注销
    2. 按意图类型查找并分发到对应处理器
    3. 未注册意图降级到 DefaultHandler
    """

    _instance: Optional["IntentHandlerRegistry"] = None
    _handlers: Dict[str, BaseIntentHandler] = {}

    def __new__(cls) -> "IntentHandlerRegistry":
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        self._handlers: Dict[str, BaseIntentHandler] = {}
        logger.info("[IntentHandlerRegistry] 初始化完成")

    def register(self, handler_cls: Type[BaseIntentHandler]) -> Type[BaseIntentHandler]:
        """
        注册一个意图处理器类（类装饰器使用）

        Args:
            handler_cls: 处理器类（必须继承 BaseIntentHandler 且设置了 intent_type）
        """
        intent_type = getattr(handler_cls, "intent_type", "")
        if not intent_type:
            raise ValueError(f"处理器 {handler_cls.__name__} 未设置 intent_type 属性")

        # 实例化处理器
        instance = handler_cls()
        self._handlers[intent_type] = instance
        logger.info("[IntentHandlerRegistry] 注册处理器: %s -> %s", intent_type, handler_cls.__name__)
        return handler_cls

    def dispatch(self, intent_type: str, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """
        根据意图类型分发到对应处理器

        Args:
            intent_type: 意图类型字符串
            ctx: 意图上下文数据袋

        Yields:
            SSE 帧字符串
        """
        handler = self._handlers.get(intent_type)
        if handler:
            logger.debug("[IntentHandlerRegistry] 分发意图: %s -> %s", intent_type, type(handler).__name__)
            return handler.handle(ctx)
        else:
            logger.warning("[IntentHandlerRegistry] 未注册的意图类型: %s", intent_type)
            from .handlers.chat_handler import ChatHandler
            fallback = ChatHandler()
            fallback.intent_type = intent_type  # 记录实际请求的类型用于日志
            return fallback.handle(ctx)

    def get_handler(self, intent_type: str) -> Optional[BaseIntentHandler]:
        """获取指定类型的处理器实例"""
        return self._handlers.get(intent_type)

    def list_handlers(self) -> Dict[str, str]:
        """列出所有已注册的处理器 {type: name}"""
        return {
            t: type(h).__name__
            for t, h in self._handlers.items()
        }

    def has(self, intent_type: str) -> bool:
        """检查是否已注册指定意图类型"""
        return intent_type in self._handlers


# ── 全局单例访问 ──────────────────────────────────────

_registry_instance: Optional[IntentHandlerRegistry] = None


def get_intent_registry() -> IntentHandlerRegistry:
    """获取全局唯一的 IntentHandlerRegistry 实例"""
    global _registry_instance
    if _registry_instance is None:
        _registry_instance = IntentHandlerRegistry()
    return _registry_instance


def intent_handler(intent_type: str):
    """
    意图处理器装饰器 —— 用法:

        @intent_handler("form")
        class FormHandler(BaseIntentHandler):
            async def handle(self, ctx):
                yield _thinking("处理表单意图")
                ...
    """
    def decorator(cls):
        cls.intent_type = intent_type
        get_intent_registry().register(cls)
        return cls
    return decorator

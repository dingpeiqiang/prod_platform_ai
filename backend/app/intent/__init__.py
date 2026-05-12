# intent 模块入口
# import 本文件即触发所有 handler 的注册

import logging

logger = logging.getLogger("intent")

# 导入所有处理器
from .handlers.form_handler import FormHandler
from .handlers.form_update_handler import FormUpdateHandler
from .handlers.delete_form_handler import DeleteFormHandler
from .handlers.manage_history_handler import ManageHistoryHandler
from .handlers.configure_handler import ConfigureHandler
from .handlers.chat_handler import ChatHandler
from .handlers.validation_handler import ValidationHandler

# 显式注册所有处理器（通过 registry 的 register 方法）
from .registry import get_intent_registry

_registry = get_intent_registry()
_registry.register(FormHandler)
_registry.register(FormUpdateHandler)
_registry.register(DeleteFormHandler)
_registry.register(ManageHistoryHandler)
_registry.register(ConfigureHandler)
_registry.register(ChatHandler)
_registry.register(ValidationHandler)

logger.info(f"[intent] 模块加载完成, 已注册 {_registry.list_handlers()}")

__all__ = [
    "FormHandler",
    "FormUpdateHandler",
    "DeleteFormHandler",
    "ManageHistoryHandler",
    "ConfigureHandler",
    "ChatHandler",
    "ValidationHandler",
    "get_intent_registry",
    "intent_handler",
]

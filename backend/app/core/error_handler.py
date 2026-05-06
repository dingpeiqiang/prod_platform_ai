# 框架统一错误处理机制
# 提供错误处理的工具函数和装饰器

import logging
import traceback
import functools
from typing import Callable, Any, Optional, Dict

from .errors import (
    FrameworkError, ErrorLevel, ErrorCategory,
    RecoveryStrategy, ErrorCode, get_error_message
)

logger = logging.getLogger("error_handler")


class ErrorHandler:
    """错误处理器单例"""

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self._initialized = True
        self._error_callbacks: Dict[str, list] = {
            "sse": [],      # SSE 事件回调
            "log": [],      # 日志回调
            "report": [],   # 上报回调
        }

    def register_callback(self, event_type: str, callback: Callable):
        """注册错误处理回调"""
        if event_type in self._error_callbacks:
            self._error_callbacks[event_type].append(callback)

    def emit(self, error: FrameworkError):
        """发射错误事件"""
        # 1. 记录日志
        self._emit_log(error)

        # 2. 发送 SSE
        self._emit_sse(error)

        # 3. 上报（如果有配置）
        self._emit_report(error)

    def _emit_log(self, error: FrameworkError):
        """记录日志"""
        log_level = {
            ErrorLevel.DEBUG: logging.debug,
            ErrorLevel.INFO: logging.info,
            ErrorLevel.WARNING: logging.warning,
            ErrorLevel.ERROR: logging.error,
            ErrorLevel.CRITICAL: logging.critical,
        }.get(ErrorLevel(error.level), logging.error)

        log_level(
            f"[FrameworkError] {error.code} - {error.message} | "
            f"category={error.category} source={error.source} "
            f"recoverable={error.recoverable}"
        )

        if error.detail:
            logger.debug(f"[FrameworkError] 详情: {error.detail}")

        # 调用注册的 log 回调
        for callback in self._error_callbacks.get("log", []):
            try:
                callback(error)
            except Exception as e:
                logger.warning(f"[FrameworkError] log callback 失败: {e}")

    def _emit_sse(self, error: FrameworkError):
        """发送 SSE 事件（存储供 SSE 端点发送）"""
        # 存储到上下文或直接通过 WebSocket 发送
        # 这里存储到类属性，SSE 端点可以获取
        if not hasattr(self, '_pending_errors'):
            self._pending_errors = []
        self._pending_errors.append(error.to_sse())

        # 调用注册的 SSE 回调
        for callback in self._error_callbacks.get("sse", []):
            try:
                callback(error)
            except Exception as e:
                logger.warning(f"[FrameworkError] sse callback 失败: {e}")

    def _emit_report(self, error: FrameworkError):
        """上报错误（到监控系统等）"""
        for callback in self._error_callbacks.get("report", []):
            try:
                callback(error)
            except Exception as e:
                logger.warning(f"[FrameworkError] report callback 失败: {e}")

    def get_pending_errors(self) -> list:
        """获取待发送的 SSE 错误事件"""
        if not hasattr(self, '_pending_errors'):
            return []
        errors = self._pending_errors
        self._pending_errors = []
        return errors

    def clear_pending_errors(self):
        """清除待发送的错误"""
        self._pending_errors = []


# 全局单例
error_handler = ErrorHandler()


def framework_error(
    category: str = ErrorCategory.SYSTEM.value,
    level: str = ErrorLevel.ERROR.value,
    code: str = "ERR_UNKNOWN",
    recoverable: bool = True,
    recovery_hint: str = ""
):
    """
    框架错误处理装饰器

    用法:
    @framework_error(category=ErrorCategory.TOOL.value, level=ErrorLevel.WARNING.value)
    async def my_function():
        ...
    """
    def decorator(func: Callable) -> Callable:
        is_async = asyncio_iscoroutinefunction(func)

        if is_async:
            @functools.wraps(func)
            async def async_wrapper(*args, **kwargs):
                try:
                    return await func(*args, **kwargs)
                except Exception as e:
                    error = _build_error(
                        func=func,
                        exception=e,
                        category=category,
                        level=level,
                        code=code,
                        recoverable=recoverable,
                        recovery_hint=recovery_hint
                    )
                    error_handler.emit(error)

                    if ErrorLevel(level) >= ErrorLevel.ERROR:
                        raise
                    return None
            return async_wrapper
        else:
            @functools.wraps(func)
            def sync_wrapper(*args, **kwargs):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    error = _build_error(
                        func=func,
                        exception=e,
                        category=category,
                        level=level,
                        code=code,
                        recoverable=recoverable,
                        recovery_hint=recovery_hint
                    )
                    error_handler.emit(error)

                    if ErrorLevel(level) >= ErrorLevel.ERROR:
                        raise
                    return None
            return sync_wrapper
    return decorator


def _build_error(
    func: Callable,
    exception: Exception,
    category: str,
    level: str,
    code: str,
    recoverable: bool,
    recovery_hint: str
) -> FrameworkError:
    """构建错误对象"""
    module = getattr(func, '__module__', 'unknown')
    name = getattr(func, '__name__', 'unknown')
    source = f"{module}.{name}"

    return FrameworkError(
        category=category,
        level=level,
        code=code,
        message=get_error_message(code, str(exception)),
        detail=traceback.format_exc(),
        source=source,
        recoverable=recoverable,
        recovery_hint=recovery_hint or _get_default_recovery_hint(category)
    )


def _get_default_recovery_hint(category: str) -> str:
    """获取类别的默认恢复建议"""
    hints = {
        ErrorCategory.LLM.value: "请稍后重试，或联系管理员检查 AI 服务配置",
        ErrorCategory.TOOL.value: "该操作不会影响其他功能，已跳过",
        ErrorCategory.VALIDATION.value: "请检查输入内容后重试",
        ErrorCategory.DATABASE.value: "请稍后重试",
        ErrorCategory.CONFIG.value: "请联系管理员检查配置",
        ErrorCategory.INTENT.value: "请尝试重新描述您的需求",
        ErrorCategory.EXTERNAL.value: "请稍后重试",
        ErrorCategory.WEBSOCKET.value: "请刷新页面后重试",
        ErrorCategory.AUTH.value: "请重新登录",
        ErrorCategory.SYSTEM.value: "请联系管理员",
    }
    return hints.get(category, "请稍后重试")


def asyncio_iscoroutinefunction(func: Callable) -> bool:
    """检查是否为异步函数"""
    import asyncio
    return asyncio.iscoroutinefunction(func)


# 便捷函数：创建特定类型的错误
def create_error(
    category: str,
    code: str,
    message: str = None,
    level: str = ErrorLevel.ERROR.value,
    recoverable: bool = True,
    recovery_hint: str = "",
    **context
) -> FrameworkError:
    """创建错误对象"""
    import inspect
    source = "unknown"
    try:
        frame = inspect.currentframe()
        if frame:
            caller = frame.f_back
            if caller:
                source = f"{caller.f_code.co_filename}:{caller.f_lineno}"
    except:
        pass

    return FrameworkError(
        category=category,
        level=level,
        code=code,
        message=get_error_message(code, message),
        source=source,
        recoverable=recoverable,
        recovery_hint=recovery_hint or _get_default_recovery_hint(category),
        context=context
    )


def emit_error(error: FrameworkError):
    """发射错误事件（便捷函数）"""
    error_handler.emit(error)
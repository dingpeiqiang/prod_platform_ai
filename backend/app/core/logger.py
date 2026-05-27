"""
统一日志框架 - 提供标准化的日志配置和错误处理
"""

import logging
import logging.handlers
import os
import sys
from typing import Optional, Callable
from functools import wraps


# ── 配置常量 ───────────────────────────────────────────────────────────────────

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_DIR = os.path.join(BASE_DIR, "..", "logs")
os.makedirs(LOG_DIR, exist_ok=True)

FMT = "%(asctime)s.%(msecs)03d [%(levelname)-8s] %(name)-30s %(funcName)-20s:%(lineno)-4d - %(message)s"
DATE_FMT = "%Y-%m-%d %H:%M:%S"

# 定义日志级别颜色
COLOR_MAP = {
    logging.DEBUG: "\033[94m",      # 蓝色
    logging.INFO: "\033[92m",       # 绿色
    logging.WARNING: "\033[93m",    # 黄色
    logging.ERROR: "\033[91m",      # 红色
    logging.CRITICAL: "\033[95m",   # 紫色
}
RESET_COLOR = "\033[0m"


# ── 统一日志管理器 ───────────────────────────────────────────────────────────

class ColoredFormatter(logging.Formatter):
    """带颜色的日志格式化器"""
    
    def format(self, record):
        level_color = COLOR_MAP.get(record.levelno, "")
        record.levelname = f"{level_color}{record.levelname}{RESET_COLOR}"
        return super().format(record)


class LoggerManager:
    """统一日志管理器"""
    
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
        
        # 清除所有现有handlers
        self._root_logger = logging.getLogger()
        self._root_logger.handlers.clear()
        self._root_logger.setLevel(logging.DEBUG)
        
        # 终端输出（带颜色）
        self._console_handler = logging.StreamHandler(sys.stdout)
        self._console_handler.setLevel(logging.DEBUG)
        self._console_handler.setFormatter(ColoredFormatter(FMT, DATE_FMT))
        self._root_logger.addHandler(self._console_handler)
        
        # 文件输出（不带颜色）
        try:
            self._file_handler = logging.FileHandler(
                filename=os.path.join(LOG_DIR, "app.log"),
                mode="a",
                encoding="utf-8",
                delay=True,
            )
            self._file_handler.setLevel(logging.DEBUG)
            self._file_handler.setFormatter(logging.Formatter(FMT, DATE_FMT))
            self._root_logger.addHandler(self._file_handler)
        except Exception as e:
            print(f"Failed to create file handler: {e}")
        
        # 降低第三方库的日志级别
        self._configure_third_party_loggers()
        
        # 记录初始化信息
        self._init_logger = logging.getLogger("logger.init")
        self._init_logger.info("=" * 80)
        self._init_logger.info("统一日志框架初始化完成")
        self._init_logger.info(f"日志文件: {os.path.join(LOG_DIR, 'app.log')}")
        self._init_logger.info(f"日志级别: DEBUG")
        self._init_logger.info("=" * 80)
    
    def _configure_third_party_loggers(self):
        """配置第三方库日志级别"""
        third_party_loggers = [
            ("uvicorn.access", logging.INFO),
            ("uvicorn.autodiscover", logging.WARNING),
            ("watchfiles.main", logging.WARNING),
            ("httpx", logging.WARNING),
            ("urllib3", logging.WARNING),
            ("httpcore", logging.WARNING),
            ("sqlalchemy", logging.WARNING),
            ("langchain", logging.WARNING),
            ("openai", logging.WARNING),
        ]
        
        for logger_name, level in third_party_loggers:
            logging.getLogger(logger_name).setLevel(level)
    
    def get_logger(self, name: str) -> logging.Logger:
        """获取指定名称的logger"""
        logger = logging.getLogger(name)
        logger.setLevel(logging.DEBUG)
        return logger
    
    def get_log_dir(self) -> str:
        """获取日志目录"""
        return LOG_DIR
    
    def flush(self):
        """刷新所有handlers"""
        for handler in self._root_logger.handlers:
            handler.flush()


# ── 全局单例 ─────────────────────────────────────────────────────────────────

logger_manager = LoggerManager()


# ── 便捷函数 ─────────────────────────────────────────────────────────────────

def get_logger(name: Optional[str] = None) -> logging.Logger:
    """
    获取logger
    
    Args:
        name: logger名称，默认为调用模块的__name__
    
    Returns:
        logging.Logger实例
    """
    if name is None:
        import inspect
        caller_frame = inspect.currentframe().f_back
        name = caller_frame.f_globals.get('__name__', 'unknown')
    
    return logger_manager.get_logger(name)


# ── 统一错误处理装饰器 ───────────────────────────────────────────────────────

def log_errors(logger: Optional[logging.Logger] = None, re_raise: bool = True):
    """
    统一错误处理装饰器
    
    自动捕获并记录函数中的异常，支持同步和异步函数。
    
    Args:
        logger: 用于记录错误的logger，默认为函数所在模块的logger
        re_raise: 是否重新抛出异常，默认为True
    
    Usage:
        @log_errors()
        def my_function():
            ...
        
        @log_errors(logger=custom_logger, re_raise=False)
        async def my_async_function():
            ...
    """
    def decorator(func):
        # 获取logger
        if logger is None:
            import inspect
            caller_frame = inspect.currentframe().f_back
            module_name = caller_frame.f_globals.get('__name__', 'unknown')
            func_logger = logging.getLogger(module_name)
        else:
            func_logger = logger
        
        # 检查是否为异步函数
        import asyncio
        is_async = asyncio.iscoroutinefunction(func)
        
        if is_async:
            @wraps(func)
            async def async_wrapper(*args, **kwargs):
                try:
                    return await func(*args, **kwargs)
                except Exception as e:
                    func_logger.exception(
                        f"[ERROR] 函数执行异常 - {func.__module__}.{func.__name__}"
                    )
                    if re_raise:
                        raise
                    return None
            return async_wrapper
        else:
            @wraps(func)
            def sync_wrapper(*args, **kwargs):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    func_logger.exception(
                        f"[ERROR] 函数执行异常 - {func.__module__}.{func.__name__}"
                    )
                    if re_raise:
                        raise
                    return None
            return sync_wrapper
    return decorator


# ── API日志装饰器 ───────────────────────────────────────────────────────────

def log_api(logger: Optional[logging.Logger] = None):
    """
    API日志装饰器 - 记录请求和响应信息
    
    Usage:
        @router.post("/api/endpoint")
        @log_api()
        async def my_api_endpoint(request: Request):
            ...
    """
    def decorator(func):
        if logger is None:
            import inspect
            caller_frame = inspect.currentframe().f_back
            module_name = caller_frame.f_globals.get('__name__', 'unknown')
            func_logger = logging.getLogger(module_name)
        else:
            func_logger = logger
        
        import asyncio
        is_async = asyncio.iscoroutinefunction(func)
        
        if is_async:
            @wraps(func)
            async def async_wrapper(*args, **kwargs):
                request = kwargs.get('request') or (args[0] if args else None)
                if hasattr(request, 'url'):
                    func_logger.info(f"[API] 请求开始 - {request.method} {request.url.path}")
                
                try:
                    result = await func(*args, **kwargs)
                    func_logger.debug(f"[API] 请求完成 - {func.__name__}")
                    return result
                except Exception as e:
                    func_logger.error(
                        f"[API] 请求失败 - {func.__module__}.{func.__name__}: {str(e)}"
                    )
                    raise
            return async_wrapper
        else:
            @wraps(func)
            def sync_wrapper(*args, **kwargs):
                request = kwargs.get('request') or (args[0] if args else None)
                if hasattr(request, 'url'):
                    func_logger.info(f"[API] 请求开始 - {request.method} {request.url.path}")
                
                try:
                    result = func(*args, **kwargs)
                    func_logger.debug(f"[API] 请求完成 - {func.__name__}")
                    return result
                except Exception as e:
                    func_logger.error(
                        f"[API] 请求失败 - {func.__module__}.{func.__name__}: {str(e)}"
                    )
                    raise
            return sync_wrapper
    return decorator

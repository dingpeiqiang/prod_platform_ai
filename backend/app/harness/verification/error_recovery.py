"""
错误自动恢复模块

核心功能：
- 错误分类与诊断
- 自动恢复策略
- 降级处理
"""

from typing import Dict, Any, Optional, Callable, List, Type
from dataclasses import dataclass, field
from enum import Enum
import logging
import traceback

logger = logging.getLogger(__name__)


class ErrorCategory(Enum):
    """错误分类"""
    VALIDATION = "validation"           # 验证错误
    SCHEMA_MISMATCH = "schema_mismatch" # Schema 不匹配
    TIMEOUT = "timeout"                 # 超时错误
    NETWORK = "network"                 # 网络错误
    PERMISSION = "permission"           # 权限错误
    RATE_LIMIT = "rate_limit"          # 限流错误
    LLM_ERROR = "llm_error"            # LLM 调用错误
    UNKNOWN = "unknown"                # 未知错误


class RecoveryStrategy(Enum):
    """恢复策略"""
    RETRY = "retry"                   # 重试
    FALLBACK_SCHEMA = "fallback_schema"  # 降级 Schema
    USE_DEFAULT = "use_default"        # 使用默认值
    PARTIAL_RESULT = "partial_result"  # 返回部分结果
    CACHE_FALLBACK = "cache_fallback"  # 回退到缓存
    USER_INTERVENTION = "user_intervention"  # 需要用户干预
    FAIL_FAST = "fail_fast"           # 快速失败


@dataclass
class ErrorContext:
    """错误上下文"""
    category: ErrorCategory
    message: str
    original_error: Exception
    traceback_str: str
    metadata: Dict[str, Any] = field(default_factory=dict)
    timestamp: str = ""


@dataclass
class RecoveryAction:
    """恢复动作"""
    strategy: RecoveryStrategy
    success: bool
    result: Any = None
    message: str = ""
    attempts: int = 0


class ErrorRecovery:
    """
    错误自动恢复器
    
    功能：
    1. 错误分类 - 自动识别错误类型
    2. 策略选择 - 根据错误类型选择恢复策略
    3. 自动恢复 - 执行恢复动作
    4. 降级处理 - 确保服务可用性
    
    使用示例：
    ```python
    recovery = ErrorRecovery()
    
    # 注册恢复策略
    recovery.register_strategy(
        ErrorCategory.VALIDATION,
        RecoveryStrategy.USE_DEFAULT,
        lambda ctx: {"status": "default"}
    )
    
    # 处理错误
    try:
        result = some_operation()
    except Exception as e:
        action = recovery.handle_error(e)
        if action.success:
            return action.result
    ```
    """

    def __init__(self):
        self._strategies: Dict[ErrorCategory, Dict[RecoveryStrategy, Callable]] = {}
        self._error_handlers: List[Callable] = []
        self._fallback_values: Dict[str, Any] = {}
        self._error_history: List[ErrorContext] = []
        self._max_history = 100
        
        # 注册默认策略
        self._register_default_strategies()

    def _register_default_strategies(self):
        """注册默认策略"""
        # 验证错误 - 使用默认值
        self.register_strategy(
            ErrorCategory.VALIDATION,
            RecoveryStrategy.USE_DEFAULT,
            lambda ctx: self._get_fallback_value("validation")
        )
        
        # Schema 不匹配 - 降级 Schema
        self.register_strategy(
            ErrorCategory.SCHEMA_MISMATCH,
            RecoveryStrategy.FALLBACK_SCHEMA,
            lambda ctx: self._get_fallback_value("schema")
        )
        
        # 超时错误 - 重试
        self.register_strategy(
            ErrorCategory.TIMEOUT,
            RecoveryStrategy.RETRY,
            None  # 需要外部重试逻辑
        )
        
        # 限流错误 - 等待后重试
        self.register_strategy(
            ErrorCategory.RATE_LIMIT,
            RecoveryStrategy.RETRY,
            None
        )
        
        # LLM 错误 - 使用缓存
        self.register_strategy(
            ErrorCategory.LLM_ERROR,
            RecoveryStrategy.CACHE_FALLBACK,
            lambda ctx: self._get_cached_result(ctx)
        )

    def register_strategy(
        self,
        category: ErrorCategory,
        strategy: RecoveryStrategy,
        handler: Optional[Callable]
    ):
        """注册恢复策略"""
        if category not in self._strategies:
            self._strategies[category] = {}
        self._strategies[category][strategy] = handler

    def register_fallback(self, key: str, value: Any):
        """注册降级值"""
        self._fallback_values[key] = value

    def register_error_handler(self, handler: Callable):
        """注册错误处理器"""
        self._error_handlers.append(handler)

    def handle_error(
        self,
        error: Exception,
        context: Optional[Dict[str, Any]] = None,
        max_attempts: int = 3
    ) -> RecoveryAction:
        """
        处理错误并尝试恢复
        
        Args:
            error: 异常对象
            context: 错误上下文
            max_attempts: 最大尝试次数
            
        Returns:
            RecoveryAction: 恢复结果
        """
        # 1. 错误分类
        category = self._classify_error(error)
        
        # 2. 记录错误
        error_ctx = self._create_error_context(category, error, context)
        self._record_error(error_ctx)
        
        # 3. 通知错误处理器
        for handler in self._error_handlers:
            try:
                handler(error_ctx)
            except Exception as e:
                logger.error(f"Error handler failed: {e}")
        
        # 4. 选择恢复策略
        strategy = self._select_strategy(category, error_ctx)
        
        # 5. 执行恢复
        return self._execute_recovery(strategy, category, error_ctx, max_attempts)

    def _classify_error(self, error: Exception) -> ErrorCategory:
        """分类错误"""
        error_type = type(error).__name__
        error_msg = str(error).lower()
        
        # 基于错误类型和消息分类
        if "validation" in error_msg or "ValidationError" in error_type:
            return ErrorCategory.VALIDATION
        elif "schema" in error_msg or "SchemaError" in error_type:
            return ErrorCategory.SCHEMA_MISMATCH
        elif "timeout" in error_msg or "TimeoutError" in error_type:
            return ErrorCategory.TIMEOUT
        elif "network" in error_msg or "ConnectionError" in error_type:
            return ErrorCategory.NETWORK
        elif "permission" in error_msg or "Unauthorized" in error_type or "Forbidden" in error_type:
            return ErrorCategory.PERMISSION
        elif "rate" in error_msg or "429" in error_msg or "limit" in error_msg:
            return ErrorCategory.RATE_LIMIT
        elif "llm" in error_msg or "openai" in error_msg or "anthropic" in error_msg:
            return ErrorCategory.LLM_ERROR
        else:
            return ErrorCategory.UNKNOWN

    def _select_strategy(
        self,
        category: ErrorCategory,
        error_ctx: ErrorContext
    ) -> RecoveryStrategy:
        """选择恢复策略"""
        if category in self._strategies:
            strategies = self._strategies[category]
            
            # 优先选择可用的策略
            priority_order = [
                RecoveryStrategy.RETRY,
                RecoveryStrategy.CACHE_FALLBACK,
                RecoveryStrategy.FALLBACK_SCHEMA,
                RecoveryStrategy.USE_DEFAULT,
                RecoveryStrategy.PARTIAL_RESULT,
            ]
            
            for strategy in priority_order:
                if strategy in strategies:
                    return strategy
        
        return RecoveryStrategy.FAIL_FAST

    def _execute_recovery(
        self,
        strategy: RecoveryStrategy,
        category: ErrorCategory,
        error_ctx: ErrorContext,
        max_attempts: int
    ) -> RecoveryAction:
        """执行恢复"""
        attempts = 0
        last_error = None
        
        while attempts < max_attempts:
            attempts += 1
            
            try:
                handler = self._get_handler(category, strategy)
                
                if handler is None:
                    return RecoveryAction(
                        strategy=strategy,
                        success=False,
                        message=f"No handler for {category.value} + {strategy.value}",
                        attempts=attempts
                    )
                
                result = handler(error_ctx)
                
                return RecoveryAction(
                    strategy=strategy,
                    success=True,
                    result=result,
                    message=f"Recovered using {strategy.value}",
                    attempts=attempts
                )
                
            except Exception as e:
                last_error = e
                logger.warning(f"Recovery attempt {attempts} failed: {e}")
        
        return RecoveryAction(
            strategy=strategy,
            success=False,
            message=f"Failed after {attempts} attempts: {last_error}",
            attempts=attempts
        )

    def _get_handler(
        self,
        category: ErrorCategory,
        strategy: RecoveryStrategy
    ) -> Optional[Callable]:
        """获取处理器"""
        if category in self._strategies:
            return self._strategies[category].get(strategy)
        return None

    def _get_fallback_value(self, key: str) -> Any:
        """获取降级值"""
        return self._fallback_values.get(key, {})

    def _get_cached_result(self, error_ctx: ErrorContext) -> Any:
        """从缓存获取结果"""
        cache_key = error_ctx.metadata.get("cache_key")
        if cache_key and hasattr(self, "_cache"):
            return self._cache.get(cache_key)
        return None

    def _create_error_context(
        self,
        category: ErrorCategory,
        error: Exception,
        context: Optional[Dict[str, Any]]
    ) -> ErrorContext:
        """创建错误上下文"""
        from datetime import datetime
        
        return ErrorContext(
            category=category,
            message=str(error),
            original_error=error,
            traceback_str=traceback.format_exc(),
            metadata=context or {},
            timestamp=datetime.now().isoformat()
        )

    def _record_error(self, error_ctx: ErrorContext):
        """记录错误"""
        self._error_history.append(error_ctx)
        
        # 保持历史长度
        if len(self._error_history) > self._max_history:
            self._error_history = self._error_history[-self._max_history:]

    def get_error_stats(self) -> Dict[str, Any]:
        """获取错误统计"""
        stats = {
            "total": len(self._error_history),
            "by_category": {},
            "recent": []
        }
        
        for error_ctx in self._error_history:
            cat = error_ctx.category.value
            stats["by_category"][cat] = stats["by_category"].get(cat, 0) + 1
        
        # 最近 10 条
        stats["recent"] = [
            {
                "category": e.category.value,
                "message": e.message,
                "timestamp": e.timestamp
            }
            for e in self._error_history[-10:]
        ]
        
        return stats

    def clear_history(self):
        """清空错误历史"""
        self._error_history.clear()


# 便捷函数
def with_recovery(
    recovery: ErrorRecovery,
    fallback: Any = None,
    context: Optional[Dict] = None
):
    """装饰器：为函数添加错误恢复"""
    def decorator(func: Callable) -> Callable:
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                action = recovery.handle_error(e, context)
                if action.success:
                    return action.result
                if fallback is not None:
                    return fallback
                raise
        return wrapper
    return decorator

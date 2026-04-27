"""
重试策略模块

支持：
- 指数退避
- 抖动
- 条件重试
- 最大尝试次数
- 超时控制
"""

from typing import Callable, Any, Optional, Set, Type
from dataclasses import dataclass, field
from enum import Enum
import asyncio
import logging
import random
import time

logger = logging.getLogger(__name__)


class RetryStrategy(Enum):
    """重试策略"""
    FIXED = "fixed"                  # 固定间隔
    LINEAR = "linear"                # 线性递增
    EXPONENTIAL = "exponential"       # 指数退避
    FIBONACCI = "fibonacci"          # 斐波那契退避


@dataclass
class RetryConfig:
    """重试配置"""
    max_attempts: int = 3              # 最大尝试次数
    initial_delay: float = 1.0         # 初始延迟（秒）
    max_delay: float = 60.0            # 最大延迟（秒）
    strategy: RetryStrategy = RetryStrategy.EXPONENTIAL
    jitter: bool = True                # 是否添加抖动
    jitter_factor: float = 0.5         # 抖动因子
    retryable_exceptions: Set[Type[Exception]] = field(default_factory=set)
    retryable_predicates: list = field(default_factory=list)  # 条件重试
    timeout: Optional[float] = None    # 总超时时间（秒）
    
    def __post_init__(self):
        if not self.retryable_exceptions:
            self.retryable_exceptions = {
                ConnectionError,
                TimeoutError,
                OSError,
            }


@dataclass
class RetryResult:
    """重试结果"""
    success: bool
    result: Any = None
    attempts: int = 0
    total_time: float = 0.0
    errors: list = field(default_factory=list)
    last_error: Optional[Exception] = None


class RetryPolicy:
    """
    重试策略管理器
    
    功能：
    1. 多种退避策略（固定、线性、指数、斐波那契）
    2. 抖动支持（防止惊群效应）
    3. 条件重试（基于异常类型或断言）
    4. 超时控制
    
    使用示例：
    ```python
    policy = RetryPolicy()
    
    # 简单重试
    result = await policy.execute(
        lambda: api_call(),
        config=RetryConfig(max_attempts=3)
    )
    
    # 自定义重试
    result = await policy.execute(
        lambda: fragile_operation(),
        config=RetryConfig(
            max_attempts=5,
            strategy=RetryStrategy.EXPONENTIAL,
            initial_delay=2.0,
            jitter=True
        )
    )
    ```
    """

    def __init__(self, default_config: Optional[RetryConfig] = None):
        self.default_config = default_config or RetryConfig()

    async def execute(
        self,
        func: Callable,
        *args,
        config: Optional[RetryConfig] = None,
        **kwargs
    ) -> RetryResult:
        """
        执行带重试的函数
        """
        cfg = config or self.default_config
        start_time = time.time()
        errors = []
        last_error = None
        
        for attempt in range(1, cfg.max_attempts + 1):
            try:
                # 检查超时
                if cfg.timeout and (time.time() - start_time) > cfg.timeout:
                    return RetryResult(
                        success=False,
                        attempts=attempt,
                        total_time=time.time() - start_time,
                        errors=errors,
                        last_error=last_error
                    )
                
                # 执行函数
                if asyncio.iscoroutinefunction(func):
                    result = await func(*args, **kwargs)
                else:
                    result = func(*args, **kwargs)
                
                return RetryResult(
                    success=True,
                    result=result,
                    attempts=attempt,
                    total_time=time.time() - start_time,
                    errors=errors
                )
                
            except Exception as e:
                last_error = e
                errors.append(str(e))
                
                logger.warning(f"Attempt {attempt}/{cfg.max_attempts} failed: {e}")
                
                # 检查是否应该重试
                if not self._should_retry(e, cfg, attempt):
                    return RetryResult(
                        success=False,
                        attempts=attempt,
                        total_time=time.time() - start_time,
                        errors=errors,
                        last_error=e
                    )
                
                # 如果不是最后一次尝试，等待后重试
                if attempt < cfg.max_attempts:
                    delay = self._calculate_delay(attempt, cfg)
                    logger.info(f"Retrying in {delay:.2f} seconds...")
                    await asyncio.sleep(delay)
        
        return RetryResult(
            success=False,
            attempts=cfg.max_attempts,
            total_time=time.time() - start_time,
            errors=errors,
            last_error=last_error
        )

    def _should_retry(
        self,
        exception: Exception,
        config: RetryConfig,
        attempt: int
    ) -> bool:
        """判断是否应该重试"""
        if attempt >= config.max_attempts:
            return False
        
        if config.retryable_exceptions:
            if not any(isinstance(exception, exc_type) for exc_type in config.retryable_exceptions):
                return False
        
        for predicate in config.retryable_predicates:
            try:
                if predicate(exception):
                    return True
            except Exception:
                pass
        
        return len(config.retryable_predicates) == 0

    def _calculate_delay(self, attempt: int, config: RetryConfig) -> float:
        """计算延迟时间"""
        base_delay = config.initial_delay
        
        if config.strategy == RetryStrategy.FIXED:
            delay = base_delay
        elif config.strategy == RetryStrategy.LINEAR:
            delay = base_delay * attempt
        elif config.strategy == RetryStrategy.EXPONENTIAL:
            delay = base_delay * (2 ** (attempt - 1))
        elif config.strategy == RetryStrategy.FIBONACCI:
            delay = base_delay * self._fibonacci(attempt)
        else:
            delay = base_delay
        
        delay = min(delay, config.max_delay)
        
        if config.jitter:
            jitter_range = delay * config.jitter_factor
            delay = delay + random.uniform(-jitter_range, jitter_range)
            delay = max(0.1, delay)
        
        return delay

    def _fibonacci(self, n: int) -> int:
        """计算斐波那契数"""
        if n <= 1:
            return 1
        a, b = 1, 1
        for _ in range(n - 1):
            a, b = b, a + b
        return b


# 便捷装饰器
def retry(
    max_attempts: int = 3,
    strategy: RetryStrategy = RetryStrategy.EXPONENTIAL,
    initial_delay: float = 1.0,
    jitter: bool = True
):
    """重试装饰器"""
    config = RetryConfig(
        max_attempts=max_attempts,
        strategy=strategy,
        initial_delay=initial_delay,
        jitter=jitter
    )
    
    def decorator(func: Callable) -> Callable:
        policy = RetryPolicy()
        
        async def async_wrapper(*args, **kwargs):
            result = await policy.execute(func, *args, config=config, **kwargs)
            if not result.success:
                raise result.last_error or Exception("Max retries exceeded")
            return result.result
        
        def sync_wrapper(*args, **kwargs):
            result = policy.execute_sync(func, *args, config=config, **kwargs)
            if not result.success:
                raise result.last_error or Exception("Max retries exceeded")
            return result.result
        
        if asyncio.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper
    
    return decorator

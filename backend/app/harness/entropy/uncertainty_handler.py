# -*- coding: utf-8 -*-
"""
不确定性处理器 - 不确定性处理
==============================
处理AI输出中的不确定性，提供多种解决策略

不确定性类型：
1. 认知不确定性（Epistemic）- 知识缺乏导致
2. 偶然不确定性（Aleatoric）- 数据本身的不确定性
3. 分布不确定性（Distributional）- 输入分布变化

解决策略：
1. 重试（Retry）- 重新生成
2. 置信度阈值（Confidence Threshold）- 拒绝低置信度
3. 多样性采样（Diverse Sampling）- 使用不同参数
4. 外部查询（External Lookup）- 查询外部知识
5. 人工审核（Human Review）- 人工介入
6. 降级处理（Degradation）- 返回保守结果
"""

import asyncio
import random
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable, Dict, List, Optional, Tuple, Union
import logging

logger = logging.getLogger(__name__)


class UncertaintyLevel(str, Enum):
    """不确定性级别"""
    NONE = "none"          # 无不确定性
    LOW = "low"           # 低不确定性
    MEDIUM = "medium"      # 中等不确定性
    HIGH = "high"          # 高不确定性
    VERY_HIGH = "very_high"  # 极高不确定性


class UncertaintyType(str, Enum):
    """不确定性类型"""
    COGNITIVE = "cognitive"        # 认知不确定性
    ALEATORIC = "aleatoric"        # 偶然不确定性
    DISTRIBUTIONAL = "distributional"  # 分布不确定性
    UNKNOWN = "unknown"            # 未知类型


class ResolutionStrategy(str, Enum):
    """解决策略"""
    RETRY = "retry"               # 重试
    CONFIDENCE_THRESHOLD = "confidence_threshold"  # 置信度阈值
    DIVERSE_SAMPLING = "diverse_sampling"  # 多样性采样
    EXTERNAL_LOOKUP = "external_lookup"  # 外部查询
    HUMAN_REVIEW = "human_review"  # 人工审核
    DEGRADATION = "degradation"   # 降级处理
    ENSEMBLE = "ensemble"         # 集成方法
    ASK_CLARIFICATION = "ask_clarification"  # 请求澄清


@dataclass
class UncertaintyResult:
    """
    不确定性处理结果

    Attributes:
        level: 不确定性级别
        uncertainty_type: 不确定性类型
        confidence: 置信度（0-1）
        resolved: 是否已解决
        strategy_used: 使用的解决策略
        result: 处理结果
        original_output: 原始输出
        fallback_output: 降级输出
        requires_human: 是否需要人工介入
        metadata: 附加信息
    """
    level: UncertaintyLevel
    uncertainty_type: UncertaintyType
    confidence: float
    resolved: bool
    strategy_used: Optional[ResolutionStrategy]
    result: Any = None
    original_output: Any = None
    fallback_output: Any = None
    requires_human: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "level": self.level.value,
            "uncertainty_type": self.uncertainty_type.value,
            "confidence": self.confidence,
            "resolved": self.resolved,
            "strategy_used": self.strategy_used.value if self.strategy_used else None,
            "result": self.result,
            "requires_human": self.requires_human
        }


@dataclass
class RetryConfig:
    """重试配置"""
    max_retries: int = 3
    initial_delay: float = 0.5  # 秒
    max_delay: float = 10.0    # 秒
    exponential_base: float = 2.0
    jitter: bool = True


@dataclass
class ThresholdConfig:
    """阈值配置"""
    accept_threshold: float = 0.7   # 接受的最低置信度
    review_threshold: float = 0.4    # 需要审核的阈值
    reject_threshold: float = 0.2    # 拒绝的阈值


class UncertaintyHandler:
    """
    不确定性处理器

    功能：
    - 检测不确定性
    - 选择解决策略
    - 执行处理
    - 降级处理
    """

    def __init__(
        self,
        retry_config: Optional[RetryConfig] = None,
        threshold_config: Optional[ThresholdConfig] = None
    ):
        """
        初始化不确定性处理器

        Args:
            retry_config: 重试配置
            threshold_config: 阈值配置
        """
        self._retry_config = retry_config or RetryConfig()
        self._threshold_config = threshold_config or ThresholdConfig()

        # 策略处理器
        self._strategies: Dict[ResolutionStrategy, Callable] = {}

        # 外部查询函数
        self._external_lookup: Optional[Callable] = None

        # 人工审核回调
        self._human_review_callback: Optional[Callable] = None

        # 统计
        self._stats = {
            "total_processed": 0,
            "resolved_by_retry": 0,
            "resolved_by_threshold": 0,
            "resolved_by_diverse": 0,
            "resolved_by_external": 0,
            "requires_human_review": 0,
            "used_fallback": 0
        }

        logger.info("UncertaintyHandler initialized")

    # ==================== 配置 ====================

    def set_retry_config(self, config: RetryConfig) -> None:
        """设置重试配置"""
        self._retry_config = config

    def set_threshold_config(self, config: ThresholdConfig) -> None:
        """设置阈值配置"""
        self._threshold_config = config

    def register_strategy(self, strategy: ResolutionStrategy, handler: Callable) -> None:
        """
        注册策略处理器

        Args:
            strategy: 策略类型
            handler: 处理函数，签名: async def handler(output, config) -> Any
        """
        self._strategies[strategy] = handler

    def set_external_lookup(self, lookup_func: Callable) -> None:
        """
        设置外部查询函数

        Args:
            lookup_func: 查询函数，签名: async def lookup(query) -> Any
        """
        self._external_lookup = lookup_func

    def set_human_review_callback(self, callback: Callable) -> None:
        """
        设置人工审核回调

        Args:
            callback: 回调函数，签名: async def callback(output, context) -> Any
        """
        self._human_review_callback = callback

    # ==================== 核心处理 ====================

    async def handle(
        self,
        output: Any,
        confidence: float,
        uncertainty_type: UncertaintyType = UncertaintyType.UNKNOWN,
        context: Optional[Dict[str, Any]] = None
    ) -> UncertaintyResult:
        """
        处理不确定性

        Args:
            output: 模型输出
            confidence: 置信度
            uncertainty_type: 不确定性类型
            context: 上下文信息

        Returns:
            处理结果
        """
        context = context or {}
        self._stats["total_processed"] += 1

        # 1. 评估不确定性级别
        level = self._evaluate_level(confidence)

        # 2. 创建初始结果
        result = UncertaintyResult(
            level=level,
            uncertainty_type=uncertainty_type,
            confidence=confidence,
            resolved=False,
            strategy_used=None,
            original_output=output,
            result=output
        )

        # 3. 根据级别决定处理策略
        if level == UncertaintyLevel.NONE:
            result.resolved = True
            return result

        elif level == UncertaintyLevel.LOW:
            # 低不确定性，可以接受
            result.resolved = True
            return result

        elif level == UncertaintyLevel.MEDIUM:
            # 中等不确定性，尝试自动解决
            result = await self._handle_medium(result, context)

        elif level in (UncertaintyLevel.HIGH, UncertaintyLevel.VERY_HIGH):
            # 高/极高不确定性，需要更强力措施
            result = await self._handle_high(result, context)

        return result

    def _evaluate_level(self, confidence: float) -> UncertaintyLevel:
        """评估不确定性级别"""
        if confidence >= 0.9:
            return UncertaintyLevel.NONE
        elif confidence >= 0.7:
            return UncertaintyLevel.LOW
        elif confidence >= 0.4:
            return UncertaintyLevel.MEDIUM
        elif confidence >= 0.2:
            return UncertaintyLevel.HIGH
        else:
            return UncertaintyLevel.VERY_HIGH

    async def _handle_medium(self, result: UncertaintyResult, context: Dict) -> UncertaintyResult:
        """处理中等不确定性"""
        # 优先尝试重试
        if ResolutionStrategy.RETRY in self._strategies:
            try:
                new_output = await self._strategies[ResolutionStrategy.RETRY](
                    result.original_output,
                    {"retries": 1}
                )
                result.result = new_output
                result.strategy_used = ResolutionStrategy.RETRY
                result.resolved = True
                self._stats["resolved_by_retry"] += 1
                return result
            except Exception as e:
                logger.warning(f"Retry strategy failed: {e}")

        # 如果重试失败，标记为需要审核
        result.requires_human = True
        result.resolved = False
        return result

    async def _handle_high(self, result: UncertaintyResult, context: Dict) -> UncertaintyResult:
        """处理高不确定性"""
        # 尝试多种策略

        # 1. 多样性采样
        if ResolutionStrategy.DIVERSE_SAMPLING in self._strategies:
            try:
                outputs = await self._strategies[ResolutionStrategy.DIVERSE_SAMPLING](
                    result.original_output,
                    {"n_samples": 3}
                )
                # 选择最一致的输出
                best = self._select_most_consistent(outputs)
                result.result = best
                result.strategy_used = ResolutionStrategy.DIVERSE_SAMPLING
                self._stats["resolved_by_diverse"] += 1

                # 更新置信度
                result.confidence = min(result.confidence + 0.2, 1.0)
                if result.confidence >= self._threshold_config.accept_threshold:
                    result.resolved = True
                    return result
            except Exception as e:
                logger.warning(f"Diverse sampling failed: {e}")

        # 2. 外部查询
        if self._external_lookup and "query" in context:
            try:
                external_result = await self._external_lookup(context["query"])
                if external_result:
                    result.result = external_result
                    result.strategy_used = ResolutionStrategy.EXTERNAL_LOOKUP
                    result.confidence = 0.8  # 外部查询提高置信度
                    result.resolved = True
                    self._stats["resolved_by_external"] += 1
                    return result
            except Exception as e:
                logger.warning(f"External lookup failed: {e}")

        # 3. 请求人工审核
        if self._human_review_callback:
            try:
                reviewed = await self._human_review_callback(
                    result.original_output,
                    context
                )
                if reviewed:
                    result.result = reviewed
                    result.strategy_used = ResolutionStrategy.HUMAN_REVIEW
                    result.requires_human = True
                    self._stats["requires_human_review"] += 1
                    return result
            except Exception as e:
                logger.warning(f"Human review failed: {e}")

        # 4. 使用降级输出
        result.result = result.fallback_output or self._generate_fallback(context)
        result.strategy_used = ResolutionStrategy.DEGRADATION
        result.requires_human = True
        self._stats["used_fallback"] += 1

        return result

    def _select_most_consistent(self, outputs: List[Any]) -> Any:
        """选择最一致的输出"""
        if not outputs:
            return None
        if len(outputs) == 1:
            return outputs[0]

        # 简化的选择策略：返回第一个
        # 实际应该计算输出间的相似度
        return outputs[0]

    def _generate_fallback(self, context: Dict[str, Any]) -> Any:
        """生成降级输出"""
        # 返回一个保守的默认值
        return {
            "status": "uncertain",
            "message": "输出存在不确定性，请人工审核",
            "original_result": None
        }

    # ==================== 预定义策略 ====================

    async def retry_strategy(
        self,
        output: Any,
        config: Dict[str, Any]
    ) -> Any:
        """
        预定义重试策略

        Args:
            output: 原始输出
            config: 配置，包含 retries 次数

        Returns:
            新输出
        """
        retries = config.get("retries", 1)
        delay = self._retry_config.initial_delay

        for i in range(retries):
            # 等待后重试
            await asyncio.sleep(delay)

            # 模拟重试（实际应该重新调用模型）
            if hasattr(output, "__dict__"):
                # 如果是对象，添加重试标记
                new_output = f"{output}_retry_{i+1}"
            else:
                new_output = f"{str(output)}_retry_{i+1}"

            # 更新延迟
            delay = min(
                delay * self._retry_config.exponential_base,
                self._retry_config.max_delay
            )

            if self._retry_config.jitter:
                delay += random.uniform(0, delay * 0.1)

        return new_output

    async def diverse_sampling_strategy(
        self,
        output: Any,
        config: Dict[str, Any]
    ) -> List[Any]:
        """
        预定义多样性采样策略

        Args:
            output: 原始输出
            config: 配置，包含 n_samples 数量

        Returns:
            多个采样结果
        """
        n_samples = config.get("n_samples", 3)

        # 模拟多样性采样
        outputs = []
        for i in range(n_samples):
            if hasattr(output, "__dict__"):
                new_output = f"{output}_sample_{i+1}"
            else:
                new_output = f"{str(output)}_sample_{i+1}"
            outputs.append(new_output)

        return outputs

    # ==================== 便捷方法 ====================

    async def process_with_fallback(
        self,
        generator: Callable,
        fallback: Any,
        *args,
        **kwargs
    ) -> Tuple[Any, UncertaintyResult]:
        """
        带降级的处理

        Args:
            generator: 生成函数
            fallback: 降级值
            *args, **kwargs: 传递给生成函数的参数

        Returns:
            (结果, 不确定性结果)
        """
        try:
            output = await generator(*args, **kwargs)
            confidence = kwargs.get("confidence", 0.5)
            context = kwargs.get("context", {})

            result = await self.handle(output, confidence, context=context)
            result.fallback_output = fallback

            return result.result, result

        except Exception as e:
            logger.error(f"Processing failed: {e}")
            fallback_result = UncertaintyResult(
                level=UncertaintyLevel.VERY_HIGH,
                uncertainty_type=UncertaintyType.UNKNOWN,
                confidence=0.0,
                resolved=False,
                strategy_used=ResolutionStrategy.DEGRADATION,
                result=fallback,
                fallback_output=fallback,
                requires_human=True
            )
            return fallback, fallback_result

    # ==================== 统计 ====================

    def get_stats(self) -> Dict[str, Any]:
        """获取处理统计"""
        total = self._stats["total_processed"]
        if total == 0:
            return self._stats

        return {
            **self._stats,
            "resolution_rate": (
                self._stats["total_processed"] -
                self._stats["requires_human_review"]
            ) / total,
            "human_review_rate": self._stats["requires_human_review"] / total
        }

    def reset_stats(self) -> None:
        """重置统计"""
        for key in self._stats:
            self._stats[key] = 0


# 全局实例
_uncertainty_handler: Optional[UncertaintyHandler] = None


def get_uncertainty_handler() -> UncertaintyHandler:
    """获取全局不确定性处理器"""
    global _uncertainty_handler
    if _uncertainty_handler is None:
        _uncertainty_handler = UncertaintyHandler()
        # 注册预定义策略
        _uncertainty_handler.register_strategy(
            ResolutionStrategy.RETRY,
            _uncertainty_handler.retry_strategy
        )
        _uncertainty_handler.register_strategy(
            ResolutionStrategy.DIVERSE_SAMPLING,
            _uncertainty_handler.diverse_sampling_strategy
        )
    return _uncertainty_handler

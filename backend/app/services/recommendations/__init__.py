"""
推荐引擎模块 - 高性能批量推荐服务
"""
from .value_source import (
    ValueSource,
    SourcePriority,
    ValueItem,
    FieldRecommendation,
    RecommendationDecision
)

from .batch_ai_inference import BatchAIInferenceService
from .parallel_executor import ParallelRecommendationExecutor
from .high_perf_engine import HighPerformanceRecommendationEngine


__all__ = [
    # 数据结构
    'ValueSource',
    'SourcePriority',
    'ValueItem',
    'FieldRecommendation',
    'RecommendationDecision',
    
    # 服务类
    'BatchAIInferenceService',
    'ParallelRecommendationExecutor',
    'HighPerformanceRecommendationEngine'
]


def get_high_perf_engine() -> HighPerformanceRecommendationEngine:
    """获取高性能推荐引擎实例"""
    return HighPerformanceRecommendationEngine()
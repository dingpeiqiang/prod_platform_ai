"""
智能推荐引擎 - 基于历史数据的表单字段推荐系统

功能特性：
1. 多维度推荐策略：历史频率、用户个性化、时间衰减、语义相似度
2. 灵活的配置：推荐条数、策略权重、数据源
3. 上下文感知：结合用户当前输入和对话上下文
4. 结构化输出：详细的推荐结果和元数据

推荐策略：
- frequency: 基于历史填写频率
- user_personalized: 基于同一用户的历史记录
- time_decay: 时间衰减，优先推荐近期数据
- semantic_similarity: 基于语义的相似度匹配（可选）
- static: 静态配置兜底
"""

from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime
from collections import defaultdict
import logging
import time

from sqlalchemy.orm import Session
from app.core.config_loader import config_loader

from app.services.recommendations.strategies import (
    RecommendationItem,
    RecommendationResult,
    FrequencyRecommendationStrategy,
    UserPersonalizedStrategy,
    TimeDecayStrategy,
    ContextAwareStrategy
)

logger = logging.getLogger("recommendation_engine")


@dataclass
class RecommendationRequest:
    form_code: str
    field_code: str
    user_input: str
    user_id: Optional[str] = None
    conversation_context: Optional[Dict[str, Any]] = None
    max_recommendations: int = 5
    strategies: Optional[List[str]] = None
    db: Optional[Session] = None

    def __post_init__(self):
        if self.strategies is None:
            self.strategies = ["frequency", "user_personalized", "time_decay", "static"]


class RecommendationEngine:
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.recommendation_config = config_loader.get_recommendation_config()
        self._load_config()
        self._init_strategies()

    def _load_config(self):
        self.max_recommendations = self.recommendation_config.get('recommendationLimit', 5)
        self.history_query_limit = self.recommendation_config.get('historyQueryLimit', 1000)

    def _init_strategies(self):
        self.frequency_strategy = FrequencyRecommendationStrategy(self.recommendation_config)
        self.user_personalized_strategy = UserPersonalizedStrategy(self.recommendation_config)
        self.time_decay_strategy = TimeDecayStrategy(self.recommendation_config)
        self.context_aware_strategy = ContextAwareStrategy()

    def recommend(
        self,
        form_code: str,
        field_code: str,
        user_input: str,
        user_id: Optional[str] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        max_recommendations: int = 5,
        strategies: Optional[List[str]] = None,
        db: Optional[Session] = None
    ) -> RecommendationResult:
        start_time = time.time()

        if db is None:
            try:
                from app.core.database import get_db
                db_gen = get_db()
                db = next(db_gen)
            except Exception as e:
                logger.warning(f"[RecommendationEngine] 无法获取数据库连接: {e}")
                return RecommendationResult(
                    success=False,
                    field_code=field_code,
                    recommendations=[],
                    error=f"无法获取数据库连接: {e}",
                    processing_time_ms=(time.time() - start_time) * 1000
                )

        try:
            active_strategies = strategies or ["frequency", "user_personalized", "time_decay", "static"]
            strategies_used = []
            all_candidates: List[Tuple[str, RecommendationItem]] = []
            candidate_scores: Dict[str, float] = {}

            if "frequency" in active_strategies:
                strategies_used.append("frequency")
                history_candidates = self.frequency_strategy.recommend(
                    db, form_code, field_code, user_id, conversation_context
                )
                for item in history_candidates:
                    all_candidates.append((item.value, item))
                    candidate_scores[item.value] = item.score

            if "user_personalized" in active_strategies and user_id:
                strategies_used.append("user_personalized")
                user_candidates = self.user_personalized_strategy.recommend(
                    db, form_code, field_code, user_id, conversation_context
                )
                for item in user_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            if "time_decay" in active_strategies:
                strategies_used.append("time_decay")
                time_candidates = self.time_decay_strategy.recommend(
                    db, form_code, field_code, user_id
                )
                for item in time_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            if "static" in active_strategies:
                strategies_used.append("static")
                static_candidates = self._get_static_recommendations(form_code, field_code)
                for item in static_candidates:
                    if item.value not in candidate_scores:
                        all_candidates.append((item.value, item))

            if user_input and conversation_context:
                strategies_used.append("context_aware")
                context_candidates = self.context_aware_strategy.recommend(
                    user_input, form_code, field_code, conversation_context
                )
                for item in context_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            best_by_value: Dict[str, RecommendationItem] = {}
            for value, item in all_candidates:
                if value not in best_by_value or item.score > best_by_value[value].score:
                    best_by_value[value] = item
            sorted_candidates = sorted(
                best_by_value.values(),
                key=lambda x: x.score,
                reverse=True
            )

            final_recommendations = sorted_candidates[:max_recommendations]
            processing_time = (time.time() - start_time) * 1000

            result = RecommendationResult(
                success=True,
                field_code=field_code,
                recommendations=final_recommendations,
                total_candidates=len(all_candidates),
                strategy_used=strategies_used,
                processing_time_ms=processing_time
            )

            logger.info(
                f"[RecommendationEngine] 推荐完成: field={field_code}, "
                f"candidates={len(all_candidates)}, "
                f"returned={len(final_recommendations)}, "
                f"strategies={strategies_used}, "
                f"time={processing_time:.2f}ms"
            )

            return result

        except Exception as e:
            logger.exception(f"[RecommendationEngine] 推荐失败: {e}")
            return RecommendationResult(
                success=False,
                field_code=field_code,
                recommendations=[],
                error=str(e),
                processing_time_ms=(time.time() - start_time) * 1000
            )

    def _get_static_recommendations(
        self,
        form_code: str,
        field_code: str
    ) -> List[RecommendationItem]:
        try:
            static_values = config_loader.get_recommendations(form_code, field_code)

            if not static_values:
                return []

            recommendations = []

            for i, value in enumerate(static_values):
                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=0.3 - (i * 0.05),
                    source="static",
                    confidence=0.5,
                    match_type="exact",
                    reason="常用选项",
                    metadata={"staticRank": i + 1}
                ))

            return recommendations

        except Exception as e:
            logger.warning(f"[RecommendationEngine] 静态推荐失败: {e}")
            return []

    def batch_recommend(
        self,
        form_code: str,
        extracted_fields: Dict[str, str],
        user_input: str,
        user_id: Optional[str] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        max_per_field: int = 5,
        db: Optional[Session] = None,
        field_codes: Optional[List[str]] = None
    ) -> Dict[str, RecommendationResult]:
        results = {}

        target_fields = field_codes if field_codes else list(extracted_fields.keys())

        for field_code in target_fields:
            result = self.recommend(
                form_code=form_code,
                field_code=field_code,
                user_input=user_input,
                user_id=user_id,
                conversation_context={
                    **(conversation_context or {}),
                    'extractedFields': extracted_fields
                },
                max_recommendations=max_per_field,
                db=db
            )
            results[field_code] = result

        return results


_recommendation_engine: Optional[RecommendationEngine] = None


def get_recommendation_engine() -> RecommendationEngine:
    global _recommendation_engine
    if _recommendation_engine is None:
        _recommendation_engine = RecommendationEngine()
    return _recommendation_engine
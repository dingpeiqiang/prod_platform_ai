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
"""

from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime
from collections import defaultdict
import logging
import re

from sqlalchemy.orm import Session
from app.core.config_loader import config_loader
from app.models.form import FormInstance, FormTemplate, FormHistory

logger = logging.getLogger("recommendation_engine")


@dataclass
class RecommendationStrategy:
    """推荐策略配置"""
    name: str
    enabled: bool = True
    weight: float = 1.0
    params: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RecommendationItem:
    """单个推荐项"""
    value: str
    field_code: str
    score: float
    source: str  # "history", "static", "semantic"
    confidence: float  # 置信度 0-1
    match_type: str  # "exact", "fuzzy", "inferred"
    reason: str  # 推荐原因说明
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "value": self.value,
            "fieldCode": self.field_code,
            "score": round(self.score, 3),
            "source": self.source,
            "confidence": round(self.confidence, 3),
            "matchType": self.match_type,
            "reason": self.reason,
            "metadata": self.metadata
        }


@dataclass
class RecommendationResult:
    """推荐结果"""
    success: bool
    field_code: str
    recommendations: List[RecommendationItem]
    total_candidates: int = 0
    strategy_used: List[str] = field(default_factory=list)
    processing_time_ms: float = 0.0
    error: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "success": self.success,
            "fieldCode": self.field_code,
            "recommendations": [r.to_dict() for r in self.recommendations],
            "totalCandidates": self.total_candidates,
            "strategyUsed": self.strategy_used,
            "processingTimeMs": round(self.processing_time_ms, 3),
            "error": self.error
        }


@dataclass
class RecommendationRequest:
    """推荐请求"""
    form_code: str
    field_code: str
    user_input: str  # 用户当前输入上下文
    user_id: Optional[str] = None
    conversation_context: Optional[Dict[str, Any]] = None
    max_recommendations: int = 5
    strategies: Optional[List[str]] = None  # 指定使用的策略列表，None表示使用全部
    db: Optional[Session] = None

    def __post_init__(self):
        if self.strategies is None:
            self.strategies = ["frequency", "user_personalized", "time_decay", "static"]


class RecommendationEngine:
    """
    智能推荐引擎

    使用多策略融合的方式，基于历史数据生成推荐列表。
    支持的推荐策略：
    - frequency: 基于历史填写频率
    - user_personalized: 基于同一用户的历史
    - time_decay: 基于时间衰减
    - static: 静态配置兜底
    """

    # 默认策略配置
    DEFAULT_STRATEGIES = {
        "frequency": RecommendationStrategy(
            name="frequency",
            weight=0.4,
            params={"min_count": 1, "normalize": True}
        ),
        "user_personalized": RecommendationStrategy(
            name="user_personalized",
            weight=0.4,
            params={"boost_user_matches": True, "user_boost_factor": 2.0}
        ),
        "time_decay": RecommendationStrategy(
            name="time_decay",
            weight=0.2,
            params={"decay_days": 30, "min_recent_days": 7}
        ),
        "static": RecommendationStrategy(
            name="static",
            weight=0.3,
            params={"fallback_only": True}
        )
    }

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.recommendation_config = config_loader.get_recommendation_config()
        self._load_config()

    def _load_config(self):
        """从配置加载策略参数"""
        self.max_recommendations = self.recommendation_config.get('recommendationLimit', 5)
        self.history_query_limit = self.recommendation_config.get('historyQueryLimit', 1000)

        # 策略权重配置
        self.count_score_weight = self.recommendation_config.get('countScoreWeight', 0.4)
        self.user_score_weight = self.recommendation_config.get('userScoreWeight', 0.4)
        self.time_score_weight = self.recommendation_config.get('timeScoreWeight', 0.2)

        # 时间衰减配置
        self.time_decay_days = self.recommendation_config.get('timeDecayDays', 30)
        self.count_score_per_unit = self.recommendation_config.get('countScorePerUnit', 0.1)
        self.user_score_per_unit = self.recommendation_config.get('userScorePerUnit', 0.2)

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
        """
        生成推荐结果

        Args:
            form_code: 表单编码
            field_code: 字段编码
            user_input: 用户当前输入
            user_id: 用户ID
            conversation_context: 对话上下文（包含已提取的字段等）
            max_recommendations: 最大推荐条数
            strategies: 使用的策略列表，None表示全部
            db: 数据库会话，如果为None则自动获取

        Returns:
            RecommendationResult: 包含推荐结果的结构化对象
        """
        import time
        start_time = time.time()

        # 如果没有提供 db，自动获取
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
            # 初始化策略
            active_strategies = strategies or list(self.DEFAULT_STRATEGIES.keys())
            strategies_used = []

            # 收集所有候选推荐
            all_candidates: List[Tuple[str, RecommendationItem]] = []
            candidate_scores: Dict[str, float] = {}

            # 1. 历史数据推荐
            if "history" in active_strategies or "frequency" in active_strategies:
                strategies_used.append("frequency")
                history_candidates = self._get_history_recommendations(
                    db, form_code, field_code, user_id, conversation_context
                )
                for item in history_candidates:
                    all_candidates.append((item.value, item))
                    candidate_scores[item.value] = item.score

            # 2. 用户个性化推荐
            if "user_personalized" in active_strategies and user_id:
                strategies_used.append("user_personalized")
                user_candidates = self._get_user_personalized_recommendations(
                    db, form_code, field_code, user_id, conversation_context
                )
                for item in user_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            # 3. 时间衰减推荐
            if "time_decay" in active_strategies:
                strategies_used.append("time_decay")
                time_candidates = self._get_time_decay_recommendations(
                    db, form_code, field_code, user_id
                )
                for item in time_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            # 4. 静态配置兜底
            if "static" in active_strategies:
                strategies_used.append("static")
                static_candidates = self._get_static_recommendations(form_code, field_code)
                for item in static_candidates:
                    if item.value not in candidate_scores:
                        all_candidates.append((item.value, item))

            # 5. 基于输入上下文的推荐
            if user_input and conversation_context:
                strategies_used.append("context_aware")
                context_candidates = self._get_context_aware_recommendations(
                    user_input, form_code, field_code, conversation_context
                )
                for item in context_candidates:
                    if item.value not in candidate_scores or item.score > candidate_scores[item.value]:
                        all_candidates.append((item.value, item))
                        candidate_scores[item.value] = item.score

            # 去重并排序
            unique_candidates = {value: item for value, item in all_candidates}.values()
            sorted_candidates = sorted(
                unique_candidates,
                key=lambda x: x.score,
                reverse=True
            )

            # 取前 max_recommendations 条
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

    def _get_history_recommendations(
        self,
        db: Optional[Session],
        form_code: str,
        field_code: str,
        user_id: Optional[str],
        context: Optional[Dict[str, Any]]
    ) -> List[RecommendationItem]:
        """基于历史频率的推荐"""
        if not db:
            return []

        try:
            # 先通过 form_code 查出 template_id 列表，再用 template_id 查 FormInstance
            template_ids = db.query(FormTemplate.id).filter(
                FormTemplate.form_code == form_code,
                FormTemplate.is_active == True
            ).all()
            tid_list = [t.id for t in template_ids]
            if not tid_list:
                return []

            # 查询历史记录
            query_limit = self.history_query_limit
            form_instances = db.query(FormInstance).filter(
                FormInstance.template_id.in_(tid_list),
                FormInstance.status == 'submitted'
            ).order_by(
                FormInstance.submitted_at.desc()
            ).limit(query_limit).all()

            # 统计每个值的出现频率
            value_stats = defaultdict(lambda: {
                'count': 0,
                'last_used': None,
                'user_count': 0
            })

            for instance in form_instances:
                form_data = instance.data or {}
                if field_code in form_data:
                    value = str(form_data[field_code])
                    if value and value.strip():
                        value = value.strip()
                        stats = value_stats[value]
                        stats['count'] += 1

                        if instance.submitted_at:
                            if not stats['last_used'] or instance.submitted_at > stats['last_used']:
                                stats['last_used'] = instance.submitted_at

                        if user_id and instance.user_id == user_id:
                            stats['user_count'] += 1

            # 计算推荐分数
            now = datetime.now()
            recommendations = []

            for value, stats in value_stats.items():
                if stats['count'] < 1:
                    continue

                score = self._calculate_score(stats, now)

                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=score,
                    source="history",
                    confidence=min(stats['count'] / 10.0, 1.0),
                    match_type="exact",
                    reason=f"历史填写{stats['count']}次",
                    metadata={
                        "count": stats['count'],
                        "userCount": stats['user_count'],
                        "lastUsed": stats['last_used'].isoformat() if stats['last_used'] else None
                    }
                ))

            # 按分数排序
            return sorted(recommendations, key=lambda x: x.score, reverse=True)

        except Exception as e:
            logger.warning(f"[RecommendationEngine] 历史推荐查询失败: {e}")
            return []

    def _get_user_personalized_recommendations(
        self,
        db: Optional[Session],
        form_code: str,
        field_code: str,
        user_id: str,
        context: Optional[Dict[str, Any]]
    ) -> List[RecommendationItem]:
        """基于用户个性化的推荐"""
        if not db or not user_id:
            return []

        try:
            # 先通过 form_code 查出 template_id 列表
            template_ids = db.query(FormTemplate.id).filter(
                FormTemplate.form_code == form_code,
                FormTemplate.is_active == True
            ).all()
            tid_list = [t.id for t in template_ids]
            if not tid_list:
                return []

            # 查询当前用户的历史记录
            form_instances = db.query(FormInstance).filter(
                FormInstance.template_id.in_(tid_list),
                FormInstance.user_id == user_id,
                FormInstance.status == 'submitted'
            ).order_by(
                FormInstance.submitted_at.desc()
            ).limit(100).all()

            value_stats = defaultdict(lambda: {'count': 0, 'last_used': None})

            for instance in form_instances:
                form_data = instance.data or {}
                if field_code in form_data:
                    value = str(form_data[field_code]).strip()
                    if value:
                        stats = value_stats[value]
                        stats['count'] += 1
                        if instance.submitted_at:
                            if not stats['last_used'] or instance.submitted_at > stats['last_used']:
                                stats['last_used'] = instance.submitted_at

            now = datetime.now()
            recommendations = []

            for value, stats in value_stats.items():
                if stats['count'] < 1:
                    continue

                # 用户个性化分数加成
                base_score = self._calculate_score(stats, now)
                user_boost = min(stats['count'] * self.user_score_per_unit, 1.0)
                boosted_score = base_score + (user_boost * 0.5)

                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=boosted_score,
                    source="history",
                    confidence=min(stats['count'] / 5.0, 1.0),
                    match_type="exact",
                    reason=f"您历史填写{stats['count']}次",
                    metadata={"count": stats['count'], "personalized": True}
                ))

            return sorted(recommendations, key=lambda x: x.score, reverse=True)

        except Exception as e:
            logger.warning(f"[RecommendationEngine] 用户个性化推荐失败: {e}")
            return []

    def _get_time_decay_recommendations(
        self,
        db: Optional[Session],
        form_code: str,
        field_code: str,
        user_id: Optional[str]
    ) -> List[RecommendationItem]:
        """基于时间衰减的推荐"""
        if not db:
            return []

        try:
            # 先通过 form_code 查出 template_id 列表
            template_ids = db.query(FormTemplate.id).filter(
                FormTemplate.form_code == form_code,
                FormTemplate.is_active == True
            ).all()
            tid_list = [t.id for t in template_ids]
            if not tid_list:
                return []

            # 只查询近期记录
            recent_days = self.recommendation_config.get('recentDaysThreshold', 90)
            from datetime import timedelta
            cutoff_date = datetime.now() - timedelta(days=recent_days)

            form_instances = db.query(FormInstance).filter(
                FormInstance.template_id.in_(tid_list),
                FormInstance.status == 'submitted',
                FormInstance.submitted_at >= cutoff_date
            ).order_by(
                FormInstance.submitted_at.desc()
            ).limit(500).all()

            # 按时间加权统计
            value_recency = defaultdict(lambda: {'value': None, 'recency_score': 0.0, 'count': 0})

            for instance in form_instances:
                form_data = instance.data or {}
                if field_code in form_data:
                    value = str(form_data[field_code]).strip()
                    if value:
                        info = value_recency[value]
                        info['value'] = value
                        info['count'] += 1

                        if instance.submitted_at:
                            days_ago = (datetime.now() - instance.submitted_at).days
                            recency = max(0, 1.0 - (days_ago / self.time_decay_days))
                            info['recency_score'] += recency

            recommendations = []

            for value, info in value_recency.items():
                if info['count'] < 1:
                    continue

                avg_recency = info['recency_score'] / info['count']
                score = avg_recency * 1.5  # 时间衰减策略权重

                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=score,
                    source="history",
                    confidence=avg_recency,
                    match_type="exact",
                    reason="近期常用选项",
                    metadata={"recencyScore": avg_recency, "count": info['count']}
                ))

            return sorted(recommendations, key=lambda x: x.score, reverse=True)

        except Exception as e:
            logger.warning(f"[RecommendationEngine] 时间衰减推荐失败: {e}")
            return []

    def _get_static_recommendations(
        self,
        form_code: str,
        field_code: str
    ) -> List[RecommendationItem]:
        """静态配置兜底推荐"""
        try:
            static_values = config_loader.get_recommendations(form_code, field_code)

            if not static_values:
                return []

            recommendations = []

            for i, value in enumerate(static_values):
                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=0.3 - (i * 0.05),  # 递减分数
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

    def _get_context_aware_recommendations(
        self,
        user_input: str,
        form_code: str,
        field_code: str,
        context: Dict[str, Any]
    ) -> List[RecommendationItem]:
        """基于上下文的推荐 - 分析用户输入中的潜在意图"""
        recommendations = []

        try:
            # 从已提取的字段中寻找关联
            extracted_fields = context.get('extractedFields', {})

            # 例如：如果用户提到了某个金额，提取相关的金额模式
            if field_code in ['order_amount', 'amount', 'contract_amount']:
                amount_pattern = r'(\d+(?:\.\d+)?)\s*(?:万|元|千)?'
                matches = re.findall(amount_pattern, user_input)
                if matches:
                    amount = matches[0]
                    recommendations.append(RecommendationItem(
                        value=amount,
                        field_code=field_code,
                        score=0.9,
                        source="context",
                        confidence=0.8,
                        match_type="inferred",
                        reason="从您输入的金额推断",
                        metadata={"inferredFrom": "user_input"}
                    ))

            # 从已提取的字段推断相关字段
            if 'customer_name' in extracted_fields and field_code == 'customer_phone':
                # 如果用户提到了客户名，查询该客户的历史电话
                customer_name = extracted_fields['customer_name']
                recommendations.append(RecommendationItem(
                    value=f"客户{customer_name}的历史电话",
                    field_code=field_code,
                    score=0.85,
                    source="context",
                    confidence=0.7,
                    match_type="inferred",
                    reason=f"基于您提到的客户{customer_name}",
                    metadata={"relatedField": "customer_name"}
                ))

        except Exception as e:
            logger.warning(f"[RecommendationEngine] 上下文推荐失败: {e}")

        return recommendations

    def _calculate_score(
        self,
        stats: Dict[str, Any],
        now: datetime
    ) -> float:
        """计算综合评分"""
        count = stats.get('count', 0)
        user_count = stats.get('user_count', 0)
        last_used = stats.get('last_used')

        # 频率得分
        count_score = min(count * self.count_score_per_unit, 1.0)

        # 用户得分
        user_score = min(user_count * self.user_score_per_unit, 1.0)

        # 时间得分
        time_score = 1.0
        if last_used:
            days_since = (now - last_used).days
            if days_since > 0:
                time_score = max(0.0, 1.0 - (days_since / self.time_decay_days))

        # 综合评分
        final_score = (
            count_score * self.count_score_weight +
            user_score * self.user_score_weight +
            time_score * self.time_score_weight
        )

        return final_score

    def batch_recommend(
        self,
        form_code: str,
        extracted_fields: Dict[str, str],
        user_input: str,
        user_id: Optional[str] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        max_per_field: int = 5,
        db: Optional[Session] = None
    ) -> Dict[str, RecommendationResult]:
        """
        批量推荐 - 为多个字段生成推荐

        Args:
            form_code: 表单编码
            extracted_fields: 已提取的字段 dict[field_code] = value
            user_input: 用户当前输入
            user_id: 用户ID
            conversation_context: 对话上下文
            max_per_field: 每个字段的最大推荐数
            db: 数据库会话

        Returns:
            Dict[field_code, RecommendationResult]: 每个字段的推荐结果
        """
        results = {}

        for field_code in extracted_fields.keys():
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


# 全局实例
_recommendation_engine: Optional[RecommendationEngine] = None


def get_recommendation_engine() -> RecommendationEngine:
    """获取推荐引擎全局实例"""
    global _recommendation_engine
    if _recommendation_engine is None:
        _recommendation_engine = RecommendationEngine()
    return _recommendation_engine

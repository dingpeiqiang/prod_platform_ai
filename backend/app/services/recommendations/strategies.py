from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict
import logging
import re

from sqlalchemy.orm import Session
from app.models.form import FormInstance

logger = logging.getLogger("recommendation_strategies")


@dataclass
class RecommendationStrategy:
    name: str
    enabled: bool = True
    weight: float = 1.0
    params: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RecommendationItem:
    value: str
    field_code: str
    score: float
    source: str
    confidence: float
    match_type: str
    reason: str
    metadata: Dict[str, Any] = field(default_factory=dict)
    priority: Optional[int] = None

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


class FrequencyRecommendationStrategy:
    """基于历史频率的推荐策略"""

    def __init__(self, config: Dict[str, Any]):
        self.count_score_weight = config.get('countScoreWeight', 0.4)
        self.time_score_weight = config.get('timeScoreWeight', 0.2)
        self.count_score_per_unit = config.get('countScorePerUnit', 0.1)
        self.time_decay_days = config.get('timeDecayDays', 30)
        self.history_query_limit = config.get('historyQueryLimit', 1000)

    def recommend(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str] = None,
        context: Optional[Dict[str, Any]] = None
    ) -> List[RecommendationItem]:
        try:
            form_instances = db.query(FormInstance).filter(
                FormInstance.form_code == form_code,
                FormInstance.status == 'submitted'
            ).order_by(
                FormInstance.submitted_at.desc()
            ).limit(self.history_query_limit).all()

            if not form_instances:
                return []

            extracted_fields = {}
            if context and isinstance(context, dict):
                extracted_fields = context.get('extractedFields', {}) or {}

            filtered_instances = form_instances
            filter_field_count = 0

            if extracted_fields:
                matched = []
                for inst in form_instances:
                    data = inst.data or {}
                    match_count = 0
                    total_checkable = 0

                    for ef_key, ef_val in extracted_fields.items():
                        ef_val_str = str(ef_val).strip().lower() if ef_val else ""
                        if not ef_val_str:
                            continue
                        total_checkable += 1

                        hist_val = str(data.get(ef_key, "")).strip().lower() if data.get(ef_key) else ""
                        if hist_val == ef_val_str:
                            match_count += 1
                        elif ef_val_str and hist_val and (
                            ef_val_str in hist_val or hist_val in ef_val_str
                        ):
                            match_count += 0.5

                    if total_checkable == 0 or (total_checkable > 0 and match_count > 0):
                        inst._match_score = match_count / max(total_checkable, 1)
                        matched.append(inst)

                if matched:
                    filtered_instances = matched
                    filter_field_count = len(extracted_fields)

            value_stats = defaultdict(lambda: {
                'count': 0,
                'last_used': None,
                'user_count': 0,
                'weighted_sum': 0.0
            })

            for instance in filtered_instances:
                form_data = instance.data or {}
                if field_code in form_data:
                    value = str(form_data[field_code])
                    if value and value.strip():
                        value = value.strip()
                        stats = value_stats[value]
                        stats['count'] += 1

                        match_score = getattr(instance, '_match_score', None)
                        weight = match_score if match_score is not None else 1.0
                        stats['weighted_sum'] += weight

                        if instance.submitted_at:
                            if not stats['last_used'] or instance.submitted_at > stats['last_used']:
                                stats['last_used'] = instance.submitted_at

                        if user_id and instance.user_id == user_id:
                            stats['user_count'] += 1

            now = datetime.now()
            recommendations = []

            for value, stats in value_stats.items():
                if stats['count'] < 1:
                    continue

                score = self._calculate_score(stats, now)

                if filter_field_count > 0 and len(form_instances) != len(filtered_instances):
                    reason = f"基于{len(filtered_instances)}条相似记录推断"
                    source = "inference"
                    confidence = min(stats['weighted_sum'] / max(len(filtered_instances), 1), 1.0)
                else:
                    reason = f"历史填写{stats['count']}次"
                    source = "history"
                    confidence = min(stats['count'] / 10.0, 1.0)

                recommendations.append(RecommendationItem(
                    value=value,
                    field_code=field_code,
                    score=score,
                    source=source,
                    confidence=confidence,
                    match_type="exact",
                    reason=reason,
                    metadata={
                        "count": stats['count'],
                        "userCount": stats['user_count'],
                        "lastUsed": stats['last_used'].isoformat() if stats['last_used'] else None,
                        "filteredCount": len(filtered_instances) if filter_field_count > 0 else None,
                        "totalCount": len(form_instances),
                        "filterFieldCount": filter_field_count
                    }
                ))

            return sorted(recommendations, key=lambda x: x.score, reverse=True)

        except Exception as e:
            logger.warning(f"[FrequencyStrategy] 推荐查询失败: {e}")
            return []

    def _calculate_score(self, stats: Dict[str, Any], now: datetime) -> float:
        count = stats.get('count', 0)
        user_count = stats.get('user_count', 0)
        last_used = stats.get('last_used')

        count_score = min(count * self.count_score_per_unit, 1.0)

        time_score = 1.0
        if last_used:
            days_since = (now - last_used).days
            if days_since > 0:
                time_score = max(0.0, 1.0 - (days_since / self.time_decay_days))

        return count_score * self.count_score_weight + time_score * self.time_score_weight


class UserPersonalizedStrategy:
    """基于用户个性化的推荐策略"""

    def __init__(self, config: Dict[str, Any]):
        self.user_score_weight = config.get('userScoreWeight', 0.4)
        self.user_score_per_unit = config.get('userScorePerUnit', 0.2)

    def recommend(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: str,
        context: Optional[Dict[str, Any]] = None
    ) -> List[RecommendationItem]:
        if not db or not user_id:
            return []

        try:
            form_instances = db.query(FormInstance).filter(
                FormInstance.form_code == form_code,
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
            logger.warning(f"[UserPersonalizedStrategy] 推荐失败: {e}")
            return []

    def _calculate_score(self, stats: Dict[str, Any], now: datetime) -> float:
        count = stats.get('count', 0)
        last_used = stats.get('last_used')

        count_score = min(count * 0.1, 1.0)

        time_score = 1.0
        if last_used:
            days_since = (now - last_used).days
            time_score = max(0.0, 1.0 - (days_since / 30))

        return count_score * 0.5 + time_score * 0.5


class TimeDecayStrategy:
    """基于时间衰减的推荐策略"""

    def __init__(self, config: Dict[str, Any]):
        self.time_decay_days = config.get('timeDecayDays', 30)
        self.recent_days_threshold = config.get('recentDaysThreshold', 90)

    def recommend(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str] = None
    ) -> List[RecommendationItem]:
        if not db:
            return []

        try:
            cutoff_date = datetime.now() - timedelta(days=self.recent_days_threshold)

            form_instances = db.query(FormInstance).filter(
                FormInstance.form_code == form_code,
                FormInstance.status == 'submitted',
                FormInstance.submitted_at >= cutoff_date
            ).order_by(
                FormInstance.submitted_at.desc()
            ).limit(500).all()

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
                score = avg_recency * 1.5

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
            logger.warning(f"[TimeDecayStrategy] 推荐失败: {e}")
            return []


class ContextAwareStrategy:
    """基于上下文的推荐策略"""

    def recommend(
        self,
        user_input: str,
        form_code: str,
        field_code: str,
        context: Dict[str, Any]
    ) -> List[RecommendationItem]:
        recommendations = []

        try:
            extracted_fields = context.get('extractedFields', {})

            # 【新增】资费备案表单的本体规则推断
            if form_code == 'tariff_filing_publicity':
                # 从用户输入中提取套餐编码（P开头+数字）
                if field_code == 'bossid':
                    tariff_pattern = r'(P\d{6,})'
                    matches = re.findall(tariff_pattern, user_input, re.IGNORECASE)
                    if matches:
                        recommendations.append(RecommendationItem(
                            value=matches[0].upper(),
                            field_code=field_code,
                            score=0.95,
                            source="llm_rule",
                            confidence=0.9,
                            match_type="inferred",
                            reason="从您输入的套餐编码提取",
                            metadata={"inferredFrom": "user_input", "pattern": "tariff_code"}
                        ))
                
                # 根据操作类型推断
                if field_code == 'action_type':
                    if '新增' in user_input or '新建' in user_input or 'add' in user_input.lower():
                        recommendations.append(RecommendationItem(
                            value="新增",
                            field_code=field_code,
                            score=0.9,
                            source="llm_rule",
                            confidence=0.85,
                            match_type="inferred",
                            reason="基于本体规则推断",
                            metadata={"inferredFrom": "keyword_match"}
                        ))
                    elif '修改' in user_input or '更新' in user_input or 'update' in user_input.lower():
                        recommendations.append(RecommendationItem(
                            value="修改",
                            field_code=field_code,
                            score=0.9,
                            source="llm_rule",
                            confidence=0.85,
                            match_type="inferred",
                            reason="基于本体规则推断",
                            metadata={"inferredFrom": "keyword_match"}
                        ))
                    elif '删除' in user_input or 'delete' in user_input.lower():
                        recommendations.append(RecommendationItem(
                            value="删除",
                            field_code=field_code,
                            score=0.9,
                            source="llm_rule",
                            confidence=0.85,
                            match_type="inferred",
                            reason="基于本体规则推断",
                            metadata={"inferredFrom": "keyword_match"}
                        ))

            # 原有的通用规则
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

            if 'customer_name' in extracted_fields and field_code == 'customer_phone':
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
            logger.warning(f"[ContextAwareStrategy] 推荐失败: {e}")

        return recommendations
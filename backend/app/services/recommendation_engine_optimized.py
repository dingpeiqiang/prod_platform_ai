"""
历史推荐优化版本 - 大数据量场景

优化点：
1. SQL聚合替代Python聚合：减少数据传输
2. 多级缓存：内存缓存 + Redis缓存
3. 聚合表预计算：定期任务更新统计结果
4. 并行查询：多个策略并行执行
"""

from typing import Dict, Any, List, Optional, Callable
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict
from functools import lru_cache
import logging
import hashlib
import json
import time
import asyncio

from sqlalchemy import text
from sqlalchemy.orm import Session
from app.core.config_loader import config_loader
from app.models.form import FormInstance, FormTemplate, FormHistory

logger = logging.getLogger("recommendation_engine_optimized")

try:
    import redis
    REDIS_AVAILABLE = True
except ImportError:
    REDIS_AVAILABLE = False


class RecommendationCache:
    """多级缓存管理器"""

    def __init__(self, redis_url: Optional[str] = None, local_ttl: int = 300, redis_ttl: int = 3600):
        self.local_cache: Dict[str, tuple] = {}
        self.local_ttl = local_ttl
        self.redis_ttl = redis_ttl
        self.redis_client = None

        if redis_url and REDIS_AVAILABLE:
            try:
                self.redis_client = redis.from_url(redis_url, decode_responses=True)
                self.redis_client.ping()
                logger.info("[RecommendationCache] Redis连接成功")
            except Exception as e:
                logger.warning(f"[RecommendationCache] Redis连接失败: {e}")
                self.redis_client = None

    def _make_key(self, form_code: str, field_code: str, user_id: Optional[str]) -> str:
        key_data = f"{form_code}:{field_code}:{user_id or 'anonymous'}"
        return f"rec:{hashlib.md5(key_data.encode()).hexdigest()}"

    def get(self, form_code: str, field_code: str, user_id: Optional[str] = None) -> Optional[List[Dict]]:
        key = self._make_key(form_code, field_code, user_id)

        if key in self.local_cache:
            expire_time, data = self.local_cache[key]
            if time.time() < expire_time:
                logger.debug(f"[RecommendationCache] 命中本地缓存: {key}")
                return data
            else:
                del self.local_cache[key]

        if self.redis_client:
            try:
                data = self.redis_client.get(key)
                if data:
                    result = json.loads(data)
                    self.local_cache[key] = (time.time() + self.local_ttl, result)
                    logger.debug(f"[RecommendationCache] 命中Redis缓存: {key}")
                    return result
            except Exception as e:
                logger.warning(f"[RecommendationCache] Redis获取失败: {e}")

        return None

    def set(self, form_code: str, field_code: str, data: List[Dict], user_id: Optional[str] = None):
        key = self._make_key(form_code, field_code, user_id)
        self.local_cache[key] = (time.time() + self.local_ttl, data)

        if self.redis_client:
            try:
                self.redis_client.setex(key, self.redis_ttl, json.dumps(data, default=str))
                logger.debug(f"[RecommendationCache] 已写入Redis: {key}")
            except Exception as e:
                logger.warning(f"[RecommendationCache] Redis写入失败: {e}")

    def invalidate(self, form_code: Optional[str] = None):
        if form_code:
            pattern = f"rec:*"
            keys_to_delete = []
            if self.redis_client:
                try:
                    for key in self.redis_client.scan_iter(match=pattern):
                        if form_code in key:
                            keys_to_delete.append(key)
                    if keys_to_delete:
                        self.redis_client.delete(*keys_to_delete)
                except Exception as e:
                    logger.warning(f"[RecommendationCache] Redis批量删除失败: {e}")

        self.local_cache.clear()
        logger.info(f"[RecommendationCache] 缓存已清除")


class OptimizedRecommendationEngine:
    """
    优化版推荐引擎

    优化策略：
    1. SQL聚合：使用数据库GROUP BY，减少数据传输
    2. 多级缓存：本地内存缓存 + Redis分布式缓存
    3. 聚合表：定期预计算统计结果
    4. 降级策略：缓存失效时优雅降级
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.recommendation_config = config_loader.get_recommendation_config()
        self._load_config()
        self._init_cache()

    def _load_config(self):
        self.max_recommendations = self.recommendation_config.get('recommendationLimit', 5)
        self.history_query_limit = self.recommendation_config.get('historyQueryLimit', 1000)
        self.count_score_weight = self.recommendation_config.get('countScoreWeight', 0.4)
        self.user_score_weight = self.recommendation_config.get('userScoreWeight', 0.4)
        self.time_score_weight = self.recommendation_config.get('timeScoreWeight', 0.2)
        self.time_decay_days = self.recommendation_config.get('timeDecayDays', 30)
        self.cache_enabled = self.recommendation_config.get('cacheEnabled', True)
        self.redis_url = self.recommendation_config.get('redisUrl')

    def _init_cache(self):
        if self.cache_enabled:
            self.cache = RecommendationCache(
                redis_url=self.redis_url,
                local_ttl=300,
                redis_ttl=3600
            )
        else:
            self.cache = None

    def _get_cached_or_compute(
        self,
        form_code: str,
        field_code: str,
        user_id: Optional[str],
        compute_func: Callable
    ) -> List[Dict]:
        """缓存查询结果，获取或计算"""
        if self.cache:
            cached = self.cache.get(form_code, field_code, user_id)
            if cached is not None:
                return cached

        result = compute_func()

        if self.cache and result:
            self.cache.set(form_code, field_code, result, user_id)

        return result

    def sql_aggregate_recommendations(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str] = None,
        days_threshold: int = 90
    ) -> List[Dict[str, Any]]:
        """
        使用SQL聚合替代Python聚合

        直接在数据库层面完成统计，减少数据传输
        """
        cutoff_date = datetime.now() - timedelta(days=days_threshold)

        if user_id:
            sql = text("""
                SELECT
                    f.data->>:field_code as field_value,
                    COUNT(*) as total_count,
                    SUM(CASE WHEN f.user_id = :user_id THEN 1 ELSE 0 END) as user_count,
                    MAX(f.submitted_at) as last_used_at,
                    SUM(CASE WHEN f.submitted_at >= :cutoff_date THEN 1 ELSE 0 END) as recent_count
                FROM form_instances f
                WHERE f.form_code = :form_code
                    AND f.status = 'submitted'
                    AND f.data->>:field_code IS NOT NULL
                    AND f.data->>:field_code != ''
                GROUP BY f.data->>:field_code
                ORDER BY total_count DESC
                LIMIT :limit
            """)
            params = {
                "form_code": form_code,
                "field_code": field_code,
                "user_id": user_id,
                "cutoff_date": cutoff_date,
                "limit": self.max_recommendations * 2
            }
        else:
            sql = text("""
                SELECT
                    f.data->>:field_code as field_value,
                    COUNT(*) as total_count,
                    0 as user_count,
                    MAX(f.submitted_at) as last_used_at,
                    SUM(CASE WHEN f.submitted_at >= :cutoff_date THEN 1 ELSE 0 END) as recent_count
                FROM form_instances f
                WHERE f.form_code = :form_code
                    AND f.status = 'submitted'
                    AND f.data->>:field_code IS NOT NULL
                    AND f.data->>:field_code != ''
                GROUP BY f.data->>:field_code
                ORDER BY total_count DESC
                LIMIT :limit
            """)
            params = {
                "form_code": form_code,
                "field_code": field_code,
                "cutoff_date": cutoff_date,
                "limit": self.max_recommendations * 2
            }

        try:
            result = db.execute(sql, params)
            rows = result.fetchall()

            recommendations = []
            now = datetime.now()

            for row in rows:
                field_value = row.field_value
                if not field_value:
                    continue

                total_count = row.total_count or 0
                user_count = row.user_count or 0
                last_used_at = row.last_used_at
                recent_count = row.recent_count or 0

                score = self._calculate_sql_score(
                    total_count=total_count,
                    user_count=user_count,
                    last_used_at=last_used_at,
                    now=now
                )

                recommendations.append({
                    "value": field_value,
                    "field_code": field_code,
                    "score": score,
                    "source": "history",
                    "confidence": min(total_count / 10.0, 1.0),
                    "match_type": "exact",
                    "reason": f"历史填写{total_count}次",
                    "metadata": {
                        "totalCount": total_count,
                        "userCount": user_count,
                        "recentCount": recent_count,
                        "lastUsed": last_used_at.isoformat() if last_used_at else None
                    }
                })

            return sorted(recommendations, key=lambda x: x["score"], reverse=True)[:self.max_recommendations]

        except Exception as e:
            logger.warning(f"[OptimizedEngine] SQL聚合查询失败，回退到ORM: {e}")
            return self._fallback_orm_query(db, form_code, field_code, user_id)

    def _calculate_sql_score(
        self,
        total_count: int,
        user_count: int,
        last_used_at: Optional[datetime],
        now: datetime
    ) -> float:
        """基于SQL聚合结果计算分数"""
        count_score = min(total_count * 0.1, 1.0)
        user_score = min(user_count * 0.2, 1.0)

        time_score = 1.0
        if last_used_at:
            days_since = (now - last_used_at).days
            if days_since > 0:
                time_score = max(0.0, 1.0 - (days_since / self.time_decay_days))

        return (
            count_score * self.count_score_weight +
            user_score * self.user_score_weight +
            time_score * self.time_score_weight
        )

    def _fallback_orm_query(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str]
    ) -> List[Dict]:
        """ORM查询降级方案"""
        try:
            cutoff_date = datetime.now() - timedelta(days=self.recommendation_config.get('recentDaysThreshold', 90))

            # 先通过 form_code 查出 template_id 列表
            template_ids = db.query(FormTemplate.id).filter(
                FormTemplate.form_code == form_code,
                FormTemplate.is_active == True
            ).all()
            tid_list = [t.id for t in template_ids]
            if not tid_list:
                return []

            query = db.query(
                FormInstance.data,
                FormInstance.user_id,
                FormInstance.submitted_at
            ).filter(
                FormInstance.template_id.in_(tid_list),
                FormInstance.status == 'submitted',
                FormInstance.submitted_at >= cutoff_date
            ).limit(self.history_query_limit)

            instances = query.all()

            value_stats = defaultdict(lambda: {
                'count': 0, 'user_count': 0, 'last_used': None
            })

            for instance in instances:
                data = instance.data or {}
                if field_code in data:
                    value = str(data[field_code]).strip()
                    if value:
                        stats = value_stats[value]
                        stats['count'] += 1
                        if instance.submitted_at and (not stats['last_used'] or instance.submitted_at > stats['last_used']):
                            stats['last_used'] = instance.submitted_at
                        if user_id and instance.user_id == user_id:
                            stats['user_count'] += 1

            now = datetime.now()
            recommendations = []

            for value, stats in value_stats.items():
                if stats['count'] < 1:
                    continue

                score = self._calculate_sql_score(
                    stats['count'],
                    stats['user_count'],
                    stats['last_used'],
                    now
                )

                recommendations.append({
                    "value": value,
                    "field_code": field_code,
                    "score": score,
                    "source": "history",
                    "confidence": min(stats['count'] / 10.0, 1.0),
                    "match_type": "exact",
                    "reason": f"历史填写{stats['count']}次",
                    "metadata": {"totalCount": stats['count'], "userCount": stats['user_count']}
                })

            return sorted(recommendations, key=lambda x: x["score"], reverse=True)[:self.max_recommendations]

        except Exception as e:
            logger.error(f"[OptimizedEngine] ORM查询也失败: {e}")
            return []

    def recommend(
        self,
        form_code: str,
        field_code: str,
        user_input: str = "",
        user_id: Optional[str] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        max_recommendations: int = 5,
        strategies: Optional[List[str]] = None,
        db: Optional[Session] = None
    ) -> Dict[str, Any]:
        """
        生成推荐结果（优化版）

        优先使用缓存，缓存失效时使用SQL聚合
        """
        import time
        start_time = time.time()

        if db is None:
            try:
                from app.core.database import get_db
                db_gen = get_db()
                db = next(db_gen)
            except Exception as e:
                logger.warning(f"[OptimizedEngine] 无法获取数据库连接: {e}")
                return {
                    "success": False,
                    "field_code": field_code,
                    "recommendations": [],
                    "error": str(e),
                    "processing_time_ms": 0
                }

        def compute():
            return self.sql_aggregate_recommendations(db, form_code, field_code, user_id)

        recommendations = self._get_cached_or_compute(form_code, field_code, user_id, compute)

        processing_time = (time.time() - start_time) * 1000

        return {
            "success": True,
            "field_code": field_code,
            "recommendations": recommendations[:max_recommendations],
            "total_candidates": len(recommendations),
            "strategy_used": ["sql_aggregate", "cache"],
            "processing_time_ms": processing_time,
            "from_cache": self.cache is not None and self.cache.get(form_code, field_code, user_id) is not None
        }


_cache_instance: Optional[RecommendationCache] = None
_engine_instance: Optional[OptimizedRecommendationEngine] = None


def get_optimized_recommendation_engine() -> OptimizedRecommendationEngine:
    global _engine_instance
    if _engine_instance is None:
        _engine_instance = OptimizedRecommendationEngine()
    return _engine_instance


def get_recommendation_cache() -> RecommendationCache:
    global _cache_instance
    if _cache_instance is None:
        _cache_instance = RecommendationCache()
    return _cache_instance

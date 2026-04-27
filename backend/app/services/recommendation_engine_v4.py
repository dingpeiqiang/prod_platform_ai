"""
智能推荐引擎 v4 - 综合版（Schema约束 + 数据模型适配）

同时解决：
1. 不同业务表单的数据模型差异（嵌套结构、字段名不一致）
2. 不同字段类型的推荐策略（枚举、历史值、数值范围）
3. 性能优化（SQL聚合、多级缓存）

使用方式：
from app.services.recommendation_engine_v4 import get_recommendation_engine_v4
"""

from typing import Dict, Any, List, Optional, Set
from dataclasses import dataclass
from datetime import datetime, timedelta
from collections import defaultdict
import logging
import re
import hashlib

from sqlalchemy.orm import Session
from sqlalchemy import text
from app.core.config_loader import config_loader
from app.models.form import FormInstance

logger = logging.getLogger("recommendation_engine_v4")


class FieldMapper:
    """字段映射器 - 适配不同业务数据模型"""

    def __init__(self):
        self._mappings: Dict[str, Dict[str, str]] = {}
        self._load_default_mappings()

    def _load_default_mappings(self):
        """加载默认映射：form_code -> {field_code: json_path}"""
        self._mappings = {
            "leave": {
                "leave_type": "leave_type",
                "leave_days": "leave_days",
                "reason": "reason",
                "start_date": "start_date",
                "end_date": "end_date",
                "applicant_name": "applicant.name",
                "department": "applicant.dept",
            },
            "sales_order": {
                "customer_name": "customer_name",
                "customer_phone": "customer_phone",
                "order_amount": "order_amount",
                "order_date": "order_date",
                "remark": "remark",
                "items_total": "items[0].price",
            },
            "expense": {
                "expense_type": "expense_type",
                "amount": "amount",
                "description": "description",
                "receipt_ids": "receipt_ids",
            }
        }

    def get_path(self, form_code: str, field_code: str) -> str:
        """获取字段的 JSON 路径"""
        return self._mappings.get(form_code, {}).get(field_code, field_code)

    def extract(self, data: Dict[str, Any], json_path: str) -> Optional[Any]:
        """从 JSON 数据中提取值"""
        if not json_path or not data:
            return None

        try:
            parts = json_path.split('.')
            current = data

            for part in parts:
                if isinstance(current, dict):
                    current = current.get(part)
                elif isinstance(current, list) and part.isdigit():
                    idx = int(part)
                    current = current[idx] if len(current) > idx else None
                else:
                    return None

                if current is None:
                    return None

            return current if not isinstance(current, (dict, list)) else None

        except Exception:
            return None


class SchemaRegistry:
    """Schema 注册表"""

    def __init__(self):
        self._schemas: Dict[str, Dict[str, Any]] = {}
        self._load_schemas()

    def _load_schemas(self):
        try:
            schemas = config_loader.get_all_schemas()
            for form_code, schema_info in schemas.items():
                field_map = {}
                for f in schema_info.get("fields", []):
                    field_map[f.get("name", "")] = f
                self._schemas[form_code] = field_map
            logger.info(f"[SchemaRegistry] 已加载 {len(self._schemas)} 个表单 Schema")
        except Exception as e:
            logger.warning(f"[SchemaRegistry] 加载失败: {e}")

    def get_field(self, form_code: str, field_code: str) -> Optional[Dict]:
        schema = self._schemas.get(form_code, {})
        return schema.get(field_code)

    def is_enum(self, form_code: str, field_code: str) -> bool:
        field = self.get_field(form_code, field_code)
        return field is not None and field.get("type") == "enum" and field.get("enumValues")

    def get_enum_values(self, form_code: str, field_code: str) -> Optional[List[str]]:
        field = self.get_field(form_code, field_code)
        if field and field.get("enumValues"):
            return field["enumValues"]
        return None

    def validate_value(self, form_code: str, field_code: str, value: str) -> bool:
        """验证值是否符合 Schema 定义"""
        field = self.get_field(form_code, field_code)
        if not field:
            return True

        field_type = field.get("type", "string")

        if field_type == "integer":
            try:
                int(value)
                return True
            except (ValueError, TypeError):
                return False

        if field_type == "number":
            try:
                float(value)
                return True
            except (ValueError, TypeError):
                return False

        if field_type == "boolean":
            return value.lower() in ("true", "false", "1", "0", "yes", "no")

        if field_type == "date":
            return bool(re.match(r"\d{4}-\d{2}-\d{2}", str(value)))

        return True


class RecommendationCache:
    """本地缓存"""

    def __init__(self, ttl: int = 300):
        self._cache: Dict[str, tuple] = {}
        self._ttl = ttl

    def _make_key(self, form_code: str, field_code: str, user_id: Optional[str]) -> str:
        key_data = f"{form_code}:{field_code}:{user_id or 'anonymous'}"
        return hashlib.md5(key_data.encode()).hexdigest()

    def get(self, form_code: str, field_code: str, user_id: Optional[str] = None) -> Optional[List[Dict]]:
        key = self._make_key(form_code, field_code, user_id)
        if key in self._cache:
            expire_time, data = self._cache[key]
            if datetime.now().timestamp() < expire_time:
                return data
            del self._cache[key]
        return None

    def set(self, form_code: str, field_code: str, data: List[Dict], user_id: Optional[str] = None):
        key = self._make_key(form_code, field_code, user_id)
        self._cache[key] = (datetime.now().timestamp() + self._ttl, data)


class RecommendationEngineV4:
    """
    智能推荐引擎 v4 - 综合版

    核心能力：
    1. Schema 约束：枚举类型直接返回 enumValues
    2. 数据模型适配：通过 FieldMapper 适配不同表单的嵌套结构
    3. 历史值验证：历史值必须符合 Schema 类型定义
    4. 多级缓存：本地缓存加速
    5. SQL聚合：减少数据传输
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.field_mapper = FieldMapper()
        self.schema_registry = SchemaRegistry()
        self.cache = RecommendationCache(ttl=300)
        self._load_config()

    def _load_config(self):
        rec_config = self.config.get("recommendation", {})
        self.max_recommendations = rec_config.get("recommendationLimit", 5)
        self.history_query_limit = rec_config.get("historyQueryLimit", 1000)

    def recommend(
        self,
        form_code: str,
        field_code: str,
        user_input: str = "",
        user_id: Optional[str] = None,
        db: Optional[Session] = None,
        max_recommendations: int = 5
    ) -> Dict[str, Any]:
        """
        生成推荐结果

        推荐策略优先级：
        1. 枚举字段 → enumValues
        2. 历史高频值 → SQL 聚合
        3. 静态配置兜底
        """
        import time
        start_time = time.time()

        cached = self.cache.get(form_code, field_code, user_id)
        if cached:
            return {
                "success": True,
                "field_code": field_code,
                "recommendations": cached[:max_recommendations],
                "total_candidates": len(cached),
                "strategy_used": ["cache"],
                "processing_time_ms": (time.time() - start_time) * 1000,
                "from_cache": True
            }

        recommendations = []
        enum_values = self.schema_registry.get_enum_values(form_code, field_code)

        if enum_values:
            recommendations = self._build_enum_recommendations(field_code, enum_values)
        else:
            if db is None:
                try:
                    from app.core.database import get_db
                    db_gen = get_db()
                    db = next(db_gen)
                except Exception as e:
                    logger.warning(f"[V4Engine] DB获取失败: {e}")
                    return {"success": False, "field_code": field_code, "error": str(e)}

            history_recs = self._sql_aggregate_recommendations(db, form_code, field_code, user_id)
            recommendations.extend(history_recs)

            static_recs = self._get_static_recommendations(form_code, field_code)
            recommendations.extend(static_recs)

        recommendations = self._deduplicate_and_limit(recommendations, max_recommendations)
        self.cache.set(form_code, field_code, recommendations, user_id)

        return {
            "success": True,
            "field_code": field_code,
            "recommendations": recommendations,
            "total_candidates": len(recommendations),
            "strategy_used": self._get_strategies(enum_values, recommendations),
            "processing_time_ms": (time.time() - start_time) * 1000,
            "from_cache": False
        }

    def _build_enum_recommendations(self, field_code: str, enum_values: List[str]) -> List[Dict]:
        return [
            {
                "value": value,
                "field_code": field_code,
                "score": 1.0 - (i * 0.01),
                "source": "enum",
                "confidence": 1.0,
                "match_type": "exact",
                "reason": "枚举选项",
                "metadata": {"enumRank": i + 1}
            }
            for i, value in enumerate(enum_values)
        ]

    def _sql_aggregate_recommendations(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str]
    ) -> List[Dict]:
        """
        SQL 聚合查询 - 适配不同数据模型

        通过 field_mapper 获取正确的 JSON 路径进行提取
        """
        json_path = self.field_mapper.get_path(form_code, field_code)
        cutoff_date = datetime.now() - timedelta(days=90)

        if user_id:
            sql = text(f"""
                SELECT
                    f.data->>'{json_path}' as field_value,
                    COUNT(*) as total_count,
                    SUM(CASE WHEN f.user_id = :user_id THEN 1 ELSE 0 END) as user_count,
                    MAX(f.submitted_at) as last_used_at
                FROM form_instances f
                WHERE f.form_code = :form_code
                    AND f.status = 'submitted'
                    AND f.submitted_at >= :cutoff_date
                    AND f.data->>'{json_path}' IS NOT NULL
                    AND f.data->>'{json_path}' != ''
                GROUP BY f.data->>'{json_path}'
                ORDER BY total_count DESC
                LIMIT 20
            """)
            params = {"form_code": form_code, "user_id": user_id, "cutoff_date": cutoff_date}
        else:
            sql = text(f"""
                SELECT
                    f.data->>'{json_path}' as field_value,
                    COUNT(*) as total_count,
                    0 as user_count,
                    MAX(f.submitted_at) as last_used_at
                FROM form_instances f
                WHERE f.form_code = :form_code
                    AND f.status = 'submitted'
                    AND f.submitted_at >= :cutoff_date
                    AND f.data->>'{json_path}' IS NOT NULL
                    AND f.data->>'{json_path}' != ''
                GROUP BY f.data->>'{json_path}'
                ORDER BY total_count DESC
                LIMIT 20
            """)
            params = {"form_code": form_code, "cutoff_date": cutoff_date}

        try:
            result = db.execute(sql, params)
            rows = result.fetchall()

            recommendations = []
            now = datetime.now()

            for row in rows:
                field_value = row.field_value
                if not field_value:
                    continue

                if not self.schema_registry.validate_value(form_code, field_code, field_value):
                    continue

                total_count = row.total_count or 0
                user_count = row.user_count or 0
                last_used_at = row.last_used_at

                time_score = 1.0
                if last_used_at:
                    days_since = (now - last_used_at).days
                    time_score = max(0.0, 1.0 - (days_since / 30))

                score = (
                    min(total_count * 0.1, 1.0) * 0.4 +
                    min(user_count * 0.2, 1.0) * 0.4 +
                    time_score * 0.2
                )

                recommendations.append({
                    "value": str(field_value),
                    "field_code": field_code,
                    "score": score,
                    "source": "history",
                    "confidence": min(total_count / 10.0, 1.0),
                    "match_type": "exact",
                    "reason": f"历史填写{total_count}次",
                    "metadata": {
                        "totalCount": total_count,
                        "userCount": user_count
                    }
                })

            return recommendations

        except Exception as e:
            logger.warning(f"[V4Engine] SQL聚合失败: {e}")
            return []

    def _get_static_recommendations(self, form_code: str, field_code: str) -> List[Dict]:
        try:
            static_values = config_loader.get_recommendations(form_code, field_code)
            if not static_values:
                return []

            return [
                {
                    "value": value,
                    "field_code": field_code,
                    "score": 0.3 - (i * 0.05),
                    "source": "static",
                    "confidence": 0.5,
                    "match_type": "exact",
                    "reason": "常用选项",
                    "metadata": {"staticRank": i + 1}
                }
                for i, value in enumerate(static_values)
            ]
        except Exception as e:
            logger.warning(f"[V4Engine] 静态推荐失败: {e}")
            return []

    def _deduplicate_and_limit(self, recommendations: List[Dict], max_count: int) -> List[Dict]:
        seen: Set[str] = set()
        unique_recs = []
        for rec in recommendations:
            value = rec.get("value")
            if value and value not in seen:
                seen.add(value)
                unique_recs.append(rec)
        return sorted(unique_recs, key=lambda x: x.get("score", 0), reverse=True)[:max_count]

    def _get_strategies(self, enum_values: Optional[List[str]], recommendations: List[Dict]) -> List[str]:
        strategies = []
        if enum_values:
            strategies.append("enum")
        if any(r.get("source") == "history" for r in recommendations):
            strategies.append("history")
        if any(r.get("source") == "static" for r in recommendations):
            strategies.append("static")
        return strategies


_engine_v4_instance: Optional[RecommendationEngineV4] = None


def get_recommendation_engine_v4() -> RecommendationEngineV4:
    global _engine_v4_instance
    if _engine_v4_instance is None:
        _engine_v4_instance = RecommendationEngineV4()
    return _engine_v4_instance

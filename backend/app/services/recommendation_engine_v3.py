"""
智能推荐引擎 v3 - 带 Schema 约束

核心优化：
1. Schema 约束：枚举类型只推荐 enumValues，历史值需要匹配 Schema
2. 类型感知：数值/日期类型推荐合理范围
3. 聚合表按表单+字段隔离：天然支持不同表单
"""

from typing import Dict, Any, List, Optional, Set
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict
import logging
import re

from sqlalchemy.orm import Session
from sqlalchemy import text
from app.core.config_loader import config_loader
from app.models.form import FormInstance

logger = logging.getLogger("recommendation_engine_v3")


@dataclass
class FieldSchema:
    """字段 Schema 定义"""
    name: str
    type: str
    enum_values: Optional[List[str]] = None
    min_value: Optional[float] = None
    max_value: Optional[float] = None
    required: bool = False


class SchemaRegistry:
    """Schema 注册表 - 管理所有表单的字段定义"""

    def __init__(self):
        self._schemas: Dict[str, Dict[str, FieldSchema]] = {}
        self._load_schemas()

    def _load_schemas(self):
        """从配置文件加载所有 Schema"""
        try:
            schemas = config_loader.get_all_schemas()
            for form_code, schema_info in schemas.items():
                fields = schema_info.get("fields", [])
                field_map = {}

                for f in fields:
                    field_schema = FieldSchema(
                        name=f.get("name", ""),
                        type=f.get("type", "string"),
                        enum_values=f.get("enumValues"),
                        min_value=f.get("min"),
                        max_value=f.get("max"),
                        required=f.get("required", False)
                    )
                    field_map[f.get("name", "")] = field_schema

                self._schemas[form_code] = field_map

            logger.info(f"[SchemaRegistry] 已加载 {len(self._schemas)} 个表单的 Schema")

        except Exception as e:
            logger.warning(f"[SchemaRegistry] Schema 加载失败: {e}")

    def get_field_schema(self, form_code: str, field_code: str) -> Optional[FieldSchema]:
        """获取字段 Schema"""
        form_schema = self._schemas.get(form_code, {})
        return form_schema.get(field_code)

    def is_enum_field(self, form_code: str, field_code: str) -> bool:
        """判断是否为枚举字段"""
        schema = self.get_field_schema(form_code, field_code)
        return schema is not None and schema.type == "enum" and schema.enum_values

    def get_enum_values(self, form_code: str, field_code: str) -> Optional[List[str]]:
        """获取枚举值列表"""
        schema = self.get_field_schema(form_code, field_code)
        if schema and schema.enum_values:
            return schema.enum_values
        return None

    def get_numeric_range(self, form_code: str, field_code: str) -> tuple:
        """获取数值字段范围 (min, max)"""
        schema = self.get_field_schema(form_code, field_code)
        if schema:
            return (schema.min_value, schema.max_value)
        return (None, None)


class SmartRecommendationEngine:
    """
    智能推荐引擎 v3

    核心改进：
    1. Schema 感知：枚举类型严格使用 enumValues
    2. 历史值验证：历史值必须符合 Schema 定义
    3. 聚合表隔离：每个 form_code + field_code 独立统计
    4. 优先级：枚举值 > 历史高频值 > 静态配置
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.recommendation_config = config_loader.get_recommendation_config()
        self.schema_registry = SchemaRegistry()
        self._load_config()

    def _load_config(self):
        self.max_recommendations = self.recommendation_config.get('recommendationLimit', 5)
        self.history_query_limit = self.recommendation_config.get('historyQueryLimit', 1000)

    def recommend(
        self,
        form_code: str,
        field_code: str,
        user_input: str = "",
        user_id: Optional[str] = None,
        conversation_context: Optional[Dict[str, Any]] = None,
        max_recommendations: int = 5,
        db: Optional[Session] = None
    ) -> Dict[str, Any]:
        """
        生成推荐结果（Schema 感知版本）

        推荐优先级：
        1. 枚举字段 → 直接返回 enumValues
        2. 历史高频值 → 从数据库聚合（需验证符合 Schema）
        3. 静态配置兜底 → recommendations.json
        """
        import time
        start_time = time.time()

        if db is None:
            try:
                from app.core.database import get_db
                db_gen = get_db()
                db = next(db_gen)
            except Exception as e:
                logger.warning(f"[SmartEngine] 无法获取数据库连接: {e}")
                return self._error_result(field_code, str(e), start_time)

        try:
            recommendations = []
            enum_values = self.schema_registry.get_enum_values(form_code, field_code)

            # 策略1：枚举字段 - 最高优先级
            if enum_values:
                recommendations = self._build_enum_recommendations(
                    form_code, field_code, enum_values
                )

            # 策略2：历史高频值
            else:
                history_recs = self._get_history_recommendations(
                    db, form_code, field_code, user_id
                )
                recommendations.extend(history_recs)

            # 策略3：静态配置兜底
            if len(recommendations) < max_recommendations:
                static_recs = self._get_static_recommendations(form_code, field_code)
                for rec in static_recs:
                    if not any(r.value == rec.value for r in recommendations):
                        recommendations.append(rec)

            # 去重并限制数量
            recommendations = self._deduplicate_and_limit(recommendations, max_recommendations)

            processing_time = (time.time() - start_time) * 1000

            return {
                "success": True,
                "field_code": field_code,
                "recommendations": [r for r in recommendations],
                "total_candidates": len(recommendations),
                "strategy_used": self._get_strategies(enum_values, recommendations),
                "processing_time_ms": processing_time
            }

        except Exception as e:
            logger.exception(f"[SmartEngine] 推荐失败: {e}")
            return self._error_result(field_code, str(e), start_time)

    def _build_enum_recommendations(
        self,
        form_code: str,
        field_code: str,
        enum_values: List[str]
    ) -> List[Dict[str, Any]]:
        """为枚举字段构建推荐"""
        recommendations = []

        for i, value in enumerate(enum_values):
            recommendations.append({
                "value": value,
                "field_code": field_code,
                "score": 1.0 - (i * 0.01),
                "source": "enum",
                "confidence": 1.0,
                "match_type": "exact",
                "reason": "枚举选项",
                "metadata": {"enumRank": i + 1}
            })

        return recommendations

    def _get_history_recommendations(
        self,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str]
    ) -> List[Dict[str, Any]]:
        """从数据库获取历史推荐（带 Schema 验证）"""
        try:
            cutoff_date = datetime.now() - timedelta(days=90)

            if user_id:
                sql = text("""
                    SELECT
                        f.data->>:field_code as field_value,
                        COUNT(*) as total_count,
                        SUM(CASE WHEN f.user_id = :user_id THEN 1 ELSE 0 END) as user_count,
                        MAX(f.submitted_at) as last_used_at
                    FROM form_instances f
                    WHERE f.form_code = :form_code
                        AND f.status = 'submitted'
                        AND f.submitted_at >= :cutoff_date
                        AND f.data->>:field_code IS NOT NULL
                        AND f.data->>:field_code != ''
                    GROUP BY f.data->>:field_code
                    ORDER BY total_count DESC
                    LIMIT 20
                """)
                params = {"form_code": form_code, "field_code": field_code, "user_id": user_id, "cutoff_date": cutoff_date}
            else:
                sql = text("""
                    SELECT
                        f.data->>:field_code as field_value,
                        COUNT(*) as total_count,
                        0 as user_count,
                        MAX(f.submitted_at) as last_used_at
                    FROM form_instances f
                    WHERE f.form_code = :form_code
                        AND f.status = 'submitted'
                        AND f.submitted_at >= :cutoff_date
                        AND f.data->>:field_code IS NOT NULL
                        AND f.data->>:field_code != ''
                    GROUP BY f.data->>:field_code
                    ORDER BY total_count DESC
                    LIMIT 20
                """)
                params = {"form_code": form_code, "field_code": field_code, "cutoff_date": cutoff_date}

            result = db.execute(sql, params)
            rows = result.fetchall()

            recommendations = []
            now = datetime.now()
            numeric_min, numeric_max = self.schema_registry.get_numeric_range(form_code, field_code)

            for row in rows:
                field_value = row.field_value
                if not field_value:
                    continue

                total_count = row.total_count or 0
                user_count = row.user_count or 0
                last_used_at = row.last_used_at

                if not self._validate_against_schema(form_code, field_code, field_value):
                    logger.debug(f"[SmartEngine] 历史值 '{field_value}' 不符合 Schema，跳过")
                    continue

                if numeric_min is not None or numeric_max is not None:
                    try:
                        num_value = float(field_value)
                        if numeric_min is not None and num_value < numeric_min:
                            continue
                        if numeric_max is not None and num_value > numeric_max:
                            continue
                    except (ValueError, TypeError):
                        pass

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
                        "userCount": user_count,
                        "lastUsed": last_used_at.isoformat() if last_used_at else None
                    }
                })

            return recommendations

        except Exception as e:
            logger.warning(f"[SmartEngine] 历史推荐查询失败: {e}")
            return []

    def _validate_against_schema(self, form_code: str, field_code: str, value: str) -> bool:
        """验证历史值是否符合 Schema 定义"""
        schema = self.schema_registry.get_field_schema(form_code, field_code)
        if not schema:
            return True

        if schema.type == "string":
            return True

        if schema.type == "integer":
            try:
                int(value)
                return True
            except (ValueError, TypeError):
                return False

        if schema.type == "number":
            try:
                float(value)
                return True
            except (ValueError, TypeError):
                return False

        if schema.type == "boolean":
            return value.lower() in ("true", "false", "1", "0", "yes", "no")

        if schema.type == "date":
            return bool(re.match(r"\d{4}-\d{2}-\d{2}", str(value)))

        if schema.type == "datetime":
            return bool(re.match(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}", str(value)))

        if schema.type == "email":
            return bool(re.match(r"^[\w\.-]+@[\w\.-]+\.\w+$", str(value)))

        if schema.type == "phone":
            return bool(re.match(r"^1[3-9]\d{9}$", str(value)))

        return True

    def _get_static_recommendations(
        self,
        form_code: str,
        field_code: str
    ) -> List[Dict[str, Any]]:
        """获取静态配置兜底推荐"""
        try:
            static_values = config_loader.get_recommendations(form_code, field_code)
            if not static_values:
                return []

            recommendations = []
            for i, value in enumerate(static_values):
                recommendations.append({
                    "value": value,
                    "field_code": field_code,
                    "score": 0.3 - (i * 0.05),
                    "source": "static",
                    "confidence": 0.5,
                    "match_type": "exact",
                    "reason": "常用选项",
                    "metadata": {"staticRank": i + 1}
                })

            return recommendations

        except Exception as e:
            logger.warning(f"[SmartEngine] 静态推荐失败: {e}")
            return []

    def _deduplicate_and_limit(
        self,
        recommendations: List[Dict],
        max_count: int
    ) -> List[Dict]:
        """去重并限制数量"""
        seen: Set[str] = set()
        unique_recs = []

        for rec in recommendations:
            value = rec.get("value")
            if value and value not in seen:
                seen.add(value)
                unique_recs.append(rec)

        return sorted(unique_recs, key=lambda x: x.get("score", 0), reverse=True)[:max_count]

    def _get_strategies(self, enum_values: Optional[List[str]], recommendations: List[Dict]) -> List[str]:
        """获取使用的策略列表"""
        strategies = []
        if enum_values:
            strategies.append("enum")
        if any(r.get("source") == "history" for r in recommendations):
            strategies.append("history")
        if any(r.get("source") == "static" for r in recommendations):
            strategies.append("static")
        return strategies

    def _error_result(self, field_code: str, error: str, start_time: float) -> Dict[str, Any]:
        return {
            "success": False,
            "field_code": field_code,
            "recommendations": [],
            "error": error,
            "processing_time_ms": (time.time() - start_time) * 1000
        }


_smart_engine_instance: Optional[SmartRecommendationEngine] = None


def get_smart_recommendation_engine() -> SmartRecommendationEngine:
    global _smart_engine_instance
    if _smart_engine_instance is None:
        _smart_engine_instance = SmartRecommendationEngine()
    return _smart_engine_instance

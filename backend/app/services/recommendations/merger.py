"""
推荐合并器 - 负责合并多个来源的推荐结果

合并策略：
1. 按 value 去重
2. 取最高优先级的 source 和 reason
3. 支持三层推荐源：推荐引擎、历史服务、静态配置
"""

from typing import Dict, Any, List, Optional
import logging

from app.core.config_loader import config_loader
from app.services.history_service import HistoryService

logger = logging.getLogger("recommendation_merger")


class RecommendationMerger:
    SOURCE_PRIORITY = {
        "llm_rule": 4,
        "inference": 3,
        "history": 2,
        "static": 1,
        "context": 2,
    }

    @classmethod
    def merge_recommendations(
        cls,
        form_code: str,
        field_code: str,
        field_def: Dict[str, Any],
        user_id: Optional[str] = None,
        db: Optional[Any] = None,
        engine_recommendations: Optional[Dict[str, Any]] = None
    ) -> List[Dict[str, Any]]:
        smart_config = config_loader.get_system_config().get("smartRecommend", {})
        enable_history = smart_config.get("enableHistoryInference", True)
        enable_static = smart_config.get("enableStaticFallback", True)
        max_per_field = smart_config.get("maxRecommendationsPerField", 5)

        all_items: Dict[str, Dict[str, Any]] = {}

        if engine_recommendations and field_code in engine_recommendations:
            rec_data = engine_recommendations[field_code]
            items = []
            if isinstance(rec_data, dict):
                items = rec_data.get("items", [])
            elif isinstance(rec_data, list):
                items = rec_data

            for item in items:
                value = item.get("value", "")
                if not value:
                    continue
                source = item.get("source", "history")
                reason = item.get("reason", "")
                confidence = item.get("confidence", 0)
                priority = cls.SOURCE_PRIORITY.get(source, 1)

                if value not in all_items or priority > all_items[value]["priority"]:
                    all_items[value] = {
                        "value": value,
                        "source": source,
                        "reason": reason,
                        "confidence": confidence,
                        "priority": priority
                    }

        if enable_history and db:
            try:
                recommend_result = HistoryService.get_recommend_values(
                    form_code, field_code, user_id, db
                )
                if recommend_result["success"]:
                    for rec in recommend_result["recommendations"]:
                        # HistoryService 返回的是 Dict: {value, label, score, source}
                        rec_value = rec.get("value") if isinstance(rec, dict) else rec
                        if rec_value and rec_value not in all_items:
                            all_items[rec_value] = {
                                "value": rec_value,
                                "label": rec.get("label") if isinstance(rec, dict) else rec_value,
                                "source": rec.get("source", "history") if isinstance(rec, dict) else "history",
                                "reason": "历史推荐",
                                "confidence": rec.get("score", 0.5) if isinstance(rec, dict) else 0.5,
                                "priority": cls.SOURCE_PRIORITY.get("history", 2)
                            }
            except Exception as e:
                logger.warning("[RecommendationMerger] HistoryService 推荐失败 field=%s: %s", field_code, e)

        if enable_static:
            enum_config = field_def.get("enumConfig", {})
            options = enum_config.get("options", []) if isinstance(enum_config, dict) else []
            if options:
                for opt in options:
                    opt_val = opt.get("value", opt.get("label", ""))
                    if opt_val and opt_val not in all_items:
                        all_items[opt_val] = {
                            "value": opt_val,
                            "source": "static",
                            "reason": "常用选项",
                            "confidence": 0.3,
                            "priority": cls.SOURCE_PRIORITY.get("static", 1)
                        }

            try:
                static_values = config_loader.get_recommendations(form_code, field_code)
                for sv in (static_values or []):
                    if sv and sv not in all_items:
                        all_items[sv] = {
                            "value": sv,
                            "source": "static",
                            "reason": "常用选项（兜底）",
                            "confidence": 0.3,
                            "priority": cls.SOURCE_PRIORITY.get("static", 1)
                        }
            except Exception:
                pass

        sorted_items = sorted(
            all_items.values(),
            key=lambda x: (x["priority"], x["confidence"]),
            reverse=True
        )

        result = []
        for item in sorted_items[:max_per_field]:
            label = item.get("label", item["value"])
            if not label:
                try:
                    from app.services.ontology_service import OntologyService
                    ontology_result = OntologyService.get_form_constraint(form_code)
                    if ontology_result.get('success'):
                        ontology = ontology_result.get('constraints', {})
                        entities = ontology.get('entities', [])
                        for entity in entities:
                            fields = entity.get('fields', [])
                            for field_def_item in fields:
                                if field_def_item.get('fieldCode') == field_code:
                                    options = field_def_item.get('options', [])
                                    for opt in options:
                                        if isinstance(opt, dict) and opt.get('value') == item["value"]:
                                            label = opt.get('label', item["value"])
                                            break
                except Exception:
                    pass

            result.append({
                "value": item["value"],
                "label": label,
                "source": item["source"],
                "reason": item["reason"],
                "confidence": item["confidence"]
            })

        return result
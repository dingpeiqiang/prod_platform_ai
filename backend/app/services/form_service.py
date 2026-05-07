from typing import Dict, Any, Optional
import uuid
import logging
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.ontology_service import OntologyService
from app.services.history_service import HistoryService
from app.core.config_loader import config_loader
from app.models.form import FormTemplate

logger = logging.getLogger("form_service")


def _convert_date_string(date_str: str) -> str:
    """
    将自然语言日期转换为标准日期格式 (YYYY-MM-DD)
    支持：今天、明天、后天、昨天、大前天等
    """
    if not date_str or not isinstance(date_str, str):
        return date_str
    
    today = datetime.now()
    date_str_lower = date_str.strip().lower()
    
    # 直接匹配常见表达
    if date_str_lower in ['今天', '今日']:
        return today.strftime('%Y-%m-%d')
    elif date_str_lower in ['明天', '明日']:
        return (today + timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['后天']:
        return (today + timedelta(days=2)).strftime('%Y-%m-%d')
    elif date_str_lower in ['昨天', '昨日']:
        return (today - timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['前天']:
        return (today - timedelta(days=2)).strftime('%Y-%m-%d')
    
    # 如果已经是标准格式，直接返回
    try:
        datetime.strptime(date_str, '%Y-%m-%d')
        return date_str
    except ValueError:
        pass
    
    # 其他情况返回原始值
    return date_str


class FormService:
    @classmethod
    def generate_form(
        cls,
        user_input: str,
        form_code: str = None,
        user_id: str = None,
        extracted_fields: Dict[str, Any] = None,
        db: Session = None,
        field_recommendations: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        if not form_code:
            scene_result = SceneRecognitionSkill.recognize(user_input)
            form_code = scene_result["sceneCode"]

        logger.info("[FormService] 生成表单 form_code=%s user_id=%s "
                    "pre_extracted_fields=%s",
                    form_code, user_id,
                    list(extracted_fields.keys()) if extracted_fields else [])

        ontology_result = OntologyService.get_form_constraint(form_code)
        if not ontology_result["success"]:
            return {
                "success": False,
                "message": ontology_result.get("message", "获取本体约束失败")
            }

        ontology = ontology_result["constraints"]

        # ── 自动 upsert FormTemplate（确保 form_code 有对应记录） ────────
        if db:
            try:
                existing = db.query(FormTemplate).filter(
                    FormTemplate.form_code == form_code,
                    FormTemplate.is_active == True
                ).first()
                if not existing:
                    template = FormTemplate(
                        form_code=form_code,
                        form_name=ontology.get("formName", form_code),
                        schema=ontology,
                        version=1,
                        is_active=True
                    )
                    db.add(template)
                    db.commit()
                    db.refresh(template)
                    logger.info("[FormService] 自动创建 FormTemplate form_code=%s id=%s",
                                form_code, template.id)
                else:
                    # 更新 schema 到最新本体（版本+1）
                    existing.schema = ontology
                    existing.version = (existing.version or 0) + 1
                    db.commit()
                    logger.debug("[FormService] FormTemplate 已存在 form_code=%s id=%s ver=%s",
                                 form_code, existing.id, existing.version)
            except Exception as e:
                logger.warning("[FormService] FormTemplate upsert 失败 form_code=%s: %s", form_code, e)
                # 不阻塞主流程，仅记录日志

        fields = []

        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_dict = {
                    "fieldCode": field["fieldCode"],
                    "fieldName": field["fieldName"],
                    "fieldType": field["fieldType"],
                    "required": field.get("required", False),
                    "disabled": False,
                    "hidden": False,
                    "ruleDescription": field.get("ruleDescription", ""),
                    "recommend": [],
                    "defaultValue": None
                }

                # 兼容旧 rules 结构（如有）
                if "rules" in field:
                    field_dict["rules"] = [
                        {"rule_type": r["rule_type"], "rule_value": r["rule_value"], "message": r["message"]}
                        for r in field["rules"]
                    ]
                else:
                    field_dict["rules"] = []

                if "options" in field:
                    field_dict["options"] = field["options"]

                # 复制 enumConfig（外部API枚举或静态枚举配置）
                if "enumConfig" in field:
                    field_dict["enumConfig"] = field["enumConfig"]

                # ── 三层推荐合并 ──────────────────────────────────────────
                fc = field["fieldCode"]
                merged = cls._merge_recommendations(
                    form_code=form_code,
                    field_code=fc,
                    field_def=field,
                    user_id=user_id,
                    db=db,
                    engine_recommendations=field_recommendations
                )
                field_dict["recommend"] = merged

                fields.append(field_dict)

        temp_schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", ""),
            "fields": fields
        }

        # ── 字段值合并策略 ────────────────────────────────────────────────
        # 优先级：LLM 传入字段 > FieldExtractionSkill > 历史推荐值
        extracted_values: Dict[str, Any] = {}

        # 1. 先用 FieldExtractionSkill 从 user_input 提取（作为兜底）
        extraction_result = FieldExtractionSkill.extract(user_input, form_code, temp_schema)
        if extraction_result["success"]:
            method = extraction_result.get("method", "unknown")
            field_count = len(extraction_result["fields"])
            logger.info("[FormService] 字段提取完成 方法=%s 提取字段数=%d", method, field_count)
            if method == "llm":
                logger.debug("[FormService] LLM提取结果=%s", extraction_result["fields"])
            for ef in extraction_result["fields"]:
                fc = ef.get("fieldCode")
                fv = ef.get("fieldValue", ef.get("defaultValue"))
                if fc and fv is not None:
                    # 对日期字段进行转换
                    if fc in ['start_date', 'end_date', 'date', 'expense_date']:
                        fv = _convert_date_string(fv)
                        logger.debug("[FormService] Skills日期字段 %s 转换: %s -> %s", fc, ef.get("fieldValue"), fv)
                    extracted_values[fc] = fv

        # 2. 用 LLM 传入的字段覆盖（更准确）
        if extracted_fields:
            for fc, fv in extracted_fields.items():
                if fc in ['success', 'error', 'message', 'result']:
                    continue
                if fv is not None and fv != "":
                    # 对日期字段进行转换
                    if fc in ['start_date', 'end_date', 'date', 'expense_date']:
                        fv = _convert_date_string(fv)
                        logger.debug("[FormService] 日期字段 %s 转换: %s -> %s", fc, extracted_fields[fc], fv)
                    extracted_values[fc] = fv
                    logger.debug("[FormService] 使用 LLM 提取字段 %s=%s", fc, fv)

        logger.info("[FormService] 最终提取字段: %s", extracted_values)

        for field in fields:
            field_code = field["fieldCode"]

            if field_code in extracted_values:
                field["defaultValue"] = extracted_values[field_code]
            else:
                # 从推荐列表中选择 defaultValue
                # 优先选择高置信度推荐（source=llm_rule > inference > static）
                if field.get("recommend") and len(field["recommend"]) > 0:
                    first_rec = field["recommend"][0]
                    # 兼容字符串和对象两种格式
                    if isinstance(first_rec, dict):
                        field["defaultValue"] = first_rec.get("value", "")
                    else:
                        field["defaultValue"] = first_rec

        form_id = f"form_{uuid.uuid4().hex[:12]}"

        form_schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", ""),
            "version": 1,
            "globalControl": {},
            "fields": fields
        }

        logger.info("[FormService] 表单生成完成 form_id=%s fields_count=%d "
                    "prefilled=%d",
                    form_id, len(fields), len(extracted_values))

        return {
            "success": True,
            "formSchema": form_schema,
            "formId": form_id,
            "sceneMethod": extraction_result.get("method", "unknown"),
            "extractedFields": list(extracted_values.keys())
        }
    
    @classmethod
    def adapt_for_chat_window(cls, form_schema: Dict[str, Any]) -> Dict[str, Any]:
        adapted_schema = form_schema.copy()
        
        for field in adapted_schema["fields"]:
            if field["fieldType"] == "select":
                field["fieldType"] = "select"
            elif field["fieldType"] == "textarea":
                field["fieldType"] = "textarea"
            elif field["fieldType"] == "date":
                field["fieldType"] = "date"
            elif field["fieldType"] == "number":
                field["fieldType"] = "number"
            else:
                field["fieldType"] = "input"
        
        return adapted_schema

    # ── 三层推荐合并 ──────────────────────────────────────────────────────

    # 推荐来源优先级（数值越大优先级越高）
    SOURCE_PRIORITY = {
        "llm_rule": 4,       # LLM 规则推理（意图识别阶段产出）
        "inference": 3,      # 历史关联推断
        "history": 2,        # 历史频次统计
        "static": 1,         # 静态配置兜底
        "context": 2,        # 上下文推断（与 history 同级）
    }

    @classmethod
    def _merge_recommendations(
        cls,
        form_code: str,
        field_code: str,
        field_def: Dict[str, Any],
        user_id: Optional[str] = None,
        db: Optional[Session] = None,
        engine_recommendations: Optional[Dict[str, Any]] = None
    ) -> list:
        """
        三层推荐合并：
        Layer 1: 推荐引擎输出（含 LLM 规则推理 + 历史推断 + 上下文推断）
        Layer 2: HistoryService 历史推荐（DB 查询兜底）
        Layer 3: 静态配置兜底（options / config_loader）

        合并策略：按 value 去重，取最高优先级的 source 和 reason
        """
        # 读取三层推荐开关
        smart_config = config_loader.get_system_config().get("smartRecommend", {})
        enable_history = smart_config.get("enableHistoryInference", True)
        enable_static = smart_config.get("enableStaticFallback", True)
        max_per_field = smart_config.get("maxRecommendationsPerField", 5)

        all_items: Dict[str, Dict[str, Any]] = {}  # value -> {value, source, reason, confidence, priority}

        # ── Layer 1: 推荐引擎输出（优先级最高） ────────────────────────
        if engine_recommendations and field_code in engine_recommendations:
            rec_data = engine_recommendations[field_code]
            # 兼容两种格式：
            #   chat.py 推荐引擎: {"items": [...], "strategyUsed": ...}
            #   LLM 意图识别输出: [{"value":..., "reason":...}, ...]
            if isinstance(rec_data, dict):
                items = rec_data.get("items", [])
            elif isinstance(rec_data, list):
                items = rec_data
            else:
                items = []
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

        # ── Layer 2: HistoryService 历史推荐（兜底） ──────────────────
        if enable_history and db:
            try:
                recommend_result = HistoryService.get_recommend_values(
                    form_code, field_code, user_id, db
                )
                if recommend_result["success"]:
                    for rec_value in recommend_result["recommendations"]:
                        if rec_value and rec_value not in all_items:
                            all_items[rec_value] = {
                                "value": rec_value,
                                "source": "history",
                                "reason": "历史推荐",
                                "confidence": 0.5,
                                "priority": cls.SOURCE_PRIORITY.get("history", 2)
                            }
            except Exception as e:
                logger.warning("[FormService] HistoryService 推荐失败 field=%s: %s", field_code, e)

        # ── Layer 3: 静态配置兜底 ──────────────────────────────────────
        if enable_static:
            # 3a. 本体 options 作为静态推荐
            options = field_def.get("options", [])
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

            # 3b. config_loader 静态推荐
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

        # ── 合并排序：按优先级降序 → 置信度降序 ────────────────────────
        sorted_items = sorted(
            all_items.values(),
            key=lambda x: (x["priority"], x["confidence"]),
            reverse=True
        )

        # 去掉辅助字段 priority，只保留 value/source/reason/confidence
        result = []
        for item in sorted_items[:max_per_field]:
            # ⚠️ 重要：从本体获取label，返回 {value, label} 格式
            label = item.get("label", item["value"])  # 如果已经有label则使用，否则用value
            if not label:
                # 尝试从本体获取
                try:
                    from app.services.ontology_service import OntologyService
                    ontology_result = OntologyService.get_form_constraint(form_code)
                    if ontology_result.get('success'):
                        ontology = ontology_result.get('constraints', {})
                        entities = ontology.get('entities', [])
                        for entity in entities:
                            fields = entity.get('fields', [])
                            for field_def in fields:
                                if field_def.get('fieldCode') == field_code:
                                    options = field_def.get('options', [])
                                    for opt in options:
                                        if isinstance(opt, dict) and opt.get('value') == item["value"]:
                                            label = opt.get('label', item["value"])
                                            break
                except Exception:
                    pass
            
            result.append({
                "value": item["value"],
                "label": label,  # ⚠️ 新增：中文标签
                "source": item["source"],
                "reason": item["reason"],
                "confidence": item["confidence"]
            })

        return result

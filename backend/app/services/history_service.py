from typing import Dict, Any, List, Optional
from datetime import datetime, timedelta
from collections import defaultdict
import logging
from sqlalchemy.orm import Session
from app.core.config_loader import config_loader
from app.models.form import FormInstance, FormHistory, FormTemplate
from app.services.ontology_service import OntologyService

logger = logging.getLogger("history_service")


class HistoryService:
    
    @classmethod
    def get_recommend_values(
        cls, 
        form_code: str, 
        field_code: str, 
        user_id: str = None,
        db: Session = None,
        limit: int = None
    ) -> Dict[str, Any]:
        recommendations = []
        config = config_loader.get_recommendation_config()
        
        if limit is None:
            limit = config.get('recommendationLimit', 10)
        
        if db:
            db_recommendations = cls._get_db_recommendations(db, form_code, field_code, user_id, config)
            recommendations.extend(db_recommendations)
        
        static_recommendations = config_loader.get_recommendations(form_code, field_code)
        for rec in static_recommendations:
            if rec not in [r['value'] for r in recommendations]:
                recommendations.append({
                    'value': rec,
                    'score': 0.5,
                    'source': 'static'
                })
        
        sorted_recommendations = sorted(
            recommendations, 
            key=lambda x: x['score'], 
            reverse=True
        )
        
        # ⚠️ 重要：返回编码和标签，而不是仅返回值
        top_recommendations = []
        for r in sorted_recommendations[:limit]:
            code = r['value']  # 编码
            label = cls._get_field_label(form_code, field_code, code)  # 中文标签
            top_recommendations.append({
                'value': code,    # 用于提交
                'label': label,   # 用于显示
                'score': r['score'],
                'source': r.get('source', 'db')
            })
        
        return {
            "success": True,
            "recommendations": top_recommendations,
            "source": "hybrid" if db else "static"
        }
    
    @classmethod
    def _get_db_recommendations(
        cls, 
        db: Session, 
        form_code: str, 
        field_code: str, 
        user_id: Optional[str] = None,
        config: Dict = None
    ) -> List[Dict[str, Any]]:
        if config is None:
            config = config_loader.get_recommendation_config()
        
        value_scores = defaultdict(lambda: {'count': 0, 'last_used': None, 'user_count': 0})

        query_limit = config.get('historyQueryLimit', 1000)

        # [修复] 先通过 form_code 查出 template_id 列表，再按 template_id 过滤 FormInstance
        # 避免不同表单类型的字段值串入推荐结果
        template_ids = db.query(FormTemplate.id).filter(
            FormTemplate.form_code == form_code,
            FormTemplate.is_active == True
        ).all()
        tid_list = [t.id for t in template_ids]

        if not tid_list:
            # [兜底] FormTemplate 无记录时，直接从 form_history 表查询
            # 这确保即使未通过 upsert 创建模板，历史推荐仍能工作
            logger.info("[HistoryService] form_code=%s 无FormTemplate记录，回退到form_history表", form_code)
            return cls._get_recommendations_from_history(db, form_code, field_code, user_id, config)

        base_query = db.query(FormInstance).filter(
            FormInstance.template_id.in_(tid_list),
            FormInstance.status == 'submitted'
        )
        if user_id:
            base_query = base_query.filter(FormInstance.user_id == user_id)

        form_instances = base_query.order_by(
            FormInstance.submitted_at.desc()
        ).limit(query_limit).all()
        
        for instance in form_instances:
            form_data = instance.data or {}
            if field_code in form_data:
                value = str(form_data[field_code])
                if value and value.strip():
                    score_info = value_scores[value]
                    score_info['count'] += 1
                    
                    if instance.submitted_at:
                        if not score_info['last_used'] or instance.submitted_at > score_info['last_used']:
                            score_info['last_used'] = instance.submitted_at
                    
                    if user_id and instance.user_id == user_id:
                        score_info['user_count'] += 1
        
        recommendations = []
        now = datetime.now()
        
        for value, info in value_scores.items():
            score = cls._calculate_score(info, now, config)
            recommendations.append({
                'value': value,
                'score': score,
                'source': 'database',
                'count': info['count'],
                'user_count': info['user_count']
            })
        
        return sorted(recommendations, key=lambda x: x['score'], reverse=True)[:config.get('recommendationLimit', 10)]
    
    @classmethod
    def _calculate_score(cls, info: Dict[str, Any], now: datetime, config: Dict = None) -> float:
        if config is None:
            config = config_loader.get_recommendation_config()
        
        count = info['count']
        user_count = info['user_count']
        last_used = info['last_used']
        
        count_score_weight = config.get('countScoreWeight', 0.4)
        user_score_weight = config.get('userScoreWeight', 0.4)
        time_score_weight = config.get('timeScoreWeight', 0.2)
        count_score_per_unit = config.get('countScorePerUnit', 0.1)
        user_score_per_unit = config.get('userScorePerUnit', 0.2)
        time_decay_days = config.get('timeDecayDays', 30)
        
        count_score = min(count * count_score_per_unit, 1.0)
        
        user_score = min(user_count * user_score_per_unit, 1.0)
        
        time_score = 1.0
        if last_used:
            days_since = (now - last_used).days
            if days_since > 0:
                time_score = max(0.0, 1.0 - (days_since / time_decay_days))
        
        final_score = (
            count_score * count_score_weight + 
            user_score * user_score_weight + 
            time_score * time_score_weight
        )
        
        return final_score
    
    @classmethod
    def _get_field_label(cls, form_code: str, field_code: str, code: str) -> str:
        """
        从本体定义中获取字段的中文标签
        
        Args:
            form_code: 表单编码
            field_code: 字段编码
            code: 字段值（编码）
        
        Returns:
            中文标签，如果没有映射则返回原值
        """
        try:
            # 加载本体
            ontology_result = OntologyService.get_form_constraint(form_code)
            if not ontology_result.get('success'):
                return code
            
            ontology = ontology_result.get('constraints', {})
            
            # ⚠️ 本体结构是 entities 数组，需要遍历查找字段
            entities = ontology.get('entities', [])
            for entity in entities:
                fields = entity.get('fields', [])
                for field_def in fields:
                    if field_def.get('fieldCode') == field_code:
                        # 检查是否有 options 映射
                        options = field_def.get('options', [])
                        for opt in options:
                            # 支持两种格式：字符串数组 和 {value, label} 对象数组
                            if isinstance(opt, str):
                                if opt == code:
                                    return opt
                            elif isinstance(opt, dict):
                                if opt.get('value') == code:
                                    return opt.get('label', code)
                        
                        # 没有找到映射，返回原值
                        return code
            
            # 没有找到字段定义，返回原值
            return code
            
        except Exception as e:
            logger.warning("[HistoryService] 获取字段标签失败: %s", e)
            return code

    @classmethod
    def _get_recommendations_from_history(
        cls,
        db: Session,
        form_code: str,
        field_code: str,
        user_id: Optional[str] = None,
        config: Dict = None
    ) -> List[Dict[str, Any]]:
        """
        兜底路径：直接从 form_history 表查询，不依赖 FormTemplate。
        当 FormTemplate 无记录时使用（兼容旧数据或异常情况）。
        
        查询策略：
          1. 从 form_instances.data JSON 中反推 form_code（字段签名匹配）
          2. 找到匹配的 instance_ids
          3. 用这些 IDs 过滤 form_history
          4. 统计频次 + 时间衰减打分
        """
        from collections import defaultdict as ddict
        
        # 已知的字段签名 → form_code 映射
        FIELD_SIGNATURES = {
            "sales_order": {"customer_name", "customer_phone", "order_amount", "order_date", "remark"},
            "expense":    {"expense_type", "amount", "expense_date", "description"},
            "leave":      {"applicant_name", "department", "leave_type", "start_date", "end_date", "reason"},
        }
        
        # 1. 反推匹配的 instance_ids
        target_signature = FIELD_SIGNATURES.get(form_code)
        if not target_signature:
            logger.warning("[HistoryService] form_code=%s 无已知字段签名，跳过兜底查询", form_code)
            return []
        
        matched_instance_ids: List[int] = []
        instances = db.query(FormInstance.id, FormInstance.data).filter(
            FormInstance.status == 'submitted'
        ).all()
        
        for inst_id, data in instances:
            keys = set((data or {}).keys())
            overlap = len(keys & target_signature) / max(len(keys | target_signature), 1)
            if overlap >= 0.5:  # Jaccard >= 0.5 认为是同类型
                matched_instance_ids.append(inst_id)
        
        if not matched_instance_ids:
            return []
        
        # 2. 从 form_history 查询这些实例的字段值
        value_scores = ddict(lambda: {'count': 0, 'last_used': None})
        
        query_limit = config.get('historyQueryLimit', 1000) if config else 1000
        
        histories = db.query(FormHistory).filter(
            FormHistory.form_instance_id.in_(matched_instance_ids),
            FormHistory.field_code == field_code
        ).limit(query_limit).all()
        
        for h in histories:
            value = h.field_value or ''
            if value.strip():
                info = value_scores[value]
                info['count'] += 1
                if h.created_at and (not info['last_used'] or h.created_at > info['last_used']):
                    info['last_used'] = h.created_at
                if user_id and h.user_id == user_id:
                    info.setdefault('user_count', 0)
                    info['user_count'] += 1
        
        # 3. 打分排序
        recommendations = []
        now = datetime.now()
        
        for value, info in value_scores.items():
            score = cls._calculate_score(info, now, config)
            recommendations.append({
                'value': value,
                'score': score,
                'source': 'history_fallback',
                'count': info['count'],
                'user_count': info.get('user_count', 0)
            })
        
        rec_limit = config.get('recommendationLimit', 10) if config else 10
        result = sorted(recommendations, key=lambda x: x['score'], reverse=True)[:rec_limit]
        logger.info("[HistoryService] form_code=%s field=%s 兜底查询到 %d 条推荐",
                     form_code, field_code, len(result))
        return result

    @classmethod
    def save_history(
        cls, 
        form_instance_id: int, 
        field_code: str, 
        field_value: str, 
        user_id: str = None,
        db: Session = None
    ):
        if not db:
            return
        
        try:
            history = FormHistory(
                form_instance_id=form_instance_id,
                field_code=field_code,
                field_value=str(field_value) if field_value is not None else '',
                user_id=user_id
            )
            db.add(history)
            db.commit()
            logger.debug("[HistoryService] 保存历史记录 instance_id=%s field=%s",
                         form_instance_id, field_code)
        except Exception as e:
            logger.exception("[HistoryService] 保存历史记录失败 instance_id=%s field=%s: %s",
                             form_instance_id, field_code, e)
            db.rollback()

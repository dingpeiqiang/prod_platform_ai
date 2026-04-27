from typing import Dict, Any, List, Optional
from datetime import datetime, timedelta
from collections import defaultdict
import logging
from sqlalchemy.orm import Session
from app.core.config_loader import config_loader
from app.models.form import FormInstance, FormHistory

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
        
        top_recommendations = [r['value'] for r in sorted_recommendations[:limit]]
        
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
        
        form_instances = db.query(FormInstance).filter(
            FormInstance.status == 'submitted'
        ).order_by(
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

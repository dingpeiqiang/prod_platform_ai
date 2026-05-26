"""
并行推荐策略执行器 - 并行执行多个推荐策略
"""
import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Dict, List, Any, Optional

from .value_source import ValueItem, ValueSource

logger = logging.getLogger("parallel_executor")


class ParallelRecommendationExecutor:
    """并行推荐策略执行器"""
    
    def __init__(self, max_workers: int = 4):
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
    
    async def execute_strategies(
        self,
        form_code: str,
        field_codes: List[str],
        user_id: Optional[str],
        existing_values: Dict[str, Any],
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """
        并行执行所有推荐策略
        
        Args:
            form_code: 表单编码
            field_codes: 字段列表
            user_id: 用户ID
            existing_values: 已确定的字段值
            db: 数据库会话
        
        Returns:
            {field_code: [ValueItem]}
        """
        if not field_codes:
            return {}
        
        logger.info(f"[ParallelExecutor] 开始并行执行推荐策略，表单: {form_code}, 字段数: {len(field_codes)}")
        
        # 定义需要执行的策略任务
        tasks = []
        
        # 1. 历史频率策略（异步执行）
        tasks.append(self._execute_frequency_strategy(
            form_code, field_codes, user_id, db
        ))
        
        # 2. 用户个性化策略（异步执行）
        if user_id:
            tasks.append(self._execute_user_personalized_strategy(
                form_code, field_codes, user_id, db
            ))
        
        # 3. 时间衰减策略（异步执行）
        tasks.append(self._execute_time_decay_strategy(
            form_code, field_codes, db
        ))
        
        # 4. 静态配置（同步执行，快速）
        static_result = await self._execute_static_strategy(
            form_code, field_codes, existing_values
        )
        
        # 并行执行所有任务
        results = await asyncio.gather(*tasks)
        
        # 合并结果
        merged = static_result  # 先以静态配置为基础
        for result in results:
            for field_code, items in result.items():
                if field_code not in merged:
                    merged[field_code] = []
                merged[field_code].extend(items)
        
        logger.info(f"[ParallelExecutor] 并行执行完成")
        return merged
    
    async def _execute_frequency_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        user_id: Optional[str],
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行历史频率策略"""
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(self.executor, lambda:
            self._run_frequency_strategy(form_code, field_codes, user_id, db)
        )
        return result
    
    def _run_frequency_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        user_id: Optional[str],
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行历史频率策略（同步）"""
        try:
            from .strategies import FrequencyRecommendationStrategy
            
            strategy = FrequencyRecommendationStrategy({})
            result = {}
            
            for field_code in field_codes:
                candidates = strategy.recommend(db, form_code, field_code, user_id, {})
                value_items = [
                    ValueItem(
                        value=c.value,
                        source=ValueSource.FREQUENCY,
                        confidence=c.confidence,
                        reason=c.reason,
                        label=c.label,
                        strategy_weight=0.6
                    )
                    for c in candidates
                ]
                if value_items:
                    result[field_code] = value_items
            
            logger.debug(f"[ParallelExecutor] 历史频率策略完成，推荐 {len(result)} 个字段")
            return result
        except Exception as e:
            logger.error(f"[ParallelExecutor] 历史频率策略失败: {e}")
            return {}
    
    async def _execute_user_personalized_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        user_id: str,
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行用户个性化策略"""
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(self.executor, lambda:
            self._run_user_personalized_strategy(form_code, field_codes, user_id, db)
        )
        return result
    
    def _run_user_personalized_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        user_id: str,
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行用户个性化策略（同步）"""
        try:
            from .strategies import UserPersonalizedStrategy
            
            strategy = UserPersonalizedStrategy({})
            result = {}
            
            for field_code in field_codes:
                candidates = strategy.recommend(db, form_code, field_code, user_id, {})
                value_items = [
                    ValueItem(
                        value=c.value,
                        source=ValueSource.USER_PERSONALIZED,
                        confidence=c.confidence,
                        reason=c.reason,
                        label=c.label,
                        strategy_weight=0.8
                    )
                    for c in candidates
                ]
                if value_items:
                    result[field_code] = value_items
            
            logger.debug(f"[ParallelExecutor] 用户个性化策略完成，推荐 {len(result)} 个字段")
            return result
        except Exception as e:
            logger.error(f"[ParallelExecutor] 用户个性化策略失败: {e}")
            return {}
    
    async def _execute_time_decay_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行时间衰减策略"""
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(self.executor, lambda:
            self._run_time_decay_strategy(form_code, field_codes, db)
        )
        return result
    
    def _run_time_decay_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        db: Any
    ) -> Dict[str, List[ValueItem]]:
        """执行时间衰减策略（同步）"""
        try:
            from .strategies import TimeDecayStrategy
            
            strategy = TimeDecayStrategy({})
            result = {}
            
            for field_code in field_codes:
                candidates = strategy.recommend(db, form_code, field_code, None)
                value_items = [
                    ValueItem(
                        value=c.value,
                        source=ValueSource.TIME_DECAY,
                        confidence=c.confidence,
                        reason=c.reason,
                        label=c.label,
                        strategy_weight=0.5
                    )
                    for c in candidates
                ]
                if value_items:
                    result[field_code] = value_items
            
            logger.debug(f"[ParallelExecutor] 时间衰减策略完成，推荐 {len(result)} 个字段")
            return result
        except Exception as e:
            logger.error(f"[ParallelExecutor] 时间衰减策略失败: {e}")
            return {}
    
    async def _execute_static_strategy(
        self,
        form_code: str,
        field_codes: List[str],
        existing_values: Dict[str, Any]
    ) -> Dict[str, List[ValueItem]]:
        """执行静态配置策略（同步，快速）"""
        result = {}
        
        try:
            from app.core.config_loader import config_loader
            
            for field_code in field_codes:
                static_values = config_loader.get_recommendations(form_code, field_code)
                value_items = []
                
                for i, value in enumerate(static_values[:5]):
                    value_items.append(ValueItem(
                        value=value,
                        source=ValueSource.STATIC,
                        confidence=0.3 - (i * 0.05),
                        reason="常用选项",
                        strategy_weight=0.3
                    ))
                
                if value_items:
                    result[field_code] = value_items
            
            logger.debug(f"[ParallelExecutor] 静态配置策略完成，推荐 {len(result)} 个字段")
        except Exception as e:
            logger.error(f"[ParallelExecutor] 静态配置策略失败: {e}")
        
        return result
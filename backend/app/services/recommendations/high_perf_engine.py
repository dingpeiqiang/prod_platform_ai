"""
高性能推荐引擎主类 - 支持批量 AI 调用和并行策略执行
"""
import time
import logging
from typing import Dict, List, Any, Optional, Tuple

from .value_source import ValueItem, ValueSource, RecommendationDecision
from .batch_ai_inference import BatchAIInferenceService
from .parallel_executor import ParallelRecommendationExecutor

logger = logging.getLogger("high_perf_engine")


class HighPerformanceRecommendationEngine:
    """高性能推荐引擎"""
    
    def __init__(self):
        self.parallel_executor = ParallelRecommendationExecutor()
    
    async def recommend_form_data(
        self,
        form_code: str,
        ontology: dict,
        user_input: str,
        user_id: Optional[str] = None,
        db: Optional[Any] = None,
        context_values: Optional[Dict[str, Any]] = None,
        tool_result: Optional[Dict[str, Any]] = None
    ) -> Tuple[Dict[str, Any], Dict[str, dict]]:
        """
        高性能批量推荐表单数据
        
        性能优化点：
        1. 批量 LLM 调用：一次调用提取所有字段 + 一次调用推断所有候选
        2. 并行数据库查询：历史频率、用户个性化、时间衰减并行执行
        3. 确定性值优先：先提取确定性值，再处理需要推荐的字段
        4. 约束感知：推荐时考虑已确定的字段值
        
        Args:
            form_code: 表单编码
            ontology: 本体定义
            user_input: 用户输入文本
            user_id: 用户ID
            db: 数据库会话
            context_values: 上下文变量
            tool_result: 工具调用结果
        
        Returns:
            (表单数据, 决策日志)
        """
        start_time = time.time()
        all_field_codes = self._get_all_field_codes(ontology)
        
        logger.info(f"[HighPerfEngine] 开始推荐，表单: {form_code}, 字段数: {len(all_field_codes)}")
        
        # 步骤1：收集确定性值（本地来源）
        definite_values = self._collect_definite_values(
            all_field_codes, context_values, tool_result
        )
        
        # 步骤2：批量提取确定性值（LLM）
        try:
            llm_extracted = await BatchAIInferenceService.extract_definite_values(
                form_code, ontology, user_input
            )
            # 合并 LLM 提取结果（优先级高于本地来源）
            for field_code, value in llm_extracted.items():
                if value and str(value).strip():
                    definite_values[field_code] = value
        except Exception as e:
            logger.error(f"[HighPerfEngine] LLM 提取失败: {e}")
        
        # 步骤3：识别需要推荐的字段
        fields_needing_recommendation = [
            fc for fc in all_field_codes 
            if fc not in definite_values or not str(definite_values.get(fc, "")).strip()
        ]
        
        logger.info(f"[HighPerfEngine] 确定性值字段: {len(definite_values)}, 需要推荐的字段: {len(fields_needing_recommendation)}")
        
        # 步骤4：批量 AI 智能推断（仅针对需要推荐的字段）
        ai_inference_result = {}
        if fields_needing_recommendation:
            try:
                ai_inference_result = await BatchAIInferenceService.infer_fields(
                    form_code=form_code,
                    field_codes=fields_needing_recommendation,
                    user_input=user_input,
                    ontology=ontology,
                    existing_values=definite_values
                )
            except Exception as e:
                logger.error(f"[HighPerfEngine] 批量 AI 推断失败: {e}")
        
        # 步骤5：并行执行本地推荐策略
        local_recommendations = {}
        if fields_needing_recommendation and db:
            try:
                local_recommendations = await self.parallel_executor.execute_strategies(
                    form_code=form_code,
                    field_codes=fields_needing_recommendation,
                    user_id=user_id,
                    existing_values=definite_values,
                    db=db
                )
            except Exception as e:
                logger.error(f"[HighPerfEngine] 本地推荐策略执行失败: {e}")
        
        # 步骤6：合并 AI 推断和本地推荐
        merged_recommendations = self._merge_recommendations(
            ai_inference_result, local_recommendations
        )
        
        # 步骤7：决策并应用约束
        form_data, decision_log = self._make_decisions(
            form_code, ontology, definite_values, merged_recommendations
        )
        
        # 步骤8：批量约束校验
        form_data = await self._validate_constraints(form_code, form_data, ontology)
        
        total_time = time.time() - start_time
        logger.info(f"[HighPerfEngine] 推荐完成，耗时: {total_time:.2f}s")
        
        return form_data, decision_log
    
    def _collect_definite_values(
        self,
        field_codes: List[str],
        context_values: Optional[Dict[str, Any]],
        tool_result: Optional[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """收集确定性值（本地来源）"""
        result = {}
        
        # 1. 工具结果（最高优先级）
        if tool_result:
            for field_code in field_codes:
                if field_code in tool_result:
                    value = tool_result[field_code]
                    if value and str(value).strip():
                        result[field_code] = value
        
        # 2. 上下文变量
        if context_values:
            for field_code in field_codes:
                if field_code not in result and field_code in context_values:
                    value = context_values[field_code]
                    if value and str(value).strip():
                        result[field_code] = value
        
        return result
    
    def _merge_recommendations(
        self,
        ai_inference: Dict[str, List[str]],
        local_recommendations: Dict[str, List[ValueItem]]
    ) -> Dict[str, List[ValueItem]]:
        """合并 AI 推断和本地推荐结果"""
        merged = {}
        
        all_fields = set(list(ai_inference.keys()) + list(local_recommendations.keys()))
        
        for field_code in all_fields:
            items = []
            
            # 添加 AI 推断结果
            if field_code in ai_inference:
                for i, value in enumerate(ai_inference[field_code][:5]):  # 最多5个
                    items.append(ValueItem(
                        value=value,
                        source=ValueSource.AI_INTELLIGENT,
                        confidence=0.8 - (i * 0.1),
                        reason="AI智能推断",
                        strategy_weight=1.0
                    ))
            
            # 添加本地推荐结果
            if field_code in local_recommendations:
                items.extend(local_recommendations[field_code])
            
            merged[field_code] = items
        
        return merged
    
    def _make_decisions(
        self,
        form_code: str,
        ontology: dict,
        definite_values: Dict[str, Any],
        recommendations: Dict[str, List[ValueItem]]
    ) -> Tuple[Dict[str, Any], Dict[str, dict]]:
        """决策：为每个字段选择最终值"""
        form_data = definite_values.copy()
        decision_log = {}
        
        all_field_codes = self._get_all_field_codes(ontology)
        
        for field_code in all_field_codes:
            # 如果已有确定性值，直接使用
            if field_code in definite_values and str(definite_values[field_code]).strip():
                decision_log[field_code] = {
                    "source": ValueSource.LLM_EXTRACTION.value,
                    "confidence": 1.0,
                    "reason": "确定性值",
                    "is_definite": True,
                    "recommendations_count": 0
                }
                continue
            
            # 获取推荐候选
            field_recommendations = recommendations.get(field_code, [])
            
            # 排序：按优先级 + 综合分数
            field_recommendations.sort(
                key=lambda x: (x.get_priority(), -x.get_final_score())
            )
            
            # 选择最佳候选
            final_value = ""
            final_source = ValueSource.DEFAULT
            confidence = 0.0
            reason = "无有效值"
            
            if field_recommendations:
                best_item = field_recommendations[0]
                final_value = best_item.value
                final_source = best_item.source
                confidence = best_item.confidence
                reason = best_item.reason
            
            # 保存结果
            form_data[field_code] = final_value
            decision_log[field_code] = {
                "source": final_source.value,
                "confidence": confidence,
                "reason": reason,
                "is_definite": False,
                "recommendations_count": len(field_recommendations),
                "top_recommendations": [
                    {"value": r.value, "source": r.source.value, "confidence": r.confidence}
                    for r in field_recommendations[:3]
                ]
            }
        
        return form_data, decision_log
    
    async def _validate_constraints(
        self,
        form_code: str,
        form_data: Dict[str, Any],
        ontology: dict
    ) -> Dict[str, Any]:
        """应用约束规则"""
        # 1. 日期约束：end_date >= start_date
        if 'online_day' in form_data and 'offline_day' in form_data:
            online = form_data['online_day']
            offline = form_data['offline_day']
            if online and offline and offline < online:
                logger.warning(f"[HighPerfEngine] 日期约束冲突: online_day({online}) > offline_day({offline})")
                form_data['offline_day'] = ""
        
        # 2. 分类约束：type1 和 type2 的组合校验
        if 'type1' in form_data and 'type2' in form_data:
            type1 = form_data['type1']
            type2 = form_data['type2']
            
            valid_combinations = {
                "1": ["1", "2", "3"],      # 公众分类允许的二级分类
                "2": ["4", "5", "6", "7"]  # 政企分类允许的二级分类
            }
            
            allowed_type2 = valid_combinations.get(type1, [])
            if type2 and allowed_type2 and type2 not in allowed_type2:
                logger.warning(f"[HighPerfEngine] 分类约束冲突: type1={type1} 与 type2={type2} 不匹配")
                if allowed_type2:
                    form_data['type2'] = allowed_type2[0]
        
        # 3. 业务规则约束：删除操作需要序列号
        if form_data.get('action_type') == "D" and not form_data.get('seq_no'):
            logger.warning("[HighPerfEngine] 删除操作需要填写序列号")
        
        return form_data
    
    def _get_all_field_codes(self, ontology: dict) -> List[str]:
        """获取所有字段编码"""
        field_codes = []
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                if field_code:
                    field_codes.append(field_code)
        
        return field_codes
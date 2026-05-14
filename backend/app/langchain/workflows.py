from typing import Optional, Dict, Any, List, Callable, AsyncGenerator
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime
import logging
from .agents import FormAgent, TaskAgent, ChatAgent
from .chains import FormRecognitionChain, FieldExtractionChain, FormValidationChain
from app.services.recommendation_engine import get_recommendation_engine
from app.core.config_loader import config_loader

logger = logging.getLogger("langchain.workflows")


class WorkflowStatus(str, Enum):
    """工作流状态"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class WorkflowStepStatus(str, Enum):
    """步骤状态"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    SKIPPED = "skipped"
    FAILED = "failed"


@dataclass
class WorkflowStep:
    """工作流步骤"""
    id: str
    name: str
    type: str
    action: Callable
    inputs: Dict[str, Any] = field(default_factory=dict)
    outputs: List[str] = field(default_factory=list)
    condition: Optional[str] = None
    next_step: Optional[str] = None
    status: WorkflowStepStatus = WorkflowStepStatus.PENDING
    result: Any = None
    error: Optional[str] = None
    
    def can_execute(self, context: Dict[str, Any]) -> bool:
        """判断是否可以执行"""
        if self.condition:
            try:
                return eval(self.condition, {}, context)
            except Exception:
                return False
        return True


@dataclass
class WorkflowContext:
    """工作流上下文"""
    workflow_id: str
    inputs: Dict[str, Any] = field(default_factory=dict)
    outputs: Dict[str, Any] = field(default_factory=dict)
    status: WorkflowStatus = WorkflowStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    current_step: Optional[str] = None
    error: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


class FormWorkflow:
    """表单处理工作流"""
    
    def __init__(self):
        self.form_agent = FormAgent()
        self.recommendation_engine = get_recommendation_engine()
    
    async def run(self, user_input: str, context: Optional[Dict[str, Any]] = None) -> AsyncGenerator[Dict[str, Any], None]:
        """运行表单工作流"""
        workflow_context = WorkflowContext(
            workflow_id=f"wf_{datetime.now().strftime('%Y%m%d%H%M%S')}",
            inputs={"user_input": user_input, **(context or {})}
        )
        
        yield {"type": "workflow_start", "workflow_id": workflow_context.workflow_id}
        
        # Step 1: 意图识别
        workflow_context.current_step = "intent_recognition"
        yield {"type": "step_start", "step": "intent_recognition", "name": "意图识别"}
        
        intent_result = await self._recognize_intent(user_input)
        workflow_context.outputs["intent_result"] = intent_result
        
        yield {"type": "step_complete", "step": "intent_recognition", "result": intent_result}
        
        intent_type = intent_result.get("intentType", "chat")
        
        if intent_type != "form":
            yield {"type": "workflow_complete", "result": intent_result}
            return
        
        # Step 2: 表单识别
        workflow_context.current_step = "form_recognition"
        yield {"type": "step_start", "step": "form_recognition", "name": "表单识别"}
        
        form_result = await self._recognize_form(user_input)
        workflow_context.outputs["form_result"] = form_result
        form_code = form_result.get("formCode")
        
        yield {"type": "step_complete", "step": "form_recognition", "result": form_result}
        
        # Step 3: 字段提取
        workflow_context.current_step = "field_extraction"
        yield {"type": "step_start", "step": "field_extraction", "name": "字段提取"}
        
        extract_result = await self._extract_fields(form_code, user_input)
        workflow_context.outputs["extract_result"] = extract_result
        extracted_fields = extract_result.get("extractedFields", {})
        
        yield {"type": "step_complete", "step": "field_extraction", "result": extract_result}
        
        # Step 4: 推荐引擎
        workflow_context.current_step = "recommendation"
        yield {"type": "step_start", "step": "recommendation", "name": "推荐引擎"}
        
        rec_result = await self._get_recommendations(form_code, extracted_fields, user_input)
        workflow_context.outputs["recommendation_result"] = rec_result
        
        yield {"type": "step_complete", "step": "recommendation", "result": rec_result}
        
        # Step 5: 合并结果
        workflow_context.current_step = "merge_results"
        yield {"type": "step_start", "step": "merge_results", "name": "合并结果"}
        
        final_result = self._merge_results(form_result, extract_result, rec_result)
        workflow_context.outputs["final_result"] = final_result
        
        yield {"type": "step_complete", "step": "merge_results", "result": final_result}
        
        # 完成工作流
        workflow_context.status = WorkflowStatus.COMPLETED
        workflow_context.completed_at = datetime.now()
        
        yield {"type": "workflow_complete", "result": final_result}
    
    async def _recognize_intent(self, user_input: str) -> Dict[str, Any]:
        """意图识别"""
        agent = TaskAgent()
        return await agent.analyze_task(user_input)
    
    async def _recognize_form(self, user_input: str) -> Dict[str, Any]:
        """表单识别"""
        chain = FormRecognitionChain()
        return await chain.run(user_input)
    
    async def _extract_fields(self, form_code: str, user_input: str) -> Dict[str, Any]:
        """字段提取"""
        chain = FieldExtractionChain(form_code)
        return await chain.run(user_input)
    
    async def _get_recommendations(self, form_code: str, extracted_fields: Dict[str, Any], user_input: str) -> Dict[str, Any]:
        """获取推荐"""
        try:
            rec_config = config_loader.get_recommendation_config()
            max_recs = rec_config.get('recommendationLimit', 3)
            
            ontology = config_loader.get_ontology(form_code)
            if not ontology:
                return {}
            
            all_field_codes = [
                f.get("fieldCode") 
                for entity in ontology.get("entities", []) 
                for f in entity.get("fields", [])
            ]
            
            recommendations = self.recommendation_engine.batch_recommend(
                form_code=form_code,
                extracted_fields=extracted_fields,
                user_input=user_input,
                max_per_field=max_recs,
                field_codes=all_field_codes
            )
            
            result = {}
            for field_code, rec_result in recommendations.items():
                if rec_result.success and rec_result.recommendations:
                    result[field_code] = {
                        "items": [r.to_dict() for r in rec_result.recommendations[:max_recs]],
                        "strategyUsed": rec_result.strategy_used
                    }
            
            return result
        except Exception as e:
            logger.error(f"Recommendation failed: {e}")
            return {}
    
    def _merge_results(self, form_result: Dict, extract_result: Dict, rec_result: Dict) -> Dict[str, Any]:
        """合并结果"""
        return {
            "formCode": form_result.get("formCode"),
            "formName": form_result.get("formName"),
            "extractedFields": extract_result.get("extractedFields", {}),
            "reasoning": extract_result.get("reasoning", ""),
            "recommendations": rec_result,
            "confidence": form_result.get("confidence", 0.5)
        }


class ValidationWorkflow:
    """表单验证工作流"""
    
    def __init__(self):
        pass
    
    async def run(self, form_code: str, form_data: Dict[str, Any]) -> AsyncGenerator[Dict[str, Any], None]:
        """运行验证工作流"""
        workflow_context = WorkflowContext(
            workflow_id=f"val_{datetime.now().strftime('%Y%m%d%H%M%S')}",
            inputs={"form_code": form_code, "form_data": form_data}
        )
        
        yield {"type": "workflow_start", "workflow_id": workflow_context.workflow_id}
        
        # Step 1: 初步验证
        yield {"type": "step_start", "step": "pre_validation", "name": "初步验证"}
        pre_result = await self._pre_validate(form_data)
        workflow_context.outputs["pre_validation"] = pre_result
        yield {"type": "step_complete", "step": "pre_validation", "result": pre_result}
        
        # Step 2: LLM验证
        yield {"type": "step_start", "step": "llm_validation", "name": "LLM智能验证"}
        llm_result = await self._llm_validate(form_code, form_data)
        workflow_context.outputs["llm_validation"] = llm_result
        yield {"type": "step_complete", "step": "llm_validation", "result": llm_result}
        
        # Step 3: 规则验证
        yield {"type": "step_start", "step": "rule_validation", "name": "规则验证"}
        rule_result = await self._rule_validate(form_code, form_data)
        workflow_context.outputs["rule_validation"] = rule_result
        yield {"type": "step_complete", "step": "rule_validation", "result": rule_result}
        
        # Step 4: 合并结果
        yield {"type": "step_start", "step": "merge_results", "name": "合并结果"}
        final_result = self._merge_validation_results(pre_result, llm_result, rule_result)
        workflow_context.outputs["final_result"] = final_result
        yield {"type": "step_complete", "step": "merge_results", "result": final_result}
        
        workflow_context.status = WorkflowStatus.COMPLETED
        workflow_context.completed_at = datetime.now()
        
        yield {"type": "workflow_complete", "result": final_result}
    
    async def _pre_validate(self, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """初步验证"""
        errors = []
        for key, value in form_data.items():
            if value is None or value == "":
                errors.append({"field": key, "error": "字段为空", "type": "empty"})
        
        return {"valid": len(errors) == 0, "errors": errors}
    
    async def _llm_validate(self, form_code: str, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """LLM验证"""
        chain = FormValidationChain(form_code)
        return await chain.run(form_data)
    
    async def _rule_validate(self, form_code: str, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """规则验证"""
        errors = []
        
        ontology = config_loader.get_ontology(form_code)
        if not ontology:
            return {"valid": True, "errors": []}
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                field_type = field.get("fieldType", "string")
                required = field.get("required", False)
                
                value = form_data.get(field_code)
                
                if required and (value is None or value == ""):
                    errors.append({
                        "field": field_code,
                        "fieldName": field.get("fieldName", field_code),
                        "error": "必填字段",
                        "type": "required"
                    })
                
                if value is not None:
                    if field_type == "number" and not isinstance(value, (int, float)):
                        try:
                            float(value)
                        except ValueError:
                            errors.append({
                                "field": field_code,
                                "fieldName": field.get("fieldName", field_code),
                                "error": "必须是数字",
                                "type": "type"
                            })
        
        return {"valid": len(errors) == 0, "errors": errors}
    
    def _merge_validation_results(self, pre_result: Dict, llm_result: Dict, rule_result: Dict) -> Dict[str, Any]:
        """合并验证结果"""
        all_errors = []
        all_errors.extend(pre_result.get("errors", []))
        all_errors.extend(llm_result.get("errors", []))
        all_errors.extend(rule_result.get("errors", []))
        
        # 去重
        unique_errors = []
        seen = set()
        for error in all_errors:
            key = (error.get("field"), error.get("error"))
            if key not in seen:
                seen.add(key)
                unique_errors.append(error)
        
        warnings = llm_result.get("warnings", [])
        
        return {
            "valid": len(unique_errors) == 0,
            "errors": unique_errors,
            "warnings": warnings,
            "sources": ["pre_validation", "llm_validation", "rule_validation"]
        }

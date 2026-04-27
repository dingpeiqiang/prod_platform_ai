"""
Harness Engine - AI Agent 统一运行环境

整合八大核心组件，为 AI Agent 提供完整的运行环境：
- 上下文工程
- 护栏系统
- 工具编排
- 验证纠错（Phase 2）
- 状态记忆（Phase 2）
- 可观测性（Phase 2）
- 多Agent协作（Phase 3）
- 熵管理（Phase 3）
- 分级审批（Phase 3）
"""

from typing import Dict, Any, Optional, List, Callable, Union
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime
import logging
import json

from .context import ContextManager
from .guardrails import GuardrailRegistry, InputValidationResult, OutputValidationResult
from .tools import EnhancedToolRegistry, PermissionLevel, ToolCategory

# Phase 2 组件
from .verification import (
    SelfVerifier,
    VerificationResult,
    ErrorRecovery,
    RetryPolicy,
    RetryConfig,
    RetryStrategy,
)
from .memory import (
    SessionManager,
    SessionState,
    VectorStore,
    ContextCompressor,
    CompressionConfig,
    CompressionResult,
    CheckpointManager,
)
from .observability import (
    AgentLogger,
    LogLevel,
    MetricsCollector,
    Tracer,
    Span,
    SpanStatus,
)

# Phase 3 组件 - Multi-Agent
from .multi_agent import (
    AgentManager,
    AgentCapability,
    TaskRouter,
    MessageBus,
)

# Phase 3 组件 - Entropy
from .entropy import (
    EntropyDetector,
    ConfidenceCalibrator,
    UncertaintyHandler,
    UncertaintyLevel,
)

# Phase 3 组件 - Approval
from .approval import (
    ApprovalWorkflow,
    RiskAssessor,
    ApprovalLevel,
)

logger = logging.getLogger("harness.engine")


class RequestType(Enum):
    """请求类型"""
    FORM_RECOGNITION = "form_recognition"
    FIELD_EXTRACTION = "field_extraction"
    FORM_VALIDATION = "form_validation"
    GENERAL_CHAT = "general_chat"
    TOOL_CALL = "tool_call"


@dataclass
class AgentRequest:
    """Agent 请求"""
    request_type: RequestType
    user_input: str
    session_id: Optional[str] = None
    user_id: Optional[str] = None
    user_level: PermissionLevel = PermissionLevel.PUBLIC
    context_types: List[str] = field(default_factory=lambda: ["agents_md", "system_prompt"])
    form_code: Optional[str] = None
    form_data: Optional[Dict] = None
    schema: Optional[Dict] = None
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    def __post_init__(self):
        if isinstance(self.request_type, str):
            self.request_type = RequestType(self.request_type)


@dataclass
class AgentResponse:
    """Agent 响应"""
    success: bool
    data: Any = None
    error: Optional[str] = None
    warnings: List[str] = field(default_factory=list)
    tool_calls: List[Dict] = field(default_factory=list)
    context_used: List[str] = field(default_factory=list)
    guardrail_result: Optional[Dict] = None
    verification_result: Optional[Dict] = None
    processing_time_ms: Optional[float] = None
    trace_id: Optional[str] = None


@dataclass
class ExecutionTrace:
    """执行追踪"""
    timestamp: datetime
    stage: str
    action: str
    data: Any
    duration_ms: Optional[float] = None


class HarnessEngine:
    """
    Harness 引擎
    
    统一管理六大组件，为 AI Agent 提供完整的运行环境
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        
        # Phase 1 组件
        self.context = ContextManager(self.config.get("context", {}))
        self.guardrails = GuardrailRegistry(self.config.get("guardrails", {}))
        self.tools = EnhancedToolRegistry(self.config.get("tools", {}))
        
        # Phase 2 组件
        # 验证纠错
        self.verifier = SelfVerifier(
            llm_client=self.config.get("llm_client"),
            pass_threshold=self.config.get("verification_threshold", 70.0)
        )
        self.error_recovery = ErrorRecovery()
        self.retry_policy = RetryPolicy(
            default_config=self.config.get("retry_config", RetryConfig())
        )
        
        # 状态记忆
        self.sessions = SessionManager(
            max_age_hours=self.config.get("session_max_age_hours", 24)
        )
        self.vector_store = VectorStore(
            max_entries=self.config.get("max_memory_entries", 10000)
        )
        self.context_compressor = ContextCompressor(
            default_config=self.config.get("compression_config", CompressionConfig())
        )
        self.checkpoints = CheckpointManager()
        
        # 可观测性
        self.logger = AgentLogger(
            enable_console=self.config.get("enable_console_logging", True)
        )
        self.metrics = MetricsCollector()
        self.tracer = Tracer(service_name=self.config.get("service_name", "harness"))
        
        self._observability_enabled = self.config.get("enable_observability", True)
        self._traces: List[ExecutionTrace] = []

        # Phase 3 组件 - 多Agent协作
        self.agent_manager = AgentManager()
        self.task_router = TaskRouter(agent_manager=self.agent_manager)
        self.message_bus = MessageBus()

        # Phase 3 组件 - 熵管理
        self.entropy_detector = EntropyDetector()
        self.confidence_calibrator = ConfidenceCalibrator()
        self.uncertainty_handler = UncertaintyHandler()

        # Phase 3 组件 - 分级审批
        self.risk_assessor = RiskAssessor()
        self.approval_workflow = ApprovalWorkflow()

        logger.info("HarnessEngine Phase 3 initialized")

    async def process(self, request: AgentRequest) -> AgentResponse:
        """处理 Agent 请求"""
        start_time = datetime.now()
        span = None
        
        try:
            # 1. 启动追踪
            if self._observability_enabled:
                span = self.tracer.start_span(
                    name=f"harness_process_{request.request_type.value}",
                    component="harness.engine"
                )
            
            # 2. 输入护栏检查
            guardrail_input = self.guardrails.check_input(
                request.user_input,
                request.metadata.get("bypass_token")
            )
            
            if not guardrail_input.allowed:
                self._log_trace("guardrail", "input_rejected", guardrail_input.input_result)
                self.metrics.increment("harness.input.rejected")
                
                if span:
                    span.add_log("input_rejected", {"reason": str(guardrail_input.input_result)})
                    self.tracer.finish_span(span, SpanStatus.ERROR)
                
                return AgentResponse(
                    success=False,
                    error=f"输入被护栏拦截: {guardrail_input.input_result.message if guardrail_input.input_result else '未知原因'}",
                    guardrail_result={"input": guardrail_input.__dict__},
                    trace_id=span.trace_id if span else None
                )
            
            self.metrics.increment("harness.input.accepted")
            
            # 3. 上下文注入
            enhanced_prompt = self._build_enhanced_prompt(request)
            
            # 4. 获取可用工具
            available_tools = self._get_available_tools(request)
            
            # 5. 执行任务（带重试）
            result = await self._execute_with_retry(request, enhanced_prompt, available_tools)
            
            # 6. 输出护栏检查
            if result.get("output"):
                guardrail_output = self.guardrails.check_output(
                    result["output"],
                    request.schema,
                    request.metadata.get("required_fields")
                )
                
                if not guardrail_output.allowed:
                    result["warnings"].append(
                        f"输出校验未通过: {guardrail_output.output_result.error_message if guardrail_output.output_result else ''}"
                    )
                    result["guardrail_result"] = {"output": guardrail_output.__dict__}
            
            # 7. 验证输出（Phase 2）
            verification_result = None
            if request.schema and result.get("output"):
                verification_result = self._verify_output(result["output"], request)
                if not verification_result.passed:
                    result["warnings"].append(
                        f"验证未通过: {', '.join(verification_result.issues)}"
                    )
                    self.metrics.increment("harness.verification.failed")
                else:
                    self.metrics.increment("harness.verification.passed")
            
            # 8. 状态记忆（Phase 2）
            self._save_to_memory(request, result)
            
            # 9. 记录指标
            processing_time = (datetime.now() - start_time).total_seconds() * 1000
            self.metrics.observe("harness.processing_time_ms", processing_time)
            
            # 10. 记录追踪
            self._log_trace(
                "execution",
                "task_completed",
                {"result_type": type(result.get("data")).__name__},
                processing_time
            )
            
            if span:
                span.add_tag("success", "true")
                self.tracer.finish_span(span, SpanStatus.OK)
            
            return AgentResponse(
                success=True,
                data=result.get("data"),
                warnings=result.get("warnings", []),
                tool_calls=result.get("tool_calls", []),
                context_used=request.context_types,
                guardrail_result=result.get("guardrail_result"),
                verification_result=verification_result.to_dict() if verification_result else None,
                processing_time_ms=processing_time,
                trace_id=span.trace_id if span else None
            )

        except Exception as e:
            logger.exception("HarnessEngine process error")
            processing_time = (datetime.now() - start_time).total_seconds() * 1000
            
            # 错误恢复
            recovery_action = self.error_recovery.handle_error(e, {"request": str(request)})
            
            self._log_trace(
                "execution",
                "task_error",
                {"error": str(e), "recovery": recovery_action.strategy.value if recovery_action else None},
                processing_time
            )
            
            if span:
                span.add_log("error", {"message": str(e)})
                self.tracer.finish_span(span, SpanStatus.ERROR)
            
            self.metrics.increment("harness.errors", 1)
            
            return AgentResponse(
                success=False,
                error=str(e),
                processing_time_ms=processing_time,
                trace_id=span.trace_id if span else None
            )

    def process_sync(self, request: AgentRequest) -> AgentResponse:
        """同步处理"""
        import asyncio
        
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(asyncio.run, self.process(request))
                    return future.result()
            else:
                return loop.run_until_complete(self.process(request))
        except RuntimeError:
            return asyncio.run(self.process(request))

    async def _execute_with_retry(
        self,
        request: AgentRequest,
        enhanced_prompt: str,
        available_tools: List[Dict]
    ) -> Dict[str, Any]:
        """带重试的执行"""
        retry_config = RetryConfig(
            max_attempts=3,
            strategy=RetryStrategy.EXPONENTIAL,
            initial_delay=1.0
        )
        
        async def execute():
            return await self._execute_task(request, enhanced_prompt, available_tools)
        
        result = await self.retry_policy.execute(execute, config=retry_config)
        
        if result.success:
            return result.result
        else:
            raise result.last_error or Exception("Max retries exceeded")

    def _verify_output(self, output: Any, request: AgentRequest) -> VerificationResult:
        """验证输出"""
        return self.verifier.verify_with_schema(
            output=output,
            schema=request.schema,
            required_fields=request.metadata.get("required_fields")
        )

    def _save_to_memory(self, request: AgentRequest, result: Dict):
        """保存到记忆"""
        if request.session_id:
            session = self.sessions.get_or_create(request.session_id, request.user_id)
            
            # 保存关键信息
            session.set("last_request_type", request.request_type.value)
            session.set("last_form_code", request.form_code)
            session.set("last_result", result.get("data"))
            
            # 添加向量记忆
            if request.user_input:
                self.vector_store.add(
                    content=request.user_input,
                    metadata={
                        "request_type": request.request_type.value,
                        "form_code": request.form_code,
                        "result": str(result.get("data"))[:200]
                    },
                    session_id=request.session_id
                )

    def recall_memories(
        self,
        query: str,
        session_id: Optional[str] = None,
        top_k: int = 5
    ) -> List[Dict]:
        """检索相关记忆"""
        results = self.vector_store.search(
            query=query,
            top_k=top_k,
            session_id=session_id
        )
        return [
            {
                "content": entry.content,
                "similarity": similarity,
                "metadata": entry.metadata
            }
            for entry, similarity in results
        ]

    def _build_enhanced_prompt(self, request: AgentRequest) -> str:
        """构建增强提示词"""
        base_prompt = self._get_base_prompt(request)
        
        enhanced = self.context.inject_context(
            base_prompt,
            request.context_types,
            [request.form_code] if request.form_code else None
        )
        
        return enhanced

    def _get_base_prompt(self, request: AgentRequest) -> str:
        """获取基础提示词"""
        prompts = {
            RequestType.FORM_RECOGNITION: "识别用户输入对应的表单类型。\n\n用户输入：{input}\n\n请返回表单编码。",
            RequestType.FIELD_EXTRACTION: "从用户输入中提取表单字段值。\n\n表单类型：{form_code}\n用户输入：{input}\n\n请提取并返回字段值 JSON。",
            RequestType.FORM_VALIDATION: "验证表单数据。\n\n表单类型：{form_code}\n表单数据：{data}\n\n请返回验证结果。",
            RequestType.GENERAL_CHAT: "{input}",
            RequestType.TOOL_CALL: "执行工具调用。\n\n输入：{input}\n\n可用工具：{tools}",
        }
        
        template = prompts.get(request.request_type, "{input}")
        
        return template.format(
            input=request.user_input,
            form_code=request.form_code or "",
            data=json.dumps(request.form_data, ensure_ascii=False) if request.form_data else "{}",
            tools=json.dumps(self.tools.get_all_tools(), ensure_ascii=False)
        )

    def _get_available_tools(self, request: AgentRequest) -> List[Dict]:
        """获取可用工具"""
        context_map = {
            RequestType.FORM_RECOGNITION: "recognition",
            RequestType.FIELD_EXTRACTION: "form extraction",
            RequestType.FORM_VALIDATION: "validation",
            RequestType.TOOL_CALL: "tools",
        }
        
        context = context_map.get(request.request_type, "general")
        
        return self.tools.get_tools_for_context(context, request.user_level)

    async def _execute_task(
        self,
        request: AgentRequest,
        enhanced_prompt: str,
        available_tools: List[Dict]
    ) -> Dict[str, Any]:
        """执行任务"""
        self._log_trace("execution", "prompt_enhanced", {"length": len(enhanced_prompt)})
        
        if request.request_type == RequestType.FORM_RECOGNITION:
            return await self._handle_form_recognition(request, enhanced_prompt)
        elif request.request_type == RequestType.FIELD_EXTRACTION:
            return await self._handle_field_extraction(request, enhanced_prompt)
        elif request.request_type == RequestType.FORM_VALIDATION:
            return await self._handle_form_validation(request, enhanced_prompt)
        elif request.request_type == RequestType.TOOL_CALL:
            return await self._handle_tool_call(request, enhanced_prompt, available_tools)
        else:
            return await self._handle_general_chat(request, enhanced_prompt)

    async def _handle_form_recognition(self, request: AgentRequest, prompt: str) -> Dict:
        return {
            "data": {
                "form_code": "leave",
                "confidence": 0.95,
                "alternatives": [{"code": "expense", "confidence": 0.3}]
            },
            "warnings": []
        }

    async def _handle_field_extraction(self, request: AgentRequest, prompt: str) -> Dict:
        return {
            "data": {
                "extracted_fields": {
                    "leave_type": "年假",
                    "leave_days": 3,
                    "reason": "个人事务"
                }
            },
            "warnings": []
        }

    async def _handle_form_validation(self, request: AgentRequest, prompt: str) -> Dict:
        return {
            "data": {"valid": True, "errors": []},
            "warnings": []
        }

    async def _handle_tool_call(self, request: AgentRequest, prompt: str, tools: List[Dict]) -> Dict:
        return {
            "data": {"result": "success"},
            "tool_calls": [],
            "warnings": []
        }

    async def _handle_general_chat(self, request: AgentRequest, prompt: str) -> Dict:
        return {
            "data": {"response": "我理解了您的问题。"},
            "warnings": []
        }

    def _log_trace(self, stage: str, action: str, data: Any, duration_ms: Optional[float] = None):
        if not self._observability_enabled:
            return
        
        trace = ExecutionTrace(
            timestamp=datetime.now(),
            stage=stage,
            action=action,
            data=data,
            duration_ms=duration_ms
        )
        
        self._traces.append(trace)
        
        if len(self._traces) > 1000:
            self._traces = self._traces[-1000:]
        
        # 记录到 AgentLogger
        self.logger.info(
            f"{stage}.{action}",
            context={"data": data, "duration_ms": duration_ms}
        )

    def get_traces(self) -> List[Dict]:
        return [t.__dict__ for t in self._traces]

    def clear_traces(self):
        self._traces.clear()
        self.logger.clear()

    def get_metrics(self) -> Dict:
        """获取指标"""
        return {
            "engine_metrics": self.metrics.get_all_metrics(),
            "vector_store": self.vector_store.get_stats(),
            "sessions": self.sessions.get_stats(),
            "tracer": self.tracer.get_stats(),
            # Phase 3 组件指标
            "agent_manager": self.agent_manager.health_check(),
            "task_router": self.task_router.get_stats(),
            "message_bus": self.message_bus.get_stats(),
            "entropy_detector": self.entropy_detector.get_history_stats(),
            "risk_assessor": self.risk_assessor.get_history_stats(),
            "approval_workflow": self.approval_workflow.get_stats(),
        }

    def get_trace(self, trace_id: str) -> Dict:
        """获取追踪"""
        return self.tracer.export_trace(trace_id)

    def reload(self):
        self.context.reload()
        logger.info("HarnessEngine configuration reloaded")


# 全局引擎实例
_engine: Optional[HarnessEngine] = None


def get_engine(config: Optional[Dict] = None) -> HarnessEngine:
    global _engine
    if _engine is None:
        _engine = HarnessEngine(config)
    return _engine

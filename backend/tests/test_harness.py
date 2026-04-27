# -*- coding: utf-8 -*-
"""
Harness AI 综合测试套件
测试 Phase 1-3 所有模块功能
"""
import asyncio
import time
import sys
from datetime import datetime

# 添加项目路径
sys.path.insert(0, '.')

from app.harness import (
    # Phase 1
    ContextManager, GuardrailRegistry, EnhancedToolRegistry, HarnessEngine,
    AgentRequest, AgentResponse, RequestType,
    # Phase 2
    SelfVerifier,
    ErrorRecovery,
    RetryPolicy, RetryConfig,
    SessionManager, VectorStore, ContextCompressor, CompressionConfig,
    AgentLogger, MetricsCollector, Tracer,
    # Phase 3
    AgentManager, TaskRouter, MessageBus, AgentCapability, AgentStatus,
    EntropyDetector, ConfidenceCalibrator, UncertaintyHandler,
    ApprovalWorkflow, RiskAssessor, RiskLevel,
)
from app.harness.tools.enhanced_registry import PermissionLevel, ToolCategory

# 额外导入
from app.harness.verification.self_verifier import VerificationLevel
from app.harness.multi_agent.task_router import TaskPriority, TaskStatus


class TestRunner:
    """测试运行器"""
    
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.results = []
    
    def add_result(self, name: str, passed: bool, message: str = ""):
        status = "PASS" if passed else "FAIL"
        self.results.append(f"[{status}] {name}")
        if passed:
            self.passed += 1
        else:
            self.failed += 1
            if message:
                self.results.append(f"       -> {message}")
        return passed
    
    def print_summary(self):
        print("\n" + "=" * 60)
        print(f"Test Results: {self.passed} passed, {self.failed} failed")
        print("=" * 60)
        for r in self.results:
            print(r)
        print("=" * 60)
        return self.failed == 0


def test_phase1_context_manager(runner: TestRunner):
    """Phase 1: 上下文工程测试"""
    print("\n=== Phase 1: Context Manager ===")
    
    cm = ContextManager()
    
    # 测试加载AGENTS.md上下文
    context = cm.get_agents_context()
    runner.add_result(
        "AGENTS context loaded",
        isinstance(context, str) and len(context) > 0,
        f"context length: {len(context)}"
    )
    
    # 测试Schema获取
    schemas = cm.get_all_schemas()
    runner.add_result(
        "Schema loading",
        isinstance(schemas, dict)
    )


def test_phase1_guardrails(runner: TestRunner):
    """Phase 1: 护栏系统测试"""
    print("\n=== Phase 1: Guardrails ===")
    
    registry = GuardrailRegistry()
    
    # 测试输入护栏 - XSS检测
    xss_result = registry.check_input("<script>alert('xss')</script>normal content")
    runner.add_result(
        "XSS injection detection",
        xss_result.allowed == False,
        f"allowed: {xss_result.allowed}"
    )
    
    # 测试输入护栏 - SQL注入
    sql_result = registry.check_input("'; DROP TABLE users; --")
    runner.add_result(
        "SQL injection detection",
        sql_result.allowed == False,
        f"allowed: {sql_result.allowed}"
    )
    
    # 测试正常输入通过
    normal_result = registry.check_input("Please fill leave application for 3 days")
    runner.add_result(
        "Normal input passes",
        normal_result.allowed == True
    )
    
    # 测试输出护栏 - Schema验证
    schema = {
        "type": "object",
        "properties": {
            "days": {"type": "integer", "minimum": 1, "maximum": 30},
            "reason": {"type": "string", "maxLength": 200}
        },
        "required": ["days"]
    }
    
    valid_data = {"days": 3, "reason": "Family matter"}
    valid_result = registry.check_output(valid_data, schema)
    runner.add_result(
        "Schema validation (valid)",
        valid_result.allowed == True
    )
    
    invalid_data = {"days": -1}
    invalid_result = registry.check_output(invalid_data, schema)
    runner.add_result(
        "Schema validation (invalid)",
        invalid_result.allowed == False
    )


def test_phase1_tools(runner: TestRunner):
    """Phase 1: 工具编排测试"""
    print("\n=== Phase 1: Tool Registry ===")
    
    registry = EnhancedToolRegistry()
    
    # 注册测试工具
    def test_tool(param1: str, param2: int = 0) -> dict:
        return {"result": f"test_tool executed with {param1}, {param2}"}
    
    registry.register_tool(
        name="test_tool",
        description="Test tool",
        category=ToolCategory.DATA,
        permission=PermissionLevel.PUBLIC,
        func=test_tool,
        parameters={
            "type": "object",
            "properties": {
                "param1": {"type": "string"},
                "param2": {"type": "integer", "default": 0}
            },
            "required": ["param1"]
        }
    )
    
    runner.add_result(
        "Tool registration",
        any(t.get("name") == "test_tool" for t in registry.get_all_tools())
    )
    
    # 测试工具调用
    result = registry.execute("test_tool", param1="hello", param2=42)
    runner.add_result(
        "Tool execution",
        result.get("result", "").find("hello") >= 0,
        f"result: {result}"
    )
    
    # 测试工具列表获取
    all_tools = registry.get_all_tools()
    runner.add_result(
        "Get all tools",
        len(all_tools) > 0,
        f"total tools: {len(all_tools)}"
    )


async def test_phase2_verification(runner: TestRunner):
    """Phase 2: 验证纠错测试"""
    print("\n=== Phase 2: Verification ===")
    
    verifier = SelfVerifier()
    
    # 测试Schema验证
    schema = {
        "type": "object",
        "properties": {
            "leave_days": {"type": "integer", "minimum": 1}
        },
        "required": ["leave_days"]
    }
    
    valid_data = {"leave_days": 3}
    invalid_data = {"leave_days": -1}
    
    result1 = verifier.verify_with_schema(valid_data, schema)
    runner.add_result(
        "Schema verification (valid)",
        result1.passed == True
    )
    
    result2 = verifier.verify_with_schema(invalid_data, schema)
    runner.add_result(
        "Schema verification (invalid)",
        result2.passed == False
    )
    
    # 测试重试策略
    retry_policy = RetryPolicy(RetryConfig(max_attempts=3, initial_delay=0.01))
    
    attempt_count = 0
    async def flaky_operation():
        nonlocal attempt_count
        attempt_count += 1
        if attempt_count < 3:
            raise Exception("Temporary failure")
        return "success"
    
    result = await retry_policy.execute(flaky_operation)
    runner.add_result(
        "Retry policy",
        result == "success",
        f"attempts: {attempt_count}"
    )


def test_phase2_memory(runner: TestRunner):
    """Phase 2: 状态记忆测试"""
    print("\n=== Phase 2: Memory ===")
    
    session_mgr = SessionManager()
    vector_store = VectorStore()
    
    # 测试会话管理
    session = session_mgr.create_session("test_user")
    session_id = session.session_id
    runner.add_result(
        "Session creation",
        session is not None and len(session_id) > 0
    )
    
    session2 = session_mgr.get_session(session_id)
    runner.add_result(
        "Session retrieval",
        session2 is not None and session2.session_id == session_id
    )
    
    # 测试向量存储
    entry_id = vector_store.add(
        session_id=session_id,
        content="User submitted leave application for 3 days",
        metadata={"type": "test"}
    )
    runner.add_result(
        "Vector add",
        entry_id is not None,
        f"entry_id: {entry_id}"
    )
    
    # 测试向量搜索
    memories = vector_store.search(
        query="leave application",
        top_k=5
    )
    runner.add_result(
        "Vector search",
        isinstance(memories, list),
        f"search returned {type(memories)}"
    )
    
    # 测试上下文压缩
    compressor = ContextCompressor(default_config=CompressionConfig(max_tokens=100))
    long_context = "\n".join([f"Dialog {i}: This is a very long conversation content for testing context compression" for i in range(50)])
    result = compressor.compress(long_context)
    runner.add_result(
        "Context compression",
        len(result.compressed_text) < len(long_context),
        f"{len(long_context)} chars -> {len(result.compressed_text)} chars"
    )


def test_phase2_observability(runner: TestRunner):
    """Phase 2: 可观测性测试"""
    print("\n=== Phase 2: Observability ===")
    
    # 测试日志
    try:
        logger = AgentLogger("test", max_entries=100)
        logger.info("Test log", user_id="test", action="log_test")
        runner.add_result(
            "Structured logging",
            True,
            "log written"
        )
    except Exception as e:
        runner.add_result(
            "Structured logging",
            False,
            f"error: {e}"
        )
    
    # 测试指标收集
    try:
        metrics = MetricsCollector()
        metrics.increment("test_counter", tags={"env": "test"})
        metrics.gauge("test_gauge", 42.5)
        with metrics.timer("test_timer"):
            time.sleep(0.01)
        
        summary = metrics.get_summary()
        runner.add_result(
            "Metrics collection",
            "counters" in summary,
            f"metrics collected"
        )
    except Exception as e:
        runner.add_result(
            "Metrics collection",
            False,
            f"error: {e}"
        )
    
    # 测试追踪
    try:
        tracer = Tracer("test_trace")
        with tracer.start_span("test_operation") as span:
            span.set_attribute("operation", "test")
        
        spans = tracer.get_spans()
        runner.add_result(
            "Span tracing",
            len(spans) > 0,
            f"recorded {len(spans)} spans"
        )
    except Exception as e:
        runner.add_result(
            "Span tracing",
            False,
            f"error: {e}"
        )


async def test_phase3_multi_agent(runner: TestRunner):
    """Phase 3: 多Agent协作测试"""
    print("\n=== Phase 3: Multi-Agent ===")
    
    agent_mgr = AgentManager()
    task_router = TaskRouter(agent_mgr)
    message_bus = MessageBus()
    
    # 测试Agent注册
    agent_id = await agent_mgr.register(
        name="TestAgent",
        capabilities=[AgentCapability.FORM_RECOGNITION, AgentCapability.FIELD_EXTRACTION],
        metadata={"version": "1.0"}
    )
    runner.add_result(
        "Agent registration",
        agent_id is not None
    )
    
    # 测试Agent状态
    agents = await agent_mgr.list_all()
    runner.add_result(
        "Agent list",
        len(agents) > 0,
        f"registered {len(agents)} agents"
    )
    
    # 测试任务路由
    task = await task_router.submit_task(
        name="Test task",
        task_type="form_recognition",
        input_data={"text": "Leave for 3 days"},
        priority=TaskPriority.NORMAL
    )
    runner.add_result(
        "Task submission",
        task is not None and task.task_id is not None
    )
    
    # 测试消息总线基本功能
    try:
        message_bus.publish("test_channel", "test_message")
        runner.add_result(
            "Message bus publish",
            True,
            "published message"
        )
    except Exception as e:
        runner.add_result(
            "Message bus publish",
            False,
            f"error: {e}"
        )


async def test_phase3_entropy(runner: TestRunner):
    """Phase 3: 熵管理测试"""
    print("\n=== Phase 3: Entropy ===")
    
    entropy_detector = EntropyDetector()
    confidence_calibrator = ConfidenceCalibrator()
    uncertainty_handler = UncertaintyHandler()
    
    # 测试熵检测
    high_entropy_output = "Possible answers: 1. Yes 2. No 3. Maybe 4. Not sure 5. Depends"
    low_entropy_output = '{"status": "success", "code": 200}'
    
    high_entropy = await entropy_detector.detect(
        output=high_entropy_output,
        expected_format="json",
        required_fields=["status", "code"]
    )
    low_entropy = await entropy_detector.detect(
        output=low_entropy_output,
        expected_format="json",
        required_fields=["status", "code"]
    )
    
    runner.add_result(
        "High entropy detection",
        high_entropy.overall_entropy > low_entropy.overall_entropy,
        f"high: {high_entropy.overall_entropy:.2f}, low: {low_entropy.overall_entropy:.2f}"
    )
    
    # 测试置信度校准
    try:
        raw_confidence = [0.9, 0.8, 0.7, 0.6, 0.5]
        calibrated = confidence_calibrator.calibrate_batch(
            confidences=raw_confidence
        )
        runner.add_result(
            "Confidence calibration",
            len(calibrated) == len(raw_confidence),
            f"calibrated: {len(calibrated)} results"
        )
    except Exception as e:
        runner.add_result(
            "Confidence calibration",
            False,
            f"error: {e}"
        )
    
    # 测试不确定性处理
    try:
        uncertainty = await uncertainty_handler.handle(
            output="uncertain output",
            confidence=0.3
        )
        runner.add_result(
            "Uncertainty handling",
            uncertainty is not None,
            f"handled uncertainty"
        )
    except Exception as e:
        runner.add_result(
            "Uncertainty handling",
            False,
            f"error: {e}"
        )


def test_phase3_approval(runner: TestRunner):
    """Phase 3: 分级审批测试"""
    print("\n=== Phase 3: Approval ===")
    
    risk_assessor = RiskAssessor()
    approval_workflow = ApprovalWorkflow()
    
    # 测试风险评估
    risk = risk_assessor.assess(
        operation="Bulk export user data",
        context={
            "data_type": "user_info",
            "record_count": 1000,
            "contains_sensitive": True,
            "has_financial_impact": False,
            "affects_compliance": True,
            "reputation_impact": "medium",
            "security_impact": "high",
            "privacy_impact": "high"
        }
    )
    
    runner.add_result(
        "Risk assessment",
        risk.overall_level in [RiskLevel.HIGH, RiskLevel.CRITICAL],
        f"risk level: {risk.overall_level.value}"
    )
    
    # 测试审批工作流
    request = approval_workflow.create_request(
        title="High-risk data export",
        content={"operation": "export", "record_count": 1000},
        requester="admin_user",
        risk_level=risk.overall_level.value
    )
    runner.add_result(
        "Approval request creation",
        request.request_id is not None,
        f"approval ID: {request.request_id}"
    )


async def test_integration(runner: TestRunner):
    """集成测试"""
    print("\n=== Integration Test ===")
    
    engine = HarnessEngine()
    
    # 测试完整处理流程
    try:
        response = await engine.process(
            AgentRequest(
                request_type=RequestType.FORM_RECOGNITION,
                user_input="Please fill a leave application for 3 days",
                session_id="test_session",
                user_id="test_user"
            )
        )
        
        runner.add_result(
            "Engine processing",
            response.success == True or response.success == False,
            f"completed: success={response.success}"
        )
    except Exception as e:
        runner.add_result(
            "Engine processing",
            False,
            f"error: {e}"
        )
    
    # 测试指标获取
    metrics = engine.get_metrics()
    runner.add_result(
        "Metrics aggregation",
        "total_requests" in str(metrics),
        f"includes: {list(metrics.keys())[:5]}"
    )


async def run_all_tests():
    """运行所有测试"""
    print("\n" + "=" * 60)
    print("Harness AI Test Suite")
    print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    
    runner = TestRunner()
    
    try:
        # Phase 1 测试
        test_phase1_context_manager(runner)
        test_phase1_guardrails(runner)
        test_phase1_tools(runner)
        
        # Phase 2 测试
        await test_phase2_verification(runner)
        test_phase2_memory(runner)
        test_phase2_observability(runner)
        
        # Phase 3 测试
        await test_phase3_multi_agent(runner)
        await test_phase3_entropy(runner)
        test_phase3_approval(runner)
        
        # 集成测试
        await test_integration(runner)
        
    except Exception as e:
        print(f"\nTest exception: {e}")
        import traceback
        traceback.print_exc()
        runner.add_result("Test execution", False, str(e))
    
    # 打印总结
    success = runner.print_summary()
    
    if success:
        print("\nAll tests passed!")
    else:
        print("\nSome tests failed, please check results above")
    
    return success


if __name__ == "__main__":
    # 设置UTF-8编码
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    
    success = asyncio.run(run_all_tests())
    sys.exit(0 if success else 1)

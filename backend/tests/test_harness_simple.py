# -*- coding: utf-8 -*-
"""
Harness AI 核心模块验证脚本
验证 Phase 1-3 所有模块的基本功能
"""
import asyncio
import sys
sys.path.insert(0, '.')

from app.harness import (
    # Phase 1
    ContextManager, GuardrailRegistry, EnhancedToolRegistry,
    # Phase 2
    SelfVerifier, ErrorRecovery, RetryPolicy, RetryConfig,
    SessionManager, VectorStore, ContextCompressor, CompressionConfig,
    AgentLogger, MetricsCollector, Tracer,
    # Phase 3
    AgentManager, TaskRouter, MessageBus, AgentCapability,
    EntropyDetector, ConfidenceCalibrator, UncertaintyHandler,
    ApprovalWorkflow, RiskAssessor, RiskLevel,
)


def test_context_manager():
    """测试上下文管理器"""
    print("[TEST] ContextManager...")
    try:
        cm = ContextManager()
        context = cm.get_agents_context()
        print(f"  PASS - AGENTS context loaded ({len(context)} chars)")
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_guardrails():
    """测试护栏系统"""
    print("[TEST] Guardrails...")
    try:
        registry = GuardrailRegistry()
        
        # XSS检测
        result = registry.check_input("<script>alert('xss')</script>")
        assert result.allowed == False, "XSS should be blocked"
        print("  PASS - XSS injection blocked")
        
        # SQL注入检测
        result = registry.check_input("'; DROP TABLE users; --")
        assert result.allowed == False, "SQL injection should be blocked"
        print("  PASS - SQL injection blocked")
        
        # 正常输入放行
        result = registry.check_input("Normal text input")
        assert result.allowed == True, "Normal input should be allowed"
        print("  PASS - Normal input allowed")
        
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_tools():
    """测试工具注册"""
    print("[TEST] EnhancedToolRegistry...")
    try:
        from app.harness.tools.enhanced_registry import PermissionLevel, ToolCategory
        
        registry = EnhancedToolRegistry()
        
        def test_tool(param1: str) -> dict:
            return {"result": f"executed {param1}"}
        
        registry.register_tool(
            name="test_tool",
            description="Test",
            category=ToolCategory.DATA,
            permission=PermissionLevel.PUBLIC,
            func=test_tool
        )
        
        result = registry.execute("test_tool", param1="hello")
        assert "result" in result, "Tool should return result"
        print("  PASS - Tool registration and execution")
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_verification():
    """测试验证纠错"""
    print("[TEST] SelfVerifier...")
    try:
        verifier = SelfVerifier()
        schema = {
            "type": "object",
            "properties": {"days": {"type": "integer", "minimum": 1}},
            "required": ["days"]
        }
        
        result = verifier.verify_with_schema({"days": 3}, schema)
        assert result.passed == True, "Valid data should pass"
        print("  PASS - Schema validation")
        
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_memory():
    """测试状态记忆"""
    print("[TEST] SessionManager & VectorStore...")
    try:
        session_mgr = SessionManager()
        session = session_mgr.create_session("test_user")
        assert session.session_id is not None, "Session should be created"
        print("  PASS - SessionManager")
        
        vector_store = VectorStore()
        entry_id = vector_store.add(content="test memory")
        assert entry_id is not None, "Memory should be added"
        print("  PASS - VectorStore")
        
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_observability():
    """测试可观测性"""
    print("[TEST] MetricsCollector...")
    try:
        metrics = MetricsCollector()
        metrics.increment("test_counter")
        metrics.gauge("test_gauge", 42.5)
        summary = metrics.get_summary()
        assert "counters" in summary, "Should have counters"
        print("  PASS - MetricsCollector")
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_entropy():
    """测试熵管理"""
    print("[TEST] EntropyDetector...")
    try:
        entropy_detector = EntropyDetector()
        print("  PASS - EntropyDetector initialized")
        
        calibrator = ConfidenceCalibrator()
        print("  PASS - ConfidenceCalibrator initialized")
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def test_approval():
    """测试分级审批"""
    print("[TEST] RiskAssessor...")
    try:
        risk_assessor = RiskAssessor()
        risk = risk_assessor.assess(
            operation="Test operation",
            context={
                "data_type": "test",
                "record_count": 100,
                "contains_sensitive": True,
                "has_financial_impact": True,
                "affects_compliance": True,
                "reputation_impact": "high",
                "security_impact": "high",
                "privacy_impact": "high"
            }
        )
        assert risk.overall_level in [RiskLevel.HIGH, RiskLevel.CRITICAL], "Should be high risk"
        print(f"  PASS - RiskAssessor (level: {risk.overall_level.value})")
        return True
    except Exception as e:
        print(f"  FAIL - {e}")
        return False


def main():
    print("=" * 60)
    print("Harness AI Core Modules Verification")
    print("=" * 60)
    
    results = []
    
    # Phase 1
    print("\n--- Phase 1: Core Components ---")
    results.append(("ContextManager", test_context_manager()))
    results.append(("Guardrails", test_guardrails()))
    results.append(("ToolRegistry", test_tools()))
    
    # Phase 2
    print("\n--- Phase 2: Verification & Memory ---")
    results.append(("SelfVerifier", test_verification()))
    results.append(("Memory", test_memory()))
    results.append(("Metrics", test_observability()))
    
    # Phase 3
    print("\n--- Phase 3: Advanced Features ---")
    results.append(("Entropy", test_entropy()))
    results.append(("Approval", test_approval()))
    
    # Summary
    print("\n" + "=" * 60)
    print("Summary:")
    passed = sum(1 for _, r in results if r)
    total = len(results)
    for name, result in results:
        status = "PASS" if result else "FAIL"
        print(f"  [{status}] {name}")
    print(f"\nTotal: {passed}/{total} passed")
    print("=" * 60)
    
    return passed == total


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)

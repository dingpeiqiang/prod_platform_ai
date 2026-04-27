"""
Harness - AI Agent 运行环境框架

为 AI 模型提供可控、高效、可靠的运行环境

核心组件：
- context: 上下文工程
- guardrails: 护栏系统
- tools: 工具编排
- verification: 验证纠错 (Phase 2)
- memory: 状态记忆 (Phase 2)
- observability: 可观测性 (Phase 2)
- multi_agent: 多Agent协作 (Phase 3)
- entropy: 熵管理 (Phase 3)
- approval: 分级审批 (Phase 3)
"""

# Phase 1 组件
from .context import ContextManager
from .guardrails import GuardrailRegistry, InputGuard, OutputGuard
from .tools import EnhancedToolRegistry
from .engine import HarnessEngine, AgentRequest, AgentResponse, RequestType, ExecutionTrace

# Phase 2 组件
from .verification import (
    SelfVerifier,
    VerificationResult,
    ErrorRecovery,
    RecoveryStrategy,
    RetryPolicy,
    RetryConfig,
)
from .memory import (
    SessionState,
    SessionManager,
    VectorStore,
    EmbeddingsManager,
    ContextCompressor,
    CompressionConfig,
    CheckpointManager,
    Checkpoint,
)
from .observability import (
    AgentLogger,
    LogLevel,
    LogEntry,
    MetricsCollector,
    MetricValue,
    MetricType,
    Tracer,
    Span,
    SpanStatus,
)

# Phase 3 组件 - Multi-Agent
from .multi_agent import (
    AgentManager,
    Agent,
    AgentStatus,
    AgentCapability,
    TaskRouter,
    RouteStrategy,
    Task,
    MessageBus,
    Message,
    MessageType,
    AgentMessage,
)

# Phase 3 组件 - Entropy
from .entropy import (
    EntropyDetector,
    EntropyResult,
    EntropyLevel,
    ConfidenceCalibrator,
    CalibrationResult,
    CalibrationMethod,
    UncertaintyHandler,
    UncertaintyLevel,
    ResolutionStrategy,
)

# Phase 3 组件 - Approval
from .approval import (
    ApprovalWorkflow,
    ApprovalRequest,
    ApprovalResult,
    ApprovalLevel,
    ApprovalStatus,
    ApprovalStep,
    RiskAssessor,
    RiskLevel,
    RiskFactor,
    RiskAssessment,
)

__all__ = [
    # Phase 1
    "ContextManager",
    "GuardrailRegistry",
    "InputGuard",
    "OutputGuard",
    "EnhancedToolRegistry",
    "HarnessEngine",
    "AgentRequest",
    "AgentResponse",
    "RequestType",
    "ExecutionTrace",

    # Phase 2 - Verification
    "SelfVerifier",
    "VerificationResult",
    "ErrorRecovery",
    "RecoveryStrategy",
    "RetryPolicy",
    "RetryConfig",

    # Phase 2 - Memory
    "SessionState",
    "SessionManager",
    "VectorStore",
    "EmbeddingsManager",
    "ContextCompressor",
    "CompressionConfig",
    "CheckpointManager",
    "Checkpoint",

    # Phase 2 - Observability
    "AgentLogger",
    "LogLevel",
    "LogEntry",
    "MetricsCollector",
    "MetricValue",
    "MetricType",
    "Tracer",
    "Span",
    "SpanStatus",

    # Phase 3 - Multi-Agent
    "AgentManager",
    "Agent",
    "AgentStatus",
    "AgentCapability",
    "TaskRouter",
    "RouteStrategy",
    "Task",
    "MessageBus",
    "Message",
    "MessageType",
    "AgentMessage",

    # Phase 3 - Entropy
    "EntropyDetector",
    "EntropyResult",
    "EntropyLevel",
    "ConfidenceCalibrator",
    "CalibrationResult",
    "CalibrationMethod",
    "UncertaintyHandler",
    "UncertaintyLevel",
    "ResolutionStrategy",

    # Phase 3 - Approval
    "ApprovalWorkflow",
    "ApprovalRequest",
    "ApprovalResult",
    "ApprovalLevel",
    "ApprovalStatus",
    "ApprovalStep",
    "RiskAssessor",
    "RiskLevel",
    "RiskFactor",
    "RiskAssessment",
]

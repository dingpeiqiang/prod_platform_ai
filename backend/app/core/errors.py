# 框架错误类型定义
# 统一的错误分类、级别和处理策略

from enum import Enum
from dataclasses import dataclass, field
from typing import Optional, Dict, Any


class ErrorLevel(Enum):
    """错误严重级别"""
    DEBUG = 0      # 调试信息，不上报
    INFO = 1       # 正常信息
    WARNING = 2    # 警告，需要关注但不影响流程
    ERROR = 3      # 错误，功能受损但可降级
    CRITICAL = 4   # 严重，系统级故障


class ErrorCategory(Enum):
    """错误来源分类"""
    LLM = "llm"                    # LLM 服务相关
    TOOL = "tool"                  # MCP 工具相关
    VALIDATION = "validation"       # 校验规则相关
    DATABASE = "database"          # 数据库操作相关
    CONFIG = "config"              # 配置/本体相关
    INTENT = "intent"              # 意图处理相关
    EXTERNAL = "external"          # 外部服务/API相关
    WEBSOCKET = "websocket"        # WebSocket 连接相关
    AUTH = "auth"                  # 权限/认证相关
    SYSTEM = "system"              # 系统级错误


class RecoveryStrategy(Enum):
    """恢复策略"""
    RETRY = "retry"                # 重试 N 次后降级
    FALLBACK = "fallback"          # 降级到规则/缓存
    SKIP = "skip"                  # 跳过继续流程
    BLOCK = "block"                # 阻止操作
    RECONNECT = "reconnect"        # 重连
    ESCALATE = "escalate"          # 记录告警


# 错误码定义
class ErrorCode:
    """统一错误码"""

    # LLM 相关 (ERR_LLM_XXX)
    LLM_DISABLED = "ERR_LLM_DISABLED"
    LLM_TIMEOUT = "ERR_LLM_TIMEOUT"
    LLM_EMPTY_RESPONSE = "ERR_LLM_EMPTY_RESPONSE"
    LLM_PARSE_ERROR = "ERR_LLM_PARSE_ERROR"
    LLM_TOKEN_EXCEED = "ERR_LLM_TOKEN_EXCEED"
    LLM_RATE_LIMIT = "ERR_LLM_RATE_LIMIT"
    LLM_UNAVAILABLE = "ERR_LLM_UNAVAILABLE"

    # Tool 相关 (ERR_TOOL_XXX)
    TOOL_NOT_FOUND = "ERR_TOOL_NOT_FOUND"
    TOOL_PARAM_ERROR = "ERR_TOOL_PARAM_ERROR"
    TOOL_EXEC_FAILED = "ERR_TOOL_EXEC_FAILED"
    TOOL_TIMEOUT = "ERR_TOOL_TIMEOUT"

    # Validation 相关 (ERR_VAL_XXX)
    VAL_REQUIRED = "ERR_VAL_REQUIRED"
    VAL_FORMAT = "ERR_VAL_FORMAT"
    VAL_RANGE = "ERR_VAL_RANGE"
    VAL_RULE_FAIL = "ERR_VAL_RULE_FAIL"

    # Database 相关 (ERR_DB_XXX)
    DB_CONNECT = "ERR_DB_CONNECT"
    DB_QUERY = "ERR_DB_QUERY"
    DB_NOT_FOUND = "ERR_DB_NOT_FOUND"
    DB_DUPLICATE = "ERR_DB_DUPLICATE"

    # Config 相关 (ERR_CONFIG_XXX)
    CONFIG_NOT_FOUND = "ERR_CONFIG_NOT_FOUND"
    CONFIG_FORMAT = "ERR_CONFIG_FORMAT"
    ONTOLOGY_MISSING = "ERR_ONTOLOGY_MISSING"

    # Intent 相关 (ERR_INTENT_XXX)
    INTENT_PARSE = "ERR_INTENT_PARSE"
    INTENT_UNSUPPORTED = "ERR_INTENT_UNSUPPORTED"
    INTENT_HANDLER = "ERR_INTENT_HANDLER"

    # External 相关 (ERR_EXT_XXX)
    EXT_API_ERROR = "ERR_EXT_API_ERROR"
    EXT_API_TIMEOUT = "ERR_EXT_API_TIMEOUT"
    EXT_API_LIMIT = "ERR_EXT_API_LIMIT"

    # System 相关 (ERR_SYS_XXX)
    SYS_FILE_NOT_FOUND = "ERR_SYS_FILE_NOT_FOUND"
    SYS_FILE_IO = "ERR_SYS_FILE_IO"
    SYS_MEMORY = "ERR_SYS_MEMORY"


@dataclass
class FrameworkError:
    """统一错误结构"""
    category: str           # 错误来源
    level: str              # 严重级别
    code: str               # 错误码
    message: str            # 用户友好的错误信息
    detail: str = ""        # 技术细节（供调试）
    source: str = ""        # 错误发生的函数/模块
    recoverable: bool = True # 是否可恢复
    recovery_hint: str = "" # 恢复建议
    context: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "category": self.category,
            "level": self.level,
            "code": self.code,
            "message": self.message,
            "detail": self.detail,
            "source": self.source,
            "recoverable": self.recoverable,
            "recovery_hint": self.recovery_hint,
            "context": self.context
        }

    def to_sse(self) -> Dict[str, Any]:
        """转换为 SSE 事件格式"""
        return {
            "type": "error",
            "error_type": self.category,
            "error_code": self.code,
            "message": self.message,
            "recoverable": self.recoverable
        }


# 错误码到错误信息的默认映射
ERROR_CODE_MESSAGES = {
    # LLM
    ErrorCode.LLM_DISABLED: "AI 服务已禁用",
    ErrorCode.LLM_TIMEOUT: "AI 服务响应超时，请稍后重试",
    ErrorCode.LLM_EMPTY_RESPONSE: "AI 服务返回为空",
    ErrorCode.LLM_PARSE_ERROR: "AI 响应解析失败",
    ErrorCode.LLM_TOKEN_EXCEED: "输入内容过长，已超出 AI 处理限制",
    ErrorCode.LLM_RATE_LIMIT: "AI 服务调用过于频繁，请稍后重试",
    ErrorCode.LLM_UNAVAILABLE: "AI 服务暂时不可用",

    # Tool
    ErrorCode.TOOL_NOT_FOUND: "请求的工具不存在",
    ErrorCode.TOOL_PARAM_ERROR: "工具参数错误",
    ErrorCode.TOOL_EXEC_FAILED: "工具执行失败",
    ErrorCode.TOOL_TIMEOUT: "工具执行超时",

    # Validation
    ErrorCode.VAL_REQUIRED: "必填字段缺失",
    ErrorCode.VAL_FORMAT: "字段格式错误",
    ErrorCode.VAL_RANGE: "字段值超出范围",
    ErrorCode.VAL_RULE_FAIL: "规则校验失败",

    # Database
    ErrorCode.DB_CONNECT: "数据库连接失败",
    ErrorCode.DB_QUERY: "数据库查询失败",
    ErrorCode.DB_NOT_FOUND: "数据不存在",
    ErrorCode.DB_DUPLICATE: "数据重复",

    # Config
    ErrorCode.CONFIG_NOT_FOUND: "配置不存在",
    ErrorCode.CONFIG_FORMAT: "配置格式错误",
    ErrorCode.ONTOLOGY_MISSING: "表单定义缺失",

    # Intent
    ErrorCode.INTENT_PARSE: "意图解析失败",
    ErrorCode.INTENT_UNSUPPORTED: "不支持的意图类型",
    ErrorCode.INTENT_HANDLER: "意图处理失败",

    # External
    ErrorCode.EXT_API_ERROR: "外部服务调用失败",
    ErrorCode.EXT_API_TIMEOUT: "外部服务响应超时",
    ErrorCode.EXT_API_LIMIT: "外部服务调用频率超限",

    # System
    ErrorCode.SYS_FILE_NOT_FOUND: "文件不存在",
    ErrorCode.SYS_FILE_IO: "文件操作失败",
    ErrorCode.SYS_MEMORY: "系统资源不足",
}


def get_error_message(error_code: str, custom_message: str = None) -> str:
    """获取错误信息，优先使用自定义信息"""
    return custom_message or ERROR_CODE_MESSAGES.get(error_code, "未知错误")
"""
输入护栏 - 拦截注入攻击等不安全输入

检测类型：
- XSS 跨站脚本攻击
- SQL 注入
- 命令注入
- Prompt 注入
- 敏感信息泄露
"""

from typing import Optional, List, Tuple, Dict, Any
from dataclasses import dataclass
from enum import Enum
import re
import logging

logger = logging.getLogger(__name__)


class ThreatLevel(Enum):
    """威胁级别"""
    SAFE = "safe"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


@dataclass
class InputValidationResult:
    """输入验证结果"""
    allowed: bool
    threat_level: ThreatLevel
    message: Optional[str] = None
    detected_patterns: List[str] = None
    
    def __post_init__(self):
        if self.detected_patterns is None:
            self.detected_patterns = []


class InputGuard:
    """
    输入安全检查器
    
    检测并拦截：
    - XSS 攻击
    - SQL 注入
    - 命令注入
    - Prompt 注入
    - 恶意 URL
    - 敏感信息
    """
    
    # 危险模式定义
    DANGEROUS_PATTERNS: Dict[str, re.Pattern] = {
        # XSS 攻击
        "xss_script": re.compile(
            r'<script[^>]*>.*?</script>',
            re.IGNORECASE | re.DOTALL
        ),
        "xss_onerror": re.compile(
            r'on\w+\s*=\s*["\'].*?["\']',
            re.IGNORECASE
        ),
        "xss_javascript": re.compile(
            r'javascript:\s*',
            re.IGNORECASE
        ),
        
        # SQL 注入
        "sql_union": re.compile(
            r'union\s+(all\s+)?select',
            re.IGNORECASE
        ),
        "sql_drop": re.compile(
            r'drop\s+(table|database)',
            re.IGNORECASE
        ),
        "sql_exec": re.compile(
            r'exec(\s|\()+',
            re.IGNORECASE
        ),
        
        # 命令注入
        "cmd_shell": re.compile(
            r'(rm|rmdir|del|format)\s+-rf',
            re.IGNORECASE
        ),
        "cmd_pipe": re.compile(
            r'\|\s*\w+',
            re.IGNORECASE
        ),
        "cmd_semicolon": re.compile(
            r';\s*(rm|del|cat|ls)',
            re.IGNORECASE
        ),
        
        # Prompt 注入
        "prompt_ignore": re.compile(
            r'(ignore|disregard)\s+(previous|all|above)\s+(instructions?|commands?)',
            re.IGNORECASE
        ),
        "prompt_override": re.compile(
            r'(system|prompt):\s*',
            re.IGNORECASE
        ),
        "prompt_jailbreak": re.compile(
            r'(you\s+are\s+|pretend\s+to\s+be|act\s+as\s+).*?(DAN|evil|jailbreak)',
            re.IGNORECASE
        ),
        
        # 恶意 URL
        "malicious_url": re.compile(
            r'(file://|ftp://|dict://|sftp://|ldap://|gopher://)',
            re.IGNORECASE
        ),
        
        # 路径遍历
        "path_traversal": re.compile(
            r'(\.\./|\.\.\\|%2e%2e)',
            re.IGNORECASE
        ),
    }
    
    # 敏感信息模式
    SENSITIVE_PATTERNS: Dict[str, re.Pattern] = {
        "api_key": re.compile(
            r'(api[_-]?key|apikey)\s*[=:]\s*["\']?[\w-]{20,}["\']?',
            re.IGNORECASE
        ),
        "password": re.compile(
            r'password\s*[=:]\s*["\']?[^\s"\']{6,}["\']?',
            re.IGNORECASE
        ),
        "private_key": re.compile(
            r'-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----',
            re.IGNORECASE
        ),
        "token": re.compile(
            r'(bearer\s+)?token\s*[=:]\s*["\']?[A-Za-z0-9_-]{20,}["\']?',
            re.IGNORECASE
        ),
    }
    
    # 威胁级别阈值
    THREAT_THRESHOLDS = {
        ThreatLevel.LOW: 1,
        ThreatLevel.MEDIUM: 2,
        ThreatLevel.HIGH: 3,
        ThreatLevel.CRITICAL: 5,
    }
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        """
        初始化输入护栏
        
        Args:
            config: 配置字典
                - enabled: 是否启用
                - strict_mode: 严格模式（任何检测都拦截）
                - allow_sensitive: 是否允许敏感信息（默认警告）
        """
        self.config = config or {}
        self.enabled = self.config.get("enabled", True)
        self.strict_mode = self.config.get("strict_mode", False)
        self.allow_sensitive = self.config.get("allow_sensitive", False)
        
        # 威胁模式权重
        self.threat_weights: Dict[str, int] = {
            "xss_script": 3,
            "xss_onerror": 2,
            "sql_union": 3,
            "sql_drop": 5,
            "cmd_shell": 5,
            "prompt_ignore": 2,
            "prompt_jailbreak": 4,
            "malicious_url": 3,
            "path_traversal": 2,
        }
    
    def validate(self, text: str) -> InputValidationResult:
        """
        验证输入安全性
        
        Args:
            text: 待验证的文本
            
        Returns:
            InputValidationResult: 验证结果
        """
        if not self.enabled:
            return InputValidationResult(allowed=True, threat_level=ThreatLevel.SAFE)
        
        if not text:
            return InputValidationResult(allowed=True, threat_level=ThreatLevel.SAFE)
        
        detected_threats: List[str] = []
        detected_sensitive: List[str] = []
        threat_score = 0
        
        # 检测危险模式
        for pattern_name, pattern in self.DANGEROUS_PATTERNS.items():
            if pattern.search(text):
                detected_threats.append(pattern_name)
                threat_score += self.threat_weights.get(pattern_name, 1)
        
        # 检测敏感信息
        if not self.allow_sensitive:
            for pattern_name, pattern in self.SENSITIVE_PATTERNS.items():
                if pattern.search(text):
                    detected_sensitive.append(pattern_name)
                    threat_score += 1
        
        # 确定威胁级别
        threat_level = self._calculate_threat_level(threat_score)
        
        # 判断是否允许
        allowed = True
        message = None
        
        if threat_level == ThreatLevel.CRITICAL:
            allowed = False
            message = "检测到严重安全威胁，输入被拦截"
        elif threat_level == ThreatLevel.HIGH:
            if self.strict_mode:
                allowed = False
                message = "检测到高危威胁，严格模式下输入被拦截"
            else:
                message = "检测到高危威胁，需要审核"
        elif detected_sensitive and self.strict_mode:
            allowed = False
            message = "检测到敏感信息，请使用脱敏后的数据"
        
        if detected_threats or detected_sensitive:
            logger.warning(
                f"Input validation: threats={detected_threats}, "
                f"sensitive={detected_sensitive}, level={threat_level}"
            )
        
        return InputValidationResult(
            allowed=allowed,
            threat_level=threat_level,
            message=message,
            detected_patterns=detected_threats + detected_sensitive
        )
    
    def sanitize(self, text: str) -> str:
        """
        尝试清理危险内容
        
        注意：这是防御性措施，不保证完全安全
        """
        sanitized = text
        
        # 移除 script 标签
        sanitized = self.DANGEROUS_PATTERNS["xss_script"].sub('', sanitized)
        
        # 转义 HTML 特殊字符
        sanitized = sanitized.replace('<', '&lt;').replace('>', '&gt;')
        sanitized = sanitized.replace('"', '&quot;').replace("'", '&#x27;')
        
        return sanitized
    
    def _calculate_threat_level(self, score: int) -> ThreatLevel:
        """根据威胁分数计算威胁级别"""
        if score >= self.THREAT_THRESHOLDS[ThreatLevel.CRITICAL]:
            return ThreatLevel.CRITICAL
        elif score >= self.THREAT_THRESHOLDS[ThreatLevel.HIGH]:
            return ThreatLevel.HIGH
        elif score >= self.THREAT_THRESHOLDS[ThreatLevel.MEDIUM]:
            return ThreatLevel.MEDIUM
        elif score >= self.THREAT_THRESHOLDS[ThreatLevel.LOW]:
            return ThreatLevel.LOW
        return ThreatLevel.SAFE


# 便捷函数
_default_guard: Optional[InputGuard] = None


def get_input_guard(config: Optional[Dict] = None) -> InputGuard:
    """获取全局输入护栏实例"""
    global _default_guard
    if _default_guard is None:
        _default_guard = InputGuard(config)
    return _default_guard


def validate_input(text: str, config: Optional[Dict] = None) -> InputValidationResult:
    """快捷验证函数"""
    guard = get_input_guard(config)
    return guard.validate(text)

"""
护栏注册器 - 统一管理输入/输出护栏
"""

from typing import Dict, Any, Optional, List
from dataclasses import dataclass
import logging

from .input_guard import InputGuard, InputValidationResult, ThreatLevel
from .output_guard import OutputGuard, OutputValidationResult

logger = logging.getLogger(__name__)


@dataclass
class GuardrailCheckResult:
    """护栏检查结果"""
    allowed: bool  # 对于输入：是否允许；对于输出：是否有效
    input_result: Optional[InputValidationResult] = None
    output_result: Optional[OutputValidationResult] = None
    bypass_token: Optional[str] = None  # 用于紧急放行的 Token


class GuardrailRegistry:
    """
    护栏注册器
    
    统一管理输入/输出护栏，提供一站式安全检查
    """
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        """
        初始化护栏注册器
        
        Args:
            config: 配置字典
                - input: InputGuard 配置
                - output: OutputGuard 配置
                - emergency_bypass_tokens: 紧急放行 Token 列表
        """
        self.config = config or {}
        
        # 初始化各护栏
        self.input_guard = InputGuard(self.config.get("input", {}))
        self.output_guard = OutputGuard(self.config.get("output", {}))
        
        # 紧急放行 Token
        self._bypass_tokens = set(
            self.config.get("emergency_bypass_tokens", [])
        )
    
    def add_bypass_token(self, token: str):
        """添加紧急放行 Token"""
        self._bypass_tokens.add(token)
    
    def check_input(
        self, 
        text: str, 
        bypass_token: Optional[str] = None
    ) -> GuardrailCheckResult:
        """
        检查输入
        
        Args:
            text: 待检查的文本
            bypass_token: 放行 Token（紧急情况使用）
            
        Returns:
            GuardrailCheckResult: 检查结果
        """
        # 检查是否使用放行 Token
        if bypass_token and bypass_token in self._bypass_tokens:
            logger.warning("Input check bypassed with emergency token")
            return GuardrailCheckResult(
                allowed=True,
                bypass_token=bypass_token
            )
        
        # 执行输入检查
        result = self.input_guard.validate(text)
        
        return GuardrailCheckResult(
            allowed=result.allowed,
            input_result=result
        )
    
    def check_output(
        self,
        output: Any,
        schema: Optional[Dict[str, Any]] = None,
        required_fields: Optional[List[str]] = None
    ) -> GuardrailCheckResult:
        """
        检查输出
        
        Args:
            output: 待检查的输出
            schema: JSON Schema 定义
            required_fields: 必填字段列表
            
        Returns:
            GuardrailCheckResult: 检查结果
        """
        # 执行输出检查
        result = self.output_guard.validate(output, schema, required_fields)
        
        return GuardrailCheckResult(
            allowed=result.valid,
            output_result=result
        )
    
    def check(
        self,
        input_text: str,
        output: Any,
        schema: Optional[Dict[str, Any]] = None,
        bypass_token: Optional[str] = None
    ) -> Dict[str, GuardrailCheckResult]:
        """
        同时检查输入和输出
        
        Args:
            input_text: 输入文本
            output: 输出内容
            schema: Schema 定义
            bypass_token: 放行 Token
            
        Returns:
            Dict: {"input": InputResult, "output": OutputResult}
        """
        return {
            "input": self.check_input(input_text, bypass_token),
            "output": self.check_output(output, schema)
        }
    
    def register_content_filter(self, filter_func):
        """注册内容过滤器"""
        self.output_guard.register_content_filter(filter_func)

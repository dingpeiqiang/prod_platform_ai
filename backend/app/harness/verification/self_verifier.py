"""
自我验证循环 - AI 输出打分，不合格则重新执行

核心原理：
1. Schema 验证（快速检查）
2. LLM 验证（深度检查）
3. 评分与阈值判断
4. 自动修复建议
"""

from typing import Dict, Any, Optional, Callable, List
from dataclasses import dataclass, field
from enum import Enum
import json
import logging
import asyncio

logger = logging.getLogger(__name__)


class VerificationLevel(Enum):
    """验证级别"""
    FAST = "fast"        # 仅 Schema 验证
    STANDARD = "standard"  # Schema + 基本检查
    DEEP = "deep"        # Schema + LLM 深度验证


class ScoreLevel(Enum):
    """评分等级"""
    EXCELLENT = "excellent"   # >= 90
    GOOD = "good"              # >= 70
    ACCEPTABLE = "acceptable"  # >= 50
    POOR = "poor"              # < 50


@dataclass
class VerificationResult:
    """验证结果"""
    passed: bool
    score: float  # 0-100
    level: ScoreLevel
    issues: List[str] = field(default_factory=list)
    suggestions: List[str] = field(default_factory=list)
    verification_type: str = "schema"
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict:
        return {
            "passed": self.passed,
            "score": self.score,
            "level": self.level.value,
            "issues": self.issues,
            "suggestions": self.suggestions,
            "verification_type": self.verification_type,
            "metadata": self.metadata
        }


class SelfVerifier:
    """
    AI 输出自验证器
    
    通过多层次验证确保 AI 输出质量：
    1. Schema 验证 - 格式正确性
    2. 必需字段验证 - 完整性检查
    3. 类型验证 - 数据类型正确性
    4. 范围验证 - 数值/长度合理性
    5. 自定义验证 - 业务规则验证（可选）
    
    使用示例：
    ```python
    verifier = SelfVerifier(llm_client=None)  # 不使用 LLM 验证
    
    # Schema 验证
    result = verifier.verify_with_schema(
        output={"name": "张三", "age": 25},
        schema={
            "type": "object",
            "required": ["name", "age"],
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer", "minimum": 0}
            }
        }
    )
    
    # 自定义验证
    result = verifier.verify_custom(
        output={"email": "test@example.com"},
        validators=[
            ("email格式", lambda x: "@" in x.get("email", "")),
            ("手机号", lambda x: len(x.get("phone", "")) == 11),
        ]
    )
    ```
    """

    def __init__(
        self,
        llm_client: Optional[Any] = None,
        default_level: VerificationLevel = VerificationLevel.STANDARD,
        pass_threshold: float = 70.0,
        enable_llm_verification: bool = False
    ):
        """
        初始化验证器
        
        Args:
            llm_client: LLM 客户端（用于深度验证，可选）
            default_level: 默认验证级别
            pass_threshold: 通过阈值（默认 70 分）
            enable_llm_verification: 是否启用 LLM 验证
        """
        self.llm_client = llm_client
        self.default_level = default_level
        self.pass_threshold = pass_threshold
        self.enable_llm_verification = enable_llm_verification and llm_client is not None
        
        # LLM 验证提示词
        self._llm_verification_prompt = """你是一个 AI 输出质量验证专家。请评估以下输出是否满足要求。

任务描述：{task_description}
输出内容：{output}

评估维度（每项 25 分，满分 100）：
1. 格式正确性 - 输出是否符合指定格式（如 JSON）
2. 内容完整性 - 是否包含所有必要信息
3. 逻辑一致性 - 内容是否自洽、合理
4. 安全性检查 - 是否包含不当内容

请返回严格的 JSON 格式评估结果：
{{"score": 0-100, "issues": ["问题列表"], "suggestions": ["改进建议"]}}

只返回 JSON，不要有其他内容。"""

    def verify_with_schema(
        self,
        output: Any,
        schema: Dict[str, Any],
        required_fields: Optional[List[str]] = None,
        custom_validators: Optional[List[Callable]] = None
    ) -> VerificationResult:
        """
        使用 Schema 验证输出
        
        Args:
            output: 待验证的输出
            schema: JSON Schema 定义
            required_fields: 必需字段列表（覆盖 schema 中的 required）
            custom_validators: 自定义验证函数列表
            
        Returns:
            VerificationResult: 验证结果
        """
        issues = []
        suggestions = []
        score = 100.0
        metadata = {"schema_validated": True}

        # 1. 类型检查
        type_check = self._check_type(output, schema)
        if not type_check["valid"]:
            issues.append(f"类型错误: {type_check['message']}")
            score -= 30

        # 2. 必需字段检查
        required = required_fields or schema.get("required", [])
        if required:
            field_check = self._check_required_fields(output, required)
            if field_check["missing"]:
                issues.append(f"缺少必需字段: {', '.join(field_check['missing'])}")
                score -= 20 * len(field_check["missing"]) / max(len(required), 1)

        # 3. 字段验证
        properties = schema.get("properties", {})
        if properties and isinstance(output, dict):
            field_check = self._check_fields(output, properties)
            issues.extend(field_check["issues"])
            score -= field_check["score_deduction"]

        # 4. 自定义验证
        if custom_validators:
            custom_check = self._run_custom_validators(output, custom_validators)
            issues.extend(custom_check["issues"])
            score -= custom_check["score_deduction"]

        # 5. 计算最终评分
        score = max(0, min(100, score))
        passed = score >= self.pass_threshold and len(issues) == 0

        # 生成建议
        if issues:
            suggestions = self._generate_suggestions(issues)

        return VerificationResult(
            passed=passed,
            score=score,
            level=self._get_score_level(score),
            issues=issues,
            suggestions=suggestions,
            verification_type="schema",
            metadata=metadata
        )

    def verify_custom(
        self,
        output: Any,
        validators: List[tuple],
        weights: Optional[List[float]] = None
    ) -> VerificationResult:
        """
        自定义验证
        
        Args:
            output: 待验证的输出
            validators: 验证规则列表 [(名称, 验证函数), ...]
            weights: 每个验证的权重（可选）
            
        Returns:
            VerificationResult: 验证结果
        """
        issues = []
        passed_count = 0
        total = len(validators)

        for i, (name, validator) in enumerate(validators):
            try:
                result = validator(output)
                if not result:
                    issues.append(f"{name} 验证失败")
                else:
                    passed_count += 1
            except Exception as e:
                issues.append(f"{name} 验证出错: {str(e)}")

        # 计算分数
        if total > 0:
            score = (passed_count / total) * 100
        else:
            score = 100

        passed = score >= self.pass_threshold and len(issues) == 0

        return VerificationResult(
            passed=passed,
            score=score,
            level=self._get_score_level(score),
            issues=issues,
            suggestions=self._generate_suggestions(issues) if issues else [],
            verification_type="custom"
        )

    def verify_output(
        self,
        output: Any,
        schema: Optional[Dict] = None,
        required_fields: Optional[List[str]] = None,
        task_description: str = "",
        level: Optional[VerificationLevel] = None,
        custom_validators: Optional[List[Callable]] = None
    ) -> VerificationResult:
        """
        综合验证输出
        
        Args:
            output: 待验证的输出
            schema: JSON Schema（可选）
            required_fields: 必需字段（可选）
            task_description: 任务描述（用于 LLM 验证）
            level: 验证级别
            custom_validators: 自定义验证
            
        Returns:
            VerificationResult: 验证结果
        """
        level = level or self.default_level
        all_issues = []
        total_score = 100.0
        suggestions = []

        # 1. Schema 验证（如果提供）
        if schema:
            schema_result = self.verify_with_schema(
                output, schema, required_fields, custom_validators
            )
            all_issues.extend(schema_result.issues)
            total_score = min(total_score, schema_result.score)
            suggestions.extend(schema_result.suggestions)
        elif custom_validators:
            # 只有自定义验证
            custom_result = self.verify_custom(output, custom_validators)
            all_issues.extend(custom_result.issues)
            total_score = min(total_score, custom_result.score)
            suggestions.extend(custom_result.suggestions)

        # 2. LLM 深度验证（可选）
        if level == VerificationLevel.DEEP and self.enable_llm_verification:
            llm_result = self._verify_with_llm(output, task_description)
            all_issues.extend(llm_result.issues)
            total_score = min(total_score, llm_result.score)
            suggestions.extend(llm_result.suggestions)

        # 3. 基本安全检查（始终执行）
        security_check = self._check_security(output)
        if not security_check["passed"]:
            all_issues.extend(security_check["issues"])
            total_score -= 20

        passed = total_score >= self.pass_threshold and len(all_issues) == 0

        return VerificationResult(
            passed=passed,
            score=max(0, min(100, total_score)),
            level=self._get_score_level(total_score),
            issues=all_issues,
            suggestions=suggestions,
            verification_type="comprehensive"
        )

    async def verify_and_fix(
        self,
        output: Any,
        fix_handler: Optional[Callable] = None,
        max_retries: int = 2,
        **kwargs
    ) -> Dict[str, Any]:
        """
        验证并尝试修复
        
        如果验证不通过，尝试使用修复函数修复输出
        
        Args:
            output: 待验证和修复的输出
            fix_handler: 修复处理函数，签名: (output, issues) -> fixed_output
            max_retries: 最大修复尝试次数
            **kwargs: 传递给 verify_output 的参数
            
        Returns:
            Dict: {
                "output": 最终输出（可能已修复）,
                "verified": 是否通过验证,
                "score": 最终评分,
                "attempts": 尝试次数,
                "issues": 剩余问题
            }
        """
        current_output = output
        attempts = 0
        final_result = None

        while attempts < max_retries + 1:
            result = self.verify_output(current_output, **kwargs)
            
            if result.passed:
                final_result = result
                break
            
            attempts += 1
            
            if attempts > max_retries or not fix_handler:
                final_result = result
                break
            
            # 尝试修复
            try:
                logger.info(f"Attempting to fix output, attempt {attempts}")
                current_output = fix_handler(current_output, result.issues)
            except Exception as e:
                logger.error(f"Fix handler error: {e}")
                final_result = result
                break

        return {
            "output": current_output,
            "verified": final_result.passed if final_result else False,
            "score": final_result.score if final_result else 0,
            "attempts": attempts,
            "issues": final_result.issues if final_result else [],
            "result": final_result
        }

    # ==================== 内部方法 ====================

    def _check_type(self, output: Any, schema: Dict) -> Dict:
        """检查类型"""
        expected_type = schema.get("type", "object")
        type_map = {
            "string": str,
            "number": (int, float),
            "integer": int,
            "boolean": bool,
            "array": list,
            "object": dict,
            "null": type(None)
        }
        
        expected_class = type_map.get(expected_type)
        if expected_class and not isinstance(output, expected_class):
            return {
                "valid": False,
                "message": f"期望 {expected_type}，实际 {type(output).__name__}"
            }
        return {"valid": True}

    def _check_required_fields(self, output: Any, required: List[str]) -> Dict:
        """检查必需字段"""
        missing = []
        if isinstance(output, dict):
            missing = [f for f in required if f not in output or output[f] is None]
        return {"missing": missing}

    def _check_fields(self, output: Dict, properties: Dict) -> Dict:
        """检查字段"""
        issues = []
        score_deduction = 0

        for field_name, field_schema in properties.items():
            if field_name not in output:
                continue
                
            value = output[field_name]
            
            # 类型检查
            expected_type = field_schema.get("type")
            if expected_type:
                type_valid = self._check_field_type(value, expected_type)
                if not type_valid:
                    issues.append(f"字段 {field_name} 类型错误")
                    score_deduction += 5
            
            # 最小值/最大值
            if "minimum" in field_schema and isinstance(value, (int, float)):
                if value < field_schema["minimum"]:
                    issues.append(f"字段 {field_name} 小于最小值 {field_schema['minimum']}")
                    score_deduction += 3
            
            if "maximum" in field_schema and isinstance(value, (int, float)):
                if value > field_schema["maximum"]:
                    issues.append(f"字段 {field_name} 大于最大值 {field_schema['maximum']}")
                    score_deduction += 3
            
            # 字符串长度
            if "minLength" in field_schema and isinstance(value, str):
                if len(value) < field_schema["minLength"]:
                    issues.append(f"字段 {field_name} 长度小于最小值 {field_schema['minLength']}")
                    score_deduction += 3
            
            if "maxLength" in field_schema and isinstance(value, str):
                if len(value) > field_schema["maxLength"]:
                    issues.append(f"字段 {field_name} 长度大于最大值 {field_schema['maxLength']}")
                    score_deduction += 3
            
            # 枚举值
            if "enum" in field_schema:
                if value not in field_schema["enum"]:
                    issues.append(f"字段 {field_name} 不在允许的值范围内")
                    score_deduction += 5

        return {"issues": issues, "score_deduction": min(score_deduction, 30)}

    def _check_field_type(self, value: Any, expected_type: str) -> bool:
        """检查字段类型"""
        if expected_type == "string":
            return isinstance(value, str)
        elif expected_type == "number":
            return isinstance(value, (int, float)) and not isinstance(value, bool)
        elif expected_type == "integer":
            return isinstance(value, int) and not isinstance(value, bool)
        elif expected_type == "boolean":
            return isinstance(value, bool)
        elif expected_type == "array":
            return isinstance(value, list)
        elif expected_type == "object":
            return isinstance(value, dict)
        return True

    def _run_custom_validators(
        self,
        output: Any,
        validators: List[Callable]
    ) -> Dict:
        """运行自定义验证"""
        issues = []
        score_deduction = 0

        for validator in validators:
            try:
                if not validator(output):
                    issues.append(f"自定义验证失败")
                    score_deduction += 10
            except Exception as e:
                issues.append(f"验证执行错误: {str(e)}")
                score_deduction += 5

        return {"issues": issues, "score_deduction": min(score_deduction, 30)}

    def _check_security(self, output: Any) -> Dict:
        """安全检查"""
        issues = []
        
        # 转换为字符串进行模式匹配
        output_str = json.dumps(output, ensure_ascii=False) if not isinstance(output, str) else output
        
        # 检测敏感信息模式
        sensitive_patterns = [
            (r'password["\']?\s*[:=]\s*["\']?\S+', "可能包含密码明文"),
            (r'api[_-]?key["\']?\s*[:=]\s*["\']?\S+', "可能包含 API Key"),
            (r'token["\']?\s*[:=]\s*["\']?\S+', "可能包含 Token"),
            (r'secret["\']?\s*[:=]\s*["\']?\S+', "可能包含密钥"),
        ]
        
        import re
        for pattern, message in sensitive_patterns:
            if re.search(pattern, output_str, re.IGNORECASE):
                issues.append(f"安全警告: {message}")
        
        return {
            "passed": len(issues) == 0,
            "issues": issues
        }

    async def _verify_with_llm(
        self,
        output: Any,
        task_description: str
    ) -> VerificationResult:
        """使用 LLM 进行深度验证"""
        if not self.llm_client:
            return VerificationResult(
                passed=True, score=100, level=ScoreLevel.EXCELLENT
            )

        try:
            prompt = self._llm_verification_prompt.format(
                task_description=task_description,
                output=json.dumps(output, ensure_ascii=False, indent=2)
            )
            
            response = await self.llm_client.chat(prompt)
            
            # 解析 LLM 响应
            result_data = json.loads(response)
            
            return VerificationResult(
                passed=result_data.get("score", 0) >= self.pass_threshold,
                score=result_data.get("score", 0),
                level=self._get_score_level(result_data.get("score", 0)),
                issues=result_data.get("issues", []),
                suggestions=result_data.get("suggestions", []),
                verification_type="llm"
            )
        except Exception as e:
            logger.error(f"LLM verification error: {e}")
            return VerificationResult(
                passed=True,
                score=50,
                level=ScoreLevel.ACCEPTABLE,
                issues=["LLM 验证失败，降级为基本验证"],
                verification_type="llm_fallback"
            )

    def _get_score_level(self, score: float) -> ScoreLevel:
        """获取评分等级"""
        if score >= 90:
            return ScoreLevel.EXCELLENT
        elif score >= 70:
            return ScoreLevel.GOOD
        elif score >= 50:
            return ScoreLevel.ACCEPTABLE
        else:
            return ScoreLevel.POOR

    def _generate_suggestions(self, issues: List[str]) -> List[str]:
        """根据问题生成修复建议"""
        suggestions = []
        
        for issue in issues:
            if "类型错误" in issue:
                suggestions.append("检查字段类型是否与预期一致")
            elif "缺少" in issue:
                suggestions.append("补充缺失的必需字段")
            elif "长度" in issue:
                suggestions.append("调整字段长度至允许范围内")
            elif "值范围" in issue:
                suggestions.append("将字段值调整至允许范围内")
            elif "格式" in issue:
                suggestions.append("检查数据格式是否正确")
            else:
                suggestions.append("请检查并修正相关问题")
        
        return list(dict.fromkeys(suggestions))  # 去重


# 便捷函数
def quick_verify(output: Any, schema: Dict) -> bool:
    """快速验证（仅 Schema）"""
    verifier = SelfVerifier()
    result = verifier.verify_with_schema(output, schema)
    return result.passed


def verify_json(output: Any) -> VerificationResult:
    """验证输出是否为有效 JSON"""
    try:
        if isinstance(output, str):
            json.loads(output)
        elif isinstance(output, dict):
            json.dumps(output)
        return VerificationResult(
            passed=True, score=100, level=ScoreLevel.EXCELLENT
        )
    except Exception as e:
        return VerificationResult(
            passed=False,
            score=0,
            level=ScoreLevel.POOR,
            issues=[f"JSON 解析错误: {str(e)}"]
        )

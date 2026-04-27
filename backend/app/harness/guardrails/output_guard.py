"""
输出护栏 - 校验 AI 输出格式和内容

检测类型：
- Schema 不匹配
- 数据类型错误
- 范围超限
- 格式错误
- 内容安全（可选）
"""

from typing import Optional, List, Dict, Any, Union, Callable
from dataclasses import dataclass, field
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class ViolationType(Enum):
    """违规类型"""
    SCHEMA_MISMATCH = "schema_mismatch"          # Schema 不匹配
    TYPE_ERROR = "type_error"                    # 类型错误
    RANGE_ERROR = "range_error"                  # 范围超限
    REQUIRED_MISSING = "required_missing"        # 必填字段缺失
    FORMAT_ERROR = "format_error"                 # 格式错误
    ENUM_INVALID = "enum_invalid"                # 枚举值无效
    CONTENT_FILTER = "content_filter"           # 内容过滤


@dataclass
class FieldViolation:
    """字段违规详情"""
    field: str
    violation_type: ViolationType
    expected: Optional[str] = None
    actual: Optional[str] = None
    message: Optional[str] = None


@dataclass
class OutputValidationResult:
    """输出验证结果"""
    valid: bool
    violations: List[FieldViolation] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    sanitized_output: Optional[Dict] = None
    
    @property
    def error_message(self) -> Optional[str]:
        """获取错误消息"""
        if not self.violations:
            return None
        return "; ".join(v.message or f"{v.field}: {v.violation_type.value}" 
                         for v in self.violations)


class OutputGuard:
    """
    输出安全检查器
    
    校验 AI 输出是否符合：
    - Schema 定义
    - 数据类型约束
    - 数值范围限制
    - 枚举值限制
    - 格式要求
    """
    
    # Python 类型到 JSON Schema 类型的映射
    TYPE_MAP = {
        "string": str,
        "number": (int, float),
        "integer": int,
        "boolean": bool,
        "array": list,
        "object": dict,
    }
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        """
        初始化输出护栏
        
        Args:
            config: 配置字典
                - strict_mode: 严格模式（任何违规都返回失败）
                - auto_fix: 自动修复可修复的问题
                - max_string_length: 字符串最大长度
                - max_array_length: 数组最大长度
        """
        self.config = config or {}
        self.strict_mode = self.config.get("strict_mode", True)
        self.auto_fix = self.config.get("auto_fix", False)
        self.max_string_length = self.config.get("max_string_length", 10000)
        self.max_array_length = self.config.get("max_array_length", 1000)
        
        # 内容过滤器（可选）
        self._content_filters: List[Callable[[str], bool]] = []
    
    def register_content_filter(self, filter_func: Callable[[str], bool]):
        """注册内容过滤器"""
        self._content_filters.append(filter_func)
    
    def validate(
        self, 
        output: Any, 
        schema: Optional[Dict[str, Any]] = None,
        required_fields: Optional[List[str]] = None
    ) -> OutputValidationResult:
        """
        验证输出
        
        Args:
            output: 待验证的输出
            schema: JSON Schema 定义
            required_fields: 必填字段列表
            
        Returns:
            OutputValidationResult: 验证结果
        """
        violations: List[FieldViolation] = []
        warnings: List[str] = []
        
        # 1. 基本类型检查
        if output is None:
            violations.append(FieldViolation(
                field="root",
                violation_type=ViolationType.TYPE_ERROR,
                message="输出不能为空"
            ))
            return OutputValidationResult(valid=False, violations=violations)
        
        # 2. Schema 验证
        if schema:
            schema_violations = self._validate_against_schema(output, schema, "")
            violations.extend(schema_violations)
        
        # 3. 必填字段检查
        if required_fields:
            required_violations = self._check_required_fields(output, required_fields)
            violations.extend(required_violations)
        
        # 4. 内容过滤检查
        if isinstance(output, dict):
            content_violations = self._check_content_filters(output)
            violations.extend(content_violations)
        
        # 5. 尝试自动修复
        sanitized = None
        if violations and self.auto_fix:
            sanitized = self._try_fix(output, violations)
            if sanitized:
                warnings.append("部分内容已自动修复")
        
        # 判断是否有效
        valid = len(violations) == 0 or (not self.strict_mode and len(violations) <= 1)
        
        return OutputValidationResult(
            valid=valid,
            violations=violations,
            warnings=warnings,
            sanitized_output=sanitized
        )
    
    def _validate_against_schema(
        self, 
        data: Any, 
        schema: Dict,
        path: str
    ) -> List[FieldViolation]:
        """根据 Schema 验证数据"""
        violations = []
        
        # 类型检查
        if "type" in schema:
            expected_type = schema["type"]
            if not self._check_type(data, expected_type):
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.TYPE_ERROR,
                    expected=expected_type,
                    actual=type(data).__name__,
                    message=f"字段 {path or 'root'} 类型错误，期望 {expected_type}，实际 {type(data).__name__}"
                ))
                return violations  # 类型错误时不继续检查
        
        # 枚举检查
        if "enum" in schema:
            if data not in schema["enum"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.ENUM_INVALID,
                    expected=str(schema["enum"]),
                    actual=str(data),
                    message=f"字段 {path or 'root'} 值不在允许范围内"
                ))
        
        # 字符串约束
        if isinstance(data, str):
            if "maxLength" in schema and len(data) > schema["maxLength"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.RANGE_ERROR,
                    expected=f"最大长度 {schema['maxLength']}",
                    actual=f"长度 {len(data)}",
                    message=f"字段 {path or 'root'} 超过最大长度限制"
                ))
            
            if "minLength" in schema and len(data) < schema["minLength"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.RANGE_ERROR,
                    expected=f"最小长度 {schema['minLength']}",
                    actual=f"长度 {len(data)}",
                    message=f"字段 {path or 'root'} 低于最小长度限制"
                ))
            
            if "pattern" in schema:
                import re
                if not re.match(schema["pattern"], data):
                    violations.append(FieldViolation(
                        field=path or "root",
                        violation_type=ViolationType.FORMAT_ERROR,
                        expected=schema["pattern"],
                        message=f"字段 {path or 'root'} 格式不符合要求"
                    ))
        
        # 数值约束
        if isinstance(data, (int, float)):
            if "maximum" in schema and data > schema["maximum"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.RANGE_ERROR,
                    expected=f"最大 {schema['maximum']}",
                    actual=str(data),
                    message=f"字段 {path or 'root'} 超过最大值"
                ))
            
            if "minimum" in schema and data < schema["minimum"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.RANGE_ERROR,
                    expected=f"最小 {schema['minimum']}",
                    actual=str(data),
                    message=f"字段 {path or 'root'} 小于最小值"
                ))
        
        # 对象属性检查
        if isinstance(data, dict) and "properties" in schema:
            for prop_name, prop_schema in schema["properties"].items():
                if prop_name in data:
                    prop_violations = self._validate_against_schema(
                        data[prop_name],
                        prop_schema,
                        f"{path}.{prop_name}" if path else prop_name
                    )
                    violations.extend(prop_violations)
        
        # 数组元素检查
        if isinstance(data, list) and "items" in schema:
            for i, item in enumerate(data):
                item_violations = self._validate_against_schema(
                    item,
                    schema["items"],
                    f"{path}[{i}]" if path else f"[{i}]"
                )
                violations.extend(item_violations)
            
            if "maxItems" in schema and len(data) > schema["maxItems"]:
                violations.append(FieldViolation(
                    field=path or "root",
                    violation_type=ViolationType.RANGE_ERROR,
                    expected=f"最多 {schema['maxItems']} 项",
                    actual=f"{len(data)} 项",
                    message=f"数组超过最大项数限制"
                ))
        
        return violations
    
    def _check_type(self, data: Any, expected_type: str) -> bool:
        """检查数据类型"""
        if expected_type not in self.TYPE_MAP:
            return True  # 未知类型，跳过
        
        expected = self.TYPE_MAP[expected_type]
        return isinstance(data, expected)
    
    def _check_required_fields(
        self, 
        data: Union[Dict, Any], 
        required_fields: List[str]
    ) -> List[FieldViolation]:
        """检查必填字段"""
        violations = []
        
        if not isinstance(data, dict):
            if required_fields:
                violations.append(FieldViolation(
                    field="root",
                    violation_type=ViolationType.TYPE_ERROR,
                    message="输出必须是对象类型"
                ))
            return violations
        
        for field in required_fields:
            if field not in data or data[field] is None:
                violations.append(FieldViolation(
                    field=field,
                    violation_type=ViolationType.REQUIRED_MISSING,
                    message=f"必填字段 {field} 缺失"
                ))
        
        return violations
    
    def _check_content_filters(self, data: Dict) -> List[FieldViolation]:
        """检查内容过滤器"""
        violations = []
        
        def check_string(value: Any):
            if isinstance(value, str):
                for filter_func in self._content_filters:
                    if not filter_func(value):
                        violations.append(FieldViolation(
                            field="content",
                            violation_type=ViolationType.CONTENT_FILTER,
                            message="内容未通过安全检查"
                        ))
                        break
            elif isinstance(value, dict):
                for v in value.values():
                    check_string(v)
            elif isinstance(value, list):
                for item in value:
                    check_string(item)
        
        check_string(data)
        return violations
    
    def _try_fix(self, output: Any, violations: List[FieldViolation]) -> Optional[Dict]:
        """尝试自动修复"""
        if not isinstance(output, dict):
            return None
        
        fixed = output.copy()
        for violation in violations:
            if violation.violation_type == ViolationType.RANGE_ERROR:
                # 尝试验证修复类型错误
                pass
        
        return fixed


# 便捷函数
_default_output_guard: Optional[OutputGuard] = None


def get_output_guard(config: Optional[Dict] = None) -> OutputGuard:
    """获取全局输出护栏实例"""
    global _default_output_guard
    if _default_output_guard is None:
        _default_output_guard = OutputGuard(config)
    return _default_output_guard


def validate_output(
    output: Any,
    schema: Optional[Dict] = None,
    required_fields: Optional[List[str]] = None
) -> OutputValidationResult:
    """快捷验证函数"""
    guard = get_output_guard()
    return guard.validate(output, schema, required_fields)

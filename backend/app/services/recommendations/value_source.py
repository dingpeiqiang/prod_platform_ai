"""
值来源定义 - 用于推荐引擎的确定性值和推荐候选的区分
"""
from enum import Enum
from dataclasses import dataclass, field
from typing import Any, Optional, Dict


class ValueSource(Enum):
    """值来源类型"""
    # ========== 确定性值来源（优先级 0-9）==========
    USER_EXPLICIT = "user_explicit"          # 用户明确指定
    LLM_EXTRACTION = "llm_extraction"        # LLM 提取推断
    TOOL_RESULT = "tool_result"              # 工具调用结果
    CONTEXT_VARIABLE = "context_variable"     # 上下文变量
    
    # ========== 推荐候选来源（优先级 10+）==========
    AI_INTELLIGENT = "ai_intelligent"        # AI 智能推荐
    USER_PERSONALIZED = "user_personalized"  # 用户个性化
    FREQUENCY = "frequency"                  # 历史频率
    TIME_DECAY = "time_decay"                # 时间衰减
    STATIC = "static"                        # 静态配置
    
    # ========== 特殊处理（优先级 90+）==========
    DEFAULT = "default"                      # 字段默认值
    ENUM_OPTION = "enum_option"              # 枚举选项（兜底）


class SourcePriority:
    """来源优先级（数字越小优先级越高）"""
    PRIORITY = {
        ValueSource.USER_EXPLICIT: 0,       # 最高：用户明确指定
        ValueSource.LLM_EXTRACTION: 1,       # LLM 提取
        ValueSource.TOOL_RESULT: 2,          # 工具结果
        ValueSource.CONTEXT_VARIABLE: 3,      # 上下文
        
        ValueSource.AI_INTELLIGENT: 10,      # AI 智能推荐
        ValueSource.USER_PERSONALIZED: 20,   # 用户个性化
        ValueSource.FREQUENCY: 30,           # 历史频率
        ValueSource.TIME_DECAY: 40,          # 时间衰减
        ValueSource.STATIC: 50,              # 静态配置
        
        ValueSource.DEFAULT: 90,            # 默认值
        ValueSource.ENUM_OPTION: 100,        # 枚举选项
    }


@dataclass
class ValueItem:
    """值项：包含值和来源信息"""
    value: Any                              # 值
    source: ValueSource                      # 来源类型
    confidence: float = 1.0                 # 置信度 (0-1)
    reason: str = ""                        # 推荐理由
    label: Optional[str] = None              # 显示标签（如枚举的label）
    strategy_weight: float = 1.0            # 策略权重（用于合并计算）
    metadata: dict = field(default_factory=dict)  # 元数据
    
    def get_priority(self) -> int:
        """获取优先级"""
        return SourcePriority.PRIORITY.get(self.source, 999)
    
    def is_definite(self) -> bool:
        """是否是确定性值"""
        return self.get_priority() < 10
    
    def is_candidate(self) -> bool:
        """是否是推荐候选"""
        return self.get_priority() >= 10
    
    def get_final_score(self) -> float:
        """计算综合分数（置信度 × 策略权重）"""
        return self.confidence * self.strategy_weight


@dataclass
class FieldRecommendation:
    """字段推荐结果"""
    field_code: str
    recommendations: list  # List[ValueItem]
    final_value: Any = None
    final_source: Optional[ValueSource] = None
    constraint_violations: list = field(default_factory=list)


@dataclass
class RecommendationDecision:
    """推荐决策结果"""
    field_code: str
    value: Any
    source: ValueSource
    confidence: float
    reason: str
    is_definite: bool
    recommendations_count: int = 0
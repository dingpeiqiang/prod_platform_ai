# Intent Handler 基础定义
# BaseIntentHandler ABC + IntentContext dataclass

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Dict, Any, Optional, AsyncGenerator

from sqlalchemy.orm import Session

# 避免循环引用，用字符串类型标注或延迟导入
@dataclass
class IntentContext:
    """意图处理器上下文数据袋 —— 替代 chat_stream() 内部十余个局部变量"""

    # 意图识别结果
    intent_data: Dict[str, Any]
    intent_result: str  # 原始 LLM JSON 字符串
    intent_type: str = ""
    confidence: float = 0.0

    # 本体与场景数据
    ontologies: Dict[str, Any] = field(default_factory=dict)
    ontologies_info: str = ""
    scene_keywords: str = ""

    # 原始请求相关
    request: Any = None  # ChatRequest 对象
    db: Session = None
    last_user_message: str = ""
    messages_text: str = ""

    # Prompt 相关
    intent_prompt: str = ""  # 构建好的意图识别 prompt

    # 统计与时间
    start_time: float = 0.0
    stream_stats: Any = None


class BaseIntentHandler(ABC):
    """意图处理器抽象基类"""

    # 子类必须设置此属性，用于注册器查找
    intent_type: str = ""

    @abstractmethod
    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        """
        处理意图并 yield SSE 帧。

        Args:
            ctx: 意图处理器上下文

        Yields:
            SSE 格式化的帧字符串 (data: {...}\n\n)
        """
        # 默认空实现 —— 如果子类未覆盖则什么都不 yield
        return

    def get_description(self) -> str:
        return self.intent_type

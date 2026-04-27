"""
上下文压缩器

功能：
- 长上下文压缩
- 关键信息提取
- token 计数与限制
- 智能摘要
"""

from typing import Dict, Any, Optional, List, Tuple, Callable
from dataclasses import dataclass, field
from enum import Enum
import logging
import json
import re

logger = logging.getLogger(__name__)


class CompressionStrategy(Enum):
    """压缩策略"""
    NONE = "none"                    # 不压缩
    TRUNCATE = "truncate"            # 直接截断
    SUMMARIZE = "summarize"          # 摘要压缩
    SELECTIVE = "selective"          # 选择性保留
    MIXED = "mixed"                  # 混合策略


@dataclass
class CompressionConfig:
    """压缩配置"""
    strategy: CompressionStrategy = CompressionStrategy.MIXED
    max_tokens: int = 4000           # 最大 token 数
    preserve_system_prompt: bool = True  # 保留系统提示
    preserve_recent_messages: int = 3    # 保留最近消息数
    importance_keywords: List[str] = field(default_factory=list)
    summary_llm: Optional[Callable] = None  # 摘要生成函数


@dataclass
class CompressionResult:
    """压缩结果"""
    original_text: str
    compressed_text: str
    original_tokens: int
    compressed_tokens: int
    compression_ratio: float
    strategy_used: CompressionStrategy
    preserved_sections: List[str] = field(default_factory=list)
    removed_sections: List[str] = field(default_factory=list)


class ContextCompressor:
    """
    上下文压缩器
    
    功能：
    1. Token 计数与估算
    2. 多策略压缩（截断、摘要、选择性）
    3. 关键信息保留
    4. 智能摘要生成
    
    使用示例：
    ```python
    compressor = ContextCompressor()
    
    # 压缩对话上下文
    result = compressor.compress(
        messages=[
            {"role": "system", "content": "你是一个助手..."},
            {"role": "user", "content": "用户消息1"},
            {"role": "assistant", "content": "助手回复1"},
            ...
        ],
        config=CompressionConfig(max_tokens=4000)
    )
    
    print(f"压缩率: {result.compression_ratio:.2%}")
    print(result.compressed_text)
    ```
    """

    def __init__(
        self,
        default_config: Optional[CompressionConfig] = None,
        tokenizer: Optional[Callable] = None
    ):
        """
        初始化压缩器
        
        Args:
            default_config: 默认配置
            tokenizer: 自定义分词器（接收字符串，返回 token 列表或数量）
        """
        self.default_config = default_config or CompressionConfig()
        self.tokenizer = tokenizer or self._simple_tokenize
    
    def compress(
        self,
        content: str,
        config: Optional[CompressionConfig] = None,
        **kwargs
    ) -> CompressionResult:
        """
        压缩内容
        
        Args:
            content: 待压缩内容
            config: 压缩配置
            **kwargs: 配置覆盖参数
            
        Returns:
            CompressionResult: 压缩结果
        """
        cfg = config or self.default_config
        
        # 更新配置
        for key, value in kwargs.items():
            if hasattr(cfg, key):
                setattr(cfg, key, value)
        
        original_tokens = self.count_tokens(content)
        
        # 检查是否需要压缩
        if original_tokens <= cfg.max_tokens:
            return CompressionResult(
                original_text=content,
                compressed_text=content,
                original_tokens=original_tokens,
                compressed_tokens=original_tokens,
                compression_ratio=1.0,
                strategy_used=CompressionStrategy.NONE
            )
        
        # 根据策略压缩
        if cfg.strategy == CompressionStrategy.TRUNCATE:
            return self._truncate(content, cfg, original_tokens)
        elif cfg.strategy == CompressionStrategy.SUMMARIZE:
            return self._summarize(content, cfg, original_tokens)
        elif cfg.strategy == CompressionStrategy.SELECTIVE:
            return self._selective_compress(content, cfg, original_tokens)
        elif cfg.strategy == CompressionStrategy.MIXED:
            return self._mixed_compress(content, cfg, original_tokens)
        else:
            return self._truncate(content, cfg, original_tokens)

    def compress_messages(
        self,
        messages: List[Dict[str, str]],
        config: Optional[CompressionConfig] = None,
        **kwargs
    ) -> CompressionResult:
        """
        压缩消息列表
        
        Args:
            messages: 消息列表 [{"role": "...", "content": "..."}]
            config: 压缩配置
            
        Returns:
            CompressionResult: 压缩结果
        """
        cfg = config or self.default_config
        
        for key, value in kwargs.items():
            if hasattr(cfg, key):
                setattr(cfg, key, value)
        
        # 计算总 token 数
        total_tokens = sum(self.count_tokens(m["content"]) for m in messages)
        
        if total_tokens <= cfg.max_tokens:
            return CompressionResult(
                original_text=json.dumps(messages, ensure_ascii=False),
                compressed_text=json.dumps(messages, ensure_ascii=False),
                original_tokens=total_tokens,
                compressed_tokens=total_tokens,
                compression_ratio=1.0,
                strategy_used=CompressionStrategy.NONE
            )
        
        # 分离系统消息和对话消息
        system_messages = []
        dialog_messages = []
        
        for msg in messages:
            if msg.get("role") == "system":
                system_messages.append(msg)
            else:
                dialog_messages.append(msg)
        
        preserved = []
        removed = []
        
        # 1. 保留系统消息
        if cfg.preserve_system_prompt:
            preserved.extend(system_messages)
        
        # 2. 保留最近消息
        recent_count = min(cfg.preserve_recent_messages, len(dialog_messages))
        if recent_count > 0:
            preserved.extend(dialog_messages[-recent_count:])
            removed.extend(dialog_messages[:-recent_count])
        
        # 3. 如果还不够，合并/摘要旧消息
        while self.count_tokens(json.dumps(preserved, ensure_ascii=False)) > cfg.max_tokens:
            if len(dialog_messages) <= recent_count:
                break
            
            # 合并最老的消息为一个摘要
            old_messages = dialog_messages[:-recent_count]
            if old_messages:
                summary = self._create_summary(old_messages)
                preserved.insert(1 if cfg.preserve_system_prompt else 0, {
                    "role": "system",
                    "content": f"[早期对话摘要] {summary}"
                })
                
                # 移除旧消息
                for _ in old_messages[:-1]:
                    if dialog_messages:
                        removed.append(dialog_messages[0])
                        dialog_messages = dialog_messages[1:]
        
        # 重新组装
        compressed_messages = preserved + dialog_messages[-recent_count:]
        
        return CompressionResult(
            original_text=json.dumps(messages, ensure_ascii=False),
            compressed_text=json.dumps(compressed_messages, ensure_ascii=False),
            original_tokens=total_tokens,
            compressed_tokens=self.count_tokens(json.dumps(compressed_messages, ensure_ascii=False)),
            compression_ratio=1 - (len(compressed_messages) / len(messages)),
            strategy_used=CompressionStrategy.MIXED,
            preserved_sections=[m["content"][:100] for m in preserved[:5]],
            removed_sections=[m["content"][:100] for m in removed[:5]]
        )

    def count_tokens(self, text: str) -> int:
        """估算 token 数"""
        return len(self.tokenizer(text))

    def _simple_tokenize(self, text: str) -> List[str]:
        """
        简单分词（用于估算）
        
        估算规则：中文每个字符约 1.5 tokens，英文每个单词约 1.3 tokens
        """
        # 移除多余空白
        text = re.sub(r'\s+', ' ', text).strip()
        
        if not text:
            return []
        
        # 估算：中文按字符，英文按单词
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', text))
        english_words = len(re.findall(r'[a-zA-Z]+', text))
        other_chars = len(text) - chinese_chars
        
        # 估算 tokens
        tokens = chinese_chars * 1.5 + english_words * 1.3 + other_chars
        
        return [text] * int(tokens)  # 返回模拟的 token 列表

    def _truncate(
        self,
        content: str,
        config: CompressionConfig,
        original_tokens: int
    ) -> CompressionResult:
        """截断策略"""
        max_chars = int(config.max_tokens * 4)  # 粗略估算
        
        if len(content) <= max_chars:
            return CompressionResult(
                original_text=content,
                compressed_text=content,
                original_tokens=original_tokens,
                compressed_tokens=original_tokens,
                compression_ratio=1.0,
                strategy_used=CompressionStrategy.TRUNCATE
            )
        
        truncated = content[:max_chars]
        
        return CompressionResult(
            original_text=content,
            compressed_text=truncated,
            original_tokens=original_tokens,
            compressed_tokens=self.count_tokens(truncated),
            compression_ratio=len(truncated) / len(content),
            strategy_used=CompressionStrategy.TRUNCATE
        )

    def _summarize(
        self,
        content: str,
        config: CompressionConfig,
        original_tokens: int
    ) -> CompressionResult:
        """摘要策略"""
        if config.summary_llm:
            try:
                summary = config.summary_llm(content, max_tokens=config.max_tokens)
            except Exception as e:
                logger.warning(f"Summary LLM failed: {e}")
                return self._truncate(content, config, original_tokens)
        else:
            summary = self._create_summary_from_text(content, config.max_tokens)
        
        return CompressionResult(
            original_text=content,
            compressed_text=summary,
            original_tokens=original_tokens,
            compressed_tokens=self.count_tokens(summary),
            compression_ratio=len(summary) / len(content),
            strategy_used=CompressionStrategy.SUMMARIZE
        )

    def _selective_compress(
        self,
        content: str,
        config: CompressionConfig,
        original_tokens: int
    ) -> CompressionResult:
        """选择性保留策略"""
        # 提取关键段落
        sections = self._split_into_sections(content)
        
        # 评估每个段落的重要性
        scored_sections = []
        for section in sections:
            score = self._calculate_importance(section, config)
            scored_sections.append((section, score))
        
        # 按重要性排序，保留重要的
        scored_sections.sort(key=lambda x: x[1], reverse=True)
        
        # 选择足够的内容
        selected = []
        current_tokens = 0
        
        for section, score in scored_sections:
            section_tokens = self.count_tokens(section)
            if current_tokens + section_tokens <= config.max_tokens:
                selected.append(section)
                current_tokens += section_tokens
        
        # 重新组装
        compressed = "\n\n".join(selected)
        
        return CompressionResult(
            original_text=content,
            compressed_text=compressed,
            original_tokens=original_tokens,
            compressed_tokens=current_tokens,
            compression_ratio=current_tokens / original_tokens,
            strategy_used=CompressionStrategy.SELECTIVE,
            preserved_sections=[s[:100] for s in selected[:5]]
        )

    def _mixed_compress(
        self,
        content: str,
        config: CompressionConfig,
        original_tokens: int
    ) -> CompressionResult:
        """混合策略：保留关键内容 + 摘要不重要的"""
        sections = self._split_into_sections(content)
        
        if len(sections) <= 2:
            return self._truncate(content, config, original_tokens)
        
        # 分类：重要和不重要
        important = []
        unimportant = []
        
        for section in sections:
            score = self._calculate_importance(section, config)
            if score > 0.5:
                important.append(section)
            else:
                unimportant.append(section)
        
        # 组合结果
        result_parts = []
        result_tokens = 0
        
        # 先放重要的
        for section in important:
            tokens = self.count_tokens(section)
            if result_tokens + tokens <= config.max_tokens:
                result_parts.append(section)
                result_tokens += tokens
        
        # 如果还有空间，放不重要的摘要
        if result_tokens < config.max_tokens:
            remaining_tokens = config.max_tokens - result_tokens
            summary = self._create_summary(unimportant, max_tokens=remaining_tokens)
            result_parts.append(summary)
            result_tokens += self.count_tokens(summary)
        
        compressed = "\n\n".join(result_parts)
        
        return CompressionResult(
            original_text=content,
            compressed_text=compressed,
            original_tokens=original_tokens,
            compressed_tokens=result_tokens,
            compression_ratio=result_tokens / original_tokens,
            strategy_used=CompressionStrategy.MIXED
        )

    def _split_into_sections(self, content: str) -> List[str]:
        """将内容分割为段落"""
        # 按双换行或特定分隔符分割
        sections = re.split(r'\n\n+', content)
        return [s.strip() for s in sections if s.strip()]

    def _calculate_importance(
        self,
        section: str,
        config: CompressionConfig
    ) -> float:
        """计算段落重要性"""
        score = 0.5  # 基础分数
        
        # 关键词匹配
        for keyword in config.importance_keywords:
            if keyword.lower() in section.lower():
                score += 0.1
        
        # 长度因素（太长或太短都可能不太重要）
        length = len(section)
        if 50 < length < 500:
            score += 0.1
        
        # 数字和结构化内容
        if re.search(r'\d+', section):
            score += 0.05
        if re.search(r'[{}\[\]]', section):
            score += 0.05
        
        return min(score, 1.0)

    def _create_summary(
        self,
        items: List[Any],
        max_tokens: Optional[int] = None
    ) -> str:
        """创建摘要"""
        if not items:
            return ""
        
        max_tokens = max_tokens or self.default_config.max_tokens
        
        if isinstance(items[0], dict):
            # 消息摘要
            summary_parts = []
            for item in items[:10]:  # 最多取 10 条
                role = item.get("role", "unknown")
                content = item.get("content", "")[:100]
                summary_parts.append(f"{role}: {content}")
            
            if len(items) > 10:
                summary_parts.append(f"... 共 {len(items) - 10} 条消息")
            
            return " | ".join(summary_parts)
        
        return str(items[:5])

    def _create_summary_from_text(
        self,
        text: str,
        max_tokens: int
    ) -> str:
        """从文本创建摘要"""
        sentences = re.split(r'[。！？\n]+', text)
        
        # 选择前几句
        summary = []
        current_tokens = 0
        
        for sentence in sentences[:10]:
            sentence = sentence.strip()
            if not sentence:
                continue
            
            tokens = self.count_tokens(sentence)
            if current_tokens + tokens <= max_tokens:
                summary.append(sentence)
                current_tokens += tokens
            else:
                break
        
        return "。".join(summary) + "。" if summary else text[:max_tokens]

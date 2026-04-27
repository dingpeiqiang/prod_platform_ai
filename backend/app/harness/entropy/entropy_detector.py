# -*- coding: utf-8 -*-
"""
熵检测器 - 输出熵检测
======================
检测AI输出的不确定性和混乱度

熵（Entropy）在AI系统中指输出的"混乱程度"或"不确定性"：
- 高熵：输出随机、不一致、不可预测
- 低熵：输出确定、一致、可预测

检测维度：
1. 一致性熵：多次输出一致性
2. 格式熵：输出格式规范性
3. 内容熵：内容完整性
4. 语义熵：语义一致性
"""

import asyncio
import re
import statistics
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Callable
import logging

logger = logging.getLogger(__name__)


class EntropyLevel(str, Enum):
    """熵级别"""
    VERY_LOW = "very_low"    # 极低熵（非常确定）
    LOW = "low"              # 低熵（确定）
    MEDIUM = "medium"        # 中等熵（有一定不确定性）
    HIGH = "high"            # 高熵（不确定）
    VERY_HIGH = "very_high"  # 极高熵（非常不确定）


@dataclass
class EntropyResult:
    """
    熵检测结果

    Attributes:
        overall_entropy: 总体熵值（0-1，1为最高熵）
        entropy_level: 熵级别
        consistency_entropy: 一致性熵
        format_entropy: 格式熵
        content_entropy: 内容熵
        semantic_entropy: 语义熵
        issues: 检测到的问题列表
        warnings: 警告列表
        suggestions: 建议列表
        metadata: 附加元数据
    """
    overall_entropy: float = 0.0
    entropy_level: EntropyLevel = EntropyLevel.LOW
    consistency_entropy: float = 0.0
    format_entropy: float = 0.0
    content_entropy: float = 0.0
    semantic_entropy: float = 0.0
    issues: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    suggestions: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)

    def is_acceptable(self, threshold: float = 0.5) -> bool:
        """判断熵是否可接受"""
        return self.overall_entropy <= threshold

    def needs_review(self) -> bool:
        """是否需要人工审核"""
        return self.entropy_level in (EntropyLevel.HIGH, EntropyLevel.VERY_HIGH)

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "overall_entropy": self.overall_entropy,
            "entropy_level": self.entropy_level.value,
            "consistency_entropy": self.consistency_entropy,
            "format_entropy": self.format_entropy,
            "content_entropy": self.content_entropy,
            "semantic_entropy": self.semantic_entropy,
            "issues": self.issues,
            "warnings": self.warnings,
            "suggestions": self.suggestions,
            "needs_review": self.needs_review()
        }


class EntropyDetector:
    """
    熵检测器

    功能：
    - 一致性检测（多次输出一致性）
    - 格式检测（输出格式规范性）
    - 内容完整性检测
    - 语义一致性检测
    - 自动阈值调整
    """

    def __init__(self):
        # 检测阈值
        self._thresholds = {
            "consistency": 0.3,    # 一致性熵阈值
            "format": 0.4,         # 格式熵阈值
            "content": 0.3,       # 内容熵阈值
            "semantic": 0.4,       # 语义熵阈值
            "overall": 0.5        # 总体熵阈值
        }

        # 历史检测结果（用于自适应阈值）
        self._history: List[EntropyResult] = []

        logger.info("EntropyDetector initialized")

    def set_threshold(self, dimension: str, value: float) -> None:
        """设置检测阈值"""
        if dimension in self._thresholds:
            self._thresholds[dimension] = max(0.0, min(1.0, value))
            logger.debug(f"Threshold {dimension} set to {value}")

    def get_thresholds(self) -> Dict[str, float]:
        """获取当前阈值"""
        return dict(self._thresholds)

    # ==================== 一致性检测 ====================

    def detect_consistency(self, outputs: List[str]) -> float:
        """
        检测输出一致性

        Args:
            outputs: 多次输出列表

        Returns:
            一致性熵值（0-1）
        """
        if len(outputs) <= 1:
            return 0.0  # 单次输出无一致性差异

        if len(outputs) == 2:
            return 0.0 if outputs[0] == outputs[1] else 1.0

        # 计算字符串相似度
        def similarity(s1: str, s2: str) -> float:
            # 简单相似度：字符集合重叠度
            set1, set2 = set(s1), set(s2)
            if not set1 and not set2:
                return 1.0
            intersection = len(set1 & set2)
            union = len(set1 | set2)
            return intersection / union if union > 0 else 1.0

        # 计算平均相似度
        total_sim = 0.0
        count = 0
        for i in range(len(outputs)):
            for j in range(i + 1, len(outputs)):
                total_sim += similarity(outputs[i], outputs[j])
                count += 1

        avg_similarity = total_sim / count if count > 0 else 1.0

        # 熵 = 1 - 相似度
        return 1.0 - avg_similarity

    # ==================== 格式检测 ====================

    def detect_format(self, text: str, expected_format: Optional[str] = None) -> float:
        """
        检测输出格式规范性

        Args:
            text: 输出文本
            expected_format: 期望格式（json/xml/markdown/plain）

        Returns:
            格式熵值（0-1）
        """
        if not text:
            return 1.0  # 空文本高熵

        entropy = 0.0

        if expected_format == "json":
            entropy = self._detect_json_format(text)
        elif expected_format == "xml":
            entropy = self._detect_xml_format(text)
        elif expected_format == "markdown":
            entropy = self._detect_markdown_format(text)
        else:
            # 无特定格式要求，检测通用格式一致性
            entropy = self._detect_generic_format(text)

        return entropy

    def _detect_json_format(self, text: str) -> float:
        """检测JSON格式规范性"""
        import json

        # 检查基本结构
        issues = 0

        # 1. 是否以 { 或 [ 开头
        if not text.strip().startswith(('{', '[')):
            issues += 0.3

        # 2. 尝试解析
        try:
            json.loads(text)
        except json.JSONDecodeError:
            issues += 0.4

        # 3. 检查键值对格式
        if ':' not in text and ',' not in text:
            issues += 0.2

        return min(1.0, issues)

    def _detect_xml_format(self, text: str) -> float:
        """检测XML格式规范性"""
        issues = 0

        # 检查标签结构
        if not text.strip().startswith('<'):
            issues += 0.3

        # 检查闭合标签
        open_tags = len(re.findall(r'<(\w+)[^>]*>', text))
        close_tags = len(re.findall(r'</(\w+)>', text))
        self_closing = len(re.findall(r'<(\w+)[^>]*/>', text))

        if open_tags > 0 and open_tags != close_tags + self_closing:
            issues += 0.4

        return min(1.0, issues)

    def _detect_markdown_format(self, text: str) -> float:
        """检测Markdown格式规范性"""
        issues = 0

        # 检查标题
        has_headers = bool(re.search(r'^#{1,6}\s', text, re.MULTILINE))

        # 检查代码块
        has_code_blocks = '```' in text

        # 检查列表
        has_lists = bool(re.search(r'^[-*]\s', text, re.MULTILINE))

        # 缺乏格式元素
        if not has_headers and not has_code_blocks and not has_lists:
            issues += 0.3

        # 混合使用多种格式特征
        format_count = sum([has_headers, has_code_blocks, has_lists])
        if format_count > 1:
            issues -= 0.1  # 格式丰富

        return max(0.0, min(1.0, issues))

    def _detect_generic_format(self, text: str) -> float:
        """检测通用格式一致性"""
        # 简单检测：换行一致性、空白字符使用
        lines = text.split('\n')

        if len(lines) <= 1:
            return 0.0  # 单行文本格式熵低

        # 检查行首空白一致性
        indented = sum(1 for line in lines if line.startswith((' ', '\t')))
        non_empty = sum(1 for line in lines if line.strip())

        if non_empty > 0:
            indent_ratio = indented / non_empty
            # 如果缩进比例不自然
            if 0.1 < indent_ratio < 0.9:
                return 0.3

        return 0.0

    # ==================== 内容检测 ====================

    def detect_content(self, text: str, required_fields: Optional[List[str]] = None) -> float:
        """
        检测内容完整性

        Args:
            text: 输出文本
            required_fields: 必需字段列表

        Returns:
            内容熵值（0-1）
        """
        if not text:
            return 1.0  # 空文本高熵

        entropy = 0.0

        # 1. 长度检查
        if len(text) < 10:
            entropy += 0.3  # 过短

        # 2. 占位符检测
        placeholders = ['xxx', '...', 'TODO', 'TBD', 'XXXX']
        for placeholder in placeholders:
            if placeholder.lower() in text.lower():
                entropy += 0.2
                break

        # 3. 必需字段检查
        if required_fields:
            missing = sum(
                1 for field in required_fields
                if field.lower() not in text.lower()
            )
            if missing > 0:
                entropy += 0.3 * (missing / len(required_fields))

        # 4. 不完整句子检测
        incomplete_count = self._count_incomplete_sentences(text)
        if incomplete_count > 0:
            entropy += 0.2 * min(1.0, incomplete_count / max(1, len(text) // 100))

        return min(1.0, entropy)

    def _count_incomplete_sentences(self, text: str) -> int:
        """统计不完整句子数"""
        sentences = re.split(r'[.!?。！？]', text)
        incomplete = 0

        for sent in sentences:
            sent = sent.strip()
            # 跳过空句子和短句子
            if len(sent) < 3:
                continue
            # 检测不完整标记
            if any(marker in sent for marker in [',，', ':：', ';；']):
                # 以连接词开头的句子可能不完整
                connectors = ['和', '但', '但是', '而且', '或者', '或者']
                if any(sent.startswith(c) for c in connectors):
                    incomplete += 1

        return incomplete

    # ==================== 语义检测 ====================

    def detect_semantic(
        self,
        text: str,
        expected_topic: Optional[str] = None,
        forbidden_words: Optional[List[str]] = None
    ) -> float:
        """
        检测语义一致性

        Args:
            text: 输出文本
            expected_topic: 期望主题关键词
            forbidden_words: 禁止词列表

        Returns:
            语义熵值（0-1）
        """
        if not text:
            return 1.0

        entropy = 0.0

        # 1. 主题偏离检测
        if expected_topic:
            topic_similarity = self._calculate_topic_similarity(text, expected_topic)
            if topic_similarity < 0.3:
                entropy += 0.4  # 主题严重偏离

        # 2. 禁止词检测
        if forbidden_words:
            for word in forbidden_words:
                if word.lower() in text.lower():
                    entropy += 0.5
                    break

        # 3. 自相矛盾检测（简化版）
        contradictions = self._detect_contradictions(text)
        entropy += contradictions * 0.2

        # 4. 重复检测
        repeat_ratio = self._detect_repetition(text)
        if repeat_ratio > 0.3:
            entropy += 0.3

        return min(1.0, entropy)

    def _calculate_topic_similarity(self, text: str, topic: str) -> float:
        """计算主题相似度"""
        text_words = set(text.lower().split())
        topic_words = set(topic.lower().split())

        if not topic_words:
            return 0.5

        intersection = len(text_words & topic_words)
        return intersection / len(topic_words)

    def _detect_contradictions(self, text: str) -> int:
        """检测自相矛盾"""
        contradictions = 0

        # 检测肯定/否定矛盾对
        contradiction_pairs = [
            ('是', '不是'),
            ('有', '没有'),
            ('可以', '不能'),
            ('对', '错'),
            ('是', '非'),
            ('真', '假'),
        ]

        for pos, neg in contradiction_pairs:
            if pos in text and neg in text:
                # 检查它们是否在相近位置（可能在同一句子中）
                sentences = re.split(r'[.!?。！？]', text)
                for sent in sentences:
                    if pos in sent and neg in sent:
                        contradictions += 1
                        break

        return contradictions

    def _detect_repetition(self, text: str) -> float:
        """检测重复度"""
        words = text.split()
        if len(words) < 5:
            return 0.0

        # 简单的n-gram重复检测
        n = 3
        ngrams = []
        for i in range(len(words) - n + 1):
            ngrams.append(' '.join(words[i:i+n]))

        if not ngrams:
            return 0.0

        unique_ngrams = len(set(ngrams))
        return 1.0 - (unique_ngrams / len(ngrams))

    # ==================== 综合检测 ====================

    async def detect(
        self,
        output: str,
        outputs: Optional[List[str]] = None,
        expected_format: Optional[str] = None,
        required_fields: Optional[List[str]] = None,
        expected_topic: Optional[str] = None,
        forbidden_words: Optional[List[str]] = None
    ) -> EntropyResult:
        """
        综合熵检测

        Args:
            output: 当前输出
            outputs: 多次输出列表（用于一致性检测）
            expected_format: 期望格式
            required_fields: 必需字段
            expected_topic: 期望主题
            forbidden_words: 禁止词

        Returns:
            熵检测结果
        """
        result = EntropyResult()

        # 1. 一致性熵
        if outputs and len(outputs) > 1:
            result.consistency_entropy = self.detect_consistency(outputs)
            if result.consistency_entropy > self._thresholds["consistency"]:
                result.issues.append("多次输出不一致")
                result.suggestions.append("考虑增加Prompt约束或使用确定性参数")

        # 2. 格式熵
        result.format_entropy = self.detect_format(output, expected_format)
        if result.format_entropy > self._thresholds["format"]:
            result.warnings.append(f"输出格式不规范（熵: {result.format_entropy:.2f}）")
            result.suggestions.append("检查JSON/标记语法")

        # 3. 内容熵
        result.content_entropy = self.detect_content(output, required_fields)
        if result.content_entropy > self._thresholds["content"]:
            result.issues.append("输出内容不完整")
            result.suggestions.append("补充缺失信息或填充占位符")

        # 4. 语义熵
        result.semantic_entropy = self.detect_semantic(
            output, expected_topic, forbidden_words
        )
        if result.semantic_entropy > self._thresholds["semantic"]:
            result.issues.append("输出语义不一致或包含禁止词")
            result.suggestions.append("重新生成或过滤敏感内容")

        # 5. 计算总体熵
        entropies = [
            result.consistency_entropy,
            result.format_entropy,
            result.content_entropy,
            result.semantic_entropy
        ]

        # 加权平均
        weights = [0.3, 0.2, 0.3, 0.2]  # 一致性、内容更重要
        result.overall_entropy = sum(e * w for e, w in zip(entropies, weights))

        # 6. 确定熵级别
        if result.overall_entropy < 0.2:
            result.entropy_level = EntropyLevel.VERY_LOW
        elif result.overall_entropy < 0.4:
            result.entropy_level = EntropyLevel.LOW
        elif result.overall_entropy < 0.6:
            result.entropy_level = EntropyLevel.MEDIUM
        elif result.overall_entropy < 0.8:
            result.entropy_level = EntropyLevel.HIGH
        else:
            result.entropy_level = EntropyLevel.VERY_HIGH

        # 7. 自适应阈值调整（基于历史）
        self._adjust_thresholds(result)

        # 保存历史
        self._history.append(result)
        if len(self._history) > 100:
            self._history.pop(0)

        return result

    def _adjust_thresholds(self, result: EntropyResult) -> None:
        """基于检测结果自适应调整阈值"""
        if len(self._history) < 10:
            return

        # 如果当前结果正常但检测到高熵，降低阈值使检测更严格
        recent_results = self._history[-10:]
        high_entropy_ratio = sum(
            1 for r in recent_results
            if r.entropy_level in (EntropyLevel.HIGH, EntropyLevel.VERY_HIGH)
        ) / len(recent_results)

        if high_entropy_ratio > 0.5:
            # 检测过于严格了，放宽一点
            for key in self._thresholds:
                self._thresholds[key] = min(1.0, self._thresholds[key] + 0.05)

    # ==================== 统计 ====================

    def get_history_stats(self) -> Dict[str, Any]:
        """获取历史统计"""
        if not self._history:
            return {"count": 0}

        return {
            "count": len(self._history),
            "avg_entropy": statistics.mean(r.overall_entropy for r in self._history),
            "max_entropy": max(r.overall_entropy for r in self._history),
            "high_entropy_ratio": sum(
                1 for r in self._history
                if r.entropy_level in (EntropyLevel.HIGH, EntropyLevel.VERY_HIGH)
            ) / len(self._history),
            "current_thresholds": self.get_thresholds()
        }


# 全局实例
_entropy_detector: Optional[EntropyDetector] = None


def get_entropy_detector() -> EntropyDetector:
    """获取全局熵检测器"""
    global _entropy_detector
    if _entropy_detector is None:
        _entropy_detector = EntropyDetector()
    return _entropy_detector

# -*- coding: utf-8 -*-
"""
置信度校准器 - 置信度校准
==========================
将模型输出的原始置信度校准为更准确的概率估计

原始置信度问题：
1. 模型倾向于过度自信
2. 校准不足（Confidence >> Accuracy）
3. 过度校准（Confidence << Accuracy）

校准方法：
1. Platt Scaling (Sigmoid) - 二分类
2. Temperature Scaling - 多分类/生成
3. Isotonic Regression - 非参数方法
4. Beta Calibration - 概率校准
"""

import math
import statistics
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple
import logging

logger = logging.getLogger(__name__)


class CalibrationMethod(str, Enum):
    """校准方法"""
    NONE = "none"                    # 不校准
    TEMPERATURE = "temperature"      # 温度缩放
    PLATT = "platt"                  # Platt缩放
    ISOTONIC = "isotonic"           # 保序回归
    BETA = "beta"                    # Beta校准
    HISTOGRAM = "histogram"          # 直方图校准
    AUTO = "auto"                    # 自动选择


@dataclass
class CalibrationResult:
    """
    校准结果

    Attributes:
        original_confidence: 原始置信度
        calibrated_confidence: 校准后置信度
        accuracy: 实际准确率
        calibration_error: 校准误差（ECE）
        method: 使用的校准方法
        is_reliable: 是否可靠
        metadata: 附加信息
    """
    original_confidence: float
    calibrated_confidence: float
    accuracy: float = 0.0
    calibration_error: float = 0.0
    method: CalibrationMethod = CalibrationMethod.NONE
    is_reliable: bool = True
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "original_confidence": self.original_confidence,
            "calibrated_confidence": self.calibrated_confidence,
            "accuracy": self.accuracy,
            "calibration_error": self.calibration_error,
            "method": self.method.value,
            "is_reliable": self.is_reliable
        }


@dataclass
class CalibrationMetrics:
    """校准指标"""
    ece: float = 0.0       # Expected Calibration Error
    mce: float = 0.0      # Maximum Calibration Error
    nll: float = 0.0      # Negative Log Likelihood
    brier_score: float = 0.0  # Brier Score
    sample_count: int = 0


class ConfidenceCalibrator:
    """
    置信度校准器

    功能：
    - 多种校准方法
    - 自适应方法选择
    - 在线学习更新
    - 校准质量评估
    """

    def __init__(self, method: CalibrationMethod = CalibrationMethod.TEMPERATURE):
        """
        初始化校准器

        Args:
            method: 默认校准方法
        """
        self._default_method = method

        # 校准参数
        self._temperature: float = 1.0
        self._platt_a: float = 1.0
        self._platt_b: float = 0.0
        self._beta_a: float = 1.0
        self._beta_b: float = 1.0

        # 直方图校准的分箱
        self._histogram_bins: List[Tuple[float, float, int]] = []  # (lower, upper, count)
        self._num_bins: int = 10

        # 训练数据
        self._training_data: List[Tuple[float, bool]] = []  # (confidence, is_correct)
        self._min_samples: int = 20

        # 校准质量指标
        self._metrics = CalibrationMetrics()

        logger.info(f"ConfidenceCalibrator initialized with method: {method}")

    # ==================== 校准方法 ====================

    def calibrate(
        self,
        confidence: float,
        method: Optional[CalibrationMethod] = None
    ) -> CalibrationResult:
        """
        校准置信度

        Args:
            confidence: 原始置信度（0-1）
            method: 校准方法

        Returns:
            校准结果
        """
        method = method or self._default_method

        original = confidence
        calibrated = confidence

        if method == CalibrationMethod.NONE:
            pass

        elif method == CalibrationMethod.TEMPERATURE:
            calibrated = self._apply_temperature(confidence)

        elif method == CalibrationMethod.PLATT:
            calibrated = self._apply_platt(confidence)

        elif method == CalibrationMethod.BETA:
            calibrated = self._apply_beta(confidence)

        elif method == CalibrationMethod.HISTOGRAM:
            calibrated = self._apply_histogram(confidence)

        elif method == CalibrationMethod.AUTO:
            # 基于训练数据自动选择
            method = self._select_best_method()
            calibrated = self.calibrate(confidence, method).calibrated_confidence

        # 确保在[0, 1]范围内
        calibrated = max(0.0, min(1.0, calibrated))

        return CalibrationResult(
            original_confidence=original,
            calibrated_confidence=calibrated,
            method=method,
            calibration_error=abs(original - calibrated)
        )

    def _apply_temperature(self, confidence: float) -> float:
        """温度缩放"""
        # 使用logit空间的缩放
        if confidence <= 0:
            return 0.0
        if confidence >= 1:
            return 1.0

        # logit变换
        logits = math.log(confidence / (1 - confidence))
        # 温度缩放
        scaled_logits = logits / self._temperature
        # sigmoid逆变换
        return 1 / (1 + math.exp(-scaled_logits))

    def _apply_platt(self, confidence: float) -> float:
        """Platt缩放（逻辑回归）"""
        if confidence <= 0:
            return 0.0
        if confidence >= 1:
            return 1.0

        logits = math.log(confidence / (1 - confidence))
        scaled = self._platt_a * logits + self._platt_b
        return 1 / (1 + math.exp(-scaled))

    def _apply_beta(self, confidence: float) -> float:
        """Beta校准"""
        # Beta分布参数化
        import math as m
        if confidence <= 0:
            return 0.0
        if confidence >= 1:
            return 1.0

        # 简化的Beta校准
        adjusted = self._beta_a * confidence + (1 - self._beta_b) * confidence ** 2
        return max(0.0, min(1.0, adjusted))

    def _apply_histogram(self, confidence: float) -> float:
        """直方图校准"""
        if not self._histogram_bins:
            return confidence

        # 找到置信度所在的bin
        for lower, upper, count in self._histogram_bins:
            if lower <= confidence < upper or (confidence == 1.0 and upper == 1.0):
                if count > 0:
                    # 返回该bin的校准值（实际准确率）
                    return (lower + upper) / 2
                break

        return confidence

    # ==================== 训练 ====================

    def fit(
        self,
        confidences: List[float],
        correct: List[bool],
        method: Optional[CalibrationMethod] = None
    ) -> CalibrationMetrics:
        """
        训练校准器

        Args:
            confidences: 置信度列表
            correct: 正确性列表

        Returns:
            校准指标
        """
        if len(confidences) != len(correct):
            raise ValueError("confidences and correct must have same length")

        if len(confidences) < self._min_samples:
            logger.warning(f"Insufficient samples for calibration: {len(confidences)}")
            return self._metrics

        method = method or self._default_method

        # 保存训练数据
        self._training_data = list(zip(confidences, correct))

        if method == CalibrationMethod.TEMPERATURE:
            self._fit_temperature(confidences, correct)
        elif method == CalibrationMethod.PLATT:
            self._fit_platt(confidences, correct)
        elif method == CalibrationMethod.BETA:
            self._fit_beta(confidences, correct)
        elif method == CalibrationMethod.HISTOGRAM:
            self._fit_histogram(confidences, correct)

        # 计算校准指标
        self._calculate_metrics(method)

        logger.info(f"Calibrator trained with {len(confidences)} samples, method={method}")

        return self._metrics

    def _fit_temperature(self, confidences: List[float], correct: List[bool]) -> None:
        """训练温度参数"""
        import math

        # 使用交叉熵损失优化温度
        best_temp = 1.0
        best_loss = float('inf')

        for temp in [0.1, 0.2, 0.3, 0.5, 0.7, 1.0, 1.5, 2.0, 3.0, 5.0]:
            loss = 0.0
            for conf, is_correct in zip(confidences, correct):
                if conf <= 0 or conf >= 1:
                    continue
                # NLL loss
                p = 1 / (1 + math.exp(-math.log(conf / (1 - conf)) / temp))
                p = max(1e-10, min(1 - 1e-10, p))
                if is_correct:
                    loss -= math.log(p)
                else:
                    loss -= math.log(1 - p)

            if loss < best_loss:
                best_loss = loss
                best_temp = temp

        self._temperature = best_temp
        logger.debug(f"Temperature set to {self._temperature}")

    def _fit_platt(self, confidences: List[float], correct: List[bool]) -> None:
        """训练Platt参数"""
        import math

        # 简化的Platt训练
        best_a, best_b = 1.0, 0.0
        best_loss = float('inf')

        for a in [0.5, 0.7, 1.0, 1.5, 2.0]:
            for b in [-1.0, -0.5, 0.0, 0.5, 1.0]:
                loss = 0.0
                for conf, is_correct in zip(confidences, correct):
                    if conf <= 0 or conf >= 1:
                        continue
                    logits = math.log(conf / (1 - conf))
                    scaled = a * logits + b
                    p = 1 / (1 + math.exp(-scaled))
                    p = max(1e-10, min(1 - 1e-10, p))
                    if is_correct:
                        loss -= math.log(p)
                    else:
                        loss -= math.log(1 - p)

                if loss < best_loss:
                    best_loss = loss
                    best_a, best_b = a, b

        self._platt_a = best_a
        self._platt_b = best_b

    def _fit_beta(self, confidences: List[float], correct: List[bool]) -> None:
        """训练Beta参数"""
        # 简化的Beta参数估计
        correct_confs = [c for c, k in zip(confidences, correct) if k]
        wrong_confs = [c for c, k in zip(confidences, correct) if not k]

        if correct_confs:
            avg_correct = statistics.mean(correct_confs)
            self._beta_a = 0.5 + 0.5 * avg_correct

        if wrong_confs:
            avg_wrong = statistics.mean(wrong_confs)
            self._beta_b = 0.5 + 0.5 * (1 - avg_wrong)

    def _fit_histogram(self, confidences: List[float], correct: List[bool]) -> None:
        """训练直方图分箱"""
        bin_edges = [i / self._num_bins for i in range(self._num_bins + 1)]
        bin_edges[-1] = 1.0 + 1e-10  # 确保包含1.0

        self._histogram_bins = []
        counts = [0] * self._num_bins
        correct_counts = [0] * self._num_bins

        for conf, is_correct in zip(confidences, correct):
            # 找到bin索引
            bin_idx = min(int(conf * self._num_bins), self._num_bins - 1)
            counts[bin_idx] += 1
            if is_correct:
                correct_counts[bin_idx] += 1

        for i in range(self._num_bins):
            lower = bin_edges[i]
            upper = bin_edges[i + 1]
            count = counts[i]
            self._histogram_bins.append((lower, upper, count))

    def _select_best_method(self) -> CalibrationMethod:
        """选择最佳校准方法"""
        if len(self._training_data) < self._min_samples:
            return CalibrationMethod.TEMPERATURE

        # 简单启发式选择
        confidences = [c for c, _ in self._training_data]
        variance = statistics.variance(confidences) if len(confidences) > 1 else 0

        if variance < 0.01:
            # 方差小，使用温度缩放
            return CalibrationMethod.TEMPERATURE
        else:
            # 方差大，使用直方图
            return CalibrationMethod.HISTOGRAM

    def _calculate_metrics(self, method: CalibrationMethod) -> None:
        """计算校准指标"""
        if not self._training_data:
            return

        confidences = [c for c, _ in self._training_data]
        correct = [k for _, k in self._training_data]

        # ECE (Expected Calibration Error)
        bin_size = 1.0 / self._num_bins
        ece = 0.0

        for i in range(self._num_bins):
            lower = i * bin_size
            upper = (i + 1) * bin_size

            bin_confs = [c for c in confidences if lower <= c < upper]
            if not bin_confs:
                continue

            bin_acc = sum(1 for c, k in zip(confidences, correct) if lower <= c < upper and k) / len(bin_confs)
            bin_conf = statistics.mean(bin_confs)
            bin_count = len(bin_confs)

            ece += (bin_count / len(confidences)) * abs(bin_acc - bin_conf)

        self._metrics.ece = ece
        self._metrics.sample_count = len(self._training_data)

    # ==================== 在线更新 ====================

    def update(self, confidence: float, is_correct: bool) -> None:
        """
        在线更新校准器

        Args:
            confidence: 置信度
            is_correct: 是否正确
        """
        self._training_data.append((confidence, is_correct))

        # 限制数据量
        if len(self._training_data) > 1000:
            self._training_data = self._training_data[-1000:]

        # 定期重新训练
        if len(self._training_data) % 50 == 0:
            self.fit(
                [c for c, _ in self._training_data],
                [k for _, k in self._training_data]
            )

    # ==================== 批量校准 ====================

    def calibrate_batch(
        self,
        confidences: List[float],
        method: Optional[CalibrationMethod] = None
    ) -> List[CalibrationResult]:
        """
        批量校准

        Args:
            confidences: 置信度列表
            method: 校准方法

        Returns:
            校准结果列表
        """
        return [
            self.calibrate(conf, method)
            for conf in confidences
        ]

    # ==================== 质量评估 ====================

    def evaluate(self) -> CalibrationMetrics:
        """评估校准质量"""
        return self._metrics

    def is_calibrated(self, threshold: float = 0.05) -> bool:
        """判断是否已校准"""
        return self._metrics.ece < threshold

    # ==================== 序列化 ====================

    def get_state(self) -> Dict[str, Any]:
        """获取校准器状态"""
        return {
            "method": self._default_method.value,
            "temperature": self._temperature,
            "platt_a": self._platt_a,
            "platt_b": self._platt_b,
            "beta_a": self._beta_a,
            "beta_b": self._beta_b,
            "histogram_bins": self._histogram_bins,
            "sample_count": len(self._training_data),
            "metrics": {
                "ece": self._metrics.ece,
                "sample_count": self._metrics.sample_count
            }
        }

    def load_state(self, state: Dict[str, Any]) -> None:
        """加载校准器状态"""
        self._temperature = state.get("temperature", 1.0)
        self._platt_a = state.get("platt_a", 1.0)
        self._platt_b = state.get("platt_b", 0.0)
        self._beta_a = state.get("beta_a", 1.0)
        self._beta_b = state.get("beta_b", 1.0)
        self._histogram_bins = state.get("histogram_bins", [])


# 全局实例
_confidence_calibrator: Optional[ConfidenceCalibrator] = None


def get_confidence_calibrator() -> ConfidenceCalibrator:
    """获取全局置信度校准器"""
    global _confidence_calibrator
    if _confidence_calibrator is None:
        _confidence_calibrator = ConfidenceCalibrator()
    return _confidence_calibrator

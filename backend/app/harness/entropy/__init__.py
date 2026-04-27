# -*- coding: utf-8 -*-
"""
Phase 3: 熵管理系统
=========================
检测和控制AI输出的不确定性和混乱度

核心组件：
- EntropyDetector: 输出熵检测
- ConfidenceCalibrator: 置信度校准
- UncertaintyHandler: 不确定性处理
"""

from .entropy_detector import EntropyDetector, EntropyResult, EntropyLevel
from .confidence_calibrator import ConfidenceCalibrator, CalibrationResult, CalibrationMethod
from .uncertainty_handler import UncertaintyHandler, UncertaintyLevel, ResolutionStrategy

__all__ = [
    "EntropyDetector",
    "EntropyResult",
    "EntropyLevel",
    "ConfidenceCalibrator",
    "CalibrationResult",
    "CalibrationMethod",
    "UncertaintyHandler",
    "UncertaintyLevel",
    "ResolutionStrategy",
]

"""
指标收集
"""

from typing import Dict, Any, Optional, List, Callable
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
import threading
import time

from collections import defaultdict


class MetricType(Enum):
    COUNTER = "counter"      # 计数器
    GAUGE = "gauge"          # 瞬时值
    HISTOGRAM = "histogram"  # 直方图
    TIMER = "timer"          # 计时器


@dataclass
class MetricValue:
    """指标值"""
    name: str
    value: float
    timestamp: datetime
    labels: Dict[str, str] = field(default_factory=dict)


class MetricsCollector:
    """
    指标收集器
    
    功能：
    1. 计数器 - 累加计数
    2. 瞬时值 - 当前状态
    3. 直方图 - 分布统计
    4. 计时器 - 耗时统计
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._counters: Dict[str, float] = defaultdict(float)
        self._gauges: Dict[str, float] = {}
        self._histograms: Dict[str, List[float]] = defaultdict(list)
        self._timers: Dict[str, List[float]] = defaultdict(list)
        self._labels: Dict[str, Dict[str, str]] = {}
        self._labels_index: Dict[str, List[str]] = defaultdict(list)

    def increment(
        self,
        name: str,
        value: float = 1.0,
        labels: Optional[Dict[str, str]] = None
    ):
        """递增计数器"""
        with self._lock:
            key = self._make_key(name, labels)
            self._counters[key] += value
            if labels:
                self._labels[key] = labels

    def decrement(
        self,
        name: str,
        value: float = 1.0,
        labels: Optional[Dict[str, str]] = None
    ):
        """递减计数器"""
        with self._lock:
            key = self._make_key(name, labels)
            self._counters[key] -= value

    def set(
        self,
        name: str,
        value: float,
        labels: Optional[Dict[str, str]] = None
    ):
        """设置瞬时值"""
        with self._lock:
            key = self._make_key(name, labels)
            self._gauges[key] = value
            if labels:
                self._labels[key] = labels

    def observe(
        self,
        name: str,
        value: float,
        labels: Optional[Dict[str, str]] = None
    ):
        """记录直方图值"""
        with self._lock:
            key = self._make_key(name, labels)
            self._histograms[key].append(value)
            if labels:
                self._labels[key] = labels

    def start_timer(self, name: str, labels: Optional[Dict[str, str]] = None) -> float:
        """开始计时"""
        start = time.time()
        key = self._make_key(name, labels)
        with self._lock:
            if key not in self._timers:
                self._timers[key] = []
            self._timers[key].append({"start": start, "end": None})
            if labels:
                self._labels[key] = labels
        return start

    def stop_timer(self, name: str, start: float, labels: Optional[Dict[str, str]] = None) -> float:
        """停止计时"""
        elapsed = time.time() - start
        key = self._make_key(name, labels)
        with self._lock:
            if key in self._timers:
                for t in reversed(self._timers[key]):
                    if t["end"] is None:
                        t["end"] = time.time()
                        t["duration"] = elapsed
                        self._histograms[key].append(elapsed)
                        break
        return elapsed

    def get_counter(self, name: str, labels: Optional[Dict[str, str]] = None) -> float:
        """获取计数器值"""
        key = self._make_key(name, labels)
        return self._counters.get(key, 0.0)

    def get_gauge(self, name: str, labels: Optional[Dict[str, str]] = None) -> Optional[float]:
        """获取瞬时值"""
        key = self._make_key(name, labels)
        return self._gauges.get(key)

    def get_histogram_stats(
        self,
        name: str,
        labels: Optional[Dict[str, str]] = None
    ) -> Optional[Dict[str, float]]:
        """获取直方图统计"""
        key = self._make_key(name, labels)
        values = self._histograms.get(key, [])
        
        if not values:
            return None
        
        sorted_values = sorted(values)
        n = len(sorted_values)
        
        return {
            "count": n,
            "sum": sum(values),
            "min": min(values),
            "max": max(values),
            "mean": sum(values) / n,
            "p50": sorted_values[n // 2],
            "p90": sorted_values[int(n * 0.9)],
            "p95": sorted_values[int(n * 0.95)],
            "p99": sorted_values[int(n * 0.99)] if n >= 100 else sorted_values[-1]
        }

    def get_all_metrics(self) -> Dict[str, Any]:
        """获取所有指标"""
        with self._lock:
            return {
                "counters": dict(self._counters),
                "gauges": dict(self._gauges),
                "histogram_stats": {
                    k: self.get_histogram_stats(k) for k in self._histograms.keys()
                }
            }

    def reset(self):
        """重置所有指标"""
        with self._lock:
            self._counters.clear()
            self._gauges.clear()
            self._histograms.clear()
            self._timers.clear()
            self._labels.clear()

    def _make_key(self, name: str, labels: Optional[Dict[str, str]]) -> str:
        if not labels:
            return name
        label_str = ",".join(f"{k}={v}" for k, v in sorted(labels.items()))
        return f"{name}{{{label_str}}}"

    def export_prometheus(self) -> str:
        """导出 Prometheus 格式"""
        lines = []
        
        with self._lock:
            # Counters
            for key, value in self._counters.items():
                lines.append(f"{key} {value}")
            
            # Gauges
            for key, value in self._gauges.items():
                lines.append(f"{key} {value}")
            
            # Histograms
            for key, values in self._histograms.items():
                if values:
                    stats = self.get_histogram_stats(key)
                    if stats:
                        for stat_name, stat_value in stats.items():
                            if stat_name != "count":
                                lines.append(f"{key}_{stat_name} {stat_value}")
        
        return "\n".join(lines)


_metrics_collector: Optional[MetricsCollector] = None


def get_metrics_collector() -> MetricsCollector:
    global _metrics_collector
    if _metrics_collector is None:
        _metrics_collector = MetricsCollector()
    return _metrics_collector

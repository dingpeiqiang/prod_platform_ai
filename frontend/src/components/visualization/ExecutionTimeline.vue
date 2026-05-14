<template>
  <div class="timeline-container">
    <div class="timeline-header">
      <h3>执行时间线</h3>
      <div class="legend">
        <span class="legend-item">
          <span class="dot ok"></span> 成功
        </span>
        <span class="legend-item">
          <span class="dot error"></span> 错误
        </span>
        <span class="legend-item">
          <span class="dot timeout"></span> 超时
        </span>
      </div>
    </div>
    
    <div class="timeline">
      <div class="timeline-line"></div>
      
      <div
        v-for="(span, index) in spans"
        :key="span.span_id"
        class="timeline-item"
        :class="span.status"
        :style="{ left: getPosition(span) + '%' }"
        @click="selectSpan(span)"
      >
        <div class="timeline-dot"></div>
        <div class="timeline-content">
          <div class="timeline-title">{{ span.name }}</div>
          <div class="timeline-meta">
            {{ formatDuration(span.duration_ms) }}
          </div>
        </div>
      </div>
    </div>
    
    <div class="timeline-axis">
      <span class="axis-label">0ms</span>
      <span class="axis-label" style="left: 25%">25%</span>
      <span class="axis-label" style="left: 50%">50%</span>
      <span class="axis-label" style="left: 75%">75%</span>
      <span class="axis-label" style="left: 100%">{{ formatDuration(totalDuration) }}</span>
    </div>
    
    <div v-if="selectedSpan" class="span-detail">
      <div class="detail-header">
        <h4>{{ selectedSpan.name }}</h4>
        <button class="close-btn" @click="selectedSpan = null">×</button>
      </div>
      <div class="detail-grid">
        <div class="detail-item">
          <span class="label">Span ID</span>
          <span class="value">{{ selectedSpan.span_id }}</span>
        </div>
        <div class="detail-item">
          <span class="label">状态</span>
          <span class="value" :class="selectedSpan.status">{{ getStatusText(selectedSpan.status) }}</span>
        </div>
        <div class="detail-item">
          <span class="label">耗时</span>
          <span class="value">{{ formatDuration(selectedSpan.duration_ms) }}</span>
        </div>
        <div class="detail-item">
          <span class="label">开始时间</span>
          <span class="value">{{ formatTime(selectedSpan.start_time) }}</span>
        </div>
        <div class="detail-item">
          <span class="label">结束时间</span>
          <span class="value">{{ formatTime(selectedSpan.end_time) }}</span>
        </div>
        <div class="detail-item">
          <span class="label">组件</span>
          <span class="value">{{ selectedSpan.component }}</span>
        </div>
      </div>
      <div v-if="selectedSpan.logs && selectedSpan.logs.length" class="logs-section">
        <h5>日志</h5>
        <div class="logs-list">
          <div v-for="(log, idx) in selectedSpan.logs" :key="idx" class="log-item">
            <span class="log-time">{{ log.timestamp }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  spans: {
    type: Array,
    default: () => []
  },
  totalDuration: {
    type: Number,
    default: 0
  }
});

const selectedSpan = ref(null);

const sortedSpans = computed(() => {
  return [...props.spans].sort((a, b) => {
    const aStart = new Date(a.start_time).getTime();
    const bStart = new Date(b.start_time).getTime();
    return aStart - bStart;
  });
});

function getPosition(span) {
  if (!props.totalDuration) return 0;
  const start = new Date(span.start_time).getTime();
  const firstStart = sortedSpans.value.length ? new Date(sortedSpans.value[0].start_time).getTime() : start;
  const position = ((start - firstStart) / props.totalDuration) * 100;
  return Math.max(0, Math.min(100, position));
}

function formatDuration(ms) {
  if (!ms || ms < 0) return '0ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}m`;
}

function formatTime(timeStr) {
  if (!timeStr) return '-';
  const date = new Date(timeStr);
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }) + '.' + date.getMilliseconds().toString().padStart(3, '0');
}

function getStatusText(status) {
  const texts = {
    ok: '成功',
    error: '错误',
    timeout: '超时'
  };
  return texts[status] || status;
}

function selectSpan(span) {
  selectedSpan.value = span;
}
</script>

<style scoped>
.timeline-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.timeline-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6b7280;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.ok {
  background: #22c55e;
}

.dot.error {
  background: #ef4444;
}

.dot.timeout {
  background: #eab308;
}

.timeline {
  position: relative;
  height: 80px;
  margin-bottom: 16px;
}

.timeline-line {
  position: absolute;
  top: 40px;
  left: 0;
  right: 0;
  height: 2px;
  background: #e5e7eb;
}

.timeline-item {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  cursor: pointer;
  transition: all 0.2s ease;
}

.timeline-item:hover {
  transform: translateX(-50%) scale(1.05);
}

.timeline-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid #fff;
  margin: 0 auto 8px;
}

.timeline-item.ok .timeline-dot {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
}

.timeline-item.error .timeline-dot {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.2);
}

.timeline-item.timeout .timeline-dot {
  background: #eab308;
  box-shadow: 0 0 0 3px rgba(234, 179, 8, 0.2);
}

.timeline-content {
  text-align: center;
  white-space: nowrap;
}

.timeline-title {
  font-size: 11px;
  font-weight: 500;
  color: #374151;
}

.timeline-meta {
  font-size: 10px;
  color: #9ca3af;
}

.timeline-axis {
  position: relative;
  height: 20px;
}

.axis-label {
  position: absolute;
  font-size: 10px;
  color: #9ca3af;
  transform: translateX(-50%);
}

.span-detail {
  margin-top: 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #e5e7eb;
  border-radius: 50%;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: #d1d5db;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item .label {
  font-size: 11px;
  color: #6b7280;
}

.detail-item .value {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.detail-item .value.error {
  color: #ef4444;
}

.detail-item .value.timeout {
  color: #eab308;
}

.logs-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.logs-section h5 {
  margin: 0 0 12px 0;
  font-size: 12px;
  font-weight: 600;
}

.logs-list {
  max-height: 120px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  gap: 12px;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  margin-bottom: 4px;
}

.log-time {
  font-size: 11px;
  color: #9ca3af;
  font-family: monospace;
}

.log-message {
  font-size: 12px;
  color: #374151;
}
</style>

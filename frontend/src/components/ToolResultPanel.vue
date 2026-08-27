<template>
  <div class="tool-result-panel">
    <div class="panel-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
        <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
        <line x1="6" y1="6" x2="6.01" y2="6"/>
        <line x1="6" y1="18" x2="6.01" y2="18"/>
      </svg>
      <span class="panel-title">工具执行结果</span>
      <span class="panel-count">{{ results.length }} 个工具</span>
    </div>

    <div class="tool-cards">
      <div
        v-for="(tool, idx) in results"
        :key="tool.name || idx"
        class="tool-card"
        :class="[`status-${tool.status || 'done'}`, { expanded: expandedTools[idx] }]"
      >
        <!-- 卡片头部：工具名 + 状态 + 耗时 -->
        <div class="tool-card-header" @click="toggleExpand(idx)">
          <div class="tool-status-icon">
            <svg v-if="tool.status === 'running'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin-icon">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            <svg v-else-if="tool.status === 'error'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>

          <span class="tool-name">{{ tool.displayName || tool.name }}</span>

          <span class="tool-elapsed" v-if="tool.elapsed != null">
            {{ formatElapsed(tool.elapsed) }}
          </span>

          <span class="tool-status-badge" :class="tool.status || 'done'">
            {{ statusLabel(tool) }}
          </span>

          <svg
            class="expand-arrow"
            :class="{ expanded: expandedTools[idx] }"
            width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          >
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </div>

        <!-- 展开详情 -->
        <div v-show="expandedTools[idx]" class="tool-card-body">
          <!-- 输入参数 -->
          <div v-if="tool.params" class="tool-section">
            <span class="section-label">查询参数</span>
            <div class="param-list">
              <div v-for="(val, key) in tool.params" :key="key" class="param-item">
                <span class="param-key">{{ paramLabel(key) }}</span>
                <span class="param-value">{{ formatParam(val) }}</span>
              </div>
            </div>
          </div>

          <!-- 输出摘要 -->
          <div v-if="tool.result" class="tool-section">
            <span class="section-label">执行结果</span>
            <div class="result-summary">{{ formatResult(tool.result) }}</div>
          </div>

          <!-- 错误信息 -->
          <div v-if="tool.status === 'error' && tool.error" class="tool-section error-section">
            <span class="section-label">错误信息</span>
            <pre class="error-text">{{ tool.error }}</pre>
          </div>

          <!-- 可展开的证据 -->
          <div v-if="tool.evidence" class="tool-section">
            <button
              type="button"
              class="evidence-toggle"
              @click.stop="toggleEvidence(idx)"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              {{ showEvidence[idx] ? '收起证据' : '查看证据' }}
              <span class="evidence-count">{{ tool.evidence.length }} 条</span>
            </button>
            <div v-show="showEvidence[idx]" class="evidence-content">
              <pre>{{ formatEvidence(tool.evidence) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  results: { type: Array, default: () => [] }
})

const expandedTools = reactive({})
const showEvidence = reactive({})

const toggleExpand = (idx) => {
  expandedTools[idx] = !expandedTools[idx]
}

const toggleEvidence = (idx) => {
  showEvidence[idx] = !showEvidence[idx]
}

const statusLabel = (tool) => {
  switch (tool.status) {
    case 'running': return '执行中'
    case 'error':   return '失败'
    default:        return '完成'
  }
}

const formatElapsed = (seconds) => {
  if (seconds == null || seconds < 0) return ''
  if (seconds < 0.01) return '<10ms'
  if (seconds < 1) return Math.round(seconds * 1000) + 'ms'
  if (seconds < 60) return seconds.toFixed(1) + 's'
  return Math.floor(seconds / 60) + 'm ' + Math.round(seconds % 60) + 's'
}

const paramLabel = (key) => {
  const map = {
    question: '查询问题',
    offering: '分析对象',
    time: '时间范围',
    rule_set: '规则集',
    dimension: '分析维度',
    metric: '指标',
  }
  return map[key] || key
}

const formatParam = (val) => {
  if (val == null) return '—'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

const formatResult = (result) => {
  if (result == null) return '—'
  if (typeof result === 'string') return result
  if (typeof result === 'number') return String(result)
  if (result.summary) return result.summary
  if (result.message) return result.message
  if (result.count != null) return `共 ${result.count} 条记录`
  return JSON.stringify(result, null, 2)
}

const formatEvidence = (evidence) => {
  if (typeof evidence === 'string') return evidence
  return JSON.stringify(evidence, null, 2)
}
</script>

<style scoped>
.tool-result-panel {
  margin: 12px 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.panel-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.panel-count {
  margin-left: auto;
  color: #94a3b8;
  font-size: 11px;
}

.tool-cards {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.tool-card {
  border-bottom: 1px solid #f1f5f9;
}

.tool-card:last-child {
  border-bottom: none;
}

.tool-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.tool-card-header:hover {
  background: #f8fafc;
}

.tool-status-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-done .tool-status-icon {
  color: #16a34a;
}

.status-running .tool-status-icon {
  color: #2563eb;
}

.status-error .tool-status-icon {
  color: #dc2626;
}

.tool-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  font-family: ui-monospace, 'SF Mono', monospace;
}

.tool-elapsed {
  font-size: 11px;
  color: #94a3b8;
  font-family: ui-monospace, monospace;
}

.tool-status-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 999px;
  line-height: 1.6;
}

.tool-status-badge.done {
  background: #dcfce7;
  color: #15803d;
}

.tool-status-badge.running {
  background: #dbeafe;
  color: #1d4ed8;
}

.tool-status-badge.error {
  background: #fef2f2;
  color: #dc2626;
}

.expand-arrow {
  flex-shrink: 0;
  color: #94a3b8;
  transition: transform 0.2s ease;
}

.expand-arrow.expanded {
  transform: rotate(90deg);
}

.tool-card-body {
  padding: 0 14px 12px;
  border-top: 1px solid #f1f5f9;
  background: #fafafa;
}

.tool-section {
  margin-top: 10px;
}

.section-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.param-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.param-key {
  flex-shrink: 0;
  color: #64748b;
  min-width: 60px;
}

.param-value {
  color: #0f172a;
  word-break: break-word;
}

.result-summary {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.error-section .error-text {
  margin: 0;
  font-size: 12px;
  color: #dc2626;
  background: #fef2f2;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  white-space: pre-wrap;
  font-family: ui-monospace, monospace;
}

.evidence-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.evidence-toggle:hover {
  border-color: #93c5fd;
  color: #3b82f6;
  background: #f0f9ff;
}

.evidence-count {
  font-size: 11px;
  color: #94a3b8;
  margin-left: 2px;
}

.evidence-content {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f1f5f9;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.evidence-content pre {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', 'Fira Code', ui-monospace, monospace;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .spin-icon {
    animation: none;
  }
  .expand-arrow {
    transition: none;
  }
}
</style>
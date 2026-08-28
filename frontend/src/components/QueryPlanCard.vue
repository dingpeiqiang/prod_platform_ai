<template>
  <div v-if="plan" class="query-plan-card">
    <div class="qpc-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <polyline points="12 6 12 12 16 14"/>
      </svg>
      <span class="qpc-title">查询计划</span>
      <span class="qpc-intent">{{ intentLabelComputed }}</span>
    </div>

    <div class="qpc-body">
      <div class="qpc-row">
        <span class="qpc-label">意图</span>
        <span class="qpc-value">{{ intentLabelComputed }}</span>
      </div>

      <div v-if="toolsDisplay.length" class="qpc-row">
        <span class="qpc-label">工具</span>
        <div class="qpc-tools">
          <span v-for="tool in toolsDisplay" :key="tool" class="qpc-tool-tag">{{ toolLabel(tool) }}</span>
        </div>
      </div>

      <div v-if="plan.clarify && plan.clarify.length" class="qpc-row">
        <span class="qpc-label">待补充</span>
        <div class="qpc-tools">
          <span v-for="param in plan.clarify" :key="param" class="qpc-tool-tag clarify">{{ paramLabel(param) }}</span>
        </div>
      </div>

      <div v-if="plan.params && Object.keys(plan.params).length" class="qpc-params">
        <div
          v-for="(val, key) in plan.params"
          :key="key"
          class="qpc-param"
        >
          <span class="qpc-param-key">{{ paramLabel(key) }}</span>
          <span class="qpc-param-value">{{ formatParam(key, val) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { intentLabel, toolLabel, paramLabel, paramValue } from '../utils/businessLabels.js'

const props = defineProps({
  plan: { type: Object, default: null }
})

const intentLabelComputed = computed(() => {
  if (!props.plan) return ''
  return intentLabel(props.plan.intent)
})

const toolsDisplay = computed(() => {
  if (!props.plan || !props.plan.tools) return []
  return props.plan.tools
})

const formatParam = (key, val) => {
  return paramValue(key, val)
}
</script>

<style scoped>
.query-plan-card {
  margin: 8px 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  overflow: hidden;
}

.qpc-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.qpc-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.qpc-intent {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  color: #6d28d9;
  background: #ede9fe;
  padding: 0 8px;
  border-radius: 4px;
  line-height: 1.6;
}

.qpc-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qpc-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
}

.qpc-label {
  color: #64748b;
  min-width: 40px;
  flex-shrink: 0;
}

.qpc-value {
  color: #0f172a;
  font-weight: 500;
  font-family: ui-monospace, monospace;
}

.qpc-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.qpc-tool-tag {
  font-size: 11px;
  font-weight: 600;
  color: #1d4ed8;
  background: #dbeafe;
  padding: 1px 8px;
  border-radius: 4px;
  font-family: ui-monospace, monospace;
}

.qpc-tool-tag.clarify {
  color: #b45309;
  background: #fef3c7;
  font-family: inherit;
}

.qpc-params {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.qpc-param {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.qpc-param-key {
  color: #64748b;
  min-width: 60px;
  flex-shrink: 0;
}

.qpc-param-value {
  color: #334155;
  word-break: break-word;
  font-family: ui-monospace, monospace;
}
</style>
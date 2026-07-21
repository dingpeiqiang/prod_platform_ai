<template>
  <div v-if="visible" class="intent-card product-ops-card">
    <div class="card-header">
      <span class="card-icon">🧭</span>
      <span class="card-title">{{ title }}</span>
      <span class="card-time">{{ formatTime(msg.timestamp) }}</span>
    </div>

    <!-- 市场洞察 -->
    <div v-if="isQuery" class="card-body">
      <div class="field"><span class="field-label">问题</span><span class="field-value">{{ queryQuestion }}</span></div>
      <div class="field"><span class="field-label">结果数</span><span class="field-value">{{ queryCount }}</span></div>
      <div v-if="queryResults.length" class="result-table">
        <div v-for="(row, idx) in queryResults" :key="idx" class="result-row">
          <span class="row-index">{{ idx + 1 }}</span>
          <span class="row-content">{{ summarizeRow(row) }}</span>
        </div>
      </div>
    </div>

    <!-- 风险研判 -->
    <div v-else-if="isPolicy" class="card-body">
      <div class="field"><span class="field-label">策略集</span><span class="field-value">{{ policySetId }}</span></div>
      <div class="field"><span class="field-label">评估模式</span><span class="field-value">{{ expectationType }}</span></div>
      <div class="field"><span class="field-label">结论</span><span class="field-value" :class="verdictClass">{{ verdict }}</span></div>
      <div class="field"><span class="field-label">原因</span><span class="field-value">{{ reason }}</span></div>
      <div v-if="rules.length" class="field"><span class="field-label">命中规则</span><span class="field-value">{{ rules.join(', ') }}</span></div>
    </div>

    <!-- 根因解释 -->
    <div v-else class="card-body">
      <div class="field"><span class="field-label">引用规则</span><span class="field-value">{{ explanationRules.join(', ') }}</span></div>
      <div class="field"><span class="field-label">审计步骤</span><span class="field-value">{{ traceSteps }} 步</span></div>
      <div v-if="traceId" class="field"><span class="field-label">Trace</span><span class="field-value">{{ traceId }}</span></div>
    </div>

    <div class="card-footer">
      <button class="link-btn" @click="handleExport">导出结论</button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  intentType: { type: String, default: '' },
  msg: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['intent-action'])

const normalizedIntent = computed(() => props.intentType || props.msg?.intentType || '')
const visible = computed(() => ['product_ops_query', 'product_ops_policy', 'product_ops_reason'].includes(normalizedIntent.value))
const isQuery = computed(() => normalizedIntent.value === 'product_ops_query')
const isPolicy = computed(() => normalizedIntent.value === 'product_ops_policy')
const title = computed(() => {
  if (isQuery.value) return '市场洞察结果'
  if (isPolicy.value) return '立项研判与风险评估'
  return '异动归因与证据链'
})

const queryQuestion = computed(() => props.msg?.stats?.question || props.msg?.intentData?.question || '')
const queryCount = computed(() => props.msg?.stats?.count ?? props.msg?.intentData?.count ?? 0)
const queryResults = computed(() => props.msg?.intentData?.results || [])
const policySetId = computed(() => props.msg?.stats?.policySetId || props.msg?.intentData?.policySetId || '')
const expectationType = computed(() => props.msg?.stats?.expectationType || props.msg?.intentData?.expectationType || '')
const verdict = computed(() => props.msg?.stats?.verdict || props.msg?.intentData?.verdict || '')
const reason = computed(() => props.msg?.stats?.reason || props.msg?.intentData?.reason || '')
const rules = computed(() => props.msg?.stats?.triggered_rules || props.msg?.intentData?.triggered_rules || [])
const verdictClass = computed(() => verdict.value === 'allow' ? 'ok' : 'warn')
const traceId = computed(() => props.msg?.intentData?.traceId || '')
const explanationRules = computed(() => props.msg?.intentData?.referenced_rules || [])
const traceSteps = computed(() => props.msg?.intentData?.traceSteps ?? 0)

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const summarizeRow = (row) => {
  if (!row) return ''
  const parts = []
  if (row.name) parts.push(row.name)
  if (row.productName) parts.push(row.productName)
  if (row.status) parts.push(row.status)
  return parts.join(' | ') || JSON.stringify(row)
}

const handleExport = () => {
  emit('intent-action', {
    type: normalizedIntent.value,
    action: 'export',
    payload: {
      intentType: normalizedIntent.value,
      intentData: props.msg?.intentData,
      stats: props.msg?.stats,
      streamText: props.msg?.streamText,
    },
  })
}
</script>

<style scoped>
.intent-card { border: 1px solid #e5e7eb; border-radius: 16px; background: #fff; margin-top: 10px; overflow: hidden; }
.card-header { padding: 12px 14px; border-bottom: 1px solid #f1f5f9; display: flex; align-items: center; gap: 8px; background: #f8fafc; }
.card-icon { font-size: 16px; }
.card-title { font-weight: 700; color: #0f172a; flex: 1; }
.card-time { color: #94a3b8; font-size: 12px; }
.card-body { padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; }
.field { display: grid; grid-template-columns: 90px 1fr; gap: 8px; }
.field-label { color: #64748b; }
.field-value { color: #0f172a; }
.field-value.ok { color: #16a34a; font-weight: 700; }
.field-value.warn { color: #ea580c; font-weight: 700; }
.result-table { display: flex; flex-direction: column; gap: 6px; }
.result-row { display: flex; gap: 8px; background: #f8fafc; padding: 8px 10px; border-radius: 10px; }
.row-index { width: 20px; color: #94a3b8; }
.row-content { color: #334155; }
.card-footer { padding: 10px 14px; border-top: 1px solid #f1f5f9; display: flex; justify-content: flex-end; }
.link-btn { background: transparent; border: none; color: #2563eb; cursor: pointer; }
</style>

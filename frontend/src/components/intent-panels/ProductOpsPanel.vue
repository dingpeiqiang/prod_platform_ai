<template>
  <div v-if="visible" class="intent-card product-ops-card">
    <div class="card-header" :class="'header-' + normalizedIntent">
      <span class="card-icon">{{ cardIcon }}</span>
      <span class="card-title">{{ title }}</span>
      <span class="card-badge" :class="'badge-' + normalizedIntent">{{ badgeLabel }}</span>
      <span class="card-time">{{ formatTime(msg.timestamp) }}</span>
    </div>

    <!-- ==================== 市场洞察 ==================== -->
    <div v-if="isQuery" class="card-body">
      <div class="field">
        <span class="field-label">查询问题</span>
        <span class="field-value highlight">{{ queryQuestion }}</span>
      </div>
      <div class="field">
        <span class="field-label">结果数</span>
        <span class="field-value">{{ queryCount }} 条</span>
      </div>
      <div v-if="queryColumns.length" class="field">
        <span class="field-label">关键字段</span>
        <div class="tag-group">
          <span v-for="col in queryColumns" :key="col" class="tag tag-blue">{{ col }}</span>
        </div>
      </div>
      <div v-if="queryResults.length" class="data-table">
        <div class="table-header">
          <span class="th-idx">#</span>
          <span v-for="col in visibleColumns" :key="col" class="th-cell">{{ columnLabel(col) }}</span>
        </div>
        <div v-for="(row, idx) in queryResults.slice(0, 8)" :key="idx" class="table-row">
          <span class="td-idx">{{ idx + 1 }}</span>
          <span v-for="col in visibleColumns" :key="col" class="td-cell">{{ row[col] || '-' }}</span>
        </div>
        <div v-if="queryResults.length > 8" class="table-more">
          其余 {{ queryResults.length - 8 }} 条结果已省略
        </div>
      </div>
      <div v-else class="empty-state">暂无匹配数据</div>
    </div>

    <!-- ==================== 风险研判 ==================== -->
    <div v-else-if="isPolicy" class="card-body">
      <div class="field">
        <span class="field-label">策略集</span>
        <span class="field-value mono">{{ policySetId }}</span>
      </div>
      <div class="field">
        <span class="field-label">评估模式</span>
        <span class="field-value">{{ expectationTypeLabel }}</span>
      </div>
      <div class="verdict-section">
        <span class="verdict-label">结论</span>
        <span class="verdict-badge" :class="'verdict-' + verdict">
          {{ verdictLabel }}
        </span>
      </div>
      <div v-if="reason" class="field">
        <span class="field-label">原因</span>
        <span class="field-value">{{ reason }}</span>
      </div>
      <div v-if="triggeredRules.length" class="field">
        <span class="field-label">命中规则</span>
        <div class="tag-group">
          <span v-for="rule in triggeredRules" :key="rule" class="tag tag-orange">{{ rule }}</span>
        </div>
      </div>
      <div v-if="factsSummary" class="facts-grid">
        <div class="facts-title">评估事实</div>
        <div class="facts-row" v-for="(value, key) in factsSummary" :key="key">
          <span class="facts-key">{{ factLabel(key) }}</span>
          <span class="facts-val">{{ value }}</span>
        </div>
      </div>
    </div>

    <!-- ==================== 异动归因 ==================== -->
    <div v-else class="card-body">
      <div class="field">
        <span class="field-label">分析目标</span>
        <span class="field-value highlight">{{ reasonTarget }}</span>
      </div>
      <div v-if="explanation" class="explanation-block">
        <div class="explanation-title">归因结论</div>
        <div class="explanation-text">{{ explanation }}</div>
      </div>
      <div v-if="explanationRules.length" class="field">
        <span class="field-label">引用规则</span>
        <div class="tag-group">
          <span v-for="rule in explanationRules" :key="rule" class="tag tag-purple">{{ rule }}</span>
        </div>
      </div>
      <div v-if="evidenceCount > 0" class="field">
        <span class="field-label">关联证据</span>
        <span class="field-value">{{ evidenceCount }} 条事实数据</span>
      </div>
      <div v-if="traceId" class="field">
        <span class="field-label">审计追踪</span>
        <span class="field-value mono trace-id">{{ traceId }}</span>
      </div>
    </div>

    <div class="card-footer">
      <button class="action-btn" @click="handleExport">
        <span class="btn-icon">&#8615;</span> 导出结论
      </button>
      <button class="action-btn" @click="handleFollowUp">
        <span class="btn-icon">&#10148;</span> 追问
      </button>
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

const cardIcon = computed(() => {
  if (isQuery.value) return '\u{1F50D}'
  if (isPolicy.value) return '\u{1F6E1}'
  return '\u{1F500}'
})
const title = computed(() => {
  if (isQuery.value) return '市场洞察结果'
  if (isPolicy.value) return '立项研判与风险评估'
  return '异动归因与证据链'
})
const badgeLabel = computed(() => {
  if (isQuery.value) return '查询'
  if (isPolicy.value) return '研判'
  return '归因'
})

const queryQuestion = computed(() => props.msg?.intentData?.question || props.msg?.stats?.question || '')
const queryCount = computed(() => props.msg?.intentData?.count ?? props.msg?.stats?.count ?? 0)
const queryResults = computed(() => props.msg?.intentData?.results || props.msg?.results || [])
const queryColumns = computed(() => props.msg?.intentData?.columns || [])
const visibleColumns = computed(() => queryColumns.value.slice(0, 5))

const policySetId = computed(() => props.msg?.intentData?.policySetId || props.msg?.stats?.policySetId || '')
const expectationType = computed(() => props.msg?.intentData?.expectationType || props.msg?.stats?.expectationType || '')
const expectationTypeLabel = computed(() => {
  const map = { risk_audit: '风险稽核', online_check: '立项校验', candidate_check: '候选评估' }
  return map[expectationType.value] || expectationType.value
})
const verdict = computed(() => props.msg?.intentData?.verdict || props.msg?.stats?.verdict || '')
const verdictLabel = computed(() => {
  const map = { allow: '\u2714 通过', deny: '\u2718 拒绝', review: '\u26A0 待审' }
  return map[verdict.value] || verdict.value
})
const reason = computed(() => props.msg?.intentData?.reason || props.msg?.stats?.reason || '')
const triggeredRules = computed(() => props.msg?.intentData?.triggeredRules || props.msg?.stats?.triggered_rules || [])
const factsSummary = computed(() => props.msg?.intentData?.factsSummary || null)

const reasonTarget = computed(() => props.msg?.intentData?.target || props.msg?.stats?.target || '')
const explanation = computed(() => props.msg?.intentData?.explanation || '')
const explanationRules = computed(() => props.msg?.intentData?.referencedRules || props.msg?.intentData?.referenced_rules || [])
const evidenceCount = computed(() => props.msg?.intentData?.evidenceCount ?? props.msg?.stats?.evidenceCount ?? 0)
const traceId = computed(() => props.msg?.intentData?.traceId || props.msg?.stats?.traceId || '')

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const columnLabel = (col) => {
  const map = {
    name: '名称', productName: '产品名', _bucket: '分类', status: '状态',
    revenueGrowth: '收入增长', newUserMonth: '月新增', isZeroFee: '零资费',
    growth: '增长率', users: '用户数',
  }
  return map[col] || col
}

const factLabel = (key) => {
  const map = {
    productType: '产品类型', targetMarketSize: '目标市场规模',
    isZeroFee: '零资费', onlineMonths: '在售月数',
    newUserMonth: '月新增用户', annualSpend: '年消费', vipLevel: '会员等级',
  }
  return map[key] || key
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

const handleFollowUp = () => {
  const followUpMap = {
    product_ops_query: '帮我进一步分析这些数据的趋势',
    product_ops_policy: '详细解释为什么会被拒绝',
    product_ops_reason: '给我更详细的证据链和时间线',
  }
  emit('intent-action', {
    type: normalizedIntent.value,
    action: 'follow_up',
    payload: { text: followUpMap[normalizedIntent.value] || '请继续分析' },
  })
}
</script>

<style scoped>
.intent-card { border: 1px solid #e2e8f0; border-radius: 14px; background: #fff; margin-top: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.card-header { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; display: flex; align-items: center; gap: 8px; }
.header-product_ops_query { background: linear-gradient(135deg, #eff6ff, #f0f9ff); }
.header-product_ops_policy { background: linear-gradient(135deg, #fefce8, #fff7ed); }
.header-product_ops_reason { background: linear-gradient(135deg, #faf5ff, #fdf2f8); }
.card-icon { font-size: 18px; }
.card-title { font-weight: 700; color: #0f172a; flex: 1; font-size: 14px; }
.card-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
.badge-product_ops_query { background: #dbeafe; color: #1d4ed8; }
.badge-product_ops_policy { background: #fef3c7; color: #b45309; }
.badge-product_ops_reason { background: #f3e8ff; color: #7c3aed; }
.card-time { color: #94a3b8; font-size: 12px; }
.card-body { padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; }
.field { display: grid; grid-template-columns: 85px 1fr; gap: 8px; align-items: start; }
.field-label { color: #64748b; font-size: 13px; white-space: nowrap; padding-top: 1px; }
.field-value { color: #1e293b; font-size: 13px; line-height: 1.5; }
.field-value.highlight { font-weight: 600; color: #0f172a; }
.field-value.mono { font-family: 'SF Mono', 'Consolas', monospace; font-size: 12px; }
.field-value.trace-id { color: #6366f1; }

/* Tags */
.tag-group { display: flex; flex-wrap: wrap; gap: 4px; }
.tag { font-size: 11px; padding: 2px 8px; border-radius: 8px; font-weight: 500; }
.tag-blue { background: #eff6ff; color: #2563eb; }
.tag-orange { background: #fff7ed; color: #ea580c; }
.tag-purple { background: #faf5ff; color: #7c3aed; }

/* Data table */
.data-table { border: 1px solid #f1f5f9; border-radius: 10px; overflow: hidden; }
.table-header { display: grid; gap: 0; background: #f8fafc; border-bottom: 1px solid #e2e8f0; padding: 6px 0; }
.table-row { display: grid; gap: 0; padding: 7px 0; border-bottom: 1px solid #f8fafc; }
.table-row:last-child { border-bottom: none; }
.table-row:hover { background: #f8fafc; }
.th-idx, .td-idx { width: 32px; text-align: center; color: #94a3b8; font-size: 12px; grid-column: span 1; }
.th-cell, .td-cell { font-size: 12px; padding: 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.th-cell { color: #64748b; font-weight: 600; }
.td-cell { color: #334155; }
.table-more { text-align: center; color: #94a3b8; font-size: 12px; padding: 6px; background: #fafbfc; }

/* Verdict */
.verdict-section { display: flex; align-items: center; gap: 10px; padding: 10px 12px; background: #f8fafc; border-radius: 10px; }
.verdict-label { color: #64748b; font-size: 13px; }
.verdict-badge { font-size: 14px; font-weight: 700; padding: 4px 14px; border-radius: 8px; }
.verdict-allow { background: #dcfce7; color: #15803d; }
.verdict-deny { background: #fee2e2; color: #dc2626; }
.verdict-review { background: #fef3c7; color: #d97706; }

/* Facts grid */
.facts-grid { background: #f8fafc; border-radius: 10px; padding: 10px 12px; }
.facts-title { font-size: 12px; color: #64748b; font-weight: 600; margin-bottom: 6px; }
.facts-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 12px; border-bottom: 1px solid #f1f5f9; }
.facts-row:last-child { border-bottom: none; }
.facts-key { color: #64748b; }
.facts-val { color: #1e293b; font-weight: 500; }

/* Explanation block */
.explanation-block { background: #faf5ff; border: 1px solid #f3e8ff; border-radius: 10px; padding: 12px; }
.explanation-title { font-size: 12px; color: #7c3aed; font-weight: 700; margin-bottom: 6px; }
.explanation-text { font-size: 13px; color: #1e293b; line-height: 1.6; }

.empty-state { text-align: center; color: #94a3b8; padding: 20px; font-size: 13px; }

/* Footer */
.card-footer { padding: 10px 16px; border-top: 1px solid #f1f5f9; display: flex; justify-content: flex-end; gap: 8px; }
.action-btn { background: transparent; border: 1px solid #e2e8f0; color: #475569; cursor: pointer; padding: 5px 12px; border-radius: 8px; font-size: 12px; display: flex; align-items: center; gap: 4px; transition: all 0.15s; }
.action-btn:hover { background: #f1f5f9; border-color: #cbd5e1; color: #1e293b; }
.btn-icon { font-size: 13px; }
</style>

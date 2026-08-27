<template>
  <aside class="scene-summary-panel">
    <div class="summary-head">
      <div class="summary-title">
        <span class="head-icon">\u{1F4C8}</span>
        场景汇总
      </div>
      <button type="button" class="collapse-btn" :class="{ collapsed }" @click="collapsed = !collapsed" :title="collapsed ? '展开' : '收起'">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline v-if="collapsed" points="9 6 15 12 9 18"/>
          <polyline v-else points="15 6 9 12 15 18"/>
        </svg>
      </button>
    </div>

    <div v-if="!collapsed" class="summary-body">
      <div class="scene-tag">{{ sceneLabel }}</div>
      <div v-if="sceneCards.length" class="summary-cards">
        <div
          v-for="card in sceneCards"
          :key="card.key"
          class="summary-card"
          :class="toneClass(card.compute(summary))"
        >
          <div class="card-head">
            <span class="card-icon">{{ card.icon }}</span>
            <span class="card-title">{{ card.title }}</span>
          </div>
          <div class="card-value" :class="{ multiline: card.compute(summary).multiline }">{{ card.compute(summary).value }}</div>
          <div v-if="card.compute(summary).sub" class="card-sub">{{ card.compute(summary).sub }}</div>
        </div>
      </div>
      <div v-else class="summary-empty">暂无场景数据，开始对话后将实时汇总关键信息。</div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { sceneSummaries, SCENE_TITLES } from '../config/sceneSummaries.js'

const props = defineProps({
  mode: { type: String, default: 'rd' }, // 'rd' | 'ops'
  /** 会话消息列表（用于按最近意图推导场景） */
  messages: { type: Array, default: () => [] },
  /** useProductConfig() 返回的响应式状态对象 */
  productConfig: { type: Object, default: null },
})

const collapsed = ref(false)

/** 场景 key → 中文标签 */
const sceneLabel = computed(() => SCENE_TITLES[scene.value] || '会话常驻')

/** 最近一条已完结助手消息（含意图/结构数据） */
const lastDone = computed(() => {
  const list = props.messages || []
  for (let i = list.length - 1; i >= 0; i -= 1) {
    const m = list[i]
    if (m?.role === 'assistant' && m?.done) return m
  }
  return null
})

/** 按最近意图推导当前场景 key（RD/Ops 共用） */
const scene = computed(() => {
  const msg = lastDone.value
  if (!msg) return 'session'

  // 优先：RD playbook 场景标记（scene / _scenario）
  const s = msg.scene || msg._scenario
  if (s) {
    const rdMap = {
      'rd.chat': 'rd.chat',
      'chat-generate': 'rd.chat',
      form: 'rd.chat',
      form_update: 'rd.chat',
      'rd.import': 'rd.import',
      'file-parse': 'rd.import',
      'rd.query': 'rd.query',
      query: 'rd.query',
      'rd.compliance': 'rd.compliance',
      compliance: 'rd.compliance',
      'rd.compare': 'rd.compare',
      compare: 'rd.compare',
    }
    if (rdMap[s]) return rdMap[s]
    const opsSceneMap = {
      ops_rules: 'ops.rules',
      rules: 'ops.rules',
      ops_monitor: 'ops.monitor',
      root_cause: 'ops.reason',
      risk_audit: 'ops.risk',
      market_insight: 'ops.query',
      online_check: 'ops.online',
    }
    if (opsSceneMap[s]) return opsSceneMap[s]
  }

  // 其次：SSE intentType
  const intent = msg.intentType || ''
  const opsMap = {
    product_ops_query: 'ops.query',
    product_ops_reason: 'ops.reason',
    product_ops_monitor: 'ops.monitor',
    product_ops_compare: 'rd.compare',
  }
  if (opsMap[intent]) return opsMap[intent]
  if (intent === 'product_ops_policy') {
    const d = msg.intentData || {}
    if (d.expectationType === 'risk_audit' || d.riskAudit || Array.isArray(d.items)) return 'ops.risk'
    return 'ops.online'
  }
  if (intent === 'form' || intent === 'form_update') return 'rd.chat'
  if (intent === 'file-parse') return 'rd.import'

  return 'session'
})

/** 当前场景卡片集 */
const sceneCards = computed(() => {
  const cards = (sceneSummaries[props.mode] || sceneSummaries.rd)[scene.value]
  return cards || sceneSummaries[props.mode].session
})

/** 响应式汇总快照：引用各 ref 的 .value，随状态变化自动更新 */
const summary = computed(() => {
  const pc = props.productConfig || {}
  const products = pc.products?.value || []
  const batchItems = pc.batchItems?.value || []
  const currentProduct = pc.currentProduct?.value || null
  const monitorWorkOrders = pc.monitorWorkOrders?.value || []
  const rootCause = pc.rootCauseResult?.value
  const riskAudit = pc.riskAuditResult?.value
  const monitor = pc.monitorResult?.value
  const rules = pc.opsRulesCatalog?.value
  const compareResult = pc.compareResult?.value

  const msg = lastDone.value
  const data = msg?.intentData || {}

  // 文件引用：跨全部助手消息聚合（会话常驻卡需反映整个会话的引用文档）
  const fileRefs = []
  for (const m of props.messages || []) {
    if (m?.role !== 'assistant') continue
    if (m.fileRef?.fileName && !fileRefs.some((f) => f.fileName === m.fileRef.fileName)) {
      fileRefs.push(m.fileRef)
    }
    for (const a of m.attachments || []) {
      if (a?.type && a.type !== 'image' && !fileRefs.some((f) => f.fileName === a.name)) {
        fileRefs.push({ fileName: a.name })
      }
    }
  }

  // 会话内累计操作（Ops 归因次数等）
  const analysisCount = (props.messages || []).filter(
    (m) => m?.role === 'assistant' && (m.intentType === 'product_ops_reason' || m._scenario === 'root-cause'),
  ).length

  // 批次计数：从 batchItems 推导
  const batchPassed = batchItems.filter((i) => i.compliancePass || i.status === '通过').length
  const batchPending = batchItems.filter((i) => !i.compliancePass && i.status !== '已备案' && i.status !== '通过').length

  // 查询趋势（Ops 市场洞察 / RD 智查）
  const queryResults = data.results || msg?.queryResults || []
  const trendRows = queryResults
    .map((r) => {
      const g = parseNum(r.growth ?? r.revenueGrowth ?? r.growthRate)
      if (g == null) return null
      return { growth: Math.abs(g) > 1 ? g / 100 : g, zero: r.isZeroFee === true || r.isZeroFee === 'true' || r.isZeroFee === 1 }
    })
    .filter(Boolean)
  const avgGrowth = trendRows.length ? trendRows.reduce((a, b) => a + b.growth, 0) / trendRows.length : 0
  const negativeGrowth = trendRows.filter((r) => r.growth < 0).length
  const zeroFee = trendRows.filter((r) => r.zero).length

  // 风险稽核分布
  const riskItems = riskAudit?.items
    || (Array.isArray(data.items) ? data.items : (data.riskAudit?.items || []))
    || []
  const high = countWhere(riskItems, (i) => (i.riskLevel || '').toUpperCase() === 'HIGH')
  const medium = countWhere(riskItems, (i) => (i.riskLevel || '').toUpperCase() === 'MEDIUM')
  const low = countWhere(riskItems, (i) => (i.riskLevel || '').toUpperCase() === 'LOW')

  // 规则运营
  const rulesVersion = rules?.ruleVersion || rules?.version || rules?.catalogVersion

  return {
    products,
    batchItems,
    currentProduct,
    fileRefs,
    batchCounts: {
      total: batchItems.length || data.total || 0,
      passed: batchPassed,
      pending: batchPending,
      confirmable: batchPassed,
    },
    queryCount: data.count ?? data.total ?? msg?.queryResults?.length ?? 0,
    queryQuestion: data.question || '',
    copiedCount: 0,
    lastCompliance: currentProduct
      ? { pass: !!currentProduct.compliancePass, issues: currentProduct.issues || [] }
      : null,
    compareResult,
    rootCause: rootCause || { paths: data.paths || data.rootCause?.paths || [], referencedRules: data.referencedRules || [] },
    policy: {
      verdict: data.verdict || (rootCause ? '' : ''),
      triggeredRules: data.triggeredRules || data.triggered_rules || [],
    },
    monitor: {
      total: monitor?.total ?? data.alertCount ?? (data.alertItems || []).length ?? 0,
      highPriority: monitor?.highPriorityCount ?? data.highPriorityCount ?? 0,
    },
    monitorWorkOrders,
    risk: {
      high: riskAudit?.highCount ?? high,
      medium: riskAudit?.mediumCount ?? medium,
      low: low,
      scanned: riskAudit?.scannedCount ?? riskAudit?.total ?? riskItems.length,
      suggestDelist: riskAudit?.suggestDelistCount ?? riskItems.filter((i) => i.suggestDelist).length,
    },
    rules: {
      version: rulesVersion,
      overrides: rules?.overrides || rules?.thresholdOverrides || [],
    },
    negativeGrowth,
    avgGrowth,
    zeroFee,
    analysisCount,
  }
})

function parseNum(v) {
  if (v == null || v === '') return null
  const n = Number(String(v).replace(/%/g, '').trim())
  return Number.isFinite(n) ? n : null
}

function countWhere(list, pred) {
  return (Array.isArray(list) ? list : []).filter(pred).length
}

function toneClass(res) {
  const tone = res?.tone || 'neutral'
  return `tone-${tone}`
}
</script>

<style scoped>
.scene-summary-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  min-width: 0;
  background: #fbfbfd;
  border-left: 1px solid #e5e7eb;
}
.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #eef0f3;
  flex-shrink: 0;
}
.summary-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: #0f172a;
  font-size: 14px;
}
.head-icon { font-size: 14px; }
.collapse-btn {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
}
.collapse-btn:hover { background: #e2e8f0; color: #475569; }
.collapse-btn.collapsed svg { transform: rotate(0deg); }
.summary-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.scene-tag {
  display: inline-flex;
  align-self: flex-start;
  padding: 4px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
}
.summary-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.summary-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 3px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
}
.summary-card.tone-good { border-left-color: #22c55e; }
.summary-card.tone-warn { border-left-color: #f59e0b; }
.summary-card.tone-bad { border-left-color: #ef4444; }
.card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.card-icon { font-size: 13px; }
.card-title { font-size: 12px; font-weight: 600; color: #475569; }
.card-value { font-size: 16px; font-weight: 700; color: #0f172a; line-height: 1.3; white-space: pre-line; }
.card-value.multiline { font-size: 13px; font-weight: 600; }
.card-sub { margin-top: 4px; font-size: 12px; color: #64748b; line-height: 1.5; word-break: break-all; }
.summary-empty { font-size: 12px; color: #94a3b8; line-height: 1.6; padding: 8px 4px; }
</style>

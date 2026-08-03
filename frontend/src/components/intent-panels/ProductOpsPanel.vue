<template>
  <div v-if="visible" class="intent-card product-ops-card" :class="{ 'is-compact': compactOnly }">
    <template v-if="!compactOnly">
    <div class="card-header" :class="'header-' + normalizedIntent">
      <span class="card-icon">{{ cardIcon }}</span>
      <span class="card-title">{{ title }}</span>
      <span class="card-badge" :class="'badge-' + normalizedIntent">{{ badgeLabel }}</span>
      <span class="card-time">{{ formatTime(msg.timestamp) }}</span>
    </div>

    <!-- ==================== 市场洞察 ==================== -->
    <div v-if="isQuery" class="card-body">
      <div class="query-meta">
        <span class="meta-chip">{{ queryCount }} 条结果</span>
        <span v-if="queryQuestion" class="meta-question" :title="queryQuestion">{{ queryQuestion }}</span>
      </div>

      <!-- 有增长数据时优先展示趋势条，避免与表格重复刷屏 -->
      <div v-if="trendRows.length" class="trend-section">
        <div class="section-title">增长趋势</div>
        <div class="trend-kpis">
          <div class="kpi">
            <span class="kpi-label">样本</span>
            <span class="kpi-val">{{ trendRows.length }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">平均增长</span>
            <span class="kpi-val" :class="avgGrowth >= 0 ? 'up' : 'down'">{{ formatPct(avgGrowth) }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">负增长</span>
            <span class="kpi-val down">{{ negativeGrowthCount }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">零资费</span>
            <span class="kpi-val">{{ zeroFeeCount }}</span>
          </div>
        </div>
        <div class="trend-bars">
          <div v-for="row in trendRows.slice(0, 8)" :key="row.key" class="trend-row">
            <span class="trend-name" :title="row.name">{{ row.name }}</span>
            <div class="trend-track">
              <div
                class="trend-fill"
                :class="row.growth >= 0 ? 'pos' : 'neg'"
                :style="{ width: barWidth(row.growth) }"
              />
            </div>
            <span class="trend-val" :class="row.growth >= 0 ? 'up' : 'down'">{{ formatPct(row.growth) }}</span>
            <span v-if="row.users != null" class="trend-users">+{{ row.users }}</span>
          </div>
        </div>
      </div>

      <!-- 无趋势时展示精简表；有趋势时默认折叠明细 -->
      <div v-if="queryResults.length && (!trendRows.length || showQueryTable)" class="data-table">
        <div class="table-header" :style="tableGridStyle">
          <span class="th-idx">#</span>
          <span v-for="col in visibleColumns" :key="col" class="th-cell">{{ columnLabel(col) }}</span>
        </div>
        <div v-for="(row, idx) in queryResults.slice(0, 8)" :key="idx" class="table-row" :style="tableGridStyle">
          <span class="td-idx">{{ idx + 1 }}</span>
          <span v-for="col in visibleColumns" :key="col" class="td-cell">{{ formatCell(row[col], col) }}</span>
        </div>
        <div v-if="queryResults.length > 8" class="table-more">
          其余 {{ queryResults.length - 8 }} 条结果已省略
        </div>
      </div>
      <button
        v-if="queryResults.length && trendRows.length"
        type="button"
        class="toggle-btn"
        @click="showQueryTable = !showQueryTable"
      >
        {{ showQueryTable ? '收起明细表' : '展开明细表' }}
      </button>

      <div v-if="queryResults.length" class="graph-section">
        <button type="button" class="toggle-btn" @click="showQueryGraph = !showQueryGraph">
          {{ showQueryGraph ? '收起关系图谱' : '展开关系图谱' }}
        </button>
        <SparqlResultGraph v-if="showQueryGraph" :results="queryResults" :query="queryQuestion" />
      </div>
      <div v-else-if="!queryResults.length" class="empty-state">暂无匹配数据</div>
    </div>

    <!-- ==================== 运营监控 ==================== -->
    <div v-else-if="isMonitor" class="card-body">
      <div class="trend-kpis">
        <div class="kpi">
          <span class="kpi-label">告警</span>
          <span class="kpi-val">{{ monitorAlertCount }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">高优先级</span>
          <span class="kpi-val down">{{ monitorHighCount }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">进行中工单</span>
          <span class="kpi-val">{{ monitorOpenWo }}</span>
        </div>
      </div>
      <div v-if="monitorAlerts.length" class="data-table">
        <div class="table-header monitor-grid">
          <span class="th-idx">#</span>
          <span class="th-cell">商品</span>
          <span class="th-cell">类型</span>
          <span class="th-cell">摘要</span>
        </div>
        <div v-for="(row, idx) in monitorAlerts.slice(0, 6)" :key="row.id || idx" class="table-row monitor-grid">
          <span class="td-idx">{{ idx + 1 }}</span>
          <span class="td-cell">{{ row.offeringName || row.offeringId || '-' }}</span>
          <span class="td-cell">{{ row.tag || row.type || '-' }}</span>
          <span class="td-cell">{{ row.text || '-' }}</span>
        </div>
      </div>
      <div v-else class="empty-state">暂无告警，右侧可查看工单</div>
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
      <div v-if="isRiskAudit && riskAuditItems.length" class="data-table">
        <div class="table-header">
          <span class="th-idx">#</span>
          <span class="th-cell">商品</span>
          <span class="th-cell">风险</span>
          <span class="th-cell">分值</span>
        </div>
        <div v-for="(row, idx) in riskAuditItems.slice(0, 8)" :key="row.offeringId || idx" class="table-row">
          <span class="td-idx">{{ idx + 1 }}</span>
          <span class="td-cell">{{ row.offeringName || row.offeringId || '-' }}</span>
          <span class="td-cell">{{ row.riskLevel || '-' }}</span>
          <span class="td-cell">{{ row.riskScore ?? '-' }}</span>
        </div>
        <div v-if="riskAuditItems.length > 8" class="table-more">
          其余 {{ riskAuditItems.length - 8 }} 条详见右侧清单
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

    <!-- ==================== 假设分析 ==================== -->
    <div v-else-if="isCompare" class="card-body">
      <div class="field">
        <span class="field-label">分析问题</span>
        <span class="field-value highlight">{{ compareQuestion }}</span>
      </div>
      <div v-if="comparePatches.length" class="field">
        <span class="field-label">假设变更</span>
        <div class="compare-patches">
          <div v-for="(patch, idx) in comparePatches" :key="idx" class="patch-item">
            <div class="patch-desc">{{ patch.description }}</div>
            <div v-if="patch.changes" class="patch-changes">
              <span v-for="(val, key) in patch.changes" :key="key" class="tag tag-blue">{{ key }}: {{ val }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-if="compareComparisons.length" class="field">
        <span class="field-label">评估结果</span>
        <div class="compare-results">
          <div v-for="(comp, idx) in compareComparisons" :key="idx" class="compare-item">
            <span class="compare-desc">{{ comp.patch_description }}</span>
            <span class="verdict-badge" :class="'verdict-' + (comp.evaluation?.verdict || 'review')">
              {{ comp.evaluation?.verdict === 'allow' ? '通过' : comp.evaluation?.verdict === 'deny' ? '拒绝' : '待审' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 异动归因（仅正文无完整报告时展示） ==================== -->
    <div v-else-if="isReason" class="card-body">
      <div class="field">
        <span class="field-label">异动现象</span>
        <span class="field-value highlight">{{ reasonTargetDisplay }}</span>
      </div>
      <div v-if="reasonSummary" class="explanation-block">
        <div class="explanation-title">归因结论</div>
        <div class="explanation-text">{{ reasonSummary }}</div>
      </div>
      <div v-if="evidenceRows.length" class="evidence-section">
        <div class="evidence-title">支撑证据</div>
        <ol class="evidence-list">
          <li v-for="(row, idx) in evidenceRows" :key="idx">{{ row }}</li>
        </ol>
      </div>
      <div v-else-if="evidenceCount > 0" class="field">
        <span class="field-label">支撑证据</span>
        <span class="field-value">共 {{ evidenceCount }} 条业务事实</span>
      </div>
      <div v-if="explanationRules.length" class="field">
        <span class="field-label">引用规则</span>
        <div class="tag-group">
          <span v-for="rule in explanationRules" :key="rule" class="tag tag-purple" :title="rule">
            {{ formatRuleLabel(rule) }}
          </span>
        </div>
      </div>
      <div v-if="traceId || sparqlText" class="tech-fold">
        <button type="button" class="tech-toggle" @click="showTech = !showTech">
          {{ showTech ? '收起技术信息' : '展开技术信息' }}
        </button>
        <div v-if="showTech" class="tech-body">
          <div v-if="traceId" class="field">
            <span class="field-label">追踪编号</span>
            <span class="field-value mono trace-id">{{ traceId }}</span>
          </div>
          <div v-if="sparqlText" class="field">
            <span class="field-label">查询语句</span>
            <pre class="sparql-pre">{{ sparqlText }}</pre>
          </div>
        </div>
      </div>
    </div>
    </template>

    <div class="card-footer" :class="{ 'footer-compact': compactOnly }">
      <span v-if="compactOnly" class="compact-label">{{ compactLabel }}</span>
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
import { computed, ref } from 'vue'
import SparqlResultGraph from '../SparqlResultGraph.vue'
import { classCn, formatEvidenceRow, formatRule, formatWeight } from '../../utils/ontologyLabels.js'

const props = defineProps({
  intentType: { type: String, default: '' },
  msg: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['intent-action'])
const showTech = ref(false)
const showQueryTable = ref(false)
const showQueryGraph = ref(false)

const normalizedIntent = computed(() => props.intentType || props.msg?.intentType || '')
const isQuery = computed(() => normalizedIntent.value === 'product_ops_query')
const isPolicy = computed(() => normalizedIntent.value === 'product_ops_policy')
const isCompare = computed(() => normalizedIntent.value === 'product_ops_compare')
const isMonitor = computed(() => normalizedIntent.value === 'product_ops_monitor')
const isReason = computed(() => normalizedIntent.value === 'product_ops_reason')
const isRiskAudit = computed(() => {
  const expectation = props.msg?.intentData?.expectationType || props.msg?.action || ''
  return isPolicy.value && (expectation === 'risk_audit' || Array.isArray(props.msg?.intentData?.items))
})
const riskAuditItems = computed(() => {
  const items = props.msg?.intentData?.items || props.msg?.intentData?.riskAudit?.items
  return Array.isArray(items) ? items : []
})
const chatText = computed(() => String(props.msg?.streamText || props.msg?.content || ''))

/** 正文已覆盖结论时，不再重复刷完整结构化卡片 */
const reportAlreadyInChat = computed(() => {
  const text = chatText.value
  if (isReason.value) {
    return /\*\*异动结论\*\*/.test(text)
      || /\*\*根因路径\*\*/.test(text)
      || /###\s+.+\s*异动根因分析/.test(text)
  }
  if (isRiskAudit.value) {
    return /###\s*全量智能稽核结果/.test(text)
  }
  if (isPolicy.value) {
    return /###\s*立项研判结论/.test(text)
      || (/评估结论[：:]/.test(text) && /策略集[：:]/.test(text))
  }
  return false
})

const hasReasonStructuredResult = computed(() => {
  const data = props.msg?.intentData || {}
  const paths = data.paths || data.rootCause?.paths || data.results || []
  const anomalies = data.anomalies || data.rootCause?.anomalies || []
  return Boolean(
    data.explanation
    || data.message
    || (Array.isArray(paths) && paths.length)
    || (Array.isArray(anomalies) && anomalies.length),
  )
})

/** 结论型意图：正文已有报告时只保留导出/追问条 */
const compactOnly = computed(() => {
  if (isQuery.value || isCompare.value || isMonitor.value) return false
  if (isPolicy.value || isReason.value) return reportAlreadyInChat.value
  return false
})

const visible = computed(() => {
  if (isQuery.value || isCompare.value || isMonitor.value) return true
  if (isPolicy.value) return true
  if (!isReason.value) return false
  // 异动归因：正文有报告 → 紧凑操作条；否则仅在确有结构化结果时展示完整卡
  if (reportAlreadyInChat.value) return true
  return hasReasonStructuredResult.value
})

const compactLabel = computed(() => {
  if (isRiskAudit.value) return '稽核结论已写入上方'
  if (isPolicy.value) return '研判结论已写入上方'
  if (isReason.value) return '归因结论已写入上方'
  return '结论已写入上方'
})

const cardIcon = computed(() => {
  if (isQuery.value) return '\u{1F50D}'
  if (isMonitor.value) return '\u{1F4CA}'
  if (isPolicy.value) return '\u{1F6E1}'
  if (isCompare.value) return '\u{1F504}'
  return '\u{1F500}'
})
const title = computed(() => {
  if (isQuery.value) return '市场洞察结果'
  if (isMonitor.value) return '运营监控摘要'
  if (isRiskAudit.value) return '风险稽核结果'
  if (isPolicy.value) return '立项研判与风险评估'
  if (isCompare.value) return '假设分析与场景模拟'
  return '异动归因与证据链'
})
const badgeLabel = computed(() => {
  if (isQuery.value) return '查询'
  if (isMonitor.value) return '监控'
  if (isRiskAudit.value) return '稽核'
  if (isPolicy.value) return '研判'
  if (isCompare.value) return '假设'
  return '归因'
})

const queryQuestion = computed(() => props.msg?.intentData?.question || props.msg?.stats?.question || '')
const queryCount = computed(() => props.msg?.intentData?.count ?? props.msg?.stats?.count ?? 0)
const queryResults = computed(() => props.msg?.intentData?.results || props.msg?.results || [])
const queryColumns = computed(() => props.msg?.intentData?.columns || [])

const DISPLAY_COL_PRIORITY = ['name', 'status', 'growth', 'users', 'isZeroFee', '_bucket']
const HIDDEN_COLS = new Set([
  'product', 'entity', 'uri', 'productName', 'revenueGrowth', 'newUserMonth', 'category', 'price',
])

const visibleColumns = computed(() => {
  const fromBackend = (queryColumns.value || []).filter(
    (c) => c && (!String(c).startsWith('_') || c === '_bucket'),
  )
  const preferred = DISPLAY_COL_PRIORITY.filter((c) => {
    if (fromBackend.includes(c)) return true
    return queryResults.value.some((row) => row?.[c] != null && String(row[c]).trim() !== '')
  })
  if (preferred.length) return preferred.slice(0, 5)
  const fallback = fromBackend.filter((c) => !HIDDEN_COLS.has(c))
  if (fallback.length) return fallback.slice(0, 5)
  const keys = queryResults.value[0] ? Object.keys(queryResults.value[0]) : []
  return keys.filter((k) => !k.startsWith('_') && !HIDDEN_COLS.has(k)).slice(0, 5)
})
const tableGridStyle = computed(() => ({
  gridTemplateColumns: `40px repeat(${Math.max(visibleColumns.value.length, 1)}, 1fr)`,
}))

function parseNum(val) {
  if (val == null || val === '') return null
  if (typeof val === 'number') return Number.isFinite(val) ? val : null
  const s = String(val).replace(/%/g, '').trim()
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

function pickGrowth(row) {
  const g = parseNum(row.growth ?? row.revenueGrowth ?? row.growthRate)
  if (g == null) return null
  // 本体里增长多为小数（0.05=5%）；若绝对值>1 则视为已是百分数
  return Math.abs(g) > 1 ? g / 100 : g
}

const trendRows = computed(() => {
  const rows = []
  queryResults.value.forEach((row, idx) => {
    const growth = pickGrowth(row)
    if (growth == null) return
    const name = String(row.name || row.productName || row.offeringName || row.product || `商品${idx + 1}`)
    const users = parseNum(row.users ?? row.newUserMonth)
    rows.push({ key: `${name}-${idx}`, name, growth, users })
  })
  return rows.sort((a, b) => a.growth - b.growth)
})
const avgGrowth = computed(() => {
  if (!trendRows.value.length) return 0
  const sum = trendRows.value.reduce((acc, r) => acc + r.growth, 0)
  return sum / trendRows.value.length
})
const negativeGrowthCount = computed(() => trendRows.value.filter((r) => r.growth < 0).length)
const zeroFeeCount = computed(() =>
  queryResults.value.filter((r) => {
    const z = r.isZeroFee
    return z === true || z === 'true' || z === 1 || z === '1' || z === '是'
  }).length,
)
const maxAbsGrowth = computed(() => {
  const vals = trendRows.value.map((r) => Math.abs(r.growth))
  return vals.length ? Math.max(...vals, 0.01) : 0.01
})
function barWidth(growth) {
  const pct = Math.min(100, (Math.abs(growth) / maxAbsGrowth.value) * 100)
  return `${Math.max(6, pct)}%`
}
function formatPct(v) {
  if (v == null || Number.isNaN(v)) return '-'
  return `${(v * 100).toFixed(1)}%`
}
function formatCell(val, col) {
  if (val == null || val === '') return '-'
  if (['growth', 'revenueGrowth', 'growthRate'].includes(col)) {
    const g = pickGrowth({ [col]: val, growth: val })
    return g == null ? String(val) : formatPct(g)
  }
  if (col === 'isZeroFee') {
    const z = val === true || val === 'true' || val === 1 || val === '1' || val === '是'
    return z ? '是' : '否'
  }
  return String(val)
}

const monitorAlerts = computed(() => {
  const items = props.msg?.intentData?.alertItems
    || props.msg?.intentData?.alerts?.items
    || []
  return Array.isArray(items) ? items : []
})
const monitorAlertCount = computed(() =>
  props.msg?.intentData?.alertCount ?? monitorAlerts.value.length,
)
const monitorHighCount = computed(() => props.msg?.intentData?.highPriorityCount ?? 0)
const monitorOpenWo = computed(() => props.msg?.intentData?.openWorkOrderCount ?? 0)

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
const reasonTargetDisplay = computed(() => {
  const t = reasonTarget.value
  if (!t) return ''
  return t.length > 80 ? `${t.slice(0, 80)}…` : t
})
const explanationRules = computed(() => props.msg?.intentData?.referencedRules || props.msg?.intentData?.referenced_rules || [])
const evidenceResults = computed(() => {
  const paths = props.msg?.intentData?.paths || props.msg?.intentData?.rootCause?.paths
  if (Array.isArray(paths) && paths.length) return paths
  return props.msg?.intentData?.results || props.msg?.results || []
})
const evidenceCount = computed(() => {
  const n = props.msg?.intentData?.evidenceCount ?? props.msg?.stats?.evidenceCount
  if (n != null) return n
  return evidenceResults.value.length
})
/** 面板只展示短结论；若后端仍回传整份 Markdown 报告则改从结构化字段拼摘要 */
const reasonSummary = computed(() => {
  const raw = String(props.msg?.intentData?.explanation || '').trim()
  const looksLikeFullReport = /^#{1,6}\s/.test(raw) || /\*\*异动结论\*\*/.test(raw) || /\*\*根因路径\*\*/.test(raw)
  if (raw && !looksLikeFullReport) return raw

  const root = props.msg?.intentData?.rootCause || props.msg?.intentData || {}
  const anomalies = root.anomalies || props.msg?.intentData?.anomalies || []
  const paths = evidenceResults.value
  const anomaly = anomalies[0]
  if (!anomaly && !paths.length) return raw || String(root.message || props.msg?.intentData?.message || '')

  const parts = []
  if (anomaly?.message) {
    const rule = anomaly.ruleId ? `（${formatRule(anomaly.ruleId)}）` : ''
    parts.push(`${anomaly.message}${rule}`)
  }
  if (paths[0]?.name) {
    const w = paths[0].weight != null ? `，权重 ${formatWeight(paths[0].weight)}` : ''
    parts.push(`主因：${paths[0].name}${w}`)
    if (paths.length > 1) parts.push(`另有 ${paths.length - 1} 条次因，见支撑证据`)
  }
  return parts.join('。') + (parts.length ? '。' : '')
})
const evidenceRows = computed(() =>
  evidenceResults.value.slice(0, 8).map((row) => {
    if (row && (row.rootCauseType || row.weight != null || Array.isArray(row.evidence))) {
      const type = classCn(row.rootCauseType) || row.rootCauseType || ''
      const bits = [row.name || '—']
      if (row.rank != null) bits.push(`#${row.rank}`)
      if (type) bits.push(type)
      if (row.weight != null) bits.push(`权重 ${formatWeight(row.weight)}`)
      if (row.ruleId) bits.push(formatRule(row.ruleId))
      const ev = Array.isArray(row.evidence) && row.evidence.length
        ? `；证据：${row.evidence.join('；')}`
        : ''
      return `${bits.join(' · ')}${ev}`
    }
    return formatEvidenceRow(row)
  }),
)
const traceId = computed(() => props.msg?.intentData?.traceId || props.msg?.stats?.traceId || '')
const sparqlText = computed(() => props.msg?.intentData?.sparql || '')
const formatRuleLabel = (id) => formatRule(id)

const compareQuestion = computed(() => props.msg?.intentData?.question || props.msg?.stats?.question || '')
const comparePatches = computed(() => props.msg?.intentData?.patches || [])
const compareComparisons = computed(() => props.msg?.intentData?.comparisons || [])

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const columnLabel = (col) => {
  const map = {
    name: '名称', productName: '产品名', _bucket: '分类', status: '状态',
    revenueGrowth: '收入增长', newUserMonth: '月新增', isZeroFee: '零资费',
    growth: '增长率', users: '月新增',
  }
  return map[col] || col
}

const factLabel = (key) => {
  const map = {
    productType: '产品类型', targetMarketSize: '目标市场规模',
    isZeroFee: '零资费', onlineMonths: '在售月数',
    newUserMonth: '月新增用户', annualSpend: '年消费', vipLevel: '会员等级',
    scannedCount: '扫描数', highCount: '高风险', mediumCount: '中风险',
    suggestDelistCount: '建议下架', ruleVersion: '规则版本',
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
    product_ops_policy: '请详细解释该评估结论的依据与可改进点',
    product_ops_reason: '给我更详细的证据链和时间线',
    product_ops_compare: '如果改变更多条件会怎样',
    product_ops_monitor: '对高优先级告警做智能归因',
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
.intent-card.is-compact { box-shadow: none; }
.card-header { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; display: flex; align-items: center; gap: 8px; }
.header-product_ops_query { background: linear-gradient(135deg, #eff6ff, #f0f9ff); }
.header-product_ops_policy { background: linear-gradient(135deg, #fefce8, #fff7ed); }
.header-product_ops_reason { background: linear-gradient(135deg, #faf5ff, #fdf2f8); }
.header-product_ops_monitor { background: linear-gradient(135deg, #ecfdf5, #f0fdf4); }
.header-product_ops_compare { background: linear-gradient(135deg, #f8fafc, #eff6ff); }
.card-icon { font-size: 18px; }
.card-title { font-weight: 700; color: #0f172a; flex: 1; font-size: 14px; }
.card-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
.badge-product_ops_query { background: #dbeafe; color: #1d4ed8; }
.badge-product_ops_policy { background: #fef3c7; color: #b45309; }
.badge-product_ops_reason { background: #f3e8ff; color: #7c3aed; }
.badge-product_ops_monitor { background: #d1fae5; color: #047857; }
.badge-product_ops_compare { background: #e2e8f0; color: #334155; }
.card-time { color: #94a3b8; font-size: 12px; }
.card-body { padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; }
.query-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.meta-chip {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 2px 10px;
}
.meta-question {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.toggle-btn {
  align-self: flex-start;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  padding: 5px 10px;
  cursor: pointer;
}
.toggle-btn:hover { background: #f1f5f9; color: #0f172a; }
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
.monitor-grid { grid-template-columns: 40px 1.2fr 0.7fr 2fr; }
.th-idx, .td-idx { text-align: center; color: #94a3b8; font-size: 12px; }
.th-cell, .td-cell { font-size: 12px; padding: 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.th-cell { color: #64748b; font-weight: 600; }
.td-cell { color: #334155; }
.table-more { text-align: center; color: #94a3b8; font-size: 12px; padding: 6px; background: #fafbfc; }

/* Trend */
.trend-section { margin-top: 4px; }
.trend-section .section-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}
.trend-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 10px; }
.kpi { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 8px 10px; }
.kpi-label { display: block; font-size: 11px; color: #64748b; margin-bottom: 2px; }
.kpi-val { font-size: 15px; font-weight: 700; color: #0f172a; }
.kpi-val.up { color: #15803d; }
.kpi-val.down { color: #dc2626; }
.trend-bars { display: flex; flex-direction: column; gap: 6px; }
.trend-row { display: grid; grid-template-columns: 96px 1fr 52px 48px; gap: 8px; align-items: center; }
.trend-name { font-size: 12px; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.trend-track { height: 8px; background: #f1f5f9; border-radius: 999px; overflow: hidden; }
.trend-fill { height: 100%; border-radius: 999px; }
.trend-fill.pos { background: linear-gradient(90deg, #86efac, #22c55e); }
.trend-fill.neg { background: linear-gradient(90deg, #fda4af, #ef4444); }
.trend-val { font-size: 12px; font-weight: 600; text-align: right; }
.trend-val.up { color: #15803d; }
.trend-val.down { color: #dc2626; }
.trend-users { font-size: 11px; color: #64748b; text-align: right; }

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
.explanation-text { font-size: 13px; color: #1e293b; line-height: 1.6; white-space: pre-wrap; }

/* Evidence section */
.evidence-section {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
}
.evidence-title {
  font-size: 12px;
  color: #475569;
  font-weight: 700;
  margin-bottom: 6px;
}
.evidence-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.evidence-list li {
  font-size: 12px;
  color: #334155;
  line-height: 1.45;
}

.tech-fold { margin-top: 2px; }
.tech-toggle {
  border: none;
  background: none;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
.tech-toggle:hover { color: #334155; }
.tech-body {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sparql-pre {
  margin: 0;
  font-size: 11px;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', Consolas, monospace;
  max-height: 120px;
  overflow: auto;
}

.empty-state { text-align: center; color: #94a3b8; padding: 20px; font-size: 13px; }

/* Graph section */
.graph-section { margin-top: 4px; display: flex; flex-direction: column; gap: 8px; }
.graph-section .section-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.graph-section .section-title::before {
  content: '';
  width: 3px;
  height: 12px;
  background: linear-gradient(180deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 2px;
}

/* Compare section */
.compare-patches { display: flex; flex-direction: column; gap: 8px; }
.patch-item { background: #f8fafc; border-radius: 8px; padding: 8px 10px; border: 1px solid #e2e8f0; }
.patch-desc { font-size: 12px; color: #475569; margin-bottom: 4px; }
.patch-changes { display: flex; flex-wrap: wrap; gap: 4px; }
.compare-results { display: flex; flex-direction: column; gap: 6px; }
.compare-item { display: flex; align-items: center; gap: 8px; background: #f8fafc; padding: 8px 10px; border-radius: 8px; }
.compare-desc { font-size: 12px; color: #334155; flex: 1; }

/* Footer */
.card-footer { padding: 10px 16px; border-top: 1px solid #f1f5f9; display: flex; justify-content: flex-end; align-items: center; gap: 8px; }
.card-footer.footer-compact {
  border-top: none;
  justify-content: flex-start;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: #f8fafc;
}
.compact-label {
  margin-right: auto;
  font-size: 12px;
  color: #94a3b8;
}
.action-btn { background: transparent; border: 1px solid #e2e8f0; color: #475569; cursor: pointer; padding: 5px 12px; border-radius: 8px; font-size: 12px; display: flex; align-items: center; gap: 4px; transition: all 0.15s; }
.action-btn:hover { background: #f1f5f9; border-color: #cbd5e1; color: #1e293b; }
.btn-icon { font-size: 13px; }
</style>

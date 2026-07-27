/**
 * 运营：风险稽核清单 / 下钻 / 规则配置 / 导出
 */
<template>
  <aside v-if="visible && result" class="ops-panel risk-panel">
    <header class="ops-panel-head">
      <div>
        <h3>智能风险稽核</h3>
        <p>
          规则 {{ result.ruleVersion || 'RiskRules-v1.2' }} · 扫描
          {{ result.scannedCount || result.total }} 条 ·
          {{ formatTime(result.auditedAt) }}
        </p>
      </div>
      <button type="button" class="close-btn" @click="$emit('close')">×</button>
    </header>

    <div class="ops-panel-body">
      <section v-if="scanning" class="scan-box">
        <div class="scan-anim" />
        <p>图谱节点扫描中… 规则批次命中 R-B01 → R-B05</p>
        <div class="scan-rules">
          <span v-for="r in scanRules" :key="r" :class="{ hit: hitRules.includes(r) }">{{ r }}</span>
        </div>
      </section>

      <section class="summary-row">
        <div class="stat high">
          <b>{{ result.highCount || 0 }}</b>
          <span>高风险</span>
        </div>
        <div class="stat med">
          <b>{{ result.mediumCount || 0 }}</b>
          <span>中风险</span>
        </div>
        <div class="stat delist">
          <b>{{ result.suggestDelistCount || 0 }}</b>
          <span>建议下架</span>
        </div>
      </section>

      <section class="toolbar">
        <div class="filters">
          <button
            v-for="f in filters"
            :key="f.key"
            type="button"
            :class="{ active: filter === f.key }"
            @click="setFilter(f.key)"
          >
            {{ f.label }}
          </button>
        </div>
        <div class="toolbar-actions">
          <button type="button" class="re-btn" @click="runBatch">全量稽核</button>
          <button type="button" class="export-btn" @click="exportJson">导出清单</button>
        </div>
      </section>

      <section class="rule-box">
        <div class="rule-row">
          <label>零销在架天数阈值</label>
          <input v-model.number="shelfDays" type="number" min="30" max="365" />
          <button type="button" class="re-btn" @click="applyRule">重新推理</button>
        </div>
        <p class="hint">调整在架天数阈值后重新稽核，可验证规则可配置</p>
      </section>

      <section class="list">
        <article
          v-for="item in filteredItems"
          :key="item.offeringId"
          class="risk-card"
          :class="{ active: activeId === item.offeringId, high: item.riskLevel === 'HIGH' }"
          @click="activeId = item.offeringId"
        >
          <div class="risk-top">
            <strong>{{ item.offeringName }}</strong>
            <span class="level" :class="item.riskLevel">{{ item.riskLevel }}</span>
          </div>
          <p class="sub">
            {{ item.offeringId }} · 分值 {{ item.riskScore }}
            <template v-if="item.urgent"> · 紧急</template>
          </p>
          <p class="features">
            {{ (item.risks || []).map((r) => `${r.ruleId}:${r.feature}`).join('；') }}
          </p>
        </article>
        <p v-if="!filteredItems.length" class="empty">当前筛选无命中项</p>
      </section>

      <section v-if="activeItem" class="detail">
        <h4>证据链 · {{ activeItem.offeringName }}</h4>
        <ul class="kv">
          <li>月费 / 一次性费：{{ activeItem.monthlyFee }} / {{ activeItem.oneTimeFee }}</li>
          <li>在架天数：{{ activeItem.shelfDays }} · 近30日销量：{{ activeItem.salesCnt30d }}</li>
          <li>近周期收入：{{ activeItem.revenue30d }} · 合约：{{ activeItem.hasContract ? '有' : '无' }}</li>
          <li>战略标签：{{ activeItem.strategicTag ? '有' : '无' }}</li>
        </ul>
        <code v-for="(t, i) in activeItem.evidenceTriples || []" :key="i">
          ({{ t.s }})-{{ t.p }}→({{ t.o }})
        </code>
        <div class="action-card">
          <h5>处置建议</h5>
          <p>{{ activeItem.disposition?.defaultAction || (activeItem.actions || []).join('、') }}</p>
          <span v-if="activeItem.disposition?.needConfirm" class="need-confirm">需人工确认</span>
          <div class="hypo-actions">
            <button type="button" class="hypo-btn" :disabled="hypoLoading" @click="runHypothetical('delist')">
              退市影响推演
            </button>
            <button type="button" class="hypo-btn secondary" :disabled="hypoLoading" @click="runHypothetical('price')">
              改价为19元推演
            </button>
          </div>
        </div>
        <div v-if="hypoResult" class="hypo-result">
          <h5>假设推演结果</h5>
          <p class="hypo-summary">{{ hypoResult.summary }}</p>
          <ul v-if="hypoResult.impacts?.length" class="kv">
            <li v-for="(imp, idx) in hypoResult.impacts" :key="idx">
              {{ imp.offeringName }}：营收影响 {{ formatImpact(imp.revenueImpact30d) }}；
              {{ imp.userMigrationHint }}；{{ imp.conclusion }}
            </li>
          </ul>
          <p class="hint">
            推演前后风险项：{{ hypoResult.before?.total || 0 }} → {{ hypoResult.after?.total || 0 }}
            （高风险 {{ hypoResult.before?.highCount || 0 }} → {{ hypoResult.after?.highCount || 0 }}）
          </p>
          <button type="button" class="hypo-btn" @click="emitCreateOrder">生成处置工单</button>
        </div>
        <div v-else class="hypo-actions" style="margin-top: 8px">
          <button type="button" class="hypo-btn secondary" @click="emitCreateOrder">生成处置工单</button>
        </div>
      </section>

      <section v-if="result.coverageCompare && !result.demoMode" class="compare">
        <h4>人工抽检 vs 规则全量</h4>
        <div class="compare-grid">
          <div>
            <b>{{ Math.round((result.coverageCompare.manualSampleRate || 0) * 100) }}%</b>
            <span>人工抽检覆盖</span>
            <small>约命中 {{ result.coverageCompare.manualHitEstimate }} 项</small>
          </div>
          <div>
            <b>100%</b>
            <span>规则全量覆盖</span>
            <small>命中 {{ result.coverageCompare.ruleHitCount }} 项</small>
          </div>
        </div>
      </section>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { evaluateHypothetical, runBatchRiskAudit } from '../services/productOntologyApi.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  result: { type: Object, default: null },
})

const emit = defineEmits(['close', 're-audit', 'export', 'create-work-order'])

const filter = ref('all')
const activeId = ref('')
const shelfDays = ref(180)
const scanning = ref(false)
const hitRules = ref([])
const scanRules = ['R-B01', 'R-B02', 'R-B03', 'R-B04', 'R-B05']
const hypoLoading = ref(false)
const hypoResult = ref(null)
let scanTimer = null

const filters = [
  { key: 'all', label: '全部' },
  { key: 'HIGH', label: '高风险' },
  { key: 'MEDIUM', label: '中风险' },
  { key: 'delist', label: '建议下架' },
]

function preferActive(r, mode = filter.value) {
  const items = r?.items || []
  if (mode === 'delist') {
    return items.find((i) => i.offeringId === 'OF-LOW-019') || items.find((i) => i.suggestDelist)
  }
  if (mode === 'HIGH') {
    return items.find((i) => i.offeringId === 'OF-RISK-001') || items.find((i) => i.riskLevel === 'HIGH')
  }
  return (
    items.find((i) => i.offeringId === 'OF-RISK-001') ||
    items.find((i) => i.riskLevel === 'HIGH') ||
    items[0]
  )
}

function setFilter(key) {
  filter.value = key
  const prefer = preferActive(props.result, key)
  if (prefer) activeId.value = prefer.offeringId
}

function playScanAnim() {
  scanning.value = true
  hitRules.value = []
  let i = 0
  if (scanTimer) clearInterval(scanTimer)
  scanTimer = setInterval(() => {
    if (i < scanRules.length) {
      hitRules.value = [...hitRules.value, scanRules[i]]
      i += 1
    } else {
      clearInterval(scanTimer)
      scanTimer = null
      setTimeout(() => {
        scanning.value = false
      }, 400)
    }
  }, 220)
}

watch(
  () => props.result,
  (r) => {
    if (!r) return
    shelfDays.value = r.riskRules?.zeroSalesShelfDays ?? 180
    hypoResult.value = null
    playScanAnim()
    const prefer = preferActive(r)
    activeId.value = prefer?.offeringId || ''
  },
  { immediate: true },
)

watch(activeId, () => {
  hypoResult.value = null
})

const filteredItems = computed(() => {
  const items = props.result?.items || []
  if (filter.value === 'all') return items
  if (filter.value === 'delist') return items.filter((i) => i.suggestDelist)
  return items.filter((i) => i.riskLevel === filter.value)
})

const activeItem = computed(() =>
  (props.result?.items || []).find((i) => i.offeringId === activeId.value),
)

function formatTime(iso) {
  if (!iso) return '刚刚'
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

function formatImpact(v) {
  const n = Number(v) || 0
  if (n === 0) return '0'
  return n > 0 ? `+${n}` : `${n}`
}

async function runHypothetical(mode) {
  if (!activeItem.value?.offeringId || hypoLoading.value) return
  hypoLoading.value = true
  try {
    const payload =
      mode === 'price'
        ? {
            mode: 'price',
            offeringId: activeItem.value.offeringId,
            changes: { monthlyFee: 19, oneTimeFee: 0 },
          }
        : {
            mode: 'delist',
            offeringId: activeItem.value.offeringId,
            changes: { state: '下架' },
          }
    const resp = await evaluateHypothetical(payload)
    hypoResult.value = resp?.data || resp
  } catch (e) {
    hypoResult.value = { summary: e?.message || '假设推演失败' }
  } finally {
    hypoLoading.value = false
  }
}

function emitCreateOrder() {
  if (!activeItem.value) return
  emit('create-work-order', {
    item: activeItem.value,
    hypo: hypoResult.value,
    mode: hypoResult.value?.mode || 'delist',
  })
}

async function runBatch() {
  try {
    await runBatchRiskAudit('ui')
    emit('re-audit', { zeroSalesShelfDays: Number(shelfDays.value) || 180 })
  } catch (e) {
    console.warn('[OpsRiskAudit] batch audit failed', e)
  }
}

function applyRule() {
  emit('re-audit', { zeroSalesShelfDays: Number(shelfDays.value) || 180 })
}

function exportJson() {
  const payload = {
    ruleVersion: props.result?.ruleVersion,
    auditedAt: props.result?.auditedAt,
    summary: {
      high: props.result?.highCount,
      medium: props.result?.mediumCount,
      suggestDelist: props.result?.suggestDelistCount,
    },
    items: props.result?.items || [],
  }
  emit('export', payload)
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `risk-audit-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.ops-panel {
  width: 380px;
  flex-shrink: 0;
  border-left: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  max-height: 100%;
}
.ops-panel-head {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.ops-panel-head h3 {
  margin: 0;
  font-size: 15px;
}
.ops-panel-head p {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}
.close-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: #94a3b8;
}
.ops-panel-body {
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.scan-box {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  position: relative;
  overflow: hidden;
}
.scan-anim {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.25), transparent);
  animation: scan-move 1.2s linear infinite;
}
@keyframes scan-move {
  from { transform: translateX(-100%); }
  to { transform: translateX(100%); }
}
.scan-box p {
  margin: 0 0 8px;
  font-size: 12px;
  position: relative;
}
.scan-rules {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  position: relative;
}
.scan-rules span {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #1e293b;
  color: #64748b;
}
.scan-rules span.hit {
  background: #065f46;
  color: #a7f3d0;
}
.summary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.stat {
  background: #fff;
  border-radius: 8px;
  padding: 10px;
  text-align: center;
  border: 1px solid #e2e8f0;
}
.stat b {
  display: block;
  font-size: 20px;
}
.stat span {
  font-size: 11px;
  color: #64748b;
}
.stat.high b {
  color: #dc2626;
}
.stat.med b {
  color: #d97706;
}
.stat.delist b {
  color: #7c3aed;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.filters {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.filters button,
.export-btn,
.re-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}
.filters button.active {
  background: #0f766e;
  color: #fff;
  border-color: #0f766e;
}
.rule-box,
.detail,
.compare,
.risk-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
}
.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.rule-row input {
  width: 72px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  padding: 4px 6px;
}
.hint {
  margin: 6px 0 0;
  font-size: 11px;
  color: #94a3b8;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 280px;
  overflow: auto;
}
.risk-card {
  cursor: pointer;
}
.risk-card.active,
.risk-card:hover {
  border-color: #0ea5e9;
}
.risk-card.high {
  border-left: 3px solid #dc2626;
}
.risk-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.level {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.level.HIGH {
  background: #fee2e2;
  color: #b91c1c;
}
.level.MEDIUM {
  background: #ffedd5;
  color: #c2410c;
}
.sub,
.features {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}
.empty {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
.detail h4,
.compare h4 {
  margin: 0 0 8px;
  font-size: 13px;
}
.kv {
  margin: 0 0 8px;
  padding-left: 16px;
  font-size: 12px;
  color: #334155;
}
.detail code {
  display: block;
  font-size: 11px;
  background: #f1f5f9;
  padding: 4px 6px;
  margin: 3px 0;
  border-radius: 4px;
  word-break: break-all;
}
.action-card {
  margin-top: 8px;
  padding: 8px;
  background: #ecfdf5;
  border-radius: 6px;
}
.action-card h5 {
  margin: 0 0 4px;
  font-size: 12px;
}
.action-card p {
  margin: 0;
  font-size: 13px;
}
.need-confirm {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
  color: #b91c1c;
}
.hypo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.hypo-btn {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #fff;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}
.hypo-btn.secondary {
  background: #fff;
  color: #0f766e;
}
.hypo-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.hypo-result {
  margin-top: 10px;
  padding: 8px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
}
.hypo-result h5 {
  margin: 0 0 6px;
  font-size: 12px;
}
.hypo-summary {
  margin: 0 0 6px;
  font-size: 12px;
  color: #0c4a6e;
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.compare-grid div {
  text-align: center;
  padding: 8px;
  background: #f8fafc;
  border-radius: 6px;
}
.compare-grid b {
  display: block;
  font-size: 18px;
  color: #0f766e;
}
.compare-grid span {
  display: block;
  font-size: 12px;
}
.compare-grid small {
  font-size: 11px;
  color: #94a3b8;
}
</style>

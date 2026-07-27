/**
 * 运营：规则目录 / 风险阈值覆盖 / 审计与热重载
 */
<template>
  <aside v-if="visible" class="ops-panel rules-panel">
    <header class="ops-panel-head">
      <div>
        <h3>规则运营</h3>
        <p>
          {{ catalog?.opsRulesVersion || catalog?.version || 'OpsRules' }}
          · 风险 {{ effective.ruleVersion || '—' }}
          <template v-if="overrideCount"> · 覆盖 {{ overrideCount }} 项</template>
        </p>
      </div>
      <button type="button" class="close-btn" @click="$emit('close')">×</button>
    </header>

    <div class="ops-panel-body">
      <section class="tabs">
        <button type="button" :class="{ active: tab === 'thresholds' }" @click="tab = 'thresholds'">风险阈值</button>
        <button type="button" :class="{ active: tab === 'catalog' }" @click="tab = 'catalog'">规则目录</button>
        <button type="button" :class="{ active: tab === 'audit' }" @click="tab = 'audit'">变更审计</button>
      </section>

      <p v-if="loading" class="empty">规则加载中…</p>
      <p v-else-if="error" class="empty err">{{ error }}</p>

      <template v-else>
        <!-- 风险阈值 -->
        <template v-if="tab === 'thresholds'">
          <section class="meta-card">
            <div class="meta-row">
              <span>规则文件</span>
              <code>{{ catalog?.rulesPath || 'ops_rules.json' }}</code>
            </div>
            <div class="meta-row">
              <span>SWRL</span>
              <b>{{ catalog?.swrlEnabled === false ? '关' : '开' }}</b>
            </div>
            <div class="meta-row">
              <span>生效覆盖</span>
              <b>{{ overrideCount ? `${overrideCount} 项` : '无（等同文件默认）' }}</b>
            </div>
          </section>

          <section class="form-grid">
            <label>
              零销在架天数
              <input v-model.number="form.zeroSalesShelfDays" type="number" min="30" max="730" />
              <small>默认 {{ defaults.zeroSalesShelfDays ?? 180 }}</small>
            </label>
            <label>
              零销窗口（天）
              <input v-model.number="form.zeroSalesDaysWindow" type="number" min="7" max="90" />
              <small>默认 {{ defaults.zeroSalesDaysWindow ?? 30 }}</small>
            </label>
            <label>
              高风险复核天数
              <input v-model.number="form.highRiskReviewDays" type="number" min="7" max="180" />
              <small>默认 {{ defaults.highRiskReviewDays ?? 30 }}</small>
            </label>
            <label>
              低收百分位
              <input v-model.number="form.lowRevenuePercentile" type="number" min="0.01" max="0.5" step="0.01" />
              <small>默认 {{ defaults.lowRevenuePercentile ?? 0.05 }}</small>
            </label>
            <label class="full">
              规则版本号
              <input v-model="form.ruleVersion" type="text" maxlength="64" />
              <small>默认 {{ defaults.ruleVersion || 'RiskRules-v1.2' }}</small>
            </label>
          </section>

          <section class="actions">
            <button type="button" class="primary-btn" :disabled="saving" @click="saveThresholds">保存覆盖</button>
            <button type="button" class="ghost-btn" :disabled="saving" @click="resetThresholds">重置为默认</button>
            <button type="button" class="ghost-btn" :disabled="saving" @click="reloadFile">热重载文件</button>
          </section>
          <p v-if="toast" class="hint ok">{{ toast }}</p>
          <p class="hint">覆盖仅存进程内存；重启后回落 ops_rules.json。保存后可打开风险稽核验证。</p>
        </template>

        <!-- 规则目录 -->
        <template v-else-if="tab === 'catalog'">
          <section class="engine-box">
            <h4>引擎</h4>
            <div v-for="(eng, key) in engines" :key="key" class="engine-row">
              <strong>{{ key }}</strong>
              <span>{{ eng.impl || eng.note || '' }}</span>
              <em v-if="eng.primary">主</em>
              <em v-else-if="eng.deprecated">弃</em>
            </div>
          </section>

          <section v-for="group in ruleGroups" :key="group.key" class="rule-group">
            <h4>{{ group.title }} <small>{{ group.rules.length }}</small></h4>
            <article v-for="r in group.rules" :key="r.id" class="rule-card" :class="{ off: r.enabled === false }">
              <div class="rule-top">
                <code>{{ r.id }}</code>
                <span>{{ r.name || '—' }}</span>
                <b :class="r.enabled === false ? 'off' : 'on'">{{ r.enabled === false ? '停用' : '启用' }}</b>
              </div>
              <p class="sub">引擎 {{ r.engine || 'java' }}<template v-if="r.proposalAlias"> · {{ r.proposalAlias }}</template></p>
            </article>
          </section>
        </template>

        <!-- 审计 -->
        <template v-else>
          <p v-if="!auditLog.length" class="empty">暂无阈值变更记录</p>
          <article v-for="(row, idx) in auditLog" :key="idx" class="audit-card">
            <div class="audit-top">
              <strong>{{ actionLabel(row.action) }}</strong>
              <span>{{ formatTime(row.at) }}</span>
            </div>
            <pre class="audit-pre">{{ formatDetail(row) }}</pre>
          </article>
        </template>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  getOpsRules,
  updateRiskRules,
  resetRiskRules,
  reloadOpsRules,
} from '../services/productOntologyApi.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  catalog: { type: Object, default: null },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'updated', 'open-risk'])

const tab = ref('thresholds')
const saving = ref(false)
const error = ref('')
const toast = ref('')
const localCatalog = ref(null)

const catalog = computed(() => props.catalog || localCatalog.value || {})
const effective = computed(() => catalog.value.riskEffective || catalog.value.riskRules || {})
const defaults = computed(() => catalog.value.riskDefaults || {})
const overrides = computed(() => catalog.value.riskOverrides || {})
const overrideCount = computed(() => Object.keys(overrides.value || {}).length)
const auditLog = computed(() => catalog.value.riskAuditLog || [])
const engines = computed(() => catalog.value.engines || {})

const form = reactive({
  zeroSalesShelfDays: 180,
  zeroSalesDaysWindow: 30,
  highRiskReviewDays: 30,
  lowRevenuePercentile: 0.05,
  ruleVersion: 'RiskRules-v1.2',
})

function syncForm(src) {
  const e = src || effective.value || {}
  form.zeroSalesShelfDays = Number(e.zeroSalesShelfDays ?? 180)
  form.zeroSalesDaysWindow = Number(e.zeroSalesDaysWindow ?? 30)
  form.highRiskReviewDays = Number(e.highRiskReviewDays ?? 30)
  form.lowRevenuePercentile = Number(e.lowRevenuePercentile ?? 0.05)
  form.ruleVersion = String(e.ruleVersion || 'RiskRules-v1.2')
}

watch(
  () => props.catalog,
  (c) => {
    if (c) syncForm(c.riskEffective || c.riskRules)
  },
  { immediate: true },
)

watch(
  () => props.visible,
  async (v) => {
    if (v && !props.catalog) {
      await refresh()
    }
  },
)

function mapRules(section) {
  const rules = section?.rules || {}
  return Object.entries(rules).map(([id, cfg]) => ({
    id,
    name: cfg?.name,
    enabled: cfg?.enabled,
    engine: cfg?.engine,
    proposalAlias: cfg?.proposalAlias,
  }))
}

const ruleGroups = computed(() => [
  { key: 'risk', title: '风险稽核 R-B*', rules: mapRules(catalog.value.risk) },
  { key: 'rootCause', title: '异动归因 R-A*', rules: mapRules(catalog.value.rootCause) },
  { key: 'config', title: '配置合规 R-C*', rules: mapRules(catalog.value.config) },
  { key: 'batch', title: '批量规则 R-D*', rules: mapRules(catalog.value.batch) },
])

function formatTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

function actionLabel(action) {
  const map = {
    update: '更新阈值',
    reset: '重置默认',
    reload_file: '热重载文件',
  }
  return map[action] || action || '变更'
}

function formatDetail(row) {
  const detail = row?.detail || {}
  const ov = row?.overrides || {}
  return JSON.stringify({ detail, overrides: ov }, null, 2)
}

async function refresh() {
  error.value = ''
  try {
    const resp = await getOpsRules()
    localCatalog.value = resp?.data || resp
    syncForm()
    emit('updated', localCatalog.value)
  } catch (e) {
    error.value = e?.message || '加载规则失败'
  }
}

async function saveThresholds() {
  saving.value = true
  toast.value = ''
  error.value = ''
  try {
    const resp = await updateRiskRules({
      zeroSalesShelfDays: form.zeroSalesShelfDays,
      zeroSalesDaysWindow: form.zeroSalesDaysWindow,
      highRiskReviewDays: form.highRiskReviewDays,
      lowRevenuePercentile: form.lowRevenuePercentile,
      ruleVersion: form.ruleVersion,
    })
    localCatalog.value = resp?.data || resp
    syncForm()
    toast.value = '阈值已覆盖生效'
    emit('updated', localCatalog.value)
  } catch (e) {
    error.value = e?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function resetThresholds() {
  saving.value = true
  toast.value = ''
  error.value = ''
  try {
    const resp = await resetRiskRules()
    localCatalog.value = resp?.data || resp
    syncForm()
    toast.value = '已重置为文件默认'
    emit('updated', localCatalog.value)
  } catch (e) {
    error.value = e?.message || '重置失败'
  } finally {
    saving.value = false
  }
}

async function reloadFile() {
  saving.value = true
  toast.value = ''
  error.value = ''
  try {
    const resp = await reloadOpsRules()
    localCatalog.value = resp?.data || resp
    syncForm()
    toast.value = 'ops_rules.json 已热重载'
    emit('updated', localCatalog.value)
  } catch (e) {
    error.value = e?.message || '热重载失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ops-panel {
  width: 360px;
  max-width: 100%;
  height: 100%;
  border-left: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}
.ops-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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
.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
}
.tabs button {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 12px;
  cursor: pointer;
}
.tabs button.active {
  background: #0f766e;
  color: #fff;
  border-color: #0f766e;
}
.meta-card,
.engine-box,
.rule-group {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
}
.meta-row,
.engine-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px solid #f1f5f9;
}
.meta-row:last-child,
.engine-row:last-child {
  border-bottom: none;
}
.meta-row code,
.engine-row span {
  color: #334155;
  word-break: break-all;
  text-align: right;
}
.engine-row em {
  font-style: normal;
  font-size: 11px;
  color: #0f766e;
  font-weight: 700;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #475569;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px;
}
.form-grid label.full {
  grid-column: 1 / -1;
}
.form-grid input {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 13px;
}
.form-grid small {
  color: #94a3b8;
  font-size: 11px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.primary-btn,
.ghost-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}
.primary-btn {
  background: #0f766e;
  color: #fff;
  border-color: #0f766e;
}
.primary-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.hint {
  margin: 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.45;
}
.hint.ok {
  color: #0f766e;
}
.empty {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 20px 8px;
}
.empty.err {
  color: #dc2626;
}
.rule-group h4,
.engine-box h4 {
  margin: 0 0 8px;
  font-size: 12px;
  color: #475569;
}
.rule-group h4 small {
  color: #94a3b8;
  font-weight: 500;
}
.rule-card,
.audit-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  background: #f8fafc;
}
.rule-card.off {
  opacity: 0.65;
}
.rule-top,
.audit-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.rule-top code {
  background: #ecfdf5;
  color: #0f766e;
  padding: 1px 6px;
  border-radius: 4px;
}
.rule-top b.on {
  margin-left: auto;
  color: #15803d;
  font-size: 11px;
}
.rule-top b.off {
  margin-left: auto;
  color: #b45309;
  font-size: 11px;
}
.rule-card .sub {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}
.audit-top strong {
  flex: 1;
}
.audit-top span {
  color: #94a3b8;
  font-size: 11px;
}
.audit-pre {
  margin: 6px 0 0;
  font-size: 11px;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow: auto;
  background: #fff;
  border-radius: 6px;
  padding: 6px 8px;
  border: 1px dashed #e2e8f0;
}
</style>

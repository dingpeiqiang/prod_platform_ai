/**
 * 运营 MVP-1：根因路径 / 证据链 / 工单草稿面板（业务可读）
 */
<template>
  <aside v-if="visible && result" class="ops-panel root-cause-panel">
    <header class="ops-panel-head">
      <div>
        <h3>根因路径 Top3</h3>
        <p>{{ result.offeringName }} · {{ result.offeringId }}</p>
      </div>
      <button type="button" class="close-btn" @click="$emit('close')">×</button>
    </header>

    <div class="ops-panel-body">
      <section class="anomaly-box">
        <span class="tag">异动确认</span>
        <ul>
          <li v-for="(a, i) in result.anomalies || []" :key="i">
            {{ a.message || a.metricCode }}
            <span class="rule-hint">{{ formatRule(a.ruleId) }}</span>
          </li>
        </ul>
      </section>

      <section class="scope-box">
        <h4>分析维度</h4>
        <p class="hint">按渠道 / 促销 / 竞品 / 行为下钻，定位异动集中的维度</p>
        <div class="scope-chips">
          <span
            v-for="n in scopeNodes"
            :key="n"
            class="chip"
            :class="{ active: activePath?.rootCauseType === n || n === 'Metric' }"
          >
            {{ scopeCn(n) }}
          </span>
        </div>
      </section>

      <section class="path-cards">
        <article
          v-for="p in result.paths || []"
          :key="p.rank"
          class="path-card"
          :class="{ primary: p.isPrimary || p.rank === 1, active: activeRank === p.rank }"
          @click="setActiveRank(p.rank)"
        >
          <div class="path-rank">#{{ p.rank }}</div>
          <div class="path-main">
            <h4>{{ p.name }} <span class="type">{{ typeCn(p.rootCauseType) }}</span></h4>
            <div class="weight-bar">
              <i :style="{ width: `${(p.weight || 0) * 100}%` }" />
            </div>
            <p class="meta">
              影响权重 {{ formatWeight(p.weight) }} · {{ formatRule(p.ruleId) }}
              <template v-if="p.rank === 1"> · 主因</template>
            </p>
            <ul class="evidence">
              <li v-for="(e, ei) in p.evidence || []" :key="ei">{{ e }}</li>
            </ul>
          </div>
        </article>
      </section>

      <section v-if="ontologyChain" class="graph-box">
        <h4>当前路径关系</h4>
        <OntologyChainViz
          :chain="ontologyChain"
          :focus-relation-ids="focusRelationIds"
          hide-status
          compact
        />
      </section>

      <section v-if="activePath" class="drill-box">
        <h4>下钻：{{ activePath.name }}</h4>
        <ol v-if="pathSteps.length" class="path-steps">
          <li v-for="(step, si) in pathSteps" :key="si">{{ step }}</li>
        </ol>
        <div v-if="activePath.drill" class="drill-metrics">
          <div v-if="activePath.drill.orderDelta != null" class="metric">
            <b>{{ Math.round(activePath.drill.orderDelta * 100) }}%</b>
            <span>订购量变化</span>
          </div>
          <div v-if="activePath.drill.contribRatio != null" class="metric">
            <b>{{ Math.round(activePath.drill.contribRatio * 100) }}%</b>
            <span>贡献占比</span>
          </div>
        </div>
        <div v-if="activePath.drill?.trend?.length" class="trend">
          <span
            v-for="(t, ti) in activePath.drill.trend"
            :key="ti"
            class="bar-wrap"
            :title="`${t.label}: ${t.value}`"
          >
            <i class="bar" :style="{ height: `${Math.max(8, t.value * 0.4)}px` }" />
            <em>{{ t.label }}</em>
          </span>
        </div>
      </section>

      <section class="report-box">
        <h4>分析报告</h4>
        <div class="report-md" v-html="reportHtml" />
      </section>

      <section class="wo-box">
        <h4>产品优化工单草稿</h4>
        <p class="wo-title">{{ result.workOrder?.title }}</p>
        <ul>
          <li v-for="(a, i) in result.workOrder?.actions || result.actionList || []" :key="i">{{ a }}</li>
        </ul>
        <button type="button" class="primary-btn" @click="$emit('create-work-order', result.workOrder)">
          一键生成工单草稿
        </button>
      </section>

      <section class="evidence-box">
        <button type="button" class="link-btn" @click="showEvidence = !showEvidence">
          {{ showEvidence ? '收起支撑数据' : '查看支撑数据' }}
        </button>
        <div v-if="showEvidence" class="triples">
          <p class="hint">数据快照 {{ snapshotLabel }}</p>
          <ul class="triple-list">
            <li v-for="(t, ti) in result.evidenceTriples || []" :key="ti">
              {{ formatTriple(t) }}
            </li>
          </ul>
        </div>
      </section>
    </div>
  </aside>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import OntologyChainViz from './OntologyChainViz.vue'
import {
  classCn,
  formatPathStep,
  formatRule,
  formatTriple,
  formatWeight,
} from '../utils/ontologyLabels.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  result: { type: Object, default: null },
  ontologyChain: { type: Object, default: null },
  activeRank: { type: Number, default: null },
})

const emit = defineEmits(['close', 'create-work-order', 'update:activeRank', 'path-select'])

const injectedRank = inject('rootCauseActiveRank', null)
const localRank = ref(1)
const showEvidence = ref(false)

const activeRank = computed({
  get() {
    if (props.activeRank != null) return props.activeRank
    if (injectedRank?.value != null) return injectedRank.value
    return localRank.value
  },
  set(v) {
    localRank.value = v
    if (injectedRank && typeof injectedRank === 'object' && 'value' in injectedRank) {
      injectedRank.value = v
    }
    emit('update:activeRank', v)
    emit('path-select', v)
  },
})

function setActiveRank(rank) {
  activeRank.value = rank
}

watch(
  () => props.result,
  () => {
    setActiveRank(1)
    showEvidence.value = false
  },
)

const activePath = computed(() =>
  (props.result?.paths || []).find((p) => p.rank === activeRank.value),
)

const pathSteps = computed(() =>
  (activePath.value?.path || []).map((step) => formatPathStep(step)).filter(Boolean),
)

const focusRelationIds = computed(() => {
  const paths = props.result?.paths || []
  const idx = paths.findIndex((p) => p.rank === activeRank.value)
  if (idx < 0) return []
  return ['rel-metric', `rel-cause-${idx}`]
})

const scopeNodes = computed(() =>
  props.result?.graphScope?.nodes || ['Metric', 'Channel', 'Promotion', 'Competitor', 'UserBehavior', 'MarketScope'],
)

const snapshotLabel = computed(() => {
  const raw = props.result?.snapshotAt
  if (!raw) return '-'
  try {
    return new Date(raw).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return String(raw)
  }
})

function typeCn(t) {
  return classCn(t) || t || ''
}

function scopeCn(n) {
  return classCn(n) || n || ''
}

const reportHtml = computed(() => {
  const r = props.result
  if (!r) return ''
  const anomaly = r.anomalies?.[0]
  const lines = [
    `<p><strong>异动结论</strong>：${r.offeringName} ${anomaly?.message || '指标异动'}（${formatRule(anomaly?.ruleId || 'R-A01')}）。</p>`,
    '<p><strong>根因排序</strong>：</p><ol>',
    ...(r.paths || []).map(
      (p) =>
        `<li>${p.name}（权重 ${formatWeight(p.weight)}，${formatRule(p.ruleId)}）— ${(p.evidence || []).join('；')}</li>`,
    ),
    '</ol>',
    `<p><strong>策略建议</strong>：${(r.actionList || []).join('；')}</p>`,
    '<p class="note">以上数字均来自业务事实与规则推理，大模型未改写关键指标。</p>',
  ]
  return lines.join('')
})
</script>

<style scoped>
.ops-panel {
  width: 380px;
  flex-shrink: 0;
  border-left: 1px solid var(--border-color, #e2e8f0);
  background: var(--bg-secondary, #f8fafc);
  display: flex;
  flex-direction: column;
  max-height: 100%;
}
.ops-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  background: #fff;
}
.ops-panel-head h3 {
  margin: 0;
  font-size: 15px;
}
.ops-panel-head p {
  margin: 4px 0 0;
  font-size: 12px;
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
  padding: 12px 14px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.anomaly-box,
.path-card,
.drill-box,
.report-box,
.wo-box,
.evidence-box,
.scope-box,
.graph-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
}
.scope-box h4,
.drill-box h4,
.report-box h4,
.wo-box h4,
.graph-box h4 {
  margin: 0 0 6px;
  font-size: 13px;
}
.scope-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.chip {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.chip.active {
  background: #e0f2fe;
  color: #0369a1;
  border-color: #7dd3fc;
}
.tag {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #fef3c7;
  color: #b45309;
  margin-bottom: 6px;
}
.anomaly-box ul,
.evidence ul,
.wo-box ul {
  margin: 0;
  padding-left: 16px;
  font-size: 13px;
  color: #334155;
}
.rule-hint {
  margin-left: 6px;
  font-size: 11px;
  color: #0369a1;
}
.path-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.path-card {
  display: flex;
  gap: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.path-card:hover,
.path-card.active {
  border-color: #0ea5e9;
}
.path-card.primary {
  border-color: #f59e0b;
  background: #fffbeb;
}
.path-rank {
  font-weight: 700;
  color: #0ea5e9;
  min-width: 28px;
}
.path-main h4 {
  margin: 0 0 6px;
  font-size: 13px;
}
.path-main .type {
  font-size: 11px;
  color: #64748b;
  font-weight: 400;
}
.weight-bar {
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}
.weight-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #0ea5e9, #0369a1);
}
.meta {
  margin: 0 0 4px;
  font-size: 11px;
  color: #64748b;
}
.path-steps {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.path-steps li {
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
}
.triple-list {
  margin: 6px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.triple-list li {
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
}
.drill-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 8px;
}
.drill-metrics .metric {
  background: #f8fafc;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
}
.drill-metrics b {
  display: block;
  font-size: 18px;
  color: #0f766e;
}
.drill-metrics span {
  font-size: 11px;
  color: #64748b;
}
.trend {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  height: 56px;
  margin-top: 10px;
  padding: 0 4px;
}
.bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}
.bar {
  display: block;
  width: 100%;
  max-width: 28px;
  background: linear-gradient(180deg, #38bdf8, #0284c7);
  border-radius: 4px 4px 0 0;
}
.bar-wrap em {
  font-style: normal;
  font-size: 10px;
  color: #94a3b8;
}
.report-md {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}
.report-md :deep(.note) {
  font-size: 11px;
  color: #94a3b8;
}
.wo-title {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 6px;
}
.primary-btn {
  margin-top: 8px;
  width: 100%;
  border: none;
  background: #0f766e;
  color: #fff;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.link-btn {
  border: none;
  background: none;
  color: #0369a1;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}
.hint {
  font-size: 11px;
  color: #94a3b8;
  margin: 0;
}
</style>

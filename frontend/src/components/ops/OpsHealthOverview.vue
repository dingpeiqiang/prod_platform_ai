<template>
  <div class="ov-drill-health-card">
    <div class="ov-drill-health-gauge">
      <div class="ov-drill-gauge-ring" :class="drillHealthCls(level)">
        <span class="ov-drill-gauge-num">{{ total }}</span>
        <span class="ov-drill-gauge-unit">分</span>
      </div>
    </div>
    <div class="ov-drill-health-info">
      <div class="ov-drill-health-level" :class="drillHealthCls(level)">{{ level }} · 较上月 {{ drill.healthDelta }}分</div>
      <div class="ov-drill-health-note">{{ drill.healthNote }}</div>
      <div class="ov-drill-health-metrics">
        <div class="ov-kpi ov-kpi-sm">
          <div class="ov-kpi-icon ov-ki-blue"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/></svg></div>
          <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ drill.scale.label }}</div><div class="ov-kpi-value ov-kpi-value-sm">{{ drill.scale.value }}</div></div>
        </div>
        <div class="ov-kpi ov-kpi-sm">
          <div class="ov-kpi-icon ov-ki-green"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg></div>
          <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ drill.added.label }}<span class="ov-kpi-delta" :class="drillDeltaCls(drill.added.delta)">{{ drill.added.delta }}</span></div><div class="ov-kpi-value ov-kpi-value-sm">{{ drill.added.value }}</div></div>
        </div>
        <div class="ov-kpi ov-kpi-sm">
          <div class="ov-kpi-icon ov-ki-amber"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z"/></svg></div>
          <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ drill.income.label }}<span class="ov-kpi-delta" :class="drillDeltaCls(drill.income.delta)">{{ drill.income.delta }}</span></div><div class="ov-kpi-value ov-kpi-value-sm">{{ drill.income.value }}</div></div>
        </div>
      </div>
    </div>
  </div>
  <div class="ov-drill-dims">
    <div v-for="d in dims" :key="d.key" class="ov-drill-dim" :class="'ov-dim-' + d.key">
      <div class="ov-drill-dim-head">
        <span class="ov-drill-dim-name">{{ d.name }}</span>
        <span class="ov-drill-dim-weight">权重 {{ d.weight }} · 满分{{ d.full }}分</span>
        <span class="ov-drill-dim-score">{{ d.score }}<i>分</i></span>
      </div>
      <div class="ov-drill-dim-bar"><div class="ov-drill-dim-fill" :style="{ width: d.pct + '%' }"></div></div>
      <div class="ov-drill-dim-items">
        <div v-for="it in d.items" :key="it.name" class="ov-drill-dim-item">
          <span class="ov-drill-dim-item-name">{{ it.name }}</span>
          <span class="ov-drill-dim-item-value">{{ it.value }}</span>
          <b :class="it.score >= 8 ? 'ov-item-ok' : it.score >= 6 ? 'ov-item-mid' : 'ov-item-warn'">{{ it.score }}</b>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { drillHealthCls, drillDeltaCls } from './opsFormat.js'

defineProps({
  drill: { type: Object, required: true },
  dims: { type: Array, required: true },
  total: { type: Number, required: true },
  level: { type: String, required: true },
})
</script>

<style src="./opsView.css" scoped></style>

<template>
  <div class="ov-panel ov-rev-panel">
    <div class="ov-panel-title-bar">
      <h3 class="ov-title">收入与规模总览</h3>
      <span class="ov-title-note">{{ periodNote }}</span>
    </div>
    <div v-if="loading" class="ov-rev-loading">加载中...</div>
    <div v-else class="ov-rev-row">
      <div v-for="card in cards" :key="card.key" class="ov-rev-card">
        <div class="ov-rev-card-head">
          <div class="ov-rev-icon" :class="card.iconCls">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
          </div>
          <div class="ov-rev-head-title">{{ card.title }}</div>
        </div>
        <div class="ov-rev-body">
          <div class="ov-rev-metric">
            <div class="ov-rev-label">30天收入</div>
            <div class="ov-rev-value-row">
              <span class="ov-rev-now">{{ fmt(card.report.now) }}<span class="ov-rev-unit">元</span></span>
            </div>
            <div class="ov-rev-sub">
              <span class="ov-rev-cum">在架商品 {{ fmt(card.report.offeringCount) }} 个</span>
              <span class="ov-rev-cum">有销量 {{ fmt(card.report.activeCount) }} 个</span>
            </div>
          </div>
          <div class="ov-rev-metric">
            <div class="ov-rev-label">30天销量</div>
            <div class="ov-rev-value-row">
              <span class="ov-rev-now">{{ fmt(card.bill.now) }}<span class="ov-rev-unit">单</span></span>
            </div>
            <div class="ov-rev-sub">
              <span class="ov-rev-cum">异动告警 {{ fmt(card.bill.alertCount) }} 条</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getOpsRevenueOverview } from '../../services/productOntologyApi.js'
import { fmt } from './opsFormat.js'

const loading = ref(true)
const overview = ref(null)
const loadError = ref(false)

const ICONS = ['ov-icon-blue', 'ov-icon-emerald', 'ov-icon-amber']

const periodNote = computed(() => {
  if (overview.value?.generatedAt) {
    return `数据口径：事实图 · 30天滚动 · ${String(overview.value.generatedAt).slice(0, 10)}`
  }
  return '数据口径：事实图 · 30天滚动'
})

const cards = computed(() => {
  if (!overview.value) return []
  const t = overview.value.totals || {}
  const rows = [
    { key: 'revenue', title: '30天收入', report: { now: t.revenue30d || 0, offeringCount: t.offeringCount || 0, activeCount: t.activeOfferingCount || 0 }, bill: { now: overview.value.anomalyAlertCount || 0, alertCount: overview.value.anomalyAlertCount || 0 } },
    { key: 'sales', title: '30天销量', report: { now: t.sales30d || 0, offeringCount: t.offeringCount || 0, activeCount: t.activeOfferingCount || 0 }, bill: { now: overview.value.anomalyAlertCount || 0, alertCount: overview.value.anomalyAlertCount || 0 } },
    { key: 'active', title: '在架商品', report: { now: t.offeringCount || 0, offeringCount: t.offeringCount || 0, activeCount: t.activeOfferingCount || 0 }, bill: { now: t.activeOfferingCount || 0, alertCount: overview.value.anomalyAlertCount || 0 } },
  ]
  return rows.map((r, i) => ({ ...r, iconCls: ICONS[i % ICONS.length] }))
})

onMounted(async () => {
  try {
    const body = await getOpsRevenueOverview()
    if (body && body.success) {
      overview.value = body
    } else {
      loadError.value = true
    }
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style src="./opsView.css" scoped></style>

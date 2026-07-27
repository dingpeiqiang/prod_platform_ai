<template>
  <aside class="compare-panel" v-if="visible">
    <header class="compare-header">
      <h3>多方案对比</h3>
      <button type="button" class="close-btn" @click="$emit('close')">关闭</button>
    </header>
    <p class="compare-summary" v-if="result?.explanation">{{ shortExplain }}</p>
    <div class="compare-table" v-if="rows.length">
      <div
        v-for="(row, idx) in rows"
        :key="idx"
        class="compare-row"
        :class="{ recommended: isRecommended(row) }"
      >
        <div class="row-title">
          <strong>{{ row.label }}</strong>
          <span class="badge" :class="row.compliancePass ? 'ok' : 'bad'">
            {{ row.compliancePass ? '合规通过' : '未通过' }}
          </span>
          <span v-if="isRecommended(row)" class="badge rec">推荐</span>
        </div>
        <div class="row-meta">
          月费 {{ row.monthlyFee }} · 转化率 {{ row.conversionRate }} · 预估年营收
          {{ formatMoney(row.estimatedAnnualRevenue) }}
        </div>
        <ul v-if="row.issues?.length" class="issue-list">
          <li v-for="(iss, i) in row.issues.slice(0, 3)" :key="i">
            {{ iss.ruleId }} {{ iss.message }}
          </li>
        </ul>
      </div>
    </div>
    <p class="trace" v-if="result?.trace_id">trace：{{ result.trace_id }}</p>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  result: { type: Object, default: null },
})

defineEmits(['close'])

const rows = computed(() => props.result?.comparisons || [])
const recommendedLabel = computed(() => props.result?.recommended?.label || '')
const shortExplain = computed(() => {
  const text = String(props.result?.explanation || '')
  const lines = text.split('\n').filter(Boolean)
  return lines.slice(-2).join(' ')
})

function isRecommended(row) {
  return row?.label && row.label === recommendedLabel.value
}

function formatMoney(n) {
  const num = Number(n) || 0
  return num.toLocaleString('zh-CN')
}
</script>

<style scoped>
.compare-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  padding: 16px;
  overflow: auto;
}
.compare-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.compare-header h3 {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
}
.close-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
}
.compare-summary {
  font-size: 13px;
  color: #475569;
  margin: 0 0 12px;
  line-height: 1.5;
}
.compare-row {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
}
.compare-row.recommended {
  border-color: #0ea5e9;
  box-shadow: 0 0 0 1px rgba(14, 165, 233, 0.2);
}
.row-title {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.row-meta {
  font-size: 12px;
  color: #64748b;
}
.badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}
.badge.ok {
  background: #dcfce7;
  color: #166534;
}
.badge.bad {
  background: #fee2e2;
  color: #991b1b;
}
.badge.rec {
  background: #e0f2fe;
  color: #0369a1;
}
.issue-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #b45309;
}
.trace {
  margin-top: auto;
  font-size: 11px;
  color: #94a3b8;
}
</style>

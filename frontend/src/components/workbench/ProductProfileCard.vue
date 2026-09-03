<template>
  <div class="ppc">
    <header class="ppc-head">
      <span class="ppc-name">{{ profile.name || '商品档案' }}</span>
      <span class="ppc-status" :class="`st-${profile.status || 'draft'}`">{{ statusLabel }}</span>
    </header>

    <div class="ppc-section">
      <div class="ppc-section-title">基本信息</div>
      <div class="ppc-grid">
        <div v-for="row in baseRows" :key="row.label" class="ppc-item">
          <span class="ppc-label">{{ row.label }}</span>
          <span class="ppc-value">{{ row.value || '-' }}</span>
        </div>
      </div>
    </div>

    <div v-if="extRows.length" class="ppc-section">
      <div class="ppc-section-title">补充信息</div>
      <div class="ppc-grid">
        <div v-for="row in extRows" :key="row.label" class="ppc-item">
          <span class="ppc-label">{{ row.label }}</span>
          <span class="ppc-value">{{ row.value || '-' }}</span>
        </div>
      </div>
    </div>

    <div v-if="summary" class="ppc-summary">{{ summary }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 商品数据（product / comparison item 均可） */
  profile: { type: Object, default: () => ({}) },
})

const statusLabel = computed(() => {
  const map = { draft: '草稿', submitted: '已提交', archived: '已归档', listed: '在售' }
  return map[props.profile.status] || props.profile.status || '草稿'
})

const baseRows = computed(() => {
  const p = props.profile
  return [
    { label: '商品名称', value: p.offeringName || p.name },
    { label: '商品编码', value: p.offeringId || p.code },
    { label: '月费', value: p.monthlyFee != null && p.monthlyFee !== '' ? `${p.monthlyFee} 元` : '' },
    { label: '产品品类', value: p.category || p.categoryName || p.categoryCode },
    { label: '目标用户', value: p.targetUser },
    { label: '销售渠道', value: p.channelScope },
  ]
})

const extRows = computed(() => {
  const p = props.profile
  const rows = [
    { label: '业务场景', value: p.bizScenario },
    { label: '包含流量', value: p.includeData },
    { label: '包含语音', value: p.includeVoice },
    { label: '包含宽带', value: p.includeBroadband },
    { label: '发布地市', value: p.regionScope },
    { label: '互斥组', value: p.mutexGroup },
  ]
  return rows.filter((r) => r.value != null && r.value !== '')
})

const summary = computed(() => props.profile.summary || props.profile.desc || '')
</script>

<style scoped>
.ppc { display: flex; flex-direction: column; gap: 14px; }
.ppc-head { display: flex; align-items: center; gap: 10px; }
.ppc-name { font-size: 15px; font-weight: 700; color: #0f172a; }
.ppc-status { font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.st-draft { background: #f1f5f9; color: #64748b; }
.st-submitted { background: #eff6ff; color: #2563eb; }
.st-archived { background: #f5f3ff; color: #7c3aed; }
.st-listed { background: #ecfdf5; color: #059669; }

.ppc-section { display: flex; flex-direction: column; gap: 8px; }
.ppc-section-title { font-size: 12px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.04em; }
.ppc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.ppc-item {
  display: flex; flex-direction: column; gap: 2px;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 8px 10px;
}
.ppc-label { font-size: 11px; color: #64748b; }
.ppc-value { font-size: 13px; font-weight: 600; color: #0f172a; word-break: break-all; }

.ppc-summary {
  font-size: 12px; color: #475569; line-height: 1.7;
  background: #f8fafc; border-left: 3px solid #60a5fa; border-radius: 0 10px 10px 0;
  padding: 10px 12px;
}
</style>

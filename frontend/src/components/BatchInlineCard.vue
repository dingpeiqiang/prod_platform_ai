<template>
  <div class="batch-inline-card">
    <div class="batch-summary">
      <span class="bs-label">智读批次清单</span>
      <div class="bs-counts">
        <span class="count pass">通过 {{ passed }}</span>
        <span class="count warn">待修 {{ pending }}</span>
        <span class="count ok">可入库 {{ confirmable }}</span>
      </div>
    </div>

    <div v-if="items.length" class="batch-items">
      <div v-for="(it, idx) in items" :key="idx" class="batch-item" :class="statusTone(it)">
        <div class="bi-main">
          <span class="bi-name">{{ itemName(it) }}</span>
          <span class="bi-status" :class="statusTone(it)">{{ itemStatus(it) }}</span>
        </div>
        <div class="bi-meta">
          <span v-if="feeOf(it) != null">月费{{ feeOf(it) }}</span>
          <span v-if="confOf(it) != null" class="bi-conf" :class="confTone(confOf(it))">
            置信度 {{ Math.round(confOf(it) * 100) }}%
          </span>
          <span v-if="it.needsConfirm" class="bi-needs">需人工确认</span>
          <span v-if="(issuesOf(it) || []).length" class="bi-rules">
            {{ issuesOf(it).map((i) => i.ruleId || i).join('、') }}
          </span>
        </div>
        <button
          v-if="!isSubmitted(it)"
          type="button"
          class="bi-delete"
          title="删除该条草稿（对话确认）"
          @click="$emit('delete', it)"
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0h10M10 11v6M14 11v6"/>
          </svg>
        </button>
      </div>
    </div>
    <div v-else class="batch-empty">暂无批次条目</div>

    <div class="batch-actions">
      <button v-if="confirmable" type="button" class="act-btn primary" @click="$emit('confirm')">
        确认通过项入库
      </button>
      <button v-if="pending" type="button" class="act-btn" @click="$emit('fix')">
        修正待修项后重跑合规
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  batch: { type: Object, default: null },
  batchItems: { type: Array, default: () => [] },
})

defineEmits(['confirm', 'fix', 'delete'])

const items = computed(() => {
  const mapped = props.batchItems && props.batchItems.length ? props.batchItems : null
  const raw = props.batch?.items || []
  if (mapped) return mapped
  return raw
})

const passed = computed(() => props.batch?.passedCount ?? countWhere(items.value, (i) => isPass(i)))
const pending = computed(() => props.batch?.pendingCount ?? countWhere(items.value, (i) => !isPass(i)))
const confirmable = computed(() =>
  props.batch?.confirmableDrafts?.length ?? countWhere(items.value, (i) => isPass(i)),
)

const isPass = (it) => !!(it.compliancePass || it.status === '通过' || it.status === 'pass')
const isSubmitted = (it) => !!(it.status === '已入库' || it.status === 'submitted')
const itemName = (it) => it.draft?.offeringName || it.name || it.offeringName || '未命名'
const feeOf = (it) => {
  const d = it.draft || it
  const f = d?.monthlyFee ?? d?.fixedFeeAmount
  return f != null && f !== '' ? f : null
}
const issuesOf = (it) => (it.issues && it.issues.length ? it.issues : it.ruleIds ? it.ruleIds : null)
const confOf = (it) => {
  const raw = it.confidence ?? it.draft?.confidence
  const c = raw == null ? NaN : Number(raw)
  return Number.isFinite(c) ? c : null
}
const confTone = (c) => (c >= 0.85 ? 'high' : c >= 0.75 ? 'mid' : 'low')
const itemStatus = (it) => {
  if (isSubmitted(it)) return '已入库'
  return isPass(it) ? '通过' : '待修正'
}
const statusTone = (it) => {
  if (isSubmitted(it)) return 'filed'
  return isPass(it) ? 'pass' : 'warn'
}

function countWhere(list, pred) {
  return (Array.isArray(list) ? list : []).filter(pred).length
}
</script>

<style scoped>
.batch-inline-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  margin: 12px 0 4px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}
.batch-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #eff6ff, #f0f9ff);
  border-bottom: 1px solid #f1f5f9;
}
.bs-label { font-weight: 700; color: #0f172a; font-size: 13px; }
.bs-counts { display: flex; gap: 12px; margin-left: auto; }
.count { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 999px; }
.count.pass { background: #dcfce7; color: #15803d; }
.count.warn { background: #fef3c7; color: #b45309; }
.count.ok { background: #dbeafe; color: #1d4ed8; }
.batch-items {
  max-height: 300px;
  overflow-y: auto;
  padding: 6px 0;
}
.batch-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 14px;
  border-left: 3px solid transparent;
}
.batch-item.pass { border-left-color: #22c55e; }
.batch-item.warn { border-left-color: #f59e0b; }
.batch-item.filed { border-left-color: #94a3b8; }
.batch-item + .batch-item { border-top: 1px solid #f8fafc; }
.bi-main { display: flex; align-items: center; gap: 8px; }
.bi-name { font-weight: 600; color: #0f172a; font-size: 13px; flex: 1; }
.bi-status { font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; flex-shrink: 0; }
.bi-status.pass { background: #dcfce7; color: #15803d; }
.bi-status.warn { background: #fef3c7; color: #b45309; }
.bi-status.filed { background: #e2e8f0; color: #475569; }
.bi-meta { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #64748b; flex-wrap: wrap; }
.bi-rules { color: #b45309; }
.bi-conf { font-weight: 600; padding: 0 6px; border-radius: 999px; }
.bi-conf.high { color: #15803d; background: #dcfce7; }
.bi-conf.mid { color: #1d4ed8; background: #dbeafe; }
.bi-conf.low { color: #b91c1c; background: #fee2e2; }
.bi-needs { color: #b45309; font-weight: 600; }
.bi-delete {
  position: absolute;
  right: 12px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  align-self: flex-end;
}
.bi-delete:hover { background: #fee2e2; color: #dc2626; }
.batch-item { position: relative; }
.batch-empty { padding: 14px; color: #94a3b8; font-size: 12px; text-align: center; }
.batch-actions { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px 14px; border-top: 1px solid #f1f5f9; }
.act-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #0f172a;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.act-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.act-btn.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.act-btn.primary:hover { background: #1d4ed8; }
</style>

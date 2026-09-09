<template>
  <span v-if="status" class="freshness-badge" :class="statusClass" :title="titleText">
    <span class="freshness-dot" aria-hidden="true"></span>
    数据截至 {{ formatted }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: Object, default: null },
})

const formatted = computed(() => {
  const ts = props.status?.syncedAt
  if (!ts) return ''
  const d = ts instanceof Date ? ts : new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

const statusClass = computed(() => (props.status?.ok ? 'is-ok' : 'is-stale'))

const titleText = computed(() => {
  const s = props.status
  if (!s) return ''
  const base = `ABox 同步时间：${formatted.value}`
  if (s.ok) {
    return s.rowCount != null ? `${base}；在架 ${s.rowCount} 条` : base
  }
  return `${base}（最近一次同步未成功${s.message ? `：${s.message}` : ''}，当前为最近有效数据）`
})
</script>

<style scoped>
.freshness-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}
.freshness-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
}
.is-stale .freshness-dot {
  background: #f59e0b;
}
.is-stale {
  color: #92400e;
  border-color: #fde68a;
  background: #fffbeb;
}
</style>

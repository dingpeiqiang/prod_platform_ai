<template>
  <div v-if="evidence" class="evidence-card" :class="`sev-${severityClass}`">
    <div class="ec-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
        <polyline points="22 4 12 14.01 9 11.01"/>
      </svg>
      <span class="ec-title">{{ title }}</span>
      <span v-if="count != null" class="ec-count">{{ count }} 项</span>
    </div>

    <div class="ec-body">
      <div v-for="(item, idx) in items" :key="idx" class="ec-item" :class="{ highlight: item.highlight }">
        <span class="ec-item-label">{{ item.label }}</span>
        <span class="ec-item-value">{{ item.value }}</span>
        <span v-if="item.contribution" class="ec-item-contribution">{{ item.contribution }}</span>
      </div>
      <div v-if="!items.length && summary" class="ec-empty">{{ summary }}</div>
      <div v-else-if="summary" class="ec-summary">{{ summary }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  evidence: { type: Object, default: null },
})

const title = computed(() => {
  const e = props.evidence
  if (!e) return ''
  return e.title || '结论依据'
})

const count = computed(() => {
  const e = props.evidence
  if (!e) return null
  return e.count != null ? e.count : (Array.isArray(e.items) ? e.items.length : null)
})

const items = computed(() => {
  const e = props.evidence
  if (!e || !Array.isArray(e.items)) return []
  return e.items
})

const summary = computed(() => (props.evidence?.summary || '').trim())

const severityClass = computed(() => {
  const s = props.evidence?.severity
  if (s === 'high' || s === 'warn' || s === 'error') return 'high'
  if (s === 'warning') return 'warn'
  return 'info'
})
</script>

<style scoped>
.evidence-card {
  margin: 8px 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  overflow: hidden;
}

.evidence-card.sev-high {
  border-color: #fecaca;
  background: #fefafb;
}

.ec-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.sev-high .ec-header {
  background: #fff1f2;
  border-bottom-color: #fecaca;
  color: #be123c;
}

.ec-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.sev-high .ec-title {
  color: #9f1239;
}

.ec-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  background: #e2e8f0;
  padding: 1px 8px;
  border-radius: 999px;
  line-height: 1.6;
}

.sev-high .ec-count {
  color: #be123c;
  background: #ffe4e6;
}

.ec-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ec-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.ec-item.highlight {
  padding: 4px 8px;
  margin: -2px -8px;
  background: #fef2f2;
  border-radius: 6px;
  color: #b91c1c;
}

.ec-item-label {
  color: #64748b;
  flex-shrink: 0;
  min-width: 64px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ec-item-value {
  color: #0f172a;
  font-weight: 500;
  word-break: break-word;
}

.ec-item-contribution {
  color: #94a3b8;
  font-size: 11px;
  margin-left: auto;
  flex-shrink: 0;
}

.ec-summary {
  margin-top: 2px;
  padding-top: 6px;
  border-top: 1px dashed #e2e8f0;
  color: #64748b;
  font-size: 12px;
}

.ec-empty {
  color: #94a3b8;
  font-size: 12px;
}
</style>

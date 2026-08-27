<template>
  <div class="evidence-card" :class="[`severity-${severity}`]">
    <div class="ev-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
        <line x1="16" y1="13" x2="8" y2="13"/>
        <line x1="16" y1="17" x2="8" y2="17"/>
      </svg>
      <span class="ev-title">{{ title || '证据摘要' }}</span>
      <span v-if="count != null" class="ev-count">{{ count }}</span>
    </div>
    <div class="ev-body">
      <div v-if="items && items.length" class="ev-items">
        <div v-for="(item, idx) in visibleItems" :key="idx" class="ev-item">
          <span class="ev-item-label">{{ item.label }}</span>
          <span class="ev-item-value" :class="{ highlight: item.highlight }">{{ item.value }}</span>
          <span v-if="item.contribution" class="ev-item-contribution">{{ item.contribution }}</span>
        </div>
        <button
          v-if="items.length > maxItems"
          type="button"
          class="ev-toggle"
          @click="expanded = !expanded"
        >
          {{ expanded ? '收起' : `展开全部 ${items.length} 项` }}
        </button>
      </div>
      <div v-else-if="summary" class="ev-summary">{{ summary }}</div>
      <div v-else class="ev-empty">暂无数据</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  summary: { type: String, default: '' },
  count: { type: [Number, String], default: null },
  severity: { type: String, default: 'info' },
  items: { type: Array, default: () => [] },
  maxItems: { type: Number, default: 5 }
})

const expanded = ref(false)

const visibleItems = computed(() => {
  if (expanded.value || !props.items) return props.items
  return props.items.slice(0, props.maxItems)
})
</script>

<style scoped>
.evidence-card {
  margin: 8px 0;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.evidence-card.severity-high {
  border-color: #fecaca;
  background: #fef2f2;
}

.evidence-card.severity-warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.ev-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  color: #475569;
  font-size: 12px;
}

.evidence-card.severity-high .ev-header {
  background: #fef2f2;
  color: #dc2626;
}

.evidence-card.severity-warning .ev-header {
  background: #fff7ed;
  color: #c2410c;
}

.ev-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.ev-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  background: #f1f5f9;
  padding: 0 6px;
  border-radius: 4px;
  line-height: 1.6;
}

.ev-body {
  padding: 8px 12px 10px;
}

.ev-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ev-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 4px 0;
}

.ev-item-label {
  color: #64748b;
  min-width: 60px;
  flex-shrink: 0;
}

.ev-item-value {
  color: #0f172a;
  font-weight: 500;
  flex: 1;
}

.ev-item-value.highlight {
  color: #dc2626;
  font-weight: 600;
}

.ev-item-contribution {
  color: #64748b;
  font-size: 11px;
  background: #f1f5f9;
  padding: 0 6px;
  border-radius: 4px;
}

.ev-toggle {
  display: inline-block;
  margin-top: 4px;
  border: none;
  background: transparent;
  color: #3b82f6;
  font-size: 11px;
  cursor: pointer;
  padding: 2px 0;
}

.ev-toggle:hover {
  text-decoration: underline;
}

.ev-summary {
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}

.ev-empty {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 8px 0;
}
</style>
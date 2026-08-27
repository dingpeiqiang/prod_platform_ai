<template>
  <div v-if="hasContext" class="context-bar">
    <div class="context-tags">
      <div
        v-for="(ctx, idx) in contextItems"
        :key="idx"
        class="context-tag"
        :class="ctx.type ? `context-${ctx.type}` : ''"
      >
        <span class="context-tag-label">{{ ctx.label }}</span>
        <span class="context-tag-value">{{ ctx.value }}</span>
        <button
          v-if="ctx.removable !== false"
          type="button"
          class="context-tag-remove"
          @click="removeContext(idx)"
          title="移除"
        >
          ×
        </button>
      </div>
    </div>

    <button
      v-if="clearable"
      type="button"
      class="context-clear-btn"
      @click="clearAll"
      title="清除上下文"
    >
      清除
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  clearable: { type: Boolean, default: true }
})

const emit = defineEmits(['remove', 'clear'])

const contextItems = computed(() => props.items || [])

const hasContext = computed(() => contextItems.value.length > 0)

const removeContext = (idx) => {
  emit('remove', idx)
}

const clearAll = () => {
  emit('clear')
}
</script>

<style scoped>
.context-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.context-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.context-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
}

.context-tag.context-intent {
  border-color: #c4b5fd;
  background: #f5f3ff;
}

.context-tag.context-entity {
  border-color: #93c5fd;
  background: #eff6ff;
}

.context-tag-label {
  color: #64748b;
  font-size: 11px;
}

.context-tag-value {
  font-weight: 500;
  color: #0f172a;
}

.context-tag-remove {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 1px;
  display: inline-flex;
  align-items: center;
}

.context-tag-remove:hover {
  color: #dc2626;
}

.context-clear-btn {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #3b82f6;
  font-size: 11px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.context-clear-btn:hover {
  background: #eff6ff;
}
</style>
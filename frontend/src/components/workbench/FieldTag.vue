<template>
  <span class="field-tag" :class="`tag-${tag}`" @mouseenter="hover = true" @mouseleave="hover = false">
    {{ tagLabel }}
    <teleport to="body">
      <span v-if="hover && tagReason" class="tag-reason-pop">{{ tagReason }}</span>
    </teleport>
  </span>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  /** parsed=已解析(来自用户原话) | ai=AI推荐 | reuse=沿用惯例 */
  tag: { type: String, default: '' },
  /** AI 推荐理由（悬停展示） */
  tagReason: { type: String, default: '' },
})

const hover = ref(false)
const tagLabel = computed(() => {
  const map = { parsed: '已解析', ai: 'AI推荐', reuse: '复用' }
  return map[props.tag] || ''
})
</script>

<style scoped>
.field-tag {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  font-weight: 600;
  cursor: default;
  position: relative;
  flex-shrink: 0;
}
.tag-parsed { background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; }
.tag-ai { background: #eff6ff; color: #2563eb; border: 1px solid #bfdbfe; }
.tag-reuse { background: #f5f3ff; color: #7c3aed; border: 1px solid #ddd6fe; }

.tag-reason-pop {
  position: fixed;
  z-index: 3000;
  max-width: 260px;
  white-space: normal;
  background: #0f172a;
  color: #f1f5f9;
  font-size: 11px;
  line-height: 1.5;
  padding: 6px 10px;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.25);
  pointer-events: none;
  transform: translateY(18px);
}
</style>

<template>
  <ElDrawer
    v-model="visible"
    direction="rtl"
    size="560px"
    :with-header="false"
  >
    <InlineFormEditor
      v-if="card"
      :card="card"
      @submit="$emit('form-submit', $event)"
      @cancel="$emit('close')"
      @field-change="(e) => $emit('form-field-change', e)"
      @ai-validation="$emit('form-ai-validation', $event)"
      @confirm-submit="$emit('form-confirm-submit', $event)"
      @close="$emit('close')"
    />
    <div v-else class="wo-drawer-empty">
      <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
        <path d="M9 11l3 3L22 4"/>
        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
      </svg>
      <p>请先在消息中选择一条工单</p>
    </div>
  </ElDrawer>
</template>

<script setup>
import { computed } from 'vue'
import { ElDrawer } from 'element-plus'
import InlineFormEditor from './InlineFormEditor.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 激活的配置草稿表单卡（InlineFormEditor card 形状） */
  card: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'form-submit', 'form-cancel', 'form-field-change', 'form-ai-validation', 'form-confirm-submit', 'close'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})
</script>

<style scoped>
.wo-drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--text-tertiary, #94a3b8);
  font-size: 13px;
}
</style>

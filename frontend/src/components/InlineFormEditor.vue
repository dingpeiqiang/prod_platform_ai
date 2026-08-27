<template>
  <div class="inline-form-editor">
    <div class="ife-head">
      <span class="ife-title">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
        {{ formSchema?.formName || '配置草稿' }}
      </span>
      <span v-if="card.formSubmitted" class="ife-badge filed">已入库</span>
      <span v-else-if="card.compliancePass" class="ife-badge pass">合规通过</span>
      <span v-else class="ife-badge warn">待修正</span>
      <button type="button" class="ife-close" title="收起表单" @click="$emit('close')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <div class="ife-body">
      <CompliancePanel
        v-if="showCompliance"
        :issues="issues"
        :compliance-pass="compliancePass"
        :inferred-fields="inferredFields"
      />
      <DynamicForm
        :schema="formSchema"
        :formId="formId"
        :formSubmitted="card.formSubmitted"
        :formCancelled="card.formCancelled"
        :require-compliance="requireCompliance"
        :compliance-pass="compliancePass"
        :submit-label="submitLabel"
        @submit="$emit('submit', $event)"
        @cancel="$emit('cancel')"
        @field-change="$emit('field-change', $event)"
        @ai-validation="$emit('ai-validation', $event)"
        @confirm-submit="$emit('confirm-submit', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DynamicForm from './DynamicForm.vue'
import CompliancePanel from './CompliancePanel.vue'

const props = defineProps({
  card: { type: Object, default: null },
})

defineEmits(['submit', 'cancel', 'field-change', 'ai-validation', 'confirm-submit', 'close'])

const formSchema = computed(() => props.card?.formSchema || null)
const formId = computed(() => props.card?.formId || '')
const issues = computed(() => props.card?.issues || [])
const compliancePass = computed(() => !!props.card?.compliancePass)
const inferredFields = computed(() => props.card?.inferredFields || [])
const showCompliance = computed(() => {
  const c = props.card || {}
  return c.formCode === 'offering_config' || (issues.value && issues.value.length > 0) || (inferredFields.value && inferredFields.value.length > 0)
})
const requireCompliance = computed(() => props.card?.formCode === 'offering_config')
const submitLabel = computed(() => props.card?.formSubmitted ? '已入库' : (props.card?.submitLabel || '校验并提交'))
</script>

<style scoped>
.inline-form-editor {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  margin: 12px 0 4px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}
.ife-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
  background: linear-gradient(135deg, #eff6ff, #f0f9ff);
}
.ife-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: #0f172a;
  font-size: 13px;
  flex: 1;
  min-width: 0;
}
.ife-title svg { color: #2563eb; flex-shrink: 0; }
.ife-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
  flex-shrink: 0;
}
.ife-badge.filed { background: #d1fae5; color: #047857; }
.ife-badge.pass { background: #dcfce7; color: #15803d; }
.ife-badge.warn { background: #fef3c7; color: #b45309; }
.ife-close {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.ife-close:hover { background: #e2e8f0; color: #475569; }
.ife-body {
  padding: 4px 14px 14px;
  max-height: 60vh;
  overflow-y: auto;
}
</style>

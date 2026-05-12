<template>
  <div class="validation-result-panel">
    <!-- 通过状态 -->
    <div v-if="passed" class="validation-header validation-pass">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#27ae60" stroke-width="2.5">
        <polyline points="20 6 9 17 4 12"/>
      </svg>
      <span class="validation-title">校验通过</span>
      <span class="form-code-tag">{{ formCode }}</span>
    </div>

    <!-- 失败状态 -->
    <div v-else class="validation-header validation-fail">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#e74c3c" stroke-width="2.5">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <span class="validation-title">校验未通过</span>
      <span class="form-code-tag">{{ formCode }}</span>
    </div>

    <!-- 使用专门的表格组件展示校验结果 -->
    <ValidationResultTable
      v-if="validationTable"
      :columns="validationTable.columns"
      :rows="validationTable.rows"
      :summary="validationTable.summary"
      :warnings="warnings || []"
    />

    <!-- 校验阶段信息 -->
    <div v-if="step" class="validation-step">
      校验范围：规则引擎 {{ rule_engine_passed ? '✅' : '❌' }}
    </div>
  </div>
</template>

<script setup>
import ValidationResultTable from '../ValidationResultTable.vue'

defineProps({
  formCode: { type: String, default: '' },
  passed: { type: Boolean, default: false },
  errors: { type: Array, default: () => [] },
  warnings: { type: Array, default: () => [] },
  step: { type: String, default: '' },
  rule_engine_passed: { type: Boolean, default: false },
  validationTable: { type: Object, default: null }
})
</script>

<style scoped>
.validation-result-panel {
  padding: 12px 16px;
  border-radius: 8px;
  margin-top: 8px;
  background: #fafafa;
  border: 1px solid #eee;
}

.validation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.validation-pass {
  color: #27ae60;
}

.validation-fail {
  color: #e74c3c;
}

.validation-title {
  flex: 1;
}

.form-code-tag {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-tertiary);
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
}

.validation-errors,
.validation-warnings {
  margin-bottom: 10px;
}

.validation-section-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #666;
  font-weight: 500;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #eee;
}

.validation-item {
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 8px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
}

.error-item {
  border-left: 3px solid #e74c3c;
}

.warning-item {
  border-left: 3px solid #f39c12;
  display: flex;
  align-items: center;
  gap: 6px;
}

.error-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.error-field {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.source-tags {
  display: flex;
  gap: 4px;
}

.error-source {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
}

.source-rule_engine {
  background: #e8f5e9;
  color: #2e7d32;
}

.source-llm_validation {
  background: #f3e5f5;
  color: #7b1fa2;
}

.error-messages {
  margin-bottom: 6px;
}

.error-message {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 12px;
  color: #555;
  line-height: 1.4;
  padding: 2px 0;
}

.error-message svg {
  margin-top: 3px;
  flex-shrink: 0;
}

.error-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  color: #3498db;
  background: #f0f7ff;
  padding: 6px 8px;
  border-radius: 4px;
  margin-top: 4px;
}

.warning-text {
  font-size: 12px;
  color: #d68910;
}

/* 表格组件已移至 ValidationResultTable.vue */

.validation-step {
  font-size: 11px;
  color: #999;
  padding-top: 6px;
  border-top: 1px solid #eee;
}
</style>
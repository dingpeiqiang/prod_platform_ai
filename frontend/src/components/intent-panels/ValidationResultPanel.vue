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

    <!-- 错误列表（结构化） -->
    <div v-if="errors?.length" class="validation-errors">
      <div class="validation-section-title">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
        </svg>
        错误 ({{ errors.length }})
      </div>

      <div
        v-for="(err, idx) in errors"
        :key="'err-' + idx"
        class="validation-item error-item"
      >
        <div class="error-header">
          <span class="error-field">{{ err.field || '未知字段' }}</span>
          <span class="error-source" :class="'source-' + err.source">
            {{ err.source === 'rule_engine' ? '规则引擎' : 'AI 校验' }}
          </span>
        </div>
        <div class="error-message">{{ err.message }}</div>
        <div v-if="err.suggestion" class="error-suggestion">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          {{ err.suggestion }}
        </div>
      </div>
    </div>

    <!-- 警告列表 -->
    <div v-if="warnings?.length" class="validation-warnings">
      <div class="validation-section-title">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
          <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        警告 ({{ warnings.length }})
      </div>
      <div v-for="(warn, idx) in warnings" :key="'warn-' + idx" class="validation-item warning-item">
        <span class="warning-text">{{ warn }}</span>
      </div>
    </div>

    <!-- 校验阶段信息 -->
    <div v-if="step" class="validation-step">
      校验范围：规则引擎 {{ rule_engine_passed ? '✅' : '❌' }}
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  formCode: { type: String, default: '' },
  passed: { type: Boolean, default: false },
  errors: { type: Array, default: () => [] },
  warnings: { type: Array, default: () => [] },
  step: { type: String, default: '' },
  rule_engine_passed: { type: Boolean, default: false }
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
  color: #888;
  background: #f0f0f0;
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
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
  background: white;
  border: 1px solid #e8e8e8;
}

.error-item {
  border-left: 3px solid #e74c3c;
}

.warning-item {
  border-left: 3px solid #f39c12;
}

.error-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.error-field {
  font-size: 13px;
  font-weight: 600;
  color: #333;
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

.error-message {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
  margin-bottom: 4px;
}

.error-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  color: #3498db;
  background: #f0f7ff;
  padding: 4px 8px;
  border-radius: 4px;
  margin-top: 4px;
}

.warning-text {
  font-size: 12px;
  color: #d68910;
}

.validation-step {
  font-size: 11px;
  color: #999;
  padding-top: 6px;
  border-top: 1px solid #eee;
}
</style>
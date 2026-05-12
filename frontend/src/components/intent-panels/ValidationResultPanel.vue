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

    <!-- 表格格式校验结果 -->
    <div v-if="validationTable" class="validation-table-section">
      <div class="validation-section-title">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
        </svg>
        校验结果
      </div>
      
      <div class="validation-table-wrapper">
        <table class="validation-table">
          <thead>
            <tr>
              <th v-for="col in validationTable.columns" :key="col.key">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr 
              v-for="(row, idx) in validationTable.rows" 
              :key="idx"
              :class="{ 'row-failed': row.validationResult === '不通过' }"
            >
              <td v-for="col in validationTable.columns" :key="col.key" class="table-cell">
                <template v-if="col.key === 'validationResult'">
                  <span v-if="row[col.key] === '通过'" class="result-badge result-pass">✅ 通过</span>
                  <span v-else class="result-badge result-fail">❌ 不通过</span>
                </template>
                <template v-else-if="col.key === 'suggestion' && row.suggestion">
                  <div class="suggestion-cell">
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    {{ row[col.key] }}
                  </div>
                </template>
                <template v-else>
                  {{ row[col.key] || '-' }}
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 汇总信息 -->
      <div class="validation-summary">
        <span class="summary-item">
          共 <strong>{{ validationTable.summary.totalFields }}</strong> 个字段
        </span>
        <span class="summary-item summary-pass">
          ✅ 通过 <strong>{{ validationTable.summary.passedCount }}</strong>
        </span>
        <span class="summary-item summary-fail">
          ❌ 不通过 <strong>{{ validationTable.summary.failedCount }}</strong>
        </span>
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
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#f39c12" stroke-width="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
        </svg>
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
import { computed } from 'vue'

const props = defineProps({
  formCode: { type: String, default: '' },
  passed: { type: Boolean, default: false },
  errors: { type: Array, default: () => [] },
  warnings: { type: Array, default: () => [] },
  step: { type: String, default: '' },
  rule_engine_passed: { type: Boolean, default: false },
  validationTable: { type: Object, default: null }
})

// 按字段名分组错误（保留用于兼容旧格式）
const groupedErrors = computed(() => {
  const groups = {}

  for (const err of props.errors) {
    const field = err.field || '未知字段'
    if (!groups[field]) {
      groups[field] = {
        field,
        fieldCode: err.fieldCode || '',
        errors: [],
        sources: [],
        suggestions: []
      }
    }

    groups[field].errors.push({
      message: err.message,
      source: err.source
    })

    if (!groups[field].sources.includes(err.source)) {
      groups[field].sources.push(err.source)
    }

    if (err.suggestion && !groups[field].suggestions.includes(err.suggestion)) {
      groups[field].suggestions.push(err.suggestion)
    }
  }

  return Object.values(groups)
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

/* 表格样式 */
.validation-table-section {
  margin-bottom: 10px;
}

.validation-table-wrapper {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid #eee;
  margin-bottom: 8px;
}

.validation-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.validation-table th {
  background: #f8f9fa;
  padding: 8px 10px;
  text-align: left;
  font-weight: 600;
  color: #666;
  border-bottom: 2px solid #e9ecef;
  white-space: nowrap;
}

.validation-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #eee;
  vertical-align: top;
}

.validation-table tbody tr:hover {
  background: #f8f9fa;
}

.validation-table tbody tr.row-failed {
  background: #fff5f5;
}

.validation-table tbody tr.row-failed td {
  border-bottom-color: #ffe5e5;
}

.table-cell {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.result-pass {
  background: #e8f5e9;
  color: #2e7d32;
}

.result-fail {
  background: #ffebee;
  color: #c62828;
}

.suggestion-cell {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  color: #3498db;
  line-height: 1.3;
}

.validation-summary {
  display: flex;
  gap: 16px;
  font-size: 12px;
  padding: 8px 10px;
  background: #f8f9fa;
  border-radius: 4px;
}

.summary-item {
  color: #666;
}

.summary-item.summary-pass {
  color: #27ae60;
}

.summary-item.summary-fail {
  color: #e74c3c;
}

.validation-step {
  font-size: 11px;
  color: #999;
  padding-top: 6px;
  border-top: 1px solid #eee;
}
</style>
<template>
  <div class="validation-result-table">
    <!-- 表格头部 -->
    <div class="table-header">
      <div class="header-title">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="9" y1="3" x2="9" y2="21"/>
          <line x1="15" y1="3" x2="15" y2="21"/>
        </svg>
        <span>校验结果</span>
      </div>
      <div class="header-summary">
        <span class="summary-item">共 {{ summary.totalFields }} 个字段</span>
        <span class="summary-item summary-pass">✅ {{ summary.passedCount }} 通过</span>
        <span class="summary-item summary-fail">❌ {{ summary.failedCount }} 不通过</span>
      </div>
    </div>

    <!-- 表格容器 -->
    <div class="table-container">
      <table class="result-table">
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key" :class="col.key">
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr 
            v-for="(row, idx) in rows" 
            :key="idx"
            :class="{ 'row-failed': row.validationResult === '不通过', 'row-passed': row.validationResult === '通过' }"
          >
            <td class="field-code">{{ row.fieldCode }}</td>
            <td class="field-name">{{ row.fieldName }}</td>
            <td class="original-value">{{ row.originalValue || '-' }}</td>
            <td class="recommended-value">{{ row.recommendedValue || '-' }}</td>
            <td class="validation-result">
              <span v-if="row.validationResult === '通过'" class="badge badge-pass">✅ 通过</span>
              <span v-else class="badge badge-fail">❌ 不通过</span>
            </td>
            <td class="suggestion">
              <div v-if="row.suggestion" class="suggestion-content">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                <span>{{ row.suggestion }}</span>
              </div>
              <span v-else class="empty">-</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 警告信息 -->
    <div v-if="warnings.length > 0" class="warnings-section">
      <div class="warnings-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#f39c12" stroke-width="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
          <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        <span>警告（{{ warnings.length }}）</span>
      </div>
      <div class="warnings-list">
        <div v-for="(warn, idx) in warnings" :key="idx" class="warning-item">
          <span class="warning-bullet">•</span>
          <span class="warning-text">{{ warn }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  columns: {
    type: Array,
    default: () => []
  },
  rows: {
    type: Array,
    default: () => []
  },
  summary: {
    type: Object,
    default: () => ({
      totalFields: 0,
      passedCount: 0,
      failedCount: 0
    })
  },
  warnings: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.validation-result-table {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.table-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.header-summary {
  display: flex;
  gap: 16px;
}

.summary-item {
  background: rgba(255, 255, 255, 0.2);
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  color: #fff;
}

.summary-item.summary-pass {
  background: rgba(46, 139, 87, 0.6);
}

.summary-item.summary-fail {
  background: rgba(178, 34, 34, 0.6);
}

.table-container {
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.result-table th {
  background: #f8f9fa;
  padding: 12px 16px;
  text-align: left;
  font-weight: 600;
  color: #666;
  border-bottom: 2px solid #e9ecef;
  white-space: nowrap;
  position: sticky;
  top: 0;
  z-index: 1;
}

.result-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  vertical-align: top;
}

.result-table tbody tr:hover {
  background: #f8f9fa;
}

.row-failed {
  background: #fff5f5;
}

.row-failed td {
  border-bottom-color: #ffe5e5;
}

.row-passed {
  background: #f0fff4;
}

.row-passed td {
  border-bottom-color: #e6ffed;
}

.field-code {
  font-family: 'Monaco', 'Consolas', monospace;
  color: #666;
  min-width: 100px;
}

.field-name {
  font-weight: 500;
  color: #333;
  min-width: 100px;
}

.original-value, .recommended-value {
  color: #555;
  min-width: 100px;
}

.validation-result {
  min-width: 80px;
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.badge-pass {
  background: #e8f5e9;
  color: #2e7d32;
}

.badge-fail {
  background: #ffebee;
  color: #c62828;
}

.suggestion {
  min-width: 200px;
}

.suggestion-content {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: #3498db;
  font-size: 12px;
  line-height: 1.4;
}

.empty {
  color: #999;
  font-style: italic;
}

.warnings-section {
  padding: 16px 20px;
  background: #fff8e1;
  border-top: 1px solid #ffe082;
}

.warnings-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #f57c00;
  margin-bottom: 12px;
  font-size: 14px;
}

.warnings-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warning-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #e65100;
}

.warning-bullet {
  color: #ff9800;
}

.warning-text {
  line-height: 1.4;
}

@media (max-width: 768px) {
  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .header-summary {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
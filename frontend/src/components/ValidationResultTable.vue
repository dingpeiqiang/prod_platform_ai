<template>
  <div class="validation-table">
    <!-- 摘要栏 -->
    <div class="summary-bar" @click="toggleExpand">
      <span class="summary-text">
        共 {{ summary.totalFields }} 个字段，{{ summary.passedCount }} 个通过，{{ summary.failedCount }} 个未通过
      </span>
      <span class="expand-icon" :class="{ expanded: isExpanded }">
        ▼
      </span>
    </div>
    
    <!-- 表格内容（默认折叠） -->
    <div v-show="isExpanded" class="table-content">
      <table>
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key">{{ col.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in rows" :key="idx">
            <td>{{ row.fieldCode }}</td>
            <td>{{ row.fieldName }}</td>
            <td>{{ row.originalValue || '-' }}</td>
            <td>{{ row.recommendedValue || '-' }}</td>
            <td :class="row.validationResult === '通过' ? 'pass' : 'fail'">
              {{ row.validationResult }}
            </td>
            <td>{{ row.suggestion || '-' }}</td>
          </tr>
        </tbody>
      </table>
      
      <div v-if="warnings.length > 0" class="warnings">
        <div v-for="(warn, idx) in warnings" :key="idx">⚠️ {{ warn }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

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

const isExpanded = ref(false)

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value
}
</script>

<style scoped>
.validation-table {
  border: 1px solid #eee;
  border-radius: 6px;
  overflow: hidden;
}

.summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8f8f8;
  cursor: pointer;
  border-bottom: 1px solid #eee;
}

.summary-bar:hover {
  background: #f0f0f0;
}

.summary-text {
  font-size: 13px;
  color: #666;
}

.expand-icon {
  font-size: 10px;
  color: #999;
  transition: transform 0.2s ease;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.table-content {
  max-height: 400px;
  overflow-y: auto;
}

.validation-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.validation-table th {
  background: #f5f5f5;
  padding: 6px 10px;
  text-align: left;
  font-weight: 600;
  color: #666;
  border-bottom: 1px solid #ddd;
  position: sticky;
  top: 0;
  z-index: 1;
}

.validation-table td {
  padding: 6px 10px;
  border-bottom: 1px solid #eee;
}

.pass {
  color: #52c41a;
  font-weight: 500;
}

.fail {
  color: #ff4d4f;
  font-weight: 500;
}

.warnings {
  padding: 8px 10px;
  background: #fff7e6;
  border-top: 1px solid #ffd591;
  font-size: 12px;
  color: #d46b08;
}
</style>
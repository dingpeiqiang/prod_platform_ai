<template>
  <div class="validation-table">
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
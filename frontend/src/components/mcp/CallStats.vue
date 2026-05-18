<template>
  <div class="call-stats">
    <div class="charts-grid">
      <!-- 调用量排行 -->
      <div class="chart-card">
        <h4>工具调用量排行 (Top 10)</h4>
        <el-table :data="topToolsByCalls" max-height="400" stripe>
          <el-table-column prop="name" label="工具名称" />
          <el-table-column prop="total_calls" label="调用次数" sortable />
          <el-table-column prop="success_rate" label="成功率" sortable>
            <template #default="{ row }">
              {{ row.success_rate }}%
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 响应时间排行 -->
      <div class="chart-card">
        <h4>平均响应时间 (Top 10 最慢)</h4>
        <el-table :data="topToolsByResponseTime" max-height="400" stripe>
          <el-table-column prop="name" label="工具名称" />
          <el-table-column prop="avg_response_time_ms" label="平均响应 (ms)" sortable>
            <template #default="{ row }">
              {{ row.avg_response_time_ms?.toFixed(0) || 0 }} ms
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 分类统计 -->
    <div class="category-stats">
      <h4>按分类统计</h4>
      <el-row :gutter="16">
        <el-col :span="6" v-for="cat in categoryStats" :key="cat.code">
          <div class="category-card">
            <h5>{{ cat.name }}</h5>
            <div class="stat-item">
              <span>工具数:</span>
              <strong>{{ cat.tool_count }}</strong>
            </div>
            <div class="stat-item">
              <span>总调用:</span>
              <strong>{{ cat.total_calls }}</strong>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  stats: {
    type: Array,
    default: () => []
  },
  tools: {
    type: Array,
    default: () => []
  }
})

const topToolsByCalls = computed(() => {
  return [...props.stats]
    .sort((a, b) => b.total_calls - a.total_calls)
    .slice(0, 10)
    .map(s => ({
      ...s,
      success_rate: s.total_calls > 0 
        ? ((s.success_calls / s.total_calls) * 100).toFixed(1)
        : 0
    }))
})

const topToolsByResponseTime = computed(() => {
  return [...props.stats]
    .filter(s => s.total_calls > 0)
    .sort((a, b) => b.avg_response_time_ms - a.avg_response_time_ms)
    .slice(0, 10)
})

const categoryStats = computed(() => {
  const categories = {}
  
  props.tools.forEach(tool => {
    const cat = tool.metadata?.category || 'general'
    if (!categories[cat]) {
      categories[cat] = {
        code: cat,
        name: getCategoryName(cat),
        tool_count: 0,
        total_calls: 0
      }
    }
    categories[cat].tool_count++
    categories[cat].total_calls += tool.stats?.total_calls || 0
  })
  
  return Object.values(categories)
})

const getCategoryName = (category) => {
  const categoryNames = {
    form: '表单工具',
    kb: '知识库工具',
    llm: 'LLM 工具',
    system: '系统工具',
    tariff: '资费工具',
    general: '通用工具'
  }
  return categoryNames[category] || category
}
</script>

<style scoped>
.call-stats {
  width: 100%;
  height: 100%;
  overflow-y: auto;
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  background: #f9fafb;
  padding: 16px;
  border-radius: 8px;
}

.chart-card h4 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 14px;
  color: #303133;
}

.category-stats {
  background: #f9fafb;
  padding: 16px;
  border-radius: 8px;
}

.category-stats h4 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 14px;
  color: #303133;
}

.category-card {
  background: white;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  margin-bottom: 10px;
}

.category-card h5 {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: #303133;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
  color: #606266;
}

.stat-item strong {
  color: #303133;
}

:deep(.el-table) {
  max-height: 350px;
}
</style>

<template>
  <div class="tool-list">
    <div class="toolbar">
      <el-input 
        v-model="searchKeyword" 
        placeholder="搜索工具名称或描述"
        prefix-icon="Search"
        style="width: 300px"
        clearable
      />
      <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 200px">
        <el-option label="全部分类" value="" />
        <el-option 
          v-for="cat in categories" 
          :key="cat.code" 
          :label="cat.name" 
          :value="cat.code"
        />
      </el-select>
    </div>

    <el-table :data="filteredTools" stripe style="width: 100%">
      <el-table-column prop="name" label="工具名称" width="200">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
        </template>
      </el-table-column>
      
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
      
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ getCategoryName(row.metadata?.category) }}</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="调用统计" width="180">
        <template #default="{ row }">
          <div class="stats-cell">
            <div>总调用: {{ row.stats?.total_calls || 0 }}</div>
            <div :class="calculateSuccessRate(row.stats) > 90 ? 'success-text' : 'warning-text'">
              成功率: {{ calculateSuccessRate(row.stats) }}%
            </div>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="平均响应" width="120">
        <template #default="{ row }">
          {{ row.stats?.avg_response_time_ms?.toFixed(0) || 0 }} ms
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="$emit('test-tool', row)">测试</el-button>
          <el-button size="small" @click="$emit('view-logs', row.name)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="filteredTools.length === 0" class="empty-state">
      <p>暂无工具数据</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  tools: {
    type: Array,
    default: () => []
  },
  categories: {
    type: Array,
    default: () => []
  }
})

defineEmits(['test-tool', 'view-logs'])

const searchKeyword = ref('')
const filterCategory = ref('')

const filteredTools = computed(() => {
  let result = props.tools
  
  if (filterCategory.value) {
    result = result.filter(t => t.metadata?.category === filterCategory.value)
  }
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(t => 
      t.name.toLowerCase().includes(keyword) ||
      t.description?.toLowerCase().includes(keyword)
    )
  }
  
  return result
})

const calculateSuccessRate = (stats) => {
  if (!stats || stats.total_calls === 0) return 0
  return ((stats.success_calls / stats.total_calls) * 100).toFixed(1)
}

const getCategoryName = (category) => {
  const cat = props.categories.find(c => c.code === category)
  return cat?.name || category
}
</script>

<style scoped>
.tool-list {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.stats-cell {
  font-size: 12px;
  line-height: 1.5;
}

.success-text {
  color: #67c23a;
  font-weight: 500;
}

.warning-text {
  color: #e6a23c;
  font-weight: 500;
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: #909399;
}

:deep(.el-table) {
  flex: 1;
  min-height: 0;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}
</style>

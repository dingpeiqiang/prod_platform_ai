<template>
  <div class="execution-logs">
    <div class="log-toolbar">
      <el-select 
        v-model="toolFilter" 
        placeholder="全部工具" 
        clearable 
        @change="loadLogs"
        style="width: 250px"
      >
        <el-option label="全部工具" value="" />
        <el-option 
          v-for="tool in uniqueTools" 
          :key="tool" 
          :label="tool" 
          :value="tool"
        />
      </el-select>
      
      <el-button @click="loadLogs" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="logs" stripe max-height="600">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.timestamp) }}
        </template>
      </el-table-column>
      
      <el-table-column prop="tool_name" label="工具名称" width="200" />
      
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="execution_time_ms" label="耗时 (ms)" width="120" sortable>
        <template #default="{ row }">
          {{ row.execution_time_ms?.toFixed(0) }}
        </template>
      </el-table-column>
      
      <el-table-column label="错误信息" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.error" class="error-text">{{ row.error }}</span>
          <span v-else class="no-error">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="logs.length === 0" class="empty-state">
      <p>暂无执行日志</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'

const props = defineProps({
  logs: {
    type: Array,
    default: () => []
  },
  toolFilter: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:logs', 'filter-change'])

const loading = ref(false)
const toolFilter = ref(props.toolFilter || '')

const uniqueTools = computed(() => {
  const tools = new Set(props.logs.map(log => log.tool_name))
  return Array.from(tools)
})

watch(() => props.toolFilter, (newVal) => {
  toolFilter.value = newVal
})

onMounted(() => {
  loadLogs()
})

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await mcpApi.getLogs(toolFilter.value)
    if (res.success) {
      emit('update:logs', res.logs)
    }
  } catch (e) {
    console.error('加载日志失败:', e)
  } finally {
    loading.value = false
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.execution-logs {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.log-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}

.no-error {
  color: #909399;
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

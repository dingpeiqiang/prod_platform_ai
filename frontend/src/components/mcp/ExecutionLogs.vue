<template>
  <div class="execution-logs">
    <div class="log-toolbar">
      <div class="filter-group">
        <el-select 
          v-model="toolFilter" 
          placeholder="全部工具" 
          clearable 
          style="width: 180px"
        >
          <el-option label="全部工具" value="" />
          <el-option 
            v-for="tool in uniqueTools" 
            :key="tool" 
            :label="tool" 
            :value="tool"
          />
        </el-select>
        
        <el-select 
          v-model="statusFilter" 
          placeholder="全部状态" 
          style="width: 120px"
        >
          <el-option label="全部状态" value="" />
          <el-option label="成功" value="success" />
          <el-option label="失败" value="error" />
        </el-select>
        
        <el-date-picker
          v-model="dateFilter"
          type="date"
          placeholder="选择日期"
          style="width: 150px"
        />
      </div>
      
      <div class="toolbar-actions">
        <el-button @click="loadLogs" :loading="loading" size="small">刷新</el-button>
        <el-button @click="clearFilters" size="small" text>清除筛选</el-button>
      </div>
    </div>

    <el-table
      :data="filteredLogs"
      stripe
      max-height="600"
      row-key="id"
      :expand-row-keys="expandedRows"
      @expand-change="handleExpandChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expanded-content">
            <div class="expanded-section">
              <div class="section-header">
                <span class="section-title">输入参数</span>
                <el-button 
                  size="small" 
                  type="primary"
                  plain
                  @click="copyToClipboard(row.input_params)"
                >
                  复制
                </el-button>
              </div>
              <pre class="json-content" v-html="highlightJson(row.input_params)"></pre>
            </div>
            <div class="expanded-section" v-if="row.output">
              <div class="section-header">
                <span class="section-title">输出结果</span>
                <el-button 
                  size="small" 
                  type="primary"
                  plain
                  @click="copyToClipboard(row.output)"
                >
                  复制
                </el-button>
              </div>
              <pre class="json-content" v-html="highlightJson(row.output)"></pre>
            </div>
            <div class="expanded-section error-section" v-if="row.error">
              <div class="section-header">
                <span class="section-title">错误详情</span>
                <el-button 
                  size="small" 
                  type="danger"
                  plain
                  @click="copyToClipboard(row.error)"
                >
                  复制
                </el-button>
              </div>
              <pre class="json-content error-json">{{ row.error }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>
      
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
          <span :class="{ 'slow-execution': row.execution_time_ms > 5000 }">
            {{ row.execution_time_ms?.toFixed(0) }}
          </span>
        </template>
      </el-table-column>
      
      <el-table-column label="错误信息" min-width="250">
        <template #default="{ row }">
          <span v-if="row.error" class="error-text" :title="row.error">
            {{ truncateError(row.error) }}
          </span>
          <span v-else class="no-error">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="filteredLogs.length === 0" class="empty-state">
      <p>暂无执行日志</p>
      <span v-if="hasActiveFilters" class="filter-hint">尝试清除筛选条件</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
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
const statusFilter = ref('')
const dateFilter = ref(null)
const expandedRows = ref([])

const uniqueTools = computed(() => {
  const tools = new Set(props.logs.map(log => log.tool_name))
  return Array.from(tools)
})

const hasActiveFilters = computed(() => {
  return toolFilter.value || statusFilter.value || dateFilter.value
})

const filteredLogs = computed(() => {
  let result = props.logs
  
  if (toolFilter.value) {
    result = result.filter(log => log.tool_name === toolFilter.value)
  }
  
  if (statusFilter.value) {
    if (statusFilter.value === 'success') {
      result = result.filter(log => log.success)
    } else if (statusFilter.value === 'error') {
      result = result.filter(log => !log.success)
    }
  }
  
  if (dateFilter.value) {
    const filterDate = new Date(dateFilter.value)
    filterDate.setHours(0, 0, 0, 0)
    const nextDay = new Date(filterDate)
    nextDay.setDate(nextDay.getDate() + 1)
    
    result = result.filter(log => {
      const logDate = new Date(log.timestamp * 1000)
      return logDate >= filterDate && logDate < nextDay
    })
  }
  
  return result
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

const clearFilters = () => {
  toolFilter.value = ''
  statusFilter.value = ''
  dateFilter.value = null
}

const handleExpandChange = (row, expanded) => {
  if (expanded) {
    expandedRows.value.push(row.id)
  } else {
    const idx = expandedRows.value.indexOf(row.id)
    if (idx > -1) {
      expandedRows.value.splice(idx, 1)
    }
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const truncateError = (error) => {
  if (!error) return ''
  if (error.length <= 50) return error
  return error.substring(0, 50) + '...'
}

const highlightJson = (data) => {
  if (!data) return '<span style="color: #909399;">-</span>'
  try {
    const jsonString = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    return jsonString
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"([^"]+)":/g, '<span class="json-key">"$1"</span>:')
      .replace(/: "([^"]+)"/g, ': <span class="json-string">"$1"</span>')
      .replace(/: (\d+)/g, ': <span class="json-number">$1</span>')
      .replace(/: (true|false)/g, ': <span class="json-boolean">$1</span>')
      .replace(/: null/g, ': <span class="json-null">null</span>')
  } catch {
    return String(data).replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

const copyToClipboard = async (data) => {
  try {
    let textToCopy = ''
    
    if (typeof data === 'string') {
      textToCopy = data
    } else if (data) {
      textToCopy = JSON.stringify(data, null, 2)
    } else {
      ElMessage.warning('没有可复制的内容')
      return
    }
    
    await navigator.clipboard.writeText(textToCopy)
    ElMessage.success('复制成功')
  } catch (error) {
    console.error('复制失败:', error)
    ElMessage.error('复制失败')
  }
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
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.filter-group {
  display: flex;
  gap: 10px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-error {
  color: #909399;
}

.slow-execution {
  color: #e6a23c;
  font-weight: 500;
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: #909399;
}

.filter-hint {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-top: 8px;
}

/* 展开行样式 */
.expanded-content {
  padding: 12px 16px;
  background: #fafafa;
}

.expanded-section {
  margin-bottom: 12px;
}

.expanded-section:last-child {
  margin-bottom: 0;
}

.error-section {
  background: #fef2f2;
  padding: 8px;
  border-radius: 4px;
}

.section-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.json-content {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  color: #606266;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.6;
}

.json-content .json-key {
  color: #909399;
}

.json-content .json-string {
  color: #67c23a;
}

.json-content .json-number {
  color: #e6a23c;
}

.json-content .json-boolean {
  color: #409eff;
}

.json-content .json-null {
  color: #909399;
}

.error-json {
  color: #f56c6c;
}

:deep(.el-table) {
  flex: 1;
  min-height: 0;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

:deep(.el-table__expand-icon) {
  font-size: 14px;
}
</style>

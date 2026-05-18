<template>
  <div class="mcp-dashboard">
    <!-- 顶部导航栏 -->
    <div class="dashboard-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回首页
      </button>
      <h2>MCP 工具管理平台</h2>
      <div class="header-actions">
        <button @click="refreshData" class="refresh-btn">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M23 4v6h-6M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <!-- 统计概览卡片 -->
    <div class="stats-overview">
      <div class="stat-card">
        <div class="stat-icon">🔧</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.total_tools || 0 }}</div>
          <div class="stat-label">工具总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">📊</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.total_calls || 0 }}</div>
          <div class="stat-label">总调用次数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">✅</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.success_rate?.toFixed(1) || 0 }}%</div>
          <div class="stat-label">成功率</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">📁</div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.categories?.length || 0 }}</div>
          <div class="stat-label">分类数</div>
        </div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" type="border-card" class="dashboard-tabs">
      <!-- Tab 1: 工具列表 -->
      <el-tab-pane label="工具列表" name="tools">
        <ToolList 
          :tools="tools" 
          :categories="categories"
          @test-tool="openTester"
          @view-logs="viewLogs"
        />
      </el-tab-pane>

      <!-- Tab 2: 在线测试 -->
      <el-tab-pane label="在线测试" name="tester">
        <ToolTester 
          :tools="tools"
          :selected-tool="selectedTool"
          @test-complete="onTestComplete"
        />
      </el-tab-pane>

      <!-- Tab 3: 调用统计 -->
      <el-tab-pane label="调用统计" name="stats">
        <CallStats :stats="callStats" :tools="tools" />
      </el-tab-pane>

      <!-- Tab 4: 执行日志 -->
      <el-tab-pane label="执行日志" name="logs">
        <ExecutionLogs 
          :logs="logs"
          :tool-filter="logToolFilter"
          @update:logs="updateLogs"
          @filter-change="onLogFilterChange"
        />
      </el-tab-pane>

      <!-- Tab 5: 外部工具管理 -->
      <el-tab-pane label="外部工具管理" name="external">
        <ExternalToolManager 
          @refresh="loadTools"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import ToolList from './mcp/ToolList.vue'
import ToolTester from './mcp/ToolTester.vue'
import CallStats from './mcp/CallStats.vue'
import ExecutionLogs from './mcp/ExecutionLogs.vue'
import ExternalToolManager from './mcp/ExternalToolManager.vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['go-back'])

const activeTab = ref('tools')
const stats = ref({})
const tools = ref([])
const categories = ref([])
const selectedTool = ref(null)
const logs = ref([])
const logToolFilter = ref('')

const callStats = computed(() => {
  return tools.value.map(t => ({
    name: t.name,
    category: t.metadata?.category,
    ...t.stats
  }))
})

const goBack = () => {
  emit('go-back')
}

const refreshData = async () => {
  await Promise.all([
    loadStats(),
    loadTools(),
    loadCategories(),
    loadLogs()
  ])
  ElMessage.success('数据已刷新')
}

const loadStats = async () => {
  try {
    const res = await mcpApi.getStats()
    if (res.success) {
      stats.value = res.data
    }
  } catch (e) {
    console.error('加载统计失败:', e)
  }
}

const loadTools = async () => {
  try {
    const res = await mcpApi.listTools()
    if (res.success) {
      tools.value = res.tools
    }
  } catch (e) {
    console.error('加载工具列表失败:', e)
  }
}

const loadCategories = async () => {
  try {
    const res = await mcpApi.getCategories()
    if (res.success) {
      categories.value = res.categories
    }
  } catch (e) {
    console.error('加载分类失败:', e)
  }
}

const loadLogs = async () => {
  try {
    const res = await mcpApi.getLogs(logToolFilter.value)
    if (res.success) {
      logs.value = res.logs
    }
  } catch (e) {
    console.error('加载日志失败:', e)
  }
}

const openTester = (tool) => {
  selectedTool.value = tool
  activeTab.value = 'tester'
}

const viewLogs = (toolName) => {
  logToolFilter.value = toolName
  activeTab.value = 'logs'
  loadLogs()
}

const onTestComplete = () => {
  loadStats()
  loadTools()
  loadLogs()
}

const updateLogs = (newLogs) => {
  logs.value = newLogs
}

const onLogFilterChange = (filter) => {
  logToolFilter.value = filter
  loadLogs()
}

onMounted(async () => {
  await Promise.all([
    loadStats(),
    loadTools(),
    loadCategories(),
    loadLogs()
  ])
})
</script>

<style scoped>
.mcp-dashboard {
  padding: 12px;
  background: #f5f7fa;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.back-btn:hover {
  border-color: #409eff;
  color: #409eff;
}

.dashboard-header h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.header-actions .refresh-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.header-actions .refresh-btn:hover {
  border-color: #409eff;
  color: #409eff;
}

/* 统计卡片 */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.stat-icon {
  font-size: 24px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 10px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

/* Tabs */
.dashboard-tabs {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

:deep(.el-tabs) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

:deep(.el-tabs__header) {
  flex-shrink: 0;
  margin: 0;
}

:deep(.el-tabs__content) {
  padding: 16px;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* 优化表格行高 */
:deep(.el-table .cell) {
  padding: 8px 0;
}

:deep(.el-table th.el-table__cell) {
  padding: 10px 0;
}

:deep(.el-button--small) {
  padding: 5px 10px;
  font-size: 12px;
}

:deep(.el-tag--small) {
  padding: 2px 6px;
  font-size: 11px;
}
</style>

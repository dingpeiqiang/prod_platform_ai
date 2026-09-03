<template>
  <div class="workflow-library">
    <div class="library-header">
      <h3>📚 工作流库</h3>
      <button @click="refreshWorkflows" class="refresh-btn" title="刷新列表">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="23 4 23 10 17 10"/>
          <polyline points="1 20 1 14 7 14"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
      </button>
    </div>
    
    <div class="library-search">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/>
        <path d="m21 21-4.35-4.35"/>
      </svg>
      <input 
        v-model="searchQuery" 
        type="text" 
        placeholder="搜索工作流..." 
        class="search-input"
      />
    </div>
    
    <div class="library-filters">
      <select v-model="categoryFilter" class="filter-select">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat.code" :value="cat.code">
          {{ cat.name }}
        </option>
      </select>
      <select v-model="statusFilter" class="filter-select">
        <option value="">全部状态</option>
        <option value="active">已启用</option>
        <option value="inactive">已禁用</option>
      </select>
    </div>
    
    <div class="library-content">
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
      
      <div v-else-if="filteredWorkflows.length === 0" class="empty-state">
        <div class="empty-icon">📋</div>
        <p>暂无工作流</p>
        <p class="hint">创建工作流后会显示在这里</p>
      </div>
      
      <div v-else class="workflow-list">
        <div 
          v-for="workflow in filteredWorkflows" 
          :key="workflow.workflowCode"
          class="workflow-card"
          :class="{ 'is-active': workflow.isActive }"
        >
          <div class="card-header">
            <div class="workflow-info">
              <div class="workflow-name" :title="workflow.workflowName">{{ workflow.workflowName }}</div>
              <div class="workflow-code" :title="workflow.workflowCode">{{ workflow.workflowCode }}</div>
            </div>
            <!-- S3-A：发布状态徽标 + 一键发布/下线（发布走守门 + 触发词自动注册，下线自动注销路由） -->
            <div class="workflow-actions">
              <span class="status-badge" :class="workflow.published ? 'published' : 'unpublished'">
                {{ workflow.published ? '已发布' : '未发布' }}
              </span>
              <button
                v-if="!workflow.published"
                @click="publishWorkflow(workflow)"
                :disabled="togglingCode === workflow.workflowCode"
                class="action-btn publish-btn"
                title="发布（校验定义并注册触发词）"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 2 11 13"/>
                  <path d="M22 2 15 22l-4-9-9-4z"/>
                </svg>
              </button>
              <button
                v-else
                @click="unpublishWorkflow(workflow)"
                :disabled="togglingCode === workflow.workflowCode"
                class="action-btn unpublish-btn"
                title="下线（注销触发词路由）"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="card-body">
            <p v-if="workflow.description" class="workflow-desc">{{ workflow.description }}</p>
            <!-- S3-A 触发词：独占一行全量展示（对话路由入口），最多 8 个 +N -->
            <div v-if="workflow.trigger_keywords && workflow.trigger_keywords.length" class="workflow-keywords">
              <span class="kw-label">触发词</span>
              <span v-for="kw in workflow.trigger_keywords.slice(0, 8)" :key="kw" class="kw-chip" :title="kw">{{ kw }}</span>
              <span v-if="workflow.trigger_keywords.length > 8" class="kw-chip" :title="workflow.trigger_keywords.slice(8).join('、')">+{{ workflow.trigger_keywords.length - 8 }}</span>
            </div>
            <div v-if="workflow.tags && workflow.tags.length > 0" class="workflow-tags">
              <span v-for="tag in workflow.tags.filter(t => !String(t).startsWith('kw:')).slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
            </div>
            <div class="workflow-foot">
              <span class="stat-item">{{ getCategoryName(workflow.category) }}</span>
              <span class="stat-dot">·</span>
              <span class="stat-item">v{{ workflow.version }}</span>
              <span class="stat-dot">·</span>
              <span class="stat-item">{{ workflow.executionCount }}次执行</span>
              <span class="foot-actions">
                <button @click="loadWorkflow(workflow)" class="foot-btn" title="加载到编辑器">编辑</button>
                <button @click="showCopyModal(workflow)" class="foot-btn" title="复制工作流">复制</button>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 复制确认弹窗 -->
    <div v-if="showCopyDialog" class="copy-modal-overlay" @click.self="closeCopyModal">
      <div class="copy-modal">
        <div class="modal-header">
          <h4>复制工作流</h4>
          <button @click="closeCopyModal" class="close-btn">✕</button>
        </div>
        <div class="modal-body">
          <p>源工作流：<strong>{{ copyingWorkflow?.workflowName }}</strong></p>
          <div class="form-group">
            <label>新工作流代码</label>
            <input 
              v-model="newWorkflowCode" 
              type="text" 
              class="form-input"
              placeholder="请输入新工作流代码"
            />
          </div>
          <div class="form-group">
            <label>新工作流名称（可选）</label>
            <input 
              v-model="newWorkflowName" 
              type="text" 
              class="form-input"
              :placeholder="`${copyingWorkflow?.workflowName} (副本)`"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button @click="closeCopyModal" class="btn-secondary">取消</button>
          <button @click="confirmCopy" :disabled="!newWorkflowCode || copying" class="btn-primary">
            <span v-if="copying" class="spinner"></span>
            {{ copying ? '复制中...' : '确认复制' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as workflowApi from '@/services/workflowApi'

const emit = defineEmits(['load-workflow'])

const workflows = ref([])
const loading = ref(false)
const searchQuery = ref('')
const categoryFilter = ref('')
const statusFilter = ref('')
const categories = ref([
  { code: 'general', name: '通用' },
  { code: 'ai', name: 'AI应用' },
  { code: 'data', name: '数据处理' },
  { code: 'integration', name: '系统集成' },
  { code: 'automation', name: '自动化' }
])

// 复制弹窗状态
const showCopyDialog = ref(false)
const copyingWorkflow = ref(null)
const newWorkflowCode = ref('')
const newWorkflowName = ref('')
const copying = ref(false)

// S3-A 一键发布/下线：进行中的 workflowCode（防重复点击）
const togglingCode = ref('')

const filteredWorkflows = computed(() => {
  return workflows.value.filter(wf => {
    // 搜索过滤
    const matchSearch = !searchQuery.value ||
      wf.workflowName.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      wf.workflowCode.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      (wf.description && wf.description.toLowerCase().includes(searchQuery.value.toLowerCase()))
    
    // 分类过滤
    const matchCategory = !categoryFilter.value || wf.category === categoryFilter.value
    
    // 状态过滤
    const matchStatus = !statusFilter.value || 
      (statusFilter.value === 'active' && wf.isActive) ||
      (statusFilter.value === 'inactive' && !wf.isActive)
    
    return matchSearch && matchCategory && matchStatus
  })
})

const getCategoryName = (code) => {
  const cat = categories.value.find(c => c.code === code)
  return cat ? cat.name : code
}

const refreshWorkflows = async () => {
  loading.value = true
  try {
    const result = await workflowApi.workflowApi.getAllWorkflows()
    if (result.success) {
      workflows.value = result.data.data || []
    } else {
      ElMessage.error('加载工作流列表失败')
    }
  } catch (error) {
    console.error('Failed to load workflows:', error)
    ElMessage.error('加载工作流列表失败')
  } finally {
    loading.value = false
  }
}

const loadWorkflow = async (workflow) => {
  try {
    // 获取完整的工作流数据（包括 workflowData）
    const result = await workflowApi.workflowApi.get(workflow.workflowCode)
    if (result.success && result.data) {
      emit('load-workflow', result.data)
      ElMessage.success(`已加载工作流: ${workflow.workflowName}`)
    } else {
      // 如果获取失败，尝试使用本地数据（兼容没有后端的情况）
      emit('load-workflow', workflow)
      ElMessage.warning(`从缓存加载工作流: ${workflow.workflowName}`)
    }
  } catch (error) {
    console.error('加载工作流失败:', error)
    // 获取失败时使用本地数据作为兜底
    emit('load-workflow', workflow)
    ElMessage.warning(`从缓存加载工作流: ${workflow.workflowName}`)
  }
}

// S3-A 一键发布：发布即绿灯（后端守门校验非法定义拒绝发布）+ 触发词自动注册
const publishWorkflow = async (workflow) => {
  togglingCode.value = workflow.workflowCode
  try {
    const result = await workflowApi.workflowApi.publish(workflow.workflowCode)
    if (result.success) {
      ElMessage.success(`已发布: ${workflow.workflowName}（触发词已注册到对话路由）`)
      refreshWorkflows()
    } else {
      ElMessage.error(result.message || '发布失败：定义校验未通过')
    }
  } catch (error) {
    console.error('发布失败:', error)
    ElMessage.error('发布失败: ' + (error.response?.data?.detail || error.message))
  } finally {
    togglingCode.value = ''
  }
}

// S3-A 一键下线：注销触发词路由（对话不再命中该流程）
const unpublishWorkflow = async (workflow) => {
  togglingCode.value = workflow.workflowCode
  try {
    const result = await workflowApi.workflowApi.unpublish(workflow.workflowCode)
    if (result.success) {
      ElMessage.success(`已下线: ${workflow.workflowName}（触发词路由已注销）`)
      refreshWorkflows()
    } else {
      ElMessage.error(result.message || '下线失败')
    }
  } catch (error) {
    console.error('下线失败:', error)
    ElMessage.error('下线失败: ' + (error.response?.data?.detail || error.message))
  } finally {
    togglingCode.value = ''
  }
}

const showCopyModal = (workflow) => {
  copyingWorkflow.value = workflow
  newWorkflowCode.value = `${workflow.workflowCode}_copy_${Date.now()}`
  newWorkflowName.value = ''
  showCopyDialog.value = true
}

const closeCopyModal = () => {
  showCopyDialog.value = false
  copyingWorkflow.value = null
  newWorkflowCode.value = ''
  newWorkflowName.value = ''
  copying.value = false
}

const confirmCopy = async () => {
  if (!newWorkflowCode.value || !copyingWorkflow.value) return
  
  copying.value = true
  try {
    const result = await workflowApi.workflowApi.copy(
      copyingWorkflow.value.workflowCode,
      newWorkflowCode.value,
      newWorkflowName.value || undefined
    )
    
    if (result.success) {
      closeCopyModal()
      refreshWorkflows()
      // 自动加载复制的工作流到编辑区，标记为复制模式（可编辑）
      emit('load-workflow', { ...result.data, isCopy: true })
    } else {
      ElMessage.error(result.message || '复制失败')
    }
  } catch (error) {
    console.error('Failed to copy workflow:', error)
    ElMessage.error('复制失败: ' + (error.response?.data?.detail || error.message))
  } finally {
    copying.value = false
  }
}

onMounted(() => {
  refreshWorkflows()
})
</script>

<style scoped>
.workflow-library {
  width: 320px;
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.library-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
}

.library-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #64748b;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background-color: #e2e8f0;
  color: #334155;
}

.library-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background-color: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.library-search svg {
  color: #94a3b8;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 12px;
  color: #334155;
  background: transparent;
}

.search-input::placeholder {
  color: #94a3b8;
}

.library-filters {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background-color: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.filter-select {
  flex: 1;
  padding: 6px 8px;
  font-size: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background-color: #fff;
  color: #475569;
  cursor: pointer;
  outline: none;
}

.library-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #94a3b8;
  font-size: 13px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-state p {
  margin: 4px 0;
  font-size: 13px;
}

.empty-state .hint {
  font-size: 11px;
  color: #cbd5e1;
}

.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.workflow-card {
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
}

.workflow-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.workflow-card.is-active {
  border-left: 3px solid #10b981;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  justify-content: space-between;
}

.workflow-info {
  flex: 1;
  min-width: 0;
}

.workflow-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  white-space: normal;
  word-break: break-word;
  line-height: 1.5;
  padding-top: 1px;
  padding-bottom: 2px;
}

.workflow-code {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workflow-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.category-tag {
  font-size: 10px;
  padding: 1px 6px;
  background-color: #eff6ff;
  color: #3b82f6;
  border-radius: 4px;
}

.status-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.status-badge.active {
  background-color: #dcfce7;
  color: #16a34a;
}

.status-badge.inactive {
  background-color: #fef2f2;
  color: #dc2626;
}

/* S3-A 发布状态徽标 */
.status-badge.published {
  background-color: #dcfce7;
  color: #16a34a;
}

.status-badge.unpublished {
  background-color: #f1f5f9;
  color: #94a3b8;
}

.workflow-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.action-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 4px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.action-btn:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
  color: #3b82f6;
}

/* S3-A 发布/下线按钮 */
.action-btn.publish-btn:hover {
  background-color: #dcfce7;
  border-color: #16a34a;
  color: #16a34a;
}

.action-btn.unpublish-btn:hover {
  background-color: #fef2f2;
  border-color: #dc2626;
  color: #dc2626;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.card-body {
  padding: 10px 12px;
}

.workflow-desc {
  margin: 0 0 8px 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* S3-A 触发词：独占一行，全量可换行展示，不被压缩隐藏 */
.workflow-keywords {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.kw-label {
  font-size: 10px;
  color: #94a3b8;
  flex-shrink: 0;
}

.kw-chip {
  font-size: 10px;
  padding: 2px 7px;
  background-color: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
  border-radius: 4px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.workflow-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.stat-dot {
  color: #cbd5e1;
}

.foot-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.foot-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 11px;
  color: #3b82f6;
  padding: 2px 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.foot-btn:hover {
  background-color: #eff6ff;
  color: #2563eb;
}

/* 复制弹窗 */
.copy-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.copy-modal {
  background-color: #fff;
  border-radius: 8px;
  width: 400px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #334155;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #94a3b8;
  font-size: 16px;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background-color: #f1f5f9;
  color: #334155;
}

.modal-body {
  padding: 16px;
}

.modal-body p {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #64748b;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 4px;
}

.form-input {
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #3b82f6;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid #e2e8f0;
}

.btn-secondary, .btn-primary {
  padding: 6px 16px;
  font-size: 13px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary {
  background-color: #f1f5f9;
  color: #475569;
}

.btn-secondary:hover {
  background-color: #e2e8f0;
}

.btn-primary {
  background-color: #3b82f6;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-right: 6px;
}
</style>
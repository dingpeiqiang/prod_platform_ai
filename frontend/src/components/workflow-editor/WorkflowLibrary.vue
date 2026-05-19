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
            <div class="workflow-icon">🔄</div>
            <div class="workflow-info">
              <div class="workflow-name">{{ workflow.workflowName }}</div>
              <div class="workflow-meta">
                <span class="category-tag">{{ getCategoryName(workflow.category) }}</span>
                <span class="status-badge" :class="workflow.isActive ? 'active' : 'inactive'">
                  {{ workflow.isActive ? '启用' : '禁用' }}
                </span>
              </div>
            </div>
            <div class="workflow-actions">
              <button 
                @click="loadWorkflow(workflow)" 
                class="action-btn load-btn"
                title="加载到编辑器"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
              </button>
              <button 
                @click="showCopyModal(workflow)" 
                class="action-btn copy-btn"
                title="复制工作流"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="card-body">
            <p v-if="workflow.description" class="workflow-desc">{{ workflow.description }}</p>
            <div class="workflow-stats">
              <span class="stat-item">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                v{{ workflow.version }}
              </span>
              <span class="stat-item">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M16 11V7a4 4 0 0 0-8 0v4M5 9h14l1 12H4L5 9"/>
                </svg>
                {{ workflow.executionCount }}次执行
              </span>
            </div>
            <div v-if="workflow.tags && workflow.tags.length > 0" class="workflow-tags">
              <span v-for="tag in workflow.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
              <span v-if="workflow.tags.length > 3" class="tag">+{{ workflow.tags.length - 3 }}</span>
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

const filteredWorkflows = computed(() => {
  return workflows.value.filter(wf => {
    // 只显示纳入工作流库的工作流
    if (!wf.isInLibrary) return false
    
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
      workflows.value = result.data
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

const loadWorkflow = (workflow) => {
  emit('load-workflow', workflow)
  ElMessage.success(`已加载工作流: ${workflow.workflowName}`)
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
  width: 280px;
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
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.workflow-icon {
  font-size: 20px;
}

.workflow-info {
  flex: 1;
  min-width: 0;
}

.workflow-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
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

.workflow-actions {
  display: flex;
  gap: 4px;
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

.card-body {
  padding: 8px 10px;
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

.workflow-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #94a3b8;
}

.workflow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag {
  font-size: 10px;
  padding: 1px 6px;
  background-color: #f1f5f9;
  color: #64748b;
  border-radius: 4px;
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
<template>
  <div class="prompt-manager">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <div class="left-section">
        <button class="back-btn" @click="goBack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回
        </button>
        <h1>提示词管理</h1>
      </div>
      <div class="right-section">
        <select v-model="filterCategory" @change="loadPrompts" class="filter-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
        </select>
        <button class="secondary-btn" @click="openCreateModal">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建提示词
        </button>
      </div>
    </div>

    <div class="main-content">
      <!-- 左侧：提示词列表 -->
      <div class="sidebar">
        <div class="search-box">
          <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <input v-model="searchQuery" placeholder="搜索提示词..." />
        </div>
        
        <div class="prompts-list">
          <div
            v-for="prompt in filteredPrompts" 
            :key="prompt.code"
            class="prompt-item"
            :class="{ active: selectedCode === prompt.code }"
            @click="selectPrompt(prompt)"
          >
            <div class="item-header">
              <div class="item-title">
                <span class="item-category" :class="getCategoryClass(prompt.category)">{{ getCategoryName(prompt.category) }}</span>
                <h4>{{ prompt.name }}</h4>
              </div>
              <div class="item-status">
                <span v-if="prompt.isActive" class="status-dot active"></span>
                <span v-else class="status-dot"></span>
              </div>
            </div>
            <p class="item-desc">{{ prompt.description || '暂无描述' }}</p>
            <div class="item-meta">
              <span class="meta-item">v{{ prompt.version }}</span>
              <span class="meta-item" v-if="prompt.variables?.length">{{ prompt.variables.length }} 变量</span>
              <span class="meta-item">{{ formatDate(prompt.updatedAt) }}</span>
            </div>
          </div>
          <div v-if="prompts.length === 0" class="empty-sidebar">
            <div class="empty-icon">📝</div>
            <p>还没有提示词</p>
            <button class="small-btn" @click="openCreateModal">创建第一个</button>
          </div>
        </div>
      </div>

      <!-- 右侧：编辑器 -->
      <div class="editor-area">
        <div v-if="!selectedPrompt" class="no-selection">
          <div class="no-selection-icon">👈</div>
          <h3>选择一个提示词开始编辑</h3>
          <p>或者点击「新建提示词」创建新的</p>
        </div>
        
        <div v-else class="editor-main-container">
          <!-- 编辑器头部 -->
          <div class="editor-header">
            <div class="editor-info">
              <div class="code-badge">{{ selectedPrompt.code }}</div>
              <input 
                v-model="editingData.name" 
                class="title-input" 
                placeholder="提示词名称"
                @blur="autoSave"
              />
            </div>
            <div class="editor-actions">
              <button class="action-btn" @click="openPreviewModal" title="预览">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
              <button class="action-btn" @click="showVersions" title="版本历史">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <polyline points="12 6 12 12 16 14"/>
                </svg>
              </button>
              <button class="primary-btn" @click="savePrompt" :disabled="saving">
                <span v-if="saving">保存中...</span>
                <span v-else>保存</span>
              </button>
            </div>
          </div>

          <!-- 主编辑区域 -->
          <div class="editor-workspace">
            <!-- 左侧：编辑器 -->
            <div class="editor-left">
              <div class="editor-container" :class="{ 'preview-mode': showEditorPreview, 'ai-optimizing': isAIOptimizing }">
                <!-- 编辑器 -->
                <div class="editor-panel editor-main">
                  <div class="panel-header">
                    <span class="panel-title">编辑器</span>
                    <div class="panel-toolbar">
                      <button v-for="btn in toolbarButtons" :key="btn.label" class="toolbar-btn" @mousedown.prevent="insertText(btn.text)" :title="btn.label">
                        {{ btn.icon }}
                      </button>
                      <div class="toolbar-divider"></div>
                      <button class="toolbar-btn" @click="startAIOptimization" title="AI优化提示词" :disabled="aiGenerating">
                        {{ aiGenerating ? '⏳' : '🤖' }}
                      </button>
                      <div class="toolbar-divider"></div>
                      <button class="toolbar-btn" @click="showEditorPreview = !showEditorPreview" :class="{ active: showEditorPreview }" title="预览">
                        👁️
                      </button>
                      <button class="toolbar-btn" @click="toggleFullscreen" title="全屏编辑">
                        ⚡
                      </button>
                    </div>
                  </div>
                  
                  <!-- AI优化状态条 -->
                  <div v-if="isAIOptimizing" class="ai-optimizing-bar">
                    <div class="ai-status">
                      <span class="ai-spinner">⏳</span>
                      <span>AI正在优化中...</span>
                    </div>
                  </div>
                  
                  <!-- AI优化结果 -->
                  <div v-if="showAIResult" class="ai-result-bar">
                    <div class="ai-result-info">
                      <span class="ai-success">✅ AI优化完成</span>
                    </div>
                    <div class="ai-result-actions">
                      <button class="ai-action-btn reject" @click="rejectAIOptimization">
                        ❌ 拒绝
                      </button>
                      <button class="ai-action-btn accept" @click="acceptAIOptimization">
                        ✓ 接受
                      </button>
                    </div>
                  </div>
                  
                  <textarea 
                    v-model="editorContent" 
                    class="main-editor"
                    :disabled="isAIOptimizing"
                    placeholder="在此输入你的提示词，支持 Markdown 格式..."
                    @input="handleContentChange"
                    @drop="onDrop"
                    @dragover.prevent
                  />
                  <div class="editor-footer">
                    <span class="char-count">{{ (editorContent || '').length }} 字符</span>
                    <span class="editor-hint">💡 使用 &#123;&#123;变量名&#125;&#125; 定义变量</span>
                  </div>
                </div>
                
                <!-- 预览 -->
                <div v-if="showEditorPreview" class="editor-panel right-panel">
                  <div class="panel-header">
                    <span class="panel-title">实时预览</span>
                  </div>
                  <div class="preview-content" v-html="renderMarkdown(editorContent)"></div>
                </div>
              </div>
            </div>

            <!-- 右侧：配置面板 -->
            <div class="editor-right">
              <!-- 标签页导航 -->
              <div class="config-tabs">
                <button 
                  v-for="tab in tabs" 
                  :key="tab.id"
                  class="config-tab-btn"
                  :class="{ active: activeTab === tab.id }"
                  @click="activeTab = tab.id"
                >
                  <span v-if="tab.icon">{{ tab.icon }}</span>
                  {{ tab.name }}
                </button>
              </div>

              <!-- 变量标签页 -->
              <div v-show="activeTab === 'variables'" class="config-content">
                <div class="panel-header-section">
                  <h4>📦 变量</h4>
                  <button class="small-btn primary" @click="addVariable">+ 新增</button>
                </div>

                <!-- 已添加的变量 -->
                <div class="added-vars-section">
                  <h5>✅ 已添加的变量</h5>
                  <div v-if="editingVariables.length === 0" class="empty-section">
                    <p>还没有添加变量</p>
                    <p class="hint">从下方变量库点击 + 添加，或直接拖拽到编辑器</p>
                  </div>
                  <div v-else class="added-vars-list">
                    <div 
                      v-for="(v, i) in editingVariables" 
                      :key="i"
                      class="added-var-item"
                      draggable="true"
                      @dragstart="onDragStart($event, v)"
                      @click="insertVariable(v)"
                    >
                      <div class="added-var-icon">📎</div>
                      <div class="added-var-info">
                        <div class="added-var-name">{{ v.name }}</div>
                        <div class="added-var-desc">{{ v.description || '暂无描述' }}</div>
                      </div>
                      <button class="remove-added-var-btn" @click.stop="removeVariable(i)" title="移除">✕</button>
                    </div>
                  </div>
                </div>

                <!-- 变量库 -->
                <div class="library-section">
                  <div class="library-header" @click="showGlobalVariables = !showGlobalVariables">
                    <h5>📚 变量库</h5>
                    <span class="toggle-icon">{{ showGlobalVariables ? '▼' : '▶' }}</span>
                  </div>
                  <div v-if="showGlobalVariables" class="library-content">
                    <div class="library-vars-list">
                      <div 
                        v-for="(v, i) in globalVariables" 
                        :key="i"
                        class="library-var-item"
                        draggable="true"
                        @dragstart="onDragStart($event, v)"
                        @click="insertVariable(v)"
                      >
                        <div class="library-var-icon">📌</div>
                        <div class="library-var-info">
                          <div class="library-var-name">{{ v.name }}</div>
                          <div class="library-var-desc">{{ v.description }}</div>
                        </div>
                        <div class="library-var-action" @click.stop="addVariableToList(v)">+</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 工具标签页 -->
              <div v-show="activeTab === 'tools'" class="config-content">
                <div class="panel-header-section">
                  <h4>🔧 工具</h4>
                  <button class="small-btn primary" @click="addTool">+ 新增</button>
                </div>
                <div class="tools-section">
                  <div v-if="editingTools.length === 0" class="empty-section">
                    <div class="empty-icon">🛠️</div>
                    <p>还没有关联工具</p>
                    <p class="hint">添加可以在这个提示词场景中使用的工具</p>
                  </div>
                  <div v-else class="tools-list">
                    <div v-for="(t, i) in editingTools" :key="i" class="tool-card">
                      <div class="tool-icon-wrapper">
                        <span class="tool-icon">🔧</span>
                      </div>
                      <div class="tool-info">
                        <div class="tool-name">{{ t.name || '未命名工具' }}</div>
                        <div class="tool-code">{{ t.code || '未设置编码' }}</div>
                        <div class="tool-desc">{{ t.description || '暂无描述' }}</div>
                      </div>
                      <button class="remove-var-btn" @click="removeTool(i)" title="删除">✕</button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 设置标签页 -->
              <div v-show="activeTab === 'settings'" class="config-content">
                <div class="panel-header-section">
                  <h4>⚙️ 设置</h4>
                </div>
                <div class="settings-section">
                  <div class="settings-row">
                    <span class="settings-label">分类</span>
                    <select v-model="editingData.category" class="settings-input">
                      <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                    </select>
                  </div>
                  <div class="settings-row">
                    <span class="settings-label">状态</span>
                    <div class="toggle-wrapper-simple">
                      <div class="custom-toggle" :class="{ on: editingData.isActive }" @click="editingData.isActive = !editingData.isActive">
                        <span class="toggle-dot"></span>
                      </div>
                      <span class="toggle-status">{{ editingData.isActive ? '启用' : '禁用' }}</span>
                    </div>
                  </div>
                  <div class="settings-row-full">
                    <span class="settings-label">描述</span>
                    <textarea v-model="editingData.description" placeholder="描述这个提示词的用途..." class="settings-textarea"></textarea>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建提示词弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑提示词' : '创建新提示词' }}</h3>
          <button class="close-btn" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>编码 *</label>
            <input v-model="createData.code" placeholder="例如：customer_service" :disabled="isEdit" />
            <span class="form-hint">创建后不可修改，建议使用英文下划线命名</span>
          </div>
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="createData.name" placeholder="例如：客服对话助手" />
          </div>
          <div class="form-group">
            <label>分类</label>
            <select v-model="createData.category">
              <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="createData.description" placeholder="简单描述用途..." />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn primary" @click="confirmCreate">确定</button>
        </div>
      </div>
    </div>

    <!-- 新增变量弹窗 -->
    <div v-if="showAddVariableModal" class="modal-overlay" @click.self="showAddVariableModal = false">
      <div class="modal add-var-modal" @click.stop>
        <div class="modal-header">
          <h3>➕ 新增变量</h3>
          <button class="close-btn" @click="showAddVariableModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>变量名</label>
            <input v-model="newVariable.name" placeholder="例如：用户名" class="form-input" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <input v-model="newVariable.description" placeholder="变量的用途说明" class="form-input" />
          </div>
          <div class="form-group">
            <label>默认值</label>
            <input v-model="newVariable.default" placeholder="可选，预设值" class="form-input" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="showAddVariableModal = false">取消</button>
          <button class="btn primary" @click="saveNewVariable">添加</button>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <div v-if="showPreviewModal" class="modal-overlay preview-overlay" @click.self="showPreviewModal = false">
      <div class="modal preview-modal" @click.stop>
        <div class="modal-header">
          <h3>👁️ 提示词预览</h3>
          <button class="close-btn" @click="showPreviewModal = false">×</button>
        </div>
        <div class="modal-body preview-body">
          <div v-if="previewVariables.length > 0" class="variables-panel">
            <h4>变量填充</h4>
            <div class="var-inputs">
              <div v-for="(v, i) in previewVariables" :key="i" class="var-input-item">
                <label>{{ v.name }}</label>
                <input v-model="previewVariableValues[v.name]" :placeholder="v.default" />
              </div>
            </div>
            <button class="btn primary small" @click="doPreview">刷新预览</button>
          </div>
          <div class="preview-display" v-html="previewContent"></div>
        </div>
      </div>
    </div>

    <!-- 版本历史弹窗 -->
    <div v-if="showVersionsModal" class="modal-overlay" @click.self="showVersionsModal = false">
      <div class="modal versions-modal" @click.stop>
        <div class="modal-header">
          <h3>📜 版本历史</h3>
          <button class="close-btn" @click="showVersionsModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="versions.length === 0" class="empty-versions">还没有历史版本</div>
          <div v-else class="versions-list">
            <div v-for="v in versions" :key="v.id" class="version-item">
              <div class="version-info">
                <span class="version-tag">v{{ v.version }}</span>
                <span class="version-note">{{ v.changeNote || '更新' }}</span>
                <span class="version-date">{{ formatDate(v.createdAt) }}</span>
              </div>
              <button class="small-btn" @click="viewVersion(v)">查看</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategories, listPrompts, getPrompt, createPrompt, updatePrompt, deletePrompt, getVersions, previewPrompt, generateWithAI, optimizeWithAI } from '../services/promptApi.js'

const emit = defineEmits(['go-back'])
const goBack = () => { emit('go-back') }

const loading = ref(false)
const saving = ref(false)
const aiGenerating = ref(false)
const prompts = ref([])
const categories = ref([])
const filterCategory = ref('')
const searchQuery = ref('')
const selectedCode = ref(null)
const selectedPrompt = ref(null)
const editingData = ref({ content: '', isActive: true })
const editingVariables = ref([])
const editingTools = ref([])
const activeTab = ref('settings')
const autoSaveTimer = ref(null)

const showModal = ref(false)
const isEdit = ref(false)
const createData = ref({})

// AI优化相关状态
const isAIOptimizing = ref(false)
const showAIResult = ref(false)
const aiTempContent = ref('')
const originalContent = ref('')
const aiResultData = ref(null)

const showEditorPreview = ref(false)
const showPreviewModal = ref(false)
const previewContent = ref('')
const previewVariables = ref([])
const previewVariableValues = ref({})
const showAddVariableModal = ref(false)
const newVariable = ref({ name: '', description: '', default: '' })

// 全局变量库
const globalVariables = ref([
  { name: '用户名', description: '用户的名字', default: '访客' },
  { name: '日期', description: '当前日期', default: '{{当前日期}}' },
  { name: '时间', description: '当前时间', default: '{{当前时间}}' },
  { name: '邮箱', description: '用户邮箱地址', default: '' },
  { name: '公司名', description: '公司名称', default: '' },
  { name: '产品名', description: '产品名称', default: '' }
])
const showGlobalVariables = ref(false)

const showVersionsModal = ref(false)
const versions = ref([])

const tabs = [
  { id: 'settings', name: '设置', icon: '⚙️' },
  { id: 'tools', name: '工具', icon: '🔧' },
  { id: 'variables', name: '变量', icon: '📦' }
]

const toolbarButtons = [
  { icon: 'H1', label: '标题1', text: '# ' },
  { icon: 'H2', label: '标题2', text: '## ' },
  { icon: 'H3', label: '标题3', text: '### ' },
  { icon: '**B**', label: '加粗', text: '**文本**' },
  { icon: '*I*', label: '斜体', text: '*文本*' },
  { icon: '📋', label: '列表', text: '- ' },
  { icon: '{{}}', label: '变量', text: '{{变量名}}' }
]

const filteredPrompts = computed(() => {
  let result = prompts.value
  if (filterCategory.value) {
    result = result.filter(p => p.category === filterCategory.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(p => 
      p.name.toLowerCase().includes(q) || 
      p.code.toLowerCase().includes(q) ||
      (p.description && p.description.toLowerCase().includes(q))
    )
  }
  return result
})

const getCategoryClass = (code) => {
  const map = {
    'general': 'general', 'form': 'form', 'qa': 'qa', 'tool': 'tool',
    'analysis': 'analysis', 'writing': 'writing'
  }
  return map[code] || 'general'
}

const getCategoryName = (code) => {
  const cat = categories.value.find(c => c.code === code)
  return cat?.name || code
}

// 编辑器内容计算属性
const editorContent = computed({
  get: () => (isAIOptimizing.value || showAIResult.value ? aiTempContent.value : editingData.value.content),
  set: (val) => {
    if (isAIOptimizing.value || showAIResult.value) {
      aiTempContent.value = val
    } else {
      editingData.value.content = val
    }
  }
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN')
}

const loadCategories = async () => {
  const res = await getCategories()
  if (res.success) categories.value = res.data
}

const loadPrompts = async () => {
  loading.value = true
  const res = await listPrompts(filterCategory.value || undefined, true)
  if (res.success) prompts.value = res.data || []
  loading.value = false
}

const selectPrompt = async (prompt) => {
  selectedCode.value = prompt.code
  const res = await getPrompt(prompt.code)
  if (res.success) {
    selectedPrompt.value = res.data
    editingData.value = { ...res.data, isActive: res.data.isActive !== false }
    editingVariables.value = res.data.variables ? [...res.data.variables.map(v => ({...v}))] : []
    editingTools.value = res.data.tools ? [...res.data.tools.map(t => ({...t}))] : []
  }
}

const savePrompt = async () => {
  if (!editingData.value.name?.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  
  saving.value = true
  try {
    const data = {
      name: editingData.value.name,
      description: editingData.value.description,
      category: editingData.value.category,
      content: editingData.value.content,
      variables: editingVariables.value,
      tools: editingTools.value,
      isActive: editingData.value.isActive,
      changeNote: '更新提示词'
    }
    const res = await updatePrompt(selectedCode.value, data)
    if (res.success) {
      ElMessage.success('保存成功')
      loadPrompts()
      selectPrompt(res.data)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch(e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const autoSave = () => {
  // 可以在这里实现自动保存逻辑
}

const openCreateModal = () => {
  isEdit.value = false
  createData.value = {
    code: '', name: '', description: '', category: 'general', content: '', variables: [], tools: [], isActive: true
  }
  showModal.value = true
}

const confirmCreate = async () => {
  if (!createData.value.code?.trim() || !createData.value.name?.trim()) {
    ElMessage.warning('请输入编码和名称')
    return
  }
  saving.value = true
  try {
    const res = await createPrompt(createData.value)
    if (res.success) {
      ElMessage.success('创建成功')
      showModal.value = false
      loadPrompts()
      selectPrompt(res.data)
    } else {
      ElMessage.error(res.message)
    }
  } catch(e) {
    ElMessage.error('创建失败')
  } finally {
    saving.value = false
  }
}

// 开始AI优化
const startAIOptimization = async () => {
  if (!selectedPrompt.value) {
    ElMessage.warning('请先选择一个提示词')
    return
  }
  if (!editingData.value.content || !editingData.value.content.trim()) {
    ElMessage.warning('请先输入一些提示词内容')
    return
  }
  
  // 保存原始内容
  originalContent.value = editingData.value.content
  aiTempContent.value = editingData.value.content
  isAIOptimizing.value = true
  showAIResult.value = false
  aiGenerating.value = true
  
  try {
    const res = await optimizeWithAI({
      content: editingData.value.content,
      requirement: '',
      options: {}
    })
    if (res.success) {
      aiResultData.value = res.data
      aiTempContent.value = res.data.content
      showAIResult.value = true
      ElMessage.success('AI优化完成，请确认是否接受')
    } else {
      ElMessage.error(res.message)
      isAIOptimizing.value = false
    }
  } catch(e) {
    ElMessage.error('优化失败')
    isAIOptimizing.value = false
  } finally {
    aiGenerating.value = false
  }
}

// 接受AI优化结果
const acceptAIOptimization = () => {
  // 保存旧版本到历史（这里会在保存时自动处理）
  editingData.value.content = aiTempContent.value
  
  // 合并变量
  if (aiResultData.value?.variables) {
    editingVariables.value = [...editingVariables.value, ...aiResultData.value.variables.filter(v => 
      !editingVariables.value.some(existing => existing.name === v.name)
    )]
  }
  
  // 合并工具
  if (aiResultData.value?.tools) {
    editingTools.value = [...editingTools.value, ...aiResultData.value.tools.filter(t => 
      !editingTools.value.some(existing => existing.code === t.code)
    )]
  }
  
  showAIResult.value = false
  isAIOptimizing.value = false
  ElMessage.success('已接受AI优化，请点击保存生效')
  autoSave()
}

// 拒绝AI优化结果
const rejectAIOptimization = () => {
  editingData.value.content = originalContent.value
  showAIResult.value = false
  isAIOptimizing.value = false
  ElMessage.info('已恢复原内容')
}

const openPreviewModal = () => {
  previewVariables.value = editingVariables.value
  previewVariableValues.value = {}
  for (const v of editingVariables.value) {
    previewVariableValues.value[v.name] = v.default || ''
  }
  showPreviewModal.value = true
  doPreview()
}

const doPreview = async () => {
  if (selectedCode.value) {
    const res = await previewPrompt(selectedCode.value, previewVariableValues.value)
    if (res.success) {
      previewContent.value = renderMarkdown(res.data.content)
    }
  } else {
    previewContent.value = renderMarkdown(editingData.value.content || '')
  }
}

const showVersions = async () => {
  const res = await getVersions(selectedCode.value)
  if (res.success) {
    versions.value = res.data
    showVersionsModal.value = true
  }
}

const viewVersion = (v) => {
  ElMessage.info('查看版本功能开发中')
}

const addVariable = () => {
  newVariable.value = { name: '', description: '', default: '' }
  showAddVariableModal.value = true
}

const saveNewVariable = () => {
  if (!newVariable.value.name.trim()) {
    ElMessage.warning('请输入变量名')
    return
  }
  editingVariables.value.push({ ...newVariable.value })
  showAddVariableModal.value = false
  ElMessage.success('变量添加成功')
}

const addVariableToList = (v) => {
  // 检查是否已存在
  const exists = editingVariables.value.some(item => item.name === v.name)
  if (!exists) {
    editingVariables.value.push({ ...v })
    ElMessage.success(`已添加变量「${v.name}」`)
  }
}

const insertVariable = (v) => {
  // 只插入到编辑器，不添加到列表
  insertText(`{{${v.name}}}`)
}

const removeVariable = (i) => {
  editingVariables.value.splice(i, 1)
}

const onDragStart = (e, v) => {
  e.dataTransfer.setData('text/plain', `{{${v.name}}}`)
  e.dataTransfer.effectAllowed = 'copy'
}

const onDrop = (e) => {
  e.preventDefault()
  const text = e.dataTransfer.getData('text/plain')
  if (text) {
    insertText(text)
  }
}

const addTool = () => {
  editingTools.value.push({ code: '', name: '', description: '' })
}

const removeTool = (i) => {
  editingTools.value.splice(i, 1)
}

const insertText = (text) => {
  const textarea = document.querySelector('.main-editor')
  if (!textarea) return
  
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const before = editingData.value.content.substring(0, start)
  const after = editingData.value.content.substring(end)
  
  editingData.value.content = before + text + after
  
  nextTick(() => {
    textarea.focus()
    textarea.selectionStart = textarea.selectionEnd = start + text.length
  })
}

const handleContentChange = () => {
  // 自动识别变量
  const content = editingData.value.content || ''
  const variableRegex = /\{\{(\w+)\}\}/g
  const matches = [...content.matchAll(variableRegex)]
  const foundNames = [...new Set(matches.map(m => m[1]))]
  
  const currentNames = editingVariables.value.map(v => v.name)
  for (const name of foundNames) {
    if (!currentNames.includes(name)) {
      editingVariables.value.push({ name, description: '', default: '' })
    }
  }
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.querySelector('.editor-area')?.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

const renderMarkdown = (text) => {
  if (!text) return ''
  
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  
  // 标题
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')
  
  // 粗体和斜体
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')
  
  // 代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  
  // 列表 - 先处理连续的列表项
  const lines = html.split('\n')
  let inList = false
  let result = []
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.trim().startsWith('- ')) {
      if (!inList) {
        result.push('<ul>')
        inList = true
      }
      result.push('<li>' + line.substring(2) + '</li>')
    } else {
      if (inList) {
        result.push('</ul>')
        inList = false
      }
      if (line.trim()) {
        result.push('<p>' + line + '</p>')
      }
    }
  }
  
  if (inList) {
    result.push('</ul>')
  }
  
  return result.join('')
}

const closeModal = () => { showModal.value = false }
const closeAIGenerator = () => { showAIModal.value = false }

onMounted(() => {
  loadCategories()
  loadPrompts()
})
</script>

<style scoped>
.prompt-manager {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-light);
}

.left-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.left-section h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.right-section {
  display: flex;
  gap: 12px;
  align-items: center;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-elevated);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.back-btn:hover {
  background: var(--bg-secondary);
}

.filter-select {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font-size: 14px;
}

.secondary-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  transition: all var(--transition-fast);
}

.secondary-btn:hover {
  background: var(--bg-secondary);
}

.primary-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.primary-btn:hover:not(:disabled) {
  background: #2563eb;
}

.ai-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.ai-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.sidebar {
  width: 320px;
  background: var(--bg-elevated);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid var(--border-light);
}

.search-icon {
  color: var(--text-tertiary);
}

.search-box input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
  color: var(--text-primary);
}

.search-box input::placeholder {
  color: var(--text-tertiary);
}

.prompts-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.prompt-item {
  padding: 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 4px;
  transition: all var(--transition-fast);
}

.prompt-item:hover {
  background: var(--bg-secondary);
}

.prompt-item.active {
  background: rgba(91, 124, 250, 0.08);
  border: 1px solid var(--color-primary-300);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.item-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-category {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  width: fit-content;
}

.item-category.general { background: #f3f4f6; color: #4b5563; }
.item-category.form { background: #dbeafe; color: #1d4ed8; }
.item-category.qa { background: #fef3c7; color: #92400e; }
.item-category.tool { background: #d1fae5; color: #065f46; }

.item-title h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.item-desc {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #9ca3af;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
}

.status-dot.active {
  background: #22c55e;
}

.empty-sidebar {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.small-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.small-btn.primary {
  background: #3b82f6;
  color: white;
}

.editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.no-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
}

.no-selection-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.no-selection h3 {
  margin: 0 0 8px 0;
  color: #4b5563;
}

.no-selection p {
  margin: 0;
}

.editor-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e5e7eb;
}

.editor-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-badge {
  padding: 4px 10px;
  background: #f3f4f6;
  border-radius: 6px;
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
}

.title-input {
  border: none;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  outline: none;
  background: transparent;
  padding: 4px 0;
}

.title-input:focus {
  border-bottom: 2px solid #3b82f6;
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #e5e7eb;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f9fafb;
}

.editor-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 24px 0;
  background: white;
  border-bottom: 1px solid #e5e7eb;
}

.tab-btn {
  padding: 10px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #6b7280;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
}

.tab-btn.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
}

.tab-btn:hover:not(.active) {
  color: #4b5563;
  background: #f8fafc;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.editor-main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-workspace {
  flex: 1;
  display: flex;
  gap: 24px;
  min-height: 0;
  padding: 0 24px 24px 24px;
}

.editor-left {
  flex: 2;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.editor-left .editor-container {
  flex: 1;
  display: block;
  min-height: 0;
}

.editor-left .editor-container.preview-mode {
  display: flex;
  gap: 16px;
}

.editor-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #fefefe 0%, #ffffff 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.08);
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.config-tabs {
  display: flex;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
  padding: 10px 20px 0;
  gap: 6px;
}

.config-tab-btn {
  padding: 10px 20px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: #6b7280;
  border-radius: 12px 12px 0 0;
  transition: all 0.25s;
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: -1px;
}

.config-tab-btn:hover {
  color: #4b5563;
  background: #f1f5f9;
}

.config-tab-btn.active {
  color: #1d4ed8;
  background: white;
  box-shadow: 0 -4px 12px rgba(0,0,0,0.04);
}

.panel-header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
}

.panel-header-section h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
}

.config-content {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

/* 已添加的变量 */
.added-vars-section {
  padding: 18px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #ffffff;
}

.added-vars-section h5 {
  margin: 0 0 14px 0;
  font-size: 14px;
  font-weight: 700;
  color: #16a34a;
  display: flex;
  align-items: center;
  gap: 6px;
}

.added-vars-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.added-var-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%);
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 1px 3px rgba(22, 163, 74, 0.06);
}

.added-var-item:hover {
  border-color: #22c55e;
  background: linear-gradient(135deg, #dcfce7 0%, #d1fae5 100%);
  transform: translateX(3px);
  box-shadow: 0 4px 12px rgba(22, 163, 74, 0.12);
}

.added-var-item:active {
  transform: scale(0.98);
}

.added-var-icon {
  font-size: 20px;
}

.added-var-info {
  flex: 1;
  min-width: 0;
}

.added-var-name {
  font-size: 14px;
  font-weight: 700;
  color: #166534;
  margin-bottom: 3px;
}

.added-var-desc {
  font-size: 12px;
  color: #16a34a;
  opacity: 0.75;
}

.remove-added-var-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #dc2626;
  border: none;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.remove-added-var-btn:hover {
  background: linear-gradient(135deg, #fecaca 0%, #fca5a5 100%);
  transform: scale(1.15);
}

/* 变量库 */
.library-section {
  flex: 1;
  overflow-y: auto;
}

.library-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.25s;
  border-bottom: 1px solid #f0f0f0;
}

.library-header:hover {
  background: #f1f5f9;
}

.library-header h5 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 6px;
}

.toggle-icon {
  font-size: 12px;
  color: #6b7280;
  transition: transform 0.2s;
}

.library-content {
  padding: 14px 20px;
  background: #fafafa;
}

.library-vars-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.library-var-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.library-var-item:hover {
  border-color: #3b82f6;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  transform: translateX(3px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.12);
}

.library-var-item:active {
  transform: scale(0.98);
}

.library-var-icon {
  font-size: 20px;
}

.library-var-info {
  flex: 1;
  min-width: 0;
}

.library-var-name {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
  margin-bottom: 3px;
}

.library-var-desc {
  font-size: 12px;
  color: #6b7280;
}

.library-var-action {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border-radius: 50%;
  font-size: 15px;
  font-weight: 700;
  transition: all 0.2s;
}

.library-var-action:hover {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  transform: scale(1.1);
}

.config-section {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.config-section h5 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h4 {
  margin: 0;
}

.config-list {
  flex: 1;
  overflow-y: auto;
}

.config-card {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.config-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.2s;
}

.config-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.editor-panel {
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  min-width: 0;
}

.editor-container:not(.preview-mode) .editor-main {
  width: 100%;
  height: 100%;
}

.editor-container.preview-mode .editor-main {
  flex: 1;
}

.editor-panel.right-panel {
  flex: 1;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.toolbar-btn:hover {
  background: #e5e7eb;
}

.toolbar-btn.active {
  background: #dbeafe;
  color: #1d4ed8;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 4px;
}

.main-editor {
  flex: 1;
  padding: 16px;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 1.7;
  font-family: 'Monaco', 'Menlo', monospace;
  resize: none;
  min-height: 0;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #f8fafc;
  border-top: 1px solid #e5e7eb;
  font-size: 13px;
  color: #6b7280;
}

.preview-content {
  flex: 1;
  padding: 24px;
  font-size: 15px;
  line-height: 1.8;
  overflow-y: auto;
  min-height: 0;
  color: #1f2937;
}

.preview-content h1 {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 20px 0;
  color: #111827;
  border-bottom: 2px solid #e5e7eb;
  padding-bottom: 10px;
}

.preview-content h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 28px 0 14px 0;
  color: #1f2937;
}

.preview-content h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 22px 0 10px 0;
  color: #374151;
}

.preview-content code {
  background: #f3f4f6;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
  color: #dc2626;
}

.preview-content p {
  margin: 0 0 16px 0;
  text-align: justify;
}

.preview-content ul {
  margin: 12px 0;
  padding-left: 28px;
}

.preview-content li {
  margin: 6px 0;
  color: #374151;
}

.preview-content strong {
  color: #111827;
  font-weight: 600;
}

.preview-content em {
  color: #6b7280;
  font-style: italic;
}

.variables-tab, .tools-tab, .settings-tab {
  background: white;
  border-radius: 12px;
}

.variables-header, .tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
}

.variables-header h4, .tools-header h4 {
  margin: 0;
  font-size: 15px;
  color: #1f2937;
}

.variables-list, .tools-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.variable-card, .tool-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  background: #f8fafc;
  border-radius: 10px;
}

.var-main, .tool-main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 2fr 1fr;
  gap: 12px;
}

.var-name-section, .var-desc-section, .var-default-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.var-label {
  font-size: 12px;
  color: #6b7280;
}

.var-input, .tool-input {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
}

.remove-var-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  font-size: 16px;
  border-radius: 6px;
  transition: all 0.2s;
}

.remove-var-btn:hover {
  background: #fee2e2;
  color: #dc2626;
}

.settings-grid {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.setting-item.full {
  grid-column: span 2;
}

.setting-item label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.setting-item input,
.setting-item select,
.setting-item textarea {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
}

.setting-item textarea {
  min-height: 80px;
  resize: vertical;
}

.toggle-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.custom-toggle {
  width: 44px;
  height: 24px;
  background: #d1d5db;
  border-radius: 12px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.custom-toggle.on {
  background: #3b82f6;
}

.toggle-dot {
  position: absolute;
  width: 18px;
  height: 18px;
  background: white;
  border-radius: 50%;
  top: 3px;
  left: 3px;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.custom-toggle.on .toggle-dot {
  left: 23px;
}

.toggle-label {
  font-size: 13px;
  color: #6b7280;
}

.empty-section {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
}

.empty-section .hint {
  font-size: 13px;
  margin-top: 4px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.6;
}

/* 工具样式优化 */
.tools-section {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border: 1px solid #fde68a;
  border-radius: 12px;
  transition: all 0.3s;
  box-shadow: 0 2px 6px rgba(245, 158, 11, 0.06);
}

.tool-card:hover {
  border-color: #f59e0b;
  transform: translateY(-3px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.15);
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
}

.tool-icon-wrapper {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  border-radius: 14px;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.2);
}

.tool-icon {
  font-size: 26px;
}

.tool-info {
  flex: 1;
  min-width: 0;
}

.tool-name {
  font-size: 15px;
  font-weight: 700;
  color: #92400e;
  margin-bottom: 4px;
}

.tool-code {
  font-size: 12px;
  color: #78350f;
  font-family: 'Monaco', 'Menlo', monospace;
  background: white;
  padding: 3px 10px;
  border-radius: 6px;
  display: inline-block;
  margin-bottom: 4px;
  font-weight: 600;
}

.tool-desc {
  font-size: 13px;
  color: #78350f;
  opacity: 0.75;
  line-height: 1.5;
}

/* 设置样式优化 - 简约版 */
.settings-section {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.settings-row-full {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.settings-label {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.settings-input {
  padding: 10px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  background: linear-gradient(135deg, #ffffff 0%, #fafafa 100%);
  min-width: 160px;
  text-align: right;
  font-weight: 500;
}

.settings-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  background: white;
}

.settings-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  background: linear-gradient(135deg, #ffffff 0%, #fafafa 100%);
  min-height: 90px;
  resize: vertical;
  line-height: 1.6;
  box-sizing: border-box;
}

.settings-textarea:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  background: white;
}

.toggle-wrapper-simple {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-status {
  font-size: 14px;
  color: #6b7280;
  font-weight: 600;
  min-width: 38px;
  text-align: right;
}

/* 统一空状态样式 */
.config-content .empty-section {
  padding: 48px 24px;
  text-align: center;
  background: linear-gradient(135deg, #f9fafb 0%, #f8fafc 100%);
  border-radius: 16px;
  border: 2px dashed #e5e7eb;
  margin: 8px 0;
}

.config-content .empty-section p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
}

.config-content .empty-section .hint {
  margin-top: 8px;
  color: #9ca3af;
  font-size: 13px;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: 16px;
  width: 480px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.modal-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.close-btn {
  border: none;
  background: none;
  font-size: 24px;
  cursor: pointer;
  color: #9ca3af;
}

.close-btn:hover {
  color: #4b5563;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
}

.btn {
  padding: 10px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  background: white;
  font-size: 14px;
  font-weight: 500;
}

.btn.primary {
  background: #3b82f6;
  color: white;
  border: none;
}

.btn.primary.small {
  padding: 6px 14px;
  font-size: 13px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
}

.form-hint {
  font-size: 12px;
  color: #9ca3af;
}

.large-textarea {
  min-height: 120px;
  resize: vertical;
}

.ai-modal {
  width: 560px;
}

.ai-tips {
  background: #f0fdf4;
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 16px;
}

.tip-item {
  color: #166534;
  font-size: 13px;
}

.ai-result-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.use-btn {
  padding: 6px 14px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.result-content {
  padding: 14px;
  background: #f8fafc;
  border-radius: 10px;
  font-family: monospace;
  font-size: 13px;
  white-space: pre-wrap;
  max-height: 300px;
  overflow-y: auto;
}

.preview-overlay {
  align-items: stretch;
  padding: 40px;
}

.preview-modal {
  width: 100%;
  max-width: 1000px;
}

.add-var-modal {
  width: 420px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.preview-body {
  display: flex;
  gap: 20px;
  padding: 20px 24px;
}

.variables-panel {
  width: 260px;
  flex-shrink: 0;
  background: #f8fafc;
  padding: 16px;
  border-radius: 12px;
}

.variables-panel h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
}

.var-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.var-input-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.var-input-item label {
  font-size: 13px;
  color: #4b5563;
}

.var-input-item input {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
}

.preview-display {
  flex: 1;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  overflow-y: auto;
}

.versions-modal {
  width: 520px;
}

.empty-versions {
  text-align: center;
  padding: 40px;
  color: #9ca3af;
}

.versions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px;
  background: #f8fafc;
  border-radius: 10px;
}

.version-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.version-tag {
  padding: 4px 10px;
  background: #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.version-note {
  font-size: 14px;
  color: #374151;
}

.version-date {
  font-size: 12px;
  color: #9ca3af;
}

/* AI优化器新样式 */
.ai-optimizing-bar {
  padding: 12px 16px;
  background: linear-gradient(135deg, #fff7ed, #ffedd5);
  border-bottom: 1px solid #fed7aa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #92400e;
  font-weight: 500;
}

.ai-spinner {
  font-size: 18px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.ai-result-bar {
  padding: 12px 16px;
  background: linear-gradient(135deg, #ecfdf5, #d1fae5);
  border-bottom: 1px solid #6ee7b7;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-result-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-success {
  color: #065f46;
  font-weight: 600;
}

.ai-result-actions {
  display: flex;
  gap: 10px;
}

.ai-action-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.ai-action-btn.reject {
  background: #fee2e2;
  color: #991b1b;
}

.ai-action-btn.reject:hover {
  background: #fecaca;
  transform: translateY(-1px);
}

.ai-action-btn.accept {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: white;
}

.ai-action-btn.accept:hover {
  background: linear-gradient(135deg, #16a34a, #15803d);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(34,197,94,0.3);
}

.editor-container.ai-optimizing .main-editor {
  opacity: 0.6;
  pointer-events: none;
}
</style>

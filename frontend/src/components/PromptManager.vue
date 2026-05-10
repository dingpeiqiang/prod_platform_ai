<template>
  <div class="prompt-manager">
    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回首页
      </button>
      <h2>🤖 提示词管理</h2>
      <div class="header-actions">
        <select v-model="filterCategory" @change="loadPrompts" class="filter-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
        </select>
        <button class="primary-btn" @click="openCreateModal">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建提示词
        </button>
        <button class="ai-btn" @click="openAIGenerator">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
            </svg>
            AI帮我写
        </button>
      </div>
    </div>

    <!-- 提示词列表 -->
    <div class="prompts-grid">
      <div
        v-for="prompt in prompts" :key="prompt.code"
        class="prompt-card"
        :class="{ active: selectedCode === prompt.code }"
        @click="selectPrompt(prompt)"
      >
        <div class="prompt-header">
          <span class="prompt-category" :class="getCategoryClass(prompt.category)">{{ getCategoryName(prompt.category) }}</span>
          <div class="prompt-status">
            <span v-if="prompt.isActive" class="active-badge">启用</span>
            <span class="prompt-version">v{{ prompt.version }}</span>
          </div>
        </div>
        <h3 class="prompt-name">{{ prompt.name }}</h3>
        <p class="prompt-desc">{{ prompt.description || '暂无描述' }}</p>
        <div class="prompt-meta">
          <span v-if="prompt.variables?.length">
            {{ prompt.variables.length }} 个变量
          </span>
          <span v-if="prompt.tools?.length">
            {{ prompt.tools.length }} 个工具
          </span>
          <span>{{ formatDate(prompt.updatedAt) }}</span>
        </div>
      </div>
      <div v-if="prompts.length === 0" class="empty-state">
        <div class="empty-icon">📝</div>
        <h3>还没有提示词</h3>
        <p>点击「AI帮我写」来创建第一个提示词吧！</p>
      </div>
    </div>

    <!-- 提示词编辑器 -->
    <div v-if="selectedPrompt && !showModal && !showAIModal" class="editor-panel">
      <div class="editor-header">
        <div class="editor-title">
          <h3>{{ selectedPrompt.name }}</h3>
          <span class="editor-code">{{ selectedPrompt.code }}</span>
        </div>
        <div class="editor-actions">
          <button class="btn-secondary" @click="previewPrompt">👁️ 预览</button>
          <button class="btn-secondary" @click="showVersions">📜 历史版本</button>
          <button class="btn-primary" @click="savePrompt" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>

      <!-- 编辑区域 -->
      <div class="editor-content">
        <!-- 基本信息 -->
        <div class="section">
          <div class="section-title">基本信息</div>
          <div class="form-grid">
            <div class="form-item">
              <label>名称 *</label>
              <input v-model="editingData.name" placeholder="给提示词起个名字" />
            </div>
            <div class="form-item">
              <label>分类</label>
              <select v-model="editingData.category">
                <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
              </select>
            </div>
          </div>
          <div class="form-item full">
            <label>描述</label>
            <input v-model="editingData.description" placeholder="描述这个提示词的用途" />
          </div>
        </div>

        <!-- 变量管理 -->
        <div class="section">
          <div class="section-title">
            模板变量
            <button class="small-btn" @click="addVariable">+ 新增变量</button>
          </div>
          <div v-if="editingVariables.length === 0" class="empty-small">
            还没有变量，在提示词中用 {{变量名}} 会自动识别
          </div>
          <div v-else class="variables-list">
            <div v-for="(v, i) in editingVariables" :key="i" class="variable-item">
            <input v-model="v.name" class="var-input" placeholder="变量名" />
            <input v-model="v.description" class="var-input" placeholder="描述" />
            <input v-model="v.default" class="var-input" placeholder="默认值" />
            <button class="remove-btn" @click="removeVariable(i)">×</button>
          </div>
        </div>

        <!-- 工具管理 -->
        <div class="section">
          <div class="section-title">
            可用工具
            <button class="small-btn" @click="addTool">+ 添加工具</button>
          </div>
          <div v-if="editingTools.length === 0" class="empty-small">
            还没有配置工具
          </div>
          <div v-else class="tools-list">
            <div v-for="(t, i) in editingTools" :key="i" class="tool-item">
              <input v-model="t.code" class="tool-input" placeholder="工具编码" />
              <input v-model="t.name" class="tool-input" placeholder="工具名称" />
              <input v-model="t.description" class="tool-input" placeholder="工具描述" />
              <button class="remove-btn" @click="removeTool(i)">×</button>
          </div>
        </div>

        <!-- Markdown 编辑器 -->
        <div class="section">
          <div class="section-title">提示词内容</div>
          <div class="editor-layout">
            <div class="editor-half">
              <div class="tab-bar">
                <span class="tab active">编辑</span>
              </div>
              <textarea v-model="editingData.content" class="markdown-editor" placeholder="在这里输入 Markdown 提示词..." />
            </div>
            <div class="editor-half">
              <div class="tab-bar">
                <span class="tab active">预览</span>
              </div>
              <div class="markdown-preview" v-html="renderMarkdown(editingData.content)"></div>
            </div>
          </div>
          <div class="variable-hint">
            💡 提示：使用 {{变量名}} 插入模板变量，会自动识别
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
          <div class="form-item">
            <label>编码 *</label>
            <input v-model="createData.code" placeholder="例如：customer_service" :disabled="isEdit" />
          </div>
          <div class="form-item">
            <label>名称 *</label>
            <input v-model="createData.name" placeholder="例如：客服对话助手" />
          </div>
          <div class="form-item">
            <label>分类</label>
            <select v-model="createData.category">
              <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>描述</label>
            <input v-model="createData.description" placeholder="简单描述用途" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmCreate">确定</button>
        </div>
      </div>
    </div>

    <!-- AI生成器弹窗 -->
    <div v-if="showAIModal" class="modal-overlay large" @click.self="closeAIGenerator">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3>✨ AI帮我写提示词</h3>
          <button class="close-btn" @click="closeAIGenerator">×</button>
        </div>
        <div class="modal-body">
          <div class="ai-help">
            <div class="help-item">
              <span class="help-icon">💡</span>
              <span class="help-text">描述你需要的提示词功能，AI会帮你写好</span>
            </div>
          </div>
          <div class="form-item">
            <label>提示词用途</label>
            <textarea v-model="aiRequirement" class="large-textarea" placeholder="例如：我需要一个客服机器人，负责处理用户的问题..." />
          </div>
          <div class="form-grid">
            <div class="form-item">
              <label>分类</label>
              <select v-model="aiCategory">
                <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
              </select>
            </div>
          </div>
          <div v-if="aiResult" class="ai-result">
            <div class="ai-result-header">
              <span>AI生成结果</span>
              <button class="small-btn" @click="useAIResult">使用这个</button>
            </div>
            <textarea v-model="aiResult.content" class="ai-result-content" readonly />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeAIGenerator">取消</button>
          <button class="btn btn-primary" @click="generateAI" :disabled="aiGenerating">
            {{ aiGenerating ? '生成中...' : '开始生成' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <div v-if="showPreview" class="modal-overlay large" @click.self="showPreview = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3>👁️ 提示词预览</h3>
          <button class="close-btn" @click="showPreview = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="previewVariables.length > 0" class="variables-input">
            <div class="section-title small">变量填充预览</div>
            <div class="form-grid">
              <div v-for="(v, i) in previewVariables" :key="i" class="form-item">
                <label>{{ v.name }}</label>
                <input v-model="previewVariableValues[v.name]" :placeholder="v.default" />
              </div>
            </div>
            <button class="btn btn-primary" @click="doPreview">更新预览</button>
          </div>
          <div class="markdown-preview full" v-html="previewContent"></div>
        </div>
      </div>
    </div>

    <!-- 版本历史弹窗 -->
    <div v-if="showVersionsModal" class="modal-overlay large" @click.self="showVersionsModal = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h3>📜 版本历史</h3>
          <button class="close-btn" @click="showVersionsModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="versions.length === 0" class="empty-state">
            还没有历史版本
          </div>
          <div v-else class="versions-list">
            <div v-for="v in versions" :key="v.id" class="version-item">
              <div class="version-info">
                <span class="version-tag">v{{ v.version }}</span>
                <span class="version-note">{{ v.changeNote || '更新' }}</span>
                <span class="version-date">{{ formatDate(v.createdAt) }}</span>
              </div>
              <div class="version-actions">
                <button class="small-btn" @click="viewVersion(v)">查看</button>
              </div>
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
const selectedCode = ref(null)
const selectedPrompt = ref(null)
const editingData = ref({})
const editingVariables = ref([])
const editingTools = ref([])

const showModal = ref(false)
const isEdit = ref(false)
const createData = ref({})

const showAIModal = ref(false)
const aiRequirement = ref('')
const aiCategory = ref('general')
const aiResult = ref(null)

const showPreview = ref(false)
const previewContent = ref('')
const previewVariables = ref([])
const previewVariableValues = ref({})

const showVersionsModal = ref(false)
const versions = ref([])

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
    editingData.value = { ...res.data }
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

const openCreateModal = () => {
  isEdit.value = false
  createData.value = {
    code: '', name: '', description: '', category: 'general', content: '', variables: [], tools: [] }
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

const openAIGenerator = () => {
  aiRequirement.value = ''
  aiResult.value = null
  showAIModal.value = true
}

const generateAI = async () => {
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请描述你的需求')
    return
  }
  aiGenerating.value = true
  try {
    const res = await generateWithAI(aiRequirement.value, aiCategory.value, [])
    if (res.success) {
      aiResult.value = res.data
      ElMessage.success('生成成功')
    } else {
      ElMessage.error(res.message)
    }
  } catch(e) {
    ElMessage.error('生成失败')
  } finally {
    aiGenerating.value = false
  }
}

const useAIResult = () => {
  createData.value.content = aiResult.value.content
  createData.value.variables = aiResult.value.variables
  createData.value.tools = aiResult.value.tools
  showAIModal.value = false
  isEdit.value = false
  showModal.value = true
}

const previewPrompt = () => {
  previewVariables.value = editingVariables.value
  previewVariableValues.value = {}
  for (const v of editingVariables.value) {
    previewVariableValues.value[v.name] = v.default || ''
  }
  showPreview.value = true
  doPreview()
}

const doPreview = async () => {
  if (selectedCode.value) {
    const res = await previewPrompt(selectedCode.value, previewVariableValues.value)
    if (res.success) {
      previewContent.value = renderMarkdown(res.data.content)
    }
  }
}

const showVersions = async () => {
  const res = await getVersions(selectedCode.value)
  if (res.success) {
    versions.value = res.data
    showVersionsModal.value = true
  }
}

const addVariable = () => {
  editingVariables.value.push({ name: '', description: '', default: '' })
}

const removeVariable = (i) => {
  editingVariables.value.splice(i, 1)
}

const addTool = () => {
  editingTools.value.push({ code: '', name: '', description: '' })
}

const removeTool = (i) => {
  editingTools.value.splice(i, 1)
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return text
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^# (.*$)/gm, '<h1>$1</h1>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
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
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: white;
  cursor: pointer;
  font-size: 14px;
}

.top-bar h2 {
  margin: 0;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.filter-select {
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.primary-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.ai-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.prompts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.prompt-card {
  background: white;
  padding: 20px;
  border-radius: 12px;
  border: 2px solid transparent;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.prompt-card:hover {
  border-color: #3b82f6;
}

.prompt-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.prompt-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.prompt-category {
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
}

.prompt-category.general { background: #e5e7eb; color: #374151; }
.prompt-category.form { background: #dbeafe; color: #1d4ed8; }
.prompt-category.qa { background: #fef3c7; color: #92400e; }
.prompt-category.tool { background: #d1fae5; color: #065f46; }

.prompt-name {
  font-size: 16px;
  margin: 0 0 8px 0;
}

.prompt-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 12px 0;
}

.prompt-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.editor-panel {
  background: white;
  border-radius: 12px;
  padding: 24px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.editor-content {
  gap: 24px;
}

.section {
  margin-bottom: 24px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full {
  grid-column: span 2;
}

.form-item label {
  font-size: 13px;
  font-weight: 500;
}

.form-item input,
.form-item select {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.editor-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.markdown-editor {
  min-height: 300px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-family: monospace;
  resize: vertical;
}

.markdown-preview {
  min-height: 300px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
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
  border-radius: 12px;
  width: 500px;
  max-height: 90vh;
  overflow: auto;
}

.modal-overlay.large .modal {
  width: 800px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.close-btn {
  border: none;
  background: none;
  font-size: 24px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
}

.large-textarea {
  min-height: 120px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.ai-help {
  background: #f0fdf4;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.ai-result {
  margin-top: 20px;
  border-top: 1px solid #e5e7eb;
  padding-top: 16px;
}

.ai-result-content {
  width: 100%;
  min-height: 200px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid #e5e7eb;
}

.btn {
  padding: 10px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  background: white;
}

.btn-primary {
  background: #3b82f6;
  color: white;
  border: none;
}

.small-btn {
  padding: 4px 10px;
  background: #f3f4f6;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.variables-list, .tools-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.variable-item, .tool-item {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr auto;
  gap: 10px;
  align-items: center;
}

.var-input, .tool-input {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.remove-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: #fee2e2;
  color: #dc2626;
  cursor: pointer;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px;
  color: #9ca3af;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-small {
  color: #9ca3af;
  font-size: 13px;
  padding: 16px;
}
</style>

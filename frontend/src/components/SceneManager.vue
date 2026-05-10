<template>
  <div class="scene-manager">
    <!-- 顶部操作栏 -->
    <div class="header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1 class="page-title">场景管理</h1>
      </div>
      <el-button type="primary" @click="handleAddCenter">
        <el-icon><Plus /></el-icon>
        新增中心域
      </el-button>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧：场景树 -->
      <div class="tree-section">
        <div class="tree-header">
          <h3>场景结构</h3>
        </div>
        <div class="tree-search">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索场景名称、关键词" 
            clearable
            size="small"
            :prefix-icon="Search"
          />
        </div>
        <div class="tree-container">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
            class="scene-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <div class="node-content">
                  <!-- 节点图标 -->
                  <span class="node-icon" :class="`icon-${data.type}`">
                    <template v-if="data.type === 'center'">🏢</template>
                    <template v-else-if="data.type === 'business'">📁</template>
                    <template v-else>🎯</template>
                  </span>
                  <!-- 节点名称 -->
                  <span class="node-label">{{ data.sceneName || data.label }}
                    <span v-if="data.type === 'scene'" class="scene-count">(场景)</span>
                  </span>
                </div>
                <div class="node-actions">
                  <!-- 中心域：可以新增业务域 -->
                  <el-tooltip v-if="data.type === 'center'" content="新增业务域" placement="top">
                    <el-button size="small" link @click.stop="handleAddBusiness(data)" class="action-btn add-btn">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 业务域：可以新增场景 -->
                  <el-tooltip v-if="data.type === 'business'" content="新增场景" placement="top">
                    <el-button size="small" link @click.stop="handleAddScene(data)" class="action-btn add-btn">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 编辑 -->
                  <el-tooltip content="编辑" placement="top">
                    <el-button size="small" link @click.stop="handleEdit(data)" class="action-btn">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 删除 -->
                  <el-tooltip content="删除" placement="top">
                    <el-button size="small" link type="danger" @click.stop="handleDelete(data)" class="action-btn">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- 右侧：详情区 -->
      <div class="detail-section">
        <div v-if="selectedNode" class="scene-detail">
          <div class="detail-header">
            <div class="header-title">
              <span class="scene-icon">
                <template v-if="selectedNode.type === 'center'">🏢</template>
                <template v-else-if="selectedNode.type === 'business'">📁</template>
                <template v-else>🎯</template>
              </span>
              <h2>{{ selectedNode.sceneName || selectedNode.label }}</h2>
            </div>
            <div class="header-actions">
              <el-button v-if="selectedNode.type === 'scene'" @click="testScene(selectedNode)">
                <el-icon><View /></el-icon>
                测试
              </el-button>
              <el-button type="primary" @click="handleEdit(selectedNode)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
          </div>

          <!-- 类型标签 -->
          <div class="type-tags">
            <el-tag :type="typeColor(selectedNode.type)">
              {{ typeLabel(selectedNode.type) }}
            </el-tag>
            <el-tag :type="selectedNode.isActive ? 'success' : 'info'">
              {{ selectedNode.isActive ? '已启用' : '已禁用' }}
            </el-tag>
          </div>

          <!-- 基本信息 -->
          <el-card class="info-card">
            <template #header>
              <span class="card-title">基本信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="编码">{{ selectedNode.sceneCode }}</el-descriptions-item>
              <el-descriptions-item label="优先级">
                <el-badge :value="selectedNode.priority" :type="priorityType(selectedNode.priority)" />
              </el-descriptions-item>
              <el-descriptions-item label="描述">{{ selectedNode.description || '暂无描述' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 关键词 -->
          <el-card class="info-card">
            <template #header>
              <span class="card-title">关键词</span>
            </template>
            <div class="keywords-container">
              <el-tag
                v-for="(kw, idx) in selectedNode.keywords"
                :key="idx"
                size="large"
                class="keyword-tag"
              >
                {{ kw }}
              </el-tag>
              <span v-if="!selectedNode.keywords || selectedNode.keywords.length === 0" class="empty-tip">暂无关键词</span>
            </div>
          </el-card>

          <!-- 配置信息（仅场景） -->
          <el-card v-if="selectedNode.type === 'scene'" class="info-card">
            <template #header>
              <span class="card-title">配置信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="关联表单">
                {{ selectedNode.formCode || '未关联' }}
              </el-descriptions-item>
              <el-descriptions-item label="提示词文件">
                {{ selectedNode.actionPromptFile || '未配置' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 子节点列表 -->
          <div v-if="selectedNode.children && selectedNode.children.length > 0" class="children-section">
            <h3>子节点列表</h3>
            <el-table :data="selectedNode.children" style="width: 100%" size="small">
              <el-table-column prop="sceneName" label="名称" />
              <el-table-column prop="sceneCode" label="编码" width="150" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="typeColor(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
                    {{ row.isActive ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button size="small" link @click="handleNodeClick(row, null)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-content">
            <div class="empty-icon">
              <el-icon><Pointer /></el-icon>
            </div>
            <p class="empty-text">请从左侧选择一个节点查看详情</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingNode ? '编辑' : '新增' + ' ' + typeLabel(currentType)"
      width="600px"
      class="scene-dialog"
    >
      <el-form :model="formData" label-width="110px" ref="formRef">
        <el-form-item label="编码" prop="sceneCode" required>
          <el-input v-model="formData.sceneCode" :disabled="!!editingNode" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="名称" prop="sceneName" required>
          <el-input v-model="formData.sceneName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="关键词">
          <div class="keyword-input-wrapper">
            <el-tag
              v-for="(tag, index) in formData.keywords"
              :key="index"
              closable
              @close="removeKeyword(index)"
              style="margin-right: 8px"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="keywordInputVisible"
              ref="keywordInputRef"
              v-model="keywordInput"
              size="small"
              style="width: 200px"
              @keyup.enter="confirmKeyword"
              @blur="confirmKeyword"
            />
            <el-button v-else size="small" icon="Plus" @click="showKeywordInput" />
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-slider v-model="formData.priority" :min="1" :max="100" show-stops />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.isActive" />
        </el-form-item>
        <template v-if="currentType === 'scene'">
          <el-form-item label="关联表单">
            <el-select v-model="formData.formCode" placeholder="选择表单" clearable filterable style="width: 100%">
              <el-option
                v-for="form in forms"
                :key="form.formCode"
                :label="`${form.formName} (${form.formCode})`"
                :value="form.formCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="提示词文件">
            <el-select v-model="formData.actionPromptFile" placeholder="选择提示词文件" clearable filterable style="width: 100%">
              <el-option
                v-for="prompt in scenePrompts"
                :key="prompt"
                :label="prompt"
                :value="prompt"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 场景测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="场景识别测试" width="520px">
      <el-form label-width="90px">
        <el-form-item label="测试输入">
          <el-input
            v-model="testInput"
            type="textarea" :rows="4" placeholder="输入一段文本，测试场景识别效果..." />
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="test-result">
        <div class="result-header">
          <span class="result-title">识别结果</span>
          <el-tag v-if="testResult.bestMatch" type="success">匹配成功</el-tag>
          <el-tag v-else type="warning">未匹配</el-tag>
        </div>
        <div v-if="testResult.bestMatch" class="match-info">
          <div class="match-item">
            <span class="match-label">最佳匹配</span>
            <span class="match-value">{{ testResult.bestMatch.sceneName }}</span>
          </div>
          <div class="match-item">
            <span class="match-label">匹配关键词</span>
            <el-tag size="small">{{ testResult.bestMatch.matchedKeyword }}</el-tag>
          </div>
          <div v-if="testResult.matchedCenter" class="match-item">
            <span class="match-label">匹配中心域</span>
            <span class="match-value">{{ testResult.matchedCenter }}</span>
          </div>
          <div v-if="testResult.matchedBusiness" class="match-item">
            <span class="match-label">匹配业务域</span>
            <span class="match-value">{{ testResult.matchedBusiness }}</span>
          </div>
        </div>
        <div class="result-detail">
          <pre>{{ JSON.stringify(testResult, null, 2) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleTest" :loading="testing">
          开始测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, ArrowLeft, Pointer, Search } from '@element-plus/icons-vue'
import { listScenesTree, createScene, updateScene, deleteScene, toggleScene, testSceneRecognition, listScenePrompts } from '../services/sceneApi.js'
import { listForms } from '../services/formApi.js'

const emit = defineEmits(['go-back'])

const goBack = () => {
  emit('go-back')
}

const loading = ref(false)
const treeData = ref([])
const treeRef = ref(null)
const selectedNode = ref(null)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const editingNode = ref(null)
const saving = ref(false)
const testDialogVisible = ref(false)
const testInput = ref('')
const testResult = ref(null)
const testing = ref(false)
const keywordInputVisible = ref(false)
const keywordInput = ref('')
const keywordInputRef = ref(null)
const forms = ref([])
const scenePrompts = ref([])
const formRef = ref(null)

const currentType = computed(() => {
  return editingNode.value ? editingNode.value.type : 'scene'
})

const formData = reactive({
  sceneCode: '',
  sceneName: '',
  description: '',
  keywords: [],
  priority: 10,
  isActive: true,
  formCode: '',
  actionPromptFile: '',
  type: 'scene',
  parentId: null
})

const treeProps = {
  children: 'children',
  label: (data) => data.sceneName || data.label,
  isLeaf: (data) => !data.children || data.children.length === 0
}

// 类型相关
const typeLabel = (type) => {
  const labels = {
    'center': '中心域',
    'business': '业务域',
    'scene': '场景'
  }
  return labels[type] || type
}
const typeColor = (type) => {
  const colors = {
    'center': 'primary',
    'business': 'warning',
    'scene': 'success'
  }
  return colors[type] || ''
}
const priorityType = (priority) => {
  if (priority >= 80) return 'danger'
  if (priority >= 50) return 'warning'
  return 'info'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listScenesTree()
    if (res.success) {
      treeData.value = res.data || []
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadForms = async () => {
  try {
    const res = await listForms()
    if (res.success) {
      forms.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

const loadScenePrompts = async () => {
  try {
    const res = await listScenePrompts()
    if (res.success) {
      scenePrompts.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

// 节点点击
const handleNodeClick = (data, node) => {
  selectedNode.value = data
}

// 新增中心域
const handleAddCenter = () => {
  editingNode.value = null
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    formCode: '',
    actionPromptFile: '',
    type: 'center',
    parentId: null
  })
  Promise.all([loadForms(), loadScenePrompts()]).then(() => {
    dialogVisible.value = true
  })
}

// 新增业务域
const handleAddBusiness = (parentData) => {
  editingNode.value = null
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    formCode: '',
    actionPromptFile: '',
    type: 'business',
    parentId: parentData.id
  })
  Promise.all([loadForms(), loadScenePrompts()]).then(() => {
    dialogVisible.value = true
  })
}

// 新增场景
const handleAddScene = (parentData) => {
  editingNode.value = null
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    formCode: '',
    actionPromptFile: '',
    type: 'scene',
    parentId: parentData.id
  })
  Promise.all([loadForms(), loadScenePrompts()]).then(() => {
    dialogVisible.value = true
  })
}

// 编辑
const handleEdit = async (data) => {
  editingNode.value = data
  Object.assign(formData, {
    sceneCode: data.sceneCode,
    sceneName: data.sceneName,
    description: data.description,
    keywords: [...(data.keywords || [])],
    priority: data.priority,
    isActive: data.isActive,
    formCode: data.formCode,
    actionPromptFile: data.actionPromptFile,
    type: data.type,
    parentId: data.parentId
  })
  await Promise.all([loadForms(), loadScenePrompts()])
  dialogVisible.value = true
}

// 删除
const handleDelete = async (data) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${data.sceneName || data.label}」吗？\n这将同时删除所有子节点。`,
      '确认删除',
      { type: 'warning' }
    )
    const result = await deleteScene(data.sceneCode)
    if (result.success) {
      ElMessage.success('删除成功')
      await loadData()
      selectedNode.value = null
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 测试场景
const testScene = (data) => {
  testInput.value = data.keywords?.[0] || ''
  testResult.value = null
  testDialogVisible.value = true
}

const handleTest = async () => {
  if (!testInput.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const result = await testSceneRecognition(testInput.value)
    if (result.success) {
      testResult.value = result
    } else {
      ElMessage.error(result.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

// 关键词管理
const showKeywordInput = () => {
  keywordInputVisible.value = true
  nextTick(() => {
    keywordInputRef.value?.focus()
  })
}

const confirmKeyword = () => {
  if (keywordInput.value.trim()) {
    if (!formData.keywords.includes(keywordInput.value.trim())) {
      formData.keywords.push(keywordInput.value.trim())
    }
  }
  keywordInputVisible.value = false
  keywordInput.value = ''
}

const removeKeyword = (index) => {
  formData.keywords.splice(index, 1)
}

// 保存
const handleSave = async () => {
  if (!formData.sceneCode || !formData.sceneName) {
    ElMessage.warning('请填写编码和名称')
    return
  }

  saving.value = true
  try {
    let result
    if (editingNode.value) {
      result = await updateScene(formData.sceneCode, formData)
    } else {
      result = await createScene(formData)
    }

    if (result.success) {
      ElMessage.success(editingNode.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      await loadData()
      // 重新选中节点
      if (result.data && result.data.id) {
        // 找到新节点
        setTimeout(() => {
          const findNode = (nodes, id) => {
            for (const node of nodes) {
              if (node.id === id) return node
              if (node.children) {
                const found = findNode(node.children, id)
                if (found) return found
              }
            }
            return null
          }
          const newNode = findNode(treeData.value, result.data.id)
          if (newNode) {
            selectedNode.value = newNode
          }
        }, 100)
      }
    } else {
      ElMessage.error(result.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.scene-manager {
  height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  overflow: hidden;
}

.tree-section {
  width: 360px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
}

.tree-header {
  padding: 16px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h3 {
  margin: 0;
  font-size: 16px;
}

.tree-search {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.tree-container {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.scene-tree {
  font-size: 14px;
}

.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 4px 0;
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  font-size: 14px;
}

.scene-count {
  margin-left: 4px;
  font-size: 12px;
  color: #999;
}

.node-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.tree-node:hover .node-actions {
  opacity: 1;
}

.action-btn {
  padding: 4px 8px;
}

.detail-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow-y: auto;
  padding: 20px;
}

.scene-detail {
  height: 100%;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scene-icon {
  font-size: 28px;
}

.header-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.type-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.info-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.keywords-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.keyword-tag {
  padding: 6px 12px;
}

.empty-tip {
  color: #999;
  font-size: 14px;
}

.children-section {
  margin-top: 20px;
}

.children-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #333;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-content {
  text-align: center;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  margin-bottom: 20px;
}

.empty-btn {
  margin-top: 12px;
}

.scene-dialog :deep(.el-dialog__body) {
  padding: 10px 20px;
}

.keyword-input-wrapper {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.test-result {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
}

.result-title {
  font-weight: 600;
  font-size: 15px;
}

.match-info {
  margin-bottom: 16px;
}

.match-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.match-label {
  min-width: 90px;
  color: #666;
}

.match-value {
  font-weight: 500;
  color: #333;
}

.result-detail {
  margin-top: 12px;
}

.result-detail pre {
  margin: 0;
  background: white;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}
</style>

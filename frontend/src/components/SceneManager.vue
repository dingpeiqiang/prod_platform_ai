<template>
  <div class="scene-manager">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回首页
      </el-button>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建场景
      </el-button>
      <el-button @click="refresh">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
      <div class="filters">
        <el-switch v-model="showOnlyActive" active-text="仅显示启用" @change="refresh" />
      </div>
    </div>

    <!-- 场景列表 -->
    <el-card class="scene-list-card">
      <el-table
        :data="scenes"
        v-loading="loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="sceneCode" label="场景编码" width="180" />
        <el-table-column prop="sceneName" label="场景名称" width="180" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="关键词" width="200">
          <template #default="{ row }">
            <el-tag v-for="kw in (row.keywords || []).slice(0, 3)" :key="kw" size="small" style="margin-right: 4px; margin-bottom: 4px;">
              {{ kw }}
            </el-tag>
            <span v-if="(row.keywords || []).length > 3" class="more-tags">
              +{{ (row.keywords || []).length - 3 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="动作类型" width="140">
          <template #default="{ row }">
            <el-tag :type="row.actionType === 'form_with_mcp' ? 'primary' : 'info'" size="small">
              {{ row.actionType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.isActive"
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" @click="testScene(row)">测试</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingScene ? '编辑场景' : '新建场景'"
      width="800px"
    >
      <el-form :model="formData" label-width="120px">
        <el-form-item label="场景编码" required>
          <el-input v-model="formData.sceneCode" :disabled="!!editingScene" />
        </el-form-item>
        <el-form-item label="场景名称" required>
          <el-input v-model="formData.sceneName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-tag
            v-for="tag in formData.keywords"
            :key="tag"
            closable
            style="margin-right: 8px; margin-bottom: 8px;"
            @close="handleKeywordRemove(tag)"
          >
            {{ tag }}
          </el-tag>
          <el-input
            v-if="keywordInputVisible"
            ref="keywordInputRef"
            v-model="keywordInput"
            class="keyword-input"
            size="small"
            @keyup.enter="handleKeywordConfirm"
            @blur="handleKeywordConfirm"
          />
          <el-button v-else size="small" @click="showKeywordInput">
            <el-icon><Plus /></el-icon>
            添加关键词
          </el-button>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="formData.priority" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="formData.isActive" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="意图类型">
              <el-input v-model="formData.intentType" placeholder="如: form, tariff_filing" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联表单">
              <el-input v-model="formData.formCode" placeholder="表单编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="动作类型">
          <el-select v-model="formData.actionType">
            <el-option label="标准表单生成" value="form_generation" />
            <el-option label="带MCP工具调用" value="form_with_mcp" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词文件">
          <el-input v-model="formData.actionPromptFile" placeholder="如: tariff_filing_prompt.txt" />
        </el-form-item>
        <el-form-item label="必需工具">
          <el-input v-model="requiredToolsText" placeholder="工具列表，用逗号分隔" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 场景识别测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="场景识别测试" width="500px">
      <el-form label-width="100px">
        <el-form-item label="测试输入">
          <el-input
            v-model="testInput"
            type="textarea"
            :rows="3"
            placeholder="输入要测试的文本..."
          />
        </el-form-item>
      </el-form>

      <div v-if="testResult" class="test-result">
        <h4>识别结果:</h4>
        <pre>{{ JSON.stringify(testResult, null, 2) }}</pre>
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
import { Plus, Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { listScenes, createScene, updateScene, deleteScene, toggleScene, testSceneRecognition } from '../services/sceneApi.js'

const emit = defineEmits(['go-back'])

const goBack = () => {
  emit('go-back')
}

const loading = ref(false)
const scenes = ref([])
const showOnlyActive = ref(false)
const dialogVisible = ref(false)
const testDialogVisible = ref(false)
const editingScene = ref(null)
const saving = ref(false)
const testing = ref(false)
const testInput = ref('')
const testResult = ref(null)
const keywordInputVisible = ref(false)
const keywordInput = ref('')
const keywordInputRef = ref(null)

const formData = reactive({
  sceneCode: '',
  sceneName: '',
  description: '',
  keywords: [],
  priority: 10,
  isActive: true,
  intentType: '',
  formCode: '',
  actionType: 'form_generation',
  actionPromptFile: '',
  requiredTools: [],
  availableTools: [],
  preActionSteps: [],
  postActionSteps: []
})

const requiredToolsText = computed({
  get: () => (formData.requiredTools || []).join(', '),
  set: (val) => {
    formData.requiredTools = val.split(/[,，]/).map(s => s.trim()).filter(s => s)
  }
})

async function refresh() {
  loading.value = true
  try {
    const result = await listScenes(showOnlyActive.value ? true : undefined)
    if (result.success) {
      scenes.value = result.data || []
    } else {
      ElMessage.error(result.message || '获取场景列表失败')
    }
  } catch (e) {
    ElMessage.error('获取场景列表失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingScene.value = null
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    intentType: '',
    formCode: '',
    actionType: 'form_generation',
    actionPromptFile: '',
    requiredTools: [],
    availableTools: [],
    preActionSteps: [],
    postActionSteps: []
  })
  dialogVisible.value = true
}

function openEditDialog(scene) {
  editingScene.value = scene
  Object.assign(formData, {
    sceneCode: scene.sceneCode,
    sceneName: scene.sceneName,
    description: scene.description,
    keywords: [...(scene.keywords || [])],
    priority: scene.priority,
    isActive: scene.isActive,
    intentType: scene.intentType,
    formCode: scene.formCode,
    actionType: scene.actionType,
    actionPromptFile: scene.actionPromptFile,
    requiredTools: [...(scene.requiredTools || [])],
    availableTools: [...(scene.availableTools || [])],
    preActionSteps: [...(scene.preActionSteps || [])],
    postActionSteps: [...(scene.postActionSteps || [])]
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.sceneCode.trim()) {
    ElMessage.warning('请输入场景编码')
    return
  }
  if (!formData.sceneName.trim()) {
    ElMessage.warning('请输入场景名称')
    return
  }

  saving.value = true
  try {
    let result
    if (editingScene.value) {
      result = await updateScene(formData.sceneCode, formData)
    } else {
      result = await createScene(formData)
    }

    if (result.success) {
      ElMessage.success(editingScene.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      refresh()
    } else {
      ElMessage.error(result.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

async function handleToggle(scene) {
  try {
    const result = await toggleScene(scene.sceneCode)
    if (result.success) {
      ElMessage.success('状态已切换')
      refresh()
    } else {
      ElMessage.error(result.message || '切换失败')
    }
  } catch (e) {
    ElMessage.error('切换失败: ' + e.message)
    scene.isActive = !scene.isActive
  }
}

async function handleDelete(scene) {
  try {
    await ElMessageBox.confirm(
      `确定要删除场景「${scene.sceneName}」吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const result = await deleteScene(scene.sceneCode)
    if (result.success) {
      ElMessage.success('删除成功')
      refresh()
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

function testScene(scene) {
  testInput.value = scene.keywords?.[0] || ''
  testResult.value = null
  testDialogVisible.value = true
}

async function handleTest() {
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
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    testing.value = false
  }
}

// 关键词管理
function showKeywordInput() {
  keywordInputVisible.value = true
  keywordInput.value = ''
  nextTick(() => {
    keywordInputRef.value?.focus()
  })
}

function handleKeywordConfirm() {
  if (keywordInput.value.trim() && !formData.keywords.includes(keywordInput.value.trim())) {
    formData.keywords.push(keywordInput.value.trim())
  }
  keywordInputVisible.value = false
  keywordInput.value = ''
}

function handleKeywordRemove(tag) {
  const idx = formData.keywords.indexOf(tag)
  if (idx > -1) {
    formData.keywords.splice(idx, 1)
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.scene-manager {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.filters {
  margin-left: auto;
}

.scene-list-card {
  margin-bottom: 20px;
}

.more-tags {
  font-size: 12px;
  color: #909399;
}

.keyword-input {
  width: 200px;
}

.test-result {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  margin-top: 16px;
  max-height: 300px;
  overflow: auto;
}

.test-result pre {
  margin: 0;
  font-size: 12px;
}
</style>

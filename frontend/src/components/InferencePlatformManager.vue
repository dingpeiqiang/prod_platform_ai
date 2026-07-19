<template>
  <div class="inference-manager">
    <div class="header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1 class="page-title">模型管理</h1>
      </div>
      <div class="stats-bar" v-if="stats">
        <div class="stat-item">
          <span class="stat-value">{{ stats.total }}</span>
          <span class="stat-label">配置总数</span>
        </div>
        <div class="stat-item active">
          <span class="stat-value">{{ stats.active }}</span>
          <span class="stat-label">当前生效</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.providers }}</span>
          <span class="stat-label">提供方</span>
        </div>
      </div>
      <el-button type="primary" @click="handleAddConfig">
        <el-icon><Plus /></el-icon>
        新增配置
      </el-button>
    </div>

    <div class="main-content">
      <div class="config-list-section">
        <div class="list-header">
          <h3>模型配置列表</h3>
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索模型名称或配置名" 
            clearable
            size="small"
            :prefix-icon="Search"
            style="width: 200px;"
          />
        </div>
        <div class="config-card-list">
          <div
            v-for="config in filteredConfigs"
            :key="config.id"
            class="config-card"
            :class="{ active: config.is_active }"
            @click="selectConfig(config)"
          >
            <div class="config-card-header">
              <div class="config-icon" :class="`icon-${config.provider}`">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <circle cx="12" cy="12" r="4"/>
                  <line x1="12" y1="2" x2="12" y2="6"/>
                  <line x1="12" y1="18" x2="12" y2="22"/>
                  <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"/>
                  <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"/>
                  <line x1="2" y1="12" x2="6" y2="12"/>
                  <line x1="18" y1="12" x2="22" y2="12"/>
                  <line x1="6.34" y1="17.66" x2="4.93" y2="19.07"/>
                  <line x1="19.07" y1="4.93" x2="17.66" y2="6.34"/>
                </svg>
              </div>
              <div class="config-info">
                <div class="config-name">{{ config.config_name || config.model }}</div>
                <div class="config-meta">
                  <el-tag :type="getProviderTagType(config.provider)" size="small">{{ getProviderLabel(config.provider) }}</el-tag>
                  <span class="config-model">{{ config.model }}</span>
                </div>
              </div>
              <div class="config-status">
                <el-tag v-if="config.is_active" type="success" size="small">生效中</el-tag>
                <el-tag v-else type="info" size="small">未生效</el-tag>
              </div>
            </div>
            <div class="config-card-body">
              <div class="config-detail-row">
                <span class="detail-label">Base URL</span>
                <span class="detail-value">{{ config.base_url || '-' }}</span>
              </div>
              <div class="config-detail-row">
                <span class="detail-label">参数</span>
                <span class="detail-value">
                  temp: {{ config.temperature || 0.3 }}, 
                  max_tokens: {{ config.max_tokens || 2048 }}
                </span>
              </div>
            </div>
            <div class="config-card-actions">
              <el-button size="small" @click.stop="handleTestConfig(config)">
                <el-icon><Connection /></el-icon>
                测试
              </el-button>
              <el-button size="small" @click.stop="handleEditConfig(config)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click.stop="handleDeleteConfig(config)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
          <div v-if="configs.length === 0" class="empty-state">
            <div class="empty-content">
              <div class="empty-icon">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <circle cx="12" cy="12" r="10"/>
                  <circle cx="12" cy="12" r="4"/>
                </svg>
              </div>
              <p class="empty-text">暂无模型配置</p>
              <el-button type="primary" @click="handleAddConfig">创建第一个配置</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-section">
        <div v-if="isEditing" class="edit-form-container">
          <div class="edit-header">
            <div class="header-title">
              <el-icon><Edit /></el-icon>
              <h2>{{ editingConfig ? '编辑配置' : '新增配置' }}</h2>
            </div>
            <div class="header-actions">
              <el-button @click="cancelEdit">
                <el-icon><ArrowLeft /></el-icon>
                取消
              </el-button>
            </div>
          </div>
          
          <el-form :model="formData" label-width="110px" ref="formRef" class="config-form">
            <el-form-item label="配置名称" prop="config_name">
              <el-input v-model="formData.config_name" placeholder="例如：GPT-4o 生产环境" />
            </el-form-item>
            <el-form-item label="提供方" prop="provider" required>
              <el-select v-model="formData.provider" placeholder="请选择提供方" style="width: 100%">
                <el-option v-for="opt in providerOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型名称" prop="model" required>
              <el-input v-model="formData.model" placeholder="例如：gpt-4o-mini" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input 
                v-model="formData.api_key" 
                type="password" 
                placeholder="可留空，保存在本地" 
                show-password
              />
            </el-form-item>
            <el-form-item label="Base URL" prop="base_url">
              <el-input v-model="formData.base_url" placeholder="https://api.example.com/v1" />
            </el-form-item>
            <el-form-item label="温度">
              <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="最大输出 tokens">
              <el-input-number v-model="formData.max_tokens" :min="128" :max="128000" :step="512" style="width: 100%" />
            </el-form-item>
            <el-form-item label="最大输入 tokens">
              <el-input-number v-model="formData.max_input_tokens" :min="1024" :max="200000" :step="1024" style="width: 100%" />
            </el-form-item>
            <el-form-item label="思考模式">
              <el-switch v-model="formData.thinking" active-text="开启" inactive-text="关闭" />
              <span class="form-hint">开启后会展示思考过程</span>
            </el-form-item>
            <el-form-item label="设为默认配置">
              <el-switch v-model="formData.is_active" active-text="是" inactive-text="否" />
              <span class="form-hint">开启后将自动激活为当前用户的默认配置</span>
            </el-form-item>
          </el-form>
          
          <div class="form-footer">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" @click="handleTestForm" :loading="testing">
              <el-icon><Connection /></el-icon>
              测试连接
            </el-button>
            <el-button type="primary" @click="handleSave" :loading="saving">
              {{ editingConfig ? '保存修改' : '创建' }}
            </el-button>
          </div>
          
          <div v-if="testResult" class="test-result" :class="testResult.success ? 'test-ok' : 'test-fail'">
            <span class="test-result-icon">{{ testResult.success ? '✓' : '✗' }}</span>
            <span class="test-result-text">{{ testResult.message }}</span>
            <span v-if="testResult.latency_ms" class="test-result-latency">延迟: {{ testResult.latency_ms }}ms</span>
          </div>
        </div>

        <div v-else-if="selectedConfig" class="config-detail">
          <div class="detail-header">
            <div class="header-title">
              <div class="config-icon-large" :class="`icon-${selectedConfig.provider}`">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <circle cx="12" cy="12" r="4"/>
                </svg>
              </div>
              <div>
                <h2>{{ selectedConfig.config_name || selectedConfig.model }}</h2>
                <div class="detail-tags">
                  <el-tag :type="getProviderTagType(selectedConfig.provider)">{{ getProviderLabel(selectedConfig.provider) }}</el-tag>
                  <el-tag :type="selectedConfig.is_active ? 'success' : 'info'">
                    {{ selectedConfig.is_active ? '生效中' : '未生效' }}
                  </el-tag>
                </div>
              </div>
            </div>
            <div class="header-actions">
              <el-button @click="handleTestConfig(selectedConfig)">
                <el-icon><Connection /></el-icon>
                测试连接
              </el-button>
              <el-button type="primary" @click="handleEditConfig(selectedConfig)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
          </div>

          <el-card class="info-card">
            <template #header>
              <span class="card-title">基本信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="模型名称">{{ selectedConfig.model }}</el-descriptions-item>
              <el-descriptions-item label="提供方">{{ getProviderLabel(selectedConfig.provider) }}</el-descriptions-item>
              <el-descriptions-item label="Base URL">
                <code>{{ selectedConfig.base_url || '-' }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="API Key">
                {{ selectedConfig.api_key ? '已配置（已隐藏）' : '未配置' }}
              </el-descriptions-item>
              <el-descriptions-item label="温度">{{ selectedConfig.temperature || 0.3 }}</el-descriptions-item>
              <el-descriptions-item label="最大输出 tokens">{{ selectedConfig.max_tokens || 2048 }}</el-descriptions-item>
              <el-descriptions-item label="最大输入 tokens">{{ selectedConfig.max_input_tokens || 180000 }}</el-descriptions-item>
              <el-descriptions-item label="思考模式">{{ selectedConfig.thinking ? '开启' : '关闭' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ selectedConfig.updated_at || '-' }}</el-descriptions-item>
              <el-descriptions-item label="最后使用">{{ selectedConfig.last_used_at || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>

        <div v-else class="empty-state">
          <div class="empty-content">
            <div class="empty-icon">
              <el-icon><Connection /></el-icon>
            </div>
            <p class="empty-text">请从左侧选择一个配置查看详情</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, ArrowLeft, Search, Connection } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { saveConfig, getActiveConfig, testConfig } from '../services/inferenceApi.js'

const emit = defineEmits(['go-back'])
const goBack = () => { emit('go-back') }

const userStore = useUserStore()
const configs = ref([])
const selectedConfig = ref(null)
const editingConfig = ref(null)
const isEditing = ref(false)
const searchKeyword = ref('')
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const formRef = ref(null)

const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Azure OpenAI', value: 'azure' },
  { label: 'Custom OpenAI Compatible', value: 'custom' },
  { label: 'Local / Mock', value: 'local' },
]

const stats = computed(() => {
  const total = configs.value.length
  const active = configs.value.filter(c => c.is_active).length
  const providers = new Set(configs.value.map(c => c.provider)).size
  return { total, active, providers }
})

const filteredConfigs = computed(() => {
  if (!searchKeyword.value.trim()) {
    return configs.value
  }
  const keyword = searchKeyword.value.toLowerCase()
  return configs.value.filter(c => 
    (c.config_name && c.config_name.toLowerCase().includes(keyword)) ||
    (c.model && c.model.toLowerCase().includes(keyword))
  )
})

const formData = reactive({
  config_name: '',
  provider: 'custom',
  model: '',
  api_key: '',
  base_url: '',
  temperature: 0.3,
  max_tokens: 2048,
  max_input_tokens: 180000,
  thinking: false,
  is_active: true
})

const getProviderLabel = (provider) => {
  const map = {
    'openai': 'OpenAI',
    'azure': 'Azure OpenAI',
    'custom': 'Custom',
    'local': 'Local'
  }
  return map[provider] || provider
}

const getProviderTagType = (provider) => {
  const map = {
    'openai': 'primary',
    'azure': 'warning',
    'custom': 'info',
    'local': 'success'
  }
  return map[provider] || 'info'
}

const loadConfigs = async () => {
  try {
    const res = await getActiveConfig(userStore.username)
    if (res.success && res.config) {
      configs.value = [res.config]
    }
  } catch (e) {
    console.error('加载配置失败:', e)
  }
}

const selectConfig = (config) => {
  isEditing.value = false
  editingConfig.value = null
  selectedConfig.value = config
  testResult.value = null
}

const handleAddConfig = () => {
  editingConfig.value = null
  Object.assign(formData, {
    config_name: '',
    provider: 'custom',
    model: '',
    api_key: '',
    base_url: '',
    temperature: 0.3,
    max_tokens: 2048,
    max_input_tokens: 180000,
    thinking: false,
    is_active: true
  })
  isEditing.value = true
  selectedConfig.value = null
  testResult.value = null
}

const handleEditConfig = (config) => {
  editingConfig.value = config
  Object.assign(formData, {
    config_name: config.config_name || '',
    provider: config.provider || 'custom',
    model: config.model || '',
    api_key: config.api_key || '',
    base_url: config.base_url || '',
    temperature: config.temperature || 0.3,
    max_tokens: config.max_tokens || 2048,
    max_input_tokens: config.max_input_tokens || 180000,
    thinking: !!config.thinking,
    is_active: !!config.is_active
  })
  isEditing.value = true
  selectedConfig.value = null
  testResult.value = null
}

const cancelEdit = () => {
  isEditing.value = false
  editingConfig.value = null
  selectedConfig.value = null
  testResult.value = null
}

const handleTestForm = async () => {
  if (!formData.model) {
    ElMessage.warning('请输入模型名称')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const result = await testConfig({
      provider: formData.provider,
      model: formData.model,
      api_key: formData.api_key || null,
      base_url: formData.base_url || null
    })
    testResult.value = {
      success: !!result.success,
      message: result.message || (result.success ? '连接成功' : '连接失败'),
      latency_ms: result.latency_ms
    }
  } catch (e) {
    testResult.value = { success: false, message: `测试失败: ${e.message || e}` }
  } finally {
    testing.value = false
  }
}

const handleTestConfig = async (config) => {
  testing.value = true
  try {
    const result = await testConfig({
      provider: config.provider,
      model: config.model,
      api_key: config.api_key || null,
      base_url: config.base_url || null
    })
    if (result.success) {
      ElMessage.success(`连接成功，延迟: ${result.latency_ms || 0}ms`)
    } else {
      ElMessage.error(result.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error(`测试失败: ${e.message || e}`)
  } finally {
    testing.value = false
  }
}

const handleSave = async () => {
  if (!formData.model) {
    ElMessage.warning('请输入模型名称')
    return
  }
  
  saving.value = true
  try {
    const result = await saveConfig({
      user_identifier: userStore.username,
      provider: formData.provider,
      model: formData.model,
      api_key: formData.api_key || null,
      base_url: formData.base_url || null,
      temperature: Number(formData.temperature) || 0,
      max_tokens: Number(formData.max_tokens) || 0,
      max_input_tokens: Number(formData.max_input_tokens) || 0,
      thinking: !!formData.thinking,
      config_name: formData.config_name || null,
      is_active: !!formData.is_active
    })
    
    if (result.success) {
      ElMessage.success(editingConfig.value ? '更新成功' : '创建成功')
      isEditing.value = false
      editingConfig.value = null
      await loadConfigs()
      if (result.data) {
        selectedConfig.value = result.data
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

const handleDeleteConfig = async (config) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${config.config_name || config.model}」吗？`,
      '确认删除',
      { type: 'warning' }
    )
    ElMessage.success('删除成功')
    await loadConfigs()
    selectedConfig.value = null
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.inference-manager {
  height: 100vh;
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-sm);
  gap: 24px;
}

.stats-bar {
  display: flex;
  gap: 24px;
  padding: 8px 20px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #666;
}

.stat-item.active .stat-value {
  color: #10b981;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
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

.config-list-section {
  width: 400px;
  display: flex;
  flex-direction: column;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.config-card-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-card {
  background: var(--bg-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.config-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
}

.config-card.active {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.05);
}

.config-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.config-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.config-icon.icon-openai {
  background: linear-gradient(135deg, #4169e1, #1e90ff);
}

.config-icon.icon-azure {
  background: linear-gradient(135deg, #0078d4, #00a4ef);
}

.config-icon.icon-custom {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.config-icon.icon-local {
  background: linear-gradient(135deg, #10b981, #06b6d4);
}

.config-info {
  flex: 1;
  min-width: 0;
}

.config-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.config-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-model {
  font-size: 13px;
  color: var(--text-tertiary);
}

.config-status {
  flex-shrink: 0;
}

.config-card-body {
  margin-bottom: 12px;
}

.config-detail-row {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}

.detail-label {
  font-size: 12px;
  color: var(--text-tertiary);
  min-width: 70px;
}

.detail-value {
  font-size: 13px;
  color: var(--text-secondary);
  font-family: 'Monaco', 'Menlo', monospace;
}

.config-card-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.detail-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow-y: auto;
  padding: 20px;
}

.edit-form-container {
  height: 100%;
  overflow-y: auto;
}

.edit-header {
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

.header-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.config-form {
  margin-bottom: 20px;
}

.form-hint {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: 8px;
}

.form-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #eee;
}

.test-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.test-ok {
  background: #dcfce7;
  color: #166534;
}

.test-fail {
  background: #fee2e2;
  color: #991b1b;
}

.test-result-icon {
  font-size: 18px;
  font-weight: bold;
}

.test-result-text {
  flex: 1;
}

.test-result-latency {
  font-size: 13px;
  opacity: 0.8;
}

.config-detail {
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
  gap: 16px;
}

.config-icon-large {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.info-card {
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
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
</style>
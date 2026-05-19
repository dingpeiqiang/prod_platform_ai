<template>
  <div class="model-selector">
    <div class="selector-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <circle cx="12" cy="12" r="4"/>
        <circle cx="12" cy="12" r="1"/>
      </svg>
      <span class="selector-title">模型配置</span>
    </div>

    <!-- Provider 选择 -->
    <div class="selector-group">
      <label class="field-label">Provider 类型</label>
      <select v-model="provider" class="provider-select">
        <option value="custom">自定义 (OpenAI 兼容)</option>
        <option value="openai">OpenAI</option>
        <option value="minimax">MiniMax</option>
      </select>
    </div>

    <!-- 模型名称 -->
    <div class="selector-group">
      <label class="field-label">模型名称</label>
      <input
        v-model="modelName"
        type="text"
        placeholder="例如: gpt-4, qwen-plus"
        class="model-name-input"
      />
    </div>

    <!-- API Key -->
    <div class="selector-group">
      <label class="field-label">API Key</label>
      <div class="input-with-toggle">
        <input
          v-model="apiKey"
          :type="showApiKey ? 'text' : 'password'"
          placeholder="sk-..."
          class="api-key-input"
        />
        <button 
          @click="showApiKey = !showApiKey" 
          class="toggle-visibility-btn"
          type="button"
          title="显示/隐藏"
        >
          <svg v-if="!showApiKey" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
            <line x1="1" y1="1" x2="23" y2="23"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Base URL -->
    <div class="selector-group">
      <label class="field-label">Base URL</label>
      <input
        v-model="baseUrl"
        type="text"
        placeholder="https://api.openai.com/v1"
        class="base-url-input"
      />
      <div class="field-hint">OpenAI 兼容 API 的基础 URL</div>
    </div>

    <!-- 高级配置折叠面板 -->
    <div class="advanced-config">
      <div class="advanced-header" @click="showAdvanced = !showAdvanced">
        <svg 
          width="12" 
          height="12" 
          viewBox="0 0 24 24" 
          fill="none" 
          stroke="currentColor" 
          stroke-width="2"
          :class="{ 'rotated': showAdvanced }"
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
        <span>高级配置</span>
      </div>
      
      <div v-if="showAdvanced" class="advanced-content">
        <!-- Temperature -->
        <div class="config-item">
          <label class="field-label">Temperature</label>
          <input
            v-model.number="temperature"
            type="number"
            min="0"
            max="2"
            step="0.1"
            class="number-input"
          />
          <div class="field-hint">控制随机性 (0-2)，默认 0.3</div>
        </div>

        <!-- Max Tokens -->
        <div class="config-item">
          <label class="field-label">Max Tokens</label>
          <input
            v-model.number="maxTokens"
            type="number"
            min="1"
            max="32768"
            class="number-input"
          />
          <div class="field-hint">最大输出 token 数，默认 2048</div>
        </div>

        <!-- Thinking Mode -->
        <div class="config-item">
          <label class="checkbox-label">
            <input v-model="thinking" type="checkbox" class="checkbox-input" />
            <span>启用思考模式</span>
          </label>
          <div class="field-hint">显示模型的推理过程（如果支持）</div>
        </div>
      </div>
    </div>

    <!-- 按钮组 -->
    <div class="button-group">
      <button
        @click="testModel"
        :disabled="!modelName || testing"
        class="test-btn"
      >
        <svg v-if="!testing" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          <polygon points="9.75 7.75 9.75 12.5 14.5 14.5"/>
        </svg>
        <span>{{ testing ? '测试中...' : '测试连接' }}</span>
      </button>
      <button
        @click="applyModel"
        :disabled="!modelName || applying"
        class="apply-btn"
      >
        {{ applying ? '应用中...' : '应用配置' }}
      </button>
    </div>

    <!-- 状态消息 -->
    <div v-if="statusMessage" class="status-message" :class="statusType">
      <pre v-if="statusType === 'success' && resultPreview">{{ resultPreview }}</pre>
      <div>{{ statusMessage }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const emit = defineEmits(['modelChange'])

// 基础配置
const provider = ref('custom')
const modelName = ref('')
const apiKey = ref('')
const baseUrl = ref('')
const showApiKey = ref(false)

// 高级配置
const showAdvanced = ref(false)
const temperature = ref(0.3)
const maxTokens = ref(2048)
const thinking = ref(false)

// 状态
const applying = ref(false)
const testing = ref(false)
const statusMessage = ref('')
const statusType = ref('')
const resultPreview = ref('')

const createModelConfig = () => {
  const config = {
    provider: provider.value,
    model: modelName.value.trim()
  }

  if (apiKey.value.trim()) {
    config.api_key = apiKey.value.trim()  // 使用下划线命名，与后端保持一致
  }
  if (baseUrl.value.trim()) {
    config.base_url = baseUrl.value.trim()  // 使用下划线命名，与后端保持一致
  }
  
  // 添加高级配置
  config.temperature = temperature.value
  config.max_tokens = maxTokens.value  // 使用下划线命名
  config.thinking = thinking.value

  return config
}

const saveConfigToStorage = (config) => {
  localStorage.setItem('chat_model_config', JSON.stringify(config))
}

const testModel = async () => {
  if (!modelName.value.trim()) {
    showStatus('请输入模型名称', 'error')
    return
  }

  testing.value = true
  showStatus('', '')
  resultPreview.value = ''

  try {
    const modelConfig = createModelConfig()
    
    // 点击测试时立即保存配置
    saveConfigToStorage(modelConfig)

    const response = await fetch('/api/v1/chat/model/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(modelConfig)
    })

    const result = await response.json()

    if (result.success) {
      let successMsg = '✓ 模型连接测试成功'
      if (result.response_preview) {
        resultPreview.value = result.response_preview
      }
      showStatus(successMsg, 'success')
    } else {
      // 显示详细错误信息
      let errorMsg = '✗ ' + (result.message || '测试失败')
      
      // 添加建议信息
      if (result.suggestion) {
        errorMsg += `\n💡 建议: ${result.suggestion}`
      }
      
      // 添加详细信息
      if (result.detail && result.detail !== result.message) {
        errorMsg += `\n\n详细信息: ${result.detail}`
      }
      
      showStatus(errorMsg, 'error')
    }
  } catch (error) {
    console.error('模型测试请求失败:', error)
    showStatus('✗ 请求失败，请稍后重试', 'error')
  } finally {
    testing.value = false
  }
}

const applyModel = async () => {
  if (!modelName.value.trim()) {
    showStatus('请输入模型名称', 'error')
    return
  }

  applying.value = true
  showStatus('', '')
  resultPreview.value = ''

  try {
    const modelConfig = createModelConfig()

    // 生成用户标识（使用 session_id 或随机 ID）
    let userId = localStorage.getItem('user_id')
    if (!userId) {
      userId = 'user-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
      localStorage.setItem('user_id', userId)
    }

    // 1. 保存到数据库
    const saveResponse = await fetch('/api/v1/llm-config/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        user_identifier: userId,
        ...modelConfig
      })
    })

    const saveResult = await saveResponse.json()

    if (!saveResult.success) {
      throw new Error(saveResult.message || '保存配置失败')
    }

    // 2. 保存到 localStorage（作为缓存）
    saveConfigToStorage(modelConfig)

    // 3. 通知父组件
    emit('modelChange', modelConfig)

    showStatus('✓ 模型配置已保存到数据库', 'success')
    setTimeout(() => {
      statusMessage.value = ''
    }, 2000)
  } catch (error) {
    console.error('保存配置失败:', error)
    showStatus('✗ ' + (error.message || '保存失败，请稍后重试'), 'error')
  } finally {
    applying.value = false
  }
}

const showStatus = (message, type) => {
  statusMessage.value = message
  statusType.value = type
}

const loadDefaultConfig = async () => {
  try {
    // 获取用户标识
    let userId = localStorage.getItem('user_id')
    if (!userId) {
      userId = 'user-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
      localStorage.setItem('user_id', userId)
    }

    // 1. 优先从数据库获取激活配置
    try {
      const dbResponse = await fetch(`/api/v1/llm-config/active/${userId}`)
      const dbResult = await dbResponse.json()

      if (dbResult.success && dbResult.config) {
        const config = dbResult.config
        provider.value = config.provider || 'custom'
        modelName.value = config.model || ''
        // 不加载 API Key（安全考虑）
        apiKey.value = ''
        baseUrl.value = config.base_url || ''
        temperature.value = config.temperature ?? 0.3
        maxTokens.value = config.max_tokens ?? 2048
        thinking.value = config.thinking ?? false
        
        console.log('✓ 从数据库加载配置成功')
        return
      }
    } catch (dbError) {
      console.warn('从数据库加载配置失败，尝试从 localStorage 加载:', dbError)
    }

    // 2. 如果数据库没有配置，从 localStorage 获取上次保存的配置
    const savedConfig = localStorage.getItem('chat_model_config')
    if (savedConfig) {
      const config = JSON.parse(savedConfig)
      provider.value = config.provider || 'custom'
      modelName.value = config.model || ''
      // 兼容新旧命名方式（apiKey/api_key, baseUrl/base_url）
      apiKey.value = config.apiKey || config.api_key || ''
      baseUrl.value = config.baseUrl || config.base_url || ''
      temperature.value = config.temperature ?? 0.3
      maxTokens.value = (config.maxTokens || config.max_tokens) ?? 2048
      thinking.value = config.thinking ?? false
      console.log('✓ 从 localStorage 加载配置')
      return
    }

    // 3. 如果没有保存的配置，从后端获取系统默认配置
    const response = await fetch('/api/v1/chat/model/default')
    const result = await response.json()

    if (result.success && result.config) {
      provider.value = result.config.provider || 'custom'
      modelName.value = result.config.model || ''
      // 不加载 API Key（安全考虑）
      baseUrl.value = result.config.baseUrl || result.config.base_url || ''
      temperature.value = result.config.temperature ?? 0.3
      maxTokens.value = (result.config.maxTokens || result.config.max_tokens) ?? 2048
      thinking.value = result.config.thinking ?? false
      console.log('✓ 从系统默认配置加载')
    }
  } catch (error) {
    console.error('加载默认配置失败:', error)
  }
}

onMounted(() => {
  loadDefaultConfig()
})
</script>

<style scoped>
.model-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.selector-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.selector-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  margin-bottom: 4px;
}

.provider-select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  font-size: 13px;
  background: var(--bg-input);
  color: var(--text-primary);
  transition: border-color var(--transition-fast);
  cursor: pointer;
}

.provider-select:focus {
  outline: none;
  border-color: var(--color-primary-500);
}

.input-with-toggle {
  position: relative;
  display: flex;
  align-items: center;
}

.toggle-visibility-btn {
  position: absolute;
  right: 8px;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--text-tertiary);
  transition: color var(--transition-fast);
}

.toggle-visibility-btn:hover {
  color: var(--text-primary);
}

.field-hint {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

/* 高级配置 */
.advanced-config {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.advanced-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  background: var(--bg-secondary);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.advanced-header:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.advanced-header svg {
  transition: transform var(--transition-fast);
}

.advanced-header svg.rotated {
  transform: rotate(180deg);
}

.advanced-content {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg-input);
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.number-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: white;
  color: var(--text-primary);
}

.number-input:focus {
  outline: none;
  border-color: var(--color-primary-500);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.model-name-input,
.api-key-input,
.base-url-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  font-size: 13px;
  background: var(--bg-input);
  color: var(--text-primary);
  transition: border-color var(--transition-fast);
}

.model-name-input:focus,
.api-key-input:focus,
.base-url-input:focus {
  outline: none;
  border-color: var(--color-primary-500);
}

.model-name-input::placeholder,
.api-key-input::placeholder,
.base-url-input::placeholder {
  color: var(--text-tertiary);
}

.button-group {
  display: flex;
  gap: 8px;
}

.test-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.test-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: var(--border-hover);
}

.test-btn:disabled {
  background: var(--bg-disabled);
  color: var(--text-disabled);
  cursor: not-allowed;
}

.apply-btn {
  flex: 1;
  padding: 10px;
  background: var(--color-primary-500);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.apply-btn:hover:not(:disabled) {
  background: var(--color-primary-600);
}

.apply-btn:disabled {
  background: var(--bg-disabled);
  color: var(--text-disabled);
  cursor: not-allowed;
}

.status-message {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  text-align: center;
}

.status-message pre {
  margin: 0 0 8px 0;
  padding: 8px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}

.status-message.success {
  background: rgba(34, 197, 94, 0.1);
  color: var(--color-success);
}

.status-message.error {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}
</style>

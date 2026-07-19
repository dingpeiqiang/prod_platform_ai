<template>
  <div class="api-doc-container">
    <div class="api-doc-header">
      <div class="api-doc-title">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
          <polyline points="10 9 9 9 8 9"/>
        </svg>
        <h1>API 文档</h1>
      </div>
      <div class="api-doc-search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索 API 接口..."
          prefix-icon="Search"
          clearable
        />
      </div>
    </div>

    <div class="api-doc-body">
      <div class="api-doc-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-title">分类</div>
          <div class="sidebar-items">
            <button
              v-for="category in categories"
              :key="category.key"
              class="sidebar-item"
              :class="{ active: activeCategory === category.key }"
              @click="activeCategory = category.key"
            >
              <span class="sidebar-icon">{{ category.icon }}</span>
              <span class="sidebar-label">{{ category.label }}</span>
              <span class="sidebar-count">{{ getCategoryCount(category.key) }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="api-doc-content">
        <div v-if="filteredApis.length === 0" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <polyline points="10 9 9 9 8 9"/>
          </svg>
          <p>未找到匹配的 API</p>
        </div>

        <div v-for="api in filteredApis" :key="api.path + api.method" class="api-card">
          <div class="api-card-header" @click="toggleApi(api)">
            <div class="api-method" :class="api.method.toLowerCase()">
              {{ api.method }}
            </div>
            <div class="api-path">{{ api.path }}</div>
            <div class="api-expand">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedApis.includes(api.path + api.method) }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
          </div>

          <div v-if="expandedApis.includes(api.path + api.method)" class="api-card-body">
            <div class="api-description">{{ api.description }}</div>

            <div v-if="api.request" class="api-section">
              <div class="section-title">请求参数</div>
              <div class="api-json">
                <pre><code>{{ formatJson(api.request) }}</code></pre>
              </div>
            </div>

            <div v-if="api.response" class="api-section">
              <div class="section-title">响应示例</div>
              <div class="api-json">
                <pre><code>{{ formatJson(api.response) }}</code></pre>
              </div>
            </div>

            <div v-if="api.queryParams && api.queryParams.length" class="api-section">
              <div class="section-title">查询参数</div>
              <div class="api-table">
                <table>
                  <thead>
                    <tr>
                      <th>参数名</th>
                      <th>类型</th>
                      <th>必填</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="param in api.queryParams" :key="param.name">
                      <td>{{ param.name }}</td>
                      <td>{{ param.type }}</td>
                      <td>{{ param.required ? '是' : '否' }}</td>
                      <td>{{ param.description }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div v-if="api.pathParams && api.pathParams.length" class="api-section">
              <div class="section-title">路径参数</div>
              <div class="api-table">
                <table>
                  <thead>
                    <tr>
                      <th>参数名</th>
                      <th>类型</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="param in api.pathParams" :key="param.name">
                      <td>{{ param.name }}</td>
                      <td>{{ param.type }}</td>
                      <td>{{ param.description }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div class="api-actions">
              <button class="copy-btn" @click="copyApi(api)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
                复制请求
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElInput } from 'element-plus'

const emit = defineEmits(['go-back'])

const searchKeyword = ref('')
const activeCategory = ref('chat')
const expandedApis = ref([])

const categories = [
  { key: 'chat', label: '聊天接口', icon: '💬' },
  { key: 'config', label: '配置接口', icon: '⚙️' },
  { key: 'admin', label: '管理接口', icon: '🔧' },
  { key: 'validation', label: '验证接口', icon: '✅' },
  { key: 'health', label: '健康检查', icon: '❤️' },
]

const apiData = [
  {
    category: 'health',
    method: 'GET',
    path: '/api/v1/health',
    description: '检查服务健康状态',
    response: { status: 'ok', service: 'ai-form-assistant' },
  },
  {
    category: 'chat',
    method: 'POST',
    path: '/api/v1/chat',
    description: '发送聊天消息',
    request: {
      messages: [{ role: 'user', content: '你好' }],
      user_id: 'user123',
      conversation_context: {}
    },
    response: {
      success: true,
      reply: '你好！有什么可以帮你的？',
      intentType: null,
      formCode: null,
      extractedFields: null,
      toolCalls: null
    }
  },
  {
    category: 'chat',
    method: 'POST',
    path: '/api/v1/chat/stream',
    description: '流式聊天接口',
    request: {
      messages: [{ role: 'user', content: '你好' }],
      user_id: 'user123'
    },
    response: {
      event: 'text',
      data: { content: '你好！' }
    }
  },
  {
    category: 'chat',
    method: 'POST',
    path: '/api/v1/chat/completion',
    description: 'LLM 补全接口',
    request: {
      model: 'gpt-4o-mini',
      messages: [{ role: 'user', content: '你好' }],
      temperature: 0.7,
      max_tokens: 2048,
      stream: false
    },
    response: {
      success: true,
      reply: '你好！',
      stats: {
        prompt_tokens: 10,
        completion_tokens: 5,
        total_tokens: 15,
        duration_ms: 120,
        model: 'gpt-4o-mini'
      }
    }
  },
  {
    category: 'chat',
    method: 'GET',
    path: '/api/v1/chat/model/default',
    description: '获取默认模型配置',
    response: {
      success: true,
      config: {
        provider: 'openai',
        model: 'gpt-4o-mini',
        base_url: 'https://api.openai.com/v1',
        temperature: 0.3,
        max_tokens: 2048
      }
    }
  },
  {
    category: 'chat',
    method: 'GET',
    path: '/api/v1/chat/model/providers',
    description: '获取支持的模型提供商列表',
    response: {
      success: true,
      providers: ['openai', 'deepseek', 'aliyun', 'baichuan', 'qwen']
    }
  },
  {
    category: 'chat',
    method: 'POST',
    path: '/api/v1/chat/model/test',
    description: '测试模型连接',
    request: {
      model: 'gpt-4o-mini',
      base_url: 'https://api.openai.com/v1',
      api_key: 'sk-xxx',
      provider: 'openai'
    },
    response: {
      success: true,
      message: '连接成功',
      provider: 'openai',
      model: 'gpt-4o-mini',
      base_url: 'https://api.openai.com/v1',
      api_key_present: true,
      latency_ms: 120
    }
  },
  {
    category: 'chat',
    method: 'POST',
    path: '/api/v1/chat/model/switch',
    description: '切换当前使用的模型',
    request: {
      base_url: 'https://api.openai.com/v1',
      api_key: 'sk-xxx',
      model_name: 'gpt-4o-mini'
    },
    response: {
      success: true,
      message: '模型切换成功'
    }
  },
  {
    category: 'config',
    method: 'GET',
    path: '/api/v1/llm-config/list/{userIdentifier}',
    description: '获取用户的所有配置列表',
    pathParams: [{ name: 'userIdentifier', type: 'string', description: '用户标识' }],
    response: {
      success: true,
      message: '获取成功',
      data: []
    }
  },
  {
    category: 'config',
    method: 'GET',
    path: '/api/v1/llm-config/active/{userIdentifier}',
    description: '获取当前激活的配置',
    pathParams: [{ name: 'userIdentifier', type: 'string', description: '用户标识' }],
    response: {
      success: true,
      message: '获取成功',
      config: {}
    }
  },
  {
    category: 'config',
    method: 'POST',
    path: '/api/v1/llm-config/save',
    description: '保存 LLM 配置',
    request: {
      user_identifier: 'user123',
      provider: 'openai',
      model: 'gpt-4o-mini',
      api_key: 'sk-xxx',
      base_url: 'https://api.openai.com/v1',
      temperature: 0.3,
      max_tokens: 2048,
      thinking: false,
      max_input_tokens: 180000,
      config_name: '默认配置'
    },
    response: {
      success: true,
      message: '配置保存成功',
      config: {}
    }
  },
  {
    category: 'config',
    method: 'DELETE',
    path: '/api/v1/llm-config/{configId}',
    description: '删除指定配置',
    pathParams: [{ name: 'configId', type: 'integer', description: '配置ID' }],
    response: {
      success: true,
      message: '删除成功'
    }
  },
  {
    category: 'config',
    method: 'POST',
    path: '/api/v1/llm-config/activate',
    description: '激活指定配置',
    request: {
      user_identifier: 'user123',
      config_id: 1
    },
    response: {
      success: true,
      message: '激活成功',
      config: {}
    }
  },
  {
    category: 'config',
    method: 'POST',
    path: '/api/v1/llm-config/test',
    description: '测试配置连接',
    request: {
      model: 'gpt-4o-mini',
      base_url: 'https://api.openai.com/v1',
      api_key: 'sk-xxx',
      provider: 'openai'
    },
    response: {
      success: true,
      message: '连接成功',
      provider: 'openai',
      model: 'gpt-4o-mini',
      base_url: 'https://api.openai.com/v1',
      api_key_present: true,
      latency_ms: 120
    }
  },
  {
    category: 'admin',
    method: 'GET',
    path: '/api/v1/scenes',
    description: '获取场景列表',
    queryParams: [{ name: 'is_active', type: 'boolean', required: false, description: '是否只查询激活的场景' }],
    response: {
      success: true,
      total: 10,
      data: []
    }
  },
  {
    category: 'admin',
    method: 'GET',
    path: '/api/v1/scenes/{scene_code}',
    description: '获取场景详情',
    pathParams: [{ name: 'scene_code', type: 'string', description: '场景编码' }],
    response: {
      success: true,
      data: {}
    }
  },
  {
    category: 'admin',
    method: 'GET',
    path: '/api/v1/prompts',
    description: '获取提示词列表',
    queryParams: [
      { name: 'category', type: 'string', required: false, description: '分类' },
      { name: 'keyword', type: 'string', required: false, description: '关键词' },
      { name: 'page', type: 'integer', required: false, description: '页码' },
      { name: 'page_size', type: 'integer', required: false, description: '每页大小' }
    ],
    response: {
      success: true,
      data: [],
      total: 100,
      page: 1
    }
  },
  {
    category: 'admin',
    method: 'GET',
    path: '/api/v1/prompts/{code}',
    description: '获取提示词详情',
    pathParams: [{ name: 'code', type: 'string', description: '提示词编码' }],
    response: {
      success: true,
      data: {}
    }
  },
  {
    category: 'admin',
    method: 'POST',
    path: '/api/v1/prompts',
    description: '创建提示词',
    request: {
      code: 'prompt001',
      name: '示例提示词',
      category: '通用',
      content: '你是一个AI助手...',
      variables: []
    },
    response: {
      success: true,
      data: {},
      message: '创建成功'
    }
  },
  {
    category: 'admin',
    method: 'PUT',
    path: '/api/v1/prompts/{code}',
    description: '更新提示词',
    pathParams: [{ name: 'code', type: 'string', description: '提示词编码' }],
    request: {
      name: '更新后的名称',
      category: '通用',
      content: '更新后的内容...',
      variables: []
    },
    response: {
      success: true,
      data: {},
      message: '更新成功'
    }
  },
  {
    category: 'admin',
    method: 'DELETE',
    path: '/api/v1/prompts/{code}',
    description: '删除提示词',
    pathParams: [{ name: 'code', type: 'string', description: '提示词编码' }],
    response: {
      success: true,
      message: '删除成功'
    }
  },
  {
    category: 'validation',
    method: 'POST',
    path: '/api/v1/validation/field',
    description: '字段验证',
    request: {
      field_code: 'field001',
      field_value: '测试值',
      rules: []
    },
    response: {
      success: true,
      valid: true,
      errors: []
    }
  },
  {
    category: 'validation',
    method: 'POST',
    path: '/api/v1/validation/form',
    description: '表单验证',
    request: {
      form_data: {},
      fields: []
    },
    response: {
      success: true,
      valid: true,
      errors: []
    }
  },
  {
    category: 'validation',
    method: 'POST',
    path: '/api/v1/validation/llm',
    description: 'LLM 验证',
    request: {
      form_data: {},
      form_code: 'form001'
    },
    response: {
      success: true,
      valid: true,
      issues: [],
      suggestions: []
    }
  },
]

const filteredApis = computed(() => {
  let apis = apiData
  if (activeCategory.value !== 'all') {
    apis = apis.filter(api => api.category === activeCategory.value)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    apis = apis.filter(api => 
      api.path.toLowerCase().includes(keyword) ||
      api.description.toLowerCase().includes(keyword)
    )
  }
  return apis
})

const getCategoryCount = (category) => {
  return apiData.filter(api => api.category === category).length
}

const toggleApi = (api) => {
  const key = api.path + api.method
  const index = expandedApis.value.indexOf(key)
  if (index > -1) {
    expandedApis.value.splice(index, 1)
  } else {
    expandedApis.value.push(key)
  }
}

const formatJson = (obj) => {
  return JSON.stringify(obj, null, 2)
}

const copyApi = (api) => {
  const request = `curl -X ${api.method} "${api.path}" \\`
  ElMessage.success('API 请求已复制到剪贴板')
}
</script>

<style scoped>
.api-doc-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.api-doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 30px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.api-doc-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.api-doc-search {
  width: 300px;
}

.api-doc-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.api-doc-sidebar {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.sidebar-section {
  padding: 16px;
}

.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 12px;
  padding-left: 8px;
}

.sidebar-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: none;
  background: transparent;
  transition: all 0.2s;
  font-size: 14px;
  color: #606266;
}

.sidebar-item:hover {
  background: #ecf5ff;
  color: #409eff;
}

.sidebar-item.active {
  background: #409eff;
  color: #fff;
}

.sidebar-icon {
  font-size: 16px;
}

.sidebar-label {
  flex: 1;
  text-align: left;
}

.sidebar-count {
  font-size: 12px;
  background: #f2f6fc;
  color: #909399;
  padding: 2px 8px;
  border-radius: 10px;
}

.sidebar-item.active .sidebar-count {
  background: rgba(255, 255, 255, 0.3);
  color: #fff;
}

.api-doc-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #909399;
}

.empty-state svg {
  margin-bottom: 16px;
}

.api-card {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.api-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: background 0.2s;
}

.api-card-header:hover {
  background: #fafafa;
}

.api-method {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  min-width: 60px;
  text-align: center;
}

.api-method.get {
  background: #67c23a;
}

.api-method.post {
  background: #409eff;
}

.api-method.put {
  background: #e6a23c;
}

.api-method.delete {
  background: #f56c6c;
}

.api-method.patch {
  background: #909399;
}

.api-path {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  font-family: 'Consolas', 'Monaco', monospace;
}

.api-expand svg {
  transition: transform 0.2s;
}

.api-expand svg.rotated {
  transform: rotate(180deg);
}

.api-card-body {
  padding: 0 20px 20px;
  border-top: 1px solid #f0f0f0;
}

.api-description {
  padding: 16px 0;
  font-size: 14px;
  color: #606266;
}

.api-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 12px;
}

.api-json {
  background: #f8f9fa;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
}

.api-json pre {
  margin: 0;
  font-size: 13px;
  color: #303133;
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.6;
}

.api-table {
  overflow-x: auto;
}

.api-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.api-table th,
.api-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
}

.api-table th {
  background: #fafafa;
  font-weight: 600;
  color: #606266;
}

.api-table td {
  color: #303133;
}

.api-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover {
  border-color: #409eff;
  color: #409eff;
}
</style>
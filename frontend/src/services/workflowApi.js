import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
apiClient.interceptors.request.use(
  (config) => {
    console.debug('[Workflow API Request]', config.method.toUpperCase(), config.url, config.data)
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理成功时返回data.success包裹的数据结构
apiClient.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('[Workflow API Error]', error)
    return Promise.reject(error)
  }
)

// 工作流API - 适配GenericManager组件的接口
export const workflowApi = {
  // 获取工作流分类
  getCategories() {
    return apiClient.get('/workflows/categories')
  },

  // 获取工作流列表
  list(category, isActive) {
    return apiClient.get('/workflows', { params: { category, isActive } })
  },

  // 获取单个工作流
  get(workflowCode) {
    return apiClient.get(`/workflows/${workflowCode}`)
  },

  // 创建工作流 - 适配表单数据
  create(data) {
    // 如果数据已经包含 workflowCode 和 workflowName，直接使用
    if (data.workflowCode && data.workflowName) {
      return apiClient.post('/workflows', data)
    }
    
    // 否则将传入的表单数据转换为API期望的格式
    const payload = {
      workflowCode: data.code || data.workflowCode,
      workflowName: data.name || data.workflowName,
      description: data.description,
      category: data.category || 'general',
      tags: data.tags || [],
      priority: data.priority || 10,
      isActive: data.isActive !== undefined ? data.isActive : true,
      workflowData: data.workflowData || {}
    }
    return apiClient.post('/workflows', payload)
  },

  // 更新工作流
  update(workflowCode, data) {
    // 转换数据格式
    const payload = {}
    if (data.workflowName !== undefined) payload.workflowName = data.workflowName
    if (data.name !== undefined) payload.workflowName = data.name
    if (data.description !== undefined) payload.description = data.description
    if (data.category !== undefined) payload.category = data.category
    if (data.tags !== undefined) payload.tags = data.tags
    if (data.priority !== undefined) payload.priority = data.priority
    if (data.isActive !== undefined) payload.isActive = data.isActive
    if (data.workflowData !== undefined) payload.workflowData = data.workflowData
    return apiClient.put(`/workflows/${workflowCode}`, payload)
  },

  // 删除工作流
  delete(workflowCode) {
    return apiClient.delete(`/workflows/${workflowCode}`)
  },

  // 切换工作流启用状态
  toggle(workflowCode) {
    return apiClient.post(`/workflows/${workflowCode}/toggle`)
  },

  // AI生成工作流
  generate(requirement) {
    return apiClient.post('/workflows/generate', { requirement })
  },

  // AI优化工作流
  optimize(workflowData) {
    return apiClient.post('/scheduler/optimize', workflowData)
  },

  // 获取工作流可用变量
  getVariables(workflowCode, nodeId = null, typeFilter = null) {
    const params = {}
    if (nodeId) params.nodeId = nodeId
    if (typeFilter) params.typeFilter = typeFilter
    return apiClient.get(`/workflows/${workflowCode}/variables`, { params })
  },

  // 获取节点配置选项（包含可用变量和字段定义）
  getNodeConfigOptions(workflowCode, nodeId) {
    return apiClient.get(`/workflows/${workflowCode}/node-config-options/${nodeId}`)
  },

  // 复制工作流
  copy(workflowCode, newWorkflowCode, newWorkflowName = null) {
    const payload = {
      newWorkflowCode
    }
    if (newWorkflowName) {
      payload.newWorkflowName = newWorkflowName
    }
    return apiClient.post(`/workflows/${workflowCode}/copy`, payload)
  },

  // 获取工作流列表（用于工作流库）
  getAllWorkflows() {
    return apiClient.get('/workflows')
  }
}

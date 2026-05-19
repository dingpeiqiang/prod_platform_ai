import { get, post, put, del } from './httpClient'

export const workflowApi = {
  getCategories() {
    return get('/api/workflows/categories', { baseURL: '' })
  },

  list(category, isActive) {
    return get('/api/workflows', { 
      baseURL: '',
      params: { category, isActive }
    })
  },

  get(workflowCode) {
    return get(`/api/workflows/${workflowCode}`, { baseURL: '' })
  },

  create(data) {
    let payload = data
    if (!(data.workflowCode && data.workflowName)) {
      payload = {
        workflowCode: data.code || data.workflowCode,
        workflowName: data.name || data.workflowName,
        description: data.description,
        category: data.category || 'general',
        tags: data.tags || [],
        priority: data.priority || 10,
        isActive: data.isActive !== undefined ? data.isActive : true,
        workflowData: data.workflowData || {}
      }
    }
    return post('/api/workflows', payload, { 
      baseURL: '',
      loadingText: '创建工作流中...'
    })
  },

  update(workflowCode, data) {
    const payload = {}
    if (data.workflowName !== undefined) payload.workflowName = data.workflowName
    if (data.name !== undefined) payload.workflowName = data.name
    if (data.description !== undefined) payload.description = data.description
    if (data.category !== undefined) payload.category = data.category
    if (data.tags !== undefined) payload.tags = data.tags
    if (data.priority !== undefined) payload.priority = data.priority
    if (data.isActive !== undefined) payload.isActive = data.isActive
    if (data.workflowData !== undefined) payload.workflowData = data.workflowData
    return put(`/api/workflows/${workflowCode}`, payload, { 
      baseURL: '',
      loadingText: '更新工作流中...'
    })
  },

  delete(workflowCode) {
    return del(`/api/workflows/${workflowCode}`, { 
      baseURL: '',
      loadingText: '删除工作流中...'
    })
  },

  toggle(workflowCode) {
    return post(`/api/workflows/${workflowCode}/toggle`, {}, { 
      baseURL: '',
      loadingText: '切换状态中...'
    })
  },

  generate(requirement) {
    return post('/api/workflows/generate', { requirement }, { 
      baseURL: '',
      loadingText: 'AI生成中...'
    })
  },

  optimize(workflowData) {
    return post('/api/scheduler/optimize', workflowData, { 
      baseURL: '',
      loadingText: 'AI优化中...'
    })
  },

  getVariables(workflowCode, nodeId = null, typeFilter = null) {
    const params = {}
    if (nodeId) params.nodeId = nodeId
    if (typeFilter) params.typeFilter = typeFilter
    return get(`/api/workflows/${workflowCode}/variables`, { 
      baseURL: '',
      params 
    })
  },

  getNodeConfigOptions(workflowCode, nodeId) {
    return get(`/api/workflows/${workflowCode}/node-config-options/${nodeId}`, { 
      baseURL: ''
    })
  },

  copy(workflowCode, newWorkflowCode, newWorkflowName = null) {
    const payload = { newWorkflowCode }
    if (newWorkflowName) {
      payload.newWorkflowName = newWorkflowName
    }
    return post(`/api/workflows/${workflowCode}/copy`, payload, { 
      baseURL: '',
      loadingText: '复制工作流中...'
    })
  },

  getAllWorkflows() {
    return get('/api/workflows', { baseURL: '' })
  }
}
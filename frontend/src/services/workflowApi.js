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

  search(params) {
    return get('/api/workflows', { 
      baseURL: '',
      params
    })
  },

  get(workflowCode) {
    return get(`/api/workflows/${workflowCode}`, { baseURL: '' })
  },

  create(data, options = {}) {
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
      loadingText: '创建工作流中...',
      ...options
    })
  },

  update(workflowCode, data, options = {}) {
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
      loadingText: '更新工作流中...',
      ...options
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

  publish(workflowCode, user = null) {
    return post(`/api/workflows/${workflowCode}/publish`, user ? { user } : {}, { 
      baseURL: '',
      loadingText: '发布工作流中...'
    })
  },

  unpublish(workflowCode, user = null) {
    return post(`/api/workflows/${workflowCode}/unpublish`, user ? { user } : {}, { 
      baseURL: '',
      loadingText: '下线工作流中...'
    })
  },

  batchPublish(workflowCodes, user = null) {
    return post('/api/workflows/batch-publish', { workflowCodes, user }, { 
      baseURL: '',
      loadingText: '批量发布中...'
    })
  },

  rollback(workflowCode, targetVersion, user = null) {
    return post(`/api/workflows/${workflowCode}/rollback`, { targetVersion, user }, { 
      baseURL: '',
      loadingText: '回滚中...'
    })
  },

  compareVersions(workflowCode, version1, version2) {
    return post(`/api/workflows/${workflowCode}/compare-versions`, { version1, version2 }, { 
      baseURL: ''
    })
  },

  getHistory(workflowCode) {
    return get(`/api/workflows/${workflowCode}/history`, { baseURL: '' })
  },

  generate(requirement) {
    return post('/api/workflows/generate', { requirement }, { 
      baseURL: '',
      loadingText: 'AI生成中...'
    })
  },

  generateValidationRules(description, inputType = 'text') {
    return post('/api/workflows/generate-validation-rules', { description, inputType }, {
      baseURL: '',
      loadingText: 'AI生成校验规则中...'
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
    return get(`/api/workflows/${workflowCode}/node-config-options/${nodeId}`, { baseURL: '' })
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
  },

  // ── 固定流程引擎（P3-1b：编辑器执行入口切换后端引擎，见 FlowEngineController）──

  startEngineExecution(workflowCode, inputData, options = {}) {
    return post('/api/v1/flow-engine/executions', {
      workflow_code: workflowCode,
      input_data: inputData || {}
    }, { baseURL: '', loadingText: '流程执行中...', ...options })
  },

  getEngineExecution(executionId) {
    return get(`/api/v1/flow-engine/executions/${executionId}`, { baseURL: '', showLoading: false })
  },

  getEngineNodeLogs(executionId) {
    return get(`/api/v1/flow-engine/executions/${executionId}/node-logs`, { baseURL: '', showLoading: false })
  },

  resumeEngineExecution(executionId, user = null) {
    return post(`/api/v1/flow-engine/executions/${executionId}/resume`, user ? { triggered_by: user } : {}, { baseURL: '' })
  },

  humanResumeEngine(executionId, resumeToken, formData, user = null) {
    return post(`/api/v1/flow-engine/executions/${executionId}/human-resume`, {
      resume_token: resumeToken,
      form_data: formData || {},
      ...(user ? { triggered_by: user } : {})
    }, { baseURL: '', loadingText: '提交确认中...' })
  },

  // ── 固定流程引擎运维闭环（P4，见 FlowEngineController）──

  listEngineExecutions(workflowCode = null, page = 1, pageSize = 20) {
    return get('/api/v1/flow-engine/executions', {
      baseURL: '',
      params: {
        ...(workflowCode ? { workflow_code: workflowCode } : {}),
        page,
        page_size: pageSize
      }
    })
  },

  cancelEngineExecution(executionId, reason = null, user = null) {
    return post(`/api/v1/flow-engine/executions/${executionId}/cancel`, {
      ...(reason ? { reason } : {}),
      ...(user ? { triggered_by: user } : {})
    }, { baseURL: '', loadingText: '取消执行中...' })
  }
}
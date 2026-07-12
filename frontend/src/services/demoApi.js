import { get, post } from './httpClient'

export const demoSearchApi = {
  semanticSearch: (query, topK = 5) =>
    post('/demo/search/semantic', { query, top_k: topK }, { loadingText: '语义检索中...' }),

  getConfig: (packageId) =>
    get(`/demo/search/config/${packageId}`),

  clone: (sourcePackageId, modifications = null) =>
    post('/demo/search/clone', { source_package_id: sourcePackageId, modifications }, { loadingText: '克隆配置中...' }),

  diff: (sourcePackageId, clonedPackageId) =>
    post('/demo/search/diff', { source_package_id: sourcePackageId, cloned_package_id: clonedPackageId }, { loadingText: '对比差异中...' }),

  submit: (packageId, config = null) =>
    post('/demo/search/submit', { package_id: packageId, config }, { loadingText: '提交校验中...' })
}

export const demoDocApi = {
  upload: (formData) =>
    post('/demo/doc/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      loadingText: '上传文档中...'
    }),

  parse: (useBuiltin = true, filePath = null) =>
    post('/demo/doc/parse', { use_builtin: useBuiltin, file_path: filePath }, { loadingText: 'AI解析文档中...' }),

  batchGenerate: (items) =>
    post('/demo/doc/batch-generate', { items }, { loadingText: '批量生成配置中...' }),

  batchSubmit: (configs) =>
    post('/demo/doc/batch-submit', { configs }, { loadingText: '批量提交稽核中...' })
}

export const demoDialogApi = {
  start: () =>
    post('/demo/dialog/start', {}, { loadingText: '初始化会话中...' }),

  chatStream: (sessionId, message) => {
    return fetch('/api/v1/demo/dialog/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ session_id: sessionId, message })
    })
  },

  getCanvas: (sessionId) =>
    get(`/demo/dialog/canvas/${sessionId}`),

  updateNode: (sessionId, node) =>
    post('/demo/dialog/update-node', { session_id: sessionId, node }, { loadingText: '更新节点中...' }),

  validate: (sessionId) =>
    post('/demo/dialog/validate', { session_id: sessionId }, { loadingText: '本体校验中...' })
}

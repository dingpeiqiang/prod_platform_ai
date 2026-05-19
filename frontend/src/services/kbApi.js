import { get, post, del } from './httpClient'

export async function addDocument(content, title = '', source = '', importance = 1.0) {
  return await post('/api/kb/add', { content, title, source, importance }, {
    baseURL: '',
    loadingText: '添加文档中...'
  })
}

export async function importFromDir(dirPath) {
  return await post(`/api/kb/import-dir?dir_path=${encodeURIComponent(dirPath)}`, {}, {
    baseURL: '',
    loadingText: '导入文档中...'
  })
}

export async function searchDocuments(query, top_k = 5, min_similarity = 0.5) {
  return await post('/api/kb/search', { query, top_k, min_similarity }, {
    baseURL: '',
    loadingText: '搜索中...'
  })
}

export async function qa(query, top_k = 3) {
  return await post('/api/kb/qa', { query, top_k }, {
    baseURL: '',
    loadingText: '问答处理中...'
  })
}

export async function getDocument(entry_id) {
  return await get(`/api/kb/document/${entry_id}`, {
    baseURL: ''
  })
}

export async function deleteDocument(entry_id) {
  return await del(`/api/kb/document/${entry_id}`, {
    baseURL: '',
    loadingText: '删除文档中...'
  })
}

export async function getStats() {
  return await get('/api/kb/stats', {
    baseURL: ''
  })
}
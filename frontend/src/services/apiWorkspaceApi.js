import { request } from './httpClient.js'

const BASE = '/api-workspace'

/**
 * 已保存请求（请求集合）列表
 * @param {string|null} collectionName - 可选的集合过滤
 */
export async function listSavedRequests(collectionName = null) {
  const params = collectionName ? { collection_name: collectionName } : {}
  return request(`${BASE}/requests`, { params })
}

/**
 * 获取已有集合名称列表
 */
export async function listCollections() {
  return request(`${BASE}/collections`)
}

/**
 * 保存一条请求
 * @param {Object} payload - { name, method, url, headers, params, body, body_type, collection_name, description }
 */
export async function createSavedRequest(payload) {
  return request(`${BASE}/requests`, { method: 'POST', data: payload, silentError: true })
}

/**
 * 更新已保存请求
 * @param {number} id
 * @param {Object} payload
 */
export async function updateSavedRequest(id, payload) {
  return request(`${BASE}/requests/${id}`, { method: 'PUT', data: payload, silentError: true })
}

/**
 * 删除已保存请求
 * @param {number} id
 */
export async function deleteSavedRequest(id) {
  return request(`${BASE}/requests/${id}`, { method: 'DELETE', silentError: true })
}

/**
 * 请求历史
 * @param {number} limit
 */
export async function listHistory(limit = 50) {
  return request(`${BASE}/history`, { params: { limit }, silentError: true })
}

/**
 * 记录一条请求历史
 * @param {Object} payload
 */
export async function createHistory(payload) {
  return request(`${BASE}/history`, { method: 'POST', data: payload, silentError: true, showLoading: false })
}

/**
 * 清空请求历史
 */
export async function clearHistory() {
  return request(`${BASE}/history`, { method: 'DELETE', silentError: true })
}

/**
 * 删除单条历史
 * @param {number} id
 */
export async function deleteHistory(id) {
  return request(`${BASE}/history/${id}`, { method: 'DELETE', silentError: true })
}

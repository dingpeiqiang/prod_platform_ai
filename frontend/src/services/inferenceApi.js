/**
 * 推理平台模型配置 API 封装。
 * 统一走 httpClient 的 apiClient（自动附带 Authorization、401 统一跳登录），
 * 替代原先裸 axios 裸实例导致 401 静默失败的问题。
 * 注意：apiClient baseURL = /api/v1，路径不要再带前缀；响应拦截器已返回 body。
 */
import { request } from './httpClient.js'
import { authFetch } from './authFetch.js'

const BASE = '/llm-config'

export async function getUserConfigs(userId) {
  try {
    return await request(`${BASE}/list/${userId}`, { method: 'GET', silentError: true })
  } catch (e) {
    return { success: false, data: [], message: e.message }
  }
}

export async function saveConfig(config) {
  return request(`${BASE}/save`, { method: 'POST', data: config })
}

export async function getActiveConfig(userId) {
  return request(`${BASE}/active/${encodeURIComponent(userId)}`, { method: 'GET', silentError: true })
}

export async function testConfig(config) {
  return request(`${BASE}/test`, { method: 'POST', data: config })
}

/**
 * 模型对话测试 - 流式对话（多轮），复用 /api/v1/chat/stream。
 * 返回 { response, abortCtrl }，支持中止。
 */
export function chatTestStream(messages, modelConfig) {
  const abortCtrl = new AbortController()
  const body = {
    messages,
    modelConfig: modelConfig || {},
  }
  const prom = authFetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal: abortCtrl.signal,
  })
  return { prom, abortCtrl }
}

/**
 * 模型对话测试 - 非流式对话（多轮），复用 /api/v1/chat/completion。
 * 供流式输出失败（部分提供方不支持 SSE）时的自动降级使用。
 */
export async function chatTestCompletion(messages, modelConfig) {
  const body = {
    messages,
    modelConfig: modelConfig || {},
  }
  const res = await authFetch('/api/v1/chat/completion', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return res.json()
}

export async function deleteConfig(configId) {
  try {
    return await request(`${BASE}/${configId}`, { method: 'DELETE', silentError: true })
  } catch (e) {
    return { success: false, message: e.message }
  }
}

export async function activateConfig(userId, configId) {
  try {
    return await request(`${BASE}/activate`, {
      method: 'POST',
      data: { user_identifier: userId, config_id: configId },
      silentError: true,
    })
  } catch (e) {
    return { success: false, message: e.message }
  }
}

export async function getDefaultModel() {
  return request('/chat/model/default', { method: 'GET', silentError: true })
}

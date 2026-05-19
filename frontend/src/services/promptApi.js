/**
 * 提示词管理 API 服务
 */

const API_BASE = '/api/v1'

/**
 * 获取提示词列表
 */
export async function listPrompts() {
  const resp = await fetch(`${API_BASE}/prompts`)
  return await resp.json()
}

/**
 * 获取单个提示词详情
 */
export async function getPrompt(code) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}`)
  return await resp.json()
}

/**
 * 保存提示词内容
 */
export async function savePrompt(code, content) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  })
  return await resp.json()
}

/**
 * 创建提示词
 */
export async function createPrompt(promptData) {
  const resp = await fetch(`${API_BASE}/prompts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(promptData)
  })
  return await resp.json()
}

/**
 * 删除提示词
 */
export async function deletePrompt(code) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}`, {
    method: 'DELETE'
  })
  return await resp.json()
}
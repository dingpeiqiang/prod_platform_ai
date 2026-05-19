/**
 * 提示词管理 API 服务
 */

const API_BASE = '/api/v1'

/**
 * 获取分类列表
 */
export async function getCategories() {
  const resp = await fetch(`${API_BASE}/prompts/categories`)
  return await resp.json()
}

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
 * 更新提示词
 */
export async function updatePrompt(code, promptData) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(promptData)
  })
  return await resp.json()
}

/**
 * 获取提示词版本列表
 */
export async function getVersions(code) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}/versions`)
  return await resp.json()
}

/**
 * 预览提示词
 */
export async function previewPrompt(code, variables = {}) {
  const resp = await fetch(`${API_BASE}/prompts/${encodeURIComponent(code)}/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ variables })
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

/**
 * 使用AI生成提示词
 */
export async function generateWithAI(prompt, options = {}) {
  const resp = await fetch(`${API_BASE}/prompts/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, ...options })
  })
  return await resp.json()
}

/**
 * 使用AI优化提示词
 */
export async function optimizeWithAI(content) {
  const resp = await fetch(`${API_BASE}/prompts/optimize`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  })
  return await resp.json()
}
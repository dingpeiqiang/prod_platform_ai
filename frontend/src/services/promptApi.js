import { get, post, put, del } from './httpClient'

export async function getCategories() {
  return await get('prompts/categories')
}

export async function listPrompts() {
  return await get('prompts')
}

export async function getPrompt(code) {
  return await get(`prompts/${encodeURIComponent(code)}`)
}

export async function savePrompt(code, content) {
  return await put(`prompts/${encodeURIComponent(code)}`, { content }, {
    loadingText: '保存提示词中...'
  })
}

export async function createPrompt(promptData) {
  return await post('prompts', promptData, {
    loadingText: '创建提示词中...'
  })
}

export async function updatePrompt(code, promptData) {
  return await put(`prompts/${encodeURIComponent(code)}`, promptData, {
    loadingText: '更新提示词中...'
  })
}

export async function getVersions(code) {
  return await get(`prompts/${encodeURIComponent(code)}/versions`)
}

export async function previewPrompt(code, variables = {}) {
  return await post(`prompts/${encodeURIComponent(code)}/preview`, { variables })
}

export async function deletePrompt(code) {
  return await del(`prompts/${encodeURIComponent(code)}`, {
    loadingText: '删除提示词中...'
  })
}

export async function generateWithAI(prompt, options = {}) {
  return await post('prompts/generate', { prompt, ...options }, {
    loadingText: 'AI生成中...'
  })
}

export async function optimizeWithAI(content) {
  return await post('prompts/optimize', { content }, {
    loadingText: 'AI优化中...'
  })
}
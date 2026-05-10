/**
 * 提示词管理 API 服务
 */

const API_BASE = '/api/v1'

/**
 * 获取提示词分类列表
 */
export async function getCategories() {
    const resp = await fetch(`${API_BASE}/prompts/categories`)
    return await resp.json()
}

/**
 * 获取提示词列表
 */
export async function listPrompts(category, isActive) {
    const params = new URLSearchParams()
    if (category) params.set('category', category)
    if (isActive !== undefined) params.set('isActive', isActive)
    
    const resp = await fetch(`${API_BASE}/prompts?${params}`)
    return await resp.json()
}

/**
 * 获取单个提示词详情
 */
export async function getPrompt(code) {
    const resp = await fetch(`${API_BASE}/prompts/${code}`)
    return await resp.json()
}

/**
 * 创建提示词
 */
export async function createPrompt(data) {
    const resp = await fetch(`${API_BASE}/prompts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

/**
 * 更新提示词
 */
export async function updatePrompt(code, data) {
    const resp = await fetch(`${API_BASE}/prompts/${code}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

/**
 * 删除提示词
 */
export async function deletePrompt(code) {
    const resp = await fetch(`${API_BASE}/prompts/${code}`, {
        method: 'DELETE'
    })
    return await resp.json()
}

/**
 * 获取提示词版本历史
 */
export async function getVersions(code) {
    const resp = await fetch(`${API_BASE}/prompts/${code}/versions`)
    return await resp.json()
}

/**
 * 预览提示词
 */
export async function previewPrompt(code, variables) {
    const resp = await fetch(`${API_BASE}/prompts/${code}/preview`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ variables })
    })
    return await resp.json()
}

/**
 * AI辅助生成提示词
 */
export async function generateWithAI(requirement, category, useTools) {
    const resp = await fetch(`${API_BASE}/prompts/ai/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requirement, category, useTools })
    })
    return await resp.json()
}

/**
 * AI优化提示词
 */
export async function optimizeWithAI(content) {
    const resp = await fetch(`${API_BASE}/prompts/ai/optimize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content })
    })
    return await resp.json()
}

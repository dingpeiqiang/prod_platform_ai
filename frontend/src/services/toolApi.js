import { get, post, put, del, patch } from './httpClient'

export async function getToolCategories() {
    return await get('tools/categories')
}

export async function listTools(category, isActive) {
    return await get('tools', {
        params: { category, isActive }
    })
}

export async function getTool(toolCode) {
    return await get(`tools/${toolCode}`)
}

export async function createTool(data) {
    return await post('tools', data, {
        loadingText: '创建工具中...'
    })
}

export async function updateTool(toolCode, data) {
    return await put(`tools/${toolCode}`, data, {
        loadingText: '更新工具中...'
    })
}

export async function deleteTool(toolCode) {
    return await del(`tools/${toolCode}`, {
        loadingText: '删除工具中...'
    })
}

export async function toggleTool(toolCode) {
    return await patch(`tools/${toolCode}/toggle`, {}, {
        loadingText: '切换状态中...'
    })
}
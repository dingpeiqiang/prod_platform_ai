const API_BASE = '/api/v1'

export async function getToolCategories() {
    const resp = await fetch(`${API_BASE}/tools/categories`)
    return await resp.json()
}

export async function listTools(category, isActive) {
    const params = new URLSearchParams()
    if (category) params.set('category', category)
    if (isActive !== undefined) params.set('isActive', isActive)
    
    const resp = await fetch(`${API_BASE}/tools?${params}`)
    return await resp.json()
}

export async function getTool(toolCode) {
    const resp = await fetch(`${API_BASE}/tools/${toolCode}`)
    return await resp.json()
}

export async function createTool(data) {
    const resp = await fetch(`${API_BASE}/tools`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function updateTool(toolCode, data) {
    const resp = await fetch(`${API_BASE}/tools/${toolCode}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function deleteTool(toolCode) {
    const resp = await fetch(`${API_BASE}/tools/${toolCode}`, {
        method: 'DELETE'
    })
    return await resp.json()
}

export async function toggleTool(toolCode) {
    const resp = await fetch(`${API_BASE}/tools/${toolCode}/toggle`, {
        method: 'PATCH'
    })
    return await resp.json()
}

const API_BASE = '/api/v1'

export async function getFormCategories() {
    const resp = await fetch(`${API_BASE}/forms/categories`)
    return await resp.json()
}

export async function listForms(category, isActive) {
    const params = new URLSearchParams()
    if (category) params.set('category', category)
    if (isActive !== undefined) params.set('isActive', isActive)
    
    const resp = await fetch(`${API_BASE}/forms?${params}`)
    return await resp.json()
}

export async function getForm(formCode) {
    const resp = await fetch(`${API_BASE}/forms/${formCode}`)
    return await resp.json()
}

export async function createForm(data) {
    const resp = await fetch(`${API_BASE}/forms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function updateForm(formCode, data) {
    const resp = await fetch(`${API_BASE}/forms/${formCode}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function deleteForm(formCode) {
    const resp = await fetch(`${API_BASE}/forms/${formCode}`, {
        method: 'DELETE'
    })
    return await resp.json()
}

export async function toggleForm(formCode) {
    const resp = await fetch(`${API_BASE}/forms/${formCode}/toggle`, {
        method: 'PATCH'
    })
    return await resp.json()
}

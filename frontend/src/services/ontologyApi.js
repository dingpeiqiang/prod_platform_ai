const API_BASE = '/api/v1'

export async function getOntologyCategories() {
    const resp = await fetch(`${API_BASE}/ontologies/categories`)
    return await resp.json()
}

export async function listOntologies(isActive) {
    const params = new URLSearchParams()
    if (isActive !== undefined) params.set('isActive', isActive)
    
    const resp = await fetch(`${API_BASE}/ontologies?${params}`)
    return await resp.json()
}

export async function getOntology(ontologyCode) {
    const resp = await fetch(`${API_BASE}/ontologies/${ontologyCode}`)
    return await resp.json()
}

export async function createOntology(data) {
    const resp = await fetch(`${API_BASE}/ontologies`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function updateOntology(ontologyCode, data) {
    const resp = await fetch(`${API_BASE}/ontologies/${ontologyCode}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    return await resp.json()
}

export async function deleteOntology(ontologyCode) {
    const resp = await fetch(`${API_BASE}/ontologies/${ontologyCode}`, {
        method: 'DELETE'
    })
    return await resp.json()
}

export async function toggleOntology(ontologyCode) {
    const resp = await fetch(`${API_BASE}/ontologies/${ontologyCode}/toggle`, {
        method: 'PATCH'
    })
    return await resp.json()
}

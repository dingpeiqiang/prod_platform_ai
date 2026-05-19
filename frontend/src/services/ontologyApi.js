import { get, post, put, del, patch } from './httpClient'

export async function getOntologyCategories() {
    return await get('ontologies/categories')
}

export async function listOntologies(isActive) {
    return await get('ontologies', {
        params: { isActive }
    })
}

export async function getOntology(ontologyCode) {
    return await get(`ontologies/${ontologyCode}`)
}

export async function createOntology(data) {
    return await post('ontologies', data, {
        loadingText: '创建本体中...'
    })
}

export async function updateOntology(ontologyCode, data) {
    return await put(`ontologies/${ontologyCode}`, data, {
        loadingText: '更新本体中...'
    })
}

export async function deleteOntology(ontologyCode) {
    return await del(`ontologies/${ontologyCode}`, {
        loadingText: '删除本体中...'
    })
}

export async function toggleOntology(ontologyCode) {
    return await patch
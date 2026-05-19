import { get, post, put, del } from './httpClient'

export async function getFormCategories() {
    return await get('forms/categories')
}

export async function listForms(category, isActive) {
    return await get('forms', {
        params: {
            category,
            isActive
        }
    })
}

export async function getForm(formCode) {
    return await get(`forms/${formCode}`)
}

export async function createForm(data) {
    return await post('forms', data, {
        loadingText: '创建表单中...'
    })
}

export async function updateForm(formCode, data) {
    return await put(`forms/${formCode}`, data, {
        loadingText: '更新表单中...'
    })
}

export async function deleteForm(formCode) {
    return await del(`forms/${formCode}`, {
        loadingText: '删除表单中...'
    })
}

export async function toggleForm(formCode) {
    return await put(`forms/${formCode}/toggle`, {}, {
        loadingText: '切换状态中...'
    })
}
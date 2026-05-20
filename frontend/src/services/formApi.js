import { get, post, put, del } from './httpClient'

/**
 * 获取本体分类列表（唯一 category 值）
 * 对应后端 GET /api/v1/config/ontologies
 * 后端返回: {success, ontologies: [{ontologyCode, ontologyName, category, ...}]}
 */
export async function getFormCategories() {
    const res = await get('config/ontologies')
    if (res.success && res.ontologies) {
        const cats = [...new Set(res.ontologies.map(o => o.category).filter(Boolean))]
        return { success: true, data: cats }
    }
    return res
}

/**
 * 列出所有可用本体（表单类型）
 * 对应后端 GET /api/v1/config/ontologies
 * 后端返回 ontologyCode/ontologyName/isActive/entities 来自 Ontology.to_dict()
 * 前端适配: result.data → [{formCode, formName, fieldCount, isActive}]
 */
export async function listForms(category, isActive) {
    const res = await get('config/ontologies')
    if (!res.success || !res.ontologies) return res

    let forms = res.ontologies.map(o => {
        // 从 entities 统计字段数量
        let fieldCount = 0
        const entities = o.entities || []
        for (const entity of entities) {
            fieldCount += (entity.fields || []).length
        }
        return {
            formCode: o.ontologyCode,
            formName: o.ontologyName,
            fieldCount,
            isActive: o.isActive,
            category: o.category || 'general',
            description: o.description || ''
        }
    })

    if (category) {
        forms = forms.filter(f => f.category === category)
    }
    if (isActive !== undefined) {
        forms = forms.filter(f => f.isActive === isActive)
    }

    return { success: true, data: forms }
}

/**
 * 获取单个本体的完整字段定义
 * 对应后端 GET /api/v1/form/schema/{form_code}
 * 后端返回: {success, formCode, formName, version, fields, entities}
 */
export async function getForm(formCode) {
    return await get(`form/schema/${formCode}`)
}

/**
 * 以下 CRUD 方法已废弃（FormTemplate 模型已删除）
 * 表单类型通过本体配置管理，不支持运行时增删改
 */
export async function createForm(data) {
    console.warn('[formApi] createForm 已废弃，请通过配置本体来创建表单')
    return { success: false, message: '已废弃：表单类型通过本体配置管理' }
}

export async function updateForm(formCode, data) {
    console.warn('[formApi] updateForm 已废弃')
    return { success: false, message: '已废弃' }
}

export async function deleteForm(formCode) {
    console.warn('[formApi] deleteForm 已废弃')
    return { success: false, message: '已废弃' }
}

export async function toggleForm(formCode) {
    console.warn('[formApi] toggleForm 已废弃')
    return { success: false, message: '已废弃' }
}
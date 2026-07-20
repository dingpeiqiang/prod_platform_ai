import { request } from './httpClient'

const REASONING_BASE = '/reasoning'

export async function retrieveFacts(req) {
  try {
    const resp = await request(`${REASONING_BASE}/facts/retrieve`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '检索事实数据...'
    })
    return resp
  } catch (e) {
    console.error('检索事实失败:', e)
    return {
      success: false,
      message: e.message || '检索事实失败',
      snapshot_id: null,
      facts_map: {}
    }
  }
}

export async function evaluatePolicy(req) {
  try {
    const resp = await request(`${REASONING_BASE}/policy/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '评估策略...'
    })
    return resp
  } catch (e) {
    console.error('策略评估失败:', e)
    return {
      success: false,
      message: e.message || '策略评估失败'
    }
  }
}

export async function evaluateWithFacts(req) {
  try {
    const resp = await request(`${REASONING_BASE}/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '评估事实...'
    })
    return resp
  } catch (e) {
    console.error('评估失败:', e)
    return { success: false, message: e.message || '评估失败' }
  }
}

export async function evaluateSwrl(req) {
  try {
    const resp = await request(`${REASONING_BASE}/swrl/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '评估SWRL规则...'
    })
    return resp
  } catch (e) {
    console.error('SWRL评估失败:', e)
    return { success: false, message: e.message || 'SWRL评估失败' }
  }
}

export async function validateShacl(req) {
  try {
    const resp = await request(`${REASONING_BASE}/shacl/validate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '验证SHACL约束...'
    })
    return resp
  } catch (e) {
    console.error('SHACL验证失败:', e)
    return { success: false, message: e.message || 'SHACL验证失败' }
  }
}

export async function compareState(req) {
  try {
    const resp = await request(`${REASONING_BASE}/compare-state`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '比较状态...'
    })
    return resp
  } catch (e) {
    console.error('状态比较失败:', e)
    return { success: false, message: e.message || '状态比较失败' }
  }
}

export async function hypotheticalEvaluate(req) {
  try {
    const resp = await request(`${REASONING_BASE}/hypothetical/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '假设评估...'
    })
    return resp
  } catch (e) {
    console.error('假设评估失败:', e)
    return { success: false, message: e.message || '假设评估失败' }
  }
}

export async function explain(req) {
  try {
    const resp = await request(`${REASONING_BASE}/explain`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '生成解释...'
    })
    return resp
  } catch (e) {
    console.error('解释生成失败:', e)
    return { success: false, message: e.message || '解释生成失败' }
  }
}

export async function getTrace(trace_id, tenant_id) {
  try {
    const resp = await request(`${REASONING_BASE}/trace/${encodeURIComponent(trace_id)}`, {
      method: 'GET',
      params: { tenant_id },
      showLoading: true,
      loadingText: '查询审计日志...'
    })
    return resp
  } catch (e) {
    console.error('查询审计日志失败:', e)
    return {
      success: false,
      message: e.message || '查询审计日志失败',
      results: []
    }
  }
}

export async function nlQuery(req) {
  try {
    const resp = await request(`${REASONING_BASE}/nl/query`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '自然语言查询...'
    })
    return resp
  } catch (e) {
    console.error('自然语言查询失败:', e)
    return { success: false, message: e.message || '自然语言查询失败' }
  }
}

export async function schema() {
  try {
    return await request(`${REASONING_BASE}/schema`, { method: 'GET' })
  } catch (e) {
    console.error('获取Schema失败:', e)
    return { classes: [], properties: [] }
  }
}

export async function schemaCatalog() {
  try {
    return await request(`${REASONING_BASE}/schema/catalog`, { method: 'GET' })
  } catch (e) {
    console.error('获取Schema目录失败:', e)
    return { categories: [], classes: [] }
  }
}

export async function schemaDetail(req) {
  try {
    return await request(`${REASONING_BASE}/schema/detail`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '获取Schema详情...'
    })
  } catch (e) {
    console.error('获取Schema详情失败:', e)
    return { success: false, message: e.message || '获取Schema详情失败' }
  }
}

export async function reasoningSparqlQuery(query) {
  try {
    return await request(`${REASONING_BASE}/sparql/query`, {
      method: 'POST',
      data: { query },
      showLoading: true,
      loadingText: '执行SPARQL查询...'
    })
  } catch (e) {
    console.error('SPARQL查询失败:', e)
    return { results: [] }
  }
}

export async function reasoningHealth() {
  try {
    return await request(`${REASONING_BASE}/health`, { method: 'GET' })
  } catch (e) {
    console.error('健康检查失败:', e)
    return { status: 'error' }
  }
}

export async function reasoningStats() {
  try {
    return await request(`${REASONING_BASE}/ontology/stats`, { method: 'GET' })
  } catch (e) {
    console.error('获取统计信息失败:', e)
    return { classCount: 0, propertyCount: 0, instanceCount: 0, tripleCount: 0 }
  }
}

export async function reasoningAddClass(name) {
  try {
    return await request(`${REASONING_BASE}/ontology/classes`, {
      method: 'POST',
      data: { name }
    })
  } catch (e) {
    console.error('创建类失败:', e)
    return { success: false, message: e.message || '创建类失败' }
  }
}

export async function reasoningAddProperty(name) {
  try {
    return await request(`${REASONING_BASE}/ontology/properties`, {
      method: 'POST',
      data: { name }
    })
  } catch (e) {
    console.error('创建属性失败:', e)
    return { success: false, message: e.message || '创建属性失败' }
  }
}

export async function reasoningAllInstances() {
  try {
    return await request(`${REASONING_BASE}/ontology/instances`, { method: 'GET' })
  } catch (e) {
    console.error('获取实例失败:', e)
    return []
  }
}

export async function reasoningAddInstance(uri, type, facts = {}) {
  try {
    return await request(`${REASONING_BASE}/ontology/instances`, {
      method: 'POST',
      data: { uri, type, facts }
    })
  } catch (e) {
    console.error('创建实例失败:', e)
    return { success: false, message: e.message || '创建实例失败' }
  }
}

export async function reasoningUpdateInstance(uri, facts = {}) {
  try {
    return await request(`${REASONING_BASE}/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'PUT',
      data: { facts }
    })
  } catch (e) {
    console.error('更新实例失败:', e)
    return { success: false, message: e.message || '更新实例失败' }
  }
}

export async function reasoningDeleteInstance(uri) {
  try {
    return await request(`${REASONING_BASE}/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'DELETE'
    })
  } catch (e) {
    console.error('删除实例失败:', e)
    return { success: false, message: e.message || '删除实例失败' }
  }
}
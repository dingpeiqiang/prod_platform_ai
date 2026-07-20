import { request } from './httpClient'

const ONTOLOGY_BASE = '/reasoning'

export async function nlDiscover(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/nl-discover`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '自然语言发现...'
    })
    return resp
  } catch (e) {
    console.error('自然语言发现失败:', e)
    return { success: false, message: e.message || '自然语言发现失败' }
  }
}

export async function quickEvaluate(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/quick-evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '快速评估...'
    })
    return resp
  } catch (e) {
    console.error('快速评估失败:', e)
    return { success: false, message: e.message || '快速评估失败' }
  }
}

export async function getPolicySets() {
  try {
    return await request(`${ONTOLOGY_BASE}/policy/sets`, { method: 'GET' })
  } catch (e) {
    console.error('获取策略集失败:', e)
    return []
  }
}

export async function getSwrlRules() {
  try {
    return await request(`${ONTOLOGY_BASE}/swrl/rules`, { method: 'GET' })
  } catch (e) {
    console.error('获取SWRL规则失败:', e)
    return []
  }
}

export async function getOntologyStats() {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/stats`, { method: 'GET' })
  } catch (e) {
    console.error('获取统计信息失败:', e)
    return { classCount: 0, propertyCount: 0, instanceCount: 0, tripleCount: 0 }
  }
}

export async function addOntologyClass(name) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/classes`, {
      method: 'POST',
      data: { name }
    })
  } catch (e) {
    console.error('创建类失败:', e)
    return { success: false, message: e.message || '创建类失败' }
  }
}

export async function addOntologyProperty(name) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/properties`, {
      method: 'POST',
      data: { name }
    })
  } catch (e) {
    console.error('创建属性失败:', e)
    return { success: false, message: e.message || '创建属性失败' }
  }
}

export async function getAllInstances() {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/instances`, { method: 'GET' })
  } catch (e) {
    console.error('获取实例失败:', e)
    return []
  }
}

export async function addInstance(uri, type, facts = {}) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/instances`, {
      method: 'POST',
      data: { uri, type, facts }
    })
  } catch (e) {
    console.error('创建实例失败:', e)
    return { success: false, message: e.message || '创建实例失败' }
  }
}

export async function updateInstance(uri, facts = {}) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'PUT',
      data: { facts }
    })
  } catch (e) {
    console.error('更新实例失败:', e)
    return { success: false, message: e.message || '更新实例失败' }
  }
}

export async function deleteInstance(uri) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'DELETE'
    })
  } catch (e) {
    console.error('删除实例失败:', e)
    return { success: false, message: e.message || '删除实例失败' }
  }
}

export async function sparqlQuery(query) {
  try {
    return await request(`${ONTOLOGY_BASE}/sparql/query`, {
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

export async function importTtl(ttlContent, replace = false) {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/import-ttl`, {
      method: 'POST',
      data: { ttlContent, replace },
      showLoading: true,
      loadingText: '导入TTL本体...'
    })
  } catch (e) {
    console.error('TTL导入失败:', e)
    return { success: false, message: e.message || 'TTL导入失败' }
  }
}

export async function getOntologyGraph() {
  try {
    return await request(`${ONTOLOGY_BASE}/ontology/graph`, {
      method: 'GET',
      showLoading: true,
      loadingText: '加载本体图数据...'
    })
  } catch (e) {
    console.error('获取本体图数据失败:', e)
    return { nodes: [], edges: [], classCount: 0, propertyCount: 0, instanceCount: 0, edgeCount: 0 }
  }
}

export async function getOntologySchema() {
  try {
    return await request(`${ONTOLOGY_BASE}/schema`, { method: 'GET' })
  } catch (e) {
    console.error('获取Schema失败:', e)
    return { classes: [], properties: [] }
  }
}

export async function getSchemaCatalog() {
  try {
    return await request(`${ONTOLOGY_BASE}/schema/catalog`, { method: 'GET' })
  } catch (e) {
    console.error('获取Schema目录失败:', e)
    return { categories: [], classes: [] }
  }
}

export async function getSchemaDetail(req) {
  try {
    return await request(`${ONTOLOGY_BASE}/schema/detail`, {
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

export async function nlQuery(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/nl/query`, {
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

export async function explain(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/explain`, {
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
    const resp = await request(`${ONTOLOGY_BASE}/trace/${encodeURIComponent(trace_id)}`, {
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
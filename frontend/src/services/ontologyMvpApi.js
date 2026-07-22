export async function getOpsDashboard() {
  return {
    anomalyOfferingCount: 1,
    highRiskCount: 13,
    suggestDelistCount: 7,
    shelfCount: 80,
    ruleVersion: 'RiskRules-v1.2',
    alerts: []
  }
}

export async function chatConfigure() { return {} }
export async function checkCompliance() { return { issues: [], compliancePass: true } }
export async function batchFromDocument() { return [] }
export async function analyzeRootCause() { return {} }
export async function auditRisks() { return { violations: [], passes: [] } }
export async function updateRiskRules() { return {} }

export async function getOntologyInstances() {
  try {
    const response = await fetch('/api/v1/product-ops/ontology/instances')
    if (!response.ok) throw new Error('加载失败')
    return await response.json()
  } catch (e) {
    console.error('获取本体实例失败:', e)
    return []
  }
}

export async function getOntologyInstance(uri) {
  try {
    const response = await fetch(`/api/v1/product-ops/ontology/instances/${encodeURIComponent(uri)}`)
    if (!response.ok) throw new Error('加载失败')
    return await response.json()
  } catch (e) {
    console.error('获取实例详情失败:', e)
    return null
  }
}

export async function createOntologyInstance(data) {
  try {
    const response = await fetch('/api/v1/product-ops/ontology/instances', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    if (!response.ok) throw new Error('创建失败')
    return await response.json()
  } catch (e) {
    console.error('创建实例失败:', e)
    throw e
  }
}

export async function updateOntologyInstance(uri, data) {
  try {
    const response = await fetch(`/api/v1/product-ops/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    if (!response.ok) throw new Error('更新失败')
    return await response.json()
  } catch (e) {
    console.error('更新实例失败:', e)
    throw e
  }
}

export async function deleteOntologyInstance(uri) {
  try {
    const response = await fetch(`/api/v1/product-ops/ontology/instances/${encodeURIComponent(uri)}`, {
      method: 'DELETE'
    })
    if (!response.ok) throw new Error('删除失败')
    return await response.json()
  } catch (e) {
    console.error('删除实例失败:', e)
    throw e
  }
}

export async function getOntologyGraph() {
  try {
    const response = await fetch('/api/v1/product-ops/ontology/graph')
    if (!response.ok) throw new Error('加载失败')
    return await response.json()
  } catch (e) {
    console.error('获取图谱数据失败:', e)
    return { nodes: [], edges: [], classCount: 0, propertyCount: 0, instanceCount: 0, edgeCount: 0 }
  }
}
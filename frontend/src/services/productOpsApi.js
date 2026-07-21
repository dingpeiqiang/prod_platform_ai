import { request } from './httpClient'

const BASE = '/api/v1/product-ops'

export async function getProductOpsOverview() {
  return request(`${BASE}/overview`, { method: 'GET' })
}

export async function searchProductOps(question) {
  return request(`${BASE}/search`, {
    method: 'GET',
    params: { q: question },
    showLoading: true,
    loadingText: '查询产商品运营数据...'
  })
}

export async function checkProductOnline(facts, traceId = 'product-online-trace', tenantId = 'product_ops') {
  return request(`${BASE}/product-online/check`, {
    method: 'POST',
    data: { facts, trace_id: traceId, tenant_id: tenantId },
    showLoading: true,
    loadingText: '校验新品立项...'
  })
}

export async function auditProductRisk(entities = [], traceId = 'product-risk-trace', tenantId = 'product_ops') {
  return request(`${BASE}/product-risk/audit`, {
    method: 'POST',
    data: { entities, trace_id: traceId, tenant_id: tenantId },
    showLoading: true,
    loadingText: '执行风险稽核...'
  })
}

export async function compareProductState(snapshotId, patches = [], policySetId = 'PS_PRODUCT_ONLINE_V1', traceId = 'product-compare-trace', tenantId = 'product_ops') {
  return request(`${BASE}/compare`, {
    method: 'POST',
    data: { snapshot_id: snapshotId, patches, policy_set_id: policySetId, trace_id: traceId, tenant_id: tenantId },
    showLoading: true,
    loadingText: '执行方案对比...'
  })
}

export async function explainProductOps(traceId, audience = 'business', tenantId = 'product_ops') {
  return request(`${BASE}/explain`, {
    method: 'POST',
    data: { trace_id: traceId, audience, tenant_id: tenantId },
    showLoading: true,
    loadingText: '生成解释说明...'
  })
}

export async function discoverProductOps(question, maxEntities = 10) {
  return request(`${BASE}/nl-discover`, {
    method: 'POST',
    data: { question, max_entities: maxEntities },
    showLoading: true,
    loadingText: '智能发现与检索...'
  })
}

export async function getProductOpsTrace(traceId) {
  return request(`${BASE}/trace`, {
    method: 'GET',
    params: { trace_id: traceId },
    showLoading: true,
    loadingText: '查询审计链路...'
  })
}

export async function getProductOpsFormConstraint(formCode) {
  return request(`${BASE}/form-constraint`, {
    method: 'GET',
    params: { form_code: formCode },
    showLoading: true,
    loadingText: '加载表单约束...'
  })
}

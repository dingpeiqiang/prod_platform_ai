import { get, post, put } from './httpClient.js'

const BASE = 'product-ontology'

export async function getOntologyGraph() {
  return get(`${BASE}/graph`, { showLoading: false })
}

export async function getOntologyMeta() {
  return get(`${BASE}/meta`, { showLoading: false })
}

/**
 * P1-4：按品类码拉取模板渲染 schema（§11.8）。
 * 未识别品类 / 模板接口未就绪时返回 null，由调用方降级本地 mock schema。
 */
export async function fetchTemplateSchema(categoryCode) {
  if (!categoryCode) return null
  try {
    const body = await get(`${BASE}/config/template/${encodeURIComponent(categoryCode)}`, {
      showLoading: false,
    })
    if (body && body.success && body.schema) {
      return { template: body.template || null, schema: body.schema }
    }
    return null
  } catch (e) {
    return null
  }
}

export async function getOpsDashboard() {
  return get(`${BASE}/ops/dashboard`, { showLoading: false })
}

export async function chatConfigure(text, draft = null) {
  return post(`${BASE}/config/chat`, { text, draft }, { showLoading: false, loadingText: '本体推理中...' })
}

export async function checkCompliance(draft, extras = {}) {
  const body = { draft: draft || null, ...extras }
  return post(`${BASE}/config/compliance`, body, { showLoading: false })
}

export async function batchFromDocument(documentText = '', packages = null) {
  return post(
    `${BASE}/config/batch`,
    { documentText, packages },
    { showLoading: false, loadingText: '文档映射中...' },
  )
}

/** 智查：检索历史配置方案 */
export async function discoverConfigs(q = '', limit = 20) {
  return post(
    `${BASE}/config/discover`,
    { q, question: q, limit },
    { showLoading: false, loadingText: '检索历史配置...' },
  )
}

/** 一键复制为草稿并合规校验（带 sessionId 时复制即开配置工单；requirement 为复制弹窗补充需求，后端按需求修正副本字段） */
export async function copyAsDraft(offeringId, text = null, sessionId = null, requirement = null) {
  const body = { offering_id: offeringId, offeringId, text, session_id: sessionId }
  if (requirement) body.requirement = requirement
  return post(
    `${BASE}/config/copy-as-draft`,
    body,
    { showLoading: false, loadingText: '复制配置草稿...' },
  )
}

/** 解包 Vue Proxy，并重建原生 File，避免 FormData 传出空 part */
async function toNativeUploadFile(file) {
  let blob = file
  // Vue 3 reactive Proxy 带 __v_raw
  if (blob && typeof blob === 'object' && blob.__v_raw) {
    blob = blob.__v_raw
  }
  if (!(blob instanceof Blob)) {
    throw new Error('无效的本地文件对象')
  }
  if (blob.size <= 0) {
    throw new Error('文件内容为空，无法上传')
  }
  const filename = blob instanceof File && blob.name ? blob.name : 'upload.bin'
  const type = blob.type || 'application/octet-stream'
  const buffer = await blob.arrayBuffer()
  return new File([buffer], filename, { type, lastModified: Date.now() })
}

/** 智读：选择文件后立即上传，返回 file_id */
export async function uploadConfigFile(file) {
  const nativeFile = await toNativeUploadFile(file)
  const form = new FormData()
  form.append('file', nativeFile, nativeFile.name)
  const resp = await fetch('/api/v1/product-ontology/config/upload', {
    method: 'POST',
    body: form,
  })
  let data = {}
  try {
    data = await resp.json()
  } catch {
    data = {}
  }
  if (!resp.ok || data?.success === false) {
    throw new Error(data?.message || `上传失败（HTTP ${resp.status}）`)
  }
  return data
}

/** 智读：按已上传 file_id 批量映射（发送时调用） */
export async function batchFromUploadedFile(fileId, fileName = null) {
  const body = { file_id: fileId, fileId }
  if (fileName) {
    body.fileName = fileName
    body.file_name = fileName
  }
  return post(`${BASE}/config/batch-by-file`, body, {
    showLoading: false,
    loadingText: '解析文档并映射...',
  })
}

/** 智读：上传文件并一步映射（兼容旧入口） */
export async function batchFromUpload(file) {
  const nativeFile = await toNativeUploadFile(file)
  const form = new FormData()
  form.append('file', nativeFile, nativeFile.name)
  const resp = await fetch('/api/v1/product-ontology/config/batch-upload', {
    method: 'POST',
    body: form,
  })
  let data = {}
  try {
    data = await resp.json()
  } catch {
    data = {}
  }
  if (!resp.ok || data?.success === false) {
    throw new Error(data?.message || `解析失败（HTTP ${resp.status}）`)
  }
  return data
}

/** 知识沉淀：合规草稿写入事实图/本体 */
export async function publishConfigDraft(draft) {
  return post(
    `${BASE}/config/publish`,
    { draft },
    { showLoading: false, loadingText: '沉淀至本体...' },
  )
}

/** 草稿列表（按会话/用户） */
export async function listConfigDrafts({ sessionId = null, userId = null, status = null } = {}) {
  const params = {}
  if (sessionId) params.session_id = sessionId
  if (userId) params.user_id = userId
  if (status) params.status = status
  return get(`${BASE}/config/drafts`, { params, showLoading: false })
}

export async function getConfigDraft(draftId) {
  return get(`${BASE}/config/drafts/${encodeURIComponent(draftId)}`, { showLoading: false })
}

/** 持久化配置草稿（新增/更新：更新走 body.draftId，后端按主键覆盖） */
export async function saveConfigDraft({
  draft,
  draftId = null,
  clientId = null,
  sessionId = null,
  userId = null,
  compliancePass = null,
} = {}) {
  const body = { draft }
  if (draftId != null) body.draftId = draftId
  if (clientId) body.clientId = clientId
  if (sessionId) body.sessionId = sessionId
  if (userId) body.userId = userId
  if (compliancePass != null) body.compliancePass = compliancePass
  return post(`${BASE}/config/drafts`, body, {
    showLoading: false,
    loadingText: '保存草稿...',
  })
}

/** 合规通过后提交：沉淀 + 生成工单 */
export async function submitConfigDraft({
  draft,
  draftId = null,
  clientId = null,
  sessionId = null,
  userId = null,
} = {}) {
  const body = { draft }
  if (draftId != null) body.draftId = draftId
  if (clientId) body.clientId = clientId
  if (sessionId) body.sessionId = sessionId
  if (userId) body.userId = userId
  return post(`${BASE}/config/submit`, body, {
    showLoading: false,
    loadingText: '提交中...',
  })
}

/** 多方案对比 */
export async function compareConfigSchemes({
  draft = null,
  patches = null,
  fees = null,
  text = null,
  marketScale = null,
} = {}) {
  const body = {}
  if (draft) body.draft = draft
  if (patches) body.patches = patches
  if (fees) body.fees = fees
  if (text) body.text = text
  if (marketScale != null) body.marketScale = marketScale
  return post(`${BASE}/config/compare`, body, {
    showLoading: false,
    loadingText: '多方案对比中...',
  })
}

export async function getConfigTrace(traceId) {
  return get(`${BASE}/config/trace`, {
    params: { trace_id: traceId },
    showLoading: false,
  })
}

export async function explainConfig(traceId, audience = 'business') {
  return post(
    `${BASE}/config/explain`,
    { trace_id: traceId, audience },
    { showLoading: false, loadingText: '生成审计说明...' },
  )
}

export async function analyzeRootCause(offeringId = null, text = null) {
  const body = {}
  if (offeringId) body.offeringId = offeringId
  if (text) body.text = text
  return post(`${BASE}/ops/root-cause`, body, { showLoading: false, loadingText: '根因推理中...' })
}

export async function auditRisks(offeringIds = null) {
  return post(`${BASE}/ops/risk-audit`, { offeringIds }, { showLoading: false, loadingText: '风险稽核中...' })
}

/** 假设推演：退市 / 改价后重跑风险稽核 */
export async function evaluateHypothetical({ mode = 'delist', patches = null, offeringId = null, changes = null, monthlyFee = null } = {}) {
  const body = { mode }
  if (patches) body.patches = patches
  if (offeringId) body.offeringId = offeringId
  if (changes) body.changes = changes
  if (monthlyFee != null) body.monthlyFee = monthlyFee
  return post(`${BASE}/ops/hypothetical`, body, { showLoading: false, loadingText: '假设推演中...' })
}

/**
 * 立项/策略多方案对比（替代旧 /api/v1/product-ops/compare）
 */
export async function comparePolicyState({
  snapshotId = 'current',
  patches = null,
  policySetId = 'PS_PRODUCT_ONLINE_V1',
  currentFacts = null,
  description = '假设变更',
  changes = null,
  entityId = null,
  traceId = 'product-compare-trace',
  tenantId = 'product_ops',
} = {}) {
  const body = {
    snapshot_id: snapshotId,
    policy_set_id: policySetId,
    trace_id: traceId,
    tenant_id: tenantId,
    patches: patches || [{
      description,
      changes: changes || {},
      entity_id: entityId,
    }],
  }
  if (currentFacts) body.current_facts = currentFacts
  return post(`${BASE}/ops/compare`, body, { showLoading: false, loadingText: '方案对比中...' })
}

export async function listOpsAlerts(offeringId = null) {
  const params = {}
  if (offeringId) params.offering_id = offeringId
  return get(`${BASE}/ops/alerts`, { params, showLoading: false })
}

export async function listWorkOrders({ status = null, sessionId = null, page = null, size = null, q = null } = {}) {
  const params = {}
  if (status && status !== 'all') params.status = status
  if (sessionId) params.session_id = sessionId
  if (page) params.page = page
  if (size) params.size = size
  if (q) params.q = q
  return get(`${BASE}/ops/work-orders`, { params, showLoading: false })
}

export async function createWorkOrder(payload = {}) {
  return post(`${BASE}/ops/work-orders`, payload, { showLoading: false, loadingText: '生成处置工单...' })
}

export async function updateWorkOrderStatus(workOrderId, status, remark = null) {
  const body = { status }
  if (remark) body.remark = remark
  return put(`${BASE}/ops/work-orders/${encodeURIComponent(workOrderId)}`, body, {
    showLoading: false,
    loadingText: '更新工单状态...',
  })
}

export async function runBatchRiskAudit(trigger = 'manual') {
  return post(`${BASE}/ops/batch-audit`, { trigger }, { showLoading: false, loadingText: '全量批量稽核中...' })
}

export async function getLastBatchAudit() {
  return get(`${BASE}/ops/batch-audit`, { showLoading: false })
}

export async function updateRiskRules(overrides = {}) {
  return post(`${BASE}/ops/risk-rules`, overrides, { showLoading: false })
}

export async function resetRiskRules() {
  return post(`${BASE}/ops/risk-rules/reset`, {}, { showLoading: false })
}

export async function getOpsRules() {
  return get(`${BASE}/ops/rules`, { showLoading: false })
}

export async function reloadOpsRules() {
  return post(`${BASE}/ops/rules/reload`, {}, { showLoading: false, loadingText: '重载规则中...' })
}

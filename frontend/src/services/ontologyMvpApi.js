import { get, post } from './httpClient.js'

const BASE = 'ontology-mvp'

export async function getOntologyGraph() {
  return get(`${BASE}/graph`, { showLoading: false })
}

export async function getOntologyMeta() {
  return get(`${BASE}/meta`, { showLoading: false })
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

/** 一键复制为草稿并合规校验 */
export async function copyAsDraft(offeringId, text = null) {
  return post(
    `${BASE}/config/copy-as-draft`,
    { offering_id: offeringId, offeringId, text },
    { showLoading: false, loadingText: '复制配置草稿...' },
  )
}

/** 智读：上传文件批量映射（docx/pdf/xlsx/txt） */
export async function batchFromUpload(file) {
  const form = new FormData()
  form.append('file', file)
  // 不设 Content-Type，由浏览器自动带 multipart boundary
  return post(`${BASE}/config/batch-upload`, form, {
    showLoading: false,
    loadingText: '解析文档并映射...',
  })
}

/** 知识沉淀：合规草稿写入事实图/本体 */
export async function publishConfigDraft(draft) {
  return post(
    `${BASE}/config/publish`,
    { draft },
    { showLoading: false, loadingText: '沉淀至本体...' },
  )
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

export async function updateRiskRules(overrides = {}) {
  return post(`${BASE}/ops/risk-rules`, overrides, { showLoading: false })
}

export async function resetRiskRules() {
  return post(`${BASE}/ops/risk-rules/reset`, {}, { showLoading: false })
}

export async function getOpsRules() {
  return get(`${BASE}/ops/rules`, { showLoading: false })
}

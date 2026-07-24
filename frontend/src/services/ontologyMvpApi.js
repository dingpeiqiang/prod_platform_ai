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

export async function checkCompliance(draft) {
  return post(`${BASE}/config/compliance`, { draft }, { showLoading: false })
}

export async function batchFromDocument(documentText = '', packages = null) {
  return post(
    `${BASE}/config/batch`,
    { documentText, packages },
    { showLoading: false, loadingText: '文档映射中...' },
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

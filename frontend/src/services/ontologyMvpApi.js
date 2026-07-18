import { get, post } from './httpClient.js'
import {
  chatConfigureLocal,
  checkComplianceLocal,
  batchFromDocumentLocal,
  analyzeRootCauseLocal,
  auditRisksLocal,
  updateRiskRulesLocal,
  getOpsDashboardLocal,
} from './ontologyMvpLocal.js'

const BASE = 'ontology-mvp'

async function withLocalFallback(remoteCall, localCall) {
  try {
    return await remoteCall()
  } catch (e) {
    console.warn('[ontology-mvp] 后端不可用，切换本地推理:', e.message)
    return localCall()
  }
}

export async function getOntologyGraph() {
  return withLocalFallback(
    () => get(`${BASE}/graph`, { showLoading: false }),
    () => ({
      success: true,
      scenarios: ['家庭融合', '校园体验', '5G个人主套餐'],
      templates: ['TPL-HF-128', 'TPL-CAMPUS-59'],
      shelfCount: 80,
      ruleVersion: 'RiskRules-v1.2',
      shelfOfferings: [
        { offeringId: 'OF-HF-128', offeringName: '家庭融合畅享128' },
        { offeringId: 'OF-RISK-001', offeringName: '校园体验流量包0元' },
        { offeringId: 'OF-LOW-019', offeringName: '旧版彩铃包-2019' },
      ],
      local: true,
    }),
  )
}

export async function getOntologyMeta() {
  return withLocalFallback(
    () => get(`${BASE}/meta`, { showLoading: false }),
    () => ({
      success: true,
      classes: [
        { classCode: 'OfferingConfig', className: '商品配置草稿' },
        { classCode: 'ConfigRule', className: '配置规则' },
        { classCode: 'BizScenario', className: '业务场景' },
      ],
      local: true,
    }),
  )
}

export async function getOpsDashboard() {
  return withLocalFallback(
    () => get(`${BASE}/ops/dashboard`, { showLoading: false }),
    () => getOpsDashboardLocal(),
  )
}

export async function chatConfigure(text, draft = null) {
  return withLocalFallback(
    () => post(`${BASE}/config/chat`, { text, draft }, { showLoading: false, loadingText: '本体推理中...' }),
    () => chatConfigureLocal(text, draft),
  )
}

export async function checkCompliance(draft) {
  return withLocalFallback(
    () => post(`${BASE}/config/compliance`, { draft }, { showLoading: false }),
    () => checkComplianceLocal(draft),
  )
}

export async function batchFromDocument(documentText = '', packages = null) {
  return withLocalFallback(
    () => post(
      `${BASE}/config/batch`,
      { documentText, packages },
      { showLoading: false, loadingText: '文档映射中...' },
    ),
    () => batchFromDocumentLocal(documentText, packages),
  )
}

export async function analyzeRootCause(offeringId = 'OF-HF-128') {
  return withLocalFallback(
    () => post(`${BASE}/ops/root-cause`, { offeringId }, { showLoading: false, loadingText: '根因推理中...' }),
    () => analyzeRootCauseLocal(offeringId),
  )
}

export async function auditRisks(offeringIds = null) {
  return withLocalFallback(
    () => post(`${BASE}/ops/risk-audit`, { offeringIds }, { showLoading: false, loadingText: '风险稽核中...' }),
    () => auditRisksLocal(offeringIds),
  )
}

export async function updateRiskRules(overrides = {}) {
  return withLocalFallback(
    () => post(`${BASE}/ops/risk-rules`, overrides, { showLoading: false }),
    () => updateRiskRulesLocal(overrides),
  )
}

export async function resetRiskRules() {
  return withLocalFallback(
    () => post(`${BASE}/ops/risk-rules/reset`, {}, { showLoading: false }),
    () => updateRiskRulesLocal({ reset: true }),
  )
}

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
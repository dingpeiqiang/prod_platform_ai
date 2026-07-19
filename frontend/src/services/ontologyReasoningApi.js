import { request } from './httpClient'

const ONTOLOGY_BASE = ''

export async function retrieveFacts(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/facts/retrieve`, {
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
    const resp = await request(`${ONTOLOGY_BASE}/policy/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行规则评估...'
    })
    return resp
  } catch (e) {
    console.error('规则评估失败:', e)
    return {
      success: false,
      message: e.message || '规则评估失败',
      decision: {
        verdict: 'deny',
        confidence: 0,
        triggered_rules: [],
        reason: '评估失败'
      }
    }
  }
}

export async function evaluatePolicyWithFacts(req, policy_set_id) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/evaluate`, {
      method: 'POST',
      data: { ...req, policy_set_id },
      showLoading: true,
      loadingText: '执行合并评估...'
    })
    return resp
  } catch (e) {
    console.error('合并评估失败:', e)
    return {
      success: false,
      message: e.message || '合并评估失败',
      decision: {
        verdict: 'deny',
        confidence: 0,
        triggered_rules: [],
        reason: '评估失败'
      }
    }
  }
}

export async function evaluateSwrl(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/swrl/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行SWRL推理...'
    })
    return resp
  } catch (e) {
    console.error('SWRL推理失败:', e)
    return {
      success: false,
      message: e.message || 'SWRL推理失败',
      results: [],
      fired_rule_ids: [],
      rules: []
    }
  }
}

export async function validateShacl(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/shacl/validate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行SHACL验证...'
    })
    return resp
  } catch (e) {
    console.error('SHACL验证失败:', e)
    return {
      success: false,
      message: e.message || 'SHACL验证失败',
      conforms: false,
      results: []
    }
  }
}

export async function compareState(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/compare-state`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行假设推理...'
    })
    return resp
  } catch (e) {
    console.error('假设推理失败:', e)
    return {
      success: false,
      message: e.message || '假设推理失败',
      comparisons: []
    }
  }
}

export async function hypotheticalEvaluate(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/hypothetical/evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行本体假设推理...'
    })
    return resp
  } catch (e) {
    console.error('本体假设推理失败:', e)
    return {
      success: false,
      message: e.message || '本体假设推理失败',
      facts: {},
      decision: {
        verdict: 'deny',
        confidence: 0,
        triggered_rules: [],
        reason: '评估失败'
      }
    }
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
    console.error('生成解释失败:', e)
    return {
      success: false,
      message: e.message || '生成解释失败',
      natural_language: '',
      referenced_rules: []
    }
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
      trace_id,
      tenant_id,
      steps: [],
      total_steps: 0
    }
  }
}

export async function nlQuery(question) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/nl/query`, {
      method: 'POST',
      data: { question },
      showLoading: true,
      loadingText: '执行自然语言查询...'
    })
    return resp
  } catch (e) {
    console.error('自然语言查询失败:', e)
    return {
      success: false,
      message: e.message || '自然语言查询失败',
      answer: ''
    }
  }
}

export async function nlDiscoverAndRetrieve(question, max_entities = 5) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/nl-discover`, {
      method: 'POST',
      data: { question, max_entities },
      showLoading: true,
      loadingText: '执行实体发现...'
    })
    return resp
  } catch (e) {
    console.error('实体发现失败:', e)
    return {
      success: false,
      message: e.message || '实体发现失败',
      nl_answer: '',
      entity_ids: [],
      sparql: '',
      raw_results: [],
      snapshot: null,
      facts_flat: {}
    }
  }
}

export async function quickEvaluate(req) {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/quick-evaluate`, {
      method: 'POST',
      data: req,
      showLoading: true,
      loadingText: '执行快捷评估...'
    })
    return resp
  } catch (e) {
    console.error('快捷评估失败:', e)
    return {
      success: false,
      message: e.message || '快捷评估失败',
      verdict: 'deny',
      triggered_rules: [],
      reason: '评估失败'
    }
  }
}

export async function getSchemaCatalog() {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/schema/catalog`, {
      method: 'GET',
      showLoading: true,
      loadingText: '加载Schema目录...'
    })
    return resp
  } catch (e) {
    console.error('加载Schema目录失败:', e)
    return {
      success: false,
      message: e.message || '加载Schema目录失败',
      classes: [],
      properties: []
    }
  }
}

export async function getPolicySets() {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/policy/sets`, {
      method: 'GET',
      showLoading: true,
      loadingText: '加载策略集...'
    })
    return resp
  } catch (e) {
    console.error('加载策略集失败:', e)
    return {
      success: false,
      message: e.message || '加载策略集失败',
      policy_sets: []
    }
  }
}

export async function getSwrlRules() {
  try {
    const resp = await request(`${ONTOLOGY_BASE}/swrl/rules`, {
      method: 'GET',
      showLoading: true,
      loadingText: '加载SWRL规则...'
    })
    return resp
  } catch (e) {
    console.error('加载SWRL规则失败:', e)
    return {
      success: false,
      message: e.message || '加载SWRL规则失败',
      rules: []
    }
  }
}
/**
 * 将 SSE / 历史 thinking 步骤规范成与研发助手一致的调度结构：
 * id / title / content / result / status / type
 *
 * 原则：后端已下发 title/content/result 时原样保留，禁止旧空壳模板覆盖。
 */

/**
 * 仅作历史兼容：旧消息无 id/title 时按 step 号回退。
 * @deprecated 批次②清理对象：库内 reasoning_full 无 title 老数据命中量为 0 后删除，禁止新增条目。
 */
const LEGACY_STEP_TEMPLATES = {
  1: { id: 'intent', title: '确认业务意图', type: 'llm', content: '确认业务意图' },
  2: { id: 'analyze', title: '业务分析', type: 'llm', content: '执行业务分析' },
  3: { id: 'conclude', title: '整理结论', type: 'llm', content: '汇总结果并生成说明' },
}

/**
 * 步骤 id / 类型 → 业务分类徽标（替代技术徽标「大模型处理/工具调用/本体推理」）。
 * ontology 步骤映射为「分析推理」，plan/intent 类为「理解需求」，其余 llm 默认「生成内容」。
 */
const ID_CATEGORY = {
  intent: 'understand',
  plan: 'understand',
  slots: 'understand',
  parse: 'understand',
  identify: 'understand',
  locate: 'lookup',
  retrieve: 'lookup',
  load: 'lookup',
  match: 'lookup',
  pull: 'lookup',
  recommend: 'lookup',
  rules: 'verify',
  ai: 'verify',
  scan: 'verify',
  evaluate: 'verify',
  decide: 'verify',
  reason: 'reason',
  drill: 'reason',
  conclude: 'generate',
  reply: 'generate',
  generate: 'generate',
}

export const CATEGORY_LABELS = {
  understand: '理解需求',
  lookup: '查资料',
  verify: '做校验',
  reason: '分析推理',
  generate: '生成内容',
}

/** 步骤业务分类：优先显式 category，其次按 id/type 推断。 */
export function resolveCategory(step) {
  if (step?.category && CATEGORY_LABELS[step.category]) return step.category
  const type = step?.type || ''
  if (type === 'ontology') return 'reason'
  if (type === 'tool') return 'lookup'
  const id = step?.id || ''
  if (ID_CATEGORY[id]) return ID_CATEGORY[id]
  return 'generate'
}

/**
 * @param {Object} raw SSE thinking 或历史 reasoning 条目
 * @returns {Object} 与 ThinkingProcessPanel / thinkingSchedule 兼容的步骤
 */
export function normalizeThinkingStep(raw = {}) {
  const content = String(raw.content || '').trim()
  const meta = { ...(raw.metadata || {}) }
  const stepNum = meta.step != null ? Number(meta.step) : null
  const phase = meta.phase || ''
  const isRunning = phase === 'running' || phase === 'waiting_llm' || raw.status === 'running'
  const legacyTpl = stepNum != null ? LEGACY_STEP_TEMPLATES[stepNum] : null

  const result =
    raw.result != null && raw.result !== ''
      ? raw.result
      : meta.intentLabel || null

  const id = raw.id || meta.scheduleId || legacyTpl?.id || (stepNum != null ? `step_${stepNum}` : null)

  const stepTypeRaw = raw.stepType || raw.type
  let type = 'llm'
  if (stepTypeRaw === 'ontology' || id === 'reason' || id === 'ontology') {
    type = 'ontology'
  } else if (stepTypeRaw === 'tool' || id === 'tool') {
    type = 'tool'
  } else if (stepTypeRaw && stepTypeRaw !== 'thinking' && stepTypeRaw !== 'llm') {
    type = stepTypeRaw
  }

  const hasBackendTitle = !!(raw.title && String(raw.title).trim())
  const title = hasBackendTitle
    ? String(raw.title).trim()
    : (legacyTpl?.title || (type === 'ontology' ? '分析推理' : type === 'tool' ? '业务处理' : '处理步骤'))

  // 后端已给 content 则保留；仅空时回退
  const resolvedContent = content || legacyTpl?.content || title

  // 统一 io：既有 step.io，也兼容顶层 input/output 透传（如「明确做法」步骤带 input）
  const rawIo = raw.io && typeof raw.io === 'object' ? raw.io : {}
  const hasRawIo = Object.keys(rawIo).length > 0
  const hasTopIo = raw.input != null || raw.output != null
  const io = hasRawIo
    ? rawIo
    : hasTopIo
      ? { input: raw.input != null ? raw.input : null, output: raw.output != null ? raw.output : null }
      : null

    return {
    id: id || undefined,
    type,
    title,
    content: resolvedContent,
    result,
    category: resolveCategory({ id, type, category: raw.category }),
    goal: raw.goal != null ? String(raw.goal) : null,
    manualHint: raw.manualHint || raw.manual_hint || null,
    status: isRunning ? 'running' : (raw.status === 'pending' ? 'pending' : 'done'),
    metadata: {
      ...meta,
      phase: isRunning ? (phase === 'waiting_llm' ? 'waiting_llm' : 'running') : 'done',
      scheduleId: id || meta.scheduleId,
    },
    io,
    details: raw.details || null,
    workflow: raw.workflow || null,
    segment: raw.segment || null,
    elapsed: raw.elapsed != null ? raw.elapsed : null,
    ontologyChain: raw.ontologyChain || null,
    ontologyPreview: raw.ontologyPreview || null,
    chainRevealCount: raw.chainRevealCount || 0,
    _waiting: isRunning && phase === 'waiting_llm',
    stepStartedAt: raw.stepStartedAt || raw.timestamp || null,
    waitingStartedAt: raw.waitingStartedAt || null,
    timestamp: raw.timestamp || Date.now(),
  }
}

/** 历史还原：全部收为 done，并补齐 title/id */
export function normalizeReasoningList(list = []) {
  return (list || []).map((step) => {
    const n = normalizeThinkingStep(step)
    return {
      ...n,
      status: 'done',
      _waiting: false,
      metadata: { ...(n.metadata || {}), phase: 'done' },
    }
  })
}

/** 流结束时把仍 running 的步骤收尾 */
export function finalizeReasoningList(list = []) {
  return (list || []).map((step) => {
    if (step.status === 'done' && !step._waiting) return step
    return {
      ...step,
      status: 'done',
      _waiting: false,
      metadata: { ...(step.metadata || {}), phase: 'done' },
    }
  })
}

/**
 * 将 rootCause 结果挂到 reason 步骤的 ontology 可视化字段。
 */
export function attachRootCauseOntology(steps, rootCause, builders = {}) {
  if (!rootCause || !steps?.length) return steps || []
  const { buildChain, buildPreview } = builders
  if (typeof buildChain !== 'function') return steps
  const chain = buildChain(rootCause)
  if (!chain) return steps
  const preview = typeof buildPreview === 'function' ? buildPreview(rootCause, chain) : null
  return steps.map((s) => {
    if (s.id !== 'reason' && s.type !== 'ontology') return s
    return {
      ...s,
      type: 'ontology',
      ontologyChain: s.ontologyChain || chain,
      ontologyPreview: s.ontologyPreview || preview,
    }
  })
}

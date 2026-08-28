/**
 * 将 SSE / 历史 thinking 步骤规范成与研发助手一致的调度结构：
 * id / title / content / result / status / type
 *
 * 原则：后端已下发 title/content/result 时原样保留，禁止旧空壳模板覆盖。
 */

/** 仅作历史兼容：旧消息无 id/title 时按 step 号回退 */
const LEGACY_STEP_TEMPLATES = {
  1: { id: 'intent', title: '确认业务意图', type: 'llm', content: '确认业务意图' },
  2: { id: 'analyze', title: '业务分析', type: 'llm', content: '执行业务分析' },
  3: { id: 'conclude', title: '整理结论', type: 'llm', content: '汇总结果并生成说明' },
}

const ID_TITLES = {
  intent: '确认业务意图',
  locate: '锁定分析对象',
  confirm: '异动确认',
  drill: '多维下钻',
  reason: '规则推理',
  conclude: '归因结论',
  scope: '锁定洞察范围',
  retrieve: '检索经营事实',
  aggregate: '聚合经营指标',
  pull: '拉取告警清单',
  grade: '告警分级统计',
  link: '关联处置工单',
  extract: '抽取方案假设',
  snapshot: '构建事实快照',
  evaluate: '合规与收益评估',
  load: '加载在架清单',
  match: '匹配风险规则集',
  scan: '全量扫描打分',
  facts: '抽取立项要素',
  policy: '选择策略集',
  decide: '规则引擎判定',
  identify: '识别请求',
  generate: '生成方案',
  infer: '字段推断',
  recommend: '历史推荐',
  assemble: '组装表单',
  rules: '规则引擎校验',
  ai: 'AI 辅助校验',
  reply: '生成回复',
  tool: '调用工具',
  skip: '跳过业务执行',
}

function pickResultFromContent(content) {
  if (!content) return null
  let m = content.match(/已确认业务意图[：:]\s*(.+)$/)
  if (m) return m[1].trim()
  m = content.match(/检索完成[，,].*共找到?\s*(\d+)\s*条/)
  if (m) return `命中 ${m[1]} 条`
  m = content.match(/方案对比完成[，,]\s*共\s*(\d+)\s*套/)
  if (m) return `对比 ${m[1]} 套方案`
  return null
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

  const extracted = pickResultFromContent(content)
  const result =
    raw.result != null && raw.result !== ''
      ? raw.result
      : extracted != null
        ? extracted
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
    : (ID_TITLES[id] || legacyTpl?.title || (type === 'ontology' ? '知识推理' : type === 'tool' ? '工具调用' : '处理步骤'))

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

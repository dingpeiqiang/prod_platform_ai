/**
 * 将 SSE / 历史 thinking 步骤规范成与研发助手一致的调度结构：
 * id / title / content / result / status / type
 */

const STEP_TEMPLATES = {
  1: { id: 'understand', title: '理解需求', type: 'llm', content: '理解您的业务问题' },
  2: { id: 'match', title: '匹配知识', type: 'llm', content: '匹配业务知识与规则库' },
  3: { id: 'intent', title: '意图识别', type: 'llm', content: '确认业务意图' },
  4: { id: 'start', title: '开始执行', type: 'llm', content: '启动对应业务处理' },
  5: { id: 'analyze', title: '业务分析', type: 'ontology', content: '执行分析与规则判定' },
  6: { id: 'reply', title: '整理结论', type: 'llm', content: '汇总结果并生成说明' },
}

function pickResultFromContent(content) {
  if (!content) return null
  let m = content.match(/已确认业务意图[：:]\s*(.+)$/)
  if (m) return m[1].trim()
  m = content.match(/开始执行「([^」]+)」/)
  if (m) return m[1].trim()
  m = content.match(/检索完成[，,].*共找到?\s*(\d+)\s*条/)
  if (m) return `命中 ${m[1]} 条`
  m = content.match(/方案对比完成[，,]\s*共\s*(\d+)\s*套/)
  if (m) return `对比 ${m[1]} 套方案`
  return null
}

function softenContent(content, tpl) {
  if (!content) return tpl?.content || ''
  if (/正在理解您的需求|正在分析用户输入/.test(content)) return tpl?.content || '理解您的业务问题'
  if (/匹配业务知识|构建意图识别|已加载.*本体/.test(content)) return tpl?.content || '匹配业务知识与规则库'
  if (/已确认业务意图|意图识别完成/.test(content)) return '确认业务意图'
  if (/开始执行「|分发到「/.test(content)) return '启动对应业务处理'
  if (/正在分析异动|正在基于图谱|正在检索|正在对比|正在按风险|正在评估|正在加载运营/.test(content)) {
    return content.replace(/\.\.\.$/, '') || (tpl?.content || content)
  }
  if (/分析完成|整理结论|组织答复|已就绪|检索完成|对比完成|排查完成|评估完成/.test(content)) {
    return tpl?.content || '汇总结果并生成说明'
  }
  return content
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
  const tpl = stepNum != null ? STEP_TEMPLATES[stepNum] : null

  const extracted = pickResultFromContent(content)
  const result =
    raw.result != null && raw.result !== ''
      ? raw.result
      : extracted != null
        ? extracted
        : meta.intentLabel || null

  const id = raw.id || tpl?.id || (stepNum != null ? `step_${stepNum}` : null)
  const type = raw.type === 'ontology' || tpl?.type === 'ontology'
    ? 'ontology'
    : (raw.type && raw.type !== 'thinking' ? raw.type : 'llm')

  return {
    id: id || undefined,
    type,
    title: raw.title || tpl?.title || (type === 'ontology' ? '业务分析' : '处理步骤'),
    content: softenContent(content, tpl) || content || (tpl?.content || ''),
    result,
    status: isRunning ? 'running' : (raw.status === 'pending' ? 'pending' : 'done'),
    metadata: {
      ...meta,
      phase: isRunning ? (phase === 'waiting_llm' ? 'waiting_llm' : 'running') : 'done',
      scheduleId: id || meta.scheduleId,
    },
    details: raw.details || null,
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

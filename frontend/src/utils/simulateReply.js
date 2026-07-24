/**
 * 模拟真实对话体验：
 * - 外层：思考过程（时间线逐步展开）
 * - 其中 type=ontology：展开本体推理链
 */
export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function splitChunks(text, size = 6) {
  if (!text) return []
  const chunks = []
  for (let i = 0; i < text.length; i += size) {
    chunks.push(text.slice(i, i + size))
  }
  return chunks
}

function normalizeStep(step) {
  if (typeof step === 'string') {
    return { type: 'llm', content: step }
  }
  return {
    type: step.type || 'llm',
    title: step.title || (step.type === 'ontology' ? '本体推理' : ''),
    content: step.content || '',
    ontologyChain: step.ontologyChain || null,
    ontologyPreview: step.ontologyPreview || null,
    focus: step.focus || '',
  }
}

/**
 * @param {Object} options
 * @param {Object} options.msg
 * @param {Array<string|Object>} options.thinkingSteps
 * @param {string} options.content
 * @param {Object} [options.formCard]
 * @param {Array} [options.queryResults]
 * @param {Function} [options.onTick]
 */
export async function playSimulatedReply({
  msg,
  thinkingSteps = [],
  content = '',
  formCard = null,
  queryResults = null,
  onTick,
  thinkDelay = 160,
  typeDelay = 8,
  /** 保留已有 thinking（用于请求进行中已展示的进度） */
  preserveReasoning = false,
  /** 跳过本体链逐步揭示，直接展示 */
  skipChainReveal = false,
}) {
  if (!msg) return

  msg.loading = true
  msg.done = false
  msg.showReasoning = true
  if (!preserveReasoning) {
    msg.reasoning = []
    msg.content = ''
    msg.streamText = ''
    msg.ontologyChain = null
  }
  onTick?.()

  for (const raw of thinkingSteps) {
    await sleep(thinkDelay)
    const step = normalizeStep(raw)
    const item = {
      type: step.type,
      title: step.title,
      content: step.content,
      ontologyChain: null,
      ontologyPreview: null,
      chainRevealCount: 0,
      focus: step.focus,
    }
    msg.reasoning = [...(msg.reasoning || []), item]
    onTick?.()

    if (step.type !== 'ontology') continue

    item.ontologyPreview = step.ontologyPreview
    item.ontologyChain = step.ontologyChain

    const chainTotal =
      step.ontologyChain?.hops?.length ||
      step.ontologyChain?.nodes?.length ||
      0

    if (skipChainReveal || chainTotal <= 1) {
      item.chainRevealCount = 0
      onTick?.()
      continue
    }

    for (let n = 1; n <= chainTotal; n++) {
      await sleep(Math.min(80, thinkDelay / 2))
      item.chainRevealCount = Math.min(chainTotal, n)
      onTick?.()
    }
    item.chainRevealCount = 0
    onTick?.()
  }

  msg.loading = false
  onTick?.()

  const chunks = splitChunks(content, 12)
  for (const chunk of chunks) {
    await sleep(typeDelay)
    msg.streamText = (msg.streamText || '') + chunk
    msg.content = msg.streamText
    onTick?.()
  }

  if (formCard) msg.formCard = formCard
  if (queryResults) msg.queryResults = queryResults
  msg.done = true
  msg.loading = false
  onTick?.()
}

export function createStreamingPlaceholder(genId) {
  return {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    reasoning: [],
    showReasoning: true,
    loading: true,
    done: false,
    type: 'chat',
    timestamp: Date.now(),
  }
}

import { ref } from 'vue'
import { sendMessageWithModel, loadMessages as apiLoadMessages, getSessions } from '../services/chatApi.js'
import { getEventHandler, getPostProcessor } from './useIntentRegistry.js'
import {
  attachRootCauseOntology,
  finalizeReasoningList,
  normalizeThinkingStep,
} from '../utils/normalizeThinkingStep.js'
import {
  buildRootCauseOntologyChain,
  buildRootCauseOntologyPreview,
} from '../services/productOntologyLocal.js'

function uid(prefix = 'msg') {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
}

function genSessionId() {
  return `sess_${Date.now().toString(36)}_${Math.random().toString(16).slice(2, 10)}`
}

/** 等待心跳原地刷新；同一步完成时替换进行中条目，避免刷屏 */
function preserveStepTiming(prev, next) {
  const startedAt = prev?.waitingStartedAt || prev?.stepStartedAt || prev?.timestamp || next.timestamp || Date.now()
  return {
    ...next,
    stepStartedAt: prev?.stepStartedAt || prev?.timestamp || startedAt,
    waitingStartedAt: prev?.waitingStartedAt || (next._waiting || next.metadata?.phase === 'waiting_llm' ? startedAt : undefined),
    timestamp: startedAt,
  }
}

/** 后端若未回传耗时（或写死 0），用本地 stepStartedAt 回退估算 */
function resolveElapsedSeconds(prev, next) {
  if (next?.elapsed != null && next.elapsed > 0) return next.elapsed
  const startedAt = prev?.waitingStartedAt || prev?.stepStartedAt || prev?.timestamp || next?.stepStartedAt
  if (!startedAt) return next?.elapsed ?? null
  const sec = Math.max(0, (Date.now() - startedAt) / 1000)
  return Math.round(sec * 1000) / 1000
}

function mergeReasoningStep(steps, value) {
  const normalized = normalizeThinkingStep(value)
  const list = [...(steps || [])]
  const meta = normalized.metadata || {}
  const isWaiting = meta.phase === 'waiting_llm' || normalized._waiting

  if (isWaiting) {
    const step = { ...normalized, _waiting: true, status: 'running' }
    const waitingIdx = list.findIndex(
      (s) => s._waiting || s.metadata?.phase === 'waiting_llm',
    )
    if (waitingIdx >= 0) {
      list[waitingIdx] = preserveStepTiming(list[waitingIdx], step)
      return list
    }
    const byId = step.id ? list.findIndex((s) => s.id === step.id) : -1
    if (byId >= 0) {
      list[byId] = preserveStepTiming(list[byId], step)
      return list
    }
    const runningIdx = list.findIndex(
      (s) => s.metadata?.step === meta.step && (s.metadata?.phase === 'running' || s.status === 'running'),
    )
    if (runningIdx >= 0) {
      list[runningIdx] = preserveStepTiming(list[runningIdx], step)
      return list
    }
    const ts = value.timestamp || Date.now()
    list.push({ ...step, stepStartedAt: ts, waitingStartedAt: ts, timestamp: ts })
    return list
  }

  const withoutWaiting = list.filter(
    (s) => !(s._waiting || s.metadata?.phase === 'waiting_llm'),
  )

  // 同 id / 同 step 原地更新（对齐研发助手「一条时间线」）
  const byId = normalized.id
    ? withoutWaiting.findIndex((s) => s.id === normalized.id)
    : -1
  if (byId >= 0) {
    const merged = preserveStepTiming(withoutWaiting[byId], normalized)
    merged.elapsed = resolveElapsedSeconds(withoutWaiting[byId], normalized)
    withoutWaiting[byId] = merged
    return withoutWaiting
  }

  const lastIdx = withoutWaiting.length - 1
  if (
    lastIdx >= 0
    && meta.step != null
    && withoutWaiting[lastIdx].metadata?.step === meta.step
    && (withoutWaiting[lastIdx].metadata?.phase === 'running' || withoutWaiting[lastIdx].status === 'running')
  ) {
    const merged = preserveStepTiming(withoutWaiting[lastIdx], normalized)
    merged.elapsed = resolveElapsedSeconds(withoutWaiting[lastIdx], normalized)
    withoutWaiting[lastIdx] = merged
    return withoutWaiting
  }

  const ts = value.timestamp || Date.now()
  withoutWaiting.push({ ...normalized, stepStartedAt: ts, timestamp: ts })
  return withoutWaiting
}

export function useChatStream() {
  const messages = ref([])
  const streaming = ref(false)
  const abortRef = ref(null)
  const sessionId = ref('')
  const sessionList = ref([])

  const pushUserMessage = (text, meta = {}) => {
    const msg = {
      id: uid('user'),
      role: 'user',
      type: 'chat',
      content: text,
      done: true,
      timestamp: Date.now(),
      ...meta,
    }
    messages.value = [...messages.value, msg]
    return msg
  }

  const upsertAssistantMessage = (patch) => {
    const list = [...messages.value]
    let idx = list.findIndex(m => m.role === 'assistant' && !m.done)
    if (idx === -1) {
      list.push({
        id: uid('ai'),
        role: 'assistant',
        type: 'chat',
        content: '',
        streamText: '',
        reasoning: [],
        showReasoning: true,
        loading: true,
        done: false,
        timestamp: Date.now(),
        intentType: '',
        intentData: null,
        contentType: '',
        stats: null,
      })
      idx = list.length - 1
    }
    const current = { ...list[idx] }
    for (const [key, value] of Object.entries(patch)) {
      if (key === 'reasoningStep') {
        current.reasoning = mergeReasoningStep(current.reasoning, value)
        current.showReasoning = true
      } else {
        current[key] = value
      }
    }
    list[idx] = current
    messages.value = list
    return list[idx]
  }

  const enrichReasoningWithRootCause = (reasoning, intentData) => {
    const root = intentData?.rootCause || intentData?.data?.rootCause
    if (!root) return reasoning
    return attachRootCauseOntology(reasoning || [], root, {
      buildChain: buildRootCauseOntologyChain,
      buildPreview: buildRootCauseOntologyPreview,
    })
  }

  const applyIntentEvent = (data) => {
    const type = data.intentType || data.type
    const handler = getEventHandler(type)
    const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
    if (handler) handler(data, current)
    const intentData = { ...(current.intentData || {}), ...(data.data || data) }
    upsertAssistantMessage({
      intentType: type,
      action: data.action || current.action || '',
      intentData,
      stats: { ...(current.stats || {}), ...(data.stats || {}) },
      reasoning: enrichReasoningWithRootCause(current.reasoning, intentData),
    })
  }

  // ── 会话管理 ──────────────────────────────────────

  /** 加载会话列表（供侧边栏使用） */
  const loadSessions = async (userId = '', limit = 50) => {
    try {
      sessionList.value = await getSessions(userId, limit)
    } catch { /* 静默 */ }
  }

  /** 切换到指定会话：清空当前消息，从后端加载历史，并回放意图后处理 */
  const switchSession = async (targetSessionId) => {
    if (!targetSessionId) return []
    if (streaming.value) return messages.value
    sessionId.value = targetSessionId
    messages.value = []
    try {
      const historyMsgs = await apiLoadMessages(targetSessionId)
      if (historyMsgs.length) {
        messages.value = historyMsgs
        // 回放最近一条带意图的助手消息，恢复右侧面板
        for (let i = historyMsgs.length - 1; i >= 0; i--) {
          const m = historyMsgs[i]
          if (m.role === 'assistant' && m.intentType) {
            const post = getPostProcessor(m.intentType)
            if (post) post(m, m)
            break
          }
        }
      }
    } catch { /* 静默 */ }
    return messages.value
  }

  /**
   * 新建本地空会话（不落库）。
   * 会话仅在首条有效消息发送并由后端持久化后出现在历史列表。
   */
  const newSession = async () => {
    if (streaming.value) return
    sessionId.value = ''
    messages.value = []
  }

  /** 获取当前 sessionId（供发送消息时传入后端） */
  const getSessionId = () => sessionId.value

  // ── 消息发送 ──────────────────────────────────────

  const sendMessage = async ({ text, scene = '', modelConfig = null, history = null, attachments = [] }) => {
    const content = (text || '').trim()
    if ((!content && !attachments?.length) || streaming.value) return
    streaming.value = true
    // 首条有效消息时再分配 sessionId，避免空会话进入历史
    if (!sessionId.value) {
      sessionId.value = genSessionId()
    }
    const displayAttachments = (attachments || []).map((a) => ({
      type: a.type || 'file',
      name: a.name || '附件',
      size: a.size,
      preview: a.preview || null,
      url: a.url || null,
      duration: a.duration,
    }))
    const userContent = content || `导入文档：${displayAttachments[0]?.name || '附件'}`
    pushUserMessage(userContent, {
      attachments: displayAttachments,
    })
    let streamText = ''

    try {
      const autoHistory = history != null ? history : messages.value.slice(-20).map(m => ({
        role: m.role === 'assistant' ? 'assistant' : 'user',
        content: m.streamText || m.content || '',
      })).filter(m => String(m.content || '').trim())
      const payload = [
        ...autoHistory,
        { role: 'user', content: userContent },
      ]

      upsertAssistantMessage({ loading: true, streamText: '' })

      // 传 sessionId 给后端，让后端做持久化
      const resp = await sendMessageWithModel(payload, {
        modelConfig,
        scene,
        sessionId: sessionId.value,
      })
      abortRef.value = resp?.abortCtrl || null
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop()

        for (const frame of frames) {
          if (!frame.startsWith('data:')) continue
          try {
            const data = JSON.parse(frame.slice(5).trim())
            if (data.type === 'session' && data.sessionId) {
              sessionId.value = data.sessionId
            } else if (data.type === 'thinking') {
              upsertAssistantMessage({
                reasoningStep: {
                  type: data.stepType || 'thinking',
                  id: data.id || data.metadata?.scheduleId || undefined,
                  title: data.title || undefined,
                  stepType: data.stepType || undefined,
                  content: data.content,
                  metadata: data.metadata || {},
                  elapsed: data.elapsed != null ? data.elapsed : null,
                  details: data.details || null,
                  result: data.result || null,
                  ontologyChain: data.ontologyChain || null,
                  ontologyPreview: data.ontologyPreview || null,
                  timestamp: Date.now(),
                },
              })
            } else if (data.type === 'text') {
              streamText += data.content || ''
              upsertAssistantMessage({ streamText, content: streamText, loading: false })
            } else if (data.type === 'stats') {
              upsertAssistantMessage({ stats: data })
            } else if (data.type === 'done') {
              const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
              // done 事件的 intentType 来自后端 Handler，优先级最高
              const doneIntent = data.intentType
                || (data.intentData && data.intentData.intentType)
                || current.intentType
                || ''
              const doneAction = (data.intentData && data.intentData.action) || data.action || current.action || ''
              const doneStats = {
                ...(current.stats || {}),
                ...((data.intentData && data.intentData.stats) || {}),
                ...(data.stats || {}),
              }
              const doneIntentData = {
                ...(current.intentData || {}),
                ...(data.intentData || {}),
              }
              if (data.sessionId) {
                sessionId.value = data.sessionId
              }
              const finalText = streamText || current.streamText || current.content || ''
              upsertAssistantMessage({
                done: true,
                loading: false,
                streamText: finalText,
                content: finalText,
                reasoning: enrichReasoningWithRootCause(
                  finalizeReasoningList(current.reasoning || []),
                  doneIntentData,
                ),
                intentType: doneIntent,
                action: doneAction,
                stats: doneStats,
                intentData: doneIntentData,
                contentType: data.contentType || 'chat',
                // 翻译层产物：理解层查询计划 + 执行层证据摘要
                queryPlan: current.queryPlan || data.queryPlan || null,
                evidence: current.evidence || data.evidence || null,
              })
            } else if (data.type === 'query_plan') {
              // 翻译层理解产物：查询计划（中间语言）
              upsertAssistantMessage({ queryPlan: data.queryPlan || null })
            } else if (data.type === 'intent') {
              applyIntentEvent(data)
            } else if (data.type === 'product_ops_query' || data.type === 'product_ops_policy' || data.type === 'product_ops_reason' || data.type === 'product_ops_compare') {
              applyIntentEvent({ ...data, intentType: data.type })
            }
          } catch (err) {
            console.warn('[useChatStream] SSE frame parse error:', err)
          }
        }
      }

      const finalMsg = messages.value.find(m => m.role === 'assistant' && m.done)
        || messages.value.find(m => m.role === 'assistant' && !m.done)
        || messages.value[messages.value.length - 1]
      if (finalMsg) {
        const post = getPostProcessor(finalMsg.intentType)
        if (post) post(finalMsg, finalMsg)
      }

      if (!messages.value.some(m => m.role === 'assistant' && m.done)) {
        upsertAssistantMessage({
          done: true,
          loading: false,
          content: streamText,
          streamText,
          reasoning: finalizeReasoningList(
            (messages.value.find(m => m.role === 'assistant' && !m.done) || {}).reasoning || [],
          ),
        })
      }
      // 首条消息落库后刷新侧边栏，空会话不会出现在列表
      await loadSessions()
    } catch (e) {
      console.warn('[useChatStream] sendMessage error:', e)
      upsertAssistantMessage({
        done: true,
        loading: false,
        content: streamText || '处理过程中出现异常，请稍后重试。',
        streamText: streamText || '处理过程中出现异常，请稍后重试。',
      })
    } finally {
      streaming.value = false
      abortRef.value = null
    }
  }

  const stop = () => {
    if (abortRef.value && typeof abortRef.value.abort === 'function') {
      abortRef.value.abort()
    }
    streaming.value = false
    const current = messages.value.find(m => m.role === 'assistant' && !m.done)
    if (current) {
      const text = current.streamText || current.content || ''
      upsertAssistantMessage({
        done: true,
        loading: false,
        content: text,
        streamText: text,
        reasoning: finalizeReasoningList(current.reasoning || []),
        queryPlan: current.queryPlan || null,
        evidence: current.evidence || null,
      })
    }
  }

  return {
    messages,
    streaming,
    sessionId,
    sessionList,
    sendMessage,
    stop,
    loadSessions,
    switchSession,
    newSession,
    getSessionId,
  }
}

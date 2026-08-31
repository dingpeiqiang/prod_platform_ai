import { ref } from 'vue'
import { sendAgentStream, loadMessages as apiLoadMessages, getSessions } from '../services/chatApi.js'
import { getEventHandler, getPostProcessor } from './useIntentRegistry.js'
import {
  attachRootCauseOntology,
  finalizeReasoningList,
  normalizeThinkingStep,
} from '../utils/normalizeThinkingStep.js'
import { toolLabel, intentLabel } from '../utils/businessLabels.js'
import {
  buildRootCauseOntologyChain,
  buildRootCauseOntologyPreview,
} from '../services/productOntologyLocal.js'

function uid(prefix = 'msg') {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
}

/**
 * rAF 批量刷新器：SSE chunk 到达频率高于渲染帧率时合并为每帧一次更新，
 * 避免高频 text 事件导致的重复渲染抖动；流结束后冲刷残余缓冲。
 */
function createRafFlusher(flush) {
  let rafId = 0
  const schedule = () => {
    if (rafId) return
    rafId = requestAnimationFrame(() => {
      rafId = 0
      flush()
    })
  }
  const cancel = () => {
    if (rafId) cancelAnimationFrame(rafId)
    rafId = 0
  }
  return { schedule, cancel }
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

function mergeReasoningStep(steps, value) {  const normalized = normalizeThinkingStep(value)
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

  /** 意图类型 → 执行态（v3.2）：分析/扫描类意图完成后即「已执行」；后端可经 actionState 覆盖 */
  const defaultActionState = (type) => {
    if (['product_ops_policy', 'product_ops_reason', 'product_ops_monitor', 'product_ops_compare', 'product_ops_query'].includes(type)) {
      return 'executed'
    }
    return null
  }

  const applyIntentEvent = (data) => {
    const type = data.intentType || data.type
    const handler = getEventHandler(type)
    const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
    if (handler) handler(data, current)
    const intentData = {
      ...(current.intentData || {}),
      ...(data.data || data),
      ...(data.actionState ? { actionState: data.actionState } : {}),
    }
    upsertAssistantMessage({
      intentType: type,
      action: data.action || current.action || '',
      intentData,
      // SSE intentData 透传 actionState（后端覆盖优先），否则按意图给默认执行态
      actionState: data.actionState || defaultActionState(type) || current.actionState || null,
      stats: { ...(current.stats || {}), ...(data.stats || {}) },
      reasoning: enrichReasoningWithRootCause(current.reasoning, intentData),
    })
  }

  // ── 翻译层事件（/api/v1/agent/chat/stream）──────────────────

  /**
   * 翻译层事件分流（真流式：后端边执行边推送，前端按到达顺序渐进渲染）：
   * thinking（理解中→计划确认→生成中 多阶段）→ tool（running/done）→ text* → done（澄清时 clarify）。
   * tool 事件同时写入 msg.toolResults（ToolResultPanel）与思考时间线（running → done 原地收尾）。
   */

  /** 新阶段事件到达时，把仍在 running 的思考类步骤收尾（保持时间线状态推进） */
  const settleThinkingSteps = (reasoning) => (reasoning || []).map((s) =>
    s.type === 'thinking' && s.status === 'running' && !s._waiting
      ? { ...s, status: 'done', metadata: { ...(s.metadata || {}), phase: 'done' } }
      : s,
  )

  const applyAgentEvent = async (eventName, data) => {
    if (eventName === 'thinking') {
      const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
      const intentType = data.intent || current.intentType || ''
      const step = (data.steps && data.steps[0]) || {}
      const stepTitle = step.title || step.label || '正在处理...'
      // 后端已给出业务化内容（理解/方案/汇总），否则按意图补一句
      const stepContent =
        step.content
        || (intentType && intentType !== 'CLARIFY' ? `已确认业务意图：${intentLabel(intentType)}` : undefined)
      const stepIo = step.io && Object.keys(step.io).length ? step.io : null
      const stepTopInput = step.input != null ? step.input : null
      const stepTopOutput = step.output != null ? step.output : null
      const stepFactoryIo = stepIo || (stepTopInput != null || stepTopOutput != null
        ? { input: stepTopInput, output: stepTopOutput }
        : null)
      upsertAssistantMessage({
        intentType,
          reasoning: settleThinkingSteps(current.reasoning),
          reasoningStep: {
            type: 'thinking',
            id: step.id || undefined,
            title: stepTitle,
            content: stepContent,
            status: 'running',
            io: stepFactoryIo,
            workflow: step.workflow || null,
            segment: step.segment || null,
            goal: step.goal || null,
            category: step.category || null,
            metadata: {},
            timestamp: Date.now(),
          },
      })
    } else if (eventName === 'tool') {
      const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
      const status = data.status === 'running' ? 'running' : (data.status === 'error' ? 'error' : 'done')
      const list = [...(current.toolResults || [])]
      const toolEntry = {
        name: data.name || '',
        displayName: toolLabel(data.name) || data.name || '',
        status,
        elapsed: data.durationMs != null ? data.durationMs / 1000 : undefined,
        result: status === 'running' ? null : (data.summary || null),
        error: data.errorMessage || null,
        params: status === 'running' ? null : (data.input || null),
        output: status === 'running' ? null : (data.output || null),
      }
      const idx = list.findIndex(t => t.name === toolEntry.name)
      if (idx >= 0) {
        list[idx] = { ...list[idx], ...toolEntry }
      } else {
        list.push(toolEntry)
      }
      upsertAssistantMessage({
        toolResults: list,
        // 工具开始执行即代表思考/计划阶段完成，收尾仍在 running 的思考步骤
        reasoning: settleThinkingSteps(current.reasoning),
        // 思考时间线同步推进：工具开始即出现（running），完成原地收尾（done/error + 耗时）
        reasoningStep: {
          id: `tool_${toolEntry.name}`,
          type: 'tool',
          title: data.title || toolEntry.displayName || toolEntry.name,
          content: status === 'running' ? '正在处理…' : undefined,
          goal: data.goal || null,
          manualHint: data.manualHint || data.manual_hint || null,
          result: status === 'done' ? (toolEntry.result || '执行完成') : (status === 'error' ? (toolEntry.error || '执行失败') : null),
          status: status === 'running' ? 'running' : 'done',
          elapsed: toolEntry.elapsed,
          segment: data.segment || null,
          io: status === 'running' ? null : {
            input: data.input || null,
            output: data.output || null,
          },
          timestamp: Date.now(),
        },
      })
    } else if (eventName === 'error') {
      const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
      const errorMsg = data.errorMessage || data.error || '服务异常'
      upsertAssistantMessage({
        agentError: errorMsg,
        done: true,
        loading: false,
        content: errorMsg,
        streamText: errorMsg,
        reasoning: finalizeReasoningList(current.reasoning || []),
      })
    }
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
      })
    }
  }

  // ── 翻译层消息发送（三阶架构：理解 → 执行 → 表达）──────────────

  /**
   * 通过翻译层统一入口发送消息（POST /api/v1/agent/chat/stream）。
   * <p>
   * 事件序列：thinking（查询计划）→ tool（工具卡片）→ text*（正文）→ done（结构化结果）。
   * CLARIFY 澄清分支：done 事件携带 clarify 参数列表，正文为追问文案。
   */
  const sendAgentMessage = async ({ text, sessionId: explicitSessionId = '', params = {}, scene = null } = {}) => {
    const content = (text || '').trim()
    if (!content || streaming.value) return
    streaming.value = true
    if (!sessionId.value) {
      sessionId.value = explicitSessionId || genSessionId()
    }
    pushUserMessage(content)
    let streamText = ''

    /** 正文 rAF 批量刷新：chunk 只累积到 streamText，渲染按帧合并 */
    const flushText = () => {
      if (!streamText) return
      // 流已由 done 事件收尾（done=true）时不再 flush，否则会把全文重复 push 成第二条助手消息
      const inProgress = messages.value.some(m => m.role === 'assistant' && !m.done)
      if (!inProgress) return
      upsertAssistantMessage({ streamText, content: streamText, loading: false })
    }
    const textFlusher = createRafFlusher(flushText)

    try {
      upsertAssistantMessage({ loading: true, streamText: '' })

      const { response, abortCtrl } = await sendAgentStream(content, { sessionId: sessionId.value, params, scene })
      abortRef.value = abortCtrl
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop()

        for (const frame of frames) {
          const lines = frame.split('\n')
          let eventName = ''
          let dataPayload = ''
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              dataPayload += line.slice(5).trim()
            }
          }
          if (!eventName || !dataPayload) continue
          try {
            const data = JSON.parse(dataPayload)
            if (eventName === 'text') {
              streamText += data.chunk || ''
              textFlusher.schedule()
            } else if (eventName === 'text_done' || eventName === 'done') {
              const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
              if (eventName === 'text_done') continue
              if (data.session_id || data.sessionId) {
                sessionId.value = data.session_id || data.sessionId
              }
              const finalText = streamText || current.streamText || current.content || ''
              const doneIntent = data.intent || current.intentType || ''
              upsertAssistantMessage({
                done: true,
                loading: false,
                streamText: finalText,
                content: finalText,
                intentType: doneIntent,
                actionState: current.actionState || data.actionState || defaultActionState(doneIntent) || null,
                // 澄清分支：待补充参数列表（前端据此展示追问上下文标签）
                clarify: data.clarify || current.clarify || [],
                // 澄清参数契约（U1 选择题化）：参数名 → {label, description, options}
                clarifyContracts: data.clarify_contracts || current.clarifyContracts || null,
                // 确认分支（U2）：需求歧义候选解读列表（与 CLARIFY 互斥渲染）
                confirm: Array.isArray(data.candidates) && data.candidates.length
                  ? { candidates: data.candidates }
                  : (current.confirm || null),
                queryPlan: current.queryPlan || null,
                toolResults: current.toolResults || [],
                suggestedFollowUps: data.suggested_follow_ups || data.suggestedFollowUps || [],
                reasoning: finalizeReasoningList(current.reasoning || []),
              })
              // 追问建议映射到 nextSteps 渲染：直接原地更新已完结消息，
              // 避免 upsertAssistantMessage 因找不到「未完成」assistant 而 push 出第二条助手消息。
              // 必须取最后一条助手消息（本轮流式收尾的那条）：会话开头可能有
              // sceneWelcome 等早已完结的助手消息，find 会错误命中并把意图后处理
              //（如工单内联挂载）落到首条消息上
              const doneMsg = [...messages.value].reverse().find(m => m.role === 'assistant' && m.done)
              const followUps = data.suggested_follow_ups || []
              if (doneMsg && followUps.length && !doneMsg.nextSteps?.length) {
                doneMsg.nextSteps = followUps
                messages.value = [...messages.value]
              }
              // 直播流收尾：触发意图后处理（驱动右侧面板），与历史回放路径一致
              if (doneMsg && doneIntent) {
                const post = getPostProcessor(doneIntent)
                if (post) post(doneMsg, doneMsg)
              }
            } else {
              await applyAgentEvent(eventName, data)
            }
          } catch (err) {
            console.warn('[useChatStream] agent SSE frame parse error:', err)
          }
        }
      }
      textFlusher.cancel()
      flushText()

      // 流意外结束但未收到 done：兜底收尾
      const pending = messages.value.find(m => m.role === 'assistant' && !m.done)
      if (pending) {
        upsertAssistantMessage({
          done: true,
          loading: false,
          content: streamText || pending.streamText || '',
          streamText: streamText || pending.streamText || '',
          reasoning: finalizeReasoningList(pending.reasoning || []),
        })
      }
      await loadSessions()
    } catch (e) {
      textFlusher.cancel()
      flushText()
      if (e?.name === 'AbortError') {
        streaming.value = false
        abortRef.value = null
        return
      }
      console.warn('[useChatStream] sendAgentMessage error:', e)
      upsertAssistantMessage({
        done: true,
        loading: false,
        content: streamText || '翻译层处理异常，请稍后重试。',
        streamText: streamText || '翻译层处理异常，请稍后重试。',
      })
    } finally {
      streaming.value = false
      abortRef.value = null
    }
  }

  return {
    messages,
    streaming,
    sessionId,
    sessionList,
    sendAgentMessage,
    stop,
    loadSessions,
    switchSession,
    newSession,
    getSessionId,
  }
}

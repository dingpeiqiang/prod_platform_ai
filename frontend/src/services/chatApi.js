/**
 * chatApi - 聊天记录持久化 + AI 原生流式意图处理
 */

import { normalizeReasoningList } from '../utils/normalizeThinkingStep.js'

const BASE = '/api/v2/chat'

function buildMessageMetadata(msg) {
  const metadata = { ...(msg.metadata || {}) }

  if (msg.reasoning !== undefined && Array.isArray(msg.reasoning)) {
    metadata.reasoning_full = JSON.stringify(msg.reasoning.map((step, index) => ({ ...step, _index: index })))
  }
  if (msg.reasoning !== undefined) {
    metadata.reasoning = Array.isArray(msg.reasoning)
      ? msg.reasoning.map(s => s.content || '').join('\n')
      : String(msg.reasoning)
  }

  if (msg.formId !== undefined) metadata.formId = msg.formId
  if (msg.formSchema !== undefined) metadata.formSchema = JSON.stringify(msg.formSchema)
  if (msg.formSubmitted !== undefined) metadata.formSubmitted = msg.formSubmitted
  if (msg.formCard) metadata.formCard = JSON.stringify(msg.formCard)

  if (msg.intentType || msg.intent_type) metadata.intent_type = msg.intentType || msg.intent_type
  if (msg.action) metadata.action = msg.action
  if (msg.intentData || msg.intent_data) {
    metadata.intent_data = typeof (msg.intentData || msg.intent_data) === 'string'
      ? (msg.intentData || msg.intent_data)
      : JSON.stringify(msg.intentData || msg.intent_data)
  }
  if (msg.formCode || msg.form_code) metadata.form_code = msg.formCode || msg.form_code
  if (msg.extractedFields || msg.extracted_fields) metadata.extracted_fields = msg.extractedFields || msg.extracted_fields
  if (msg.confidence != null) metadata.confidence = String(msg.confidence)
  if (msg.model) metadata.model = msg.model
  if (msg.streamText) metadata.stream_text = msg.streamText
  if (msg.done !== undefined) metadata.done = msg.done
  if (msg.contentType) metadata.content_type = msg.contentType

  // 翻译层产物：理解层查询计划
  if (msg.queryPlan) metadata.query_plan = JSON.stringify(msg.queryPlan)

  // 用户附件快照（fileId/名称随消息持久化，回放时气泡可显示附件）
  if (Array.isArray(msg.attachments) && msg.attachments.length) {
    metadata.attachments = JSON.stringify(msg.attachments.map((a) => ({
      id: a.id,
      type: a.type || 'file',
      name: a.name,
      size: a.size ?? null,
      fileId: a.fileId || a.file_id || null,
      fileName: a.fileName || a.name,
    })))
  }

  // 智读文档记忆锚（fileRef）：跨轮引用与「已引用文档」锚的历史快照
  if (msg.fileRef) {
    metadata.file_ref = JSON.stringify({
      fileName: msg.fileRef.fileName,
      fileId: msg.fileRef.fileId ?? null,
      fileIds: msg.fileRef.fileIds || [],
      counts: msg.fileRef.counts || null,
    })
  }

  return Object.keys(metadata).length > 0 ? metadata : null
}

function restoreMessageMetadata(meta = {}) {
  let reasoning = []
  if (meta.reasoning_full) {
    try {
      reasoning = typeof meta.reasoning_full === 'string'
        ? JSON.parse(meta.reasoning_full)
        : meta.reasoning_full
      if (Array.isArray(reasoning)) {
        reasoning.sort((a, b) => (a._index ?? 0) - (b._index ?? 0))
      } else {
        reasoning = []
      }
    } catch {
      reasoning = []
    }
  }
  if (!reasoning.length && meta.reasoning) {
    const raw = typeof meta.reasoning === 'string' ? meta.reasoning : String(meta.reasoning)
    reasoning = raw.split('\n').filter(Boolean).map((c, index) => ({
      type: 'llm',
      content: c,
      _index: index,
    }))
  }

  // 与研发助手同一套步骤结构，便于 ThinkingProcessPanel 展示
  reasoning = normalizeReasoningList(reasoning)

  let formSchema = null
  if (meta.formSchema !== undefined) {
    try {
      formSchema = typeof meta.formSchema === 'string' ? JSON.parse(meta.formSchema) : meta.formSchema
    } catch {}
  }

  let formCard = null
  if (meta.formCard) {
    try {
      formCard = typeof meta.formCard === 'string' ? JSON.parse(meta.formCard) : meta.formCard
    } catch {}
  }

  let intentData = null
  if (meta.intent_data != null) {
    try {
      intentData = typeof meta.intent_data === 'string' ? JSON.parse(meta.intent_data) : meta.intent_data
    } catch {
      intentData = meta.intent_data
    }
  }

  // 翻译层产物：理解层查询计划
  let queryPlan = null
  if (meta.query_plan != null) {
    try {
      queryPlan = typeof meta.query_plan === 'string' ? JSON.parse(meta.query_plan) : meta.query_plan
    } catch { queryPlan = meta.query_plan }
  }

  // 澄清参数契约（U1 选择题化）：从 query_plan / meta 中还原 clarify_contracts
  let clarifyContracts = null
  if (queryPlan?.clarify_contracts != null) {
    clarifyContracts = queryPlan.clarify_contracts
  } else if (meta.clarify_contracts != null) {
    try {
      clarifyContracts = typeof meta.clarify_contracts === 'string'
        ? JSON.parse(meta.clarify_contracts)
        : meta.clarify_contracts
    } catch { clarifyContracts = meta.clarify_contracts }
  }

  // 澄清参数列表（实时 done 事件 data.clarify 的历史快照）：还原为 msg.clarify
  let clarify = null
  const rawClarify = meta.clarify ?? queryPlan?.clarify
  if (rawClarify != null) {
    try {
      clarify = typeof rawClarify === 'string' ? JSON.parse(rawClarify) : rawClarify
    } catch { clarify = rawClarify }
    if (!Array.isArray(clarify) || !clarify.length) clarify = null
  }

  // 确认分支候选（实时 done 事件 data.candidates 的历史快照）：还原为 msg.confirm
  let confirm = null
  if (meta.candidates != null) {
    try {
      const candidates = typeof meta.candidates === 'string' ? JSON.parse(meta.candidates) : meta.candidates
      if (Array.isArray(candidates) && candidates.length) {
        confirm = { candidates }
      }
    } catch { confirm = null }
  }

  let extractedFields = meta.extracted_fields
  if (typeof extractedFields === 'string') {
    try { extractedFields = JSON.parse(extractedFields) } catch {}
  }

  // 工具执行卡片快照（后端 persistTurn 落库的 tool_results）：还原为 toolResults 供面板回放
  let toolResults = null
  if (meta.tool_results != null) {
    try {
      const parsed = typeof meta.tool_results === 'string' ? JSON.parse(meta.tool_results) : meta.tool_results
      if (Array.isArray(parsed)) {
        toolResults = parsed.map(t => ({
          name: t.name || '',
          displayName: t.title || t.name || '',
          status: t.status === 'error' ? 'error' : 'done',
          elapsed: t.elapsedMs != null ? t.elapsedMs / 1000 : undefined,
          result: t.summary || null,
          error: t.errorMessage || null,
          params: t.input || null,
          output: t.output || null,
          title: t.title || null,
          goal: t.goal || null,
          manualHint: t.manualHint || t.manual_hint || null,
        }))
      }
    } catch { toolResults = null }
  }

  const hasError = reasoning.some(r => r.type === 'error')

  // 用户附件快照：还原为 msg.attachments（历史回放气泡显示附件）
  let attachments = []
  if (meta.attachments != null) {
    try {
      const parsed = typeof meta.attachments === 'string' ? JSON.parse(meta.attachments) : meta.attachments
      if (Array.isArray(parsed)) attachments = parsed
    } catch { attachments = [] }
  }

  // S3-E 固定流程执行明细快照（后端 persistTurn 落库的 flow_matched/flow_execution）：
  // 历史回放还原为 msg.flowMatched / msg.flowExecution，与实时 done 事件字段同构
  const parseFlowField = (raw) => {
    if (raw == null) return null
    try {
      return typeof raw === 'string' ? JSON.parse(raw) : raw
    } catch { return null }
  }
  const flowMatched = parseFlowField(meta.flow_matched)
  const flowExecution = parseFlowField(meta.flow_execution)

  // 智读文档记忆锚：还原为 msg.fileRef（历史回放显示「已引用文档」锚，支持跨轮引用）
  let fileRef = null
  if (meta.file_ref != null) {
    try {
      fileRef = typeof meta.file_ref === 'string' ? JSON.parse(meta.file_ref) : meta.file_ref
    } catch { fileRef = null }
  }

  return {
    reasoning,
    showReasoning: hasError || reasoning.length > 0,
    done: true,
    intentType: meta.intent_type || intentData?.intentType || '',
    action: meta.action || intentData?.action || '',
    intentData,
    formCode: meta.form_code,
    extractedFields,
    confidence: meta.confidence,
    model: meta.model,
    contentType: meta.content_type || 'chat',
    streamText: meta.stream_text || '',
    formId: meta.formId,
    formSchema,
    formSubmitted: meta.formSubmitted === 'true' || meta.formSubmitted === true,
    formCard,
    stats: intentData?.stats || null,
    queryPlan,
    clarifyContracts,
    clarify: clarify || [],
    confirm,
    toolResults,
    flowMatched,
    flowExecution,
    attachments: attachments.length ? attachments : undefined,
    fileRef,
  }
}

export async function createSession(userId, title) {
  const resp = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ user_id: userId || undefined, title: title || '新对话' }),
  })
  const data = await resp.json()
  if (!resp.ok) return { success: false, error: data }
  return { success: true, session_id: data.session_id, ...data }
}

export async function saveMessage(sessionId, msg) {
  const content = String(msg.content || msg.streamText || '').trim()
  // 无实际内容的消息不落库，避免空历史
  if (!sessionId || !content) {
    return { success: false, skipped: true }
  }
  try {
    const metadata = buildMessageMetadata(msg)
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role: msg.role,
        content,
        content_type: msg.contentType || 'text',
        parent_id: msg.parentId || null,
        step_type: msg.step_type || null,
        metadata,
      }),
    })
    const data = await resp.json()
    return { success: resp.ok, message_id: data.message_id, ...data }
  } catch (e) {
    throw e
  }
}

export async function updateMessage(sessionId, messageId, { content, metadata }) {
  const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content: content || null, metadata: metadata || null }),
  })
  return resp.ok
}

export async function saveMessages(sessionId, messages) {
  if (!sessionId) return
  const done = messages.filter(m => {
    if (m.done === false) return false
    return String(m.content || m.streamText || '').trim().length > 0
  })
  if (!done.length) return
  if (done.length > 3) {
    const batchData = done.map(msg => ({
      role: msg.role,
      content: msg.content || msg.streamText || '',
      content_type: msg.contentType || 'text',
      parent_id: msg.parentId || null,
      metadata: buildMessageMetadata(msg),
    }))
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: batchData }),
    })
    if (!resp.ok) {
      for (const msg of done) await saveMessage(sessionId, msg)
    }
  } else {
    for (const msg of done) await saveMessage(sessionId, msg)
  }
}

export async function loadMessages(sessionId) {
  try {
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`)
    if (!resp.ok) return []
    const result = await resp.json()
    const msgs = result.messages || []
    return msgs.map(m => {
      const restored = restoreMessageMetadata(m.metadata || {})
      const content = m.content || restored.streamText || ''
      const streamText = restored.streamText || content
      // 占位正文「意图: xxx」时，优先用 intent 摘要补全展示
      let displayContent = content
      if (/^意图\s*[:：]/.test(String(content).trim()) && restored.intentData) {
        const d = restored.intentData
        displayContent = d.explanation || d.nl_answer || d.message || content
      }
      return {
        id: m.message_id,
        role: m.role === 'assistant' ? 'assistant' : 'user',
        ...restored,
        content: displayContent,
        streamText: restored.streamText || displayContent,
        done: true,
        loading: false,
        type: 'chat',
        parentId: m.parent_id,
        createdAt: m.created_at,
        timestamp: m.created_at ? Date.parse(m.created_at) || Date.now() : Date.now(),
        metadata: m.metadata,
      }
    }).filter(m => {
      const hasText = String(m.content || m.streamText || '').trim().length > 0
      return hasText || !!m.formCard || !!m.intentData || (m.reasoning && m.reasoning.length)
        || (Array.isArray(m.attachments) && m.attachments.length)
    })
  } catch {
    return []
  }
}

export async function getSessions(userId, limit = 50) {
  try {
    const resp = await fetch(`${BASE}/sessions?user_id=${encodeURIComponent(userId)}&limit=${limit}`)
    if (!resp.ok) return []
    const result = await resp.json()
    return result.sessions || []
  } catch {
    return []
  }
}

export async function deleteSession(sessionId) {
  await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
}

export async function updateSessionTitle(sessionId, title) {
  await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
}

export async function getSessionStats(sessionId) {
  const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/stats`)
  if (!resp.ok) return {}
  return resp.json()
}

/**
 * 翻译层统一流式入口（POST /api/v1/agent/chat/stream，SSE）。
 * <p>
 * 事件按执行阶段串行推送：thinking → tool → text* → text_done → done，
 * 与一次性 /api/v1/agent/chat 共用同一 AgentOrchestrator 编排。
 * 返回 { response, abortCtrl }，abortCtrl 可真正中止请求（fix：旧链路未返回导致 stop 软停止）。
 */
export async function sendAgentStream(question, { sessionId = '', params = {}, scene = null } = {}) {
  const abortCtrl = new AbortController()
  const body = { question }
  if (sessionId) body.session_id = sessionId
  if (scene) body.scene = scene
  if (params && typeof params === 'object' && Object.keys(params).length) body.params = params
  const resp = await fetch('/api/v1/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal: abortCtrl.signal,
  })
  if (!resp.ok) throw new Error('翻译层请求失败')
  return { response: resp, abortCtrl }
}

export async function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  const resp = await fetch(`${BASE}/upload`, { method: 'POST', body: formData })
  return resp.json()
}

export async function switchModel(modelConfig) {
  const resp = await fetch('/api/v1/chat/model/switch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(modelConfig),
  })
  return resp.json()
}

export async function getSupportedProviders() {
  const resp = await fetch('/api/v1/chat/model/providers')
  return resp.json()
}

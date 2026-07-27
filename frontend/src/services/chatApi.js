/**
 * chatApi - 聊天记录持久化 + AI 原生流式意图处理
 */

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
      type: 'thinking',
      content: c,
      _index: index,
    }))
  }

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

  let extractedFields = meta.extracted_fields
  if (typeof extractedFields === 'string') {
    try { extractedFields = JSON.parse(extractedFields) } catch {}
  }

  const hasError = reasoning.some(r => r.type === 'error')
  return {
    reasoning,
    showReasoning: hasError || reasoning.length > 0,
    done: meta.done === 'true' || meta.done === true || meta.done === undefined || true,
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
      const content = m.content || ''
      const streamText = restored.streamText || content
      return {
        id: m.message_id,
        role: m.role === 'assistant' ? 'assistant' : 'user',
        content,
        ...restored,
        // 历史还原：确保 MessageCard / IntentPanel 都能拿到正文与意图数据
        streamText,
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
      return hasText || !!m.formCard || !!m.intentData
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

export async function sendMessage({ sessionId, message, attachments, modelConfig, scene = '' }) {
  try {
    const body = {
      messages: [{ role: 'user', content: message }],
      session_id: sessionId,
      scene,
    }
    if (attachments?.length) body.attachments = attachments
    if (modelConfig) body.modelConfig = modelConfig

    const resp = await fetch('/api/v1/chat/agent/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })

    if (!resp.ok) throw new Error('AI 原生处理链请求失败')

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let content = ''

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
          if (data.type === 'thinking') {
            content += `\n> ${data.content}\n`
          } else if (data.type === 'text_start' || data.type === 'text_end') {
            continue
          } else if (data.type === 'text') {
            content += data.content || ''
          } else if (data.type === 'done') {
            return { content }
          }
        } catch {}
      }
    }

    return { content }
  } catch (e) {
    console.warn('[chatApi] sendMessage failed:', e)
    throw e
  }
}

export async function sendMessageWithModel(messages, { modelConfig = null, scene = '', sessionId = '' } = {}) {
  const body = { messages, scene }
  if (modelConfig) body.modelConfig = modelConfig
  if (sessionId) body.sessionId = sessionId
  const resp = await fetch('/api/v1/chat/agent/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!resp.ok) throw new Error('AI 原生处理链请求失败')
  return resp
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

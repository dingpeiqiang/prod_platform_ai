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
      reasoning = JSON.parse(meta.reasoning_full)
      reasoning.sort((a, b) => (a._index ?? 0) - (b._index ?? 0))
    } catch {}
  }
  if (!reasoning.length && meta.reasoning) {
    reasoning = meta.reasoning.split('\n').filter(Boolean).map((c, index) => ({
      type: 'thinking',
      content: c,
      _index: index,
    }))
  }

  let formSchema = null
  if (meta.formSchema !== undefined) {
    try {
      formSchema = JSON.parse(meta.formSchema)
    } catch {}
  }

  let formCard = null
  if (meta.formCard) {
    try {
      formCard = JSON.parse(meta.formCard)
    } catch {}
  }

  const hasError = reasoning.some(r => r.type === 'error')
  return {
    reasoning,
    showReasoning: hasError || false,
    done: meta.done === 'true' || meta.done === true || true,
    intentType: meta.intent_type,
    formCode: meta.form_code,
    extractedFields: meta.extracted_fields,
    confidence: meta.confidence,
    model: meta.model,
    contentType: meta.content_type,
    streamText: meta.stream_text,
    formId: meta.formId,
    formSchema,
    formSubmitted: meta.formSubmitted === 'true' || meta.formSubmitted === true,
    formCard,
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
  try {
    const metadata = buildMessageMetadata(msg)
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role: msg.role,
        content: msg.content || msg.streamText || '',
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
  const done = messages.filter(m => m.done !== false)
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
      const restored = restoreMessageMetadata(m.metadata)
      return {
        id: m.message_id,
        role: m.role === 'assistant' ? 'assistant' : 'user',
        content: m.content || '',
        ...restored,
        type: 'chat',
        parentId: m.parent_id,
        createdAt: m.created_at,
        metadata: m.metadata,
      }
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

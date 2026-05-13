/**
 * chatApi - 聊天记录持久化 API（v2 架构）
 * 业务扩展字段全部存 metadata，与核心消息表完全解耦。
 * 所有调用通过 /api/v2/chat/* 端点。
 */

const BASE = '/api/v2/chat'

/**
 * 构建消息的 metadata 对象
 * @param {Object} msg - 消息对象
 * @returns {Object} - metadata 对象
 */
function buildMessageMetadata(msg) {
  const metadata = { ...(msg.metadata || {}) }

  if (msg.reasoning !== undefined && Array.isArray(msg.reasoning)) {
    const indexedReasoning = msg.reasoning.map((step, index) => ({
      ...step,
      _index: index
    }))
    metadata.reasoning_full = JSON.stringify(indexedReasoning)
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

/**
 * 从 metadata 恢复消息对象的扩展字段
 * @param {Object} meta - metadata 对象
 * @returns {Object} - 恢复的扩展字段
 */
function restoreMessageMetadata(meta = {}) {
  let reasoning = []
  if (meta.reasoning_full) {
    try {
      reasoning = JSON.parse(meta.reasoning_full)
      reasoning.sort((a, b) => (a._index ?? 0) - (b._index ?? 0))
    } catch (e) {
    }
  }

  if (!reasoning.length && meta.reasoning) {
    reasoning = meta.reasoning.split('\n').filter(Boolean).map((c, index) => ({ 
      type: 'thinking', 
      content: c,
      _index: index
    }))
  }

  let formSchema = null
  if (meta.formSchema !== undefined) {
    try {
      formSchema = JSON.parse(meta.formSchema)
    } catch (e) {
      formSchema = null
    }
  }

  let formCard = null
  if (meta.formCard) {
    try {
      formCard = JSON.parse(meta.formCard)
    } catch (e) {
      formCard = null
    }
  }

  if (!formCard && formSchema && meta.formId) {
    formCard = {
      formId: meta.formId,
      formName: formSchema.formName || formSchema.formCode || '',
      formCode: formSchema.formCode || '',
      status: (meta.formSubmitted === 'true' || meta.formSubmitted === true) ? 'submitted' : 'filling',
      fieldCount: formSchema.fields?.length || 0
    }
  }

  // 检查是否有错误类型的步骤
    const hasError = reasoning.some(r => r.type === 'error');
    
    return {
    reasoning,
    // 历史消息默认保持折叠，只有发生错误时才展开
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
    formCard
  }
}

export async function createSession(userId, title) {
  const resp = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ 
      user_id: userId || undefined,
      title: title || '新对话'
    })
  })
  const data = await resp.json()
  if (!resp.ok) {
    console.warn('[chatApi] createSession 失败:', resp.status, data)
    return { success: false, error: data }
  }
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
        metadata
      })
    })
    const data = await resp.json()
    return { success: resp.ok, message_id: data.message_id, ...data }
  } catch (e) {
    console.warn('[chatApi] saveMessage failed:', e)
    throw e
  }
}

export async function updateMessage(sessionId, messageId, { content, metadata }) {
  try {
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        content: content || null,
        metadata: metadata || null
      })
    })
    if (!resp.ok) {
      console.warn('[chatApi] updateMessage failed:', resp.status)
      return false
    }
    return true
  } catch (e) {
    console.warn('[chatApi] updateMessage failed:', e)
    return false
  }
}

export async function saveMessages(sessionId, messages) {
  const done = messages.filter(m => m.done !== false)
  
  if (done.length > 3) {
    try {
      const batchData = done.map(msg => ({
        role: msg.role,
        content: msg.content || msg.streamText || '',
        content_type: msg.contentType || 'text',
        parent_id: msg.parentId || null,
        metadata: buildMessageMetadata(msg)
      }))
      
      const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages/batch`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: batchData })
      })
      
      if (!resp.ok) {
        console.warn('[chatApi] saveMessages batch failed, falling back')
        for (const msg of done) {
          await saveMessage(sessionId, msg)
        }
      } else {
        const result = await resp.json()
        console.log(`[chatApi] saveMessages batch success: ${result.count}`)
      }
    } catch (e) {
      console.warn('[chatApi] saveMessages batch error, falling back:', e)
      for (const msg of done) {
        await saveMessage(sessionId, msg)
      }
    }
  } else {
    for (const msg of done) {
      await saveMessage(sessionId, msg)
    }
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
        metadata: m.metadata
      }
    })
  } catch (e) {
    console.warn('[chatApi] loadMessages failed:', e)
    return []
  }
}

export async function getSessions(userId, limit = 50) {
  try {
    const resp = await fetch(`${BASE}/sessions?user_id=${encodeURIComponent(userId)}&limit=${limit}`)
    if (!resp.ok) return []
    const result = await resp.json()
    return result.sessions || []
  } catch (e) {
    console.warn('[chatApi] getSessions failed:', e)
    return []
  }
}

export async function deleteSession(sessionId) {
  try {
    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
  } catch (e) {
    console.warn('[chatApi] deleteSession failed:', e)
  }
}

export async function updateSessionTitle(sessionId, title) {
  try {
    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title })
    })
  } catch (e) {
    console.warn('[chatApi] updateSessionTitle failed:', e)
  }
}

export async function getSessionStats(sessionId) {
  try {
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/stats`)
    if (!resp.ok) return {}
    return resp.json()
  } catch (e) {
    console.warn('[chatApi] getSessionStats failed:', e)
    return {}
  }
}

export async function searchMessages(query_text, { user_id, session_id, limit = 20 } = {}) {
  try {
    const params = new URLSearchParams({ q: query_text })
    if (user_id) params.set('user_id', user_id)
    if (session_id) params.set('session_id', session_id)
    params.set('limit', String(limit))
    const resp = await fetch(`${BASE}/messages/search?${params}`)
    if (!resp.ok) return []
    const result = await resp.json()
    return result.results || []
  } catch (e) {
    console.warn('[chatApi] searchMessages failed:', e)
    return []
  }
}

export async function getFormSchema(formCode) {
  try {
    const resp = await fetch(`/api/v1/form/schema/${encodeURIComponent(formCode)}`)
    if (!resp.ok) return null
    const result = await resp.json()
    return result.success ? result : null
  } catch (e) {
    console.warn('[chatApi] getFormSchema failed:', e)
    return null
  }
}
/**
 * chatApi - 聊天记录持久化 API（v2 架构）
 * 业务扩展字段全部存 metadata，与核心消息表完全解耦。
 * 所有调用通过 /api/v2/chat/* 端点。
 */

const BASE = '/api/v2/chat'

/**
 * 创建数据库会话
 * @param {string} userId  用户ID
 * @param {string} title   会话标题（可选）
 */
export async function createSession(userId, title) {
  const resp = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ user_id: userId, title })
  })
  return resp.json()
}

/**
 * 保存单条消息到数据库
 * 所有业务字段（intent_type / form_code / extracted_fields 等）全部写入 metadata。
 */
export async function saveMessage(sessionId, msg) {
  try {
    const metadata = { ...(msg.metadata || {}) }

    if (msg.reasoning !== undefined) {
      metadata.reasoning = Array.isArray(msg.reasoning)
        ? msg.reasoning.map(s => s.content || '').join('\n')
        : String(msg.reasoning)
    }
    if (msg.intentType || msg.intent_type) metadata.intent_type = msg.intentType || msg.intent_type
    if (msg.formCode || msg.form_code)     metadata.form_code   = msg.formCode   || msg.form_code
    if (msg.extractedFields || msg.extracted_fields) metadata.extracted_fields = msg.extractedFields || msg.extracted_fields
    if (msg.confidence != null)            metadata.confidence  = String(msg.confidence)
    if (msg.model)                         metadata.model       = msg.model

    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role:         msg.role,
        content:      msg.content || msg.streamText || '',
        content_type: msg.contentType || 'text',
        parent_id:    msg.parentId || null,
        metadata:     Object.keys(metadata).length > 0 ? metadata : null
      })
    })
  } catch (e) {
    console.warn('[chatApi] saveMessage failed:', e)
  }
}

/**
 * 批量保存消息
 */
export async function saveMessages(sessionId, messages) {
  const done = messages.filter(m => m.done !== false)
  for (const msg of done) {
    await saveMessage(sessionId, msg)
  }
}

/**
 * 从数据库加载某会话的所有消息，转换为前端消息格式
 */
export async function loadMessages(sessionId) {
  try {
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`)
    if (!resp.ok) return []
    const result = await resp.json()
    const msgs = result.messages || []

    return msgs.map(m => {
      const meta = m.metadata || {}
      return {
        id:              m.message_id,
        role:            m.role === 'assistant' ? 'assistant' : 'user',
        content:         m.content || '',
        reasoning:       meta.reasoning
          ? meta.reasoning.split('\n').filter(Boolean).map(c => ({ type: 'thinking', content: c }))
          : [],
        showReasoning:   false,
        done:            true,
        type:            'chat',
        intentType:      meta.intent_type,
        formCode:        meta.form_code,
        extractedFields: meta.extracted_fields,
        confidence:      meta.confidence,
        model:           meta.model,
        contentType:     m.content_type,
        parentId:        m.parent_id,
        createdAt:       m.created_at,
        metadata:        meta
      }
    })
  } catch (e) {
    console.warn('[chatApi] loadMessages failed:', e)
    return []
  }
}

/**
 * 查询用户的所有会话列表（按 updated_at 倒序）
 */
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

/**
 * 删除会话（级联删除 messages + metadata）
 */
export async function deleteSession(sessionId) {
  try {
    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
  } catch (e) {
    console.warn('[chatApi] deleteSession failed:', e)
  }
}

/**
 * 更新会话标题
 */
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

/**
 * 会话统计
 */
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

/**
 * 全文搜索消息
 */
export async function searchMessages(query_text, { user_id, session_id, limit = 20 } = {}) {
  try {
    const params = new URLSearchParams({ q: query_text })
    if (user_id)    params.set('user_id', user_id)
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

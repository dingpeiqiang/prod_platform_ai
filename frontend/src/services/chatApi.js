/**
 * chatApi - 聊天记录数据库持久化 API 封装
 * 所有调用均通过 /api/v1/chat/sessions/* 端点
 */

const BASE = '/api/v1/chat'

/**
 * 创建数据库会话
 * @param {string} userId  用户ID（来自 userStore）
 * @param {string} title   会话标题（可选）
 * @returns {Promise<{success, session_id, title, created_at} | {success:false, error}>}
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
 * @param {string} sessionId  数据库 session_id（而非前端临时 id）
 * @param {object} msg        消息对象 { role, content, reasoning, intentType, formCode, extractedFields, confidence }
 */
export async function saveMessage(sessionId, msg) {
  try {
    const reasoning = Array.isArray(msg.reasoning)
      ? msg.reasoning.map(s => s.content || '').join('\n')
      : (msg.reasoning || '')

    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role: msg.role,
        content: msg.content || msg.streamText || '',
        intent_type: msg.intentType || msg.intent_type || null,
        form_code: msg.formCode || msg.form_code || null,
        extracted_fields: msg.extractedFields || msg.extracted_fields || null,
        confidence: msg.confidence != null ? String(msg.confidence) : null,
        reasoning: reasoning || null
      })
    })
  } catch (e) {
    console.warn('[chatApi] saveMessage failed:', e)
  }
}

/**
 * 批量保存消息（用于切换会话时一次性写入）
 * @param {string} sessionId
 * @param {Array}  messages
 */
export async function saveMessages(sessionId, messages) {
  const done = messages.filter(m => m.done !== false)
  for (const msg of done) {
    await saveMessage(sessionId, msg)
  }
}

/**
 * 从数据库加载某会话的所有消息，转换为前端消息格式
 * @param {string} sessionId
 * @returns {Promise<Array>} 前端消息对象数组
 */
export async function loadMessages(sessionId) {
  try {
    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`)
    if (!resp.ok) return []
    const result = await resp.json()
    const msgs = result.messages || []

    return msgs.map(m => ({
      id: `db_${m.id}`,
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content || '',
      reasoning: m.reasoning
        ? m.reasoning.split('\n').filter(Boolean).map(c => ({ type: 'thinking', content: c }))
        : [],
      showReasoning: false,
      done: true,
      type: 'chat',
      // 额外字段（供意图面板使用）
      intentType: m.intent_type,
      formCode: m.form_code,
      extractedFields: m.extracted_fields,
      confidence: m.confidence
    }))
  } catch (e) {
    console.warn('[chatApi] loadMessages failed:', e)
    return []
  }
}

/**
 * 查询用户的所有会话列表（按 updated_at 倒序）
 * @param {string} userId
 * @param {number} limit
 * @returns {Promise<Array>}
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
 * 删除数据库中的会话（级联删除 messages）
 * @param {string} sessionId
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
 * @param {string} sessionId
 * @param {string} title
 */
export async function updateSessionTitle(sessionId, title) {
  try {
    await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}?title=${encodeURIComponent(title)}`, {
      method: 'PATCH'
    })
  } catch (e) {
    console.warn('[chatApi] updateSessionTitle failed:', e)
  }
}
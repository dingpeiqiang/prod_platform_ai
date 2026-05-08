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
    body: JSON.stringify({ 
      user_id: userId || undefined,  // 不传 null，避免后端解析问题
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

/**
 * 保存单条消息到数据库
 * 所有业务字段（intent_type / form_code / extracted_fields 等）全部写入 metadata。
 */
export async function saveMessage(sessionId, msg) {
  try {
    const metadata = { ...(msg.metadata || {}) }

    // 保存完整的 reasoning 数组结构（用于模型思考内容恢复）
    // 为每个步骤添加索引确保顺序正确
    if (msg.reasoning !== undefined && Array.isArray(msg.reasoning)) {
      const indexedReasoning = msg.reasoning.map((step, index) => ({
        ...step,
        _index: index  // 添加序号确保顺序
      }))
      metadata.reasoning_full = JSON.stringify(indexedReasoning)
    }
    // 同时保存旧格式的 reasoning 字符串（用于向后兼容）
    if (msg.reasoning !== undefined) {
      metadata.reasoning = Array.isArray(msg.reasoning)
        ? msg.reasoning.map(s => s.content || '').join('\n')
        : String(msg.reasoning)
    }
    // 保存表单状态（用于恢复表单）
    if (msg.formId !== undefined) {
      metadata.formId = msg.formId
    }
    if (msg.formSchema !== undefined) {
      metadata.formSchema = JSON.stringify(msg.formSchema)
    }
    // 保存表单提交状态（用于标记已提交的表单）
    if (msg.formSubmitted !== undefined) {
      metadata.formSubmitted = msg.formSubmitted
    }
    // 保存表单卡片信息（用于消息中显示表单卡片）
    if (msg.formCard) {
      metadata.formCard = JSON.stringify(msg.formCard)
    }
    
    // 保存意图相关字段
    if (msg.intentType || msg.intent_type) metadata.intent_type = msg.intentType || msg.intent_type
    if (msg.formCode || msg.form_code)     metadata.form_code   = msg.formCode   || msg.form_code
    if (msg.extractedFields || msg.extracted_fields) metadata.extracted_fields = msg.extractedFields || msg.extracted_fields
    if (msg.confidence != null)            metadata.confidence  = String(msg.confidence)
    if (msg.model)                         metadata.model       = msg.model
    
    // 保存 streamText（用于恢复流式状态）
    if (msg.streamText) metadata.stream_text = msg.streamText
    
    // 保存完成状态
    if (msg.done !== undefined) metadata.done = msg.done
    
    // 保存 contentType
    if (msg.contentType) metadata.content_type = msg.contentType

    const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        role:         msg.role,
        content:      msg.content || msg.streamText || '',
        content_type: msg.contentType || 'text',
        parent_id:    msg.parentId || null,
        step_type:    msg.step_type || null,
        metadata:     Object.keys(metadata).length > 0 ? metadata : null
      })
    })
    const data = await resp.json()
    return { success: resp.ok, message_id: data.message_id, ...data }
  } catch (e) {
    console.warn('[chatApi] saveMessage failed:', e)
    throw e  // 重新抛出错误，让调用者处理
  }
}

/**
 * 更新消息内容或 metadata（用于 thinking 步骤实时更新）
 */
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

/**
 * 批量保存消息
 */
export async function saveMessages(sessionId, messages) {
  const done = messages.filter(m => m.done !== false)
  
  // 如果消息数量较多，使用批量 API
  if (done.length > 3) {
    try {
      const batchData = done.map(msg => {
        const metadata = { ...(msg.metadata || {}) }
        
        // 保存完整的 reasoning 数组结构（用于模型思考内容恢复）
        // 为每个步骤添加索引确保顺序正确
        if (msg.reasoning !== undefined && Array.isArray(msg.reasoning)) {
          const indexedReasoning = msg.reasoning.map((step, index) => ({
            ...step,
            _index: index  // 添加序号确保顺序
          }))
          metadata.reasoning_full = JSON.stringify(indexedReasoning)
        }
        // 同时保存旧格式的 reasoning 字符串（用于向后兼容）
        if (msg.reasoning !== undefined) {
          metadata.reasoning = Array.isArray(msg.reasoning)
            ? msg.reasoning.map(s => s.content || '').join('\n')
            : String(msg.reasoning)
        }
        // 保存表单状态（用于恢复表单）
        if (msg.formId !== undefined) {
          metadata.formId = msg.formId
        }
        if (msg.formSchema !== undefined) {
          metadata.formSchema = JSON.stringify(msg.formSchema)
        }
        // 保存表单提交状态（用于标记已提交的表单）
        if (msg.formSubmitted !== undefined) {
          metadata.formSubmitted = msg.formSubmitted
        }
        // 保存表单卡片信息
        if (msg.formCard) {
          metadata.formCard = JSON.stringify(msg.formCard)
        }
        if (msg.intentType || msg.intent_type) metadata.intent_type = msg.intentType || msg.intent_type
        if (msg.formCode || msg.form_code)     metadata.form_code   = msg.formCode   || msg.form_code
        if (msg.extractedFields || msg.extracted_fields) metadata.extracted_fields = msg.extractedFields || msg.extracted_fields
        if (msg.confidence != null)            metadata.confidence  = String(msg.confidence)
        if (msg.model)                         metadata.model       = msg.model
        
        return {
          role:         msg.role,
          content:      msg.content || msg.streamText || '',
          content_type: msg.contentType || 'text',
          parent_id:    msg.parentId || null,
          metadata:     Object.keys(metadata).length > 0 ? metadata : null
        }
      })
      
      const resp = await fetch(`${BASE}/sessions/${encodeURIComponent(sessionId)}/messages/batch`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: batchData })
      })
      
      if (!resp.ok) {
        console.warn('[chatApi] saveMessages batch failed, falling back to individual saves')
        // 批量失败时降级为逐个保存
        for (const msg of done) {
          await saveMessage(sessionId, msg)
        }
      } else {
        const result = await resp.json()
        console.log(`[chatApi] saveMessages batch success: ${result.count} messages saved`)
      }
    } catch (e) {
      console.warn('[chatApi] saveMessages batch error, falling back to individual saves:', e)
      // 出错时降级为逐个保存
      for (const msg of done) {
        await saveMessage(sessionId, msg)
      }
    }
  } else {
    // 消息数量少时直接逐个保存
    for (const msg of done) {
      await saveMessage(sessionId, msg)
    }
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
      
      // 优先从 reasoning_full 恢复完整的 reasoning 结构
      let reasoning = []
      if (meta.reasoning_full) {
        try {
          reasoning = JSON.parse(meta.reasoning_full)
          // 按步骤序号排序，确保顺序正确
          reasoning.sort((a, b) => (a._index ?? 0) - (b._index ?? 0))
        } catch (e) {
          // 如果解析失败，降级到旧格式
        }
      }
      
      // 如果没有完整结构，用旧格式 fallback
      if (!reasoning.length && meta.reasoning) {
        reasoning = meta.reasoning.split('\n').filter(Boolean).map((c, index) => ({ 
          type: 'thinking', 
          content: c,
          _index: index  // 添加序号确保顺序
        }))
      }
      
      // 恢复表单状态
      let formId = undefined
      let formSchema = null
      let formSubmitted = false
      if (meta.formId !== undefined) {
        formId = meta.formId
      }
      if (meta.formSchema !== undefined) {
        try {
          formSchema = JSON.parse(meta.formSchema)
        } catch (e) {
          formSchema = null
        }
      }
      // 恢复表单提交状态
      if (meta.formSubmitted !== undefined) {
        formSubmitted = meta.formSubmitted === 'true' || meta.formSubmitted === true
      }

      // 恢复表单卡片信息
      let formCard = null
      if (meta.formCard) {
        try {
          formCard = JSON.parse(meta.formCard)
        } catch (e) {
          formCard = null
        }
      }

      // 如果没有 formCard 但有 formSchema，根据状态重建一个
      if (!formCard && formSchema) {
        formCard = {
          formId: formId,
          formName: formSchema.formName || formSchema.formCode || '',
          formCode: formSchema.formCode || '',
          status: formSubmitted ? 'submitted' : 'filling',
          fieldCount: formSchema.fields?.length || 0,
          createdAt: m.created_at
        }
      }
      
      return {
        id:              m.message_id,
        role:            m.role === 'assistant' ? 'assistant' : 'user',
        content:         m.content || '',
        // 恢复 streamText（如果有）
        streamText:      meta.stream_text || m.content || '',
        // 恢复 reasoning
        reasoning:       reasoning,
        showReasoning:   (reasoning && reasoning.length > 0) ? true : false,  // 有处理步骤则默认展开
        done:            meta.done === 'true' || meta.done === true || true,  // 默认已完成
        type:            'chat',
        // 恢复意图相关字段
        intentType:      meta.intent_type,
        formCode:        meta.form_code,
        extractedFields: meta.extracted_fields,
        confidence:      meta.confidence,
        model:           meta.model,
        // 恢复 contentType
        contentType:     meta.content_type || m.content_type || 'text',
        parentId:        m.parent_id,
        createdAt:       m.created_at,
        metadata:        meta,
        // 恢复表单状态
        formId:          formId,
        formSchema:      formSchema,
        formSubmitted:   formSubmitted,
        // 恢复表单卡片
        formCard:        formCard
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

/**
 * 根据formCode获取表单Schema
 * 用于从数据库恢复表单状态
 */
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

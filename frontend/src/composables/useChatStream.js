import { ref } from 'vue'
import { sendMessageWithModel } from '../services/chatApi.js'
import { getEventHandler, getPostProcessor } from './useIntentRegistry.js'

function uid(prefix = 'msg') {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
}

export function useChatStream() {
  const messages = ref([])
  const streaming = ref(false)
  const abortRef = ref(null)

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
        current.reasoning = [...(current.reasoning || []), value]
        current.showReasoning = true
      } else {
        current[key] = value
      }
    }
    list[idx] = current
    messages.value = list
    return list[idx]
  }

  const applyIntentEvent = (data) => {
    const type = data.intentType || data.type
    const handler = getEventHandler(type)
    const current = messages.value.find(m => m.role === 'assistant' && !m.done) || {}
    if (handler) handler(data, current)
    upsertAssistantMessage({
      intentType: type,
      action: data.action || current.action || '',
      intentData: { ...(current.intentData || {}), ...(data.data || data) },
      stats: { ...(current.stats || {}), ...(data.stats || {}) },
    })
  }

  const sendMessage = async ({ text, scene = '', modelConfig = null, history = [] }) => {
    if (!text || streaming.value) return
    streaming.value = true
    pushUserMessage(text)
    let streamText = ''

    try {
      const payload = [
        ...history.map(m => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content || '' })),
        { role: 'user', content: text },
      ]

      upsertAssistantMessage({ loading: true, streamText: '' })

      const resp = await sendMessageWithModel(payload, { modelConfig, scene })
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
            if (data.type === 'thinking') {
              upsertAssistantMessage({
                reasoningStep: {
                  type: 'thinking',
                  content: data.content,
                  metadata: data.metadata || {},
                  timestamp: Date.now(),
                },
              })
            } else if (data.type === 'text') {
              streamText += data.content || ''
              upsertAssistantMessage({ streamText, loading: false })
            } else if (data.type === 'stats') {
              upsertAssistantMessage({ stats: data })
            } else if (data.type === 'done') {
              const doneIntent = data.intentType || (data.intentData && data.intentData.intentType) || ''
              const doneAction = (data.intentData && data.intentData.action) || data.action || ''
              const doneStats = (data.intentData && data.intentData.stats) || data.stats || null
              const doneIntentData = data.intentData || null
              upsertAssistantMessage({
                done: true,
                loading: false,
                intentType: doneIntent,
                action: doneAction,
                stats: doneStats,
                intentData: doneIntentData,
                contentType: data.contentType || 'chat',
              })
            } else if (data.type === 'intent') {
              applyIntentEvent(data)
            } else if (data.type === 'product_ops_query' || data.type === 'product_ops_policy' || data.type === 'product_ops_reason') {
              applyIntentEvent({ ...data, intentType: data.type })
            }
          } catch (err) {
            console.warn('[useChatStream] SSE frame parse error:', err)
          }
        }
      }

      const finalMsg = messages.value.find(m => m.role === 'assistant' && m.done) || messages.value[messages.value.length - 1]
      if (finalMsg) {
        const post = getPostProcessor(finalMsg.intentType)
        if (post) post(finalMsg, finalMsg)
      }

      upsertAssistantMessage({ done: true, loading: false })
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
  }

  return {
    messages,
    streaming,
    sendMessage,
    stop,
  }
}
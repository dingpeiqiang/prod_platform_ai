/**
 * useChatStream - SSE 通信逻辑
 * 将 ChatAssistant 中的 sendMessage / handleEvent / 流式处理逻辑抽离
 */
import { ref, nextTick } from 'vue'

export function useChatStream(options = {}) {
  const {
    onThinking,
    onText,
    onIntent,
    onDone,
    onError
  } = options

  const isStreaming = ref(false)
  let abortCtrl = null
  let eventCount = 0

  const buildEventHandler = (msg, intentDataRef, intentTypeRef) => (data) => {
    switch (data.type) {
      case 'thinking':
      case 'decision':
      case 'executing':
        onThinking?.(data.content)
        break
      case 'reasoning':
        onThinking?.(data.content, { reasoning: true })
        break
      case 'text_start':
        msg.streamText = ''
        break
      case 'text':
        msg.streamText = (msg.streamText || '') + (data.content || '')
        onText?.(data.content)
        break
      case 'text_end':
        break
      case 'intent': {
        const { intentType, data: intentData } = data
        if (intentType) intentTypeRef.value = intentType
        if (intentData) intentDataRef.value = intentData
        onIntent?.(intentType, intentData)
        break
      }
      case 'result': {
        const parsed = typeof data.content === 'string'
          ? JSON.parse(data.content)
          : data.content
        msg.intentResult = parsed
        if (parsed?.formCode) intentDataRef.value = parsed
        break
      }
      case 'config':
      case 'delete_form':
      case 'manage_history':
        onIntent?.(data.type, data.content)
        break
      case 'error':
        onError?.(data.content)
        break
    }
  }

  const parseSSEFrame = (frame) => {
    if (!frame.startsWith('data:')) return null
    try {
      return JSON.parse(frame.slice(5).trim())
    } catch {
      return null
    }
  }

  const abort = () => {
    if (abortCtrl) {
      abortCtrl.abort()
      abortCtrl = null
    }
  }

  return {
    isStreaming,
    abortCtrl: { get: () => abortCtrl, set: (v) => { abortCtrl = v } },
    buildEventHandler,
    parseSSEFrame,
    abort
  }
}
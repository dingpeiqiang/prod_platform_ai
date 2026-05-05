/**
 * usePersistence - localStorage 封装
 * 将 ChatAssistant 中的 localStorage 持久化逻辑抽离
 */
import { ref, watch } from 'vue'

export function usePersistence(storageKey, defaultValue = null) {
  const load = () => {
    try {
      const raw = localStorage.getItem(storageKey)
      if (raw) return JSON.parse(raw)
    } catch {}
    return defaultValue
  }

  const save = (value) => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(value))
    } catch {}
  }

  const clear = () => {
    try {
      localStorage.removeItem(storageKey)
    } catch {}
  }

  return { load, save, clear }
}

export function useSessionPersistence(sessionId) {
  const storageKey = `chat_session_${sessionId}`
  const { load, save, clear } = usePersistence(storageKey)

  const saveMessages = (messages) => {
    const done = messages.filter(m => m.done !== false)
    save(done)
  }

  const loadMessages = () => load() || []

  return { saveMessages, loadMessages, clearMessages: clear }
}

export function useFormPersistence(sessionId) {
  const storageKey = `chat_form_${sessionId}`
  const { load, save, clear } = usePersistence(storageKey)

  const saveFormState = (state) => save(state)

  const loadFormState = () => {
    const raw = load()
    if (raw) {
      return {
        formId: raw.formId || '',
        formSchema: raw.formSchema || null
      }
    }
    return { formId: '', formSchema: null }
  }

  return { saveFormState, loadFormState, clearFormState: clear }
}
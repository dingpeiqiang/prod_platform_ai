/**
 * chat store - Pinia 状态管理
 * 管理会话、消息、表单等全局状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // ── 会话状态 ──────────────────────────────────
  const sessions = ref([])
  const activeSessionId = ref('')

  const activeSession = computed(() =>
    sessions.value.find(s => s.id === activeSessionId.value)
  )

  // ── 消息状态 ──────────────────────────────────
  const messages = ref([])

  // ── 表单状态 ──────────────────────────────────
  const currentFormId = ref('')
  const currentFormSchema = ref(null)

  // ── 配置部署状态 ──────────────────────────────────
  const configDeploying = ref({})  // msgId -> bool

  // ── 会话操作 ──────────────────────────────────
  const addSession = (session) => {
    sessions.value.unshift(session)
  }

  const removeSession = (id) => {
    const idx = sessions.value.findIndex(s => s.id === id)
    if (idx !== -1) sessions.value.splice(idx, 1)
  }

  const switchSession = (id) => {
    activeSessionId.value = id
  }

  const updateSessionTitle = (id, title) => {
    const s = sessions.value.find(s => s.id === id)
    if (s) s.title = title
  }

  // ── 消息操作 ──────────────────────────────────
  const addMessage = (msg) => {
    messages.value.push(msg)
  }

  const updateMessage = (id, updates) => {
    const msg = messages.value.find(m => m.id === id)
    if (msg) Object.assign(msg, updates)
  }

  const clearMessages = () => {
    messages.value = []
  }

  // ── 表单操作 ──────────────────────────────────
  const setForm = (formId, formSchema) => {
    currentFormId.value = formId
    currentFormSchema.value = formSchema
  }

  const clearForm = () => {
    currentFormId.value = ''
    currentFormSchema.value = null
  }

  // ── 配置部署操作 ──────────────────────────────────
  const setConfigDeploying = (msgId, deploying) => {
    if (deploying) {
      configDeploying.value[msgId] = true
    } else {
      delete configDeploying.value[msgId]
    }
  }

  return {
    sessions, activeSessionId, activeSession,
    messages, currentFormId, currentFormSchema, configDeploying,
    addSession, removeSession, switchSession, updateSessionTitle,
    addMessage, updateMessage, clearMessages,
    setForm, clearForm, setConfigDeploying
  }
})
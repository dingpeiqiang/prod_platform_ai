import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { assistants } from '../config/assistantModes.js'

const LS_SESSIONS_KEY = 'workbench.sessions.v1'
const LS_ASSISTANT_KEY = 'workbench.activeAssistant.v1'
const LS_ACTIVE_SESSION_KEY = 'workbench.activeSession.v1'

function loadSessions() {
  try {
    const raw = localStorage.getItem(LS_SESSIONS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

function loadActiveAssistant() {
  try {
    return localStorage.getItem(LS_ASSISTANT_KEY) || null
  } catch (e) {
    return null
  }
}

function loadActiveSessionId() {
  try {
    return localStorage.getItem(LS_ACTIVE_SESSION_KEY) || null
  } catch (e) {
    return null
  }
}

/**
 * 工作台 store
 *
 * 职责：
 * 1. 维护当前选中的助手（null = 工作台总览）
 * 2. 维护每个助手的历史会话列表（按助手分组）
 * 3. 切换助手时，把当前对话归档到对应助手的历史列表
 *
 * 历史会话结构：
 *   { id, assistantKey, title, messages, updated_at }
 */
export const useWorkbenchStore = defineStore('workbench', () => {
  // 当前选中的助手 key，null 表示工作台总览（未选助手）
  const activeAssistant = ref(loadActiveAssistant())

  // 历史会话：一个扁平数组，每条带 assistantKey 字段
  const sessions = ref(loadSessions())

  // 当前正在编辑的会话 id（一个会话对应左侧一条记录，随对话实时更新）
  const activeSessionId = ref(loadActiveSessionId())

  const persistSessions = () => {
    try {
      localStorage.setItem(LS_SESSIONS_KEY, JSON.stringify(sessions.value))
    } catch (e) { /* ignore quota/security errors */ }
  }

  const persistAssistant = () => {
    try {
      if (activeAssistant.value) localStorage.setItem(LS_ASSISTANT_KEY, activeAssistant.value)
      else localStorage.removeItem(LS_ASSISTANT_KEY)
    } catch (e) { /* ignore */ }
  }

  const persistActiveSessionId = () => {
    try {
      if (activeSessionId.value) localStorage.setItem(LS_ACTIVE_SESSION_KEY, activeSessionId.value)
      else localStorage.removeItem(LS_ACTIVE_SESSION_KEY)
    } catch (e) { /* ignore */ }
  }

  const activeAssistantMeta = computed(
    () => assistants.find((a) => a.key === activeAssistant.value) || null
  )

  // 当前助手下的历史会话
  const sessionsForActive = computed(() => {
    if (!activeAssistant.value) return []
    return sessions.value
      .filter((s) => s.assistantKey === activeAssistant.value)
      .sort((a, b) => (b.updated_at || 0) - (a.updated_at || 0))
  })

  function selectAssistant(key) {
    activeAssistant.value = key
    persistAssistant()
  }

  function clearAssistant() {
    activeAssistant.value = null
    persistAssistant()
  }

  /**
   * 把一段对话归档到历史会话。
   * @param {Object} payload { assistantKey, title, messages, snapshot }
   *   snapshot 为面板状态快照（status/filing/salesChannel/testReport/panelOpen/activeTab/workflow 等）
   * @returns {Object} 归档后的 session 对象
   */
  function archiveSession({ assistantKey, title, messages, snapshot }) {
    if (!messages || messages.length === 0) return null
    const session = {
      id: 's_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      assistantKey,
      title: title || deriveTitle(messages),
      messages: messages.slice(),
      snapshot: snapshot || null,
      updated_at: Date.now(),
    }
    sessions.value = [session, ...sessions.value]
    persistSessions()
    return session
  }

  /**
   * 更新"当前活动会话"（左侧一条记录，随对话实时更新）。
   * 若当前正在编辑的会话已存在，则更新其消息/快照/标题并移到最前；
   * 否则新建一条记录并记为当前活动会话。
   * @returns {Object} 活动 session
   */
  function upsertActiveSession({ assistantKey, messages, snapshot }) {
    if (!messages || messages.length === 0) return null
    const now = Date.now()
    let session = sessions.value.find((s) => s.id === activeSessionId.value)
    if (session) {
      session.messages = messages.slice()
      session.snapshot = snapshot || session.snapshot
      session.title = deriveTitle(messages)
      session.updated_at = now
      // 移到最前（最近更新排最前）
      sessions.value = [session, ...sessions.value.filter((s) => s.id !== session.id)]
    } else {
      session = {
        id: 's_' + now.toString(36) + Math.random().toString(36).slice(2, 6),
        assistantKey,
        title: deriveTitle(messages),
        messages: messages.slice(),
        snapshot: snapshot || null,
        updated_at: now,
      }
      sessions.value = [session, ...sessions.value]
      activeSessionId.value = session.id
      persistActiveSessionId()
    }
    persistSessions()
    return session
  }

  /**
   * 结束当前活动会话。下次对话将创建一条新的历史记录。
   * 用于"新建会话""切换助手"等起新记录的操作。
   */
  function startNewSession() {
    activeSessionId.value = null
    persistActiveSessionId()
  }

  /**
   * 把活动会话指向指定会话（用于点击某条历史记录继续编辑它）
   */
  function setActiveSession(id) {
    activeSessionId.value = id || null
    persistActiveSessionId()
  }

  function deriveTitle(messages) {
    const firstUser = messages.find((m) => m.role === 'user')
    if (firstUser && firstUser.content) {
      const t = String(firstUser.content).replace(/\s+/g, ' ').trim()
      return t.length > 20 ? t.slice(0, 20) + '…' : t
    }
    return '新对话'
  }

  function removeSession(id) {
    sessions.value = sessions.value.filter((s) => s.id !== id)
    persistSessions()
  }

  return {
    activeAssistant,
    activeAssistantMeta,
    sessions,
    sessionsForActive,
    activeSessionId,
    selectAssistant,
    clearAssistant,
    archiveSession,
    upsertActiveSession,
    startNewSession,
    setActiveSession,
    removeSession,
  }
})

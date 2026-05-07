<template>
  <div id="app">
    <!-- 未登录：显示登录页 -->
    <LoginScreen v-if="!userStore.isLoggedIn" />

    <!-- 已登录：主界面 -->
    <template v-else>
      <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false" />

      <Sidebar
        :class="['sidebar-wrapper', { 'sidebar-visible': sidebarOpen }]"
        :sessions="sessionList"
        :activeId="activeSessionId"
        @new-session="onNewSession"
        @switch-session="onSwitchSession"
        @delete-session="deleteSession"
        @logout="handleLogout"
      />

      <div class="main-area">
        <ChatAssistant
          ref="chatRef"
          :sessionId="activeSessionId"
          :dbSessionId="activeDbSessionId"
          :userId="userStore.username"
          :sessionTitle="activeSessionTitle"
          @title-update="onTitleUpdate"
          @session-init="onSessionInit"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, provide, onMounted, watch } from 'vue'
import Sidebar from './components/Sidebar.vue'
import ChatAssistant from './components/ChatAssistant.vue'
import LoginScreen from './components/LoginScreen.vue'
import { useUserStore } from './stores/user'
import { createSession as apiCreateSession, getSessions as apiGetSessions, deleteSession as apiDeleteSession } from './services/chatApi.js'

const userStore = useUserStore()

const SESSIONS_KEY = 'chat_sessions'
const ACTIVE_SESSION_KEY = 'chat_active_session'

// ── 本地会话列表 ──────────────────────────────────────────
// 每项：{ id, title, createdAt, updatedAt, dbSessionId }
// dbSessionId: 数据库 session_id，已知则直接用，未知则首次发消息时创建
const sessions = ref([])
const activeSessionId = ref('')
const sidebarOpen = ref(false)

// 当前数据库会话 ID（与 activeSessionId 对应）
const activeDbSessionId = ref('')

// ── 加载本地会话列表 ──────────────────────────────────────
const loadSessions = () => {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (raw) sessions.value = JSON.parse(raw)
  } catch {}
  if (!sessions.value.length) {
    sessions.value = []
  }
}

// ── 保存本地会话列表 ──────────────────────────────────────
const saveSessions = () => {
  try { localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions.value)) } catch {}
}

// ── 保存当前激活的会话 ID ─────────────────────────────────
const saveActiveSessionId = () => {
  try { localStorage.setItem(ACTIVE_SESSION_KEY, activeSessionId.value) } catch {}
}

// ── 恢复当前激活的会话 ID ─────────────────────────────────
const loadActiveSessionId = () => {
  try {
    const raw = localStorage.getItem(ACTIVE_SESSION_KEY)
    return raw || ''
  } catch { return '' }
}

// ── 生成 ID ──────────────────────────────────────────────
const genId = () => `sess_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

// ── 创建本地会话 ──────────────────────────────────────────
const createLocalSession = (dbSessionId = null) => {
  const now = Date.now()
  const s = {
    id: genId(),
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    dbSessionId
  }
  sessions.value.unshift(s)
  activeSessionId.value = s.id
  activeDbSessionId.value = dbSessionId || ''
  saveSessions()
  saveActiveSessionId()  // 保存当前激活会话
  sidebarOpen.value = false
  return s
}

// ── 新建按钮 ──────────────────────────────────────────────
const onNewSession = () => createLocalSession()

// ── 切换会话 ──────────────────────────────────────────────
const onSwitchSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  activeSessionId.value = id
  activeDbSessionId.value = s?.dbSessionId || ''
  saveActiveSessionId()  // 保存当前激活会话
  sidebarOpen.value = false
}

// ── 删除会话 ──────────────────────────────────────────────
const deleteSession = async (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s?.dbSessionId) {
    await apiDeleteSession(s.dbSessionId)
  }
  sessions.value = sessions.value.filter(s => s.id !== id)
  localStorage.removeItem(`chat_session_${id}`)
  saveSessions()
  if (activeSessionId.value === id) {
    activeSessionId.value = sessions.value.length ? sessions.value[0].id : ''
    const first = sessions.value.find(s => s.id === activeSessionId.value)
    activeDbSessionId.value = first?.dbSessionId || ''
    if (!activeSessionId.value) createLocalSession()
    else saveActiveSessionId()  // 保存切换后的激活会话
  }
}

// ── 标题更新 ──────────────────────────────────────────────
const onTitleUpdate = (id, title) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) { s.title = title; s.updatedAt = Date.now(); saveSessions() }
}

// ── ChatAssistant 首次发消息后回调：回填 dbSessionId ──────
const onSessionInit = ({ localId, dbSessionId }) => {
  const s = sessions.value.find(s => s.id === localId)
  if (s && dbSessionId && !s.dbSessionId) {
    s.dbSessionId = dbSessionId
    saveSessions()
  }
  if (localId === activeSessionId.value) {
    activeDbSessionId.value = dbSessionId || ''
  }
}

// ── 计算属性 ──────────────────────────────────────────────
const sessionList = computed(() =>
  [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt)
)

const activeSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.id === activeSessionId.value)
  return s?.title || '新对话'
})

const chatRef = ref(null)
provide('chatRef', chatRef)

// ── 登出 ──────────────────────────────────────────────────
const handleLogout = () => {
  sessions.value = []
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  userStore.logout()
}

// ── 初始化：加载本地会话 + 同步 DB 会话 ────────────────────
const initDbSessions = async () => {
  // 0. 恢复之前激活的会话 ID
  const savedActiveId = loadActiveSessionId()

  // 1. 加载本地会话
  loadSessions()

  // 2. 查询 DB 中该用户的会话列表
  const dbSessions = await apiGetSessions(userStore.username)

  // 3. 为本地会话匹配 dbSessionId（按 dbSessionId 精确匹配 + 时间戳兜底）
  for (const s of sessions.value) {
    if (s.dbSessionId) {
      // 已有映射，检查 DB 中是否还存在（可能被删了）
      const stillExists = dbSessions.some(d => d.session_id === s.dbSessionId)
      if (!stillExists) s.dbSessionId = null
      continue
    }
    // 无 dbSessionId → 用时间戳匹配（误差 5s 内视为同一会话）
    const matched = dbSessions.find(d =>
      Math.abs(new Date(d.created_at).getTime() - s.createdAt) < 5000
    )
    if (matched) s.dbSessionId = matched.session_id
  }

  // 4. 找出 DB 中有但本地没有的会话（跨设备/跨浏览器场景），追加到本地
  for (const d of dbSessions) {
    const alreadyLocal = sessions.value.some(s => s.dbSessionId === d.session_id)
    if (!alreadyLocal) {
      const createdAt = new Date(d.created_at).getTime()
      sessions.value.push({
        id: genId(),                            // 生成本地 ID（避免与旧的冲突）
        title: d.title || '新对话',
        createdAt,
        updatedAt: new Date(d.updated_at || d.created_at).getTime(),
        dbSessionId: d.session_id
      })
    }
  }

  saveSessions()

  // 5. 激活会话：优先使用刷新前激活的那个
  if (savedActiveId) {
    const saved = sessions.value.find(s => s.id === savedActiveId)
    if (saved?.dbSessionId && dbSessions.some(d => d.session_id === saved.dbSessionId)) {
      // 之前激活的会话仍然有效，恢复它
      activeSessionId.value = saved.id
      activeDbSessionId.value = saved.dbSessionId
    } else if (saved && sessions.value.some(s => s.id === saved.id)) {
      // 会话存在但 dbSessionId 无效，仍恢复（ChatAssistant 会重新创建 DB 会话）
      activeSessionId.value = saved.id
      activeDbSessionId.value = saved.dbSessionId || ''
    } else {
      // 保存的会话已不存在，fallback 到最近会话
      const sorted = [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt)
      if (sorted.length > 0) {
        activeSessionId.value = sorted[0].id
        activeDbSessionId.value = sorted[0].dbSessionId || ''
      } else {
        // 只有在完全没有会话时才创建新会话
        createLocalSession()
      }
    }
  } else {
    // 没有保存的激活会话，按原有逻辑激活最近会话
    const sorted = [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt)
    if (sorted.length > 0) {
      activeSessionId.value = sorted[0].id
      activeDbSessionId.value = sorted[0].dbSessionId || ''
    } else {
      // 只有在完全没有会话时才创建新会话
      createLocalSession()
    }
  }
}

// ── 监听登录状态 ───────────────────────────────────────────
let wasLoggedIn = false

watch(() => userStore.isLoggedIn, (loggedIn) => {
  if (loggedIn && !wasLoggedIn) {
    wasLoggedIn = true
    initDbSessions()
  } else if (!loggedIn) {
    wasLoggedIn = false
    sessions.value = []
    activeSessionId.value = ''
    activeDbSessionId.value = ''
  }
}, { immediate: true })   // immediate: true 确保 onMounted 之前也触发一次
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; overflow: hidden; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei',
    'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  display: flex;
  height: 100vh;
  height: 100dvh;
  width: 100vw;
  background: #f5f5f5;
}

.sidebar-wrapper { flex-shrink: 0; }
.sidebar-mask { display: none; }

.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  position: relative;
}

@media (max-width: 768px) {
  .sidebar-wrapper {
    position: fixed;
    top: 0; left: 0;
    height: 100vh; height: 100dvh;
    z-index: 200;
    transform: translateX(-100%);
    transition: transform .25s cubic-bezier(.4,0,.2,1);
  }
  .sidebar-wrapper.sidebar-visible { transform: translateX(0); }

  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,.4);
    z-index: 199;
    animation: fadeIn .2s ease;
  }
  @keyframes fadeIn {
    from { opacity: 0; } to { opacity: 1; }
  }
}
</style>
<template>
  <div id="app">
    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false" />

    <Sidebar
      :class="['sidebar-wrapper', { 'sidebar-visible': sidebarOpen }]"
      :sessions="sessionList"
      :activeId="activeSessionId"
      @new-session="onNewSession"
      @switch-session="onSwitchSession"
      @delete-session="deleteSession"
    />

    <div class="main-area">
      <ChatAssistant
        ref="chatRef"
        :sessionId="activeSessionId"
        :sessionTitle="activeSessionTitle"
        @title-update="onTitleUpdate"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, provide } from 'vue'
import Sidebar from './components/Sidebar.vue'
import ChatAssistant from './components/ChatAssistant.vue'

const SESSIONS_KEY = 'chat_sessions'

const sessions = ref([])
const activeSessionId = ref('')
const sidebarOpen = ref(false)

const loadSessions = () => {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (raw) sessions.value = JSON.parse(raw)
  } catch {}
  if (!sessions.value.length) createSession()
  else activeSessionId.value = sessions.value[0].id
}

const saveSessions = () => {
  try { localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions.value)) } catch {}
}

const genId = () => `sess_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

const createSession = () => {
  const now = Date.now()
  const s = { id: genId(), title: '新对话', createdAt: now, updatedAt: now }
  sessions.value.unshift(s)
  activeSessionId.value = s.id
  saveSessions()
  sidebarOpen.value = false
}

const onNewSession = () => createSession()

const switchSession = (id) => {
  activeSessionId.value = id
  sidebarOpen.value = false
}

const onSwitchSession = (id) => switchSession(id)

const deleteSession = (id) => {
  sessions.value = sessions.value.filter(s => s.id !== id)
  localStorage.removeItem(`chat_session_${id}`)
  saveSessions()
  if (activeSessionId.value === id) {
    activeSessionId.value = sessions.value.length ? sessions.value[0].id : ''
    if (!activeSessionId.value) createSession()
  }
}

const onTitleUpdate = (id, title) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) { s.title = title; s.updatedAt = Date.now(); saveSessions() }
}

const sessionList = computed(() =>
  [...sessions.value].sort((a, b) => b.updatedAt - a.updatedAt)
)

const activeSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.id === activeSessionId.value)
  return s?.title || '新对话'
})

const chatRef = ref(null)

// 提供 requestValidation 给子树组件（通过 provide/inject 传递给 DynamicForm）
provide('chatRef', chatRef)

loadSessions()
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

.sidebar-wrapper {
  flex-shrink: 0;
}
.sidebar-mask {
  display: none;
}

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
  .sidebar-wrapper.sidebar-visible {
    transform: translateX(0);
  }

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

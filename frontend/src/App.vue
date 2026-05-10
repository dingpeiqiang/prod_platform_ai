<template>
  <div id="app">
    <!-- 统一 Loading -->
    <Loading :visible="isLoading" :text="loadingText" />
    
    <!-- 网络状态提示 -->
    <transition name="slide-down">
      <div v-if="!isOnline" class="network-status-bar offline">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="20" x2="21" y2="23"/>
          <line x1="12" y1="14" x2="12" y2="23"/>
          <line x1="6" y1="8" x2="6" y2="23"/>
          <line x1="2" y1="2" x2="23" y2="23"/>
        </svg>
        <span>网络连接已断开，请检查网络设置</span>
        <button class="reconnect-btn" @click="checkNetwork">重新连接</button>
      </div>
      <div v-else-if="!isBackendOnline" class="network-status-bar warning">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin-icon">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          <path d="M9 12l2 2 4-4"/>
        </svg>
        <span>服务器连接异常，正在自动重连...</span>
      </div>
    </transition>

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
        <!-- 首页：没有活动会话时显示 -->
        <DashboardHome
          v-if="!isInitializing && currentView === 'dashboard' && !activeSessionId"
          @send-message="onSendMessageFromHome"
          @switch-chat="onSwitchChat"
          @create-session="onNewSession"
          @open-scene-manager="openSceneManager"
          @open-prompt-manager="openPromptManager"
          @open-tool-manager="openToolManager"
          @open-form-manager="openFormManager"
          @open-ontology-manager="openOntologyManager"
        />

        <!-- 场景管理界面 -->
        <SceneManager 
          v-if="!isInitializing && currentView === 'scene-manager'" 
          @go-back="returnToDashboard"
        />

        <!-- 提示词管理界面 -->
        <PromptManager 
          v-if="!isInitializing && currentView === 'prompt-manager'" 
          @go-back="returnToDashboard"
        />

        <!-- 工具管理界面 -->
        <GenericManager 
          v-if="!isInitializing && currentView === 'tool-manager'" 
          title="🔧 工具管理"
          item-type="工具"
          code-field="toolCode"
          name-field="toolName"
          :show-tools="true"
          :api-service="toolApiService"
          @go-back="returnToDashboard"
        />

        <!-- 表单管理界面 -->
        <GenericManager 
          v-if="!isInitializing && currentView === 'form-manager'" 
          title="📝 表单管理"
          item-type="表单"
          code-field="formCode"
          name-field="formName"
          :show-entities="true"
          :api-service="formApiService"
          @go-back="returnToDashboard"
        />

        <!-- 本体管理界面 -->
        <OntologyManager 
          v-if="!isInitializing && currentView === 'ontology-manager'" 
          @go-back="returnToDashboard"
        />
        
        <!-- 聊天界面：有活动会话时显示 -->
        <ChatAssistant
          v-if="!isInitializing && currentView === 'dashboard' && activeSessionId"
          ref="chatRef"
          :sessionId="activeSessionId"
          :dbSessionId="activeDbSessionId"
          :userId="userStore.username"
          :sessionTitle="activeSessionTitle"
          @title-update="onTitleUpdate"
          @session-init="onSessionInit"
          @create-session-from-home="onCreateSessionFromHome"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, provide, onMounted, watch, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import Sidebar from './components/Sidebar.vue'
import ChatAssistant from './components/ChatAssistant.vue'
import LoginScreen from './components/LoginScreen.vue'
import DashboardHome from './components/DashboardHome.vue'
import SceneManager from './components/SceneManager.vue'
import PromptManager from './components/PromptManager.vue'
import GenericManager from './components/GenericManager.vue'
import OntologyManager from './components/OntologyManager.vue'
import Loading from './components/Loading.vue'
import { useUserStore } from './stores/user'
import { useLoadingStore } from './stores/loading'
import { useTheme } from './composables/useTheme'
import { createSession as apiCreateSession, getSessions as apiGetSessions, deleteSession as apiDeleteSession, updateSessionTitle as apiUpdateSessionTitle } from './services/chatApi.js'
import * as toolApi from './services/toolApi.js'
import * as formApi from './services/formApi.js'
import * as ontologyApi from './services/ontologyApi.js'

const currentView = ref('dashboard')

// 网络状态
const isOnline = ref(navigator.onLine)
const isBackendOnline = ref(true)
const reconnectAttempts = ref(0)
const isReconnecting = ref(false)
let backendCheckInterval = null
let reconnectTimeout = null

const userStore = useUserStore()
const loadingStore = useLoadingStore()
const { isLoading, loadingText } = storeToRefs(loadingStore)
const { initTheme } = useTheme()

const SESSIONS_KEY = 'chat_sessions'
const ACTIVE_SESSION_KEY = 'chat_active_session'

// ── 本地会话列表 ──────────────────────────────────────────
// 每项：{ id, title, createdAt, updatedAt, dbSessionId }
// dbSessionId: 数据库 session_id，已知则直接用，未知则首次发消息时创建
const sessions = ref([])
const activeSessionId = ref('')
const sidebarOpen = ref(false)
const isInitializing = ref(true)  // 初始化状态标志

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

// ── 打开场景管理 ─────────────────────────────────────────
const openSceneManager = () => {
  currentView.value = 'scene-manager'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── 打开提示词管理 ─────────────────────────────────────────
const openPromptManager = () => {
  currentView.value = 'prompt-manager'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── 打开工具管理 ─────────────────────────────────────────
const openToolManager = () => {
  currentView.value = 'tool-manager'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── 打开表单管理 ─────────────────────────────────────────
const openFormManager = () => {
  currentView.value = 'form-manager'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── 打开本体管理 ─────────────────────────────────────────
const openOntologyManager = () => {
  currentView.value = 'ontology-manager'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── API 服务适配器 ─────────────────────────────────────────
const toolApiService = {
  getCategories: toolApi.getToolCategories,
  list: toolApi.listTools,
  get: toolApi.getTool,
  create: toolApi.createTool,
  update: toolApi.updateTool,
  delete: toolApi.deleteTool,
  toggle: toolApi.toggleTool
}

const formApiService = {
  getCategories: formApi.getFormCategories,
  list: formApi.listForms,
  get: formApi.getForm,
  create: formApi.createForm,
  update: formApi.updateForm,
  delete: formApi.deleteForm,
  toggle: formApi.toggleForm
}

const ontologyApiService = {
  getCategories: ontologyApi.getOntologyCategories,
  list: ontologyApi.listOntologies,
  get: ontologyApi.getOntology,
  create: ontologyApi.createOntology,
  update: ontologyApi.updateOntology,
  delete: ontologyApi.deleteOntology,
  toggle: ontologyApi.toggleOntology
}

// ── 返回首页 ─────────────────────────────────────────────
const returnToDashboard = () => {
  currentView.value = 'dashboard'
}

// ── 新建按钮 ──────────────────────────────────────────────
const onNewSession = () => {
  // 点击新建会话不创建新会话，只跳转到首页
  currentView.value = 'dashboard'
  activeSessionId.value = ''
  activeDbSessionId.value = ''
  saveActiveSessionId()
  sidebarOpen.value = false
}

// ── 切换会话 ──────────────────────────────────────────────
const onSwitchSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  currentView.value = 'dashboard'
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
    if (sessions.value.length > 0) {
      // 还有其他会话，切换到第一个
      activeSessionId.value = sessions.value[0].id
      const first = sessions.value.find(s => s.id === activeSessionId.value)
      activeDbSessionId.value = first?.dbSessionId || ''
      saveActiveSessionId()
    } else {
      // 删除了最后一个会话，跳转回首页
      activeSessionId.value = ''
      activeDbSessionId.value = ''
      saveActiveSessionId()
    }
  }
}

// ── 标题更新 ──────────────────────────────────────────────
const onTitleUpdate = async (id, title) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) { 
    s.title = title; 
    s.updatedAt = Date.now(); 
    saveSessions();
    // 如果有数据库会话ID，同步更新到数据库
    if (s.dbSessionId) {
      await apiUpdateSessionTitle(s.dbSessionId, title);
    }
  }
}

// ── ChatAssistant 在首页发消息时，需要先创建本地会话 ──────
const onCreateSessionFromHome = async (initialText) => {
  const newSession = createLocalSession()
  // 等待一下让 props 传递更新
  await new Promise(resolve => setTimeout(resolve, 50))
  // 告诉 ChatAssistant 可以继续发消息了
  if (chatRef.value && chatRef.value.sendMessageAfterSessionCreated) {
    chatRef.value.sendMessageAfterSessionCreated(initialText, newSession.id)
  }
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

// ── 从首页发送消息 ────────────────────────────────────────
const onSendMessageFromHome = async (text) => {
  const newSession = createLocalSession()
  await new Promise(resolve => setTimeout(resolve, 50))
  if (chatRef.value && chatRef.value.sendMessageAfterSessionCreated) {
    chatRef.value.sendMessageAfterSessionCreated(text, newSession.id)
  }
}

// ── 切换聊天 ──────────────────────────────────────────────
const onSwitchChat = (sessionId) => {
  activeSessionId.value = sessionId
  const s = sessions.value.find(s => s.id === sessionId)
  activeDbSessionId.value = s?.dbSessionId || ''
  saveActiveSessionId()
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
  isInitializing.value = true  // 开始初始化
  loadingStore.show('正在初始化...')
  
  try {
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

    // 5. 刷新页面默认显示首页，不自动激活任何会话
    // 用户需要主动点击会话列表中的会话才能进入聊天界面
    activeSessionId.value = ''
    activeDbSessionId.value = ''
  } finally {
    isInitializing.value = false  // 初始化完成
    loadingStore.hide()
  }
}

// ── 检查后端服务是否在线 ──────────────────────────────────
const checkBackendOnline = async () => {
  if (!isOnline.value) {
    isBackendOnline.value = false
    return
  }
  try {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 5000)
    const response = await fetch('/api/v1/health', { 
      method: 'HEAD', 
      signal: controller.signal 
    })
    clearTimeout(timeoutId)
    
    if (response.ok && !isBackendOnline.value) {
      // 从离线恢复到在线
      reconnectAttempts.value = 0
      isReconnecting.value = false
    }
    isBackendOnline.value = response.ok
  } catch {
    const shouldReconnect = isBackendOnline.value || reconnectAttempts.value === 0
    if (shouldReconnect) {
      // 刚刚断开连接，或者是首次检查失败，开始重连流程
      // 先设置重连次数，再设置离线状态，确保 UI 显示正确
      reconnectAttempts.value = 1
      isReconnecting.value = true
      isBackendOnline.value = false
      attemptReconnect()
    } else {
      isBackendOnline.value = false
    }
  }
}

// ── 开始重连流程（带指数退避）──────────────────────────────
const startReconnect = () => {
  if (isReconnecting.value) return
  
  isReconnecting.value = true
  reconnectAttempts.value = 1  // 从第1次开始
  attemptReconnect()
}

// ── 尝试重连 ──────────────────────────────────────────────
const attemptReconnect = async () => {
  if (!isOnline.value) {
    // 网络离线，等待网络恢复
    isReconnecting.value = false
    return
  }
  
  try {
    const response = await fetch('/api/v1/health', { method: 'HEAD' })
    if (response.ok) {
      // 重连成功
      isBackendOnline.value = true
      isReconnecting.value = false
      reconnectAttempts.value = 0
      return
    }
  } catch {
    // 重连失败，继续尝试
  }
  
  // 指数退避：1s, 2s, 4s, 8s, 16s, 最大30s
  reconnectAttempts.value++
  const delay = Math.min(Math.pow(2, reconnectAttempts.value - 1) * 1000, 30000)
  
  reconnectTimeout = setTimeout(() => {
    if (!isBackendOnline.value) {
      attemptReconnect()
    }
  }, delay)
}

// ── 手动检查网络 ──────────────────────────────────────────
const checkNetwork = () => {
  if (navigator.onLine) {
    checkBackendOnline()
  }
}

// ── 网络状态变化处理 ──────────────────────────────────────
const handleOnline = () => {
  isOnline.value = true
  if (!isBackendOnline.value) {
    startReconnect()
  } else {
    checkBackendOnline()
  }
}

const handleOffline = () => {
  isOnline.value = false
  isBackendOnline.value = false
  isReconnecting.value = false
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
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

onMounted(() => {
  // 初始化主题（在应用加载时立即执行）
  initTheme()
  
  // 添加网络状态监听
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  
  // 初始检查后端服务状态
  checkBackendOnline()
  
  // 定期检查后端服务状态（每30秒）
  backendCheckInterval = setInterval(checkBackendOnline, 30000)
})

onUnmounted(() => {
  // 清理网络状态监听
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  
  // 清理定时器
  if (backendCheckInterval) {
    clearInterval(backendCheckInterval)
  }
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
  }
})
</script>

<style scoped>
/* 网络状态提示条 */
.network-status-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.network-status-bar.offline {
  background: var(--color-error-500);
  color: var(--text-inverse);
}

.network-status-bar.warning {
  background: var(--color-warning-500);
  color: var(--text-inverse);
}

.network-status-bar svg {
  flex-shrink: 0;
}

.network-status-bar span {
  flex: 1;
}

.reconnect-btn {
  flex-shrink: 0;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-md);
  color: var(--text-inverse);
  font-size: var(--font-size-xs);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.reconnect-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
}

.reconnect-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.reconnect-status {
  font-size: var(--font-size-xs);
  opacity: 0.8;
}

.spin-icon {
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

/* App 主容器 */
#app {
  display: flex;
  height: 100vh;
  height: 100dvh;
  width: 100vw;
  background: var(--bg-secondary);
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
  background: var(--bg-secondary);
  position: relative;
}

@media (max-width: 768px) {
  .sidebar-wrapper {
    position: fixed;
    top: 0; left: 0;
    height: 100vh; height: 100dvh;
    z-index: var(--z-fixed);
    transform: translateX(-100%);
    transition: transform 0.25s cubic-bezier(.4,0,.2,1);
  }
  .sidebar-wrapper.sidebar-visible { 
    transform: translateX(0); 
  }

  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: var(--bg-overlay);
    z-index: calc(var(--z-fixed) - 1);
    animation: fadeIn 0.2s ease;
  }
  @keyframes fadeIn {
    from { opacity: 0; } 
    to { opacity: 1; }
  }
}
</style>
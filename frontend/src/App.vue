<template>
  <div class="app-container">
    <!-- 加载状态 -->
    <Loading :visible="isLoading" :text="loadingText" />
    
    <!-- 网络状态提示 -->
    <transition name="slide-down">
      <div v-if="!isOnline" class="network-banner offline">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="1" y1="1" x2="23" y2="23"/>
          <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39"/>
          <path d="M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/>
        </svg>
        <span>网络已断开</span>
        <button @click="checkNetwork">重连</button>
      </div>
      <div v-else-if="!isBackendOnline" class="network-banner warning">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
          <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
        </svg>
        <span>服务器连接异常，正在重连...</span>
      </div>
    </transition>

    <!-- 登录页 -->
    <LoginScreen v-if="!userStore.isLoggedIn" />

    <!-- 主界面 -->
    <div class="main-layout">
      <!-- 左侧导航侧边栏 -->
      <Sidebar
        :sessions="sessionList"
        :activeId="activeSessionId"
        :isSidebarVisible="isSidebarVisible"
        :username="userStore.username"
        @new-session="onNewSession"
        @switch-session="onSwitchSession"
        @delete-session="deleteSession"
        @pin-session="pinSession"
        @share-session="shareSession"
        @rename-session="renameSession"
        @logout="handleLogout"
        @toggle-sidebar="toggleSidebar"
        @theme-toggle="toggleTheme"
      />

      <!-- 中间主聊天区 -->
      <div class="main-content">
        <!-- 聊天区域 -->
        <div v-if="activeSessionId" class="chat-area">
          <!-- 顶部标题栏 -->
          <div class="chat-header">
            <div class="header-left">
              <span class="session-title">{{ activeSessionTitle }}</span>
            </div>
            
            <div class="header-actions">
              <button class="header-btn" @click="clearContext" title="清空上下文">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
              <button class="header-btn" @click="shareSession(activeSessionId)" title="分享">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>
                  <polyline points="16 6 12 2 8 6"/>
                  <line x1="12" y1="2" x2="12" y2="15"/>
                </svg>
              </button>
              <button class="header-btn" @click="activeSessionId = ''" title="关闭对话">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <ChatMessageList
            ref="messageListRef"
            :messages="currentMessages"
            :showWelcome="showWelcome"
            @suggest="handleSuggest"
            @regenerate="handleRegenerate"
            @form-card-click="handleFormCardClick"
            @intent-action="handleIntentAction"
          />

          <!-- 输入区域 -->
          <ChatInput
            ref="chatInputRef"
            v-model="inputMessage"
            :disabled="isSending"
            :currentSkill="currentSkill"
            @send="handleSend"
            @stop="handleStop"
            @quick-action="handleQuickAction"
            @file-upload="handleFileUpload"
            @image-upload="handleImageUpload"
            @voice-record="handleVoiceRecord"
            @remove-skill="currentSkill = ''"
          />
        </div>

        <!-- 首页/欢迎页 -->
        <div v-else class="welcome-area">
          <DashboardHome
            @send-message="onSendMessageFromHome"
            @switch-chat="onSwitchChat"
            @create-session="onNewSession"
            @open-scene-manager="openSceneManager"
            @open-prompt-manager="openPromptManager"
            @open-ontology-manager="openOntologyManager"
            @open-workflow-manager="openWorkflowManager"
            @open-mcp-manager="openMCPManager"
            @open-kb-manager="openKBManager"
          />
        </div>
      </div>

      <!-- 右侧附属面板（可选） -->
      <div v-if="showRightPanel" class="right-panel">
        <div class="panel-header">
          <span>{{ rightPanelTitle }}</span>
          <button class="close-btn" @click="showRightPanel = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="panel-content">
          <!-- 右侧面板内容 -->
          <slot name="right-panel">
            <div class="panel-placeholder">
              <p>选择功能以查看详情</p>
            </div>
          </slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import Sidebar from './components/Sidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInput from './components/ChatInput.vue'
import DashboardHome from './components/DashboardHome.vue'
import LoginScreen from './components/LoginScreen.vue'
import Loading from './components/Loading.vue'
import { useUserStore } from './stores/user'
import { useLoadingStore } from './stores/loading'
import { 
  createSession as apiCreateSession, 
  getSessions as apiGetSessions, 
  deleteSession as apiDeleteSession,
  sendMessage as apiSendMessage,
  updateSessionTitle as apiUpdateSessionTitle 
} from './services/chatApi.js'

const userStore = useUserStore()
const loadingStore = useLoadingStore()
const { isLoading, loadingText } = storeToRefs(loadingStore)

// 状态
const sessions = ref([])
const activeSessionId = ref('')
const isSidebarVisible = ref(true)
const isOnline = ref(navigator.onLine)
const isBackendOnline = ref(true)
const inputMessage = ref('')
const isSending = ref(false)
const currentSkill = ref('')
const showRightPanel = ref(false)
const rightPanelTitle = ref('工具面板')
const messageListRef = ref(null)
const chatInputRef = ref(null)

const SESSIONS_KEY = 'chat_sessions'
const ACTIVE_SESSION_KEY = 'chat_active_session'

// 计算属性
const sessionList = computed(() => 
  [...sessions.value].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return b.updatedAt - a.updatedAt
  })
)

const activeSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.id === activeSessionId.value)
  return s?.title || '新对话'
})

const currentMessages = computed(() => {
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  return session?.messages || []
})

const showWelcome = computed(() => 
  !activeSessionId.value || currentMessages.value.length === 0
)

// 方法
const genId = () => `sess_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

const loadSessions = () => {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (raw) {
      sessions.value = JSON.parse(raw).map(s => ({
        ...s,
        messages: s.messages || []
      }))
    }
  } catch (e) {
    console.error('加载会话失败:', e)
  }
}

const saveSessions = () => {
  try {
    const sessionsToSave = sessions.value.map(s => ({
      ...s,
      messages: s.messages.slice(-50) // 只保留最近50条
    }))
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessionsToSave))
  } catch (e) {
    console.error('保存会话失败:', e)
  }
}

const saveActiveSessionId = () => {
  try {
    localStorage.setItem(ACTIVE_SESSION_KEY, activeSessionId.value)
  } catch {}
}

const createLocalSession = () => {
  const now = Date.now()
  const s = {
    id: genId(),
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    messages: [],
    pinned: false
  }
  sessions.value.unshift(s)
  activeSessionId.value = s.id
  saveSessions()
  saveActiveSessionId()
  return s
}

const onNewSession = () => {
  activeSessionId.value = ''
  saveActiveSessionId()
}

const onSwitchSession = (id) => {
  activeSessionId.value = id
  saveActiveSessionId()
}

const deleteSession = async (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s?.dbSessionId) {
    await apiDeleteSession(s.dbSessionId)
  }
  sessions.value = sessions.value.filter(s => s.id !== id)
  saveSessions()
  if (activeSessionId.value === id) {
    activeSessionId.value = ''
    saveActiveSessionId()
  }
}

const pinSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) {
    s.pinned = !s.pinned
    s.updatedAt = Date.now()
    saveSessions()
  }
}

const renameSession = async (id, newTitle) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) {
    s.title = newTitle
    s.updatedAt = Date.now()
    saveSessions()
    if (s.dbSessionId) {
      await apiUpdateSessionTitle(s.dbSessionId, newTitle)
    }
  }
}

const shareSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s?.dbSessionId) {
    const shareUrl = `${window.location.origin}/chat/${s.dbSessionId}`
    navigator.clipboard.writeText(shareUrl).then(() => {
      alert('分享链接已复制')
    })
  }
}

const toggleSidebar = () => {
  isSidebarVisible.value = !isSidebarVisible.value
}

const toggleTheme = (isDark) => {
  document.documentElement.classList.toggle('dark', isDark)
}

// 消息处理
const handleSend = async ({ text, attachments }) => {
  if (!text.trim() && !attachments?.length) return

  // 如果没有活动会话，创建新会话
  let session = sessions.value.find(s => s.id === activeSessionId.value)
  if (!session) {
    session = createLocalSession()
  }

  // 添加用户消息
  const userMessage = {
    id: genId(),
    role: 'user',
    content: text,
    attachments: attachments || [],
    timestamp: Date.now()
  }
  session.messages.push(userMessage)
  session.updatedAt = Date.now()
  saveSessions()

  // 发送请求
  isSending.value = true
  try {
    const response = await apiSendMessage({
      sessionId: session.dbSessionId,
      message: text,
      attachments
    })

    // 添加 AI 回复
    const aiMessage = {
      id: genId(),
      role: 'assistant',
      content: response.content,
      timestamp: Date.now(),
      done: true
    }
    session.messages.push(aiMessage)
    saveSessions()

    // 更新会话标题
    if (session.title === '新对话' && text) {
      const newTitle = text.slice(0, 20) + (text.length > 20 ? '...' : '')
      renameSession(session.id, newTitle)
    }
  } catch (error) {
    console.error('发送消息失败:', error)
  } finally {
    isSending.value = false
    nextTick(() => messageListRef.value?.scrollToBottom(true))
  }
}

const handleStop = () => {
  // 停止生成
  isSending.value = false
}

const handleSuggest = (content) => {
  inputMessage.value = content
  chatInputRef.value?.focus()
}

const handleQuickAction = (content) => {
  handleSend({ text: content, attachments: [] })
}

const handleRegenerate = (msg) => {
  // 重新生成消息
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    const index = session.messages.findIndex(m => m.id === msg.id)
    if (index > 0) {
      // 找到对应的问题重新发送
      const userMsg = session.messages[index - 1]
      handleSend({ text: userMsg.content, attachments: userMsg.attachments || [] })
    }
  }
}

const handleFileUpload = (files) => {
  console.log('文件上传:', files)
}

const handleImageUpload = (files) => {
  console.log('图片上传:', files)
}

const handleVoiceRecord = (attachment) => {
  console.log('语音录制:', attachment)
}

const handleFormCardClick = (msg) => {
  console.log('表单卡片点击:', msg)
}

const handleIntentAction = (event) => {
  console.log('意图动作:', event)
}

const clearContext = () => {
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    session.messages = []
    saveSessions()
  }
}

// 从首页发送消息
const onSendMessageFromHome = async (messageData) => {
  const session = createLocalSession()
  const text = typeof messageData === 'string' ? messageData : messageData?.text
  if (text) {
    await handleSend({ text, attachments: [] })
  }
}

const onSwitchChat = (sessionId) => {
  activeSessionId.value = sessionId
  saveActiveSessionId()
}

// 管理页面跳转
const openSceneManager = () => { /* ... */ }
const openPromptManager = () => { /* ... */ }
const openOntologyManager = () => { /* ... */ }
const openWorkflowManager = () => { /* ... */ }
const openMCPManager = () => { /* ... */ }
const openKBManager = () => { /* ... */ }

// 登出
const handleLogout = () => {
  sessions.value = []
  activeSessionId.value = ''
  userStore.logout()
}

// 网络检查
const checkNetwork = () => {
  isOnline.value = navigator.onLine
  if (isOnline.value) {
    checkBackendOnline()
  }
}

const checkBackendOnline = async () => {
  try {
    const response = await fetch('/api/v1/health', { method: 'HEAD' })
    isBackendOnline.value = response.ok
  } catch {
    isBackendOnline.value = false
  }
}

// 生命周期
onMounted(() => {
  loadSessions()
  const savedActiveId = localStorage.getItem(ACTIVE_SESSION_KEY)
  if (savedActiveId && sessions.value.find(s => s.id === savedActiveId)) {
    activeSessionId.value = savedActiveId
  }
  
  window.addEventListener('online', () => isOnline.value = true)
  window.addEventListener('offline', () => isOnline.value = false)
  
  // 定期检查后端
  setInterval(checkBackendOnline, 30000)
})

onUnmounted(() => {
  window.removeEventListener('online', () => {})
  window.removeEventListener('offline', () => {})
})
</script>

<style>
/* 全局样式变量 */
:root {
  /* 主色调 - 天蓝色 */
  --primary-500: #3b82f6;
  --primary-600: #2563eb;
  --primary-100: #dbeafe;
  
  /* 背景色 */
  --bg-primary: #ffffff;
  --bg-secondary: #f8fafc;
  --bg-tertiary: #f1f5f9;
  
  /* 边框 */
  --border-light: #e2e8f0;
  --border-default: #cbd5e1;
  
  /* 文字 */
  --text-primary: #1e293b;
  --text-secondary: #64748b;
  --text-tertiary: #94a3b8;
  --text-inverse: #ffffff;
  
  /* 侧边栏 */
  --sidebar-bg: #f8fafc;
  --sidebar-width: 280px;
  
  /* 过渡 */
  --transition-fast: 0.15s ease;
  --transition-normal: 0.2s ease;
  
  /* 层级 */
  --z-dropdown: 100;
  --z-modal: 200;
}

/* 深色模式 */
:root.dark {
  --bg-primary: #0f172a;
  --bg-secondary: #1e293b;
  --bg-tertiary: #334155;
  
  --border-light: #334155;
  --border-default: #475569;
  
  --text-primary: #f1f5f9;
  --text-secondary: #cbd5e1;
  --text-tertiary: #94a3b8;
  
  --sidebar-bg: #0f172a;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  display: flex;
  height: 100vh;
  height: 100dvh;
  background: var(--bg-primary);
  color: var(--text-primary);
  overflow: hidden;
}

.app-container {
  display: flex;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.main-layout {
  display: flex;
  flex: 1;
  min-width: 0;
}

/* 网络状态条 */
.network-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
}

.network-banner.offline {
  background: #ef4444;
  color: white;
}

.network-banner.warning {
  background: #f59e0b;
  color: white;
}

.network-banner button {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.network-banner button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.spin {
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-primary);
}

/* 聊天区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 聊天头部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-primary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.header-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

/* 欢迎区域 */
.welcome-area {
  flex: 1;
  overflow-y: auto;
  width: 100%;
}

/* 右侧面板 */
.right-panel {
  width: 320px;
  min-width: 320px;
  border-left: 1px solid var(--border-light);
  background: var(--bg-secondary);
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.panel-header span {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.close-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text-tertiary);
  cursor: pointer;
}

.close-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.panel-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
}

/* 过渡动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

/* 响应式 - 平板 */
@media (max-width: 1024px) {
  .right-panel {
    display: none;
  }

  .chat-header {
    padding: 12px 20px;
  }

  .session-title {
    font-size: 15px;
  }
}

/* 响应式 - 手机横屏/小平板 */
@media (max-width: 768px) {
  :root {
    --sidebar-width: 0px;
  }

  .network-banner {
    font-size: 12px;
    padding: 6px 12px;
  }

  .network-banner button {
    padding: 3px 10px;
    font-size: 11px;
  }

  .chat-header {
    padding: 10px 16px;
    height: 52px;
  }

  .header-left {
    gap: 8px;
  }

  .session-title {
    font-size: 15px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 50vw;
  }

  .header-actions {
    gap: 6px;
  }

  .header-btn {
    width: 34px;
    height: 34px;
  }

  .header-btn svg {
    width: 16px;
    height: 16px;
  }
}

/* 响应式 - 手机 */
@media (max-width: 480px) {
  .network-banner {
    font-size: 11px;
    padding: 6px 10px;
    gap: 6px;
  }

  .network-banner svg {
    width: 12px;
    height: 12px;
  }

  .chat-header {
    padding: 8px 12px;
    height: 48px;
  }

  .session-title {
    font-size: 14px;
    max-width: 45vw;
  }

  .header-btn {
    width: 32px;
    height: 32px;
  }

  .header-btn svg {
    width: 14px;
    height: 14px;
  }

  .main-content {
    min-width: 100%;
  }
}

/* 移动端触摸优化 */
@media (pointer: coarse) {
  .header-btn {
    min-height: 36px;
    min-width: 36px;
    -webkit-tap-highlight-color: transparent;
  }

  .header-btn:active {
    background: var(--bg-tertiary);
  }

  .network-banner button {
    min-height: 28px;
  }
}

/* iPhone X+ 刘海屏适配 */
@supports (padding-top: env(safe-area-inset-top)) {
  @media (max-width: 768px) {
    .network-banner {
      padding-top: calc(8px + env(safe-area-inset-top));
    }

    .chat-header {
      padding-top: calc(10px + env(safe-area-inset-top) * 0.5);
    }
  }

  @media (max-width: 480px) {
    .network-banner {
      padding-top: calc(6px + env(safe-area-inset-top));
    }
  }
}

/* 小高度屏幕优化 - 横屏手机 */
@media (max-height: 500px) and (orientation: landscape) {
  .chat-header {
    height: 44px;
    padding: 6px 12px;
  }

  .session-title {
    font-size: 13px;
  }

  .network-banner {
    position: relative;
    padding: 4px 8px;
    font-size: 11px;
  }
}

/* 平板横屏优化 */
@media (min-width: 769px) and (max-width: 1024px) and (orientation: landscape) {
  :root {
    --sidebar-width: 240px;
  }
}

/* 大屏幕优化 */
@media (min-width: 1400px) {
  .chat-header {
    padding: 16px 32px;
  }

  .session-title {
    font-size: 17px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .main-content,
  .chat-header {
    background: var(--bg-primary);
  }
}
</style>

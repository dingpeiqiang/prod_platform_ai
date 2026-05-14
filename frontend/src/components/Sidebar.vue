<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="6"/>
          <circle cx="12" cy="12" r="2"/>
          <path d="M12 6a6 6 0 0 1 4 1.5"/>
          <path d="M12 6a6 6 0 0 0-4 1.5"/>
          <path d="M12 18a6 6 0 0 1 4-1.5"/>
          <path d="M12 18a6 6 0 0 0-4-1.5"/>
          <path d="M6 12a6 6 0 0 1 1.5 4"/>
          <path d="M6 12a6 6 0 0 0 1.5-4"/>
          <path d="M18 12a6 6 0 0 1-1.5 4"/>
          <path d="M18 12a6 6 0 0 0-1.5-4"/>
          <circle cx="7.5" cy="8.5" r="1"/>
          <circle cx="16.5" cy="8.5" r="1"/>
          <circle cx="7.5" cy="15.5" r="1"/>
          <circle cx="16.5" cy="15.5" r="1"/>
        </svg>
      </div>
      <span class="logo-text">产商品研发助手</span>
    </div>

    <button class="new-chat-btn" @click="$emit('new-session')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      新建对话
    </button>

    <button class="langchain-btn" @click="$emit('open-langchain')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
      </svg>
      LangChain
    </button>

    <button class="visualization-btn" @click="$emit('open-visualization')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
        <polyline points="16 7 22 7 22 13"/>
      </svg>
      可视化
    </button>

    <button class="editor-btn" @click="$emit('open-langchain-editor')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
        <line x1="9" y1="9" x2="15" y2="9"/>
        <line x1="9" y1="15" x2="13" y2="15"/>
      </svg>
      工作流编辑器
    </button>

    <div class="session-list">
      <div class="session-group-label" v-if="todaySessions.length">今天</div>
      <div
        v-for="s in todaySessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId, pinned: s.pinned }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg v-if="s.pinned" class="pinned-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
          <circle cx="12" cy="10" r="3"/>
        </svg>
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button 
          class="session-menu-btn" 
          @click.stop="toggleSessionMenu(s.id)" 
          title="更多操作"
          ref="sessionMenuBtnRef"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="1"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="7"/>
          </svg>
        </button>
        
        <!-- 会话操作菜单 -->
        <div 
          v-if="activeSessionMenu === s.id" 
          class="session-menu" 
          @click.stop
        >
          <div class="session-menu-inner">
            <button class="session-menu-item pin" @click.stop="handlePinSession(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
              <span>{{ s.pinned ? '取消置顶' : '置顶' }}</span>
            </button>
            <button class="session-menu-item share" @click.stop="$emit('share-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 16.08c-.76 0-1.44.3-1.96.72L8.92 12l7.12-4.8c.54.5 1.25.87 2.04.87 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .79.38 1.49.97 1.92L8.92 12l7.08 4.08c-.59.43-.97 1.13-.97 1.92 0 1.66 1.34 3 3 3s3-1.34 3-3-1.34-3-3-3z"/>
              </svg>
              <span>分享</span>
            </button>
            <button class="session-menu-item rename" @click.stop="handleRename(s)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              <span>重命名</span>
            </button>
            <button class="session-menu-item report" @click.stop="handleReport(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 9v2m0 4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/>
              </svg>
              <span>举报</span>
            </button>
            <div class="menu-divider"></div>
            <button class="session-menu-item danger" @click.stop="$emit('delete-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
              <span>删除</span>
            </button>
          </div>
        </div>
      </div>

      <div class="session-group-label" v-if="olderSessions.length">更早</div>
      <div
        v-for="s in olderSessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId, pinned: s.pinned }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg v-if="s.pinned" class="pinned-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
          <circle cx="12" cy="10" r="3"/>
        </svg>
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button 
          class="session-menu-btn" 
          @click.stop="toggleSessionMenu(s.id)" 
          title="更多操作"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="1"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="7"/>
          </svg>
        </button>
        
        <!-- 会话操作菜单 -->
        <div 
          v-if="activeSessionMenu === s.id" 
          class="session-menu" 
          @click.stop
        >
          <div class="session-menu-inner">
            <button class="session-menu-item pin" @click.stop="handlePinSession(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
              <span>{{ s.pinned ? '取消置顶' : '置顶' }}</span>
            </button>
            <button class="session-menu-item share" @click.stop="$emit('share-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 16.08c-.76 0-1.44.3-1.96.72L8.92 12l7.12-4.8c.54.5 1.25.87 2.04.87 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .79.38 1.49.97 1.92L8.92 12l7.08 4.08c-.59.43-.97 1.13-.97 1.92 0 1.66 1.34 3 3 3s3-1.34 3-3-1.34-3-3-3z"/>
              </svg>
              <span>分享</span>
            </button>
            <button class="session-menu-item rename" @click.stop="handleRename(s)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              <span>重命名</span>
            </button>
            <button class="session-menu-item report" @click.stop="handleReport(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 9v2m0 4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/>
              </svg>
              <span>举报</span>
            </button>
            <div class="menu-divider"></div>
            <button class="session-menu-item danger" @click.stop="$emit('delete-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
              <span>删除</span>
            </button>
          </div>
        </div>
      </div>

      <div class="empty-tip" v-if="!sessions.length">暂无历史对话</div>
    </div>

    <div class="sidebar-footer">
      <!-- 用户信息 -->
      <div class="user-info" @click="showUserMenu = !showUserMenu" ref="userInfoRef">
        <div class="user-avatar" :style="{ background: avatarColor }">{{ avatarText }}</div>
        <div class="user-detail">
          <span class="user-name">{{ username }}</span>
          <span class="user-status">已登录</span>
        </div>
        <svg class="menu-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      <!-- 用户操作菜单 -->
      <div class="user-menu" v-if="showUserMenu" @click.stop>
        <div class="user-menu-inner">
          <!-- 主题切换 -->
          <button class="menu-item theme-toggle-item" @click="toggleTheme">
            <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle v-if="isDark" cx="12" cy="12" r="5"/>
              <path v-else d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              <line v-if="isDark" x1="12" y1="1" x2="12" y2="3"/>
              <line v-if="isDark" x1="12" y1="21" x2="12" y2="23"/>
              <line v-if="isDark" x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
              <line v-if="isDark" x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line v-if="isDark" x1="1" y1="12" x2="3" y2="12"/>
              <line v-if="isDark" x1="21" y1="12" x2="23" y2="12"/>
              <line v-if="isDark" x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
              <line v-if="isDark" x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
            <span class="menu-item-text">{{ isDark ? '切换亮色模式' : '切换暗色模式' }}</span>
          </button>
          
          <!-- 分隔线 -->
          <div class="menu-divider"></div>
          
          <!-- 退出登录 -->
          <button class="menu-item logout-item" @click="doLogout">
            <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span class="menu-item-text">退出登录</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '../stores/user'
import { useTheme } from '../composables/useTheme'

const userStore = useUserStore()
const { isDark, toggleTheme } = useTheme()
const showUserMenu = ref(false)
const activeSessionMenu = ref(null)
const userInfoRef = ref(null)

const username = computed(() => userStore.username)
const avatarText = computed(() => userStore.avatarText)
const avatarColor = computed(() => userStore.avatar)

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

// 调试：打印 sessions 数量
console.log('[Sidebar] sessions 数量:', props.sessions.length, 'activeId:', props.activeId)

const emit = defineEmits(['new-session', 'switch-session', 'delete-session', 'logout', 'pin-session', 'share-session', 'report-session', 'rename-session', 'open-langchain', 'open-visualization', 'open-langchain-editor'])

const handlePinSession = (sessionId) => {
  console.log('[Sidebar.vue] handlePinSession called with sessionId:', sessionId)
  emit('pin-session', sessionId)
}

const doLogout = () => {
  showUserMenu.value = false
  emit('logout')
}

const toggleSessionMenu = (sessionId) => {
  activeSessionMenu.value = activeSessionMenu.value === sessionId ? null : sessionId
}

const handleRename = (session) => {
  activeSessionMenu.value = null
  const newTitle = prompt('请输入新的对话名称:', session.title || '新对话')
  if (newTitle !== null) {
    emit('rename-session', session.id, newTitle.trim())
  }
}

const handleReport = (sessionId) => {
  activeSessionMenu.value = null
  emit('report-session', sessionId)
}

// 点击空白处关闭菜单
const handleClickOutside = (e) => {
  if (userInfoRef.value && !userInfoRef.value.contains(e.target)) {
    showUserMenu.value = false
  }
  activeSessionMenu.value = null
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

const today = new Date().toDateString()

const todaySessions = computed(() =>
  props.sessions.filter(s => new Date(s.updatedAt).toDateString() === today)
)
const olderSessions = computed(() =>
  props.sessions.filter(s => new Date(s.updatedAt).toDateString() !== today)
)
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  height: 100vh;
  height: 100dvh;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  padding: var(--space-4) var(--space-3);
  overflow: hidden;
}

@media (max-width: 768px) {
  .sidebar {
    width: var(--sidebar-width-mobile);
    min-width: var(--sidebar-width-mobile);
    box-shadow: var(--shadow-sidebar);
    padding: var(--space-4);
  }
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  padding: var(--space-1) var(--space-2) var(--space-5);
}
.logo-icon {
  width: 30px; height: 30px;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
}
.logo-text {
  color: var(--sidebar-text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.2px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 10px var(--space-3-5);
  background: var(--sidebar-hover-bg);
  border: 1px solid var(--sidebar-border);
  border-radius: var(--radius-lg);
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background var(--transition-fast), border-color var(--transition-fast);
  margin-bottom: var(--space-3-5);
}
.new-chat-btn:hover {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.15);
}

.langchain-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 10px var(--space-3-5);
  background: linear-gradient(135deg, rgba(91, 124, 250, 0.15), rgba(91, 124, 250, 0.05));
  border: 1px solid rgba(91, 124, 250, 0.3);
  border-radius: var(--radius-lg);
  color: var(--color-primary-400);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: var(--space-3-5);
}
.langchain-btn:hover {
  background: linear-gradient(135deg, rgba(91, 124, 250, 0.25), rgba(91, 124, 250, 0.1));
  border-color: rgba(91, 124, 250, 0.5);
  color: var(--color-primary-300);
}

.visualization-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 10px var(--space-3-5);
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15), rgba(34, 197, 94, 0.05));
  border: 1px solid rgba(34, 197, 94, 0.3);
  border-radius: var(--radius-lg);
  color: var(--color-success-400);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: var(--space-3-5);
}
.visualization-btn:hover {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.25), rgba(34, 197, 94, 0.1));
  border-color: rgba(34, 197, 94, 0.5);
  color: var(--color-success-300);
}

.editor-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 10px var(--space-3-5);
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.15), rgba(139, 92, 246, 0.05));
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: var(--radius-lg);
  color: var(--color-purple-400);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: var(--space-3-5);
}
.editor-btn:hover {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.25), rgba(139, 92, 246, 0.1));
  border-color: rgba(139, 92, 246, 0.5);
  color: var(--color-purple-300);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.session-list::-webkit-scrollbar { width: 3px; }
.session-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 2px; }

.session-group-label {
  font-size: var(--font-size-xs);
  color: var(--sidebar-text-muted);
  padding: 10px var(--space-3) var(--space-1);
  letter-spacing: 0.5px;
  font-weight: var(--font-weight-medium);
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 9px var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  position: relative;
  transition: background var(--transition-fast), color var(--transition-fast);
  margin-bottom: 1px;
}
.session-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
}
.session-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-text-primary);
}

.pinned-icon {
  flex-shrink: 0;
  color: var(--color-primary-500);
  opacity: 0.7;
}
.session-icon { flex-shrink: 0; opacity: 0.5; }
.session-item.active .session-icon { opacity: 0.8; }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-menu-btn {
  display: none;
  background: none;
  border: none;
  padding: 3px;
  cursor: pointer;
  color: var(--sidebar-text-muted);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  line-height: 1;
}
.session-menu-btn:hover { color: var(--sidebar-text-primary); background: var(--bg-tertiary); }
.session-item:hover .session-menu-btn { display: flex; }

.session-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  transform: translateX(0);
  z-index: var(--z-dropdown);
  animation: sessionMenuIn 0.15s cubic-bezier(.16,1,.3,1) both;
  min-width: 160px;
}

@keyframes sessionMenuIn {
  from { 
    opacity: 0; 
    transform: translateY(-8px) scale(0.95);
  }
  to   { 
    opacity: 1; 
    transform: translateY(0) scale(1);
  }
}

@keyframes menuItemIn {
  from {
    opacity: 0;
    transform: translateX(-8px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.session-menu-inner {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-1-5);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
}

.session-menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  width: 100%;
  padding: var(--space-2-5) var(--space-3);
  background: none;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  transition: all var(--transition-fast);
  text-align: left;
  position: relative;
  overflow: hidden;
}
.session-menu-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-primary-500);
  transform: scaleY(0);
  transition: transform var(--transition-fast);
}
.session-menu-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
  padding-left: var(--space-4);
}
.session-menu-item:hover::before {
  transform: scaleY(1);
}
.session-menu-item:active {
  transform: scale(0.98);
}

.session-menu-item .menu-icon {
  flex-shrink: 0;
  transition: all var(--transition-fast);
}
.session-menu-item:hover .menu-icon {
  transform: translateX(2px);
}

/* 置顶按钮 */
.session-menu-item.pin:hover {
  background: rgba(91, 124, 250, 0.1);
  color: var(--color-primary-500);
}
.session-menu-item.pin:hover::before {
  background: var(--color-primary-500);
}

/* 分享按钮 */
.session-menu-item.share:hover {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success-500);
}
.session-menu-item.share:hover::before {
  background: var(--color-success-500);
}

/* 重命名按钮 */
.session-menu-item.rename:hover {
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning-500);
}
.session-menu-item.rename:hover::before {
  background: var(--color-warning-500);
}

/* 举报按钮 */
.session-menu-item.report:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--color-error-500);
}
.session-menu-item.report:hover::before {
  background: var(--color-error-500);
}

/* 删除按钮 */
.session-menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error-500);
}
.session-menu-item.danger:hover::before {
  background: var(--color-error-500);
}

.menu-divider {
  height: 1px;
  background: var(--border-light);
  margin: var(--space-1-5) 0;
}

.empty-tip {
  text-align: center;
  color: var(--sidebar-text-muted);
  font-size: var(--font-size-sm);
  padding: var(--space-8) 0;
}

.sidebar-footer {
  border-top: 1px solid var(--sidebar-border);
  padding-top: var(--space-2-5);
  margin-top: var(--space-2);
  position: relative;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 9px var(--space-3);
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
  position: relative;
}
.user-info:hover { background: var(--sidebar-hover-bg); color: var(--sidebar-text-primary); }

.user-avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  flex-shrink: 0;
}

.user-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-status {
  font-size: var(--font-size-xs);
  color: var(--sidebar-text-muted);
}

.menu-arrow {
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}
.user-info:hover .menu-arrow { opacity: 0.8; }
.user-info.open .menu-arrow { transform: rotate(180deg); }

/* 用户操作菜单 */
.user-menu {
  position: absolute;
  bottom: calc(100% + 6px);
  left: var(--space-2);
  right: var(--space-2);
  z-index: var(--z-dropdown);
  animation: menuIn 0.15s cubic-bezier(.16,1,.3,1) both;
}

@keyframes menuIn {
  from { opacity: 0; transform: translateY(6px) scale(0.97); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.user-menu-inner {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-2);
  box-shadow: var(--shadow-xl);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  width: 100%;
  padding: var(--space-2-5) var(--space-3);
  background: none;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  transition: background var(--transition-fast), color var(--transition-fast);
  text-align: left;
}
.menu-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
}

.menu-item-icon {
  flex-shrink: 0;
  opacity: 0.7;
}

.menu-item-text {
  flex: 1;
}

.menu-divider {
  height: 1px;
  background: var(--border-light);
  margin: var(--space-2) 0;
}

.logout-item:hover {
  background: rgba(239,68,68,0.1);
  color: var(--color-error-500);
}

</style>

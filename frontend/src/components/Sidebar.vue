<template>
  <div class="sidebar" :class="{ collapsed: !isSidebarVisible }">
    <!-- 侧边栏头部 -->
    <div class="sidebar-header">
      <div class="logo-section" v-if="isSidebarVisible">
        <div class="logo-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <circle cx="12" cy="12" r="6"/>
            <circle cx="12" cy="12" r="2"/>
          </svg>
        </div>
        <span class="logo-text">产商品助手</span>
      </div>
      
      <button 
        class="toggle-btn"
        @click="$emit('toggle-sidebar')"
        :title="isSidebarVisible ? '收起' : '展开'"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline :points="isSidebarVisible ? '15 18 9 12 15 6' : '9 18 15 12 9 6'"/>
        </svg>
      </button>
    </div>

    <!-- 新建对话按钮 -->
    <button class="new-chat-btn" @click="$emit('new-session')" v-if="isSidebarVisible">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      <span>新建对话</span>
    </button>

    <!-- 对话列表 -->
    <div class="session-list" v-if="isSidebarVisible" ref="sessionListRef">
      <!-- 置顶对话 -->
      <div v-if="pinnedSessions.length" class="session-group">
        <div class="group-label">置顶</div>
        <div
          v-for="session in pinnedSessions"
          :key="session.id"
          :class="['session-item', { active: session.id === activeId }]"
          @click="$emit('switch-session', session.id)"
        >
          <div class="session-icon pinned">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
              <circle cx="12" cy="10" r="3"/>
            </svg>
          </div>
          <span class="session-title">{{ session.title || '新对话' }}</span>
          <button 
            class="menu-trigger"
            @click.stop="toggleMenu(session.id)"
            ref="menuTriggerRef"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="5" r="1.5"/>
              <circle cx="12" cy="12" r="1.5"/>
              <circle cx="12" cy="19" r="1.5"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 今天 -->
      <div v-if="todaySessions.length" class="session-group">
        <div class="group-label">今天</div>
        <div
          v-for="session in todaySessions"
          :key="session.id"
          :class="['session-item', { active: session.id === activeId }]"
          @click="$emit('switch-session', session.id)"
        >
          <div class="session-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <span class="session-title">{{ session.title || '新对话' }}</span>
          <button 
            class="menu-trigger"
            @click.stop="toggleMenu(session.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="5" r="1.5"/>
              <circle cx="12" cy="12" r="1.5"/>
              <circle cx="12" cy="19" r="1.5"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 更早 -->
      <div v-if="olderSessions.length" class="session-group">
        <div class="group-label">更早</div>
        <div
          v-for="session in olderSessions"
          :key="session.id"
          :class="['session-item', { active: session.id === activeId }]"
          @click="$emit('switch-session', session.id)"
        >
          <div class="session-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <span class="session-title">{{ session.title || '新对话' }}</span>
          <button 
            class="menu-trigger"
            @click.stop="toggleMenu(session.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="5" r="1.5"/>
              <circle cx="12" cy="12" r="1.5"/>
              <circle cx="12" cy="19" r="1.5"/>
            </svg>
          </button>
        </div>
      </div>

      <div v-if="!sessions.length" class="empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>暂无对话</span>
      </div>
    </div>

    <!-- 会话操作菜单 -->
    <div 
      v-if="activeMenu && isSidebarVisible" 
      class="session-menu"
      :style="menuPosition"
      @click.stop
    >
      <div class="menu-content">
        <button class="menu-item" @click="handlePin">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
            <circle cx="12" cy="10" r="3"/>
          </svg>
          <span>{{ menuSession?.pinned ? '取消置顶' : '置顶' }}</span>
        </button>
        <button class="menu-item" @click="handleRename">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
          <span>重命名</span>
        </button>
        <button class="menu-item" @click="handleShare">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>
            <polyline points="16 6 12 2 8 6"/>
            <line x1="12" y1="2" x2="12" y2="15"/>
          </svg>
          <span>分享</span>
        </button>
        <div class="menu-divider"></div>
        <button class="menu-item danger" @click="handleDelete">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
          <span>删除</span>
        </button>
      </div>
    </div>

    <!-- 底部用户信息 -->
    <div class="sidebar-footer" v-if="isSidebarVisible">
      <div class="user-section" @click="toggleUserMenu" ref="userSectionRef">
        <div class="user-avatar" :style="{ background: avatarGradient }">
          {{ userInitial }}
        </div>
        <div class="user-info">
          <span class="user-name">{{ username }}</span>
          <span class="user-status">在线</span>
        </div>
        <svg class="chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      <!-- 用户菜单 -->
      <div v-if="showUserMenu" class="user-menu" @click.stop>
        <div class="menu-content">
          <button class="menu-item" @click="toggleTheme">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle v-if="isDark" cx="12" cy="12" r="5"/>
              <path v-else d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              <template v-if="isDark">
                <line x1="12" y1="1" x2="12" y2="3"/>
                <line x1="12" y1="21" x2="12" y2="23"/>
                <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
                <line x1="1" y1="12" x2="3" y2="12"/>
                <line x1="21" y1="12" x2="23" y2="12"/>
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
              </template>
            </svg>
            <span>{{ isDark ? '切换亮色' : '切换暗色' }}</span>
          </button>
          <button class="menu-item" @click="showModelConfig = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <circle cx="12" cy="12" r="4"/>
            </svg>
            <span>模型设置</span>
          </button>
          <div class="menu-divider"></div>
          <button class="menu-item danger" @click="handleLogout">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  isSidebarVisible: { type: Boolean, default: true },
  username: { type: String, default: '用户' }
})

const emit = defineEmits([
  'new-session', 
  'switch-session', 
  'delete-session',
  'pin-session',
  'share-session',
  'rename-session',
  'logout',
  'toggle-sidebar',
  'theme-toggle'
])

// 状态
const activeMenu = ref(null)
const showUserMenu = ref(false)
const isDark = ref(false)
const showModelConfig = ref(false)
const sessionListRef = ref(null)
const userSectionRef = ref(null)

// 计算属性
const today = new Date().toDateString()

const pinnedSessions = computed(() => 
  props.sessions.filter(s => s.pinned)
)

const todaySessions = computed(() => 
  props.sessions.filter(s => !s.pinned && new Date(s.updatedAt).toDateString() === today)
)

const olderSessions = computed(() => 
  props.sessions.filter(s => !s.pinned && new Date(s.updatedAt).toDateString() !== today)
)

const menuSession = computed(() => 
  props.sessions.find(s => s.id === activeMenu.value)
)

const userInitial = computed(() => 
  props.username.charAt(0).toUpperCase()
)

const avatarGradient = computed(() => {
  const colors = [
    'linear-gradient(135deg, #3b82f6, #6366f1)',
    'linear-gradient(135deg, #10b981, #06b6d4)',
    'linear-gradient(135deg, #f59e0b, #ef4444)',
    'linear-gradient(135deg, #8b5cf6, #ec4899)'
  ]
  const index = props.username.length % colors.length
  return colors[index]
})

// 菜单位置计算
const menuPosition = computed(() => {
  return { top: 'auto', bottom: '60px', right: '16px' }
})

// 方法
const toggleMenu = (sessionId) => {
  activeMenu.value = activeMenu.value === sessionId ? null : sessionId
}

const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}

const handlePin = () => {
  emit('pin-session', activeMenu.value)
  activeMenu.value = null
}

const handleRename = () => {
  const session = menuSession.value
  if (!session) return
  
  const newTitle = prompt('请输入新的对话名称:', session.title || '新对话')
  if (newTitle && newTitle.trim()) {
    emit('rename-session', session.id, newTitle.trim())
  }
  activeMenu.value = null
}

const handleShare = () => {
  emit('share-session', activeMenu.value)
  activeMenu.value = null
}

const handleDelete = () => {
  if (confirm('确定要删除这个对话吗？')) {
    emit('delete-session', activeMenu.value)
  }
  activeMenu.value = null
}

const handleLogout = () => {
  showUserMenu.value = false
  emit('logout')
}

const toggleTheme = () => {
  isDark.value = !isDark.value
  emit('theme-toggle', isDark.value)
}

// 点击外部关闭菜单
const handleClickOutside = (e) => {
  if (!e.target.closest('.session-menu') && !e.target.closest('.menu-trigger')) {
    activeMenu.value = null
  }
  if (userSectionRef.value && !userSectionRef.value.contains(e.target)) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.sidebar {
  width: 280px;
  min-width: 280px;
  height: 100vh;
  background: var(--sidebar-bg, #f8fafc);
  border-right: 1px solid var(--border-light, #e2e8f0);
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
}

.sidebar.collapsed {
  width: 60px;
  min-width: 60px;
}

/* 头部 */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border-light, #e2e8f0);
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.toggle-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text-tertiary, #94a3b8);
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  background: var(--bg-tertiary, #e2e8f0);
  color: var(--text-secondary, #64748b);
}

.sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding: 16px 0;
}

.sidebar.collapsed .toggle-btn {
  transform: rotate(180deg);
}

/* 新建对话 */
.new-chat-btn {
  margin: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  background: white;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 10px;
  color: var(--text-primary, #1e293b);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.new-chat-btn:hover {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

/* 对话列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 12px;
}

.session-list::-webkit-scrollbar {
  width: 4px;
}

.session-list::-webkit-scrollbar-thumb {
  background: var(--border-default, #cbd5e1);
  border-radius: 2px;
}

.session-group {
  margin-bottom: 16px;
}

.group-label {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-tertiary, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
  transition: all 0.2s;
  position: relative;
}

.session-item:hover {
  background: rgba(59, 130, 246, 0.08);
  color: var(--text-primary, #1e293b);
}

.session-item.active {
  background: rgba(59, 130, 246, 0.12);
  color: #3b82f6;
}

.session-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--bg-tertiary, #e2e8f0);
  color: var(--text-tertiary, #94a3b8);
  flex-shrink: 0;
}

.session-icon.pinned {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.session-item.active .session-icon {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-trigger {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 4px;
  color: inherit;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
}

.session-item:hover .menu-trigger {
  opacity: 1;
}

.menu-trigger:hover {
  background: rgba(0, 0, 0, 0.05);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--text-tertiary, #94a3b8);
}

.empty-state span {
  font-size: 14px;
}

/* 菜单 */
.session-menu,
.user-menu {
  position: fixed;
  z-index: 1000;
  background: white;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  padding: 6px;
  min-width: 160px;
}

.menu-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.menu-item:hover {
  background: var(--bg-secondary, #f1f5f9);
  color: var(--text-primary, #1e293b);
}

.menu-item.danger {
  color: #ef4444;
}

.menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.menu-divider {
  height: 1px;
  background: var(--border-light, #e2e8f0);
  margin: 4px 0;
}

/* 底部用户信息 */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light, #e2e8f0);
  position: relative;
}

.user-section {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.user-section:hover {
  background: var(--bg-secondary, #f1f5f9);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1e293b);
}

.user-status {
  font-size: 12px;
  color: #10b981;
}

.chevron {
  color: var(--text-tertiary, #94a3b8);
  transition: transform 0.2s;
}

.user-section:hover .chevron {
  color: var(--text-secondary, #64748b);
}

/* 用户菜单 */
.user-menu {
  bottom: 70px;
  left: 16px;
  right: 16px;
}

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 100;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }
  
  .sidebar.collapsed {
    transform: translateX(-100%);
  }
}
</style>

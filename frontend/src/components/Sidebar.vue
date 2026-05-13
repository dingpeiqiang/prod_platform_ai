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

    <div class="session-list">
      <div class="session-group-label" v-if="todaySessions.length">今天</div>
      <div
        v-for="s in todaySessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button class="session-delete" @click.stop="$emit('delete-session', s.id)" title="删除对话">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="session-group-label" v-if="olderSessions.length">更早</div>
      <div
        v-for="s in olderSessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button class="session-delete" @click.stop="$emit('delete-session', s.id)" title="删除对话">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
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

const emit = defineEmits(['new-session', 'switch-session', 'delete-session', 'logout'])

const doLogout = () => {
  showUserMenu.value = false
  emit('logout')
}

// 点击空白处关闭菜单
const handleClickOutside = (e) => {
  if (userInfoRef.value && !userInfoRef.value.contains(e.target)) {
    showUserMenu.value = false
  }
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

.session-icon { flex-shrink: 0; opacity: 0.5; }
.session-item.active .session-icon { opacity: 0.8; }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-delete {
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
.session-delete:hover { color: var(--color-error-500); background: rgba(248,113,113,0.1); }
.session-item:hover .session-delete { display: flex; }

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

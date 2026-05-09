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
      <!-- 用户信息 & 登出 -->
      <div class="user-info" @click="showLogoutMenu = !showLogoutMenu" ref="userInfoRef">
        <div class="user-avatar" :style="{ background: avatarColor }">{{ avatarText }}</div>
        <div class="user-detail">
          <span class="user-name">{{ username }}</span>
          <span class="user-status">已登录</span>
        </div>
        <svg class="logout-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
          <polyline points="16 17 21 12 16 7"/>
          <line x1="21" y1="12" x2="9" y2="12"/>
        </svg>
      </div>

      <!-- 登出确认气泡 -->
      <div class="logout-menu" v-if="showLogoutMenu" @click.stop>
        <div class="logout-menu-inner">
          <div class="logout-tip">确定要退出登录吗？</div>
          <div class="logout-actions">
            <button class="logout-btn-cancel" @click="showLogoutMenu = false">取消</button>
            <button class="logout-btn-confirm" @click="doLogout">确认</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const showLogoutMenu = ref(false)
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
  showLogoutMenu.value = false
  emit('logout')
}

// 点击空白处关闭菜单
const handleClickOutside = (e) => {
  if (userInfoRef.value && !userInfoRef.value.contains(e.target)) {
    showLogoutMenu.value = false
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

.logout-icon {
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity var(--transition-fast);
}
.user-info:hover .logout-icon { opacity: 0.8; }

/* 登出确认气泡 */
.logout-menu {
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

.logout-menu-inner {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-3-5);
  box-shadow: var(--shadow-xl);
}

.logout-tip {
  font-size: var(--font-size-sm);
  color: var(--sidebar-text-primary);
  margin-bottom: var(--space-3);
}

.logout-actions {
  display: flex;
  gap: var(--space-2);
}

.logout-btn-cancel,
.logout-btn-confirm {
  flex: 1;
  padding: 7px 0;
  border-radius: var(--radius-md);
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-weight: var(--font-weight-medium);
  transition: opacity var(--transition-fast), transform 0.1s;
}
.logout-btn-cancel {
  background: rgba(255,255,255,0.07);
  color: var(--sidebar-text-secondary);
}
.logout-btn-cancel:hover { background: rgba(255,255,255,0.1); }

.logout-btn-confirm {
  background: rgba(239,68,68,0.2);
  color: var(--color-error-500);
  border: 1px solid rgba(239,68,68,0.25);
}
.logout-btn-confirm:hover {
  background: rgba(239,68,68,0.3);
  transform: translateY(-1px);
}

</style>

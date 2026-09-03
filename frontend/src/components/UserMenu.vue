<template>
  <div class="user-menu-wrap" ref="wrapEl">
    <button
      class="user-trigger"
      :class="{ active: open }"
      type="button"
      @click.stop="open = !open"
    >
      <span class="user-avatar" :style="avatarStyle">
        <span class="avatar-text">{{ avatarText }}</span>
      </span>
      <span class="user-name">{{ userStore.displayName || '用户' }}</span>
      <svg class="caret" :class="{ up: open }" width="12" height="12" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </button>

    <transition name="user-menu">
      <div v-if="open" class="user-dropdown" @click.stop>
        <div class="dropdown-header">
          <span class="dropdown-avatar" :style="avatarStyle">
            <span class="avatar-text">{{ avatarText }}</span>
          </span>
          <div class="dropdown-meta">
            <div class="dropdown-name">{{ userStore.displayName || '用户' }}</div>
          </div>
        </div>
        <ul class="dropdown-list">
          <li v-for="item in menuItems" :key="item.key">
            <button type="button" class="dropdown-item" @click="onSelect(item)">
              <span class="item-icon" v-html="item.icon" />
              <span class="item-label">{{ item.label }}</span>
            </button>
          </li>
        </ul>
        <div class="dropdown-footer">
          <button type="button" class="logout-btn" @click="onLogout">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            退出登录
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user.js'

const userStore = useUserStore()

const open = ref(false)
const wrapEl = ref(null)

const avatarText = computed(() => {
  const name = userStore.displayName || ''
  return name ? name.slice(-2) : '用户'
})

const avatarStyle = computed(() => {
  const colors = [
    'linear-gradient(135deg, #3b82f6, #6366f1)',
    'linear-gradient(135deg, #0f766e, #14b8a6)',
    'linear-gradient(135deg, #7c3aed, #a855f7)',
  ]
  const idx = (userStore.username || '').length % colors.length
  return { background: colors[idx] }
})

const menuItems = [
  {
    key: 'profile',
    label: '个人信息',
    icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
  },
  {
    key: 'settings',
    label: '账号设置',
    icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  },
  {
    key: 'help',
    label: '帮助与反馈',
    icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  },
]

const handleDocumentClick = (e) => {
  if (!open.value) return
  const wrap = wrapEl.value
  if (wrap && !wrap.contains(e.target)) {
    open.value = false
  }
}

watch(open, (visible) => {
  if (visible) {
    nextTick(() => document.addEventListener('click', handleDocumentClick))
  } else {
    document.removeEventListener('click', handleDocumentClick)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

const onSelect = (item) => {
  open.value = false
  ElMessage({ message: `${item.label}（待实现）`, type: 'info', duration: 1500 })
}

const onLogout = () => {
  open.value = false
  userStore.logout()
  ElMessage({ message: '已退出登录', type: 'success', duration: 1500 })
}
</script>

<style scoped>
.user-menu-wrap {
  position: relative;
  display: inline-flex;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  color: #0f172a;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.user-trigger:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}
.user-trigger.active {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.user-avatar,
.dropdown-avatar {
  flex-shrink: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #fff;
}
.user-avatar { width: 28px; height: 28px; }
.dropdown-avatar { width: 44px; height: 44px; }
.avatar-text {
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}
.dropdown-avatar .avatar-text { font-size: 16px; }

.user-name {
  font-size: 13px;
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret {
  color: #94a3b8;
  transition: transform 0.18s;
}
.caret.up { transform: rotate(180deg); }

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 50;
  width: 260px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f8fafc, #eff6ff);
  border-bottom: 1px solid #e2e8f0;
}
.dropdown-meta { min-width: 0; }
.dropdown-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.dropdown-list {
  list-style: none;
  margin: 0;
  padding: 6px;
}
.dropdown-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, color 0.15s;
}
.dropdown-item:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.item-icon {
  display: inline-flex;
  color: #64748b;
}
.dropdown-item:hover .item-icon { color: #2563eb; }

.dropdown-footer {
  padding: 6px;
  border-top: 1px solid #e2e8f0;
}
.logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #dc2626;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.logout-btn:hover { background: #fef2f2; }

.user-menu-enter-active,
.user-menu-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
  transform-origin: top right;
}
.user-menu-enter-from,
.user-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.96);
}
</style>

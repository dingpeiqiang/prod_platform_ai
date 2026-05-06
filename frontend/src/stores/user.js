/**
 * user store - 用户认证状态管理（模拟登录）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const USER_KEY = 'mock_user'

export const useUserStore = defineStore('user', () => {
  // ── 状态 ──────────────────────────────────────
  const userInfo = ref(null)  // { username, avatar, loginAt }

  // ── 计算属性 ──────────────────────────────────
  const isLoggedIn = computed(() => !!userInfo.value)
  const username = computed(() => userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || generateAvatar(username.value))
  // 取 username 首字符，汉字取拼音首字母（大人は理性）
  const avatarText = computed(() => {
    const name = username.value
    if (!name) return '?'
    // 简单处理：取首字符大写
    return name.charAt(0).toUpperCase()
  })

  // ── 登录 ──────────────────────────────────────
  const login = (name) => {
    const trimmed = (name || '').trim()
    if (!trimmed) return { success: false, message: '请输入用户名' }
    userInfo.value = {
      username: trimmed,
      loginAt: Date.now()
    }
    _save()
    return { success: true }
  }

  // ── 登出 ──────────────────────────────────────
  const logout = () => {
    userInfo.value = null
    _clear()
  }

  // ── 本地存储 ──────────────────────────────────
  const _save = () => {
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    } catch {}
  }

  const _clear = () => {
    try {
      localStorage.removeItem(USER_KEY)
    } catch {}
  }

  // ── 初始化 ────────────────────────────────────
  const load = () => {
    try {
      const raw = localStorage.getItem(USER_KEY)
      if (raw) userInfo.value = JSON.parse(raw)
    } catch {
      userInfo.value = null
    }
  }

  // ── 工具 ──────────────────────────────────────
  function generateAvatar(name) {
    if (!name) return '#6366f1'
    // 根据名字生成固定颜色
    let hash = 0
    for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) & 0xffffffff
    const colors = ['#6366f1','#8b5cf6','#a855f7','#ec4899','#f43f5e','#ef4444','#f97316','#eab308','#22c55e','#14b8a6','#06b6d4','#3b82f6']
    return colors[Math.abs(hash) % colors.length]
  }

  // 启动时加载
  load()

  return {
    userInfo, isLoggedIn, username, avatar, avatarText,
    login, logout, load
  }
})
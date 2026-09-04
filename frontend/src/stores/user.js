/**
 * user store - 用户认证状态管理（JWT 真实登录）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout } from '../services/authApi.js'

const USER_KEY = 'auth_user'
const TOKEN_KEY = 'auth_token'

export const useUserStore = defineStore('user', () => {
  // ── 状态 ──────────────────────────────────────
  const userInfo = ref(null)  // { username, displayName, role, lastLoginAt }
  const token = ref(null)

  // ── 计算属性 ──────────────────────────────────
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)
  const username = computed(() => userInfo.value?.username || '')
  // 展示名：优先昵称字段，兜底 username（问候语等场景使用）
  const displayName = computed(() => userInfo.value?.displayName || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || generateAvatar(username.value))
  // 取 username 首字符
  const avatarText = computed(() => {
    const name = username.value
    if (!name) return '?'
    return name.charAt(0).toUpperCase()
  })

  // ── 登录 ──────────────────────────────────────
  const login = async (name, password) => {
    const trimmed = (name || '').trim()
    if (!trimmed) return { success: false, message: '请输入用户名' }
    if (!password) return { success: false, message: '请输入密码' }
    try {
      const res = await apiLogin(trimmed, password)
      if (!res?.success) {
        return { success: false, message: res?.message || '登录失败' }
      }
      token.value = res.token
      userInfo.value = {
        username: res.user?.username || trimmed,
        displayName: res.user?.display_name || trimmed,
        role: res.user?.role || 'user',
        lastLoginAt: res.user?.last_login_at || null
      }
      _save()
      return { success: true }
    } catch (err) {
      return { success: false, message: err?.message || '登录失败，请检查网络' }
    }
  }

  /** 访客注册并进入（体验账号，服务端自助注册） */
  const guestLogin = async () => {
    const suffix = Date.now().toString(36)
    const name = `访客_${suffix}`
    try {
      const res = await apiRegister(name, 'guest-pass-' + suffix, '体验访客')
      if (!res?.success) {
        // 注册失败（如服务端关闭注册）则尝试直接登录固定访客账号
        return login(name, 'guest-pass-' + suffix)
      }
      token.value = res.token
      userInfo.value = {
        username: res.user?.username || name,
        displayName: res.user?.display_name || '体验访客',
        role: res.user?.role || 'user',
        lastLoginAt: res.user?.last_login_at || null
      }
      _save()
      return { success: true }
    } catch (err) {
      return { success: false, message: err?.message || '访客进入失败，请检查网络' }
    }
  }

  // ── 登出 ──────────────────────────────────────
  const logout = async () => {
    try {
      if (token.value) await apiLogout()
    } catch { /* 服务端登出失败不阻塞本地清理 */ }
    userInfo.value = null
    token.value = null
    _clear()
  }

  /** httpClient 启动时读取 token */
  const getToken = () => token.value

  // ── 本地存储 ──────────────────────────────────
  const _save = () => {
    try {
      localStorage.setItem(TOKEN_KEY, token.value || '')
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    } catch {}
  }

  const _clear = () => {
    try {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    } catch {}
  }

  // ── 初始化 ────────────────────────────────────
  const load = () => {
    try {
      const rawToken = localStorage.getItem(TOKEN_KEY)
      const rawUser = localStorage.getItem(USER_KEY)
      if (rawToken) token.value = rawToken
      if (rawUser) userInfo.value = JSON.parse(rawUser)
      if (!rawToken) userInfo.value = null
    } catch {
      userInfo.value = null
      token.value = null
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
    userInfo, token, isLoggedIn, username, displayName, avatar, avatarText,
    login, guestLogin, logout, load, getToken
  }
})

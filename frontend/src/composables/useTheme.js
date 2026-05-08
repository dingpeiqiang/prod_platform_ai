/**
 * useTheme - 主题管理 Composable
 * 提供暗色/亮色主题切换功能
 * 
 * 使用方式：
 * import { useTheme } from '@/composables/useTheme'
 * 
 * const { isDark, toggleTheme } = useTheme()
 */

import { ref, computed, onMounted, watch } from 'vue'

const THEME_KEY = 'app_theme'
const STORAGE_KEY = 'theme'

// 全局主题状态（跨组件共享）
const isDark = ref(false)

// 初始化标志
let initialized = false

export function useTheme() {
  // 初始化主题
  const initTheme = () => {
    if (initialized) return
    initialized = true

    // 1. 检查 localStorage 保存的主题
    const savedTheme = localStorage.getItem(STORAGE_KEY)
    if (savedTheme) {
      isDark.value = savedTheme === 'dark'
    } else {
      // 2. 检查系统偏好
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      isDark.value = prefersDark
    }

    // 3. 应用主题到 document
    applyTheme()
  }

  // 应用主题到 DOM
  const applyTheme = () => {
    if (isDark.value) {
      document.documentElement.setAttribute('data-theme', 'dark')
    } else {
      document.documentElement.removeAttribute('data-theme')
    }
  }

  // 切换主题
  const toggleTheme = () => {
    isDark.value = !isDark.value
    applyTheme()
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
  }

  // 设置特定主题
  const setTheme = (theme) => {
    isDark.value = theme === 'dark'
    applyTheme()
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
  }

  // 监听系统主题变化
  const setupSystemThemeListener = () => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQuery.addEventListener('change', (e) => {
      // 只有在用户没有手动设置主题时才跟随系统
      if (!localStorage.getItem(STORAGE_KEY)) {
        isDark.value = e.matches
        applyTheme()
      }
    })
  }

  // 响应式计算属性
  const themeIcon = computed(() => isDark.value ? '🌙' : '☀️')
  const themeLabel = computed(() => isDark.value ? '暗色模式' : '亮色模式')
  const themeClass = computed(() => isDark.value ? 'dark' : 'light')

  // 生命周期钩子
  onMounted(() => {
    initTheme()
    setupSystemThemeListener()
  })

  return {
    isDark,
    toggleTheme,
    setTheme,
    themeIcon,
    themeLabel,
    themeClass,
    initTheme
  }
}

export default useTheme
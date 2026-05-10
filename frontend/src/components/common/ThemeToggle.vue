<template>
  <button
    class="theme-toggle"
    @click="toggleTheme"
    :title="themeLabel"
    :aria-label="themeLabel"
    :class="{ 'is-dark': isDark }"
  >
    <!-- 太阳图标（亮色模式显示） -->
    <svg
      v-if="!isDark"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <circle cx="12" cy="12" r="5"/>
      <line x1="12" y1="1" x2="12" y2="3"/>
      <line x1="12" y1="21" x2="12" y2="23"/>
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
      <line x1="1" y1="12" x2="3" y2="12"/>
      <line x1="21" y1="12" x2="23" y2="12"/>
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
    </svg>
    
    <!-- 月亮图标（暗色模式显示） -->
    <svg
      v-else
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
    </svg>
  </button>
</template>

<script setup>
import { useTheme } from '@/composables/useTheme'

const { isDark, toggleTheme, themeLabel } = useTheme()
</script>

<style scoped>
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--sidebar-hover-bg);
  border: 1px solid var(--sidebar-border);
  color: var(--sidebar-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.theme-toggle:hover {
  background: rgba(255,255,255,0.1);
  color: var(--sidebar-text-primary);
  border-color: rgba(255,255,255,0.2);
}

.theme-toggle:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 2px;
}

/* 暗色模式激活状态 */
.theme-toggle.is-dark {
  background: rgba(255,255,255,0.08);
  border-color: rgba(255,255,255,0.15);
  color: var(--color-primary-400);
}

.theme-toggle.is-dark:hover {
  background: rgba(255,255,255,0.12);
  color: var(--color-primary-300);
}
</style>
<template>
  <header class="assistant-nav">
    <div class="nav-brand">
      <div class="brand">AI 原生产商品助手</div>
      <div class="title">{{ title }}</div>
    </div>

    <!-- 助手切换器：RouterLink 驱动（rd/ops 双助手路由架构） -->
    <div class="assistant-switcher">
      <RouterLink
        v-for="a in assistants"
        :key="a.key"
        :to="a.route"
        class="switch-item"
        :class="{ active: mode === a.key }"
        :style="mode === a.key ? { '--sw-accent': a.color, '--sw-bg': a.bg } : null"
      >
        {{ a.name }}
      </RouterLink>
      <span class="switch-sep" />
      <RouterLink to="/admin" class="switch-item admin-item" title="工作流管理、模型管理等后台配置入口">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        后台管理中心
      </RouterLink>
    </div>

    <div class="nav-right">
      <div v-if="$slots.actions" class="nav-actions">
        <slot name="actions" />
      </div>
      <UserMenu />
    </div>
  </header>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import UserMenu from './UserMenu.vue'
import { assistants } from '../config/assistantModes.js'

defineProps({
  mode: { type: String, default: 'rd' },
  title: { type: String, default: '' },
})
</script>

<style scoped>
.assistant-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 28px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.nav-brand { min-width: 0; flex-shrink: 0; }
.brand {
  font-size: 12px;
  color: #0f766e;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.assistant-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: #f1f5f9;
  border-radius: 12px;
}
.switch-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 9px;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}
.switch-item:hover { color: #0f172a; }
.switch-item.active {
  background: var(--sw-bg, #ffffff);
  color: var(--sw-accent, #0f172a);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}
.switch-item.workflow-item {
  color: #15803d;
}
.switch-item.workflow-item:hover {
  background: #f0fdf4;
}
.switch-item.admin-item {
  color: #475569;
}
.switch-item.admin-item:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.switch-sep {
  width: 1px;
  height: 16px;
  background: #cbd5e1;
  margin: 0 2px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

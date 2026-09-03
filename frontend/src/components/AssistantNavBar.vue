<template>
  <header class="assistant-nav">
    <div class="nav-brand">
      <div class="brand">AI 原生产商品助手</div>
      <div class="title">{{ title }}</div>
    </div>
    <div class="nav-right">
      <div v-if="$slots.actions" class="nav-actions">
        <slot name="actions" />
      </div>
      <nav class="links">
        <RouterLink to="/rd" :class="{ active: mode === 'rd' }">研发助手</RouterLink>
        <RouterLink to="/ops" :class="{ active: mode === 'ops' }">运营助手</RouterLink>
        <RouterLink to="/workflow-editor" class="workflow-link">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1"/>
            <rect x="14" y="14" width="7" height="7" rx="1"/>
            <path d="M10 6.5h6a1.5 1.5 0 0 1 1.5 1.5v6"/>
            <path d="M6.5 10v6a1.5 1.5 0 0 0 1.5 1.5h6"/>
          </svg>
          工作流管理
        </RouterLink>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { RouterLink } from 'vue-router'

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
  padding: 14px 28px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.nav-brand { min-width: 0; }
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
  margin-top: 4px;
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
.links { display: flex; gap: 10px; }
.links a {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  color: #334155;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}
.links a.active {
  background: #0f766e;
  color: #fff;
  border-color: #0f766e;
}
/* S1 工作流管理入口：次级强调（描边风格区别于助手页签） */
.links a.workflow-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-color: #16a34a;
  color: #15803d;
  background: #f0fdf4;
}
.links a.workflow-link:hover {
  background: #dcfce7;
  border-color: #15803d;
}
</style>

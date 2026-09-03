<template>
  <header class="admin-nav">
    <div class="nav-brand">
      <div class="brand">AI 原生产商品助手</div>
      <div class="title">{{ title }}</div>
    </div>

    <div class="nav-right">
      <slot name="actions" />
      <RouterLink to="/rd" class="nav-icon-btn" title="返回助手">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
      </RouterLink>
      <UserMenu />
    </div>
  </header>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import UserMenu from './UserMenu.vue'

defineProps({
  title: { type: String, default: '后台管理中心' },
})
</script>

<style scoped>
.admin-nav {
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
.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.nav-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.nav-icon-btn:hover {
  border-color: #93c5fd;
  background: #f0f9ff;
}
</style>

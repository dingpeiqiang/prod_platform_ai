<template>
  <header class="assistant-nav">
    <div>
      <div class="brand">AI 原生产商品助手</div>
      <div class="title">{{ title }}</div>
    </div>
    <nav class="links">
      <RouterLink to="/rd" :class="{ active: mode === 'rd' }">研发助手</RouterLink>
      <RouterLink to="/ops" :class="{ active: mode === 'ops' }">运营助手</RouterLink>
    </nav>
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
.assistant-nav { display:flex; justify-content:space-between; align-items:center; padding:16px 28px; border-bottom:1px solid #e2e8f0; background:#fff; }
.brand { font-size:12px; color:#0f766e; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }
.title { font-size:18px; font-weight:700; color:#0f172a; margin-top:4px; }
.links { display:flex; gap:10px; }
.links a { padding:8px 14px; border-radius:999px; border:1px solid #cbd5e1; color:#334155; text-decoration:none; }
.links a.active { background:#0f766e; color:#fff; border-color:#0f766e; }
</style>

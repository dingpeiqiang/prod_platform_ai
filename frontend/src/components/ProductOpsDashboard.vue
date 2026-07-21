<template>
  <div class="product-ops-dashboard">
    <section class="hero">
      <p class="eyebrow">产商品运营助手</p>
      <h1>AI 原生的产商品运营工作台</h1>
      <p class="subtitle">用本体统一事实，用规则守住底线，用大模型提升效率。</p>
      <div class="actions">
        <button class="primary" @click="$emit('open-root-cause')">异动根因分析</button>
        <button @click="$emit('open-risk')">风险稽核</button>
        <button @click="$emit('open-online')">立项研判</button>
        <button @click="$emit('open-query')">市场洞察</button>
      </div>
    </section>
  </div>
</template>

<script setup>
defineEmits(['open-query', 'open-online', 'open-root-cause', 'open-risk'])
</script>

<style scoped>
.product-ops-dashboard { padding: 24px 28px 0; }
.hero { border: 1px solid #d1fae5; background: linear-gradient(135deg, #ecfdf5, #f8fafc); border-radius: 20px; padding: 24px; }
.eyebrow { margin: 0 0 8px; color: #0f766e; font-weight: 700; }
h1 { margin: 0; font-size: 28px; color: #0f172a; }
.subtitle { margin: 10px 0 0; color: #475569; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
button { border: 1px solid #cbd5e1; background: #fff; color: #0f172a; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
button.primary { background: #0f766e; border-color: #0f766e; color: #fff; }
</style>

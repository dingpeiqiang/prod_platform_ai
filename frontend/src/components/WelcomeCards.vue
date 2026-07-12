<template>
  <div class="welcome-cards-container">
    <p class="welcome-title">您好！我是产品智能配置助手，可以帮您快速完成商品配置。</p>
    <div class="welcome-cards">
      <button type="button" class="welcome-card" @click="$emit('card-click', 'query')">
        <div class="card-icon"><i class="fa-solid fa-magnifying-glass" /></div>
        <h4>AI智查</h4>
        <p>查询历史商品，快速复制配置</p>
      </button>
      <button type="button" class="welcome-card" @click="$emit('card-click', 'file')">
        <div class="card-icon"><i class="fa-solid fa-file-import" /></div>
        <h4>AI方案导入</h4>
        <p>上传文档，批量导入配置</p>
      </button>
      <button type="button" class="welcome-card" @click="$emit('card-click', 'chat')">
        <div class="card-icon"><i class="fa-solid fa-comments" /></div>
        <h4>对话式配置</h4>
        <p>自然语言描述，智能生成配置</p>
      </button>
    </div>
  </div>
</template>

<script setup>
defineEmits(['card-click'])
</script>

<style scoped>
.welcome-cards-container {
  padding: 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
}

.welcome-title {
  font-size: var(--font-size-base);
  color: var(--text-primary);
  margin-bottom: 16px;
  text-align: center;
}

.welcome-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-width: 780px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .welcome-cards {
    grid-template-columns: 1fr;
  }
}

.welcome-card {
  text-align: left;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 16px;
  transition: all var(--transition-fast);
  cursor: pointer;
}

.welcome-card:hover {
  border-color: var(--color-primary-400);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
  transform: translateY(-2px);
}

.card-icon {
  width: 42px;
  height: 42px;
  background: var(--color-primary-100);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--color-primary-600);
  font-size: 1.125rem;
}

.welcome-card h4 {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 4px;
  color: var(--text-primary);
}

.welcome-card p {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}
</style>

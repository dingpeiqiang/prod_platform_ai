<template>
  <div class="welcome-container">
    <div class="welcome-content">
      <!-- 品牌区域 -->
      <div class="brand-section">
        <div class="brand-logo">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <circle cx="12" cy="12" r="6"/>
            <circle cx="12" cy="12" r="2"/>
          </svg>
        </div>
        <h1 class="brand-title">产商品智能助手</h1>
        <p class="brand-subtitle">基于 AI 的智能产商品配置平台，让配置更简单</p>
      </div>

      <!-- 快捷推荐问题卡片 - 与 index_v31.html 一致 -->
      <div class="suggestions-section">
        <div class="suggestion-cards">
          <div
            class="suggestion-card"
            @click="handleCardClick('query')"
          >
            <div class="card-icon" style="background: var(--primary-light); color: var(--primary-color);">
              <i class="fa-solid fa-magnifying-glass"></i>
            </div>
            <div class="card-content">
              <div class="card-title">AI智查</div>
              <div class="card-desc">查询历史商品，快速复制配置</div>
            </div>
          </div>

          <div
            class="suggestion-card"
            @click="handleCardClick('file')"
          >
            <div class="card-icon" style="background: var(--primary-light); color: var(--primary-color);">
              <i class="fa-solid fa-file-import"></i>
            </div>
            <div class="card-content">
              <div class="card-title">AI方案导入</div>
              <div class="card-desc">上传文档，批量导入配置</div>
            </div>
          </div>

          <div
            class="suggestion-card"
            @click="handleCardClick('chat')"
          >
            <div class="card-icon" style="background: var(--primary-light); color: var(--primary-color);">
              <i class="fa-solid fa-comments"></i>
            </div>
            <div class="card-content">
              <div class="card-title">对话式配置</div>
              <div class="card-desc">自然语言描述，智能生成配置</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const emit = defineEmits(['card-click'])

// 处理卡片点击 - 与 index_v31.html 的 handleCardClick 一致
const handleCardClick = (type) => {
  emit('card-click', type)
}
</script>

<style scoped>
:root {
  --primary-color: #25b2fe;
  --primary-light: #e6f4ff;
}

.welcome-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  overflow-y: auto;
}

.welcome-content {
  width: 100%;
  text-align: center;
  padding: 0 48px;
}

/* 品牌区域 */
.brand-section {
  margin-bottom: 48px;
}

.brand-logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 8px 32px rgba(59, 130, 246, 0.3);
}

.brand-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px 0;
}

.brand-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
}

/* 推荐卡片 - 与 index_v31.html 的 welcome-cards 样式一致 */
.suggestions-section {
  margin-bottom: 32px;
}

.suggestion-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  max-width: 800px;
  margin: 0 auto;
}

.suggestion-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
  background: white;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.suggestion-card:hover {
  border-color: #25b2fe;
  box-shadow: 0 2px 8px rgba(37, 178, 254, 0.2);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--text-tertiary);
  line-height: 1.4;
}

/* 响应式 */
@media (max-width: 768px) {
  .suggestion-cards {
    grid-template-columns: 1fr;
    max-width: 400px;
  }

  .brand-title {
    font-size: 24px;
  }
}
</style>

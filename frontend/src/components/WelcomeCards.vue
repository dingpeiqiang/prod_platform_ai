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

      <!-- 快捷推荐问题卡片 -->
      <div class="suggestions-section">
        <div class="suggestion-cards">
          <div
            v-for="(card, index) in suggestionCards"
            :key="index"
            class="suggestion-card"
            @click="handleClick(card.content)"
          >
            <div class="card-icon" :style="{ background: card.bgColor, color: card.iconColor }">
              <i :class="card.icon"></i>
            </div>
            <div class="card-content">
              <div class="card-title">{{ card.title }}</div>
              <div class="card-desc">{{ card.desc }}</div>
            </div>
            <svg class="card-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>
        </div>
      </div>

      <!-- 快捷功能入口 -->
      <div class="quick-features">
        <div class="feature-item" @click="handleClick('帮我查询一个表单配置')">
          <div class="feature-icon" style="background: #fef3c7; color: #f59e0b;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </div>
          <span class="feature-text">智能查询</span>
        </div>
        <div class="feature-item" @click="handleClick('帮我导入一个方案')">
          <div class="feature-icon" style="background: #dbeafe; color: #3b82f6;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <span class="feature-text">方案导入</span>
        </div>

        <div class="feature-item" @click="handleClick('帮我分析这些数据')">
          <div class="feature-icon" style="background: #fce7f3; color: #ec4899;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 20V10"/>
              <path d="M12 20V4"/>
              <path d="M6 20v-6"/>
            </svg>
          </div>
          <span class="feature-text">数据分析</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const emit = defineEmits(['suggest'])

const suggestionCards = [
  {
    title: '导入Excel方案',
    desc: '上传Excel文件，智能解析并导入',
    content: '帮我导入一个Excel配置方案',
    icon: 'fa-solid fa-file-excel',
    bgColor: '#d1fae5',
    iconColor: '#10b981'
  },
  {
    title: '查询已有配置',
    desc: '搜索并查看现有的表单配置',
    content: '帮我查询现有的表单配置',
    icon: 'fa-solid fa-magnifying-glass',
    bgColor: '#fef3c7',
    iconColor: '#f59e0b'
  },
  {
    title: '修改字段属性',
    desc: '快速调整表单的字段和属性',
    content: '我想修改表单的字段属性',
    icon: 'fa-solid fa-pen-to-square',
    bgColor: '#fce7f3',
    iconColor: '#ec4899'
  }
]

const handleClick = (content) => {
  emit('suggest', content)
}
</script>

<style scoped>
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

/* 推荐卡片 */
.suggestions-section {
  margin-bottom: 32px;
}

.suggestion-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.suggestion-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 20px rgba(59, 130, 246, 0.1);
  transform: translateY(-2px);
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.card-content {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--text-tertiary);
  line-height: 1.4;
}

.card-arrow {
  color: var(--text-tertiary);
  flex-shrink: 0;
  transition: transform 0.2s;
}

.suggestion-card:hover .card-arrow {
  transform: translateX(4px);
  color: #3b82f6;
}

/* 快捷功能 */
.quick-features {
  display: flex;
  justify-content: center;
  gap: 24px;
  flex-wrap: wrap;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.2s;
}

.feature-item:hover {
  transform: translateY(-4px);
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: box-shadow 0.2s;
}

.feature-item:hover .feature-icon {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.feature-text {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 768px) {
  .suggestion-cards {
    grid-template-columns: 1fr;
  }
  
  .brand-title {
    font-size: 24px;
  }
  
  .quick-features {
    gap: 16px;
  }
  
  .feature-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
  }
}
</style>

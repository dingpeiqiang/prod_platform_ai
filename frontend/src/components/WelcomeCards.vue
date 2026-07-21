<template>
  <div class="welcome-container">
    <div class="welcome-content">
      <div class="brand-section">
        <div class="brand-logo">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/>
            <circle cx="12" cy="12" r="6"/>
            <circle cx="12" cy="12" r="2"/>
          </svg>
        </div>
        <h1 class="brand-title">{{ title }}</h1>
        <p class="brand-subtitle">{{ subtitle }}</p>
      </div>

      <div class="suggestions-section">
        <div class="suggestion-cards">
          <div
            v-for="card in cards"
            :key="card.label"
            class="suggestion-card"
            @click="emit('suggest', card.text)"
          >
            <div class="card-icon" :style="{ background: card.bg, color: card.color }">
              {{ card.icon }}
            </div>
            <div class="card-content">
              <div class="card-title">{{ card.label }}</div>
              <div class="card-desc">{{ card.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  mode: { type: String, default: 'rd' },
})

const emit = defineEmits(['suggest'])

const rdCards = [
  { label: 'AI智查', desc: '查询历史商品，快速复制配置', icon: '\u{1F50D}', text: '查一下近30天大学生套餐配置', bg: '#eff6ff', color: '#2563eb' },
  { label: 'AI方案导入', desc: '上传文档，批量导入配置', icon: '\u{1F4C4}', text: '帮我导入校园迎新方案', bg: '#f0fdf4', color: '#16a34a' },
  { label: '对话式配置', desc: '自然语言描述，智能生成配置', icon: '\u{1F4AC}', text: '给家庭用户做一个融合套餐，月费158，带500M宽带', bg: '#faf5ff', color: '#7c3aed' },
]

const opsCards = [
  { label: '市场洞察', desc: '查询在售商品与增长指标', icon: '\u{1F4CA}', text: '查一下在售5G套餐和风险商品', bg: '#eff6ff', color: '#2563eb' },
  { label: '立项研判', desc: '评估新品上市门槛', icon: '\u{1F6E1}', text: '判断这个新品能不能立项', bg: '#fefce8', color: '#ca8a04' },
  { label: '异动归因', desc: '追溯收入下滑根因', icon: '\u{1F500}', text: '分析家庭融合畅享128本月收入下滑原因', bg: '#faf5ff', color: '#7c3aed' },
]

const cards = props.mode === 'ops' ? opsCards : rdCards
const title = props.mode === 'ops' ? '运营分析助手' : '产商品智能助手'
const subtitle = props.mode === 'ops' ? '洞察、研判、归因、稽核，一站完成' : '基于 AI 的智能产商品配置平台，让配置更简单'
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
.welcome-content { width: 100%; text-align: center; padding: 0 48px; }
.brand-section { margin-bottom: 48px; }
.brand-logo {
  width: 80px; height: 80px; margin: 0 auto 24px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 20px; display: flex; align-items: center; justify-content: center;
  color: white; box-shadow: 0 8px 32px rgba(59, 130, 246, 0.3);
}
.brand-title { font-size: 28px; font-weight: 600; color: #0f172a; margin: 0 0 12px 0; }
.brand-subtitle { font-size: 15px; color: #64748b; margin: 0; line-height: 1.6; }
.suggestions-section { margin-bottom: 32px; }
.suggestion-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; max-width: 800px; margin: 0 auto; }
.suggestion-card {
  display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 24px;
  background: white; border: 1px solid #e5e7eb; border-radius: 12px; cursor: pointer;
  transition: all 0.2s; text-align: center;
}
.suggestion-card:hover { border-color: #93c5fd; box-shadow: 0 4px 16px rgba(59, 130, 246, 0.12); transform: translateY(-2px); }
.card-icon { font-size: 28px; width: 52px; height: 52px; border-radius: 14px; display: flex; align-items: center; justify-content: center; }
.card-title { font-size: 15px; font-weight: 600; color: #0f172a; margin-bottom: 4px; }
.card-desc { font-size: 12px; color: #94a3b8; line-height: 1.4; }
@media (max-width: 768px) {
  .suggestion-cards { grid-template-columns: 1fr; max-width: 400px; }
  .brand-title { font-size: 24px; }
}
</style>

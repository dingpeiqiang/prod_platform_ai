<template>
  <div class="profile-card">
    <!-- 产品基本信息 -->
    <div class="profile-header">
      <div class="profile-header-left">
        <div class="profile-name">{{ data.name }}</div>
        <div class="profile-code">{{ data.code }}</div>
      </div>
      <div class="profile-status">
        <span class="status-badge">{{ data.status }}</span>
      </div>
    </div>

    <!-- 配置参数网格 -->
    <div class="profile-grid">
      <div class="profile-item" v-for="item in data.configItems" :key="item.label">
        <div class="profile-item-label">{{ item.label }}</div>
        <div class="profile-item-value" :class="{ highlight: item.highlight }">{{ item.value }}</div>
      </div>
    </div>

    <!-- 补充信息 -->
    <div class="profile-extra">
      <div class="extra-row" v-for="item in data.extraInfo" :key="item.label">
        <span class="extra-label">{{ item.label }}</span>
        <span class="extra-value">{{ item.value }}</span>
      </div>
    </div>

    <!-- 健康评分 -->
    <div class="health-section">
      <div class="health-header">
        <div class="health-title-row">
          <span class="health-title">当前健康评分</span>
          <span class="health-score" :class="scoreClass">{{ data.health.score }}分</span>
        </div>
        <span class="health-change">较上周{{ data.health.change }}</span>
      </div>

      <!-- 健康评分维度条 -->
      <div class="health-dimensions">
        <div class="dim-item" v-for="dim in data.health.dimensions" :key="dim.name">
          <div class="dim-header">
            <span class="dim-name">{{ dim.name }}</span>
            <span class="dim-score">{{ dim.score }}<span class="dim-change" :class="dim.change.startsWith('↓') ? 'down' : 'up'">{{ dim.change }}</span></span>
          </div>
          <div class="dim-bar-track">
            <div class="dim-bar-fill" :style="{ width: dim.score + '%', background: getDimColor(dim.score) }"></div>
          </div>
          <div class="dim-weight">权重 {{ dim.weight }}</div>
        </div>
      </div>
    </div>

    <!-- 小结 -->
    <div class="profile-summary" v-if="data.summary">
      <span class="summary-icon">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="16" x2="12" y2="12"/>
          <line x1="12" y1="8" x2="12.01" y2="8"/>
        </svg>
      </span>
      <span class="summary-text">{{ data.summary }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Object, required: true },
})

const scoreClass = computed(() => {
  const score = props.data?.health?.score || 0
  if (score >= 80) return 'score-good'
  if (score >= 70) return 'score-warn'
  return 'score-danger'
})

const getDimColor = (score) => {
  if (score >= 80) return 'linear-gradient(90deg, #10b981, #34d399)'
  if (score >= 70) return 'linear-gradient(90deg, #f59e0b, #fbbf24)'
  return 'linear-gradient(90deg, #ef4444, #f87171)'
}
</script>

<style scoped>
.profile-card {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px;
  margin: 12px 0;
}

.profile-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e2e8f0;
}

.profile-name {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.profile-code {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, monospace;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: #dcfce7;
  color: #16a34a;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.profile-item {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.profile-item-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.profile-item-value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.profile-item-value.highlight {
  color: #2563eb;
}

.profile-extra {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.extra-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.extra-label {
  color: #64748b;
  min-width: 90px;
  flex-shrink: 0;
}

.extra-value {
  color: #334155;
  font-weight: 500;
}

.health-section {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 12px;
}

.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.health-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.health-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.health-score {
  font-size: 20px;
  font-weight: 700;
}

.health-score.score-good { color: #16a34a; }
.health-score.score-warn { color: #f59e0b; }
.health-score.score-danger { color: #ef4444; }

.health-change {
  font-size: 12px;
  color: #ef4444;
  font-weight: 500;
}

.health-dimensions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dim-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dim-name {
  font-size: 12px;
  color: #475569;
}

.dim-score {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dim-change {
  font-size: 11px;
  font-weight: 500;
}

.dim-change.down { color: #ef4444; }
.dim-change.up { color: #16a34a; }

.dim-bar-track {
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}

.dim-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.dim-weight {
  font-size: 10px;
  color: #94a3b8;
  text-align: right;
}

.profile-summary {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(59, 130, 246, 0.04);
  border-left: 3px solid #3b82f6;
  border-radius: 0 6px 6px 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
}

.summary-icon {
  color: #3b82f6;
  flex-shrink: 0;
  margin-top: 2px;
}

.summary-text {
  flex: 1;
}

@media (max-width: 600px) {
  .profile-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

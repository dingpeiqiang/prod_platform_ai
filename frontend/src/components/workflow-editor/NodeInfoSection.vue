<template>
  <div class="node-info-section" :class="infoSectionClass">
    <div class="info-content">
      <div class="icon-wrapper">
        <div class="icon-box" :style="iconStyle">
          <svg v-if="iconName === 'StartIcon'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          <svg v-else-if="iconName === 'EndIcon'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/>
            <line x1="4" y1="22" x2="4" y2="15"/>
          </svg>
          <svg v-else-if="iconName === 'LlmIcon'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <path d="M21 12a9 9 0 0 1-9 9m9-9a9 9 0 0 0-9-9m9 9H3m9 9a9 9 0 0 1-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 0 1 9-9"/>
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
        </div>
        <span class="type-label">{{ nodeTypeLabel }}</span>
      </div>
      <p class="description">{{ description }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodeType: {
    type: String,
    required: true
  },
  nodeTypeLabel: {
    type: String,
    required: true
  }
})

// 节点配置映射
const NODE_CONFIGS = {
  start: {
    icon: 'StartIcon',
    badge: 'START',
    badgeClass: 'badge-start',
    description: '工作流的起始节点,用于设定启动工作流需要的信息',
    bgColor: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)'
  },
  end: {
    icon: 'EndIcon',
    badge: 'END',
    badgeClass: 'badge-end',
    description: '工作流的最终节点,用于返回工作流运行后的结果',
    bgColor: 'linear-gradient(135deg, #a855f7 0%, #7c3aed 100%)',
    sectionBg: 'linear-gradient(135deg, #fef3f7 0%, #fce7f3 100%)'
  },
  llm: {
    icon: 'LlmIcon',
    badge: 'LLM',
    badgeClass: 'badge-llm',
    description: '调用大语言模型,使用变量和提示词生成回复。',
    bgColor: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)'
  }
}

const config = computed(() => NODE_CONFIGS[props.nodeType] || {})

const iconName = computed(() => config.value.icon || '')

const iconStyle = computed(() => ({
  background: config.value.bgColor || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
}))


const description = computed(() => config.value.description || '')
const infoSectionClass = computed(() => config.value.sectionBg ? 'custom-bg' : '')
</script>

<style scoped>
.node-info-section {
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
}

.node-info-section.custom-bg {
  background: linear-gradient(135deg, #fef3f7 0%, #fce7f3 100%);
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.type-label {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.description {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  padding-left: 52px;
}
</style>

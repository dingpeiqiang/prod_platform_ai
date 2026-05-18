<template>
  <div class="config-section">
    <div class="section-header" @click="toggleCollapse">
      <svg 
        v-if="collapsible"
        :class="['collapse-icon', { rotated: !isCollapsed }]" 
        width="14" 
        height="14" 
        viewBox="0 0 24 24" 
        fill="none" 
        stroke="currentColor" 
        stroke-width="2"
      >
        <polyline points="6 9 12 15 18 9"/>
      </svg>
      <span class="section-title">{{ title }}</span>
      <span v-if="helpText" class="help-icon" :title="helpText">?</span>
      <slot name="header-actions"></slot>
    </div>
    <div v-show="!isCollapsed" class="section-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  collapsible: {
    type: Boolean,
    default: true
  },
  collapsed: {
    type: Boolean,
    default: false
  },
  helpText: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:collapsed']);

const isCollapsed = ref(props.collapsed);

const toggleCollapse = () => {
  if (props.collapsible) {
    isCollapsed.value = !isCollapsed.value;
    emit('update:collapsed', isCollapsed.value);
  }
};
</script>

<style scoped>
.config-section {
  border-bottom: 1px solid #e8e8e8;
}

.config-section:last-child {
  border-bottom: none;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  cursor: pointer;
  user-select: none;
}

.collapse-icon {
  width: 14px;
  height: 14px;
  color: #666;
  transition: transform 0.2s;
}

.collapse-icon.rotated {
  transform: rotate(180deg);
}

.section-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #e8e8e8;
  color: #999;
  font-size: 11px;
  cursor: help;
  transition: all 0.2s;
}

.help-icon:hover {
  background: #3b82f6;
  color: white;
}

.section-content {
  padding: 0 0 16px;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

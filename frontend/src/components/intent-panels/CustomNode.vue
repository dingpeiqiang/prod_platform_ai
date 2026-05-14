<template>
  <div 
    class="custom-node" 
    :class="[`node-${getNodeCategory(data)}`, { selected }]"
  >
    <div class="node-header">
      <span class="node-icon">{{ data.icon }}</span>
      <span class="node-label">{{ data.label }}</span>
    </div>
    <div class="node-body" v-if="showBody">
      <div class="node-info" v-if="data.model">
        <span class="info-label">模型:</span>
        <span class="info-value">{{ data.model }}</span>
      </div>
      <div class="node-info" v-if="data.toolName">
        <span class="info-label">工具:</span>
        <span class="info-value">{{ data.toolName }}</span>
      </div>
      <div class="node-info" v-if="data.httpMethod">
        <span class="info-label">方法:</span>
        <span class="info-value">{{ data.httpMethod }}</span>
      </div>
      <div class="node-info" v-if="data.loopType">
        <span class="info-label">循环:</span>
        <span class="info-value">{{ data.loopType }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
});

const getNodeCategory = (data) => {
  const categoryMap = {
    '🚀': 'flow',
    '🏁': 'flow',
    '🔀': 'flow',
    '🔄': 'flow',
    '📝': 'llm',
    '🤖': 'llm',
    '🔧': 'tool',
    '🌐': 'tool',
    '💻': 'tool',
    '📦': 'tool',
    '📊': 'parser'
  };
  return categoryMap[data.icon] || 'default';
};

const showBody = computed(() => {
  return props.data.model || props.data.toolName || props.data.httpMethod || props.data.loopType;
});
</script>

<style scoped>
.custom-node {
  background-color: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  min-width: 140px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: 2px solid transparent;
  transition: all 0.2s;
}

.custom-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

/* 节点分类样式 */
.node-flow {
  border-color: #f59e0b;
}

.node-flow.selected {
  border-color: #f59e0b;
}

.node-llm {
  border-color: #8b5cf6;
}

.node-llm.selected {
  border-color: #8b5cf6;
}

.node-tool {
  border-color: #3b82f6;
}

.node-tool.selected {
  border-color: #3b82f6;
}

.node-parser {
  border-color: #06b6d4;
}

.node-parser.selected {
  border-color: #06b6d4;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  font-size: 18px;
}

.node-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.node-body {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.info-label {
  color: #94a3b8;
}

.info-value {
  color: #64748b;
  font-family: monospace;
}
</style>
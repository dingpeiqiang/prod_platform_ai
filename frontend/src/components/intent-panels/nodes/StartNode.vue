<template>
  <div class="node start-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🚀</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <span class="node-desc">工作流入口</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core';

defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
});
</script>

<style scoped>
.start-node {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 8px;
  min-width: 150px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.start-node.selected {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.5);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
}

.node-body {
  padding: 8px 10px;
  text-align: center;
}

.node-desc {
  font-size: 11px;
  opacity: 0.9;
}
</style>

<template>
  <div class="node start-node" :class="{ selected, [executionStatus]: executionStatus }">
    <div class="node-header">
      <span class="node-icon">🚀</span>
      <span class="node-title">{{ data.label }}</span>
      <span v-if="executionStatus" class="status-indicator">
        <span v-if="executionStatus === 'running'" class="status-dot running"></span>
        <span v-else-if="executionStatus === 'completed'" class="status-dot completed"></span>
        <span v-else-if="executionStatus === 'error'" class="status-dot error"></span>
      </span>
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
  },
  executionStatus: {
    type: String,
    default: ''
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
  transition: all 0.3s ease;
}

.start-node.selected {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.5);
}

.start-node.running {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5), 0 2px 12px rgba(245, 158, 11, 0.4);
}

.start-node.completed {
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.5), 0 2px 12px rgba(16, 185, 129, 0.3);
}

.start-node.error {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.5), 0 2px 12px rgba(239, 68, 68, 0.4);
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
  flex: 1;
}

.status-indicator {
  flex-shrink: 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.running {
  background-color: #f59e0b;
  animation: pulse 1s infinite;
}

.status-dot.completed {
  background-color: #10b981;
}

.status-dot.error {
  background-color: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.node-body {
  padding: 8px 10px;
  text-align: center;
}

.node-desc {
  font-size: 11px;
  opacity: 0.9;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  background-color: #3b82f6 !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.5) !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #059669 !important;
}
</style>

<template>
  <div
    class="node end-node"
    :class="{
      selected,
      'is-config-mode': configMode,
      'is-compact': compact && !configMode,
      [executionStatus]: executionStatus
    }"
  >
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🏁</span>
      <span class="node-title">{{ data.label }}</span>
      <span v-if="executionStatus" class="status-indicator">
        <span v-if="executionStatus === 'running'" class="status-dot running"></span>
        <span v-else-if="executionStatus === 'completed'" class="status-dot completed"></span>
        <span v-else-if="executionStatus === 'error'" class="status-dot error"></span>
      </span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">工作流结束</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <span class="node-desc">工作流结束</span>
    </div>
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

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
  },
  ...nodeDisplayProps
});
</script>

<style scoped>
.end-node {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 8px;
  min-width: 150px;
  box-shadow: 0 2px 8px rgba(245, 87, 108, 0.3);
  transition: all 0.3s ease;
}

.end-node.selected {
  box-shadow: 0 0 0 3px rgba(245, 87, 108, 0.5);
}

.end-node.running {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5), 0 2px 12px rgba(245, 158, 11, 0.4);
}

.end-node.completed {
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.5), 0 2px 12px rgba(16, 185, 129, 0.3);
}

.end-node.error {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.5), 0 2px 12px rgba(239, 68, 68, 0.4);
}

.end-node.is-compact {
  min-width: 160px;
}

.node-compact-body {
  padding: 8px 10px;
}

.compact-summary {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.95);
}

.end-node.is-config-mode {
  min-width: unset;
  box-shadow: none;
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
  background-color: #a78bfa !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}
</style>
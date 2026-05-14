<template>
  <div class="node generic-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">{{ icon }}</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <slot></slot>
    </div>
    <Handle type="target" :position="Position.Left" />
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
  icon: {
    type: String,
    default: '📦'
  }
});
</script>

<style scoped>
.generic-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.generic-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.node-body {
  padding: 8px 10px;
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

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #3b82f6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #2563eb !important;
}
</style>

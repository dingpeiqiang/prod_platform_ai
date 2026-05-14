<template>
  <div class="node loop-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔄</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <select v-model="localLoopType" @change="emitUpdate" class="node-select">
        <option value="for">for 循环</option>
        <option value="while">while 循环</option>
      </select>
      <input
        v-model.number="localLoopCount"
        @input="emitUpdate"
        type="number"
        placeholder="循环次数"
        class="node-input"
      />
      <div class="loop-labels">
        <span class="label-body">循环体</span>
        <span class="label-end">循环结束</span>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" id="body" class="handle-body" />
    <Handle type="source" :position="Position.Right" id="end" class="handle-end" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

const emit = defineEmits(['update']);
const localLoopType = ref(props.data.loopType || 'for');
const localLoopCount = ref(props.data.loopCount || 5);

const emitUpdate = () => {
  emit('update', props.data.id, { loopType: localLoopType.value, loopCount: localLoopCount.value });
};

watch(() => props.data, (d) => {
  localLoopType.value = d.loopType || 'for';
  localLoopCount.value = d.loopCount || 5;
}, { deep: true });
</script>

<style scoped>
.loop-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.loop-node.selected {
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

.node-select, .node-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 6px;
}

.node-select:focus, .node-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.loop-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}

.label-body {
  color: #1e40af;
  background-color: #dbeafe;
  padding: 2px 6px;
  border-radius: 3px;
}

.label-end {
  color: #7c3aed;
  background-color: #ede9fe;
  padding: 2px 6px;
  border-radius: 3px;
}

.handle-body {
  top: 35%;
  background-color: #3b82f6;
}

.handle-end {
  top: 65%;
  background-color: #8b5cf6;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
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
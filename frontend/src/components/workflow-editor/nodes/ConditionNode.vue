<template>
  <div class="node condition-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔀</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <input
        v-model="localCondition"
        @input="emitUpdate"
        type="text"
        placeholder="条件表达式"
        class="node-input"
      />
      <div class="condition-labels">
        <span class="label-true">✓ 满足</span>
        <span class="label-false">✗ 不满足</span>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" id="true" class="handle-true" />
    <Handle type="source" :position="Position.Right" id="false" class="handle-false" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

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

const emit = defineEmits(['update']);

const localCondition = ref(props.data.condition || '');

const emitUpdate = () => {
  emit('update', props.data.id, {
    condition: localCondition.value
  });
};

watch(() => props.data, (newData) => {
  localCondition.value = newData.condition || '';
}, { deep: true });
</script>

<style scoped>
.condition-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.condition-node.selected {
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

.node-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 6px;
}

.node-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.condition-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}

.label-true {
  color: #166534;
  background-color: #dcfce7;
  padding: 2px 6px;
  border-radius: 3px;
}

.label-false {
  color: #991b1b;
  background-color: #fee2e2;
  padding: 2px 6px;
  border-radius: 3px;
}

.handle-true {
  top: 35%;
  background-color: #22c55e;
}

.handle-false {
  top: 65%;
  background-color: #ef4444;
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
<template>
  <div class="node llm-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <select v-model="localModel" @change="emitUpdate" class="node-select">
        <option value="qwen-vl-plus">Qwen-VL-Plus</option>
        <option value="gpt-4o">GPT-4o</option>
        <option value="claude-3-opus">Claude 3 Opus</option>
      </select>
      <div class="node-row">
        <label>温度</label>
        <input
          v-model.number="localTemperature"
          type="range"
          min="0"
          max="2"
          step="0.1"
          @input="emitUpdate"
          class="node-range"
        />
        <span>{{ localTemperature }}</span>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
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

const localModel = ref(props.data.model || 'qwen-vl-plus');
const localTemperature = ref(props.data.temperature || 0.7);

const emitUpdate = () => {
  emit('update', props.data.id, {
    model: localModel.value,
    temperature: localTemperature.value
  });
};

watch(() => props.data, (newData) => {
  localModel.value = newData.model || 'qwen-vl-plus';
  localTemperature.value = newData.temperature || 0.7;
}, { deep: true });
</script>

<style scoped>
.llm-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.llm-node.selected {
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
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.node-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #64748b;
}

.node-range {
  flex: 1;
}
</style>

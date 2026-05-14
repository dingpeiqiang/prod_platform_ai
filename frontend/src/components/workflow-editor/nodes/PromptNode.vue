<template>
  <div class="node prompt-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📝</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <textarea
        v-model="localPrompt"
        @input="emitUpdate"
        placeholder="输入提示词..."
        class="node-textarea"
      ></textarea>
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

const localPrompt = ref(props.data.prompt || '');

const emitUpdate = () => {
  emit('update', props.data.id, {
    prompt: localPrompt.value
  });
};

watch(() => props.data, (newData) => {
  localPrompt.value = newData.prompt || '';
}, { deep: true });
</script>

<style scoped>
.prompt-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 200px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.prompt-node.selected {
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

.node-textarea {
  width: 100%;
  min-height: 60px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: inherit;
}

.node-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}
</style>

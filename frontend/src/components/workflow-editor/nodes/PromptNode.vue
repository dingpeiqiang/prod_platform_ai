<template>
  <div class="node prompt-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📝</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleVariables" class="var-toggle" :class="{ active: showVarPanel }">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="8" y1="3" x2="8" y2="7"/>
          <line x1="16" y1="3" x2="16" y2="7"/>
          <line x1="3" y1="16" x2="21" y2="16"/>
        </svg>
      </button>
    </div>
    <div class="node-body">
      <textarea
        v-model="localPrompt"
        @input="emitUpdate"
        placeholder="输入提示词，使用 {{变量名}} 引用变量..."
        class="node-textarea"
      ></textarea>
      
      <div v-if="showVarPanel" class="var-panel">
        <div class="var-panel-header">
          <span class="var-panel-title">可用变量</span>
          <span class="var-hint">点击插入到光标位置</span>
        </div>
        <div class="var-list">
          <div 
            v-for="varItem in availableVariables" 
            :key="varItem.name"
            @click="insertVariable(varItem)"
            class="var-item"
          >
            <span class="var-item-icon">{{ getVarIcon(varItem.category) }}</span>
            <div class="var-item-info">
              <span class="var-item-name">{{ varItem.name }}</span>
              <span class="var-item-type">{{ varItem.type }}</span>
            </div>
            <span class="var-item-insert">+</span>
          </div>
          <div v-if="availableVariables.length === 0" class="var-empty">
            <span>暂无可用变量</span>
          </div>
        </div>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
    <Handle type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  availableVariables: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update']);

const localPrompt = ref(props.data.prompt || '');
const showVarPanel = ref(false);

const emitUpdate = () => {
  emit('update', props.data.id, {
    prompt: localPrompt.value
  });
};

const toggleVariables = () => {
  showVarPanel.value = !showVarPanel.value;
};

const getVarIcon = (category) => {
  switch(category) {
    case 'input': return '📥';
    case 'workflow': return '📦';
    case 'output': return '📤';
    default: return '🔹';
  }
};

const insertVariable = (varItem) => {
  const varRef = `{{${varItem.name}}}`;
  localPrompt.value += varRef;
  emitUpdate();
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

.var-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  margin-left: auto;
}

.var-toggle:hover {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.var-toggle.active {
  background: #3b82f6;
  color: white;
}

.var-panel {
  margin-top: 8px;
  padding: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.var-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.var-panel-title {
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
}

.var-hint {
  font-size: 9px;
  color: #94a3b8;
}

.var-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-height: 150px;
  overflow-y: auto;
}

.var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.var-item:hover {
  background: #dbeafe;
}

.var-item-icon {
  font-size: 12px;
}

.var-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.var-item-name {
  font-size: 11px;
  color: #334155;
}

.var-item-type {
  font-size: 9px;
  color: #94a3b8;
}

.var-item-insert {
  font-size: 12px;
  color: #3b82f6;
  font-weight: bold;
  opacity: 0;
  transition: opacity 0.15s;
}

.var-item:hover .var-item-insert {
  opacity: 1;
}

.var-empty {
  text-align: center;
  padding: 8px;
  font-size: 11px;
  color: #94a3b8;
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

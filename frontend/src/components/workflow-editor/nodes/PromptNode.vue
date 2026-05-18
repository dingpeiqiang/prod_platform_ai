<template>
  <div class="node prompt-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📝</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ compactPromptPreview }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <textarea
        v-model="localPrompt"
        @input="emitUpdate"
        placeholder="输入提示词，使用 {{变量名}} 引用变量..."
        class="node-textarea"
      ></textarea>
      
      <div v-if="configMode || showAdvanced" class="advanced-panel">
        <div class="section-title">输入参数</div>
        <div class="input-param-row">
          <select 
            v-model="localInputVar" 
            @change="emitUpdate" 
            class="param-select"
          >
            <option value="">选择输入变量</option>
            <option v-for="varItem in availableVariables" :key="varItem.name" :value="varItem.name">
              {{ varItem.name }} ({{ varItem.type }})
            </option>
          </select>
          <span class="param-hint">或使用 {{变量名}} 语法</span>
        </div>
        
        <div class="section-title">输出参数</div>
        <div class="output-param-row">
          <input 
            v-model="localOutputVar" 
            @input="emitUpdate" 
            placeholder="输出变量名" 
            class="param-input"
          />
          <span class="param-hint">自定义变量名</span>
        </div>
      </div>
    </div>
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

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
  },
  ...nodeDisplayProps
});

const compactPromptPreview = computed(() => {
  const text = localPrompt.value || '未设置提示词';
  return text.length > 28 ? `${text.slice(0, 28)}…` : text;
});

const emit = defineEmits(['update']);

const localPrompt = ref(props.data.prompt || '');
const showAdvanced = ref(false);
const localInputVar = ref(props.data.inputVar || '');
const localOutputVar = ref(props.data.outputVar || '');

const emitUpdate = () => {
  // 构建输入输出映射
  const inputs = {};
  const outputs = {};
  
  if (localInputVar.value) {
    inputs['input'] = `{{${localInputVar.value}}}`;
  }
  
  if (localOutputVar.value) {
    outputs[localOutputVar.value] = '{{__output__}}';
  }
  
  emit('update', props.data.id, {
    prompt: localPrompt.value,
    inputVar: localInputVar.value,
    outputVar: localOutputVar.value,
    inputs: Object.keys(inputs).length > 0 ? inputs : undefined,
    outputs: Object.keys(outputs).length > 0 ? outputs : undefined
  });
};

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

watch(() => props.data, (newData) => {
  localPrompt.value = newData.prompt || '';
  localInputVar.value = newData.inputVar || '';
  localOutputVar.value = newData.outputVar || '';
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

.prompt-node.is-compact {
  min-width: 160px;
}

.node-compact-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compact-summary {
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-hint {
  font-size: 10px;
  color: #94a3b8;
}

.prompt-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
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

.advanced-toggle {
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
  font-size: 12px;
}

.advanced-toggle:hover {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.advanced-toggle.active {
  background: #3b82f6;
  color: white;
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

.advanced-panel {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.input-param-row,
.output-param-row {
  margin-bottom: 8px;
}

.param-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  background: white;
}

.param-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-input {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.param-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-hint {
  font-size: 9px;
  color: #94a3b8;
  margin-top: 2px;
  display: block;
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
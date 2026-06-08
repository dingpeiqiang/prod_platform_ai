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
        :placeholder="placeholderText"
        class="node-textarea"
      ></textarea>
      
      <!-- 输入参数配置区 - 标准模式 -->
      <div v-if="configMode || showAdvanced" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置用于构建提示词的输入参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click.stop="addInputParam" class="add-param-btn" title="添加输入参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.inputs" class="section-content">
          <div class="input-param-container">
            <!-- 输入参数表头 -->
            <div class="input-param-header">
              <span class="header-col header-name">参数名</span>
              <span class="header-col header-type">类型</span>
              <span class="header-col header-value">值</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localInputs" :key="index">
              <div v-if="param" class="input-param-item">
                <div class="param-name-cell">
                  <input v-model="param.name" @input="emitUpdate" placeholder="参数名" class="param-name-input" :class="{ error: !param.name }"/>
                </div>
                <div class="param-type-cell">
                  <select v-model="param.valueType" @change="handleValueTypeChange(index)" class="param-type-select">
                    <option value="input">自定义</option>
                    <option value="reference">引用</option>
                  </select>
                </div>
                <div class="param-value-cell">
                  <input 
                    v-if="param.valueType === 'input'" 
                    v-model="param.defaultValue" 
                    @input="emitUpdate" 
                    placeholder="默认值" 
                    class="param-default-input"
                  />
                  <VariableCascader
                    v-if="param.valueType === 'reference'"
                    v-model="param.refValue"
                    :available-variables="availableVariables"
                    placeholder="请选择变量"
                    class="param-cascader"
                    @change="(val) => handleCascaderChange(index, val)"
                  />
                </div>
                <div class="param-action-cell">
                  <button @click="removeInputParam(index)" class="action-btn delete-btn" title="删除">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
              </div>
          </template>
          </div>
          <div v-if="localInputs.some(p => p && !p.name)" class="error-message">参数名不能为空</div>
          <div v-if="localInputs.some(p => p && p.valueType === 'reference' && !p.refValue)" class="error-message">引用变量不能为空</div>
        </div>
      </div>
      
      <!-- 输出参数配置区 -->
      <div v-if="configMode || showAdvanced" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输出变量名">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.outputs" class="section-content">
          <div class="output-param-row">
            <input 
              v-model="localOutputVar" 
              @input="emitUpdate" 
              placeholder="输出变量名" 
              class="param-input"
            />
          </div>
        </div>
      </div>
    </div>
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';
import VariableCascader from '../VariableCascader.vue';

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

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const compactPromptPreview = computed(() => {
  const text = localPrompt.value || '未设置提示词';
  return text.length > 28 ? `${text.slice(0, 28)}…` : text;
});

const emit = defineEmits(['update']);

const localPrompt = ref(props.data.prompt || '');
const showAdvanced = ref(false);
const localOutputVar = ref(props.data.outputVar || '');
const expandedSections = ref({ inputs: true, outputs: true });

// 输入参数 - 标准格式
const localInputs = ref((props.data.inputParams || []).map(p => ({
  name: p.name || '',
  valueType: p.valueType || 'input',
  defaultValue: p.defaultValue || p.value || '',
  refValue: p.refValue || ''
})));

if (localInputs.value.length === 0 && props.data.inputVar) {
  localInputs.value.push({
    name: 'input',
    valueType: 'reference',
    defaultValue: '',
    refValue: props.data.inputVar
  });
}

const placeholderText = '输入提示词，使用 {{变量名}} 引用变量...';

const emitUpdate = () => {
  const inputParams = localInputs.value
    .filter(p => p && p.name)
    .map(p => ({
      name: p.name,
      valueType: p.valueType,
      defaultValue: p.valueType === 'input' ? p.defaultValue : undefined,
      refValue: p.valueType === 'reference' ? p.refValue : undefined
    }));

  const outputParams = [];
  if (localOutputVar.value) {
    outputParams.push({ name: localOutputVar.value, source: '{{__output__}}' });
  }

  emit('update', props.data.id, {
    prompt: localPrompt.value,
    inputVar: undefined,
    outputVar: localOutputVar.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
};

const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

const addInputParam = () => {
  localInputs.value.push({ name: '', valueType: 'input', defaultValue: '', refValue: '' });
};

const removeInputParam = (index) => {
  localInputs.value.splice(index, 1);
  emitUpdate();
};

const handleValueTypeChange = (index) => {
  const param = localInputs.value[index];
  if (param.valueType === 'reference') {
    param.defaultValue = '';
  } else {
    param.refValue = '';
  }
  emitUpdate();
};

const handleCascaderChange = (index, value) => {
  localInputs.value[index].refValue = value;
  emitUpdate();
};

watch(() => props.data, (newData) => {
  localPrompt.value = newData.prompt || '';
  localOutputVar.value = newData.outputVar || '';
  
  if (newData.inputParams) {
    localInputs.value = newData.inputParams.map(p => ({
      name: p.name || '',
      valueType: p.valueType || 'input',
      defaultValue: p.defaultValue || p.value || '',
      refValue: p.refValue || ''
    }));
  } else if (newData.inputVar && localInputs.value.length === 0) {
    localInputs.value.push({
      name: 'input',
      valueType: 'reference',
      defaultValue: '',
      refValue: newData.inputVar
    });
  }
}, { deep: true });
</script>

<style scoped>
.prompt-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.prompt-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.prompt-node.is-compact {
  min-width: 180px;
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

/* 配置区域样式 */
.config-section {
  margin-top: 8px;
}

.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: white;
  border-bottom: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.section-header:hover {
  background: #f8fafc;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  cursor: pointer;
  padding: 0;
}

.section-toggle-btn svg {
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.help-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: #94a3b8;
  cursor: help;
  border-radius: 4px;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #f1f5f9;
  color: #64748b;
}

.add-param-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #3b82f6;
  border: none;
  color: white;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #2563eb;
}

.section-content {
  padding: 12px;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 输入参数容器支持横向滚动 */
.input-param-container {
  overflow-x: auto;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

/* 输入参数表头 */
.input-param-header {
  display: flex;
  align-items: center;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 12px;
  color: #64748b;
  padding: 8px;
  min-width: max-content;
}

.header-col {
  display: flex;
  align-items: center;
}

.header-col.header-name {
  width: 120px;
}

.header-col.header-type {
  width: 90px;
}

.header-col.header-value {
  flex: 1;
  min-width: 200px;
}

.header-col.header-action {
  width: 40px;
  display: flex;
  justify-content: center;
}

/* 输入参数项样式 */
.input-param-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border-top: 1px solid #e5e7eb;
  min-width: max-content;
}

.input-param-item:first-child {
  border-top: none;
}

.param-name-cell {
  width: 120px;
}

.param-name-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-name-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-name-input.error {
  border-color: #ef4444;
}

.param-type-cell {
  width: 90px;
}

.param-type-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 6px center;
  background-repeat: no-repeat;
  background-size: 12px;
  box-sizing: border-box;
}

.param-type-select:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-value-cell {
  flex: 1;
  min-width: 200px;
  margin-left: 8px;
}

.param-default-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-default-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-cascader {
  width: 100%;
}

.param-action-cell {
  width: 40px;
  display: flex;
  justify-content: center;
  margin-left: 8px;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.delete-btn {
  color: #94a3b8;
}

.delete-btn:hover {
  background: #fee2e2;
  color: #ef4444;
}

.error-message {
  margin-top: 8px;
  padding: 6px 8px;
  background: #fef2f2;
  border: 1px solid #fee2e2;
  border-radius: 4px;
  font-size: 11px;
  color: #dc2626;
}

/* 输出参数样式 */
.output-param-row {
  margin-bottom: 0;
}

.param-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.param-input:focus {
  outline: none;
  border-color: #3b82f6;
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
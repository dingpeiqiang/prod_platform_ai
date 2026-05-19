<template>
  <div class="node user-input-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">👤</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ localInputType === 'text' ? '文本输入' : localInputType === 'select' ? '选择' : '确认' }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    
    <div v-if="configMode" class="user-input-config">
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('basic')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.basic }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>基本配置</span>
          </button>
        </div>
        
        <div v-if="expandedSections.basic" class="section-content">
          <div class="param-row">
            <label class="param-label">输入类型</label>
            <select v-model="localInputType" @change="emitUpdate" class="param-select">
              <option value="text">文本输入</option>
              <option value="select">下拉选择</option>
              <option value="confirm">确认框</option>
            </select>
          </div>
          
          <div class="param-row">
            <label class="param-label">输出变量名</label>
            <input v-model="localOutputVar" @input="emitUpdate" placeholder="user_input" class="param-input" />
          </div>
          
          <div class="param-row">
            <label class="param-label">
              <input v-model="localRequired" @change="emitUpdate" type="checkbox" class="checkbox-input"/>
              必填项
            </label>
          </div>
        </div>
      </div>

      <div class="config-section">
        <label class="section-label">提示文本</label>
        <textarea v-model="localPrompt" @input="emitUpdate" placeholder="请输入提示用户的文本..." class="multiline-input" rows="4"></textarea>
      </div>

      <div v-if="localInputType === 'select'" class="config-section">
        <label class="section-label">选项列表（每行一个选项）</label>
        <textarea v-model="localOptions" @input="emitUpdate" placeholder="选项1&#10;选项2&#10;选项3" class="multiline-input" rows="4"></textarea>
      </div>

      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  availableVariables: { type: Array, default: () => [] },
  ...nodeDisplayProps
});

const emit = defineEmits(['update', 'close']);

const localLabel = ref(props.data.label || '用户输入');
const localPrompt = ref(props.data.prompt || '请输入：');
const localInputType = ref(props.data.inputType || 'text');
const localOptions = ref(props.data.options || '');
const localRequired = ref(props.data.required ?? true);
const localOutputVar = ref(props.data.outputVar || 'user_input');

const expandedSections = ref({
  basic: true
});

const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    label: localLabel.value,
    prompt: localPrompt.value,
    inputType: localInputType.value,
    options: localOptions.value,
    required: localRequired.value,
    outputVar: localOutputVar.value
  });
};

watch(() => props.data, (newData) => {
  localLabel.value = newData.label || '用户输入';
  localPrompt.value = newData.prompt || '请输入：';
  localInputType.value = newData.inputType || 'text';
  localOptions.value = newData.options || '';
  localRequired.value = newData.required ?? true;
  localOutputVar.value = newData.outputVar || 'user_input';
}, { deep: true });
</script>

<style scoped>
.user-input-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 220px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.user-input-node.selected {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
}

.user-input-node.is-compact {
  min-width: 160px;
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

.user-input-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.user-input-config {
  padding: 0;
  background: #fff;
}

.config-section {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.collapsible-section {
  padding: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.section-toggle-btn:hover {
  background: #f0f0f0;
}

.section-toggle-btn svg {
  width: 16px;
  height: 16px;
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.section-content {
  padding: 16px;
  animation: slideDown 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.param-row:last-child {
  margin-bottom: 0;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.param-select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.param-select:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
}

.param-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-input:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
}

.multiline-input {
  width: 100%;
  padding: 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  transition: all 0.2s;
  box-sizing: border-box;
}

.multiline-input:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
}

.collapse-btn {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
}

.collapse-btn button {
  padding: 8px 48px;
  background: #f97316;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn button:hover {
  background: #ea580c;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.3);
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #fb923c !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #f97316 !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #f97316 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #ea580c !important;
}
</style>
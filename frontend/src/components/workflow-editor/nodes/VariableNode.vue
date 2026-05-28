<template>
  <div class="node variable-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📦</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ variableSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <input
        v-model="data.variable_name"
        class="var-name-input"
        placeholder="变量名"
      />
      <input type="hidden" v-model="data.varType" value="json" />

      <textarea
        v-model="data.variable_value"
        class="var-value-textarea"
        placeholder="输入变量值，支持: 直接文本、{{变量名}}、{{函数()}}、JSON路径"
      ></textarea>

      <div v-if="configMode || showAdvanced" class="advanced-panel">
        <div class="section-title">快捷插入</div>
        <div class="function-list">
          <button
            v-for="func in availableFunctions"
            :key="func.name"
            @click="insertFunction(func)"
            class="function-btn"
            :title="func.description"
          >
            {{ func.name }}
          </button>
        </div>
        
        <div class="section-title">使用说明</div>
        <div class="usage-guide">
          <div class="guide-item">
            <code v-text="'{{变量名}}'"></code>
            <span>引用其他变量</span>
          </div>
          <div class="guide-item">
            <code v-text="'{{now()}}'"></code>
            <span>调用内置函数</span>
          </div>
          <div class="guide-item">
            <code>1+2*3</code>
            <span>数学运算</span>
          </div>
          <div class="guide-item">
            <code>age>18</code>
            <span>比较运算</span>
          </div>
          <div class="guide-item">
            <code>$.data.items[0]</code>
            <span>JSON路径提取</span>
          </div>
          <div class="guide-item">
            <code>{"key": "value"}</code>
            <span>直接输入JSON</span>
          </div>
        </div>
      </div>
    </div>
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  ...nodeDisplayProps
});

const emit = defineEmits(['update']);

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const showAdvanced = ref(false);

const availableFunctions = [
  { name: '{{now()}}', description: '获取当前时间' },
  { name: '{{uuid()}}', description: '生成UUID' },
  { name: '{{len()}}', description: '获取长度' },
  { name: '{{trim()}}', description: '去除空白' },
  { name: '{{upper()}}', description: '转大写' },
  { name: '{{lower()}}', description: '转小写' },
  { name: '{{json()}}', description: 'JSON序列化' },
  { name: '{{parseJson()}}', description: 'JSON解析' },
  { name: '{{env()}}', description: '读取环境变量' },
  { name: '{{random()}}', description: '生成随机数' }
];

const variableSummary = computed(() => props.data.variable_name || '未命名变量');

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const insertFunction = (func) => {
  const currentValue = props.data.variable_value || '';
  const newValue = currentValue + func.name;
  emit('update', props.data.id, { variable_value: newValue });
};
</script>

<style scoped>
.variable-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.variable-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.variable-node.is-compact {
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

.variable-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: white;
  flex: 1;
}

.advanced-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.advanced-toggle:hover,
.advanced-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.node-body {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.var-name-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.var-name-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.var-value-textarea {
  width: 100%;
  min-height: 60px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: monospace;
}

.var-value-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

.advanced-panel {
  margin-top: 4px;
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

.function-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.function-btn {
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.function-btn:hover {
  background: #dbeafe;
  border-color: #3b82f6;
  color: #3b82f6;
}

.usage-guide {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  background: #f8fafc;
  border-radius: 4px;
}

.guide-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
}

.guide-item code {
  font-family: monospace;
  font-size: 10px;
  padding: 2px 4px;
  background: #e2e8f0;
  border-radius: 2px;
  color: #64748b;
  min-width: 100px;
}

.guide-item span {
  color: #64748b;
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
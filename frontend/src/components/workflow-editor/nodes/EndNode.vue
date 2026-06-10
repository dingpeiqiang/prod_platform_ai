<template>
  <div
    class="node end-node"
    :class="{
      selected,
      'is-config-mode': configMode,
      'is-compact': compact && !configMode,
      [executionStatus]: executionStatus
    }"
  >
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🏁</span>
      <span class="node-title">{{ data.label }}</span>
      <span v-if="executionStatus" class="status-indicator">
        <span v-if="executionStatus === 'running'" class="status-dot running"></span>
        <span v-else-if="executionStatus === 'completed'" class="status-dot completed"></span>
        <span v-else-if="executionStatus === 'error'" class="status-dot error"></span>
      </span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">工作流结束</span>
      <div class="compact-direction-indicator">
        <span class="direction-arrow">⬅️</span>
        <span class="direction-text">仅输入</span>
      </div>
    </div>
    
    <div v-if="configMode" class="end-node-config">
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: expandedSections.outputs }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置工作流输出的参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click.stop="addOutputParam" class="add-param-btn" title="添加输出参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="expandedSections.outputs" class="section-content">
          <!-- 输出参数容器 -->
          <div class="input-param-container">
            <!-- 输出参数表头 -->
            <div class="input-param-header">
              <span class="header-col header-name">参数名</span>
              <span class="header-col header-type">类型</span>
              <span class="header-col header-value">值</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localOutputParams" :key="index">
              <div v-if="param" class="input-param-item">
                <div class="param-name-cell">
                  <input v-model="param.name" @input="emitUpdate" placeholder="参数名" class="param-name-input" :class="{ error: !param.name }"/>
                </div>
                <div class="param-type-cell">
                  <select v-model="param.nameType" @change="handleOutputNameTypeChange(index)" class="param-type-select">
                    <option value="input">自定义</option>
                    <option value="reference">引用</option>
                  </select>
                </div>
                <div class="param-value-cell">
                  <select 
                    v-if="param.nameType === 'input'" 
                    v-model="param.type" 
                    @change="emitUpdate" 
                    class="param-default-input"
                  >
                    <option value="string">string</option>
                    <option value="json">json</option>
                  </select>
                  <VariableCascader
                    v-else
                    v-model="param.nameRef"
                    :available-variables="availableVariables"
                    placeholder="请选择变量"
                    class="param-cascader"
                    @change="emitUpdate"
                  />
                </div>
                <div class="param-action-cell">
                  <button @click="removeOutputParam(index)" class="action-btn delete-btn" title="删除">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="collapse-section">
        <button @click="$emit('close')" class="collapse-all-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="18 15 12 9 6 15"/>
          </svg>
          <span>收起</span>
        </button>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
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
  executionStatus: {
    type: String,
    default: ''
  },
  availableVariables: {
    type: Array,
    default: () => []
  },
  ...nodeDisplayProps
});

const { targetPosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update', 'close']);

// 本地状态
const safeData = props.data || {};
const localLabel = ref(safeData.label || '结束节点');
const outputParamsData = safeData.outputParams || [];
// 如果没有配置输出参数，添加一条默认配置
const localOutputParams = ref(outputParamsData.length > 0 
  ? outputParamsData.map(p => ({
      name: p.name || '',
      nameType: p.nameType || 'input',
      nameRef: p.nameRef || '',
      type: p.type || 'string'
    }))
  : [{
      name: 'output',
      nameType: 'input',
      nameRef: '',
      type: 'string'
    }]);

// 折叠状态
const expandedSections = ref({
  outputs: true
});

// 强制 VariableCascader 刷新 key，当 availableVariables 变化时重新挂载
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

// 切换区域显示状态
const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

// 更新数据
const emitUpdate = () => {
  if (!props.data || !props.data.id) return;
  
  const outputParams = localOutputParams.value
    .filter(p => p)
    .map(p => ({
      name: p.name || '',
      nameType: p.nameType || 'input',
      nameRef: p.nameRef || '',
      type: p.type || 'string'
    }));
  emit('update', props.data.id, {
    label: localLabel.value,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
};

// 添加输出参数
const addOutputParam = () => {
  localOutputParams.value.push({
    name: '',
    nameType: 'input',
    nameRef: '',
    type: 'string'
  });
  emitUpdate();
};

const handleOutputNameTypeChange = (index) => {
  const param = localOutputParams.value[index];
  if (param.nameType === 'reference') {
    param.name = '';
    param.type = 'string';
  } else {
    param.nameRef = '';
  }
  emitUpdate();
};

// 删除输出参数
const removeOutputParam = (index) => {
  localOutputParams.value.splice(index, 1);
  emitUpdate();
};

// 监听数据变化
watch(() => props.data, (newData) => {
  if (!newData) return;
  localLabel.value = newData.label || '结束节点';
  const paramsData = newData.outputParams || [];
  // 如果没有配置输出参数，添加一条默认配置
  localOutputParams.value = paramsData.length > 0 
    ? paramsData.map(p => ({
        name: p.name || '',
        nameType: p.nameType || 'input',
        nameRef: p.nameRef || '',
        type: p.type || 'string'
      }))
    : [{
        name: 'output',
        nameType: 'input',
        nameRef: '',
        type: 'string'
      }];
}, { deep: true });
</script>

<style scoped>
.end-node {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 0 2px 8px rgba(245, 87, 108, 0.3);
  transition: all 0.3s ease;
}

.end-node.selected {
  box-shadow: 0 0 0 3px rgba(245, 87, 108, 0.5);
}

.end-node.running {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5), 0 2px 12px rgba(245, 158, 11, 0.4);
}

.end-node.completed {
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.5), 0 2px 12px rgba(16, 185, 129, 0.3);
}

.end-node.error {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.5), 0 2px 12px rgba(239, 68, 68, 0.4);
}

.end-node.is-compact {
  min-width: 180px;
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
  color: rgba(255, 255, 255, 0.95);
}

.compact-direction-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.85);
}

.direction-arrow {
  font-size: 12px;
}

.direction-text {
  white-space: nowrap;
}

.end-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.end-node-config {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #999;
  cursor: help;
  border-radius: 50%;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #f0f0f0;
  color: #666;
}

.add-param-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: #3b82f6;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
}

.section-content {
  padding: 16px;
  animation: slideDown 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 参数网格布局（与 LlmNode 一致） */
.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-row.full-width {
  grid-column: 1 / -1;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #e8e8e8;
  color: #999;
  font-size: 11px;
  cursor: help;
  transition: all 0.2s;
}

.help-icon:hover {
  background: #3b82f6;
  color: white;
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
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}



.param-name-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-name-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-name-input.error {
  border-color: #ff4d4f;
}

.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #3b82f6;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
  margin: 0 auto;
}

.action-btn:hover:not(:disabled) {
  background: #f0f9ff;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
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

.input-param-header .header-col.header-type {
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
  background: #f1f5f9;
  color: #ef4444;
}

.collapse-section {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
}

.collapse-all-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 48px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-all-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  background-color: #a78bfa !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(167, 139, 250, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(167, 139, 250, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}
</style>
<template>
  <div
    class="node start-node"
    :class="{
      selected,
      'is-config-mode': configMode,
      'is-compact': compact && !configMode,
      [executionStatus]: executionStatus
    }"
  >
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🚀</span>
      <span class="node-title">{{ data.label }}</span>
      <span v-if="executionStatus" class="status-indicator">
        <span v-if="executionStatus === 'running'" class="status-dot running"></span>
        <span v-else-if="executionStatus === 'completed'" class="status-dot completed"></span>
        <span v-else-if="executionStatus === 'error'" class="status-dot error"></span>
      </span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ localParams.length }} 个入参</span>
    </div>
    
    <div v-if="configMode" class="start-node-config">
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleInputSection" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: inputSectionExpanded }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="用于设定启动工作流需要的信息">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click="addParam" class="add-param-btn" title="添加参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="inputSectionExpanded" class="section-content">
          <div class="params-table-wrapper">
            <table class="params-table">
              <thead>
                <tr>
                  <th class="col-name">
                    <span class="required-marker">*</span>参数名
                  </th>
                  <th class="col-type">类型</th>
                  <th class="col-desc">参数描述</th>
                  <th class="col-required">是否必传</th>
                  <th class="col-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <transition-group name="param-list">
                  <tr v-for="(param, index) in localParams" :key="index" class="param-row">
                    <td class="col-name">
                      <input
                        v-model="param.name"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入"
                        class="param-name-input"
                      />
                    </td>
                    <td class="col-type">
                      <select
                        v-model="param.type"
                        @change="emitUpdate"
                        class="param-type-select"
                        :title="getTypeDescription(param.type)"
                      >
                        <option value="string">string</option>
                        <option value="int">int</option>
                        <option value="float">float</option>
                        <option value="date">date</option>
                        <option value="datetime">datetime</option>
                        <option value="tel">tel</option>
                        <option value="boolean">boolean</option>
                      </select>
                    </td>
                    <td class="col-desc">
                      <input
                        v-model="param.description"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入参数说明"
                        class="param-desc-input"
                      />
                    </td>
                    <td class="col-required">
                      <div 
                        class="required-toggle" 
                        :class="{ active: param.required }"
                        @click="toggleRequired(index)"
                      >
                      </div>
                    </td>
                    <td class="col-action">
                      <button 
                        @click="removeParam(index)" 
                        class="action-btn delete-btn" 
                        title="删除"
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <line x1="18" y1="6" x2="6" y2="18"/>
                          <line x1="6" y1="6" x2="18" y2="18"/>
                        </svg>
                      </button>
                    </td>
                  </tr>
                </transition-group>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      
      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>
    
    <div v-else-if="!compact" class="node-body">
      <span class="node-desc">工作流入口</span>
    </div>
    
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
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
  executionStatus: {
    type: String,
    default: ''
  },
  ...nodeDisplayProps
});

const emit = defineEmits(['update', 'close']);

const localParams = ref([]);
const inputSectionExpanded = ref(true);

const toggleInputSection = () => {
  inputSectionExpanded.value = !inputSectionExpanded.value;
};

const addParam = () => {
  localParams.value.push({
    name: '',
    type: 'string',
    description: '',
    default: '',
    required: false
  });
  emitUpdate();
};

const removeParam = (index) => {
  localParams.value.splice(index, 1);
  emitUpdate();
};

const toggleRequired = (index) => {
  localParams.value[index].required = !localParams.value[index].required;
  emitUpdate();
};

const moveParam = (index, direction) => {
  const newIndex = index + direction;
  if (newIndex >= 0 && newIndex < localParams.value.length) {
    const temp = localParams.value[index];
    localParams.value[index] = localParams.value[newIndex];
    localParams.value[newIndex] = temp;
    emitUpdate();
  }
};

const getDefaultPlaceholder = (type) => {
  const placeholders = {
    string: '请输入字符串',
    tel: '请输入手机号'
  };
  return placeholders[type] || '';
};

const getTypeDescription = (type) => {
  const descriptions = {
    string: '字符串（文本）- 常见使用场景：文本输入、关键词、ID等',
    int: '整数 - 常见使用场景：数量、序号、状态码等',
    float: '浮点数（小数）- 常见使用场景：金额、评分、百分比等',
    date: '日期（仅年月日）- 常见使用场景：生日、下单日期、截止日期等',
    datetime: '日期+时间 - 常见使用场景：预约时间、创建时间、日志时间等',
    tel: '手机号（平台内置的特殊字符串校验）- 常见使用场景：联系电话',
    boolean: '布尔值（true/false）- 常见使用场景：是否启用、是否同意协议等'
  };
  return descriptions[type] || '';
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    parameters: localParams.value
  });
};

watch(() => props.data, (newData) => {
  if (newData.parameters && newData.parameters.length > 0) {
    localParams.value = JSON.parse(JSON.stringify(newData.parameters));
  } else {
    // 默认初始化一个参数
    localParams.value = [
      {
        name: '',
        type: 'string',
        description: '',
        default: '',
        required: false
      }
    ];
    // 触发更新，保存默认参数
    emitUpdate();
  }
}, { immediate: true, deep: true });
</script>

<style scoped>
.start-node {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 8px;
  min-width: 220px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.start-node.selected {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.5);
}

.start-node.running {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5), 0 2px 12px rgba(245, 158, 11, 0.4);
}

.start-node.completed {
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.5), 0 2px 12px rgba(16, 185, 129, 0.3);
}

.start-node.error {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.5), 0 2px 12px rgba(239, 68, 68, 0.4);
}

.start-node.is-compact {
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
  color: rgba(255, 255, 255, 0.95);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-hint {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
}

.start-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.start-node-config {
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
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.params-table-container {
  overflow-x: auto;
}

.params-table {
  width: 100%;
  border-collapse: collapse;
}

.params-table th {
  padding: 10px 16px;
  text-align: left;
  font-size: 12px;
  color: #666;
  font-weight: 500;
  border-bottom: 1px solid #e0e0e0;
  background: #fafafa;
}

.params-table td {
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.required-marker {
  color: #f5222d;
  margin-right: 4px;
  font-weight: 600;
}

.table-param-name {
  width: 120px;
}

.table-param-type {
  width: 100px;
}

.table-param-desc {
  flex: 1;
}

.table-param-required {
  width: 80px;
  text-align: center;
}

.table-param-action {
  width: 60px;
  text-align: center;
}

.params-table-wrapper {
  padding: 0;
  background: #fff;
}

.params-table-wrapper::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.params-table-wrapper::-webkit-scrollbar-track {
  background: #f5f5f5;
}

.params-table-wrapper::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.params-table-wrapper::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
  background: #fafafa;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 16px;
}

.empty-add-btn {
  padding: 8px 24px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.empty-add-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.params-table-container {
  border: none;
  border-radius: 0;
}

.params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  table-layout: fixed;
}

.params-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 13px;
  color: #333;
  font-weight: 600;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  white-space: nowrap;
}

.params-table td {
  padding: 14px 16px;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.params-table tbody tr:hover td {
  background: #fafafa;
}

.params-table tbody tr:last-child td {
  border-bottom: 1px solid #e8e8e8;
}

.param-row {
  transition: background-color 0.2s;
}

.param-row:hover {
  background-color: #fafafa;
}

.col-order {
  width: 50px;
  text-align: center;
}

.col-name {
  width: 20%;
  min-width: 140px;
}

.col-type {
  width: 15%;
  min-width: 120px;
}

.col-desc {
  width: 35%;
  min-width: 180px;
}

.col-required {
  width: 100px;
  text-align: center;
}

.col-action {
  width: 60px;
  text-align: center;
}

.order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 10px;
  font-weight: 500;
  border-radius: 50%;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #1890ff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
  margin: 0 auto;
}

.action-btn:hover:not(:disabled) {
  background: #e6f7ff;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-btn.delete-btn {
  color: #1890ff;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.param-name-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  color: #333;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-name-input::placeholder,
.param-desc-input::placeholder {
  color: #bfbfbf;
  font-style: italic;
}

.param-name-input:focus,
.param-desc-input:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
  outline: none;
}

.param-type-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  color: #333;
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.param-type-select:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
  outline: none;
}

.type-select-wrapper {
  position: relative;
  display: block;
}

.type-select-wrapper:hover::after {
  content: attr(title);
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 12px;
  background: #1f2937;
  color: white;
  font-size: 12px;
  line-height: 1.5;
  border-radius: 6px;
  white-space: nowrap;
  z-index: 1000;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  max-width: 300px;
  white-space: normal;
}

.param-desc-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  color: #333;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-desc-input::placeholder {
  color: #bfbfbf;
}

.param-desc-input:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
  outline: none;
}

.param-name-input:hover,
.param-type-select:hover,
.param-desc-input:hover {
  border-color: #40a9ff;
}

.param-default-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #374151;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-default-input::placeholder {
  color: #9ca3af;
}

.param-default-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-default-input.date-input {
  min-width: 120px;
}

.param-default-select {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 14px;
  padding-right: 30px;
  box-sizing: border-box;
}

.param-default-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.required-toggle {
  width: 44px;
  height: 22px;
  border-radius: 11px;
  background: #d9d9d9;
  cursor: pointer;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin: 0 auto;
  border: none;
  outline: none;
}

.required-toggle:hover {
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.required-toggle.active {
  background: #f5222d;
}

.required-toggle.active:hover {
  box-shadow: 0 0 0 2px rgba(245, 34, 45, 0.2);
}

.required-toggle::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  background: #fff;
  border-radius: 50%;
  transition: left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.required-toggle.active::after {
  left: 24px;
}

.action-delete {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-delete:hover {
  background: #f5f5f5;
  color: #f5222d;
}

.action-delete svg {
  width: 14px;
  height: 14px;
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
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn button:hover {
  background: #40a9ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.collapse-btn button:active {
  transform: scale(0.98);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  background: #fafafa;
  border-radius: 8px;
  margin: 16px;
}

.empty-state svg {
  margin-bottom: 12px;
  opacity: 0.6;
}

.empty-text {
  font-size: 13px;
  color: #999;
  margin: 0;
}

.param-list-enter-active,
.param-list-leave-active {
  transition: all 0.3s ease;
}

.param-list-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.param-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  flex: 1;
}

.params-toggle {
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

.params-toggle:hover,
.params-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.status-indicator {
  flex-shrink: 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.running {
  background-color: #f59e0b;
  animation: pulse 1s infinite;
}

.status-dot.completed {
  background-color: #10b981;
}

.status-dot.error {
  background-color: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.node-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-desc {
  font-size: 11px;
  opacity: 0.9;
}

.params-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.3);
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.params-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}

.param-item {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  padding: 8px;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
}

.param-name-row {
  display: flex;
  gap: 6px;
  flex: 1;
}

.btn-add-param-container {
  margin-top: 10px;
}

.btn-add-param {
  width: 100%;
  padding: 6px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px dashed rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.2s;
}

.btn-add-param:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.5);
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

:deep(.vue-flow__handle[type="source"]) {
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #059669 !important;
}
</style>
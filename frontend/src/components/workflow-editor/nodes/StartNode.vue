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
    <div v-if="!compact || configMode" class="node-body">
      <span class="node-desc">工作流入口</span>
      
      <div v-if="configMode" class="params-panel">
        <div v-if="configMode" class="config-header">
          <span class="config-desc">工作流的起始节点，用于设定启动工作流需要的信息</span>
        </div>
        
        <div v-if="configMode" class="input-section">
          <div class="section-header">
            <button @click="addParam" class="add-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              添加参数
            </button>
          </div>
          
          <div v-show="inputSectionExpanded" class="params-table-wrapper">
            <div v-if="localParams.length === 0" class="empty-state">
              <div class="empty-icon">📋</div>
              <span class="empty-text">暂无输入参数</span>
              <button @click="addParam" class="empty-add-btn">添加第一个参数</button>
            </div>
            
            <div v-else class="params-table-container">
              <table class="params-table">
                <thead>
                  <tr>
                    <th class="col-order">序号</th>
                    <th class="col-name">
                      <span class="required-marker">*</span>参数名
                    </th>
                    <th class="col-type">数据类型</th>
                    <th class="col-desc">参数描述</th>
                    <th class="col-default">默认值</th>
                    <th class="col-required">是否必填</th>
                    <th class="col-action">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(param, index) in localParams" :key="index" class="param-row">
                    <td class="col-order">
                      <span class="order-badge">{{ index + 1 }}</span>
                    </td>
                    <td class="col-name">
                      <input
                        v-model="param.name"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入参数名"
                        class="param-name-input"
                      />
                    </td>
                    <td class="col-type">
                      <select
                        v-model="param.type"
                        @change="emitUpdate"
                        class="param-type-select"
                      >
                        <option value="string">字符串</option>
                        <option value="int">整数</option>
                        <option value="float">浮点数</option>
                        <option value="date">日期</option>
                        <option value="datetime">日期时间</option>
                        <option value="tel">手机号</option>
                        <option value="boolean">布尔值</option>
                      </select>
                    </td>
                    <td class="col-desc">
                      <input
                        v-model="param.description"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入描述"
                        class="param-desc-input"
                      />
                    </td>
                    <td class="col-default">
                      <input
                        v-if="param.type === 'string' || param.type === 'tel'"
                        v-model="param.default"
                        @input="emitUpdate"
                        type="text"
                        placeholder="-"
                        class="param-default-input"
                      />
                      <input
                        v-else-if="param.type === 'int'"
                        v-model.number="param.default"
                        @input="emitUpdate"
                        type="number"
                        placeholder="0"
                        class="param-default-input"
                      />
                      <input
                        v-else-if="param.type === 'float'"
                        v-model.number="param.default"
                        @input="emitUpdate"
                        type="number"
                        step="0.01"
                        placeholder="0.00"
                        class="param-default-input"
                      />
                      <input
                        v-else-if="param.type === 'date'"
                        v-model="param.default"
                        @input="emitUpdate"
                        type="date"
                        class="param-default-input date-input"
                      />
                      <input
                        v-else-if="param.type === 'datetime'"
                        v-model="param.default"
                        @input="emitUpdate"
                        type="datetime-local"
                        class="param-default-input date-input"
                      />
                      <select
                        v-else-if="param.type === 'boolean'"
                        v-model="param.default"
                        @change="emitUpdate"
                        class="param-default-select"
                      >
                        <option :value="true">是</option>
                        <option :value="false">否</option>
                      </select>
                    </td>
                    <td class="col-required">
                      <div 
                        class="required-toggle" 
                        :class="{ active: param.required }"
                        @click="toggleRequired(index)"
                      >
                        <span class="toggle-text">{{ param.required ? '是' : '否' }}</span>
                      </div>
                    </td>
                    <td class="col-action">
                      <div class="action-buttons">
                        <button @click="moveParam(index, -1)" class="action-btn move-up" :disabled="index === 0" title="上移">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="18 15 12 9 6 15"/>
                          </svg>
                        </button>
                        <button @click="moveParam(index, 1)" class="action-btn move-down" :disabled="index === localParams.length - 1" title="下移">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="6 9 12 15 18 9"/>
                          </svg>
                        </button>
                        <button @click="removeParam(index)" class="action-btn delete-btn" title="删除">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6m3 0V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                          </svg>
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        
        <div v-if="!configMode" class="params-list">
          <div 
            v-for="(param, index) in localParams" 
            :key="index"
            class="param-item"
          >
            <div class="param-header">
              <div class="param-name-row">
                <span v-if="param.required" class="required-tag">*</span>
                <input
                  v-model="param.name"
                  @input="emitUpdate"
                  type="text"
                  placeholder="参数名"
                  class="param-name-input"
                />
                <select
                  v-model="param.type"
                  @change="emitUpdate"
                  class="param-type-select"
                >
                  <option value="string">字符串</option>
                  <option value="int">整数</option>
                  <option value="date">日期</option>
                  <option value="float">浮点数</option>
                  <option value="tel">手机号</option>
                  <option value="datetime">日期时间</option>
                  <option value="boolean">布尔值</option>
                </select>
              </div>
              <button @click="removeParam(index)" class="btn-remove">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            
            <div class="param-default">
              <label>默认值</label>
              <input
                v-if="param.type === 'string'"
                v-model="param.default"
                @input="emitUpdate"
                type="text"
                placeholder="默认值"
                class="param-input"
              />
              <input
                v-else-if="param.type === 'int'"
                v-model.number="param.default"
                @input="emitUpdate"
                type="number"
                placeholder="默认值"
                class="param-input"
              />
              <input
                v-else-if="param.type === 'float'"
                v-model.number="param.default"
                @input="emitUpdate"
                type="number"
                placeholder="默认值"
                class="param-input"
              />
              <input
                v-else-if="param.type === 'date' || param.type === 'datetime'"
                v-model="param.default"
                @input="emitUpdate"
                type="text"
                placeholder="默认值"
                class="param-input"
              />
              <input
                v-else-if="param.type === 'tel'"
                v-model="param.default"
                @input="emitUpdate"
                type="text"
                placeholder="默认值"
                class="param-input"
              />
              <select
                v-else-if="param.type === 'boolean'"
                v-model="param.default"
                @change="emitUpdate"
                class="param-select"
              >
                <option :value="true">true</option>
                <option :value="false">false</option>
              </select>
              <textarea
                v-else
                v-model="param.default"
                @input="emitUpdate"
                placeholder="JSON 格式"
                class="param-textarea"
                rows="2"
              ></textarea>
            </div>
            
            <div class="param-desc">
              <input
                v-model="param.description"
                @input="emitUpdate"
                type="text"
                placeholder="描述（可选）"
                class="param-desc-input"
              />
            </div>
          </div>
        </div>
        
        <div v-if="!configMode" class="btn-add-param-container">
          <button @click="addParam" class="btn-add-param">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            <span>添加参数</span>
          </button>
        </div>
        
        <div v-if="configMode" class="collapse-btn">
          <button @click="$emit('update', props.data.id, { parameters: localParams })">收起</button>
        </div>
      </div>
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

const emit = defineEmits(['update']);

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
    required: true
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

const emitUpdate = () => {
  emit('update', props.data.id, {
    parameters: localParams.value
  });
};

watch(() => props.data, (newData) => {
  if (newData.parameters) {
    localParams.value = JSON.parse(JSON.stringify(newData.parameters));
  } else {
    localParams.value = [];
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

.start-node.is-config-mode .node-header {
  display: none;
}

.start-node.is-config-mode .node-body {
  padding: 0;
}

.start-node.is-config-mode .node-desc {
  display: none;
}

.start-node.is-config-mode .params-panel {
  border-top: none;
  padding-top: 0;
  margin-top: 0;
}

.config-header {
  padding: 12px 20px;
  background: #fafafa;
  border-bottom: 1px solid #e5e7eb;
}

.config-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.input-section {
  border-bottom: 1px solid #e0e0e0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
}

.help-icon {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 4px;
  font-style: normal;
}

.add-btn {
  height: 32px;
  padding: 0 14px;
  border: none;
  background: #3b82f6;
  color: white;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.add-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.add-btn svg {
  width: 16px;
  height: 16px;
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
  padding: 16px 20px;
  background: #fff;
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
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.params-table {
  width: 100%;
  min-width: 800px;
  border-collapse: collapse;
  font-size: 13px;
}

.params-table th {
  padding: 12px 14px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  background: #f9fafb;
  border-bottom: 2px solid #e5e7eb;
  white-space: nowrap;
}

.params-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}

.params-table tbody tr:hover {
  background: #f9fafb;
}

.params-table tbody tr:last-child td {
  border-bottom: none;
}

.col-order {
  width: 60px;
  text-align: center;
}

.col-name {
  width: 140px;
}

.col-type {
  width: 120px;
}

.col-desc {
  min-width: 200px;
  width: auto;
}

.col-default {
  min-width: 140px;
  width: auto;
}

.col-required {
  width: 90px;
  text-align: center;
}

.col-action {
  width: 90px;
  text-align: center;
}

.order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 11px;
  font-weight: 500;
  border-radius: 50%;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.action-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: #f3f4f6;
  color: #374151;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-btn.delete-btn:hover {
  background: #fee2e2;
  color: #dc2626;
}

.start-node.is-config-mode .param-name-input {
  flex: 1;
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #333;
  outline: none;
  transition: border-color 0.2s;
}

.start-node.is-config-mode .param-name-input::placeholder {
  color: #9ca3af;
}

.start-node.is-config-mode .param-name-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.start-node.is-config-mode .param-name-input.error {
  border-color: #ef4444;
}

.start-node.is-config-mode .param-name-input.error:focus {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}

.start-node.is-config-mode .param-type-select {
  width: 100%;
  padding: 8px 10px;
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
  background-size: 16px;
  padding-right: 32px;
}

.start-node.is-config-mode .param-type-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.start-node.is-config-mode .param-desc-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #374151;
  outline: none;
  transition: all 0.2s;
}

.start-node.is-config-mode .param-desc-input::placeholder {
  color: #9ca3af;
}

.start-node.is-config-mode .param-desc-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-default-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  color: #374151;
  outline: none;
  transition: all 0.2s;
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
  padding: 8px 10px;
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
  background-size: 16px;
  padding-right: 32px;
}

.param-default-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.required-toggle {
  min-width: 52px;
  height: 28px;
  padding: 0 6px;
  border-radius: 14px;
  background: #e5e7eb;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
  display: flex;
  align-items: center;
}

.required-toggle.active {
  background: #3b82f6;
}

.required-toggle::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  background: #fff;
  border-radius: 50%;
  transition: left 0.2s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.required-toggle.active::after {
  left: 27px;
}

.toggle-text {
  font-size: 11px;
  font-weight: 500;
  color: #6b7280;
  position: relative;
  z-index: 1;
  transition: color 0.2s;
}

.required-toggle.active .toggle-text {
  color: white;
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
}

.collapse-btn button {
  padding: 8px 32px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.collapse-btn button:hover {
  background: #2563eb;
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
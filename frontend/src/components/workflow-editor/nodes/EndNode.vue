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
      <div class="config-section">
        <label class="section-label">选择回答模式</label>
        <select v-model="localResponseMode" @change="emitUpdate" class="config-select">
          <option value="text">文本</option>
          <option value="json">JSON</option>
          <option value="markdown">Markdown</option>
          <option value="html">HTML</option>
        </select>
      </div>

      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleOutputSection" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: showOutputSection }"
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
            <button @click="addOutputParam" class="add-param-btn" title="添加输出参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="showOutputSection" class="section-content">
          <!-- 输出参数容器 -->
          <div class="output-param-container">
            <!-- 输出参数表头 -->
            <div class="output-param-header">
              <span class="header-col header-type">类型</span>
              <span class="header-col header-name">参数名/变量</span>
              <span class="header-col header-data-type">数据类型</span>
              <span class="header-col header-desc">描述</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localOutputParams" :key="index">
              <div v-if="param" class="output-param-item">
                <select v-model="param.nameType" @change="handleOutputNameTypeChange(index)" class="param-name-type-select">
                  <option value="input">自定义</option>
                  <option value="reference">引用</option>
                </select>
                <div class="param-name-group">
                  <input
                    v-if="param.nameType === 'input'"
                    v-model="param.name"
                    @input="emitUpdate"
                    class="param-name-input"
                    placeholder="参数名"
                  />
                  <VariableCascader
                    v-else
                    :key="'output-ref-' + index + '-' + cascaderRefreshKey"
                    v-model="param.nameRef"
                    :available-variables="availableVariables"
                    placeholder="选择变量"
                    class="param-name-cascader"
                    @change="emitUpdate"
                  />
                </div>
                <select v-model="param.type" @change="emitUpdate" class="param-type-select">
                  <option value="string">string</option>
                  <option value="json">json</option>
                </select>
                <input v-model="param.description" @input="emitUpdate" placeholder="描述" class="param-desc-input" />
                <div class="param-action-cell">
                  <button @click="removeOutputParam(index)" class="action-btn delete-btn" title="删除">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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

      <div class="config-section">
        <label class="section-label">是否流式输出</label>
        <div class="radio-group">
          <label class="radio-option">
            <input 
              type="radio" 
              :value="true" 
              v-model="localStreaming" 
              @change="emitUpdate"
            />
            <span class="radio-label">是</span>
          </label>
          <label class="radio-option">
            <input 
              type="radio" 
              :value="false" 
              v-model="localStreaming" 
              @change="emitUpdate"
            />
            <span class="radio-label">否</span>
          </label>
        </div>
      </div>

      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleAnswerSection" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: showAnswerSection }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>回答内容</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置最终回答的内容模板">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="showAnswerSection" class="section-content">
          <textarea 
            v-model="localAnswerContent" 
            @input="emitUpdate"
            class="answer-textarea"
            placeholder="可以使用{变量名}的方式引用输出参数中的变量"
            rows="6"
          ></textarea>
        </div>
      </div>

      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
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
const localLabel = ref(props.data.label || '结束节点');
const localResponseMode = ref(props.data.responseMode || 'text');
const localOutputParams = ref((props.data.outputParams || []).map(p => ({
  name: p.name || '',
  nameType: p.nameType || 'input',
  nameRef: p.nameRef || '',
  source: p.source || '',
  type: p.type || 'string',
  description: p.description || p.desc || ''
})));
const localStreaming = ref(props.data.streaming !== undefined ? props.data.streaming : false);
const localAnswerContent = ref(props.data.answerContent || '');

// 强制 VariableCascader 刷新 key，当 availableVariables 变化时重新挂载
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

// 折叠状态
const showOutputSection = ref(true);
const showAnswerSection = ref(true);

// 更新数据
const emitUpdate = () => {
  const outputParams = localOutputParams.value
    .filter(p => p)
    .map(p => ({
      name: p.name || '',
      nameType: p.nameType || 'input',
      nameRef: p.nameRef || '',
      source: p.source || '',
      type: p.type || 'string',
      description: p.description || ''
    }));
  emit('update', props.data.id, {
    label: localLabel.value,
    responseMode: localResponseMode.value,
    outputParams,
    streaming: localStreaming.value,
    answerContent: localAnswerContent.value
  });
};

// 切换输出区域
const toggleOutputSection = () => {
  showOutputSection.value = !showOutputSection.value;
};

// 切换回答内容区域
const toggleAnswerSection = () => {
  showAnswerSection.value = !showAnswerSection.value;
};

// 添加输出参数
const addOutputParam = () => {
  localOutputParams.value.push({
    name: '',
    nameType: 'input',
    nameRef: '',
    source: '{{__output__}}',
    type: 'string',
    description: ''
  });
  emitUpdate();
};

const handleOutputNameTypeChange = (index) => {
  const param = localOutputParams.value[index];
  if (param.nameType === 'reference') {
    param.name = '';
    param.source = '';
    param.description = '';
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
  if (newData) {
    localLabel.value = newData.label || '结束节点';
    localResponseMode.value = newData.responseMode || 'text';
    localOutputParams.value = (newData.outputParams || []).map(p => ({
    name: p.name || '',
    nameType: p.nameType || 'input',
    nameRef: p.nameRef || '',
    source: p.source || '',
    type: p.type || 'string',
    description: p.description || p.desc || ''
  }));
    localStreaming.value = newData.streaming !== undefined ? newData.streaming : false;
    localAnswerContent.value = newData.answerContent || '';
  }
}, { deep: true });
</script>

<style scoped>
.end-node {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
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

.node-compact-body {
  padding: 8px 10px;
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

.section-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.config-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.config-select:focus {
  outline: none;
  border-color: #7c3aed;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1);
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
  background: #7c3aed;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #6d28d9;
  box-shadow: 0 2px 6px rgba(124, 58, 237, 0.3);
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

/* 输出参数样式 */
/* 输出参数容器支持横向滚动 */
.output-param-container {
  overflow-x: auto;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.output-param-header {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 12px;
  color: #64748b;
  padding: 6px 8px;
  min-width: max-content;
}

.output-param-header .header-col {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.output-param-header .header-type {
  width: 60px;
  min-width: 60px;
}

.output-param-header .header-name {
  width: 200px;
  min-width: 200px;
}

.output-param-header .header-source {
  width: 180px;
  min-width: 180px;
}

.output-param-header .header-data-type {
  width: 90px;
  min-width: 90px;
}

.output-param-header .header-desc {
  width: 150px;
  min-width: 150px;
}

.output-param-header .header-action {
  width: 40px;
  min-width: 40px;
}

.output-param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  min-width: max-content;
  border-bottom: 1px solid #f1f5f9;
}

.output-param-item:last-child {
  border-bottom: none;
}

.param-name-group {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 200px;
  min-width: 200px;
  box-sizing: border-box;
}

.param-name-type-select {
  padding: 8px 4px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  background: white;
  flex-shrink: 0;
  width: 60px;
  min-width: 60px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 4px center;
  background-repeat: no-repeat;
  background-size: 10px;
  padding-right: 18px;
  box-sizing: border-box;
}

.param-name-type-select:focus {
  outline: none;
  border-color: #7c3aed;
}

.param-name-cascader {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.param-type-select {
  width: 90px;
  min-width: 90px;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.param-type-select:focus {
  outline: none;
  border-color: #7c3aed;
}

.param-desc-input {
  width: 150px;
  min-width: 150px;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-desc-placeholder {
  width: 150px;
  min-width: 150px;
}

.param-action-cell {
  width: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.param-desc-input:focus {
  outline: none;
  border-color: #7c3aed;
}

.param-source-readonly {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 13px;
  background: #f9fafb;
  color: #6b7280;
  font-family: monospace;
  box-sizing: border-box;
}

.param-name-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.param-name-input:focus {
  outline: none;
  border-color: #7c3aed;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1);
}

.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #7c3aed;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
  margin: 0 auto;
}

.action-btn:hover:not(:disabled) {
  background: #f3e8ff;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
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

/* 单选按钮组 */
.radio-group {
  display: flex;
  gap: 16px;
}

.radio-option {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.radio-option input[type="radio"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: #7c3aed;
}

.radio-label {
  font-size: 13px;
  color: #333;
}

/* 开关组件 */
.switch-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.switch-text {
  font-size: 13px;
  color: #666;
}

.switch-container {
  position: relative;
  width: 40px;
  height: 22px;
}

.switch-input {
  opacity: 0;
  width: 0;
  height: 0;
  position: absolute;
}

.switch-slider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  border-radius: 22px;
  cursor: pointer;
  transition: all 0.3s;
}

.switch-slider:before {
  position: absolute;
  content: "";
  height: 16px;
  width: 16px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: all 0.3s;
}

.switch-input:checked + .switch-slider {
  background-color: #7c3aed;
}

.switch-input:checked + .switch-slider:before {
  transform: translateX(18px);
}

/* 回答内容文本框 */
.answer-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  line-height: 1.6;
  transition: all 0.2s;
}

.answer-textarea:focus {
  outline: none;
  border-color: #7c3aed;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1);
}

.answer-textarea::placeholder {
  color: #bbb;
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
  background: #7c3aed;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn button:hover {
  background: #6d28d9;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.3);
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
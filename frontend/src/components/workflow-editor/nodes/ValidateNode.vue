<template>
  <div class="node validate-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">✓</span>
      <span class="node-title">{{ data.label || '数据验证' }}</span>
    </div>
    
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ validationSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>

    <!-- 配置模式 -->
    <div v-if="configMode" class="validate-node-config">
      <!-- 输入参数配置区 - 标准模式 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置验证所需的输入参数">
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

      <!-- 验证规则配置 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('rules')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.rules }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>验证规则</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置数据验证规则">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click.stop="addRule" class="add-param-btn" title="添加验证规则">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.rules" class="section-content">
          <template v-for="(rule, index) in localRules" :key="index">
            <div v-if="rule" class="rule-item">
              <input v-model="rule.field" @input="emitUpdate" placeholder="字段名" class="rule-field-input" />
              <select v-model="rule.type" @change="emitUpdate" class="rule-type-select">
                <option value="required">必填</option>
                <option value="minLength">最小长度</option>
                <option value="maxLength">最大长度</option>
                <option value="pattern">正则匹配</option>
                <option value="email">邮箱格式</option>
                <option value="phone">手机号</option>
                <option value="url">URL</option>
                <option value="number">数字</option>
                <option value="min">最小值</option>
                <option value="max">最大值</option>
              </select>
              <input 
                v-if="['minLength', 'maxLength', 'pattern', 'min', 'max'].includes(rule.type)"
                v-model="rule.value" 
                @input="emitUpdate" 
                :placeholder="getRulePlaceholder(rule.type)" 
                class="rule-value-input"
              />
              <input v-model="rule.message" @input="emitUpdate" placeholder="错误提示" class="rule-message-input" />
              <button @click="removeRule(index)" class="action-btn delete-btn" title="删除">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </template>
          <div v-if="localRules.length === 0" class="weak-hint">点击上方按钮添加验证规则</div>
        </div>
      </div>

      <!-- 输出参数配置区 -->
      <div class="config-section collapsible-section">
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
          <div class="output-wrapper">
            <span class="output-prefix">var.</span>
            <input 
              v-model="localOutputVar" 
              @input="emitUpdate" 
              placeholder="变量名" 
              class="param-input"
            />
          </div>
        </div>
      </div>

      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>

    <!-- 非配置模式的普通视图 -->
    <div v-else-if="!compact" class="node-body">
      <div class="validation-rules">
        <div class="rule-item" v-for="(rule, index) in localRules" :key="index">
          <span class="rule-field">{{ rule.field }}</span>
          <span class="rule-type">{{ getRuleLabel(rule.type) }}</span>
        </div>
      </div>
    </div>

    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
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
  compact: {
    type: Boolean,
    default: false
  },
  ...nodeDisplayProps
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update', 'close']);

const expandedSections = ref({ inputs: true, rules: true, outputs: true });

const localRules = ref(props.data.rules || []);
const localOutputVar = ref(props.data.outputVar || '');

// 输入参数 - 标准格式
const localInputs = ref((props.data.inputParams || []).map(p => ({
  name: p.name || '',
  valueType: p.valueType || 'input',
  defaultValue: p.defaultValue || p.value || '',
  refValue: p.refValue || ''
})));

const validationSummary = computed(() => {
  const count = localRules.value.length;
  return count > 0 ? `${count} 条规则` : '未配置规则';
});

const ruleLabels = {
  required: '必填',
  minLength: '最小长度',
  maxLength: '最大长度',
  pattern: '正则',
  email: '邮箱',
  phone: '手机号',
  url: 'URL',
  number: '数字',
  min: '最小值',
  max: '最大值'
};

const getRuleLabel = (type) => ruleLabels[type] || type;

const getRulePlaceholder = (type) => {
  const placeholders = {
    minLength: '最小长度值',
    maxLength: '最大长度值',
    pattern: '正则表达式',
    min: '最小值',
    max: '最大值'
  };
  return placeholders[type] || '';
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

const addRule = () => {
  localRules.value.push({ field: '', type: 'required', value: '', message: '' });
  emitUpdate();
};

const removeRule = (index) => {
  localRules.value.splice(index, 1);
  emitUpdate();
};

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
    rules: localRules.value,
    outputVar: localOutputVar.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
};

watch(() => props.data, (d) => {
  localRules.value = d.rules || [];
  localOutputVar.value = d.outputVar || '';
  
  if (d.inputParams) {
    localInputs.value = d.inputParams.map(p => ({
      name: p.name || '',
      valueType: p.valueType || 'input',
      defaultValue: p.defaultValue || p.value || '',
      refValue: p.refValue || ''
    }));
  }
}, { deep: true });
</script>

<style scoped>
.validate-node {
  background: white;
  border: 2px solid #f59e0b;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.validate-node.selected {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.2);
}

.validate-node.is-compact {
  min-width: 180px;
}

.validate-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
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

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-bottom: 1px solid #f59e0b;
  border-radius: 6px 6px 0 0;
}

.node-icon {
  font-size: 18px;
}

.node-title {
  font-weight: 600;
  color: #92400e;
  font-size: 14px;
}

.node-body {
  padding: 12px;
}

.validation-rules {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rule-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  background: #fef3c7;
  border-radius: 4px;
  font-size: 12px;
}

.rule-field {
  color: #92400e;
  font-weight: 500;
}

.rule-type {
  color: #b45309;
  font-size: 11px;
}

/* 配置模式样式 */
.validate-node-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 可折叠区域样式 */
.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
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
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-name-input.error {
  border-color: #ff4d4f;
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

/* 验证规则配置样式 */
.rule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.rule-field-input {
  width: 100px;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.rule-field-input:focus {
  outline: none;
  border-color: #f59e0b;
}

.rule-type-select {
  width: 90px;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  background: white;
}

.rule-type-select:focus {
  outline: none;
  border-color: #f59e0b;
}

.rule-value-input {
  width: 120px;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.rule-value-input:focus {
  outline: none;
  border-color: #f59e0b;
}

.rule-message-input {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.rule-message-input:focus {
  outline: none;
  border-color: #f59e0b;
}

/* 输出参数样式 */
.output-wrapper {
  display: flex;
  align-items: center;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
}

.output-wrapper:focus-within {
  border-color: #f59e0b;
}

.output-prefix {
  padding: 10px 8px 10px 12px;
  background: #f1f5f9;
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  border-right: 2px solid #e2e8f0;
}

.param-input {
  flex: 1;
  padding: 10px 12px;
  border: none;
  font-size: 13px;
  background: transparent;
  outline: none;
}

.param-input::placeholder {
  color: #94a3b8;
}

.weak-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

.collapse-btn {
  display: flex;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.collapse-btn button {
  padding: 8px 48px;
  background: #f59e0b;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn button:hover {
  background: #d97706;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.3);
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #fbbf24 !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #f59e0b !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #f59e0b !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #d97706 !important;
}
</style>
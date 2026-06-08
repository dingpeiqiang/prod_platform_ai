<template>
  <div class="node knowledge-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <div class="header-left">
        <div class="node-icon-wrapper">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <span class="node-title">{{ data.label }}</span>
      </div>
      <div v-if="localKnowledgeBase" class="header-right">
        <span class="kb-tag">{{ knowledgeBaseName }}</span>
      </div>
    </div>
    
    <div v-if="compact && !configMode" class="node-compact-body">
      <div class="compact-info">
        <div class="compact-kb">
          <span class="kb-badge">{{ knowledgeBaseName || '未选择' }}</span>
        </div>
        <div class="compact-mode">
          <span class="mode-indicator" :class="localQueryMode">
            {{ getModeLabel(localQueryMode) }}
          </span>
        </div>
      </div>
      <p class="compact-hint">双击配置</p>
    </div>
    
    <div v-if="!compact || configMode" class="node-body">
      <div class="section">
        <div class="section-header">
          <span class="section-icon">📚</span>
          <label class="section-label">选择知识库</label>
        </div>
        <div class="kb-select-wrapper">
          <select 
            v-model="localKnowledgeBase" 
            @change="emitUpdate" 
            class="kb-select"
          >
            <option value="">请选择知识库</option>
            <option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
              {{ kb.name }}
            </option>
          </select>
          <svg class="select-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 9l6 6 6-6"/>
          </svg>
        </div>
      </div>
      
      <div class="section">
        <div class="section-header">
          <span class="section-icon">🔍</span>
          <label class="section-label">查询模式</label>
        </div>
        <div class="mode-options">
          <button 
            v-for="mode in queryModes" 
            :key="mode.value" 
            class="mode-btn"
            :class="{ active: localQueryMode === mode.value }"
            @click="selectMode(mode.value)"
          >
            <span class="mode-icon">{{ getModeIcon(mode.value) }}</span>
            <span class="mode-text">{{ mode.label }}</span>
          </button>
        </div>
      </div>
      
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
            <button class="help-btn" title="配置查询内容的输入参数">
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

const knowledgeBases = ref([
  { id: 'kb1', name: '产品知识库' },
  { id: 'kb2', name: '技术文档库' },
  { id: 'kb3', name: '用户手册' },
  { id: 'kb4', name: 'FAQ知识库' }
]);

const queryModes = [
  { value: 'retrieve', label: '仅检索' },
  { value: 'qa', label: '问答模式' },
  { value: 'summarize', label: '摘要模式' }
];

const knowledgeBaseName = computed(() => {
  const kb = knowledgeBases.value.find(k => k.id === localKnowledgeBase.value);
  return kb ? kb.name : null;
});

const emit = defineEmits(['update']);

const localKnowledgeBase = ref(props.data.knowledgeBase || '');
const localQueryMode = ref(props.data.queryMode || 'retrieve');
const localQueryText = ref(props.data.queryText || '');
const localOutputVar = ref(props.data.outputVar || '');
const expandedSections = ref({ inputs: true, outputs: true });

// 输入参数 - 标准格式
const localInputs = ref((props.data.inputParams || []).map(p => ({
  name: p.name || '',
  valueType: p.valueType || 'input',
  defaultValue: p.defaultValue || p.value || '',
  refValue: p.refValue || ''
})));

// 如果没有inputParams但有queryText，将queryText作为默认输入参数
if (localInputs.value.length === 0 && localQueryText.value) {
  localInputs.value.push({
    name: 'query',
    valueType: 'input',
    defaultValue: localQueryText.value,
    refValue: ''
  });
}

const getModeLabel = (mode) => {
  const m = queryModes.find(q => q.value === mode);
  return m ? m.label : '';
};

const getModeIcon = (mode) => {
  const icons = {
    retrieve: '📋',
    qa: '❓',
    summarize: '📝'
  };
  return icons[mode] || '📋';
};

const selectMode = (mode) => {
  localQueryMode.value = mode;
  emitUpdate();
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
    knowledgeBase: localKnowledgeBase.value,
    queryMode: localQueryMode.value,
    queryText: inputParams.find(p => p.name === 'query')?.defaultValue || '',
    outputVar: localOutputVar.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
};

watch(() => props.data, (newData) => {
  localKnowledgeBase.value = newData.knowledgeBase || '';
  localQueryMode.value = newData.queryMode || 'retrieve';
  localQueryText.value = newData.queryText || '';
  localOutputVar.value = newData.outputVar || '';
  
  if (newData.inputParams) {
    localInputs.value = newData.inputParams.map(p => ({
      name: p.name || '',
      valueType: p.valueType || 'input',
      defaultValue: p.defaultValue || p.value || '',
      refValue: p.refValue || ''
    }));
  } else if (newData.queryText && localInputs.value.length === 0) {
    localInputs.value.push({
      name: 'query',
      valueType: 'input',
      defaultValue: newData.queryText,
      refValue: ''
    });
  }
}, { deep: true });
</script>

<style scoped>
.knowledge-node {
  background: linear-gradient(135deg, #ffffff 0%, #faf5ff 100%);
  border: 2px solid #ddd6fe;
  border-radius: 12px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 
    0 2px 8px rgba(139, 92, 246, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.knowledge-node:hover {
  box-shadow: 
    0 4px 16px rgba(139, 92, 246, 0.12),
    0 2px 4px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.knowledge-node.selected {
  border-color: #8b5cf6;
  box-shadow: 
    0 0 0 3px rgba(139, 92, 246, 0.15),
    0 4px 16px rgba(139, 92, 246, 0.15);
}

.knowledge-node.is-compact {
  min-width: 180px;
}

.knowledge-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
  background: transparent;
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 10px 10px 0 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon-wrapper {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: white;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: white;
  letter-spacing: 0.3px;
}

.header-right {
  flex-shrink: 0;
}

.kb-tag {
  display: inline-block;
  padding: 3px 10px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  font-size: 11px;
  color: white;
  font-weight: 500;
}

.node-compact-body {
  padding: 12px;
}

.compact-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.compact-kb {
  display: flex;
}

.kb-badge {
  padding: 4px 12px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border: 1px solid #ddd6fe;
  border-radius: 8px;
  font-size: 12px;
  color: #6d28d9;
  font-weight: 500;
}

.compact-mode {
  display: flex;
}

.mode-indicator {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.mode-indicator.retrieve {
  background: #eff6ff;
  color: #2563eb;
}

.mode-indicator.qa {
  background: #ecfdf5;
  color: #059669;
}

.mode-indicator.summarize {
  background: #fffbeb;
  color: #d97706;
}

.compact-hint {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

.node-body {
  padding: 14px;
}

.section {
  margin-bottom: 16px;
}

.section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.section-icon {
  font-size: 14px;
}

.section-label {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

.kb-select-wrapper {
  position: relative;
}

.kb-select {
  width: 100%;
  padding: 10px 32px 10px 12px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  background: white;
  cursor: pointer;
  appearance: none;
  transition: all 0.2s;
}

.kb-select:hover {
  border-color: #ddd6fe;
}

.kb-select:focus {
  outline: none;
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.select-icon {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  pointer-events: none;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: #ddd6fe;
  background: #faf5ff;
}

.mode-btn.active {
  border-color: #8b5cf6;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.15);
}

.mode-icon {
  font-size: 18px;
}

.mode-text {
  font-size: 11px;
  color: #475569;
  font-weight: 500;
}

.mode-btn.active .mode-text {
  color: #6d28d9;
}

/* 配置区域样式 */
.config-section {
  margin-top: 16px;
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
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
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
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: 6px;
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
  padding: 8px 10px;
  background: #fef2f2;
  border: 1px solid #fee2e2;
  border-radius: 6px;
  font-size: 11px;
  color: #dc2626;
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
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
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

:deep(.vue-flow__handle) {
  width: 14px !important;
  height: 14px !important;
  background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%) !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 
    0 0 0 3px rgba(139, 92, 246, 0.3),
    0 2px 8px rgba(139, 92, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

:deep(.vue-flow__handle:hover) {
  width: 22px !important;
  height: 22px !important;
  box-shadow: 
    0 0 0 4px rgba(139, 92, 246, 0.4),
    0 4px 12px rgba(139, 92, 246, 0.4) !important;
  transform: scale(1.1);
}

:deep(.vue-flow__handle[type="target"]) {
  background: linear-gradient(135deg, #a78bfa 0%, #8b5cf6 100%) !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%) !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%) !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%) !important;
}
</style>
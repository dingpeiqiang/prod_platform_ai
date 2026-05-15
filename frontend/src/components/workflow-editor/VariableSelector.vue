<template>
  <div class="variable-selector" :class="{ visible: isVisible }">
    <div class="selector-header">
      <span class="selector-title">选择变量</span>
      <button @click="close" class="close-btn">✕</button>
    </div>
    
    <div class="search-box">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/>
        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input 
        v-model="searchQuery" 
        type="text" 
        placeholder="搜索变量..." 
        class="search-input"
      />
      <div class="type-filter">
        <select v-model="typeFilter" class="filter-select">
          <option value="">全部类型</option>
          <option value="string">字符串</option>
          <option value="number">数字</option>
          <option value="boolean">布尔</option>
          <option value="object">对象</option>
          <option value="array">数组</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" class="loading-spinner">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="20 10"/>
      </svg>
      <p>加载变量列表中...</p>
    </div>

    <div v-else class="variable-categories">
      <div v-if="inputVariables.length > 0" class="category">
        <div class="category-header">
          <span class="category-icon">📥</span>
          <span class="category-name">输入参数</span>
          <span class="category-count">{{ inputVariables.length }}</span>
        </div>
        <div class="variable-list">
          <div 
            v-for="varItem in inputVariables" 
            :key="varItem.name"
            @click="selectVariable(varItem)"
            class="variable-item"
            :class="{ selected: selectedVar?.name === varItem.name }"
          >
            <span class="var-icon input-icon">📝</span>
            <div class="var-info">
              <span class="var-name">{{ varItem.name }}</span>
              <span class="var-type">{{ getTypeLabel(varItem.type) }}</span>
            </div>
            <span v-if="varItem.description" class="var-desc">{{ varItem.description }}</span>
            <span class="var-preview">{{ varItem.preview || '-' }}</span>
          </div>
        </div>
      </div>

      <div v-if="workflowVariables.length > 0" class="category">
        <div class="category-header">
          <span class="category-icon">⚙️</span>
          <span class="category-name">系统变量</span>
          <span class="category-count">{{ workflowVariables.length }}</span>
        </div>
        <div class="variable-list">
          <div 
            v-for="varItem in workflowVariables" 
            :key="varItem.name"
            @click="selectVariable(varItem)"
            class="variable-item"
            :class="{ selected: selectedVar?.name === varItem.name }"
          >
            <span class="var-icon workflow-icon">🔧</span>
            <div class="var-info">
              <span class="var-name">{{ varItem.name }}</span>
              <span class="var-type">{{ getTypeLabel(varItem.type) }}</span>
            </div>
            <span v-if="varItem.description" class="var-desc">{{ varItem.description }}</span>
            <span class="var-preview">{{ varItem.preview || '-' }}</span>
          </div>
        </div>
      </div>

      <div v-if="nodeOutputs.length > 0" class="category">
        <div class="category-header">
          <span class="category-icon">📤</span>
          <span class="category-name">节点输出</span>
          <span class="category-count">{{ nodeOutputs.length }}</span>
        </div>
        <div class="variable-list">
          <div 
            v-for="varItem in nodeOutputs" 
            :key="varItem.name"
            @click="selectVariable(varItem)"
            class="variable-item"
            :class="{ selected: selectedVar?.name === varItem.name }"
          >
            <span class="var-icon output-icon">🚀</span>
            <div class="var-info">
              <span class="var-name">{{ varItem.name }}</span>
              <span class="var-type">{{ getTypeLabel(varItem.type) }}</span>
            </div>
            <span class="var-desc">来自: {{ getSourceLabel(varItem.source, varItem.sourceNodeType) }}</span>
            <span class="var-preview">{{ varItem.preview || '-' }}</span>
          </div>
        </div>
      </div>

      <div v-if="allVariables.length === 0" class="empty-state">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="16" y1="3" x2="16" y2="7"/>
          <line x1="8" y1="3" x2="8" y2="7"/>
          <line x1="3" y1="16" x2="21" y2="16"/>
        </svg>
        <p>暂无可用变量</p>
        <p class="hint">添加开始节点参数或变量节点来创建变量</p>
      </div>
    </div>

    <div v-if="!loading && allVariables.length > 0" class="selector-footer">
      <button @click="insertVariable" class="btn-insert" :disabled="!selectedVar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        插入变量
      </button>
      <button @click="close" class="btn-cancel">取消</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  variables: { 
    type: Array, 
    default: () => [] 
  },
  selectedVar: { type: Object, default: null },
  loading: { type: Boolean, default: false }
});

const emit = defineEmits(['close', 'select', 'insert']);

const searchQuery = ref('');
const typeFilter = ref('');

const inputVariables = computed(() => {
  return props.variables
    .filter(v => v.source === 'workflow_input')
    .filter(filterBySearch)
    .filter(filterByType);
});

const workflowVariables = computed(() => {
  return props.variables
    .filter(v => v.source === 'system')
    .filter(filterBySearch)
    .filter(filterByType);
});

const nodeOutputs = computed(() => {
  return props.variables
    .filter(v => v.source === 'node_output')
    .filter(filterBySearch)
    .filter(filterByType);
});

const allVariables = computed(() => props.variables);

const filterBySearch = (item) => {
  if (!searchQuery.value) return true;
  const query = searchQuery.value.toLowerCase();
  return item.name.toLowerCase().includes(query) || 
         item.description?.toLowerCase().includes(query) ||
         item.sourceNodeType?.toLowerCase().includes(query);
};

const filterByType = (item) => {
  if (!typeFilter.value) return true;
  return item.type === typeFilter.value;
};

const getTypeLabel = (type) => {
  const typeMap = {
    'string': '字符串',
    'number': '数字',
    'boolean': '布尔',
    'object': '对象',
    'array': '数组',
    'any': '任意',
    'null': '空'
  };
  return typeMap[type] || type;
};

const getSourceLabel = (source, sourceNodeType) => {
  if (sourceNodeType) {
    const typeLabels = {
      'prompt': '提示词节点',
      'llm': 'LLM节点',
      'http': 'HTTP节点',
      'code': '代码节点',
      'parser': '解析器节点',
      'tool': '工具节点',
      'variable': '变量节点',
      'condition': '条件节点'
    };
    return typeLabels[sourceNodeType] || sourceNodeType;
  }
  return source || '未知';
};

const selectVariable = (varItem) => {
  emit('select', varItem);
};

const insertVariable = () => {
  if (props.selectedVar) {
    emit('insert', props.selectedVar);
  }
};

const close = () => {
  emit('close');
};

watch(() => props.visible, (newVal) => {
  if (newVal) {
    searchQuery.value = '';
    typeFilter.value = '';
  }
});
</script>

<style scoped>
.variable-selector {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) scale(0.9);
  width: 480px;
  max-height: 60vh;
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.variable-selector.visible {
  opacity: 1;
  visibility: visible;
  transform: translate(-50%, -50%) scale(1);
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px 12px 0 0;
}

.selector-title {
  font-size: 14px;
  font-weight: 600;
  color: white;
}

.close-btn {
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

.close-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.search-box svg {
  color: #94a3b8;
}

.search-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  background: white;
}

.search-input:focus {
  border-color: #3b82f6;
}

.variable-categories {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.category {
  margin-bottom: 12px;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 4px;
}

.category-icon {
  font-size: 14px;
}

.category-name {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.category-count {
  margin-left: auto;
  font-size: 10px;
  color: #94a3b8;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 10px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.variable-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.variable-item:hover {
  background: #dbeafe;
}

.variable-item.selected {
  background: #eff6ff;
  border: 1px solid #3b82f6;
}

.var-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 14px;
}

.input-icon {
  background: #dbeafe;
}

.workflow-icon {
  background: #d1fae5;
}

.output-icon {
  background: #fef3c7;
}

.var-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.var-name {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
}

.var-type {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 4px;
  border-radius: 3px;
  width: fit-content;
}

.var-desc {
  font-size: 11px;
  color: #94a3b8;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.var-preview {
  font-size: 11px;
  color: #64748b;
  font-family: 'Monaco', 'Menlo', monospace;
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.empty-state svg {
  margin-bottom: 12px;
}

.empty-state p {
  margin: 4px 0;
  font-size: 13px;
}

.empty-state .hint {
  font-size: 11px;
  color: #cbd5e1;
}

.selector-footer {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 0 0 12px 12px;
}

.btn-insert {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 6px;
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-insert:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-insert:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 8px 16px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-cancel:hover {
  background: #f1f5f9;
}
</style>
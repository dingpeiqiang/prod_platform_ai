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
    </div>

    <div v-if="loading" class="loading-state">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" class="loading-spinner">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="20 10"/>
      </svg>
      <p>加载变量列表中...</p>
    </div>

    <div v-else class="selector-body">
      <div class="node-panel">
        <div class="panel-header">
          <span>节点列表</span>
          <span class="count">{{ nodes.length }}</span>
        </div>
        <div class="node-list">
          <div 
            v-for="node in nodes" 
            :key="node.id"
            @click="selectNode(node)"
            class="node-item"
            :class="{ selected: selectedNode?.id === node.id }"
          >
            <span class="node-icon">{{ getNodeIcon(node.type) }}</span>
            <span class="node-name">{{ node.name }}</span>
            <span class="node-type">{{ getNodeTypeLabel(node.type) }}</span>
          </div>
          <div v-if="nodes.length === 0" class="empty-nodes">
            <p>暂无可用节点</p>
          </div>
        </div>
      </div>

      <div class="variable-panel">
        <div class="panel-header">
          <span>变量列表</span>
          <span class="count">{{ filteredVariables.length }}</span>
        </div>
        
        <div v-if="selectedNode" class="selected-node-info">
          <span class="label">当前节点:</span>
          <span class="value">{{ selectedNode.name }}</span>
        </div>

        <div class="variable-list">
          <div 
            v-for="varItem in filteredVariables" 
            :key="varItem.name"
            @click="selectVariable(varItem)"
            class="variable-item"
            :class="{ selected: selectedVar?.name === varItem.name }"
          >
            <span class="var-icon">{{ getVarIcon(varItem.source) }}</span>
            <div class="var-info">
              <span class="var-name">{{ varItem.name }}</span>
              <span class="var-type">{{ getTypeLabel(varItem.type) }}</span>
            </div>
            <span class="var-preview">{{ varItem.preview || '-' }}</span>
          </div>
          
          <div v-if="filteredVariables.length === 0 && !loading" class="empty-variables">
            <p>{{ selectedNode ? '该节点暂无输出变量' : '请先选择一个节点' }}</p>
          </div>
        </div>
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
const selectedNode = ref(null);

const nodes = computed(() => {
  const nodeMap = new Map();
  
  props.variables.forEach(v => {
    if (v.nodeId && v.sourceNodeName) {
      const key = v.nodeId;
      if (!nodeMap.has(key)) {
        nodeMap.set(key, {
          id: v.nodeId,
          name: v.sourceNodeName,
          type: v.sourceNodeType || v.nodeType
        });
      }
    }
  });
  
  return Array.from(nodeMap.values()).sort((a, b) => a.name.localeCompare(b.name));
});

const filteredVariables = computed(() => {
  let vars = props.variables;
  
  if (selectedNode.value) {
    vars = vars.filter(v => v.nodeId === selectedNode.value.id);
  }
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    vars = vars.filter(v => 
      v.name.toLowerCase().includes(query) ||
      v.sourceNodeName?.toLowerCase().includes(query) ||
      v.description?.toLowerCase().includes(query)
    );
  }
  
  return vars;
});

const allVariables = computed(() => props.variables);

const selectNode = (node) => {
  selectedNode.value = node;
  selectedVar.value = null;
  emit('select', null);
};

const selectVariable = (varItem) => {
  selectedVar.value = varItem;
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

const getNodeIcon = (type) => {
  const icons = {
    'start': '🚀',
    'end': '🏁',
    'prompt': '📝',
    'llm': '🤖',
    'tool': '🔧',
    'http': '🌐',
    'code': '💻',
    'variable': '📦',
    'condition': '🔀',
    'loop': '🔄',
    'parser': '📊',
    'knowledgeBase': '📚',
    'userInput': '👤',
    'form': '📋',
    'validate': '✅'
  };
  return icons[type] || '📌';
};

const getNodeTypeLabel = (type) => {
  const labels = {
    'start': '开始',
    'end': '结束',
    'prompt': '提示词',
    'llm': 'LLM',
    'tool': '工具',
    'http': 'HTTP',
    'code': '代码',
    'variable': '变量',
    'condition': '条件',
    'loop': '循环',
    'parser': '解析',
    'knowledgeBase': '知识库',
    'userInput': '用户输入',
    'form': '表单',
    'validate': '验证'
  };
  return labels[type] || type;
};

const getVarIcon = (source) => {
  if (source === 'workflow_input') return '📥';
  if (source === 'system') return '⚙️';
  if (source === 'node_output') return '📤';
  return '📌';
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

watch(() => props.visible, (newVal) => {
  if (newVal) {
    searchQuery.value = '';
    selectedNode.value = null;
  }
});
</script>

<style scoped>
.variable-selector {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) scale(0.9);
  width: 600px;
  max-height: 65vh;
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
  padding: 10px 16px;
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

.selector-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-panel {
  width: 200px;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.variable-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.panel-header .count {
  font-size: 10px;
  color: #94a3b8;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 10px;
}

.node-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 2px;
}

.node-item:hover {
  background: #dbeafe;
}

.node-item.selected {
  background: #3b82f6;
  color: white;
}

.node-item.selected .node-type {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.node-icon {
  font-size: 16px;
}

.node-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-type {
  font-size: 10px;
  color: #64748b;
  background: #e2e8f0;
  padding: 2px 4px;
  border-radius: 3px;
}

.empty-nodes {
  padding: 20px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}

.selected-node-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #eff6ff;
  border-bottom: 1px solid #dbeafe;
  font-size: 12px;
}

.selected-node-info .label {
  color: #64748b;
}

.selected-node-info .value {
  color: #3b82f6;
  font-weight: 500;
}

.variable-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.variable-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 2px;
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
  background: #f1f5f9;
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

.var-preview {
  font-size: 11px;
  color: #64748b;
  font-family: 'Monaco', 'Menlo', monospace;
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.empty-variables {
  padding: 40px 20px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
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
<template>
  <div class="output-schema-tree">
    <div v-if="groupedVariables.length === 0" class="no-variables">
      无可选工具出参
    </div>
    <div v-else class="tree-groups">
      <div v-for="group in groupedVariables" :key="group.nodeId" class="tree-group">
        <div class="group-header">
          <span class="group-label">{{ group.label }}</span>
        </div>
        <div class="group-items">
          <div
            v-for="item in group.children"
            :key="item.value"
            class="tree-item"
            :class="{ selected: modelValue === item.value }"
            @click="selectItem(item.value)"
          >
            <label class="radio-wrapper">
              <input
                type="radio"
                :name="radioName"
                :value="item.value"
                :checked="modelValue === item.value"
                @change="selectItem(item.value)"
              />
              <span class="radio-custom"></span>
            </label>
            <span class="item-label">{{ item.label }}</span>
            <span class="item-type">{{ item.type }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  availableVariables: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const radioName = ref(`schema-radio-${Math.random().toString(36).substr(2, 9)}`);

const groupedVariables = computed(() => {
  if (!props.availableVariables || !Array.isArray(props.availableVariables)) {
    return [];
  }
  
  const nodeMap = new Map();
  const nodeOrder = [];
  
  props.availableVariables.forEach(variable => {
    if (variable && variable.nodeId && variable.id) {
      const varName = variable.name || variable.varName || '未知变量';
      const varType = variable.type || variable.schemaType || 'any';
      
      if (!nodeMap.has(variable.nodeId)) {
        let nodeLabel = variable.sourceNodeName || variable.nodeName || getNodeLabelById(variable.nodeId);
        
        if (variable.nodeType === 'start') {
          nodeLabel = '开始节点';
        }
        
        nodeMap.set(variable.nodeId, {
          nodeId: variable.nodeId,
          label: nodeLabel,
          children: []
        });
        nodeOrder.push(variable.nodeId);
      }
      nodeMap.get(variable.nodeId).children.push({
        value: variable.id,
        label: varName,
        type: varType
      });
    }
  });
  
  return nodeOrder.map(nodeId => nodeMap.get(nodeId)).filter(Boolean);
});

const getNodeLabelById = (nodeId) => {
  if (!nodeId) return '未知节点';
  if (nodeId.startsWith('start')) return '开始节点';
  if (nodeId.startsWith('variable')) return '变量节点';
  if (nodeId.startsWith('llm')) return 'LLM节点';
  if (nodeId.startsWith('prompt')) return '提示词节点';
  if (nodeId.startsWith('tool')) return '工具节点';
  if (nodeId.startsWith('http')) return 'HTTP节点';
  if (nodeId.startsWith('code')) return '代码节点';
  if (nodeId.startsWith('parser')) return '解析节点';
  if (nodeId.startsWith('condition')) return '条件节点';
  if (nodeId.startsWith('userInput')) return '用户输入节点';
  if (nodeId.startsWith('form')) return '表单节点';
  if (nodeId.startsWith('validate')) return '验证节点';
  if (nodeId.startsWith('knowledgeBase')) return '知识库节点';
  if (nodeId.startsWith('loop')) return '循环节点';
  return nodeId;
};

const selectItem = (value) => {
  emit('update:modelValue', value);
  emit('change', value);
};
</script>

<style scoped>
.output-schema-tree {
  width: 100%;
  min-width: 180px;
}

.no-variables {
  padding: 8px 12px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.tree-groups {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tree-group {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.group-header {
  padding: 6px 10px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.group-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.group-items {
  padding: 4px 0;
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.tree-item:hover {
  background: #f8fafc;
}

.tree-item.selected {
  background: #ede9fe;
}

.radio-wrapper {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.radio-wrapper input[type="radio"] {
  display: none;
}

.radio-custom {
  width: 16px;
  height: 16px;
  border: 2px solid #cbd5e1;
  border-radius: 50%;
  position: relative;
  transition: all 0.2s;
}

.radio-wrapper input[type="radio"]:checked + .radio-custom {
  border-color: #8b5cf6;
  background: #8b5cf6;
}

.radio-wrapper input[type="radio"]:checked + .radio-custom::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 6px;
  height: 6px;
  background: white;
  border-radius: 50%;
}

.item-label {
  flex: 1;
  font-size: 13px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-type {
  font-size: 11px;
  color: #94a3b8;
  font-style: italic;
  flex-shrink: 0;
}
</style>
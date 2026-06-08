<template>
  <div class="cascader-wrapper">
    <el-cascader
      v-model="cascaderValue"
      :options="cascaderOptions"
      :props="{ expandTrigger: 'click', label: 'label', value: 'value', checkStrictly: true }"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="className"
      @change="handleChange"
      @visible-change="handleVisibleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    ></el-cascader>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  availableVariables: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择变量'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  className: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const cascaderValue = ref([]);

const cascaderOptions = computed(() => {
  if (!props.availableVariables || !Array.isArray(props.availableVariables)) {
    return [];
  }
  
  const nodeMap = new Map();
  const nodeOrder = [];
  
  props.availableVariables.forEach(variable => {
    if (variable && variable.nodeId && variable.id) {
      const varName = variable.name || variable.varName || '未知变量';
      if (!nodeMap.has(variable.nodeId)) {
        let nodeLabel = variable.sourceNodeName || variable.nodeName || getNodeLabelById(variable.nodeId);
        
        if (variable.nodeType === 'start') {
          nodeLabel = '开始节点';
        }
        
        nodeMap.set(variable.nodeId, {
          value: variable.nodeId,
          label: nodeLabel,
          children: []
        });
        nodeOrder.push(variable.nodeId);
      }
      nodeMap.get(variable.nodeId).children.push({
        value: variable.id,
        label: varName
      });
    }
  });
  
  return nodeOrder.map(nodeId => nodeMap.get(nodeId)).filter(Boolean);
});

const getNodeLabelById = (nodeId) => {
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

const buildPath = (variableId) => {
  if (!variableId) return [];
  
  let variable = props.availableVariables.find(v => v.id === variableId);
  
  if (!variable) {
    variable = props.availableVariables.find(v => v.varName === variableId);
  }
  
  if (!variable) {
    variable = props.availableVariables.find(v => 
      v.name && v.name.startsWith(variableId + ' ')
    );
  }
  
  if (variable && variable.nodeId) {
    return [variable.nodeId, variable.id];
  }
  
  // 如果 variableId 匹配某个父节点的 nodeId，则选中父节点本身
  const parentNode = props.availableVariables.find(v => v.nodeId === variableId);
  if (parentNode) {
    return [parentNode.nodeId];
  }
  
  return [];
};

const handleChange = (value) => {
  if (value && Array.isArray(value) && value.length >= 1) {
    // 选中子节点: [nodeId, childId] -> 取 childId
    // 选中父节点: [nodeId] -> 取 nodeId
    const selectedValue = value.length === 2 ? value[1] : value[0];
    emit('update:modelValue', selectedValue);
    emit('change', selectedValue);
  } else {
    emit('update:modelValue', '');
    emit('change', '');
  }
};

const handleVisibleChange = (visible) => {};

const handleBlur = () => {};

const handleFocus = () => {};

watch(() => props.modelValue, (newVal) => {
  cascaderValue.value = buildPath(newVal);
}, { immediate: true });

watch(cascaderOptions, () => {
  cascaderValue.value = buildPath(props.modelValue);
}, { deep: true });
</script>

<style scoped>
.cascader-wrapper {
  width: 100%;
  min-width: 200px;
  z-index: 1000;
}

:deep(.el-cascader) {
  width: 100%;
  min-width: 200px;
}

:deep(.el-cascader__input) {
  width: 100%;
}
</style>
<template>
  <el-cascader
    v-model="cascaderValue"
    :options="cascaderOptions"
    :props="{ expandTrigger: 'click', label: 'label', value: 'value' }"
    :placeholder="placeholder"
    :disabled="disabled"
    :class="className"
    @change="handleChange"
  ></el-cascader>
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
  if (!props.availableVariables || !Array.isArray(props.availableVariables)) return [];
  
  const nodeMap = new Map();
  
  props.availableVariables.forEach(variable => {
    if (variable && variable.nodeId && variable.id && variable.name) {
      if (!nodeMap.has(variable.nodeId)) {
        let nodeLabel = getNodeLabelById(variable.nodeId);
        
        if (variable.nodeType === 'start') {
          nodeLabel = '开始节点';
        } else if (variable.sourceNodeName) {
          nodeLabel = variable.sourceNodeName;
        } else {
          const namePart = variable.name.split('(')[0].trim();
          if (namePart && !namePart.includes('输出') && !namePart.includes('入参')) {
            nodeLabel = namePart;
          }
        }
        
        nodeMap.set(variable.nodeId, {
          value: variable.nodeId,
          label: nodeLabel,
          children: []
        });
      }
      nodeMap.get(variable.nodeId).children.push({
        value: variable.id,
        label: variable.name
      });
    }
  });
  
  return Array.from(nodeMap.values());
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
  return [];
};

const handleChange = (value) => {
  if (value && value.length === 2) {
    emit('update:modelValue', value[1]);
    emit('change', value[1]);
  } else {
    emit('update:modelValue', '');
    emit('change', '');
  }
};

watch(() => props.modelValue, (newVal) => {
  cascaderValue.value = buildPath(newVal);
}, { immediate: true });

watch(cascaderOptions, () => {
  cascaderValue.value = buildPath(props.modelValue);
}, { deep: true });
</script>

<style scoped>
</style>
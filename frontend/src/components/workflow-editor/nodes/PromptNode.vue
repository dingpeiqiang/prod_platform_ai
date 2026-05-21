<template>
  <div class="node prompt-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📝</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ compactPromptPreview }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <textarea
        v-model="localPrompt"
        @input="emitUpdate"
        :placeholder="placeholderText"
        class="node-textarea"
      ></textarea>
      
      <div v-if="configMode || showAdvanced" class="advanced-panel">
        <div class="section-title">输入参数</div>
        <div class="input-param-row">
          <el-cascader
            v-model="inputCascaderValue"
            :options="cascaderOptions"
            :props="{ expandTrigger: 'hover' }"
            placeholder="选择输入变量"
            class="param-cascader"
            :popper-append-to-body="true"
            @change="handleInputCascaderChange"
          ></el-cascader>
          <span class="param-hint" title="使用双花括号引用变量">或使用 {{ displayVarSyntax }} 语法</span>
        </div>
        
        <div class="section-title">输出参数</div>
        <div class="output-param-row">
          <input 
            v-model="localOutputVar" 
            @input="emitUpdate" 
            placeholder="输出变量名" 
            class="param-input"
          />
          <span class="param-hint">自定义变量名</span>
        </div>
      </div>
    </div>
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';

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

const compactPromptPreview = computed(() => {
  const text = localPrompt.value || '未设置提示词';
  return text.length > 28 ? `${text.slice(0, 28)}…` : text;
});

const emit = defineEmits(['update']);

const localPrompt = ref(props.data.prompt || '');
const showAdvanced = ref(false);
const localInputVar = ref(props.data.inputVar || '');
const localInputNodeId = ref(props.data.inputNodeId || '');
const inputCascaderValue = ref(props.data.inputCascaderValue || []);
const localOutputVar = ref(props.data.outputVar || '');

// 用于显示的占位符文本（避免 Vue 解析 {{}}）
const placeholderText = '输入提示词，使用 {{变量名}} 引用变量...';
const displayVarSyntax = '{{变量名}}';

// 监听 cascaderOptions 变化，清除已失效的 cascaderValue
watch(cascaderOptions, (newOptions) => {
  if (!newOptions || newOptions.length === 0) {
    // 选项为空时清除值
    if (inputCascaderValue.value?.length) {
      inputCascaderValue.value = [];
    }
    return;
  }

  // 检查当前选中值是否仍然有效
  if (inputCascaderValue.value?.length === 2) {
    const [nodeId, varId] = inputCascaderValue.value;
    const nodeExists = newOptions.some(opt => opt.value === nodeId);
    if (!nodeExists) {
      inputCascaderValue.value = [];
    }
  }
}, { immediate: false });

// 监听 availableVariables 变化，为存量工作流的引用参数重建 cascaderValue
watch(() => props.availableVariables, (newVars) => {
  // 如果变量列表为空或未定义，不处理
  if (!newVars || !Array.isArray(newVars) || newVars.length === 0) {
    return;
  }

  // 如果已经有 cascaderValue，说明已加载过，不需要重建
  if (inputCascaderValue.value?.length) {
    return;
  }

  // 只有当有引用信息但没有 cascaderValue 时才尝试重建
  if (localInputNodeId.value && localInputVar.value) {
    const matchedVar = newVars.find(v =>
      v.nodeId === localInputNodeId.value &&
      (v.id === `${localInputNodeId.value}.${localInputVar.value}` || v.name.startsWith(localInputVar.value))
    );

    if (matchedVar) {
      inputCascaderValue.value = [matchedVar.nodeId, matchedVar.id];
    }
  }
}, { immediate: true });

// 级联选择器选项
const cascaderOptions = computed(() => {
  if (!props.availableVariables || !Array.isArray(props.availableVariables)) return [];

  const nodeMap = new Map();

  props.availableVariables.forEach(variable => {
    if (variable && variable.nodeId && variable.id && variable.name) {
      if (!nodeMap.has(variable.nodeId)) {
        let nodeLabel = getNodeLabelById(variable.nodeId);

        if (variable.nodeType === 'start') {
          nodeLabel = '开始节点';
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
  return nodeId;
};

const handleInputCascaderChange = (value) => {
  if (Array.isArray(value) && value.length === 2) {
    localInputNodeId.value = value[0];
    localInputVar.value = value[1];
    inputCascaderValue.value = value;
  } else {
    localInputNodeId.value = '';
    localInputVar.value = '';
    inputCascaderValue.value = [];
  }
  emitUpdate();
};

const emitUpdate = () => {
  // 构建输入输出映射
  const inputs = {};
  const outputs = {};

  if (localInputVar.value) {
    inputs['input'] = `{{${localInputVar.value}}}`;
  }

  if (localOutputVar.value) {
    outputs[localOutputVar.value] = '{{__output__}}';
  }

  emit('update', props.data.id, {
    prompt: localPrompt.value,
    inputVar: localInputVar.value,
    inputNodeId: localInputNodeId.value,
    inputCascaderValue: inputCascaderValue.value,
    outputVar: localOutputVar.value,
    inputs: Object.keys(inputs).length > 0 ? inputs : undefined,
    outputs: Object.keys(outputs).length > 0 ? outputs : undefined
  });
};

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

watch(() => props.data, (newData) => {
  localPrompt.value = newData.prompt || '';
  localInputVar.value = newData.inputVar || '';
  localInputNodeId.value = newData.inputNodeId || '';
  inputCascaderValue.value = newData.inputCascaderValue || [];
  localOutputVar.value = newData.outputVar || '';
}, { deep: true });
</script>

<style scoped>
.prompt-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.prompt-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.prompt-node.is-compact {
  min-width: 180px;
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

.prompt-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.advanced-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  margin-left: auto;
  font-size: 12px;
}

.advanced-toggle:hover {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.advanced-toggle.active {
  background: #3b82f6;
  color: white;
}

.node-body {
  padding: 8px 10px;
}

.node-textarea {
  width: 100%;
  min-height: 60px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: inherit;
}

.node-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

.advanced-panel {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.input-param-row,
.output-param-row {
  margin-bottom: 8px;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
}

.param-cascader {
  width: 180px;
  flex-shrink: 0;
  font-size: 12px;
}

.param-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  background: white;
}

.param-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-input {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.param-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-hint {
  font-size: 9px;
  color: #94a3b8;
  white-space: nowrap;
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

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #3b82f6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #2563eb !important;
}
</style>
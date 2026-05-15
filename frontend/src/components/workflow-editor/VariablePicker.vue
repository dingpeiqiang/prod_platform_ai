<template>
  <div class="variable-picker">
    <button 
      @click="openSelector" 
      class="picker-btn"
      :title="title || '选择变量'"
      :disabled="loading"
    >
      <svg v-if="!loading" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
        <line x1="8" y1="3" x2="8" y2="7"/>
        <line x1="16" y1="3" x2="16" y2="7"/>
        <line x1="3" y1="16" x2="21" y2="16"/>
      </svg>
      <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" class="loading-icon">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="10 5"/>
      </svg>
      <span>{{ label }}</span>
    </button>
    
    <VariableSelector
      :visible="showSelector"
      :variables="variables"
      :selected-var="selectedVar"
      :loading="loading"
      @close="closeSelector"
      @select="handleSelect"
      @insert="handleInsert"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import VariableSelector from './VariableSelector.vue';
import { workflowApi } from '../../services/workflowApi';

const props = defineProps({
  modelValue: { type: String, default: '' },
  availableVariables: { type: Array, default: () => [] },
  label: { type: String, default: '变量' },
  title: { type: String, default: '' },
  placeholder: { type: String, default: '{{变量名}}' },
  workflowCode: { type: String, default: '' },
  nodeId: { type: String, default: '' }
});

const emit = defineEmits(['update:modelValue', 'insert']);

const showSelector = ref(false);
const selectedVar = ref(null);
const loading = ref(false);
const variables = ref([]);

const loadVariables = async () => {
  if (!props.workflowCode) {
    variables.value = props.availableVariables;
    return;
  }
  
  loading.value = true;
  try {
    const response = await workflowApi.getVariables(props.workflowCode, props.nodeId);
    if (response.variables) {
      variables.value = response.variables;
    } else {
      variables.value = props.availableVariables;
    }
  } catch (error) {
    console.error('Failed to load variables:', error);
    variables.value = props.availableVariables;
  } finally {
    loading.value = false;
  }
};

const openSelector = async () => {
  await loadVariables();
  showSelector.value = true;
};

const closeSelector = () => {
  showSelector.value = false;
  selectedVar.value = null;
};

const handleSelect = (varItem) => {
  selectedVar.value = varItem;
};

const handleInsert = (varItem) => {
  const variableRef = `{{${varItem.name}}}`;
  emit('update:modelValue', variableRef);
  emit('insert', varItem);
  closeSelector();
};

watch(() => props.availableVariables, (newVal) => {
  if (!props.workflowCode) {
    variables.value = newVal;
  }
}, { deep: true });
</script>

<style scoped>
.variable-picker {
  display: inline-block;
  position: relative;
}

.picker-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #3b82f6;
  border: none;
  border-radius: 4px;
  color: white;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.picker-btn:hover:not(:disabled) {
  background: #2563eb;
}

.picker-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.picker-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-icon {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
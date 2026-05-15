<template>
  <div class="variable-picker">
    <button 
      @click="openSelector" 
      class="picker-btn"
      :title="title || '选择变量'"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
        <line x1="8" y1="3" x2="8" y2="7"/>
        <line x1="16" y1="3" x2="16" y2="7"/>
        <line x1="3" y1="16" x2="21" y2="16"/>
      </svg>
      <span>{{ label }}</span>
    </button>
    
    <VariableSelector
      :visible="showSelector"
      :variables="availableVariables"
      :selected-var="selectedVar"
      @close="closeSelector"
      @select="handleSelect"
      @insert="handleInsert"
    />
  </div>
</template>

<script setup>import { ref } from 'vue';
import VariableSelector from './VariableSelector.vue';
const props = defineProps({
 modelValue: { type: String, default: '' },
 availableVariables: { type: Array, default: () => [] },
 label: { type: String, default: '变量' },
 title: { type: String, default: '' },
 placeholder: { type: String, default: '{{变量名}}' }
});
const emit = defineEmits(['update:modelValue', 'insert']);
const showSelector = ref(false);
const selectedVar = ref(null);
const openSelector = () => {
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

.picker-btn:hover {
  background: #2563eb;
}

.picker-btn:active {
  transform: scale(0.98);
}
</style>
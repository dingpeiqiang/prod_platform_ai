<template>
  <GenericNode :data="data" :selected="selected" icon="📦">
    <div class="variable-content">
      <input v-model="localVarName" @input="emitUpdate" placeholder="变量名" class="node-input" />
      <input v-model="localVarValue" @input="emitUpdate" placeholder="变量值" class="node-input" />
    </div>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({ data: { type: Object, required: true }, selected: { type: Boolean, default: false } });
const emit = defineEmits(['update']);
const localVarName = ref(props.data.varName || '');
const localVarValue = ref(props.data.varValue || '');

const emitUpdate = () => emit('update', props.data.id, { varName: localVarName.value, varValue: localVarValue.value });
watch(() => props.data, (d) => { localVarName.value = d.varName || ''; localVarValue.value = d.varValue || ''; }, { deep: true });
</script>

<style scoped>
.variable-content { display: flex; flex-direction: column; gap: 6px; }
.node-input { width: 100%; padding: 5px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 11px; }
</style>

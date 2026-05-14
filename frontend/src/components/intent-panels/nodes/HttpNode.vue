<template>
  <GenericNode :data="data" :selected="selected" icon="🌐">
    <div class="http-content">
      <select v-model="localMethod" @change="emitUpdate" class="node-select">
        <option value="GET">GET</option>
        <option value="POST">POST</option>
      </select>
      <input v-model="localUrl" @input="emitUpdate" placeholder="URL" class="node-input" />
    </div>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({ data: { type: Object, required: true }, selected: { type: Boolean, default: false } });
const emit = defineEmits(['update']);
const localMethod = ref(props.data.httpMethod || 'GET');
const localUrl = ref(props.data.httpUrl || '');

const emitUpdate = () => emit('update', props.data.id, { httpMethod: localMethod.value, httpUrl: localUrl.value });
watch(() => props.data, (d) => { localMethod.value = d.httpMethod || 'GET'; localUrl.value = d.httpUrl || ''; }, { deep: true });
</script>

<style scoped>
.http-content { display: flex; flex-direction: column; gap: 6px; }
.node-select, .node-input { width: 100%; padding: 5px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 11px; }
</style>

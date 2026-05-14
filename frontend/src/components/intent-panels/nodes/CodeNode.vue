<template>
  <GenericNode :data="data" :selected="selected" icon="💻">
    <textarea v-model="localCode" @input="emitUpdate" placeholder="输入代码..." class="node-textarea"></textarea>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({ data: { type: Object, required: true }, selected: { type: Boolean, default: false } });
const emit = defineEmits(['update']);
const localCode = ref(props.data.code || '');

const emitUpdate = () => emit('update', props.data.id, { code: localCode.value });
watch(() => props.data, (d) => { localCode.value = d.code || ''; }, { deep: true });
</script>

<style scoped>
.node-textarea { width: 100%; min-height: 60px; padding: 6px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 11px; resize: vertical; font-family: monospace; }
</style>

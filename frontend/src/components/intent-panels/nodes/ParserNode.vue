<template>
  <GenericNode :data="data" :selected="selected" icon="📊">
    <div class="parser-content">
      <select v-model="localParserType" @change="emitUpdate" class="node-select">
        <option value="json">JSON 解析</option>
        <option value="regex">正则提取</option>
      </select>
    </div>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({ data: { type: Object, required: true }, selected: { type: Boolean, default: false } });
const emit = defineEmits(['update']);
const localParserType = ref(props.data.parserType || 'json');

const emitUpdate = () => emit('update', props.data.id, { parserType: localParserType.value });
watch(() => props.data, (d) => { localParserType.value = d.parserType || 'json'; }, { deep: true });
</script>

<style scoped>
.parser-content { display: flex; flex-direction: column; gap: 6px; }
.node-select { width: 100%; padding: 5px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 11px; }
</style>

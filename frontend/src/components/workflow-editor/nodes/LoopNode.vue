<template>
  <GenericNode :data="data" :selected="selected" icon="🔄">
    <div class="loop-content">
      <select v-model="localLoopType" @change="emitUpdate" class="node-select">
        <option value="for">for 循环</option>
        <option value="while">while 循环</option>
      </select>
      <input
        v-model.number="localLoopCount"
        @input="emitUpdate"
        type="number"
        placeholder="循环次数"
        class="node-input"
      />
    </div>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

const emit = defineEmits(['update']);
const localLoopType = ref(props.data.loopType || 'for');
const localLoopCount = ref(props.data.loopCount || 5);

const emitUpdate = () => {
  emit('update', props.data.id, { loopType: localLoopType.value, loopCount: localLoopCount.value });
};

watch(() => props.data, (d) => {
  localLoopType.value = d.loopType || 'for';
  localLoopCount.value = d.loopCount || 5;
}, { deep: true });
</script>

<style scoped>
.loop-content { display: flex; flex-direction: column; gap: 6px; }
.node-select, .node-input { width: 100%; padding: 5px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 11px; }
</style>

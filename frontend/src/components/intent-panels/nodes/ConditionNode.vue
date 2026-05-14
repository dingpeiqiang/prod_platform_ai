<template>
  <GenericNode :data="data" :selected="selected" icon="🔀">
    <div class="condition-content">
      <input
        v-model="localCondition"
        @input="emitUpdate"
        type="text"
        placeholder="条件表达式"
        class="node-input"
      />
      <div class="condition-labels">
        <span class="label-true">✓ 满足</span>
        <span class="label-false">✗ 不满足</span>
      </div>
    </div>
  </GenericNode>
</template>

<script setup>
import { ref, watch } from 'vue';
import GenericNode from './GenericNode.vue';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update']);

const localCondition = ref(props.data.condition || '');

const emitUpdate = () => {
  emit('update', props.data.id, {
    condition: localCondition.value
  });
};

watch(() => props.data, (newData) => {
  localCondition.value = newData.condition || '';
}, { deep: true });
</script>

<style scoped>
.condition-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.condition-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}

.label-true {
  color: #166534;
  background-color: #dcfce7;
  padding: 2px 6px;
  border-radius: 3px;
}

.label-false {
  color: #991b1b;
  background-color: #fee2e2;
  padding: 2px 6px;
  border-radius: 3px;
}
</style>

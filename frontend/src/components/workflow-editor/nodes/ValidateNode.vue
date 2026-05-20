<template>
  <div class="node validate-node" :class="{ selected, compact }">
    <div class="node-header">
      <span class="node-icon">✓</span>
      <span class="node-title">{{ data.label || '数据验证' }}</span>
    </div>
    
    <div v-if="!compact" class="node-body">
      <div class="validation-rules">
        <div class="rule-item" v-for="(rule, index) in localRules" :key="index">
          <span class="rule-field">{{ rule.field }}</span>
          <span class="rule-type">{{ rule.type }}</span>
        </div>
      </div>
    </div>

    <Handle type="target" :position="targetPosition" id="target" />
    <Handle type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle } from '@vue-flow/core';
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
  compact: {
    type: Boolean,
    default: false
  }
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update']);

const localRules = ref(props.data.rules || []);

// 监听数据变化
watch(() => props.data.rules, (newRules) => {
  localRules.value = newRules || [];
});

const emitUpdate = () => {
  emit('update', {
    nodeId: props.data.id,
    updates: {
      rules: localRules.value
    }
  });
};
</script>

<style scoped>
.validate-node {
  background: white;
  border: 2px solid #f59e0b;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.validate-node.selected {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.2);
}

.validate-node.compact {
  min-width: 180px;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-bottom: 1px solid #f59e0b;
  border-radius: 6px 6px 0 0;
}

.node-icon {
  font-size: 18px;
}

.node-title {
  font-weight: 600;
  color: #92400e;
  font-size: 14px;
}

.node-body {
  padding: 12px;
}

.validation-rules {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rule-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  background: #fef3c7;
  border-radius: 4px;
  font-size: 12px;
}

.rule-field {
  color: #92400e;
  font-weight: 500;
}

.rule-type {
  color: #b45309;
  font-size: 11px;
}
</style>

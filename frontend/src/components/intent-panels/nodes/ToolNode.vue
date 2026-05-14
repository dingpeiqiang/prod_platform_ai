<template>
  <div class="node tool-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔧</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <select v-model="localToolName" @change="emitUpdate" class="node-select">
        <option value="">选择工具</option>
        <option value="web_search">网页搜索</option>
        <option value="database_query">数据库查询</option>
        <option value="file_read">文件读取</option>
        <option value="api_call">API调用</option>
      </select>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

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

const localToolName = ref(props.data.toolName || '');

const emitUpdate = () => {
  emit('update', props.data.id, {
    toolName: localToolName.value
  });
};

watch(() => props.data, (newData) => {
  localToolName.value = newData.toolName || '';
}, { deep: true });
</script>

<style scoped>
.tool-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 150px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.tool-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
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

.node-body {
  padding: 8px 10px;
}

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}
</style>

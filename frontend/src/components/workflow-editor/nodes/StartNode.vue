<template>
  <div class="node start-node" :class="{ selected, [executionStatus]: executionStatus }">
    <div class="node-header">
      <span class="node-icon">🚀</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleParams" class="params-toggle" :class="{ active: showParams }">
        ⚙
      </button>
      <span v-if="executionStatus" class="status-indicator">
        <span v-if="executionStatus === 'running'" class="status-dot running"></span>
        <span v-else-if="executionStatus === 'completed'" class="status-dot completed"></span>
        <span v-else-if="executionStatus === 'error'" class="status-dot error"></span>
      </span>
    </div>
    <div class="node-body">
      <span class="node-desc">工作流入口</span>
      
      <div v-if="showParams" class="params-panel">
        <div class="params-list">
          <div 
            v-for="(param, index) in localParams" 
            :key="index"
            class="param-item"
          >
            <div class="param-header">
              <div class="param-name-row">
                <input
                  v-model="param.name"
                  @input="emitUpdate"
                  type="text"
                  placeholder="参数名"
                  class="param-name-input"
                />
                <select
                  v-model="param.type"
                  @change="emitUpdate"
                  class="param-type-select"
                >
                  <option value="string">字符串</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔值</option>
                  <option value="object">对象</option>
                  <option value="array">数组</option>
                </select>
              </div>
              <button @click="removeParam(index)" class="btn-remove">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            
            <div class="param-default">
              <label>默认值</label>
              <input
                v-if="param.type === 'string'"
                v-model="param.default"
                @input="emitUpdate"
                type="text"
                placeholder="默认值"
                class="param-input"
              />
              <input
                v-else-if="param.type === 'number'"
                v-model.number="param.default"
                @input="emitUpdate"
                type="number"
                placeholder="默认值"
                class="param-input"
              />
              <select
                v-else-if="param.type === 'boolean'"
                v-model="param.default"
                @change="emitUpdate"
                class="param-select"
              >
                <option :value="true">true</option>
                <option :value="false">false</option>
              </select>
              <textarea
                v-else
                v-model="param.default"
                @input="emitUpdate"
                placeholder="JSON 格式"
                class="param-textarea"
                rows="2"
              ></textarea>
            </div>
            
            <div class="param-desc">
              <input
                v-model="param.description"
                @input="emitUpdate"
                type="text"
                placeholder="描述（可选）"
                class="param-desc-input"
              />
            </div>
          </div>
        </div>
        
        <button @click="addParam" class="btn-add-param">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span>添加参数</span>
        </button>
      </div>
    </div>
    <Handle type="source" :position="Position.Right" id="source" />
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
  },
  executionStatus: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update']);

const showParams = ref(false);
const localParams = ref([]);

const toggleParams = () => {
  showParams.value = !showParams.value;
};

const addParam = () => {
  localParams.value.push({
    name: '',
    type: 'string',
    description: '',
    default: ''
  });
  emitUpdate();
};

const removeParam = (index) => {
  localParams.value.splice(index, 1);
  emitUpdate();
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    parameters: localParams.value
  });
};

watch(() => props.data, (newData) => {
  if (newData.parameters) {
    localParams.value = JSON.parse(JSON.stringify(newData.parameters));
  } else {
    localParams.value = [];
  }
}, { immediate: true, deep: true });
</script>

<style scoped>
.start-node {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 8px;
  min-width: 220px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.start-node.selected {
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.5);
}

.start-node.running {
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.5), 0 2px 12px rgba(245, 158, 11, 0.4);
}

.start-node.completed {
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.5), 0 2px 12px rgba(16, 185, 129, 0.3);
}

.start-node.error {
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.5), 0 2px 12px rgba(239, 68, 68, 0.4);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  flex: 1;
}

.params-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.params-toggle:hover,
.params-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.status-indicator {
  flex-shrink: 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.running {
  background-color: #f59e0b;
  animation: pulse 1s infinite;
}

.status-dot.completed {
  background-color: #10b981;
}

.status-dot.error {
  background-color: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.node-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-desc {
  font-size: 11px;
  opacity: 0.9;
}

.params-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.3);
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.params-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}

.param-item {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  padding: 8px;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
}

.param-name-row {
  display: flex;
  gap: 6px;
  flex: 1;
}

.param-name-input {
  flex: 1;
  padding: 4px 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  outline: none;
}

.param-name-input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.param-name-input:focus {
  border-color: rgba(255, 255, 255, 0.6);
}

.param-type-select {
  padding: 4px 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  outline: none;
  cursor: pointer;
}

.btn-remove {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  padding: 2px;
  border-radius: 3px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-remove:hover {
  background: rgba(239, 68, 68, 0.3);
  color: #fecaca;
}

.param-default {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 4px;
}

.param-default label {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
}

.param-input,
.param-select {
  width: 100%;
  padding: 4px 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  outline: none;
}

.param-input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.param-input:focus,
.param-select:focus {
  border-color: rgba(255, 255, 255, 0.6);
}

.param-textarea {
  width: 100%;
  padding: 4px 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  outline: none;
  resize: vertical;
  font-family: inherit;
}

.param-textarea::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.param-textarea:focus {
  border-color: rgba(255, 255, 255, 0.6);
}

.param-desc {
  margin-top: 4px;
}

.param-desc-input {
  width: 100%;
  padding: 3px 6px;
  border: 1px dashed rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  font-size: 10px;
  background: transparent;
  color: rgba(255, 255, 255, 0.8);
  outline: none;
}

.param-desc-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.param-desc-input:focus {
  border-color: rgba(255, 255, 255, 0.4);
}

.btn-add-param {
  width: 100%;
  padding: 6px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px dashed rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.2s;
}

.btn-add-param:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.5);
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  background-color: #3b82f6 !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.5) !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #059669 !important;
}
</style>

<template>
  <div class="node llm-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div class="node-body">
      <select v-model="localModel" @change="emitUpdate" class="node-select">
        <option value="qwen-vl-plus">Qwen-VL-Plus</option>
        <option value="qwen-plus">Qwen-Plus</option>
        <option value="qwen-7b">Qwen-7B</option>
        <option value="qwen-14b">Qwen-14B</option>
        <option value="gpt-4o">GPT-4o</option>
        <option value="gpt-4">GPT-4</option>
        <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
        <option value="claude-3-opus">Claude 3 Opus</option>
        <option value="claude-3-sonnet">Claude 3 Sonnet</option>
        <option value="claude-3-haiku">Claude 3 Haiku</option>
        <option value="gemini-1.5-pro">Gemini 1.5 Pro</option>
        <option value="gemini-1.5-flash">Gemini 1.5 Flash</option>
      </select>
      
      <div class="param-row">
        <label>温度</label>
        <div class="param-control">
          <input
            v-model.number="localTemperature"
            type="range"
            min="0"
            max="2"
            step="0.1"
            @input="emitUpdate"
            class="node-range"
          />
          <input
            v-model.number="localTemperature"
            type="number"
            min="0"
            max="2"
            step="0.1"
            @input="emitUpdate"
            class="node-input-small"
          />
        </div>
      </div>

      <div v-if="showAdvanced" class="advanced-panel">
        <div class="section-title">高级参数</div>
        
        <div class="param-row">
          <label>Top P</label>
          <div class="param-control">
            <input
              v-model.number="localTopP"
              type="range"
              min="0"
              max="1"
              step="0.01"
              @input="emitUpdate"
              class="node-range"
            />
            <input
              v-model.number="localTopP"
              type="number"
              min="0"
              max="1"
              step="0.01"
              @input="emitUpdate"
              class="node-input-small"
            />
          </div>
        </div>

        <div class="param-row">
          <label>最大 Token</label>
          <input
            v-model.number="localMaxTokens"
            type="number"
            min="1"
            max="16384"
            @input="emitUpdate"
            class="node-input"
          />
        </div>

        <div class="param-row">
          <label>频率惩罚</label>
          <div class="param-control">
            <input
              v-model.number="localFrequencyPenalty"
              type="range"
              min="-2"
              max="2"
              step="0.1"
              @input="emitUpdate"
              class="node-range"
            />
            <input
              v-model.number="localFrequencyPenalty"
              type="number"
              min="-2"
              max="2"
              step="0.1"
              @input="emitUpdate"
              class="node-input-small"
            />
          </div>
        </div>

        <div class="param-row">
          <label>存在惩罚</label>
          <div class="param-control">
            <input
              v-model.number="localPresencePenalty"
              type="range"
              min="-2"
              max="2"
              step="0.1"
              @input="emitUpdate"
              class="node-range"
            />
            <input
              v-model.number="localPresencePenalty"
              type="number"
              min="-2"
              max="2"
              step="0.1"
              @input="emitUpdate"
              class="node-input-small"
            />
          </div>
        </div>

        <div class="param-row">
          <label>停止词</label>
          <input
            v-model="localStopTokens"
            @input="emitUpdate"
            type="text"
            placeholder="用逗号分隔"
            class="node-input"
          />
        </div>

        <div class="param-row">
          <label>系统提示词</label>
          <textarea
            v-model="localSystemPrompt"
            @input="emitUpdate"
            placeholder="输入系统提示词..."
            class="node-textarea"
          ></textarea>
        </div>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
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
  }
});

const emit = defineEmits(['update']);

const showAdvanced = ref(false);

const localModel = ref(props.data.model || 'qwen-vl-plus');
const localTemperature = ref(props.data.temperature || 0.7);
const localTopP = ref(props.data.topP || 0.95);
const localMaxTokens = ref(props.data.maxTokens || 4096);
const localFrequencyPenalty = ref(props.data.frequencyPenalty || 0);
const localPresencePenalty = ref(props.data.presencePenalty || 0);
const localStopTokens = ref(props.data.stopTokens || '');
const localSystemPrompt = ref(props.data.systemPrompt || '');

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    model: localModel.value,
    temperature: localTemperature.value,
    topP: localTopP.value,
    maxTokens: localMaxTokens.value,
    frequencyPenalty: localFrequencyPenalty.value,
    presencePenalty: localPresencePenalty.value,
    stopTokens: localStopTokens.value,
    systemPrompt: localSystemPrompt.value
  });
};

watch(() => props.data, (newData) => {
  localModel.value = newData.model || 'qwen-vl-plus';
  localTemperature.value = newData.temperature || 0.7;
  localTopP.value = newData.topP || 0.95;
  localMaxTokens.value = newData.maxTokens || 4096;
  localFrequencyPenalty.value = newData.frequencyPenalty || 0;
  localPresencePenalty.value = newData.presencePenalty || 0;
  localStopTokens.value = newData.stopTokens || '';
  localSystemPrompt.value = newData.systemPrompt || '';
}, { deep: true });
</script>

<style scoped>
.llm-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 220px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.llm-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: white;
  flex: 1;
}

.advanced-toggle {
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

.advanced-toggle:hover,
.advanced-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.node-body {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  background: white;
}

.node-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-row label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.param-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-range {
  flex: 1;
  height: 6px;
  cursor: pointer;
}

.node-input {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.node-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.node-input-small {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  text-align: center;
}

.advanced-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
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

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.node-textarea {
  width: 100%;
  min-height: 60px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: inherit;
}

.node-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
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

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #3b82f6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #2563eb !important;
}
</style>
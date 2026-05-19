<template>
  <div class="node knowledge-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <div class="header-left">
        <div class="node-icon-wrapper">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <span class="node-title">{{ data.label }}</span>
      </div>
      <div v-if="localKnowledgeBase" class="header-right">
        <span class="kb-tag">{{ knowledgeBaseName }}</span>
      </div>
    </div>
    
    <div v-if="compact && !configMode" class="node-compact-body">
      <div class="compact-info">
        <div class="compact-kb">
          <span class="kb-badge">{{ knowledgeBaseName || '未选择' }}</span>
        </div>
        <div class="compact-mode">
          <span class="mode-indicator" :class="localQueryMode">
            {{ getModeLabel(localQueryMode) }}
          </span>
        </div>
      </div>
      <p class="compact-hint">双击配置</p>
    </div>
    
    <div v-if="!compact || configMode" class="node-body">
      <div class="section">
        <div class="section-header">
          <span class="section-icon">📚</span>
          <label class="section-label">选择知识库</label>
        </div>
        <div class="kb-select-wrapper">
          <select 
            v-model="localKnowledgeBase" 
            @change="emitUpdate" 
            class="kb-select"
          >
            <option value="">请选择知识库</option>
            <option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
              {{ kb.name }}
            </option>
          </select>
          <svg class="select-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 9l6 6 6-6"/>
          </svg>
        </div>
      </div>
      
      <div class="section">
        <div class="section-header">
          <span class="section-icon">🔍</span>
          <label class="section-label">查询模式</label>
        </div>
        <div class="mode-options">
          <button 
            v-for="mode in queryModes" 
            :key="mode.value" 
            class="mode-btn"
            :class="{ active: localQueryMode === mode.value }"
            @click="selectMode(mode.value)"
          >
            <span class="mode-icon">{{ getModeIcon(mode.value) }}</span>
            <span class="mode-text">{{ mode.label }}</span>
          </button>
        </div>
      </div>
      
      <div class="section">
        <div class="section-header">
          <span class="section-icon">✏️</span>
          <label class="section-label">查询内容</label>
          <button v-if="localQueryText" class="clear-btn" @click="clearQuery">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="textarea-wrapper">
          <textarea
            v-model="localQueryText"
            @input="emitUpdate"
            placeholder="输入查询内容，使用 {{变量名}} 引用变量..."
            class="node-textarea"
            :rows="3"
          ></textarea>
          <div v-if="localQueryText" class="char-count">{{ localQueryText.length }}/500</div>
        </div>
      </div>
      
      <div class="section">
        <div class="section-header">
          <span class="section-icon">📤</span>
          <label class="section-label">输出变量</label>
        </div>
        <div class="output-wrapper">
          <span class="output-prefix">var.</span>
          <input 
            v-model="localOutputVar" 
            @input="emitUpdate" 
            placeholder="变量名" 
            class="param-input"
          />
        </div>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  ...nodeDisplayProps
});

const knowledgeBases = ref([
  { id: 'kb1', name: '产品知识库' },
  { id: 'kb2', name: '技术文档库' },
  { id: 'kb3', name: '用户手册' },
  { id: 'kb4', name: 'FAQ知识库' }
]);

const queryModes = [
  { value: 'retrieve', label: '仅检索' },
  { value: 'qa', label: '问答模式' },
  { value: 'summarize', label: '摘要模式' }
];

const knowledgeBaseName = computed(() => {
  const kb = knowledgeBases.value.find(k => k.id === localKnowledgeBase.value);
  return kb ? kb.name : null;
});

const emit = defineEmits(['update']);

const localKnowledgeBase = ref(props.data.knowledgeBase || '');
const localQueryMode = ref(props.data.queryMode || 'retrieve');
const localQueryText = ref(props.data.queryText || '');
const localOutputVar = ref(props.data.outputVar || '');

const getModeLabel = (mode) => {
  const m = queryModes.find(q => q.value === mode);
  return m ? m.label : '';
};

const getModeIcon = (mode) => {
  const icons = {
    retrieve: '📋',
    qa: '❓',
    summarize: '📝'
  };
  return icons[mode] || '📋';
};

const selectMode = (mode) => {
  localQueryMode.value = mode;
  emitUpdate();
};

const clearQuery = () => {
  localQueryText.value = '';
  emitUpdate();
};

const emitUpdate = () => {
  const outputs = {};
  if (localOutputVar.value) {
    outputs[localOutputVar.value] = '{{__output__}}';
  }
  
  emit('update', props.data.id, {
    knowledgeBase: localKnowledgeBase.value,
    queryMode: localQueryMode.value,
    queryText: localQueryText.value,
    outputVar: localOutputVar.value,
    outputs: Object.keys(outputs).length > 0 ? outputs : undefined
  });
};

watch(() => props.data, (newData) => {
  localKnowledgeBase.value = newData.knowledgeBase || '';
  localQueryMode.value = newData.queryMode || 'retrieve';
  localQueryText.value = newData.queryText || '';
  localOutputVar.value = newData.outputVar || '';
}, { deep: true });
</script>

<style scoped>
.knowledge-node {
  background: linear-gradient(135deg, #ffffff 0%, #faf5ff 100%);
  border: 2px solid #ddd6fe;
  border-radius: 12px;
  min-width: 260px;
  box-shadow: 
    0 2px 8px rgba(139, 92, 246, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.knowledge-node:hover {
  box-shadow: 
    0 4px 16px rgba(139, 92, 246, 0.12),
    0 2px 4px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.knowledge-node.selected {
  border-color: #8b5cf6;
  box-shadow: 
    0 0 0 3px rgba(139, 92, 246, 0.15),
    0 4px 16px rgba(139, 92, 246, 0.15);
}

.knowledge-node.is-compact {
  min-width: 180px;
}

.knowledge-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
  background: transparent;
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 10px 10px 0 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon-wrapper {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: white;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: white;
  letter-spacing: 0.3px;
}

.header-right {
  flex-shrink: 0;
}

.kb-tag {
  display: inline-block;
  padding: 3px 10px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  font-size: 11px;
  color: white;
  font-weight: 500;
}

.node-compact-body {
  padding: 12px;
}

.compact-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.compact-kb {
  display: flex;
}

.kb-badge {
  padding: 4px 12px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border: 1px solid #ddd6fe;
  border-radius: 8px;
  font-size: 12px;
  color: #6d28d9;
  font-weight: 500;
}

.compact-mode {
  display: flex;
}

.mode-indicator {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.mode-indicator.retrieve {
  background: #eff6ff;
  color: #2563eb;
}

.mode-indicator.qa {
  background: #ecfdf5;
  color: #059669;
}

.mode-indicator.summarize {
  background: #fffbeb;
  color: #d97706;
}

.compact-hint {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

.node-body {
  padding: 14px;
}

.section {
  margin-bottom: 16px;
}

.section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.section-icon {
  font-size: 14px;
}

.section-label {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

.clear-btn {
  margin-left: auto;
  padding: 4px;
  background: #f1f5f9;
  border: none;
  border-radius: 4px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #e2e8f0;
  color: #64748b;
}

.kb-select-wrapper {
  position: relative;
}

.kb-select {
  width: 100%;
  padding: 10px 32px 10px 12px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  background: white;
  cursor: pointer;
  appearance: none;
  transition: all 0.2s;
}

.kb-select:hover {
  border-color: #ddd6fe;
}

.kb-select:focus {
  outline: none;
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.select-icon {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  pointer-events: none;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: #ddd6fe;
  background: #faf5ff;
}

.mode-btn.active {
  border-color: #8b5cf6;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.15);
}

.mode-icon {
  font-size: 18px;
}

.mode-text {
  font-size: 11px;
  color: #475569;
  font-weight: 500;
}

.mode-btn.active .mode-text {
  color: #6d28d9;
}

.textarea-wrapper {
  position: relative;
}

.node-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  resize: none;
  font-family: inherit;
  transition: all 0.2s;
  box-sizing: border-box;
}

.node-textarea:hover {
  border-color: #ddd6fe;
}

.node-textarea:focus {
  outline: none;
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.node-textarea::placeholder {
  color: #94a3b8;
}

.char-count {
  position: absolute;
  right: 10px;
  bottom: 8px;
  font-size: 10px;
  color: #94a3b8;
}

.output-wrapper {
  display: flex;
  align-items: center;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
}

.output-wrapper:focus-within {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.output-prefix {
  padding: 10px 8px 10px 12px;
  background: #f1f5f9;
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  border-right: 2px solid #e2e8f0;
}

.param-input {
  flex: 1;
  padding: 10px 12px;
  border: none;
  font-size: 13px;
  background: transparent;
  outline: none;
}

.param-input::placeholder {
  color: #94a3b8;
}

:deep(.vue-flow__handle) {
  width: 14px !important;
  height: 14px !important;
  background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%) !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 
    0 0 0 3px rgba(139, 92, 246, 0.3),
    0 2px 8px rgba(139, 92, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

:deep(.vue-flow__handle:hover) {
  width: 22px !important;
  height: 22px !important;
  box-shadow: 
    0 0 0 4px rgba(139, 92, 246, 0.4),
    0 4px 12px rgba(139, 92, 246, 0.4) !important;
  transform: scale(1.1);
}

:deep(.vue-flow__handle[type="target"]) {
  background: linear-gradient(135deg, #a78bfa 0%, #8b5cf6 100%) !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%) !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%) !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%) !important;
}
</style>
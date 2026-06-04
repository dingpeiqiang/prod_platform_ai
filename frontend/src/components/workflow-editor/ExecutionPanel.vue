<template>
  <div class="execution-panel">
    <div class="panel-header">
      <div class="header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
        <span>执行日志</span>
        <span v-if="isPaused && waitingForm" class="status-badge paused">
          <span class="status-dot"></span>等待表单填写
        </span>
        <span v-else-if="isPaused" class="status-badge paused">
          <span class="status-dot"></span>等待输入
        </span>
        <span v-else-if="isRunning" class="status-badge running">
          <span class="status-dot"></span>运行中
        </span>
        <span v-else-if="lastResult" class="status-badge" :class="lastResult.status === 'success' ? 'success' : 'error'">
          {{ lastResult.status === 'success' ? '✓ 成功' : '✗ 失败' }}
        </span>
        <span class="node-count">{{ nodeExecutionData.length }} 个节点</span>
        <span v-if="hasError" class="error-badge">
          ⚠️ 已终止
        </span>
      </div>
      <div class="header-right">
        <button v-if="nodeExecutionData.length > 0" @click="toggleAllExpand" class="btn-action" :title="allExpanded ? '收起全部' : '展开全部'">
          <svg v-if="allExpanded" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="18 15 12 9 6 15"/>
          </svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
        <button v-if="nodeExecutionData.length > 0" @click="$emit('clear')" class="btn-action" title="清空日志">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="logs-container" ref="logsContainer">
      <div v-if="nodeExecutionData.length === 0 && !waitingForm" class="empty-logs">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="9" y1="13" x2="15" y2="13"/>
        </svg>
        <p>{{ isRunning ? '执行中...' : '暂无执行日志' }}</p>
        <span v-if="isRunning" class="running-hint">节点正在运行，请稍候</span>
      </div>

      <div v-else>
        <div
          v-for="(nodeData, groupIdx) in nodeExecutionData"
          :key="nodeData.nodeId"
          class="node-card"
          :class="[nodeData.status, { 'has-error': nodeData.status === 'error' }]"
        >
          <div class="node-header" @click="toggleExpand(nodeData.nodeId)">
            <div class="node-left">
              <span class="expand-icon" :class="{ expanded: expandedNodes[nodeData.nodeId] }">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </span>
              <span class="step-number">{{ groupIdx + 1 }}</span>
              <span class="node-icon">{{ nodeIconMap[nodeData.nodeType] || '📌' }}</span>
              <div class="node-info">
                <span class="node-name">{{ nodeData.nodeLabel }}</span>
                <span class="node-type">[{{ nodeData.nodeType }}]</span>
              </div>
            </div>
            <div class="node-right">
              <span class="node-duration">{{ nodeData.duration }}ms</span>
              <span :class="['status-badge-sm', nodeData.status]">
                {{ nodeData.status === 'completed' ? '✓' : nodeData.status === 'error' ? '✗' : '⋯' }}
              </span>
            </div>
          </div>

          <div v-if="expandedNodes[nodeData.nodeId]" class="node-details">
            <div v-if="nodeData.config && Object.keys(nodeData.config).length > 0" class="node-config">
              <div class="section-header">
                <span class="section-icon">⚙️</span>
                <span>配置</span>
              </div>
              <pre class="section-content config-content">{{ formatJson(nodeData.config) }}</pre>
            </div>

            <div v-if="nodeData.input" class="node-input">
              <div class="section-header">
                <span class="section-icon">📥</span>
                <span>输入</span>
                <button @click="copyToClipboard(nodeData.input)" class="copy-btn" title="复制">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  复制
                </button>
              </div>
              <pre class="section-content input-content">{{ formatJson(nodeData.input) }}</pre>
            </div>

            <div v-if="nodeData.logs && nodeData.logs.length > 0" class="node-logs">
              <div class="section-header">
                <span class="section-icon">📝</span>
                <span>执行日志 ({{ nodeData.logs.length }})</span>
              </div>
              <div class="logs-list">
                <div
                  v-for="(log, idx) in nodeData.logs"
                  :key="idx"
                  class="log-row"
                >
                  <span class="log-type" :class="log.type">{{ getLogTypeLabel(log.type) }}</span>
                  <span class="log-content">{{ log.message }}</span>
                </div>
              </div>
            </div>

            <div v-if="nodeData.error" class="node-error">
              <div class="error-alert">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="8" x2="12" y2="12"/>
                  <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <span>执行终止：此节点执行失败</span>
              </div>
              <div class="section-header error">
                <span class="section-icon">✗</span>
                <span>错误详情</span>
              </div>
              <pre class="section-content error-content">{{ nodeData.error }}</pre>
            </div>

            <div v-if="nodeData.output !== null && nodeData.output !== undefined" class="node-output">
              <div class="section-header">
                <span class="section-icon">📤</span>
                <span>输出</span>
                <button @click="copyToClipboard(nodeData.output)" class="copy-btn" title="复制">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  复制
                </button>
              </div>
              <pre class="section-content output-content">{{ formatJson(nodeData.output) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="lastResult && !isRunning && !isPaused" class="result-summary">
      <div class="result-header">
        <span>📊 执行结果</span>
        <div class="result-actions">
          <button @click="copyToClipboard(lastResult)" class="copy-btn" title="复制结果">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            复制
          </button>
          <span class="result-status" :class="lastResult.status === 'success' ? 'success' : 'error'">
            {{ lastResult.status === 'success' ? '✓ 成功' : '✗ 失败' }}
          </span>
        </div>
      </div>
      <pre class="result-content">{{ formatJson(lastResult) }}</pre>
    </div>

    <div v-if="waitingForm" class="form-panel">
      <div class="form-panel-header">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="9" y1="13" x2="15" y2="13"/>
        </svg>
        <span>📋 请填写表单</span>
      </div>
      
      <div class="form-panel-content">
        <div v-if="waitingForm?.validationError" class="validation-error-banner">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>{{ waitingForm.validationError }}</span>
        </div>
        <p v-if="waitingMessage" class="form-message">{{ waitingMessage }}</p>
        
        <DynamicForm
          v-if="waitingForm"
          :schema="waitingForm"
          :form-data="formData"
          :form-submitted="formSubmitted"
          :form-cancelled="formCancelled"
          @field-change="handleFieldChange"
          @submit="handleFormSubmit"
          @cancel="handleFormCancel"
          @confirm-submit="handleFormConfirmSubmit"
        />
      </div>
    </div>

    <div v-if="isPaused && !waitingForm && pendingInput" class="user-input-panel">
      <div class="input-panel-header">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>等待用户输入</span>
      </div>
      
      <div class="input-panel-content">
        <div class="prompt-text">{{ pendingInput.prompt }}</div>
        
        <div v-if="pendingInput.validationError" class="validation-error-banner">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>{{ pendingInput.validationError }}</span>
        </div>
        
        <div v-if="pendingInput.inputType === 'text'" class="input-field">
          <textarea 
            v-model="userInputValue"
            :placeholder="pendingInput.required ? '请输入内容（必填）' : '请输入内容'"
            :rows="3"
            class="text-input"
            @keyup.enter="handleSubmit"
          ></textarea>
        </div>
        
        <div v-else-if="pendingInput.inputType === 'select'" class="input-field">
          <select v-model="userInputValue" class="select-input">
            <option value="" disabled>请选择选项</option>
            <option v-for="(opt, idx) in pendingInput.options" :key="idx" :value="opt">{{ opt }}</option>
          </select>
        </div>
        
        <div v-else-if="pendingInput.inputType === 'confirm'" class="input-field">
          <div class="confirm-options">
            <button @click="handleConfirm(true)" class="confirm-btn confirm-yes">✓ 确认</button>
            <button @click="handleConfirm(false)" class="confirm-btn confirm-no">✗ 取消</button>
          </div>
        </div>
        
        <div class="input-actions">
          <button 
            v-if="pendingInput.inputType !== 'confirm'"
            @click="handleSubmit" 
            class="submit-btn"
            :disabled="pendingInput.required && !userInputValue"
          >
            继续执行
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, reactive } from 'vue';
import DynamicForm from '../DynamicForm.vue';

const props = defineProps({
  logs: { type: Array, default: () => [] },
  isRunning: { type: Boolean, default: false },
  isPaused: { type: Boolean, default: false },
  pendingInput: { type: Object, default: null },
  lastResult: { type: Object, default: null },
  history: { type: Array, default: () => [] },
  nodeExecutionData: { type: Array, default: () => [] },
  waitingForm: { type: Object, default: null },
  waitingMessage: { type: String, default: '' },
  workflowId: { type: String, default: '' }
});

const emit = defineEmits(['clear', 'resume', 'form-submit', 'form-cancel']);

const userInputValue = ref('');
const formData = ref({});
const formSubmitted = ref(false);
const formCancelled = ref(false);

watch(() => props.pendingInput, (newInput) => {
  if (newInput) {
    userInputValue.value = '';
  }
});

watch(() => props.waitingForm, (newForm) => {
  console.log('[ExecutionPanel] waitingForm 变化:', newForm);
  console.log('[ExecutionPanel] 推荐数据 formData:', newForm?.formData);
  if (newForm) {
    formSubmitted.value = false;
    formCancelled.value = false;
    formData.value = {};
    
    const recommendedData = newForm.formData || {};
    console.log('[ExecutionPanel] 推荐数据 recommendedData:', recommendedData);
    
    if (newForm.fields) {
      newForm.fields.forEach(field => {
        if (field.defaultValue !== undefined && field.defaultValue !== null) {
          const fieldCode = field.fieldCode;
          if (!recommendedData[fieldCode]) {
            recommendedData[fieldCode] = field.defaultValue;
            console.log(`[ExecutionPanel] 字段 ${fieldCode} 使用默认值:`, field.defaultValue);
          }
        }
      });
    }
    
    Object.assign(formData.value, recommendedData);
    console.log('[ExecutionPanel] 最终表单数据 formData:', formData.value);
  }
}, { immediate: true });

const handleFieldChange = (fieldCode, value) => {
  formData.value[fieldCode] = value;
  emit('form-submit', { type: 'field-change', workflowId: props.workflowId, fieldCode, value });
};

const handleFormSubmit = async () => {
  if (formSubmitted.value || formCancelled.value) return;
  
  formSubmitted.value = true;
  emit('resume', { workflowId: props.workflowId, formData: formData.value, type: 'form' });
};

const handleFormConfirmSubmit = async (formSubmitData) => {
  if (formSubmitted.value || formCancelled.value) return;
  
  console.log('[ExecutionPanel] 表单校验通过，准备提交:', formSubmitData);
  console.log('[ExecutionPanel] 工作流ID:', props.workflowId);
  console.log('[ExecutionPanel] 表单数据:', formSubmitData.data);
  
  formData.value = formSubmitData.data;
  formSubmitted.value = true;
  
  emit('resume', { 
    workflowId: props.workflowId, 
    formData: formSubmitData.data, 
    type: 'form',
    formCode: formSubmitData.formCode,
    formName: formSubmitData.formName,
    schema: formSubmitData.schema
  });
};

const handleFormCancel = () => {
  if (formSubmitted.value || formCancelled.value) return;
  
  formCancelled.value = true;
  emit('form-cancel', { workflowId: props.workflowId });
};

const handleSubmit = () => {
  if (props.pendingInput?.required && !userInputValue.value) {
    return;
  }
  emit('resume', userInputValue.value);
};

const handleConfirm = (value) => {
  emit('resume', value);
};

const logsContainer = ref(null);
const expandedNodes = reactive({});
const allExpanded = ref(false);
const autoScrollEnabled = ref(true);

const hasError = computed(() => {
  return props.nodeExecutionData?.some(node => node.status === 'error') || 
         props.lastResult?.status === 'error';
});

const shouldAutoScroll = () => {
  if (!logsContainer.value) return false;
  const container = logsContainer.value;
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight;
  const clientHeight = container.clientHeight;
  return scrollHeight - scrollTop - clientHeight < 100;
};

const nodeIconMap = {
  'start': '🚀', 'end': '🏁', 'prompt': '💬', 'llm': '🤖',
  'tool': '🔧', 'condition': '🔀', 'loop': '🔄', 'variable': '📦',
  'http': '🌐', 'code': '💻', 'parser': '🔣', 'knowledgeBase': '📚',
  'userInput': '⌨️', 'form': '📋', 'validate': '✅', 'default': '📌'
};

const logTypeLabels = {
  'start': '开始', 'end': '结束', 'success': '成功', 'error': '错误',
  'info': '信息', 'warn': '警告', 'debug': '调试', 'node': '节点',
  'result': '结果', 'default': '日志'
};

const getLogTypeLabel = (type) => {
  return logTypeLabels[type] || logTypeLabels['default'];
};

const toggleExpand = (nodeId) => {
  expandedNodes[nodeId] = !expandedNodes[nodeId];
};

const toggleAllExpand = () => {
  allExpanded.value = !allExpanded.value;
  const newValue = allExpanded.value;
  props.nodeExecutionData?.forEach(node => {
    expandedNodes[node.nodeId] = newValue;
  });
};

watch(() => props.nodeExecutionData, (newData) => {
  if (newData && newData.length > 0) {
    newData.forEach(node => {
      if (expandedNodes[node.nodeId] === undefined) {
        expandedNodes[node.nodeId] = false;
      }
      
      if (node.status === 'error') {
        expandedNodes[node.nodeId] = true;
      }
    });
  }
}, { immediate: true });

const formatJson = (data) => {
  if (!data) return '';
  try { return JSON.stringify(data, null, 2); }
  catch { return String(data); }
};

const formatTimestamp = (ts) => {
  if (!ts) return '--:--:--';
  const d = new Date(ts);
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

const copyToClipboard = async (data) => {
  try {
    let textToCopy = '';
    
    if (typeof data === 'string') {
      textToCopy = data;
    } else if (data) {
      textToCopy = JSON.stringify(data, null, 2);
    } else {
      return;
    }
    
    await navigator.clipboard.writeText(textToCopy);
    
    const message = document.createElement('div');
    message.className = 'copy-toast';
    message.textContent = '✓ 复制成功';
    message.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      padding: 12px 20px;
      background: #10b981;
      color: white;
      border-radius: 6px;
      font-size: 14px;
      z-index: 10000;
      animation: fadeInOut 2s ease-in-out;
    `;
    document.body.appendChild(message);
    setTimeout(() => message.remove(), 2000);
  } catch (error) {
    console.error('复制失败:', error);
  }
};

watch(() => props.logs.length + props.nodeExecutionData.length, async () => {
  await nextTick();
  if (logsContainer.value && shouldAutoScroll()) {
    logsContainer.value.scrollTop = logsContainer.value.scrollHeight;
  }
});
</script>

<style scoped>
.execution-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e6e6e6;
  background: #fafafa;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  gap: 6px;
}

.status-badge.running {
  background: #e6f7ff;
  color: #1890ff;
}

.status-badge.paused {
  background: #fff7e6;
  color: #fa8c16;
}

.status-badge.success {
  background: #f6ffed;
  color: #52c41a;
}

.status-badge.error {
  background: #fff2f0;
  color: #ff4d4f;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.node-count {
  color: #8c8c8c;
  font-size: 12px;
}

.error-badge {
  color: #ff4d4f;
  font-size: 12px;
}

.btn-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #595959;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-action:hover {
  background: #f0f0f0;
  color: #262626;
}

.logs-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #8c8c8c;
  gap: 12px;
}

.empty-logs svg {
  opacity: 0.5;
}

.empty-logs p {
  margin: 0;
  font-size: 14px;
}

.running-hint {
  font-size: 12px;
  color: #bfbfbf;
}

.node-card {
  background: white;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}

.node-card.running {
  border-color: #91d5ff;
}

.node-card.completed {
  border-color: #b7eb8f;
}

.node-card.error {
  border-color: #ffccc7;
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.node-header:hover {
  background: #fafafa;
}

.node-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.expand-icon {
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.step-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #e6e6e6;
  font-size: 11px;
  color: #595959;
}

.node-icon {
  font-size: 16px;
}

.node-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-name {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
}

.node-type {
  font-size: 11px;
  color: #8c8c8c;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.node-duration {
  font-size: 12px;
  color: #8c8c8c;
}

.status-badge-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 11px;
}

.status-badge-sm.running {
  background: #e6f7ff;
  color: #1890ff;
}

.status-badge-sm.completed {
  background: #f6ffed;
  color: #52c41a;
}

.status-badge-sm.error {
  background: #fff2f0;
  color: #ff4d4f;
}

.node-details {
  padding: 0 16px 16px;
  border-top: 1px solid #f0f0f0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0 8px;
  font-size: 13px;
  font-weight: 500;
  color: #595959;
}

.section-icon {
  font-size: 14px;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 4px 8px;
  border: none;
  border-radius: 4px;
  background: #f0f0f0;
  color: #595959;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover {
  background: #d9d9d9;
}

.section-content {
  margin: 0;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
}

.config-content {
  color: #8c8c8c;
}

.input-content {
  color: #1890ff;
}

.output-content {
  color: #52c41a;
}

.error-content {
  color: #ff4d4f;
  background: #fff2f0;
}

.node-error {
  margin-top: 12px;
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  color: #ff4d4f;
  font-size: 13px;
}

.section-header.error {
  color: #ff4d4f;
}

.logs-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.log-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
}

.log-type {
  flex-shrink: 0;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 10px;
}

.log-type.info { background: #e6f7ff; color: #1890ff; }
.log-type.error { background: #fff2f0; color: #ff4d4f; }
.log-type.success { background: #f6ffed; color: #52c41a; }
.log-type.warn { background: #fff7e6; color: #fa8c16; }
.log-type.debug { background: #f0f0f0; color: #8c8c8c; }

.log-content {
  color: #595959;
  word-break: break-all;
}

.result-summary {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-status {
  font-size: 13px;
}

.result-status.success { color: #52c41a; }
.result-status.error { color: #ff4d4f; }

.result-content {
  margin: 0;
  padding: 12px;
  background: white;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}

.form-panel {
  border-top: 2px solid #1890ff;
  background: #fafafa;
  margin-top: auto;
}

.form-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #e6f7ff;
  color: #1890ff;
  font-weight: 500;
}

.form-panel-content {
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.form-message {
  margin: 0 0 16px;
  padding: 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  color: #ad6800;
  font-size: 13px;
}

.user-input-panel {
  border-top: 2px solid #fa8c16;
  background: #fff7e6;
  margin-top: auto;
}

.input-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff7e6;
  color: #fa8c16;
  font-weight: 500;
}

.input-panel-content {
  padding: 16px;
}

.prompt-text {
  margin-bottom: 12px;
  font-size: 14px;
  color: #595959;
}

.validation-error-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  color: #cf1322;
  font-size: 13px;
  line-height: 1.5;
}

.validation-error-banner svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: #ff4d4f;
}

.input-field {
  margin-bottom: 12px;
}

.text-input,
.select-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  resize: vertical;
}

.text-input:focus,
.select-input:focus {
  outline: none;
  border-color: #fa8c16;
  box-shadow: 0 0 0 2px rgba(250, 140, 22, 0.1);
}

.confirm-options {
  display: flex;
  gap: 12px;
}

.confirm-btn {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-yes {
  background: #52c41a;
  color: white;
}

.confirm-yes:hover {
  background: #73d13d;
}

.confirm-no {
  background: #ff4d4f;
  color: white;
}

.confirm-no:hover {
  background: #ff7875;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

.submit-btn {
  padding: 10px 24px;
  border: none;
  border-radius: 4px;
  background: #fa8c16;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #fa8c16;
}

.submit-btn:disabled {
  background: #d9d9d9;
  cursor: not-allowed;
}
</style>

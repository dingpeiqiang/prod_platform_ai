<template>
  <div class="execution-panel">
    <div class="panel-header">
      <div class="header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
        <span>执行日志</span>
        <span v-if="isPaused" class="status-badge paused">
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
      <div v-if="nodeExecutionData.length === 0" class="empty-logs">
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

          <!-- 展开的详细信息 -->
          <div v-if="expandedNodes[nodeData.nodeId]" class="node-details">
            <!-- 节点配置信息 -->
            <div v-if="nodeData.config && Object.keys(nodeData.config).length > 0" class="node-config">
              <div class="section-header">
                <span class="section-icon">⚙️</span>
                <span>配置</span>
              </div>
              <pre class="section-content config-content">{{ formatJson(nodeData.config) }}</pre>
            </div>

            <!-- 输入数据 -->
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

            <!-- 执行日志 -->
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

            <!-- 错误信息 -->
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

            <!-- 输出数据 -->
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

    <div v-if="isPaused && pendingInput" class="user-input-panel">
      <div class="input-panel-header">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>等待用户输入</span>
      </div>
      
      <div class="input-panel-content">
        <div class="prompt-text">{{ pendingInput.prompt }}</div>
        
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

const props = defineProps({
  logs: { type: Array, default: () => [] },
  isRunning: { type: Boolean, default: false },
  isPaused: { type: Boolean, default: false },
  pendingInput: { type: Object, default: null },
  lastResult: { type: Object, default: null },
  history: { type: Array, default: () => [] },
  nodeExecutionData: { type: Array, default: () => [] }
});

const emit = defineEmits(['clear', 'resume']);

const userInputValue = ref('');

watch(() => props.pendingInput, (newInput) => {
  if (newInput) {
    userInputValue.value = '';
  }
});

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
const allExpanded = ref(false);  // 默认全部折叠
const autoScrollEnabled = ref(true);  // 自动滚动开关

const hasError = computed(() => {
  return nodeExecutionData.value?.some(node => node.status === 'error') || 
         lastResult.value?.status === 'error';
});

const shouldAutoScroll = () => {
  if (!logsContainer.value) return false;
  const container = logsContainer.value;
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight;
  const clientHeight = container.clientHeight;
  // 如果滚动条接近底部（100像素内），允许自动滚动
  return scrollHeight - scrollTop - clientHeight < 100;
};

const nodeIconMap = {
  'start': '🚀', 'end': '🏁', 'prompt': '💬', 'llm': '🤖',
  'tool': '🔧', 'condition': '🔀', 'loop': '🔄', 'variable': '📦',
  'http': '🌐', 'code': '💻', 'parser': '🔣', 'knowledgeBase': '📚',
  'userInput': '⌨️', 'form': '📋', 'validate': '✅', 'default': '📌'
};

const logTypeLabels = {
  'start': '开始',
  'end': '结束',
  'success': '成功',
  'error': '错误',
  'info': '信息',
  'warn': '警告',
  'debug': '调试',
  'node': '节点',
  'result': '结果',
  'default': '日志'
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
  nodeExecutionData.value?.forEach(node => {
    expandedNodes[node.nodeId] = newValue;
  });
};

// 监听节点执行数据变化，默认全部折叠
watch(() => props.nodeExecutionData, (newData) => {
  if (newData && newData.length > 0) {
    // 新数据到达时，默认全部折叠，用户可自行展开
    newData.forEach(node => {
      if (expandedNodes[node.nodeId] === undefined) {
        expandedNodes[node.nodeId] = false;
      }
      
      // 如果节点执行失败，自动展开显示错误
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
    
    // 使用原生的提示方式，避免依赖 Element Plus
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
  background-color: #0f172a;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #e2e8f0;
  font-size: 14px;
  font-weight: 500;
}

.node-count {
  font-size: 12px;
  color: #64748b;
  background: #334155;
  padding: 2px 8px;
  border-radius: 10px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.running { background: rgba(245,158,11,.2); color: #f59e0b; }
.status-badge.success { background: rgba(16,185,129,.2); color: #10b981; }
.status-badge.error { background: rgba(239,68,68,.2); color: #ef4444; }

.status-dot {
  width: 6px; height: 6px; background: #f59e0b; border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }

.btn-action {
  background: none; border: none; color: #94a3b8; cursor: pointer;
  padding: 5px; border-radius: 4px;
}

.btn-action:hover { background: #334155; color: #e2e8f0; }

.header-right {
  display: flex;
  gap: 4px;
}

.logs-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #64748b;
}

.empty-logs svg { opacity: 0.5; }
.empty-logs p { margin-top: 12px; font-size: 14px; }
.running-hint { font-size: 12px; color: #f59e0b; margin-top: 6px; }

.node-card {
  margin-bottom: 12px;
  padding: 0;
  background: #1e293b;
  border-radius: 8px;
  border-left: 4px solid #64748b;
  overflow: hidden;
}

.node-card.completed { border-left-color: #10b981; }
.node-card.error { border-left-color: #ef4444; }
.node-card.running { border-left-color: #f59e0b; }

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.node-header:hover {
  background: rgba(255, 255, 255, 0.03);
}

.node-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
  color: #64748b;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.step-number {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #334155;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  color: #e2e8f0;
}

.node-icon {
  font-size: 18px;
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.node-type {
  font-size: 11px;
  color: #64748b;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.node-time {
  font-size: 11px;
  color: #64748b;
}

.node-duration {
  font-size: 11px;
  color: #94a3b8;
  background: #0f172a;
  padding: 2px 8px;
  border-radius: 8px;
}

.status-badge-sm {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 8px;
}

.status-badge-sm.completed { background: rgba(16,185,129,.2); color: #10b981; }
.status-badge-sm.error { background: rgba(239,68,68,.2); color: #ef4444; }
.status-badge-sm.running { background: rgba(245,158,11,.2); color: #f59e0b; }

.node-details {
  padding: 0 14px 14px;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Section styles */
.node-config,
.node-input,
.node-output {
  margin-top: 10px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 6px;
}

.section-header.error {
  color: #ef4444;
}

.section-header .copy-btn {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: rgba(148,163,184,.1);
  border: 1px solid rgba(148,163,184,.2);
  border-radius: 4px;
  color: #94a3b8;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.section-header .copy-btn:hover {
  background: rgba(148,163,184,.2);
  color: #e2e8f0;
}

.copy-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(148,163,184,.1);
  border: 1px solid rgba(148,163,184,.2);
  border-radius: 4px;
  color: #94a3b8;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover {
  background: rgba(148,163,184,.2);
  color: #e2e8f0;
}

.section-icon {
  font-size: 12px;
}

.section-content {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  margin: 0;
  padding: 10px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow-y: auto;
}

.config-content {
  background: rgba(148,163,184,.05);
  color: #94a3b8;
  border: 1px solid rgba(148,163,184,.1);
}

.input-content {
  background: rgba(59,130,246,.05);
  color: #93c5fd;
  border: 1px solid rgba(59,130,246,.2);
}

.output-content {
  background: rgba(16,185,129,.05);
  color: #86efac;
  border: 1px solid rgba(16,185,129,.2);
}

/* Logs list */
.node-logs {
  margin-top: 10px;
}

.logs-list {
  background: rgba(0,0,0,.2);
  border-radius: 6px;
  border: 1px solid rgba(148,163,184,.1);
}

.log-row {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px dashed rgba(148,163,184,.1);
}

.log-row:last-child {
  border-bottom: none;
}

.log-type {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  min-width: 40px;
  text-align: center;
  flex-shrink: 0;
}

.log-type.info { background: rgba(59,130,246,.2); color: #60a5fa; }
.log-type.warn { background: rgba(245,158,11,.2); color: #f59e0b; }
.log-type.error { background: rgba(239,68,68,.2); color: #ef4444; }
.log-type.debug { background: rgba(139,92,246,.2); color: #a78bfa; }
.log-type.result { background: rgba(16,185,129,.2); color: #10b981; }
.log-type.default { background: rgba(148,163,184,.2); color: #94a3b8; }

.log-content {
  font-size: 12px;
  color: #cbd5e1;
  flex: 1;
  word-break: break-all;
}

/* Error */
.node-error {
  margin-top: 10px;
}

.error-content {
  background: rgba(239,68,68,.1);
  color: #fca5a5;
  border: 1px solid rgba(239,68,68,.3);
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  margin: 0;
  padding: 10px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow-y: auto;
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(239,68,68,.15);
  border: 1px solid rgba(239,68,68,.4);
  border-radius: 6px;
  color: #fca5a5;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
}

.error-alert svg {
  color: #ef4444;
  flex-shrink: 0;
}

.error-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: rgba(239,68,68,.2);
  border: 1px solid rgba(239,68,68,.4);
  border-radius: 12px;
  color: #fca5a5;
  font-size: 11px;
  font-weight: 600;
}

.node-card.has-error {
  border-left: 3px solid #ef4444;
  background: rgba(239,68,68,.05);
}

.node-card.has-error .node-header {
  background: rgba(239,68,68,.1);
}

/* Result summary */
.result-summary {
  background: #1e293b;
  border-top: 1px solid #334155;
  padding: 12px 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-header > span {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-status {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
}

.result-status.success { background: rgba(16,185,129,.2); color: #10b981; }
.result-status.error { background: rgba(239,68,68,.2); color: #ef4444; }

.result-content {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  color: #e2e8f0;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
}

.user-input-panel {
  background: #1e293b;
  border-top: 1px solid #334155;
  padding: 12px 16px;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.input-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #f59e0b;
  margin-bottom: 12px;
}

.input-panel-content {
  background: #0f172a;
  border-radius: 8px;
  padding: 16px;
}

.prompt-text {
  font-size: 13px;
  color: #e2e8f0;
  margin-bottom: 12px;
  line-height: 1.5;
}

.input-field {
  margin-bottom: 16px;
}

.text-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #334155;
  border-radius: 6px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.text-input:focus {
  outline: none;
  border-color: #f97316;
}

.text-input::placeholder {
  color: #64748b;
}

.select-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #334155;
  border-radius: 6px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 13px;
  box-sizing: border-box;
  transition: border-color 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 10px center;
  background-repeat: no-repeat;
  background-size: 14px;
  padding-right: 32px;
}

.select-input:focus {
  outline: none;
  border-color: #f97316;
}

.confirm-options {
  display: flex;
  gap: 12px;
}

.confirm-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-yes {
  background: #10b981;
  color: white;
}

.confirm-yes:hover {
  background: #059669;
}

.confirm-no {
  background: #64748b;
  color: white;
}

.confirm-no:hover {
  background: #475569;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

.submit-btn {
  padding: 10px 24px;
  background: #f97316;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #ea580c;
}

.submit-btn:disabled {
  background: #475569;
  cursor: not-allowed;
}

.status-badge.paused { background: rgba(249,115,22,.2); color: #f97316; }

::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: #0f172a; }
::-webkit-scrollbar-thumb { background: #475569; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #64748b; }
</style>

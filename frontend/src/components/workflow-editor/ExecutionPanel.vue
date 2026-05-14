<template>
  <div class="execution-panel">
    <!-- 面板头部 -->
    <div class="panel-header">
      <div class="header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
        <span>执行日志</span>
        <span class="log-count">{{ visibleLogs.length }}</span>
        <span v-if="isRunning" class="status-badge running">
          <span class="status-dot"></span>
          运行中
        </span>
        <span v-else-if="lastResult" class="status-badge" :class="lastResult.status">
          {{ lastResult.status === 'success' ? '✓ 成功' : '✗ 失败' }}
        </span>
      </div>
      <div class="header-right">
        <button 
          v-if="logs.length > 0" 
          @click="$emit('clear')" 
          class="btn-action" 
          title="清空日志"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18"/>
            <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
            <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          </svg>
        </button>
      </div>
    </div>
    
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="search-box">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="搜索日志..."
          class="search-input"
        />
      </div>
      <div class="filter-tags">
        <button 
          v-for="tag in filterTags" 
          :key="tag.value"
          @click="toggleFilter(tag.value)"
          :class="['filter-tag', { active: activeFilters.includes(tag.value), [tag.value]: true }]"
        >
          {{ tag.label }}
        </button>
      </div>
    </div>
    
    <!-- 日志列表 -->
    <div class="logs-container" ref="logsContainer">
      <div v-if="visibleLogs.length === 0" class="empty-logs">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
          <line x1="10" y1="9" x2="8" y2="9"/>
        </svg>
        <p>{{ searchQuery ? '未找到匹配的日志' : '暂无执行日志' }}</p>
      </div>
      
      <div v-else>
        <div 
          v-for="(log, index) in visibleLogs" 
          :key="index" 
          class="log-item"
          :class="log.type"
          @click="toggleLogExpand(index)"
        >
          <div class="log-icon">
            <span v-if="log.type === 'start'">🚀</span>
            <span v-else-if="log.type === 'success'">✓</span>
            <span v-else-if="log.type === 'error'">✗</span>
            <span v-else-if="log.type === 'info'">📝</span>
            <span v-else-if="log.type === 'node'">🔹</span>
            <span v-else>●</span>
          </div>
          <div class="log-main">
            <div class="log-header">
              <span class="log-title">{{ log.title }}</span>
              <span class="log-time">{{ log.time }}</span>
            </div>
            <div v-if="log.message" class="log-message">{{ log.message }}</div>
            <div v-if="log.data && expandedLogs.includes(index)" class="log-data">
              <pre>{{ formatJson(log.data) }}</pre>
            </div>
            <div v-if="log.data" class="expand-hint" :class="{ expanded: expandedLogs.includes(index) }">
              {{ expandedLogs.includes(index) ? '点击收起数据' : '点击查看详情' }}
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 执行结果 -->
    <div v-if="lastResult" class="result-section">
      <div class="result-header">
        <h4>执行结果</h4>
        <button @click="copyResult" class="btn-copy" title="复制结果">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
        </button>
      </div>
      <div class="result-content">
        <pre>{{ formatJson(lastResult) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue';

const props = defineProps({
  logs: {
    type: Array,
    default: () => []
  },
  isRunning: {
    type: Boolean,
    default: false
  },
  lastResult: {
    type: Object,
    default: null
  }
});

defineEmits(['clear']);

const searchQuery = ref('');
const activeFilters = ref(['all']);
const expandedLogs = ref([]);
const logsContainer = ref(null);

const filterTags = [
  { value: 'all', label: '全部' },
  { value: 'node', label: '节点' },
  { value: 'info', label: '信息' },
  { value: 'start', label: '开始' },
  { value: 'success', label: '成功' },
  { value: 'error', label: '错误' }
];

const visibleLogs = computed(() => {
  let result = props.logs;
  
  if (activeFilters.value.length === 0 || !activeFilters.value.includes('all')) {
    result = result.filter(log => activeFilters.value.includes(log.type));
  }
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(log => 
      log.title.toLowerCase().includes(query) ||
      (log.message && log.message.toLowerCase().includes(query))
    );
  }
  
  return result;
});

const toggleFilter = (filter) => {
  if (filter === 'all') {
    activeFilters.value = ['all'];
  } else {
    activeFilters.value = activeFilters.value.includes(filter)
      ? activeFilters.value.filter(f => f !== filter)
      : [...activeFilters.value.filter(f => f !== 'all'), filter];
    if (activeFilters.value.length === 0) {
      activeFilters.value = ['all'];
    }
  }
};

const toggleLogExpand = (index) => {
  const idx = expandedLogs.value.indexOf(index);
  if (idx > -1) {
    expandedLogs.value.splice(idx, 1);
  } else {
    expandedLogs.value.push(index);
  }
};

const copyResult = async () => {
  if (props.lastResult) {
    try {
      await navigator.clipboard.writeText(formatJson(props.lastResult));
      alert('已复制到剪贴板');
    } catch (err) {
      console.error('复制失败:', err);
    }
  }
};

const formatJson = (data) => {
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
};

watch(() => props.logs.length, async () => {
  await nextTick();
  if (logsContainer.value) {
    logsContainer.value.scrollTop = logsContainer.value.scrollHeight;
  }
});
</script>

<style scoped>
.execution-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #1e293b;
}

.execution-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background-color: #0f172a;
  border-bottom: 1px solid #334155;
}

.execution-panel .header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.execution-panel .header-left span {
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}

.log-count {
  background-color: #334155;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  color: #94a3b8;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.status-badge.running {
  background-color: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
}

.status-dot {
  width: 6px;
  height: 6px;
  background-color: #f59e0b;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-badge.success {
  background-color: rgba(16, 185, 129, 0.2);
  color: #10b981;
}

.status-badge.error {
  background-color: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.execution-panel .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-action {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.btn-action:hover {
  background-color: #334155;
  color: #e2e8f0;
}

.execution-panel .toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  gap: 12px;
  background-color: #1e293b;
  border-bottom: 1px solid #334155;
}

.search-box {
  display: flex;
  align-items: center;
  background-color: #0f172a;
  border-radius: 6px;
  padding: 4px 8px;
  flex: 1;
}

.search-box svg {
  color: #64748b;
}

.search-input {
  background: none;
  border: none;
  color: #e2e8f0;
  font-size: 12px;
  padding: 4px 6px;
  outline: none;
  flex: 1;
}

.search-input::placeholder {
  color: #64748b;
}

.filter-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.filter-tag {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  background-color: #0f172a;
  color: #94a3b8;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-tag:hover {
  background-color: #334155;
}

.filter-tag.active {
  background-color: #3b82f6;
  color: white;
}

.filter-tag.node.active {
  background-color: #f59e0b;
}

.filter-tag.info.active {
  background-color: #64748b;
}

.filter-tag.start.active {
  background-color: #3b82f6;
}

.filter-tag.success.active {
  background-color: #10b981;
}

.filter-tag.error.active {
  background-color: #ef4444;
}

.logs-container {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #64748b;
}

.empty-logs p {
  margin-top: 8px;
  font-size: 12px;
}

.log-item {
  display: flex;
  gap: 10px;
  padding: 8px;
  margin-bottom: 4px;
  background-color: #0f172a;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.log-item:hover {
  background-color: #1e293b;
}

.log-item.start {
  border-left: 3px solid #3b82f6;
}

.log-item.success {
  border-left: 3px solid #10b981;
}

.log-item.error {
  border-left: 3px solid #ef4444;
}

.log-item.info {
  border-left: 3px solid #64748b;
}

.log-item.node {
  border-left: 3px solid #f59e0b;
}

.log-icon {
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}

.log-main {
  flex: 1;
  min-width: 0;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.log-title {
  font-size: 12px;
  font-weight: 500;
  color: #e2e8f0;
}

.log-time {
  font-size: 10px;
  color: #64748b;
  flex-shrink: 0;
}

.log-message {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  word-break: break-all;
}

.log-data {
  margin-top: 8px;
  padding: 8px;
  background-color: #1e293b;
  border-radius: 4px;
  overflow-x: auto;
}

.log-data pre {
  font-size: 11px;
  color: #94a3b8;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: 'Monaco', 'Menlo', monospace;
}

.expand-hint {
  font-size: 10px;
  color: #64748b;
  margin-top: 4px;
  font-style: italic;
}

.expand-hint.expanded {
  color: #3b82f6;
}

.result-section {
  background-color: #0f172a;
  border-top: 1px solid #334155;
  padding: 10px 12px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-section h4 {
  font-size: 11px;
  color: #94a3b8;
  margin: 0;
  text-transform: uppercase;
}

.btn-copy {
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.btn-copy:hover {
  background-color: #334155;
  color: #e2e8f0;
}

.result-content {
  max-height: 100px;
  overflow-y: auto;
}

.result-content pre {
  font-size: 11px;
  color: #e2e8f0;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Monaco', 'Menlo', monospace;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #0f172a;
}

::-webkit-scrollbar-thumb {
  background: #475569;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>
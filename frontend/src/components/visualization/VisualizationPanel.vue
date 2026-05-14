<template>
  <div class="visualization-panel">
    <div class="panel-header">
      <h2>工作流可视化</h2>
      <div class="header-actions">
        <button @click="refreshTraces" class="refresh-btn">
          <span>刷新</span>
        </button>
      </div>
    </div>
    
    <div class="panel-body">
      <div class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['tab-btn', { active: activeTab === tab.id }]"
          @click="activeTab = tab.id"
        >
          {{ tab.name }}
        </button>
      </div>
      
      <div class="tab-content">
        <div v-if="activeTab === 'traces'" class="tab-pane">
          <div v-if="loading" class="loading">加载中...</div>
          <div v-else-if="traces.length === 0" class="empty-state">
            <div class="empty-icon">📊</div>
            <p>暂无追踪记录</p>
            <p class="hint">执行工作流后将在这里显示追踪数据</p>
          </div>
          <div v-else class="traces-list">
            <div
              v-for="trace in traces"
              :key="trace.trace_id"
              class="trace-item"
              :class="{ selected: selectedTraceId === trace.trace_id }"
              @click="selectTrace(trace)"
            >
              <div class="trace-header">
                <span class="trace-id">{{ truncate(trace.trace_id, 20) }}</span>
                <span :class="['status-badge', getStatusClass(trace)]">
                  {{ getStatusText(trace) }}
                </span>
              </div>
              <div class="trace-meta">
                <span class="meta-item">
                  <span class="icon">⏱️</span>
                  {{ formatDuration(trace.total_duration_ms) }}
                </span>
                <span class="meta-item">
                  <span class="icon">📦</span>
                  {{ trace.span_count }} 个节点
                </span>
                <span class="meta-item">
                  <span class="icon">🕐</span>
                  {{ formatTime(trace.start_time) }}
                </span>
              </div>
            </div>
          </div>
        </div>
        
        <div v-if="activeTab === 'flow'" class="tab-pane flow-tab">
          <div v-if="!selectedTrace">
            <div class="empty-state">
              <div class="empty-icon">🔍</div>
              <p>请先选择一条追踪记录</p>
            </div>
          </div>
          <div v-else>
            <FlowDiagram
              :nodes="flowData.nodes"
              :edges="flowData.edges"
              :total-duration="selectedTrace.total_duration_ms"
            />
          </div>
        </div>
        
        <div v-if="activeTab === 'timeline'" class="tab-pane timeline-tab">
          <div v-if="!selectedTrace">
            <div class="empty-state">
              <div class="empty-icon">⏰</div>
              <p>请先选择一条追踪记录</p>
            </div>
          </div>
          <div v-else>
            <ExecutionTimeline
              :spans="selectedTrace.spans"
              :total-duration="selectedTrace.total_duration_ms"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { visualizationApi } from '../../services/visualizationApi';
import FlowDiagram from './FlowDiagram.vue';
import ExecutionTimeline from './ExecutionTimeline.vue';

const tabs = [
  { id: 'traces', name: '追踪列表' },
  { id: 'flow', name: '流程图' },
  { id: 'timeline', name: '时间线' }
];

const activeTab = ref('traces');
const traces = ref([]);
const loading = ref(false);
const selectedTraceId = ref(null);

const selectedTrace = computed(() => {
  return traces.value.find(t => t.trace_id === selectedTraceId.value);
});

const flowData = computed(() => {
  if (!selectedTrace.value) return { nodes: [], edges: [] };
  return {
    nodes: selectedTrace.value.spans.map(span => ({
      id: span.span_id,
      name: span.name,
      status: span.status,
      duration_ms: span.duration_ms,
      start_time: span.start_time,
      end_time: span.end_time,
      component: span.component,
      tags: span.tags
    })),
    edges: selectedTrace.value.spans
      .filter(span => span.parent_span_id)
      .map(span => ({
        from: span.parent_span_id,
        to: span.span_id
      }))
  };
});

async function refreshTraces() {
  loading.value = true;
  try {
    const response = await visualizationApi.getTraces(20);
    if (response.success) {
      traces.value = response.data;
    }
  } catch (error) {
    console.error('Failed to fetch traces:', error);
  } finally {
    loading.value = false;
  }
}

function selectTrace(trace) {
  selectedTraceId.value = trace.trace_id;
}

function truncate(str, length) {
  if (str.length <= length) return str;
  return str.substring(0, length) + '...';
}

function getStatusClass(trace) {
  const errorCount = trace.spans?.filter(s => s.status === 'error').length || 0;
  return errorCount > 0 ? 'error' : 'success';
}

function getStatusText(trace) {
  const errorCount = trace.spans?.filter(s => s.status === 'error').length || 0;
  return errorCount > 0 ? '有错误' : '正常';
}

function formatDuration(ms) {
  if (!ms || ms < 0) return '0ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}m`;
}

function formatTime(timeStr) {
  if (!timeStr) return '-';
  const date = new Date(timeStr);
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

onMounted(() => {
  refreshTraces();
});
</script>

<style scoped>
.visualization-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.panel-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f3f4f6;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.refresh-btn:hover {
  background: #e5e7eb;
}

.panel-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
}

.tab-btn {
  padding: 12px 20px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  background: #f9fafb;
}

.tab-btn.active {
  border-bottom-color: #3b82f6;
  color: #3b82f6;
  font-weight: 500;
}

.tab-content {
  flex: 1;
  overflow: auto;
}

.tab-pane {
  padding: 16px;
  height: calc(100% - 32px);
}

.loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #6b7280;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0;
  color: #6b7280;
}

.empty-state .hint {
  font-size: 13px;
  color: #9ca3af;
}

.traces-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trace-item {
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.trace-item:hover {
  background: #f3f4f6;
}

.trace-item.selected {
  border-color: #3b82f6;
  background: #eff6ff;
}

.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.trace-id {
  font-family: monospace;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.status-badge.success {
  background: #dcfce7;
  color: #16a34a;
}

.status-badge.error {
  background: #fee2e2;
  color: #dc2626;
}

.trace-meta {
  display: flex;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
}

.meta-item .icon {
  font-size: 14px;
}

.flow-tab, .timeline-tab {
  height: calc(100% - 32px);
}
</style>

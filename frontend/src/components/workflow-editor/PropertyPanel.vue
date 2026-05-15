<template>
  <div class="properties-panel" :class="{ collapsed: !expanded }">
    <div class="panel-header">
      <h3>属性</h3>
      <button @click="$emit('toggle')" class="panel-close-btn">✕</button>
    </div>
    <div class="panel-content">
      <div v-if="nodeData" class="properties-content">
        <div class="property-section">
          <h4>基本信息</h4>
          <div class="property-group">
            <label>节点名称</label>
            <input
              :value="nodeData.label"
              @input="onLabelChange"
              type="text"
              class="property-input"
            />
          </div>
          <div class="property-group">
            <label>节点类型</label>
            <span class="property-value">{{ nodeTypeLabel }}</span>
          </div>
          <div class="property-group">
            <label>节点ID</label>
            <span class="property-value mono">{{ nodeData.id }}</span>
          </div>
        </div>

        <div class="property-section">
          <h4>通用配置</h4>
          <div class="property-group">
            <label>超时时间</label>
            <input
              v-model.number="nodeData.timeout"
              type="number"
              min="1"
              max="300"
              class="property-input"
              placeholder="30"
            />
            <span class="property-unit">秒</span>
          </div>
          <label class="checkbox-label">
            <input v-model="nodeData.ignoreError" type="checkbox" />
            <span>忽略错误继续执行</span>
          </label>
          <div class="property-group">
            <label>重试次数</label>
            <input
              v-model.number="nodeData.retryCount"
              type="number"
              min="0"
              max="10"
              class="property-input"
              placeholder="0"
            />
          </div>
        </div>

        <div class="property-section">
          <h4>执行信息</h4>
          <div class="property-group">
            <label>执行状态</label>
            <span :class="['property-value', executionStatus?.toLowerCase()]">
              {{ executionStatus || '未执行' }}
            </span>
          </div>
          <div class="property-group">
            <label>执行时间</label>
            <span class="property-value">{{ executionTime || '-' }}</span>
          </div>
        </div>
      </div>
      <div v-else class="empty-properties">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="6"/>
          <circle cx="12" cy="12" r="2"/>
        </svg>
        <p>选择一个节点查看属性</p>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  nodeData: { type: Object, default: null },
  expanded: { type: Boolean, default: true },
  nodeTypeLabel: { type: String, default: '' },
  executionStatus: { type: String, default: '' },
  executionTime: { type: String, default: '' }
});

const emit = defineEmits(['toggle', 'update-label']);

const onLabelChange = (event) => {
  if (props.nodeData) {
    emit('update-label', props.nodeData.id, event.target.value);
  }
};
</script>

<style scoped>
.properties-panel {
  width: 260px;
  background-color: #f8fafc;
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;
}

.properties-panel.collapsed {
  width: 0;
  overflow: hidden;
  border-left: none;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.panel-header h3 {
  margin: 0;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.panel-close-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.panel-close-btn:hover {
  background-color: #e2e8f0;
}

.panel-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.property-section {
  margin-bottom: 16px;
}

.property-section h4 {
  margin: 0 0 8px 0;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

.property-group {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.property-group label {
  font-size: 11px;
  color: #64748b;
  min-width: 60px;
}

.property-input {
  flex: 1;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.property-value {
  font-size: 12px;
  color: #334155;
  flex: 1;
}

.property-value.mono {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  background-color: #0f172a;
  color: #e2e8f0;
  padding: 2px 6px;
  border-radius: 3px;
}

.property-value.running {
  color: #f59e0b;
}

.property-value.completed {
  color: #10b981;
}

.property-value.error {
  color: #ef4444;
}

.property-unit {
  font-size: 11px;
  color: #64748b;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
}

.checkbox-label input {
  width: 14px;
  height: 14px;
}

.empty-properties {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.empty-properties p {
  margin-top: 8px;
  font-size: 12px;
}
</style>

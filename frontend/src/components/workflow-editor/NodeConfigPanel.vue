<template>
  <div class="node-config-panel">
    <div class="panel-header">
      <div class="header-info">
        <span class="header-icon">{{ headerIcon }}</span>
        <div>
          <h3>{{ nodeTypeLabel || '节点配置' }}</h3>
          <p v-if="nodeData?.label" class="header-subtitle">{{ nodeData.label }}</p>
        </div>
      </div>
      <button type="button" class="panel-close-btn" title="关闭 (Esc)" @click="$emit('close')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <div class="panel-scroll">
      <PropertyPanel
        embedded
        :node-data="nodeData"
        :node-type-label="nodeTypeLabel"
        :execution-status="executionStatus"
        :execution-time="executionTime"
        @update-label="(id, label) => $emit('update-label', id, label)"
        @update="(payload) => $emit('update', payload)"
      />

      <div v-if="configComponent && nodeData" class="node-config-section">
        <h4 class="section-title">{{ configTitle }}</h4>
        <component
          :is="configComponent"
          :data="nodeData"
          :selected="true"
          config-mode
          @update="(nodeId, data) => $emit('node-update', nodeId, data)"
        />
      </div>

      <div v-else-if="!nodeData" class="empty-config">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <path d="M9 3v18"/>
        </svg>
        <p>双击画布上的节点打开配置</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import PropertyPanel from './PropertyPanel.vue';
import { NODE_CONFIG_COMPONENTS, NODE_TYPE_LABELS } from './nodeConfigRegistry.js';

const props = defineProps({
  node: { type: Object, default: null },
  executionStatus: { type: String, default: '' },
  executionTime: { type: String, default: '' }
});

const nodeData = computed(() => {
  if (!props.node) return null;
  return { ...props.node.data, id: props.node.id, type: props.node.type };
});

const nodeType = computed(() => props.node?.type || '');

defineEmits(['close', 'update', 'update-label', 'node-update']);

const nodeTypeLabel = computed(() => NODE_TYPE_LABELS[nodeType.value] || nodeType.value);

const configComponent = computed(() => NODE_CONFIG_COMPONENTS[nodeType.value] || null);

const headerIcon = computed(() => {
  const icons = {
    start: '🚀',
    end: '🏁',
    prompt: '📝',
    llm: '🤖',
    tool: '🔧',
    condition: '🔀',
    loop: '🔁',
    variable: '📊',
    http: '🌐',
    code: '💻',
    parser: '📋',
    form: '📄'
  };
  return icons[nodeType.value] || '⚙️';
});

const configTitle = computed(() => {
  const titles = {
    start: '输入参数',
    end: '输出配置',
    prompt: '提示词配置',
    llm: '模型配置',
    tool: '工具配置',
    condition: '条件配置',
    loop: '循环配置',
    variable: '变量配置',
    http: 'HTTP配置',
    code: '代码配置',
    parser: '解析配置',
    form: '表单配置'
  };
  return titles[nodeType.value] || '配置';
});
</script>

<style scoped>
.node-config-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  animation: panelFadeIn 0.25s ease-out;
}

@keyframes panelFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
  animation: headerSlideIn 0.3s ease-out;
}

@keyframes headerSlideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-icon {
  font-size: 22px;
  flex-shrink: 0;
}

.panel-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.header-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: #f1f5f9;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s, color 0.2s;
}

.panel-close-btn:hover {
  background: #e2e8f0;
  color: #334155;
}

.panel-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.node-config-section {
  padding: 0 12px 16px;
}

.section-title {
  margin: 0 0 10px;
  padding: 0 4px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.node-config-section :deep(.node) {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border: 1px solid #e2e8f0;
}

.node-config-section :deep(.node-header) {
  display: none;
}

.empty-config {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #94a3b8;
  text-align: center;
}

.empty-config p {
  margin-top: 12px;
  font-size: 13px;
}
</style>

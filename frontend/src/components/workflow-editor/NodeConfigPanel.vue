<template>
  <div class="node-config-panel">
    <div class="panel-header">
      <h2 class="panel-title">编辑工作流</h2>
      <div class="header-actions">
        <button type="button" class="action-btn run-btn" title="运行 / 测试节点" @click="$emit('run')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
        </button>
        <button type="button" class="action-btn close-btn" title="关闭 (Esc)" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    
    <!-- 节点信息区(根据节点类型动态显示) -->
    <NodeInfoSection v-if="nodeData" :node-type="nodeType" :node-type-label="nodeTypeLabel" />

    <!-- 可滚动内容区 -->
    <div class="panel-scroll">
      <!-- 通用属性面板 -->
      <PropertyPanel
        embedded
        :node-data="nodeData"
        :node-type-label="nodeTypeLabel"
        :execution-status="executionStatus"
        :execution-time="executionTime"
        @update-label="(id, label) => $emit('update-label', id, label)"
        @update="(payload) => $emit('update', payload)"
      />

      <!-- 节点特定配置组件 -->
      <div v-if="configComponent && nodeData" class="node-config-section">
        <h4 class="section-title">{{ configTitle }}</h4>
        <component
          :is="configComponent"
          :data="nodeData"
          :selected="true"
          config-mode
          @update="(nodeId, data) => $emit('node-update', nodeId, data)"
          @close="$emit('close')"
          @run="$emit('run')"
        />
      </div>

      <!-- 空状态 -->
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
import NodeInfoSection from './NodeInfoSection.vue';
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

defineEmits(['close', 'update', 'update-label', 'node-update', 'run']);

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
    form: '📄',
    knowledgeBase: '📚'
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
    form: '表单配置',
    knowledgeBase: '知识库配置'
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
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
  background: #fff;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #000;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f1f5f9;
  color: #334155;
}

.run-btn:hover {
  background: #dcfce7;
  color: #16a34a;
}

.close-btn:hover {
  background: #fef2f2;
  color: #dc2626;
}

.panel-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.panel-scroll::-webkit-scrollbar {
  width: 6px;
}

.panel-scroll::-webkit-scrollbar-track {
  background: #f5f5f5;
}

.panel-scroll::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.panel-scroll::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
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

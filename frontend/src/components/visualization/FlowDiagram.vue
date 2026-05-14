<template>
  <div class="flow-diagram">
    <div class="diagram-header">
      <h3>工作流执行图</h3>
      <div class="duration">
        总耗时: <span :class="{ warning: totalDuration > 5000 }">{{ formatDuration(totalDuration) }}</span>
      </div>
    </div>
    
    <div class="diagram-container" ref="diagramContainer">
      <svg :width="svgWidth" :height="svgHeight">
        <g class="edges">
          <line
            v-for="(edge, index) in edges"
            :key="'edge-' + index"
            :x1="getNodePosition(edge.from).x + 100"
            :y1="getNodePosition(edge.from).y + 40"
            :x2="getNodePosition(edge.to).x"
            :y2="getNodePosition(edge.to).y + 40"
            :stroke="getEdgeColor(edge)"
            stroke-width="2"
            marker-end="url(#arrowhead)"
          />
        </g>
        
        <defs>
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="9"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="#6b7280" />
          </marker>
        </defs>
        
        <g
          v-for="node in nodes"
          :key="node.id"
          :transform="`translate(${getNodePosition(node.id).x}, ${getNodePosition(node.id).y})`"
          class="node-group"
          @click="selectNode(node)"
        >
          <rect
            x="0"
            y="0"
            width="200"
            height="80"
            rx="8"
            :fill="getNodeBgColor(node.status)"
            :stroke="getNodeBorderColor(node.status)"
            stroke-width="2"
            class="node-rect"
          />
          
          <text
            x="100"
            y="35"
            text-anchor="middle"
            class="node-name"
          >{{ node.name }}</text>
          
          <text
            x="100"
            y="58"
            text-anchor="middle"
            class="node-status"
            :class="node.status"
          >{{ getStatusText(node.status) }} · {{ formatDuration(node.duration_ms) }}</text>
        </g>
      </svg>
    </div>
    
    <div v-if="selectedNode" class="node-detail">
      <h4>{{ selectedNode.name }}</h4>
      <div class="detail-row">
        <span class="label">状态:</span>
        <span :class="selectedNode.status">{{ getStatusText(selectedNode.status) }}</span>
      </div>
      <div class="detail-row">
        <span class="label">耗时:</span>
        <span>{{ formatDuration(selectedNode.duration_ms) }}</span>
      </div>
      <div class="detail-row">
        <span class="label">开始时间:</span>
        <span>{{ selectedNode.start_time }}</span>
      </div>
      <div class="detail-row">
        <span class="label">结束时间:</span>
        <span>{{ selectedNode.end_time }}</span>
      </div>
      <div v-if="selectedNode.tags && Object.keys(selectedNode.tags).length" class="detail-row">
        <span class="label">标签:</span>
        <div class="tags">
          <span v-for="(value, key) in selectedNode.tags" :key="key" class="tag">{{ key }}: {{ value }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  },
  edges: {
    type: Array,
    default: () => []
  },
  totalDuration: {
    type: Number,
    default: 0
  }
});

const selectedNode = ref(null);
const diagramContainer = ref(null);
const svgWidth = ref(800);
const svgHeight = ref(600);

const nodePositions = ref({});

watch(() => props.nodes, () => {
  calculatePositions();
}, { immediate: true });

function calculatePositions() {
  const positions = {};
  const rows = 4;
  const cols = Math.ceil(props.nodes.length / rows);
  const spacingX = 220;
  const spacingY = 100;
  const startX = 50;
  const startY = 50;

  props.nodes.forEach((node, index) => {
    const row = index % rows;
    const col = Math.floor(index / rows);
    positions[node.id] = {
      x: startX + col * spacingX,
      y: startY + row * spacingY
    };
  });

  nodePositions.value = positions;
  
  svgWidth.value = Math.max(800, cols * spacingX + 150);
  svgHeight.value = Math.max(600, rows * spacingY + 150);
}

function getNodePosition(nodeId) {
  return nodePositions.value[nodeId] || { x: 0, y: 0 };
}

function getNodeBgColor(status) {
  const colors = {
    ok: '#f0fdf4',
    error: '#fef2f2',
    timeout: '#fefce8'
  };
  return colors[status] || '#f3f4f6';
}

function getNodeBorderColor(status) {
  const colors = {
    ok: '#22c55e',
    error: '#ef4444',
    timeout: '#eab308'
  };
  return colors[status] || '#9ca3af';
}

function getEdgeColor(edge) {
  const fromNode = props.nodes.find(n => n.id === edge.from);
  return fromNode?.status === 'error' ? '#ef4444' : '#9ca3af';
}

function getStatusText(status) {
  const texts = {
    ok: '成功',
    error: '错误',
    timeout: '超时'
  };
  return texts[status] || status;
}

function formatDuration(ms) {
  if (!ms || ms < 0) return '0ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}m`;
}

function selectNode(node) {
  selectedNode.value = node;
}
</script>

<style scoped>
.flow-diagram {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.diagram-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.diagram-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.duration {
  font-size: 14px;
  color: #6b7280;
}

.duration span.warning {
  color: #f59e0b;
  font-weight: 600;
}

.diagram-container {
  flex: 1;
  overflow: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.node-group {
  cursor: pointer;
}

.node-group:hover .node-rect {
  filter: brightness(0.95);
}

.node-rect {
  transition: all 0.2s ease;
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  fill: #1f2937;
}

.node-status {
  font-size: 11px;
  fill: #6b7280;
}

.node-status.ok {
  fill: #22c55e;
}

.node-status.error {
  fill: #ef4444;
}

.node-status.timeout {
  fill: #eab308;
}

.node-detail {
  margin-top: 16px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.node-detail h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
}

.detail-row {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-row .label {
  width: 80px;
  color: #6b7280;
}

.detail-row .tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  padding: 2px 8px;
  background: #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
}
</style>

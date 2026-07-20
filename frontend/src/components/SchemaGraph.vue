<template>
  <div class="schema-graph-container">
    <div class="graph-toolbar">
      <div class="toolbar-left">
        <el-tag type="info" size="small">类: {{ classCount }}</el-tag>
        <el-tag type="success" size="small">属性: {{ propertyCount }}</el-tag>
        <el-tag type="danger" size="small">实例: {{ instanceCount }}</el-tag>
        <el-tag type="warning" size="small">关系: {{ edgeCount }}</el-tag>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" size="small" @click="handleRefresh" :loading="loading">
          <Refresh /> 刷新
        </el-button>
        <el-button size="small" @click="handleFitView">
          <Expand /> 适应视图
        </el-button>
        <el-button size="small" @click="handleZoomIn">
          <ZoomIn /> 放大
        </el-button>
        <el-button size="small" @click="handleZoomOut">
          <ZoomOut /> 缩小
        </el-button>
      </div>
    </div>
    
    <div class="graph-wrapper">
      <VueFlow
        v-model:nodes="vueFlowNodes"
        v-model:edges="vueFlowEdges"
        :default-zoom="1"
        :default-view-state="{ zoom: 1, x: 0, y: 0 }"
        :min-zoom="0.2"
        :max-zoom="3"
        @node-click="handleNodeClick"
        @edge-click="handleEdgeClick"
        class="vue-flow-container"
      >
        <Background pattern-color="#aaa" :gap="16" />
        <Controls />
        <MiniMap />
        
        <template #node-custom="{ data, id }">
          <div
            class="custom-node"
            :class="data.nodeType"
            :style="{ background: data.bgColor, borderColor: data.color }"
          >
            <div class="node-header">
              <span class="node-icon">{{ getNodeIcon(data.nodeType) }}</span>
              <span class="node-name">{{ data.name }}</span>
            </div>
            <div class="node-label">{{ data.label }}</div>
            <div class="node-type-tag">{{ getNodeTypeLabel(data.nodeType) }}</div>
          </div>
        </template>
        
        <template #edge-custom="{ data, edge }">
          <div class="edge-label">
            {{ data.label }}
          </div>
        </template>
      </VueFlow>
    </div>
    
    <div v-if="selectedNode" class="node-panel">
      <div class="panel-header">
        <span class="panel-title">节点详情</span>
        <el-button size="small" @click="selectedNode = null">关闭</el-button>
      </div>
      <div class="panel-body">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">
            <code class="uri-code">{{ selectedNode.id }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="名称">{{ selectedNode.name }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ selectedNode.label }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="getNodeTagType(selectedNode.type)" size="small">
              {{ getNodeTypeLabel(selectedNode.type) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="relatedEdges.length" class="related-section">
          <div class="section-title">关联关系</div>
          <div class="edge-list">
            <div v-for="edge in relatedEdges" :key="edge.id" class="edge-item">
              <span class="edge-label">{{ edge.label }}</span>
              <span class="edge-arrow">→</span>
              <span class="edge-target">{{ getNodeName(edge.target) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { Refresh, Expand, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { getOntologyGraph } from '../services/ontologyReasoningApi.js'

const emit = defineEmits(['node-click', 'edge-click'])

const { nodes, edges, zoomIn, zoomOut, fitView } = useVueFlow()

const loading = ref(false)
const selectedNode = ref(null)

const graphData = ref({ nodes: [], edges: [], classCount: 0, propertyCount: 0, instanceCount: 0, edgeCount: 0 })

const classCount = computed(() => graphData.value.classCount)
const propertyCount = computed(() => graphData.value.propertyCount)
const instanceCount = computed(() => graphData.value.instanceCount)
const edgeCount = computed(() => graphData.value.edgeCount)

const vueFlowNodes = computed(() => {
  const nodeTypes = {
    class: { bgColor: '#e0e7ff', color: '#6366f1' },
    object_property: { bgColor: '#dcfce7', color: '#22c55e' },
    datatype_property: { bgColor: '#fef3c7', color: '#f59e0b' },
    instance: { bgColor: '#f3e8ff', color: '#8b5cf6' }
  }
  
  let x = 100
  let y = 100
  const rowHeight = 150
  const colWidth = 200
  
  const classNodes = graphData.value.nodes.filter(n => n.type === 'class')
  const propNodes = graphData.value.nodes.filter(n => n.type === 'object_property' || n.type === 'datatype_property')
  const instanceNodes = graphData.value.nodes.filter(n => n.type === 'instance')
  
  const allNodes = []
  
  classNodes.forEach((node, idx) => {
    const pos = nodeTypes[node.type]
    allNodes.push({
      id: node.id,
      type: 'custom',
      position: { x: x + (idx % 5) * colWidth, y: y + Math.floor(idx / 5) * rowHeight },
      data: {
        ...node,
        nodeType: node.type,
        bgColor: pos.bgColor,
        color: pos.color
      },
      selected: false
    })
  })
  
  y += Math.ceil(classNodes.length / 5) * rowHeight + 50
  
  propNodes.forEach((node, idx) => {
    const pos = nodeTypes[node.type]
    allNodes.push({
      id: node.id,
      type: 'custom',
      position: { x: x + (idx % 5) * colWidth, y: y + Math.floor(idx / 5) * rowHeight },
      data: {
        ...node,
        nodeType: node.type,
        bgColor: pos.bgColor,
        color: pos.color
      },
      selected: false
    })
  })
  
  y += Math.ceil(propNodes.length / 5) * rowHeight + 50
  
  instanceNodes.forEach((node, idx) => {
    const pos = nodeTypes[node.type]
    allNodes.push({
      id: node.id,
      type: 'custom',
      position: { x: x + (idx % 5) * colWidth, y: y + Math.floor(idx / 5) * rowHeight },
      data: {
        ...node,
        nodeType: node.type,
        bgColor: pos.bgColor,
        color: pos.color
      },
      selected: false
    })
  })
  
  return allNodes
})

const vueFlowEdges = computed(() => {
  return graphData.value.edges.map(edge => {
    const edgeColors = {
      domain: '#6366f1',
      object_range: '#22c55e',
      datatype_range: '#f59e0b',
      instance_of: '#8b5cf6',
      relation: '#6b7280'
    }
    
    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.label,
      data: { label: edge.label },
      style: {
        stroke: edgeColors[edge.type] || '#9ca3af',
        strokeWidth: 2
      },
      animated: edge.type === 'relation'
    }
  })
})

const relatedEdges = computed(() => {
  if (!selectedNode.value) return []
  return graphData.value.edges.filter(e => e.source === selectedNode.value.id)
})

function getNodeIcon(nodeType) {
  const icons = {
    class: '📦',
    object_property: '🔗',
    datatype_property: '📊',
    instance: '🧩'
  }
  return icons[nodeType] || '📌'
}

function getNodeTypeLabel(nodeType) {
  const labels = {
    class: '类',
    object_property: '对象属性',
    datatype_property: '数据属性',
    instance: '实例'
  }
  return labels[nodeType] || nodeType
}

function getNodeTagType(nodeType) {
  const types = {
    class: 'primary',
    object_property: 'success',
    datatype_property: 'warning',
    instance: 'danger'
  }
  return types[nodeType] || 'info'
}

function getNodeName(nodeId) {
  const node = graphData.value.nodes.find(n => n.id === nodeId)
  return node ? node.name : nodeId
}

async function handleRefresh() {
  loading.value = true
  try {
    const res = await getOntologyGraph()
    graphData.value = {
      nodes: Array.isArray(res.nodes) ? res.nodes : [],
      edges: Array.isArray(res.edges) ? res.edges : [],
      classCount: res.classCount || 0,
      propertyCount: res.propertyCount || 0,
      instanceCount: res.instanceCount || 0,
      edgeCount: res.edgeCount || 0
    }
    selectedNode.value = null
  } catch (e) {
    console.error('加载图数据失败:', e)
  } finally {
    loading.value = false
  }
}

function handleFitView() {
  fitView({ padding: 0.1 })
}

function handleZoomIn() {
  zoomIn()
}

function handleZoomOut() {
  zoomOut()
}

function handleNodeClick(event) {
  const node = graphData.value.nodes.find(n => n.id === event.node.id)
  if (node) {
    selectedNode.value = node
    emit('node-click', node)
  }
}

function handleEdgeClick(event) {
  emit('edge-click', event.edge)
}

onMounted(() => {
  handleRefresh()
})

watch(graphData, () => {
  setTimeout(() => {
    fitView({ padding: 0.1 })
  }, 100)
}, { deep: true })
</script>

<style>
.vue-flow-container {
  height: 100%;
  width: 100%;
  background: #fafafa;
}

.custom-node {
  padding: 12px 16px;
  border-radius: 8px;
  border: 2px solid;
  min-width: 120px;
  max-width: 200px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.custom-node:hover {
  transform: scale(1.05);
  filter: brightness(0.95);
}

.custom-node.selected {
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.3);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.node-icon {
  font-size: 16px;
}

.node-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-type-tag {
  font-size: 11px;
  color: #6b7280;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  display: inline-block;
}

.edge-label {
  background: rgba(255, 255, 255, 0.9);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #374151;
  border: 1px solid #e5e7eb;
  white-space: nowrap;
}

.schema-graph-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.graph-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 8px 8px 0 0;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  gap: 4px;
}

.graph-wrapper {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-top: none;
  border-radius: 0 0 8px 8px;
  overflow: hidden;
}

.node-panel {
  margin-top: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
}

.related-section {
  margin-top: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.edge-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.edge-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f9fafb;
  border-radius: 4px;
}

.edge-label {
  font-size: 13px;
  font-weight: 500;
  color: #6366f1;
}

.edge-arrow {
  font-size: 12px;
  color: #9ca3af;
}

.edge-target {
  font-size: 13px;
  color: #374151;
}

.uri-code {
  font-family: monospace;
  font-size: 11px;
  color: #6b7280;
  word-break: break-all;
}

.vue-flow__controls {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.vue-flow__minimap {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
</style>

<template>
  <div class="schema-graph-container">
    <div class="graph-toolbar">
      <div class="toolbar-left">
        <el-tag type="info" size="small">类: {{ classCount }}</el-tag>
        <el-tag type="warning" size="small">属性: {{ propertyCount }}</el-tag>
        <el-tag type="success" size="small">实例: {{ instanceCount }}</el-tag>
        <el-tag type="danger" size="small">关系: {{ edgeCount }}</el-tag>
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
        <el-select v-model="layoutType" size="small" style="width: 140px">
          <el-option label="力导向布局" value="forceDirected" />
          <el-option label="分层布局" value="hierarchical" />
          <el-option label="圆形布局" value="circular" />
        </el-select>
      </div>
    </div>
    
    <div ref="graphContainer" class="graph-wrapper"></div>
    <div class="graph-status">{{ statusText }}</div>
    
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
          <el-descriptions-item v-if="selectedNode.type === 'class' && selectedInstanceCount > 0" label="实例数">
            <span class="instance-count">{{ selectedInstanceCount }} 个</span>
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
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Network, DataSet } from 'vis-network/standalone'
import { Refresh, Expand, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { getOntologyGraph } from '../services/ontologyReasoningApi.js'

const emit = defineEmits(['node-click', 'edge-click'])

const graphContainer = ref(null)
const loading = ref(false)
const selectedNode = ref(null)
const layoutType = ref('forceDirected')

let network = null
let nodesDataSet = null
let edgesDataSet = null

const graphData = ref({ nodes: [], edges: [], classCount: 0, propertyCount: 0, instanceCount: 0, edgeCount: 0 })

const classCount = computed(() => graphData.value.classCount)
const propertyCount = computed(() => graphData.value.propertyCount)
const instanceCount = computed(() => graphData.value.instanceCount)
const edgeCount = computed(() => graphData.value.edgeCount)
const statusText = computed(() => `${graphData.value.nodes.length} 个节点 / ${graphData.value.edges.length} 条关系`)


const nodeTypeStyles = {
  class: {
    color: '#4f46e5',
    borderColor: '#818cf8',
    shape: 'ellipse',
    shadowColor: 'rgba(79, 70, 229, 0.3)'
  },
  object_property: {
    color: '#22c55e',
    borderColor: '#16a34a',
    shape: 'ellipse',
    shadowColor: 'rgba(34, 197, 94, 0.25)'
  },
  datatype_property: {
    color: '#f59e0b',
    borderColor: '#d97706',
    shape: 'ellipse',
    shadowColor: 'rgba(245, 158, 11, 0.25)'
  },
  instance: {
    color: '#8b5cf6',
    borderColor: '#7c3aed',
    shape: 'dot',
    shadowColor: 'rgba(139, 92, 246, 0.25)'
  }
}

const edgeTypeStyles = {
  subclass_of: { color: '#818cf8', smooth: true, animated: true, labelColor: '#6366f1' },
  domain: { color: '#6366f1', smooth: true, animated: false, labelColor: '#6366f1' },
  range: { color: '#22c55e', smooth: true, animated: false, labelColor: '#22c55e' },
  relation: { color: '#9ca3af', smooth: true, animated: true, labelColor: '#6b7280' }
}

function getLayoutOptions() {
  switch (layoutType.value) {
    case 'hierarchical':
      return {
        layout: {
          hierarchical: {
            enabled: true,
            direction: 'UD',
            sortMethod: 'directed',
            levelSeparation: 150,
            nodeSpacing: 100,
            treeSpacing: 200
          }
        },
        physics: {
          enabled: false
        }
      }
    case 'circular':
      return {
        layout: {
          hierarchical: { enabled: false }
        },
        physics: {
          enabled: true,
          solver: 'forceAtlas2Based',
          forceAtlas2Based: {
            gravitationalConstant: -50,
            centralGravity: 0.01,
            springLength: 200,
            springConstant: 0.04,
            damping: 0.09,
            avoidOverlap: 0.5
          }
        }
      }
    default:
      return {
        layout: {
          hierarchical: { enabled: false }
        },
        physics: {
          enabled: false
        }
      }
  }
}

function buildVisNodes() {
  return graphData.value.nodes
    .filter(node => node.type !== 'instance')
    .map((node) => {
      const style = nodeTypeStyles[node.type] || nodeTypeStyles.class
      const label = node.label || node.name || node.id
      const chineseLabel = getNodeTypeLabel(node.type)
      const title = `${chineseLabel}：${label}\n标识：${node.id}`

      return {
        id: node.id,
        label: `${chineseLabel}\n${label}`,
        title,
        color: {
          background: '#ffffff',
          border: style.borderColor,
          highlight: {
            background: '#f8fafc',
            border: style.borderColor
          },
          hover: {
            background: '#f1f5f9',
            border: style.borderColor
          }
        },
        borderWidth: 2,
        shape: style.shape || 'ellipse',
        widthConstraint: {
          minimum: 110,
          maximum: 220
        },
        heightConstraint: {
          minimum: 70,
          maximum: 120
        },
        font: {
          color: '#1e293b',
          size: 14,
          bold: true,
          face: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
        },
        shadow: {
          enabled: true,
          color: style.shadowColor,
          size: 12,
          x: 0,
          y: 4
        },
        margin: 14,
        data: node
      }
    })
}

function hexToRgb(hex) {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? `${parseInt(result[1], 16)}, ${parseInt(result[2], 16)}, ${parseInt(result[3], 16)}` : '79, 70, 229'
}

function getClassInstanceCount(classId) {
  return graphData.value.nodes.filter(n => n.type === 'instance' && n.classId === classId).length
}

function buildVisEdges() {
  const visibleNodeIds = new Set(
    graphData.value.nodes
      .filter(node => node.type !== 'instance')
      .map(node => node.id)
  )

  return graphData.value.edges
    .filter(edge => visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target))
    .map(edge => {
      const style = edgeTypeStyles[edge.type] || edgeTypeStyles.relation
      return {
        id: edge.id,
        from: edge.source,
        to: edge.target,
        label: edge.label ? `关系：${edge.label}` : '关系',
        color: {
          color: style.color,
          highlight: '#4f46e5',
          hover: '#6366f1',
          inherit: false
        },
        font: {
          color: style.labelColor,
          size: 11,
          bold: true,
          background: '#ffffff',
          strokeColor: '#ffffff',
          strokeWidth: 4,
          face: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
        },
        smooth: {
          enabled: true,
          type: 'continuous',
          roundness: 0.5
        },
        arrows: {
          to: {
            enabled: true,
            type: 'arrow',
            scaleFactor: 0.8,
            color: {
              color: style.color,
              highlight: '#4f46e5',
              hover: '#6366f1'
            }
          },
          middle: {
            enabled: false
          }
        },
        width: 2.5,
        shadow: {
          enabled: false
        },
        animation: style.animated ? {
          enabled: true,
          duration: 2500,
          easingFunction: 'easeInOutQuad'
        } : undefined,
        data: edge
      }
    })
}

function initNetwork() {
  if (!graphContainer.value) return
  
  if (network) {
    network.destroy()
  }

  nodesDataSet = new DataSet(buildVisNodes())
  edgesDataSet = new DataSet(buildVisEdges())

  const options = {
    ...getLayoutOptions(),
    autoResize: true,
    interaction: {
      hover: true,
      tooltipDelay: 80,
      hideEdgesOnDrag: false,
      hideEdgesOnZoom: false,
      hideEdgesOnSelect: false,
      dragNodes: true,
      dragView: true,
      zoomView: true,
      selectable: true,
      selectConnectedEdges: false
    },
    edges: {
      smooth: {
        enabled: true,
        type: 'continuous',
        roundness: 0.5
      },
      shadow: {
        enabled: false
      }
    },
    nodes: {
      font: {
        multi: 'html'
      },
      shadow: {
        enabled: true,
        color: 'rgba(79, 70, 229, 0.15)',
        size: 8,
        x: 2,
        y: 2
      },
      shapeProperties: {
        useBorderWithImage: false
      }
    },
    manipulation: {
      enabled: false
    },
    physics: {
      enabled: true,
      solver: 'forceAtlas2Based',
      forceAtlas2Based: {
        gravitationalConstant: -90,
        centralGravity: 0.01,
        springLength: 160,
        springConstant: 0.08,
        damping: 0.35,
        avoidOverlap: 0.8
      },
      stabilization: {
        enabled: true,
        iterations: 150,
        updateInterval: 25
      }
    }
  }

  network = new Network(graphContainer.value, { nodes: nodesDataSet, edges: edgesDataSet }, options)

  network.on('click', (params) => {
    if (params.nodes.length > 0) {
      const nodeId = params.nodes[0]
      const node = graphData.value.nodes.find(n => n.id === nodeId)
      if (node) {
        selectedNode.value = node
        emit('node-click', node)
      }
    }
    
    if (params.edges.length > 0) {
      const edgeId = params.edges[0]
      const edge = graphData.value.edges.find(e => e.id === edgeId)
      if (edge) {
        emit('edge-click', edge)
      }
    }
  })

  network.on('hoverNode', (params) => {
    network.selectNodes([params.node])
  })

  network.on('blurNode', () => {
    network.unselectAll()
  })
}

function handleRefresh() {
  loading.value = true
  getOntologyGraph().then(res => {
    graphData.value = {
      nodes: Array.isArray(res.nodes) ? res.nodes : [],
      edges: Array.isArray(res.edges) ? res.edges : [],
      classCount: res.classCount || 0,
      propertyCount: res.propertyCount || 0,
      instanceCount: res.instanceCount || 0,
      edgeCount: res.edgeCount || 0
    }
    selectedNode.value = null
    nextTick(() => {
      handleFitView()
    })
  }).catch(e => {
    console.error('加载图数据失败:', e)
  }).finally(() => {
    loading.value = false
  })
}

function handleFitView() {
  if (network) {
    network.fit({
      animation: {
        duration: 500,
        easingFunction: 'easeInOutQuad'
      }
    })
  }
}

function handleZoomIn() {
  if (network) {
    network.zoomIn()
  }
}

function handleZoomOut() {
  if (network) {
    network.zoomOut()
  }
}

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
  return labels[nodeType] || '节点'
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
  return node ? (node.label || node.name || '未知节点') : '未知节点'
}

watch(graphData, () => {
  nextTick(() => {
    if (nodesDataSet && edgesDataSet) {
      const newNodes = buildVisNodes()
      const newEdges = buildVisEdges()
      nodesDataSet.clear()
      edgesDataSet.clear()
      nodesDataSet.add(newNodes)
      edgesDataSet.add(newEdges)
      nextTick(() => {
        handleFitView()
      })
    } else {
      initNetwork()
    }
  })
}, { deep: true })

watch(layoutType, () => {
  if (network) {
    network.setOptions(getLayoutOptions())
  }
})

onMounted(() => {
  initNetwork()
  handleRefresh()
})

onUnmounted(() => {
  if (network) {
    network.destroy()
    network = null
  }
})
</script>

<style scoped>
.schema-graph-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
}

.graph-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.95);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  backdrop-filter: blur(10px);
}

.toolbar-left {
  display: flex;
  gap: 10px;
  align-items: center;
}

.toolbar-right {
  display: flex;
  gap: 6px;
  align-items: center;
}

.graph-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  background-image: 
    linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
  background-size: 20px 20px;
  background-position: center center;
}

.graph-wrapper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at 50% 50%, rgba(79, 70, 229, 0.03) 0%, transparent 50%);
  pointer-events: none;
}

.node-panel {
  margin: 12px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  backdrop-filter: blur(10px);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.related-section {
  margin-top: 18px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-title::before {
  content: '';
  width: 4px;
  height: 14px;
  background: linear-gradient(180deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 2px;
}

.edge-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.edge-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  transition: all 0.2s ease;
}

.edge-item:hover {
  background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
  border-color: rgba(99, 102, 241, 0.2);
}

.edge-label {
  font-size: 13px;
  font-weight: 500;
  color: #6366f1;
}

.edge-arrow {
  font-size: 12px;
  color: #94a3b8;
}

.edge-target {
  font-size: 13px;
  color: #334155;
}

.uri-code {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Mono', monospace;
  font-size: 11px;
  color: #64748b;
  word-break: break-all;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 4px;
}

.instance-count {
  font-size: 14px;
  font-weight: 600;
  color: #6366f1;
}

.instance-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
  padding-right: 8px;
}

.instance-list::-webkit-scrollbar {
  width: 6px;
}

.instance-list::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 3px;
}

.instance-list::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.instance-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border-radius: 8px;
  border: 1px solid rgba(139, 92, 246, 0.15);
  transition: all 0.2s ease;
}

.instance-item:hover {
  background: linear-gradient(135deg, #f3e8ff 0%, #ede9fe 100%);
  border-color: rgba(139, 92, 246, 0.3);
}

.instance-type {
  flex-shrink: 0;
}

.instance-name {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
}

.instance-uri {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Mono', monospace;
  font-size: 11px;
  color: #64748b;
  word-break: break-all;
  flex: 1;
  min-width: 0;
}
</style>

<template>
  <div class="langchain-editor">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <button @click="saveWorkflow" :disabled="!hasChanges" class="btn-primary">
          💾 保存
        </button>
        <button @click="runWorkflow" :disabled="!isValid" class="btn-success">
          ▶️ 运行
        </button>
        <button @click="clearWorkflow" class="btn-danger">
          🗑️ 清空
        </button>
      </div>
      <div class="toolbar-right">
        <span class="status" :class="validationStatus">
          {{ validationText }}
        </span>
      </div>
    </div>

    <!-- 主编辑区域 -->
    <div class="editor-container">
      <!-- 节点面板 -->
      <div class="node-panel">
        <h3>节点类型</h3>
        <div class="node-types">
          <div 
            v-for="nodeType in nodeTypes" 
            :key="nodeType.id"
            class="node-type-item"
            draggable="true"
            @dragstart="onDragStart($event, nodeType)"
          >
            <span class="node-icon">{{ nodeType.icon }}</span>
            <span class="node-name">{{ nodeType.name }}</span>
          </div>
        </div>
      </div>

      <!-- 画布区域 -->
      <div 
        class="canvas"
        ref="canvasRef"
        @dragover.prevent
        @drop="onDrop"
        @click="onCanvasClick"
      >
        <!-- 网格背景 -->
        <div class="grid"></div>
        
        <!-- 连接线 -->
        <svg class="connections">
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
          <path
            v-for="conn in connections"
            :key="conn.id"
            :d="getConnectionPath(conn)"
            fill="none"
            stroke="#6b7280"
            stroke-width="2"
            marker-end="url(#arrowhead)"
            class="connection-line"
          />
        </svg>

        <!-- 节点 -->
        <div
          v-for="node in nodes"
          :key="node.id"
          :class="['node', { selected: selectedNode === node.id, 'node-error': hasNodeError(node) }]"
          :style="{ left: node.x + 'px', top: node.y + 'px' }"
          @mousedown="onNodeMouseDown($event, node)"
        >
          <div class="node-header">
            <span class="node-icon">{{ getNodeIcon(node.type) }}</span>
            <span class="node-title">{{ getNodeTitle(node) }}</span>
            <button class="node-delete" @click.stop="deleteNode(node.id)">×</button>
          </div>
          <div class="node-body">
            <input
              v-if="node.type === 'prompt'"
              v-model="node.prompt"
              type="text"
              placeholder="输入提示词..."
              class="node-input"
              @input="markDirty"
            />
            <select
              v-if="node.type === 'llm'"
              v-model="node.model"
              class="node-select"
              @change="markDirty"
            >
              <option value="qwen-vl-plus">Qwen-VL-Plus</option>
              <option value="gpt-4o">GPT-4o</option>
              <option value="claude-3">Claude 3</option>
            </select>
            <input
              v-if="node.type === 'tool'"
              v-model="node.toolName"
              type="text"
              placeholder="工具名称..."
              class="node-input"
              @input="markDirty"
            />
            <textarea
              v-if="node.type === 'parser'"
              v-model="node.format"
              placeholder="输出格式 (JSON Schema)..."
              class="node-textarea"
              @input="markDirty"
            ></textarea>
          </div>
          <!-- 连接点 -->
          <div class="node-ports">
            <div 
              class="port port-input" 
              :class="{ 'port-active': hasInputConnection(node.id) }"
              @mousedown="startConnection($event, 'input', node)"
            ></div>
            <div 
              class="port port-output"
              :class="{ 'port-active': hasOutputConnection(node.id) }"
              @mousedown="startConnection($event, 'output', node)"
            ></div>
          </div>
        </div>

        <!-- 连接创建提示 -->
        <div v-if="connecting" class="connect-hint">
          点击另一个节点的连接点完成连接
        </div>
      </div>

      <!-- 属性面板 -->
      <div class="properties-panel">
        <h3>属性</h3>
        <div v-if="selectedNodeData" class="properties-content">
          <div class="property-group">
            <label>节点名称</label>
            <input
              v-model="selectedNodeData.title"
              type="text"
              class="property-input"
              @input="markDirty"
            />
          </div>
          <div class="property-group">
            <label>节点类型</label>
            <span class="property-value">{{ getNodeTypeLabel(selectedNodeData.type) }}</span>
          </div>
          <div class="property-group">
            <label>位置</label>
            <span class="property-value">{{ selectedNodeData.x }}, {{ selectedNodeData.y }}</span>
          </div>
        </div>
        <div v-else class="empty-properties">
          选择一个节点查看属性
        </div>
      </div>
    </div>

    <!-- 运行结果面板 -->
    <div v-if="showResults" class="results-panel">
      <div class="results-header">
        <h3>运行结果</h3>
        <button @click="showResults = false" class="close-btn">×</button>
      </div>
      <div class="results-content">
        <pre>{{ JSON.stringify(runResults, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>import { ref, computed } from 'vue';
import { v4 as uuidv4 } from 'uuid';
// 节点类型定义
const nodeTypes = [
 { id: 'start', name: '开始', icon: '🚀', category: 'flow' },
 { id: 'prompt', name: '提示词', icon: '📝', category: 'llm' },
 { id: 'llm', name: 'LLM', icon: '🤖', category: 'llm' },
 { id: 'tool', name: '工具调用', icon: '🔧', category: 'tool' },
 { id: 'parser', name: '输出解析', icon: '📊', category: 'parser' },
 { id: 'end', name: '结束', icon: '🏁', category: 'flow' }
];
// 画布引用
const canvasRef = ref(null);
// 节点列表
const nodes = ref([
 {
 id: 'start-1',
 type: 'start',
 x: 50,
 y: 200,
 title: '开始'
 },
 {
 id: 'llm-1',
 type: 'llm',
 x: 250,
 y: 200,
 title: 'LLM 节点',
 model: 'qwen-vl-plus'
 },
 {
 id: 'end-1',
 type: 'end',
 x: 450,
 y: 200,
 title: '结束'
 }
]);
// 连接线列表
const connections = ref([
 { id: 'conn-1', from: 'start-1', to: 'llm-1' },
 { id: 'conn-2', from: 'llm-1', to: 'end-1' }
]);
// 选中的节点
const selectedNode = ref(null);
// 是否有未保存的更改
const hasChanges = ref(false);
// 是否正在创建连接
const connecting = ref(false);
const connectStart = ref(null);
const connectType = ref(null);
// 拖拽状态
const dragging = ref(false);
const dragNode = ref(null);
const dragOffset = ref({ x: 0, y: 0 });
// 运行结果
const showResults = ref(false);
const runResults = ref(null);
// 计算属性
const selectedNodeData = computed(() => {
 return nodes.value.find(n => n.id === selectedNode.value);
});
const isValid = computed(() => {
 const startNodes = nodes.value.filter(n => n.type === 'start');
 const endNodes = nodes.value.filter(n => n.type === 'end');
 return startNodes.length === 1 && endNodes.length === 1 && connections.value.length > 0;
});
const validationStatus = computed(() => {
 if (!isValid.value)
 return 'invalid';
 return 'valid';
});
const validationText = computed(() => {
 if (!isValid.value)
 return '⚠️ 工作流不完整';
 return '✓ 工作流有效';
});
// 方法
const getNodeIcon = (type) => {
 const nodeType = nodeTypes.find(t => t.id === type);
 return nodeType ? nodeType.icon : '📦';
};
const getNodeTitle = (node) => {
 return node.title || getNodeTypeLabel(node.type);
};
const getNodeTypeLabel = (type) => {
 const nodeType = nodeTypes.find(t => t.id === type);
 return nodeType ? nodeType.name : type;
};
const hasNodeError = (node) => {
 if (node.type === 'prompt' && !node.prompt)
 return true;
 if (node.type === 'tool' && !node.toolName)
 return true;
 return false;
};
const hasInputConnection = (nodeId) => {
 return connections.value.some(c => c.to === nodeId);
};
const hasOutputConnection = (nodeId) => {
 return connections.value.some(c => c.from === nodeId);
};
const onDragStart = (event, nodeType) => {
 event.dataTransfer.setData('nodeType', JSON.stringify(nodeType));
};
const onDrop = (event) => {
 const canvas = canvasRef.value;
 if (!canvas)
 return;
 const rect = canvas.getBoundingClientRect();
 const x = event.clientX - rect.left - 75;
 const y = event.clientY - rect.top - 50;
 const nodeType = JSON.parse(event.dataTransfer.getData('nodeType'));
 const newNode = {
 id: `${nodeType.id}-${uuidv4().slice(0, 8)}`,
 type: nodeType.id,
 x: Math.max(0, x),
 y: Math.max(0, y),
 title: nodeType.name
 };
 nodes.value.push(newNode);
 markDirty();
};
const onCanvasClick = (event) => {
 if (event.target === canvasRef.value || event.target.classList.contains('grid')) {
 selectedNode.value = null;
 }
};
const onNodeMouseDown = (event, node) => {
 event.stopPropagation();
 selectedNode.value = node.id;
 dragging.value = true;
 dragNode.value = node;
 const rect = canvasRef.value.getBoundingClientRect();
 dragOffset.value = {
 x: event.clientX - rect.left - node.x,
 y: event.clientY - rect.top - node.y
 };
 document.addEventListener('mousemove', onMouseMove);
 document.addEventListener('mouseup', onMouseUp);
};
const onMouseMove = (event) => {
 if (!dragging.value || !dragNode.value || !canvasRef.value)
 return;
 const rect = canvasRef.value.getBoundingClientRect();
 dragNode.value.x = Math.max(0, event.clientX - rect.left - dragOffset.value.x);
 dragNode.value.y = Math.max(0, event.clientY - rect.top - dragOffset.value.y);
};
const onMouseUp = () => {
 dragging.value = false;
 dragNode.value = null;
 document.removeEventListener('mousemove', onMouseMove);
 document.removeEventListener('mouseup', onMouseUp);
};
const startConnection = (event, portType, node) => {
 event.stopPropagation();
 connecting.value = true;
 connectStart.value = node.id;
 connectType.value = portType;
 document.addEventListener('mouseup', finishConnection);
};
const finishConnection = (event) => {
 if (!connecting.value || !connectStart.value) {
 connecting.value = false;
 document.removeEventListener('mouseup', finishConnection);
 return;
 }
 const target = event.target;
 if (target.classList.contains('port')) {
 const portType = target.classList.contains('port-input') ? 'input' : 'output';
 const nodeElement = target.closest('.node');
 if (nodeElement) {
 const nodeId = nodeElement.getAttribute('data-node-id');
 if (connectType.value === 'output' && portType === 'input') {
 // 从输出连接到输入
 if (connectStart.value !== nodeId && !connections.value.some(c => c.to === nodeId)) {
 connections.value.push({
 id: `conn-${uuidv4().slice(0, 8)}`,
 from: connectStart.value,
 to: nodeId
 });
 markDirty();
 }
 }
 else if (connectType.value === 'input' && portType === 'output') {
 // 从输入连接到输出（反向）
 if (connectStart.value !== nodeId && !connections.value.some(c => c.to === connectStart.value)) {
 connections.value.push({
 id: `conn-${uuidv4().slice(0, 8)}`,
 from: nodeId,
 to: connectStart.value
 });
 markDirty();
 }
 }
 }
 }
 connecting.value = false;
 connectStart.value = null;
 connectType.value = null;
 document.removeEventListener('mouseup', finishConnection);
};
const deleteNode = (nodeId) => {
 // 删除相关连接
 connections.value = connections.value.filter(c => c.from !== nodeId && c.to !== nodeId);
 // 删除节点
 nodes.value = nodes.value.filter(n => n.id !== nodeId);
 if (selectedNode.value === nodeId) {
 selectedNode.value = null;
 }
 markDirty();
};
const getConnectionPath = (conn) => {
 const fromNode = nodes.value.find(n => n.id === conn.from);
 const toNode = nodes.value.find(n => n.id === conn.to);
 if (!fromNode || !toNode)
 return '';
 const fromX = fromNode.x + 150;
 const fromY = fromNode.y + 40;
 const toX = toNode.x;
 const toY = toNode.y + 40;
 const midX = (fromX + toX) / 2;
 return `M ${fromX} ${fromY} C ${midX} ${fromY}, ${midX} ${toY}, ${toX} ${toY}`;
};
const markDirty = () => {
 hasChanges.value = true;
};
const saveWorkflow = () => {
 const workflow = {
 nodes: nodes.value,
 connections: connections.value
 };
 localStorage.setItem('langchain-workflow', JSON.stringify(workflow));
 hasChanges.value = false;
 alert('工作流已保存！');
};
const clearWorkflow = () => {
 if (confirm('确定要清空工作流吗？')) {
 nodes.value = [];
 connections.value = [];
 selectedNode.value = null;
 hasChanges.value = false;
 }
};
const runWorkflow = async () => {
 showResults.value = true;
 runResults.value = {
 status: 'running',
 message: '工作流执行中...'
 };
 // 模拟执行
 setTimeout(() => {
 runResults.value = {
 status: 'completed',
 message: '工作流执行完成',
 workflow: {
 nodes: nodes.value.map(n => ({ id: n.id, type: n.type, title: n.title })),
 connections: connections.value
 },
 executionTime: '1.2s'
 };
 }, 1500);
};
</script>

<style scoped>
.langchain-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.btn-primary, .btn-success, .btn-danger {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
}

.btn-primary:disabled {
  background-color: #93c5fd;
  cursor: not-allowed;
}

.btn-success {
  background-color: #22c55e;
  color: white;
}

.btn-success:disabled {
  background-color: #86efac;
  cursor: not-allowed;
}

.btn-danger {
  background-color: #ef4444;
  color: white;
}

.status {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
}

.status.valid {
  background-color: #d4edda;
  color: #155724;
}

.status.invalid {
  background-color: #f8d7da;
  color: #721c24;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-panel {
  width: 180px;
  background-color: #f9fafb;
  border-right: 1px solid #e5e7eb;
  padding: 16px;
  overflow-y: auto;
}

.node-panel h3 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #374151;
}

.node-types {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-type-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background-color: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.node-type-item:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
}

.node-icon {
  font-size: 18px;
}

.node-name {
  font-size: 13px;
  color: #374151;
}

.canvas {
  flex: 1;
  position: relative;
  overflow: auto;
  background-color: #fff;
}

.grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    linear-gradient(#e5e7eb 1px, transparent 1px),
    linear-gradient(90deg, #e5e7eb 1px, transparent 1px);
  background-size: 20px 20px;
}

.connections {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 1;
}

.connection-line {
  transition: stroke 0.2s;
}

.connection-line:hover {
  stroke: #3b82f6;
}

.node {
  position: absolute;
  width: 150px;
  background-color: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  cursor: move;
  z-index: 10;
  transition: all 0.2s;
}

.node:hover {
  border-color: #93c5fd;
}

.node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.node-error {
  border-color: #ef4444;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background-color: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 6px 6px 0 0;
}

.node-header .node-icon {
  font-size: 14px;
}

.node-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.node-delete {
  width: 20px;
  height: 20px;
  border: none;
  background-color: #fee2e2;
  color: #dc2626;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.node-body {
  padding: 10px;
}

.node-input, .node-select, .node-textarea {
  width: 100%;
  padding: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
  box-sizing: border-box;
}

.node-textarea {
  min-height: 60px;
  resize: vertical;
}

.node-ports {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 100%;
  display: flex;
  justify-content: space-between;
  padding: 0 -8px;
}

.port {
  width: 12px;
  height: 12px;
  background-color: #9ca3af;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s;
}

.port-input {
  margin-left: -6px;
}

.port-output {
  margin-right: -6px;
}

.port:hover, .port-active {
  background-color: #3b82f6;
  transform: scale(1.3);
}

.connect-hint {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 16px 24px;
  background-color: rgba(0, 0, 0, 0.8);
  color: white;
  border-radius: 8px;
  font-size: 14px;
  z-index: 100;
}

.properties-panel {
  width: 220px;
  background-color: #f9fafb;
  border-left: 1px solid #e5e7eb;
  padding: 16px;
  overflow-y: auto;
}

.properties-panel h3 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #374151;
}

.properties-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.property-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.property-group label {
  font-size: 12px;
  color: #6b7280;
}

.property-input {
  padding: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 12px;
}

.property-value {
  font-size: 12px;
  color: #374151;
  padding: 4px;
  background-color: #fff;
  border-radius: 4px;
}

.empty-properties {
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  padding: 20px;
}

.results-panel {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 300px;
  background-color: #fff;
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.results-header h3 {
  margin: 0;
  font-size: 14px;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background-color: #f3f4f6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  color: #6b7280;
}

.results-content {
  height: calc(100% - 48px);
  overflow: auto;
  padding: 16px;
}

.results-content pre {
  margin: 0;
  font-size: 12px;
  color: #374151;
  white-space: pre-wrap;
}
</style>
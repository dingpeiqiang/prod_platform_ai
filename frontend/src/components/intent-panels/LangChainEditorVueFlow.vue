<template>
  <div class="langchain-editor-vueflow">
    <!-- 主工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <button @click="undo" :disabled="!canUndo" class="btn-icon" title="撤销">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 7v6h6"/>
            <path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/>
          </svg>
        </button>
        <button @click="redo" :disabled="!canRedo" class="btn-icon" title="重做">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 7v6h-6"/>
            <path d="M3 17a9 9 0 0 0 9 9 9 9 0 0 0 6-2.3L21 11"/>
          </svg>
        </button>
        <div class="toolbar-divider"></div>
        <button @click="saveWorkflow" :disabled="!hasChanges" class="btn-primary">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2"/>
            <polyline points="12 15 17 20 22 15"/>
            <path d="M12 15V3"/>
          </svg>
          保存
        </button>
        <button @click="exportWorkflow" class="btn-secondary">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          导出
        </button>
        <button @click="importWorkflow" class="btn-secondary">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          导入
        </button>
        <div class="toolbar-divider"></div>
        <button @click="runWorkflow" :disabled="!isValid" class="btn-success">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          运行
        </button>
        <button @click="clearWorkflow" class="btn-danger">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18"/>
            <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
            <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          </svg>
          清空
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
        <div v-for="group in nodeGroups" :key="group.id" class="node-group">
          <div class="group-header" @click="toggleGroup(group.id)">
            <svg 
              width="12" 
              height="12" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: expandedGroups.includes(group.id) }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>{{ group.name }}</span>
          </div>
          <div v-show="expandedGroups.includes(group.id)" class="node-types">
            <div 
              v-for="nodeType in group.nodes" 
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
        
        <!-- 模板快速添加 -->
        <div class="templates-section">
          <h4>快速模板</h4>
          <div class="templates-list">
            <button v-for="template in quickTemplates" :key="template.id" @click="applyTemplate(template)" class="template-btn">
              {{ template.name }}
            </button>
          </div>
        </div>
      </div>

      <!-- Vue Flow 画布 -->
      <div class="canvas-wrapper">
        <VueFlow
          v-model="elements"
          :default-zoom="1"
          :min-zoom="0.2"
          :max-zoom="4"
          @connect="onConnect"
          @node-drag-stop="onNodeDragStop"
          @pane-click="onPaneClick"
          class="vue-flow-canvas"
        >
          <Background pattern-color="#aaa" :gap="20" />
          <Controls />
          
          <!-- 自定义节点模板 -->
          <template #node-start="props">
            <StartNode :data="props.data" :selected="props.selected" />
          </template>
          
          <template #node-end="props">
            <EndNode :data="props.data" :selected="props.selected" />
          </template>
          
          <template #node-prompt="props">
            <PromptNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-llm="props">
            <LlmNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-tool="props">
            <ToolNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-condition="props">
            <ConditionNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-loop="props">
            <LoopNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-variable="props">
            <VariableNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-http="props">
            <HttpNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-code="props">
            <CodeNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
          
          <template #node-parser="props">
            <ParserNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
          </template>
        </VueFlow>
      </div>

      <!-- 属性面板 -->
      <div class="properties-panel">
        <h3>属性</h3>
        <div v-if="selectedNodeData" class="properties-content">
          <div class="property-section">
            <h4>基本信息</h4>
            <div class="property-group">
              <label>节点名称</label>
              <input
                v-model="selectedNodeData.label"
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
              <label>节点ID</label>
              <span class="property-value mono">{{ selectedNodeData.id }}</span>
            </div>
          </div>

          <!-- 节点特定属性 -->
          <div v-if="selectedNodeData.type === 'llm'" class="property-section">
            <h4>LLM 配置</h4>
            <div class="property-group">
              <label>模型</label>
              <select v-model="selectedNodeData.model" class="property-select" @change="markDirty">
                <option value="qwen-vl-plus">Qwen-VL-Plus</option>
                <option value="gpt-4o">GPT-4o</option>
                <option value="claude-3-opus">Claude 3 Opus</option>
              </select>
            </div>
            <div class="property-group">
              <label>温度: {{ selectedNodeData.temperature }}</label>
              <input
                v-model.number="selectedNodeData.temperature"
                type="range"
                min="0"
                max="2"
                step="0.1"
                class="property-range"
                @input="markDirty"
              />
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { v4 as uuidv4 } from 'uuid';

// 导入自定义节点组件（需要创建）
import StartNode from './nodes/StartNode.vue';
import EndNode from './nodes/EndNode.vue';
import PromptNode from './nodes/PromptNode.vue';
import LlmNode from './nodes/LlmNode.vue';
import ToolNode from './nodes/ToolNode.vue';
import ConditionNode from './nodes/ConditionNode.vue';
import LoopNode from './nodes/LoopNode.vue';
import VariableNode from './nodes/VariableNode.vue';
import HttpNode from './nodes/HttpNode.vue';
import CodeNode from './nodes/CodeNode.vue';
import ParserNode from './nodes/ParserNode.vue';

const { addEdges, project, viewport } = useVueFlow();

// 响应式状态
const elements = ref([]);
const hasChanges = ref(false);
const selectedNodeId = ref(null);
const expandedGroups = ref(['flow', 'llm', 'tools', 'parser']);

// 节点类型定义
const nodeGroups = ref([
  {
    id: 'flow',
    name: '流程控制',
    nodes: [
      { id: 'start', name: '开始', icon: '🚀', type: 'start' },
      { id: 'end', name: '结束', icon: '🏁', type: 'end' },
      { id: 'condition', name: '条件分支', icon: '🔀', type: 'condition' },
      { id: 'loop', name: '循环', icon: '🔄', type: 'loop' }
    ]
  },
  {
    id: 'llm',
    name: 'LLM 相关',
    nodes: [
      { id: 'prompt', name: '提示词', icon: '📝', type: 'prompt' },
      { id: 'llm', name: 'LLM 调用', icon: '🤖', type: 'llm' }
    ]
  },
  {
    id: 'tools',
    name: '工具与数据',
    nodes: [
      { id: 'tool', name: '工具调用', icon: '🔧', type: 'tool' },
      { id: 'http', name: 'HTTP请求', icon: '🌐', type: 'http' },
      { id: 'code', name: '代码执行', icon: '💻', type: 'code' },
      { id: 'variable', name: '变量赋值', icon: '📦', type: 'variable' }
    ]
  },
  {
    id: 'parser',
    name: '数据处理',
    nodes: [
      { id: 'parser', name: '输出解析', icon: '📊', type: 'parser' }
    ]
  }
]);

// 计算属性
const selectedNodeData = computed(() => {
  return elements.value.find(el => el.id === selectedNodeId.value && !el.source);
});

const isValid = computed(() => {
  const nodes = elements.value.filter(el => !el.source && !el.target);
  const edges = elements.value.filter(el => el.source && el.target);
  const startNodes = nodes.filter(n => n.type === 'start');
  const endNodes = nodes.filter(n => n.type === 'end');
  return startNodes.length >= 1 && endNodes.length >= 1 && edges.length > 0;
});

const validationStatus = computed(() => isValid.value ? 'valid' : 'invalid');

const validationText = computed(() => {
  const nodes = elements.value.filter(el => !el.source && !el.target);
  const edges = elements.value.filter(el => el.source && el.target);
  const startNodes = nodes.filter(n => n.type === 'start');
  const endNodes = nodes.filter(n => n.type === 'end');
  
  if (startNodes.length === 0) return '⚠️ 缺少开始节点';
  if (endNodes.length === 0) return '⚠️ 缺少结束节点';
  if (edges.length === 0) return '⚠️ 没有连接线';
  return '✓ 工作流有效';
});

const canUndo = ref(false);
const canRedo = ref(false);

// 事件处理
const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType));
  event.dataTransfer.effectAllowed = 'move';
};

const onConnect = (params) => {
  addEdges(params);
  markDirty();
};

const onNodeDragStop = (event) => {
  markDirty();
};

const onPaneClick = () => {
  selectedNodeId.value = null;
};

const updateNodeData = (nodeId, data) => {
  const node = elements.value.find(el => el.id === nodeId);
  if (node) {
    node.data = { ...node.data, ...data };
    markDirty();
  }
};

const markDirty = () => {
  hasChanges.value = true;
};

const getNodeTypeLabel = (type) => {
  for (const group of nodeGroups.value) {
    const nodeType = group.nodes.find(t => t.id === type);
    if (nodeType) return nodeType.name;
  }
  return type;
};

// 工具栏操作
const saveWorkflow = () => {
  const workflow = {
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target),
    version: '2.0',
    savedAt: new Date().toISOString()
  };
  localStorage.setItem('langchain-workflow-vueflow', JSON.stringify(workflow));
  hasChanges.value = false;
};

const exportWorkflow = () => {
  const workflow = {
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target),
    version: '2.0',
    exportedAt: new Date().toISOString()
  };
  
  const blob = new Blob([JSON.stringify(workflow, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `workflow-${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

const importWorkflow = () => {
  // TODO: 实现导入对话框
  alert('导入功能待实现');
};

const clearWorkflow = () => {
  if (confirm('确定要清空工作流吗？')) {
    elements.value = [];
    selectedNodeId.value = null;
    hasChanges.value = false;
  }
};

const runWorkflow = async () => {
  alert('运行功能待实现');
};

const undo = () => {
  // TODO: 实现撤销
};

const redo = () => {
  // TODO: 实现重做
};

const toggleGroup = (groupId) => {
  const index = expandedGroups.value.indexOf(groupId);
  if (index > -1) {
    expandedGroups.value.splice(index, 1);
  } else {
    expandedGroups.value.push(groupId);
  }
};

const applyTemplate = (template) => {
  const newElements = [];
  
  // 添加节点
  template.nodes.forEach((node, idx) => {
    const nodeId = `${node.type}-${uuidv4().slice(0, 8)}`;
    newElements.push({
      id: nodeId,
      type: node.type,
      position: { x: node.x, y: node.y },
      data: { 
        label: node.title || getNodeTypeLabel(node.type),
        ...node
      }
    });
  });
  
  // 添加边
  template.connections.forEach(conn => {
    const fromNode = newElements[conn.from];
    const toNode = newElements[conn.to];
    if (fromNode && toNode) {
      newElements.push({
        id: `edge-${uuidv4().slice(0, 8)}`,
        source: fromNode.id,
        target: toNode.id,
        sourceHandle: conn.outputIndex ? `source-${conn.outputIndex}` : undefined,
        targetHandle: conn.inputIndex ? `target-${conn.inputIndex}` : undefined
      });
    }
  });
  
  elements.value = newElements;
  markDirty();
};

// 快速模板
const quickTemplates = ref([
  {
    id: 'simple-qa',
    name: '简单问答',
    nodes: [
      { type: 'start', x: 50, y: 200, title: '开始' },
      { type: 'prompt', x: 250, y: 200, title: '问题提示词', prompt: '请回答以下问题：{{question}}' },
      { type: 'llm', x: 450, y: 200, title: 'LLM', model: 'qwen-vl-plus', temperature: 0.7 },
      { type: 'end', x: 650, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 }
    ]
  }
]);

// 初始化
onMounted(() => {
  const saved = localStorage.getItem('langchain-workflow-vueflow');
  if (saved) {
    try {
      const workflow = JSON.parse(saved);
      const nodes = workflow.nodes.map(node => ({
        id: node.id,
        type: node.type,
        position: node.position,
        data: node.data
      }));
      const edges = workflow.edges.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        sourceHandle: edge.sourceHandle,
        targetHandle: edge.targetHandle
      }));
      elements.value = [...nodes, ...edges];
    } catch (e) {
      console.error('加载工作流失败:', e);
    }
  }
  
  if (elements.value.length === 0) {
    applyTemplate(quickTemplates.value[0]);
    hasChanges.value = false;
  }
});
</script>

<style scoped>
.langchain-editor-vueflow {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background-color: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background-color: #e2e8f0;
  margin: 0 8px;
}

.btn-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background-color: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.btn-icon:hover:not(:disabled) {
  background-color: #e2e8f0;
  color: #334155;
}

.btn-icon:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-primary, .btn-secondary, .btn-success, .btn-danger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-secondary {
  background-color: #e2e8f0;
  color: #334155;
}

.btn-success {
  background-color: #22c55e;
  color: white;
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
  background-color: #dcfce7;
  color: #166534;
}

.status.invalid {
  background-color: #fee2e2;
  color: #991b1b;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-panel {
  width: 180px;
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  padding: 12px;
  overflow-y: auto;
}

.node-panel h3 {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.node-group {
  margin-bottom: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
  color: #475569;
}

.group-header svg {
  transition: transform 0.2s;
}

.group-header svg.rotated {
  transform: rotate(180deg);
}

.node-types {
  padding-left: 8px;
}

.node-type-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: grab;
}

.node-type-item:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
}

.vue-flow-canvas {
  width: 100%;
  height: 100%;
}

.properties-panel {
  width: 220px;
  background-color: #f8fafc;
  border-left: 1px solid #e2e8f0;
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
}

.property-group label {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.property-input, .property-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.property-range {
  width: 100%;
}

.empty-properties {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #94a3b8;
}
</style>

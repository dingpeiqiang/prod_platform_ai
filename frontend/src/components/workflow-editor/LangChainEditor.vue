<template>
  <div class="langchain-editor">
    <!-- 主工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <button @click="undo" :disabled="!canUndo" class="btn-icon" title="撤销 (Ctrl+Z)">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 7v6h6"/>
            <path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"/>
          </svg>
        </button>
        <button @click="redo" :disabled="!canRedo" class="btn-icon" title="重做 (Ctrl+Shift+Z)">
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
        <button 
          @click="runWorkflowWithPanel" 
          :disabled="!isValid || isRunning" 
          class="btn-success"
          :class="{ running: isRunning }"
        >
          <svg v-if="!isRunning" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          <svg v-else class="spin" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2"/>
          </svg>
          {{ isRunning ? '运行中...' : '运行' }}
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
        <button 
          @click="toggleRightPanel" 
          :class="['panel-toggle-btn', { active: showRightPanel }]"
          title="面板"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <line x1="10" y1="9" x2="8" y2="9"/>
          </svg>
          <span v-if="executionLogs.length > 0" class="badge">{{ executionLogs.length }}</span>
        </button>
      </div>
    </div>

    <!-- 主编辑区域 -->
    <div class="editor-container">
      <!-- 左侧节点面板 -->
      <NodePanel 
        :quick-templates="quickTemplates"
        @apply-template="applyTemplate"
      />

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
          @node-click="onNodeClick"
          class="vue-flow-canvas"
        >
          <Background pattern-color="#aaa" :gap="20" />
          <Controls />
          <MiniMap
            node-color="#3b82f6"
            node-stroke-color="#fff"
            node-stroke-width="2"
            background-color="#f8fafc"
            stroke-color="#e2e8f0"
            class="mini-map"
          />
          
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

        <!-- 执行状态覆盖层 -->
        <div v-if="isRunning" class="execution-overlay">
          <div class="execution-progress">
            <div class="progress-spinner"></div>
            <span>工作流执行中...</span>
          </div>
        </div>
      </div>

      <!-- 右侧面板区域 -->
      <div v-show="showRightPanel" class="right-panel">
        <!-- 面板标签切换 -->
        <div class="panel-tabs">
          <button 
            @click="activePanel = 'properties'" 
            :class="['tab-btn', { active: activePanel === 'properties' }]"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            属性
          </button>
          <button 
            @click="activePanel = 'execution'" 
            :class="['tab-btn', { active: activePanel === 'execution', running: isRunning }]"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
            日志
            <span v-if="executionLogs.length > 0" class="tab-badge">{{ executionLogs.length }}</span>
          </button>
        </div>

        <!-- 属性面板 -->
        <div v-show="activePanel === 'properties'" class="panel-content-wrapper">
          <PropertyPanel 
            :node-data="selectedNodeData"
            :node-type-label="selectedNodeTypeLabel"
            @update="onPropertyUpdate"
          />
        </div>

        <!-- 执行日志面板 -->
        <div v-show="activePanel === 'execution'" class="panel-content-wrapper">
          <ExecutionPanel 
            :logs="executionLogs"
            :is-running="isRunning"
            :last-result="lastExecutionResult"
            @clear="clearExecutionLogs"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import { v4 as uuidv4 } from 'uuid';

import NodePanel from './NodePanel.vue';
import PropertyPanel from './PropertyPanel.vue';
import ExecutionPanel from './ExecutionPanel.vue';

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

const { addEdges, removeNodes, removeEdges } = useVueFlow();

const elements = ref([]);
const hasChanges = ref(false);
const selectedNodeId = ref(null);
const showRightPanel = ref(true);
const activePanel = ref('properties');

const history = ref([]);
const historyIndex = ref(-1);
const MAX_HISTORY = 50;

const executionLogs = ref([]);
const isRunning = ref(false);
const lastExecutionResult = ref(null);

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

const nodeTypeDefinitions = [
  { id: 'start', name: '开始' },
  { id: 'end', name: '结束' },
  { id: 'condition', name: '条件分支' },
  { id: 'loop', name: '循环' },
  { id: 'prompt', name: '提示词' },
  { id: 'llm', name: 'LLM调用' },
  { id: 'tool', name: '工具调用' },
  { id: 'http', name: 'HTTP请求' },
  { id: 'code', name: '代码执行' },
  { id: 'variable', name: '变量赋值' },
  { id: 'parser', name: '输出解析' }
];

const selectedNodeData = computed(() => {
  return elements.value.find(el => el.id === selectedNodeId.value && !el.source);
});

const selectedNodeTypeLabel = computed(() => {
  if (!selectedNodeData.value) return '';
  const def = nodeTypeDefinitions.find(d => d.id === selectedNodeData.value.type);
  return def ? def.name : selectedNodeData.value.type;
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

const canUndo = computed(() => historyIndex.value > 0);
const canRedo = computed(() => historyIndex.value < history.value.length - 1);

const saveHistory = () => {
  const snapshot = JSON.stringify(elements.value);
  history.value = history.value.slice(0, historyIndex.value + 1);
  history.value.push(snapshot);
  if (history.value.length > MAX_HISTORY) {
    history.value.shift();
  }
  historyIndex.value = history.value.length - 1;
};

const onConnect = (params) => {
  saveHistory();
  addEdges(params);
  markDirty();
};

const onNodeDragStop = (event) => {
  markDirty();
};

const onPaneClick = () => {
  selectedNodeId.value = null;
};

const onNodeClick = (event) => {
  selectedNodeId.value = event.node.id;
  if (activePanel.value !== 'properties') {
    activePanel.value = 'properties';
  }
};

const updateNodeData = (nodeId, data) => {
  saveHistory();
  const node = elements.value.find(el => el.id === nodeId);
  if (node) {
    node.data = { ...node.data, ...data };
    markDirty();
  }
};

const onPropertyUpdate = ({ key, value }) => {
  if (selectedNodeId.value) {
    saveHistory();
    const node = elements.value.find(el => el.id === selectedNodeId.value);
    if (node) {
      node.data[key] = value;
      markDirty();
    }
  }
};

const markDirty = () => {
  hasChanges.value = true;
};

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
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.json';
  input.onchange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        try {
          const workflow = JSON.parse(event.target.result);
          if (workflow.nodes && Array.isArray(workflow.nodes)) {
            saveHistory();
            const nodes = workflow.nodes.map(node => ({
              id: node.id,
              type: node.type,
              position: node.position,
              data: node.data
            }));
            const edges = workflow.edges ? workflow.edges.map(edge => ({
              id: edge.id,
              source: edge.source,
              target: edge.target,
              sourceHandle: edge.sourceHandle,
              targetHandle: edge.targetHandle
            })) : [];
            elements.value = [...nodes, ...edges];
            markDirty();
          } else {
            alert('无效的工作流文件格式');
          }
        } catch (error) {
          alert('解析工作流文件失败: ' + error.message);
        }
      };
      reader.readAsText(file);
    }
  };
  input.click();
};

const clearWorkflow = () => {
  if (confirm('确定要清空工作流吗？')) {
    saveHistory();
    elements.value = [];
    selectedNodeId.value = null;
    hasChanges.value = false;
  }
};

const runWorkflowWithPanel = () => {
  showRightPanel.value = true;
  activePanel.value = 'execution';
  runWorkflow();
};

const runWorkflow = async () => {
  if (!isValid.value || isRunning.value) return;
  
  isRunning.value = true;
  executionLogs.value = [];
  lastExecutionResult.value = null;
  
  addLog('start', '开始执行工作流', null, null);
  
  try {
    const nodes = elements.value.filter(el => !el.source && !el.target);
    const edges = elements.value.filter(el => el.source && el.target);
    
    const startNode = nodes.find(n => n.type === 'start');
    if (!startNode) {
      throw new Error('未找到开始节点');
    }
    
    const context = {
      input: '',
      variables: {}
    };
    
    await executeNode(startNode.id, nodes, edges, context);
    
    addLog('success', '工作流执行完成', null, { 
      context, 
      timestamp: new Date().toISOString() 
    });
    
    lastExecutionResult.value = {
      status: 'success',
      context,
      timestamp: new Date().toISOString()
    };
    
  } catch (error) {
    addLog('error', '工作流执行失败', error.message, null);
    lastExecutionResult.value = {
      status: 'error',
      error: error.message,
      timestamp: new Date().toISOString()
    };
  } finally {
    isRunning.value = false;
  }
};

const addLog = (type, title, message, data) => {
  const now = new Date();
  const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
  executionLogs.value.push({
    type,
    title,
    message,
    data,
    time: timeStr
  });
};

const executeNode = async (nodeId, nodes, edges, context) => {
  const node = nodes.find(n => n.id === nodeId);
  if (!node) return;
  
  addLog('node', `执行节点: ${node.data.label || node.type}`, `节点ID: ${node.id}`, { node });
  
  await delay(500);
  
  switch (node.type) {
    case 'start':
      addLog('info', '初始化工作流上下文', null, context);
      break;
    
    case 'prompt':
      const prompt = node.data.prompt || '请输入内容';
      context.input = prompt.replace(/\{\{(\w+)\}\}/g, (_, key) => context.variables[key] || '');
      addLog('info', '构建提示词', prompt, { result: context.input });
      break;
    
    case 'llm':
      const model = node.data.model || 'qwen-vl-plus';
      const temperature = node.data.temperature || 0.7;
      addLog('info', `调用 LLM 模型`, `模型: ${model}, 温度: ${temperature}`, { input: context.input });
      
      const mockResponse = `这是模拟的 LLM 响应。\n\n输入: ${context.input}\n\n模型: ${model}\n\n时间: ${new Date().toLocaleString()}`;
      context.output = mockResponse;
      addLog('info', 'LLM 响应完成', null, { output: mockResponse });
      break;
    
    case 'tool':
      addLog('info', '工具调用', `工具类型: ${node.data.toolType || '未知'}`, null);
      context.variables['toolResult'] = '模拟工具执行结果';
      break;
    
    case 'condition':
      const condition = node.data.condition || 'true';
      addLog('info', '条件判断', `条件: ${condition}`, { result: true });
      break;
    
    case 'loop':
      const iterations = node.data.iterations || 3;
      addLog('info', '循环执行', `迭代次数: ${iterations}`, null);
      break;
    
    case 'variable':
      const varName = node.data.variableName || 'result';
      const varValue = node.data.variableValue || context.output;
      context.variables[varName] = varValue;
      addLog('info', '变量赋值', `${varName} = ${varValue}`, context.variables);
      break;
    
    case 'http':
      addLog('info', 'HTTP 请求', `URL: ${node.data.url || '未配置'}`, null);
      context.variables['httpResult'] = { status: 200, data: '模拟响应数据' };
      break;
    
    case 'code':
      addLog('info', '代码执行', node.data.code || '无代码', null);
      context.variables['codeResult'] = '模拟代码执行结果';
      break;
    
    case 'parser':
      addLog('info', '输出解析', null, { input: context.output });
      context.parsed = context.output ? JSON.stringify({ text: context.output }, null, 2) : null;
      break;
    
    case 'end':
      addLog('info', '到达结束节点', null, null);
      return;
    
    default:
      addLog('info', `执行节点类型: ${node.type}`, null, null);
  }
  
  const nextEdges = edges.filter(e => e.source === nodeId);
  for (const edge of nextEdges) {
    await executeNode(edge.target, nodes, edges, context);
  }
};

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const clearExecutionLogs = () => {
  executionLogs.value = [];
  lastExecutionResult.value = null;
};

const undo = () => {
  if (canUndo.value) {
    historyIndex.value--;
    const snapshot = history.value[historyIndex.value];
    elements.value = JSON.parse(snapshot);
    markDirty();
  }
};

const redo = () => {
  if (canRedo.value) {
    historyIndex.value++;
    const snapshot = history.value[historyIndex.value];
    elements.value = JSON.parse(snapshot);
    markDirty();
  }
};

const deleteSelectedNode = () => {
  if (selectedNodeId.value) {
    saveHistory();
    removeNodes([selectedNodeId.value]);
    const connectedEdges = elements.value.filter(
      el => el.source === selectedNodeId.value || el.target === selectedNodeId.value
    );
    if (connectedEdges.length > 0) {
      removeEdges(connectedEdges.map(e => e.id));
    }
    selectedNodeId.value = null;
    markDirty();
  }
};

const applyTemplate = (template) => {
  saveHistory();
  const newElements = [];
  
  template.nodes.forEach((node, idx) => {
    const nodeId = `${node.type}-${uuidv4().slice(0, 8)}`;
    newElements.push({
      id: nodeId,
      type: node.type,
      position: { x: node.x, y: node.y },
      data: { 
        label: node.title || node.type,
        ...node
      }
    });
  });
  
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

const copyNodes = () => {
  if (selectedNodeId.value) {
    const node = elements.value.find(el => el.id === selectedNodeId.value && !el.source);
    if (node) {
      const copyData = {
        nodes: [node],
        edges: []
      };
      localStorage.setItem('workflow-copy', JSON.stringify(copyData));
    }
  }
};

const pasteNodes = () => {
  const copyData = localStorage.getItem('workflow-copy');
  if (copyData) {
    try {
      const data = JSON.parse(copyData);
      saveHistory();
      const offset = { x: 50, y: 50 };
      data.nodes.forEach(node => {
        const newId = `${node.type}-${uuidv4().slice(0, 8)}`;
        elements.value.push({
          ...node,
          id: newId,
          position: {
            x: node.position.x + offset.x,
            y: node.position.y + offset.y
          },
          data: {
            ...node.data,
            id: newId
          }
        });
      });
      markDirty();
    } catch (error) {
      console.error('粘贴失败:', error);
    }
  }
};

const toggleRightPanel = () => {
  showRightPanel.value = !showRightPanel.value;
};

const handleKeydown = (event) => {
  const target = event.target;
  const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT';
  
  if (!isInput) {
    if ((event.ctrlKey || event.metaKey) && event.key === 'z') {
      event.preventDefault();
      if (event.shiftKey) {
        redo();
      } else {
        undo();
      }
    }
    
    if ((event.ctrlKey || event.metaKey) && event.key === 'c') {
      event.preventDefault();
      copyNodes();
    }
    
    if ((event.ctrlKey || event.metaKey) && event.key === 'v') {
      event.preventDefault();
      pasteNodes();
    }
    
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      deleteSelectedNode();
    }
  }
};

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
  
  saveHistory();
  
  window.addEventListener('keydown', handleKeydown);
});

watch(elements, () => {
  const saved = localStorage.getItem('langchain-workflow-vueflow');
  const current = JSON.stringify({
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target)
  });
  if (saved !== current) {
    hasChanges.value = true;
  }
}, { deep: true });
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

.btn-success.running {
  background-color: #f59e0b;
}

.btn-success svg.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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

.panel-toggle-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  cursor: pointer;
  color: #64748b;
  font-size: 12px;
  transition: all 0.2s;
  position: relative;
  margin-left: 12px;
}

.panel-toggle-btn:hover {
  background-color: #e2e8f0;
  color: #334155;
}

.panel-toggle-btn.active {
  background-color: #3b82f6;
  border-color: #3b82f6;
  color: white;
}

.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  background-color: #ef4444;
  color: white;
  font-size: 9px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  min-width: 0;
}

.vue-flow-canvas {
  width: 100%;
  height: 100%;
}

.right-panel {
  width: 320px;
  background-color: #f8fafc;
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.panel-tabs {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 12px;
  color: #64748b;
  transition: all 0.2s;
  position: relative;
}

.tab-btn:hover {
  background-color: #f1f5f9;
}

.tab-btn.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
  background-color: #fff;
}

.tab-btn.running.active {
  color: #f59e0b;
  border-bottom-color: #f59e0b;
}

.tab-badge {
  position: absolute;
  top: 6px;
  right: 8px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  background-color: #ef4444;
  color: white;
  font-size: 9px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-content-wrapper {
  flex: 1;
  overflow: hidden;
}

.execution-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.execution-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 30px 40px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.progress-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.execution-progress span {
  color: #334155;
  font-size: 14px;
  font-weight: 500;
}
</style>
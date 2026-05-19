<template>
  <div class="langchain-editor">
    <div class="toolbar">
      <div class="toolbar-left">
        <button @click="goBack" class="btn-secondary" title="返回工作流管理">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回
        </button>
        <div class="toolbar-divider"></div>
        <button @click="newWorkflow" class="btn-primary" title="新建工作流">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14"/>
            <path d="M5 12h14"/>
          </svg>
          新建
        </button>
        <div class="workflow-selector">
          <button @click="showWorkflowList = !showWorkflowList" class="btn-secondary workflow-btn">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
            <span class="workflow-name">{{ workflowName }}</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div v-if="showWorkflowList" class="workflow-dropdown">
            <div class="dropdown-header">
              <span>工作流列表</span>
              <span class="workflow-count" v-if="workflows.length > 0">{{ workflows.length }}</span>
            </div>
            <div class="dropdown-content">
              <div v-if="workflows.length === 0" class="empty-workflows">
                <div class="empty-icon">📋</div>
                <div class="empty-text">暂无保存的工作流</div>
                <div class="empty-hint">从右侧快速模板开始创建</div>
              </div>
              <div 
                v-for="wf in workflows" 
                :key="wf.id"
                @click="openWorkflow(wf)"
                class="dropdown-item"
                :class="{ active: currentWorkflowId === wf.id }"
              >
                <div class="item-left">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                    <path d="M9 3v18"/>
                  </svg>
                  <div class="item-info">
                    <div class="item-name">{{ wf.name }}</div>
                    <div class="item-meta">
                      <span v-if="wf.description" class="item-desc">{{ wf.description }}</span>
                      <span class="item-time">{{ formatDate(wf.updatedAt || wf.savedAt || wf.createdAt) }}</span>
                    </div>
                  </div>
                </div>
                <button @click.stop="deleteWorkflow(wf.id)" class="delete-btn" title="删除">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M3 6h18"/>
                    <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
        <button @click="renameWorkflow" class="btn-icon" title="重命名">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
            <path d="m15 3 4 4"/>
          </svg>
        </button>
        <div class="toolbar-divider"></div>
        <button @click="undo" :disabled="!canUndo" class="btn-icon" title="撤销 (Ctrl+Z)">
          <Undo2 :size="16" />
        </button>
        <button @click="redo" :disabled="!canRedo" class="btn-icon" title="重做 (Ctrl+Y)">
          <Redo2 :size="16" />
        </button>
        <div class="toolbar-divider"></div>
        <button @click="saveWorkflow" :disabled="!hasChanges" class="btn-icon" title="保存 (Ctrl+S)">
          <Save :size="16" />
        </button>
        <button @click="exportWorkflow" class="btn-icon" title="导出">
          <Download :size="16" />
        </button>
        <button @click="importWorkflow" class="btn-icon" title="导入">
          <Upload :size="16" />
        </button>
        <div class="toolbar-divider"></div>
        <button 
          @click="runWorkflowWithPanel" 
          :disabled="!isValid || isRunning" 
          class="btn-success"
          :class="{ running: isRunning }"
          title="带参数执行"
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
        <button 
          @click="runWorkflow()" 
          :disabled="!isValid || isRunning" 
          class="btn-secondary"
          title="直接执行（无参数）"
        >
          <svg v-if="!isRunning" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          <svg v-else class="spin" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2"/>
          </svg>
          快速执行
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
      
      <div class="toolbar-center">
        <div class="align-group">
          <button @click="alignLeft" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="左对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="4" y1="12" x2="14" y2="12"/>
              <line x1="4" y1="18" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignCenter" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="水平居中">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="8" y1="12" x2="16" y2="12"/>
              <line x1="6" y1="18" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignRight" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="右对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="10" y1="12" x2="20" y2="12"/>
              <line x1="6" y1="18" x2="20" y2="18"/>
            </svg>
          </button>
          <div class="toolbar-divider-small"></div>
          <button @click="alignTop" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="顶部对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="4" x2="12" y2="14"/>
              <line x1="18" y1="4" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignMiddle" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="垂直居中">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="18" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignBottom" :disabled="selectedNodeIds.length < 2" class="btn-icon" title="底部对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="10" x2="12" y2="20"/>
              <line x1="18" y1="6" x2="18" y2="20"/>
            </svg>
          </button>
        </div>
        <div class="toolbar-divider"></div>
        <div class="distribute-group">
          <button @click="distributeHorizontal" :disabled="selectedNodeIds.length < 3" class="btn-icon" title="水平分布">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="8" x2="4" y2="16"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="20" y1="8" x2="20" y2="16"/>
              <line x1="4" y1="12" x2="20" y2="12"/>
            </svg>
          </button>
          <button @click="distributeVertical" :disabled="selectedNodeIds.length < 3" class="btn-icon" title="垂直分布">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="4" x2="16" y2="4"/>
              <line x1="8" y1="12" x2="16" y2="12"/>
              <line x1="8" y1="20" x2="16" y2="20"/>
              <line x1="12" y1="4" x2="12" y2="20"/>
            </svg>
          </button>
        </div>
      </div>
      <div class="toolbar-right">
        <span class="status" :class="validationStatus">
          {{ validationText }}
        </span>
        <button 
          @click="showShortcuts = !showShortcuts" 
          class="btn-icon"
          title="快捷键"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="4" width="20" height="16" rx="2"/>
            <path d="M6 8h.01M10 8h.01M14 8h.01M18 8h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M8 16h8"/>
          </svg>
        </button>
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
        <button 
          @click="showLibraryPanel = !showLibraryPanel" 
          :class="['panel-toggle-btn', { active: showLibraryPanel }]"
          title="工作流库"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="editor-container">
      <div v-if="showLibraryPanel" class="library-panel-wrapper">
        <WorkflowLibrary @load-workflow="handleLoadFromLibrary" />
      </div>
      <div v-if="showLeftPanel" class="left-panel-wrapper">
        <NodePanel 
          :quick-templates="quickTemplates"
          @apply-template="applyTemplate"
          @drag-start="onNodeDragStartFromPanel"
        />
      </div>

      <div class="canvas-wrapper">
        <VueFlow
          v-model="elements"
          :default-zoom="1"
          :min-zoom="0.2"
          :max-zoom="4"
          :nodes-draggable="true"
          :nodes-connectable="true"
          :edges-connectable="true"
          :connect-on-drag="true"
          :auto-connect="false"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :edges-updatable="true"
          :edges-deletable="true"
          :delete-key-code="['Delete', 'Backspace']"
          :disable-pan="false"
          :prevent-scroll-on-drag="true"
          @connect="onConnect"
          @connect-end="onConnectEnd"
          @node-drag-start="onNodeDragStart"
          @node-drag-stop="onNodeDragStop"
          @pane-click="onPaneClick"
          @node-click="({ event, node }) => onNodeClick(event, node)"
          @node-double-click="({ event, node }) => onNodeDoubleClick(event, node)"
          @edge-click="onEdgeClick"
          @drop="onDrop"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @pane-ready="onPaneReady"
          @handle-click="onHandleClick"
          @handle-mousedown="onHandleMouseDown"
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
          
          <!-- 空状态提示 -->
          <div v-if="elements.length === 0" class="empty-state">
            <div class="empty-state-content">
              <div class="empty-icon">📋</div>
              <h3>开始创建工作流</h3>
              <p>从左侧面板拖拽节点到此处</p>
              <p class="hint">或使用快速模板快速开始</p>
            </div>
          </div>
          
          <!-- 连接成功提示 -->
          <transition name="fade-slide">
            <div v-if="connectionSuccess" class="connection-success-toast">
              <span class="success-icon">✓</span>
              <span>连接创建成功</span>
            </div>
          </transition>
          
          <!-- 智能吸附提示 -->
          <transition name="fade-slide">
            <div v-if="nearbyHandle" class="snap-indicator">
              <span class="snap-icon">🧲</span>
              <span class="snap-text">已锁定连接点</span>
            </div>
          </transition>
          
          <template #node-start="props">
            <StartNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              :execution-status="nodeExecutionStatus[props.node?.id]"
              @update="updateNodeData"
            />
          </template>

          <template #node-end="props">
            <EndNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              :execution-status="nodeExecutionStatus[props.node?.id]"
              :available-variables="getAvailableVariables(props.node?.id)"
              @update="updateNodeData"
            />
          </template>

          <template #node-prompt="props">
            <PromptNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-llm="props">
            <LlmNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-tool="props">
            <ToolNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-condition="props">
            <ConditionNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              @update="updateNodeData"
            />
          </template>

          <template #node-loop="props">
            <LoopNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-variable="props">
            <VariableNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-http="props">
            <HttpNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-code="props">
            <CodeNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-parser="props">
            <ParserNode
              :data="enrichNodeData(props.data, props.node?.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>
        </VueFlow>
      </div>

      <div class="node-config-drawer" :class="{ open: showNodeConfigPanel }">
        <NodeConfigPanel
          :node="selectedNodeData"
          :execution-status="selectedNodeExecutionStatus"
          :execution-time="selectedNodeExecutionTime"
          @close="closeNodeConfigPanel"
          @update-label="onPropertyLabelUpdate"
          @update="onPropertyUpdate"
          @node-update="updateNodeData"
          @run="handleNodeRun"
        />
      </div>

      <!-- 快捷键提示面板 -->
      <div v-if="showShortcuts" class="shortcuts-panel">
        <div class="shortcuts-header">
          <h4>⌨️ 快捷键</h4>
          <button @click="showShortcuts = false" class="close-btn">✕</button>
        </div>
        <div class="shortcuts-content">
          <div class="shortcut-group">
            <h5>编辑操作</h5>
            <div class="shortcut-item">
              <span class="key">Ctrl + Z</span>
              <span class="desc">撤销</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Ctrl + Y</span>
              <span class="desc">重做</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Delete</span>
              <span class="desc">删除选中节点</span>
            </div>
          </div>
          
          <div class="shortcut-group">
            <h5>选择操作</h5>
            <div class="shortcut-item">
              <span class="key">Ctrl + A</span>
              <span class="desc">全选</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Shift + 点击</span>
              <span class="desc">多选</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Esc</span>
              <span class="desc">取消选择</span>
            </div>
          </div>
          
          <div class="shortcut-group">
            <h5>剪贴板</h5>
            <div class="shortcut-item">
              <span class="key">Ctrl + C</span>
              <span class="desc">复制</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Ctrl + V</span>
              <span class="desc">粘贴</span>
            </div>
          </div>
          
          <div class="shortcut-group">
            <h5>文件操作</h5>
            <div class="shortcut-item">
              <span class="key">Ctrl + S</span>
              <span class="desc">保存</span>
            </div>
          </div>
          
          <div class="shortcut-group">
            <h5>面板切换</h5>
            <div class="shortcut-item">
              <span class="key">双击节点</span>
              <span class="desc">打开/关闭配置面板</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Ctrl + G</span>
              <span class="desc">切换节点配置面板</span>
            </div>
            <div class="shortcut-item">
              <span class="key">Ctrl + L</span>
              <span class="desc">执行日志</span>
            </div>
          </div>
          
          <div class="shortcut-hint">
            💡 提示：将鼠标移到连接点上，按住左键拖动即可创建连接
          </div>
        </div>
      </div>

      <div class="right-panel" :class="{ open: showRightPanel }">
        <div class="panel-tabs">
          <button
            @click="activePanel = 'validation'"
            :class="['panel-tab', { active: activePanel === 'validation' }]"
          >
            验证
            <span v-if="validationResults.errors.length > 0" class="error-count">{{ validationResults.errors.length }}</span>
          </button>
          <button 
            @click="activePanel = 'execution'" 
            :class="['panel-tab', { active: activePanel === 'execution' }]"
          >
            执行日志
          </button>
        </div>
        
        <div class="panel-content">
          <div v-show="activePanel === 'validation'" class="panel-content-wrapper">
            <div v-if="validationResults.errors.length === 0 && validationResults.warnings.length === 0" class="validation-empty">
              <span class="success-icon">✓</span>
              <p>工作流验证通过</p>
            </div>
            <div v-else>
              <div v-if="validationResults.errors.length > 0" class="validation-section">
                <h4>错误</h4>
                <div 
                  v-for="(error, index) in validationResults.errors" 
                  :key="'error-' + index" 
                  class="validation-item error"
                >
                  <span class="validation-icon">✗</span>
                  <div class="validation-detail">
                    <span class="validation-message">{{ error.message }}</span>
                    <span class="validation-suggestion">💡 {{ error.suggestion }}</span>
                  </div>
                </div>
              </div>
              <div v-if="validationResults.warnings.length > 0" class="validation-section">
                <h4>警告</h4>
                <div 
                  v-for="(warning, index) in validationResults.warnings" 
                  :key="'warning-' + index" 
                  class="validation-item warning"
                >
                  <span class="validation-icon">⚠️</span>
                  <div class="validation-detail">
                    <span class="validation-message">{{ warning.message }}</span>
                    <span class="validation-suggestion">💡 {{ warning.suggestion }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-show="activePanel === 'execution'" class="panel-content-wrapper">
            <!-- 参数输入面板 -->
            <ParameterInputPanel 
              v-if="showParameterPanel"
              :initial-parameters="executionParameters"
              @close="closeParameterPanel"
              @execute="handleParameterExecute"
            />
            <!-- 执行日志面板 -->
            <ExecutionPanel 
              v-else
              :logs="executionLogs"
              :is-running="isRunning"
              :last-result="lastExecutionResult"
              @clear="clearExecutionLogs"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import { v4 as uuidv4 } from 'uuid';
import { ElMessage } from 'element-plus';
import { Undo2, Redo2, Save, Download, Upload } from 'lucide-vue-next';
import * as workflowApi from '@/services/workflowApi';

import NodePanel from './NodePanel.vue';
import NodeConfigPanel from './NodeConfigPanel.vue';
import ExecutionPanel from './ExecutionPanel.vue';
import ParameterInputPanel from './ParameterInputPanel.vue';
import WorkflowLibrary from './WorkflowLibrary.vue';

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

import { debounce, validateWorkflow, alignNodes, distributeNodes } from './utils/editorUtils';
import { ExecutionEngine } from './utils/executionEngine';
import { KeyboardShortcuts } from './utils/keyboardShortcuts';

// Props
const props = defineProps({
  workflowCode: {
    type: String,
    default: ''
  }
});

// Emits
const emit = defineEmits(['go-back']);

const goBack = () => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要返回吗？')) {
      return;
    }
  }
  emit('go-back');
};

const { addEdges, removeNodes, removeEdges, project } = useVueFlow();

const elements = ref([]);
const hasChanges = ref(false);
const selectedNodeId = ref(null);
const selectedNodeIds = ref([]);
const showLeftPanel = ref(true); // 控制左侧节点面板显示/隐藏
const showRightPanel = ref(false);
const showLibraryPanel = ref(false); // 控制工作流库面板显示/隐藏
const showNodeConfigPanel = ref(false);
const activePanel = ref('validation');
const lastNodeClick = ref({ id: null, time: 0 });
const DOUBLE_CLICK_MS = 320;
const showShortcuts = ref(false);
const connectionSuccess = ref(false);

// 智能吸附相关状态
const nearbyHandle = ref(null);
const SNAP_DISTANCE = 50;

const history = ref([]);
const historyIndex = ref(-1);
const MAX_HISTORY = 50;

const workflows = ref([]);
const currentWorkflowId = ref(null);
const showWorkflowList = ref(false);
const workflowName = ref('未命名工作流');

const executionLogs = ref([]);
const isRunning = ref(false);
const lastExecutionResult = ref(null);
const copiedNodes = ref([]);
const nodeExecutionStatus = ref({});

// 参数配置相关状态
const showParameterPanel = ref(false);
const executionParameters = ref([]);

const executionEngine = new ExecutionEngine();
const keyboardShortcuts = new KeyboardShortcuts();

const quickTemplates = ref([
  {
    id: 'simple-qa',
    name: '简单问答',
    description: '基础的问答流程，适合快速上手',
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
  },
  {
    id: 'data-analysis',
    name: '数据分析',
    description: '获取数据并进行分析处理',
    nodes: [
      { type: 'start', x: 50, y: 200, title: '开始' },
      { type: 'http', x: 250, y: 200, title: '获取数据', method: 'GET', url: '{{apiUrl}}' },
      { type: 'parser', x: 450, y: 200, title: '解析数据' },
      { type: 'prompt', x: 650, y: 200, title: '分析提示词', prompt: '请分析以下数据：{{data}}' },
      { type: 'llm', x: 850, y: 200, title: 'LLM分析', model: 'qwen-vl-plus', temperature: 0.5 },
      { type: 'end', x: 1050, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 4 },
      { from: 4, to: 5 }
    ]
  },
  {
    id: 'condition-branch',
    name: '条件分支',
    description: '根据条件判断走不同流程',
    nodes: [
      { type: 'start', x: 50, y: 250, title: '开始' },
      { type: 'prompt', x: 250, y: 250, title: '输入问题', prompt: '{{question}}' },
      { type: 'llm', x: 450, y: 250, title: '意图识别', model: 'qwen-vl-plus', temperature: 0.3 },
      { type: 'condition', x: 650, y: 250, title: '判断意图' },
      { type: 'http', x: 850, y: 150, title: '查询数据', method: 'GET' },
      { type: 'prompt', x: 850, y: 350, title: '闲聊回复', prompt: '用友好的语气回复：{{input}}' },
      { type: 'llm', x: 1050, y: 150, title: '生成答案', model: 'qwen-vl-plus', temperature: 0.7 },
      { type: 'llm', x: 1050, y: 350, title: '生成回复', model: 'qwen-vl-plus', temperature: 0.9 },
      { type: 'end', x: 1250, y: 250, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 4, outputIndex: 0, inputIndex: 0 },
      { from: 3, to: 5, outputIndex: 1, inputIndex: 0 },
      { from: 4, to: 6 },
      { from: 5, to: 7 },
      { from: 6, to: 8 },
      { from: 7, to: 8 }
    ]
  },
  {
id: 'code-execution',
    name: '代码执行',
    description: '生成并执行代码获取结果',
    nodes: [
      { type: 'start', x: 50, y: 200, title: '开始' },
      { type: 'prompt', x: 250, y: 200, title: '需求描述', prompt: '{{requirement}}' },
      { type: 'llm', x: 450, y: 200, title: '生成代码', model: 'qwen-vl-plus', temperature: 0.3 },
      { type: 'code', x: 650, y: 200, title: '执行代码', language: 'javascript' },
      { type: 'parser', x: 850, y: 200, title: '解析结果' },
      { type: 'end', x: 1050, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 4 },
      { from: 4, to: 5 }
    ]
  }
]);

const generateDefaultElements = () => {
  const template = quickTemplates.value[0];
  const elements = [];
  
  template.nodes.forEach((node, idx) => {
    const nodeId = `${node.type}-${uuidv4().slice(0, 8)}`;
    elements.push({
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
    const fromNode = elements[conn.from];
    const toNode = elements[conn.to];
    if (fromNode && toNode) {
      elements.push({
        id: `edge-${uuidv4().slice(0, 8)}`,
        source: fromNode.id,
        target: toNode.id,
        sourceHandle: conn.outputIndex ? `source-${conn.outputIndex}` : undefined,
        targetHandle: conn.inputIndex ? `target-${conn.inputIndex}` : undefined,
        markerEnd: {
          type: 'arrowclosed',
          color: '#94a3b8'
        }
      });
    }
  });
  
  return elements;
};

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
  return elements.value.find(el => el.id === selectedNodeId.value && el.type && !el.source);
});

const selectedNodeTypeLabel = computed(() => {
  if (!selectedNodeData.value) return '';
  const def = nodeTypeDefinitions.find(d => d.id === selectedNodeData.value.type);
  return def ? def.name : selectedNodeData.value.type;
});

const validationResults = computed(() => {
  return validateWorkflow(elements.value);
});

const isValid = computed(() => validationResults.value.errors.length === 0);

const validationStatus = computed(() => {
  if (validationResults.value.errors.length > 0) return 'invalid';
  if (validationResults.value.warnings.length > 0) return 'warning';
  return 'valid';
});

const validationText = computed(() => {
  if (validationResults.value.errors.length > 0) {
    return `❌ ${validationResults.value.errors.length} 个错误`;
  }
  if (validationResults.value.warnings.length > 0) {
    return `⚠️ ${validationResults.value.warnings.length} 个警告`;
  }
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
  // 验证连接
  const validation = validateConnection(params);
  if (!validation.valid) {
    console.warn('连接验证失败:', validation.message);
    return;
  }
  
  // 添加箭头标记
  const edgeWithMarker = {
    ...params,
    markerEnd: {
      type: 'arrowclosed',
      color: '#94a3b8'
    }
  };
  
  saveHistory();
  addEdges(edgeWithMarker);
  markDirty();
  
  // 显示连接成功动画
  showConnectionSuccess();
};

// 连接结束时的智能吸附
const onConnectEnd = (event) => {
  if (!nearbyHandle.value) return;
  
  // 获取锁定的连接点信息
  const snappedHandle = nearbyHandle.value;
  const handleId = snappedHandle.getAttribute('data-handleid');
  const nodeId = snappedHandle.closest('.vue-flow__node')?.getAttribute('data-id');
  const handleType = snappedHandle.classList.contains('target') ? 'target' : 'source';
  
  if (!nodeId || !handleId) return;
  
  // 检查是否需要修正连接
  // Vue Flow 在 connect-end 时，如果释放位置不在有效目标上，会创建一个无效连接
  // 我们需要检测这种情况并用锁定的点替换
  
  setTimeout(() => {
    // 查找刚刚创建的边（最后一条边）
    const edges = elements.value.filter(el => el.source && el.target);
    if (edges.length === 0) return;
    
    const lastEdge = edges[edges.length - 1];
    
    // 判断是否需要修正
    let needsCorrection = false;
    
    if (handleType === 'target') {
      // 如果锁定的是目标点，检查最后一条边的目标是否正确
      if (lastEdge.target !== nodeId || lastEdge.targetHandle !== handleId) {
        needsCorrection = true;
      }
    } else if (handleType === 'source') {
      // 如果锁定的是源点，检查最后一条边的源是否正确
      if (lastEdge.source !== nodeId || lastEdge.sourceHandle !== handleId) {
        needsCorrection = true;
      }
    }
    
    if (needsCorrection) {
      // 删除错误的边
      removeEdges([lastEdge.id]);
      
      // 创建正确的边
      const correctEdge = {
        id: `edge-${uuidv4().slice(0, 8)}`,
        source: handleType === 'source' ? nodeId : lastEdge.source,
        target: handleType === 'target' ? nodeId : lastEdge.target,
        sourceHandle: handleType === 'source' ? handleId : lastEdge.sourceHandle,
        targetHandle: handleType === 'target' ? handleId : lastEdge.targetHandle,
        markerEnd: {
          type: 'arrowclosed',
          color: '#94a3b8'
        }
      };
      
      // 验证新连接
      const validation = validateConnection(correctEdge);
      if (validation.valid) {
        addEdges([correctEdge]);
        showConnectionSuccess();
      }
    }
  }, 50);
};

// 显示连接成功提示
const showConnectionSuccess = () => {
  connectionSuccess.value = true;
  setTimeout(() => {
    connectionSuccess.value = false;
  }, 1500);
};

// 智能吸附功能
const onPaneReady = ({ vueFlowRef }) => {
  // 监听画布的鼠标移动事件
  const pane = vueFlowRef.value?.querySelector('.vue-flow__pane');
  if (!pane) return;
  
  pane.addEventListener('mousemove', handleMouseMove);
  pane.addEventListener('mouseleave', clearNearbyHandle);
};

const handleMouseMove = (event) => {
  const handles = document.querySelectorAll('.vue-flow__handle');
  let closestHandle = null;
  let minDistance = SNAP_DISTANCE;
  
  const container = event.target.closest('.vue-flow__pane');
  if (!container) return;
  
  const containerRect = container.getBoundingClientRect();
  const mouseX = event.clientX - containerRect.left;
  const mouseY = event.clientY - containerRect.top;
  
  handles.forEach(handle => {
    const handleRect = handle.getBoundingClientRect();
    const handleX = handleRect.left - containerRect.left + handleRect.width / 2;
    const handleY = handleRect.top - containerRect.top + handleRect.height / 2;
    const distance = Math.sqrt(
      Math.pow(mouseX - handleX, 2) + Math.pow(mouseY - handleY, 2)
    );
    
    if (distance < minDistance) {
      minDistance = distance;
      closestHandle = handle;
    }
  });
  
  if (closestHandle && closestHandle !== nearbyHandle.value) {
    if (nearbyHandle.value) {
      nearbyHandle.value.classList.remove('handle-snapped');
    }
    closestHandle.classList.add('handle-snapped');
    nearbyHandle.value = closestHandle;
    container.style.cursor = 'crosshair';
    
  } else if (!closestHandle && nearbyHandle.value) {
    nearbyHandle.value.classList.remove('handle-snapped');
    nearbyHandle.value = null;
    container.style.cursor = 'default';
  }
};

const clearNearbyHandle = () => {
  if (nearbyHandle.value) {
    nearbyHandle.value.classList.remove('handle-snapped');
    nearbyHandle.value = null;
  }
  // 恢复默认鼠标样式
  const pane = document.querySelector('.vue-flow__pane');
  if (pane) {
    pane.style.cursor = 'default';
  }
};

// 连接验证逻辑
const validateConnection = (connection) => {
  const { source, target } = connection;
  
  // 查找源节点和目标节点
  const sourceNode = elements.value.find(el => el.id === source && !el.source);
  const targetNode = elements.value.find(el => el.id === target && !el.source);
  
  if (!sourceNode || !targetNode) {
    return { valid: false, message: '节点不存在' };
  }
  
  // 规则1: 不能连接到自身
  if (source === target) {
    return { valid: false, message: '不能连接到自身' };
  }
  
  // 规则2: 结束节点不能有输出连接
  if (sourceNode.type === 'end') {
    return { valid: false, message: '结束节点不能有输出连接' };
  }
  
  // 规则3: 开始节点不能有输入连接
  if (targetNode.type === 'start') {
    return { valid: false, message: '开始节点不能有输入连接' };
  }
  
  // 规则4: 检查是否已存在相同的连接
  const existingEdge = elements.value.find(
    el => el.source === source && 
          el.target === target && 
          el.sourceHandle === connection.sourceHandle &&
          el.targetHandle === connection.targetHandle
  );
  if (existingEdge) {
    return { valid: false, message: '连接已存在' };
  }
  
  // 规则5: 检查是否会形成循环（可选，根据需求决定）
  // if (wouldCreateCycle(source, target)) {
  //   return { valid: false, message: '不能形成循环' };
  // }
  
  return { valid: true, message: '连接有效' };
};

const onNodeDragStart = () => {
  saveHistory();
};

const onNodeDragStop = debounce(() => {
  markDirty();
}, 100);

const onNodeDragStartFromPanel = (nodeType) => {
  // 可以在这里添加拖拽开始时的逻辑，例如显示提示等
  console.log('开始拖拽节点:', nodeType.name);
};

const enrichNodeData = (data, nodeId) => {
  if (!data) return data;
  return nodeId ? { ...data, id: nodeId } : data;
};

// 获取节点可用的变量列表
const getAvailableVariables = (nodeId) => {
  if (!nodeId) return [];
  
  const variables = [];
  const nodes = elements.value.filter(el => !el.source && !el.target);
  
  // 遍历所有在当前节点之前的节点
  for (const node of nodes) {
    if (node.id === nodeId) break;
    
    // 为每个节点添加输出变量
    const nodeType = node.type;
    let outputVarName = '';
    
    switch (nodeType) {
      case 'start':
        // 开始节点的输入参数
        if (node.data.params && Array.isArray(node.data.params)) {
          node.data.params.forEach(param => {
            variables.push({
              id: `${node.id}.${param.name}`,
              name: `${param.name} (入参)`,
              nodeId: node.id,
              nodeType: 'start',
              type: param.type || 'string'
            });
          });
        }
        break;
      case 'llm':
      case 'prompt':
      case 'tool':
      case 'http':
      case 'code':
      case 'variable':
      case 'parser':
        // 这些节点都有输出
        outputVarName = node.data.outputVar || node.data.label || nodeType;
        variables.push({
          id: `${node.id}.output`,
          name: `${outputVarName} (输出)`,
          nodeId: node.id,
          nodeType: nodeType,
          type: 'any'
        });
        break;
      case 'condition':
        // 条件节点可能有多个输出分支
        variables.push({
          id: `${node.id}.result`,
          name: `${node.data.label || '条件'} (结果)`,
          nodeId: node.id,
          nodeType: 'condition',
          type: 'boolean'
        });
        break;
    }
  }
  
  return variables;
};

const openNodeConfigPanel = (node) => {
  selectedNodeIds.value = [node.id];
  selectedNodeId.value = node.id;
  showNodeConfigPanel.value = true;
};

const closeNodeConfigPanel = () => {
  showNodeConfigPanel.value = false;
};

const toggleNodeConfigPanel = () => {
  if (!selectedNodeId.value) return;
  if (showNodeConfigPanel.value) {
    closeNodeConfigPanel();
  } else {
    const node = elements.value.find((el) => el.id === selectedNodeId.value && !el.source);
    if (node) openNodeConfigPanel(node);
  }
};

const handleNodeRun = async () => {
  if (!selectedNodeId.value) return;
  
  // 获取当前节点
  const node = elements.value.find(el => el.id === selectedNodeId.value && !el.source);
  if (!node) return;
  
  ElMessage.info(`运行节点: ${node.data.label}`);
  
  // TODO: 实现单个节点的运行逻辑
  // 这里可以调用后端API来运行单个LLM节点
  console.log('Running node:', node);
};

const onPaneClick = () => {
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
  closeNodeConfigPanel();
};

const onEdgeClick = ({ edge, event }) => {
  event.stopPropagation();
};

const onHandleClick = ({ event, handle, node }) => {
};

const onHandleMouseDown = ({ event, handle, node }) => {
};

const animatingNodeId = ref(null);

const onNodeClick = (event, node) => {
  if (event.shiftKey) {
    const idx = selectedNodeIds.value.indexOf(node.id);
    if (idx > -1) {
      selectedNodeIds.value.splice(idx, 1);
    } else {
      selectedNodeIds.value.push(node.id);
    }
    selectedNodeId.value = selectedNodeIds.value.length > 0
      ? selectedNodeIds.value[selectedNodeIds.value.length - 1]
      : null;
  } else {
    selectedNodeIds.value = [node.id];
    selectedNodeId.value = node.id;
  }
};

const onNodeDoubleClick = (event, node) => {
  triggerNodeDoubleClickAnimation(node.id);
  
  if (showNodeConfigPanel.value && selectedNodeId.value === node.id) {
    closeNodeConfigPanel();
  } else {
    openNodeConfigPanel(node);
  }
};

const triggerNodeDoubleClickAnimation = (nodeId) => {
  animatingNodeId.value = nodeId;
  
  const nodeElement = document.querySelector(`[data-id="${nodeId}"]`);
  if (nodeElement) {
    nodeElement.classList.add('dblclick-triggered');
    
    setTimeout(() => {
      nodeElement.classList.remove('dblclick-triggered');
      animatingNodeId.value = null;
    }, 400);
  }
};

// 拖拽放置事件处理
const onDragOver = (event) => {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
  
  // 添加放置区域的视觉反馈
  const pane = event.target.closest('.vue-flow__pane');
  if (pane) {
    pane.classList.add('dropzone');
  }
};

const onDragLeave = (event) => {
  // 移除放置区域的视觉反馈
  const pane = event.target.closest('.vue-flow__pane');
  if (pane) {
    pane.classList.remove('dropzone');
  }
};

const onDrop = (event) => {
  event.preventDefault();
  
  // 移除放置区域的视觉反馈
  const pane = event.target.closest('.vue-flow__pane');
  if (pane) {
    pane.classList.remove('dropzone');
  }
  
  try {
    const data = event.dataTransfer.getData('application/vueflow');
    if (!data) return;
    
    const nodeType = JSON.parse(data);
    
    // 获取画布容器的位置
    const container = pane?.getBoundingClientRect();
    if (!container) {
      console.error('无法获取画布容器位置');
      return;
    }
    
    // 计算相对于画布的坐标
    const x = event.clientX - container.left;
    const y = event.clientY - container.top;
    
    // 使用 project 函数转换为画布坐标（考虑缩放和平移）
    const position = project({ x, y });
    
    // 创建新节点
    const newNode = {
      id: `${nodeType.type}-${uuidv4().slice(0, 8)}`,
      type: nodeType.type,
      position,
      data: {
        label: nodeType.name,
        ...nodeType
      }
    };
    
    elements.value.push(newNode);
    saveHistory();
    markDirty();
    
    // 自动选中新创建的节点
    selectedNodeId.value = newNode.id;
    selectedNodeIds.value = [newNode.id];
  } catch (error) {
    console.error('拖拽节点失败:', error);
  }
};

const updateNodeData = (nodeId, data) => {
  saveHistory();
  const node = elements.value.find(el => el.id === nodeId);
  if (node) {
    node.data = { ...node.data, ...data };
    markDirty();
    
    // 如果是条件分支节点，延迟重新计算锚点位置
    if (node.type === 'condition') {
      setTimeout(() => {
        // 触发 Vue Flow 重新渲染，间接触发锚点位置重新计算
        elements.value = [...elements.value];
      }, 150); // 增加延迟时间，确保DOM完全更新
    }
  }
};

const onPropertyUpdate = ({ key, value }) => {
  if (selectedNodeId.value) {
    saveHistory();
    const node = elements.value.find((el) => el.id === selectedNodeId.value);
    if (node) {
      node.data[key] = value;
      markDirty();
    }
  }
};

const onPropertyLabelUpdate = (nodeId, label) => {
  saveHistory();
  const node = elements.value.find((el) => el.id === nodeId);
  if (node) {
    node.data.label = label;
    markDirty();
  }
};

const selectedNodeExecutionStatus = computed(() => {
  if (!selectedNodeId.value) return '';
  return nodeExecutionStatus.value[selectedNodeId.value] || '';
});

const selectedNodeExecutionTime = computed(() => '');

const markDirty = () => {
  hasChanges.value = true;
};

const loadWorkflows = () => {
  const saved = localStorage.getItem('langchain-workflows');
  if (saved) {
    workflows.value = JSON.parse(saved);
  } else {
    workflows.value = [];
  }
};

const saveWorkflows = () => {
  localStorage.setItem('langchain-workflows', JSON.stringify(workflows.value));
};

const saveWorkflow = async () => {
  const workflowData = {
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target),
    version: '2.0',
    savedAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  try {
    if (currentWorkflowId.value) {
      // 更新现有工作流
      const index = workflows.value.findIndex(w => w.id === currentWorkflowId.value);
      if (index !== -1) {
        workflows.value[index] = { 
          ...workflows.value[index], 
          ...workflowData,
          name: workflowName.value
        };
        saveWorkflows();
      }
      
      // 同步到后端
      const updateResult = await workflowApi.workflowApi.update(currentWorkflowId.value, {
        workflowName: workflowName.value,
        workflowData: workflowData
      });
      
      if (updateResult.success) {
        ElMessage.success('工作流已保存');
      } else {
        ElMessage.warning('本地保存成功，但云端同步失败：' + (updateResult.message || '未知错误'));
      }
    } else {
      // 创建新工作流
      const newId = uuidv4();
      const newWorkflow = {
        id: newId,
        name: workflowName.value,
        description: '',
        ...workflowData,
        createdAt: new Date().toISOString()
      };
      workflows.value.push(newWorkflow);
      currentWorkflowId.value = newId;
      saveWorkflows();
      
      // 同步到后端
      const createResult = await workflowApi.workflowApi.create({
        workflowCode: newId,
        workflowName: workflowName.value,
        description: '',
        category: 'general',
        workflowData: workflowData
      });
      
      if (createResult.success) {
        ElMessage.success('工作流已创建并保存');
      } else {
        ElMessage.warning('本地保存成功，但云端同步失败：' + (createResult.message || '未知错误'));
      }
    }
    hasChanges.value = false;
  } catch (error) {
    console.error('保存工作流失败:', error);
    ElMessage.error('保存失败：' + (error.message || '未知错误'));
  }
};

const newWorkflow = () => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要创建新工作流吗？')) {
      return;
    }
  }
  elements.value = [];
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
  currentWorkflowId.value = null;
  workflowName.value = '未命名工作流';
  hasChanges.value = false;
  history.value = [];
  historyIndex.value = -1;
  showWorkflowList.value = false;
};

const openWorkflow = (workflow) => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要打开其他工作流吗？')) {
      return;
    }
  }
  currentWorkflowId.value = workflow.id;
  workflowName.value = workflow.name;
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
    targetHandle: edge.targetHandle,
    markerEnd: edge.markerEnd || {
      type: 'arrowclosed',
      color: '#94a3b8'
    }
  })) : [];
  elements.value = [...nodes, ...edges];
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
  hasChanges.value = false;
  history.value = [];
  historyIndex.value = -1;
  showWorkflowList.value = false;
};

const handleLoadFromLibrary = (workflow) => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要加载工作流库中的工作流吗？')) {
      return;
    }
  }
  
  currentWorkflowId.value = workflow.workflowCode;
  workflowName.value = workflow.workflowName;
  
  const workflowData = workflow.workflowData || {};
  const nodes = (workflowData.nodes || []).map(node => ({
    id: node.id,
    type: node.type,
    position: node.position || { x: 0, y: 0 },
    data: node.data || {}
  }));
  const edges = (workflowData.edges || []).map(edge => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    markerEnd: edge.markerEnd || {
      type: 'arrowclosed',
      color: '#94a3b8'
    }
  }));
  
  elements.value = [...nodes, ...edges];
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
  hasChanges.value = false;
  history.value = [];
  historyIndex.value = -1;
};

const deleteWorkflow = (workflowId) => {
  if (!confirm('确定要删除这个工作流吗？')) {
    return;
  }
  const index = workflows.value.findIndex(w => w.id === workflowId);
  if (index !== -1) {
    workflows.value.splice(index, 1);
    saveWorkflows();
    if (currentWorkflowId.value === workflowId) {
      newWorkflow();
    }
  }
};

const renameWorkflow = () => {
  const currentWorkflow = currentWorkflowId.value 
    ? workflows.value.find(w => w.id === currentWorkflowId.value)
    : null;
  
  const newName = prompt('请输入工作流名称:', workflowName.value);
  if (newName && newName.trim()) {
    workflowName.value = newName.trim();
    
    const newDesc = prompt('请输入工作流描述（可选）:', currentWorkflow?.description || '');
    if (newDesc !== null) {
      if (currentWorkflowId.value) {
        const index = workflows.value.findIndex(w => w.id === currentWorkflowId.value);
        if (index !== -1) {
          workflows.value[index].name = workflowName.value;
          workflows.value[index].description = newDesc.trim();
          saveWorkflows();
        }
      }
    }
  }
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
              targetHandle: edge.targetHandle,
              markerEnd: edge.markerEnd || {
                type: 'arrowclosed',
                color: '#94a3b8'
              }
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
  // 显示参数输入面板
  showParameterPanel.value = true;
};

const runWorkflow = async (inputParams = {}) => {
  if (!isValid.value || isRunning.value) return;
  
  isRunning.value = true;
  executionLogs.value = [];
  lastExecutionResult.value = null;
  
  const onStatusChange = (status) => {
    nodeExecutionStatus.value = status;
  };
  
  const onLog = (log) => {
    executionLogs.value.push(log);
  };
  
  executionEngine.setCallbacks(onStatusChange, onLog);
  const result = await executionEngine.execute(elements.value, inputParams);
  lastExecutionResult.value = result;
  isRunning.value = false;
};

const handleParameterExecute = (params) => {
  // 关闭参数面板
  showParameterPanel.value = false;
  // 执行工作流并传入参数
  runWorkflow(params);
};

const closeParameterPanel = () => {
  showParameterPanel.value = false;
};

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
  const nodesToDelete = selectedNodeIds.value.length > 0 
    ? selectedNodeIds.value 
    : (selectedNodeId.value ? [selectedNodeId.value] : []);
  
  if (nodesToDelete.length > 0) {
    saveHistory();
    removeNodes(nodesToDelete);
    const connectedEdges = elements.value.filter(
      el => nodesToDelete.includes(el.source) || nodesToDelete.includes(el.target)
    );
    if (connectedEdges.length > 0) {
      removeEdges(connectedEdges.map(e => e.id));
    }
    selectedNodeId.value = null;
    selectedNodeIds.value = [];
    markDirty();
  }
};

const getSelectedNodes = () => {
  const ids = selectedNodeIds.value.length > 0 
    ? selectedNodeIds.value 
    : (selectedNodeId.value ? [selectedNodeId.value] : []);
  return elements.value.filter(el => !el.source && !el.target && ids.includes(el.id));
};

const alignLeft = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'left');
  markDirty();
};

const alignCenter = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'center');
  markDirty();
};

const alignRight = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'right');
  markDirty();
};

const alignTop = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'top');
  markDirty();
};

const alignMiddle = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'middle');
  markDirty();
};

const alignBottom = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 2) return;
  saveHistory();
  alignNodes(nodes, 'bottom');
  markDirty();
};

const distributeHorizontal = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 3) return;
  saveHistory();
  distributeNodes(nodes, 'horizontal');
  markDirty();
};

const distributeVertical = () => {
  const nodes = getSelectedNodes();
  if (nodes.length < 3) return;
  saveHistory();
  distributeNodes(nodes, 'vertical');
  markDirty();
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
        targetHandle: conn.inputIndex ? `target-${conn.inputIndex}` : undefined,
        markerEnd: {
          type: 'arrowclosed',
          color: '#94a3b8'
        }
      });
    }
  });
  
  elements.value = newElements;
  markDirty();
};

const copyNodes = () => {
  const nodes = getSelectedNodes();
  if (nodes.length > 0) {
    const copyData = {
      nodes: nodes.map(n => ({ ...n })),
      edges: []
    };
    localStorage.setItem('workflow-copy', JSON.stringify(copyData));
  }
};

const pasteNodes = () => {
  const copyData = localStorage.getItem('workflow-copy');
  if (copyData) {
    try {
      const data = JSON.parse(copyData);
      if (data.nodes && data.nodes.length > 0) {
        saveHistory();
        const offset = { x: 50, y: 50 };
        const newNodes = [];
        
        data.nodes.forEach(node => {
          const newId = `${node.type}-${uuidv4().slice(0, 8)}`;
          const newNode = {
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
          };
          newNodes.push(newNode);
          elements.value.push(newNode);
        });
        
        selectedNodeId.value = newNodes[newNodes.length - 1].id;
        selectedNodeIds.value = newNodes.map(n => n.id);
        markDirty();
      }
    } catch (error) {
      console.error('粘贴失败:', error);
    }
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now - date;
  
  // 小于1分钟
  if (diff < 60000) return '刚刚';
  // 小于1小时
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  // 小于24小时
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
  // 小于7天
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`;
  
  // 超过7天显示日期
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
};

const toggleRightPanel = () => {
  showRightPanel.value = !showRightPanel.value;
};

const selectAllNodes = () => {
  const nodes = elements.value.filter(el => !el.source && !el.target);
  if (nodes.length > 0) {
    selectedNodeIds.value = nodes.map(n => n.id);
    selectedNodeId.value = nodes[nodes.length - 1].id;
  }
};

const registerShortcuts = () => {
  keyboardShortcuts.register('ctrl+z', () => undo());
  keyboardShortcuts.register('ctrl+y', () => redo());
  keyboardShortcuts.register('ctrl+c', () => copyNodes());
  keyboardShortcuts.register('ctrl+v', () => pasteNodes());
  keyboardShortcuts.register('ctrl+s', () => saveWorkflow());
  keyboardShortcuts.register('delete', () => deleteSelectedNode());
  keyboardShortcuts.register('backspace', () => deleteSelectedNode());
  keyboardShortcuts.register('ctrl+shift+a', () => selectAllNodes());
  keyboardShortcuts.register('escape', () => {
    if (showNodeConfigPanel.value) {
      closeNodeConfigPanel();
      return;
    }
    selectedNodeId.value = null;
    selectedNodeIds.value = [];
  });
  keyboardShortcuts.register('ctrl+g', () => { toggleNodeConfigPanel(); });
  keyboardShortcuts.register('ctrl+l', () => {
    activePanel.value = 'execution';
    showRightPanel.value = true;
  });
};

const handleKeydown = (event) => {
  const target = event.target;
  const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT';
  
  if (!isInput) {
    keyboardShortcuts.handleEvent(event);
  }
};

onMounted(async () => {
  registerShortcuts();
  window.addEventListener('keydown', handleKeydown);
  
  loadWorkflows();
  
  // 如果传入了 workflowCode，从后端加载
  if (props.workflowCode) {
    try {
      const result = await workflowApi.workflowApi.get(props.workflowCode);
      if (result.success && result.data) {
        const workflow = result.data;
        currentWorkflowId.value = workflow.workflowCode;
        workflowName.value = workflow.workflowName;
        
        // 加载工作流数据
        if (workflow.workflowData) {
          const { nodes, edges } = workflow.workflowData;
          elements.value = [
            ...(nodes || []),
            ...(edges || [])
          ];
        }
        
        ElMessage.success('工作流加载成功');
      } else {
        ElMessage.warning('未找到工作流，将创建新的工作流');
      }
    } catch (error) {
      console.error('加载工作流失败:', error);
      ElMessage.error('加载工作流失败');
    }
  } else if (workflows.value.length > 0) {
    // 否则打开第一个本地工作流
    openWorkflow(workflows.value[0]);
  }
  
  history.value.push(JSON.stringify(elements.value));
  historyIndex.value = 0;
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style>
.langchain-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: white;
  border-bottom: 1px solid #e0e0e0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  white-space: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  min-height: 40px;
}

/* 修复工具栏按钮内SVG图标显示问题 */
.toolbar button svg {
  display: inline-block !important;
  flex-shrink: 0;
  max-width: none !important;
  /* 不强制设置宽高，使用HTML中的width/height属性 */
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin: 0 10px;
  padding: 0 10px;
  border-left: 1px solid #e0e0e0;
  border-right: 1px solid #e0e0e0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.workflow-selector {
  position: relative;
  display: inline-block;
}

.workflow-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
  justify-content: flex-start;
  padding: 6px 12px;
}

.workflow-btn svg {
  display: inline-block;
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  max-width: none;
}

.workflow-name {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 240px;
  z-index: 1000;
  overflow: hidden;
}

.dropdown-header {
  padding: 10px 12px;
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workflow-count {
  background: #3b82f6;
  color: white;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}

.empty-workflows {
  padding: 30px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 4px;
}

.empty-hint {
  font-size: 11px;
  color: #94a3b8;
}

.item-left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 2px;
}

.item-desc {
  font-size: 11px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-time {
  font-size: 10px;
  color: #94a3b8;
}

.dropdown-content {
  max-height: 300px;
  overflow-y: auto;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.dropdown-item:hover {
  background-color: #f0f5ff;
}

.dropdown-item.active {
  background-color: #e6f0ff;
}

.dropdown-item svg {
  flex-shrink: 0;
  margin-top: 2px;
  display: inline-block;
  width: 14px;
  height: 14px;
  max-width: none;
}

.delete-btn {
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s, visibility 0.15s;
  padding: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #999;
}

.delete-btn svg {
  display: inline-block;
  width: 12px;
  height: 12px;
  max-width: none;
}

.dropdown-item:hover .delete-btn {
  opacity: 1;
  visibility: visible;
}

.delete-btn:hover {
  color: #ff4d4f;
}

.align-group,
.distribute-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 768px) {
  .toolbar {
    padding: 6px 8px;
  }
  
  .toolbar-center {
    margin: 0 5px;
    padding: 0 5px;
  }
  
  .toolbar-left,
  .toolbar-right {
    gap: 4px;
  }
  
  .btn-icon, .btn-primary, .btn-secondary, .btn-success, .btn-danger {
    padding: 6px 8px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .toolbar {
    padding: 4px 6px;
  }
  
  .toolbar-center {
    display: none;
  }
  
  .status {
    font-size: 11px;
    padding: 3px 8px;
  }
}

.toolbar::-webkit-scrollbar {
  display: none;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin: 0 10px;
  padding: 0 10px;
  border-left: 1px solid #e0e0e0;
  border-right: 1px solid #e0e0e0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background-color: #e0e0e0;
  margin: 0 4px;
  flex-shrink: 0;
}

.toolbar-divider-small {
  width: 1px;
  height: 16px;
  background-color: #e0e0e0;
  margin: 0 4px;
  flex-shrink: 0;
}

.btn-icon, .btn-primary, .btn-secondary, .btn-success, .btn-danger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  min-width: 32px;
  height: 32px;
}

.btn-icon svg,
.btn-primary svg,
.btn-secondary svg,
.btn-success svg,
.btn-danger svg {
  display: inline-block;
  flex-shrink: 0;
  max-width: none;
  /* 使用HTML中定义的width和height */
}

.btn-icon {
  background: transparent;
  color: #666;
}

.btn-icon:hover:not(:disabled) {
  background-color: #f0f0f0;
}

.btn-icon:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-icon:disabled svg {
  opacity: 1;
}

.btn-primary {
  background-color: #2196f3;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #1976d2;
}

.btn-primary:disabled {
  background-color: #90caf9;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: #e0e0e0;
  color: #333;
}

.btn-secondary:hover {
  background-color: #bdbdbd;
}

.btn-success {
  background-color: #4caf50;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background-color: #388e3c;
}

.btn-success.running {
  background-color: #ff9800;
}

.btn-danger {
  background-color: #f44336;
  color: white;
}

.btn-danger:hover {
  background-color: #d32f2f;
}

.status {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}

.status.valid {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.status.warning {
  background-color: #fff3e0;
  color: #ef6c00;
}

.status.invalid {
  background-color: #ffebee;
  color: #c62828;
}

.panel-toggle-btn {
  position: relative;
  padding: 6px;
  background: transparent;
  border: 1px solid #ddd;
  border-radius: 4px;
  color: #666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
}

.panel-toggle-btn svg {
  display: inline-block;
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  max-width: none;
}

.panel-toggle-btn.active {
  background-color: #2196f3;
  color: white;
  border-color: #2196f3;
}

.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background-color: #f44336;
  color: white;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 10px;
  min-width: 16px;
  text-align: center;
  line-height: 1;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0; /* 防止flex子项溢出 */
}

/* 左侧面板容器 */
.left-panel-wrapper {
  display: flex;
  flex-shrink: 0;
  position: relative;
}

.library-panel-wrapper {
  display: flex;
  flex-shrink: 0;
  position: relative;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}

/* 左侧面板切换按钮样式 */
.toggle-left-panel-btn,
.hide-left-panel-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 36px;
  background: white;
  border: 1px solid #e0e0e0;
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  color: #999;
  transition: all 0.2s;
  z-index: 100;
  flex-shrink: 0;
  box-shadow: 1px 0 4px rgba(0, 0, 0, 0.06);
  align-self: center;
}

.toggle-left-panel-btn:hover,
.hide-left-panel-btn:hover {
  background: #f8f9fa;
  color: #3b82f6;
  border-color: #d0d0d0;
}

.toggle-left-panel-btn svg,
.hide-left-panel-btn svg {
  width: 12px;
  height: 12px;
  stroke-width: 2.5;
}

.hide-left-panel-btn {
  position: absolute;
  right: -20px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 100;
  border-radius: 0 4px 4px 0;
  border: 1px solid #e0e0e0;
  border-left: none;
  margin-left: 0;
}

.canvas-wrapper :deep(.vue-flow__pane) {
  cursor: default;
}

.canvas-wrapper :deep(.vue-flow__pane.dragging) {
  cursor: grabbing !important;
}

/* 连接时的鼠标样式 */
.canvas-wrapper :deep(.vue-flow__pane.connecting) {
  cursor: crosshair !important;
}

.canvas-wrapper :deep(.vue-flow__connection-line) {
  stroke: #3b82f6;
  stroke-width: 3;
  stroke-dasharray: 5;
  filter: drop-shadow(0 0 4px rgba(59, 130, 246, 0.6));
}

/* 快捷键提示面板 */
.shortcuts-panel {
  position: absolute;
  top: 60px;
  right: 20px;
  width: 320px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  z-index: 1000;
  animation: slideIn 0.3s ease-out;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.shortcuts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px 12px 0 0;
}

.shortcuts-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.1);
}

.shortcuts-content {
  padding: 16px;
}

.shortcut-group {
  margin-bottom: 16px;
}

.shortcut-group:last-child {
  margin-bottom: 0;
}

.shortcut-group h5 {
  margin: 0 0 8px 0;
  font-size: 12px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}

.shortcut-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  margin-bottom: 4px;
  background: #f8fafc;
  border-radius: 6px;
  transition: all 0.2s;
}

.shortcut-item:hover {
  background: #eff6ff;
  transform: translateX(2px);
}

.shortcut-item .key {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
  background: white;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.shortcut-item .desc {
  font-size: 13px;
  color: #475569;
}

.shortcut-hint {
  margin-top: 16px;
  padding: 12px;
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-left: 4px solid #f59e0b;
  border-radius: 6px;
  font-size: 13px;
  color: #92400e;
  line-height: 1.5;
}

/* 连接成功提示 */
.connection-success-toast {
  position: absolute;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.1);
  z-index: 1000;
  font-size: 14px;
  font-weight: 500;
}

.success-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  font-size: 16px;
  font-weight: bold;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
}

/* 智能吸附指示器 */
.snap-indicator {
  position: absolute;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.1);
  z-index: 1000;
  font-size: 14px;
  font-weight: 500;
  pointer-events: none;
}

.snap-icon {
  font-size: 18px;
  animation: snap-icon-bounce 1s ease-in-out infinite;
}

@keyframes snap-icon-bounce {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.2);
  }
}

.canvas-wrapper :deep(.vue-flow__background) {
  opacity: 0.3;
}

.canvas-wrapper :deep(.vue-flow__pane.dropzone) {
  background-color: rgba(59, 130, 246, 0.05);
}

/* VueFlow Handle 样式优化 */
.canvas-wrapper :deep(.vue-flow__handle) {
  width: 20px;
  height: 20px;
  background-color: #3b82f6;
  border: 2px solid white;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.4);
  cursor: crosshair;
  z-index: 1000;
  pointer-events: auto;
}

.canvas-wrapper :deep(.vue-flow__handle:hover) {
  transform: scale(1.6);
  box-shadow: 0 0 0 6px rgba(59, 130, 246, 0.6);
  background-color: #2563eb;
}

.canvas-wrapper :deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa;
}

.canvas-wrapper :deep(.vue-flow__handle[type="source"]) {
  background-color: #3b82f6;
}



.canvas-wrapper :deep(.vue-flow__handle-connecting) {
  background-color: #10b981;
  border-color: white;
  box-shadow: 0 0 0 5px rgba(16, 185, 129, 0.5), 0 4px 12px rgba(16, 185, 129, 0.4);
  animation: pulse-green 1.5s ease-in-out infinite;
}

@keyframes pulse-green {
  0%, 100% {
    box-shadow: 0 0 0 5px rgba(16, 185, 129, 0.5), 0 4px 12px rgba(16, 185, 129, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.3), 0 4px 12px rgba(16, 185, 129, 0.4);
  }
}

.canvas-wrapper :deep(.vue-flow__handle-valid) {
  background-color: #10b981;
  border-color: white;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.5), 0 2px 8px rgba(16, 185, 129, 0.3);
}

.canvas-wrapper :deep(.vue-flow__handle-invalid) {
  background-color: #ef4444;
  border-color: white;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.5), 0 2px 8px rgba(239, 68, 68, 0.3);
  animation: shake 0.4s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}

/* 输入点（target）特殊样式 */
.canvas-wrapper :deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa;
}

/* 输出点（source）特殊样式 */
.canvas-wrapper :deep(.vue-flow__handle[type="source"]) {
  background-color: #60a5fa;
}



/* 确保节点不会遮挡 Handle */
.canvas-wrapper :deep(.vue-flow__node) {
  z-index: 1;
  transition: box-shadow 0.2s, transform 0.2s;
}

.canvas-wrapper :deep(.vue-flow__node.selected) {
  z-index: 2;
}

.canvas-wrapper :deep(.vue-flow__node.dblclick-triggered) {
  animation: nodeDblClickPulse 0.4s ease-out;
}

@keyframes nodeDblClickPulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4);
  }
  50% {
    transform: scale(1.02);
    box-shadow: 0 0 0 12px rgba(59, 130, 246, 0);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0);
  }
}

.canvas-wrapper :deep(.vue-flow__node.dblclick-opening) {
  animation: nodeOpenConfig 0.3s ease-out forwards;
}

@keyframes nodeOpenConfig {
  0% {
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3);
  }
  100% {
    box-shadow: 0 0 20px 4px rgba(59, 130, 246, 0.2), 0 0 0 3px rgba(59, 130, 246, 0.3);
  }
}

/* 节点悬停时的连接提示 */
.canvas-wrapper :deep(.vue-flow__node:hover) {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

/* VueFlow Edge 样式优化 */
.canvas-wrapper :deep(.vue-flow__edge-path) {
  stroke: #94a3b8;
  stroke-width: 2.5;
  transition: stroke 0.2s, stroke-width 0.2s, filter 0.2s;
  cursor: pointer;
}

.canvas-wrapper :deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke: #64748b;
  stroke-width: 3.5;
  filter: drop-shadow(0 0 4px rgba(100, 116, 139, 0.5));
}

.canvas-wrapper :deep(.vue-flow__edge:selected .vue-flow__edge-path),
.canvas-wrapper :deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: #3b82f6;
  stroke-width: 3.5;
  filter: drop-shadow(0 0 5px rgba(59, 130, 246, 0.6));
}

.canvas-wrapper :deep(.vue-flow__edge-animated) {
  stroke-dasharray: 5;
  animation: dashdraw 0.5s linear infinite;
}

/* 连接引导线样式 */
.canvas-wrapper :deep(.vue-flow__connection-path) {
  stroke: #3b82f6;
  stroke-width: 3;
  stroke-dasharray: 10;
  opacity: 0.9;
  filter: drop-shadow(0 0 6px rgba(59, 130, 246, 0.5));
  cursor: crosshair;
}

/* 连线可拖拽区域扩展 */
.canvas-wrapper :deep(.vue-flow__edge) {
  pointer-events: stroke;
  stroke-width: 12;
}

.canvas-wrapper :deep(.vue-flow__edge):hover {
  stroke-width: 16;
}



@keyframes dashdraw {
  from {
    stroke-dashoffset: 10;
  }
  to {
    stroke-dashoffset: 0;
  }
}

.vue-flow-canvas {
  height: 100%;
  width: 100%;
}

.mini-map {
  position: absolute;
  bottom: 10px;
  right: 10px;
  width: 200px;
  height: 150px;
}

.empty-state {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 10;
}

.empty-state-content {
  text-align: center;
  color: #94a3b8;
  pointer-events: auto;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #64748b;
  font-weight: 600;
}

.empty-state p {
  margin: 4px 0;
  font-size: 14px;
  color: #94a3b8;
}

.empty-state .hint {
  font-size: 12px;
  color: #cbd5e1;
  margin-top: 8px;
}



.node-config-drawer {
  width: 0;
  flex-shrink: 0;
  overflow: hidden;
  background: #fff;
  border-left: 1px solid transparent;
  display: flex;
  flex-direction: column;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: width 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease, visibility 0.3s ease, border-color 0.32s ease;
}

.node-config-drawer.open {
  width: 520px;
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  border-left-color: #e2e8f0;
  box-shadow: -8px 0 24px rgba(15, 23, 42, 0.08), -2px 0 8px rgba(15, 23, 42, 0.04);
}

.canvas-wrapper {
  flex: 1;
  transition: flex 0.3s ease;
}

.right-panel {
  width: 0;
  min-width: 0;
  overflow: hidden;
  flex-shrink: 0;
  background: white;
  border-left: 1px solid transparent;
  display: flex;
  flex-direction: column;
  transition: width 0.32s cubic-bezier(0.4, 0, 0.2, 1), border-color 0.32s ease;
  visibility: hidden;
  pointer-events: none;
}

.right-panel.open {
  width: 320px;
  border-left-color: #e0e0e0;
  visibility: visible;
  pointer-events: auto;
}

.panel-tabs {
  display: flex;
  border-bottom: 1px solid #e0e0e0;
}

.panel-tab {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: #666;
  position: relative;
}

.panel-tab.active {
  color: #2196f3;
  font-weight: 500;
}

.panel-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background-color: #2196f3;
}

.error-count {
  background-color: #f44336;
  color: white;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 10px;
  margin-left: 4px;
}

.panel-content {
  flex: 1;
  overflow: hidden;
}

.panel-content-wrapper {
  height: 100%;
  overflow-y: auto;
  padding: 12px;
}

.validation-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #666;
}

.success-icon {
  font-size: 48px;
  color: #4caf50;
  margin-bottom: 12px;
}

.validation-section {
  margin-bottom: 16px;
}

.validation-section h4 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #333;
}

.validation-item {
  display: flex;
  gap: 8px;
  padding: 8px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.validation-item.error {
  background-color: #ffebee;
  border-left: 3px solid #f44336;
}

.validation-item.warning {
  background-color: #fff3e0;
  border-left: 3px solid #ff9800;
}

.validation-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.validation-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.validation-message {
  font-size: 13px;
  color: #333;
}

.validation-suggestion {
  font-size: 12px;
  color: #666;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
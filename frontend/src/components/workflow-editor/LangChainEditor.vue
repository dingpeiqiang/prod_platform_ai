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
        <button @click="renameWorkflow" :disabled="isReadOnly" class="btn-icon" title="重命名">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
            <path d="m15 3 4 4"/>
          </svg>
        </button>
        <div class="toolbar-divider"></div>
        <button @click="undo" :disabled="!canUndo || isReadOnly" class="btn-icon" title="撤销 (Ctrl+Z)">
          <Undo2 :size="16" />
        </button>
        <button @click="redo" :disabled="!canRedo || isReadOnly" class="btn-icon" title="重做 (Ctrl+Y)">
          <Redo2 :size="16" />
        </button>
        <div class="toolbar-divider"></div>
        <button @click="saveWorkflow" :disabled="!hasChanges || isReadOnly" class="btn-icon" title="保存 (Ctrl+S)">
          <Save :size="16" />
        </button>
        <button @click="exportWorkflow" class="btn-icon" title="导出">
          <Download :size="16" />
        </button>
        <button @click="editJson" :disabled="isReadOnly" class="btn-icon" title="编辑 JSON">
          <Code :size="16" />
        </button>
        <button @click="importWorkflow" :disabled="isReadOnly" class="btn-icon" title="导入">
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
        <button @click="clearWorkflow" :disabled="isReadOnly" class="btn-danger">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18"/>
            <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
            <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          </svg>
          清空
        </button>
        <span v-if="isReadOnly" class="read-only-badge">🔒 只读模式</span>
        <div class="toolbar-divider"></div>
        <button @click="toggleAnchorMode" class="btn-icon" :title="currentAnchorMode === 'horizontal' ? '当前：水平布局（点击切换为垂直）' : '当前：垂直布局（点击切换为水平）'">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path v-if="currentAnchorMode === 'vertical'" d="M12 5v14M5 12l7-7 7 7"/>
            <path v-else d="M5 12h14M12 5l-7 7 7 7"/>
          </svg>
        </button>
      </div>
      
      <div class="toolbar-center">
        <div class="align-group">
          <button @click="alignLeft" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="左对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="4" y1="12" x2="14" y2="12"/>
              <line x1="4" y1="18" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignCenter" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="水平居中">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="8" y1="12" x2="16" y2="12"/>
              <line x1="6" y1="18" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignRight" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="右对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="6" x2="20" y2="6"/>
              <line x1="10" y1="12" x2="20" y2="12"/>
              <line x1="6" y1="18" x2="20" y2="18"/>
            </svg>
          </button>
          <div class="toolbar-divider-small"></div>
          <button @click="alignTop" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="顶部对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="4" x2="12" y2="14"/>
              <line x1="18" y1="4" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignMiddle" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="垂直居中">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="18" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
          <button @click="alignBottom" :disabled="selectedNodeIds.length < 2 || isReadOnly" class="btn-icon" title="底部对齐">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="6" y1="4" x2="6" y2="20"/>
              <line x1="12" y1="10" x2="12" y2="20"/>
              <line x1="18" y1="6" x2="18" y2="20"/>
            </svg>
          </button>
        </div>
        <div class="toolbar-divider"></div>
        <div class="distribute-group">
          <button @click="distributeHorizontal" :disabled="selectedNodeIds.length < 3 || isReadOnly" class="btn-icon" title="水平分布">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="4" y1="8" x2="4" y2="16"/>
              <line x1="12" y1="8" x2="12" y2="16"/>
              <line x1="20" y1="8" x2="20" y2="16"/>
              <line x1="4" y1="12" x2="20" y2="12"/>
            </svg>
          </button>
          <button @click="distributeVertical" :disabled="selectedNodeIds.length < 3 || isReadOnly" class="btn-icon" title="垂直分布">
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
        <span class="save-status" :class="saveStatus.status">
          <span class="save-icon">{{ saveStatus.icon }}</span>
          <span class="save-text">{{ saveStatus.text }}</span>
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
        <WorkflowLibrary @load-workflow="(wf) => handleLoadFromLibrary(wf, wf.isCopy || false)" />
      </div>
      <div v-if="showLeftPanel" class="left-panel-wrapper">
        <NodePanel 
          :quick-templates="quickTemplates"
          :disabled="isReadOnly"
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
          :nodes-draggable="!isReadOnly"
          :nodes-connectable="!isReadOnly"
          :edges-connectable="!isReadOnly && isEdgeConnectable"
          :connect-on-drag="!isReadOnly"
          :auto-connect="false"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :edges-updatable="!isReadOnly"
          :edges-deletable="!isReadOnly"
          :delete-key-code="isReadOnly ? [] : ['Delete', 'Backspace']"
          :disable-pan="false"
          :prevent-scroll-on-drag="true"
          :direction="vueFlowDirection"
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
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              :execution-status="nodeExecutionStatus[props.id]"
              @update="updateNodeData"
            />
          </template>

          <template #node-end="props">
            <EndNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              :execution-status="nodeExecutionStatus[props.id]"
              :available-variables="getAvailableVariables(props.id)"
              @update="updateNodeData"
            />
          </template>

          <template #node-llm="props">
            <LlmNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              :available-variables="getAvailableVariables(props.id)"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-tool="props">
            <ToolNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              :available-variables="getAvailableVariables(props.id)"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-condition="props">
            <ConditionNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              :available-variables="getAvailableVariables(props.id)"
              @update="updateNodeData"
            />
          </template>

          <template #node-loop="props">
            <LoopNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-http="props">
            <HttpNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-code="props">
            <CodeNode
              :data="enrichNodeData(props.data, props.id)"
              :selected="props.selected"
              compact
              @update="updateNodeData"
            />
          </template>

          <template #node-parser="props">
          <ParserNode
            :data="enrichNodeData(props.data, props.id)"
            :selected="props.selected"
            compact
            @update="updateNodeData"
          />
        </template>

        <template #node-knowledgeBase="props">
          <KnowledgeNode
            :data="enrichNodeData(props.data, props.id)"
            :selected="props.selected"
            compact
            @update="updateNodeData"
          />
        </template>

        <template #node-userInput="props">
          <UserInputNode
            :data="enrichNodeData(props.data, props.id)"
            :selected="props.selected"
            compact
            :available-variables="getAvailableVariables(props.id)"
            @update="updateNodeData"
          />
        </template>

        <template #node-form="props">
          <FormNode
            :data="enrichNodeData(props.data, props.id)"
            :selected="props.selected"
            compact
            @update="updateNodeData"
          />
        </template>

        <template #node-validate="props">
          <ValidateNode
            :data="enrichNodeData(props.data, props.id)"
            :selected="props.selected"
            compact
            @update="updateNodeData"
          />
        </template>

        
      </VueFlow>
      </div>

      <!-- 全屏 JSON 编辑器 -->
      <transition name="fade">
        <div v-if="isFullscreenJson" class="fullscreen-json-editor">
          <div class="fullscreen-json-header">
            <div class="header-left">
              <h2>工作流 JSON 编辑器</h2>
              <div class="json-validation-status-lg" :class="jsonValidationStatus">
                {{ jsonValidationText }}
              </div>
            </div>
            <div class="header-right">
              <button @click="formatJson" class="json-tool-btn">格式化</button>
              <button @click="clearJson" class="json-tool-btn">清空</button>
              <button @click="loadCurrentWorkflow" class="json-tool-btn">加载当前</button>
              <button @click="closeFullscreenJson" class="json-close-btn">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="json-editor-content">
            <JsonEditor 
              v-model="jsonContent" 
              :read-only="isReadOnly"
              @change="validateJson"
              ref="jsonEditor"
            />
          </div>
          
          <div v-if="jsonError" class="json-error-message-lg">
            <span class="error-icon">✗</span>
            <span>{{ jsonError }}</span>
          </div>
          <div class="fullscreen-json-footer">
            <button @click="closeFullscreenJson" class="json-cancel-btn">取消</button>
            <button @click="applyJson" :disabled="!isJsonValid" class="json-apply-btn-lg">应用到画布</button>
            <button @click="saveJsonToDatabase" :disabled="!isJsonValid" class="json-save-btn">保存到数据库</button>
          </div>
        </div>
      </transition>

      <div class="node-config-drawer" :class="{ open: showNodeConfigPanel }">
        <NodeConfigPanel
          :node="selectedNodeData"
          :execution-status="selectedNodeExecutionStatus"
          :execution-time="selectedNodeExecutionTime"
          :available-variables="getAvailableVariables(selectedNodeId)"
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
            @click="switchPanel('validation')"
            :class="['panel-tab', { active: activePanel === 'validation' }]"
          >
            验证
            <span v-if="validationResults.errors.length > 0" class="error-count">{{ validationResults.errors.length }}</span>
          </button>
          <button
            @click="switchPanel('execution')"
            :class="['panel-tab', { active: activePanel === 'execution' }]"
          >
            执行日志
          </button>
          <button
            @click="switchExecutionHistoryPanel"
            :class="['panel-tab', { active: activePanel === 'history' }]"
          >
            执行历史
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
              :is-paused="isPaused"
              :pending-input="pendingInput"
              :waiting-form="pendingForm"
              :workflow-id="engineExecutionId || executionEngine.getWorkflowId()"
              :last-result="lastExecutionResult"
              :node-execution-data="nodeExecutionData"
              @clear="clearExecutionLogs"
              @resume="handleResume"
              @cancel="handleEngineCancel"
            />
          </div>

          <!-- P4：执行历史面板（引擎执行实例列表 + 详情 + 取消） -->
          <div v-show="activePanel === 'history'" class="panel-content-wrapper history-panel">
            <div class="history-toolbar">
              <input
                v-model="historyWorkflowFilter"
                type="text"
                class="history-filter-input"
                placeholder="按工作流代码过滤（留空查全部）"
              />
              <button @click="refreshExecutionHistory" class="history-refresh-btn" title="刷新">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="23 4 23 10 17 10"/>
                  <polyline points="1 20 1 14 7 14"/>
                </svg>
              </button>
            </div>

            <div v-if="historyLoading" class="history-loading">加载中...</div>
            <div v-else-if="executionHistory.length === 0" class="history-empty">暂无执行记录</div>

            <div v-else class="history-list">
              <div
                v-for="exec in executionHistory"
                :key="exec.execution_id"
                class="history-item"
                :class="exec.status"
                @click="showExecutionDetail(exec)"
              >
                <div class="history-item-main">
                  <span class="history-status-dot" :class="exec.status"></span>
                  <span class="history-code">{{ exec.workflow_code }}</span>
                  <span class="history-exec-id" :title="exec.execution_id">{{ exec.execution_id }}</span>
                </div>
                <div class="history-item-meta">
                  <span class="history-status-text">{{ historyStatusText(exec.status) }}</span>
                  <span v-if="exec.start_time" class="history-time">{{ formatHistoryTime(exec.start_time) }}</span>
                  <button
                    v-if="['running', 'waiting_human', 'pending'].includes(exec.status)"
                    @click.stop="cancelHistoryExecution(exec)"
                    class="history-cancel-btn"
                  >取消</button>
                </div>
              </div>
            </div>

            <div v-if="historyTotal > historyPageSize" class="history-pagination">
              <button :disabled="historyPage <= 1" @click="loadHistoryPage(historyPage - 1)" class="history-page-btn">上一页</button>
              <span class="history-page-info">{{ historyPage }} / {{ Math.ceil(historyTotal / historyPageSize) }}</span>
              <button :disabled="historyPage >= Math.ceil(historyTotal / historyPageSize)" @click="loadHistoryPage(historyPage + 1)" class="history-page-btn">下一页</button>
            </div>

            <!-- 执行详情弹窗 -->
            <div v-if="historyDetail" class="history-detail-overlay" @click.self="historyDetail = null">
              <div class="history-detail-modal">
                <div class="history-detail-header">
                  <h4>执行详情 · {{ historyDetail.execution_id }}</h4>
                  <button @click="historyDetail = null" class="history-close-btn">✕</button>
                </div>
                <div class="history-detail-body">
                  <div class="history-detail-row">
                    <span class="detail-label">状态</span>
                    <span :class="['history-status-dot', historyDetail.status]"></span>
                    {{ historyStatusText(historyDetail.status) }}
                  </div>
                  <div class="history-detail-row">
                    <span class="detail-label">工作流</span>
                    {{ historyDetail.workflow_code }} (v{{ historyDetail.workflow_version }})
                  </div>
                  <div class="history-detail-row" v-if="historyDetail.error_message">
                    <span class="detail-label">错误</span>
                    <span class="detail-error">{{ historyDetail.error_message }}</span>
                  </div>
                  <div class="history-detail-section">
                    <span class="detail-label">输入</span>
                    <pre class="detail-json">{{ formatHistoryJson(historyDetail.input_data) }}</pre>
                  </div>
                  <div class="history-detail-section" v-if="historyDetail.output_data">
                    <span class="detail-label">输出</span>
                    <pre class="detail-json">{{ formatHistoryJson(historyDetail.output_data) }}</pre>
                  </div>
                  <div class="history-detail-section" v-if="historyDetail.context_data">
                    <span class="detail-label">上下文</span>
                    <pre class="detail-json">{{ formatHistoryJson(historyDetail.context_data) }}</pre>
                  </div>
                </div>
                <div class="history-detail-footer">
                  <button
                    v-if="historyDetail.status === 'failed'"
                    @click="resumeHistoryExecution(historyDetail)"
                    class="history-resume-btn"
                  >失败重试（续跑）</button>
                  <button
                    v-if="['running', 'waiting_human', 'pending'].includes(historyDetail.status)"
                    @click="cancelHistoryExecution(historyDetail)"
                    class="history-cancel-btn"
                  >取消执行</button>
                </div>
              </div>
            </div>
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
import { Undo2, Redo2, Save, Download, Upload, Code } from 'lucide-vue-next';
import * as workflowApi from '@/services/workflowApi';
import { useWorkflowDataStore } from '@/stores/workflowData.js';
import { useModelsStore } from '@/stores/models.js';

import NodePanel from './NodePanel.vue';
import NodeConfigPanel from './NodeConfigPanel.vue';
import ExecutionPanel from './ExecutionPanel.vue';
import ParameterInputPanel from './ParameterInputPanel.vue';
import WorkflowLibrary from './WorkflowLibrary.vue';
import JsonEditor from './JsonEditor.vue';

import StartNode from './nodes/StartNode.vue';
import EndNode from './nodes/EndNode.vue';
import LlmNode from './nodes/LlmNode.vue';
import ToolNode from './nodes/ToolNode.vue';
import ConditionNode from './nodes/ConditionNode.vue';
import LoopNode from './nodes/LoopNode.vue';
import HttpNode from './nodes/HttpNode.vue';
import CodeNode from './nodes/CodeNode.vue';
import ParserNode from './nodes/ParserNode.vue';
import KnowledgeNode from './nodes/KnowledgeNode.vue';
import UserInputNode from './nodes/UserInputNode.vue';
import FormNode from './nodes/FormNode.vue';
import ValidateNode from './nodes/ValidateNode.vue';

import { debounce, validateWorkflow, alignNodes, distributeNodes, autoLayoutNodes, ensureUniqueNodeIds } from './utils/editorUtils';
import { ExecutionEngine } from './utils/executionEngine';
import { KeyboardShortcuts } from './utils/keyboardShortcuts';
import { validateConnection as validateConnectionRules } from './utils/connectionRules';

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

const { addEdges, removeNodes, removeEdges, project, updateEdge, getEdges, getNodes, fitView } = useVueFlow();

const getDefaultWorkflowElements = () => {
  return [
    {
      id: 'start-1',
      type: 'start',
      position: { x: 250, y: 50 },
      data: {
        label: '开始',
        parameters: []
      },
      class: 'start-node-default'
    },
    {
      id: 'end-1',
      type: 'end',
      position: { x: 250, y: 400 },
      data: {
        label: '结束'
      },
      class: 'end-node-default'
    }
  ];
};

const elements = ref(getDefaultWorkflowElements());
const hasChanges = ref(false);
const selectedNodeId = ref(null);
const selectedNodeIds = ref([]);
const selectedEdgeIds = ref([]);
const showLeftPanel = ref(true); // 控制左侧节点面板显示/隐藏
const showRightPanel = ref(false);
const showLibraryPanel = ref(false); // 控制工作流库面板显示/隐藏
const showNodeConfigPanel = ref(false);
const activePanel = ref('validation');
const lastNodeClick = ref({ id: null, time: 0 });
const DOUBLE_CLICK_MS = 320;
const showShortcuts = ref(false);
const connectionSuccess = ref(false);
const showJsonEditor = ref(false);
const jsonContent = ref('');
const jsonError = ref('');
const isJsonValid = ref(false);
const isFullscreenJson = ref(false);
const isEdgeConnectable = ref(true);
const isReadOnly = ref(false); // 工作流库加载的工作流为只读模式
const currentAnchorMode = ref('vertical'); // 当前锚点模式：vertical（垂直）或 horizontal（水平）
const vueFlowDirection = ref('TB'); // VueFlow 布局方向：TB（垂直，从上到下）或 LR（水平，从左到右）

// 连接阻止标志，用于协调 onConnect 和 onConnectEnd
const connectionBlocked = ref(false);

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
const isPaused = ref(false);
const pendingInput = ref(null);
const pendingForm = ref(null);
const lastExecutionResult = ref(null);
const copiedNodes = ref([]);
const nodeExecutionStatus = ref({});
const nodeExecutionData = ref([]);  // 结构化的节点执行数据

// 参数配置相关状态
const showParameterPanel = ref(false);
const executionParameters = ref([]);

// P3-1b：后端引擎执行状态（execution_id + resume_token 一次有效）
const engineExecutionId = ref(null);
const engineResumeToken = ref(null);

const executionEngine = new ExecutionEngine();

const AUTO_SAVE_INTERVAL = 10000;
let autoSaveTimer = null;
const isAutoSaving = ref(false);
const lastSavedTime = ref(null);

const checkPauseStatus = () => {
  isPaused.value = executionEngine.isExecutionPaused();
  pendingInput.value = executionEngine.getPendingInput();
  pendingForm.value = executionEngine.getPendingForm();
};

const handleResume = (userInputValue) => {
  // P3-1b 分流：后端引擎挂起 → human-resume 端点；前端单节点模拟挂起 → 旧引擎回调
  if (engineExecutionId.value && engineResumeToken.value) {
    // 简单输入（pendingInput 面板）提交的是字符串值；表单提交的是对象
    const formData = typeof userInputValue === 'object' && userInputValue !== null
      ? (userInputValue.formData || userInputValue)
      : { value: userInputValue };
    handleEngineHumanResume(formData);
    return;
  }
  executionEngine.resume(userInputValue);
};
const keyboardShortcuts = new KeyboardShortcuts();

// 模型 store：给工作流模板中的 LLM 节点填充后台默认模型，避免硬编码模型名
const modelsStore = useModelsStore();
const defaultModelName = computed(() => {
  const list = modelsStore.models;
  if (!list || list.length === 0) return '';
  return (list.find(m => m.isDefault) || list[0]).name || '';
});

const quickTemplates = ref([
  {
    id: 'simple-qa',
    name: '简单问答',
    description: '基础的问答流程，适合快速上手',
    nodes: [
      { type: 'start', x: 50, y: 200, title: '开始', parameters: [{ name: 'input', type: 'string', description: '用户输入', default: '', required: true }] },
      { type: 'llm', x: 450, y: 200, title: 'LLM', model: '', temperature: 0.7, prompt: '请回答以下问题：{{question}}' },
      { type: 'end', x: 650, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 }
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
      { type: 'llm', x: 650, y: 200, title: 'LLM分析', model: '', temperature: 0.5, prompt: '请分析以下数据：{{data}}' },
      { type: 'end', x: 850, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 4 }
    ]
  },
  {
    id: 'condition-branch',
    name: '条件分支',
    description: '根据条件判断走不同流程',
    nodes: [
      { type: 'start', x: 50, y: 250, title: '开始' },
      { type: 'llm', x: 350, y: 250, title: '意图识别', model: '', temperature: 0.3, prompt: '{{question}}' },
      { type: 'condition', x: 550, y: 250, title: '判断意图' },
      { type: 'http', x: 750, y: 150, title: '查询数据', method: 'GET' },
      { type: 'llm', x: 750, y: 350, title: '闲聊回复', model: '', temperature: 0.9, prompt: '用友好的语气回复：{{input}}' },
      { type: 'llm', x: 950, y: 150, title: '生成答案', model: '', temperature: 0.7 },
      { type: 'end', x: 1150, y: 250, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3, outputIndex: 0, inputIndex: 0 },
      { from: 2, to: 4, outputIndex: 1, inputIndex: 0 },
      { from: 3, to: 5 },
      { from: 4, to: 6 },
      { from: 5, to: 6 }
    ]
  },
  {
    id: 'code-execution',
    name: '代码执行',
    description: '生成并执行代码获取结果',
    nodes: [
      { type: 'start', x: 50, y: 200, title: '开始' },
      { type: 'llm', x: 250, y: 200, title: '生成代码', model: '', temperature: 0.3, prompt: '{{requirement}}' },
      { type: 'code', x: 450, y: 200, title: '执行代码', language: 'javascript' },
      { type: 'parser', x: 650, y: 200, title: '解析结果' },
      { type: 'end', x: 850, y: 200, title: '结束' }
    ],
    connections: [
      { from: 0, to: 1 },
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 4 }
    ]
  }
]);

const generateDefaultElements = () => {
  const template = quickTemplates.value[0];
  const elements = [];
  
  template.nodes.forEach((node, idx) => {
    const nodeId = `${node.type}_${uuidv4().slice(0, 6)}`;
    elements.push({
      id: nodeId,
      type: node.type,
      position: { x: node.x, y: node.y },
      data: { 
        label: node.title || node.type,
        ...node,
        model: node.type === 'llm' ? (node.model || defaultModelName.value) : node.model
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
  { id: 'llm', name: 'LLM调用' },
  { id: 'tool', name: 'MCP工具' },
  { id: 'http', name: 'HTTP请求' },
  { id: 'code', name: '代码执行' },
  { id: 'variable', name: '变量赋值' },
  { id: 'parser', name: '输出解析' }
];

const selectedNodeData = computed(() => {
  const result = elements.value.find(el => el.id === selectedNodeId.value && el.type && !el.source);
  console.log('[DEBUG] selectedNodeData computed:', selectedNodeId.value, result);
  return result;
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

const saveStatus = computed(() => {
  if (isAutoSaving.value) {
    return {
      status: 'saving',
      text: '保存中...',
      icon: '⏳'
    };
  }
  if (hasChanges.value) {
    return {
      status: 'unsaved',
      text: '未保存',
      icon: '⚠️'
    };
  }
  if (lastSavedTime.value) {
    const now = new Date();
    const diff = now - lastSavedTime.value;
    let timeAgo = '';
    if (diff < 5000) {
      timeAgo = '刚刚';
    } else if (diff < 60000) {
      timeAgo = `${Math.floor(diff / 1000)}秒前`;
    } else if (diff < 3600000) {
      timeAgo = `${Math.floor(diff / 60000)}分钟前`;
    } else {
      timeAgo = lastSavedTime.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }
    return {
      status: 'saved',
      text: `已保存 ${timeAgo}`,
      icon: '✓'
    };
  }
  return {
    status: 'idle',
    text: '',
    icon: ''
  };
});

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
  const validation = validateConnectionRules(params, elements.value);
  
  if (!validation.valid) {
    console.warn('连接验证失败:', validation.message);
    ElMessage.warning({
      message: validation.message,
      duration: 3000,
      showClose: true
    });
    
    connectionBlocked.value = true;
    setTimeout(() => {
      connectionBlocked.value = false;
    }, 100);
    
    return false;
  }
  
  // 显式创建边对象并添加到 elements
  const newEdge = {
    id: `edge-${uuidv4().slice(0, 8)}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    type: 'default',
    markerEnd: {
      type: 'arrowclosed',
      color: '#94a3b8'
    },
    style: {
      stroke: '#94a3b8',
      strokeWidth: 2.5
    },
    animated: false
  };
  
  // 使用 addEdges 显式添加边
  addEdges([newEdge]);
  
  saveHistory();
  markDirty();
  showConnectionSuccess();
};

// 连接结束时的智能吸附
const onConnectEnd = (event) => {
  if (connectionBlocked.value) return;
  
  if (!nearbyHandle.value) return;
  
  const snappedHandle = nearbyHandle.value;
  const handleId = snappedHandle.getAttribute('data-handleid');
  const nodeId = snappedHandle.closest('.vue-flow__node')?.getAttribute('data-id');
  const handleType = snappedHandle.classList.contains('target') ? 'target' : 'source';
  
  if (!nodeId || !handleId) return;
  
  setTimeout(() => {
    if (connectionBlocked.value) return;
    
    const edges = elements.value.filter(el => el.source && el.target);
    if (edges.length === 0) return;
    
    const lastEdge = edges[edges.length - 1];
    
    let needsCorrection = false;
    
    if (handleType === 'target') {
      if (lastEdge.target !== nodeId || lastEdge.targetHandle !== handleId) {
        needsCorrection = true;
      }
    } else if (handleType === 'source') {
      if (lastEdge.source !== nodeId || lastEdge.sourceHandle !== handleId) {
        needsCorrection = true;
      }
    }
    
    if (needsCorrection) {
      removeEdges([lastEdge.id]);
      
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
      
      const validation = validateConnectionRules(correctEdge, elements.value);
      if (validation.valid) {
        addEdges([correctEdge]);
        showConnectionSuccess();
      } else {
        ElMessage.warning({
          message: validation.message,
          duration: 3000,
          showClose: true
        });
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

// 计算节点的拓扑顺序（从 start 节点开始，按照边的连接关系遍历）
const getTopologicalNodeOrder = () => {
  const nodes = elements.value.filter(el => !el.source && !el.target);
  const edges = elements.value.filter(el => el.source && el.target);
  
  if (nodes.length === 0) return [];
  
  // 构建邻接表和入度表
  const adjacencyList = new Map();  // source -> [targets]
  const inDegree = new Map();       // nodeId -> in-degree count
  
  // 初始化
  nodes.forEach(node => {
    adjacencyList.set(node.id, []);
    inDegree.set(node.id, 0);
  });
  
  // 构建图
  edges.forEach(edge => {
    if (adjacencyList.has(edge.source) && inDegree.has(edge.target)) {
      adjacencyList.get(edge.source).push(edge.target);
      inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1);
    }
  });
  
  // Kahn's algorithm - BFS 拓扑排序
  const queue = [];
  const result = [];
  
  // 从 start 节点开始（入度为 0 的节点）
  nodes.forEach(node => {
    if (inDegree.get(node.id) === 0) {
      queue.push(node.id);
    }
  });
  
  // 如果没有 start 节点（入度为 0 的节点），使用原始顺序中的第一个
  if (queue.length === 0 && nodes.length > 0) {
    queue.push(nodes[0].id);
  }
  
  while (queue.length > 0) {
    const currentId = queue.shift();
    result.push(currentId);
    
    const neighbors = adjacencyList.get(currentId) || [];
    neighbors.forEach(neighborId => {
      const newDegree = inDegree.get(neighborId) - 1;
      inDegree.set(neighborId, newDegree);
      if (newDegree === 0) {
        queue.push(neighborId);
      }
    });
  }
  
  // 如果有节点未被访问（可能有环），追加到结果末尾
  nodes.forEach(node => {
    if (!result.includes(node.id)) {
      result.push(node.id);
    }
  });
  
  return result;
};

// 获取节点可用的变量列表
const getAvailableVariables = (nodeId) => {
  if (!nodeId) return [];
  
  const variables = [];
  const nodes = elements.value.filter(el => !el.source && !el.target);
  if (nodes.length === 0) return [];
  
  // 获取拓扑顺序
  const topoOrder = getTopologicalNodeOrder();
  
  // 找出当前节点在拓扑顺序中的索引
  const currentIndex = topoOrder.indexOf(nodeId);
  if (currentIndex === -1) return [];
  
  // 获取当前节点之前的所有节点 ID
  const precedingNodeIds = topoOrder.slice(0, currentIndex);
  
  // 记录已添加的变量引用，处理同名变量
  const addedVarRefs = new Set();
  
  // 遍历所有在当前节点之前的节点，收集它们的输出变量
  for (const precedingId of precedingNodeIds) {
    const node = nodes.find(n => n.id === precedingId);
    if (!node) continue;
    
    const nodeType = node.type;
    const nodeName = node.data?.label || nodeType;
    let outputVarName = '';
    
    switch (nodeType) {
      case 'start': {
        const startParams = node.data?.parameters;
        let paramsToAdd = [];
        
        if (!startParams || !Array.isArray(startParams) || startParams.length === 0) {
          paramsToAdd = [{ name: 'input', type: 'string', description: '用户输入', default: '', required: true }];
        } else {
          paramsToAdd = startParams;
        }
        
        paramsToAdd.forEach((param, index) => {
          if (param && param.name) {
            const varName = param.name;
            // 变量引用格式: ${nodeId}.output.${variableName}
            const varRef = `${node.id}.output.${varName}`;
            if (addedVarRefs.has(varRef)) return;
            addedVarRefs.add(varRef);
            
            variables.push({
              id: varRef,
              name: `${param.name} (入参)`,
              nodeId: node.id,
              nodeType: 'start',
              nodeName: '开始节点',
              type: param.type || 'string',
              source: 'workflow_input',
              sourceNodeType: 'start',
              sourceNodeName: '开始节点',
              varName: varName
            });
          }
        });
        break;
      }
      case 'variable':
        const varOutputs = node.data?.outputParams || [];
        if (varOutputs.length > 0) {
          varOutputs.forEach((output, index) => {
            if (output && output.name) {
              const varName = output.name;
              const varRef = `${node.id}.output.${varName}`;
              if (addedVarRefs.has(varRef)) return;
              addedVarRefs.add(varRef);
              
              variables.push({
                id: varRef,
                name: `${output.name} (输出)`,
                nodeId: node.id,
                nodeType: nodeType,
                nodeName: nodeName,
                type: node.data?.varType || 'any',
                source: 'node_output',
                sourceNodeType: nodeType,
                sourceNodeName: nodeName,
                varName: varName
              });
            }
          });
        } else {
          const outputVarName = node.data?.outputVar || node.data?.variable_name || node.data?.varName || node.data?.variableName || node.data?.label || nodeType;
          const varRef = `${node.id}.output.${outputVarName}`;
          if (addedVarRefs.has(varRef)) continue;
          addedVarRefs.add(varRef);
          
          variables.push({
            id: varRef,
            name: `${outputVarName} (输出)`,
            nodeId: node.id,
            nodeType: nodeType,
            nodeName: nodeName,
            type: node.data?.varType || 'any',
            source: 'node_output',
            sourceNodeType: nodeType,
            sourceNodeName: nodeName,
            varName: outputVarName
          });
        }
        break;
      case 'llm':
      case 'prompt':
      case 'tool':
      case 'http':
      case 'code':
      case 'parser':
      case 'userInput':
      case 'form':
      case 'knowledgeBase':
      case 'loop':
      case 'end': {
        const nodeOutputs = node.data?.outputParams || [];
        if (nodeOutputs.length > 0) {
          nodeOutputs.forEach((output, index) => {
            if (output && output.name) {
              const varName = output.name;
              const varRef = `${node.id}.output.${varName}`;
              if (addedVarRefs.has(varRef)) return;
              addedVarRefs.add(varRef);
              
              variables.push({
                id: varRef,
                name: `${output.name} (输出)`,
                nodeId: node.id,
                nodeType: nodeType,
                nodeName: nodeName,
                type: output.type || 'any',
                source: 'node_output',
                sourceNodeType: nodeType,
                sourceNodeName: nodeName,
                varName: varName
              });
            }
          });
        } else {
          outputVarName = node.data?.outputVar || node.data?.label || nodeType;
          const varRef = `${node.id}.output.${outputVarName}`;
          if (addedVarRefs.has(varRef)) continue;
          addedVarRefs.add(varRef);
          
          variables.push({
            id: varRef,
            name: `${outputVarName} (输出)`,
            nodeId: node.id,
            nodeType: nodeType,
            nodeName: nodeName,
            type: 'any',
            source: 'node_output',
            sourceNodeType: nodeType,
            sourceNodeName: nodeName,
            varName: outputVarName
          });
        }
        break;
      }
      case 'condition': {
        const condOutputs = node.data?.outputParams || [];
        if (condOutputs.length > 0) {
          condOutputs.forEach((output, index) => {
            if (output && output.name) {
              const varName = output.name;
              const varRef = `${node.id}.output.${varName}`;
              if (addedVarRefs.has(varRef)) return;
              addedVarRefs.add(varRef);
              
              variables.push({
                id: varRef,
                name: `${output.name} (输出)`,
                nodeId: node.id,
                nodeType: nodeType,
                nodeName: nodeName,
                type: output.type || 'any',
                source: 'node_output',
                sourceNodeType: nodeType,
                sourceNodeName: nodeName,
                varName: varName
              });
            }
          });
        } else {
          outputVarName = node.data?.label || '条件';
          const varRef = `${node.id}.output.${outputVarName}`;
          if (addedVarRefs.has(varRef)) continue;
          addedVarRefs.add(varRef);
          
          variables.push({
            id: varRef,
            name: `${outputVarName} (结果)`,
            nodeId: node.id,
            nodeType: 'condition',
            nodeName: nodeName,
            type: 'boolean',
            source: 'node_output',
            sourceNodeType: 'condition',
            sourceNodeName: nodeName,
            varName: outputVarName
          });
        }
        break;
      }
      default:
        outputVarName = node.data?.outputVar || node.data?.label || nodeType;
        const varRef = `${node.id}.output.${outputVarName}`;
        if (addedVarRefs.has(varRef)) continue;
        addedVarRefs.add(varRef);
        
        variables.push({
          id: varRef,
          name: `${outputVarName} (输出)`,
          nodeId: node.id,
          nodeType: nodeType,
          nodeName: nodeName,
          type: 'any',
          source: 'node_output',
          sourceNodeType: nodeType,
          sourceNodeName: nodeName,
          varName: outputVarName
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
  if (!selectedNodeId.value || isRunning.value) return;
  
  const node = elements.value.find(el => el.id === selectedNodeId.value && !el.source);
  if (!node) return;

  if (node.type === 'start' || node.type === 'end' || node.type === 'condition' || node.type === 'loop') {
    ElMessage.warning(`该节点类型不支持单节点运行: ${node.data.label}`);
    return;
  }

  closeNodeConfigPanel();
  showRightPanel.value = true;
  activePanel.value = 'execution';
  showParameterPanel.value = false;

  isRunning.value = true;
  executionLogs.value = [];
  lastExecutionResult.value = null;
  nodeExecutionData.value = [];

  const onStatusChange = (status) => {
    nodeExecutionStatus.value = status;
  };

  const onLog = (log) => {
    if (log.type === 'clear') {
      executionLogs.value = [];
    } else {
      executionLogs.value.push(log);
    }
    checkPauseStatus();
  };

  const onNodeDataChange = (data) => {
    nodeExecutionData.value = [...data];
  };

  executionEngine.setCallbacks(onStatusChange, onLog, onNodeDataChange);

  const inputData = {};
  if (node.data.inputParams && Array.isArray(node.data.inputParams)) {
    node.data.inputParams.forEach(param => {
      if (param.value && !param.value.startsWith('{{')) {
        inputData[param.name] = param.value;
      }
    });
  }

  try {
    const result = await executionEngine.executeSingleNode(node, inputData);
    lastExecutionResult.value = result;
    nodeExecutionData.value = executionEngine.getNodeExecutionData();
    
    // 检查节点执行状态
    const hasNodeError = nodeExecutionData.value.some(n => n.status === 'error');
    
    if (hasNodeError || result.status === 'error') {
      lastExecutionResult.value = { ...result, status: 'error' };
      ElMessage.error(`节点执行失败: ${result.error || '请查看错误详情'}`);
    } else {
      ElMessage.success(`节点执行成功: ${node.data.label}`);
    }
  } catch (error) {
    lastExecutionResult.value = {
      status: 'error',
      error: error.message,
      timestamp: new Date().toISOString()
    };
    nodeExecutionData.value = executionEngine.getNodeExecutionData();
    ElMessage.error(`节点执行异常: ${error.message}`);
    console.error('Single node execution error:', error);
  } finally {
    isRunning.value = false;
  }
};

const onPaneClick = () => {
  selectedEdgeIds.value.forEach(id => {
    updateEdgeStyle(id, false);
  });
  selectedEdgeIds.value = [];
  
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
  closeNodeConfigPanel();
};

const onEdgeClick = ({ edge, event }) => {
  event.stopPropagation();
  
  let wasSelected = false;
  if (event.shiftKey) {
    const idx = selectedEdgeIds.value.indexOf(edge.id);
    if (idx > -1) {
      selectedEdgeIds.value.splice(idx, 1);
      wasSelected = true;
    } else {
      selectedEdgeIds.value.push(edge.id);
    }
  } else {
    const previousSelected = [...selectedEdgeIds.value];
    selectedEdgeIds.value = [edge.id];
    
    previousSelected.forEach(id => {
      if (id !== edge.id) {
        updateEdgeStyle(id, false);
      }
    });
  }
  
  const isSelected = selectedEdgeIds.value.includes(edge.id);
  updateEdgeStyle(edge.id, isSelected);
  
  selectedNodeId.value = null;
  selectedNodeIds.value = [];
};

const updateEdgeStyle = (edgeId, isSelected) => {
  const edge = elements.value.find(el => el.id === edgeId);
  if (edge) {
    edge.style = {
      stroke: isSelected ? '#3b82f6' : '#94a3b8',
      strokeWidth: isSelected ? 4 : 2.5
    };
  }
};

const onHandleClick = ({ event, handle, node }) => {
};

const onHandleMouseDown = ({ event, handle, node }) => {
};

const animatingNodeId = ref(null);

const onNodeClick = (event, node) => {
  selectedEdgeIds.value.forEach(id => {
    updateEdgeStyle(id, false);
  });
  selectedEdgeIds.value = [];
  
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
    // 根据节点类型提供必要的默认数据
    const defaultData = {
      loop: {
        loopType: 'for',
        loopCount: 5
      },
      condition: {
        branches: [
          {
            type: 'if',
            expanded: true,
            handle: 'branch_0',
            conditions: [
              {
                variable: '',
                variableNodeId: '',
                variableCascaderValue: [],
                operator: '',
                valueType: 'input',
                value: '',
                valueNodeId: '',
                valueCascaderValue: []
              }
            ]
          },
          {
            type: 'else',
            expanded: true,
            handle: 'branch_else',
            conditions: []
          }
        ]
      },
      form: {
        ontologyCode: '',
        toolType: '',
        toolName: '',
        timeout: 60,
        model: '',
        temperature: 0.3,
        systemPrompt: `你是一个表单智能生成助手。请根据输入的本体信息和业务数据，完成以下任务：
1. 分析本体结构，生成合理的表单字段配置
2. 根据业务规则生成表单校验规则
3. 基于输入数据和推荐算法生成表单默认值和推荐填写内容
4. 调用MCP工具完成表单数据的提交和处理

请使用JSON格式输出结果，包含表单字段配置、校验规则、默认值和推荐建议。`,
        prompt: `根据以下本体信息和输入数据，生成表单配置和智能推荐：

【本体信息】
{ontology}

【输入参数】
{inputs}

请输出：
1. 表单字段配置（字段名、类型、标签、必填性）
2. 表单校验规则（格式验证、范围限制）
3. 字段默认值（基于输入数据推导）
4. 智能推荐建议（根据业务规则生成）

输出格式为JSON。`,
        inputParams: [
          { name: 'ontology', description: '本体编码', type: 'string', required: true, sourceType: 'input', value: '', refValue: '' },
          { name: 'inputs', description: '输入数据（JSON格式）', type: 'string', required: false, sourceType: 'input', value: '', refValue: '' }
        ],
        outputParams: [
          { name: 'formConfig', nameType: 'input', nameRef: '', source: '', type: 'object', description: '表单配置模型' },
          { name: 'validationRules', nameType: 'input', nameRef: '', source: '', type: 'object', description: '表单校验规则' },
          { name: 'defaultValues', nameType: 'input', nameRef: '', source: '', type: 'object', description: '字段默认值' },
          { name: 'recommendations', nameType: 'input', nameRef: '', source: '', type: 'object', description: '智能推荐建议' }
        ]
      }
    };
    
    const newNode = {
      id: `${nodeType.type}-${uuidv4().slice(0, 8)}`,
      type: nodeType.type,
      position,
      data: {
        label: nodeType.name,
        anchorMode: currentAnchorMode.value,
        ...defaultData[nodeType.type],
        ...nodeType
      }
    };
    
    // 使用数组扩展代替 push，触发 Vue 响应式更新
    elements.value = [...elements.value, newNode];
    saveHistory();
    markDirty();
    
    // 自动选中新创建的节点
    selectedNodeId.value = newNode.id;
    selectedNodeIds.value = [newNode.id];
  } catch (error) {
    console.error('拖拽节点失败:', error);
  }
};

const replaceVariableReferences = (oldName, newName) => {
  const regex = new RegExp(`{{\\s*${oldName}\\s*}}`, 'g');
  
  elements.value.forEach(el => {
    if (el.source) return;
    
    const replaceInObj = (obj) => {
      if (typeof obj === 'string') {
        return obj.replace(regex, `{{${newName}}}`);
      } else if (typeof obj === 'object' && obj !== null) {
        if (Array.isArray(obj)) {
          return obj.map(item => replaceInObj(item));
        } else {
          const result = {};
          for (const key in obj) {
            if (obj.hasOwnProperty(key)) {
              result[key] = replaceInObj(obj[key]);
            }
          }
          return result;
        }
      }
      return obj;
    };
    
    el.data = replaceInObj(el.data);
  });
};

const updateNodeData = (nodeId, data) => {
  saveHistory();
  
  const node = elements.value.find(el => el.id === nodeId);
  
  // 记录开始节点的旧参数（用于检测变化）
  const oldStartParams = node?.type === 'start' ? node.data?.parameters || [] : [];
  
  // 记录其他节点的旧 outputVar（用于检测变化）
  const outputVarNodes = ['llm', 'prompt', 'tool', 'http', 'code', 'parser', 'userInput', 'knowledgeBase'];
  const oldOutputVar = outputVarNodes.includes(node?.type) ? node.data?.outputVar : null;
  const newOutputVar = outputVarNodes.includes(node?.type) ? data?.outputVar : null;
  
  // 记录变量节点的旧变量名（用于检测变化）
  const oldVarName = node?.type === 'variable' ? node.data?.variable_name || node.data?.varName || node.data?.outputVar : null;
  const newVarName = node?.type === 'variable' ? data?.variable_name || data?.varName || data?.outputVar : null;
  
  if (node) {
    node.data = { ...node.data, ...data };
    // 删除旧的 outputs/inputs 字段，统一使用 outputParams/inputParams
    delete node.data.outputs;
    delete node.data.inputs;
    markDirty();
    
    // 处理变量节点的变量名变化
    if (oldVarName && newVarName && oldVarName !== newVarName) {
      replaceVariableReferences(oldVarName, newVarName);
    }
    
    // 处理开始节点的参数名变化
    if (node.type === 'start' && data.parameters && Array.isArray(data.parameters)) {
      const newStartParams = data.parameters;
      newStartParams.forEach((newParam, index) => {
        const oldParam = oldStartParams[index];
        if (oldParam && oldParam.name && newParam.name && oldParam.name !== newParam.name) {
          replaceVariableReferences(oldParam.name, newParam.name);
        }
      });
    }
    
    // 处理其他节点的 outputVar 变化
    if (oldOutputVar && newOutputVar && oldOutputVar !== newOutputVar) {
      replaceVariableReferences(oldOutputVar, newOutputVar);
    }
    
    // 触发响应式更新，确保 getAvailableVariables 等读取到最新数据
    elements.value = [...elements.value];
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

const loadWorkflows = async () => {
  try {
    const result = await workflowApi.workflowApi.getAllWorkflows();
    if (result.success && result.data) {
      workflows.value = result.data.map(wf => ({
        id: wf.workflowCode,
        name: wf.workflowName,
        description: wf.description,
        nodes: wf.workflowData?.nodes || [],
        edges: wf.workflowData?.edges || [],
        version: wf.workflowData?.version || '2.0',
        createdAt: wf.createdAt,
        updatedAt: wf.updatedAt,
        savedAt: wf.updatedAt
      }));
    } else {
      workflows.value = [];
    }
  } catch (error) {
    console.error('加载工作流列表失败:', error);
    workflows.value = [];
  }
};

const saveWorkflow = async (isAuto = false) => {
  const workflowData = {
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target),
    version: '2.0',
    savedAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  // 清理所有节点的旧 outputs/inputs 字段，统一使用 outputParams/inputParams
  workflowData.nodes.forEach(node => {
    if (node.data && 'outputs' in node.data) {
      delete node.data.outputs;
    }
    if (node.data && 'inputs' in node.data) {
      delete node.data.inputs;
    }
  });

  try {
    if (currentWorkflowId.value) {
      const updateResult = await workflowApi.workflowApi.update(currentWorkflowId.value, {
        workflowName: workflowName.value,
        workflowData: workflowData
      }, { showLoading: !isAuto });
      
      if (updateResult.success) {
        if (!isAuto) {
          ElMessage.success('工作流已保存');
        }
        const index = workflows.value.findIndex(w => w.id === currentWorkflowId.value);
        if (index !== -1) {
          workflows.value[index] = { 
            ...workflows.value[index], 
            ...workflowData,
            name: workflowName.value,
            updatedAt: new Date().toISOString()
          };
        }
        hasChanges.value = false;
        lastSavedTime.value = new Date();
      } else {
        if (!isAuto) {
          ElMessage.error('保存失败：' + (updateResult.message || '未知错误'));
        }
      }
    } else {
      const newId = uuidv4();
      
      const createResult = await workflowApi.workflowApi.create({
        workflowCode: newId,
        workflowName: workflowName.value,
        description: '',
        category: 'general',
        workflowData: workflowData
      }, { showLoading: !isAuto });
      
      if (createResult.success) {
        currentWorkflowId.value = newId;
        workflows.value.push({
          id: newId,
          name: workflowName.value,
          description: '',
          ...workflowData,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          savedAt: new Date().toISOString()
        });
        if (!isAuto) {
          ElMessage.success('工作流已创建并保存');
        }
        hasChanges.value = false;
        lastSavedTime.value = new Date();
      } else {
        if (!isAuto) {
          ElMessage.error('创建失败：' + (createResult.message || '未知错误'));
        }
      }
    }
  } catch (error) {
    console.error('保存工作流失败:', error);
    if (!isAuto) {
      ElMessage.error('保存失败：' + (error.message || '未知错误'));
    }
  }
};

const checkAndAutoSave = async () => {
  if (isAutoSaving.value) return;
  if (isReadOnly.value) return;
  if (!hasChanges.value) return;
  if (!currentWorkflowId.value) return;

  isAutoSaving.value = true;
  try {
    await saveWorkflow(true);
  } finally {
    isAutoSaving.value = false;
  }
};

const startAutoSaveTimer = () => {
  stopAutoSaveTimer();
  autoSaveTimer = setInterval(checkAndAutoSave, AUTO_SAVE_INTERVAL);
};

const stopAutoSaveTimer = () => {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer);
    autoSaveTimer = null;
  }
};

const openWorkflow = async (workflow) => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要打开其他工作流吗？')) {
      return;
    }
  }
  
  try {
    // 从后端重新加载最新数据
    const result = await workflowApi.workflowApi.get(workflow.id);
    
    if (result.success && result.data) {
      const workflowData = result.data.workflowData || {};
      
      currentWorkflowId.value = result.data.workflowCode;
      workflowName.value = result.data.workflowName;
      
      const nodes = (workflowData.nodes || []).map(node => ({
        id: node.id,
        type: node.type,
        position: node.position,
        data: node.data
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
      showWorkflowList.value = false;
      isReadOnly.value = false;
      
      ElMessage.success(`已打开工作流: ${result.data.workflowName}`);
    } else {
      ElMessage.error('加载工作流失败：' + (result.message || '未知错误'));
    }
  } catch (error) {
    console.error('打开工作流失败:', error);
    ElMessage.error('打开工作流失败：' + (error.message || '未知错误'));
  }
};

const handleLoadFromLibrary = (workflow, isCopy = false) => {
  if (hasChanges.value) {
    if (!confirm('当前工作流有未保存的更改，确定要加载工作流库中的工作流吗？')) {
      return;
    }
  }
  
  // currentWorkflowId: 优先使用 workflowCode（新版本完整数据），兼容 id（旧版本列表数据）
  currentWorkflowId.value = workflow.workflowCode || workflow.id;
  workflowName.value = workflow.workflowName;
  
  const workflowData = workflow.workflowData || {};
  const importNodes = (workflowData.nodes || []).map(node => ({
    id: node.id,
    type: node.type,
    position: node.position || { x: 0, y: 0 },
    // 深拷贝 data 对象，确保响应式更新正确触发
    data: JSON.parse(JSON.stringify(node.data || {}))
  }));
  
  // 确保工作流内部节点ID唯一（处理工作流文件本身可能存在的重复ID）
  const { nodes, idMapping } = ensureUniqueNodeIds(importNodes);
  
  // 更新边的引用
  const edges = (workflowData.edges || []).map(edge => ({
    id: edge.id,
    source: idMapping.get(edge.source) || edge.source,
    target: idMapping.get(edge.target) || edge.target,
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
  
  if (isCopy) {
    isReadOnly.value = false; // 复制的工作流可编辑
    const message = idMapping.size > 0 
      ? `工作流复制成功: ${workflow.workflowName}（${idMapping.size} 个节点ID已自动修正）`
      : `工作流复制成功: ${workflow.workflowName}`;
    ElMessage.success(message);
  } else {
    isReadOnly.value = true; // 直接加载的工作流设为只读模式
    if (idMapping.size > 0) {
      ElMessage.warning(`工作流加载成功，但发现 ${idMapping.size} 个重复节点ID已自动修正`);
    } else {
      ElMessage.info('工作流库中的工作流为只读模式，仅支持查看');
    }
  }
};

const deleteWorkflow = async (workflowId) => {
  if (!confirm('确定要删除这个工作流吗？')) {
    return;
  }
  
  try {
    const deleteResult = await workflowApi.workflowApi.delete(workflowId);
    
    if (deleteResult.success) {
      const index = workflows.value.findIndex(w => w.id === workflowId);
      if (index !== -1) {
        workflows.value.splice(index, 1);
        ElMessage.success('工作流已删除');
      }
      if (currentWorkflowId.value === workflowId) {
        // 删除的是当前工作流，重置画布为默认节点
        elements.value = getDefaultWorkflowElements();
        nextTick(() => fitView({ padding: 0.2, duration: 300 }));
        currentWorkflowId.value = null;
        workflowName.value = '未命名工作流';
        hasChanges.value = false;
      }
    } else {
      ElMessage.error('删除失败：' + (deleteResult.message || '未知错误'));
    }
  } catch (error) {
    console.error('删除工作流失败:', error);
    ElMessage.error('删除失败：' + (error.message || '未知错误'));
  }
};

const renameWorkflow = async () => {
  const newName = prompt('请输入工作流名称:', workflowName.value);
  if (!newName || !newName.trim()) {
    return;
  }
  
  const newDesc = prompt('请输入工作流描述（可选）:', '');
  
  if (currentWorkflowId.value) {
    try {
      const updateResult = await workflowApi.workflowApi.update(currentWorkflowId.value, {
        workflowName: newName.trim(),
        description: newDesc !== null ? newDesc.trim() : ''
      });
      
      if (updateResult.success) {
        workflowName.value = newName.trim();
        // 更新本地缓存的工作流列表
        const index = workflows.value.findIndex(w => w.id === currentWorkflowId.value);
        if (index !== -1) {
          workflows.value[index].name = newName.trim();
          workflows.value[index].description = newDesc !== null ? newDesc.trim() : '';
        }
        ElMessage.success('工作流名称已更新');
      } else {
        ElMessage.error('更新失败：' + (updateResult.message || '未知错误'));
      }
    } catch (error) {
      console.error('重命名工作流失败:', error);
      ElMessage.error('重命名失败：' + (error.message || '未知错误'));
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
            
            // 获取当前画布中已有的节点ID
            const existingNodeIds = elements.value
              .filter(el => !el.source && !el.target)
              .map(el => el.id);
            
            // 确保导入的节点ID唯一
            const importNodes = workflow.nodes.map(node => ({
              id: node.id,
              type: node.type,
              position: node.position,
              data: node.data
            }));
            
            const { nodes, idMapping } = ensureUniqueNodeIds(importNodes, existingNodeIds);
            
            // 更新边的引用
            const edges = workflow.edges ? workflow.edges.map(edge => ({
              id: edge.id,
              source: idMapping.get(edge.source) || edge.source,
              target: idMapping.get(edge.target) || edge.target,
              sourceHandle: edge.sourceHandle,
              targetHandle: edge.targetHandle,
              markerEnd: edge.markerEnd || {
                type: 'arrowclosed',
                color: '#94a3b8'
              }
            })) : [];
            
            elements.value = [...elements.value, ...nodes, ...edges];
            markDirty();
            
            // 提示用户是否有ID冲突被处理
            if (idMapping.size > 0) {
              ElMessage.info(`导入完成，${idMapping.size} 个节点ID因冲突已自动重新生成`);
            }
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

const jsonValidationStatus = computed(() => {
  if (!jsonContent.value.trim()) return 'empty';
  if (jsonError.value) return 'invalid';
  if (isJsonValid.value) return 'valid';
  return 'valid';
});

const jsonValidationText = computed(() => {
  if (!jsonContent.value.trim()) return '请输入 JSON';
  if (jsonError.value) return '❌ JSON 格式错误';
  if (isJsonValid.value) return '✓ JSON 格式有效';
  return '✓ JSON 格式有效';
});



const editJson = () => {
  loadCurrentWorkflow();
  isFullscreenJson.value = true;
};

const closeFullscreenJson = () => {
  isFullscreenJson.value = false;
};

const validateJson = () => {
  if (!jsonContent.value.trim()) {
    jsonError.value = '';
    isJsonValid.value = false;
    return;
  }
  
  try {
    const parsed = JSON.parse(jsonContent.value);
    jsonError.value = '';
    isJsonValid.value = true;
  } catch (error) {
    jsonError.value = error.message;
    isJsonValid.value = false;
  }
};

const formatJson = () => {
  if (!jsonContent.value.trim()) return;
  
  try {
    const parsed = JSON.parse(jsonContent.value);
    jsonContent.value = JSON.stringify(parsed, null, 2);
    jsonError.value = '';
    isJsonValid.value = true;
  } catch (error) {
    jsonError.value = error.message;
    isJsonValid.value = false;
  }
};

const clearJson = () => {
  jsonContent.value = '';
  jsonError.value = '';
  isJsonValid.value = false;
};

const loadCurrentWorkflow = () => {
  const workflow = {
    nodes: elements.value.filter(el => !el.source && !el.target),
    edges: elements.value.filter(el => el.source && el.target),
    version: '2.0'
  };
  jsonContent.value = JSON.stringify(workflow, null, 2);
  jsonError.value = '';
  isJsonValid.value = true;
};

const applyJson = () => {
  if (!isJsonValid.value) return;
  
  try {
    const workflow = JSON.parse(jsonContent.value);
    
    if (!workflow.nodes || !Array.isArray(workflow.nodes)) {
      ElMessage.error('无效的工作流格式：缺少 nodes 数组');
      return;
    }
    
    saveHistory();
    
    const importNodes = workflow.nodes.map(node => ({
      id: node.id,
      type: node.type,
      position: node.position || { x: 0, y: 0 },
      data: node.data || {}
    }));
    
    // 确保工作流内部节点ID唯一
    const { nodes, idMapping } = ensureUniqueNodeIds(importNodes);
    
    // 更新边的引用
    const edges = workflow.edges ? workflow.edges.map(edge => ({
      id: edge.id,
      source: idMapping.get(edge.source) || edge.source,
      target: idMapping.get(edge.target) || edge.target,
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
    markDirty();
    
    closeFullscreenJson();
    
    // 根据是否有ID冲突给出不同提示
    if (idMapping.size > 0) {
      ElMessage.success(`工作流已更新（${idMapping.size} 个重复节点ID已自动修正）`);
    } else {
      ElMessage.success('工作流已更新');
    }
  } catch (error) {
    ElMessage.error('应用失败：' + error.message);
  }
};

const saveJsonToDatabase = async () => {
  if (!isJsonValid.value) return;
  
  try {
    const workflowData = JSON.parse(jsonContent.value);
    
    if (!workflowData.nodes || !Array.isArray(workflowData.nodes)) {
      ElMessage.error('无效的工作流格式：缺少 nodes 数组');
      return;
    }

    workflowData.version = '2.0';
    workflowData.savedAt = new Date().toISOString();
    workflowData.updatedAt = new Date().toISOString();

    if (currentWorkflowId.value) {
      const updateResult = await workflowApi.workflowApi.update(currentWorkflowId.value, {
        workflowName: workflowName.value,
        workflowData: workflowData
      });
      
      if (updateResult.success) {
        ElMessage.success('工作流已保存到数据库');
        const index = workflows.value.findIndex(w => w.id === currentWorkflowId.value);
        if (index !== -1) {
          workflows.value[index] = { 
            ...workflows.value[index], 
            ...workflowData,
            name: workflowName.value,
            updatedAt: new Date().toISOString()
          };
        }
        hasChanges.value = false;
        closeFullscreenJson();
      } else {
        ElMessage.error('保存失败：' + (updateResult.message || '未知错误'));
      }
    } else {
      const newId = uuidv4();
      
      const createResult = await workflowApi.workflowApi.create({
        workflowCode: newId,
        workflowName: workflowName.value || '未命名工作流',
        description: '',
        category: 'general',
        workflowData: workflowData
      });
      
      if (createResult.success) {
        currentWorkflowId.value = newId;
        workflows.value.push({
          id: newId,
          name: workflowName.value || '未命名工作流',
          ...workflowData,
          updatedAt: new Date().toISOString()
        });
        ElMessage.success('工作流已保存到数据库');
        hasChanges.value = false;
        closeFullscreenJson();
      } else {
        ElMessage.error('创建失败：' + (createResult.message || '未知错误'));
      }
    }
  } catch (error) {
    ElMessage.error('保存失败：' + error.message);
  }
};

const clearWorkflow = () => {
  if (confirm('确定要清空工作流吗？')) {
    saveHistory();
    elements.value = getDefaultWorkflowElements();
    selectedNodeId.value = null;
    hasChanges.value = false;
    nextTick(() => fitView({ padding: 0.2, duration: 300 }));
  }
};

// 拓扑排序：按节点连接顺序排列
const topologicalSort = (nodes, edges) => {
  if (edges.length === 0) {
    // 没有边时按原始位置排序
    return [...nodes].sort((a, b) => a.position.y - b.position.y || a.position.x - b.position.x);
  }
  
  // 构建邻接表和入度表
  const inDegree = new Map();
  const outEdges = new Map();
  const nodeMap = new Map();
  
  nodes.forEach(node => {
    nodeMap.set(node.id, node);
    inDegree.set(node.id, 0);
    outEdges.set(node.id, []);
  });
  
  // 统计入度和出边
  edges.forEach(edge => {
    if (nodeMap.has(edge.source) && nodeMap.has(edge.target)) {
      inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1);
      outEdges.get(edge.source)?.push(edge.target);
    }
  });
  
  // Kahn算法：从入度为0的节点开始
  const queue = [];
  const result = [];
  
  nodes.forEach(node => {
    if (inDegree.get(node.id) === 0) {
      queue.push(node.id);
    }
  });
  
  // 按原始位置对队列排序，保持一致的相对顺序
  queue.sort((a, b) => {
    const nodeA = nodeMap.get(a);
    const nodeB = nodeMap.get(b);
    return (nodeA?.position?.y || 0) - (nodeB?.position?.y || 0) || 
           (nodeA?.position?.x || 0) - (nodeB?.position?.x || 0);
  });
  
  while (queue.length > 0) {
    const nodeId = queue.shift();
    const node = nodeMap.get(nodeId);
    if (node) result.push(node);
    
    const neighbors = outEdges.get(nodeId) || [];
    neighbors.forEach(targetId => {
      inDegree.set(targetId, inDegree.get(targetId) - 1);
      if (inDegree.get(targetId) === 0) {
        queue.push(targetId);
        // 保持队列有序
        queue.sort((a, b) => {
          const nodeA = nodeMap.get(a);
          const nodeB = nodeMap.get(b);
          return (nodeA?.position?.y || 0) - (nodeB?.position?.y || 0) || 
                 (nodeA?.position?.x || 0) - (nodeB?.position?.x || 0);
        });
      }
    });
  }
  
  // 如果有环（仍有节点未访问），将剩余节点按原始顺序追加
  if (result.length < nodes.length) {
    const visited = new Set(result.map(n => n.id));
    nodes.forEach(node => {
      if (!visited.has(node.id)) {
        result.push(node);
      }
    });
  }
  
  return result;
};

// 切换锚点模式（水平/垂直布局）
// 语义统一：anchorMode 名称与布局方向一致
// 'horizontal' = 水平布局 + 水平锚点(top/bottom)
// 'vertical' = 垂直布局 + 垂直锚点(left/right)
const toggleAnchorMode = () => {
  // 切换模式
  const newMode = currentAnchorMode.value === 'horizontal' ? 'vertical' : 'horizontal';
  // 方向：horizontal=LR(左到右), vertical=TB(上到下)
  const newDirection = newMode === 'horizontal' ? 'LR' : 'TB';
  currentAnchorMode.value = newMode;
  vueFlowDirection.value = newDirection;

  // 获取所有节点和边
  const nodes = elements.value.filter(el => !el.source && !el.target);
  const edges = elements.value.filter(el => el.source && el.target);

  if (nodes.length > 0) {
    // 定义节点尺寸常量（与UI统一优化后的尺寸一致）
    const NODE_WIDTH = 180;  // 统一的最小宽度
    const NODE_HEIGHT = 120; // 统一节点最小高度（与CSS中的min-height一致）
    
    // 定义合理的间距（确保节点之间有足够间隙不重叠）
    const HORIZONTAL_SPACING = 80; // 水平间距
    const VERTICAL_SPACING = 80;   // 垂直间距
    const MARGIN = 100;            // 画布边距
    
    // 计算节点间的总间距（用于保持布局一致性）
    const NODE_HORIZONTAL_GAP = NODE_WIDTH + HORIZONTAL_SPACING;
    const NODE_VERTICAL_GAP = NODE_HEIGHT + VERTICAL_SPACING;

    // 拓扑排序：按节点连接顺序排列
    const sortedNodes = topologicalSort(nodes, edges);

    if (newMode === 'horizontal') {
      // 水平布局：从左到右排列，所有节点在同一水平线上
      let currentX = MARGIN;
      
      sortedNodes.forEach((node) => {
        node.position.x = currentX;
        node.position.y = MARGIN; // y 固定
        currentX += NODE_HORIZONTAL_GAP;
        
        node.data = { 
          ...node.data, 
          anchorMode: newMode,
          _layoutTimestamp: Date.now()
        };
      });
    } else {
      // 垂直布局：从上到下排列，所有节点在同一垂直线上
      let currentY = MARGIN;
      
      sortedNodes.forEach((node) => {
        node.position.x = MARGIN; // x 固定
        node.position.y = currentY;
        currentY += NODE_VERTICAL_GAP;
        
        node.data = { 
          ...node.data, 
          anchorMode: newMode,
          _layoutTimestamp: Date.now()
        };
      });
    }
  }

  // 强制触发 Vue Flow 重新渲染
  // 通过创建新数组引用触发响应式更新
  const oldElements = [...elements.value];
  elements.value = [];
  
  // 使用 setTimeout 确保 DOM 完全清空后再重新渲染
  setTimeout(() => {
    elements.value = oldElements;
    hasChanges.value = true;
    console.log(`锚点模式已切换为: ${newMode}, 方向: ${newDirection}`);
  }, 10);
};

const runWorkflowWithPanel = () => {
  // 1. 找到开始节点，提取其参数定义
  const startNode = elements.value.find(el => el.type === 'start');
  if (!startNode) {
    ElMessage.warning('未找到开始节点');
    return;
  }

  const startParams = startNode.data?.parameters || [];

  // 2. 将开始节点的参数定义填充到 executionParameters（显示在参数输入面板供用户填写）
  executionParameters.value = startParams.map(p => ({
    name: p.name,
    type: p.type,
    description: p.description || '',
    required: p.required ?? false,
    value: p.default ?? ''   // 用开始节点的默认值初始化
  }));

  // 3. 打开右侧执行面板 + 显示参数输入面板
  closeNodeConfigPanel();
  showRightPanel.value = true;
  activePanel.value = 'execution';
  showParameterPanel.value = true;
};

const runWorkflow = async (inputParams = {}) => {
  if (!isValid.value || isRunning.value) return;

  isRunning.value = true;
  executionLogs.value = [];
  lastExecutionResult.value = null;
  nodeExecutionData.value = [];

  try {
    // P3-1b：全流程执行切换后端固定流程引擎（去前端模拟）
    // 引擎归一化编辑器形态并守门，节点留痕落库，前端轮询逐节点点亮
    const startResp = await workflowApi.workflowApi.startEngineExecution(
      currentWorkflowId.value, inputParams, { showLoading: false });

    if (!startResp.success) {
      throw new Error(startResp.message || '流程启动失败');
    }
    const executionId = startResp.data?.execution_id;
    if (!executionId) {
      throw new Error('引擎未返回 execution_id');
    }

    engineExecutionId.value = executionId;
    engineResumeToken.value = startResp.data?.resume_token || null;

    // 已同步走完的（无 human 挂起）直接拉取节点日志；挂起的展示输入面板等人工提交
    if (startResp.data?.status === 'waiting_human') {
      isPaused.value = true;
      pendingInput.value = buildEnginePendingInput(startResp.data?.current_node_id);
      await refreshEngineNodeLogs(executionId);
      ElMessage.info('流程在人工节点暂停，请在下方填写并提交');
    } else {
      await refreshEngineNodeLogs(executionId);
    }

    const finalStatus = startResp.data?.status;
    lastExecutionResult.value = {
      status: finalStatus === 'completed' ? 'success' : (finalStatus === 'waiting_human' ? 'paused' : 'error'),
      error: startResp.data?.error_message,
      context: startResp.data?.context_data,
      timestamp: new Date().toISOString()
    };

    if (finalStatus === 'completed') {
      ElMessage.success('流程执行成功');
    } else if (finalStatus === 'failed') {
      ElMessage.error('流程执行失败，请查看错误详情');
    }
  } catch (error) {
    lastExecutionResult.value = {
      status: 'error',
      error: error.message,
      timestamp: new Date().toISOString()
    };
    ElMessage.error(`流程执行异常: ${error.message}`);
    console.error('Workflow execution error:', error);
  } finally {
    isRunning.value = false;
  }
};

/** 拉取引擎节点日志并映射为执行面板形态（node_id/node_type/status/error_message → nodeId/nodeType/status/error）。 */
const refreshEngineNodeLogs = async (executionId) => {
  const logsResp = await workflowApi.workflowApi.getEngineNodeLogs(executionId);
  if (!logsResp.success) {
    throw new Error(logsResp.message || '获取节点日志失败');
  }
  const nodeLogs = logsResp.data?.node_logs || [];
  nodeExecutionData.value = nodeLogs.map(log => {
    const nodeLabel = elements.value.find(el => el.id === log.node_id)?.data?.label;
    return {
      nodeId: log.node_id,
      nodeType: mapEngineNodeType(log.node_type),
      nodeLabel: nodeLabel || log.node_id,
      status: log.status === 'completed' ? 'completed'
        : (log.status === 'failed' || log.status === 'cancelled' ? 'error' : 'running'),
      startTime: log.started_at,
      endTime: log.ended_at,
      duration: log.duration_ms,
      input: log.input_data,
      output: log.output_data,
      branchTaken: log.branch_taken,
      error: log.error_message
    };
  });
};

/** 引擎节点类型 → 执行面板 icon 映射键（nodeIconMap 用编辑器 type 键）。 */
const mapEngineNodeType = (engineType) => {
  const mapping = {
    'flow.start': 'start', 'flow.end': 'end', 'flow.tool': 'tool',
    'flow.llm': 'llm', 'flow.condition': 'condition',
    'flow.human': 'userInput', 'flow.http': 'http'
  };
  return mapping[engineType] || engineType;
};

/** 引擎人工挂起 → 构造执行面板 pendingInput（驱动输入面板渲染与 prompt 展示）。 */
const buildEnginePendingInput = (nodeId) => {
  const nodeData = elements.value.find(el => el.id === nodeId)?.data || {};
  return {
    nodeId,
    prompt: nodeData.prompt || nodeData.message || nodeData.label || '请输入确认内容：',
    inputType: nodeData.inputType || 'text',
    options: typeof nodeData.options === 'string'
      ? nodeData.options.split('\n').filter(opt => opt.trim())
      : (Array.isArray(nodeData.options) ? nodeData.options : []),
    required: false
  };
};

/** 人工节点恢复：提交表单数据至引擎 human-resume 端点（令牌一次有效），继续轮询至终态。 */
const handleEngineHumanResume = async (formData) => {
  if (!engineExecutionId.value || !engineResumeToken.value) return;
  isRunning.value = true;
  try {
    const resp = await workflowApi.workflowApi.humanResumeEngine(
      engineExecutionId.value, engineResumeToken.value, formData);
    if (!resp.success) {
      throw new Error(resp.message || '人工确认提交失败');
    }
    // 令牌一次有效：用后即清
    engineResumeToken.value = resp.data?.resume_token || null;
    isPaused.value = resp.data?.status === 'waiting_human';
    // 下一个人工节点 → 重建输入面板；其余终态 → 清空
    pendingInput.value = resp.data?.status === 'waiting_human'
      ? buildEnginePendingInput(resp.data?.current_node_id) : null;
    await refreshEngineNodeLogs(engineExecutionId.value);

    lastExecutionResult.value = {
      status: resp.data?.status === 'completed' ? 'success' : (resp.data?.status === 'waiting_human' ? 'paused' : 'error'),
      error: resp.data?.error_message,
      context: resp.data?.context_data,
      timestamp: new Date().toISOString()
    };
    if (resp.data?.status === 'completed') {
      ElMessage.success('流程执行成功');
    } else if (resp.data?.status === 'waiting_human') {
      ElMessage.info('流程到达下一个人工节点');
    } else if (resp.data?.status === 'failed') {
      ElMessage.error('流程执行失败，请查看错误详情');
    }
  } catch (error) {
    ElMessage.error(`人工确认提交异常: ${error.message}`);
    console.error('Human resume error:', error);
  } finally {
    isRunning.value = false;
  }
};

const handleParameterExecute = (params) => {
  // 关闭参数面板
  showParameterPanel.value = false;
  // 执行工作流并传入参数
  runWorkflow(params);
};

/** P4：取消后端引擎执行（仅引擎执行挂起/运行中时有效），终态拒绝由后端兜底。 */
const handleEngineCancel = async () => {
  if (!engineExecutionId.value) {
    // 前端单节点模拟执行无引擎实例，仅重置本地状态
    isRunning.value = false;
    isPaused.value = false;
    pendingInput.value = null;
    return;
  }
  try {
    const resp = await workflowApi.workflowApi.cancelEngineExecution(
      engineExecutionId.value, '编辑器内人工取消');
    if (!resp.success) {
      throw new Error(resp.message || '取消执行失败');
    }
    isPaused.value = false;
    pendingInput.value = null;
    engineResumeToken.value = null;
    await refreshEngineNodeLogs(engineExecutionId.value);
    lastExecutionResult.value = {
      status: 'error',
      error: '执行已取消',
      timestamp: new Date().toISOString()
    };
    ElMessage.info('执行已取消');
  } catch (error) {
    ElMessage.error(`取消执行异常: ${error.message}`);
    console.error('Cancel execution error:', error);
  }
};

// ── P4：执行历史面板（引擎执行实例列表 + 详情 + 取消/重试） ──
const executionHistory = ref([]);
const historyLoading = ref(false);
const historyWorkflowFilter = ref('');
const historyPage = ref(1);
const historyPageSize = 20;
const historyTotal = ref(0);
const historyDetail = ref(null);

const historyStatusText = (status) => ({
  running: '运行中', waiting_human: '等待人工', pending: '待执行',
  completed: '已完成', failed: '已失败', cancelled: '已取消'
}[status] || status);

const formatHistoryTime = (ts) => {
  if (!ts) return '';
  return String(ts).replace('T', ' ').substring(0, 19);
};

const formatHistoryJson = (data) => {
  if (!data) return '';
  try { return JSON.stringify(data, null, 2); } catch { return String(data); }
};

const refreshExecutionHistory = async () => {
  historyPage.value = 1;
  await loadHistoryPage(1);
};

const loadHistoryPage = async (page) => {
  historyLoading.value = true;
  try {
    const resp = await workflowApi.workflowApi.listEngineExecutions(
      historyWorkflowFilter.value.trim() || null, page, historyPageSize);
    if (!resp.success) {
      throw new Error(resp.message || '加载执行历史失败');
    }
    executionHistory.value = resp.data?.data || [];
    historyTotal.value = resp.data?.total || 0;
    historyPage.value = resp.data?.page || page;
  } catch (error) {
    ElMessage.error(`加载执行历史失败: ${error.message}`);
    console.error('Load execution history error:', error);
  } finally {
    historyLoading.value = false;
  }
};

const showExecutionDetail = async (exec) => {
  try {
    const resp = await workflowApi.workflowApi.getEngineExecution(exec.execution_id);
    if (!resp.success) {
      throw new Error(resp.message || '加载执行详情失败');
    }
    historyDetail.value = resp.data;
  } catch (error) {
    ElMessage.error(`加载执行详情失败: ${error.message}`);
  }
};

const cancelHistoryExecution = async (exec) => {
  try {
    const resp = await workflowApi.workflowApi.cancelEngineExecution(exec.execution_id, '历史面板人工取消');
    if (!resp.success) {
      throw new Error(resp.message || '取消执行失败');
    }
    ElMessage.info(`执行 ${exec.execution_id} 已取消`);
    historyDetail.value = null;
    await loadHistoryPage(historyPage.value);
  } catch (error) {
    ElMessage.error(`取消执行失败: ${error.message}`);
  }
};

const resumeHistoryExecution = async (exec) => {
  try {
    const resp = await workflowApi.workflowApi.resumeEngineExecution(exec.execution_id);
    if (!resp.success) {
      throw new Error(resp.message || '重试失败');
    }
    ElMessage.success(`执行 ${exec.execution_id} 已重新启动`);
    historyDetail.value = resp.data;
    await loadHistoryPage(historyPage.value);
  } catch (error) {
    ElMessage.error(`重试失败: ${error.message}`);
  }
};

const closeParameterPanel = () => {
  showParameterPanel.value = false;
};

const clearExecutionLogs = () => {
  executionLogs.value = [];
  lastExecutionResult.value = null;
  nodeExecutionData.value = [];
  executionEngine.clearNodeExecutionData();
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
  
  const edgesToDelete = selectedEdgeIds.value.length > 0 
    ? selectedEdgeIds.value 
    : [];
  
  if (nodesToDelete.length > 0 || edgesToDelete.length > 0) {
    saveHistory();
    
    if (edgesToDelete.length > 0) {
      removeEdges(edgesToDelete);
    }
    
    if (nodesToDelete.length > 0) {
      removeNodes(nodesToDelete);
      const connectedEdges = elements.value.filter(
        el => nodesToDelete.includes(el.source) || nodesToDelete.includes(el.target)
      );
      if (connectedEdges.length > 0) {
        removeEdges(connectedEdges.map(e => e.id));
      }
    }
    
    selectedEdgeIds.value = [];
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
    const nodeId = `${node.type}_${uuidv4().slice(0, 6)}`;
    newElements.push({
      id: nodeId,
      type: node.type,
      position: { x: node.x, y: node.y },
      data: { 
        label: node.title || node.type,
        anchorMode: currentAnchorMode.value,
        ...node,
        model: node.type === 'llm' ? (node.model || defaultModelName.value) : node.model
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

const switchPanel = (panelName) => {
  if (activePanel.value === panelName) {
    showRightPanel.value = !showRightPanel.value;
  } else {
    activePanel.value = panelName;
    showRightPanel.value = true;
  }
  
  if (panelName === 'execution') {
    closeNodeConfigPanel();
  }
};

/** P4：切换到执行历史面板时刷新列表。 */
const switchExecutionHistoryPanel = () => {
  if (activePanel.value === 'history') {
    showRightPanel.value = !showRightPanel.value;
  } else {
    activePanel.value = 'history';
    showRightPanel.value = true;
  }
  refreshExecutionHistory();
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
    closeNodeConfigPanel();
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

let pauseCheckInterval = null;

onMounted(async () => {
  registerShortcuts();
  window.addEventListener('keydown', handleKeydown);
  
  pauseCheckInterval = setInterval(() => {
    if (isRunning.value || isPaused.value) {
      checkPauseStatus();
    }
  }, 500);
  
  const workflowDataStore = useWorkflowDataStore();
  await Promise.all([
    workflowDataStore.loadOntologies(),
    workflowDataStore.loadMCPTools(),
    modelsStore.loadModels()
  ]);
  
  await loadWorkflows();
  
  if (props.workflowCode) {
    try {
      const result = await workflowApi.workflowApi.get(props.workflowCode);
      if (result.success && result.data) {
        const workflow = result.data;
        currentWorkflowId.value = workflow.workflowCode || workflow.id;
        workflowName.value = workflow.workflowName;
        
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
        await autoSaveNewWorkflow();
      }
    } catch (error) {
      console.error('加载工作流失败:', error);
      ElMessage.error('加载工作流失败');
      await autoSaveNewWorkflow();
    }
  } else {
    // 新建工作流，自动保存
    await autoSaveNewWorkflow();
  }
  
  history.value.push(JSON.stringify(elements.value));
  historyIndex.value = 0;
  
  startAutoSaveTimer();
});

const autoSaveNewWorkflow = async () => {
  try {
    const nodes = elements.value.filter(el => !el.source && !el.target);
    const edges = elements.value.filter(el => el.source && el.target);
    
    const workflowData = {
      nodes: nodes.map(node => ({
        id: node.id,
        type: node.type,
        position: node.position,
        data: node.data
      })),
      edges: edges.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        sourceHandle: edge.sourceHandle,
        targetHandle: edge.targetHandle
      }))
    };
    
    const newId = uuidv4();
    
    const saveResult = await workflowApi.workflowApi.create({
      workflowCode: newId,
      workflowName: workflowName.value || '未命名工作流',
      description: '',
      category: 'general',
      workflowData: workflowData
    });
    
    if (saveResult.success) {
      currentWorkflowId.value = newId;
      workflows.value.push({
        id: newId,
        name: workflowName.value || '未命名工作流',
        description: '',
        ...workflowData,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        savedAt: new Date().toISOString()
      });
      hasChanges.value = false;
      ElMessage.success('新工作流已创建');
    } else {
      ElMessage.error('创建工作流失败: ' + (saveResult.message || '未知错误'));
    }
  } catch (error) {
    console.error('创建工作流失败:', error);
    ElMessage.error('创建工作流失败: ' + error.message);
  }
};

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  stopAutoSaveTimer();
  if (pauseCheckInterval) {
    clearInterval(pauseCheckInterval);
    pauseCheckInterval = null;
  }
});
</script>

<style>
.langchain-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
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
  opacity: 1;
  visibility: visible;
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

.save-status {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.save-status.saving {
  background-color: #e3f2fd;
  color: #1976d2;
}

.save-status.unsaved {
  background-color: #fff3e0;
  color: #ef6c00;
  animation: pulse-warning 2s infinite;
}

.save-status.saved {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.save-status.idle {
  display: none;
}

.save-icon {
  font-size: 12px;
}

.save-text {
  font-size: 12px;
}

@keyframes pulse-warning {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
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
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
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
  width: 620px;
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

/* ── P4：执行历史面板 ── */
.history-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.history-filter-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
}

.history-filter-input:focus {
  outline: none;
  border-color: #1890ff;
}

.history-refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  color: #595959;
}

.history-refresh-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.history-loading,
.history-empty {
  text-align: center;
  color: #8c8c8c;
  padding: 24px 0;
  font-size: 13px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-item {
  padding: 8px 10px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  transition: border-color 0.2s;
}

.history-item:hover {
  border-color: #91d5ff;
}

.history-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.history-status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.history-status-dot.running { background: #1890ff; }
.history-status-dot.waiting_human { background: #fa8c16; }
.history-status-dot.pending { background: #bfbfbf; }
.history-status-dot.completed { background: #52c41a; }
.history-status-dot.failed { background: #ff4d4f; }
.history-status-dot.cancelled { background: #8c8c8c; }

.history-code {
  font-size: 13px;
  font-weight: 500;
  color: #262626;
}

.history-exec-id {
  font-size: 11px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #595959;
}

.history-status-text {
  font-size: 12px;
}

.history-time {
  color: #8c8c8c;
  font-size: 11px;
}

.history-cancel-btn {
  margin-left: auto;
  padding: 2px 10px;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  background: white;
  color: #ff4d4f;
  font-size: 11px;
  cursor: pointer;
}

.history-cancel-btn:hover {
  background: #fff2f0;
}

.history-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
}

.history-page-btn {
  padding: 4px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  font-size: 12px;
  cursor: pointer;
}

.history-page-btn:disabled {
  color: #bfbfbf;
  cursor: not-allowed;
}

.history-page-info {
  font-size: 12px;
  color: #595959;
}

.history-detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.history-detail-modal {
  width: 560px;
  max-width: 90vw;
  max-height: 80vh;
  background: white;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.history-detail-header h4 {
  margin: 0;
  font-size: 14px;
  color: #262626;
}

.history-close-btn {
  border: none;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  color: #8c8c8c;
}

.history-detail-body {
  padding: 12px 16px;
  overflow-y: auto;
  flex: 1;
}

.history-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  color: #262626;
}

.detail-label {
  min-width: 48px;
  color: #8c8c8c;
  font-size: 12px;
  flex-shrink: 0;
}

.detail-error {
  color: #ff4d4f;
  word-break: break-all;
}

.history-detail-section {
  margin-top: 10px;
}

.detail-json {
  margin: 6px 0 0;
  padding: 10px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  max-height: 180px;
  overflow: auto;
}

.history-detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.history-resume-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  background: #1890ff;
  color: white;
  font-size: 13px;
  cursor: pointer;
}

.history-resume-btn:hover {
  background: #40a9ff;
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

.read-only-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background-color: #fef3c7;
  color: #b45309;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  margin-left: 8px;
}

/* 全屏 JSON 编辑器 */
.fullscreen-json-editor {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #1e1e1e;
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.fullscreen-json-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #d4d4d4;
}

.json-validation-status-lg {
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 4px;
}

.json-validation-status-lg.empty {
  color: #94a3b8;
  background: #2d2d30;
}

.json-validation-status-lg.valid {
  color: #10b981;
  background: rgba(16, 185, 129, 0.2);
}

.json-validation-status-lg.invalid {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.2);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.json-tool-btn {
  padding: 8px 16px;
  font-size: 13px;
  background: #3c3c3c;
  border: 1px solid #4c4c4c;
  border-radius: 4px;
  cursor: pointer;
  color: #d4d4d4;
  transition: all 0.2s;
}

.json-tool-btn:hover {
  background: #4c4c4c;
  border-color: #5c5c5c;
}

.json-close-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: #858585;
  transition: all 0.2s;
}

.json-close-btn:hover {
  background: #3c3c3c;
  color: #d4d4d4;
}

.json-editor-content {
  flex: 1;
  overflow: auto;
  position: relative;
  min-height: 0;
}

.json-error-message-lg {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  background: rgba(239, 68, 68, 0.15);
  color: #ff6b6b;
  border-top: 1px solid rgba(239, 68, 68, 0.3);
  font-size: 13px;
}

.fullscreen-json-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  background: #252526;
  border-top: 1px solid #3c3c3c;
}

.json-cancel-btn {
  padding: 10px 24px;
  font-size: 14px;
  background: #3c3c3c;
  border: 1px solid #4c4c4c;
  border-radius: 4px;
  cursor: pointer;
  color: #d4d4d4;
  transition: all 0.2s;
}

.json-cancel-btn:hover {
  background: #4c4c4c;
}

.json-apply-btn-lg {
  padding: 10px 28px;
  font-size: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: white;
  font-weight: 500;
  transition: all 0.2s;
}

.json-apply-btn-lg:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.json-apply-btn-lg:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.json-save-btn {
  padding: 10px 28px;
  font-size: 14px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: white;
  font-weight: 500;
  transition: all 0.2s;
  margin-left: 12px;
}

.json-save-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.4);
}

.json-save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
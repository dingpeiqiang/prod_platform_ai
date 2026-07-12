<template>
  <div class="dialog-config-view">
    <div class="view-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <div class="header-title">
        <h1>智聊・对话式配置</h1>
        <p>零基础极简配置 — 多轮对话生成配置,可视化画布所见即所得</p>
      </div>
    </div>

    <div class="main-container">
      <div class="chat-panel">
        <div class="chat-header">
          <span>对话配置</span>
        </div>
        <div class="messages-container" ref="messagesContainer">
          <div v-if="messages.length === 0" class="welcome-message">
            <div class="welcome-icon">💬</div>
            <div class="welcome-text">{{ welcomeText }}</div>
          </div>
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message"
            :class="msg.role"
          >
            <div v-if="msg.role === 'assistant'" class="message-avatar">AI</div>
            <div class="message-content">
              <div v-if="msg.thinking" class="thinking-text">{{ msg.thinking }}</div>
              <div class="message-text">{{ msg.content }}</div>
              <div v-if="msg.missingFields && msg.missingFields.length > 0" class="missing-fields">
                <div v-for="f in msg.missingFields" :key="f.field" class="missing-item">
                  ❓ {{ f.question }}
                </div>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="message-avatar user">我</div>
          </div>
          <div v-if="streaming" class="message assistant">
            <div class="message-avatar">AI</div>
            <div class="message-content">
              <div v-if="streamThinking" class="thinking-text">{{ streamThinking }}</div>
              <div class="streaming-text">{{ streamText }}<span class="cursor">|</span></div>
            </div>
          </div>
        </div>
        <div class="input-area">
          <textarea
            v-model="inputText"
            class="chat-input"
            placeholder="描述您的配置需求,如:办理100M家庭宽带,首月半价,绑定2张副卡,仅限本市小区用户办理"
            rows="3"
            @keydown.enter.prevent="sendMessage"
            :disabled="streaming"
          ></textarea>
          <button class="send-btn" :disabled="streaming || !inputText.trim()" @click="sendMessage">
            {{ streaming ? '处理中...' : '发送' }}
          </button>
        </div>
      </div>

      <div class="canvas-panel">
        <div class="canvas-header">
          <span>可视化配置画布</span>
          <div class="canvas-actions">
            <button class="action-btn secondary small" @click="validateConfig" :disabled="validating">
              {{ validating ? '校验中...' : '本体校验' }}
            </button>
          </div>
        </div>
        <div class="canvas-container" ref="canvasContainer">
          <svg
            class="config-canvas"
            :width="canvasWidth"
            :height="canvasHeight"
            @mousemove="onCanvasMouseMove"
            @mouseup="onCanvasMouseUp"
          >
            <defs>
              <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#cbd5e1" />
              </marker>
            </defs>

            <line
              v-for="edge in canvas.edges"
              :key="`${edge.source_id}-${edge.target_id}`"
              :x1="getNode(edge.source_id).x + 90"
              :y1="getNode(edge.source_id).y + 35"
              :x2="getNode(edge.target_id).x + 90"
              :y2="getNode(edge.target_id).y + 35"
              stroke="#cbd5e1"
              stroke-width="2"
              stroke-dasharray="5,5"
              :marker-end="edge.relation === 'constrains' ? 'url(#arrowhead)' : ''"
            />
            <text
              v-for="edge in canvas.edges"
              :key="`label-${edge.source_id}-${edge.target_id}`"
              :x="(getNode(edge.source_id).x + getNode(edge.target_id).x) / 2 + 90"
              :y="(getNode(edge.source_id).y + getNode(edge.target_id).y) / 2 + 35"
              text-anchor="middle"
              class="edge-label"
            >{{ edge.relation }}</text>

            <g
              v-for="node in canvasNodes"
              :key="node.node_id"
              :transform="`translate(${node.position.x}, ${node.position.y})`"
              class="canvas-node-group"
              @mousedown="startDrag($event, node)"
              @click="selectNode(node)"
            >
              <rect
                width="180"
                height="70"
                rx="10"
                :class="['canvas-node-rect', node.node_type, { selected: selectedNode?.node_id === node.node_id }]"
              />
              <text x="90" y="22" text-anchor="middle" class="node-label">{{ node.label }}</text>
              <text x="12" y="42" class="node-field">{{ truncateFields(node.fields) }}</text>
              <text x="12" y="60" class="node-type">{{ nodeTypeLabels[node.node_type] || node.node_type }}</text>
            </g>
          </svg>

          <div v-if="selectedNode" class="node-editor">
            <div class="editor-header">
              <span>编辑: {{ selectedNode.label }}</span>
              <button class="close-btn" @click="selectedNode = null">✕</button>
            </div>
            <div class="editor-body">
              <div v-for="(value, key) in selectedNode.fields" :key="key" class="editor-field">
                <label>{{ fieldLabels[key] || key }}</label>
                <input
                  v-model="selectedNode.fields[key]"
                  class="field-input"
                  @change="updateNode(selectedNode)"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-if="validationResult" class="validation-panel">
          <div class="validation-header" :class="validationResult.valid ? 'valid' : 'invalid'">
            {{ validationResult.valid ? '✅ 校验通过' : '❌ 校验失败' }}
          </div>
          <div v-if="validationResult.errors.length > 0" class="validation-list">
            <div v-for="(err, i) in validationResult.errors" :key="'e'+i" class="validation-item error">
              {{ err.message }}
            </div>
          </div>
          <div v-if="validationResult.warnings.length > 0" class="validation-list">
            <div v-for="(warn, i) in validationResult.warnings" :key="'w'+i" class="validation-item warning">
              {{ warn.message }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { demoDialogApi } from '@/services/demoApi.js'

const emit = defineEmits(['go-back'])

const sessionId = ref('')
const welcomeText = ref('')
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const streamText = ref('')
const streamThinking = ref('')
const messagesContainer = ref(null)
const canvasContainer = ref(null)

const canvas = reactive({
  nodes: [],
  edges: []
})

const canvasWidth = ref(600)
const canvasHeight = ref(500)
const selectedNode = ref(null)
const validating = ref(false)
const validationResult = ref(null)

const nodeTypeLabels = {
  product: '产品信息',
  tariff: '资费规则',
  sub_card: '副卡配置',
  constraint: '受理约束'
}

const fieldLabels = {
  product_name: '产品名称',
  bandwidth: '带宽',
  product_type: '产品类型',
  monthly_fee: '月费',
  first_month_discount: '首月优惠',
  contract_period: '合约期',
  deposit: '押金',
  sub_card_count: '副卡数量',
  sub_card_monthly_fee: '副卡月费',
  sub_card_traffic: '副卡流量',
  region_limit: '地域限制',
  user_limit: '用户限制',
  install_limit: '安装限制'
}

const canvasNodes = computed(() => canvas.nodes)

const draggingNode = ref(null)
const dragOffset = ref({ x: 0, y: 0 })

const getNode = (nodeId) => {
  const node = canvas.nodes.find(n => n.node_id === nodeId)
  return node ? node.position : { x: 0, y: 0 }
}

const truncateFields = (fields) => {
  const entries = Object.entries(fields).filter(([, v]) => v !== '' && v !== 0 && v !== '无')
  if (entries.length === 0) return '(未配置)'
  const text = entries.map(([k, v]) => `${fieldLabels[k] || k}: ${v}`).join(', ')
  return text.length > 40 ? text.substring(0, 40) + '...' : text
}

onMounted(async () => {
  try {
    const res = await demoDialogApi.start()
    sessionId.value = res.session_id
    welcomeText.value = res.welcome
    if (res.canvas) {
      canvas.nodes = res.canvas.nodes
      canvas.edges = res.canvas.edges
    }
    updateCanvasSize()
  } catch (e) {
    console.error('初始化失败:', e)
    alert('初始化失败: ' + e.message)
  }
})

const updateCanvasSize = () => {
  if (canvasContainer.value) {
    canvasWidth.value = canvasContainer.value.clientWidth
    canvasHeight.value = canvasContainer.value.clientHeight
  }
}

const sendMessage = async () => {
  if (!inputText.value.trim() || streaming.value) return

  const userMsg = inputText.value.trim()
  messages.value.push({ role: 'user', content: userMsg })
  inputText.value = ''

  streaming.value = true
  streamText.value = ''
  streamThinking.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const response = await demoDialogApi.chatStream(sessionId.value, userMsg)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let missingFields = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value)
      const lines = buffer.split('\n\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data: ')) continue
        const dataStr = line.slice(6)
        try {
          const data = JSON.parse(dataStr)

          if (data.type === 'thinking') {
            streamThinking.value = data.content
          } else if (data.type === 'reasoning') {
            streamThinking.value = data.content
          } else if (data.type === 'text_start') {
            streamText.value = ''
          } else if (data.type === 'text') {
            streamText.value += data.content
            scrollToBottom()
          } else if (data.type === 'text_end') {
            // text complete
          } else if (data.type === 'canvas_update') {
            if (data.canvas) {
              canvas.nodes = data.canvas.nodes
              canvas.edges = data.canvas.edges
            }
          } else if (data.type === 'missing_fields') {
            missingFields = data.fields || []
          } else if (data.type === 'done') {
            messages.value.push({
              role: 'assistant',
              content: streamText.value,
              thinking: streamThinking.value,
              missingFields
            })
            streaming.value = false
            streamText.value = ''
            streamThinking.value = ''
            scrollToBottom()
          } else if (data.type === 'error') {
            alert('错误: ' + data.content)
            streaming.value = false
          }
        } catch (e) {
          console.error('解析SSE失败:', e)
        }
      }
    }
  } catch (e) {
    alert('发送失败: ' + e.message)
    streaming.value = false
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const startDrag = (event, node) => {
  draggingNode.value = node
  const rect = canvasContainer.value.getBoundingClientRect()
  dragOffset.value = {
    x: event.clientX - rect.left - node.position.x,
    y: event.clientY - rect.top - node.position.y
  }
}

const onCanvasMouseMove = (event) => {
  if (!draggingNode.value) return
  const rect = canvasContainer.value.getBoundingClientRect()
  const newX = event.clientX - rect.left - dragOffset.value.x
  const newY = event.clientY - rect.top - dragOffset.value.y

  draggingNode.value.position.x = Math.max(0, Math.min(canvasWidth.value - 180, newX))
  draggingNode.value.position.y = Math.max(0, Math.min(canvasHeight.value - 70, newY))
}

const onCanvasMouseUp = async () => {
  if (draggingNode.value) {
    const node = draggingNode.value
    try {
      await demoDialogApi.updateNode(sessionId.value, {
        node_id: node.node_id,
        position: node.position
      })
    } catch (e) {
      console.error('更新节点位置失败:', e)
    }
    draggingNode.value = null
  }
}

const selectNode = (node) => {
  selectedNode.value = JSON.parse(JSON.stringify(node))
  const original = canvas.nodes.find(n => n.node_id === node.node_id)
  if (original) {
    selectedNode.value = original
  }
}

const updateNode = async (node) => {
  try {
    await demoDialogApi.updateNode(sessionId.value, {
      node_id: node.node_id,
      fields: node.fields
    })
    validationResult.value = null
  } catch (e) {
    console.error('更新节点失败:', e)
  }
}

const validateConfig = async () => {
  validating.value = true
  try {
    const res = await demoDialogApi.validate(sessionId.value)
    validationResult.value = res
  } catch (e) {
    alert('校验失败: ' + e.message)
  } finally {
    validating.value = false
  }
}
</script>

<style scoped>
.dialog-config-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary, #f5f7fa);
  padding: 24px 32px;
}

.view-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  color: var(--text-secondary, #666);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: var(--bg-primary, #fff);
  color: #f59e0b;
  border-color: #f59e0b;
}

.header-title h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: var(--text-primary, #1a1a1a);
}

.header-title p {
  font-size: 13px;
  color: var(--text-tertiary, #999);
  margin: 0;
}

.main-container {
  flex: 1;
  display: flex;
  gap: 20px;
  min-height: 0;
}

.chat-panel {
  width: 420px;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.welcome-message {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-tertiary, #999);
}

.welcome-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.welcome-text {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  text-align: left;
  background: var(--bg-tertiary, #f0f2f5);
  padding: 16px;
  border-radius: 12px;
}

.message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.message-avatar.user {
  background: #3b82f6;
}

.message-content {
  max-width: 80%;
}

.thinking-text {
  font-size: 12px;
  color: var(--text-tertiary, #999);
  font-style: italic;
  margin-bottom: 4px;
  padding: 6px 10px;
  background: var(--bg-tertiary, #f0f2f5);
  border-radius: 8px;
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message.assistant .message-text {
  background: var(--bg-tertiary, #f0f2f5);
  color: var(--text-primary, #1a1a1a);
}

.message.user .message-text {
  background: #f59e0b;
  color: #fff;
}

.missing-fields {
  margin-top: 8px;
}

.missing-item {
  font-size: 13px;
  color: #f59e0b;
  padding: 4px 0;
}

.streaming-text {
  padding: 10px 14px;
  background: var(--bg-tertiary, #f0f2f5);
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  min-height: 20px;
}

.cursor {
  animation: blink 1s infinite;
  color: #f59e0b;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.input-area {
  padding: 16px;
  border-top: 1px solid var(--border-color, #f0f0f0);
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
  background: var(--bg-primary, #fff);
  color: var(--text-primary, #1a1a1a);
  box-sizing: border-box;
}

.chat-input:focus {
  border-color: #f59e0b;
}

.send-btn {
  padding: 10px 20px;
  background: #f59e0b;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #d97706;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.canvas-panel {
  flex: 1;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.canvas-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}

.canvas-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.small {
  padding: 5px 14px;
  font-size: 12px;
}

.action-btn.secondary {
  background: var(--bg-tertiary, #f0f2f5);
  color: var(--text-secondary, #666);
}

.action-btn.secondary:hover:not(:disabled) {
  background: var(--border-color, #e0e0e0);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.canvas-container {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #fafbfc;
  background-image:
    linear-gradient(rgba(0,0,0,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.03) 1px, transparent 1px);
  background-size: 20px 20px;
}

.config-canvas {
  display: block;
}

.canvas-node-group {
  cursor: move;
}

.canvas-node-rect {
  fill: #fff;
  stroke: #e0e0e0;
  stroke-width: 2;
  transition: all 0.2s;
}

.canvas-node-rect.product { fill: #eff6ff; stroke: #3b82f6; }
.canvas-node-rect.tariff { fill: #f0fdf4; stroke: #10b981; }
.canvas-node-rect.sub_card { fill: #fffbeb; stroke: #f59e0b; }
.canvas-node-rect.constraint { fill: #fef2f2; stroke: #ef4444; }

.canvas-node-rect.selected {
  stroke-width: 3;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.15));
}

.canvas-node-group:hover .canvas-node-rect {
  stroke-width: 3;
}

.node-label {
  font-size: 14px;
  font-weight: 600;
  fill: var(--text-primary, #1a1a1a);
}

.node-field {
  font-size: 11px;
  fill: var(--text-secondary, #666);
}

.node-type {
  font-size: 10px;
  fill: var(--text-tertiary, #999);
}

.edge-label {
  font-size: 10px;
  fill: #94a3b8;
}

.node-editor {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 260px;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  z-index: 10;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  font-size: 14px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 16px;
  color: var(--text-tertiary, #999);
  cursor: pointer;
}

.editor-body {
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.editor-field {
  margin-bottom: 12px;
}

.editor-field label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary, #666);
  margin-bottom: 4px;
}

.field-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  background: var(--bg-primary, #fff);
  color: var(--text-primary, #1a1a1a);
}

.field-input:focus {
  border-color: #f59e0b;
}

.validation-panel {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #f0f0f0);
  max-height: 200px;
  overflow-y: auto;
}

.validation-header {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.validation-header.valid { color: #10b981; }
.validation-header.invalid { color: #ef4444; }

.validation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.validation-item {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
}

.validation-item.error {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.validation-item.warning {
  background: rgba(245, 158, 11, 0.08);
  color: #f59e0b;
}
</style>

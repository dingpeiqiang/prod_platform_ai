<template>
  <div class="chat-layout">
    <!-- 左侧：对话区域 -->
    <div class="chat-main">
      <!-- 顶部栏 -->
      <div class="chat-topbar">
        <span class="session-name">{{ sessionTitle }}</span>
        <button class="icon-btn" title="清空记录" @click="clearHistory">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>
            <path d="M9 6V4h6v2"/>
          </svg>
        </button>
      </div>

      <!-- 消息区 -->
      <div class="messages-area" ref="messagesEl">
        <!-- 欢迎屏 -->
        <div v-if="!messages.length" class="welcome-screen">
          <div class="welcome-logo">
            <div class="welcome-logo-inner">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
              </svg>
            </div>
          </div>
          <h2 class="welcome-title">有什么可以帮你的？</h2>
          <p class="welcome-subtitle">AI 驱动的智能表单助手，帮你快速填写各类表单</p>
          <div class="welcome-suggestions">
            <button
              v-for="s in suggestions"
              :key="s.key"
              class="suggestion-card"
              @click="sendSuggestion(s.text)"
            >
              <span class="suggestion-icon">{{ s.icon }}</span>
              <span class="suggestion-label">{{ s.text }}</span>
              <svg class="suggestion-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="messages-list">
          <div
            v-for="(msg, idx) in messages"
            :key="msg.id"
            :class="['msg-row', msg.role]"
          >
            <!-- AI 头像 -->
            <div v-if="msg.role === 'assistant'" class="avatar ai-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
              </svg>
            </div>

            <div class="msg-body">
              <!-- 用户消息 -->
              <div v-if="msg.role === 'user'" class="bubble user-bubble">
                {{ msg.content }}
              </div>

              <!-- AI 消息 -->
              <div v-else class="ai-message">
                <!-- 系统步骤折叠区 -->
                <div v-if="msg.reasoning && msg.reasoning.length" class="reasoning-wrap">
                  <button class="reasoning-toggle" @click="toggleReasoning(idx)">
                    <svg
                      :style="{ transform: msg.showReasoning ? 'rotate(90deg)' : 'rotate(0deg)' }"
                      width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                      style="transition:transform .2s"
                    >
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                    <span class="reasoning-label">
                      🔄 处理步骤
                      <span class="reasoning-count">({{ msg.reasoning.length }} 步)</span>
                    </span>
                    <span v-if="!msg.done" class="thinking-dots"><span/><span/><span/></span>
                  </button>
                  <transition name="collapse">
                    <div v-if="msg.showReasoning" class="reasoning-body">
                      <div
                        v-for="(step, si) in msg.reasoning"
                        :key="si"
                        :class="['reasoning-step', 'step-' + step.type, { 'step-latest': si === msg.latestStepIndex && !msg.done }]"
                      >
                        <span class="step-icon">{{ stepIcon(step.type) }}</span>
                        <span class="step-text">{{ step.content }}</span>
                        <span v-if="si === msg.latestStepIndex && !msg.done" class="step-loading">
                          <span/><span/><span/>
                        </span>
                        <!-- 该步骤对应的模型推理（内嵌显示） -->
                        <div v-if="step.reasoning" class="step-reasoning-inline">
                          <span class="step-reasoning-toggle" @click="step._showReasoning = !step._showReasoning">
                            <svg
                              :style="{ transform: step._showReasoning ? 'rotate(90deg)' : 'rotate(0deg)' }"
                              width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                              style="transition:transform .2s;vertical-align:middle"
                            ><polyline points="9 18 15 12 9 6"/></svg>
                            模型思考 ({{ step.reasoning.length }} 字)
                          </span>
                          <transition name="collapse">
                            <div v-if="step._showReasoning" class="step-reasoning-body">
                              <pre class="step-reasoning-text">{{ step.reasoning }}</pre>
                            </div>
                          </transition>
                        </div>
                      </div>
                    </div>
                  </transition>
                </div>

                <!-- 正文 -->
                <div
                  v-if="msg.streamText || msg.content"
                  class="ai-text"
                  v-html="renderMarkdown(msg.streamText || msg.content)"
                />
                <span v-if="!msg.done && (msg.streamText || !msg.reasoning?.length)" class="cursor-blink">▌</span>

                <!-- 流式等待 -->
                <div v-if="!msg.done && !msg.streamText && !msg.reasoning?.length" class="dots-loading">
                  <span/><span/><span/>
                </div>

                <!-- 意图面板（统一动态渲染，通过 intent-registry.js 注册） -->
                <IntentPanel
                  v-for="intentType in intentPanelTypes"
                  :key="intentType"
                  :intentType="intentType"
                  :msg="msg"
                  @intent-action="handleIntentEvent"
                />

                <!-- 操作按钮（已移除表单内嵌） -->
                <div v-if="msg.done && (msg.streamText || msg.content)" class="msg-actions">
                  <button class="action-btn" @click="copyText(msg.streamText || msg.content)" title="复制">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                    复制
                  </button>
                </div>
              </div>
            </div>

            <!-- 用户头像（右侧） -->
            <div v-if="msg.role === 'user'" class="avatar user-avatar">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/>
              </svg>
            </div>
          </div>
          <div style="height: 24px"/>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <!-- 快捷操作栏 -->
        <div class="quick-bar">
          <button
            v-for="a in quickActions"
            :key="a.key"
            class="quick-chip"
            @click="sendSuggestion(a.content)"
            :disabled="isStreaming"
          >
            <span class="chip-dot" :style="{ background: a.color }"></span>
            {{ a.label }}
          </button>
        </div>

        <div class="input-box" :class="{ focused: inputFocused }">
          <div class="textarea-wrap">
            <textarea
              ref="inputEl"
              v-model="inputText"
              placeholder="描述你想做的事，例如「帮我填一个销售订单」"
              rows="1"
              @focus="inputFocused = true"
              @blur="inputFocused = false"
              @keydown="handleKeydown"
              @input="autoResize"
              :disabled="isStreaming"
            />
            <button v-if="isStreaming" class="send-btn stop-btn" @click="stopStream" title="停止生成">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="6" width="12" height="12" rx="2"/>
              </svg>
            </button>
            <button
              v-else
              class="send-btn"
              :class="{ active: inputText.trim() }"
              :disabled="!inputText.trim()"
              @click="sendMessage"
              title="发送 (Enter)"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
        <div class="input-hint">内容由 AI 生成，仅供参考</div>
      </div>
    </div>

    <!-- 右侧：表单面板 -->
    <FormPanel
      :formSchema="currentFormSchema"
      :formId="currentFormId"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
      @field-change="handleFormFieldChange"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormPanel from './FormPanel.vue'
import ConfigCard from './ConfigCard.vue'
// 意图面板组件
import DeleteResultPanel from './intent-panels/DeleteResultPanel.vue'
import HistoryPanel from './intent-panels/HistoryPanel.vue'
import IntentPanel from './intent-panels/IntentPanel.vue'
// 意图注册器
import { registerEventHandler, registerPostProcessor, getEventHandler, getEventPanel, getPostProcessor, listIntentPanels } from '../composables/useIntentRegistry.js'

marked.setOptions({ breaks: true, gfm: true })

// ── 意图事件处理器注册 ──────────────────────────────
// 将 SSE 事件处理逻辑从 handleEvent switch/case 迁移到注册器
registerEventHandler('config', (data, msg) => {
  console.log('[SSE] 收到 config 事件, configData:', data.content ? Object.keys(data.content) : null)
  if (!msg._intentData) msg._intentData = {}
  msg._intentData['config'] = { ...data.content, deployed: false, deploying: false }
  scrollToBottom()
}, { panel: ConfigCard })

registerEventHandler('delete_form', (data, msg) => {
  console.log('[SSE] 收到 delete_form 事件', data.content)
  if (!msg._intentData) msg._intentData = {}
  const d = data.content || {}
  msg._intentData['delete_form'] = {
    ...d,
    showVersionHistory: true,
    versionList: d.versionList || [],
    loadingVersions: false,
    rollingBack: false,
    rollbackResult: null
  }
  scrollToBottom()
}, { panel: DeleteResultPanel })

registerEventHandler('manage_history', (data, msg) => {
  const historyPayload = data.content || data.data || {}
  if (!historyPayload.action) {
    if (historyPayload.qualityScore !== undefined) historyPayload.action = 'analyze'
    else if (historyPayload.generatedCount !== undefined) historyPayload.action = 'generate'
    else if (historyPayload.importedCount !== undefined) historyPayload.action = 'import'
    else if (historyPayload.downloadUrl !== undefined) historyPayload.action = 'export'
    else if (historyPayload.totalRecords !== undefined) historyPayload.action = 'status'
    else historyPayload.action = 'status'
  }
  if (!msg._intentData) msg._intentData = {}
  if (historyPayload.action === 'export') {
    // export 不渲染面板，只在气泡里显示下载链接
    msg._historyRaw = historyPayload
  } else {
    msg._intentData['manage_history'] = {
      ...historyPayload,
      importing: false,
      importResult: null
    }
  }
  scrollToBottom()
}, { panel: HistoryPanel })

// ── 意图后处理器注册 ──────────────────────────────
// SSE 流结束后的意图专属后处理（替代 if/elif 链）
registerPostProcessor('form_update', async (msg, intentData) => {
  await updateFormFields(intentData)
})

registerPostProcessor('delete_form', (msg, intentData) => {
  msg.content = msg.streamText || ''
  const deletedCode = intentData?.formCode || msg.deleteFormData?.formCode
  if (deletedCode && currentFormSchema.value?.formCode === deletedCode) {
    currentFormId.value = null
    currentFormSchema.value = null
  }
})

registerPostProcessor('manage_history', (msg) => {
  const hd = msg._historyRaw || msg.historyData
  if (hd?.action === 'export' && hd.downloadUrl) {
    msg.content = `文件已准备好导出：${hd.filename}（共${hd.recordCount}条记录）\n点击下载：${hd.downloadUrl}`
  } else {
    msg.content = msg.streamText || ''
  }
})

registerPostProcessor('configure', (msg) => {
  msg.content = msg.streamText || ''
})

registerPostProcessor('form', async (msg, intentData) => {
  if (intentData?.formCode) {
    await generateForm({
      formCode: intentData.formCode,
      extractedFields: intentData.extractedFields || {},
      fieldRecommendations: intentData.fieldRecommendations
    })
  } else {
    msg.content = msg.streamText
  }
})

const props = defineProps({
  sessionId:    { type: String, required: true },
  sessionTitle: { type: String, default: '新对话' }
})
const emit = defineEmits(['title-update'])

const messagesEl = ref(null)
const inputEl    = ref(null)

const inputText    = ref('')
const inputFocused = ref(false)
const isStreaming  = ref(false)
const messages     = ref([])

// 表单状态（提升到父组件管理）
const currentFormId     = ref('')
const currentFormSchema = ref(null)

let abortCtrl = null

// 配置状态
const configDeploying = ref({})  // msgId -> bool

const storageKey = computed(() => `chat_session_${props.sessionId}`)
const formStorageKey = computed(() => `chat_form_${props.sessionId}`)

const saveMessages = () => {
  try {
    const toSave = messages.value.filter(m => m.done !== false)
    localStorage.setItem(storageKey.value, JSON.stringify(toSave))
  } catch {}
}

const loadMessages = () => {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (raw) messages.value = JSON.parse(raw)
  } catch {}
}

const saveFormState = () => {
  try {
    localStorage.setItem(formStorageKey.value, JSON.stringify({
      formId: currentFormId.value,
      formSchema: currentFormSchema.value
    }))
  } catch {}
}

const loadFormState = () => {
  try {
    const raw = localStorage.getItem(formStorageKey.value)
    if (raw) {
      const state = JSON.parse(raw)
      currentFormId.value = state.formId || ''
      currentFormSchema.value = state.formSchema || null
    }
  } catch {}
}

watch(messages, saveMessages, { deep: true })

watch([currentFormId, currentFormSchema], () => {
  saveFormState()
}, { deep: true })

watch(() => props.sessionId, () => {
  messages.value = []
  currentFormId.value = ''
  currentFormSchema.value = null
  loadMessages()
  loadFormState()
})

const suggestions = [
  { key: 'sales',   icon: '📋', text: '帮我填一个销售订单' },
  { key: 'leave',   icon: '📅', text: '帮我填一个请假申请' },
  { key: 'expense', icon: '💰', text: '帮我填一个费用报销' },
  { key: 'config',  icon: '🛠️', text: '我想添加一种新表单' },
  { key: 'help',    icon: '💬', text: '你能做什么？' },
]

const quickActions = [
  { key: 'sales',   label: '销售订单', content: '帮我填一个销售订单', color: '#818cf8' },
  { key: 'leave',   label: '请假申请', content: '帮我填一个请假申请', color: '#34d399' },
  { key: 'expense', label: '费用报销', content: '帮我填一个费用报销', color: '#fbbf24' },
  { key: 'config',  label: '+ 新表单', content: '我想添加一种新的业务表单', color: '#f472b6' },
]

const genId = () => `msg_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
const stepIcon = (type) => ({ thinking: '💭', reasoning: '🔍', result: '✅', error: '❌' }[type] || '•')

const renderMarkdown = (text) => {
  if (!text) return ''
  let t = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

  // 修复 MiniMax 等模型输出不规范的 Markdown 格式
  // 1. "-费用" → "- 费用"：短横线列表标记后补空格
  t = t.replace(/^-([^\s\d])/gm, '- $1')
  t = t.replace(/\n-([^\s\d])/g, '\n- $1')
  t = t.replace(/ -([^\s\d])/g, ' - $1')

  // 2. 同一行多个 "- xxx" 列表项拆行
  t = t.replace(/(- [^-]+?)(?=\s- )/g, '$1\n')

  // 3. "2.聊天" → "2. 聊天"：数字编号后补空格
  t = t.replace(/(\d+\.)(?=[^\s\d])/g, '$1 ')

  // 4. 同一行多个编号拆行
  t = t.replace(/([^\n])(\d+\.\s)/g, '$1\n$2')

  return marked.parse(t)
}

/**
 * 对完整文本做 Markdown 格式规范化（仅在 done/text_end 时调用）
 * 修复 MiniMax 等模型输出不规范的问题
 */
const formatMarkdownText = (raw) => {
  if (!raw) return raw
  let t = raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

  // 1. " -费用" → " - 费用"：空格+短横线后无空格，补空格（让 marked 识别为列表项）
  t = t.replace(/ (\-)([^\s\d])/g, ' $1 $2')

  // 2. 同一行内多个 "- xxx" 列表项拆行："- A - B" → "- A\n- B"
  t = t.replace(/(- [^-]+?)(?=\s- )/g, '$1\n')

  // 3. "2.聊天" → "2. 聊天"：数字编号后补空格
  t = t.replace(/(\d+\.)(?=[^\s\d])/g, '$1 ')

  // 4. 同一行多个编号拆行："...2. xxx3. yyy..." → "...2. xxx\n3. yyy..."
  t = t.replace(/([^\n])(\d+\. )/g, '$1\n$2')

  return t
}

const scrollToBottom = (smooth = false) => {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTo({
        top: messagesEl.value.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto'
      })
    }
  })
}

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

const resetInput = () => {
  inputText.value = ''
  nextTick(() => {
    if (inputEl.value) {
      inputEl.value.style.height = 'auto'
      inputEl.value.focus()
    }
  })
}

const toggleReasoning = (idx) => {
  messages.value[idx].showReasoning = !messages.value[idx].showReasoning
}

const copyText = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage({ message: '已复制', type: 'success', duration: 1500, plain: true })
  } catch {
    ElMessage.error('复制失败')
  }
}

const clearHistory = async () => {
  try {
    await ElMessageBox.confirm('确定清空本次对话记录吗？', '清空记录', {
      confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning'
    })
    messages.value = []
    currentFormId.value = ''
    currentFormSchema.value = null
    localStorage.removeItem(storageKey.value)
    localStorage.removeItem(formStorageKey.value)
    ElMessage({ message: '已清空', type: 'success', duration: 1500, plain: true })
  } catch {}
}

const stopStream = () => {
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
}

const sendSuggestion = (text) => {
  inputText.value = text
  sendMessage()
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  resetInput()
  messages.value.push({ id: genId(), role: 'user', content: text, done: true })

  if (messages.value.filter(m => m.role === 'user').length === 1) {
    emit('title-update', props.sessionId, text.slice(0, 20))
  }

  scrollToBottom()

  const aiMsg = {
    id: genId(), role: 'assistant',
    reasoning: [], streamText: '', content: '',
    showReasoning: false,
    done: false, type: 'chat'
  }
  messages.value.push(aiMsg)
  const msgIdx = messages.value.length - 1
  isStreaming.value = true
  abortCtrl = new AbortController()

  try {
    const chatHistory = messages.value
      .filter(m => m.role === 'user' || (m.role === 'assistant' && m.done && m.content))
      .slice(0, -1)
      .slice(-20)
      .map(m => ({ role: m.role, content: m.content || m.streamText || '' }))

    const resp = await fetch('/api/v1/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: [...chatHistory, { role: 'user', content: text }] }),
      signal: abortCtrl.signal
    })

    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)

    console.log('[SSE] 流式响应开始, content-type:', resp.headers.get('content-type'))

    const reader  = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let intentData = null
    let intentType = 'form' // 默认意图类型
    let eventCount = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        console.log('[SSE] 流结束, 共处理事件:', eventCount)
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n')
      buffer = frames.pop()

      for (const frame of frames) {
        if (!frame.startsWith('data:')) continue
        try {
          const data = JSON.parse(frame.slice(5).trim())
          eventCount++
          if (eventCount <= 3) console.log('[SSE] 事件 #' + eventCount + ':', data.type, data.content?.substring?.(0, 50) || '')
          handleEvent(data, msgIdx)
          // done 事件：优先从 intent 事件（新版）提取，再从 done 本身兼容
          if (data.type === 'done') {
            if (intentType) {
              // 已从 intent 事件提取到 intentType，无需重复处理
            } else if (data.intentType) {
              intentType = data.intentType
            } else if (data.isForm) {
              intentType = 'form'
            }
            if (data.intentData) intentData = data.intentData
          }
          // 新版 intent 事件（统一格式 v2.0）
          if (data.type === 'intent' && data.intentType) {
            intentType = data.intentType
            if (data.data) intentData = data.data
          }
          // 旧版 result 事件兼容
          if (data.type === 'result') {
            try {
              const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content
              if (parsed?.formCode && !intentData) {
                intentData = parsed
              }
            } catch {}
          }
        } catch {}
      }
    }

    const msg = messages.value[msgIdx]
    if (msg) {
      msg.done = true
      msg.showReasoning = false

      // 根据 intentType 后处理（通过注册器分发）
      const postProcessor = getPostProcessor(intentType)
      if (postProcessor) {
        await postProcessor(msg, intentData)
      } else {
        // 默认：普通聊天
        msg.content = msg.streamText
      }
    }

  } catch (err) {
    if (err.name !== 'AbortError') {
      console.error(err)
      const msg = messages.value[msgIdx]
      if (msg) {
        msg.done = true
        msg.showReasoning = true
        msg.reasoning.push({ type: 'error', content: '请求出错：' + err.message })
        msg.content = '抱歉，遇到了一些问题，请稍后重试。'
      }
    } else {
      const msg = messages.value[msgIdx]
      if (msg) {
        msg.done = true
        msg.showReasoning = false
        if (!msg.content) msg.content = msg.streamText || '（已停止）'
      }
    }
  } finally {
    isStreaming.value = false
    abortCtrl = null
    scrollToBottom(true)
  }
}

const handleEvent = (data, idx) => {
  const msg = messages.value[idx]
  if (!msg) return

  switch (data.type) {
    case 'thinking':
    case 'decision':
    case 'executing': {
      const last = msg.reasoning[msg.reasoning.length - 1]
      if (last && last.content === data.content) break
      msg.reasoning.push({ type: 'thinking', content: data.content })
      // 自动展开思考步骤，让用户看到实时进度
      msg.showReasoning = true
      // 标记最新步骤（用于动画效果）
      msg.latestStepIndex = msg.reasoning.length - 1
      scrollToBottom()
      break
    }
    case 'reasoning': {
      // 将模型推理追加到最近的 thinking 步骤（内嵌显示）
      const steps = msg.reasoning
      if (steps && steps.length) {
        const lastThinkingIdx = steps.reduce((acc, s, i) => s.type === 'thinking' ? i : acc, -1)
        if (lastThinkingIdx >= 0) {
          const target = steps[lastThinkingIdx]
          if (!target.reasoning) {
            target.reasoning = ''
            target._showReasoning = false
          }
          target.reasoning += data.content || ''
        }
      }
      scrollToBottom()
      break
    }
    case 'text_start':
      msg.streamText = ''
      break
    case 'text':
      msg.streamText = (msg.streamText || '') + (data.content || '')
      scrollToBottom()
      break
    case 'text_end':
      // 流式结束，对完整文本做一次性格式化（修复模型 Markdown 格式不规范问题）
      if (msg.streamText && !msg._formatted) {
        msg.streamText = formatMarkdownText(msg.streamText)
        msg._formatted = true
      }
      break
    case 'intent': {
      // 新版统一意图事件（v2.0）
      const { intentType, action, data: intentData } = data
      const handler = getEventHandler(intentType)
      if (handler) {
        handler({ type: intentType, action, content: intentData }, msg)
      }
      break
    }
    // 以下为旧版事件格式兼容（保留用于未升级的后端）
    case 'result': {
      const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content
      msg.intentResult = parsed
      break
    }
    case 'config':
    case 'delete_form':
    case 'manage_history': {
      // ── 意图事件：通过注册器分发 ──
      const handler = getEventHandler(data.type)
      if (handler) {
        handler(data, msg)
      }
      break
    }
    case 'error':
      // 支持新旧两种格式
      let errMsg = data.content || data.message || '未知错误'
      if (data.error_code) {
        errMsg += ` [${data.error_code}]`
      }
      if (data.recoverable === false) {
        errMsg += ' (不可恢复)'
      }
      msg.reasoning.push({ type: 'error', content: errMsg })
      break
    case 'tool_error':
      // MCP 工具执行失败，通知用户
      let errorContent = `⚠️ 工具 ${data.tool} 执行失败: ${data.error}`
      if (data.error_code) {
        errorContent += ` [${data.error_code}]`
      }
      if (data.recoverable === false) {
        errorContent += ' (不可恢复)'
      }
      msg.reasoning.push({
        type: 'error',
        content: errorContent
      })
      break
  }
}

// 生成表单 - 更新右侧面板，不再内嵌在消息中
const generateForm = async (intentData) => {
  const { formCode, extractedFields, fieldRecommendations } = intentData

  // 添加系统消息
  messages.value.push({
    id: genId(), role: 'assistant',
    content: `正在为你生成 ${formCode || ''} 表单...`, done: true, type: 'chat'
  })
  scrollToBottom()

  try {
    const userInput = `生成${formCode || ''}表单`
    const body = {
      userInput, formCode,
      extractedFields: Object.keys(extractedFields || {}).length ? extractedFields : undefined,
      fieldRecommendations: fieldRecommendations || undefined
    }
    const resp = await fetch('/api/v1/form/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    const result = await resp.json()

    if (result.success) {
      // 更新右侧表单面板
      currentFormId.value = result.formId
      currentFormSchema.value = result.formSchema

      // 更新消息
      messages.value.push({
        id: genId(), role: 'assistant',
        content: `✅ 已生成表单，请在右侧填写并提交。`, done: true, type: 'chat'
      })
    } else {
      messages.value.push({
        id: genId(), role: 'assistant',
        content: result.message || '生成表单失败，请重试。', done: true, type: 'chat'
      })
    }
  } catch {
    messages.value.push({
      id: genId(), role: 'assistant',
      content: '生成表单时出现网络错误，请重试。', done: true, type: 'chat'
    })
  }
  scrollToBottom()
}

// 更新表单字段 - 增量更新现有表单，不重新生成
const updateFormFields = async (intentData) => {
  // 兼容 formCode 和 detectedFormCode 两种字段名
  const { formCode, detectedFormCode, extractedFields } = intentData
  const actualFormCode = formCode || detectedFormCode

  // 检查是否有现有表单
  if (!currentFormSchema.value) {
    // 没有现有表单，降级为生成新表单
    messages.value.push({
      id: genId(), role: 'assistant',
      content: `检测到你想要更新表单，但没有找到现有表单。正在为你生成新的 ${actualFormCode || ''} 表单...`,
      done: true, type: 'chat'
    })
    scrollToBottom()

    if (actualFormCode) {
      await generateForm({
        formCode: actualFormCode,
        extractedFields: extractedFields || {}
      })
    }
    return
  }

  // 有关于表单，先添加确认消息
  if (extractedFields && Object.keys(extractedFields).length > 0) {
    const fieldNames = Object.keys(extractedFields)
    messages.value.push({
      id: genId(), role: 'assistant',
      content: `正在更新字段: ${fieldNames.join(', ')}...`,
      done: true, type: 'chat'
    })
  }

  // 增量更新表单字段值
  const newSchema = JSON.parse(JSON.stringify(currentFormSchema.value))

  if (extractedFields && Object.keys(extractedFields).length > 0) {
    let updatedCount = 0

    for (const [fieldCode, value] of Object.entries(extractedFields)) {
      // 找到对应字段并更新值（兼容 fieldCode 和 code 两种属性名）
      const field = newSchema.fields?.find(f => f.fieldCode === fieldCode || f.code === fieldCode)
      if (field) {
        field.value = value
        updatedCount++
        console.log(`更新字段 ${fieldCode} = ${value}`)
      } else {
        console.warn(`未找到字段: ${fieldCode}`)
      }
    }

    if (updatedCount > 0) {
      // 更新右侧表单面板
      currentFormSchema.value = newSchema

      messages.value.push({
        id: genId(), role: 'assistant',
        content: `✅ 已更新 ${updatedCount} 个字段，请查看右侧表单确认。`,
        done: true, type: 'chat'
      })
    } else {
      messages.value.push({
        id: genId(), role: 'assistant',
        content: `⚠️ 未找到可更新的字段，表单项可能不匹配。`,
        done: true, type: 'chat'
      })
    }
  } else {
    messages.value.push({
      id: genId(), role: 'assistant',
      content: `⚠️ 未提取到字段值，请尝试更明确的表达。`,
      done: true, type: 'chat'
    })
  }

  scrollToBottom()
}

// 表单提交
const handleFormSubmit = (formData, formId) => {
  // 生成提交摘要
  const schema = currentFormSchema.value
  let summaryLines = []
  if (schema && schema.fields) {
    for (const field of schema.fields) {
      const val = formData[field.fieldCode]
      if (val !== undefined && val !== null && val !== '') {
        const displayVal = Array.isArray(val) ? val.join(', ') : String(val)
        summaryLines.push(`- **${field.fieldName}**: ${displayVal}`)
      }
    }
  }

  const summary = summaryLines.length > 0
    ? `提交内容：\n${summaryLines.join('\n')}`
    : ''

  currentFormId.value = ''
  currentFormSchema.value = null
  ElMessage({ message: '表单提交成功！', type: 'success', plain: true })
  messages.value.push({
    id: genId(), role: 'assistant',
    content: `✅ 表单已成功提交！${summary ? '\n\n' + summary : ''}\n\n还有什么我可以帮你的吗？`,
    done: true, type: 'chat'
  })
  scrollToBottom()
}

// 表单取消
const handleFormCancel = () => {
  currentFormId.value = ''
  currentFormSchema.value = null
  messages.value.push({
    id: genId(), role: 'assistant',
    content: '好的，已取消。还有什么我可以帮你的吗？', done: true, type: 'chat'
  })
  scrollToBottom()
}

// 表单字段变化（可同步到 AI）
const handleFormFieldChange = (fieldCode, value) => {
  // 可选：通知后端字段变化
  console.log('表单字段变化:', fieldCode, value)
}

// ── 配置相关处理 ──────────────────────────────────────────────────────
const handleConfigDeploy = async (msg, { config, keywords }) => {
  try {
    await ElMessageBox.confirm(
      `确定部署表单「${config.formName || config.formCode}」？部署后即可在对话中直接使用。`,
      '确认部署',
      { confirmButtonText: '部署', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  _updateIntentData(msg, 'config', { deploying: true })

  try {
    const resp = await fetch('/api/v1/chat/deploy-config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ config, keywords })
    })
    const result = await resp.json()

    if (result.success) {
      _updateIntentData(msg, 'config', { deployed: true, deploying: false })
      ElMessage({ message: result.message, type: 'success', duration: 3000, plain: true })

      // 添加成功消息
      messages.value.push({
        id: genId(), role: 'assistant',
        content: `🎉 表单「${config.formName}」已部署成功！现在你可以说"帮我填一个${config.formName}"来测试了。`,
        done: true, type: 'chat'
      })
    } else {
      _updateIntentData(msg, 'config', { deploying: false })
      ElMessage.error(result.message || '部署失败')
    }
  } catch {
    _updateIntentData(msg, 'config', { deploying: false })
    ElMessage.error('部署请求失败，请重试')
  }
  scrollToBottom()
}

const handleConfigModify = (msg) => {
  // 预填输入框，引导用户修改
  inputText.value = `修改表单配置「${msg.configData?.config?.formName || ''}」：`
  nextTick(() => inputEl.value?.focus())
}

const handleConfigTest = (formCode) => {
  // 预填测试命令
  inputText.value = `帮我填一个${formCode}`
  sendMessage()
}

/**
 * 将本体配置（entities/fields）转换为表单 schema（formCode/formName/fields）
 * 用于预览未部署的配置
 */
const convertConfigToSchema = (config) => {
  const fields = []
  for (const entity of (config.entities || [])) {
    for (const field of (entity.fields || [])) {
      fields.push({
        fieldCode: field.fieldCode,
        fieldName: field.fieldName,
        fieldType: field.fieldType,
        required: !!field.required,
        disabled: false,
        hidden: false,
        ruleDescription: field.ruleDescription || '',
        // options / enumConfig
        ...(field.options ? { options: field.options } : {}),
        ...(field.enumConfig ? { enumConfig: field.enumConfig } : {}),
        recommend: [],
        defaultValue: null
      })
    }
  }
  return {
    formCode: config.formCode || '',
    formName: config.formName || '预览',
    fields,
    _preview: true // 标记为预览模式
  }
}

const handleConfigPreview = (config) => {
  const schema = convertConfigToSchema(config)
  currentFormId.value = `preview_${Date.now()}`
  currentFormSchema.value = schema

  messages.value.push({
    id: genId(), role: 'assistant',
    content: `👁️ 已将「**${config.formName}**」表单加载到右侧面板进行预览，你可以查看字段布局并填写测试。确认无误后点击部署即可正式使用。`,
    done: true, type: 'chat'
  })
  scrollToBottom()
}

onMounted(() => {
  loadMessages()
  loadFormState()
  scrollToBottom()
  nextTick(() => inputEl.value?.focus())
})

// 意图面板类型列表（静态，注册后不变）
const intentPanelTypes = listIntentPanels()

// ── 意图面板统一事件处理辅助函数 ──────────────────────
const _updateIntentData = (msg, intentType, partial) => {
  if (!msg._intentData) msg._intentData = {}
  msg._intentData[intentType] = { ...msg._intentData[intentType], ...partial }
}

// ── 版本历史管理 ───────────────────────────────────────────────────────
const loadVersions = async (msg) => {
  const intentData = msg._intentData?.['delete_form']
  const formCode = intentData?.formCode || msg.intentData?.formCode
  if (!formCode) return

  _updateIntentData(msg, 'delete_form', { loadingVersions: true })
  try {
    const resp = await fetch(`/api/v1/chat/form-versions/${encodeURIComponent(formCode)}`)
    const result = await resp.json()
    if (result.success && result.versions?.length) {
      _updateIntentData(msg, 'delete_form', { versionList: result.versions })
    } else {
      _updateIntentData(msg, 'delete_form', { versionList: [] })
    }
  } catch (e) {
    console.error('加载版本列表失败', e)
    _updateIntentData(msg, 'delete_form', { versionList: [] })
  } finally {
    _updateIntentData(msg, 'delete_form', { loadingVersions: false })
  }
}

const handleRollback = async (msg, version) => {
  const intentData = msg._intentData?.['delete_form']
  const formCode = intentData?.formCode || msg.intentData?.formCode

  // 二次确认
  try {
    await ElMessageBox.confirm(
      `确定回退到 ${formatVersionTime(version.timestamp)} 的版本？当前版本会自动备份。`,
      '确认回退',
      { confirmButtonText: '确定回退', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  _updateIntentData(msg, 'delete_form', { rollingBack: version.id, rollbackResult: null })

  try {
    const resp = await fetch('/api/v1/chat/rollback-form', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ formCode, versionId: version.id })
    })
    const result = await resp.json()

    _updateIntentData(msg, 'delete_form', { rollbackResult: result })

    if (result.success) {
      ElMessage({ message: `已成功回退到 ${version.id}`, type: 'success', duration: 3000, plain: true })

      // 添加系统消息
      messages.value.push({
        id: genId(), role: 'assistant',
        content: `🔄 表单「${result.data?.formName || formCode}」已恢复到版本 ${version.id}，现在可以正常使用了。`,
        done: true, type: 'chat'
      })
    } else {
      ElMessage({ message: result.message || '回退失败', type: 'error', duration: 4000, plain: true })
    }
  } catch (e) {
    console.error('回退失败', e)
    _updateIntentData(msg, 'delete_form', { rollbackResult: { success: false, message: '网络错误' } })
  } finally {
    _updateIntentData(msg, 'delete_form', { rollingBack: null })
  }
}

// ── 历史数据维护操作 ──

const scoreLevel = (score) => {
  if (score >= 80) return 'good'
  if (score >= 60) return 'warn'
  return 'bad'
}

const scoreLabel = (score) => {
  if (score >= 90) return '优秀'
  if (score >= 70) return '良好'
  if (score >= 50) return '一般'
  return '需改善'
}

const topFieldStats = (fieldStats) => {
  if (!fieldStats) return {}
  const entries = Object.entries(fieldStats)
  // 按不同值数量排序，返回前5个
  entries.sort((a, b) => (b[1]?.distinctValues || 0) - (a[1]?.distinctValues || 0))
  return Object.fromEntries(entries.slice(0, 5))
}

// 从面板按钮触发：分析数据质量
const handleAnalyzeHistory = async (msg) => {
  const formCode = msg.historyData?.formCode || msg.intentData?.formCode
  if (!formCode) return
  msg.historyData = null

  messages.value.push({ id: genId(), role: 'user', content: `分析一下${msg.historyData?.formName || formCode}的数据质量`, done: true })
  await sendMessage(`分析一下${msg.historyData?.formName || formCode}的数据质量`)
}

// 从面板按钮触发：导入数据到数据库
const handleImportHistory = async (msg) => {
  const formCode = msg.historyData?.formCode || msg.intentData?.formCode
  if (!formCode) return
  msg.importing = true
  try {
    const resp = await fetch('/api/v1/chat/history/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ formCode })
    })
    const result = await resp.json()
    msg.importResult = result
  } catch (e) {
    msg.importResult = { success: false, message: '导入失败: ' + e.message }
  } finally {
    msg.importing = false
  }
}

// 从面板按钮触发：导出历史数据
const handleExportHistory = async (msg, opts) => {
  const formCode = msg.historyData?.formCode || msg.intentData?.formCode
  if (!formCode) return

  try {
    const resp = await fetch(`/api/v1/config/export/${formCode}?format=${opts.format}`)
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ detail: '导出失败' }))
      ElMessage.error(err.detail || '导出失败')
      return
    }
    const blob = await resp.blob()
    const disposition = resp.headers.get('Content-Disposition') || ''
    const filenameMatch = disposition.match(/filename\*?=['"]?(?:UTF-8'')?([^;\n"']+)/i)
    const filename = filenameMatch ? decodeURIComponent(filenameMatch[1]) : `${formCode}_export.${opts.format}`

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${filename}`)
  } catch (e) {
    ElMessage.error('导出失败：' + e.message)
  }
}

// ── 意图面板统一事件处理 ──────────────────────────────
const handleIntentEvent = ({ intentType, action, payload, msg }) => {
  switch (intentType) {
    // config
    case 'config': {
      const cfg = payload
      if (action === 'deploy') {
        // 部署配置（复用 handleConfigDeploy 的逻辑）
        handleConfigDeploy(msg, cfg.config, cfg.keywords)
      } else if (action === 'modify') {
        inputText.value = `修改表单配置「${cfg.config?.formName || ''}」：`
        nextTick(() => inputEl.value?.focus())
      } else if (action === 'preview') {
        handleConfigPreview(cfg)
      } else if (action === 'test') {
        handleConfigTest(cfg)
      }
      break
    }
    // delete_form
    case 'delete_form': {
      if (action === 'rollback') {
        handleRollback(msg, payload)
      } else if (action === 'load-versions') {
        loadVersions(msg)
      }
      break
    }
    // manage_history
    case 'manage_history': {
      if (action === 'import') {
        handleImportHistory(msg)
      } else if (action === 'analyze') {
        handleAnalyzeHistory(msg)
      } else if (action === 'export') {
        handleExportHistory(msg, payload)
      }
      break
    }
    // validate
    case 'validate': {
      if (action === 'fix') {
        handleFixValidationErrors(msg, payload)
      } else if (action === 'ignore-warnings') {
        handleIgnoreValidationWarnings(msg)
      }
      break
    }
  }
}
const handleFixValidationErrors = (msg, errors) => {
  // 将错误信息注入到输入框，引导用户修正
  if (errors?.length > 0) {
    const first = errors[0]
    inputText.value = `修改一下：${first.fieldName || '字段'}，${first.reason}`
  }
}

const handleIgnoreValidationWarnings = (msg) => {
  // 忽略警告，继续提交流程
  ElMessage.info('已忽略提示，可继续操作')
}

const formatVersionTime = (isoStr) => {
  if (!isoStr) return ''
  const d = new Date(isoStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ── 对外方法：表单校验（通过聊天SSE流显示thinking，结果通过Promise返回）──
const requestValidation = (validationText) => {
  return new Promise(async (resolve) => {
    if (isStreaming.value) {
      resolve({ passed: false, errors: [], warnings: [], message: '聊天窗口正忙，请稍后重试' })
      return
    }

    // 推送用户消息（带 🤖 前缀表示AI在校验）
    const userMsg = { id: genId(), role: 'user', content: validationText, done: true }
    messages.value.push(userMsg)

    const aiMsg = {
      id: genId(), role: 'assistant',
      reasoning: [], streamText: '', content: '',
      showReasoning: false,
      done: false, type: 'chat'
    }
    messages.value.push(aiMsg)
    const msgIdx = messages.value.length - 1

    isStreaming.value = true
    abortCtrl = new AbortController()
    scrollToBottom()

    try {
      const chatHistory = messages.value
        .filter(m => m.role === 'user' || (m.role === 'assistant' && m.done && m.content))
        .slice(0, -1)
        .slice(-20)
        .map(m => ({ role: m.role, content: m.content || m.streamText || '' }))

      const resp = await fetch('/api/v1/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: [...chatHistory, { role: 'user', content: validationText }] }),
        signal: abortCtrl.signal
      })

      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)

      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop()

        for (const frame of frames) {
          if (!frame.startsWith('data:')) continue
          try {
            const data = JSON.parse(frame.slice(5).trim())
            handleEvent(data, msgIdx)
          } catch {}
        }
      }

      // 流结束后，从AI消息内容中解析结构化校验结果
      const finalContent = messages.value[msgIdx]?.content || messages.value[msgIdx]?.streamText || ''
      let result = { passed: true, errors: [], warnings: [], message: '' }

      // 尝试从JSON代码块中提取
      const jsonMatch = finalContent.match(/```(?:json)?\s*(\{[\s\S]*?\})\s*```/i)
        || finalContent.match(/(\{[\s\S]*?\})(?:\s*$)/)
      if (jsonMatch) {
        try {
          const parsed = JSON.parse(jsonMatch[1])
          result = {
            passed: parsed.passed !== false && parsed.errors?.length === 0,
            errors: parsed.errors || [],
            warnings: parsed.warnings || [],
            message: parsed.message || ''
          }
        } catch {}
      } else {
        // 从纯文本推断
        const hasError = /错误|问题|不符合|失败|❌/.test(finalContent)
        const hasWarning = /警告|提示|注意|⚠/.test(finalContent)
        result = {
          passed: !hasError,
          errors: hasError ? [{ fieldName: '校验', reason: finalContent.slice(0, 200) }] : [],
          warnings: hasWarning ? [{ fieldName: '提示', reason: finalContent.slice(0, 200) }] : [],
          message: finalContent.slice(0, 100)
        }
      }

      // 将结果注入到AI消息的intentResult中（便于后续追踪）
      messages.value[msgIdx].intentResult = result
      resolve(result)
    } catch (e) {
      console.error('[requestValidation] 异常:', e)
      messages.value[msgIdx].content = '校验请求失败：' + e.message
      resolve({ passed: false, errors: [{ fieldName: '系统', reason: e.message }], warnings: [], message: e.message })
    } finally {
      isStreaming.value = false
      abortCtrl = null
    }
  })
}

// 暴露给父组件
defineExpose({ requestValidation })
</script>

<style scoped>
/* ── 整体布局 ── */
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── 左侧对话区 ── */
.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}

/* ── 顶部栏 ── */
.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.session-name {
  font-size: 15px;
  font-weight: 600;
  color: #111;
}
.icon-btn {
  width: 34px; height: 34px;
  background: none; border: none;
  border-radius: 8px;
  color: #999; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background .15s, color .15s;
}
.icon-btn:hover { background: #f5f5f5; color: #555; }

/* ── 消息区 ── */
.messages-area {
  flex: 1;
  overflow-y: auto;
}
.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-track { background: transparent; }
.messages-area::-webkit-scrollbar-thumb { background: #ddd; border-radius: 3px; }
.messages-area::-webkit-scrollbar-thumb:hover { background: #ccc; }

/* ── 欢迎屏 ── */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 14vh;
  gap: 14px;
}
.welcome-logo { margin-bottom: 8px; }
.welcome-logo-inner {
  width: 64px; height: 64px;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  border-radius: 18px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  box-shadow: 0 8px 24px rgba(99,102,241,.25);
}
.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: #111;
  letter-spacing: -0.5px;
}
.welcome-subtitle {
  font-size: 14px;
  color: #888;
  margin-top: -6px;
}
.welcome-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-width: 520px;
  padding: 0 20px;
  margin-top: 18px;
}
.suggestion-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #eaeaea;
  border-radius: 12px;
  font-size: 13.5px;
  color: #333;
  cursor: pointer;
  transition: all .2s;
  text-align: left;
}
.suggestion-card:hover {
  border-color: #c7c7fa;
  box-shadow: 0 2px 12px rgba(99,102,241,.1);
  transform: translateY(-1px);
}
.suggestion-icon { font-size: 18px; flex-shrink: 0; }
.suggestion-label { flex: 1; line-height: 1.4; }
.suggestion-arrow {
  color: #ccc;
  flex-shrink: 0;
  transition: color .15s, transform .15s;
}
.suggestion-card:hover .suggestion-arrow {
  color: #818cf8;
  transform: translateX(2px);
}

/* ── 消息列表 ── */
.messages-list {
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 780px;
  margin: 0 auto;
  width: 100%;
  padding-left: 24px;
  padding-right: 24px;
}
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 0;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.user .msg-body {
  flex: none;
  max-width: 85%;
}

/* 头像 */
.avatar {
  width: 32px; height: 32px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.ai-avatar {
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: #fff;
}
.user-avatar {
  background: #e0e7ff;
  color: #6366f1;
}

/* 消息体 */
.msg-body { flex: 1; min-width: 0; }

/* 用户气泡 */
.bubble.user-bubble {
  display: inline-block;
  max-width: 100%;
  background: #fff;
  color: #1a1a1a;
  padding: 12px 16px;
  border-radius: 16px 4px 16px 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0,0,0,.04);
}

/* AI 消息区 */
.ai-message {
  max-width: 90%;
}

/* 系统步骤折叠 */
.reasoning-wrap {
  margin-bottom: 10px;
  background: linear-gradient(135deg, #f8f7ff, #fafafa);
  border: 1px solid #e8e6f0;
  border-radius: 10px;
  overflow: hidden;
}
.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 14px;
  background: none; border: none;
  cursor: pointer;
  color: #7c6fad;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
}
.reasoning-toggle:hover { background: rgba(99,102,241,.06); }
.reasoning-label { flex: 1; display: flex; align-items: center; gap: 4px; }
.reasoning-count { color: #b0a5c8; font-size: 12px; }

.thinking-dots span { animation: dotPulse 1.2s infinite; }
.thinking-dots span:nth-child(2) { animation-delay: .2s; }
.thinking-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dotPulse {
  0%, 80%, 100% { opacity: .3; }
  40% { opacity: 1; }
}

.reasoning-body {
  padding: 8px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-top: 1px solid #f0f0f0;
}
.reasoning-step {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #666;
  background: #fff;
  animation: stepFadeIn 0.3s ease;
}
.reasoning-step.step-result { background: #f0fdf4; color: #16a34a; }
.reasoning-step.step-error  { background: #fef2f2; color: #dc2626; }
.reasoning-step.step-latest { background: #eff6ff; color: #2563eb; }
.step-icon { flex-shrink: 0; margin-top: 1px; }
.step-text { flex: 1; min-width: 0; }
.step-reasoning-inline {
  flex-basis: 100%;
  margin-top: 4px;
  padding-left: 22px;
  border-top: 1px dashed #ede9fe;
  padding-top: 6px;
}
.step-reasoning-toggle {
  cursor: pointer; color: #7c3aed; font-size: 12px;
  display: inline-flex; align-items: center; gap: 4px;
  user-select: none;
  transition: color .15s;
}
.step-reasoning-toggle:hover { color: #5b21b6; }
.step-reasoning-body { margin-top: 6px; }
.step-reasoning-text {
  font-size: 12px; line-height: 1.65; color: #6b21a8;
  white-space: pre-wrap; word-break: break-word;
  background: #f5f3ff; border-radius: 8px; padding: 10px 12px;
  max-height: 240px; overflow-y: auto;
  border: 1px solid #ede9fe;
}
.step-reasoning-text::-webkit-scrollbar { width: 4px; }
.step-reasoning-text::-webkit-scrollbar-track { background: transparent; }
.step-reasoning-text::-webkit-scrollbar-thumb { background: #ddd6fe; border-radius: 2px; }
.step-loading {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  margin-left: 4px;
}
.step-loading span {
  width: 4px; height: 4px;
  background: #2563eb;
  border-radius: 50%;
  animation: dotPulse 1s infinite;
}
.step-loading span:nth-child(2) { animation-delay: 0.15s; }
.step-loading span:nth-child(3) { animation-delay: 0.3s; }

@keyframes stepFadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes dotPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50%      { opacity: 1; transform: scale(1.2); }
}

.collapse-enter-active, .collapse-leave-active {
  transition: max-height .25s ease, opacity .2s ease;
  overflow: hidden;
  max-height: 600px;
}
.collapse-enter-from, .collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

/* AI 正文 */
.ai-text {
  font-size: 14px;
  line-height: 1.8;
  color: #1a1a1a;
  word-break: break-word;
}
.ai-text :deep(p)            { margin: 0 0 10px; }
.ai-text :deep(p:last-child) { margin-bottom: 0; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; margin: 8px 0; }
.ai-text :deep(li) { margin-bottom: 4px; }
.ai-text :deep(h1),.ai-text :deep(h2),.ai-text :deep(h3) { margin: 14px 0 8px; font-weight: 600; }
.ai-text :deep(code) {
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  color: #dc2626;
}
.ai-text :deep(pre) {
  background: #1e1e2e;
  border-radius: 10px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 10px 0;
}
.ai-text :deep(pre code) {
  background: none; color: #cdd6f4; padding: 0;
  font-size: 13px;
}
.ai-text :deep(blockquote) {
  border-left: 3px solid #818cf8;
  padding-left: 14px;
  color: #666;
  margin: 8px 0;
}
.ai-text :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.ai-text :deep(th), .ai-text :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  font-size: 13px;
}
.ai-text :deep(th) { background: #f9fafb; font-weight: 600; }
.ai-text :deep(a) { color: #6366f1; }

/* 光标 */
.cursor-blink {
  display: inline-block;
  animation: blink .7s step-end infinite;
  color: #818cf8;
  margin-left: 1px;
  font-size: 15px;
}
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

/* 三点等待 */
.dots-loading {
  display: flex; gap: 5px; padding: 14px 0; align-items: center;
}
.dots-loading span {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #818cf8;
  animation: dotBounce 1.2s infinite ease-in-out;
}
.dots-loading span:nth-child(1) { animation-delay: 0s; }
.dots-loading span:nth-child(2) { animation-delay: .2s; }
.dots-loading span:nth-child(3) { animation-delay: .4s; }
@keyframes dotBounce {
  0%,80%,100% { transform: scale(.6); opacity: .4; }
  40%          { transform: scale(1);  opacity: 1;  }
}

/* 消息操作按钮 */
.msg-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  opacity: 0;
  transition: opacity .15s;
}
.msg-row:hover .msg-actions { opacity: 1; }
.action-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px;
  background: none;
  border: 1px solid #eaeaea;
  border-radius: 6px;
  font-size: 12px;
  color: #999;
  cursor: pointer;
  transition: all .15s;
}
.action-btn:hover { border-color: #818cf8; color: #818cf8; }

/* ── 输入区 ── */
.input-area {
  flex-shrink: 0;
  padding: 8px 24px 16px;
  background: #f5f5f5;
}
.quick-bar {
  display: flex;
  gap: 6px;
  max-width: 780px;
  margin: 0 auto 8px;
}
.quick-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: #fff;
  border: 1px solid #eaeaea;
  border-radius: 20px;
  font-size: 12.5px;
  color: #666;
  cursor: pointer;
  transition: all .15s;
}
.chip-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.quick-chip:hover:not(:disabled) {
  border-color: #c7c7fa;
  color: #6366f1;
  background: #f5f3ff;
}
.quick-chip:disabled { opacity: .4; cursor: not-allowed; }

.input-box {
  max-width: 780px;
  margin: 0 auto;
  background: #fff;
  border: 1.5px solid #eaeaea;
  border-radius: 16px;
  transition: border-color .2s, box-shadow .2s;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.input-box.focused {
  border-color: #818cf8;
  box-shadow: 0 0 0 3px rgba(129,140,248,.1);
}
.textarea-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 12px 10px;
}
textarea {
  flex: 1;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14.5px;
  line-height: 1.6;
  color: #111;
  font-family: inherit;
  max-height: 160px;
  overflow-y: auto;
}
textarea::placeholder { color: #c0c0c0; }
textarea::-webkit-scrollbar { width: 3px; }
textarea::-webkit-scrollbar-thumb { background: #e0e0e0; border-radius: 2px; }

.send-btn {
  width: 32px; height: 32px;
  border-radius: 8px;
  border: none;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all .15s;
  background: #f0f0f0;
  color: #bbb;
}
.send-btn.active {
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: #fff;
}
.send-btn.active:hover { transform: scale(1.05); box-shadow: 0 2px 8px rgba(99,102,241,.3); }
.send-btn:disabled { cursor: not-allowed; }
.stop-btn {
  background: #fef2f2;
  color: #ef4444;
  border: 1px solid #fecaca;
}
.stop-btn:hover { background: #fee2e2; }

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #d0d0d0;
  padding: 6px 0 0;
}

/* ── 响应式 ── */
@media (max-width: 1024px) {
  .chat-layout {
    position: relative;
  }
  .messages-list {
    max-width: 100%;
    padding-left: 16px;
    padding-right: 16px;
  }
}

@media (max-width: 768px) {
  .chat-main { height: 100%; }
  .chat-topbar { display: none; }
  .welcome-screen { padding-top: 8vh; gap: 12px; }
  .welcome-title { font-size: 24px; }
  .welcome-subtitle { font-size: 13px; }
  .welcome-suggestions {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 0 16px;
  }
  .messages-list {
    padding: 16px 14px;
    gap: 6px;
  }
  .avatar { width: 28px; height: 28px; border-radius: 8px; }
  .ai-avatar svg { width: 14px; height: 14px; }
  .user-avatar svg { width: 13px; height: 13px; }
  .bubble.user-bubble {
    max-width: 80%;
    padding: 10px 14px;
    font-size: 13.5px;
    border-radius: 14px 4px 14px 14px;
  }
  .ai-message { max-width: 92%; }
  .ai-text { font-size: 13.5px; line-height: 1.7; }
  .input-area { padding: 6px 14px 12px; }
  .quick-bar { gap: 5px; }
  .quick-chip { font-size: 12px; padding: 4px 10px; }
  textarea { font-size: 14px; }
  .input-hint { display: none; }
}

@media (max-width: 480px) {
  .welcome-title { font-size: 20px; }
  .welcome-logo-inner { width: 52px; height: 52px; border-radius: 14px; }
  .welcome-logo-inner svg { width: 26px; height: 26px; }
  .messages-list { padding: 12px 10px; }
  .msg-row { gap: 8px; }
  .bubble.user-bubble {
    max-width: 85%;
    padding: 9px 12px;
    font-size: 13px;
  }
  .ai-text { font-size: 13px; }
  .msg-actions { opacity: 1; }
  .input-area { padding: 4px 10px 10px; }
}

/* ── 删除结果面板 ── */
.delete-result-panel {
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  max-width: 360px;
}
.delete-result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #991b1b;
  margin-bottom: 4px;
}
.delete-result-tip {
  font-size: 12px;
  color: #b91c1c;
  margin: 0 0 10px;
  opacity: .75;
}
.load-versions-btn {
  font-size: 12px;
  color: #6366f1;
  background: none;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  padding: 4px 12px;
  cursor: pointer;
  transition: all .15s;
}
.load-versions-btn:hover { background: #eef2ff; }
.loading-small { font-size: 12px; color: #888; padding: 4px 0; }
.version-history { margin-top: 8px; }
.version-history-title {
  font-size: 12px;
  font-weight: 600;
  color: #555;
  margin-bottom: 6px;
}
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 4px;
  border: 1px solid #f0f0f0;
}
.version-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.version-action {
  font-size: 12px;
  font-weight: 500;
  color: #333;
}
.version-time {
  font-size: 11px;
  color: #999;
}
.rollback-btn {
  font-size: 11px;
  color: #6366f1;
  background: none;
  border: 1px solid #c7d2fe;
  border-radius: 5px;
  padding: 3px 10px;
  cursor: pointer;
  transition: all .15s;
  white-space: nowrap;
}
.rollback-btn:hover:not(:disabled) { background: #eef2ff; }
.rollback-btn:disabled { opacity: .5; cursor: not-allowed; }
.rollback-result {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
}
.rollback-result.success { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
.rollback-result.error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }

/* ── 历史数据维护面板 ── */
.history-mgmt-panel {
  margin-top: 8px;
  padding: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
  max-width: 420px;
}
.history-mgmt-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: #1e40af;
  margin-bottom: 10px;
}
.history-score {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.score-ring {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  border: 3px solid #ccc;
}
.score-ring.good { border-color: #22c55e; color: #15803d; }
.score-ring.warn { border-color: #f59e0b; color: #b45309; }
.score-ring.bad { border-color: #ef4444; color: #b91c1c; }
.score-label { font-size: 13px; color: #555; font-weight: 500; }
.stat-row, .stat-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #444;
  padding: 3px 0;
}
.no-data-tip {
  font-size: 12.5px;
  color: #b45309;
  background: #fffbeb;
  border-radius: 6px;
  padding: 8px 10px;
  margin: 8px 0;
}
.recommend-list { margin: 10px 0; }
.recommend-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
}
.recommend-item {
  font-size: 12.5px;
  color: #555;
  padding: 4px 0;
  line-height: 1.5;
}
.gen-data-btn, .import-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  padding: 7px 14px;
  border-radius: 7px;
  cursor: pointer;
  transition: all .15s;
  border: none;
  font-weight: 500;
  margin-top: 8px;
  margin-right: 6px;
}
.gen-data-btn {
  color: #1e40af;
  background: #dbeafe;
  border: 1px solid #93c5fd;
}
.gen-data-btn:hover { background: #bfdbfe; }
.gen-data-btn.outline {
  color: #555;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
}
.gen-success {
  font-size: 13px;
  color: #065f46;
  font-weight: 600;
  padding: 8px 0;
}
.preview-records { margin: 8px 0; }
.preview-title { font-size: 11.5px; font-weight: 600; color: #666; margin-bottom: 4px; }
.preview-records pre {
  font-size: 11px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 6px 10px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  line-height: 1.35;
  color: #374151;
  margin-bottom: 4px;
}
.import-actions { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.import-btn.primary {
  background: linear-gradient(135deg,#3b82f6,#2563eb);
  color: #fff;
  box-shadow: 0 2px 6px rgba(59,130,246,.25);
}
.import-btn.primary:hover:not(:disabled) { opacity: .9; transform: translateY(-1px); }
.import-btn.primary:disabled { opacity: .5; cursor: not-allowed; }
.import-btn.secondary {
  color: #555;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
}
.import-btn.secondary:hover { background: #e5e7eb; }
.import-result {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12.5px;
}
.import-result.success { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
.import-result.error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }
.import-done {
  font-size: 13px;
  color: #065f46;
  font-weight: 600;
  padding: 8px 0;
}
.field-dist { margin: 8px 0; }
.dist-title { font-size: 11.5px; font-weight: 600; color: #666; margin-bottom: 4px; }
.dist-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  padding: 3px 0;
  color: #555;
}
.fc-name { font-family: monospace; color: #333; font-weight: 500; }
.fc-count { color: #888; font-size: 11.5px; }
</style>

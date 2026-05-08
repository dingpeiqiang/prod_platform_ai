<template>
  <div class="chat-layout">
    <!-- 左侧：对话区域 -->
    <div class="chat-main">
      <!-- 顶部栏 -->
      <div class="chat-topbar">
        <span class="session-name">{{ sessionTitle }}</span>
        <div class="topbar-actions">
          <ThemeToggle />
          <button class="icon-btn" title="清空记录" @click="clearHistory">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4h6v2"/>
            </svg>
          </button>
        </div>
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
          <p class="welcome-subtitle">产商品研发助手，帮你快速填写各类表单</p>
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
                  :class="{ 'loading-text': msg.loading }"
                  v-html="renderMarkdown(msg.streamText || msg.content)"
                />
                <!-- 动态 loading 动画 -->
                <div v-if="msg.loading" class="loading-indicator">
                  <span class="loading-dot"/><span class="loading-dot"/><span class="loading-dot"/>
                </div>
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

                <!-- 表单卡片（嵌入消息的表单信息） -->
                <div v-if="msg.formCard" class="form-card">
                  <div class="form-card-header">
                    <div class="form-card-icon">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                        <polyline points="14 2 14 8 20 8"/>
                        <line x1="16" y1="13" x2="8" y2="13"/>
                        <line x1="16" y1="17" x2="8" y2="17"/>
                        <polyline points="10 9 9 9 8 9"/>
                      </svg>
                    </div>
                    <div class="form-card-info">
                      <div class="form-card-name">{{ msg.formCard.formName }}</div>
                      <div class="form-card-meta">
                        <span class="form-card-code">{{ msg.formCard.formCode }}</span>
                        <span class="form-card-sep">·</span>
                        <span>{{ msg.formCard.fieldCount }} 个字段</span>
                        <span class="form-card-sep">·</span>
                        <span>{{ formatTime(msg.formCard.createdAt) }}</span>
                      </div>
                    </div>
                    <div class="form-card-status" :class="'status-' + msg.formCard.status">
                      <span class="status-dot"></span>
                      <span class="status-text">{{ getFormStatusText(msg.formCard.status) }}</span>
                    </div>
                  </div>
                  <div class="form-card-actions">
                    <button class="form-card-btn primary" @click="focusFormPanel">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                      {{ msg.formCard.status === 'filling' ? '填写表单' : '查看详情' }}
                    </button>
                  </div>
                </div>

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
      :formSubmitted="currentFormSubmitted"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
      @field-change="handleFormFieldChange"
      @confirm-submit="handleConfirmSubmit"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormPanel from './FormPanel.vue'
import ConfigCard from './ConfigCard.vue'
import ThemeToggle from './common/ThemeToggle.vue'
// 意图面板组件
import DeleteResultPanel from './intent-panels/DeleteResultPanel.vue'
import HistoryPanel from './intent-panels/HistoryPanel.vue'
import IntentPanel from './intent-panels/IntentPanel.vue'
import ValidationResultPanel from './intent-panels/ValidationResultPanel.vue'
// 意图注册器
import { registerEventHandler, registerPostProcessor, getEventHandler, getEventPanel, getPostProcessor, listIntentPanels } from '../composables/useIntentRegistry.js'
// 数据库持久化 API
import { createSession as apiCreateSession, saveMessage, updateMessage, loadMessages as apiLoadMessages, deleteSession as apiDeleteSession } from '../services/chatApi.js'

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

// ── 校验结果事件处理器 ──────────────────────────────
// validation_fail 和 validation_pass 都写入 validation_result，渲染同一面板
const _handleValidationResult = (data, msg) => {
  if (!msg._intentData) msg._intentData = {}
  // 使用 data.type 作为 key（validation_fail 或 validation_pass），与 IntentPanel.vue 中的条件匹配
  msg._intentData[data.type] = {
    formCode: data.form_code || '',
    passed: data.type === 'validation_pass',
    errors: data.errors || [],
    warnings: data.warnings || [],
    step: data.step || '',
    rule_engine_passed: data.rule_engine_passed || false
  }
  scrollToBottom()
}

registerEventHandler('validation_fail', _handleValidationResult, { panel: ValidationResultPanel })
registerEventHandler('validation_pass', _handleValidationResult, { panel: ValidationResultPanel })

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
  // ══ 检查是否有未完成的表单 ══════════════════════════════════
  // 情况1：有活动表单（currentFormSchema 存在且未提交）
  // 情况2：待确认提交（pendingConfirmForm 存在，等待用户回复确认/取消）
  const hasActiveForm = currentFormSchema.value && !currentFormSubmitted.value
  const hasPendingConfirm = !!pendingConfirmForm

  if (hasActiveForm || hasPendingConfirm) {
    const formName = currentFormSchema.value?.formName || pendingConfirmForm?.formName || '当前表单'
    // 替换流式文本为阻塞提示
    msg.streamText = `⚠️ 检测到你有一个未完成的「${formName}」，请先完成或关闭后再发起新任务。\n\n你可以说「完成」或「提交」来完成当前表单，或者「取消」放弃当前表单。`
    msg.content = msg.streamText
    console.log('[form 拦截] 有未完成表单，阻止生成新表单:', formName)
    return
  }

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

// ── validate 意图后处理器 ──────────────────────────────
// 校验流结束后，根据结果决定是否进入提交确认流程
registerPostProcessor('validate', async (msg) => {
  // 从 _intentData 中检查校验结果
  const validationPass = msg._intentData?.validation_pass
  const validationFail = msg._intentData?.validation_fail

  if (validationFail) {
    // 校验失败：清除待确认状态
    console.log('[validate 后处理] 校验失败，清除 pendingConfirmForm')
    pendingConfirmForm = null
    // streamText 已包含校验结果，不需要额外处理
  } else if (validationPass) {
    // 校验通过：追加确认提示到 streamText（模板渲染优先取 streamText）
    if (pendingConfirmForm) {
      const schema = pendingConfirmForm.schema
      let previewLines = []
      if (schema && schema.fields) {
        for (const field of schema.fields) {
          const val = pendingConfirmForm.data[field.fieldCode]
          if (val !== undefined && val !== null && val !== '') {
            const displayVal = Array.isArray(val) ? val.join(', ') : String(val)
            previewLines.push(`- **${field.fieldName}**: ${displayVal}`)
          }
        }
      }

      const confirmText = `\n\n---\n\n✅ 校验通过！请确认是否提交？（回复"确认"或"好的"执行提交，回复"取消"放弃提交）`
      msg.streamText = (msg.streamText || '') + confirmText
    }
  } else {
    // 没有明确的校验结果事件（LLM 未返回 validation_pass/fail）
    // 兜底：如果 pendingConfirmForm 存在，直接显示确认提示
    if (pendingConfirmForm) {
      const confirmText = `\n\n---\n\n请确认是否提交当前表单？（回复"确认"或"好的"执行提交）`
      msg.streamText = (msg.streamText || '') + confirmText
    }
  }
})

const props = defineProps({
  sessionId:    { type: String, required: true },
  dbSessionId:  { type: String, default: '' },   // 数据库会话 ID（来自 App.vue）
  userId:       { type: String, default: '' },   // 当前登录用户名
  sessionTitle: { type: String, default: '新对话' }
})
const emit = defineEmits(['title-update', 'session-init'])

const messagesEl = ref(null)
const inputEl    = ref(null)

const inputText    = ref('')
const inputFocused = ref(false)
const isStreaming = ref(false)
const messages = ref([])

// 表单状态（提升到父组件管理）
const currentFormId = ref('')
const currentFormSchema = ref(null)
const currentFormSubmitted = ref(false)

let abortCtrl = null

// ── 数据库会话状态 ─────────────────────────────────────────
const currentDbSessionId = ref('')   // 当前会话对应的数据库 session_id
let isCreatingFromHome = false       // 是否正在从首页创建新会话

// 确保有数据库会话（首次发消息时调用）
const ensureDbSession = async (localSessionId) => {
  if (currentDbSessionId.value) return currentDbSessionId.value

  // 从 prop 中取（App.vue 已设置）
  if (props.dbSessionId) {
    currentDbSessionId.value = props.dbSessionId
    return currentDbSessionId.value
  }

  // prop 也没有 → 在这里创建（新建本地会话场景）
  try {
    const result = await apiCreateSession(props.userId || null, '新对话')
    if (result.success && result.session_id) {
      currentDbSessionId.value = result.session_id
      // 通知 App.vue 写入本地会话的 dbSessionId
      emit('session-init', { localId: localSessionId, dbSessionId: result.session_id })
    }
  } catch (e) {
    console.warn('[ChatAssistant] 创建 DB 会话失败:', e)
  }
  return currentDbSessionId.value
}

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

watch(() => props.sessionId, async (newSessionId, oldSessionId) => {
  // 如果正在处理流式响应，先优雅地终止
  if (isStreaming.value && oldSessionId) {
    if (abortCtrl) { abortCtrl.abort() }
    isStreaming.value = false
    abortCtrl = null
    console.log('[ChatAssistant] 切换会话，终止当前流式响应')
  }

  // 如果正在从首页创建会话，不清空消息，重置标志位
  if (isCreatingFromHome) {
    isCreatingFromHome = false
    // 只更新状态，不清空消息
    currentDbSessionId.value = props.dbSessionId || ''
    // 重置表单提交状态，避免新会话显示旧会话的"已提交"状态
    currentFormSubmitted.value = false
    loadFormState()
    return
  }

  // 正常的会话切换，清空状态
  messages.value = []
  currentFormId.value = ''
  currentFormSchema.value = null

  // 如果是首页（sessionId为空），重置状态但不创建会话
  if (!newSessionId) {
    currentDbSessionId.value = ''
    // 重置表单提交状态
    currentFormSubmitted.value = false
    loadFormState()
    return
  }

  // 切换会话时，先从 prop 更新 currentDbSessionId
  if (props.dbSessionId) {
    currentDbSessionId.value = props.dbSessionId
  } else {
    currentDbSessionId.value = ''
  }

  // 只有在没有 DB 会话时才创建新的（避免重复创建）
  if (!currentDbSessionId.value) {
    const result = await apiCreateSession(props.userId || null, '新对话')
    if (result.session_id) {
      currentDbSessionId.value = result.session_id
      // 通知 App.vue 写入本地会话的 dbSessionId
      emit('session-init', { localId: props.sessionId, dbSessionId: result.session_id })
    }
  }

  // 只有在有有效数据库会话ID时才加载消息
  if (currentDbSessionId.value) {
    try {
      const dbMsgs = await apiLoadMessages(currentDbSessionId.value)
      messages.value = dbMsgs
      // 从加载的消息中恢复最后一个表单状态
      let lastFormId = ''
      let lastFormSchema = null
      let lastFormSubmitted = false
      for (let i = dbMsgs.length - 1; i >= 0; i--) {
        const msg = dbMsgs[i]
        if (msg.formId !== undefined && msg.formSchema !== null) {
          lastFormId = msg.formId
          lastFormSchema = msg.formSchema
          lastFormSubmitted = msg.formSubmitted || false
          break
        }
      }
      if (lastFormId || lastFormSchema) {
        currentFormId.value = lastFormId
        currentFormSchema.value = lastFormSchema
        currentFormSubmitted.value = lastFormSubmitted
      } else {
        // 没有找到表单状态，从 localStorage 加载
        loadFormState()
      }
    } catch (e) {
      console.warn('[ChatAssistant] 加载消息失败:', e)
      messages.value = []
      loadFormState()
    }
  } else {
    // 没有数据库会话ID时从 localStorage 加载
    loadFormState()
  }
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

const formatStepResult = (result) => {
  if (!result) return ''
  if (typeof result === 'string') return result
  if (typeof result !== 'object') return String(result)

  // ── LLM 调用（model / temperature / maxTokens / promptLength）─────────
  if (result.model || result.provider) {
    const parts = []
    if (result.model) parts.push(`调用模型: ${result.model}`)
    if (result.provider) parts.push(`服务商: ${result.provider}`)
    if (result.temperature !== undefined) parts.push(`温度: ${result.temperature}`)
    if (result.maxTokens !== undefined) parts.push(`最大Token: ${result.maxTokens}`)
    if (result.promptLength !== undefined) parts.push(`Prompt: ${result.promptLength} 字`)
    if (result.elapsed !== undefined) parts.push(`耗时: ${result.elapsed}s`)
    if (result.retry) parts.push(`⚠️ 已重试 1 次`)
    return parts.join('\n')
  }

  // ── MCP 工具执行（tools / totalTools）───────────────────────────────
  if (result.tools && Array.isArray(result.tools)) {
    const lines = [`共 ${result.totalTools} 个工具调用`]
    result.tools.forEach(t => {
      const icon = t.success ? '✅' : '❌'
      const fields = t.fields && t.fields.length ? ` → ${t.fields.join(', ')}` : ''
      lines.push(`${icon} ${t.name}${fields}`)
    })
    if (result.extractedFields && result.extractedFields.length) {
      lines.push(`📝 提取字段: ${result.extractedFields.slice(0, 8).join(', ')}${result.extractedFields.length > 8 ? '...' : ''}`)
    }
    return lines.join('\n')
  }

  // ── 意图识别完成（intentType / formCode / extractedFields）─────────
  if (result.intentType !== undefined || result.formCode !== undefined) {
    const lines = []
    if (result.intentType) lines.push(`意图类型: ${result.intentType}`)
    if (result.formCode) lines.push(`表单编码: ${result.formCode}`)
    if (result.formName) lines.push(`表单名称: ${result.formName}`)
    if (result.extractedFields && result.extractedFields.length) lines.push(`提取字段 (${result.extractedCount}): ${result.extractedFields.slice(0, 6).join(', ')}${result.extractedCount > 6 ? '...' : ''}`)
    if (result.confidence !== undefined) lines.push(`置信度: ${(result.confidence * 100).toFixed(0)}%`)
    if (result.elapsed !== undefined) lines.push(`耗时: ${result.elapsed}s`)
    if (result.retryCount) lines.push(`⚠️ 重试: ${result.retryCount} 次`)
    return lines.join('\n')
  }

  // ── 表单识别（formCode / formName / confidenceLevel）────────────────
  if (result.formName !== undefined && !result.extractedFields) {
    const lines = []
    if (result.formCode) lines.push(`表单编码: ${result.formCode}`)
    if (result.formName) lines.push(`表单名称: ${result.formName}`)
    if (result.confidence !== undefined) lines.push(`置信度: ${(result.confidence * 100).toFixed(0)}% (${result.confidenceLevel})`)
    return lines.join('\n')
  }

  // ── 提取字段（extractedFields / extractedCount）────────────────────
  if (result.extractedCount !== undefined) {
    const lines = [`提取字段数: ${result.extractedCount}`]
    if (result.extractedFields && result.extractedFields.length) {
      lines.push(`字段列表: ${result.extractedFields.slice(0, 8).join(', ')}${result.extractedCount > 8 ? ` ...等${result.extractedCount}个` : ''}`)
    }
    if (result.sample) {
      const entries = Object.entries(result.sample).slice(0, 4)
      entries.forEach(([k, v]) => lines.push(`  ${k}: ${String(v).substring(0, 30)}`))
    }
    if (result.confidence !== undefined) lines.push(`置信度: ${(result.confidence * 100).toFixed(0)}%`)
    return lines.join('\n')
  }

  // ── 历史推荐（fieldCount / fieldSummary）──────────────────────────
  if (result.fieldCount !== undefined && result.fieldSummary) {
    const lines = [`推荐字段数: ${result.fieldCount}`]
    if (result.totalRecommendations !== undefined) lines.push(`推荐总数: ${result.totalRecommendations} 条`)
    const top = Object.entries(result.fieldSummary).slice(0, 5)
    top.forEach(([k, v]) => lines.push(`  ${k}: ${v} 条`))
    return lines.join('\n')
  }

  // ── 校验结果（passed / totalErrors）───────────────────────────────
  if (result.passed !== undefined && result.totalErrors !== undefined) {
    const lines = []
    if (result.totalErrors !== undefined) lines.push(`错误数: ${result.totalErrors}`)
    if (result.totalWarnings !== undefined) lines.push(`警告数: ${result.totalWarnings}`)
    if (result.ruleEngineErrors !== undefined) lines.push(`规则引擎错误: ${result.ruleEngineErrors}`)
    if (result.llmErrors !== undefined) lines.push(`AI 校验错误: ${result.llmErrors}`)
    lines.push(`结果: ${result.passed ? '✅ 全部通过' : '❌ 存在问题'}`)
    return lines.join('\n')
  }

  // ── 规则引擎校验（fieldsChecked / issues）─────────────────────────
  if (result.fieldsChecked && result.elapsedMs !== undefined) {
    const lines = [`检查字段: ${result.fieldCount} 个`, `耗时: ${result.elapsedMs}ms`]
    lines.push(`结果: ${result.passed ? '✅ 通过' : `❌ ${result.issueCount} 个问题`}`)
    if (result.issues && result.issues.length) {
      result.issues.slice(0, 3).forEach(iss => {
        lines.push(`  ❌ ${iss.field_name}: ${iss.message.substring(0, 40)}`)
      })
    }
    return lines.join('\n')
  }

  // ── AI 智能校验（model / elapsed / reasoningChunks）────────────────
  if (result.model && result.elapsed !== undefined) {
    const lines = [`模型: ${result.model}`, `耗时: ${result.elapsed}s`]
    if (result.llmErrors !== undefined) lines.push(`错误: ${result.llmErrors}`)
    if (result.llmWarnings !== undefined) lines.push(`警告: ${result.llmWarnings}`)
    if (result.ruleEnginePassed !== undefined) lines.push(`规则引擎: ${result.ruleEnginePassed ? '✅' : '❌'}`)
    if (result.reasoningChunks !== undefined) lines.push(`推理片段: ${result.reasoningChunks}`)
    return lines.join('\n')
  }

  // ── 分析/查询/导出结果（success / recordCount / total）────────────
  if (result.success !== undefined) {
    const lines = []
    if (result.qualityScore !== undefined) lines.push(`质量评分: ${result.qualityScore}`)
    if (result.recordCount !== undefined) lines.push(`记录数: ${result.recordCount}`)
    if (result.total !== undefined) lines.push(`查询结果: ${result.total} 条`)
    if (result.filename) lines.push(`文件名: ${result.filename}`)
    if (result.downloadUrl) lines.push(`下载地址: ${result.downloadUrl}`)
    if (result.passed === false && result.error) lines.push(`❌ ${result.error}`)
    if (result.lastUpdated) lines.push(`最后更新: ${result.lastUpdated}`)
    lines.push(`结果: ${result.success ? '✅ 成功' : '❌ 失败'}`)
    return lines.join('\n')
  }

  // ── 错误结果（success: false / error）─────────────────────────────
  if (result.success === false && result.error) {
    return `❌ 错误: ${result.error}`
  }

  // ── 删除结果（backupVersionId / success）──────────────────────────
  if (result.backupVersionId !== undefined || result.message !== undefined) {
    const lines = []
    if (result.success) {
      if (result.backupVersionId) lines.push(`备份版本: ${result.backupVersionId}`)
      lines.push(`结果: ✅ ${result.message || '删除成功'}`)
    } else {
      lines.push(`结果: ❌ ${result.error || result.message || '删除失败'}`)
    }
    return lines.join('\n')
  }

  // ── 配置生成完成（formName / fieldCount / entityCount）─────────────
  if (result.formName && result.fieldCount !== undefined) {
    const lines = []
    lines.push(`表单名称: ${result.formName}`)
    lines.push(`表单编码: ${result.formCode}`)
    lines.push(`字段数: ${result.fieldCount}`)
    lines.push(`实体数: ${result.entityCount}`)
    if (result.keywordCount !== undefined) lines.push(`关键词: ${result.keywordCount} 个`)
    if (result.validationErrors && result.validationErrors.length) {
      lines.push(`⚠️ 校验问题: ${result.validationErrors.join('; ')}`)
    }
    return lines.join('\n')
  }

  // ── Skills 模式（matchedKeywords）────────────────────────────────
  if (result.mode === 'skills') {
    const lines = [`模式: Skills 降级处理`]
    if (result.matchedKeywords && result.matchedKeywords.length) lines.push(`匹配关键词: ${result.matchedKeywords.join(', ')}`)
    if (result.error) lines.push(`原因: ${result.error}`)
    return lines.join('\n')
  }

  // ── 最后兜底：格式化 Key-Value 对 ─────────────────────────────────
  const entries = Object.entries(result).filter(([k]) => !k.startsWith('_'))
  if (entries.length === 0) return ''

  // 如果所有值都是简单类型，格式化为竖线对齐的键值表
  const isSimple = entries.every(([, v]) => v === null || v === undefined || typeof v !== 'object')
  if (isSimple) {
    return entries.map(([k, v]) => {
      const label = k.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())
      return `${label}: ${v}`
    }).join('\n')
  }

  // 有复杂值时，只显示关键字段
  const simple = entries.filter(([, v]) => typeof v !== 'object' || v === null)
  return simple.map(([k, v]) => {
    const label = k.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())
    return `${label}: ${JSON.stringify(v)}`
  }).join('\n')
}

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

// 格式化时间显示（几分钟前）
const formatTime = (isoString) => {
  if (!isoString) return ''
  const date = new Date(isoString)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// 获取表单状态文本
const getFormStatusText = (status) => {
  const map = {
    filling: '填写中',
    submitted: '已提交',
    cancelled: '已取消'
  }
  return map[status] || status
}

// 聚焦到表单面板
const focusFormPanel = () => {
  const formPanel = document.querySelector('.form-panel')
  if (formPanel) {
    formPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
    // 展开面板（如果被收起了）
    const collapseBtn = formPanel.querySelector('.collapse-btn')
    if (collapseBtn) {
      const isCollapsed = formPanel.classList.contains('collapsed')
      if (isCollapsed) collapseBtn.click()
    }
  }
}

// 更新消息中的表单卡片状态
const updateFormCardStatus = (formId, status) => {
  messages.value.forEach(msg => {
    if (msg.formCard && msg.formCard.formId === formId) {
      msg.formCard.status = status
    }
  })
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

  // 如果在首页，先创建新会话
  if (!props.sessionId) {
    // 设置标志：正在从首页创建会话
    isCreatingFromHome = true
    // 先在前端显示用户消息，让用户能看到
    messages.value.push({ id: genId(), role: 'user', content: text, done: true })
    // 告诉 App.vue 创建新会话，App.vue 会回调回来
    emit('create-session-from-home', text)
    return
  }

  // 正常发消息
  await doSendMessage(text)
}

// App.vue 创建完会话后，通过这个方法继续发消息
const sendMessageAfterSessionCreated = async (text, sessionId) => {
  // 等待一下让 watch 完成初始化
  await new Promise(resolve => setTimeout(resolve, 50))
  // 完成后续流程（创建 db 会话、保存消息、发送给 AI）
  // skipUserPush: 用户消息已在 handleInput 中 push 到 UI
  await doSendMessageAfterHome(text, { skipUserPush: true })
}

const doSendMessage = async (text) => {
  // 检查是否有待确认的表单提交
  if (pendingConfirmForm && checkUserConfirmation(text)) {
    // 用户确认提交
    await handleDoConfirmSubmit()
    return
  }

  // 检查是否取消
  if (pendingConfirmForm) {
    const msg = text.toLowerCase()
    if (msg.includes('取消') || msg.includes('不') || msg.includes('算了')) {
      handleCancelSubmit()
      return
    }
  }

  // ══ 检查是否有未完成的表单需要先处理 ═══════════════════
  // 情况1：有活动表单且用户想"完成/提交"它
  const lowerText = text.toLowerCase()
  const wantsSubmit = lowerText.includes('完成') || lowerText.includes('提交')
  const wantsCancel = lowerText.includes('取消') || lowerText.includes('算了') || lowerText.includes('不要了')

  if (currentFormSchema.value && !currentFormSubmitted.value) {
    if (wantsSubmit) {
      // 用户想完成当前表单，引导其完成并提交
      handleConfirmSubmitForActiveForm()
      return
    }
    if (wantsCancel) {
      // 用户想取消当前表单
      handleFormCancel()
      // 返回，不发送消息（用户可能只是想把表单关掉）
      return
    }
  }

  await doSendMessageAfterHome(text)
}

const doSendMessageAfterHome = async (text, { skipUserPush = false } = {}) => {

  // ── 首次发消息：确保数据库会话已创建 ──────────────────
  await ensureDbSession(props.sessionId)

  // ── 显示用户消息到 UI（首页创建会话时已在 handleInput 中 push，跳过） ─────
  if (!skipUserPush) {
    messages.value.push({ id: genId(), role: 'user', content: text, done: true })
    scrollToBottom()
  }

  // ── 保存用户消息到数据库 ─────────────────────────────
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'user',
      content: text
    })
  }

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

  // ── 立即保存空 AI 消息到数据库，获取 dbMessageId ───────────────────
  let dbMessageId = null
  if (currentDbSessionId.value) {
    try {
      const saved = await saveMessage(currentDbSessionId.value, {
        role: 'assistant',
        content: '',
        reasoning: [],
        metadata: { stream_status: 'streaming' }
      })
      // 从返回结果中提取 message_id（如果 API 返回）
      if (saved?.message_id) {
        dbMessageId = saved.message_id
        aiMsg.dbMessageId = dbMessageId
      }
    } catch (e) {
      console.warn('[SSE] 初始保存 AI 消息失败:', e)
    }
  }

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
            if (data.data) {
              intentData = data.data
              // 把可持久化字段写入 msg.metadata，saveMessage 会自动读取
              const msg = messages.value[msgIdx]
              if (msg) {
                msg.metadata = {
                  intentType:     data.data.intentType,
                  formCode:       data.data.formCode,
                  extractedFields: data.data.extractedFields,
                  confidence:     data.data.confidence,
                  model:          data.data.model
                }
              }
            }
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
      msg.showReasoning = msg.reasoning.some(r => r.type === 'error') || false

      // 根据 intentType 后处理（通过注册器分发）
      const postProcessor = getPostProcessor(intentType)
      if (postProcessor) {
        await postProcessor(msg, intentData)
      } else {
        // 默认：普通聊天
        msg.content = msg.streamText
      }

      // ── 保存/更新 AI 回复到数据库 ─────────────────────────────
      // 如果已有 dbMessageId，说明之前保存过空消息，使用 updateMessage 更新
      // 否则使用 saveMessage（兼容旧数据）
      if (currentDbSessionId.value) {
        const finalMetadata = {
          ...(msg.metadata || {}),
          reasoning_full: JSON.stringify(msg.reasoning.map((r, i) => ({ ...r, _index: i }))),
          reasoning: msg.reasoning.map(s => s.content || '').join('\n'),
          stream_status: 'done',
          done: 'true',
          // 保存意图相关字段
          intent_type: intentType || undefined,
          form_code: intentData?.formCode || undefined,
          extracted_fields: intentData?.extractedFields || undefined,
          confidence: intentData?.confidence != null ? String(intentData.confidence) : undefined,
          model: intentData?.model || undefined,
          // 保存表单状态
          formId: currentFormId.value || undefined,
          formSchema: currentFormSchema.value ? JSON.stringify(currentFormSchema.value) : undefined
        }
        // 清理 undefined 值
        Object.keys(finalMetadata).forEach(k => finalMetadata[k] === undefined && delete finalMetadata[k])

        if (msg.dbMessageId) {
          // 更新已存在的消息
          await updateMessage(currentDbSessionId.value, msg.dbMessageId, {
            content: msg.content || msg.streamText || '',
            metadata: finalMetadata
          })
        } else {
          // 首次保存（兼容旧逻辑）
          await saveMessage(currentDbSessionId.value, {
            role: 'assistant',
            content: msg.content || msg.streamText || '',
            reasoning: msg.reasoning,
            metadata: finalMetadata,
            formId: currentFormId.value,
            formSchema: currentFormSchema.value
          })
        }
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
        // 保存错误回复到数据库
        if (currentDbSessionId.value) {
          // 直接传递完整的消息对象，让 saveMessage 正确保存完整的 reasoning 结构和表单状态
          await saveMessage(currentDbSessionId.value, {
            role: 'assistant',
            content: msg.content,
            reasoning: msg.reasoning,
            metadata: msg.metadata || null,
            // 保存当前表单状态到数据库消息
            formId: currentFormId.value,
            formSchema: currentFormSchema.value
          }).catch(() => {})
        }
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
      msg.reasoning.push({ type: 'thinking', content: data.content, result: data.result || null })
      // 自动展开思考步骤，让用户看到实时进度
      msg.showReasoning = true
      // 标记最新步骤（用于动画效果）
      msg.latestStepIndex = msg.reasoning.length - 1
      scrollToBottom()

      // ── 实时更新 AI 消息的 metadata ─────────────────────────────
      // 将 thinking 步骤追加到 reasoning_full 中
      if (currentDbSessionId.value && msg.dbMessageId) {
        const stepTypeMap = { thinking: 'thinking', decision: 'decision', executing: 'action' }
        // 从现有 metadata 中恢复 reasoning_full，追加新步骤
        const existingMeta = msg.metadata || {}
        let reasoningFull = []
        if (existingMeta.reasoning_full) {
          try {
            reasoningFull = JSON.parse(existingMeta.reasoning_full)
          } catch {}
        }
        reasoningFull.push({
          type: stepTypeMap[data.type] || 'thinking',
          content: data.content,
          result: data.result || null,
          _index: reasoningFull.length
        })
        // 更新消息 metadata
        updateMessage(currentDbSessionId.value, msg.dbMessageId, {
          metadata: {
            ...existingMeta,
            reasoning_full: JSON.stringify(reasoningFull),
            stream_status: 'streaming'
          }
        }).catch(err => console.warn('[SSE] 更新 thinking 步骤失败:', err))
      }
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
    case 'manage_history':
    case 'validation_fail':
    case 'validation_pass': {
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
    case 'validation_fail':
      // 校验失败：显示错误列表
      if (data.errors?.length) {
        msg.reasoning.push({
          type: 'error',
          content: `❌ 校验失败：${data.errors.join('；')}`
        })
      }
      if (data.warnings?.length) {
        msg.reasoning.push({
          type: 'warning',
          content: `⚠️ 警告：${data.warnings.join('；')}`
        })
      }
      break
    case 'validation_pass':
      // 校验通过：显示警告（若有）或不显示
      if (data.warnings?.length) {
        msg.reasoning.push({
          type: 'warning',
          content: `⚠️ 校验通过（有警告）：${data.warnings.join('；')}`
        })
      }
      break
  }
}

// 生成表单 - 更新右侧面板，不再内嵌在消息中
const generateForm = async (intentData) => {
  const { formCode, extractedFields, fieldRecommendations } = intentData

  // 添加系统消息（带动态loading效果）
  const loadingMsg = {
    id: genId(), role: 'assistant',
    content: `正在为你生成 ${formCode || ''} 表单`, done: true, type: 'chat',
    loading: true,  // 标记为 loading 状态
    loadingDots: 0  // 动态点计数
  }
  messages.value.push(loadingMsg)
  scrollToBottom()

  // 启动 loading 动画（动态添加省略号）
  const loadingInterval = setInterval(() => {
    if (loadingMsg.loadingDots < 3) {
      loadingMsg.loadingDots++
      loadingMsg.content = `正在为你生成 ${formCode || ''} 表单${'.'.repeat(loadingMsg.loadingDots)}`
    } else {
      loadingMsg.loadingDots = 0
      loadingMsg.content = `正在为你生成 ${formCode || ''} 表单`
    }
  }, 400)

  // 保存消息到数据库
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: `正在为你生成 ${formCode || ''} 表单...`,
      reasoning: [],
      formId: currentFormId.value,
      formSchema: currentFormSchema.value
    }).catch(() => {})
  }

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
    
    // 停止 loading 动画
    clearInterval(loadingInterval)
    loadingMsg.loading = false

    const result = await resp.json()

    let replyMsg
      if (result.success) {
        // 更新右侧表单面板
        currentFormId.value = result.formId
        currentFormSchema.value = result.formSchema

        // 在消息中嵌入表单卡片
        replyMsg = {
          id: genId(), role: 'assistant',
          content: `✅ 已生成表单，请在右侧填写并提交。`, done: true, type: 'chat',
          formCard: {
            formId: result.formId,
            formName: result.formSchema?.formName || formCode,
            formCode: result.formSchema?.formCode || formCode,
            status: 'filling',  // filling | submitted | cancelled
            fieldCount: result.formSchema?.fields?.length || 0,
            createdAt: new Date().toISOString()
          }
        }
      } else {
      replyMsg = {
        id: genId(), role: 'assistant',
        content: result.message || '生成表单失败，请重试。', done: true, type: 'chat'
      }
    }
    messages.value.push(replyMsg)
    scrollToBottom()

    // 保存消息到数据库
    if (currentDbSessionId.value) {
      await saveMessage(currentDbSessionId.value, {
        role: 'assistant',
        content: replyMsg.content,
        reasoning: [],
        formId: currentFormId.value,
        formSchema: currentFormSchema.value
      }).catch(() => {})
    }
  } catch {
    const errorMsg = {
      id: genId(), role: 'assistant',
      content: '生成表单时出现网络错误，请重试。', done: true, type: 'chat'
    }
    messages.value.push(errorMsg)
    scrollToBottom()

    // 保存消息到数据库
    if (currentDbSessionId.value) {
      await saveMessage(currentDbSessionId.value, {
        role: 'assistant',
        content: errorMsg.content,
        reasoning: [],
        formId: currentFormId.value,
        formSchema: currentFormSchema.value
      }).catch(() => {})
    }
  }
}

// 更新表单字段 - 增量更新现有表单，不重新生成
const updateFormFields = async (intentData) => {
  // 兼容 formCode 和 detectedFormCode 两种字段名
  const { formCode, detectedFormCode, extractedFields } = intentData
  const actualFormCode = formCode || detectedFormCode

  // 检查是否有现有表单
  if (!currentFormSchema.value) {
    // 没有现有表单，降级为生成新表单
    const fallbackMsg = {
      id: genId(), role: 'assistant',
      content: `检测到你想要更新表单，但没有找到现有表单。正在为你生成新的 ${actualFormCode || ''} 表单...`,
      done: true, type: 'chat'
    }
    messages.value.push(fallbackMsg)
    scrollToBottom()

    // 保存消息到数据库
    if (currentDbSessionId.value) {
      await saveMessage(currentDbSessionId.value, {
        role: 'assistant',
        content: fallbackMsg.content,
        reasoning: [],
        formId: currentFormId.value,
        formSchema: currentFormSchema.value
      }).catch(() => {})
    }

    if (actualFormCode) {
      await generateForm({
        formCode: actualFormCode,
        extractedFields: extractedFields || {}
      })
    }
    return
  }

  // 有关于表单，先添加确认消息
  let loadingMsg
  if (extractedFields && Object.keys(extractedFields).length > 0) {
    const fieldNames = Object.keys(extractedFields)
    loadingMsg = {
      id: genId(), role: 'assistant',
      content: `正在更新字段: ${fieldNames.join(', ')}...`,
      done: true, type: 'chat'
    }
    messages.value.push(loadingMsg)
    scrollToBottom()

    // 保存消息到数据库
    if (currentDbSessionId.value) {
      await saveMessage(currentDbSessionId.value, {
        role: 'assistant',
        content: loadingMsg.content,
        reasoning: [],
        formId: currentFormId.value,
        formSchema: currentFormSchema.value
      }).catch(() => {})
    }
  }

  // 增量更新表单字段值
  const newSchema = JSON.parse(JSON.stringify(currentFormSchema.value))

  let replyMsg
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

      replyMsg = {
        id: genId(), role: 'assistant',
        content: `✅ 已更新 ${updatedCount} 个字段，请查看右侧表单确认。`,
        done: true, type: 'chat'
      }
    } else {
      replyMsg = {
        id: genId(), role: 'assistant',
        content: `⚠️ 未找到可更新的字段，表单项可能不匹配。`,
        done: true, type: 'chat'
      }
    }
  } else {
    replyMsg = {
      id: genId(), role: 'assistant',
      content: `⚠️ 未提取到字段值，请尝试更明确的表达。`,
      done: true, type: 'chat'
    }
  }

  messages.value.push(replyMsg)
  scrollToBottom()

  // 保存消息到数据库
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: replyMsg.content,
      reasoning: [],
      formId: currentFormId.value,
      formSchema: currentFormSchema.value
    }).catch(() => {})
  }
}

// AI 校验结果 - 显示到会话窗口
const handleAiValidation = ({ type, messages }) => {
  if (!messages || messages.length === 0) return

  const icon = type === 'warning' ? '⚠️' : '❌'
  const title = type === 'warning' ? 'AI 校验警告' : 'AI 校验错误'

  const lines = messages.map(m => `• **${m.fieldName}**: ${m.reason}`)
  const content = `${icon} ${title}：\n${lines.join('\n')}`

  const aiMsg = {
    id: genId(), role: 'assistant',
    content: content,
    done: true, type: 'chat'
  }
  messages.value.push(aiMsg)
  scrollToBottom()

  // 保存到数据库
  if (currentDbSessionId.value) {
    saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: content,
      reasoning: []
    }).catch(() => {})
  }
}

// 待确认提交的数据（用于聊天中确认）
let pendingConfirmForm = null

// 处理确认提交请求（从表单面板传来）
// 将表单数据显式构造为聊天消息，通过 SSE 流触发后端 validate 意图
const handleConfirmSubmit = async (data) => {
  const schema = currentFormSchema.value

  // 保存待确认数据
  pendingConfirmForm = {
    formId: data.formId,
    formCode: data.formCode,
    formName: data.formName,
    data: data.data,
    schema: schema
  }

  // 构造显式包含表单内容的校验消息
  // 获取字段选项列表（与 DynamicForm.getFieldOptions 逻辑一致）
  const _getFieldOptions = (field) => {
    if (field.enumConfig) {
      if (field.enumConfig.type === 'static' && Array.isArray(field.enumConfig.options)) {
        return field.enumConfig.options
      }
      if (field.enumConfig.type === 'api') {
        const fallback = field.enumConfig.api?.fallback
        if (Array.isArray(fallback)) return fallback
      }
    }
    return field.options || []
  }

  // 格式：字段名(字段编码)：值[code] —— 用方括号包裹 code，避免被 LLM 当作值的一部分
  let fieldLines = []
  if (schema && schema.fields) {
    for (const field of schema.fields) {
      const val = data.data[field.fieldCode]
      if (val !== undefined && val !== null && val !== '') {
        // 枚举字段：显示 "显示名[code]"，让 LLM 提取 code 值进行校验
        let displayVal
        const options = _getFieldOptions(field)
        if (options.length > 0) {
          if (Array.isArray(val)) {
            displayVal = val.map(v => {
              const opt = options.find(o => o.value === v)
              return opt ? `${opt.label}[${v}]` : v
            }).join(', ')
          } else {
            const opt = options.find(o => o.value === val)
            displayVal = opt ? `${opt.label}[${val}]` : String(val)
          }
        } else {
          displayVal = Array.isArray(val) ? val.join(', ') : String(val)
        }
        fieldLines.push(`- ${field.fieldName}(${field.fieldCode})：${displayVal}`)
      }
    }
  }
  const validationMessage = `请帮我校验【${data.formName}】（${data.formCode}）的填写内容是否符合业务规则：\n${fieldLines.join('\n')}`

  // 走 SSE 流（注意：不能走 doSendMessage，它会检查 pendingConfirmForm 误判为确认/取消）
  // 注意：doSendMessageAfterHome 内部会处理用户消息的 UI 显示和数据库保存，
  // 因此这里不需要手动 push 消息和 saveMessage，避免重复保存
  await doSendMessageAfterHome(validationMessage)
}

// 检查用户消息是否确认提交
const checkUserConfirmation = (userMessage) => {
  if (!pendingConfirmForm) return false
  const msg = userMessage.toLowerCase()
  // 确认关键词
  return msg.includes('确认') || msg.includes('好的') || msg.includes('是') || msg.includes('提交')
}

// 执行真正的表单提交
const handleDoConfirmSubmit = async () => {
  if (!pendingConfirmForm) return

  const formData = pendingConfirmForm.data
  const formId = pendingConfirmForm.formId
  const formName = pendingConfirmForm.formName
  const schema = pendingConfirmForm.schema

  // 清除待确认状态
  pendingConfirmForm = null

  // 清空表单状态
  currentFormId.value = ''
  currentFormSchema.value = null
  currentFormSubmitted.value = false

  // 调用后端提交 API
  try {
    const response = await fetch('/api/v1/form/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        formId: formId,
        data: formData,
        version: 1
      })
    })
    const result = await response.json()

    if (result.success) {
      // 生成提交摘要
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
      const summary = summaryLines.length > 0 ? `\n\n提交内容：\n${summaryLines.join('\n')}` : ''

      const submitMsg = {
        id: genId(), role: 'assistant',
        content: `✅ ${formName || '表单'}已成功提交！${summary}\n\n还有什么我可以帮你的吗？`,
        done: true, type: 'chat'
      }
      messages.value.push(submitMsg)
      scrollToBottom()

      // 保存消息到数据库
      if (currentDbSessionId.value) {
        await saveMessage(currentDbSessionId.value, {
          role: 'assistant',
          content: submitMsg.content,
          reasoning: [],
          formId: formId,
          formSchema: schema,
          formSubmitted: true
        }).catch(() => {})
      }

      ElMessage({ message: '表单提交成功！', type: 'success', plain: true })
    } else {
      const errorMsg = {
        id: genId(), role: 'assistant',
        content: `❌ 提交失败：${result.message || '未知错误'}`,
        done: true, type: 'chat'
      }
      messages.value.push(errorMsg)
      scrollToBottom()
      ElMessage.error(result.message || '提交失败')
    }
  } catch (e) {
    console.error('[handleDoConfirmSubmit] 提交失败:', e)
    const errorMsg = {
      id: genId(), role: 'assistant',
      content: `❌ 提交失败：${e.message}`,
      done: true, type: 'chat'
    }
    messages.value.push(errorMsg)
    scrollToBottom()
    ElMessage.error('提交失败: ' + e.message)
  }
}

// 取消提交
const handleCancelSubmit = () => {
  if (pendingConfirmForm) {
    pendingConfirmForm = null
    const cancelMsg = {
      id: genId(), role: 'assistant',
      content: '好的，已取消提交。表单数据保留在右侧，可以继续修改。',
      done: true, type: 'chat'
    }
    messages.value.push(cancelMsg)
    scrollToBottom()
  }
}

// 表单提交
const handleFormSubmit = async (formData, formId) => {
  const schema = currentFormSchema.value

  // 生成提交摘要
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

  // 保存表单状态快照（用于历史会话显示已提交的表单）
  const submittedFormId = currentFormId.value
  const submittedFormSchema = currentFormSchema.value

  currentFormId.value = ''
  currentFormSchema.value = null
  currentFormSubmitted.value = false
  ElMessage({ message: '表单提交成功！', type: 'success', plain: true })

  // 更新消息中的表单卡片状态
  updateFormCardStatus(submittedFormId, 'submitted')

  const submitMsg = {
    id: genId(), role: 'assistant',
    content: `✅ 表单已成功提交！${summary ? '\n\n' + summary : ''}\n\n还有什么我可以帮你的吗？`,
    done: true, type: 'chat'
  }
  messages.value.push(submitMsg)
  scrollToBottom()

  // 保存消息到数据库，包含表单已提交状态
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: submitMsg.content,
      reasoning: [],
      formId: submittedFormId,
      formSchema: submittedFormSchema,
      formSubmitted: true  // 标记表单已提交
    }).catch(() => {})
  }
}

// 表单取消
const handleFormCancel = async () => {
  const cancelledFormId = currentFormId.value
  currentFormId.value = ''
  currentFormSchema.value = null
  currentFormSubmitted.value = false

  // 更新消息中的表单卡片状态
  if (cancelledFormId) {
    updateFormCardStatus(cancelledFormId, 'cancelled')
  }

  const cancelMsg = {
    id: genId(), role: 'assistant',
    content: '好的，已取消。还有什么我可以帮你的吗？', done: true, type: 'chat'
  }
  messages.value.push(cancelMsg)
  scrollToBottom()

  // 保存消息到数据库
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: cancelMsg.content,
      reasoning: [],
      formId: currentFormId.value,
      formSchema: currentFormSchema.value
    }).catch(() => {})
  }
}

// 用户通过聊天说"完成/提交"时，引导其完成当前表单
const handleConfirmSubmitForActiveForm = () => {
  if (!currentFormSchema.value) return

  const formName = currentFormSchema.value.formName || '当前表单'
  const guideMsg = {
    id: genId(), role: 'assistant',
    content: `好的，请先完成右侧的「${formName}」并点击提交按钮。\n\n提交后我会帮你处理后续操作。`,
    done: true, type: 'chat'
  }
  messages.value.push(guideMsg)
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
      const successMsg = {
        id: genId(), role: 'assistant',
        content: `🎉 表单「${config.formName}」已部署成功！现在你可以说"帮我填一个${config.formName}"来测试了。`,
        done: true, type: 'chat'
      }
      messages.value.push(successMsg)
      scrollToBottom()

      // 保存消息到数据库
      if (currentDbSessionId.value) {
        await saveMessage(currentDbSessionId.value, {
          role: 'assistant',
          content: successMsg.content,
          reasoning: [],
          formId: currentFormId.value,
          formSchema: currentFormSchema.value
        }).catch(() => {})
      }
    } else {
      _updateIntentData(msg, 'config', { deploying: false })
      ElMessage.error(result.message || '部署失败')
    }
  } catch {
    _updateIntentData(msg, 'config', { deploying: false })
    ElMessage.error('部署请求失败，请重试')
  }
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

const handleConfigPreview = async (config) => {
  const schema = convertConfigToSchema(config)
  currentFormId.value = `preview_${Date.now()}`
  currentFormSchema.value = schema

  const previewMsg = {
    id: genId(), role: 'assistant',
    content: `👁️ 已将「**${config.formName}**」表单加载到右侧面板进行预览，你可以查看字段布局并填写测试。确认无误后点击部署即可正式使用。`,
    done: true, type: 'chat'
  }
  messages.value.push(previewMsg)
  scrollToBottom()

  // 保存消息到数据库
  if (currentDbSessionId.value) {
    await saveMessage(currentDbSessionId.value, {
      role: 'assistant',
      content: previewMsg.content,
      reasoning: [],
      formId: currentFormId.value,
      formSchema: currentFormSchema.value
    }).catch(() => {})
  }
}

onMounted(async () => {
  // 如果是首页（sessionId为空），不创建会话
  if (!props.sessionId) {
    currentDbSessionId.value = ''
    loadFormState()
    scrollToBottom()
    nextTick(() => inputEl.value?.focus())
    return
  }

  // App.vue 已通过 prop 传入 dbSessionId（精确匹配会话）
  if (props.dbSessionId) {
    currentDbSessionId.value = props.dbSessionId
  } else {
    currentDbSessionId.value = ''
  }

  // ── 无 DB 会话：创建新会话并立即通知 App.vue ────────────
  if (!currentDbSessionId.value) {
    const result = await apiCreateSession(props.userId || null, '新对话')
    if (result.session_id) {
      currentDbSessionId.value = result.session_id
      // 立即通知 App.vue 更新 dbSessionId，避免刷新后重复创建
      emit('session-init', { localId: props.sessionId, dbSessionId: result.session_id })
    }
  }
  
  // 只有在有有效数据库会话ID时才加载消息
  if (currentDbSessionId.value) {
    try {
      const dbMsgs = await apiLoadMessages(currentDbSessionId.value)
      messages.value = dbMsgs
      // 从加载的消息中恢复最后一个表单状态
      let lastFormId = ''
      let lastFormSchema = null
      let lastFormSubmitted = false
      for (let i = dbMsgs.length - 1; i >= 0; i--) {
        const msg = dbMsgs[i]
        if (msg.formId !== undefined && msg.formSchema !== null) {
          lastFormId = msg.formId
          lastFormSchema = msg.formSchema
          lastFormSubmitted = msg.formSubmitted || false
          break
        }
      }
      if (lastFormId || lastFormSchema) {
        currentFormId.value = lastFormId
        currentFormSchema.value = lastFormSchema
        currentFormSubmitted.value = lastFormSubmitted
      } else {
        // 没有找到表单状态，从 localStorage 加载
        loadFormState()
      }
    } catch (e) {
      console.warn('[ChatAssistant] 加载消息失败:', e)
      messages.value = []
      loadFormState()
    }
  } else {
    // 没有数据库会话ID时从 localStorage 加载
    loadFormState()
  }
  scrollToBottom()
  nextTick(() => inputEl.value?.focus())
})

// 监听 dbSessionId 变化（App.vue 切换会话时更新）
watch(() => props.dbSessionId, async (newId) => {
  if (!newId) return
  
  // 只有当新的dbSessionId与当前不同时才处理
  if (currentDbSessionId.value === newId) return
  
  currentDbSessionId.value = newId
  messages.value = []
  
  try {
    const dbMsgs = await apiLoadMessages(newId)
    messages.value = dbMsgs
    // 从加载的消息中恢复最后一个表单状态
    let lastFormId = ''
    let lastFormSchema = null
    for (let i = dbMsgs.length - 1; i >= 0; i--) {
      const msg = dbMsgs[i]
      if (msg.formId !== undefined && msg.formSchema !== null) {
        lastFormId = msg.formId
        lastFormSchema = msg.formSchema
        break
      }
    }
    if (lastFormId || lastFormSchema) {
      currentFormId.value = lastFormId
      currentFormSchema.value = lastFormSchema
    } else {
      // 没有找到表单状态，从 localStorage 加载
      loadFormState()
    }
  } catch (e) {
    console.warn('[ChatAssistant] 加载消息失败:', e)
    messages.value = []
    loadFormState()
  }
  
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
defineExpose({ requestValidation, sendMessageAfterSessionCreated })
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
  background: var(--bg-secondary);
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
  padding: 0 var(--space-6);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}
.session-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.icon-btn {
  width: 34px; height: 34px;
  background: none; border: none;
  border-radius: var(--radius-md);
  color: var(--text-tertiary); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background var(--transition-fast), color var(--transition-fast);
}
.icon-btn:hover { background: var(--bg-secondary); color: var(--text-secondary); }

/* ── 表单跳转按钮 ── */
/* ── 消息区 ── */
.messages-area {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* ── 历史记录区（在消息区下方，固定高度） ── */
.submission-history-area {
  flex-shrink: 0;
  max-height: 280px;
  overflow-y: auto;
  border-top: 1px solid var(--border-light);
  background: var(--bg-secondary);
}
.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-track { background: transparent; }
.messages-area::-webkit-scrollbar-thumb { background: var(--border-default); border-radius: 3px; }
.messages-area::-webkit-scrollbar-thumb:hover { background: var(--border-strong); }

/* ── 欢迎屏 ── */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 14vh;
  gap: var(--space-3-5);
}
.welcome-logo { margin-bottom: var(--space-2); }
.welcome-logo-inner {
  width: 64px; height: 64px;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  border-radius: var(--radius-xl);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
  box-shadow: 0 8px 24px rgba(99,102,241,.25);
}
.welcome-title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-gray-900);
  letter-spacing: -0.5px;
}
.welcome-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: -6px;
}
.welcome-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2-5);
  max-width: 520px;
  padding: 0 var(--space-5);
  margin-top: var(--space-4-5);
}
.suggestion-card {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  padding: var(--space-3-5) var(--space-4);
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
}
.suggestion-card:hover {
  border-color: var(--color-primary-200);
  box-shadow: 0 2px 12px rgba(99,102,241,.1);
  transform: translateY(-1px);
}
.suggestion-icon { font-size: 18px; flex-shrink: 0; }
.suggestion-label { flex: 1; line-height: 1.4; }
.suggestion-arrow {
  color: var(--border-strong);
  flex-shrink: 0;
  transition: color var(--transition-fast), transform var(--transition-fast);
}
.suggestion-card:hover .suggestion-arrow {
  color: var(--color-primary-400);
  transform: translateX(2px);
}

/* ── 消息列表 ── */
.messages-list {
  padding: var(--space-5) 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-width: 780px;
  margin: 0 auto;
  width: 100%;
  padding-left: var(--space-6);
  padding-right: var(--space-6);
}
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-1) 0;
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
  border-radius: var(--radius-lg);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.ai-avatar {
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  color: var(--text-inverse);
}
.user-avatar {
  background: var(--color-primary-100);
  color: var(--color-primary-600);
}

/* 消息体 */
.msg-body { flex: 1; min-width: 0; }

/* 用户气泡 */
.bubble.user-bubble {
  display: inline-block;
  max-width: 100%;
  background: var(--bg-primary);
  color: var(--text-primary);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl) 4px var(--radius-xl) var(--radius-xl);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: var(--shadow-sm);
}

/* AI 消息区 */
.ai-message {
  max-width: 90%;
}

/* 系统步骤折叠 */
.reasoning-wrap {
  margin-bottom: var(--space-2-5);
  background: linear-gradient(135deg, var(--color-primary-50), var(--bg-secondary));
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px var(--space-3-5);
  background: none; border: none;
  cursor: pointer;
  color: var(--color-primary-700);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-align: left;
}
.reasoning-toggle:hover { background: rgba(99,102,241,.06); }
.reasoning-label { flex: 1; display: flex; align-items: center; gap: 4px; }
.reasoning-count { color: var(--color-primary-300); font-size: var(--font-size-xs); }

.thinking-dots span { animation: dotPulse 1.2s infinite; }
.thinking-dots span:nth-child(2) { animation-delay: .2s; }
.thinking-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes dotPulse {
  0%, 80%, 100% { opacity: .3; }
  40% { opacity: 1; }
}

.reasoning-body {
  padding: var(--space-2) var(--space-3-5) var(--space-3);
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-top: 1px solid var(--border-light);
}
.reasoning-step {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  line-height: 1.5;
  color: var(--text-secondary);
  background: var(--bg-primary);
  animation: stepFadeIn 0.3s ease;
}
.reasoning-step.step-result { background: var(--color-success-50); color: var(--color-success-600); }
.reasoning-step.step-error  { background: var(--color-error-50); color: var(--color-error-600); }
.reasoning-step.step-latest { background: var(--color-info-50); color: var(--color-info-600); }
.step-icon { flex-shrink: 0; margin-top: 1px; }
.step-text { flex: 1; min-width: 0; }
.step-reasoning-inline {
  flex-basis: 100%;
  margin-top: var(--space-1);
  padding-left: 22px;
  border-top: 1px dashed var(--color-primary-100);
  padding-top: 6px;
}
.step-reasoning-toggle {
  cursor: pointer; color: var(--color-primary-700); font-size: var(--font-size-xs);
  display: inline-flex; align-items: center; gap: 4px;
  user-select: none;
  transition: color var(--transition-fast);
}
.step-reasoning-toggle:hover { color: var(--color-primary-800); }
.step-reasoning-body { margin-top: 6px; }
.step-reasoning-text {
  font-size: var(--font-size-xs); line-height: 1.65; color: var(--color-primary-800);
  white-space: pre-wrap; word-break: break-word;
  background: var(--color-primary-50); border-radius: var(--radius-md); padding: 10px var(--space-3);
  max-height: 240px; overflow-y: auto;
  border: 1px solid var(--color-primary-100);
}
.step-reasoning-text::-webkit-scrollbar { width: 4px; }
.step-reasoning-text::-webkit-scrollbar-track { background: transparent; }
.step-reasoning-text::-webkit-scrollbar-thumb { background: var(--color-primary-200); border-radius: 2px; }

.step-loading {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  margin-left: 4px;
}
.step-loading span {
  width: 4px; height: 4px;
  background: var(--color-info-600);
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
  font-size: var(--font-size-sm);
  line-height: 1.8;
  color: var(--text-primary);
  word-break: break-word;
}
.ai-text :deep(p)            { margin: 0 0 10px; }
.ai-text :deep(p:last-child) { margin-bottom: 0; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; margin: 8px 0; }
.ai-text :deep(li) { margin-bottom: 4px; }
.ai-text :deep(h1),.ai-text :deep(h2),.ai-text :deep(h3) { margin: 14px 0 8px; font-weight: 600; }
.ai-text :deep(code) {
  background: var(--bg-tertiary);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--color-error-600);
}
.ai-text :deep(pre) {
  background: #1e1e2e;
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  overflow-x: auto;
  margin: 10px 0;
}
.ai-text :deep(pre code) {
  background: none; color: #cdd6f4; padding: 0;
  font-size: 13px;
}
.ai-text :deep(blockquote) {
  border-left: 3px solid var(--color-primary-400);
  padding-left: var(--space-3-5);
  color: var(--text-secondary);
  margin: 8px 0;
}
.ai-text :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.ai-text :deep(th), .ai-text :deep(td) {
  border: 1px solid var(--border-default);
  padding: 8px 12px;
  font-size: 13px;
}
.ai-text :deep(th) { background: var(--bg-secondary); font-weight: 600; }
.ai-text :deep(a) { color: var(--color-primary-500); }

/* 光标 */
.cursor-blink {
  display: inline-block;
  animation: blink .7s step-end infinite;
  color: var(--color-primary-400);
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
  background: var(--color-primary-400);
  animation: dotBounce 1.2s infinite ease-in-out;
}
.dots-loading span:nth-child(1) { animation-delay: 0s; }
.dots-loading span:nth-child(2) { animation-delay: .2s; }
.dots-loading span:nth-child(3) { animation-delay: .4s; }
@keyframes dotBounce {
  0%,80%,100% { transform: scale(.6); opacity: .4; }
  40%          { transform: scale(1);  opacity: 1;  }
}

/* 动态 loading 指示器 */
.loading-indicator {
  display: inline-flex; gap: 4px; padding: 4px 0; align-items: center;
  vertical-align: middle;
}
.loading-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--color-primary-400);
  animation: dotPulse 1.2s infinite ease-in-out;
}
.loading-dot:nth-child(1) { animation-delay: 0s; }
.loading-dot:nth-child(2) { animation-delay: .2s; }
.loading-dot:nth-child(3) { animation-delay: .4s; }
@keyframes dotPulse {
  0%,80%,100% { transform: scale(.8); opacity: .5; }
  40%          { transform: scale(1.2); opacity: 1; }
}
.loading-text { opacity: 0.8; }

/* 消息操作按钮 */
.msg-actions {
  display: flex;
  gap: 6px;
  margin-top: var(--space-2);
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.msg-row:hover .msg-actions { opacity: 1; }
.action-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px;
  background: none;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.action-btn:hover { border-color: var(--color-primary-400); color: var(--color-primary-400); }

/* ── 输入区 ── */
.input-area {
  flex-shrink: 0;
  padding: var(--space-2) var(--space-6) var(--space-4);
  background: var(--bg-secondary);
}
.quick-bar {
  display: flex;
  gap: var(--space-2);
  max-width: 780px;
  margin: 0 auto var(--space-2);
  flex-wrap: wrap;
}
.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  padding: var(--space-1-5) var(--space-3);
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
  box-shadow: var(--shadow-sm);
}
.chip-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: transform var(--transition-fast);
}
.quick-chip:hover:not(:disabled) {
  border-color: var(--color-primary-300);
  color: var(--color-primary-600);
  background: var(--color-primary-50);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}
.quick-chip:hover:not(:disabled) .chip-dot {
  transform: scale(1.2);
}
.quick-chip:disabled { opacity: .4; cursor: not-allowed; }

.input-box {
  max-width: 780px;
  margin: 0 auto;
  background: var(--bg-primary);
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-xl);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  overflow: hidden;
}
.input-box.focused {
  border-color: var(--color-primary-400);
  box-shadow: 0 0 0 3px rgba(129,140,248,.1);
}
.textarea-wrap {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3) var(--space-2-5);
}
textarea {
  flex: 1;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--text-primary);
  font-family: inherit;
  min-height: 24px;
  max-height: 160px;
  overflow-y: auto;
  padding: 0;
  margin: 0;
}
textarea::placeholder { 
  color: var(--text-tertiary); 
}
textarea::-webkit-scrollbar { 
  width: 3px; 
}
textarea::-webkit-scrollbar-thumb { 
  background: var(--border-default); 
  border-radius: 2px; 
}

.send-btn {
  width: 32px; height: 32px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--transition-fast);
  background: var(--bg-tertiary);
  color: var(--text-tertiary);
}
.send-btn.active {
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  color: var(--text-inverse);
  border-color: transparent;
}
.send-btn.active:hover { transform: scale(1.05); box-shadow: 0 2px 8px rgba(99,102,241,.3); }
.send-btn:disabled { cursor: not-allowed; opacity: 0.5; }
.stop-btn {
  background: var(--color-error-50);
  color: var(--color-error-500);
  border: 1px solid var(--color-error-100);
}
.stop-btn:hover { background: var(--color-error-100); }

.input-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-tertiary);
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
  .welcome-screen { padding-top: 8vh; gap: var(--space-3); }
  .welcome-title { font-size: var(--font-size-2xl); }
  .welcome-subtitle { font-size: var(--font-size-sm); }
  .welcome-suggestions {
    grid-template-columns: 1fr;
    gap: var(--space-2);
    padding: 0 var(--space-4);
  }
  .messages-list {
    padding: var(--space-4) var(--space-3-5);
    gap: 6px;
  }
  .avatar { width: 28px; height: 28px; border-radius: var(--radius-md); }
  .ai-avatar svg { width: 14px; height: 14px; }
  .user-avatar svg { width: 13px; height: 13px; }
  .bubble.user-bubble {
    max-width: 80%;
    padding: 10px 14px;
    font-size: 13.5px;
    border-radius: var(--radius-xl) 4px var(--radius-xl) var(--radius-xl);
  }
  .ai-message { max-width: 92%; }
  .ai-text { font-size: var(--font-size-sm); line-height: 1.7; }
  .input-area { padding: 6px var(--space-3-5) var(--space-3); }
  .quick-bar { gap: 5px; }
  .quick-chip { font-size: var(--font-size-xs); padding: 4px 10px; }
  textarea { font-size: 14px; }
  .input-hint { display: none; }
}

@media (max-width: 480px) {
  .welcome-title { font-size: var(--font-size-xl); }
  .welcome-logo-inner { width: 52px; height: 52px; border-radius: 14px; }
  .welcome-logo-inner svg { width: 26px; height: 26px; }
  .messages-list { padding: var(--space-3) var(--space-2-5); }
  .msg-row { gap: var(--space-2); }
  .bubble.user-bubble {
    max-width: 85%;
    padding: 9px 12px;
    font-size: var(--font-size-sm);
  }
  .ai-text { font-size: var(--font-size-sm); }
  .msg-actions { opacity: 1; }
  .input-area { padding: 4px var(--space-2-5) var(--space-2-5); }
}

/* ── 删除结果面板 ── */
.delete-result-panel {
  margin-top: var(--space-2);
  padding: var(--space-3) var(--space-3-5);
  border: 1px solid var(--color-error-100);
  border-radius: var(--radius-lg);
  background: var(--color-error-50);
  max-width: 360px;
}
.delete-result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-error-700);
  margin-bottom: var(--space-1);
}
.delete-result-tip {
  font-size: var(--font-size-xs);
  color: var(--color-error-600);
  margin: 0 0 10px;
  opacity: .75;
}
.load-versions-btn {
  font-size: var(--font-size-xs);
  color: var(--color-primary-500);
  background: none;
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-sm);
  padding: 4px 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.load-versions-btn:hover { background: var(--color-primary-50); }
.loading-small { font-size: var(--font-size-xs); color: var(--text-secondary); padding: 4px 0; }
.version-history { margin-top: var(--space-2); }
.version-history-title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--text-secondary);
  margin-bottom: var(--space-1-5);
}
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  background: var(--bg-primary);
  border-radius: var(--radius-sm);
  margin-bottom: 4px;
  border: 1px solid var(--border-light);
}
.version-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.version-action {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
}
.version-time {
  font-size: 11px;
  color: var(--text-tertiary);
}
.rollback-btn {
  font-size: 11px;
  color: var(--color-primary-500);
  background: none;
  border: 1px solid var(--color-primary-200);
  border-radius: 5px;
  padding: 3px 10px;
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}
.rollback-btn:hover:not(:disabled) { background: var(--color-primary-50); }
.rollback-btn:disabled { opacity: .5; cursor: not-allowed; }
.rollback-result {
  margin-top: var(--space-2);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}
.rollback-result.success { background: var(--color-success-50); color: var(--color-success-700); border: 1px solid var(--color-success-100); }
.rollback-result.error { background: var(--color-error-50); color: var(--color-error-700); border: 1px solid var(--color-error-100); }

/* ── 历史数据维护面板 ── */
.history-mgmt-panel {
  margin-top: var(--space-2);
  padding: var(--space-3-5);
  border: 1px solid var(--color-info-100);
  border-radius: var(--radius-lg);
  background: var(--color-info-50);
  max-width: 420px;
}
.history-mgmt-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-info-700);
  margin-bottom: var(--space-2-5);
}
.history-score {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  margin-bottom: var(--space-3);
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
  border: 3px solid var(--border-strong);
}
.score-ring.good { border-color: var(--color-success-500); color: var(--color-success-600); }
.score-ring.warn { border-color: var(--color-warning-500); color: var(--color-warning-600); }
.score-ring.bad { border-color: var(--color-error-500); color: var(--color-error-600); }
.score-label { font-size: var(--font-size-sm); color: var(--text-secondary); font-weight: var(--font-weight-medium); }
.stat-row, .stat-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  padding: 3px 0;
}
.no-data-tip {
  font-size: var(--font-size-xs);
  color: var(--color-warning-600);
  background: var(--color-warning-50);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  margin: 8px 0;
}
.recommend-list { margin: 10px 0; }
.recommend-title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin-bottom: 6px;
}
.recommend-item {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  padding: 4px 0;
  line-height: 1.5;
}
.gen-data-btn, .import-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  padding: 7px 14px;
  border-radius: 7px;
  cursor: pointer;
  transition: all var(--transition-fast);
  border: none;
  font-weight: var(--font-weight-medium);
  margin-top: var(--space-2);
  margin-right: 6px;
}
.gen-data-btn {
  color: var(--color-info-700);
  background: var(--color-info-100);
  border: 1px solid var(--color-info-200);
}
.gen-data-btn:hover { background: var(--color-info-200); }
.gen-data-btn.outline {
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
}
.gen-success {
  font-size: var(--font-size-sm);
  color: var(--color-success-700);
  font-weight: var(--font-weight-semibold);
  padding: 8px 0;
}
.preview-records { margin: 8px 0; }
.preview-title { font-size: 11.5px; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; }
.preview-records pre {
  font-size: 11px;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 6px 10px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  line-height: 1.35;
  color: var(--color-gray-700);
  margin-bottom: 4px;
}
.import-actions { display: flex; gap: var(--space-2); margin-top: var(--space-2-5); flex-wrap: wrap; }
.import-btn.primary {
  background: linear-gradient(135deg,var(--color-info-500),var(--color-info-600));
  color: var(--text-inverse);
  box-shadow: 0 2px 6px rgba(59,130,246,.25);
}
.import-btn.primary:hover:not(:disabled) { opacity: .9; transform: translateY(-1px); }
.import-btn.primary:disabled { opacity: .5; cursor: not-allowed; }
.import-btn.secondary {
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
}
.import-btn.secondary:hover { background: var(--border-default); }
.import-result {
  margin-top: var(--space-2);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}
.import-result.success { background: var(--color-success-50); color: var(--color-success-700); border: 1px solid var(--color-success-100); }
.import-result.error { background: var(--color-error-50); color: var(--color-error-700); border: 1px solid var(--color-error-100); }
.import-done {
  font-size: var(--font-size-sm);
  color: var(--color-success-700);
  font-weight: var(--font-weight-semibold);
  padding: 8px 0;
}
.field-dist { margin: 8px 0; }
.dist-title { font-size: 11.5px; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; }
.dist-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-xs);
  padding: 3px 0;
  color: var(--text-secondary);
}
.fc-name { font-family: monospace; color: var(--text-primary); font-weight: var(--font-weight-medium); }
.fc-count { color: var(--text-tertiary); font-size: 11.5px; }

/* 表单卡片（嵌入消息） */
.form-card {
  margin-top: var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  overflow: hidden;
  max-width: 380px;
}
.form-card-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-light);
}
.form-card-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-50), var(--color-primary-100));
  color: var(--color-primary-500);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.form-card-info {
  flex: 1;
  min-width: 0;
}
.form-card-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.form-card-meta {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.form-card-code {
  font-family: monospace;
  font-size: 11px;
  background: var(--bg-tertiary);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
}
.form-card-sep {
  color: var(--text-tertiary);
}
.form-card-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  padding: 4px 10px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}
.form-card-status.status-filling {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}
.form-card-status.status-filling .status-dot {
  background: var(--color-primary-400);
  animation: pulse-dot 1.5s ease-in-out infinite;
}
.form-card-status.status-submitted {
  background: var(--color-success-50);
  color: var(--color-success-600);
}
.form-card-status.status-submitted .status-dot {
  background: var(--color-success-400);
}
.form-card-status.status-cancelled {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}
.form-card-status.status-cancelled .status-dot {
  background: var(--text-tertiary);
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.form-card-actions {
  padding: var(--space-2-5) var(--space-4);
  background: var(--bg-secondary);
}
.form-card-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  border: none;
  transition: all var(--transition-fast);
}
.form-card-btn.primary {
  background: var(--color-primary-500);
  color: white;
}
.form-card-btn.primary:hover {
  background: var(--color-primary-600);
}
</style>

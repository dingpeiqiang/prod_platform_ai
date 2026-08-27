<template>
  <el-dialog
    :model-value="visible"
    :title="`对话测试 · ${dialogTitle}`"
    width="640px"
    top="6vh"
    class="model-chat-tester"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    append-to-body
    @close="handleClose"
  >
    <div class="chat-viewport">
      <div v-if="messages.length === 0" class="chat-empty">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>向该模型发送消息开始测试多轮对话</p>
      </div>
      <div v-else class="chat-messages">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="bubble-row"
          :class="msg.role"
        >
          <div class="bubble">
            <pre class="bubble-text">{{ msg.content }}</pre>
            <div v-if="msg.role === 'assistant' && msg.streaming" class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-bar">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="streaming"
        @keydown.enter.exact.prevent="handleSend"
      />
      <div class="chat-input-actions">
        <el-button size="small" @click="clearMessages" :disabled="streaming">
          清空对话
        </el-button>
        <el-button size="small" v-if="streaming" @click="stopStream">
          停止
        </el-button>
        <el-button size="small" type="primary" :loading="streaming && !abortRef" @click="handleSend">
          发送
        </el-button>
      </div>
      <div v-if="statusText" class="chat-status" :class="statusType">{{ statusText }}</div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { chatTestStream, chatTestCompletion } from '../services/inferenceApi.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  config: { type: Object, default: null },
})

const emit = defineEmits(['update:visible'])

const dialogTitle = computed(() => {
  const c = props.config
  if (!c) return ''
  return `${c.config_name || c.model || ''}`
})

const messages = ref([])
const draft = ref('')
const streaming = ref(false)
const abortRef = ref(null)
const statusText = ref('')
const statusType = ref('')

function uid(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
}

function modelConfig() {
  const c = props.config || {}
  return {
    provider: c.provider || 'custom',
    model: c.model || '',
    api_key: c.api_key || null,
    base_url: c.base_url || null,
    temperature: c.temperature || 0.3,
    max_tokens: c.max_tokens || 2048,
    max_input_tokens: c.max_input_tokens || 180000,
    thinking: !!c.thinking,
    stream_enabled: c.stream_enabled !== false,
  }
}

/** 是否采用流式：由模型配置 stream_enabled 决定 */
function shouldUseStream() {
  return modelConfig().stream_enabled
}

/**
 * 组装多轮上下文：跳过失败的助手轮次，避免把占位错误文本（如"模型未返回内容"）
 * 作为正常助手回复回灌给模型，造成上下文污染并诱发空 choices。
 */
function buildPayloadMessages() {
  return messages.value
    .filter(m => !m.streaming && !m.failed)
    .map(m => ({ role: m.role, content: m.content }))
}

/** 将助手消息标记为失败：保留用户可见内容，但禁止其回灌历史。 */
function markAssistantFailed(id, errText = '') {
  messages.value = messages.value.map(m => (m.id === id ? { ...m, failed: true, content: errText || m.content } : m))
}

/** 将失败原因展示到状态栏（不写入消息正文，避免污染历史）。 */
function showFailure(detail = '') {
  statusText.value = detail ? String(detail) : '模型调用失败，请检查连接与参数'
  statusType.value = 'error'
}

function scrollToBottom() {
  nextTick(() => {
    const viewport = document.querySelector('.model-chat-tester .chat-viewport')
    if (viewport) viewport.scrollTop = viewport.scrollHeight
  })
}

async function handleSend() {
  const text = (draft.value || '').trim()
  if (!text) return
  if (streaming.value) return
  if (!props.config || !props.config.model) {
    ElMessage.warning('请先选择并配置模型')
    return
  }

  messages.value = [
    ...messages.value,
    { id: uid('user'), role: 'user', content: text, streaming: false },
  ]
  draft.value = ''
  statusText.value = ''
  scrollToBottom()

  const assistantId = uid('ai')
  messages.value = [
    ...messages.value,
    { id: assistantId, role: 'assistant', content: '', streaming: true },
  ]
  scrollToBottom()

  streaming.value = true
  let streamText = ''

  try {
    // 根据模型配置决定是否采用流式：stream_enabled=false 时直接走非流式
    if (shouldUseStream()) {
      const streamResult = await runStream(assistantId, text)
      streamText = streamResult.streamText || ''

      // 流式失败时降级为非流式完成（部分提供方不支持 SSE，
      // 后端可能把错误文本写入流，streamText 非空但 success=false）
      if (!streamResult.success) {
        streamText = await runCompletionFallback(assistantId, text)
      }
    } else {
      streamText = await runCompletion(assistantId, text)
    }

    // 兜底：整个回合最终没有产出有效正文 → 标记失败，避免错误占位文本回灌历史
    if (!streamText.trim()) {
      markAssistantFailed(assistantId)
      if (!statusText.value) showFailure()
    }
    finalizeAssistant(assistantId, streamText)
  } catch (e) {
    if (e?.name === 'AbortError') {
      finalizeAssistant(assistantId, streamText)
    } else {
      const errText = streamText || `调用失败: ${e.message || e}`
      markAssistantFailed(assistantId, errText)
      showFailure(errText)
      finalizeAssistant(assistantId, streamText)
    }
  } finally {
    streaming.value = false
    abortRef.value = null
    scrollToBottom()
  }
}

async function runStream(assistantId, userText) {
  let streamText = ''
  try {
    const { prom, abortCtrl } = chatTestStream(buildPayloadMessages(), modelConfig())
    abortRef.value = abortCtrl
    const resp = await prom
    if (!resp.ok) {
      return { success: false, streamText: '' }
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let streamFailed = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n')
      buffer = frames.pop()

      for (const frame of frames) {
        let dataPayload = ''
        for (const line of frame.split('\n')) {
          if (line.startsWith('data:')) {
            dataPayload += line.slice(5).trim()
          }
        }
        if (!dataPayload) continue
        let data
        try {
          data = JSON.parse(dataPayload)
        } catch {
          continue
        }
        if (data.type === 'text' && data.content != null) {
          streamText += data.content
          patchAssistant(assistantId, { content: streamText })
          scrollToBottom()
        } else if (data.type === 'text_start') {
          streamText = ''
        } else if (data.type === 'error') {
          streamFailed = true
          statusText.value = String(data.message || data.content || '调用出错')
          statusType.value = 'error'
        }
        // text_end / done：收尾
      }
    }

    // 流式返回了簿记错误文本（如 "LLM 调用失败: ..."）且无有效回复 → 视为失败
    if (/^(LLM( 调用)?失败|调用失败)/.test(streamText.trim())) {
      streamFailed = true
    }
    return { success: !streamFailed, streamText }
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    return { success: false, streamText }
  }
}

async function runCompletion(assistantId, userText) {
  try {
    const result = await chatTestCompletion(buildPayloadMessages(), modelConfig())
    const content = String(result?.content || '')
    if (result?.success === false || !content.trim()) {
      const errText = result?.message || result?.error || '模型未返回内容'
      markAssistantFailed(assistantId, errText)
      showFailure(errText)
      return ''
    }
    patchAssistant(assistantId, { content, failed: false })
    return content
  } catch (e) {
    const errText = `调用失败: ${e?.message || e}`
    markAssistantFailed(assistantId, errText)
    showFailure(errText)
    return ''
  }
}

async function runCompletionFallback(assistantId, userText) {
  try {
    statusText.value = '流式输出不可用，已切换到非流式模式'
    statusType.value = 'info'
    const result = await chatTestCompletion(buildPayloadMessages(), modelConfig())
    const content = String(result?.content || '')
    if (result?.success === false || !content.trim()) {
      const errText = result?.message || result?.error || '模型未返回内容'
      markAssistantFailed(assistantId, errText)
      showFailure(errText)
      return ''
    }
    patchAssistant(assistantId, { content, failed: false })
    return content
  } catch (e) {
    const errText = `调用失败: ${e?.message || e}`
    markAssistantFailed(assistantId, errText)
    showFailure(errText)
    return ''
  }
}

function patchAssistant(id, patch) {
  const list = messages.value.map(m => (m.id === id ? { ...m, ...patch } : m))
  messages.value = list
}

function finalizeAssistant(id, content) {
  messages.value = messages.value.map(m => {
    if (m.id !== id) return m
    return { ...m, content: content || m.content, streaming: false }
  })
}

function stopStream() {
  if (abortRef.value && typeof abortRef.value.abort === 'function') {
    abortRef.value.abort()
  }
}

function clearMessages() {
  messages.value = []
  statusText.value = ''
}

function handleClose() {
  if (abortRef.value && typeof abortRef.value.abort === 'function') {
    abortRef.value.abort()
  }
  abortRef.value = null
  streaming.value = false
  emit('update:visible', false)
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      messages.value = []
      draft.value = ''
      statusText.value = ''
      streaming.value = false
    }
  }
)
</script>

<style scoped>
.chat-viewport {
  height: 420px;
  overflow-y: auto;
  background: var(--bg-subtle, #f7f8fa);
  border-radius: 8px;
  padding: 16px;
}

.chat-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  gap: 12px;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bubble-row {
  display: flex;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--border-light, #e5e7eb);
}

.bubble-row.user .bubble {
  background: #3b82f6;
  color: #fff;
  border-color: #3b82f6;
}

.bubble-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.typing-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a0aec0;
  animation: blink 1.2s infinite both;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.chat-input-bar {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.chat-status {
  font-size: 13px;
}

.chat-status.error {
  color: #dc2626;
}
</style>

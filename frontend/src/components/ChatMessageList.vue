<template>
  <div class="messages-container" ref="messagesEl" @scroll.passive="onScroll">
    <!-- 欢迎状态 -->
    <WelcomeCards
      v-if="showWelcome"
      :mode="mode"
      @suggest="handleSuggest"
      @open-ops="$emit('open-ops')"
    />

    <!-- 消息列表 -->
    <div v-else class="messages-list">
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message-wrapper', msg.role]"
      >
        <!-- AI 消息 -->
        <template v-if="msg.role === 'assistant'">
          <!-- AI 头像 -->
          <div class="avatar ai-avatar">
            <div class="avatar-inner">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <circle cx="12" cy="12" r="6"/>
                <circle cx="12" cy="12" r="2"/>
              </svg>
            </div>
          </div>

          <!-- AI 消息内容 -->
          <div class="message-content">
            <!-- 思考过程折叠模块 -->
            <div class="thinking-block" :class="{ collapsed: isThinkingCollapsed(msg) }">
              <button class="thinking-header" @click="toggleThinking(msg)">
                <svg
                  class="thinking-chevron"
                  width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
                <span class="thinking-icon" v-if="msg.thinkingActive">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                  </svg>
                </span>
                <span class="thinking-title">
                  {{ msg.thinkingActive ? '正在思考' : '已思考' }}
                </span>
                <span class="thinking-duration" v-if="!msg.thinkingActive && msg.thinkingDuration">
                  用时 {{ msg.thinkingDuration }}s
                </span>
              </button>
              <transition name="thinking-slide">
                <div class="thinking-body" v-show="!isThinkingCollapsed(msg)">
                  <ul class="thinking-steps">
                    <li
                      v-for="(step, idx) in (msg.thinkingSteps || [])"
                      :key="idx"
                      class="thinking-step"
                      :class="{
                        active: msg.thinkingActive && idx === (msg.thinkingSteps || []).length - 1,
                        done: !msg.thinkingActive || idx < (msg.thinkingSteps || []).length - 1
                      }"
                    >
                      <span class="step-dot" v-if="!msg.thinkingActive || idx < (msg.thinkingSteps || []).length - 1">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                          <polyline points="20 6 9 17 4 12" />
                        </svg>
                      </span>
                      <span class="step-spinner" v-else></span>
                      <span class="step-text">{{ step }}</span>
                    </li>
                  </ul>
                </div>
              </transition>
            </div>

            <!-- 详情卡片：查看完整分析（位于思考下方、正文上方） -->
            <div v-if="msg.done && msg.detailCard" class="detail-card" @click="$emit('open-detail', msg.detailCard)">
              <div class="detail-card-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="9" y1="13" x2="15" y2="13"/>
                  <line x1="9" y1="17" x2="13" y2="17"/>
                </svg>
              </div>
              <div class="detail-card-body">
                <div class="detail-card-title">{{ msg.detailCard.title }}</div>
                <div class="detail-card-desc">点击查看完整分析报告，含趋势数据、客群结构、竞品对比、根因定位</div>
              </div>
              <svg class="detail-card-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
            </div>

            <!-- 正文内容 -->
            <div class="message-bubble ai-bubble">
              <!-- 有正在流式输出的文本 -->
              <div
                v-if="displayText(msg)"
                class="markdown-body"
                v-html="renderMarkdown(displayText(msg))"
                @click="onMarkdownClick($event, msg)"
              />
              <!-- 打字指示器 -->
              <div v-else-if="msg.loading" class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>

            <!-- 附件展示 -->
            <div v-if="msg.attachments?.length" class="attachments-container">
              <div
                v-for="(attachment, aidx) in msg.attachments"
                :key="aidx"
                class="attachment-item"
              >
                <img v-if="attachment.type === 'image'" :src="attachment.preview || attachment.url" />
                <div v-else class="file-preview">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                  <span>{{ attachment.name }}</span>
                </div>
              </div>
            </div>

            <!-- 底部工具栏 -->
            <div v-if="msg.done && displayText(msg)" class="message-actions">
              <button class="action-btn" @click="handleFeedback(msg, 'like')" title="赞同">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/>
                </svg>
              </button>
              <button class="action-btn" @click="handleFeedback(msg, 'dislike')" title="不赞同">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3"/>
                </svg>
              </button>
              <button class="action-btn" @click="copyText(displayText(msg))" title="复制">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
              <button class="action-btn" @click="$emit('regenerate', msg)" title="重新生成">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="23 4 23 10 17 10"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
            </div>

            <!-- 待办卡片列表 -->
            <div v-if="msg.done && msg.todoItems?.length" class="todo-cards">
              <div
                v-for="item in msg.todoItems"
                :key="item.id"
                class="todo-card"
                :class="`todo-${item.level}`"
              >
                <div class="todo-card-main">
                  <span class="todo-level">{{ item.levelLabel }}</span>
                  <span class="todo-type">{{ item.type }}</span>
                  <span class="todo-content">{{ item.content }}</span>
                </div>
                <div class="todo-card-actions">
                  <button
                    v-for="action in item.actions"
                    :key="action.key"
                    type="button"
                    class="todo-action-btn"
                    @click="$emit('todo-action', { item, action })"
                  >
                    {{ action.label }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 下一步体验引导 -->
            <div v-if="msg.done && msg.nextSteps?.length" class="next-steps">
              <span class="next-label">下一步可以：</span>
              <button
                v-for="step in msg.nextSteps"
                :key="step"
                type="button"
                class="next-chip"
                @click="$emit('suggest', step)"
              >
                {{ step }}
              </button>
            </div>

            <!-- 动作分流按钮：track 类直接触发，其余回显到输入框 -->
            <div v-if="msg.done && msg.followUpActions?.length" class="follow-up-actions">
              <button
                v-for="action in msg.followUpActions"
                :key="action.key"
                type="button"
                class="follow-up-btn"
                @click="onFollowUpAction(action, msg)"
              >
                {{ action.label }}
              </button>
            </div>
          </div>
        </template>

        <!-- 用户消息 -->
        <template v-else>
          <div class="message-content user-content">
            <div class="message-bubble user-bubble">
              <div v-if="msg.content" class="message-text">{{ msg.content }}</div>

              <!-- 用户附件 -->
              <div v-if="msg.attachments?.length" class="user-attachments">
                <div
                  v-for="(attachment, aidx) in msg.attachments"
                  :key="aidx"
                  class="user-attachment-item"
                >
                  <img v-if="attachment.type === 'image'" :src="attachment.preview || attachment.url" />
                  <div v-else-if="attachment.type === 'voice'" class="voice-preview">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
                    </svg>
                    <span>{{ attachment.duration || '00:00' }}</span>
                  </div>
                  <div v-else class="file-preview">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    </svg>
                    <span>{{ attachment.name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 用户头像 -->
          <div class="avatar user-avatar">
            <div class="avatar-inner">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="8" r="4"/>
                <path d="M20 21a8 8 0 1 0-16 0"/>
              </svg>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import WelcomeCards from './WelcomeCards.vue'

const props = defineProps({
  messages: { type: Array, required: true },
  showWelcome: { type: Boolean, default: false },
  mode: { type: String, default: 'rd' }
})

const emit = defineEmits(['regenerate', 'suggest', 'todo-action', 'open-detail', 'fill-input', 'action', 'open-ops'])

const BOTTOM_THRESHOLD = 140

const messagesEl = ref(null)
const stickToBottom = ref(true)
let resizeObserver = null
let scheduleRaf = 0
let smoothTimer = 0
let prevMessageCount = 0
let programmaticScroll = false
let followLockUntil = 0

const showWelcome = computed(() => props.showWelcome || props.messages.length === 0)

const displayText = (msg) => msg?.streamText || msg?.content || ''

// 直接执行类动作（如 track 查看审批进度、audit 智能稽核并提交、open-query-archive/open-draft-archive 调阅产品档案）直接 emit action；其余回填输入框
const DIRECT_ACTIONS = ['track', 'audit', 'open-ops-view', 'open-traffic-view']
const onFollowUpAction = (action, msg) => {
  if (action.key && (DIRECT_ACTIONS.includes(action.key) || action.key.startsWith('view-stock-detail-') || action.key.startsWith('open-query-archive-') || action.key.startsWith('open-draft-archive-') || action.key.startsWith('compare-remove-'))) {
    emit('action', { action, msg })
  } else {
    emit('fill-input', action.text)
  }
}

// 点击 markdown 表格/正文里的「查看详情」，把存量产品检索结果里的行映射为"查看详情"动作
const onMarkdownClick = (e, msg) => {
  const anchor = e.target && e.target.closest ? e.target.closest('a') : null
  if (!anchor) return
  const href = anchor.getAttribute('href') || ''
  const m = href.match(/^#view-(\d+)$/)
  const results = msg?.stockResults
  if (m && Array.isArray(results)) {
    const p = results[Number(m[1])]
    if (p) {
      e.preventDefault()
      onFollowUpAction(
        { key: `view-stock-detail-${p.code}`, label: `查看详情 · ${p.name}`, productCode: p.code, text: `查看 ${p.name}（${p.code}）的配置详情` },
        msg
      )
    }
  }
}

const thinkingCollapsed = ref({})

const isThinkingCollapsed = (msg) => {
  const key = msg?.id
  if (key == null) return false
  if (key in thinkingCollapsed.value) return thinkingCollapsed.value[key]
  return false
}

const toggleThinking = (msg) => {
  const key = msg?.id
  if (key == null) return
  thinkingCollapsed.value[key] = !isThinkingCollapsed(msg)
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const handleSuggest = (content) => {
  emit('suggest', content)
}

const isNearBottom = () => {
  const el = messagesEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < BOTTOM_THRESHOLD
}

const onScroll = () => {
  if (programmaticScroll || Date.now() < followLockUntil) return
  stickToBottom.value = isNearBottom()
}

const jumpToBottom = () => {
  const el = messagesEl.value
  if (!el) return
  programmaticScroll = true
  followLockUntil = Date.now() + 80
  el.scrollTop = el.scrollHeight
  programmaticScroll = false
  stickToBottom.value = true
}

const scrollToBottom = (smooth = false, force = false) => {
  if (force) stickToBottom.value = true
  if (!force && !stickToBottom.value) return

  nextTick(() => {
    const el = messagesEl.value
    if (!el) return

    if (smooth) {
      programmaticScroll = true
      followLockUntil = Date.now() + 450
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
      if (smoothTimer) clearTimeout(smoothTimer)
      smoothTimer = setTimeout(() => {
        programmaticScroll = false
        jumpToBottom()
        smoothTimer = 0
      }, 420)
      return
    }

    jumpToBottom()
    requestAnimationFrame(() => {
      if (stickToBottom.value) jumpToBottom()
    })
  })
}

const scheduleScrollToBottom = (force = false) => {
  if (force) stickToBottom.value = true
  if (!force && !stickToBottom.value) return

  if (scheduleRaf) cancelAnimationFrame(scheduleRaf)
  scheduleRaf = requestAnimationFrame(() => {
    scheduleRaf = 0
    scrollToBottom(false, force)
  })
}

const copyText = async (text) => {
  try {
    await navigator.clipboard.writeText(text.replace(/<[^>]*>/g, ''))
    ElMessage({ message: '已复制', type: 'success', duration: 1500 })
  } catch {
    ElMessage.error('复制失败')
  }
}

const handleFeedback = (msg, type) => {
  ElMessage({ message: type === 'like' ? '感谢反馈' : '我们会继续改进', type: 'success', duration: 1500 })
}

watch(
  () => [
    props.messages.length,
    props.messages[props.messages.length - 1]?.streamText?.length ?? 0,
    props.messages[props.messages.length - 1]?.content?.length ?? 0,
    props.messages[props.messages.length - 1]?.loading,
    props.messages[props.messages.length - 1]?.done,
  ],
  () => {
    const count = props.messages.length
    const force = count > prevMessageCount
    prevMessageCount = count
    scheduleScrollToBottom(force)
  }
)

const bindResizeObserver = () => {
  if (typeof ResizeObserver === 'undefined' || !messagesEl.value) return
  resizeObserver?.disconnect()
  resizeObserver = new ResizeObserver(() => {
    if (stickToBottom.value) scheduleScrollToBottom()
  })
  const target = messagesEl.value.querySelector('.messages-list') || messagesEl.value
  resizeObserver.observe(target)
}

onMounted(() => {
  prevMessageCount = props.messages.length
  nextTick(() => jumpToBottom())
  bindResizeObserver()
})

watch(showWelcome, async () => {
  await nextTick()
  bindResizeObserver()
  if (!showWelcome.value) scheduleScrollToBottom(true)
})

onUnmounted(() => {
  if (scheduleRaf) cancelAnimationFrame(scheduleRaf)
  if (smoothTimer) clearTimeout(smoothTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
})

defineExpose({ scrollToBottom })
</script>

<style scoped>
.messages-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 0 24px;
  min-height: 0;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background: var(--border-light);
  border-radius: 3px;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin: 0;
  padding: 0 24px;
  width: 100%;
}

/* 消息包装器 */
.message-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  width: 100%;
}

.message-wrapper.assistant {
  justify-content: flex-start;
}

.message-wrapper.user {
  justify-content: flex-end;
}

/* 头像 */
.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-avatar .avatar-inner {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: white;
}

.user-avatar .avatar-inner {
  background: #e0e7ff;
  color: #4f46e5;
}

/* 消息内容区 */
.message-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 70%;
  flex-shrink: 1;
}

.message-content.user-content {
  align-items: flex-end;
  max-width: min(70%, 560px);
}

/* 思考过程折叠模块 */
.thinking-block {
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.thinking-chevron {
  transition: transform 0.2s ease;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.thinking-block.collapsed .thinking-chevron {
  transform: rotate(-90deg);
}

.thinking-icon {
  display: flex;
  align-items: center;
  color: #6366f1;
}

.thinking-icon svg {
  animation: thinking-spin 1s linear infinite;
}

@keyframes thinking-spin {
  to { transform: rotate(360deg); }
}

.thinking-title {
  font-weight: 500;
  color: var(--text-secondary);
}

.thinking-block:not(.collapsed) .thinking-title,
.thinking-block .thinking-title {
  font-weight: 500;
}

.thinking-duration {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: 4px;
}

.thinking-body {
  padding: 0 12px 10px 34px;
}

.thinking-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.thinking-step {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}

.thinking-step.done {
  color: var(--text-tertiary);
}

.thinking-step.done .step-dot {
  color: #94a3b8;
  display: flex;
  align-items: center;
}

.thinking-step.active .step-text {
  color: #6366f1;
}

.step-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid #e0e7ff;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: thinking-spin 0.7s linear infinite;
  flex-shrink: 0;
}

.step-text {
  flex: 1;
}

/* 折叠动画 */
.thinking-slide-enter-active,
.thinking-slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.thinking-slide-enter-from,
.thinking-slide-leave-to {
  opacity: 0;
  max-height: 0;
}
.thinking-slide-enter-to,
.thinking-slide-leave-from {
  opacity: 1;
  max-height: 400px;
}

/* 消息气泡 */
.message-bubble {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.ai-bubble {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  color: var(--text-primary);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02);
  transition: box-shadow 0.2s ease;
}

.ai-bubble:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0, 0, 0, 0.03);
}

.user-bubble {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.22), 0 2px 6px rgba(37, 99, 235, 0.12);
  text-align: left;
}

.user-bubble .message-text {
  white-space: pre-wrap;
}

/* markdown 渲染样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 18px; }
.markdown-body :deep(h3) { font-size: 16px; }
.markdown-body :deep(h4) { font-size: 15px; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 8px 0; padding-left: 24px; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, monospace;
}
.markdown-body :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 10px 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-size: 13px;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--text-secondary);
  background: rgba(59, 130, 246, 0.04);
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-default);
  padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--bg-tertiary);
  font-weight: 600;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 6px 0;
  align-items: center;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.5);
    opacity: 0.4;
  }
  40% {
    transform: scale(1.1);
    opacity: 1;
  }
}

/* 附件 */
.attachments-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.attachment-item {
  max-width: 200px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-light);
}

.attachment-item img {
  width: 100%;
  height: auto;
  display: block;
}

.file-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--bg-tertiary);
  font-size: 13px;
  color: var(--text-secondary);
}

/* 用户附件 */
.user-attachments {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.user-attachment-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  font-size: 13px;
}

.user-attachment-item .file-preview {
  padding: 0;
  background: transparent;
  color: inherit;
  gap: 6px;
}

.user-attachment-item img {
  max-width: 180px;
  max-height: 120px;
  border-radius: 6px;
}

.voice-preview {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 消息操作栏 */
.message-actions {
  display: flex;
  gap: 4px;
  padding: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-wrapper.assistant:hover .message-actions {
  opacity: 1;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.next-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 0 2px;
}

.next-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.next-chip {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.next-chip:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

/* 待办卡片 */
.todo-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.todo-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--border-light);
  background: #fff;
}

.todo-urgent { border-left: 3px solid #ef4444; background: #fef2f2; }
.todo-important { border-left: 3px solid #f59e0b; background: #fffbeb; }
.todo-reminder { border-left: 3px solid #10b981; background: #f0fdf4; }

.todo-card-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.todo-level {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.todo-type {
  font-size: 12px;
  color: var(--text-tertiary);
  background: rgba(0,0,0,0.04);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.todo-content {
  font-size: 13px;
  color: var(--text-primary);
}

.todo-card-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.todo-action-btn {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 6px;
  padding: 4px 12px;
  min-width: 80px;
  text-align: center;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
  box-sizing: border-box;
}

.todo-action-btn:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

/* 详情卡片 */
.detail-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  cursor: pointer;
  transition: all 0.2s ease;
}

.detail-card:hover {
  border-color: #93c5fd;
  background: linear-gradient(135deg, #eff6ff 0%, #f0f9ff 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.detail-card-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #dbeafe;
  color: #2563eb;
}

.detail-card-body {
  flex: 1;
  min-width: 0;
}

.detail-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-card-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.detail-card-arrow {
  flex-shrink: 0;
  color: var(--text-tertiary);
  transition: transform 0.15s ease;
}

.detail-card:hover .detail-card-arrow {
  transform: translateX(3px);
  color: #2563eb;
}

/* 动作分流按钮 */
.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding: 0 2px;
}

.follow-up-btn {
  border: 1px solid #c7d2fe;
  background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
  color: #4338ca;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.follow-up-btn:hover {
  background: linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%);
  border-color: #a5b4fc;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
  transform: translateY(-1px);
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .user-bubble {
    background: #3b82f6;
  }

  .ai-bubble {
    background: var(--bg-tertiary);
    border-color: var(--border-default);
  }
}

/* 响应式 */
@media (max-width: 1024px) {
  .messages-container {
    padding: 16px 0;
  }

  .messages-list {
    padding: 0 20px;
  }

  .message-content {
    max-width: 75%;
  }
}

@media (max-width: 768px) {
  .messages-container {
    padding: 12px 0;
  }

  .messages-list {
    padding: 0 12px;
    gap: 16px;
  }

  .message-wrapper {
    gap: 8px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  .message-content {
    max-width: 85%;
  }

  .message-header {
    padding: 0 2px;
  }

  .ai-label {
    font-size: 12px;
  }

  .message-time {
    font-size: 11px;
  }

  .message-bubble {
    padding: 10px 12px;
    font-size: 14px;
    line-height: 1.6;
    border-radius: 14px;
  }

  .ai-bubble {
    border-bottom-left-radius: 3px;
  }

  .user-bubble {
    border-bottom-right-radius: 3px;
  }

  .message-actions {
    opacity: 1;
    padding: 2px;
  }

  .action-btn {
    width: 26px;
    height: 26px;
  }

  .attachment-item {
    max-width: 150px;
  }

  .user-attachment-item img {
    max-width: 140px;
    max-height: 100px;
  }
}

@media (max-width: 480px) {
  .messages-container {
    padding: 8px 0;
  }

  .messages-list {
    padding: 0 10px;
    gap: 12px;
  }

  .message-wrapper {
    gap: 6px;
  }

  .avatar {
    width: 28px;
    height: 28px;
  }

  .avatar-inner svg {
    width: 14px;
    height: 14px;
  }

  .message-content {
    max-width: 90%;
  }

  .message-bubble {
    padding: 8px 10px;
    font-size: 13px;
    border-radius: 12px;
  }

  .attachment-item {
    max-width: 120px;
  }

  .user-attachment-item img {
    max-width: 120px;
    max-height: 80px;
  }
}

/* 移动端触摸优化 */
@media (pointer: coarse) {
  .message-actions {
    opacity: 1;
  }

  .action-btn {
    min-width: 32px;
    min-height: 32px;
  }
}

/* 大屏幕优化 */
@media (min-width: 1400px) {
  .message-content {
    max-width: 65%;
  }
}

/* 小高度屏幕优化 */
@media (max-height: 600px) {
  .messages-container {
    padding: 8px 0;
  }

  .messages-list {
    gap: 12px;
  }
}
</style>

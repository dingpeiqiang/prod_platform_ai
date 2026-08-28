<template>
  <div class="messages-container" ref="messagesEl" @scroll.passive="onScroll">
    <!-- 欢迎状态 -->
    <WelcomeCards 
      v-if="showWelcome"
      :mode="mode"
      @suggest="handleSuggest"
    />

    <!-- 消息列表 -->
    <div v-else class="messages-list">
      <div
        v-for="(msg, idx) in messages"
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
            <!-- 头部：AI 标识 + 时间 -->
            <div class="message-header">
              <span class="ai-label">AI 助手</span>
              <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
            </div>

            <!-- 思考过程（时间线；本体推理为其中一环，含网络图 + 推理预览） -->
            <ThinkingProcessPanel
              v-if="msg.reasoning && msg.reasoning.length"
              :steps="msg.reasoning"
              :show="msg.showReasoning !== false"
              :streaming="msg.loading || !msg.done"
              :localize="localizeStepText"
              @toggle="toggleReasoning(idx)"
              @complete="(payload) => onThinkingComplete(msg, payload)"
            />

            <!-- 查询计划（历史会话恢复时亦有值；实时流以后端业务化方案文本承载） -->
            <QueryPlanCard
              v-if="msg.queryPlan && isReplySettled(msg)"
              :plan="msg.queryPlan"
            />

            <!-- 正文内容：有思考过程时等思考播完再自上而下打出 -->
            <div class="message-bubble ai-bubble">
              <MessageCard
                v-if="replyDisplayText(msg)"
                :content="replyDisplayText(msg)"
                :streaming="isReplyTyping(msg)"
              />
              <div
                v-else-if="shouldShowReplyPlaceholder(msg)"
                class="typing-indicator"
                :class="{ 'after-think': msg.reasoning?.length }"
              >
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

            <!-- 结论依据（翻译层 done 事件实时下发；历史会话恢复亦有值） -->
            <EvidenceCard
              v-if="msg.evidence && isReplySettled(msg)"
              :evidence="msg.evidence"
            />

            <!-- 执行态徽标 + 撤销（可逆操作，v3.2） -->
            <div
              v-if="msg.done && isReplySettled(msg) && (msg.undoable || msg.actionState)"
              class="exec-bar"
            >
              <span
                class="exec-badge"
                :class="`exec-${execState(msg)}`"
              >{{ execStateLabel(execState(msg)) }}</span>
              <button
                v-if="msg.undoable && msg.undoable.actionId"
                type="button"
                class="undo-btn"
                @click="$emit('undo-action', { msg, actionId: msg.undoable.actionId })"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
                </svg>
                {{ msg.undoable.undoLabel || '撤销' }}
              </button>
            </div>

            <!-- 意图结果卡紧跟正文，避免「详见下方」与卡片被操作栏隔开 -->
            <IntentPanel
              v-if="msg.intentType && msg.intentData && isReplySettled(msg)"
              :intentType="msg.intentType"
              :msg="msg"
              @intent-action="$emit('intent-action', $event)"
            />

            <!-- 底部工具栏：有思考过程时等正文打完再出现 -->
            <div v-if="msg.done && isReplySettled(msg) && (msg.streamText || msg.content)" class="message-actions">
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
              <button class="action-btn" @click="copyText(msg.streamText || msg.content)" title="复制">
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

            <!-- 表单卡片（可展开为内联编辑器） -->
            <template v-if="msg.formCard && isReplySettled(msg)">
              <div v-if="!isActiveForm(msg.formCard)" class="form-card" @click="$emit('form-card-click', msg)">
                <div class="form-card-header">
                  <div class="form-card-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                    </svg>
                  </div>
                  <div class="form-card-info">
                    <div class="form-card-name">{{ msg.formCard.formName }}</div>
                    <div class="form-card-meta">
                      <span>{{ msg.formCard.fieldCount }} 个字段</span>
                      <span class="dot">·</span>
                      <span>{{ formatTime(msg.formCard.createdAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <InlineFormEditor
                v-else
                :card="activeFormCard"
                @submit="$emit('form-submit', $event)"
                @cancel="$emit('form-cancel')"
                @field-change="(e) => $emit('form-field-change', e)"
                @ai-validation="(e) => $emit('form-ai-validation', e)"
                @confirm-submit="$emit('form-confirm-submit', $event)"
                @close="$emit('form-close')"
              />
            </template>

            <!-- 智读批次清单（对话内联） -->
            <BatchInlineCard
              v-if="msg.batch && isReplySettled(msg)"
              :batch="msg.batch"
              :batch-items="msg.batchItems"
              @confirm="$emit('batch-confirm', msg)"
              @fix="$emit('batch-fix', msg)"
              @delete="(it) => $emit('batch-delete', { msg, item: it })"
            />

            <!-- 长对话：文件/批次引用锚（已引用文档记忆条） -->
            <div v-if="msg.fileRef && isReplySettled(msg)" class="file-ref-anchor" @click="$emit('file-ref-click', msg)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
              </svg>
              <span class="fra-name">已引用文档：{{ msg.fileRef.fileName }}</span>
              <span class="fra-counts">{{ fileRefCounts(msg.fileRef) }}</span>
            </div>

            <!-- 查询结果卡片列表 -->
            <div v-if="msg.queryResults?.length && isReplySettled(msg)" class="query-results">
              <div
                v-for="p in msg.queryResults"
                :key="p.id"
                class="query-result-item"
                @click="$emit('query-result-click', p)"
              >
                <div class="qr-header">
                  <span class="qr-name">{{ p.name }}</span>
                  <span v-if="p.code" class="qr-code">{{ p.code }}</span>
                </div>
                <p v-if="p.desc" class="qr-desc">{{ p.desc }}</p>
                <button type="button" class="qr-copy-btn" @click.stop="$emit('query-result-click', p)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  复制配置
                </button>
              </div>
            </div>

            <!-- 下一步体验引导 -->
            <div v-if="msg.done && msg.nextSteps?.length && isReplySettled(msg)" class="next-steps">
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

            <!-- 澄清补参：后端判定必填参数缺失时，内联表单让用户补充后结构化回传 -->
            <div v-if="msg.done && msg.clarify?.length && isReplySettled(msg)" class="clarify-area">
              <div class="clarify-title">请补充以下信息：</div>
              <div v-for="(p, pidx) in msg.clarify" :key="p" class="clarify-field">
                <span class="clarify-label">{{ clarifyParamLabel(p) }}</span>
                <input
                  v-model="clarifyDrafts[`${msg.id}:${p}`]"
                  class="clarify-input"
                  :placeholder="`请输入${clarifyParamLabel(p)}`"
                  @keyup.enter="submitClarify(msg)"
                />
              </div>
              <div class="clarify-actions">
                <button type="button" class="clarify-submit" @click="submitClarify(msg)">继续</button>
                <button type="button" class="clarify-skip" @click="dismissClarify(msg)">跳过</button>
              </div>
            </div>

            <!-- 翻译层异常提示：执行阶段失败时的可见反馈（非空白） -->
            <div v-if="msg.done && msg.agentError && isReplySettled(msg)" class="agent-error-hint">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              <span>{{ msg.agentError }}</span>
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
import { ref, nextTick, onMounted, onUnmounted, computed, watch, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import WelcomeCards from './WelcomeCards.vue'
import IntentPanel from './intent-panels/IntentPanel.vue'
import ThinkingProcessPanel from './ThinkingProcessPanel.vue'
import MessageCard from './MessageCard.vue'
import InlineFormEditor from './InlineFormEditor.vue'
import BatchInlineCard from './BatchInlineCard.vue'
import QueryPlanCard from './QueryPlanCard.vue'
import EvidenceCard from './EvidenceCard.vue'

const props = defineProps({
  messages: { type: Array, required: true },
  showWelcome: { type: Boolean, default: false },
  mode: { type: String, default: 'rd' },
  /** 当前激活的内联表单（RD 页面注入，用于在本消息内展开编辑器） */
  activeFormCard: { type: Object, default: null },
})

const emit = defineEmits([
  'form-card-click',
  'form-submit',
  'form-field-change',
  'form-confirm-submit',
  'form-ai-validation',
  'form-close',
  'form-cancel',
  'batch-confirm',
  'batch-fix',
  'batch-delete',
  'file-ref-click',
  'intent-action',
  'undo-action',
  'regenerate',
  'suggest',
  'query-result-click',
  'clarify-submit',
])

const isActiveForm = (formCard) => {
  return !!props.activeFormCard && !!formCard && props.activeFormCard.formId === formCard.formId
}

/** 引用锚计数摘要（通过/待修/可入库） */
const fileRefCounts = (fileRef) => {
  if (!fileRef?.counts) return ''
  const c = fileRef.counts
  const parts = []
  if (c.passed != null) parts.push(`通过 ${c.passed}`)
  if (c.pending != null) parts.push(`待修 ${c.pending}`)
  if (c.confirmable != null) parts.push(`可入库 ${c.confirmable}`)
  return parts.length ? parts.join(' · ') : ''
}

/** CLARIFY 澄清补参：msg.id -> { [paramKey]: 用户输入 } */
const clarifyDrafts = reactive({})

/** 参数键 → 业务中文标签 */
function clarifyParamLabel(key) {
  const map = {
    offering: '商品/套餐',
    ruleId: '规则编号',
    concept: '本体概念',
    question: '查询内容',
  }
  return map[key] || key
}

/** 收集非空补参并结构化回传（复用 session history 让 LLM 理解原意图） */
function submitClarify(msg) {
  if (!msg?.clarify?.length) return
  const params = {}
  for (const key of msg.clarify) {
    const val = (clarifyDrafts[`${msg.id}:${key}`] || '').trim()
    if (val) params[key] = val
  }
  if (!Object.keys(params).length) {
    ElMessage.warning('请至少补充一项信息')
    return
  }
  emit('clarify-submit', { msg, params })
  for (const key of msg.clarify) {
    delete clarifyDrafts[`${msg.id}:${key}`]
  }
}

/** 跳过澄清：按缺省继续（复用追问建议通道） */
function dismissClarify(msg) {
  if (!msg?.clarify?.length) return
  for (const key of msg.clarify) {
    delete clarifyDrafts[`${msg.id}:${key}`]
  }
  emit('suggest', '忽略补充，按缺省继续')
}

const BOTTOM_THRESHOLD = 140

const messagesEl = ref(null)
const stickToBottom = ref(true)
let resizeObserver = null
let scheduleRaf = 0
let smoothTimer = 0
let prevMessageCount = 0
let programmaticScroll = false
/** 流式跟滚期间忽略 scroll 事件，避免未贴底时被误判为用户上翻 */
let followLockUntil = 0

/**
 * 有思考过程的消息：等思考播完再打出正文
 * id -> { unlocked, instant, shown, typing, token }
 */
const replyGate = reactive({})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const fullReply = (msg) => msg?.streamText || msg?.content || ''

const needsReplyGate = (msg) => !!(msg?.reasoning && msg.reasoning.length)

const ensureGate = (id) => {
  if (!replyGate[id]) {
    replyGate[id] = {
      unlocked: false,
      instant: false,
      shown: '',
      typing: false,
      token: 0,
    }
  }
  return replyGate[id]
}

const replyDisplayText = (msg) => {
  if (!msg) return ''
  if (!needsReplyGate(msg)) return fullReply(msg)
  const g = replyGate[msg.id]
  if (!g?.unlocked) return ''
  return g.shown || ''
}

const isReplyTyping = (msg) => {
  if (!needsReplyGate(msg)) return !!(msg.loading || !msg.done)
  return !!replyGate[msg.id]?.typing
}

/** 思考播完且正文打完（或无正文）后再展示附属结果 */
const isReplySettled = (msg) => {
  if (!needsReplyGate(msg)) return true
  const g = replyGate[msg.id]
  if (!g?.unlocked || g.typing) return false
  if (fullReply(msg) && !g.shown) return false
  return true
}

const shouldShowReplyPlaceholder = (msg) => {
  if (replyDisplayText(msg)) return false
  if (!needsReplyGate(msg)) {
    return !!(msg.loading && !fullReply(msg))
  }
  // 思考进行中，或思考已完但正文还在路上 / 正在准备打字
  const g = replyGate[msg.id]
  if (!g?.unlocked) return true
  return !!(msg.loading || !fullReply(msg) || g.typing)
}

const typeReply = async (msgId, text, instant) => {
  const g = ensureGate(msgId)
  const token = ++g.token
  if (instant || !text) {
    g.shown = text || ''
    g.typing = false
    return
  }
  // 已显示到同等长度则跳过
  if (g.shown === text) {
    g.typing = false
    return
  }
  g.typing = true
  // 思考播完后稍顿一下，再接正文，形成连续自上而下节奏
  if (!g.shown) {
    await sleep(220)
    if (replyGate[msgId]?.token !== token) return
  }
  // 若已有前缀，从现有长度续打；否则从头
  let start = 0
  if (g.shown && text.startsWith(g.shown)) {
    start = g.shown.length
  } else {
    g.shown = ''
  }
  // 动态节奏：块大跑得快、块小跑得慢，收尾平滑不突兀
  const remaining = text.length - start
  const chunk = remaining > 1200 ? 24 : remaining > 400 ? 12 : 6
  const delay = remaining > 1200 ? 8 : 16
  for (let i = start; i < text.length; i += chunk) {
    if (replyGate[msgId]?.token !== token) return
    g.shown = text.slice(0, Math.min(i + chunk, text.length))
    await sleep(delay)
  }
  if (replyGate[msgId]?.token !== token) return
  g.shown = text
  g.typing = false
}

const revealReplyFor = (msg, instant = false) => {
  if (!msg?.id || !needsReplyGate(msg)) return
  const g = ensureGate(msg.id)
  if (!g.unlocked) return
  const text = fullReply(msg)
  if (!text) return
  typeReply(msg.id, text, instant || g.instant)
}

const onThinkingComplete = (msg, payload = {}) => {
  if (!msg?.id) return
  const g = ensureGate(msg.id)
  g.unlocked = true
  g.instant = !!payload.instant
  revealReplyFor(msg, g.instant)
}

/** 正文在思考完成后才到达时，继续打出 */
watch(
  () => props.messages.map((m) => `${m.id}:${fullReply(m).length}:${m.done}:${m.loading}`).join('|'),
  () => {
    for (const msg of props.messages) {
      if (msg.role !== 'assistant') continue
      if (!needsReplyGate(msg)) continue
      const g = replyGate[msg.id]
      if (g?.unlocked) {
        revealReplyFor(msg, g.instant)
      }
    }
  },
)

const showWelcome = computed(() => props.showWelcome || props.messages.length === 0)

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

/** 粘底滚动：流式增高时瞬时贴底，保证研发助手思考面板/本体图不掉队 */
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
    // 布局/图谱异步撑高后再贴一次
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

const toggleReasoning = (idx) => {
  const msg = props.messages[idx]
  if (!msg) return
  msg.showReasoning = msg.showReasoning === false
}

/** 思考步骤文案转业务可读（兼容历史会话里的技术措辞） */
const localizeStepText = (text) => {
  if (!text) return ''
  return String(text)
    .replace(/已加载\s*\d+\s*个本体，构建意图识别\s*Prompt\.\.\./g, '正在匹配业务知识与规则库...')
    .replace(/正在分析用户输入\.\.\./g, '正在理解您的需求...')
    .replace(/正在调用大模型识别意图\.\.\./g, '正在识别业务意图...')
    .replace(/意图识别仍在进行/g, '业务意图识别进行中')
    .replace(/意图识别完成：([^(（]+)（来源\s*[^，,）)]+(?:[，,]\s*置信度\s*[\d.]+%)?）/g, '已确认业务意图：$1')
    .replace(/意图识别完成：/g, '已确认业务意图：')
    .replace(/正在分发到「([^」]+)」处理器执行\.\.\./g, '开始执行「$1」...')
    .replace(/正在基于图谱事实追溯异动根因\.\.\./g, '正在分析异动原因...')
    .replace(/根因分析完成，正在组织答复\.\.\./g, '异动原因分析完成，正在整理结论...')
    .replace(/产商品解析或事实检索未通过/g, '未能完成异动原因分析')
    .replace(/命中短指令白名单，跳过意图\s*LLM\.\.\./g, '已识别为常用业务指令，开始处理...')
    .replace(/意图\s*LLM\s*无有效结果，已关键词降级\.\.\./g, '已按关键词确认业务意图...')
    .replace(/正在调用规则引擎进行政策评估\.\.\./g, '正在评估业务规则与政策要求...')
    .replace(/正在构建方案对比快照并评估规则\.\.\./g, '正在对比方案并评估合规与收益...')
    .replace(/风险稽核完成，正在组织答复\.\.\./g, '风险排查完成，正在整理结论...')
    .replace(/（来源\s*llm[^）]*）/gi, '')
    .replace(/（来源\s*[^）]+）/g, '')
    .replace(/置信度/g, '把握度')
    .replace(/includeVoice/g, '语音')
    .replace(/includeData/g, '流量')
    .replace(/includeBroadband/g, '宽带')
    .replace(/offeringName/g, '商品名称')
    .replace(/monthlyFee/g, '月费')
    .replace(/bizScenario/g, '业务场景')
    .replace(/targetUser/g, '目标用户')
    .replace(/channelScope/g, '销售渠道')
    .replace(/mutexGroup/g, '互斥组')
    .replace(/bindExistingMainPkg/g, '绑定在架主套餐')
    .replace(/basedOnTemplate/g, '配置模板')
    .replace(/scenario_default/g, '场景缺省')
    .replace(/user_said/g, '用户表述')
    .replace(/create_offering_config/g, '创建商品配置')
    .replace(/OfferingConfig/g, '商品配置草稿')
    .replace(/BizScenario/g, '业务场景')
    .replace(/MAIN_PKG/g, '主套餐互斥组')
    .replace(/compliancePass/g, '合规通过')
    .replace(/OF-HF-128/g, '家庭融合畅享128')
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

/** 执行态中文标签（v3.2） */
function execStateLabel(state) {
  const map = {
    suggested: '建议',
    executing: '执行中',
    executed: '已执行',
    failed: '失败',
    reverted: '已撤销',
  }
  return map[state] || '已执行'
}

/** 统一的动作执行态来源：优先消息级 undoable.state，否则 actionState（v3.2） */
function execState(msg) {
  return msg?.undoable?.state || msg?.actionState || 'executed'
}

// 消息数量 / 流式内容 / 思考步骤变化时粘底滚动
watch(
  () => {
    const last = props.messages[props.messages.length - 1]
    const reasoning = last?.reasoning || []
    // 研发助手会原地刷新等待文案 / 本体链揭示，需纳入依赖
    const reasoningSig = reasoning
      .map((s) => `${s?.content?.length || 0}:${s?.chainRevealCount || 0}`)
      .join('|')
    return [
      props.messages.length,
      last?.id,
      last?.streamText?.length ?? 0,
      last?.content?.length ?? 0,
      reasoning.length,
      reasoningSig,
      last?.loading,
      last?.done,
      last?.showReasoning,
      !!last?.formCard,
      last?.queryResults?.length ?? 0,
      replyGate[last?.id]?.shown?.length ?? 0,
      replyGate[last?.id]?.unlocked ? 1 : 0,
    ]
  },
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

/* 消息头部 */
.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
}

.ai-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.message-time {
  font-size: 12px;
  color: var(--text-tertiary);
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

/* 推理面板 */
.reasoning-panel {
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 12px;
  overflow: hidden;
}

.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  background: none;
  border: none;
  cursor: pointer;
  color: #3b82f6;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  transition: background 0.2s;
}

.reasoning-toggle:hover {
  background: rgba(59, 130, 246, 0.05);
}

.toggle-icon {
  transition: transform 0.2s;
}

.toggle-icon.expanded {
  transform: rotate(90deg);
}

.reasoning-title {
  flex: 1;
}

.reasoning-count {
  color: #93c5fd;
  font-size: 12px;
}

.reasoning-live {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.12);
  padding: 1px 8px;
  border-radius: 999px;
  animation: pulse-live 1.2s ease-in-out infinite;
}

@keyframes pulse-live {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

.reasoning-panel.streaming {
  border-color: rgba(59, 130, 246, 0.35);
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.08);
}

.reasoning-body {
  padding: 0 14px 12px;
}

.reasoning-onto-tag {
  font-size: 11px;
  color: #1d4ed8;
  background: #dbeafe;
  padding: 1px 8px;
  border-radius: 999px;
}

.reasoning-step.is-ontology {
  background: rgba(37, 99, 235, 0.04);
  border-radius: 10px;
  padding: 8px 8px 10px;
  margin: 4px 0;
}

.step-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 6px 8px;
}

.step-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 999px;
  line-height: 1.6;
}

.step-badge.llm {
  background: #f3e8ff;
  color: #7c3aed;
}

.step-badge.ontology {
  background: #dbeafe;
  color: #1d4ed8;
}

.step-body .onto-chain {
  margin-top: 8px;
}

.reasoning-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.reasoning-step.latest .step-text {
  color: var(--text-primary);
  font-weight: 500;
}

.reasoning-step:not(:last-child) {
  border-bottom: 1px dashed rgba(59, 130, 246, 0.1);
}

.step-number {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #3b82f6;
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-text {
  flex: 1;
  line-height: 1.5;
}

.step-detail {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.45;
  white-space: pre-wrap;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 6px 0;
  align-items: center;
}

.typing-indicator.after-think {
  margin-top: 2px;
  opacity: 0.85;
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

/* 执行态徽标 + 撤销（可逆操作 v3.2） */
.exec-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.exec-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
}
.exec-badge.exec-suggested { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }
.exec-badge.exec-executing { background: #fefce8; color: #a16207; border-color: #fde68a; }
.exec-badge.exec-executed { background: #f0fdf4; color: #15803d; border-color: #bbf7d0; }
.exec-badge.exec-failed { background: #fef2f2; color: #b91c1c; border-color: #fecaca; }
.exec-badge.exec-reverted { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
.undo-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.undo-btn:hover { background: #f8fafc; border-color: #94a3b8; color: #0f172a; }

/* 表单卡片 */
.form-card {
  margin-top: 12px;
  padding: 16px;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.form-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.1);
}

.form-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
}

.form-card-info {
  flex: 1;
}

.form-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.form-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.form-card-meta .dot {
  color: var(--border-default);
}

/* 查询结果卡片列表 */
.query-results {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.query-result-item {
  padding: 12px 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.query-result-item:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
  background: rgba(59, 130, 246, 0.03);
}

.qr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.qr-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.qr-code {
  font-size: 11px;
  padding: 2px 8px;
  background: #f1f5f9;
  color: #64748b;
  border-radius: 4px;
  font-family: ui-monospace, monospace;
}

.qr-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0 0 8px;
  line-height: 1.45;
}

.qr-copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #dbeafe;
  color: #3b82f6;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.qr-copy-btn:hover {
  background: #3b82f6;
  color: white;
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

/* 澄清追问提示（CLARIFY 分支） */
.clarify-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 12px;
  border: 1px dashed #fcd34d;
  background: #fffbeb;
  border-radius: 10px;
  color: #b45309;
  font-size: 12px;
}

/* 澄清补参内联表单 */
.clarify-area {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.clarify-title {
  font-size: 13px;
  font-weight: 600;
  color: #b45309;
}

.clarify-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.clarify-label {
  flex-shrink: 0;
  width: 84px;
  font-size: 12px;
  color: #92400e;
  text-align: right;
}

.clarify-input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.clarify-input:focus {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15);
}

.clarify-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.clarify-submit {
  padding: 7px 18px;
  border: none;
  border-radius: 8px;
  background: #f59e0b;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.clarify-submit:hover {
  background: #d97706;
}

.clarify-skip {
  padding: 7px 14px;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  background: transparent;
  color: #b45309;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.clarify-skip:hover {
  background: #fef3c7;
}

/* 长对话文件引用锚 */
.file-ref-anchor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 6px 12px;
  border: 1px dashed #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  width: fit-content;
  transition: all 0.15s;
}
.file-ref-anchor:hover {
  border-color: #6366f1;
  background: #e0e7ff;
}
.fra-name { font-weight: 600; }
.fra-counts { color: #818cf8; }

/* 翻译层执行异常提示 */
.agent-error-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: 10px;
  color: #dc2626;
  font-size: 12px;
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

  .reasoning-panel {
    border-radius: 10px;
  }

  .reasoning-toggle {
    padding: 8px 12px;
    font-size: 12px;
  }

  .reasoning-body {
    padding: 0 12px 10px;
  }

  .reasoning-step {
    padding: 6px 0;
    font-size: 12px;
    gap: 8px;
  }

  .step-number {
    width: 16px;
    height: 16px;
    font-size: 10px;
  }

  .message-actions {
    opacity: 1;
    padding: 2px;
  }

  .action-btn {
    width: 26px;
    height: 26px;
  }

  .form-card {
    padding: 12px;
    border-radius: 10px;
  }

  .form-card-icon {
    width: 36px;
    height: 36px;
  }

  .form-card-name {
    font-size: 14px;
  }

  .form-card-meta {
    font-size: 12px;
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

  .reasoning-toggle {
    padding: 6px 10px;
  }

  .reasoning-body {
    padding: 0 10px 8px;
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

  .form-card {
    -webkit-tap-highlight-color: transparent;
  }

  .reasoning-toggle {
    min-height: 40px;
  }
}

/* 平板横屏优化 */
@media (min-width: 769px) and (max-width: 1024px) and (orientation: landscape) {
  .message-content {
    max-width: 75%;
  }
}

/* 大屏幕优化 - 限制最大宽度保证阅读体验 */
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

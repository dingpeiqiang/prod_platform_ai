<template>
  <div class="messages-container" ref="messagesEl">
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
            />

            <!-- 正文内容 -->
            <div class="message-bubble ai-bubble">
              <MessageCard 
                v-if="msg.streamText || msg.content"
                :content="msg.streamText || msg.content"
                :streaming="msg.loading || !msg.done"
              />
              <!-- 加载状态 -->
              <div v-if="msg.loading && !msg.streamText && !msg.content" class="typing-indicator">
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
            <div v-if="msg.done && (msg.streamText || msg.content)" class="message-actions">
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

            <!-- Intent Panel -->
            <IntentPanel
              v-if="msg.intentType && msg.intentData"
              :intentType="msg.intentType"
              :msg="msg"
              @intent-action="$emit('intent-action', $event)"
            />

            <!-- 表单卡片 -->
            <div v-if="msg.formCard" class="form-card" @click="$emit('form-card-click', msg)">
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

            <!-- 查询结果卡片列表 -->
            <div v-if="msg.queryResults?.length" class="query-results">
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
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import WelcomeCards from './WelcomeCards.vue'
import IntentPanel from './intent-panels/IntentPanel.vue'
import ThinkingProcessPanel from './ThinkingProcessPanel.vue'
import MessageCard from './MessageCard.vue'
import { renderMarkdown } from '../utils/chatUtils.js'

const props = defineProps({
  messages: { type: Array, required: true },
  showWelcome: { type: Boolean, default: false },
  mode: { type: String, default: 'rd' }
})

const emit = defineEmits(['form-card-click', 'intent-action', 'regenerate', 'suggest', 'query-result-click'])

const messagesEl = ref(null)

const showWelcome = computed(() => props.showWelcome || props.messages.length === 0)

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const handleSuggest = (content) => {
  emit('suggest', content)
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

const toggleReasoning = (idx) => {
  const msg = props.messages[idx]
  if (!msg) return
  msg.showReasoning = msg.showReasoning === false
}

/** 思考步骤文案里的英文字段转中文，方便客户阅读 */
const localizeStepText = (text) => {
  if (!text) return ''
  return String(text)
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

onMounted(() => {
  scrollToBottom()
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
  padding: 24px 0;
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

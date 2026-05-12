<template>
  <div class="messages-area" ref="messagesEl">
    <WelcomeScreen
      v-if="!messages.length"
      :suggestions="suggestions"
      @suggestion-click="handleSuggestionClick"
    />

    <div v-else class="messages-list">
      <div
        v-for="(msg, idx) in messages"
        :key="msg.id"
        :class="['msg-row', msg.role]"
      >
        <div v-if="msg.role === 'assistant'" class="avatar ai-avatar">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <circle cx="12" cy="12" r="6"/>
            <circle cx="12" cy="12" r="2"/>
            <path d="M12 6a6 6 0 0 1 4 1.5"/>
            <path d="M12 6a6 6 0 0 0-4 1.5"/>
            <path d="M12 18a6 6 0 0 1 4-1.5"/>
            <path d="M12 18a6 6 0 0 0-4-1.5"/>
            <path d="M6 12a6 6 0 0 1 1.5 4"/>
            <path d="M6 12a6 6 0 0 0 1.5-4"/>
            <path d="M18 12a6 6 0 0 1-1.5 4"/>
            <path d="M18 12a6 6 0 0 0-1.5-4"/>
            <circle cx="7.5" cy="8.5" r="1"/>
            <circle cx="16.5" cy="8.5" r="1"/>
            <circle cx="7.5" cy="15.5" r="1"/>
            <circle cx="16.5" cy="15.5" r="1"/>
          </svg>
        </div>

        <div class="msg-body">
          <div v-if="msg.role === 'user'" class="bubble user-bubble">
            {{ msg.content }}
            <button class="copy-btn" @click="copyText(msg.content)" title="复制">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
            </button>
          </div>

          <div v-else class="ai-message">
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
                    <div class="step-content">
                      <span class="step-icon">{{ stepIcon(step.type) }}</span>
                      <div class="step-main">
                        <span class="step-text">{{ step.content }}</span>
                      </div>
                      <span v-if="si === msg.latestStepIndex && !msg.done" class="step-loading">
                        <span/><span/><span/>
                      </span>
                    </div>
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

            <div v-if="msg.streamText || msg.content || msg.loading" class="bubble ai-bubble">
              <div
                v-if="msg.streamText || msg.content"
                class="ai-text"
                :class="{ 'loading-text': msg.loading }"
                v-html="renderMarkdown(msg.streamText || msg.content)"
              />
            </div>
            <div v-if="msg.loading" class="loading-indicator">
              <span class="loading-dot"/><span class="loading-dot"/><span class="loading-dot"/>
            </div>
            <span v-if="!msg.done && (msg.streamText || !msg.reasoning?.length)" class="cursor-blink">▌</span>

            <div v-if="!msg.done && !msg.streamText && !msg.reasoning?.length" class="dots-loading">
              <span/><span/><span/>
            </div>

            <IntentPanel
              v-for="intentType in intentPanelTypes"
              :key="intentType"
              :intentType="intentType"
              :msg="msg"
              @intent-action="$emit('intent-action', $event)"
            />

            <div v-if="msg.formCard" class="form-card" @click="$emit('form-card-click', msg)">
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
            </div>

            <div v-if="msg.done && (msg.streamText || msg.content)" class="msg-actions">
              <button class="action-btn" @click="copyText(msg.streamText || msg.content)" title="复制">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-if="msg.role === 'user'" class="avatar user-avatar">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/>
          </svg>
        </div>
      </div>
      <div style="height: 24px"/>
    </div>
  </div>
</template>

<script setup>import { ref, nextTick, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import WelcomeScreen from './WelcomeScreen.vue';
import IntentPanel from './intent-panels/IntentPanel.vue';
import { stepIcon, renderMarkdown, formatTime, getFormStatusText } from '../utils/chatUtils.js';
import { listIntentPanels } from '../composables/useIntentRegistry.js';
const props = defineProps({
 messages: { type: Array, required: true },
 suggestions: { type: Array, default: () => [] }
});
const emit = defineEmits(['suggestion-click', 'form-card-click', 'intent-action']);
const messagesEl = ref(null);
const intentPanelTypes = listIntentPanels();
const scrollToBottom = (smooth = false) => {
 nextTick(() => {
 if (messagesEl.value) {
 messagesEl.value.scrollTo({
 top: messagesEl.value.scrollHeight,
 behavior: smooth ? 'smooth' : 'auto'
 });
 }
 });
};
const toggleReasoning = (idx) => {
 props.messages[idx].showReasoning = !props.messages[idx].showReasoning;
};
const copyText = async (text) => {
 try {
 await navigator.clipboard.writeText(text);
 ElMessage({ message: '已复制', type: 'success', duration: 1500, plain: true });
 }
 catch {
 ElMessage.error('复制失败');
 }
};
const handleSuggestionClick = (text) => {
 emit('suggestion-click', text);
};
onMounted(() => {
 scrollToBottom();
});
defineExpose({ scrollToBottom });
</script>

<style scoped>
.messages-area {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-track { background: transparent; }
.messages-area::-webkit-scrollbar-thumb { background: var(--border-default); border-radius: 3px; }
.messages-area::-webkit-scrollbar-thumb:hover { background: var(--border-strong); }

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

.msg-body { flex: 1; min-width: 0; }

.bubble.user-bubble {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 100%;
  background: var(--bg-user-bubble);
  color: var(--text-primary);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl) 4px var(--radius-xl) var(--radius-xl);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: var(--shadow-sm);

  .copy-btn {
    flex-shrink: 0;
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.2s, visibility 0.2s;
    background: rgba(0,0,0,0.08);
    border: none;
    color: var(--text-tertiary);
    cursor: pointer;
    padding: 4px;
    border-radius: var(--radius-sm);

    &:hover {
      color: var(--text-primary);
      background: rgba(0,0,0,0.12);
    }
  }

  &:hover .copy-btn {
    opacity: 1;
    visibility: visible;
  }
}

.ai-message {
  max-width: 90%;
}

.reasoning-wrap {
  margin-bottom: var(--space-2-5);
  background: var(--bg-reasoning);
  border: 1px solid rgba(99, 102, 241, 0.15);
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

.reasoning-body {
  padding: var(--space-1) var(--space-3-5) var(--space-3-5);
  border-top: 1px solid var(--color-primary-100);
}

.reasoning-step {
  padding: var(--space-2) 0;
  font-size: var(--font-size-xs);
  color: var(--text-secondary);

  &:not(:last-child) { border-bottom: 1px dashed var(--color-primary-100); }
}

.step-content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.step-icon {
  width: 20px; height: 20px;
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  font-size: 10px;
}

.step-text { flex: 1; line-height: 1.5; }
.step-main { flex: 1; }

.step-result {
  margin-top: var(--space-1);
  padding: var(--space-2);
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-md);
  font-size: 11px;
}

.step-result-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--color-primary-700);
  line-height: 1.4;
}

.step-loading span {
  display: inline-block;
  width: 4px; height: 4px;
  border-radius: 50%;
  background: var(--color-primary-400);
  animation: dotPulse 1.2s infinite;
  margin-right: 2px;
}

.step-loading span:nth-child(2) { animation-delay: .2s; }
.step-loading span:nth-child(3) { animation-delay: .4s; }

.step-reasoning-inline {
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-top: 1px dashed var(--border-light);
  background: rgba(99, 102, 241, 0.03);
  border-radius: var(--radius-sm);
}

.step-reasoning-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  cursor: pointer;
  white-space: nowrap;
}

.step-reasoning-body {
  margin-top: var(--space-2);
  padding: var(--space-2);
  background: var(--bg-primary);
  border-radius: var(--radius-md);
}

.step-reasoning-text {
  margin: 0;
  padding: 0;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.ai-text {
  font-size: var(--font-size-sm);
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
  white-space: pre-wrap;
}

.ai-text.loading-text {
  min-height: 0;
}

.bubble.ai-bubble {
  background: var(--bg-ai-bubble);
  color: var(--text-primary);
  padding: var(--space-2) var(--space-3);
  border-radius: 4px var(--radius-xl) var(--radius-xl) var(--radius-xl);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--border-default);
}

.loading-indicator {
  display: flex;
  gap: 4px;
  padding: var(--space-2) 0;
}

.loading-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--color-primary-400);
  animation: dotPulse 1.2s infinite;
}

.loading-dot:nth-child(2) { animation-delay: .2s; }
.loading-dot:nth-child(3) { animation-delay: .4s; }

.cursor-blink {
  display: inline-block;
  animation: blink 1s infinite;
  color: var(--color-primary-400);
}

.dots-loading {
  display: flex;
  gap: 6px;
  padding: var(--space-3) 0;
}

.dots-loading span {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--color-primary-300);
  animation: dotPulse 1.2s infinite;
}

.dots-loading span:nth-child(2) { animation-delay: .2s; }
.dots-loading span:nth-child(3) { animation-delay: .4s; }

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

@keyframes dotPulse {
  0%, 60%, 100% { opacity: 0.4; transform: scale(1); }
  30% { opacity: 1; transform: scale(1.1); }
}

.form-card {
  margin-top: var(--space-3);
  padding: var(--space-4);
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.form-card:hover {
  border-color: var(--color-primary-300);
  box-shadow: 0 2px 12px rgba(99,102,241,.08);
}

.form-card-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.form-card-icon {
  width: 44px; height: 44px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-50), var(--color-primary-100));
  display: flex; align-items: center; justify-content: center;
  color: var(--color-primary-600);
  flex-shrink: 0;
}

.form-card-info { flex: 1; min-width: 0; }
.form-card-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin-bottom: 2px;
}

.form-card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.form-card-code {
  padding: 2px 8px;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}

.form-card-sep { color: var(--border-default); }

.form-card-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.form-card-status.status-filling {
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.form-card-status.status-submitted {
  background: var(--color-success-50);
  color: var(--color-success-700);
}

.form-card-status.status-cancelled {
  background: var(--color-gray-50);
  color: var(--text-tertiary);
}

.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
}

.status-filling .status-dot { background: var(--color-primary-500); }
.status-submitted .status-dot { background: var(--color-success-500); }
.status-cancelled .status-dot { background: var(--border-default); }

.msg-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-1);
  margin-top: var(--space-1);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-quaternary);
  cursor: pointer;
  transition: all var(--transition-fast);
  opacity: 0;
}

.bubble:hover + .msg-actions .action-btn,
.msg-actions:hover .action-btn {
  opacity: 1;
}

.action-btn:hover {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}
</style>
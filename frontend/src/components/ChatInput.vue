<template>
  <div class="input-area">
    <div class="quick-bar">
      <button
        v-for="a in quickActions"
        :key="a.key"
        class="quick-chip"
        @click="$emit('quick-action', a.content)"
        :disabled="disabled"
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
          :placeholder="placeholder"
          rows="1"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
          @keydown="handleKeydown"
          @input="autoResize"
          :disabled="disabled"
        />
        <button v-if="disabled" class="send-btn stop-btn" @click="$emit('stop')" title="停止生成">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
        <button
          v-else
          class="send-btn"
          :class="{ active: inputText.trim() }"
          :disabled="!inputText.trim()"
          @click="handleSend"
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
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你想做的事，例如「帮我填一个销售订单」' }
})

const emit = defineEmits(['update:modelValue', 'send', 'stop', 'quick-action'])

const inputEl = ref(null)
const inputFocused = ref(false)
const inputText = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  inputText.value = val
})

const quickActions = [
  { key: 'sales', label: '销售订单', content: '帮我填一个销售订单', color: '#818cf8' },
  { key: 'leave', label: '请假申请', content: '帮我填一个请假申请', color: '#34d399' },
  { key: 'expense', label: '费用报销', content: '帮我填一个费用报销', color: '#fbbf24' },
  { key: 'config', label: '+ 新表单', content: '我想添加一种新的业务表单', color: '#f472b6' },
]

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

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const handleSend = () => {
  const text = inputText.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  resetInput()
}

const focus = () => {
  nextTick(() => inputEl.value?.focus())
}

defineExpose({ focus, resetInput })
</script>

<style scoped>
.input-area {
  padding: var(--space-4);
  background: var(--bg-primary);
  border-top: 1px solid var(--border-light);
}

.quick-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.quick-chip:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

.quick-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chip-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
}

.input-box {
  position: relative;
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  transition: all var(--transition-fast);
}

.input-box.focused {
  border-color: var(--color-primary-400);
  box-shadow: 0 0 0 3px rgba(99,102,241,.1);
}

.textarea-wrap {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  padding: var(--space-3);
}

.textarea-wrap textarea {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: var(--text-primary);
}

.textarea-wrap textarea::placeholder {
  color: var(--text-tertiary);
}

.textarea-wrap textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  flex-shrink: 0;
  width: 32px; height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-500);
  border: none;
  border-radius: var(--radius-lg);
  color: var(--text-inverse);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-600);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn.active:not(:disabled) {
  background: var(--color-primary-600);
}

.send-btn.stop-btn {
  background: var(--color-error-500);
}

.send-btn.stop-btn:hover {
  background: var(--color-error-600);
}

.input-hint {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: var(--space-2);
}
</style>
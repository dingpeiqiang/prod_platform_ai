<template>
  <div class="chat-input-container">
    <div class="quick-actions-bar">
      <button
        v-for="(action, idx) in quickActions"
        :key="action.key + '-' + idx"
        class="quick-action-chip"
        @click="handleQuickAction(action)"
        :disabled="disabled"
      >
        <span class="chip-icon" :style="{ color: action.color }">+</span>
        <span class="chip-text">{{ action.label }}</span>
      </button>
    </div>

    <div class="input-box" :class="{ focused: isFocused, disabled }">
      <div class="composer-row">
        <textarea
          ref="inputEl"
          v-model="inputText"
          class="message-input"
          :placeholder="placeholder"
          rows="1"
          @focus="isFocused = true"
          @blur="isFocused = false"
          @keydown="handleKeydown"
          @input="handleInput"
          :disabled="disabled || isRecording"
        />

        <div class="composer-actions">
          <button
            class="tool-btn"
            @click="triggerFileUpload"
            title="上传文件"
            :disabled="disabled"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
          </button>
          <button
            class="tool-btn"
            @click="triggerImageUpload"
            title="上传图片"
            :disabled="disabled"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <polyline points="21 15 16 10 5 21" />
            </svg>
          </button>
          <button
            class="tool-btn"
            :class="{ recording: isRecording }"
            @mousedown="startRecording"
            @mouseup="stopRecording"
            @mouseleave="stopRecording"
            @touchstart.prevent="startRecording"
            @touchend="stopRecording"
            :disabled="disabled"
            :title="isRecording ? '停止录制' : '语音输入'"
          >
            <svg v-if="!isRecording" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
        </div>

        <button
          v-if="disabled"
          class="send-btn stop-btn"
          @click="$emit('stop')"
          title="停止生成"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2" />
          </svg>
        </button>
        <button
          v-else
          class="send-btn"
          :class="{ active: hasContent }"
          :disabled="!canSend"
          @click="handleSend"
          title="发送 (Enter)"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </div>

      <div v-if="skillTag" class="skill-strip">
        <div class="skill-chip" role="button" tabindex="0" @click="handleSkillSelect(currentSkill)" @keydown.enter.prevent="handleSkillSelect(currentSkill)" @keydown.space.prevent="handleSkillSelect(currentSkill)">
          <span class="skill-chip-icon" aria-hidden="true">
            <svg v-if="skillIconPaths[currentSkill]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path v-for="(d, idx) in skillIconPaths[currentSkill]" :key="idx" :d="d" />
            </svg>
          </span>
          <span class="skill-chip-text">{{ skillTag.label }}</span>
          <span class="skill-chip-arrow" aria-hidden="true">
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </span>
          <button class="skill-chip-close" type="button" @click.stop="$emit('remove-skill')" :disabled="disabled" title="关闭技能">
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      <div v-if="isRecording" class="recording-status">
        <span class="recording-dot"></span>
        <span class="recording-text">正在录音 {{ recordingTime }}</span>
      </div>
    </div>

    <div class="input-footer">
      <span class="footer-hint">按 Enter 发送，Shift + Enter 换行</span>
    </div>

    <input ref="fileInput" type="file" class="hidden-input" accept="*" @change="handleFileSelect" multiple />
    <input ref="imageInput" type="file" class="hidden-input" accept="image/*" @change="handleImageSelect" multiple />
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你想做的事...' },
  currentSkill: { type: String, default: '' },
  assistantMode: { type: String, default: '' }
})

const emit = defineEmits([
  'update:modelValue',
  'send',
  'stop',
  'quick-action',
  'file-upload',
  'image-upload',
  'voice-record',
  'remove-skill',
  'skill-select'
])

const skillConfig = {
  query: { icon: 'fa-magnifying-glass', label: 'AI智查' },
  file: { icon: 'fa-file-import', label: '智读·批量生成' },
  chat: { icon: 'fa-comments', label: '智聊·对话配置' },
  ops: { icon: 'fa-chart-line', label: '运营助手' }
}

const skillTag = computed(() =>
  props.currentSkill && skillConfig[props.currentSkill]
    ? skillConfig[props.currentSkill]
    : null
)

const handleSkillSelect = (skill) => emit('skill-select', skill)

const skillIconPaths = {
  query: ['M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14Z', 'm20 20-3.5-3.5'],
  file: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z', 'M14 2v6h6', 'M12 11v6', 'M9 14h6'],
  chat: ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'],
  ops: ['M3 3v18h18', 'M18 17V9', 'M13 17V5', 'M8 17v-3']
}

const inputEl = ref(null)
const fileInput = ref(null)
const imageInput = ref(null)
const isFocused = ref(false)
const inputText = ref(props.modelValue)
const isRecording = ref(false)
const recordingTime = ref('00:00')
const attachments = ref([])

let recordingTimer = null
let mediaRecorder = null
let audioChunks = []

const allQuickActions = [
  { key: 'chat', label: '智聊配置', content: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售', color: '#8b5cf6', modes: ['rd'] },
  { key: 'file', label: '智读批量', content: '帮我导入校园迎新方案', color: '#10b981', modes: ['rd'] },
  { key: 'query', label: 'AI智查', content: '帮我查询近30天大学生套餐配置', color: '#f59e0b', modes: ['rd'] },
  { key: 'ops', label: '指标异动根因', content: '分析家庭融合畅享128本月收入下滑原因', color: '#0ea5e9', modes: ['ops'] },
  { key: 'ops', label: '高风险商品稽核', content: '筛查所有在架的0元资费风险商品', color: '#ef4444', modes: ['ops'] }
]

const quickActions = computed(() => {
  const mode = props.assistantMode
  if (!mode) return allQuickActions
  return allQuickActions.filter(a => a.modes.includes(mode))
})

const hasContent = computed(() => inputText.value.trim().length > 0 || attachments.value.length > 0)
const canSend = computed(() => hasContent.value && !props.disabled)

watch(() => props.modelValue, (val) => {
  if (val !== inputText.value) {
    inputText.value = val
    nextTick(autoResize)
  }
})

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

const handleInput = () => {
  autoResize()
  emit('update:modelValue', inputText.value)
}

const resetInput = () => {
  inputText.value = ''
  attachments.value = []
  emit('update:modelValue', '')
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
  if (!text && attachments.value.length === 0) return
  if (props.disabled) return
  emit('send', { text, attachments: [...attachments.value] })
  resetInput()
}

const handleQuickAction = (action) => emit('quick-action', action)
const focus = () => nextTick(() => inputEl.value?.focus())
const triggerFileUpload = () => fileInput.value?.click()
const triggerImageUpload = () => imageInput.value?.click()

const handleFileSelect = (e) => {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    attachments.value.push({ type: 'file', name: file.name, size: file.size, file, preview: null })
  })
  e.target.value = ''
  emit('file-upload', files)
}

const handleImageSelect = (e) => {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    const reader = new FileReader()
    reader.onload = (event) => {
      attachments.value.push({ type: 'image', name: file.name, size: file.size, file, preview: event.target?.result })
    }
    reader.readAsDataURL(file)
  })
  e.target.value = ''
  emit('image-upload', files)
}

const startRecording = async () => {
  if (isRecording.value || props.disabled) return
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(stream)
    audioChunks = []
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) audioChunks.push(event.data)
    }
    mediaRecorder.start(100)
    isRecording.value = true
    recordingTime.value = '00:00'
    let seconds = 0
    recordingTimer = setInterval(() => {
      seconds++
      const mins = Math.floor(seconds / 60).toString().padStart(2, '0')
      const secs = (seconds % 60).toString().padStart(2, '0')
      recordingTime.value = `${mins}:${secs}`
    }, 1000)
  } catch (error) {
    console.error('录音失败:', error)
    alert('无法访问麦克风，请检查权限设置')
  }
}

const stopRecording = () => {
  if (!isRecording.value) return
  isRecording.value = false
  if (recordingTimer) {
    clearInterval(recordingTimer)
    recordingTimer = null
  }
  if (mediaRecorder) {
    mediaRecorder.stop()
    mediaRecorder.onstop = () => {
      const blob = new Blob(audioChunks, { type: 'audio/webm' })
      const url = URL.createObjectURL(blob)
      const attachment = {
        type: 'voice',
        name: `录音_${new Date().toLocaleString()}.webm`,
        size: blob.size,
        blob,
        url,
        duration: recordingTime.value
      }
      attachments.value.push(attachment)
      emit('voice-record', attachment)
      mediaRecorder = null
      audioChunks = []
    }
    mediaRecorder.stream.getTracks().forEach(track => track.stop())
  }
}

defineExpose({ focus, resetInput })
</script>

<style scoped>
.chat-input-container {
  padding: 16px 24px 24px;
  background: var(--bg-primary);
  border-top: 1px solid var(--border-light);
}

.quick-actions-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.quick-action-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-action-chip:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.quick-action-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chip-icon {
  font-weight: 600;
  font-size: 14px;
}

.input-box {
  background: linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98));
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
  padding: 12px 14px 10px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.06);
  transition: all 0.2s ease;
}

.input-box.focused {
  border-color: rgba(99, 102, 241, 0.45);
  box-shadow: 0 10px 36px rgba(99, 102, 241, 0.12);
}

.composer-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.message-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.6;
  min-height: 56px;
  max-height: 160px;
  padding: 10px 0;
}

.message-input::placeholder {
  color: var(--text-tertiary);
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 6px;
}

.tool-btn,
.send-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.tool-btn {
  background: transparent;
  color: var(--text-tertiary);
}

.tool-btn:hover:not(:disabled) {
  background: rgba(15, 23, 42, 0.04);
  color: var(--text-primary);
}

.tool-btn.recording {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.send-btn {
  background: #eef2ff;
  color: #94a3b8;
}

.send-btn.active {
  background: #4f46e5;
  color: #fff;
}

.send-btn.stop-btn {
  background: #ef4444;
  color: #fff;
}

.send-btn:disabled,
.tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.skill-strip {
  display: flex;
  align-items: center;
  margin-top: 10px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px 0 6px;
  background: #eef2ff;
  border: 1px solid rgba(99, 102, 241, 0.16);
  border-radius: 999px;
  font-size: 13px;
  color: #4f46e5;
  cursor: pointer;
}

.skill-chip-icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.12);
  flex-shrink: 0;
}

.skill-chip-icon svg {
  display: block;
}

.skill-chip-text {
  font-weight: 500;
}

.skill-chip-arrow {
  width: 12px;
  height: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #818cf8;
}

.skill-chip-close {
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  border-radius: 999px;
  color: #818cf8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.skill-chip-close:hover:not(:disabled) {
  background: rgba(99, 102, 241, 0.1);
}

.recording-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: #ef4444;
  font-size: 13px;
}

.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0.3; }
}

.input-footer {
  margin-top: 8px;
  text-align: center;
}

.footer-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.hidden-input {
  display: none;
}

@media (max-width: 768px) {
  .chat-input-container {
    padding: 12px 16px 20px;
  }

  .quick-actions-bar {
    gap: 6px;
    margin-bottom: 10px;
  }

  .quick-action-chip {
    padding: 5px 10px;
    font-size: 12px;
  }

  .input-box {
    padding: 10px 12px 8px;
    border-radius: 20px;
  }

  .message-input {
    min-height: 52px;
    font-size: 16px;
  }

  .tool-btn,
  .send-btn {
    width: 32px;
    height: 32px;
  }
}

@media (max-width: 480px) {
  .chat-input-container {
    padding: 10px 12px 16px;
  }

  .quick-actions-bar {
    overflow-x: auto;
    flex-wrap: nowrap;
    scrollbar-width: none;
  }

  .quick-actions-bar::-webkit-scrollbar {
    display: none;
  }

  .composer-row {
    gap: 8px;
  }

  .input-box {
    padding: 10px 10px 8px;
  }

  .message-input {
    min-height: 48px;
    padding: 8px 0;
  }
}
</style>

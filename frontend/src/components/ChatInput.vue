<template>
  <div class="chat-input-container">
    <!-- 快捷操作栏 -->
    <div class="quick-actions-bar">
      <button
        v-for="action in quickActions"
        :key="action.key"
        class="quick-action-chip"
        @click="handleQuickAction(action.content)"
        :disabled="disabled"
      >
        <span class="chip-icon" :style="{ color: action.color }">+</span>
        <span class="chip-text">{{ action.label }}</span>
      </button>
    </div>

    <!-- 技能标签 -->
    <div v-if="skillTag" class="skill-tag-container">
      <span class="skill-tag">
        <i :class="`fa-solid ${skillTag.icon}`" />
        <span>{{ skillTag.label }}</span>
        <button 
          class="skill-close" 
          @click="$emit('remove-skill')" 
          :disabled="disabled"
          title="关闭技能"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </span>
    </div>

    <!-- 输入框主体 -->
    <div class="input-box" :class="{ focused: isFocused, 'has-content': hasContent }">
      <div class="input-wrapper">
        <!-- 左侧功能按钮 -->
        <div class="input-tools left">
          <button
            class="tool-btn"
            @click="triggerFileUpload"
            title="上传文件"
            :disabled="disabled"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
            <svg v-if="!isRecording" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
        </div>

        <!-- 文本输入区 -->
        <div class="textarea-container">
          <textarea
            ref="inputEl"
            v-model="inputText"
            :placeholder="placeholder"
            rows="1"
            @focus="isFocused = true"
            @blur="isFocused = false"
            @keydown="handleKeydown"
            @input="handleInput"
            :disabled="disabled || isRecording"
          />
          
          <!-- 录音状态显示 -->
          <div v-if="isRecording" class="recording-status">
            <span class="recording-dot"></span>
            <span class="recording-text">正在录音 {{ recordingTime }}</span>
          </div>
        </div>

        <!-- 右侧发送按钮 -->
        <div class="input-tools right">
          <button
            v-if="disabled"
            class="send-btn stop-btn"
            @click="$emit('stop')"
            title="停止生成"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
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
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 底部提示 -->
    <div class="input-footer">
      <span class="footer-hint">按 Enter 发送，Shift + Enter 换行</span>
    </div>

    <!-- 隐藏的文件输入 -->
    <input
      ref="fileInput"
      type="file"
      class="hidden-input"
      accept="*"
      @change="handleFileSelect"
      multiple
    />
    <input
      ref="imageInput"
      type="file"
      class="hidden-input"
      accept="image/*"
      @change="handleImageSelect"
      multiple
    />
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你想做的事...' },
  currentSkill: { type: String, default: '' }
})

const emit = defineEmits([
  'update:modelValue',
  'send',
  'stop',
  'quick-action',
  'file-upload',
  'image-upload',
  'voice-record',
  'remove-skill'
])

const skillConfig = {
  query: { icon: 'fa-magnifying-glass', label: 'AI智查' },
  file: { icon: 'fa-file-import', label: 'AI方案导入' },
  chat: { icon: 'fa-comments', label: '对话式配置' }
}

const skillTag = computed(() =>
  props.currentSkill && skillConfig[props.currentSkill]
    ? skillConfig[props.currentSkill]
    : null
)

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

const quickActions = [
  { key: 'query', label: '智能查询', content: '帮我查询一个表单配置', color: '#f59e0b' },
  { key: 'import', label: '方案导入', content: '帮我导入一个配置方案', color: '#10b981' },
  { key: 'chat', label: '对话配置', content: '帮我配置一个大学生套餐', color: '#8b5cf6' }
]

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
  const newHeight = Math.min(el.scrollHeight, 200)
  el.style.height = newHeight + 'px'
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

const handleQuickAction = (content) => {
  emit('quick-action', content)
}

const focus = () => {
  nextTick(() => inputEl.value?.focus())
}

const triggerFileUpload = () => {
  fileInput.value?.click()
}

const triggerImageUpload = () => {
  imageInput.value?.click()
}

const handleFileSelect = (e) => {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    attachments.value.push({
      type: 'file',
      name: file.name,
      size: file.size,
      file: file,
      preview: null
    })
  })
  e.target.value = ''
  emit('file-upload', files)
}

const handleImageSelect = (e) => {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    const reader = new FileReader()
    reader.onload = (event) => {
      attachments.value.push({
        type: 'image',
        name: file.name,
        size: file.size,
        file: file,
        preview: event.target?.result
      })
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
      if (event.data.size > 0) {
        audioChunks.push(event.data)
      }
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
        blob: blob,
        url: url,
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

/* 快捷操作栏 */
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
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.quick-action-chip:hover:not(:disabled) {
  background: var(--bg-tertiary);
  border-color: var(--border-strong);
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

/* 技能标签 */
.skill-tag-container {
  margin-bottom: 12px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 20px;
  font-size: 13px;
  color: #3b82f6;
}

.skill-close {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  border-radius: 50%;
  color: inherit;
  cursor: pointer;
  opacity: 0.6;
  transition: all 0.2s;
}

.skill-close:hover {
  opacity: 1;
  background: rgba(59, 130, 246, 0.1);
}

.skill-close:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* 输入框主体 */
.input-box {
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: 20px;
  padding: 4px;
  transition: all 0.2s;
}

.input-box.focused {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 44px;
}

/* 输入工具按钮 */
.input-tools {
  display: flex;
  align-items: center;
  padding: 8px 4px;
}

.input-tools.left {
  gap: 4px;
}

.tool-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 10px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}

.tool-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.tool-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tool-btn.recording {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 文本输入区 */
.textarea-container {
  flex: 1;
  min-width: 0;
  padding: 10px 4px;
  display: flex;
  align-items: center;
}

textarea {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  color: var(--text-primary);
  max-height: 200px;
  padding: 0;
}

textarea::placeholder {
  color: var(--text-tertiary);
}

textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 录音状态 */
.recording-status {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 10px;
}

.recording-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0.3; }
}

.recording-text {
  font-size: 14px;
  color: #ef4444;
  font-weight: 500;
}

/* 发送按钮 */
.send-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e5e7eb;
  border: none;
  border-radius: 12px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}

.send-btn.active {
  background: #3b82f6;
  color: white;
}

.send-btn.active:hover:not(:disabled) {
  background: #2563eb;
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn.stop-btn {
  background: #ef4444;
  color: white;
}

.send-btn.stop-btn:hover {
  background: #dc2626;
}

/* 底部提示 */
.input-footer {
  margin-top: 8px;
  text-align: center;
}

.footer-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 隐藏输入 */
.hidden-input {
  display: none;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .send-btn.active {
    background: #3b82f6;
  }

  .send-btn.active:hover:not(:disabled) {
    background: #2563eb;
  }
}

/* 响应式 - 平板 */
@media (max-width: 1024px) {
  .chat-input-container {
    padding: 14px 20px 22px;
  }

  .quick-actions-bar {
    gap: 8px;
  }

  .quick-action-chip {
    padding: 6px 12px;
  }
}

/* 响应式 - 手机横屏/小平板 */
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

  .skill-tag {
    padding: 5px 10px;
    font-size: 12px;
  }

  .input-wrapper {
    min-height: 40px;
    gap: 6px;
  }

  .tool-btn,
  .send-btn {
    width: 32px;
    height: 32px;
  }

  .tool-btn svg,
  .send-btn svg {
    width: 18px;
    height: 18px;
  }

  .textarea-container {
    padding: 8px 4px;
  }

  textarea {
    font-size: 16px; /* 防止 iOS 缩放 */
    line-height: 1.5;
  }

  .recording-status {
    padding: 6px 10px;
  }

  .recording-text {
    font-size: 13px;
  }

  .input-footer {
    margin-top: 6px;
  }

  .footer-hint {
    font-size: 11px;
  }
}

/* 响应式 - 手机 */
@media (max-width: 480px) {
  .chat-input-container {
    padding: 10px 12px 16px;
    border-top: 1px solid var(--border-light);
  }

  .quick-actions-bar {
    gap: 6px;
    margin-bottom: 8px;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 4px;
    scrollbar-width: none; /* Firefox */
  }

  .quick-actions-bar::-webkit-scrollbar {
    display: none; /* Chrome/Safari */
  }

  .quick-action-chip {
    flex-shrink: 0;
    padding: 5px 10px;
    font-size: 12px;
  }

  .skill-tag-container {
    margin-bottom: 8px;
  }

  .skill-tag {
    padding: 5px 10px;
    font-size: 12px;
    gap: 6px;
  }

  .skill-close {
    width: 16px;
    height: 16px;
  }

  .input-wrapper {
    min-height: 38px;
    gap: 4px;
  }

  .tool-btn,
  .send-btn {
    width: 28px;
    height: 28px;
  }

  .tool-btn svg,
  .send-btn svg {
    width: 16px;
    height: 16px;
  }

  .input-tools {
    padding: 6px 2px;
  }

  .input-tools.left {
    gap: 2px;
  }

  .textarea-container {
    padding: 6px 2px;
  }

  textarea {
    font-size: 16px;
    line-height: 1.5;
  }

  .recording-status {
    padding: 6px 8px;
    border-radius: 8px;
  }

  .recording-text {
    font-size: 12px;
  }

  .input-footer {
    margin-top: 6px;
  }

  .footer-hint {
    font-size: 10px;
  }
}

/* 移动端触摸优化 */
@media (pointer: coarse) {
  .tool-btn,
  .send-btn,
  .quick-action-chip,
  .skill-close {
    min-height: 36px;
    min-width: 36px;
  }

  .tool-btn:active:not(:disabled),
  .send-btn:active:not(:disabled) {
    transform: scale(0.95);
  }

  textarea {
    -webkit-tap-highlight-color: transparent;
  }
}

/* iPhone X+ 刘海屏适配 */
@supports (padding-bottom: env(safe-area-inset-bottom)) {
  @media (max-width: 768px) {
    .chat-input-container {
      padding-bottom: calc(16px + env(safe-area-inset-bottom));
    }
  }

  @media (max-width: 480px) {
    .chat-input-container {
      padding-bottom: calc(12px + env(safe-area-inset-bottom));
    }
  }
}

/* 小高度屏幕优化 - 横屏手机 */
@media (max-height: 500px) and (orientation: landscape) {
  .chat-input-container {
    padding: 8px 12px;
  }

  .quick-actions-bar {
    display: none; /* 横屏时隐藏快捷操作栏节省空间 */
  }

  .input-wrapper {
    min-height: 36px;
  }

  .input-footer {
    display: none; /* 横屏时隐藏底部提示 */
  }
}

/* 键盘弹起时的适配（部分浏览器支持） */
@media (max-height: 400px) {
  .chat-input-container {
    padding: 8px 12px;
  }

  .quick-actions-bar,
  .input-footer {
    display: none;
  }
}
</style>

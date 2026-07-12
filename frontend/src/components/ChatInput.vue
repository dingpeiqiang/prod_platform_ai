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

    <div v-if="skillTag" class="skill-tags">
      <span class="skill-tag">
        <i :class="`fa-solid ${skillTag.icon}`" />
        {{ skillTag.label }}
        <button type="button" class="close-btn" aria-label="关闭技能" @click="$emit('remove-skill')" :disabled="disabled">
          <i class="fa-solid fa-xmark" />
        </button>
      </span>
    </div>

    <div class="input-box" :class="{ focused: inputFocused }">
      <div class="input-actions">
        <button 
          class="action-btn" 
          @click="triggerFileUpload" 
          title="上传文件"
          :disabled="disabled"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </button>
        <button 
          class="action-btn" 
          @click="triggerImageUpload" 
          title="上传图片"
          :disabled="disabled"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
        </button>
        <button 
          class="action-btn voice-btn" 
          :class="{ recording: isRecording }"
          @mousedown="startRecording"
          @mouseup="stopRecording"
          @mouseleave="stopRecording"
          @touchstart.prevent="startRecording"
          @touchend="stopRecording"
          :disabled="disabled || isRecording"
          :title="isRecording ? '停止录制' : '录制语音'"
        >
          <svg v-if="!isRecording" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
      </div>
      
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
          :class="{ active: inputText.trim() || hasAttachment }"
          :disabled="!inputText.trim() && !hasAttachment"
          @click="handleSend"
          title="发送 (Enter)"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </div>

    <div v-if="isRecording" class="recording-indicator">
      <span class="recording-dot"></span>
      <span>正在录音...</span>
      <span class="recording-time">{{ recordingTime }}</span>
    </div>

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

const emit = defineEmits(['update:modelValue', 'send', 'stop', 'quick-action', 'file-upload', 'image-upload', 'voice-record', 'remove-skill'])

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
const inputFocused = ref(false)
const inputText = ref(props.modelValue)
const isRecording = ref(false)
const recordingTime = ref('00:00')
const attachments = ref([])

let recordingTimer = null
let mediaRecorder = null
let audioChunks = []

const hasAttachment = computed(() => attachments.value.length > 0)

const quickActions = [
  { key: 'config', label: '+ 新表单', content: '我想添加一种新的业务表单', color: '#f472b6' },
]

watch(() => props.modelValue, (val) => {
  inputText.value = val
})

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

const resetInput = () => {
  inputText.value = ''
  attachments.value = []
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
  if (!text && !hasAttachment.value || props.disabled) return
  
  emit('send', { text, attachments: [...attachments.value] })
  resetInput()
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
      
      attachments.value.push({
        type: 'voice',
        name: `录音_${new Date().toLocaleString()}.webm`,
        size: blob.size,
        blob: blob,
        url: url,
        duration: recordingTime.value
      })
      
      mediaRecorder = null
      audioChunks = []
    }
    
    mediaRecorder.stream.getTracks().forEach(track => track.stop())
  }
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

.skill-tags {
  margin-bottom: 10px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--color-primary-100);
  border: 1px solid var(--color-primary-400);
  border-radius: 999px;
  font-size: var(--font-size-xs);
  color: var(--color-primary-700);
}

.skill-tag .close-btn {
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.skill-tag .close-btn:hover:not(:disabled) {
  background: var(--color-primary-500);
  color: white;
}

.skill-tag .close-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-box.focused {
  border-color: var(--color-primary-400);
  box-shadow: 0 0 0 3px rgba(99,102,241,.1);
}

.input-actions {
  display: flex;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-3) 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn.voice-btn.recording {
  background: var(--color-error-500);
  color: var(--text-inverse);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.textarea-wrap {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  padding: var(--space-3);
  padding-top: var(--space-1);
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

.recording-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--color-error-50);
  border-radius: var(--radius-md);
  margin-top: var(--space-2);
  color: var(--color-error-600);
  font-size: var(--font-size-xs);
}

.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-error-500);
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.recording-time {
  font-family: var(--font-mono);
}

.hidden-input {
  display: none;
}
</style>
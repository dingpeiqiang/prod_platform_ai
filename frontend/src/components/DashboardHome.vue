<template>
  <div class="dashboard-home">
    <!-- 顶部欢迎区 -->
    <div class="welcome-area">
      <div class="welcome-content">
        <h1 class="welcome-title">有什么可以帮你的？</h1>
        <p class="welcome-subtitle">产商品智能助手，随时为你效劳</p>
      </div>
    </div>

    <!-- 快捷建议 -->
    <div class="suggestions-area">
    <div class="suggestions-grid">
      <button
        v-for="s in suggestions"
        :key="s.key"
        class="suggestion-item"
        @click="handleSuggestion(s)"
      >
        <span class="suggestion-icon">
          <svg v-if="s.icon === 'form'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <polyline points="10 9 9 9 8 9"/>
          </svg>
          <svg v-else-if="s.icon === 'calendar'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <svg v-else-if="s.icon === 'wallet'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 7h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"/>
            <path d="M16 21V5a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2z"/>
          </svg>
          <svg v-else-if="s.icon === 'help'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
        </span>
        <span class="suggestion-text">{{ s.text }}</span>
      </button>
    </div>
  </div>

    <!-- 底部输入区 -->
    <div class="bottom-input">
      <div class="chat-input-bar">
        <!-- 左侧工具按钮 -->
        <div class="input-tools">
          <input
            ref="fileInput"
            type="file"
            class="hidden-input"
            accept="*"
            @change="handleFileSelect"
          />
          <button class="tool-btn" title="上传文件" @click="triggerFileInput">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </button>
          
          <input
            ref="imageInput"
            type="file"
            class="hidden-input"
            accept="image/*"
            @change="handleImageSelect"
          />
          <button class="tool-btn" title="上传图片" @click="triggerImageInput">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
          </button>
          
          <button 
            class="tool-btn voice-btn" 
            :class="{ recording: isRecording }"
            title="语音录制"
            @mousedown="startRecording"
            @mouseup="stopRecording"
            @mouseleave="stopRecording"
            @touchstart.prevent="startRecording"
            @touchend="stopRecording"
          >
            <svg v-if="!isRecording" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="19" width="12" height="2" rx="1"/>
              <rect x="8" y="15" width="8" height="2" rx="1"/>
              <rect x="10" y="11" width="4" height="2" rx="1"/>
            </svg>
          </button>
        </div>
        
        <div class="textarea-wrap">
          <textarea
            ref="inputEl"
            v-model="inputText"
            :placeholder="placeholder"
            rows="1"
            @keydown.enter.exact.prevent="handleSend"
            @input="autoResize"
          />
          <button
            class="send-btn"
            :disabled="!inputText.trim() && attachments.length === 0"
            @click="handleSend"
            title="发送 (Enter)"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </div>
      </div>
      
      <!-- 附件预览区 -->
      <div v-if="attachments.length > 0" class="attachments-preview">
        <div 
          v-for="(attachment, index) in attachments" 
          :key="index" 
          class="attachment-item"
        >
          <img v-if="attachment.type === 'image'" :src="attachment.preview" class="attachment-image" />
          <div v-else class="attachment-file">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
            <span class="attachment-filename">{{ attachment.name }}</span>
          </div>
          <button class="attachment-remove" @click="removeAttachment(index)">×</button>
        </div>
      </div>
      
      <!-- 录音时长 -->
      <div v-if="isRecording" class="recording-indicator">
        <span class="recording-dot"></span>
        <span>录音中 {{ recordingTime }}</span>
      </div>
    </div>

    <!-- 待办、快捷、预警侧边栏 -->
    <div class="sidebar-widgets">
      <!-- 待办 -->
      <div class="widget-card">
        <div class="widget-header">
          <span class="widget-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <path d="M9 3v6h6"/>
            </svg>
          </span>
          <span class="widget-title">待办</span>
          <span class="widget-count">{{ pendingTodos.length }}</span>
        </div>
        <div class="widget-body">
          <div class="todo-input-row">
            <input
              v-model="newTodo"
              class="todo-input"
              placeholder="添加待办..."
              @keydown.enter="addTodo"
            />
            <button class="todo-add-btn" @click="addTodo">+</button>
          </div>
          <div class="todo-list">
            <div v-for="todo in todos" :key="todo.id" class="todo-item" :class="{ done: todo.done }">
              <input
                type="checkbox"
                :checked="todo.done"
                @change="toggleTodo(todo.id)"
              />
              <span class="todo-text">{{ todo.text }}</span>
              <button class="todo-delete" @click="deleteTodo(todo.id)">×</button>
            </div>
            <div v-if="!todos.length" class="empty-tip">暂无待办</div>
          </div>
        </div>
      </div>

      <!-- 快捷入口 -->
      <div class="widget-card">
        <div class="widget-header">
          <span class="widget-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </span>
          <span class="widget-title">快捷</span>
        </div>
        <div class="widget-body">
          <div class="shortcut-grid">
            <button
              v-for="sc in shortcuts"
              :key="sc.key"
              class="shortcut-btn"
              @click="handleShortcut(sc)"
            >
              <span class="shortcut-icon">
                <svg v-if="sc.icon === 'form'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                <svg v-else-if="sc.icon === 'calendar'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                  <line x1="16" y1="2" x2="16" y2="6"/>
                  <line x1="8" y1="2" x2="8" y2="6"/>
                  <line x1="3" y1="10" x2="21" y2="10"/>
                </svg>
                <svg v-else-if="sc.icon === 'wallet'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 7h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"/>
                  <path d="M16 21V5a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2z"/>
                </svg>
                <svg v-else-if="sc.icon === 'chart'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="20" x2="18" y2="10"/>
                  <line x1="12" y1="20" x2="12" y2="4"/>
                  <line x1="6" y1="20" x2="6" y2="16"/>
                </svg>
                <svg v-else-if="sc.icon === 'file'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                <svg v-else-if="sc.icon === 'help'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </span>
              <span class="shortcut-label">{{ sc.label }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 预警 -->
      <div class="widget-card">
        <div class="widget-header">
          <span class="widget-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
          </span>
          <span class="widget-title">预警</span>
          <span v-if="alerts.length" class="widget-count alert">{{ alerts.length }}</span>
        </div>
        <div class="widget-body">
          <div class="alert-list">
            <div v-for="alert in alerts" :key="alert.id" class="alert-item">
              <span class="alert-tag" :class="alert.type">{{ alert.tag }}</span>
              <span class="alert-text">{{ alert.text }}</span>
              <button class="alert-dismiss" @click="dismissAlert(alert.id)">×</button>
            </div>
            <div v-if="!alerts.length" class="empty-tip">暂无预警</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'

const emit = defineEmits(['send-message', 'switch-chat', 'create-session', 'open-scene-manager', 'open-prompt-manager', 'open-ontology-manager', 'open-workflow-manager', 'open-mcp-manager', 'open-kb-manager', 'open-demo-dashboard'])

const inputEl = ref(null)
const inputText = ref('')
const newTodo = ref('')
const fileInput = ref(null)
const imageInput = ref(null)

const attachments = ref([])
const isRecording = ref(false)
const recordingTime = ref('00:00')
let mediaRecorder = null
let audioChunks = []
let recordingTimer = null

// 快捷建议
const suggestions = [
  { key: 'help', icon: 'help', text: '我能为你做什么？' },
]

// 快捷入口
const shortcuts = [
  { key: 'demo', icon: 'chart', label: '智能配置助手' },
  { key: 'scene', icon: 'chart', label: '场景管理' },
  { key: 'prompt', icon: 'file', label: '提示词管理' },
  // { key: 'tool', icon: 'chart', label: '工具管理' }, // 已迁移到 MCP 管理
  // { key: 'form', icon: 'file', label: '表单管理' }, // 已废弃，使用本体管理替代
  { key: 'ontology', icon: 'help', label: '本体管理' },
  { key: 'workflow', icon: 'chart', label: '工作流管理' },
  { key: 'mcp', icon: 'chart', label: 'MCP 管理' },
  { key: 'kb', icon: 'file', label: '知识库' },
]

// 预警列表
const alerts = ref([
  { id: 1, tag: '待审批', text: '销售订单 #1023 等待审批', type: 'warning' },
  { id: 2, tag: '超时', text: '报销单 #201 审批超时 2 天', type: 'danger' },
])

// 待办
const todos = ref([])
const TODOS_KEY = 'dashboard_todos'

const pendingTodos = computed(() => todos.value.filter(t => !t.done))

const loadTodos = () => {
  try {
    const raw = localStorage.getItem(TODOS_KEY)
    if (raw) todos.value = JSON.parse(raw)
  } catch {}
}

const saveTodos = () => {
  localStorage.setItem(TODOS_KEY, JSON.stringify(todos.value))
}

const addTodo = () => {
  const text = newTodo.value.trim()
  if (!text) return
  todos.value.push({ id: Date.now(), text, done: false })
  newTodo.value = ''
  saveTodos()
}

const toggleTodo = (id) => {
  const todo = todos.value.find(t => t.id === id)
  if (todo) {
    todo.done = !todo.done
    saveTodos()
  }
}

const deleteTodo = (id) => {
  todos.value = todos.value.filter(t => t.id !== id)
  saveTodos()
}

const dismissAlert = (id) => {
  alerts.value = alerts.value.filter(a => a.id !== id)
}

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

const placeholder = computed(() => {
  const tips = [
    '描述你想做的事...',
    '问问「我能为你做什么」',
  ]
  return tips[Math.floor(Math.random() * tips.length)]
})

const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text && attachments.value.length === 0) return
  
  const messageData = {
    text,
    attachments: [...attachments.value]
  }
  
  emit('send-message', messageData)
  inputText.value = ''
  attachments.value = []
  nextTick(() => {
    if (inputEl.value) {
      inputEl.value.style.height = 'auto'
      inputEl.value.focus()
    }
  })
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

const triggerImageInput = () => {
  imageInput.value?.click()
}

const handleFileSelect = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  
  attachments.value.push({
    name: file.name,
    file,
    type: 'file',
    size: file.size
  })
  
  event.target.value = ''
}

const handleImageSelect = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  
  const reader = new FileReader()
  reader.onload = (e) => {
    attachments.value.push({
      name: file.name,
      file,
      type: 'image',
      preview: e.target.result,
      size: file.size
    })
  }
  reader.readAsDataURL(file)
  
  event.target.value = ''
}

const removeAttachment = (index) => {
  attachments.value.splice(index, 1)
}

const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const startRecording = async () => {
  if (isRecording.value) return
  
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
      recordingTime.value = formatTime(seconds)
    }, 1000)
    
    stream.getTracks().forEach(track => {
      track.onended = () => {
        if (isRecording.value) {
          stopRecording()
        }
      }
    })
  } catch (error) {
    console.error('录音失败:', error)
    alert('无法访问麦克风，请检查权限设置')
  }
}

const stopRecording = () => {
  if (!isRecording.value || !mediaRecorder) return
  
  isRecording.value = false
  
  if (recordingTimer) {
    clearInterval(recordingTimer)
    recordingTimer = null
  }
  
  mediaRecorder.stop()
  
  mediaRecorder.stream.getTracks().forEach(track => track.stop())
  
  if (audioChunks.length > 0) {
    const blob = new Blob(audioChunks, { type: 'audio/webm' })
    const audioName = `voice_${Date.now()}.webm`
    
    attachments.value.push({
      name: audioName,
      file: blob,
      type: 'voice',
      duration: recordingTime.value,
      preview: URL.createObjectURL(blob)
    })
  }
  
  mediaRecorder = null
  audioChunks = []
}

const handleSuggestion = (s) => {
  emit('send-message', s.text)
}

const handleShortcut = (sc) => {
  if (sc.key === 'demo') {
    emit('open-demo-dashboard')
    return
  }
  if (sc.key === 'scene') {
    emit('open-scene-manager')
    return
  }
  if (sc.key === 'prompt') {
    emit('open-prompt-manager')
    return
  }
  // if (sc.key === 'tool') {
  //   emit('open-tool-manager') // 已迁移到 MCP 管理
  //   return
  // }
  // if (sc.key === 'form') {
  //   emit('open-form-manager') // 已废弃
  //   return
  // }
  if (sc.key === 'ontology') {
    emit('open-ontology-manager')
    return
  }
  if (sc.key === 'workflow') {
    emit('open-workflow-manager')
    return
  }
  if (sc.key === 'mcp') {
    emit('open-mcp-manager')
    return
  }
  if (sc.key === 'kb') {
    emit('open-kb-manager')
    return
  }
  const msg = shortcuts.find(s => s.key === sc.key)
  if (msg) {
    emit('send-message', `帮我填一个${sc.label}`)
  }
}

onMounted(() => {
  loadTodos()
  nextTick(() => inputEl.value?.focus())
})
</script>

<style scoped>
.dashboard-home {
  display: grid;
  grid-template-columns: 1fr 280px;
  grid-template-rows: auto auto 1fr;
  gap: 24px;
  height: 100%;
  padding: 40px 48px;
  background: var(--bg-secondary);
  overflow: hidden;
}

/* 顶部欢迎区 */
.welcome-area {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 20px;
}

.welcome-content {
  text-align: center;
}

.welcome-title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin-bottom: 12px;
  letter-spacing: -0.5px;
}

.welcome-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

/* 快捷建议 */
.suggestions-area {
  grid-column: 1;
  grid-row: 2;
  display: flex;
  justify-content: center;
}

.suggestions-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 640px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: 14px;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all .2s;
  box-shadow: var(--shadow-sm);
}

.suggestion-item:hover {
  border-color: #818cf8;
  box-shadow: 0 4px 16px rgba(99,102,241,.15);
  transform: translateY(-2px);
}

.suggestion-icon {
  font-size: 18px;
}

.suggestion-text {
  white-space: nowrap;
}

/* 底部输入区 */
.bottom-input {
  grid-column: 1;
  grid-row: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 20px;
}

.chat-input-bar {
  position: relative;
  width: 100%;
  max-width: 720px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  transition: all var(--transition-fast);
}

.chat-input-bar:focus-within {
  border-color: var(--color-primary-400);
  box-shadow: 0 0 0 3px rgba(99,102,241,.1);
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

.send-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
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

.input-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 工具按钮 */
.hidden-input {
  display: none;
}

.input-tools {
  display: flex;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-3) 0;
}

.tool-btn {
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

.tool-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-btn.voice-btn.recording {
  background: var(--color-error-500);
  color: var(--text-inverse);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 附件预览区 */
.attachments-preview {
  width: 100%;
  max-width: 720px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  padding: 12px 16px;
  background: var(--bg-elevated);
  border-radius: 16px;
  border: 1px solid var(--border-light);
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 10px;
  position: relative;
}

.attachment-image {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
}

.attachment-file {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.attachment-filename {
  font-size: 13px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: #ef4444;
  color: white;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform .2s;
}

.attachment-remove:hover {
  transform: scale(1.1);
}

/* 录音指示器 */
.recording-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 16px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 20px;
  color: #ef4444;
  font-size: 14px;
}

.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: blink 1s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* 右侧小部件 */
.sidebar-widgets {
  grid-column: 2;
  grid-row: 1 / 4;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 4px;
}

.sidebar-widgets::-webkit-scrollbar {
  width: 4px;
}

.sidebar-widgets::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 2px;
}

.widget-card {
  background: var(--bg-elevated);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.widget-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
}

.widget-icon {
  font-size: 16px;
}

.widget-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  flex: 1;
}

.widget-count {
  background: var(--color-primary-400);
  color: var(--text-inverse);
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.widget-count.alert {
  background: #f87171;
}

.widget-body {
  min-height: 80px;
}

/* 待办 */
.todo-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.todo-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  font-size: 13px;
  outline: none;
}

.todo-input:focus {
  border-color: #818cf8;
}

.todo-add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #818cf8;
  color: var(--text-inverse);
  border-radius: 8px;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  transition: background .2s;
}

.todo-add-btn:hover {
  background: #6366f1;
}

.todo-list {
  max-height: 120px;
  overflow-y: auto;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
}

.todo-item.done .todo-text {
  text-decoration: line-through;
  color: var(--text-tertiary);
}

.todo-item input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #818cf8;
}

.todo-text {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
}

.todo-delete {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
}

.todo-delete:hover {
  color: var(--color-error-500);
}

/* 快捷入口 */
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.shortcut-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: #f8f8ff;
  border: 1px solid #eaeaea;
  border-radius: 10px;
  cursor: pointer;
  transition: all .2s;
}

.shortcut-btn:hover {
  background: #f0f0ff;
  border-color: #c7c7fa;
  transform: translateY(-1px);
}

.shortcut-icon {
  font-size: 18px;
}

.shortcut-label {
  font-size: 11px;
  color: #555;
}

/* 预警 */
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f9f9f9;
  border-radius: 8px;
  font-size: 12px;
}

.alert-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  flex-shrink: 0;
}

.alert-tag.warning {
  background: #fef3c7;
  color: #d97706;
}

.alert-tag.danger {
  background: #fee2e2;
  color: #dc2626;
}

.alert-text {
  flex: 1;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alert-dismiss {
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
}

.alert-dismiss:hover {
  color: var(--text-secondary);
}

.empty-tip {
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
  padding: 16px 0;
}

/* 响应式 */
@media (max-width: 1024px) {
  .dashboard-home {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto 1fr auto;
    padding: 24px;
    gap: 20px;
  }
  .welcome-area {
    grid-column: 1;
  }
  .suggestions-area {
    grid-column: 1;
  }
  .bottom-input {
    grid-column: 1;
  }
  .sidebar-widgets {
    grid-column: 1;
    grid-row: 4;
    flex-direction: row;
    overflow-x: auto;
    padding-right: 0;
  }
  .widget-card {
    min-width: 200px;
    flex-shrink: 0;
  }
}

@media (max-width: 768px) {
  .welcome-title {
    font-size: 28px;
  }
  .suggestions-grid {
    gap: 8px;
  }
  .suggestion-item {
    padding: 12px 16px;
    font-size: 13px;
  }
  .chat-input-bar {
    padding: 14px 16px;
  }
  .sidebar-widgets {
    flex-direction: column;
  }
  .widget-card {
    min-width: unset;
  }
}
</style>

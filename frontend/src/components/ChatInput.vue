<template>
  <div class="chat-input-shell">
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

      <div class="composer-panel" :class="{ focused: isFocused, disabled }">
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

        <div class="composer-footer">
          <div class="composer-tools">
            <button class="tool-btn" @click="triggerFileUpload" title="上传文件" :disabled="disabled">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
            </button>
            <button class="tool-btn" @click="triggerImageUpload" title="上传图片" :disabled="disabled">
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
              @touchstart.passive="startRecording"
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

          <div class="composer-right">
            <div class="model-bar">
              <div class="model-menu-wrap" ref="modelMenuWrap">
                <button class="model-select-pill" type="button" @click.stop="toggleModelMenu" :disabled="disabled" :title="currentModelLabel || '选择模型'">
                  <span class="model-select-pill-text">{{ currentModelShortLabel || '选择模型' }}</span>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9" />
                  </svg>
                </button>
                <div v-if="showModelMenu" class="model-menu" @click.stop>
                  <template v-if="availableModels.length > 0">
                    <button
                      v-for="model in availableModels"
                      :key="model.id"
                      type="button"
                      class="model-menu-item"
                      :class="{ active: model.id === selectedModelId }"
                      @click="selectModel(model.id)"
                    >
                      <span class="model-menu-item-name">{{ model.name }}</span>
                      <span class="model-menu-item-provider">{{ model.providerName || model.provider || '' }}</span>
                    </button>
                    <div class="model-menu-divider"></div>
                  </template>
                  <div v-else class="model-menu-empty">暂无可用模型，先去管理模型里配置一个连接</div>
                  <button
                    type="button"
                    class="model-menu-item manage"
                    @click.stop="openModelManager"
                  >
                    <span class="model-menu-item-name">管理模型</span>
                    <span class="model-menu-item-provider">配置连接与参数</span>
                  </button>
                </div>
              </div>
              <span v-if="modelLoadingError" class="model-error" title="加载模型列表失败">!</span>
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

      <div class="footer-hint-row">
        <span class="footer-hint">@ # / 快捷能力，Enter 发送，Shift + Enter 换行</span>
      </div>

      <input ref="fileInput" type="file" class="hidden-input" accept="*" @change="handleFileSelect" multiple />
      <input ref="imageInput" type="file" class="hidden-input" accept="image/*" @change="handleImageSelect" multiple />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { useModelsStore } from '@/stores/models.js'

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
  'skill-select',
  'open-model-config'
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

const modelsStore = useModelsStore()
const availableModels = computed(() => modelsStore.models)
const selectedModelId = ref('')
const modelLoadingError = computed(() => !!modelsStore.loadError && modelsStore.models.length === 0)
const showModelMenu = ref(false)
const modelMenuWrap = ref(null)
const MODEL_ID_KEY = 'chat_selected_model_id'
const MODEL_DEBUG_PREFIX = '[ModelDebug][ChatInput]'
const __instance = getCurrentInstance()

const modelDebug = (step, details = {}) => {
  console.log(`${MODEL_DEBUG_PREFIX} ${step}`, {
    timestamp: new Date().toISOString(),
    showModelMenu: showModelMenu.value,
    disabled: props.disabled,
    selectedModelId: selectedModelId.value,
    availableModelCount: availableModels.value.length,
    componentConnected: !!modelMenuWrap.value?.isConnected,
    ...details,
  })
}

const currentModelLabel = computed(() => {
  const m = availableModels.value.find(x => x.id === selectedModelId.value)
  return m ? `${m.providerName || m.provider || '模型'}: ${m.name}` : ''
})

const currentModelShortLabel = computed(() => {
  const m = availableModels.value.find(x => x.id === selectedModelId.value)
  return m ? m.name : ''
})

const loadAvailableModels = async (force = false) => {
  await modelsStore.loadModels(force)
  const list = modelsStore.models
  if (list.length > 0) {
    const savedId = localStorage.getItem(MODEL_ID_KEY)
    if (savedId && list.some(m => m.id === savedId)) {
      selectedModelId.value = savedId
    } else {
      const def = list.find(m => m.isDefault) || list[0]
      selectedModelId.value = def?.id || ''
    }
  }
}

watch(showModelMenu, (visible, previous) => {
  modelDebug('showModelMenu changed', { previous, visible })
  if (visible) {
    nextTick(() => {
      document.addEventListener('click', handleDocumentClick)
      modelDebug('document click listener attached', {
        menuElementExists: !!modelMenuWrap.value?.querySelector('.model-menu'),
      })
    })
  } else {
    document.removeEventListener('click', handleDocumentClick)
    modelDebug('document click listener removed')
  }
})

watch(selectedModelId, (id) => {
  if (id) localStorage.setItem(MODEL_ID_KEY, id)
})

watch(availableModels, (newModels) => {
  if (newModels.length === 0) return
  const savedId = localStorage.getItem(MODEL_ID_KEY)
  if (savedId && newModels.some(m => m.id === savedId)) {
    selectedModelId.value = savedId
  } else {
    const def = newModels.find(m => m.isDefault) || newModels[0]
    selectedModelId.value = def?.id || ''
  }
}, { deep: true })

const closeModelMenu = (reason = 'unknown') => {
  modelDebug('closeModelMenu called', { reason })
  showModelMenu.value = false
}

const toggleModelMenu = (event) => {
  modelDebug('model selector button clicked', {
    eventType: event?.type,
    target: event?.target?.tagName,
    currentTarget: event?.currentTarget?.tagName,
  })
  if (props.disabled) {
    modelDebug('model selector ignored because input is disabled')
    return
  }
  showModelMenu.value = !showModelMenu.value
  nextTick(() => {
    modelDebug('model menu DOM checked after toggle', {
      menuElementExists: !!modelMenuWrap.value?.querySelector('.model-menu'),
      menuRect: modelMenuWrap.value?.querySelector('.model-menu')?.getBoundingClientRect?.().toJSON?.(),
    })
  })
}

const selectModel = (id) => {
  modelDebug('model item selected', { id })
  selectedModelId.value = id
  closeModelMenu('model-selected')
}

const openModelManager = (event) => {
  modelDebug('manage model button handler entered', {
    eventType: event?.type,
    eventPhase: event?.eventPhase,
    defaultPrevented: event?.defaultPrevented,
    targetText: event?.currentTarget?.innerText,
  })
  // 关键修复：先 emit 再关闭菜单。
  // 之前先 closeModelMenu 把 showModelMenu 置 false，会触发 v-if 的 DOM 卸载调度，
  // 在某些 Vue 调度时序下会干扰同步 emit 对父组件 handler 的查找。
  const vnodeProps = __instance?.vnode?.props || {}
  const hasListener = !!vnodeProps.onOpenModelConfig
  // 用纯字符串打印，避免 Chrome 控制台对象折叠导致看不到值
  console.log(
    `${MODEL_DEBUG_PREFIX} >>> hasListener=${hasListener} propKeys=${JSON.stringify(Object.keys(vnodeProps))} emitKeys=${JSON.stringify(Object.keys(__instance?.vnode?.props || {}).filter(k => k.startsWith('on')))}`
  )
  modelDebug('emitting open-model-config to App', {
    hasListener,
    propKeys: Object.keys(vnodeProps),
  })
  emit('open-model-config', {
    source: 'ChatInput',
    timestamp: Date.now(),
  })
  modelDebug('open-model-config emit completed')
  closeModelMenu('open-model-manager')
}

const handleDocumentClick = (event) => {
  if (!showModelMenu.value) return
  const wrap = modelMenuWrap.value
  const inside = !!wrap?.contains(event.target)
  modelDebug('document click observed while menu open', {
    insideModelMenu: inside,
    target: event.target?.tagName,
    targetClass: event.target?.className,
  })
  if (wrap && !inside) {
    closeModelMenu('outside-document-click')
  }
}

const buildModelConfig = () => {
  const m = availableModels.value.find(x => x.id === selectedModelId.value)
  if (!m) return null
  let fullConfig = null
  try {
    const raw = localStorage.getItem('chat_model_config')
    if (raw) {
      const cfg = JSON.parse(raw)
      const localId = (cfg.provider || 'custom') + '-' + cfg.model
      if (localId === m.id) fullConfig = { ...cfg }
    }
  } catch {}

  if (fullConfig) {
    return {
      provider: fullConfig.provider || m.provider,
      model: fullConfig.model || m.name,
      api_key: fullConfig.api_key || fullConfig.apiKey || undefined,
      base_url: fullConfig.base_url || fullConfig.baseUrl || undefined,
      temperature: fullConfig.temperature ?? 0.3,
      max_tokens: fullConfig.max_tokens || fullConfig.maxTokens || 2048,
      thinking: fullConfig.thinking ?? false
    }
  }

  return { provider: m.provider, model: m.name }
}

onMounted(() => {
  loadAvailableModels()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

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
  const modelConfig = buildModelConfig()
  emit('send', { text, attachments: [...attachments.value], modelConfig })
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

defineExpose({ focus, resetInput, reloadModels: () => loadAvailableModels(true) })
</script>

<style scoped>
.chat-input-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-input-container {
  padding: 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
}

.quick-actions-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.quick-action-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.quick-action-chip:hover:not(:disabled) {
  background: var(--bg-tertiary);
  border-color: rgba(91, 124, 250, 0.2);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.quick-action-chip:disabled { opacity: 0.45; cursor: not-allowed; }
.chip-icon { font-weight: 500; font-size: 13px; }

.composer-panel {
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: var(--bg-secondary);
  padding: 10px 10px 9px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.composer-panel.focused {
  border-color: rgba(91, 124, 250, 0.35);
  box-shadow: 0 0 0 1px rgba(91, 124, 250, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.composer-panel.disabled { opacity: 0.92; }

.message-input {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.6;
  min-height: 64px;
  max-height: 180px;
  padding: 2px 2px 8px;
}

.message-input::placeholder { color: var(--text-tertiary); }

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-tools, .composer-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-right { margin-left: auto; }

.tool-btn,
.send-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.tool-btn {
  background: var(--bg-primary);
  color: var(--text-secondary);
}

.tool-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.tool-btn.recording {
  background: rgba(248, 113, 113, 0.12);
  color: #f87171;
}

.send-btn {
  background: var(--bg-primary);
  color: var(--text-tertiary);
}

.send-btn.active {
  background: var(--color-primary-600);
  color: #fff;
  border-color: rgba(91, 124, 250, 0.2);
}

.send-btn.active:hover:not(:disabled) { transform: translateY(-1px); }
.send-btn.stop-btn { background: var(--color-error-500); color: #fff; }
.send-btn:disabled, .tool-btn:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }

.model-bar { display: flex; align-items: center; gap: 6px; }

.model-menu-wrap {
  position: relative;
  display: inline-flex;
}

.model-select-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
  white-space: nowrap;
}

.model-select-pill:hover:not(:disabled) {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border-color: rgba(91, 124, 250, 0.25);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.model-select-pill-text {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.model-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  z-index: 30;
  width: 280px;
  padding: 8px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.18);
}

.model-menu-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
  transition: background 0.16s ease, color 0.16s ease;
}

.model-menu-item:hover,
.model-menu-item.active {
  background: var(--bg-secondary);
}

.model-menu-item.active {
  color: var(--color-primary-700);
}

.model-menu-item.manage {
  background: rgba(91, 124, 250, 0.08);
}

.model-menu-item.manage:hover {
  background: rgba(91, 124, 250, 0.12);
}

.model-menu-item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
}

.model-menu-item-provider {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.model-menu-divider {
  height: 1px;
  margin: 6px 4px;
  background: var(--border-light);
}

.model-error {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-error-500);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: help;
}

.model-menu-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.skill-strip { display: flex; align-items: center; margin-top: 10px; }
.skill-chip {
  display: inline-flex; align-items: center; gap: 6px; height: 28px; padding: 0 10px 0 6px;
  background: var(--bg-primary); border: 1px solid var(--border-light); border-radius: 999px;
  font-size: 12px; color: var(--text-secondary); cursor: pointer;
}
.skill-chip-icon {
  width: 16px; height: 16px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 999px; background: var(--bg-secondary); flex-shrink: 0;
}
.skill-chip-icon svg { display: block; }
.skill-chip-text { font-weight: 500; }
.skill-chip-arrow { width: 12px; height: 12px; display: inline-flex; align-items: center; justify-content: center; color: var(--text-tertiary); }
.skill-chip-close { width: 18px; height: 18px; border: none; background: transparent; border-radius: 999px; color: var(--text-tertiary); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }
.skill-chip-close:hover:not(:disabled) { background: var(--bg-tertiary); }

.recording-status { display: flex; align-items: center; gap: 8px; margin-top: 8px; color: var(--color-error-500); font-size: 12px; }
.recording-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--color-error-500); animation: blink 1s infinite; }
@keyframes blink { 0%,50%{opacity:1;} 51%,100%{opacity:.25;} }

.footer-hint-row { display: flex; align-items: center; justify-content: space-between; padding: 0 4px; }
.footer-hint { font-size: 11px; color: var(--text-tertiary); }
.hidden-input { display: none; }

@media (max-width: 768px) {
  .chat-input-container { padding: 8px; }
  .composer-panel { padding: 10px 9px 8px; }
  .message-input { min-height: 56px; font-size: 15px; }
}

@media (max-width: 480px) {
  .quick-actions-bar { overflow-x: auto; flex-wrap: nowrap; scrollbar-width: none; }
  .quick-actions-bar::-webkit-scrollbar { display: none; }
  .composer-footer { flex-wrap: wrap; }
  .composer-right { width: 100%; justify-content: space-between; }
  .model-menu-wrap { flex: 1; }
  .model-select-pill { width: 100%; justify-content: space-between; }
  .model-menu { width: min(100vw - 32px, 280px); }
}
</style>

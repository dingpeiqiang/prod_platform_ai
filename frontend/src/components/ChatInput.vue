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

      <!-- 资费配置：三种配置方式技能（可多选，点 × 取消，选中不触发AI消息） -->
      <div v-if="showTariffSkills" class="tariff-skill-bar">
        <span class="tariff-skill-label">选择配置方式</span>
        <div
          v-for="skill in tariffSkills"
          :key="skill.key"
          class="tariff-skill-chip"
          :class="{ selected: selectedTariffSkill === skill.key }"
          role="button"
          tabindex="0"
          @click="toggleTariffSkill(skill.key)"
          @keydown.enter.prevent="toggleTariffSkill(skill.key)"
          @keydown.space.prevent="toggleTariffSkill(skill.key)"
        >
          <span class="chip-icon">+</span>
          <span class="chip-text">{{ skill.label }}</span>
          <button
            v-if="selectedTariffSkill === skill.key"
            type="button"
            class="chip-close"
            title="取消选中"
            @click.stop="toggleTariffSkill(skill.key)"
          >
            ×
          </button>
        </div>
      </div>

      <div
        class="composer-panel"
        :class="{ focused: isFocused, disabled, 'drag-over': isDragOver }"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
        @paste.capture="handlePaste"
      >
        <div v-if="attachments.length" class="attachment-list">
          <div
            v-for="(att, idx) in attachments"
            :key="att.id || `${att.name}-${idx}`"
            class="attachment-chip"
            :class="[`status-${att.uploadStatus || 'pending'}`]"
            :title="attachmentTitle(att)"
          >
            <img v-if="att.type === 'image' && att.preview" :src="att.preview" class="att-thumb" alt="" />
            <span v-else class="att-icon" aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
            </span>
            <span class="att-meta">
              <span class="att-name">{{ att.name }}</span>
              <span class="att-status-line">
                <span v-if="att.size" class="att-size">{{ formatFileSize(att.size) }}</span>
                <span class="att-status">{{ uploadStatusLabel(att) }}</span>
              </span>
            </span>
            <button
              type="button"
              class="att-remove"
              title="移除"
              :disabled="disabled"
              @click="removeAttachment(idx)"
            >
              ×
            </button>
          </div>
        </div>

        <p v-if="pasteHint" class="paste-hint">{{ pasteHint }}</p>

        <!-- 产品查询 · 富文本模板槽位导引 -->
        <div v-if="showCopySearch" class="copy-slot-guide">
          套餐类型为下拉框，套餐名称/套餐编码可直接输入，退格可删除槽位
        </div>

        <SlotTextInput
          ref="slotInputEl"
          :model-value="slotSegments"
          :template="querySlotTemplate"
          :slot-options="querySlotOptions"
          :placeholder="placeholder"
          :disabled="disabled || isRecording"
          @update:model-value="onSlotSegments"
          @input-state="onSlotInputState"
          @submit="handleSend"
        />

        <div v-if="isDragOver" class="drop-hint">松开以添加文件</div>

        <div class="composer-footer">
          <div class="composer-left">
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
                  </template>
                  <div v-else class="model-menu-empty">暂无可用模型</div>
                </div>
              </div>
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

        <div v-if="isRecording" class="recording-status">
          <span class="recording-dot"></span>
          <span class="recording-text">正在录音 {{ recordingTime }}</span>
        </div>
      </div>

      <div class="footer-hint-row">
        <span class="footer-hint">Enter 发送，Shift + Enter 换行</span>
      </div>

      <input ref="fileInput" type="file" class="hidden-input" accept="*" @change="handleFileSelect" multiple />
      <input ref="imageInput" type="file" class="hidden-input" accept="image/*" @change="handleImageSelect" multiple />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, reactive, onMounted, onBeforeUnmount, markRaw } from 'vue'
import { assistantModes } from '../config/assistantModes.js'
import { usePerspectiveStore } from '../stores/perspective.js'
import { storeToRefs } from 'pinia'
import SlotTextInput from './SlotTextInput.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你想做的事...' },
  currentSkill: { type: String, default: '' },
  assistantMode: { type: String, default: '' },
  activeScene: { type: String, default: '' },
  // 外部触发的技能选中信号：{ key, seq }，seq 递增以支持重复选中同一技能
  skillSignal: { type: Object, default: null }
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

const perspectiveStore = usePerspectiveStore()
const { opsPerspective } = storeToRefs(perspectiveStore)

const skillConfig = {
  query: { label: '智查·历史复用' },
  file: { label: '智读·文件配置' },
  chat: { label: '智聊·对话配置' },
  compliance: { label: '智检·合规校验' },
  ops: { label: '运营助手' }
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

// ── Mock 模型列表 ──
// 后续接入真实模型管理时，替换为从 store/API 加载
const availableModels = ref([
  { id: 'mock-gpt', name: 'GPT-4o (mock)', provider: 'OpenAI', providerName: 'OpenAI', isDefault: true },
  { id: 'mock-claude', name: 'Claude 3.5 (mock)', provider: 'Anthropic', providerName: 'Anthropic' },
  { id: 'mock-local', name: '本地模型 (mock)', provider: 'Local', providerName: '本地' },
])
const selectedModelId = ref('mock-gpt')
const showModelMenu = ref(false)
const modelMenuWrap = ref(null)

const currentModelLabel = computed(() => {
  const m = availableModels.value.find(x => x.id === selectedModelId.value)
  return m ? `${m.providerName || m.provider || '模型'}: ${m.name}` : ''
})

const currentModelShortLabel = computed(() => {
  const m = availableModels.value.find(x => x.id === selectedModelId.value)
  return m ? m.name : ''
})

const toggleModelMenu = () => {
  if (props.disabled) return
  showModelMenu.value = !showModelMenu.value
}

const selectModel = (id) => {
  selectedModelId.value = id
  showModelMenu.value = false
}

const handleDocumentClick = (event) => {
  if (!showModelMenu.value) return
  const wrap = modelMenuWrap.value
  if (wrap && !wrap.contains(event.target)) {
    showModelMenu.value = false
  }
}

watch(showModelMenu, (visible) => {
  if (visible) {
    nextTick(() => document.addEventListener('click', handleDocumentClick))
  } else {
    document.removeEventListener('click', handleDocumentClick)
  }
})

// ── 输入框 & 附件 ──
const fileInput = ref(null)
const imageInput = ref(null)
const isFocused = ref(false)
const inputText = ref(props.modelValue)
const isRecording = ref(false)
const recordingTime = ref('00:00')
const attachments = ref([])
const isDragOver = ref(false)
const pasteHint = ref('')
let dragDepth = 0
let pasteHintTimer = null

let recordingTimer = null
let mediaRecorder = null
let audioChunks = []

// ── Mock 快捷动作 ──
// 各助手输入框上方的三个场景（quick-action 芯片），点击后填入对应提示语。
// 产品运营助手已替换为真实运营场景；研发/查询助手暂保留通用占位。
const allQuickActions = [
  // 产品运营助手：三个真实运营场景（scene 与 assistantModes 中的 ops sceneShortcuts 对应）
  { key: 'data', label: '运营数据分析', scene: 'ops.scene1', content: '帮我看下本周活跃用户的涨跌原因', color: '#8b5cf6', modes: ['ops'] },
  { key: 'analyze', label: '异动归因分析', scene: 'ops.scene2', content: '帮我分析一下WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)数据卡的新增异动', color: '#10b981', modes: ['ops'] },
  { key: 'optimize', label: '套餐优化建议', scene: 'ops.scene3', content: '针对WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)数据卡的亚健康情况给出优化方案', color: '#f59e0b', modes: ['ops'] },
  // 产品查询助手：三个真实查询场景（scene 与 assistantModes 中的 query sceneShortcuts 对应）
  { key: 'query_chat', label: '智能问答', scene: 'query.scene1', content: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)包含多少高速流量？', color: '#8b5cf6', modes: ['query'] },
  { key: 'query_query', label: '比对分析', scene: 'query.scene3', content: '对比WeWalk香港.台湾30日不限量和WeWalk15日不限量数据卡', color: '#f59e0b', modes: ['query'] },
]

const quickActions = computed(() => {
  const mode = props.assistantMode
  if (!mode) return allQuickActions
  let list = allQuickActions.filter((a) => a.modes.includes(mode))
  // 运营助手：平台视角不显示「套餐优化建议」，代理商视角显示
  if (mode === 'ops' && opsPerspective.value === 'platform') {
    list = list.filter((a) => a.key !== 'optimize')
  }
  return list
})

const hasContent = computed(() => inputText.value.trim().length > 0 || attachments.value.length > 0 || slotHasContent.value)
const canSend = computed(() => (hasContent.value || slotHasContent.value) && !props.disabled)

// ── 产品查询：富文本模板 + 【】槽位（可下拉/可输入/可删除）──
const slotInputEl = ref(null)
const slotSegments = ref([])
const slotState = reactive({ hasText: false, hasSlot: false })
const slotHasContent = computed(() => slotState.hasText || slotState.hasSlot)
const showCopySearch = computed(() => selectedTariffSkill.value === 'copy')

// 产品查询槽位模板：一句带【】槽位的提示词
const querySlotTemplate = [
  { type: 'text', text: '我想要查' },
  { type: 'slot', label: '套餐类型', value: '预付费主套餐' },
  { type: 'text', text: '的' },
  { type: 'slot', label: '套餐名称', value: '' },
  { type: 'text', text: '套餐，套餐编码为' },
  { type: 'slot', label: '套餐编码', value: '' },
  { type: 'text', text: '。' },
]

// 产品查询槽位的静态可选项
const querySlotOptions = {
  '套餐类型': ['预付费主套餐', '附加包'],
  '套餐名称': [],
  '套餐编码': [],
}

function onSlotSegments(segs) {
  slotSegments.value = segs
}

function onSlotInputState(state) {
  slotState.hasText = !!state?.hasText
  slotState.hasSlot = !!state?.hasSlot
}

// 从槽位段中提取指定 label 的值（用于组装 copySearch）
function slotValueByLabel(label) {
  const seg = (slotSegments.value || []).find((s) => s.type === 'slot' && s.label === label)
  return (seg?.value || '').trim()
}

// 套用产品查询模板（重置输入框为模板内容）
function applyQueryTemplate() {
  slotSegments.value = []
  slotState.hasText = false
  slotState.hasSlot = false
  nextTick(() => {
    slotInputEl.value?.setTemplate()
    slotInputEl.value?.focus()
  })
}

// 套用产品查询模板（重置输入框为模板内容，可选预填套餐名称）
function applyCopyTemplate(name) {
  slotSegments.value = []
  slotState.hasText = false
  slotState.hasSlot = false
  nextTick(() => {
    if (name) {
      slotInputEl.value?.renderTemplateWith({ label: '套餐名称', value: name })
    } else {
      slotInputEl.value?.setTemplate()
    }
    slotInputEl.value?.focus()
  })
}

function formatFileSize(size) {
  const n = Number(size) || 0
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function uploadStatusLabel(att) {
  switch (att?.uploadStatus) {
    case 'success':
      return '已添加'
    default:
      return ''
  }
}

function attachmentTitle(att) {
  const status = uploadStatusLabel(att)
  return status ? `${att.name}（${status}）` : att.name
}

function removeAttachment(idx) {
  attachments.value.splice(idx, 1)
}

function genAttachmentId() {
  return `att-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function showPasteHint(msg) {
  pasteHint.value = msg
  if (pasteHintTimer) clearTimeout(pasteHintTimer)
  pasteHintTimer = setTimeout(() => {
    pasteHint.value = ''
  }, 4000)
}

function addFiles(fileList) {
  const files = Array.from(fileList || []).filter(Boolean)
  if (!files.length) return false

  const plainFiles = []
  const images = []

  files.forEach((file) => {
    const isImage = file.type?.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/i.test(file.name || '')
    const rawFile = markRaw(file)
    const base = {
      id: genAttachmentId(),
      name: file.name || `文件_${Date.now()}`,
      size: file.size,
      file: rawFile,
      uploadStatus: 'success',
    }
    if (isImage) {
      images.push(file)
      const attachment = {
        ...base,
        type: 'image',
        name: file.name || `图片_${Date.now()}.png`,
        preview: null,
      }
      attachments.value.push(attachment)
      const reader = new FileReader()
      reader.onload = (event) => {
        const found = attachments.value.find((a) => a.id === attachment.id)
        if (found) found.preview = event.target?.result || null
      }
      reader.readAsDataURL(rawFile)
    } else {
      plainFiles.push(file)
      attachments.value.push({ ...base, type: 'file' })
    }
  })

  if (plainFiles.length) emit('file-upload', plainFiles)
  if (images.length) emit('image-upload', images)
  pasteHint.value = ''
  return true
}

const handlePaste = (e) => {
  if (props.disabled) return

  const cd = e.clipboardData
  const types = Array.from(cd?.types || [])

  // 尝试从 clipboardData 提取文件
  const files = []
  if (cd?.files?.length) {
    Array.from(cd.files).forEach((f) => files.push(f))
  }
  if (cd?.items?.length) {
    for (const item of cd.items) {
      if (item.kind === 'file') {
        const f = item.getAsFile()
        if (f) files.push(f)
      }
    }
  }

  if (files.length) {
    e.preventDefault()
    e.stopPropagation()
    addFiles(files)
    return
  }

  if (types.includes('Files')) {
    e.preventDefault()
    e.stopPropagation()
    showPasteHint('当前浏览器限制：复制的本地文件无法直接粘贴，请拖拽到输入框或点击上传按钮')
    return
  }
}

const onDocumentPasteCapture = (e) => {
  if (props.disabled || !isFocused.value) return
  const files = []
  const cd = e.clipboardData
  if (cd?.files?.length) Array.from(cd.files).forEach((f) => files.push(f))
  if (cd?.items?.length) {
    for (const item of cd.items) {
      if (item.kind === 'file') {
        const f = item.getAsFile()
        if (f) files.push(f)
      }
    }
  }
  if (!files.length) return
  e.preventDefault()
  e.stopPropagation()
  addFiles(files)
}

onMounted(() => {
  document.addEventListener('paste', onDocumentPasteCapture, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('paste', onDocumentPasteCapture, true)
  if (pasteHintTimer) clearTimeout(pasteHintTimer)
})

const onDragEnter = (e) => {
  if (props.disabled) return
  if (![...e.dataTransfer.types].includes('Files')) return
  dragDepth += 1
  isDragOver.value = true
}

const onDragOver = (e) => {
  if (props.disabled) return
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
}

const onDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) isDragOver.value = false
}

const onDrop = (e) => {
  dragDepth = 0
  isDragOver.value = false
  if (props.disabled) return
  const files = e.dataTransfer?.files
  if (files?.length) addFiles(files)
}

watch(() => props.modelValue, (val) => {
  if (val !== inputText.value) {
    inputText.value = val
    // 外部设置纯文本时，写入内联输入框
    if (val) slotInputEl.value?.insertText(val)
  }
})

const resetInput = () => {
  inputText.value = ''
  attachments.value = []
  slotSegments.value = []
  slotState.hasText = false
  slotState.hasSlot = false
  emit('update:modelValue', '')
  nextTick(() => {
    if (showCopySearch.value) {
      // 产品查询模板交互：发送后恢复默认模板，方便连续查询
      applyQueryTemplate()
    } else {
      slotInputEl.value?.clearContent()
      slotInputEl.value?.focus()
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
  const rawText = inputText.value.trim()
  const hasSlot = slotState.hasSlot
  // 从产品查询槽位取值：套餐类型 / 套餐名称 / 套餐编码
  const category = slotValueByLabel('套餐类型')
  const nameValue = slotValueByLabel('套餐名称')
  const codeValue = slotValueByLabel('套餐编码')
  const value = nameValue
  // 把输入框内的文字与槽位按序拼装为一段纯文本描述
  const mixedText = (slotSegments.value || [])
    .map((s) => (s.type === 'slot' ? `【${s.label}：${s.value || '未填'}】` : s.text))
    .join('')
    .replace(/\u200b/g, '')
    .trim()
  const text =
    rawText ||
    mixedText ||
    (hasSlot
      ? `查询${category || '个人主资费'}的套餐（名称：${nameValue}，编码：${codeValue}）`
      : '')
  if (!text && attachments.value.length === 0) return
  if (props.disabled) return
  if (!canSend.value) return

  const payloadAttachments = attachments.value.map((a) => ({
    id: a.id,
    type: a.type || 'file',
    name: a.name,
    size: a.size,
    preview: a.preview || null,
    file: a.file || null,
  }))
  emit('send', {
    text,
    attachments: payloadAttachments,
    modelConfig: { model: selectedModelId.value },
    tariffSkill: selectedTariffSkill.value,
    slots: (slotSegments.value || []).filter((s) => s.type === 'slot'),
    copySearch: selectedTariffSkill.value === 'copy' || hasSlot
      ? {
          category,
          value,
          type: category,
          // 兼容下游 streamStockCopyReply(search.code / search.name)
          code: codeValue,
          name: nameValue,
        }
      : null,
  })
  resetInput()
}

const handleQuickAction = (action) => emit('quick-action', action)

// ── 资费配置：三种配置方式技能（仅研发助手·资费配置场景显示）──
const tariffSkills = [
  { key: 'dialog', label: '对话式配置' },
  { key: 'batch', label: '文件导入批量配置' },
  { key: 'copy', label: '存量产品复制配置' },
]

const showTariffSkills = computed(
  () => props.assistantMode === 'rd' && props.activeScene === 'rd.scene2'
)

const selectedTariffSkill = ref('')

const toggleTariffSkill = (key) => {
  selectedTariffSkill.value = selectedTariffSkill.value === key ? '' : key
}

// 供父组件通过 ref 调用：外部（快捷回复/欢迎卡片）点击时直接勾选对应技能
const selectTariffSkill = (key) => {
  if (!tariffSkills.some((s) => s.key === key)) return
  selectedTariffSkill.value = key
}

// 监听外部选中信号（快捷回复点击 → 自动勾选对应技能）
const pendingCopyName = ref('')
watch(() => props.skillSignal, (sig) => {
  if (sig?.key === 'copy' && sig.name) {
    const wasActive = showCopySearch.value
    selectTariffSkill(sig.key)
    if (wasActive) {
      // 原本已在 copy 模式：showCopySearch watch 不会再触发，直接预填
      applyCopyTemplate(sig.name)
    } else {
      // 刚进入 copy 模式：交由下方 showCopySearch watch 预填，避免双重渲染
      pendingCopyName.value = sig.name
    }
  } else {
    if (sig?.key) selectTariffSkill(sig.key)
    pendingCopyName.value = ''
  }
})

// 进入产品查询模式 → 套用模板；退出 → 清空输入框
watch(showCopySearch, (active) => {
  if (active) {
    const name = pendingCopyName.value
    pendingCopyName.value = ''
    applyCopyTemplate(name)
  } else {
    slotInputEl.value?.clearContent()
    slotState.hasText = false
    slotState.hasSlot = false
  }
})

const focus = () => nextTick(() => slotInputEl.value?.focus())
const triggerFileUpload = () => fileInput.value?.click()
const triggerImageUpload = () => imageInput.value?.click()

const handleFileSelect = (e) => {
  addFiles(e.target.files || [])
  e.target.value = ''
}

const handleImageSelect = (e) => {
  addFiles(e.target.files || [])
  e.target.value = ''
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
      const file = markRaw(new File([blob], `录音_${Date.now()}.webm`, { type: 'audio/webm' }))
      const attachment = {
        id: genAttachmentId(),
        type: 'voice',
        name: file.name,
        size: file.size,
        file,
        blob: markRaw(blob),
        url: URL.createObjectURL(blob),
        duration: recordingTime.value,
        uploadStatus: 'success',
      }
      attachments.value.push(attachment)
      emit('voice-record', attachment)
      mediaRecorder = null
      audioChunks = []
    }
    mediaRecorder.stream.getTracks().forEach(track => track.stop())
  }
}

defineExpose({ focus, resetInput, selectTariffSkill })
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

/* 资费配置技能选择条 */
.tariff-skill-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.tariff-skill-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-right: 2px;
}

.tariff-skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.tariff-skill-chip:hover {
  background: var(--bg-tertiary);
  border-color: rgba(91, 124, 250, 0.35);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.tariff-skill-chip.selected {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-color: #3b82f6;
  color: #1d4ed8;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.16);
}

.tariff-skill-chip.selected .chip-icon {
  color: #3b82f6;
}

.chip-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin: 0 -4px 0 2px;
  border: none;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.chip-close:hover {
  background: rgba(59, 130, 246, 0.2);
  color: #1e40af;
}

.copy-slot-guide {
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.4;
  margin: 0 2px 6px;
}

.composer-panel {
  position: relative;
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

.composer-panel.drag-over {
  border-color: rgba(16, 185, 129, 0.55);
  background: rgba(236, 253, 245, 0.65);
  box-shadow: 0 0 0 1px rgba(16, 185, 129, 0.12);
}

.composer-panel.disabled { opacity: 0.92; }

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 280px;
  padding: 6px 8px;
  border-radius: 10px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
}

.attachment-chip.status-success {
  border-color: rgba(16, 185, 129, 0.35);
}

.att-thumb {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  object-fit: cover;
}

.att-icon {
  display: inline-flex;
  color: #059669;
}

.att-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.att-name {
  font-size: 12px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.att-status-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.att-size {
  font-size: 11px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.att-status {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-chip.status-success .att-status {
  color: #059669;
}

.att-remove {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}

.att-remove:hover:not(:disabled) { color: #dc2626; }
.att-remove:disabled { opacity: 0.4; cursor: not-allowed; }

.paste-hint {
  margin: 0 0 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #c2410c;
  font-size: 12px;
  line-height: 1.5;
}

.drop-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(236, 253, 245, 0.88);
  color: #047857;
  font-size: 13px;
  font-weight: 600;
  pointer-events: none;
  z-index: 2;
}

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

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-left,
.composer-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-left {
  min-width: 0;
  flex: 1;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer-right {
  margin-left: auto;
}

.skill-strip {
  display: flex;
  align-items: center;
  margin-top: 0;
}

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

.model-menu-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px 0 6px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
}
.skill-chip-icon {
  width: 16px; height: 16px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 999px; background: var(--bg-secondary); flex-shrink: 0;
}
.skill-chip-icon svg { display: block; }
.skill-chip-text {
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}
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

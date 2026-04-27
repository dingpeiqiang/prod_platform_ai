<template>
  <div class="form-panel" :class="{ collapsed: !visible }" :style="panelStyle">
    <!-- 拖拽调整宽度手柄 -->
    <div
      v-if="visible"
      class="resize-handle"
      @mousedown="startResize"
      @dblclick="resetWidth"
      title="拖拽调整宽度 | 双击恢复默认"
    >
      <div class="resize-grip">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <!-- 展开/收起按钮 -->
    <button class="collapse-btn" @click="togglePanel" :title="visible ? '收起表单' : '展开表单'">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline v-if="visible" points="15 18 9 12 15 6"/>
        <polyline v-else points="9 18 15 12 9 6"/>
      </svg>
    </button>

    <!-- 表单内容区 -->
    <div v-if="visible" class="panel-content">
      <!-- 标题栏 -->
      <div class="panel-header">
        <div class="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <polyline points="10 9 9 9 8 9"/>
          </svg>
          <span>{{ formTitle || '表单' }}</span>
        </div>
        <div class="panel-actions">
          <el-tag v-if="formId" type="info" size="small" class="form-id-tag">
            ID: {{ formId.slice(0, 8) }}...
          </el-tag>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!formSchema" class="empty-state">
        <div class="empty-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
        </div>
        <p class="empty-title">暂无表单</p>
        <p class="empty-hint">在左侧对话中描述你的需求，我会为你生成表单</p>
      </div>

      <!-- 表单区域 -->
      <div v-else class="form-area">
        <DynamicForm
          :schema="formSchema"
          :formId="formId"
          @submit="handleFormSubmit"
          @cancel="handleFormCancel"
          @field-change="handleFieldChange"
        />
      </div>
    </div>

    <!-- 收起时的占位提示 -->
    <div v-else class="collapsed-hint">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
      </svg>
      <span>表单</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import DynamicForm from './DynamicForm.vue'

const props = defineProps({
  formSchema: {
    type: Object,
    default: null
  },
  formId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['submit', 'cancel', 'field-change'])

// 面板可见性
const visible = ref(true)

// 面板宽度（支持拖拽调整）
const DEFAULT_WIDTH = 420
const MIN_WIDTH = 320
const MAX_WIDTH = 600
const panelWidth = ref(DEFAULT_WIDTH)

// 拖拽状态
const isResizing = ref(false)
const startX = ref(0)
const startWidth = ref(0)

const panelStyle = computed(() => {
  if (!visible.value) return {}
  return {
    width: `${panelWidth.value}px`,
    minWidth: `${panelWidth.value}px`,
    maxWidth: `${MAX_WIDTH}px`
  }
})

const formTitle = computed(() => {
  return props.formSchema?.formName || ''
})

const togglePanel = () => {
  visible.value = !visible.value
}

// 拖拽调整宽度
const startResize = (e) => {
  isResizing.value = true
  startX.value = e.clientX
  startWidth.value = panelWidth.value
  document.addEventListener('mousemove', doResize)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const doResize = (e) => {
  if (!isResizing.value) return
  const delta = startX.value - e.clientX  // 向左拖增宽，向右拖减宽
  const newWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth.value + delta))
  panelWidth.value = newWidth
}

const stopResize = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', doResize)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  // 保存宽度到 localStorage
  localStorage.setItem('form_panel_width', panelWidth.value)
}

const resetWidth = () => {
  panelWidth.value = DEFAULT_WIDTH
  localStorage.setItem('form_panel_width', DEFAULT_WIDTH)
}

// 表单事件
const handleFormSubmit = (formData) => {
  emit('submit', formData)
}

const handleFormCancel = () => {
  emit('cancel')
}

const handleFieldChange = (fieldCode, value) => {
  emit('field-change', fieldCode, value)
}

// 监听表单schema变化，自动展开面板
watch(() => props.formSchema, (newVal) => {
  if (newVal) {
    visible.value = true
  }
})

// 恢复保存的宽度
onMounted(() => {
  const savedWidth = localStorage.getItem('form_panel_width')
  if (savedWidth) {
    panelWidth.value = parseInt(savedWidth, 10)
  }
})
</script>

<style scoped>
.form-panel {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #f0f0f0;
  transition: width 0.15s ease;
  position: relative;
  overflow: hidden;
}

.form-panel:not(.collapsed) {
  width: 420px;
  min-width: 320px;
  max-width: 600px;
}

.form-panel.collapsed {
  width: 56px;
  min-width: 56px;
}

/* 拖拽调整宽度手柄 */
.resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8px;
  cursor: col-resize;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.resize-handle:hover {
  background: rgba(129, 140, 248, 0.15);
}

.resize-handle:active {
  background: rgba(129, 140, 248, 0.25);
}

.resize-grip {
  display: flex;
  flex-direction: column;
  gap: 3px;
  opacity: 0.3;
  transition: opacity 0.15s;
}

.resize-handle:hover .resize-grip {
  opacity: 0.6;
}

.resize-grip span {
  width: 2px;
  height: 2px;
  background: #666;
  border-radius: 50%;
}

/* 展开/收起按钮 */
.collapse-btn {
  position: absolute;
  left: 8px;
  top: 12px;
  width: 32px;
  height: 32px;
  background: #f5f5f5;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.15s;
  z-index: 10;
}

.collapse-btn:hover {
  background: #e8e8e8;
  color: #333;
}

.collapsed .collapse-btn {
  left: 50%;
  transform: translateX(-50%);
}

/* 内容区 */
.panel-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-left: 48px;
}

/* 标题栏 */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.panel-title svg {
  color: #818cf8;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-id-tag {
  font-size: 11px;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  text-align: center;
}

.empty-icon {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #f5f3ff, #ede9fe);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a78bfa;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  max-width: 280px;
}

/* 表单区域 */
.form-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.form-area::-webkit-scrollbar {
  width: 6px;
}

.form-area::-webkit-scrollbar-track {
  background: #f5f5f5;
}

.form-area::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

.form-area::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}

/* 收起时的提示 */
.collapsed-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
  color: #999;
  font-size: 12px;
  writing-mode: vertical-rl;
  text-orientation: mixed;
}

/* 移动端适配 */
@media (max-width: 1024px) {
  .form-panel:not(.collapsed) {
    width: 380px;
    min-width: 320px;
  }
}

@media (max-width: 768px) {
  .form-panel:not(.collapsed) {
    position: fixed;
    right: 0;
    top: 0;
    bottom: 0;
    width: 100%;
    min-width: 100%;
    max-width: 100%;
    z-index: 100;
    box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  }
}
</style>

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
          <span v-if="isPreviewMode" class="preview-badge">预览</span>
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
          :formSubmitted="formSubmitted"
          :formCancelled="formCancelled"
          @submit="handleFormSubmit"
          @cancel="handleFormCancel"
          @field-change="handleFieldChange"
          @ai-validation="(data) => emit('ai-validation', data)"
          @confirm-submit="(data) => emit('confirm-submit', data)"
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import DynamicForm from './DynamicForm.vue'

const props = defineProps({
  formSchema: {
    type: Object,
    default: null
  },
  formId: {
    type: String,
    default: ''
  },
  formSubmitted: {
    type: Boolean,
    default: false
  },
  formCancelled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submit', 'cancel', 'field-change', 'ai-validation', 'confirm-submit'])

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

const isPreviewMode = computed(() => {
  return !!props.formSchema?._preview
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

// 展开面板并滚动到表单
const scrollToForm = () => {
  if (!visible.value) {
    visible.value = true
  }
  // 等待面板展开动画完成后滚动
  nextTick(() => {
    const el = document.querySelector('.form-panel')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })
}

// 监听表单schema变化，自动展开面板
watch(() => props.formSchema, (newVal) => {
  if (newVal) {
    visible.value = true
  }
}, { immediate: true })

// 恢复保存的宽度
onMounted(() => {
  const savedWidth = localStorage.getItem('form_panel_width')
  if (savedWidth) {
    panelWidth.value = parseInt(savedWidth, 10)
  }
})

// 暴露方法给父组件调用
defineExpose({
  scrollToForm
})
</script>

<style scoped>
.form-panel {
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  border-left: 1px solid var(--border-light);
  transition: width 0.15s ease;
  position: relative;
  overflow: hidden;
}

.form-panel:not(.collapsed) {
  width: var(--form-panel-width);
  min-width: var(--form-panel-width-min);
  max-width: var(--form-panel-width-max);
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
  z-index: var(--z-dropdown);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}

.resize-handle:hover {
  background: var(--color-primary-100);
}

.resize-handle:active {
  background: var(--color-primary-200);
}

.resize-grip {
  display: flex;
  flex-direction: column;
  gap: 3px;
  opacity: 0.3;
  transition: opacity var(--transition-fast);
}

.resize-handle:hover .resize-grip {
  opacity: 0.6;
}

.resize-grip span {
  width: 2px;
  height: 2px;
  background: var(--color-gray-600);
  border-radius: 50%;
}

/* 展开/收起按钮 */
.collapse-btn {
  position: absolute;
  left: 8px;
  top: var(--space-3);
  width: 32px;
  height: 32px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-gray-600);
  transition: all var(--transition-fast);
  z-index: var(--z-dropdown);
}

.collapse-btn:hover {
  background: var(--border-default);
  color: var(--text-primary);
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
  padding: var(--space-3-5) var(--space-4);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.panel-title svg {
  color: var(--color-primary-400);
}

.preview-badge {
  font-size: 11px;
  padding: 2px 8px;
  background: linear-gradient(135deg, var(--color-primary-100), var(--color-primary-50));
  color: var(--color-primary-700);
  border-radius: var(--radius-full);
  font-weight: var(--font-weight-medium);
  margin-left: var(--space-1-5);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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
  padding: var(--space-10) var(--space-6);
  text-align: center;
}

.empty-icon {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, var(--color-primary-50), var(--color-primary-100));
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary-400);
  margin-bottom: var(--space-4);
}

.empty-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin-bottom: var(--space-2);
}

.empty-hint {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
  max-width: 280px;
}

/* 表单区域 */
.form-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
}

.form-area::-webkit-scrollbar {
  width: 6px;
}

.form-area::-webkit-scrollbar-track {
  background: var(--bg-secondary);
}

.form-area::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: var(--radius-sm);
}

.form-area::-webkit-scrollbar-thumb:hover {
  background: var(--border-strong);
}

/* 收起时的提示 */
.collapsed-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  writing-mode: vertical-rl;
  text-orientation: mixed;
}

/* 移动端适配 */
@media (max-width: 1024px) {
  .form-panel:not(.collapsed) {
    width: 380px;
    min-width: var(--form-panel-width-min);
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
    z-index: var(--z-modal);
    box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  }
}
</style>

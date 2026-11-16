<template>
  <div class="detail-panel" :style="{ width: panelWidth + 'px' }">
    <div
      class="detail-panel-resizer"
      @mousedown="startResize"
      @touchstart.passive="startResize"
    >
      <div class="resizer-line"></div>
    </div>
    <div class="detail-panel-header">
      <div class="detail-panel-title-wrap">
        <svg class="detail-panel-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <span class="detail-panel-title">{{ title }}</span>
      </div>
      <div class="detail-panel-actions">
        <button class="detail-panel-btn" @click="downloadContent" title="下载">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
        </button>
        <button class="detail-panel-btn" @click="$emit('close')" title="关闭">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    <div class="detail-panel-body">
      <!-- 结构化 blocks 渲染 -->
      <template v-if="blocks && blocks.length">
        <template v-for="(block, idx) in blocks" :key="idx">
          <!-- Markdown 文本块 -->
          <div
            v-if="block.type === 'markdown'"
            class="markdown-body"
            v-html="renderMarkdown(block.data)"
          />
          <!-- ECharts 图表块 -->
          <div v-else-if="block.type === 'chart'" class="chart-block">
            <div class="chart-block-title" v-if="block.title">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 3v18h18"/>
                <path d="M7 14l4-4 4 2 5-6"/>
              </svg>
              {{ block.title }}
            </div>
            <EChartsChart :option="block.data" :height="block.height || 320" />
          </div>
          <!-- 产品画像卡片块 -->
          <ProductProfileCard
            v-else-if="block.type === 'profile-card'"
            :data="block.data"
          />
          <!-- 产品运营视图块 -->
          <div v-else-if="block.type === 'ops-dashboard'" class="ops-dashboard-block">
            <OpsView
              :initial-drill="block.drill"
              :initial-tab="block.traffic ? 'traffic' : 'product'"
              @update:drill="$emit('update:drill', $event)"
              @open-rootcause="$emit('open-rootcause', $event)"
              @ask-ai="$emit('ask-ai', $event)"
            />
          </div>
        </template>
      </template>
      <!-- 兼容旧版纯 markdown 字符串 -->
      <div v-else class="markdown-body" v-html="renderMarkdown(content)"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import EChartsChart from './EChartsChart.vue'
import ProductProfileCard from './ProductProfileCard.vue'
import OpsView from './OpsView.vue'

const props = defineProps({
  title: { type: String, default: '详情' },
  content: { type: String, default: '' },
  blocks: { type: Array, default: () => [] },
})

defineEmits(['close', 'open-rootcause', 'update:drill', 'ask-ai'])

const MIN_WIDTH = 360
const MAX_WIDTH = 1200
const DEFAULT_WIDTH = 520
const OPS_WIDTH = 900

// 运营视图（内含 ops-dashboard 块）默认以更宽的宽度平铺展示，其余详情保持常规宽度
const isOpsPanel = computed(() =>
  (props.blocks || []).some((b) => b.type === 'ops-dashboard')
)

const panelWidth = ref(isOpsPanel.value ? OPS_WIDTH : DEFAULT_WIDTH)
const resizing = ref(false)

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

const startResize = (e) => {
  e.preventDefault()
  resizing.value = true
  const startX = (e.touches ? e.touches[0].clientX : e.clientX)
  const startWidth = panelWidth.value

  const onMove = (ev) => {
    const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX
    const delta = startX - clientX
    const newWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta))
    panelWidth.value = newWidth
  }

  const onUp = () => {
    resizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onUp)
}

onUnmounted(() => {
  resizing.value = false
})

const downloadContent = () => {
  try {
    let text = props.content || ''
    if (props.blocks && props.blocks.length) {
      text = props.blocks
        .map((b) => {
          if (b.type === 'markdown') return b.data
          if (b.type === 'chart') return `\n[图表：${b.title || '数据可视化'}]\n`
          if (b.type === 'profile-card') return `\n[产品画像卡片]\n`
          return ''
        })
        .join('\n\n')
    }
    const filename = (props.title || '详情').replace(/[\\/:*?"<>|]/g, '_') + '.md'
    const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage({ message: '下载成功', type: 'success', duration: 1500 })
  } catch {
    ElMessage.error('下载失败')
  }
}
</script>

<style scoped>
.detail-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
  position: relative;
}

/* 拖拽调宽条 */
.detail-panel-resizer {
  position: absolute;
  top: 0;
  left: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resizer-line {
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.15s ease;
  border-radius: 1px;
}

.detail-panel-resizer:hover .resizer-line {
  background: #3b82f6;
}

.detail-panel-resizer:active .resizer-line,
.detail-panel-resizer.dragging .resizer-line {
  background: #2563eb;
}

.detail-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.detail-panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.detail-panel-icon {
  color: #2563eb;
  flex-shrink: 0;
}

.detail-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.detail-panel-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.detail-panel-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.detail-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.detail-panel-body::-webkit-scrollbar {
  width: 6px;
}

.detail-panel-body::-webkit-scrollbar-track {
  background: transparent;
}

.detail-panel-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

/* 图表块 */
.chart-block {
  margin: 14px 0;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.chart-block-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.chart-block-title svg {
  color: #3b82f6;
}

/* 产品运营视图块 */
.ops-dashboard-block {
  padding: 0;
  margin: 0 0 14px;
}

/* markdown 渲染样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 17px; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(h4) { font-size: 14px; }
.markdown-body :deep(p) { margin: 8px 0; font-size: 13px; line-height: 1.7; color: #334155; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 8px 0; padding-left: 20px; font-size: 13px; color: #334155; }
.markdown-body :deep(li) { margin: 4px 0; line-height: 1.7; }
.markdown-body :deep(strong) { color: #1e293b; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  margin: 10px 0;
  padding: 8px 14px;
  color: #475569;
  background: rgba(59, 130, 246, 0.04);
  border-radius: 0 6px 6px 0;
  font-size: 13px;
}
.markdown-body :deep(blockquote p) { margin: 4px 0; }
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 12px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #1e293b;
}
.markdown-body :deep(td) { color: #475569; }
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 16px 0;
}
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, monospace;
}
</style>

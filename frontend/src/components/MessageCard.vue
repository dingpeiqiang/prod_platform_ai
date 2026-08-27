<template>
  <div class="message-card" :class="{ 'is-streaming': streaming }">
    <!-- Markdown 渲染内容 -->
    <div 
      v-if="renderedHtml" 
      class="message-card-content markdown-body"
      v-html="renderedHtml"
    />
    
    <!-- 代码块（从内容中提取） -->
    <div 
      v-for="(block, idx) in codeBlocks" 
      :key="'code-' + idx" 
      class="code-block-wrapper"
    >
      <div class="code-block-header">
        <span class="code-lang">{{ block.lang || 'text' }}</span>
        <div class="code-actions">
          <button class="code-action-btn" @click="copyCode(block.code)" title="复制代码">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            {{ copiedIdx === idx ? '已复制' : '复制' }}
          </button>
          <button 
            v-if="block.lines > 10" 
            class="code-action-btn" 
            @click="toggleCodeExpand(idx)"
          >
            {{ expandedCodes[idx] ? '收起' : '展开全部' }}
          </button>
        </div>
      </div>
      <pre class="code-block-pre"><code :class="'lang-' + (block.lang || 'text')">{{ block.code }}</code></pre>
    </div>

    <!-- 表格（从内容中提取） -->
    <div 
      v-for="(tbl, idx) in tables" 
      :key="'table-' + idx" 
      class="table-wrapper"
    >
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th v-for="(h, hi) in tbl.headers" :key="hi">{{ h }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in tbl.rows" :key="ri">
              <td v-for="(cell, ci) in row" :key="ci" v-html="renderCell(cell)" />
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-footer">
        <span class="table-count">共 {{ tbl.rows.length }} 行</span>
        <button class="table-copy-btn" @click="copyTable(tbl)" title="复制表格">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
          复制表格
        </button>
      </div>
    </div>

    <!-- 空内容提示 -->
    <div v-if="!renderedHtml && !codeBlocks.length && !tables.length && !streaming" class="empty-content">
      暂无内容
    </div>

    <!-- 流式加载动画 -->
    <div v-if="streaming && !renderedHtml" class="typing-indicator">
      <span></span><span></span><span></span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'

const props = defineProps({
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false }
})

const copiedIdx = ref(-1)
const expandedCodes = ref({})

// 解析内容：提取代码块和表格
const parsedContent = computed(() => {
  const text = props.content || ''
  const codeBlocks = []
  const tables = []
  
  // 提取代码块
  const codeRegex = /```(\w+)?\n([\s\S]*?)```/g
  let match
  let cleanText = text
  
  while ((match = codeRegex.exec(text)) !== null) {
    const lang = match[1] || 'text'
    const code = match[2].trim()
    const lines = code.split('\n').length
    codeBlocks.push({ lang, code, lines })
    cleanText = cleanText.replace(match[0], '')
  }
  
  // 提取表格（Markdown 格式）
  const tableRegex = /(\|.+\|)\n(\|[-| :]+\|)\n((\|.+\|\n?)*)/g
  while ((match = tableRegex.exec(cleanText)) !== null) {
    const headerRow = match[1]
    const bodyRows = match[3].trim().split('\n')
    
    const headers = headerRow.split('|').filter(h => h.trim()).map(h => h.trim())
    const rows = bodyRows.map(row => 
      row.split('|').filter(c => c.trim()).map(c => c.trim())
    )
    
    if (headers.length > 0 && rows.length > 0) {
      tables.push({ headers, rows })
    }
    cleanText = cleanText.replace(match[0], '')
  }
  
  return { cleanText, codeBlocks, tables }
})

const renderedHtml = computed(() => {
  const text = parsedContent.value.cleanText
  if (!text.trim()) return ''
  
  try {
    if (typeof marked.parse === 'function') {
      let html = marked.parse(text)
      // 安全过滤
      html = html.replace(/<script[^>]*>.*?<\/script>/gi, '')
      html = html.replace(/\son\w+\s*=\s*["'][^"']*["']/gi, '')
      return html
    }
    // 降级处理
    return text
      .replace(/\n/g, '<br/>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
  } catch {
    return text
  }
})

const codeBlocks = computed(() => parsedContent.value.codeBlocks)
const tables = computed(() => parsedContent.value.tables)

const renderCell = (cell) => {
  // 支持简单的 markdown 格式
  return cell
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
}

const copyCode = async (code, idx) => {
  try {
    await navigator.clipboard.writeText(code)
    copiedIdx.value = idx
    ElMessage({ message: '代码已复制', type: 'success', duration: 1500 })
    setTimeout(() => { copiedIdx.value = -1 }, 2000)
  } catch {
    ElMessage.error('复制失败')
  }
}

const toggleCodeExpand = (idx) => {
  expandedCodes.value = {
    ...expandedCodes.value,
    [idx]: !expandedCodes.value[idx]
  }
}

const copyTable = async (tbl) => {
  try {
    const text = [tbl.headers.join('\t'), ...tbl.rows.map(r => r.join('\t'))].join('\n')
    await navigator.clipboard.writeText(text)
    ElMessage({ message: '表格已复制', type: 'success', duration: 1500 })
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.message-card {
  width: 100%;
}

.message-card-content {
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

/* Markdown 正文样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 18px; }
.markdown-body :deep(h3) { font-size: 16px; }

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-body :deep(em) {
  font-style: italic;
  color: var(--text-secondary);
}

.markdown-body :deep(code) {
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
}

.markdown-body :deep(a) {
  color: #3b82f6;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  padding: 8px 16px;
  margin: 12px 0;
  background: rgba(59, 130, 246, 0.04);
  border-radius: 0 8px 8px 0;
  color: var(--text-secondary);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-light);
  margin: 16px 0;
}

/* 代码块样式 */
.code-block-wrapper {
  margin: 12px 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #1e293b;
}

.code-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #0f172a;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.code-lang {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.code-actions {
  display: flex;
  gap: 6px;
}

.code-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.code-action-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #e2e8f0;
}

.code-block-pre {
  margin: 0;
  padding: 14px 18px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
}

.code-block-pre code {
  background: transparent !important;
  color: inherit !important;
  padding: 0 !important;
  border-radius: 0 !important;
}

/* 表格样式 */
.table-wrapper {
  margin: 12px 0;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  background: #f8fafc;
  padding: 10px 14px;
  text-align: left;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 2px solid var(--border-default);
  white-space: nowrap;
}

.data-table td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.data-table tr:last-child td {
  border-bottom: none;
}

.data-table tr:hover td {
  background: rgba(59, 130, 246, 0.03);
}

.data-table :deep(code) {
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
}

.data-table :deep(strong) {
  font-weight: 600;
  color: var(--text-primary);
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #f8fafc;
  border-top: 1px solid var(--border-light);
}

.table-count {
  font-size: 12px;
  color: var(--text-tertiary);
}

.table-copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.table-copy-btn:hover {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

/* 空内容提示 */
.empty-content {
  color: var(--text-tertiary);
  font-size: 14px;
  padding: 8px 0;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 6px 0;
  align-items: center;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.4; }
  40% { transform: scale(1.1); opacity: 1; }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .data-table th {
    background: #1e293b;
    color: #e2e8f0;
  }
  
  .data-table td {
    color: #cbd5e1;
  }
  
  .table-footer {
    background: #1e293b;
  }
}
</style>

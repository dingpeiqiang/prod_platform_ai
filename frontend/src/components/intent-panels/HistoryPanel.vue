<template>
  <div class="history-mgmt-panel">
    <!-- 导入入口 -->
    <template v-if="historyData.type === 'import_entry'">
      <DataImportEntry
        :formCode="historyData.formCode"
        :formName="historyData.formName"
        :message="historyData.message"
        @import-complete="handleImportComplete"
      />
    </template>

    <!-- 导出结果下载 -->
    <template v-else-if="historyData.content && historyData.filename">
      <div class="history-mgmt-header">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <span>📤 导出完成</span>
      </div>
      <div class="export-result">
        <p>文件名: <strong>{{ historyData.filename }}</strong></p>
        <p class="export-hint">文件已准备好，点击下方按钮下载</p>
      </div>
      <button class="gen-data-btn" @click="downloadExport">📥 下载文件</button>
    </template>

    <!-- 查询结果列表 -->
    <template v-else-if="historyData.records && historyData.records.length > 0">
      <div class="history-mgmt-header">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>🔍 查询结果</span>
      </div>
      <div class="query-stats">
        共 <strong>{{ historyData.total }}</strong> 条记录，
        第 {{ historyData.page }} / {{ historyData.total_pages }} 页
      </div>
      <div class="record-list">
        <div v-for="record in displayRecords" :key="record.id" class="record-item">
          <div class="record-header">
            <span class="record-id">#{{ record.id }}</span>
            <span class="record-time">{{ record.submitted_at || '-' }}</span>
          </div>
          <div class="record-data">
            <span v-for="(value, key) in displayFields(record)" :key="key" class="record-field">
              <span class="field-key">{{ key }}:</span>
              <span class="field-value">{{ value || '-' }}</span>
            </span>
          </div>
        </div>
      </div>
      <div class="export-actions">
        <button class="gen-data-btn outline" @click="exportData('jsonl')">📤 导出JSONL</button>
        <button class="gen-data-btn outline" @click="exportData('csv')">📊 导出CSV</button>
      </div>
    </template>

    <!-- 分析结果 -->
    <template v-else-if="historyData.action === 'analyze'">
      <div class="history-mgmt-header">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <path d="M21.21 15.89A10 10 0 1 1 8 2.83"/>
          <path d="M22 12A10 10 0 0 0 12 2v10z"/>
        </svg>
        <span>📊 数据维护：{{ historyData.formName || historyData.formCode || '' }}</span>
      </div>
      <div class="history-score" v-if="historyData.qualityScore !== undefined">
        <div class="score-ring" :class="scoreLevel(historyData.qualityScore)">
          <span>{{ historyData.qualityScore }}</span>
        </div>
        <span class="score-label">{{ scoreLabel(historyData.qualityScore) }}</span>
      </div>
      <div v-if="historyData.totalRecords !== undefined" class="stat-row">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#666" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>
        总记录: <strong>{{ historyData.totalRecords }}</strong>
      </div>
      <div v-if="historyData.hasData === false" class="no-data-tip">
        暂无历史数据，请导入历史数据
      </div>
      <div v-if="historyData.recommendations?.length" class="recommend-list">
        <div class="recommend-title">改进建议：</div>
        <div v-for="(rec, i) in historyData.recommendations" :key="i" class="recommend-item">
          💡 {{ rec }}
        </div>
      </div>
    </template>

    <!-- 导入结果报告 -->
    <template v-else-if="historyData.action === 'import' && historyData.success !== undefined">
      <div class="history-mgmt-header">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <path d="M21.21 15.89A10 10 0 1 1 8 2.83"/>
          <path d="M22 12A10 10 0 0 0 12 2v10z"/>
        </svg>
        <span>📊 数据维护：{{ historyData.formName || historyData.formCode || '' }}</span>
      </div>
      <div class="import-done">
        ✅ 已导入 <strong>{{ historyData.importedCount || historyData.totalImported }}</strong> 条记录
      </div>
      <div v-if="historyData.fieldStats" class="field-dist">
        <div class="dist-title">字段分布（Top5）:</div>
        <div v-for="(info, fc) in topFieldStats(historyData.fieldStats)" :key="fc" class="dist-item">
          <span class="fc-name">{{ fc }}</span>
          <span class="fc-count">{{ info.distinctValues }} 种不同值</span>
        </div>
      </div>
      <button class="gen-data-btn" @click="$emit('analyze')">🔍 再次分析</button>
    </template>

    <!-- 状态查询结果 -->
    <template v-else>
      <div class="history-mgmt-header">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3498db" stroke-width="2">
          <path d="M21.21 15.89A10 10 0 1 1 8 2.83"/>
          <path d="M22 12A10 10 0 0 0 12 2v10z"/>
        </svg>
        <span>📊 数据维护：{{ historyData.formName || historyData.formCode || '' }}</span>
      </div>
      <div v-if="historyData.totalRecords > 0">
        <div class="stat-row">
          <strong>{{ historyData.totalRecords }}</strong> 条历史记录
        </div>
        <div v-for="(info, fc) in topFieldStats(historyData.fieldStats)" :key="fc" class="stat-detail">
          {{ fc }}: {{ info.distinctValues }} 种值
        </div>
      </div>
      <div v-else class="no-data-tip">暂无数据，请导入历史数据</div>
      <div v-if="historyData.totalRecords > 0" style="margin-top:8px; display:flex; gap:8px;">
        <button class="gen-data-btn outline" @click="exportData('jsonl')">📤 导出JSONL</button>
        <button class="gen-data-btn outline" @click="exportData('csv')">📊 导出CSV</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import DataImportEntry from '../DataImportEntry.vue'

const props = defineProps({
  historyData: { type: Object, required: true },
  importing: Boolean,
  importResult: Object
})

const emit = defineEmits(['import', 'analyze', 'export'])

const handleImportComplete = (result) => {
  console.log('[HistoryPanel] 导入完成:', result)
}

// 下载导出文件
const downloadExport = () => {
  if (!props.historyData.content || !props.historyData.filename) return

  const content = props.historyData.content
  const filename = props.historyData.filename
  const contentType = props.historyData.content_type || 'text/plain'

  const blob = new Blob([content], { type: contentType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// 导出数据
const exportData = (format) => {
  emit('export', { format })
}

// 显示记录中的字段
const displayFields = (record) => {
  if (!record.data) return {}
  const fields = {}
  const keys = Object.keys(record.data).slice(0, 5) // 最多显示5个字段
  for (const key of keys) {
    let value = record.data[key]
    if (Array.isArray(value)) value = value.join(', ')
    if (typeof value === 'object') value = JSON.stringify(value).slice(0, 30)
    fields[key] = value
  }
  return fields
}

const displayRecords = props.historyData.records || []

const scoreLevel = (score) => {
  if (score >= 80) return 'level-good'
  if (score >= 60) return 'level-ok'
  return 'level-bad'
}

const scoreLabel = (score) => {
  if (score >= 80) return '优秀'
  if (score >= 60) return '一般'
  return '待改进'
}

const topFieldStats = (fieldStats) => {
  if (!fieldStats) return {}
  const entries = Object.entries(fieldStats).sort((a, b) => (b[1].distinctValues || 0) - (a[1].distinctValues || 0))
  return Object.fromEntries(entries.slice(0, 5))
}
</script>

<style scoped>
.history-mgmt-panel { padding: 12px; }
.history-mgmt-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}
.history-score {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.score-ring {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
}
.score-ring.level-good { background: var(--color-success-100); color: var(--color-success-600); }
.score-ring.level-ok { background: var(--color-warning-100); color: var(--color-warning-600); }
.score-ring.level-bad { background: var(--color-error-100); color: var(--color-error-600); }
.score-label { color: var(--text-tertiary); font-size: 13px; }
.stat-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}
.no-data-tip {
  color: var(--text-tertiary);
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}
.recommend-list { margin-top: 8px; }
.recommend-title { font-size: 12px; color: var(--text-tertiary); margin-bottom: 6px; }
.recommend-item { font-size: 12.5px; color: var(--text-secondary); padding: 4px 0; }
.import-done { font-size: 14px; color: var(--text-primary); margin-bottom: 12px; }
.field-dist { margin-top: 12px; }
.dist-title { font-size: 12px; color: var(--text-tertiary); margin-bottom: 8px; }
.dist-item {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 4px 0;
  border-bottom: 1px solid var(--border-light);
}
.fc-name { color: var(--text-secondary); }
.fc-count { color: var(--text-tertiary); font-size: 12px; }
.stat-detail { font-size: 12.5px; color: var(--text-secondary); padding: 3px 0; }
.gen-data-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #409eff;
  color: var(--text-inverse);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  margin-top: 8px;
}
.gen-data-btn.outline {
  background: var(--bg-elevated);
  color: #409eff;
  border: 1px solid #409eff;
}
.gen-data-btn:hover { opacity: 0.85; }

/* 查询结果样式 */
.query-stats { font-size: 12px; color: var(--text-secondary); margin-bottom: 10px; }
.record-list { max-height: 300px; overflow-y: auto; margin-bottom: 12px; }
.record-item {
  padding: 10px;
  background: var(--bg-secondary);
  border-radius: 6px;
  margin-bottom: 8px;
}
.record-header { display: flex; justify-content: space-between; margin-bottom: 6px; }
.record-id { font-weight: 600; color: #409eff; font-size: 12px; }
.record-time { color: var(--text-secondary); font-size: 11px; }
.record-data { display: flex; flex-wrap: wrap; gap: 6px; }
.record-field { font-size: 11px; background: var(--bg-elevated); padding: 2px 6px; border-radius: 3px; }
.field-key { color: var(--text-secondary); }
.field-value { color: var(--text-primary); margin-left: 2px; }
.export-actions { display: flex; gap: 8px; margin-top: 8px; }

/* 导出结果样式 */
.export-result { background: var(--bg-secondary); padding: 12px; border-radius: 6px; margin-bottom: 12px; }
.export-result p { margin: 4px 0; font-size: 13px; color: var(--text-secondary); }
.export-hint { color: var(--text-tertiary) !important; font-size: 12px !important; }
</style>

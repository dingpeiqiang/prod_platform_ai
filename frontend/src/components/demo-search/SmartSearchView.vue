<template>
  <div class="smart-search-view">
    <div class="view-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <div class="header-title">
        <h1>智查・一键复制</h1>
        <p>存量配置复用 — 语义检索历史方案,一键克隆差异修改</p>
      </div>
    </div>

    <div class="search-section">
      <div class="search-input-wrapper">
        <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input
          v-model="searchQuery"
          class="search-input"
          placeholder="输入自然语言查询,如:查找面向高校学生、200M宽带+手机号融合的历史在售套餐"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" :disabled="searching || !searchQuery.trim()" @click="handleSearch">
          {{ searching ? '检索中...' : '语义检索' }}
        </button>
      </div>
    </div>

    <div v-if="searchResults.length > 0" class="results-section">
      <div class="section-title">
        <span>搜索结果 ({{ searchResults.length }})</span>
        <span class="hint">按相似度排序,点击「一键复制」克隆配置</span>
      </div>
      <div class="result-list">
        <div
          v-for="result in searchResults"
          :key="result.package.package_id"
          class="result-card"
          :class="{ active: selectedResultId === result.package.package_id }"
          @click="selectResult(result)"
        >
          <div class="result-header">
            <div class="result-name">{{ result.package.package_name }}</div>
            <div class="similarity-badge" :class="getSimilarityClass(result.similarity)">
              {{ result.similarity }}% 匹配
            </div>
          </div>
          <div class="result-meta">
            <span class="meta-tag">{{ result.package.bandwidth }}</span>
            <span class="meta-tag">{{ result.package.bind_phone ? '融合套餐' : '单宽带' }}</span>
            <span class="meta-tag">月费 {{ result.package.monthly_fee }}元</span>
            <span class="meta-tag">{{ result.package.target_audience }}</span>
            <span class="meta-tag status-tag" :class="result.package.status === '在售' ? 'active' : 'inactive'">
              {{ result.package.status }}
            </span>
          </div>
          <div v-if="result.matched_fields.length > 0" class="matched-fields">
            <span class="matched-label">匹配字段:</span>
            <span v-for="f in result.matched_fields" :key="f" class="matched-tag">{{ f }}</span>
          </div>
          <div class="result-actions">
            <button class="action-btn secondary" @click.stop="viewDetail(result)">查看详情</button>
            <button class="action-btn primary" @click.stop="clonePackage(result)">一键复制</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="clonedConfig" class="diff-section">
      <div class="section-title">
        <span>配置差异预览与编辑</span>
        <button class="close-diff-btn" @click="closeDiff">✕ 关闭</button>
      </div>
      <div class="diff-container">
        <div class="diff-panel source-panel">
          <div class="panel-header">源配置 (只读)</div>
          <div class="panel-body">
            <div v-for="field in diffFields" :key="field.field" class="diff-field">
              <label>{{ field.field_label }}</label>
              <div class="field-value source">{{ formatValue(field.source_value) }}</div>
            </div>
          </div>
        </div>
        <div class="diff-arrow">→</div>
        <div class="diff-panel target-panel">
          <div class="panel-header">新配置 (可编辑)</div>
          <div class="panel-body">
            <div v-for="field in diffFields" :key="field.field" class="diff-field">
              <label>{{ field.field_label }}</label>
              <input
                v-if="field.editable"
                v-model="editValues[field.field]"
                class="field-input"
                :class="{ changed: isChanged(field) }"
              />
              <div v-else class="field-value">{{ formatValue(field.target_value) }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="diff-actions">
        <button class="action-btn secondary" @click="validateConfig" :disabled="submitting">
          {{ submitting ? '校验中...' : '本体校验' }}
        </button>
        <button class="action-btn primary" @click="submitConfig" :disabled="submitting">
          {{ submitting ? '提交中...' : '提交生成' }}
        </button>
      </div>
    </div>

    <div v-if="validationResult" class="validation-section">
      <div class="validation-header" :class="validationResult.valid ? 'valid' : 'invalid'">
        <span>{{ validationResult.valid ? '✅ 校验通过' : '❌ 校验失败' }}</span>
      </div>
      <div v-if="validationResult.errors.length > 0" class="validation-list">
        <div v-for="(err, i) in validationResult.errors" :key="'e'+i" class="validation-item error">
          {{ err.message }}
        </div>
      </div>
      <div v-if="validationResult.warnings.length > 0" class="validation-list">
        <div v-for="(warn, i) in validationResult.warnings" :key="'w'+i" class="validation-item warning">
          {{ warn.message }}
        </div>
      </div>
    </div>

    <div v-if="submitResult" class="submit-result">
      <div class="result-icon">✅</div>
      <div class="result-text">
        <div class="result-title">配置生成成功</div>
        <div class="result-detail">单据号: {{ submitResult.record_id }}</div>
        <div class="result-detail">套餐: {{ submitResult.package_name }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { demoSearchApi } from '@/services/demoApi.js'

const emit = defineEmits(['go-back'])

const searchQuery = ref('')
const searching = ref(false)
const searchResults = ref([])
const selectedResultId = ref(null)
const clonedConfig = ref(null)
const diffFields = ref([])
const editValues = reactive({})
const submitting = ref(false)
const validationResult = ref(null)
const submitResult = ref(null)

const handleSearch = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  searchResults.value = []
  clonedConfig.value = null
  validationResult.value = null
  submitResult.value = null

  try {
    const res = await demoSearchApi.semanticSearch(searchQuery.value)
    searchResults.value = res.results || []
  } catch (e) {
    console.error('搜索失败:', e)
    alert('搜索失败: ' + e.message)
  } finally {
    searching.value = false
  }
}

const selectResult = (result) => {
  selectedResultId.value = result.package.package_id
}

const viewDetail = async (result) => {
  try {
    const res = await demoSearchApi.getConfig(result.package.package_id)
    alert(JSON.stringify(res.config, null, 2))
  } catch (e) {
    alert('获取详情失败: ' + e.message)
  }
}

const clonePackage = async (result) => {
  try {
    const res = await demoSearchApi.clone(result.package.package_id)
    clonedConfig.value = res.cloned_config

    const diffRes = await demoSearchApi.diff(result.package.package_id, res.cloned_config.package_id)
    diffFields.value = diffRes.diff_fields

    diffFields.value.forEach(f => {
      editValues[f.field] = f.target_value
    })

    validationResult.value = null
    submitResult.value = null
  } catch (e) {
    alert('克隆失败: ' + e.message)
  }
}

const closeDiff = () => {
  clonedConfig.value = null
  diffFields.value = []
  validationResult.value = null
  submitResult.value = null
}

const isChanged = (field) => {
  return JSON.stringify(editValues[field.field]) !== JSON.stringify(field.target_value)
}

const formatValue = (val) => {
  if (val === null || val === undefined || val === '') return '—'
  if (typeof val === 'boolean') return val ? '是' : '否'
  return val
}

const validateConfig = async () => {
  if (!clonedConfig.value) return
  submitting.value = true
  validationResult.value = null

  try {
    const modifications = {}
    diffFields.value.forEach(f => {
      if (isChanged(f)) {
        modifications[f.field] = editValues[f.field]
      }
    })

    const res = await demoSearchApi.submit(clonedConfig.value.package_id, modifications)
    validationResult.value = res.validation
  } catch (e) {
    alert('校验失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

const submitConfig = async () => {
  if (!clonedConfig.value) return
  submitting.value = true

  try {
    const modifications = {}
    diffFields.value.forEach(f => {
      if (isChanged(f)) {
        modifications[f.field] = editValues[f.field]
      }
    })

    const res = await demoSearchApi.submit(clonedConfig.value.package_id, modifications)
    if (res.success) {
      submitResult.value = res
      validationResult.value = res.validation
    } else {
      validationResult.value = res.validation
      alert(res.message)
    }
  } catch (e) {
    alert('提交失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

const getSimilarityClass = (score) => {
  if (score >= 90) return 'high'
  if (score >= 70) return 'medium'
  return 'low'
}
</script>

<style scoped>
.smart-search-view {
  height: 100%;
  overflow-y: auto;
  background: var(--bg-secondary, #f5f7fa);
  padding: 24px 32px;
}

.view-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  color: var(--text-secondary, #666);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: var(--bg-primary, #fff);
  color: #3b82f6;
  border-color: #3b82f6;
}

.header-title h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: var(--text-primary, #1a1a1a);
}

.header-title p {
  font-size: 13px;
  color: var(--text-tertiary, #999);
  margin: 0;
}

.search-section {
  margin-bottom: 24px;
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 8px 8px 8px 16px;
  transition: border-color 0.2s;
}

.search-input-wrapper:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.search-icon {
  color: var(--text-tertiary, #999);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  background: transparent;
  color: var(--text-primary, #1a1a1a);
  padding: 8px 0;
}

.search-btn {
  padding: 10px 24px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.search-btn:hover:not(:disabled) {
  background: #2563eb;
}

.search-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
  margin-bottom: 16px;
}

.section-title .hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary, #999);
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.result-card {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.result-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
}

.result-card.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.02);
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.result-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}

.similarity-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.similarity-badge.high { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.similarity-badge.medium { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.similarity-badge.low { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.meta-tag {
  padding: 3px 10px;
  background: var(--bg-tertiary, #f0f2f5);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text-secondary, #666);
}

.status-tag.active { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.status-tag.inactive { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.matched-fields {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.matched-label {
  font-size: 12px;
  color: var(--text-tertiary, #999);
}

.matched-tag {
  padding: 2px 8px;
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
  border-radius: 8px;
  font-size: 11px;
}

.result-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.action-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.primary {
  background: #3b82f6;
  color: #fff;
}

.action-btn.primary:hover:not(:disabled) {
  background: #2563eb;
}

.action-btn.secondary {
  background: var(--bg-tertiary, #f0f2f5);
  color: var(--text-secondary, #666);
}

.action-btn.secondary:hover:not(:disabled) {
  background: var(--border-color, #e0e0e0);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.diff-section {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.close-diff-btn {
  background: none;
  border: none;
  color: var(--text-tertiary, #999);
  cursor: pointer;
  font-size: 13px;
}

.diff-container {
  display: flex;
  gap: 20px;
  align-items: stretch;
}

.diff-panel {
  flex: 1;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  overflow: hidden;
}

.diff-arrow {
  display: flex;
  align-items: center;
  font-size: 24px;
  color: var(--text-tertiary, #999);
}

.panel-header {
  padding: 10px 16px;
  background: var(--bg-tertiary, #f0f2f5);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #666);
}

.panel-body {
  padding: 16px;
}

.diff-field {
  margin-bottom: 12px;
}

.diff-field label {
  display: block;
  font-size: 12px;
  color: var(--text-tertiary, #999);
  margin-bottom: 4px;
}

.field-value {
  padding: 8px 12px;
  background: var(--bg-secondary, #f5f7fa);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary, #1a1a1a);
}

.field-value.source {
  color: var(--text-tertiary, #999);
}

.field-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary, #1a1a1a);
  background: var(--bg-primary, #fff);
  outline: none;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: #3b82f6;
}

.field-input.changed {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.05);
}

.diff-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #f0f0f0);
}

.validation-section {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.validation-header {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.validation-header.valid { color: #10b981; }
.validation-header.invalid { color: #ef4444; }

.validation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.validation-item {
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.validation-item.error {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.validation-item.warning {
  background: rgba(245, 158, 11, 0.08);
  color: #f59e0b;
}

.submit-result {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 12px;
  padding: 20px;
}

.result-icon {
  font-size: 32px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #10b981;
  margin-bottom: 4px;
}

.result-detail {
  font-size: 13px;
  color: var(--text-secondary, #666);
}
</style>

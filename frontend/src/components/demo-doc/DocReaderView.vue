<template>
  <div class="doc-reader-view">
    <div class="view-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <div class="header-title">
        <h1>智读・批量生成</h1>
        <p>方案文档批量开品 — AI解析文档,并行批量生成结构化配置</p>
      </div>
    </div>

    <div class="upload-section">
      <div
        class="upload-zone"
        :class="{ dragging, has_file: uploadedFile }"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input ref="fileInput" type="file" accept=".txt,.docx,.pdf,.md" style="display:none" @change="handleFileSelect" />
        <template v-if="!uploadedFile">
          <div class="upload-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <div class="upload-text">拖拽或点击上传方案文档</div>
          <div class="upload-hint">支持 .docx / .txt / .pdf / .md 格式</div>
        </template>
        <template v-else>
          <div class="upload-icon success">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="upload-text">{{ uploadedFile.filename }}</div>
          <div class="upload-hint">{{ formatSize(uploadedFile.size) }} · 点击重新选择</div>
        </template>
      </div>
      <div class="upload-actions">
        <button class="action-btn secondary" @click="useBuiltinDoc">使用内置示例文档</button>
        <button
          class="action-btn primary"
          :disabled="parsing || (!uploadedFile && !useBuiltin)"
          @click="parseDocument"
        >
          {{ parsing ? '解析中...' : '开始解析' }}
        </button>
      </div>
    </div>

    <div v-if="parsing" class="progress-section">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
      <div class="progress-text">{{ progressText }}</div>
    </div>

    <div v-if="parsedItems.length > 0" class="batch-section">
      <div class="section-title">
        <span>解析结果 ({{ parsedItems.length }} 套套餐)</span>
        <span class="hint">支持批量编辑和提交稽核</span>
      </div>

      <div class="batch-actions">
        <label class="select-all">
          <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
          全选
        </label>
        <button class="action-btn primary" :disabled="submitting || selectedIds.length === 0" @click="batchSubmit">
          {{ submitting ? '提交中...' : `批量提交稽核 (${selectedIds.length})` }}
        </button>
      </div>

      <div class="config-table">
        <table>
          <thead>
            <tr>
              <th class="col-check"></th>
              <th>套餐名称</th>
              <th>带宽</th>
              <th>月租(元)</th>
              <th>合约期</th>
              <th>SLA等级</th>
              <th>静态IP</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="config in batchConfigs" :key="config.package_id">
              <td class="col-check">
                <input type="checkbox" :value="config.package_id" v-model="selectedIds" />
              </td>
              <td>{{ config.package_name }}</td>
              <td>{{ config.bandwidth }}</td>
              <td>{{ config.monthly_fee }}</td>
              <td>{{ config.contract_period }}个月</td>
              <td>{{ config.sla_level }}</td>
              <td>{{ config.static_ip_count }}个</td>
              <td>
                <button class="action-btn secondary small" @click="editConfig(config)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="submitResults.length > 0" class="submit-results-section">
      <div class="section-title">稽核结果</div>
      <div class="results-summary">
        <span class="summary-item passed">通过: {{ passedCount }}</span>
        <span class="summary-item failed">失败: {{ failedCount }}</span>
      </div>
      <div class="results-list">
        <div
          v-for="result in submitResults"
          :key="result.package_id"
          class="result-item"
          :class="result.valid ? 'valid' : 'invalid'"
        >
          <div class="result-status">
            {{ result.valid ? '✅' : '❌' }}
          </div>
          <div class="result-info">
            <div class="result-name">{{ result.package_name }}</div>
            <div v-if="result.record_id" class="result-record">单据号: {{ result.record_id }}</div>
            <div v-for="(err, i) in result.errors" :key="'e'+i" class="result-error">{{ err.message }}</div>
            <div v-for="(warn, i) in result.warnings" :key="'w'+i" class="result-warning">{{ warn.message }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="editingConfig" class="edit-modal-overlay" @click.self="closeEdit">
      <div class="edit-modal">
        <div class="modal-header">
          <h3>编辑套餐配置</h3>
          <button class="close-btn" @click="closeEdit">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-field">
            <label>套餐名称</label>
            <input v-model="editingConfig.package_name" class="field-input" />
          </div>
          <div class="form-field">
            <label>带宽</label>
            <input v-model="editingConfig.bandwidth" class="field-input" />
          </div>
          <div class="form-field">
            <label>月租(元)</label>
            <input v-model.number="editingConfig.monthly_fee" type="number" class="field-input" />
          </div>
          <div class="form-field">
            <label>合约期(月)</label>
            <input v-model.number="editingConfig.contract_period" type="number" class="field-input" />
          </div>
          <div class="form-field">
            <label>SLA等级</label>
            <input v-model="editingConfig.sla_level" class="field-input" />
          </div>
          <div class="form-field">
            <label>静态IP数量</label>
            <input v-model.number="editingConfig.static_ip_count" type="number" class="field-input" />
          </div>
          <div class="form-field">
            <label>受理限制</label>
            <textarea v-model="editingConfig.acceptance_restrictions" class="field-textarea" rows="3"></textarea>
          </div>
          <div class="form-field">
            <label>优惠活动</label>
            <textarea v-model="editingConfig.promotional_activities" class="field-textarea" rows="3"></textarea>
          </div>
          <div class="form-field">
            <label>备注</label>
            <textarea v-model="editingConfig.remark" class="field-textarea" rows="2" placeholder="添加备注..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="action-btn secondary" @click="closeEdit">取消</button>
          <button class="action-btn primary" @click="saveEdit">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { demoDocApi } from '@/services/demoApi.js'

const emit = defineEmits(['go-back'])

const fileInput = ref(null)
const uploadedFile = ref(null)
const dragging = ref(false)
const useBuiltin = ref(false)
const parsing = ref(false)
const progress = ref(0)
const progressText = ref('')
const parsedItems = ref([])
const batchConfigs = ref([])
const selectedIds = ref([])
const submitting = ref(false)
const submitResults = ref([])
const editingConfig = ref(null)

const allSelected = computed(() => {
  return batchConfigs.value.length > 0 && selectedIds.value.length === batchConfigs.value.length
})

const passedCount = computed(() => submitResults.value.filter(r => r.valid).length)
const failedCount = computed(() => submitResults.value.filter(r => !r.valid).length)

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileSelect = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  await uploadFile(file)
}

const handleDrop = async (e) => {
  dragging.value = false
  const file = e.dataTransfer.files[0]
  if (!file) return
  await uploadFile(file)
}

const uploadFile = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await demoDocApi.upload(formData)
    uploadedFile.value = res
    useBuiltin.value = false
  } catch (e) {
    alert('上传失败: ' + e.message)
  }
}

const useBuiltinDoc = () => {
  uploadedFile.value = null
  useBuiltin.value = true
}

const parseDocument = async () => {
  parsing.value = true
  progress.value = 0
  progressText.value = '正在读取文档...'
  parsedItems.value = []
  batchConfigs.value = []
  submitResults.value = []

  const timer = setInterval(() => {
    if (progress.value < 30) {
      progress.value += 10
      progressText.value = '正在读取文档...'
    } else if (progress.value < 60) {
      progress.value += 8
      progressText.value = 'AI解析套餐要素...'
    } else if (progress.value < 90) {
      progress.value += 5
      progressText.value = '映射商品本体...'
    }
  }, 300)

  try {
    const res = await demoDocApi.parse(useBuiltin.value, uploadedFile.value?.file_path)
    progress.value = 100
    progressText.value = '解析完成'
    parsedItems.value = res.items

    const genRes = await demoDocApi.batchGenerate(res.items)
    batchConfigs.value = genRes.configs
  } catch (e) {
    alert('解析失败: ' + e.message)
  } finally {
    clearInterval(timer)
    setTimeout(() => { parsing.value = false }, 500)
  }
}

const toggleSelectAll = (e) => {
  if (e.target.checked) {
    selectedIds.value = batchConfigs.value.map(c => c.package_id)
  } else {
    selectedIds.value = []
  }
}

const editConfig = (config) => {
  editingConfig.value = { ...config }
}

const closeEdit = () => {
  editingConfig.value = null
}

const saveEdit = () => {
  const idx = batchConfigs.value.findIndex(c => c.package_id === editingConfig.value.package_id)
  if (idx >= 0) {
    batchConfigs.value[idx] = { ...editingConfig.value }
  }
  closeEdit()
}

const batchSubmit = async () => {
  submitting.value = true
  submitResults.value = []

  try {
    const configs = batchConfigs.value.filter(c => selectedIds.value.includes(c.package_id))
    const res = await demoDocApi.batchSubmit(configs)
    submitResults.value = res.results
  } catch (e) {
    alert('提交失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.doc-reader-view {
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
  color: #10b981;
  border-color: #10b981;
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

.upload-section {
  margin-bottom: 24px;
}

.upload-zone {
  border: 2px dashed var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--bg-primary, #fff);
}

.upload-zone.dragging {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.05);
}

.upload-zone.has_file {
  border-style: solid;
  border-color: #10b981;
}

.upload-icon {
  color: var(--text-tertiary, #999);
  margin-bottom: 12px;
}

.upload-icon.success {
  color: #10b981;
}

.upload-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary, #1a1a1a);
  margin-bottom: 4px;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-tertiary, #999);
}

.upload-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
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

.action-btn.small {
  padding: 4px 12px;
  font-size: 12px;
}

.action-btn.primary {
  background: #10b981;
  color: #fff;
}

.action-btn.primary:hover:not(:disabled) {
  background: #059669;
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

.progress-section {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.progress-bar {
  height: 8px;
  background: var(--bg-tertiary, #f0f2f5);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #34d399);
  border-radius: 4px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 13px;
  color: var(--text-secondary, #666);
  text-align: center;
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

.batch-section {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.select-all {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-secondary, #666);
  cursor: pointer;
}

.batch-actions .action-btn {
  margin-left: auto;
}

.config-table {
  overflow-x: auto;
}

.config-table table {
  width: 100%;
  border-collapse: collapse;
}

.config-table th {
  text-align: left;
  padding: 10px 12px;
  background: var(--bg-tertiary, #f0f2f5);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #666);
  border-bottom: 1px solid var(--border-color, #e0e0e0);
}

.config-table td {
  padding: 12px;
  font-size: 13px;
  color: var(--text-primary, #1a1a1a);
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.config-table tr:hover {
  background: var(--bg-secondary, #f5f7fa);
}

.col-check {
  width: 40px;
  text-align: center;
}

.submit-results-section {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 12px;
  padding: 20px;
}

.results-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.summary-item {
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
}

.summary-item.passed { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.summary-item.failed { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.results-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
}

.result-item.valid { background: rgba(16, 185, 129, 0.05); }
.result-item.invalid { background: rgba(239, 68, 68, 0.05); }

.result-status {
  font-size: 20px;
}

.result-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
  margin-bottom: 4px;
}

.result-record {
  font-size: 12px;
  color: #10b981;
  margin-bottom: 4px;
}

.result-error {
  font-size: 12px;
  color: #ef4444;
  margin-bottom: 2px;
}

.result-warning {
  font-size: 12px;
  color: #f59e0b;
}

.edit-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.edit-modal {
  background: var(--bg-primary, #fff);
  border-radius: 12px;
  width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 18px;
  color: var(--text-tertiary, #999);
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.form-field {
  margin-bottom: 16px;
}

.form-field label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary, #666);
  margin-bottom: 6px;
}

.field-input, .field-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary, #1a1a1a);
  background: var(--bg-primary, #fff);
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}

.field-input:focus, .field-textarea:focus {
  border-color: #10b981;
}

.field-textarea {
  resize: vertical;
}

.modal-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 20px;
  border-top: 1px solid var(--border-color, #f0f0f0);
}
</style>

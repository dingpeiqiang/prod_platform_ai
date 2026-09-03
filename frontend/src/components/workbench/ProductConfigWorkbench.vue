<template>
  <div class="pcw" :class="{ 'archive-mode': isArchive }">
    <!-- 头部：名称 + 工单号 + 状态徽标 -->
    <header class="pcw-head">
      <div class="pcw-head-main">
        <span class="pcw-name" :title="productName">{{ productName || '配置工作台' }}</span>
        <span v-if="workOrderId" class="pcw-wo">{{ workOrderId }}</span>
      </div>
      <div class="pcw-head-side">
        <span v-if="batchMode" class="pcw-batch-nav">
          <button class="pcw-mini-btn" :disabled="batchIndex <= 0" @click="$emit('batch-nav', -1)">上一个</button>
          <span class="pcw-batch-pos">{{ batchIndex + 1 }}/{{ batchTotal }}</span>
          <button class="pcw-mini-btn" :disabled="batchIndex >= batchTotal - 1" @click="$emit('batch-nav', 1)">下一个</button>
        </span>
        <span class="pcw-status" :class="statusClass">{{ statusLabel }}</span>
        <button class="pcw-mini-btn close" title="收起（可用还原条恢复）" @click="$emit('close')">✕</button>
      </div>
    </header>

    <!-- 页签 -->
    <nav class="pcw-tabs">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        class="pcw-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="pcw-body">
      <!-- 配置表单区（复用 DynamicForm schema 驱动） -->
      <div v-show="activeTab === 'config'" class="pcw-form">
        <div v-for="field in formFields" :key="field.fieldCode" class="pcw-field">
          <div class="pcw-field-label">
            <span class="label-text">{{ field.fieldName }}</span>
            <span v-if="field.required" class="required-mark">*</span>
            <FieldTag :tag="fieldTag(field)" :tagReason="fieldTagReason(field)" />
          </div>
          <select v-if="field.options && field.options.length" v-model="localValues[field.fieldCode]" class="pcw-input" :disabled="isArchive" @change="onFieldChange(field.fieldCode, localValues[field.fieldCode])">
            <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <textarea
            v-else-if="field.fieldType === 'textarea'"
            v-model="localValues[field.fieldCode]"
            class="pcw-input pcw-textarea"
            rows="2"
            :disabled="isArchive"
            @change="onFieldChange(field.fieldCode, localValues[field.fieldCode])"
          ></textarea>
          <input
            v-else
            v-model="localValues[field.fieldCode]"
            class="pcw-input"
            :type="field.fieldType === 'number' ? 'number' : 'text'"
            :placeholder="field.placeholder || ''"
            :disabled="isArchive"
            @change="onFieldChange(field.fieldCode, localValues[field.fieldCode])"
          />
        </div>
      </div>

      <!-- 稽核区 -->
      <div v-show="activeTab === 'audit'" class="pcw-audit">
        <AuditBlock :phase="auditPhase" :steps="auditSteps" :stepIndex="auditStepIndex" :results="auditResults" :hasError="auditHasError" />
        <div v-if="auditPhase === 'idle'" class="pcw-audit-empty">点击「智能稽核」执行提交前校验</div>
      </div>

      <!-- 汇总/概要 -->
      <div v-show="activeTab === 'summary'" class="pcw-summary">
        <div class="summary-grid">
          <div v-for="row in summaryRows" :key="row.label" class="summary-item">
            <span class="summary-label">{{ row.label }}</span>
            <span class="summary-value">{{ row.value || '-' }}</span>
          </div>
        </div>
      </div>

      <!-- 扩展信息（测试报告/备案/渠道，状态到达后展示） -->
      <div v-if="activeTab === 'test'" class="pcw-ext">
        <div class="ext-card">
          <div class="ext-title">测试报告</div>
          <template v-if="extras.testReport">
            <div v-for="(v, k) in extras.testReport" :key="k" class="ext-row">
              <span class="ext-label">{{ extLabelZh(k) }}</span><span class="ext-value">{{ v }}</span>
            </div>
          </template>
          <div v-else class="ext-empty">测试报告尚未生成</div>
        </div>
      </div>
      <div v-if="activeTab === 'filing'" class="pcw-ext">
        <div class="ext-card">
          <div class="ext-title">备案公示</div>
          <template v-if="extras.filingNo">
            <div class="ext-row"><span class="ext-label">备案号</span><span class="ext-value">{{ extras.filingNo }}</span></div>
            <div class="ext-row"><span class="ext-label">状态</span><span class="ext-value">已公示</span></div>
          </template>
          <div v-else class="ext-empty">尚未发起备案公示</div>
        </div>
      </div>
      <div v-if="activeTab === 'channel'" class="pcw-ext">
        <div class="ext-card">
          <div class="ext-title">销售渠道</div>
          <div v-if="extras.channel" class="ext-row"><span class="ext-label">渠道</span><span class="ext-value">{{ extras.channel }}</span></div>
          <div v-else class="ext-empty">尚未选择销售渠道</div>
        </div>
      </div>
    </div>

    <!-- 底部操作区 -->
    <footer v-if="!isArchive" class="pcw-foot">
      <button class="pcw-btn ghost" :disabled="busy" @click="handleAudit">智能稽核</button>
      <button class="pcw-btn primary" :disabled="busy || auditHasError" @click="$emit('submit')">提交配置</button>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import FieldTag from './FieldTag.vue'
import AuditBlock from './AuditBlock.vue'

const props = defineProps({
  /** 当前草稿 product（useProductConfig 形状：ontologyDraft/data/name/workOrderId 等） */
  product: { type: Object, default: null },
  /** 表单 schema（buildProductFormCard().formSchema） */
  formSchema: { type: Object, default: null },
  /** 档案只读模式 */
  isArchive: { type: Boolean, default: false },
  /** 批量草稿导航信息 */
  batchIndex: { type: Number, default: 0 },
  batchTotal: { type: Number, default: 0 },
  /** 稽核状态（父级 usePanelSync 驱动） */
  auditPhase: { type: String, default: 'idle' },
  auditSteps: { type: Array, default: () => [] },
  auditStepIndex: { type: Number, default: 0 },
  auditResults: { type: Array, default: () => [] },
  auditHasError: { type: Boolean, default: false },
  /** 阶段扩展信息（测试报告/备案/渠道） */
  extras: { type: Object, default: () => ({}) },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['field-change', 'submit', 'audit', 'close', 'batch-nav', 'rename'])

const activeTab = ref('config')
/** 面板 key 组合变化时强制重挂载（父级负责），此处本地值跟随 product 初始化 */
const localValues = reactive({})

function initValues() {
  const src = props.product?.ontologyDraft || props.product?.data || {}
  Object.keys(localValues).forEach((k) => delete localValues[k])
  Object.assign(localValues, { ...src })
  if (props.product?.workOrderId) localValues.workOrderId = props.product.workOrderId
}
watch(() => props.product?.id, () => initValues(), { immediate: true })

const formFields = computed(() => props.formSchema?.fields || [])
const productName = computed(() => localValues.offerName || localValues.offeringName || localValues.prodPrcName || props.product?.name || '')
const workOrderId = computed(() => props.product?.workOrderId || localValues.workOrderId || '')

/** 名称编辑 → 通知父级同步全局草稿名 */
watch(() => productName.value, (name, old) => {
  if (name && old && name !== old) emit('rename', name)
})

/** 字段三态标签：fillSource=parsed 已解析 / ai 推荐 / reuse 复用 */
function fieldTag(field) {
  const src = field.fillSource || ''
  if (src === 'parsed' || src === 'user') return 'parsed'
  if (src === 'ai' || src === 'llm' || src === 'inferred') return 'ai'
  if (src === 'reuse' || src === 'template') return 'reuse'
  const fill = props.product?.ontologyDraft?.fillSources || {}
  const s = String(fill[field.fieldCode] || '').toLowerCase()
  if (s.includes('user') || s.includes('parsed')) return 'parsed'
  if (s.includes('ai') || s.includes('llm')) return 'ai'
  if (s.includes('reuse') || s.includes('template')) return 'reuse'
  return ''
}
function fieldTagReason(field) {
  const fill = props.product?.ontologyDraft?.fillSources || {}
  return fill[`${field.fieldCode}_reason`] || fill[`${field.fieldCode}Reason`] || ''
}

const statusClass = computed(() => {
  if (props.isArchive) return 'st-archived'
  if (props.product?.status === 'submitted') return 'st-submitted'
  if (props.product?.compliancePass) return 'st-passed'
  return 'st-draft'
})
const statusLabel = computed(() => {
  if (props.isArchive) return '档案'
  if (props.product?.status === 'submitted') return '已提交'
  if (props.auditHasError) return '待修正'
  if (props.product?.compliancePass) return '稽核通过'
  return '草稿'
})

const visibleTabs = computed(() => {
  const tabs = [
    { key: 'config', label: props.isArchive ? '基础信息' : '配置' },
    { key: 'audit', label: '稽核' },
    { key: 'summary', label: '概要' },
  ]
  if (props.extras.testReport) tabs.push({ key: 'test', label: '测试报告' })
  if (props.extras.filingNo) tabs.push({ key: 'filing', label: '备案公示' })
  if (props.extras.channel) tabs.push({ key: 'channel', label: '销售渠道' })
  return tabs
})

const summaryRows = computed(() => {
  const v = localValues
  return [
    { label: '商品名称', value: productName.value },
    { label: '产品品类', value: v.categoryName || v.categoryCode || v.messageRootKey },
    { label: '月费', value: v.fixedFeeAmount != null ? `${v.fixedFeeAmount} 元` : (v.monthlyFee ? `${v.monthlyFee} 元` : '') },
    { label: '包含流量', value: v.includeData },
    { label: '包含语音', value: v.includeVoice },
    { label: '包含宽带', value: v.includeBroadband },
    { label: '业务场景', value: v.bizScenario },
    { label: '目标用户', value: v.targetUser },
    { label: '销售渠道', value: v.channelScope },
    { label: '发布地市', value: v.regionScope },
  ]
})

function extLabelZh(key) {
  const map = {
    passed: '测试结论', totalCases: '用例总数', passedCases: '通过用例',
    failedCases: '失败用例', durationDays: '测试时长(天)', reportNo: '报告编号', summary: '摘要',
  }
  return map[key] || key
}

function onFieldChange(code, value) {
  emit('field-change', { fieldCode: code, value })
}

/** 暴露给父级：对话驱动的字段级增量更新（不重建面板，仅更新字段并标已解析） */
function applyFieldUpdate(fieldCode, value, { tag = 'parsed', tagReason = '' } = {}) {
  localValues[fieldCode] = value
  if (props.product?.ontologyDraft) {
    props.product.ontologyDraft[fieldCode] = value
    const fill = props.product.ontologyDraft.fillSources || (props.product.ontologyDraft.fillSources = {})
    fill[fieldFieldCodeAlias(fieldCode)] = tag
    if (tagReason) fill[`${fieldFieldCodeAlias(fieldCode)}_reason`] = tagReason
  }
}
function fieldFieldCodeAlias(code) {
  return code
}

defineExpose({ applyFieldUpdate, activeTab })
</script>

<style scoped>
.pcw { display: flex; flex-direction: column; height: 100%; background: #fff; }
.pcw-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 14px; border-bottom: 1px solid #e2e8f0; flex-shrink: 0; }
.pcw-head-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pcw-name { font-weight: 700; font-size: 14px; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pcw-wo { font-size: 11px; color: #64748b; background: #f1f5f9; padding: 2px 8px; border-radius: 999px; flex-shrink: 0; }
.pcw-head-side { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.pcw-status { font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 999px; }
.st-draft { background: #f1f5f9; color: #64748b; }
.st-passed { background: #ecfdf5; color: #059669; }
.st-submitted { background: #eff6ff; color: #2563eb; }
.st-archived { background: #f5f3ff; color: #7c3aed; }

.pcw-mini-btn { border: 1px solid #e2e8f0; background: #fff; color: #475569; font-size: 11px; padding: 4px 8px; border-radius: 6px; cursor: pointer; }
.pcw-mini-btn:hover:not(:disabled) { border-color: #93c5fd; background: #f0f9ff; }
.pcw-mini-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pcw-mini-btn.close { width: 24px; height: 24px; padding: 0; display: inline-flex; align-items: center; justify-content: center; }
.pcw-batch-nav { display: inline-flex; align-items: center; gap: 4px; }
.pcw-batch-pos { font-size: 11px; color: #64748b; }

.pcw-tabs { display: flex; gap: 2px; padding: 0 14px; border-bottom: 1px solid #e2e8f0; flex-shrink: 0; overflow-x: auto; }
.pcw-tab { border: none; background: transparent; padding: 10px 12px; font-size: 13px; color: #64748b; cursor: pointer; border-bottom: 2px solid transparent; white-space: nowrap; }
.pcw-tab.active { color: #2563eb; font-weight: 600; border-bottom-color: #2563eb; }

.pcw-body { flex: 1; min-height: 0; overflow-y: auto; padding: 14px; }
.pcw-form { display: flex; flex-direction: column; gap: 12px; }
.pcw-field { display: flex; flex-direction: column; gap: 4px; }
.pcw-field-label { display: flex; align-items: center; gap: 6px; }
.label-text { font-size: 12px; font-weight: 600; color: #334155; }
.required-mark { color: #dc2626; font-size: 12px; }
.pcw-input { width: 100%; box-sizing: border-box; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; font-size: 13px; color: #0f172a; background: #fff; }
.pcw-input:focus { outline: none; border-color: #60a5fa; box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.15); }
.pcw-input:disabled { background: #f8fafc; color: #64748b; }
.pcw-textarea { resize: vertical; }

.pcw-audit-empty { text-align: center; font-size: 12px; color: #94a3b8; padding: 24px 0; }
.pcw-summary { display: flex; flex-direction: column; gap: 8px; }
.summary-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.summary-item { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; }
.summary-label { font-size: 11px; color: #64748b; }
.summary-value { font-size: 13px; font-weight: 600; color: #0f172a; word-break: break-all; }

.pcw-ext { display: flex; flex-direction: column; gap: 10px; }
.ext-card { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.ext-title { font-size: 13px; font-weight: 700; color: #0f172a; }
.ext-row { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; }
.ext-label { color: #64748b; flex-shrink: 0; }
.ext-value { color: #0f172a; text-align: right; }
.ext-empty { font-size: 12px; color: #94a3b8; text-align: center; padding: 12px 0; }

.pcw-foot { display: flex; gap: 10px; padding: 12px 14px; border-top: 1px solid #e2e8f0; flex-shrink: 0; }
.pcw-btn { flex: 1; padding: 9px 0; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.pcw-btn.ghost { background: #fff; border-color: #cbd5e1; color: #334155; }
.pcw-btn.ghost:hover:not(:disabled) { border-color: #93c5fd; background: #f0f9ff; }
.pcw-btn.primary { background: #2563eb; color: #fff; }
.pcw-btn.primary:hover:not(:disabled) { background: #1d4ed8; }
.pcw-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>

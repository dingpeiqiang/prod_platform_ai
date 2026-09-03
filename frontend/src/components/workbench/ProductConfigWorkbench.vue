<template>
  <div class="pcw" :class="{ 'archive-mode': isArchive }">
    <!-- 头部：图标 + 名称 + 模板徽标 + 工单号 + 状态徽标 -->
    <header class="pcw-head">
      <div class="pcw-head-main">
        <span class="pcw-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
        </span>
        <span class="pcw-name" :title="productName">{{ productName || '配置工作台' }}</span>
        <span v-if="templateLabel" class="pcw-tpl-badge" :title="'模板：' + templateLabel">{{ templateLabel }}</span>
        <span v-if="workOrderId" class="pcw-wo" :title="'工单号：' + workOrderId">{{ workOrderId }}</span>
      </div>
      <div class="pcw-head-side">
        <span v-if="batchMode" class="pcw-batch-nav">
          <button class="pcw-mini-btn" :disabled="batchIndex <= 0" @click="$emit('batch-nav', -1)">上一个</button>
          <span class="pcw-batch-pos">{{ batchIndex + 1 }}/{{ batchTotal }}</span>
          <button class="pcw-mini-btn" :disabled="batchIndex >= batchTotal - 1" @click="$emit('batch-nav', 1)">下一个</button>
        </span>
        <span class="pcw-status" :class="statusClass">{{ statusLabel }}</span>
        <button
          v-if="!isArchive"
          class="pcw-mini-btn copy"
          title="复制为新草稿"
          @click="$emit('copy')"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
        </button>
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
      <!-- 配置表单区：分节折叠 + 基础信息双列（schema 驱动） -->
      <div v-show="activeTab === 'config'" class="pcw-form">
        <div v-for="sec in fieldSections" :key="sec.key" class="pcw-section">
          <button class="pcw-section-head" @click="toggleSection(sec.key)">
            <span class="section-arrow" :class="{ collapsed: collapsedSections[sec.key] }">▾</span>
            <span class="section-title">{{ sec.label }}</span>
            <span class="section-count">{{ sec.fields.length }} 项</span>
          </button>
          <div v-show="!collapsedSections[sec.key]" class="pcw-section-content" :class="{ 'two-col': sec.twoCol }">
            <div v-for="field in sec.fields" :key="field.fieldCode" class="pcw-field">
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
        </div>
      </div>

      <!-- 稽核区 -->
      <div v-show="activeTab === 'audit'" class="pcw-audit">
        <AuditBlock :phase="auditPhase" :steps="auditSteps" :stepIndex="auditStepIndex" :results="auditResults" :hasError="auditHasError" />
        <div v-if="auditPhase === 'idle'" class="pcw-audit-empty">点击「智能稽核」执行提交前校验</div>
      </div>

      <!-- 信息预览（手机 mock：受理信息/免填单/短信预览 三个底部页签，对齐原型） -->
      <div v-show="activeTab === 'summary'" class="pcw-summary">
        <div class="phone-mock">
          <div class="phone-statusbar"><span class="ps-time">9:41</span><span class="ps-icons">●●●</span></div>
          <div class="phone-navbar">{{ previewName || '商品详情' }}</div>

          <!-- 页签1：受理信息 -->
          <div v-show="phoneTab === 'user'" class="phone-screen">
            <div class="phone-banner">
              <div class="pb-name">{{ previewName || '未命名商品' }}</div>
              <div class="pb-fee"><span class="pbf-num">{{ previewFee }}</span><span class="pbf-unit"> 元/月</span></div>
              <div class="pb-tags">
                <span v-if="localValues.includeData" class="pb-tag">{{ localValues.includeData }}</span>
                <span v-if="localValues.includeVoice" class="pb-tag">{{ localValues.includeVoice }}</span>
                <span v-if="localValues.includeBroadband" class="pb-tag">{{ localValues.includeBroadband }}</span>
              </div>
            </div>
            <div class="phone-res">
              <div class="phone-res-title">套餐资源</div>
              <div class="phone-res-grid">
                <div class="phone-res-item"><span class="pr-val">{{ localValues.includeData || '-' }}</span><span class="pr-key">包含流量</span></div>
                <div class="phone-res-item"><span class="pr-val">{{ localValues.includeVoice || '-' }}</span><span class="pr-key">包含语音</span></div>
                <div class="phone-res-item"><span class="pr-val">{{ localValues.includeBroadband || '-' }}</span><span class="pr-key">包含宽带</span></div>
              </div>
            </div>
            <div class="phone-fee">
              <div class="phone-fee-title">费用说明</div>
              <div class="phone-fee-row"><span>套餐月费</span><span>{{ previewFee }} 元/月</span></div>
              <div class="phone-fee-row"><span>一次性费用</span><span>{{ localValues.oneTimeFee != null && localValues.oneTimeFee !== '' ? localValues.oneTimeFee + ' 元' : '无' }}</span></div>
              <div class="phone-fee-row"><span>收费方式</span><span>{{ localValues.chargeType === 'yearly' ? '年付' : localValues.chargeType === 'quarterly' ? '季付' : '月付' }}</span></div>
              <div v-if="localValues.flowOvercharge" class="phone-fee-row"><span>套外流量</span><span>{{ localValues.flowOvercharge }}</span></div>
              <div v-if="localValues.voiceOvercharge" class="phone-fee-row"><span>套外语音</span><span>{{ localValues.voiceOvercharge }}</span></div>
            </div>
            <div v-if="localValues.bizScenario || localValues.targetUser" class="phone-rule">
              <div class="phone-fee-title">规则说明</div>
              <div v-if="localValues.bizScenario" class="phone-rule-item"><span class="pr-dot"></span>业务场景：{{ localValues.bizScenario }}</div>
              <div v-if="localValues.targetUser" class="phone-rule-item"><span class="pr-dot"></span>目标用户：{{ localValues.targetUser }}</div>
              <div v-if="localValues.channelScope" class="phone-rule-item"><span class="pr-dot"></span>销售渠道：{{ localValues.channelScope }}</div>
            </div>
            <div class="phone-action-bar">
              <button class="pa-btn secondary">加入购物车</button>
              <button class="pa-btn primary">立即办理</button>
            </div>
          </div>

          <!-- 页签2：免填单 -->
          <div v-show="phoneTab === 'freefill'" class="phone-screen">
            <div class="phone-fee">
              <div class="phone-fee-title">免填单信息</div>
              <div class="phone-fee-row"><span>商品名称</span><span>{{ previewName || '-' }}</span></div>
              <div class="phone-fee-row"><span>套餐月费</span><span>{{ previewFee }} 元/月</span></div>
              <div class="phone-fee-row"><span>目标用户</span><span>{{ localValues.targetUser || '-' }}</span></div>
              <div class="phone-fee-row"><span>业务场景</span><span>{{ localValues.bizScenario || '-' }}</span></div>
              <div class="phone-fee-row"><span>销售渠道</span><span>{{ localValues.channelScope || '-' }}</span></div>
              <div class="phone-fee-row"><span>生效规则</span><span>{{ localValues.effRuleId || '默认生效规则' }}</span></div>
            </div>
            <div class="phone-action-bar">
              <button class="pa-btn primary full">确认受理</button>
            </div>
          </div>

          <!-- 页签3：短信预览 -->
          <div v-show="phoneTab === 'sms'" class="phone-screen">
            <div class="phone-sms">
              <div class="sms-time">今天 9:41</div>
              <div v-for="sms in smsItems" :key="sms.label" class="sms-msg">
                <div class="sms-avatar">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                </div>
                <div class="sms-body">
                  <div class="sms-label">{{ sms.label }}</div>
                  <div class="sms-bubble">{{ sms.value }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="phone-tabbar">
            <span class="pt-item" :class="{ active: phoneTab === 'user' }" @click="phoneTab = 'user'">受理信息</span>
            <span class="pt-item" :class="{ active: phoneTab === 'freefill' }" @click="phoneTab = 'freefill'">免填单</span>
            <span class="pt-item" :class="{ active: phoneTab === 'sms' }" @click="phoneTab = 'sms'">短信预览</span>
          </div>
        </div>
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

    <!-- 底部操作区：批量导航条 / 提交成功绿色横条 / 保存草稿+稽核+提交 -->
    <footer v-if="!isArchive && statusLabel !== '已提交'" class="pcw-foot">
      <div v-if="batchMode" class="batch-nav-bar">
        <span class="bn-label">第 {{ batchIndex + 1 }} / 共 {{ batchTotal }} 条</span>
        <span class="bn-sep"></span>
        <button class="pcw-mini-btn" :disabled="batchIndex <= 0" @click="$emit('batch-nav', -1)">上一个</button>
        <button class="pcw-mini-btn" :disabled="batchIndex >= batchTotal - 1" @click="$emit('batch-nav', 1)">下一个</button>
      </div>
      <div class="pcw-foot-btns">
        <button class="pcw-btn ghost" :disabled="busy" @click="$emit('save')">保存草稿</button>
        <button class="pcw-btn ghost" :disabled="busy" @click="handleAudit">智能稽核</button>
        <button class="pcw-btn primary" :disabled="busy || auditHasError" @click="$emit('submit')">提交配置</button>
      </div>
    </footer>
    <footer v-else-if="!isArchive && statusLabel === '已提交'" class="pcw-foot submitted">
      <div class="submitted-bar" :class="'fs-' + footerStageClass">
        <span class="sb-icon">
          <svg v-if="footerTerminal" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <template v-else>✓</template>
        </span>
        <span class="sb-text">{{ footerStageText }}</span>
        <span v-if="workOrderId" class="sb-wo">{{ workOrderId }}</span>
      </div>
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
  batchMode: { type: Boolean, default: false },
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
  /** 工作流当前阶段 key（design/config/approve/test/filing/launch/done），驱动提交横条阶段化 */
  workflowStage: { type: String, default: '' },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['field-change', 'submit', 'audit', 'close', 'batch-nav', 'rename', 'save', 'copy'])

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

/** 模板徽标：优先后端模板 schema 携带的模板名/版本，降级草稿 basedOnTemplate */
const templateLabel = computed(() => {
  const schema = props.formSchema
  if (schema?.formName && schema?.formCode === 'offering_config' && schema?.templateVersion) {
    return `${schema.formName} v${schema.templateVersion}`
  }
  return localValues.basedOnTemplate || ''
})

/** 名称编辑 → 通知父级同步全局草稿名 */
watch(() => productName.value, (name, old) => {
  if (name && old && name !== old) emit('rename', name)
})

/** ── 分节渲染：按 schema sections 分组；无 sections 时按字段语义分节（基础信息/资源配置/销售规则） ── */
const SECTION_DEFS = [
  { key: 'base', label: '基础信息', twoCol: true, codes: ['offerName', 'offeringName', 'prodPrcName', 'categoryCode', 'messageRootKey', 'bizScenario', 'targetUser', 'offeringType', 'basedOnTemplate', 'workOrderId'] },
  { key: 'resource', label: '资源配置', twoCol: false, codes: ['fixedFeeAmount', 'monthlyFee', 'oneTimeFee', 'includeVoice', 'includeData', 'includeBroadband', 'flowType', 'flowAmount', 'flowUnit', 'flowOvercharge', 'flowCarryover', 'voiceType', 'voiceAmount', 'voiceOvercharge', 'smsAmount', 'smsOvercharge', 'chargeType', 'chargeStandard', 'feeSubject', 'taxRate', 'minConsumeType', 'minConsumeAmount', 'discountCondition', 'discountType', 'discountAmount'] },
  { key: 'sales', label: '销售规则', twoCol: false, codes: ['channelScope', 'regionScope', 'groupId', 'groupIdMessage', 'chnClassLimit', 'effRuleId', 'expRuleId', 'effDate', 'expDate', 'mutexGroup', 'dependOn', 'hasContract', 'contractMonths', 'repeatable', 'bindExistingMainPkg', 'limitCondition', 'otherEquity'] },
  { key: 'sms', label: '短信文案', twoCol: false, codes: ['smsContent', 'sysNoteNow', 'sysNoteNext', 'sysNoteCancle', 'sysNoteErke'] },
]

const fieldSections = computed(() => {
  const fields = formFields.value
  if (!fields.length) return []
  // 模板 schema 自带 sections（分组结构）→ 直接使用
  const sections = props.formSchema?.sections
  if (Array.isArray(sections) && sections.length) {
    return sections.map((sec, i) => {
      const keys = new Set(sec.fieldCodes || sec.fields || [])
      const secFields = fields.filter((f) => keys.has(f.fieldCode) || (sec.sectionKey && f.sectionKey === sec.sectionKey))
      return {
        key: sec.sectionKey || sec.key || `sec-${i}`,
        label: sec.sectionName || sec.label || `分组${i + 1}`,
        fields: secFields,
        twoCol: sec.twoCol || (sec.sectionKey || sec.key) === 'base',
      }
    }).filter((s) => s.fields.length)
  }
  // 扁平字段 → 按语义分节，未匹配的归入「其他」
  const grouped = new Map(SECTION_DEFS.map((s) => [s.key, { ...s, fields: [] }]))
  const other = { key: 'other', label: '其他信息', twoCol: false, fields: [] }
  for (const f of fields) {
    const def = SECTION_DEFS.find((s) => s.codes.includes(f.fieldCode))
    if (def) grouped.get(def.key).fields.push(f)
    else other.fields.push(f)
  }
  const result = Array.from(grouped.values()).filter((s) => s.fields.length)
  if (other.fields.length) result.push(other)
  return result
})

/** 分节折叠态（默认全展开） */
const collapsedSections = reactive({})
function toggleSection(key) {
  collapsedSections[key] = !collapsedSections[key]
}

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
    { key: 'summary', label: '信息预览' },
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
    { label: '产品品类', value: categoryLabel(v.categoryName || v.categoryCode || v.messageRootKey) },
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

/** 品类编码 → 中文标签（复用 schema options 词典） */
function categoryLabel(code) {
  const field = formFields.value.find((f) => f.fieldCode === 'categoryCode')
  const opt = field?.options?.find((o) => o.value === code)
  return opt?.label || code
}

/** 信息预览 mock 框架数据 */
const phoneTab = ref('user')
const previewName = computed(() => productName.value)
const previewDesc = computed(() => {
  const v = localValues
  const parts = [v.bizScenario, v.targetUser, v.channelScope && `渠道:${v.channelScope}`].filter(Boolean)
  return parts.join(' · ')
})
const previewFee = computed(() => {
  const v = localValues
  const fee = v.fixedFeeAmount ?? v.monthlyFee
  return fee != null && fee !== '' ? fee : '-'
})

/** 短信预览页签：取短信文案字段，缺省给占位 */
const smsItems = computed(() => {
  const v = localValues
  return [
    { label: '订购成功通知', value: v.sysNoteNow || `您已成功订购${productName.value || '新套餐'}，月费${previewFee.value}元。` },
    { label: '下月生效提醒', value: v.sysNoteNext || `您订购的${productName.value || '新套餐'}将于次月1日生效。` },
    { label: '退订确认', value: v.sysNoteCancle || `您已退订${productName.value || '该套餐'}，感谢使用。` },
  ]
})

/** ── 提交横条阶段化（对齐原型 footer-submitted：随工作流阶段变色+文案） ── */
const footerStageClass = computed(() => {
  const map = {
    approve: 'submitted',
    test: 'testing',
    filing: 'filing',
    launch: 'channel',
    done: 'completed',
  }
  return map[props.workflowStage] || 'submitted'
})
const footerTerminal = computed(() => ['done', 'launch', 'test'].includes(props.workflowStage))
const footerStageText = computed(() => {
  const map = {
    approve: '已提交，进入审批阶段',
    test: props.extras?.testReport ? '审批通过，测试已完成' : '审批通过，进入测试',
    filing: props.extras?.filingNo ? '备案已公示，待选择渠道' : '测试完成，等待备案公示',
    launch: props.extras?.channel ? '已配置销售渠道' : '待选择销售渠道',
    done: '已上架销售中',
  }
  return map[props.workflowStage] || '配置已提交入库'
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
.pcw-head-main { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
.pcw-icon { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 8px; background: linear-gradient(135deg, #6366f1, #3b82f6); color: #fff; flex-shrink: 0; }
.pcw-name { font-weight: 700; font-size: 14px; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pcw-tpl-badge { font-size: 10px; color: #7c3aed; background: #f5f3ff; border: 1px solid #ddd6fe; padding: 1px 7px; border-radius: 999px; flex-shrink: 0; max-width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
.pcw-mini-btn.copy { width: 24px; height: 24px; padding: 0; display: inline-flex; align-items: center; justify-content: center; }
.pcw-batch-nav { display: inline-flex; align-items: center; gap: 4px; }
.pcw-batch-pos { font-size: 11px; color: #64748b; }

.pcw-tabs { display: flex; gap: 2px; padding: 0 14px; border-bottom: 1px solid #e2e8f0; flex-shrink: 0; overflow-x: auto; }
.pcw-tab { border: none; background: transparent; padding: 10px 12px; font-size: 13px; color: #64748b; cursor: pointer; border-bottom: 2px solid transparent; white-space: nowrap; }
.pcw-tab.active { color: #2563eb; font-weight: 600; border-bottom-color: #2563eb; }

.pcw-body { flex: 1; min-height: 0; overflow-y: auto; padding: 14px; }
.pcw-form { display: flex; flex-direction: column; gap: 12px; }
.pcw-section { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; }
.pcw-section-head {
  display: flex; align-items: center; gap: 6px; width: 100%;
  padding: 9px 12px; border: none; background: #f8fafc; cursor: pointer;
}
.pcw-section-head:hover { background: #f1f5f9; }
.section-arrow { font-size: 11px; color: #64748b; transition: transform 0.18s ease; }
.section-arrow.collapsed { transform: rotate(-90deg); }
.section-title { font-size: 13px; font-weight: 700; color: #0f172a; flex: 1; text-align: left; }
.section-count { font-size: 11px; color: #94a3b8; }
.pcw-section-content { display: flex; flex-direction: column; gap: 12px; padding: 12px; border-top: 1px solid #e2e8f0; }
.pcw-section-content.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 12px; }
.pcw-section-content.two-col .pcw-field { min-width: 0; }
.pcw-field { display: flex; flex-direction: column; gap: 4px; }
.pcw-field-label { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.label-text { font-size: 12px; font-weight: 600; color: #334155; }
.required-mark { color: #dc2626; font-size: 12px; }
.pcw-input { width: 100%; box-sizing: border-box; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; font-size: 13px; color: #0f172a; background: #fff; }
.pcw-input:focus { outline: none; border-color: #60a5fa; box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.15); }
.pcw-input:disabled { background: #f8fafc; color: #64748b; }
.pcw-textarea { resize: vertical; }

.pcw-audit-empty { text-align: center; font-size: 12px; color: #94a3b8; padding: 24px 0; }

/* 信息预览：手机 mock 框架 */
.pcw-summary { display: flex; flex-direction: column; gap: 12px; }
.phone-mock {
  border: 1px solid #e2e8f0; border-radius: 16px; overflow: hidden;
  max-width: 260px; margin: 0 auto; width: 100%;
  background: #f8fafc; display: flex; flex-direction: column; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}
.phone-statusbar { display: flex; justify-content: space-between; padding: 4px 12px; font-size: 10px; color: #64748b; background: #fff; }
.phone-navbar { text-align: center; font-size: 12px; font-weight: 700; color: #0f172a; padding: 6px 0; background: #fff; border-bottom: 1px solid #f1f5f9; }
.phone-banner {
  margin: 10px; padding: 12px; border-radius: 10px; color: #fff;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
}
.pb-name { font-size: 14px; font-weight: 700; }
.pb-desc { font-size: 11px; opacity: 0.85; margin-top: 4px; }
.pb-fee { margin-top: 6px; }
.pbf-num { font-size: 20px; font-weight: 700; }
.pbf-unit { font-size: 11px; opacity: 0.85; }
.pb-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
.pb-tag { font-size: 10px; padding: 2px 7px; border-radius: 999px; background: rgba(255, 255, 255, 0.2); border: 1px solid rgba(255, 255, 255, 0.35); }
.phone-res { padding: 0 10px; }
.phone-res-title, .phone-fee-title { font-size: 12px; font-weight: 700; color: #0f172a; margin-bottom: 6px; }
.phone-res-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.phone-res-item { display: flex; flex-direction: column; align-items: center; gap: 2px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 4px; }
.pr-val { font-size: 11px; font-weight: 700; color: #2563eb; word-break: break-all; text-align: center; }
.pr-key { font-size: 10px; color: #94a3b8; }
.phone-fee { padding: 10px; }
.phone-fee-row { display: flex; justify-content: space-between; font-size: 11px; color: #475569; padding: 3px 0; }
.phone-rule { padding: 0 10px 6px; }
.phone-rule-item { display: flex; align-items: center; gap: 5px; font-size: 11px; color: #475569; padding: 2px 0; }
.pr-dot { width: 4px; height: 4px; border-radius: 50%; background: #94a3b8; flex-shrink: 0; }
.phone-action-bar { display: flex; gap: 8px; padding: 10px; }
.pa-btn { flex: 1; padding: 8px 0; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.pa-btn.secondary { background: #fff; border-color: #cbd5e1; color: #334155; }
.pa-btn.primary { background: linear-gradient(135deg, #3b82f6, #2563eb); color: #fff; }
.pa-btn.full { flex: 1; }
.phone-sms { padding: 12px 10px; display: flex; flex-direction: column; gap: 10px; background: #f1f5f9; min-height: 220px; }
.sms-time { text-align: center; font-size: 10px; color: #94a3b8; }
.sms-msg { display: flex; gap: 6px; }
.sms-avatar { width: 26px; height: 26px; border-radius: 50%; background: #fff; border: 1px solid #e2e8f0; display: inline-flex; align-items: center; justify-content: center; color: #64748b; flex-shrink: 0; }
.sms-body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.sms-label { font-size: 10px; color: #94a3b8; }
.sms-bubble { background: #fff; border: 1px solid #e2e8f0; border-radius: 0 10px 10px 10px; padding: 7px 9px; font-size: 11px; color: #334155; line-height: 1.5; word-break: break-all; }
.phone-tabbar { display: flex; justify-content: space-around; border-top: 1px solid #e2e8f0; background: #fff; padding: 6px 0; }
.pt-item { font-size: 10px; color: #94a3b8; cursor: pointer; padding: 2px 8px; border-radius: 6px; }
.pt-item:hover { color: #475569; }
.pt-item.active { color: #2563eb; font-weight: 600; }

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

/* 底部操作区：批量导航条 + 三按钮 / 提交成功横条 */
.pcw-foot { display: flex; flex-direction: column; gap: 8px; padding: 10px 14px; border-top: 1px solid #e2e8f0; flex-shrink: 0; }
.pcw-foot.submitted { padding: 10px 14px; }
.batch-nav-bar { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px dashed #fde68a; }
.bn-label { font-size: 12px; color: #b45309; font-weight: 600; }
.bn-sep { flex: 1; }
.pcw-foot-btns { display: flex; gap: 10px; }
.pcw-btn { flex: 1; padding: 9px 0; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; border: 1px solid transparent; white-space: nowrap; }
.pcw-btn.ghost { background: #fff; border-color: #cbd5e1; color: #334155; }
.pcw-btn.ghost:hover:not(:disabled) { border-color: #93c5fd; background: #f0f9ff; }
.pcw-btn.primary { background: linear-gradient(135deg, #2563eb, #7c3aed); color: #fff; }
.pcw-btn.primary:hover:not(:disabled) { background: linear-gradient(135deg, #1d4ed8, #6d28d9); }
.pcw-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.submitted-bar {
  display: flex; align-items: center; gap: 8px;
  background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 10px; padding: 9px 12px;
  transition: background 0.3s ease, border-color 0.3s ease;
}
/* 提交横条阶段化配色（对齐原型 footer-submitted.submitted/testing/tested） */
.submitted-bar.fs-submitted { background: #eff6ff; border-color: #bfdbfe; }
.submitted-bar.fs-submitted .sb-text { color: #1d4ed8; }
.submitted-bar.fs-submitted .sb-icon { background: #2563eb; }
.submitted-bar.fs-submitted .sb-wo { background: #dbeafe; color: #2563eb; }
.submitted-bar.fs-testing { background: #eff6ff; border-color: #bfdbfe; }
.submitted-bar.fs-testing .sb-text { color: #1d4ed8; }
.submitted-bar.fs-testing .sb-icon { background: #2563eb; }
.submitted-bar.fs-testing .sb-wo { background: #dbeafe; color: #2563eb; }
.submitted-bar.fs-filing { background: #f5f3ff; border-color: #ddd6fe; }
.submitted-bar.fs-filing .sb-text { color: #6d28d9; }
.submitted-bar.fs-filing .sb-icon { background: #7c3aed; }
.submitted-bar.fs-filing .sb-wo { background: #ede9fe; color: #7c3aed; }
.submitted-bar.fs-channel { background: #fffbeb; border-color: #fde68a; }
.submitted-bar.fs-channel .sb-text { color: #b45309; }
.submitted-bar.fs-channel .sb-icon { background: #d97706; }
.submitted-bar.fs-channel .sb-wo { background: #fef3c7; color: #b45309; }
.sb-icon { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: #059669; color: #fff; font-size: 11px; flex-shrink: 0; }
.sb-text { font-size: 12px; font-weight: 600; color: #065f46; flex: 1; }
.sb-wo { font-size: 11px; color: #059669; background: #d1fae5; padding: 2px 8px; border-radius: 999px; }
</style>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  formData: { type: Object, required: true },
  modifiedFields: { type: Object, required: true },
  activeTab: { type: String, default: 'base' },
})

const emit = defineEmits(['field-change'])

const openSections = ref({
  base: true,
  releaseCh: true,
  releaseCity: true,
  optional: false,
  sms: false,
  monthly: false,
  discount: false,
  min: false,
  flow: false,
  voice: false,
  smsRes: false,
})

function toggle(key) {
  openSections.value[key] = !openSections.value[key]
}

function onInput(field, e) {
  const val = e.target.value
  props.formData[field] = val
  emit('field-change', field)
}

function onSelect(field, e) {
  props.formData[field] = e.target.value
  emit('field-change', field)
  if (field === 'minConsumeType') {
    // reactive update handled by parent formData
  }
}

function modClass(field) {
  const set = props.modifiedFields
  return set && typeof set.has === 'function' && set.has(field) ? 'modified' : ''
}

const showMinConsumeAmount = computed(
  () => props.formData.minConsumeType && props.formData.minConsumeType !== 'none',
)

const maps = {
  effRule: { '1001': '立即生效', '1002': '次月生效', '1003': '预约生效' },
  expRule: { '1023': '立即失效', '1024': '次月失效' },
  channel: { A7: '全渠道', A1: '线下营业厅', A2: '线上渠道', A3: '代理商' },
  city: { '10001': '四川', '10002': '成都', '10003': '绵阳', '10004': '德阳', '10005': '南充' },
  chargeType: { monthly: '月付', quarterly: '季付', yearly: '年付' },
  flowType: { general: '通用流量', 定向: '定向流量', 专属: '专属流量' },
  voiceType: { local: '本地语音', longdistance: '长途语音' },
}

function label(map, key) {
  return maps[map]?.[props.formData[key]] || '-'
}

const summaryTitles = {
  base: '基本信息',
  release: '发布信息',
  optional: '免填单',
  sms: '短信提醒',
  monthly: '月租',
  flow: '流量资源',
  voice: '语音资源',
  smsRes: '短信资源',
}

const summarySections = computed(() => {
  const d = props.formData
  return {
    base: [
      { label: '工单编号', value: d.workOrderId || '-' },
      { label: '资费名称', value: d.prodPrcName || '-' },
      { label: '产品代码', value: d.prodId || '-' },
      { label: '资费代码', value: d.prodPrcId || '-' },
      { label: '订购生效方式', value: label('effRule', 'effRuleId') },
      { label: '退订生效方式', value: label('expRule', 'expRuleId') },
      { label: '销售开始日期', value: d.effDate || '-' },
      { label: '销售终止日期', value: d.expDate || '-' },
    ],
    release: [
      { label: '发布渠道', value: label('channel', 'chnClassLimit') },
      { label: '发布地市', value: label('city', 'groupId') },
      { label: '地市明细', value: d.groupIdMessage || '-' },
    ],
    optional: [
      { label: '套餐月费', value: d.prcMonthFee || '-' },
      { label: '包含资源', value: d.containResource || '-' },
      { label: '超套收费标准', value: d.chargeStandard || '-' },
      { label: '限定条件', value: d.limitCondition || '-' },
      { label: '其他权益', value: d.otherEquity || '-' },
    ],
    sms: [
      { label: '成功短信（立即）', value: d.sysNoteNow || '-' },
      { label: '成功短信（预约）', value: d.sysNoteNext || '-' },
      { label: '退订短信', value: d.sysNoteCancle || '-' },
      { label: '前台二确短信', value: d.sysNoteErke || '-' },
    ],
    monthly: [
      { label: '收费方式', value: label('chargeType', 'chargeType') },
      { label: '套餐固定费', value: d.monthlyFee ? `${d.monthlyFee}元` : '-' },
      {
        label: '收费科目',
        value: { '001': '通讯费', '002': '增值业务费', '003': '代收费' }[d.feeSubject] || '-',
      },
      { label: '税率', value: d.taxRate ? `${d.taxRate}%` : '-' },
    ],
    flow: [
      { label: '流量类型', value: label('flowType', 'flowType') },
      { label: '资源量', value: d.flowAmount ? `${d.flowAmount} ${d.flowUnit}` : '-' },
      { label: '套外计费标准', value: d.flowOvercharge || '-' },
      { label: '是否结转到次月', value: d.flowCarryover === 'yes' ? '是' : '否' },
    ],
    voice: [
      { label: '资源类型', value: label('voiceType', 'voiceType') },
      { label: '资源量', value: d.voiceAmount ? `${d.voiceAmount} 分钟` : '-' },
      { label: '套外计费费率', value: d.voiceOvercharge || '-' },
    ],
    smsRes: [
      { label: '资源量', value: d.smsAmount ? `${d.smsAmount} 条` : '-' },
      { label: '套外计费费率', value: d.smsOvercharge || '-' },
    ],
  }
})
</script>

<template>
  <div class="form-root">
    <div v-show="activeTab === 'base'" class="tab-pane">
      <section class="form-section">
        <button type="button" class="section-header open" :class="{ open: openSections.base }" @click="toggle('base')">
          <span class="section-title"><i class="fa-solid fa-file-lines" /> 基本信息</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.base" class="section-content">
          <div class="form-row">
            <label class="form-label">工单编号 <span class="ai-badge">AI推荐</span></label>
            <input
              :class="['form-input', modClass('workOrderId')]"
              :value="formData.workOrderId"
              @input="onInput('workOrderId', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label"><span class="required">*</span>资费名称</label>
            <input
              :class="['form-input', modClass('prodPrcName')]"
              :value="formData.prodPrcName"
              placeholder="请输入资费名称"
              @input="onInput('prodPrcName', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label"><span class="required">*</span>订购生效方式</label>
            <select :class="['form-select', modClass('effRuleId')]" :value="formData.effRuleId" @change="onSelect('effRuleId', $event)">
              <option value="1001">立即生效</option>
              <option value="1002">次月生效</option>
              <option value="1003">预约生效</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">退订生效方式</label>
            <select :class="['form-select', modClass('expRuleId')]" :value="formData.expRuleId" @change="onSelect('expRuleId', $event)">
              <option value="1023">立即失效</option>
              <option value="1024">次月失效</option>
            </select>
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label class="form-label">销售开始日期 <span class="ai-badge">AI推荐</span></label>
              <input type="date" :class="['form-input', modClass('effDate')]" :value="formData.effDate" @input="onInput('effDate', $event)" />
            </div>
            <div class="form-row">
              <label class="form-label">销售终止日期 <span class="ai-badge">AI推荐</span></label>
              <input type="date" :class="['form-input', modClass('expDate')]" :value="formData.expDate" @input="onInput('expDate', $event)" />
            </div>
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label class="form-label">产品代码 <span class="ai-badge">AI推荐</span></label>
              <input :class="['form-input', modClass('prodId')]" :value="formData.prodId" @input="onInput('prodId', $event)" />
            </div>
            <div class="form-row">
              <label class="form-label">资费代码 <span class="ai-badge">AI推荐</span></label>
              <input :class="['form-input', modClass('prodPrcId')]" :value="formData.prodPrcId" @input="onInput('prodPrcId', $event)" />
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-show="activeTab === 'release'" class="tab-pane">
      <section class="form-section">
        <button type="button" class="section-header open" @click="toggle('releaseCh')">
          <span class="section-title"><i class="fa-solid fa-globe" /> 发布渠道</span>
          <i class="fa-solid fa-chevron-down section-toggle" :class="{ rotated: openSections.releaseCh }" />
        </button>
        <div v-show="openSections.releaseCh" class="section-content">
          <div class="form-row">
            <label class="form-label">发布渠道</label>
            <select :class="['form-select', modClass('chnClassLimit')]" :value="formData.chnClassLimit" @change="onSelect('chnClassLimit', $event)">
              <option value="A7">全渠道</option>
              <option value="A1">线下营业厅</option>
              <option value="A2">线上渠道</option>
              <option value="A3">代理商</option>
            </select>
          </div>
        </div>
      </section>
      <section class="form-section">
        <button type="button" class="section-header open" @click="toggle('releaseCity')">
          <span class="section-title"><i class="fa-solid fa-map-marker-alt" /> 发布地市</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.releaseCity" class="section-content">
          <div class="form-row">
            <label class="form-label">发布地市</label>
            <select :class="['form-select', modClass('groupId')]" :value="formData.groupId" @change="onSelect('groupId', $event)">
              <option value="10001">四川</option>
              <option value="10002">成都</option>
              <option value="10003">绵阳</option>
              <option value="10004">德阳</option>
              <option value="10005">南充</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">地市明细</label>
            <input
              :class="['form-input', modClass('groupIdMessage')]"
              :value="formData.groupIdMessage"
              placeholder="多个地市用逗号分隔"
              @input="onInput('groupIdMessage', $event)"
            />
          </div>
        </div>
      </section>
    </div>

    <div v-show="activeTab === 'optional'" class="tab-pane">
      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.optional }" @click="toggle('optional')">
          <span class="section-title"
            ><i class="fa-solid fa-file-alt" /> 免填单
            <span class="section-hint">（展示给用户的文案）</span></span
          >
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.optional" class="section-content">
          <div class="form-row">
            <label class="form-label">套餐月费 <span class="ai-badge">AI推荐</span></label>
            <input
              :class="['form-input', modClass('prcMonthFee')]"
              :value="formData.prcMonthFee"
              placeholder="如：99元/月"
              @input="onInput('prcMonthFee', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">包含资源 <span class="ai-badge">AI推荐</span></label>
            <input
              :class="['form-input', modClass('containResource')]"
              :value="formData.containResource"
              placeholder="如：20G流量"
              @input="onInput('containResource', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">超套收费标准 <span class="ai-badge">AI推荐</span></label>
            <input
              :class="['form-input', modClass('chargeStandard')]"
              :value="formData.chargeStandard"
              placeholder="如：0.29元/MB"
              @input="onInput('chargeStandard', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">限定条件</label>
            <textarea
              :class="['form-input', modClass('limitCondition')]"
              rows="2"
              :value="formData.limitCondition"
              @input="onInput('limitCondition', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">其他权益</label>
            <textarea
              :class="['form-input', modClass('otherEquity')]"
              rows="2"
              :value="formData.otherEquity"
              @input="onInput('otherEquity', $event)"
            />
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.sms }" @click="toggle('sms')">
          <span class="section-title"><i class="fa-solid fa-comment-sms" /> 短信提醒</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.sms" class="section-content">
          <div class="form-row">
            <label class="form-label">短信内容</label>
            <textarea
              :class="['form-input', modClass('smsContent')]"
              rows="2"
              :value="formData.smsContent"
              @input="onInput('smsContent', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">成功短信（立即） <span class="ai-badge">AI推荐</span></label>
            <textarea
              :class="['form-input', modClass('sysNoteNow')]"
              rows="2"
              :value="formData.sysNoteNow"
              @input="onInput('sysNoteNow', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">成功短信（预约） <span class="ai-badge">AI推荐</span></label>
            <textarea
              :class="['form-input', modClass('sysNoteNext')]"
              rows="2"
              :value="formData.sysNoteNext"
              @input="onInput('sysNoteNext', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">退订短信 <span class="ai-badge">AI推荐</span></label>
            <textarea
              :class="['form-input', modClass('sysNoteCancle')]"
              rows="2"
              :value="formData.sysNoteCancle"
              @input="onInput('sysNoteCancle', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">前台二确短信</label>
            <textarea
              :class="['form-input', modClass('sysNoteErke')]"
              rows="2"
              :value="formData.sysNoteErke"
              @input="onInput('sysNoteErke', $event)"
            />
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.monthly }" @click="toggle('monthly')">
          <span class="section-title"
            ><i class="fa-solid fa-yen-sign" /> 月租 <span class="section-hint">（定价与账务）</span></span
          >
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.monthly" class="section-content">
          <div class="form-row">
            <label class="form-label">收费方式</label>
            <select :class="['form-select', modClass('chargeType')]" :value="formData.chargeType" @change="onSelect('chargeType', $event)">
              <option value="monthly">月付</option>
              <option value="quarterly">季付</option>
              <option value="yearly">年付</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label"><span class="required">*</span>套餐固定费 <span class="ai-badge">AI推荐</span></label>
            <input
              type="number"
              step="0.01"
              :class="['form-input', modClass('monthlyFee')]"
              :value="formData.monthlyFee"
              placeholder="0.00"
              @input="onInput('monthlyFee', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">收费科目</label>
            <select :class="['form-select', modClass('feeSubject')]" :value="formData.feeSubject" @change="onSelect('feeSubject', $event)">
              <option value="001">通讯费</option>
              <option value="002">增值业务费</option>
              <option value="003">代收费</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">税率</label>
            <select :class="['form-select', modClass('taxRate')]" :value="formData.taxRate" @change="onSelect('taxRate', $event)">
              <option value="6">6%</option>
              <option value="9">9%</option>
              <option value="13">13%</option>
            </select>
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.discount }" @click="toggle('discount')">
          <span class="section-title"><i class="fa-solid fa-tags" /> 账务优惠</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.discount" class="section-content">
          <div class="form-row">
            <label class="form-label">优惠条件</label>
            <input
              :class="['form-input', modClass('discountCondition')]"
              :value="formData.discountCondition"
              @input="onInput('discountCondition', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">优惠类型</label>
            <select :class="['form-select', modClass('discountType')]" :value="formData.discountType" @change="onSelect('discountType', $event)">
              <option value="cash">返费</option>
              <option value="discount">打折</option>
              <option value="free">赠送</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">优惠费用</label>
            <input
              type="number"
              step="0.01"
              :class="['form-input', modClass('discountAmount')]"
              :value="formData.discountAmount"
              @input="onInput('discountAmount', $event)"
            />
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.min }" @click="toggle('min')">
          <span class="section-title"><i class="fa-solid fa-arrow-down-wide-short" /> 保底</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.min" class="section-content">
          <div class="form-row">
            <label class="form-label">保底方式</label>
            <select
              :class="['form-select', modClass('minConsumeType')]"
              :value="formData.minConsumeType"
              @change="onSelect('minConsumeType', $event)"
            >
              <option value="none">无保底</option>
              <option value="monthly">月保底</option>
              <option value="total">总额保底</option>
            </select>
          </div>
          <div v-show="showMinConsumeAmount" class="form-row">
            <label class="form-label">保底费用</label>
            <input
              type="number"
              step="0.01"
              :class="['form-input', modClass('minConsumeAmount')]"
              :value="formData.minConsumeAmount"
              @input="onInput('minConsumeAmount', $event)"
            />
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.flow }" @click="toggle('flow')">
          <span class="section-title"><i class="fa-solid fa-mobile-screen" /> 流量资源配置</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.flow" class="section-content">
          <div class="form-row">
            <label class="form-label">流量类型</label>
            <select :class="['form-select', modClass('flowType')]" :value="formData.flowType" @change="onSelect('flowType', $event)">
              <option value="general">通用流量</option>
              <option value="定向">定向流量</option>
              <option value="专属">专属流量</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label"><span class="required">*</span>资源量 <span class="ai-badge">AI推荐</span></label>
            <input :class="['form-input', modClass('flowAmount')]" :value="formData.flowAmount" @input="onInput('flowAmount', $event)" />
          </div>
          <div class="form-row">
            <label class="form-label">流量单位</label>
            <select :class="['form-select', modClass('flowUnit')]" :value="formData.flowUnit" @change="onSelect('flowUnit', $event)">
              <option value="GB">GB</option>
              <option value="MB">MB</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">套外计费标准 <span class="ai-badge">AI推荐</span></label>
            <input
              :class="['form-input', modClass('flowOvercharge')]"
              :value="formData.flowOvercharge"
              placeholder="如：0.29元/MB"
              @input="onInput('flowOvercharge', $event)"
            />
          </div>
          <div class="form-row">
            <label class="form-label">是否结转到次月</label>
            <select
              :class="['form-select', modClass('flowCarryover')]"
              :value="formData.flowCarryover"
              @change="onSelect('flowCarryover', $event)"
            >
              <option value="yes">是</option>
              <option value="no">否</option>
            </select>
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.voice }" @click="toggle('voice')">
          <span class="section-title"><i class="fa-solid fa-phone" /> 语音资源配置</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.voice" class="section-content">
          <div class="form-row">
            <label class="form-label">资源类型</label>
            <select :class="['form-select', modClass('voiceType')]" :value="formData.voiceType" @change="onSelect('voiceType', $event)">
              <option value="local">本地语音</option>
              <option value="longdistance">长途语音</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label">资源量 <span class="ai-badge">AI推荐</span></label>
            <input :class="['form-input', modClass('voiceAmount')]" :value="formData.voiceAmount" @input="onInput('voiceAmount', $event)" />
          </div>
          <div class="form-row">
            <label class="form-label">套外计费费率</label>
            <input
              :class="['form-input', modClass('voiceOvercharge')]"
              :value="formData.voiceOvercharge"
              placeholder="如：0.15元/分钟"
              @input="onInput('voiceOvercharge', $event)"
            />
          </div>
        </div>
      </section>

      <section class="form-section">
        <button type="button" class="section-header" :class="{ open: openSections.smsRes }" @click="toggle('smsRes')">
          <span class="section-title"><i class="fa-solid fa-comment-dots" /> 短信资源配置</span>
          <i class="fa-solid fa-chevron-down section-toggle" />
        </button>
        <div v-show="openSections.smsRes" class="section-content">
          <div class="form-row">
            <label class="form-label">资源量 <span class="ai-badge">AI推荐</span></label>
            <input :class="['form-input', modClass('smsAmount')]" :value="formData.smsAmount" @input="onInput('smsAmount', $event)" />
          </div>
          <div class="form-row">
            <label class="form-label">套外计费费率</label>
            <input
              :class="['form-input', modClass('smsOvercharge')]"
              :value="formData.smsOvercharge"
              @input="onInput('smsOvercharge', $event)"
            />
          </div>
        </div>
      </section>
    </div>

    <div v-show="activeTab === 'summary'" class="tab-pane summary-pane">
      <section v-for="(items, key) in summarySections" :key="key" class="form-section">
        <button type="button" class="section-header open static" disabled>
          <span class="section-title">
            <i v-if="key === 'base'" class="fa-solid fa-file-lines" />
            <i v-else-if="key === 'release'" class="fa-solid fa-globe" />
            <i v-else-if="key === 'optional'" class="fa-solid fa-file-alt" />
            <i v-else-if="key === 'sms'" class="fa-solid fa-comment-sms" />
            <i v-else-if="key === 'monthly'" class="fa-solid fa-yen-sign" />
            <i v-else-if="key === 'flow'" class="fa-solid fa-mobile-screen" />
            <i v-else-if="key === 'voice'" class="fa-solid fa-phone" />
            <i v-else-if="key === 'smsRes'" class="fa-solid fa-comment-dots" />
            {{ summaryTitles[key] }}
          </span>
        </button>
        <div class="section-content open">
          <div class="summary-list">
            <div v-for="item in items" :key="item.label" class="summary-item">
              <span class="label">{{ item.label }}</span>
              <span class="value">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.form-root {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.form-section {
  background: var(--surface);
  border-radius: var(--radius-md);
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}

.section-header {
  width: 100%;
  padding: 14px 16px;
  background: var(--surface-elevated);
  border: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  font-size: 0.875rem;
  color: inherit;
}

.section-header.static {
  cursor: default;
  font-weight: 600;
  text-transform: capitalize;
}

.section-header:hover:not(.static) {
  background: var(--surface-muted);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.section-hint {
  font-weight: normal;
  color: #999;
  font-size: 12px;
}

.section-toggle {
  color: var(--text-muted);
  transition: transform var(--transition);
}

.section-toggle.rotated,
.section-header.open .section-toggle {
  transform: rotate(180deg);
}

.section-content {
  padding: 16px;
}

.form-row {
  margin-bottom: 14px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.required {
  color: var(--error);
}

.ai-badge {
  font-size: 0.625rem;
  padding: 2px 6px;
  background: var(--warning);
  color: white;
  border-radius: 3px;
}

.form-input,
.form-select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-family: inherit;
  transition: var(--transition);
}

.form-input:focus,
.form-select:focus {
  border-color: var(--primary);
}

.form-input.modified,
.form-select.modified {
  border-color: var(--warning);
  background: var(--warning-bg);
}

.summary-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-item {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
}

.summary-item .label {
  width: 130px;
  flex-shrink: 0;
  color: var(--text-secondary);
}

.summary-item .value {
  flex: 1;
  font-weight: 500;
  word-break: break-word;
}

/* —— 手机端表单适配 —— */
@media (max-width: 767px) {
  .form-root {
    min-width: 0;
    width: 100%;
    padding-bottom: 8px;
  }

  .form-section {
    margin-bottom: 12px;
    border-radius: var(--radius);
  }

  .section-header {
    padding: 14px 12px;
    min-height: 48px;
    text-align: left;
  }

  .section-header.static {
    text-transform: none;
  }

  .section-title {
    flex: 1;
    flex-wrap: wrap;
    align-items: flex-start;
    line-height: 1.4;
    font-size: 0.875rem;
    min-width: 0;
  }

  .section-hint {
    flex-basis: 100%;
    margin-top: 2px;
  }

  .section-toggle {
    flex-shrink: 0;
    margin-left: 8px;
    padding: 4px;
  }

  .section-content {
    padding: 12px;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .form-grid .form-row {
    margin-bottom: 14px;
  }

  .form-row {
    margin-bottom: 16px;
  }

  .form-label {
    font-size: 0.875rem;
    margin-bottom: 8px;
    flex-wrap: wrap;
    line-height: 1.35;
  }

  .form-input,
  .form-select,
  textarea.form-input {
    font-size: 16px;
    min-height: 44px;
    padding: 12px 14px;
    border-radius: 8px;
    max-width: 100%;
  }

  textarea.form-input {
    min-height: 88px;
    resize: vertical;
    line-height: 1.5;
  }

  select.form-select {
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath fill='%23666' d='M1 1l5 5 5-5'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 14px center;
    padding-right: 40px;
  }

  input[type='date'].form-input,
  input[type='number'].form-input {
    min-height: 44px;
  }

  .ai-badge {
    font-size: 0.625rem;
  }

  .summary-pane .section-header.static {
    padding: 12px;
  }

  .summary-item {
    flex-direction: column;
    gap: 4px;
    padding: 12px;
  }

  .summary-item .label {
    width: auto;
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .summary-item .value {
    font-size: 0.875rem;
    line-height: 1.45;
  }
}
</style>
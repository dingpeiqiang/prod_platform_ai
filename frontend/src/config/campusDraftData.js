/**
 * campusDraftData - 校园青春卡 · 对话式配置数据
 *
 * 场景：新建一档全新套餐「校园青春卡」，**无任何原套餐可参考**。
 * 因此本场景所有字段的 AI 推荐理由均基于「用户需求 + 业务模板」生成，
 * 刻意规避"沿用原套餐产品属性分类 / 复用原套餐XX"等表述 —— 这类表述只有在
 * 「存量产品复制配置 / 启动产品优化」这类确有原套餐参考的场景才合适。
 *
 * 字段标签规则（与 launchOptimizeData.js 一致）：
 * - tag: 'ai'     → AI推荐（依据用户需求或业务模板生成，附 tagReason 说明推荐原因）
 * - tag: 'reuse'  → 建议由 AI 按业务模板直接补全（对话式/批量场景下渲染为"AI推荐"，不显示"复用"）
 * - tag: null     → 无标签（常规字段）
 *
 * 结构对齐 launchOptimizeData：productInfo / baseInfo / attrInfo / resourceSections /
 * previewSections。
 */
import { reactive } from 'vue'

// ============ 校园青春卡 · 对话式配置的产品配置数据 ============
export const campusProductConfig = {
  // 产品基本信息
  productInfo: reactive({
    workOrderId: '',
    prodPrcName: '校园青春卡',
    template: 'personMainPrc',
    templateLabel: '个人主资费',
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 基础信息 Tab
  baseInfo: {
    productType: {
      value: 'main',
      tag: 'ai',
      tagReason: '根据用户定位判定为预付费主套餐产品',
      required: true,
      label: '产品类型',
      options: [
        { value: 'main', label: '主套餐' },
        { value: 'addon', label: '附加包' },
      ],
    },
    cardType: {
      value: 'voice',
      tag: 'ai',
      tagReason: '预付费主套餐默认语音卡，支持语音+数据业务',
      required: true,
      label: '卡类型',
      productTypes: ['main'],
      options: [
        { value: 'voice', label: '语音卡' },
        { value: 'data', label: '数据卡' },
      ],
    },
    networkType: {
      value: '5g',
      tag: 'ai',
      tagReason: '预付费主套餐默认支持5G网络',
      required: true,
      label: '网络类型',
      productTypes: ['main'],
      options: [
        { value: '4g', label: '4G' },
        { value: '5g', label: '5G' },
      ],
    },
    openTemplate: {
      value: 'person',
      tag: 'ai',
      tagReason: '按业务模板默认选择个人开户模版',
      label: '开户模版',
      productTypes: ['main'],
      options: [
        { value: 'person', label: '个人开户模版' },
        { value: 'group', label: '集团开户模版' },
      ],
    },
    roamTemplate: {
      value: 'CMCC-ROAM-GLOBAL',
      tag: 'ai',
      tagReason: '按业务模板配置默认漫游模版',
      label: '漫游模版',
      productTypes: ['main'],
    },
    prodCode: {
      value: 'SP20240590Q',
      tag: 'ai',
      tagReason: '基于原产品SP-2024-0590关联生成新产品编码',
      label: '产品编码',
      productTypes: ['main'],
    },
    prodName: {
      value: '畅享59元青春版',
      tag: 'ai',
      tagReason: '基于方案A命名策略：保留品牌延续性，定位年轻客群',
      required: true,
      label: '名称',
    },
    description: {
      value: '',
      tag: 'ai',
      tagReason: '根据用户需求自动生成套餐描述',
      label: '描述',
      type: 'textarea',
    },
    publishTenant: {
      value: ['cmcc-sichuan'],
      type: 'dropdown-multi',
      tag: 'ai',
      tagReason: '默认发布到四川分公司的租户域',
      required: true,
      label: '发布租户',
      options: [
        { value: 'cmcc-sichuan', label: '四川分公司' },
        { value: 'cmcc-guangdong', label: '广东分公司' },
        { value: 'cmcc-beijing', label: '北京分公司' },
      ],
    },
    cancelRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '默认退订立即失效',
      label: '退订规则',
      productTypes: ['addon'],
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    orderAddon: {
      value: '',
      tag: 'ai',
      tagReason: '主套餐作为母体，可挂载订购其他附加包',
      label: '订购附加产品',
      productTypes: ['main'],
      options: [
        { value: '', label: '请选择' },
        { value: 'flowAddon', label: '流量加油包' },
        { value: 'voiceAddon', label: '语音加油包' },
        { value: 'smsAddon', label: '短信加油包' },
      ],
    },
    ownerProduct: {
      value: '',
      tag: 'ai',
      tagReason: '指定该附加包挂载归属的主套餐',
      label: '归属产品',
      productTypes: ['addon'],
      options: [
        { value: '', label: '请选择' },
        { value: '畅享59yuan', label: '畅享59元青春版' },
        { value: 'campus', label: '校园青春卡' },
      ],
    },
  },
  attrInfo: {
    chargeType: {
      value: 'periodic',
      tag: 'ai',
      tagReason: '预付费套餐资费类型默认为周期性',
      required: true,
      label: '资费类型',
      options: [
        { value: 'periodic', label: '周期性' },
        { value: 'once', label: '一次性' },
      ],
    },
    validityAttr: {
      value: 'naturalDay',
      tag: 'ai',
      tagReason: '附加包有效期属性默认为自然天',
      label: '有效期属性',
      productTypes: ['addon'],
      options: [
        { value: 'naturalDay', label: '自然天' },
        { value: 'dynamicDay', label: '动态天' },
      ],
    },
    resetCycle: {
      value: 'naturalMonth',
      tag: 'ai',
      tagReason: '资源重置周期默认为自然月',
      required: true,
      label: '资源重置周期',
      options: [
        { value: 'naturalMonth', label: '自然月' },
        { value: 'fixedDays', label: '固定天数' },
      ],
    },
    validity: {
      value: 'naturalMonth',
      tag: 'ai',
      tagReason: '产品有效期默认为自然月',
      required: true,
      label: '产品有效期',
      options: [
        { value: 'naturalMonth', label: '自然月' },
        { value: '3d', label: '3天' },
        { value: '6d', label: '6天' },
        { value: '30d', label: '30天' },
      ],
    },
    isActive: {
      value: 'yes',
      tag: 'ai',
      tagReason: '默认激活生效',
      label: '是否激活',
      productTypes: ['addon'],
      options: [
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    extendLifecycle: {
      value: 'no',
      tag: 'ai',
      tagReason: '默认不延长生命周期',
      label: '延长生命周期',
      productTypes: ['addon'],
      options: [
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    repeatOrder: {
      value: 'yes',
      tag: 'ai',
      tagReason: '默认允许重复订购',
      label: '重复订购',
      productTypes: ['addon'],
      options: [
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    wakeupActiveDays: {
      value: '6',
      tag: 'ai',
      tagReason: '默认唤醒激活天数6天',
      label: '唤醒激活(天)',
      productTypes: ['addon'],
      options: [
        { value: '3', label: '3' },
        { value: '6', label: '6' },
        { value: '10', label: '10' },
        { value: '30', label: '30' },
      ],
    },
    extendDays: {
      value: '1',
      tag: 'ai',
      tagReason: '默认延长期限1天',
      label: '延长期限(天)',
      productTypes: ['addon'],
      unit: '天',
    },
  },
  resourceSections: [
    {
      key: 'data-resource',
      title: '数据资源',
      icon: 'fa-mobile-screen',
      fields: [
        {
          key: 'dataAmount',
          label: '资源量',
          value: '20',
          type: 'number',
          unit: 'GB',
          tag: 'ai',
          tagReason: '预付费套餐默认配置20GB数据资源，满足基础上网需求',
        },
        {
          key: 'dataRoamArea',
          label: '漫游地',
          value: ['domestic', 'hk-mo-tw'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '默认开通大陆及港澳台漫游',
          options: [
            { value: 'domestic', label: '大陆' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亚洲' },
            { value: 'europe', label: '欧洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'throttlePolicy',
          label: '限速策略',
          value: 'pcrf',
          type: 'select',
          tag: 'ai',
          tagReason: '默认采用PCRF限速策略，保障网络公平使用',
          options: [
            { value: 'pcrf', label: 'PCRF' },
            { value: 'gx', label: 'GX' },
          ],
        },
        {
          key: 'throttleTemplate',
          label: '限速模版',
          type: 'table',
          tag: 'ai',
          tagReason: '按业务模板默认配置限速规则（动态行，可新增）',
          rows: [
            { maxRate: '1Mbps', triggerAmount: '20GB', remark: '达量后限速1Mbps' },
            { maxRate: '0.5Mbps', triggerAmount: '50GB', remark: '高量用户限速0.5Mbps' },
          ],
          columns: [
            { key: 'maxRate', label: '限速速率', type: 'text', placeholder: '如 1Mbps' },
            { key: 'triggerAmount', label: '触发资源量', type: 'text', placeholder: '如 20GB' },
            { key: 'remark', label: '说明', type: 'text', placeholder: '限速说明' },
          ],
        },
      ],
    },
    {
      key: 'voice-resource',
      title: '语音资源',
      icon: 'fa-phone',
      fields: [
        {
          key: 'voiceAmount',
          label: '资源量',
          value: '100',
          type: 'number',
          unit: '分钟',
          tag: 'ai',
          tagReason: '默认配置100分钟语音资源，便于国际间互通',
        },
        {
          key: 'voiceRoamArea',
          label: '漫游地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '语音漫游默认仅限大陆使用',
          options: [
            { value: 'domestic', label: '大陆' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亚洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'voicePeerArea',
          label: '对端归属地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '对端归属地默认为大陆',
          options: [
            { value: 'domestic', label: '大陆' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亚洲' },
            { value: 'global', label: '全球' },
          ],
        },
      ],
    },
    {
      key: 'sms-resource',
      title: '短信资源',
      icon: 'fa-comment-dots',
      fields: [
        {
          key: 'smsAmount',
          label: '资源量',
          value: '50',
          type: 'number',
          unit: '条',
          tag: 'ai',
          tagReason: '默认配置50条短信资源',
        },
        {
          key: 'smsRoamArea',
          label: '漫游地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '短信漫游默认仅限大陆',
          options: [
            { value: 'domestic', label: '大陆' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亚洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'smsPeerArea',
          label: '对端归属地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '对端归属地默认为大陆',
          options: [
            { value: 'domestic', label: '大陆' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亚洲' },
            { value: 'global', label: '全球' },
          ],
        },
      ],
    },
  ],
  previewSections: [
    {
      key: 'user-facing',
      title: '用户受理信息',
      icon: 'fa-user',
      description: '用户在办理过程中看到的关键信息',
      items: [
        { label: '产品名称', value: '校园青春卡', highlight: true },
        { label: '月费', value: '29元/月' },
        { label: '包含流量', value: '30GB通用流量', highlight: true },
        { label: '包含语音', value: '100分钟本地语音' },
        { label: '包含短信', value: '50条' },
        { label: '定向免流', value: '校园宽带与腾讯系视频App在校内免流', highlight: true },
        { label: '套外流量费', value: '1元/GB' },
        { label: '套外语音费', value: '0.15元/分钟' },
        { label: '套外短信费', value: '0.1元/条' },
        { label: '生效方式', value: '立即生效' },
        { label: '退订方式', value: '立即失效' },
        { label: '使用范围', value: '四川省范围内' },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '免填单',
      icon: 'fa-file-alt',
      description: '营业员受理时免填的自动带出信息',
      items: [
        { label: '资费名称', value: '校园青春卡' },
        { label: '套餐月费', value: '29元/月' },
        { label: '包含资源', value: '30G流量+100分钟语音+50条短信+定向免流', highlight: true },
        { label: '超套收费标准', value: '流量1元/GB、语音0.15元/分钟、短信0.1元/条' },
        { label: '限定条件', value: '仅限在校大学生、需学生证认证、本人实名办理、每人限办1张' },
        { label: '其他权益', value: '定向免流（校园宽带+腾讯系视频App）、视频会员月卡', highlight: true },
        { label: '收费方式', value: '月付' },
        { label: '套餐固定费', value: '29.00元' },
        { label: '税率', value: '6%' },
        { label: '续订方式', value: '自动续订' },
      ],
    },
    {
      key: 'sms-preview',
      title: '短信模板预览',
      icon: 'fa-comment-sms',
      description: '各触点下发的短信内容预览',
      items: [
        { label: '办理成功短信', value: '恭喜您成功办理校园青春卡，月费29元，含30G流量、100分钟语音和50条短信，含校园定向免流权益，感谢您的支持！' },
        { label: '预约生效短信', value: '您的校园青春卡已生效，月费29元，生效当月起享受套餐权益。' },
        { label: '前台二确短信', value: '尊敬的客户，您正在办理校园青春卡，月费29元，是否确认办理？' },
        { label: '退订短信', value: '您好，您的校园青春卡已退订，如有疑问请致电10086。' },
      ],
    },
  ],
}

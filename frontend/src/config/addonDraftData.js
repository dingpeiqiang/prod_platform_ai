/**
 * addonDraftData - 可选包（附加包）· 对话式配置数据
 *
 * 场景：新建可选包产品（在个人主资费套餐上叠加的数据/语音/短信可选包）。
 * 可选包挂在某个主套餐下（归属产品），资费类型为周期性或一次性，资源按行新增，
 * 漫游地/对端归属地支持多选。
 *
 * 字段标签规则（与 launchOptimizeData.js 一致）：
 * - tag: 'ai'     → AI推荐（依据用户需求或业务模板生成，附 tagReason 说明推荐原因）
 * - tag: 'reuse'  → 建议由 AI 按业务模板直接补全（对话式场景下渲染为"AI推荐"）
 * - tag: null     → 无标签（常规字段）
 *
 * 结构对齐 launchOptimizeData/campusDraftData：productInfo / baseInfo / attrInfo /
 * resourceSections / previewSections。
 */
import { reactive } from 'vue'

// ============ 可选包 · 对话式配置的产品配置数据 ============
export const addonProductConfig = {
  // 产品基本信息
  productInfo: reactive({
    workOrderId: '',
    prodPrcName: 'WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)',
    template: 'addon',
    templateLabel: '可选包',
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 基础信息 Tab
  baseInfo: {
    productType: {
      value: 'addon',
      tag: 'ai',
      tagReason: '根据用户定位判定为附加包（可选包）产品',
      required: true,
      label: '产品类型',
      options: [
        { value: 'main', label: '主套餐' },
        { value: 'addon', label: '附加包' },
      ],
    },
    cardType: {
      value: 'data',
      tag: 'ai',
      tagReason: '可选包承载数据业务，默认数据卡类型',
      label: '卡类型',
      productTypes: ['addon'],
      options: [
        { value: 'voice', label: '语音卡' },
        { value: 'data', label: '数据卡' },
      ],
    },
    roamTemplate: {
      value: 'CMCC-ROAM-HK-TW',
      tag: 'ai',
      tagReason: '可选包漫游范围为澳门.香港.台湾共用数据，按漫游模版配置',
      label: '漫游模版',
      productTypes: ['addon'],
    },
    prodCode: {
      value: 'W23062000001',
      tag: 'ai',
      tagReason: '基于 MVNE 可选包编码规范生成产品编码',
      label: '产品编码',
      productTypes: ['addon'],
    },
    prodName: {
      value: 'WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)',
      tag: 'ai',
      tagReason: '沿用 WeWalk Data Only 系列命名规范：地区+共用+有效期+高速配额',
      required: true,
      label: '名称',
    },
    description: {
      value: 'WeWalk 澳门.香港.台湾共用2日不限量数据可选包：800MB/日高速流量，用尽后不限量限速，开通澳门.香港.台湾共用漫游，Data Only 纯流量产品。',
      tag: 'ai',
      tagReason: '根据用户需求自动生成可选包描述',
      label: '描述',
      type: 'textarea',
    },
    publishTenant: {
      value: ['China Unicom Global'],
      type: 'dropdown-multi',
      tag: 'ai',
      tagReason: '面向国际漫游客户，默认发布到 China Unicom Global 租户',
      required: true,
      label: '发布租户',
      options: [
        { value: 'China Unicom Global', label: 'China Unicom Global' },
        { value: '租户1', label: '租户1' },
        { value: '租户2', label: '租户2' },
      ],
    },
    cancelRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '默认退订立即失效',
      required: true,
      label: '退订规则',
      productTypes: ['addon'],
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    ownerProduct: {
      value: 'wewalk-hk-tw-30d',
      tag: 'ai',
      tagReason: '指定该可选包挂载归属的主套餐',
      label: '归属产品',
      productTypes: ['addon'],
      options: [
        { value: '', label: '请选择' },
        { value: 'wewalk-hk-tw-30d', label: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)' },
        { value: 'campus', label: '校园青春卡' },
      ],
    },
  },

  attrInfo: {
    chargeType: {
      value: 'once',
      tag: 'ai',
      tagReason: '2日可选包为一次性付费产品',
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
      tagReason: '可选包有效期属性默认为自然天',
      label: '有效期属性',
      productTypes: ['addon'],
      showWhen: { key: 'chargeType', value: 'once' },
      options: [
        { value: 'naturalDay', label: '自然天' },
        { value: 'dynamicDay', label: '动态天' },
      ],
    },
    resetCycle: {
      value: 'fixedDays',
      tag: 'ai',
      tagReason: '一次性2日可选包，资源按固定天数有效不重置',
      required: true,
      label: '资源重置周期',
      showWhen: { key: 'chargeType', value: 'periodic' },
      options: [
        { value: 'naturalMonth', label: '自然月' },
        { value: 'fixedDays', label: '固定天数' },
      ],
    },
    validity: {
      value: '2d',
      tag: 'ai',
      tagReason: '产品有效期为自然天2日（澳门.香港.台湾共用不限量）',
      required: true,
      label: '产品有效期',
      options: [
        { value: 'naturalMonth', label: '自然月' },
        { value: '2d', label: '2天' },
        { value: '1d', label: '1天' },
        { value: '3d', label: '3天' },
        { value: '6d', label: '6天' },
        { value: '15d', label: '15天' },
        { value: '30d', label: '30天' },
      ],
    },
    isActive: {
      value: 'yes',
      tag: 'ai',
      tagReason: '默认激活生效',
      required: true,
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
      required: true,
      label: '延长生命周期',
      productTypes: ['addon'],
      options: [
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    wakeupActiveDays: {
      value: '6',
      tag: 'ai',
      tagReason: '默认最晚激活天数6天',
      required: true,
      label: '最晚激活(天)',
      productTypes: ['addon'],
      showWhen: { key: 'isActive', value: 'yes' },
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
      required: true,
      label: '延长期限(天)',
      productTypes: ['addon'],
      showWhen: { key: 'extendLifecycle', value: 'yes' },
      unit: '天',
    },
    repeatOrder: {
      value: 'yes',
      tag: 'ai',
      tagReason: '默认允许重复订购',
      required: true,
      label: '重复订购',
      productTypes: ['addon'],
      options: [
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
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
          value: '800',
          type: 'number',
          unit: 'MB/日',
          required: true,
          tag: 'ai',
          tagReason: '可选包默认配置800MB/日高速数据资源',
        },
        {
          key: 'dataRoamArea',
          label: '漫游地',
          value: ['hk-mo-tw'],
          type: 'checkbox-group',
          required: true,
          tag: 'ai',
          tagReason: '可选包漫游范围仅为澳门.香港.台湾共用数据',
          options: [
            { value: 'hk-mo-tw', label: '澳门.香港.台湾' },
            { value: 'hk-tw', label: '香港.台湾' },
            { value: 'domestic', label: '大陆' },
            { value: 'asia', label: '亚洲' },
            { value: 'europe', label: '欧洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'throttlePolicy',
          label: '限速策略',
          value: 'gx',
          type: 'select',
          tag: 'ai',
          tagReason: '可选包采用 GX 限速策略：800MB/日高速用尽后降至低速',
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
            { maxRate: '1Mbps', triggerAmount: '800MB', remark: '800MB/日高速用尽后限速1Mbps不限量' },
          ],
          columns: [
            { key: 'maxRate', label: '限速速率', type: 'text', placeholder: '如 1Mbps' },
            { key: 'triggerAmount', label: '触发资源量', type: 'text', placeholder: '如 800MB' },
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
          key: 'voiceResourceTable',
          label: '语音资源',
          type: 'table',
          tag: 'ai',
          tagReason: '语音资源按行新增，漫游地/对端归属地支持多选',
          rows: [],
          columns: [
            { key: 'voiceAmount', label: '资源量（分钟）', type: 'text', placeholder: '如 100' },
            {
              key: 'voiceRoamArea',
              label: '漫游地',
              type: 'multiselect',
              options: [
                { value: 'hk-mo-tw', label: '港澳台日' },
                { value: 'singapore', label: '新加坡' },
                { value: 'vietnam', label: '越南' },
                { value: 'thailand', label: '泰国' },
                { value: 'malaysia', label: '马来西亚' },
                { value: 'korea', label: '韩国' },
                { value: 'us', label: '美国' },
                { value: 'canada', label: '加拿大' },
                { value: 'australia', label: '澳大利亚' },
                { value: 'asia', label: '亚洲' },
                { value: 'europe', label: '欧洲' },
                { value: 'global', label: '全球' },
              ],
            },
            {
              key: 'voicePeerArea',
              label: '对端归属地',
              type: 'multiselect',
              options: [
                { value: 'unlimited', label: '无限制' },
                { value: 'domestic', label: '大陆' },
                { value: 'hk-mo-tw', label: '港澳台' },
                { value: 'singapore', label: '新加坡' },
                { value: 'vietnam', label: '越南' },
                { value: 'global', label: '全球' },
              ],
            },
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
          key: 'smsResourceTable',
          label: '短信资源',
          type: 'table',
          tag: 'ai',
          tagReason: '短信资源按行新增，漫游地/对端归属地支持多选',
          rows: [],
          columns: [
            { key: 'smsAmount', label: '资源量（条）', type: 'text', placeholder: '如 50' },
            {
              key: 'smsRoamArea',
              label: '漫游地',
              type: 'multiselect',
              options: [
                { value: 'hk-mo-tw', label: '港澳台日' },
                { value: 'singapore', label: '新加坡' },
                { value: 'vietnam', label: '越南' },
                { value: 'thailand', label: '泰国' },
                { value: 'malaysia', label: '马来西亚' },
                { value: 'korea', label: '韩国' },
                { value: 'us', label: '美国' },
                { value: 'canada', label: '加拿大' },
                { value: 'australia', label: '澳大利亚' },
                { value: 'asia', label: '亚洲' },
                { value: 'europe', label: '欧洲' },
                { value: 'global', label: '全球' },
              ],
            },
            {
              key: 'smsPeerArea',
              label: '对端归属地',
              type: 'multiselect',
              options: [
                { value: 'unlimited', label: '无限制' },
                { value: 'domestic', label: '大陆' },
                { value: 'hk-mo-tw', label: '港澳台' },
                { value: 'singapore', label: '新加坡' },
                { value: 'vietnam', label: '越南' },
                { value: 'global', label: '全球' },
              ],
            },
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
        { label: '产品名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)', highlight: true },
        { label: '卡类型', value: '5G数据卡（Data Only）' },
        { label: '资费类型', value: '一次性' },
        { label: '高速配额', value: '800MB/日高速流量', highlight: true },
        { label: '超出后', value: '不限量（降至低速）' },
        { label: '漫游地', value: '澳门.香港.台湾 共用', highlight: true },
        { label: '限速策略', value: 'GX（800MB/日高速用尽后限速不限量）' },
        { label: '产品有效期', value: '2天（自然天）' },
        { label: '生效方式', value: '激活即生效' },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '免填单',
      icon: 'fa-file-alt',
      description: '营业员受理时免填的自动带出信息',
      items: [
        { label: '资费名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)' },
        { label: '产品类型', value: '预付费数据卡可选包' },
        { label: '归属产品', value: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)' },
        { label: '包含资源', value: '800MB/日高速流量', highlight: true },
        { label: '超套收费标准', value: '高速用尽后降至低速不限量' },
        { label: '收费方式', value: '一次性' },
      ],
    },
    {
      key: 'sms-preview',
      title: '短信模板预览',
      icon: 'fa-comment-sms',
      description: '各触点下发的短信内容预览',
      items: [
        { label: '办理成功短信', value: '恭喜您成功办理WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)可选包，含800MB/日高速流量，感谢您的支持！' },
        { label: '预约生效短信', value: '您的WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)可选包已生效，生效当日起享受套餐权益。' },
        { label: '前台二确短信', value: '尊敬的客户，您正在办理WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)可选包，是否确认办理？' },
        { label: '退订短信', value: '您好，您的WeWalk-Data Only-澳门.香港.台湾共用2日不限量(800MB/日高速)可选包已退订，如有疑问请致电客服。' },
      ],
    },
  ],
}

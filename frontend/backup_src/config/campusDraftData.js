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
 * 结构对齐 launchOptimizeData：productInfo / baseInfo / resourceSections /
 * marketingSections / salesSections / previewSections。
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
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '依据产品定位判定为面向学生个人的主资费产品，选择"个人主资费"属性',
      required: true,
      label: '产品属性',
      options: [
        { value: 'personMainPrc', label: '个人主资费' },
        { value: 'personOptionalPrc', label: '个人可选包' },
        { value: 'familyMainPrc', label: '家庭主资费' },
        { value: 'enterprisePrc', label: '政企资费' },
      ],
    },
    prodCode: {
      value: 'SP20260828001',
      tag: 'ai',
      tagReason: '按产品编码规则为新产品生成唯一编码',
      label: '产品编码',
    },
    prodName: {
      value: '校园青春卡',
      tag: 'ai',
      tagReason: '依据用户命名沿用"校园青春卡"作为产品名称，贴合学生客群定位',
      required: true,
      label: '产品名称',
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '建议9月1日开学季上线，契合在校大学生新客获客节奏',
      required: true,
      label: '上线日期',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'ai',
      tagReason: '产品长期有效，无固定下线时间',
      required: true,
      label: '下线日期',
    },
    effRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '按业务模板默认：新入网立即生效，便于学生开卡即用',
      required: true,
      label: '生效规则',
      options: [
        { value: 'immediate', label: '新入网立即/变更次月/续约指定日期' },
        { value: 'nextMonth', label: '次月生效' },
        { value: 'appoint', label: '预约指定日期生效' },
      ],
    },
    validity: {
      value: 'longTerm',
      tag: 'ai',
      tagReason: '依据用户需求"产品长期有效"，设置为长期有效',
      required: true,
      label: '产品有效期',
      options: [
        { value: 'longTerm', label: '长期有效' },
        { value: '12m', label: '12个月' },
        { value: '24m', label: '24个月' },
        { value: '36m', label: '36个月' },
      ],
    },
    displayCycle: {
      value: '3',
      tag: 'ai',
      tagReason: '按业务模板默认展示周期3年',
      required: true,
      label: '展示周期',
      unit: '年',
    },
    cancelRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '按业务模板默认退订立即失效',
      required: true,
      label: '退订生效规则',
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    refProduct: {
      value: '',
      tag: 'ai',
      tagReason: '本档为新产品，无参考产品',
      label: '参考产品',
      options: [
        { value: '', label: '请选择' },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'ai',
      tagReason: '依据用户需求"每位学生限办1张"，同时订购次数为1',
      required: true,
      label: '同时订购次数',
    },
    totalOrderTimes: {
      value: '',
      tag: 'ai',
      tagReason: '按业务模板默认不限制累计订购次数',
      label: '累计订购次数',
      unit: '个月内限订1次',
    },
    uploadYijing: {
      value: 'no',
      tag: 'ai',
      tagReason: '按业务模板默认不上传一经',
      label: '是否上传一经',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    l1ProdCode: {
      value: 'APAP202608',
      tag: 'ai',
      tagReason: '从产品库按产品属性匹配到可用一级产品编码',
      label: '一级产品编码',
    },
    isComm: {
      value: 'yes',
      tag: 'ai',
      tagReason: '本产品为通信类资费产品',
      required: true,
      label: '是否通信类',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    applyBasis: {
      value: '面向在校大学生的校园专享资费方案',
      tag: 'ai',
      tagReason: '申报依据：校园客群专项资费方案',
      label: '申请依据',
    },
    allowChannel: {
      value: 'yes',
      tag: 'ai',
      tagReason: '按业务模板允许受理渠道办理',
      required: true,
      label: '是否允许受理渠道',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
  },

  // 资源配置 Tab
  resourceSections: [
    {
      key: 'flow-resource',
      title: '流量资源配置',
      icon: 'fa-mobile-screen',
      fields: [
        {
          key: 'flowType',
          label: '流量类型',
          value: 'general',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求"通用流量30GB"，流量类型为通用流量',
          options: [
            { value: 'general', label: '通用流量' },
            { value: '定向', label: '定向流量' },
            { value: '专属', label: '专属流量' },
          ],
        },
        {
          key: 'flowAmount',
          label: '资源量',
          value: '30',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '依据用户需求：通用流量30GB',
        },
        {
          key: 'flowUnit',
          label: '流量单位',
          value: 'GB',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求以GB为单位计费',
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '套外计费标准',
          value: '1元/GB',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户需求：超套后1元/GB按量计费',
        },
        {
          key: 'flowCarryover',
          label: '是否结转到次月',
          value: 'no',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板默认当月流量不结转',
          options: [
            { value: 'yes', label: '是' },
            { value: 'no', label: '否' },
          ],
        },
      ],
    },
    {
      key: 'voice-resource',
      title: '语音资源配置',
      icon: 'fa-phone',
      fields: [
        {
          key: 'voiceType',
          label: '资源类型',
          value: 'local',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求"本地语音100分钟"，资源类型为本地语音',
          options: [
            { value: 'local', label: '本地语音' },
            { value: 'longdistance', label: '长途语音' },
          ],
        },
        {
          key: 'voiceAmount',
          label: '资源量',
          value: '100',
          type: 'number',
          unit: '分钟',
          tag: 'ai',
          tagReason: '依据用户需求：本地语音100分钟',
        },
        {
          key: 'voiceOvercharge',
          label: '套外计费费率',
          value: '0.15元/分钟',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户需求：套外语音0.15元/分钟',
        },
      ],
    },
    {
      key: 'sms-resource',
      title: '短信资源配置',
      icon: 'fa-comment-dots',
      fields: [
        {
          key: 'smsAmount',
          label: '资源量',
          value: '50',
          type: 'number',
          unit: '条',
          tag: 'ai',
          tagReason: '依据用户需求：短信50条',
        },
        {
          key: 'smsOvercharge',
          label: '套外计费费率',
          value: '0.1元/条',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户需求：套外短信0.1元/条',
        },
      ],
    },
    {
      key: 'rights-resource',
      title: '权益资源配置',
      icon: 'fa-gift',
      fields: [
        {
          key: 'rightsTable',
          label: '权益列表',
          type: 'table',
          tag: 'ai',
          tagReason: '依据用户需求含"定向免流权益：校园宽带与腾讯系视频App在校内免流"，自动补充默认权益',
          columns: [
            {
              key: 'rightName',
              label: '权益名称',
              type: 'text',
              placeholder: '请输入权益名称',
            },
            {
              key: 'obtainType',
              label: '领取方式',
              type: 'select',
              options: [
                { value: 'manual', label: '手动领取' },
                { value: 'auto', label: '自动发放' },
              ],
            },
          ],
          rows: [
            { rightName: '定向免流权益（校园宽带+腾讯系视频App校内免流）', obtainType: 'auto' },
            { rightName: '视频会员月卡', obtainType: 'auto' },
          ],
        },
      ],
    },
  ],

  // 营销资源 Tab
  marketingSections: [
    {
      key: 'monthly',
      title: '月租',
      icon: 'fa-yen-sign',
      fields: [
        {
          key: 'chargeType',
          label: '收费方式',
          value: 'monthly',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求"套餐固定费29元/月"，收费方式为月付',
          options: [
            { value: 'monthly', label: '月付' },
            { value: 'quarterly', label: '季付' },
            { value: 'yearly', label: '年付' },
          ],
        },
        { key: 'monthlyFee', label: '套餐固定费', value: '29.00', type: 'number', unit: '元', tag: 'ai', tagReason: '依据用户需求：套餐固定费29元/月' },
        {
          key: 'feeSubject',
          label: '收费科目',
          value: '001',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板归为通讯费科目',
          options: [
            { value: '001', label: '通讯费' },
            { value: '002', label: '增值业务费' },
            { value: '003', label: '代收费' },
          ],
        },
        { key: 'taxRate', label: '税率', value: '6', type: 'number', unit: '%', tag: 'ai', tagReason: '按业务模板采用6%税率' },
      ],
    },
    {
      key: 'discount',
      title: '账务优惠',
      icon: 'fa-tags',
      fields: [
        {
          key: 'discountCondition',
          label: '优惠条件',
          value: '',
          type: 'text',
          tag: 'ai',
          tagReason: '按业务模板该产品暂无账务优惠',
        },
        {
          key: 'discountType',
          label: '优惠类型',
          value: 'cash',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板默认无优惠类型',
          options: [
            { value: 'cash', label: '现金直减' },
            { value: 'coupon', label: '优惠券' },
            { value: 'gift', label: '赠品' },
          ],
        },
        { key: 'discountAmount', label: '优惠费用', value: '', type: 'number', unit: '元', tag: 'ai', tagReason: '按业务模板暂无优惠费用' },
      ],
    },
    {
      key: 'min-consume',
      title: '保底',
      icon: 'fa-arrow-down-wide-short',
      fields: [
        {
          key: 'minConsumeType',
          label: '保底方式',
          value: 'none',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板该产品无保底',
          options: [
            { value: 'none', label: '无保底' },
            { value: 'monthly', label: '月保底' },
            { value: 'total', label: '总额保底' },
          ],
        },
      ],
    },
  ],

  // 销售规则 Tab
  salesSections: [
    {
      key: 'sales-rule',
      title: '销售规则',
      icon: 'fa-shield-halved',
      fields: [
        {
          key: 'channelType',
          label: '渠道类型',
          value: 'offline',
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '依据业务模板默认含线下与线上渠道',
          options: [
            { value: 'offline', label: '线下营业厅' },
            { value: 'online', label: '中国移动在线渠道' },
            { value: 'local-online', label: '省移动线上渠道' },
            { value: 'cmcc-online', label: '中国移动线上渠道' },
            { value: 'tv', label: '电视营业厅' },
            { value: 'accelerator', label: '加速平台' },
            { value: 'points', label: '积分商城' },
            { value: 'meter', label: '一级电表' },
          ],
        },
        {
          key: 'applicableArea',
          label: '适用地区',
          value: 'sc',
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '依据业务模板默认面向全省范围',
          options: [
            { value: 'sc', label: '四川全省' },
            { value: 'cd', label: '成都' },
            { value: 'my', label: '绵阳' },
            { value: 'dy', label: '德阳' },
            { value: 'nc', label: '南充' },
            { value: 'yb', label: '宜宾' },
            { value: 'lz', label: '泸州' },
            { value: 'ls', label: '乐山' },
          ],
        },
        {
          key: 'otherFee',
          label: '其他费用',
          value: '0',
          type: 'text',
          tag: 'ai',
          tagReason: '按业务模板本产品无其他费用',
        },
        {
          key: 'breachLiability',
          label: '违约责任',
          value: 'none',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板本产品无违约责任约束',
          options: [
            { value: 'none', label: '无' },
            { value: 'compensate', label: '按约定补偿金' },
          ],
        },
        {
          key: 'renewMethod',
          label: '续订方式',
          value: 'auto',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求"支持月末自动续订"，续订方式为自动续订',
          options: [
            { value: 'auto', label: '自动续订' },
            { value: 'manual', label: '手动续订' },
            { value: 'no', label: '不续订' },
          ],
        },
      ],
    },
    {
      key: 'pre-limit',
      title: '前项限制',
      icon: 'fa-circle-arrow-left',
      fields: [
        {
          key: 'preLimitRelation',
          label: '限制间关系',
          value: 'and',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户需求多项资格条件需同时满足，采用"且"关系',
          options: [
            { value: 'and', label: '且（全部满足）' },
            { value: 'or', label: '或（任一满足）' },
          ],
        },
        {
          key: 'preLimitTable',
          label: '限制列表',
          type: 'table',
          tag: 'ai',
          tagReason: '依据用户需求：只面向在校大学生、需上传学生证认证、仅限本人实名办理、每位学生限办1张',
          columns: [
            { key: 'label', label: '标签', type: 'select', options: [
              { value: 'age', label: '年龄' },
              { value: 'globalLevel', label: '全球通等级' },
              { value: 'baseMonthlyFee', label: '基础套餐月费' },
              { value: 'targetGroup', label: '目标客群' },
              { value: 'studentOnly', label: '面向在校大学生' },
              { value: 'studentCert', label: '需上传学生证认证' },
              { value: 'realName', label: '仅限本人实名办理' },
              { value: 'limitToSelf', label: '仅限本人办理' },
            ] },
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'gt', label: '＞' },
              { value: 'lt', label: '＜' },
              { value: 'ge', label: '≥' },
              { value: 'le', label: '≤' },
              { value: 'eq', label: '＝' },
            ] },
            { key: 'limitValue', label: '限定值', type: 'text', placeholder: '请输入限定值' },
            { key: 'limitType', label: '限定类型', type: 'select', options: [
              { value: 'allow', label: '允许办理' },
              { value: 'disallow', label: '不允许办理' },
            ] },
            { key: 'tipMsg', label: '提示语', type: 'auto', placeholder: '根据条件自动生成' },
          ],
          rows: [
            { label: 'targetGroup', ruleType: 'eq', limitValue: '在校大学生', limitType: 'allow', tipMsg: '目标客群为在校大学生，允许办理' },
            { label: 'studentCert', ruleType: 'eq', limitValue: '需上传学生证认证', limitType: 'allow', tipMsg: '面向在校大学生，需学生证认证后办理' },
            { label: 'realName', ruleType: 'eq', limitValue: '仅限本人实名', limitType: 'allow', tipMsg: '仅限本人实名办理' },
          ],
        },
      ],
    },
    {
      key: 'post-limit',
      title: '后项限制',
      icon: 'fa-circle-arrow-right',
      fields: [
        {
          key: 'postLimitTable',
          label: '后项限制',
          type: 'table',
          tag: 'ai',
          tagReason: '按业务模板为学生专享卡设置退订与降档限制',
          columns: [
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'product', label: '产品限制' },
              { value: 'business', label: '业务限制' },
            ] },
            { key: 'bizName', label: '业务名称', type: 'select', dynamicOptions: true, options: {
              product: [
                { value: 'downgrade', label: '降档' },
                { value: 'unsubscribe', label: '退订' },
              ],
              business: [
                { value: 'suspend', label: '停机保号' },
                { value: 'cancel', label: '销户' },
                { value: 'mnp', label: '携号转网' },
                { value: 'transfer', label: '过户' },
              ],
            } },
            { key: 'limitType', label: '限制类型', type: 'select', options: [
              { value: 'disallow', label: '不允许' },
              { value: 'allow', label: '允许' },
            ] },
            { key: 'limitTime', label: '限制时间', type: 'select', options: [
              { value: '12', label: '12个月' },
              { value: '24', label: '24个月' },
              { value: '36', label: '36个月' },
              { value: '48', label: '48个月' },
            ] },
          ],
          rows: [
            { ruleType: 'product', bizName: 'downgrade', limitType: 'disallow', limitTime: '12' },
            { ruleType: 'product', bizName: 'unsubscribe', limitType: 'disallow', limitTime: '12' },
          ],
        },
      ],
    },
  ],

  // 信息预览 Tab
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

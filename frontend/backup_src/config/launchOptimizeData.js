/**
 * 第6轮 - 启动产品优化
 * AI助手确认方案A后，调用产品研发助手自动打开配置页面
 * 预填数据基于方案A：畅享59元青春版（原畅享59元套餐优化升级）
 *
 * 字段标签规则：
 * - tag: 'ai'     → AI推荐（新增生成或基于方案修改的值，附 tagReason 说明推荐原因）
 * - tag: 'reuse'  → 复用（从原套餐继承，未做变更的值）
 * - tag: null     → 无标签（常规字段）
 */
import { reactive } from 'vue'

// ============ 方案A预填的产品配置数据 ============
export const launchProductConfig = {
  // 产品基本信息
  productInfo: reactive({
    workOrderId: 'WO20260812001',
    prodPrcName: '畅享59元青春版',
    template: 'personMainPrc',
    templateLabel: '个人主资费',
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 基础信息 Tab —— 字段摘自智家包"基本信息"模块，保持AI工作台排版与AI推荐/复用标签机制
  baseInfo: {
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '方案A定位个人主资费，沿用原套餐产品属性分类',
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
      value: 'SP20240590Q',
      tag: 'ai',
      tagReason: '基于原产品SP-2024-0590关联生成新产品编码',
      label: '产品编码',
    },
    prodName: {
      value: '畅享59元青春版',
      tag: 'ai',
      tagReason: '基于方案A命名策略：保留"畅享59元"品牌延续性，"青春版"定位年轻客群，配合双视频权益+大流量策略',
      required: true,
      label: '产品名称',
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '方案A计划9月1日上线，避开暑期促销高峰，配合秋季开学季获客',
      required: true,
      label: '上线日期',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'reuse',
      tagReason: '复用原套餐长期销售策略',
      required: true,
      label: '下线日期',
    },
    effRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '复用原套餐生效规则：新入网立即生效',
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
      tag: 'reuse',
      tagReason: '复用原套餐长期有效策略',
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
      tag: 'reuse',
      tagReason: '复用原套餐展示周期3年',
      required: true,
      label: '展示周期',
      unit: '年',
    },
    cancelRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '复用原套餐退订生效规则',
      required: true,
      label: '退订生效规则',
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    refProduct: {
      value: 'SP-2024-0590',
      tag: 'reuse',
      tagReason: '参考原畅享59元套餐，便于对比与继承配置',
      label: '参考产品',
      options: [
        { value: '', label: '请选择' },
        { value: 'SP-2024-0590', label: '畅享59元套餐 (SP-2024-0590)' },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'reuse',
      tagReason: '复用原套餐同时订购次数限制',
      required: true,
      label: '同时订购次数',
    },
    totalOrderTimes: {
      value: '',
      tag: 'reuse',
      tagReason: '复用原套餐累计订购次数策略',
      label: '累计订购次数',
      unit: '个月内限订1次',
    },
    uploadYijing: {
      value: 'no',
      tag: 'reuse',
      tagReason: '复用原套餐是否上传一经策略',
      label: '是否上传一经',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    l1ProdCode: {
      value: 'APAP00590',
      tag: 'ai',
      tagReason: '从产品库查到的可用一级产品编码，基于原产品关联生成',
      label: '一级产品编码',
    },
    isComm: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '复用原套餐通信类属性',
      required: true,
      label: '是否通信类',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
    applyBasis: {
      value: '省公司2026年第089号文',
      tag: 'ai',
      tagReason: '方案A申报依据：省公司关于青春版套餐上线的批复文件',
      label: '申请依据',
    },
    allowChannel: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '复用原套餐允许渠道受理策略',
      required: true,
      label: '是否允许受理渠道',
      options: [
        { value: '', label: '请选择' },
        { value: 'yes', label: '是' },
        { value: 'no', label: '否' },
      ],
    },
  },

  // 资源配置 Tab —— 流量/语音/短信资源配置（从原可选配置迁入）
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
          tag: 'reuse',
          tagReason: '复用原套餐通用流量类型',
          options: [
            { value: 'general', label: '通用流量' },
            { value: '定向', label: '定向流量' },
            { value: '专属', label: '专属流量' },
          ],
        },
        {
          key: 'flowAmount',
          label: '资源量',
          value: '40',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '方案A核心变更：30GB→40GB，追平竞品A（动感青春版40GB），消除流量额度劣势（根因贡献度50%）',
        },
        {
          key: 'flowUnit',
          label: '流量单位',
          value: 'GB',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐流量单位',
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '套外计费标准',
          value: '0.29元/MB',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐套外计费标准',
        },
        {
          key: 'flowCarryover',
          label: '是否结转到次月',
          value: 'yes',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐流量结转策略',
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
          tag: 'reuse',
          tagReason: '复用原套餐本地语音类型',
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
          tag: 'reuse',
          tagReason: '复用原套餐100分钟语音配置，非本次优化重点',
        },
        {
          key: 'voiceOvercharge',
          label: '套外计费费率',
          value: '0.15元/分钟',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐语音套外费率',
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
          value: '100',
          type: 'number',
          unit: '条',
          tag: 'reuse',
          tagReason: '复用原套餐100条短信配置',
        },
        {
          key: 'smsOvercharge',
          label: '套外计费费率',
          value: '0.1元/条',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐短信套外费率',
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
          tag: 'reuse',
          tagReason: '权益按模板默认配置，领取方式可手动调整',
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
            { rightName: '视频会员月卡', obtainType: 'auto' },
            { rightName: '定向免流权益包', obtainType: 'manual' },
            { rightName: '彩铃功能', obtainType: 'auto' },
          ],
        },
      ],
    },
  ],

  // 营销资源 Tab —— 只保留月租、账务优惠、保底
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
          tag: 'reuse',
          tagReason: '复用原套餐月付方式',
          options: [
            { value: 'monthly', label: '月付' },
            { value: 'quarterly', label: '季付' },
            { value: 'yearly', label: '年付' },
          ],
        },
        { key: 'monthlyFee', label: '套餐固定费', value: '59.00', type: 'number', unit: '元', tag: 'reuse', tagReason: '方案A保持月费59元不变' },
        {
          key: 'feeSubject',
          label: '收费科目',
          value: '001',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐通讯费科目',
          options: [
            { value: '001', label: '通讯费' },
            { value: '002', label: '增值业务费' },
            { value: '003', label: '代收费' },
          ],
        },
        { key: 'taxRate', label: '税率', value: '6', type: 'number', unit: '%', tag: 'reuse', tagReason: '复用原套餐税率' },
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
          value: '新用户首月半价',
          type: 'text',
          tag: 'ai',
          tagReason: '新增优惠策略：首月半价对冲竞品促销，针对年轻新客降低尝鲜门槛',
        },
        {
          key: 'discountType',
          label: '优惠类型',
          value: 'cash',
          type: 'select',
          tag: 'ai',
          tagReason: '选择现金直减，用户感知最直接，转化效果优于优惠券',
          options: [
            { value: 'cash', label: '现金直减' },
            { value: 'coupon', label: '优惠券' },
            { value: 'gift', label: '赠品' },
          ],
        },
        { key: 'discountAmount', label: '优惠费用', value: '29.50', type: 'number', unit: '元', tag: 'ai', tagReason: '首月半价=59/2=29.50元' },
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
          tag: 'reuse',
          tagReason: '复用原套餐无保底策略',
          options: [
            { value: 'none', label: '无保底' },
            { value: 'monthly', label: '月保底' },
            { value: 'total', label: '总额保底' },
          ],
        },
      ],
    },
  ],

   // 销售规则 Tab —— 摘自智家包：销售规则、前项限制、后项限制
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
          tag: 'reuse',
          tagReason: '复用原套餐线下营业厅主渠道策略',
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
          tag: 'reuse',
          tagReason: '复用原套餐四川省适用范围',
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
          tag: 'reuse',
          tagReason: '复用原套餐无其他费用',
        },
        {
          key: 'breachLiability',
          label: '违约责任',
          value: 'none',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐无违约责任约束',
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
          tag: 'reuse',
          tagReason: '复用原套餐自动续订策略',
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
          tagReason: '方案A：与前项产品"且"关系互斥，确保新客未订购其他青春版套餐',
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
          tagReason: '新增前项限制：限制年龄/全球通等级/基础套餐月费条件，确保目标客群精准',
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
            { label: 'age', ruleType: 'lt', limitValue: '16', limitType: 'disallow', tipMsg: '年龄＜16岁的不允许办理本套餐' },
            { label: 'baseMonthlyFee', ruleType: 'ge', limitValue: '59', limitType: 'allow', tipMsg: '基础套餐月费≥59元的允许办理本套餐' },
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
          tagReason: '新增后项限制：订购本套餐后限制降档/退订/停机保号/销户/携号转网/过户等操作',
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
            { ruleType: 'business', bizName: 'cancel', limitType: 'disallow', limitTime: '24' },
          ],
        },
      ],
    },
  ],

  // 可选配置 Tab —— 兼容旧引用，已拆分到resourceSections/marketingSections/salesSections
  optionalSections: [],

  // 信息预览 Tab —— 用户受理过程相关信息 + 免填单
  previewSections: [
    {
      key: 'user-facing',
      title: '用户受理信息',
      icon: 'fa-user',
      description: '用户在办理过程中看到的关键信息',
      items: [
        { label: '产品名称', value: '畅享59元青春版', highlight: true },
        { label: '月费', value: '59元/月' },
        { label: '包含流量', value: '40GB通用流量（可结转次月）', highlight: true },
        { label: '包含语音', value: '100分钟本地语音' },
        { label: '视频权益', value: '腾讯视频月卡 + 爱奇艺月卡', highlight: true },
        { label: '其他权益', value: '生日特权、流量结转' },
        { label: '套外流量费', value: '0.29元/MB' },
        { label: '套外语音费', value: '0.15元/分钟' },
        { label: '新用户优惠', value: '首月半价（仅需29.50元）', highlight: true },
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
        { label: '资费名称', value: '畅享59元青春版' },
        { label: '套餐月费', value: '59元/月' },
        { label: '包含资源', value: '40G流量+100分钟语音+腾讯视频+爱奇艺', highlight: true },
        { label: '超套收费标准', value: '0.29元/MB' },
        { label: '限定条件', value: '四川省范围内使用，新老产品互斥' },
        { label: '其他权益', value: '生日特权、流量结转、腾讯视频月卡、爱奇艺月卡', highlight: true },
        { label: '收费方式', value: '月付' },
        { label: '套餐固定费', value: '59.00元' },
        { label: '税率', value: '6%' },
        { label: '优惠条件', value: '新用户首月半价' },
        { label: '优惠类型', value: '现金直减' },
        { label: '优惠费用', value: '29.50元' },
      ],
    },
    {
      key: 'sms-preview',
      title: '短信模板预览',
      icon: 'fa-comment-sms',
      description: '各触点下发的短信内容预览',
      items: [
        { label: '办理成功短信', value: '恭喜您成功办理畅享59元青春版，月费59元，含40G流量和100分钟语音，含腾讯视频+爱奇艺双权益，感谢您的支持！' },
        { label: '预约生效短信', value: '您的畅享59元青春版已生效，月费59元，生效当月起享受套餐权益。' },
        { label: '前台二确短信', value: '尊敬的客户，您正在办理畅享59元青春版，月费59元，是否确认办理？' },
        { label: '退订短信', value: '您好，您的畅享59元青春版已退订，如有疑问请致电10086。' },
      ],
    },
  ],
}

// ============ 深度思考步骤 ============
export const launchThinkingSteps = [
  '解析用户指令：启动产品优化，确认方案A',
  '调用产品查询助手，检索畅享59元套餐（SP-2024-0590）全量画像…',
  '产品查询助手返回：在网86万用户、月收入5,046万、合约用户31.2万',
  '校验互斥规则：新产品"畅享59元青春版"与现有产品SP-2024-0590互斥，可并行销售 ✓',
  '调用产品研发助手，拉取产品配置规则与资费审批模板…',
  '产品研发助手：匹配业务模板「个人主资费」，加载配置字段（5个Tab、10个section）',
  '根据方案A预填配置：流量30GB→40GB、视频权益1→2（腾讯+爱奇艺），未变字段标记"复用"',
  '生成工单编号：WO20260812001',
  '测算权益成本：3.0元→5.8元/户/月，ROI=5.75（达标）',
  '自动打开新套餐配置工作台',
]

// ============ 第6轮助手回复文案 ============
export function buildLaunchSummary() {
  return [
    '### 已启动产品优化 · 方案A',
    '',
    '> 收到指令，正在执行方案A（新产品策划）。已调用产品查询助手和产品研发助手完成前期准备，配置工作台已自动打开。',
    '',
    '**执行摘要**',
    '',
    '| 步骤 | 动作 | 结果 |',
    '| ---- | ---- | ---- |',
    '| 1 | 调用产品查询助手 | 检索畅享59元套餐全量画像：在网86万用户、月收入5,046万、合约用户31.2万 ✓ |',
    '| 2 | 校验互斥规则 | 新产品"畅享59元青春版"与SP-2024-0590互斥，可并行销售，存量用户不受影响 ✓ |',
    '| 3 | 调用产品研发助手 | 匹配业务模板「个人主资费」，加载5个Tab、10个section配置字段 ✓ |',
    '| 4 | 预填方案A配置 | 流量30GB→**40GB**、视频权益1→**2**（腾讯+爱奇艺）、月费59元不变 ✓ |',
    '| 5 | 生成工单 | WO20260812001，新产品名称"畅享59元青春版" ✓ |',
    '| 6 | 权益成本测算 | 3.0元→5.8元/户/月，ROI=5.75（>3.0门槛，达标）✓ |',
    '',
    '**已预填的关键变更字段**',
    '- 资费名称：畅享59元套餐 → **畅享59元青春版**',
    '- 流量资源：30GB → **40GB**（通用流量，可结转次月）',
    '- 包含资源：30G流量+100分钟语音+腾讯视频 → **40G流量+100分钟语音+腾讯视频+爱奇艺**',
    '- 其他权益：生日特权、流量结转、腾讯视频月卡 → **+爱奇艺月卡**',
    '- 月租固定费：59.00元（不变）',
    '- 优惠策略：新用户首月半价（29.50元）',
    '',
    '> 右侧工作台已打开。字段标签说明：**AI推荐**=基于方案新增或修改的值（悬浮可查看推荐原因），**复用**=从原套餐继承未变更的值。',
    '',
    '请检查预填内容并完成配置。可点击「智能稽核」进行提交前校验。',
  ].join('\n')
}

// ============ 测试报告数据（测试人员上传后自动解析） ============
// 初始 uploaded=false，表示测试人员尚未上传；上传后置为 true，配置人员即可查看
export const testReport = reactive({
  uploaded: false,
  uploadedAt: null,
  reportId: 'TR20260813001',
  workOrderId: 'WO20260812001',
  productName: '畅享59元青春版',
  tester: '张明',
  testDate: '2026-08-13',
  status: 'passed', // passed | failed
  testMethod: '新业务测试（含固费核查、计费核查、提醒核查、工单核查）',
  acceptNumber: '13920260813',
  sections: [
    {
      key: 'fixedFee',
      title: '固费核查',
      status: 'passed',
      items: [
        { label: '月租固定费', expected: '59.00元', actual: '59.00元', result: 'pass' },
        { label: '首月优惠费', expected: '29.50元（新用户首月半价）', actual: '29.50元', result: 'pass' },
        { label: '固定费生效时间', expected: '订购次月1日生效', actual: '次月1日生效', result: 'pass' },
      ],
    },
    {
      key: 'billing',
      title: '计费核查',
      status: 'passed',
      items: [
        { label: '流量计费', expected: '40GB内不额外计费，超出后1元/GB自动叠加', actual: '40GB内正常，超出按1元/GB叠加', result: 'pass' },
        { label: '语音计费', expected: '100分钟内免费，超出0.15元/分钟', actual: '100分钟内免费，超出0.15元/分钟', result: 'pass' },
        { label: '权益计费', expected: '腾讯视频+爱奇艺月卡，不额外扣费', actual: '权益正常下发，无额外扣费', result: 'pass' },
        { label: '流量结转', expected: '当月剩余流量结转至次月，次月底清零', actual: '结转规则正确', result: 'pass' },
      ],
    },
    {
      key: 'reminder',
      title: '提醒核查',
      status: 'passed',
      items: [
        { label: '流量使用提醒', expected: '使用达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
        { label: '套餐订购提醒', expected: '订购成功后下发欢迎短信', actual: '欢迎短信正常下发', result: 'pass' },
        { label: '权益领取提醒', expected: '权益到账后下发领取通知', actual: '通知正常下发', result: 'pass' },
      ],
    },
    {
      key: 'workOrder',
      title: '工单核查',
      status: 'passed',
      items: [
        { label: '订购工单', expected: '生成订购工单，状态正常流转', actual: '工单生成且状态正确', result: 'pass' },
        { label: '变更工单', expected: '老用户变更后生成变更工单', actual: '变更工单正常', result: 'pass' },
        { label: '退订工单', expected: '退订生成退订工单，次月生效', actual: '退订工单正确，次月生效', result: 'pass' },
      ],
    },
  ],
  conclusion: '通过',
  conclusionDesc: '该资费方案各项核查均符合预期，测试通过，可进入备案公示环节。',
})

/**
 * 备案公示数据
 *
 * started: 是否已发起备案公示（点击"发起备案公示"后为 true）
 * submitted: 是否已提交备案（点击"提交备案"后为 true，提交后备案通过 → 可上架销售）
 */
export const filing = reactive({
  started: false,
  submitted: false,
  approved: false,
  approvedAt: null,
  filingId: 'FL20260814001',
  workOrderId: 'WO20260812001',
  productName: '畅享59元青春版',
  template: '个人主资费',
  productCode: 'SP20240590Q',
  applicant: '产品配置专员',
  applyDate: '2026-08-14',
  // 备案信息
  info: {
    filingOrg: '产品研发中心',
    filingOfficer: '李芳',
    filingDate: '2026-08-14',
    effectiveDate: '2026-09-01',
    expireDate: '长期有效',
  },
  // 备案核查项
  checks: [
    { label: '资费合规性', status: 'passed', desc: '符合《资费管理办法》相关规定，无违规项' },
    { label: '公示内容完整性', status: 'passed', desc: '资费名称、资费标准、生效时间等公示信息完整' },
    { label: '测试报告核销', status: 'passed', desc: '测试报告 TR20260813001 已通过，允许备案' },
    { label: '审批链完整性', status: 'passed', desc: '预览审批、测试审核审批链完整' },
  ],
  // 公示预览
  notice: {
    title: '关于"畅享59元青春版"资费方案备案公示',
    target: '全体用户',
    content: '现将"畅享59元青春版"资费方案（资费代码：SP20240590Q）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。',
    notifyChannels: '营业厅公示栏、网上营业厅、掌上营业厅',
    startDate: '2026-08-15',
    endDate: '2026-08-29',
    duration: '15天',
  },
  // 全量视图字段（category: common=共有 / filing=备案特有 / public=公示特有）
  sections: [
    {
      title: '备案基本信息',
      previewOnly: true,
      fields: [
        { label: '备案公示流程', value: '先备案再公示', category: 'filing' },
        { label: '公示系列ID', value: '资费ID', category: 'filing' },
        { label: '公示系列名称', value: launchProductConfig.baseInfo.prodName.value, category: 'filing' },
        { label: '方案编号', value: 'XNBP0001', category: 'filing' },
      ],
    },
    {
      title: '套餐基本信息',
      previewOnly: false,
      fields: [
        { label: 'BOSSID', value: launchProductConfig.baseInfo.prodCode.value, category: 'filing' },
        { label: '一级分类', value: '公众', category: 'filing' },
        { label: '二级分类', value: '套餐', category: 'filing' },
        { label: '名称', value: launchProductConfig.baseInfo.prodName.value, category: 'filing' },
        { label: '资费属性', value: '省内', category: 'filing' },
        { label: '是否自选套餐', value: '是', category: 'filing' },
        { label: '模组分类名称', value: '无', category: 'filing' },
        { label: '是否通信类', value: launchProductConfig.baseInfo.isComm.value === 'yes' ? '是' : '否', category: 'filing' },
        { label: '是否主数据', value: '否', category: 'filing' },
        { label: '展示顺序权重', value: '100', category: 'filing' },
        { label: '适用范围', value: '通过前项限制生成', category: 'common' },
        { label: '购买链接', value: 'https://xxx.com/buy', category: 'filing' },
        { label: '资费单位', value: '元/月', category: 'filing' },
      ],
    },
    {
      title: '基础信息',
      previewOnly: false,
      fields: [
        { label: '资费标准', value: '59元/月', category: 'common' },
        { label: '资费类型', value: '套餐', category: 'public' },
        { label: '方案编号', value: 'AI203935', category: 'public' },
        { label: '上线日期', value: '20260901', category: 'common' },
        { label: '下线日期', value: '20991231', category: 'common' },
        { label: '有效期限', value: '36个月', category: 'common' },
        { label: '合约期(在网要求)', value: '12个月', category: 'common' },
        { label: '适用地区', value: '全省（安徽）', category: 'common' },
        { label: '销售渠道', value: '自有营业厅/网上营业厅', category: 'common' },
        { label: '退订方式', value: '线下营业厅', category: 'common' },
        { label: '违约责任', value: '合约期内退订需缴纳违约金', category: 'common' },
      ],
    },
    {
      title: '资源信息',
      previewOnly: false,
      fields: [
        { label: '国内通话', value: '100分钟', category: 'common' },
        { label: '国内通用流量', value: '40GB', category: 'common' },
        { label: '定向流量', value: '100GB', category: 'common' },
        { label: '短信', value: '100条', category: 'common' },
        { label: '国际通话(国际长途通话量)', value: '0分钟', category: 'common' },
        { label: '港澳台流量', value: '0GB', category: 'filing' },
        { label: 'IPTV(移动高清)', value: '无', category: 'common' },
        { label: '宽带', value: '无', category: 'common' },
        { label: '权益', value: '腾讯视频+爱奇艺月卡', category: 'common' },
      ],
    },
    {
      title: '说明信息',
      previewOnly: false,
      fields: [
        { label: '超出资费说明', value: '国内通话超出后0.15元/分钟，流量按0.29元/MB', category: 'common' },
        { label: '其他服务内容', value: '含来电显示、彩铃等基础服务', category: 'common' },
        { label: '其他资费说明', value: '无', category: 'public' },
        { label: '其他说明', value: '无', category: 'common' },
      ],
    },
  ],
})

/**
 * 销售渠道 store 数据
 */
export const salesChannel = reactive({
  // 是否已进入渠道选择阶段（备案通过后为 true）
  available: false,
  // 渠道选择是否已提交（提交后流程进入上架销售）
  submitted: false,
  // 可用渠道列表
  list: [
    { key: 'own_offline', label: '自有营业厅', desc: '实体营业厅线下受理' },
    { key: 'own_online', label: '网上营业厅', desc: '官方网上营业厅在线订购' },
    { key: 'app', label: '掌上营业厅APP', desc: '手机营业厅客户端' },
    { key: 'customer_service', label: '10086客服热线', desc: '电话客服人员受理' },
    { key: 'channel_coop', label: '社会合作渠道', desc: '第三方合作代理商' },
  ],
  // 已选渠道
  selected: [],
  // 默认勾选（推荐）
  defaultSelected: ['own_online', 'app'],
})

(function(global){
  const { reactive } = global.Vue;
  /* ===== launchOptimizeData ===== */
  /**
 * �?�?- 启动产品优化
 * AI助手确认方案A后，调用产品研发助手自动打开配置页面
 * 预填数据基于方案A：畅�?9元青春版（原畅享59元套餐优化升级）
 *
 * 字段标签规则�?
 * - tag: 'ai'     �?AI推荐（新增生成或基于方案修改的��，�?tagReason 说明推荐原因�?
 * - tag: 'reuse'  �?复用（从原套餐继承，未做变更的��）
 * - tag: null     �?无标签（常规字段�?
 */

// ============ 方案A预填的产品配置数�?============
const launchProductConfig = {
  // 商品基本信息
  productInfo: reactive({
    workOrderId: 'WO20260812001',
    prodPrcName: '畅享59元青春版',
    template: 'personMainPrc',
    templateLabel: '个人主资�?',
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 基础信息 Tab —��?字段摘自智家�?基本信息"模块，保持AI工作台排版与AI推荐/复用标签机制
  baseInfo: {
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '方案A定位个人主资费，沿用原套餐商品属性分�?',
      required: true,
      label: '商品属��?',
      options: [
        { value: 'personMainPrc', label: '个人主资�?' },
        { value: 'personOptionalPrc', label: '个人可��包' },
        { value: 'familyMainPrc', label: '家庭主资�?' },
        { value: 'enterprisePrc', label: '政企资费' },
      ],
    },
    prodCode: {
      value: 'SP20240590Q',
      tag: 'ai',
      tagReason: '基于原商品SP-2024-0590关联生成新商品编�?',
      label: '商品编码',
    },
    prodName: {
      value: '畅享59元青春版',
      tag: 'ai',
      tagReason: '基于方案A命名策略：保�?畅享59�?品牌延续性，"青春�?定位年轻客群，配合双视频权益+大流量策�?',
      required: true,
      label: '商品名称',
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '方案A计划9�?日上线，避开暑期促销高峰，配合秋季开学季获客',
      required: true,
      label: '上线日期',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'reuse',
      tagReason: '复用原套餐长期销售策�?',
      required: true,
      label: '下线日期',
    },
    effRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '复用原套餐生效规则：新入网立即生�?',
      required: true,
      label: '生效规则',
      options: [
        { value: 'immediate', label: '新入网立�?变更次月/续约指定日期' },
        { value: 'nextMonth', label: '次月生效' },
        { value: 'appoint', label: '预约指定日期生效' },
      ],
    },
    validity: {
      value: 'longTerm',
      tag: 'reuse',
      tagReason: '复用原套餐长期有效策�?',
      required: true,
      label: '商品有效�?',
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
      tagReason: '复用原套餐展示周�?�?',
      required: true,
      label: '展示周期',
      unit: '�?',
    },
    cancelRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '复用原套餐���订生效规�?',
      required: true,
      label: '逢�订生效规�?',
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    refProduct: {
      value: 'SP-2024-0590',
      tag: 'reuse',
      tagReason: '参��原畅享59元套餐，便于对比与继承配�?',
      label: '参��商�?',
      options: [
        { value: '', label: '请��择' },
        { value: 'SP-2024-0590', label: '畅享59元套�?(SP-2024-0590)' },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'reuse',
      tagReason: '复用原套餐同时订购次数限�?',
      required: true,
      label: '同时订购次数',
    },
    totalOrderTimes: {
      value: '',
      tag: 'reuse',
      tagReason: '复用原套餐累计订购次数策�?',
      label: '累计订购次数',
      unit: '个月内限�?�?',
    },
    uploadYijing: {
      value: 'no',
      tag: 'reuse',
      tagReason: '复用原套餐是否上传一经策�?',
      label: '是否上传丢��?',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
      ],
    },
    l1ProdCode: {
      value: 'APAP00590',
      tag: 'ai',
      tagReason: '从产品库查到的可用一级产品编码，基于原商品关联生�?',
      label: '丢�级产品编�?',
    },
    isComm: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '复用原套餐��信类属�?',
      required: true,
      label: '是否通信�?',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
      ],
    },
    applyBasis: {
      value: '省公�?026年第089号文',
      tag: 'ai',
      tagReason: '方案A申报依据：省公司关于青春版套餐上线的批复文件',
      label: '申请依据',
    },
    allowChannel: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '复用原套餐允许渠道受理策�?',
      required: true,
      label: '是否允许受理渠道',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
      ],
    },
  },

  // 资源配置 Tab —��?流量/语音/短信资源配置（从原可选配置迁入）
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
          tagReason: '复用原套餐��用流量类型',
          options: [
            { value: 'general', label: '通用流量' },
            { value: '定向', label: '定向流量' },
            { value: '专属', label: '专属流量' },
          ],
        },
        {
          key: 'flowAmount',
          label: '资源�?',
          value: '40',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '方案A核心变更�?0GB�?0GB，追平竞品A（动感青春版40GB），消除流量额度劣势（根因贡献度50%�?',
        },
        {
          key: 'flowUnit',
          label: '流量单位',
          value: 'GB',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐流量单�?',
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '套外计费标准',
          value: '0.29�?MB',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐套外计费标�?',
        },
        {
          key: 'flowCarryover',
          label: '是否结转到次�?',
          value: 'yes',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐流量结转策�?',
          options: [
            { value: 'yes', label: '�?' },
            { value: 'no', label: '�?' },
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
          tagReason: '复用原套餐本地语音类�?',
          options: [
            { value: 'local', label: '本地语音' },
            { value: 'longdistance', label: '长��语�?' },
          ],
        },
        {
          key: 'voiceAmount',
          label: '资源�?',
          value: '100',
          type: 'number',
          unit: '分钟',
          tag: 'reuse',
          tagReason: '复用原套�?00分钟语音配置，非本次优化重点',
        },
        {
          key: 'voiceOvercharge',
          label: '套外计费费率',
          value: '0.15�?分钟',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐语音套外费�?',
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
          label: '资源�?',
          value: '100',
          type: 'number',
          unit: '�?',
          tag: 'reuse',
          tagReason: '复用原套�?00条短信配�?',
        },
        {
          key: 'smsOvercharge',
          label: '套外计费费率',
          value: '0.1�?�?',
          type: 'text',
          tag: 'reuse',
          tagReason: '复用原套餐短信套外费�?',
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
          tagReason: '权益按模板默认配置，领取方式可手动调�?',
          columns: [
            {
              key: 'rightName',
              label: '权益名称',
              type: 'text',
              placeholder: '请输入权益名�?',
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
            { rightName: '定向免流权益�?', obtainType: 'manual' },
            { rightName: '彩铃功能', obtainType: 'auto' },
          ],
        },
      ],
    },
  ],

  // 营销资源 Tab —��?只保留月租��账务优惠��保�?
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
          tagReason: '复用原套餐月付方�?',
          options: [
            { value: 'monthly', label: '月付' },
            { value: 'quarterly', label: '季付' },
            { value: 'yearly', label: '年付' },
          ],
        },
        { key: 'monthlyFee', label: '套餐固定�?, value: '59.00', type: 'number', unit: '�?, tag: 'reuse', tagReason: '方案A保持月费59元不�?' },
        {
          key: 'feeSubject',
          label: '收费科目',
          value: '001',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐��讯费科�?',
          options: [
            { value: '001', label: '通讯�?' },
            { value: '002', label: '增��业务费' },
            { value: '003', label: '代收�?' },
          ],
        },
        { key: 'taxRate', label: '税率', value: '6', type: 'number', unit: '%', tag: 'reuse', tagReason: '复用原套餐税�?' },
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
          value: '新用户首月半�?',
          type: 'text',
          tag: 'ai',
          tagReason: '新增优惠策略：首月半价对冲竞品促锢�，针对年轻新客降低尝鲜门�?',
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
            { value: 'coupon', label: '优惠�?' },
            { value: 'gift', label: '赠品' },
          ],
        },
        { key: 'discountAmount', label: '优惠费用', value: '29.50', type: 'number', unit: '�?, tag: 'ai', tagReason: '首月半价=59/2=29.50�? },
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
            { value: 'none', label: '无保�?' },
            { value: 'monthly', label: '月保�?' },
            { value: 'total', label: '总额保底' },
          ],
        },
      ],
    },
  ],

   // 锢�售规�?Tab —��?摘自智家包：锢�售规则��前项限制��后项限�?
  salesSections: [
    {
      key: 'sales-rule',
      title: '锢�售规�?',
      icon: 'fa-shield-halved',
      fields: [
        {
          key: 'channelType',
          label: '渠道类型',
          value: 'offline',
          type: 'checkbox-group',
          tag: 'reuse',
          tagReason: '复用原套餐线下营业厅主渠道策�?',
          options: [
            { value: 'offline', label: '线下营业�?' },
            { value: 'online', label: '中国移动在线渠道' },
            { value: 'local-online', label: '省移动线上渠�?' },
            { value: 'cmcc-online', label: '中国移动线上渠道' },
            { value: 'tv', label: '电视营业�?' },
            { value: 'accelerator', label: '加��平�?' },
            { value: 'points', label: '积分商城' },
            { value: 'meter', label: '丢�级电�?' },
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
            { value: 'none', label: '�?' },
            { value: 'compensate', label: '按约定补偿金' },
          ],
        },
        {
          key: 'renewMethod',
          label: '续订方式',
          value: 'auto',
          type: 'select',
          tag: 'reuse',
          tagReason: '复用原套餐自动续订策�?',
          options: [
            { value: 'auto', label: '自动续订' },
            { value: 'manual', label: '手动续订' },
            { value: 'no', label: '不续�?' },
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
          label: '限制间关�?',
          value: 'and',
          type: 'select',
          tag: 'ai',
          tagReason: '方案A：与前项商品"�?关系互斥，确保新客未订购其他青春版套�?',
          options: [
            { value: 'and', label: '且（全部满足�?' },
            { value: 'or', label: '或（任一满足�?' },
          ],
        },
        {
          key: 'preLimitTable',
          label: '限制列表',
          type: 'table',
          tag: 'ai',
          tagReason: '新增前项限制：限制年�?全球通等�?基础套餐月费条件，确保目标客群精�?',
          columns: [
            { key: 'label', label: '标签', type: 'select', options: [
              { value: 'age', label: '年龄' },
              { value: 'globalLevel', label: '全球通等�?' },
              { value: 'baseMonthlyFee', label: '基础套餐月费' },
              { value: 'targetGroup', label: '目标客群' },
              { value: 'studentOnly', label: '面向在校大学�?' },
              { value: 'studentCert', label: '霢�上传学生证认�?' },
              { value: 'realName', label: '仅限本人实名办理' },
              { value: 'limitToSelf', label: '仅限本人办理' },
            ] },
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'gt', label: '�?' },
              { value: 'lt', label: '�?' },
              { value: 'ge', label: '�?' },
              { value: 'le', label: '�?' },
              { value: 'eq', label: '�?' },
            ] },
            { key: 'limitValue', label: '限定�?, type: 'text', placeholder: '请输入限定��? },
            { key: 'limitType', label: '限定类型', type: 'select', options: [
              { value: 'allow', label: '允许办理' },
              { value: 'disallow', label: '不允许办�?' },
            ] },
            { key: 'tipMsg', label: '提示�?', type: 'auto', placeholder: '根据条件自动生成' },
          ],
          rows: [
            { label: 'age', ruleType: 'lt', limitValue: '16', limitType: 'disallow', tipMsg: '年龄�?6岁的不允许办理本套餐' },
            { label: 'baseMonthlyFee', ruleType: 'ge', limitValue: '59', limitType: 'allow', tipMsg: '基础套餐月费�?9元的允许办理本套�?' },
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
          tagReason: '新增后项限制：订购本套餐后限制降�?逢��?停机保号/锢��?携号转网/过户等操�?',
          columns: [
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'product', label: '商品限制' },
              { value: 'business', label: '业务限制' },
            ] },
            { key: 'bizName', label: '业务名称', type: 'select', dynamicOptions: true, options: {
              product: [
                { value: 'downgrade', label: '降档' },
                { value: 'unsubscribe', label: '逢��?' },
              ],
              business: [
                { value: 'suspend', label: '停机保号' },
                { value: 'cancel', label: '锢��?' },
                { value: 'mnp', label: '携号转网' },
                { value: 'transfer', label: '过户' },
              ],
            } },
            { key: 'limitType', label: '限制类型', type: 'select', options: [
              { value: 'disallow', label: '不允�?' },
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

  // 可��配�?Tab —��?兼容旧引用，已拆分到resourceSections/marketingSections/salesSections
  optionalSections: [],

  // 信息预览 Tab —��?用户受理过程相关信息 + 免填�?
  previewSections: [
    {
      key: 'user-facing',
      title: '用户受理信息',
      icon: 'fa-user',
      description: '用户在办理过程中看到的关键信�?',
      items: [
        { label: '商品名称', value: '畅享59元青春版', highlight: true },
        { label: '月费', value: '59�?�?' },
        { label: '包含流量', value: '40GB通用流量（可结转次月�?', highlight: true },
        { label: '包含语音', value: '100分钟本地语音' },
        { label: '视频权益', value: '腾讯视频月卡 + 爱奇艺月�?', highlight: true },
        { label: '其他权益', value: '生日特权、流量结�?' },
        { label: '套外流量�?, value: '0.29�?'MB' },
        { label: '套外语音�?, value: '0.15�?'分钟' },
        { label: '新用户优�?', value: '首月半价（仅霢�29.50元）', highlight: true },
        { label: '生效方式', value: '立即生效' },
        { label: '逢�订方�?', value: '立即失效' },
        { label: '使用范围', value: '四川省范围内' },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '免填�?',
      icon: 'fa-file-alt',
      description: '营业员受理时免填的自动带出信�?',
      items: [
        { label: '资费名称', value: '畅享59元青春版' },
        { label: '套餐月费', value: '59�?�?' },
        { label: '包含资源', value: '40G流量+100分钟语音+腾讯视频+爱奇�?', highlight: true },
        { label: '超套收费标准', value: '0.29�?MB' },
        { label: '限定条件', value: '四川省范围内使用，新老商品互�?' },
        { label: '其他权益', value: '生日特权、流量结转��腾讯视频月卡��爱奇艺月卡', highlight: true },
        { label: '收费方式', value: '月付' },
        { label: '套餐固定�?, value: '59.00�? },
        { label: '税率', value: '6%' },
        { label: '优惠条件', value: '新用户首月半�?' },
        { label: '优惠类型', value: '现金直减' },
        { label: '优惠费用', value: '29.50�?' },
      ],
    },
    {
      key: 'sms-preview',
      title: '短信模板预览',
      icon: 'fa-comment-sms',
      description: '各触点下发的短信内容预览',
      items: [
        { label: '办理成功短信', value: '恭喜您成功办理畅�?9元青春版，月�?9元，�?0G流量�?00分钟语音，含腾讯视频+爱奇艺双权益，感谢您的支持！' },
        { label: '预约生效短信', value: '您的畅享59元青春版已生效，月费59元，生效当月起享受套餐权益��?' },
        { label: '前台二确短信', value: '尊敬的客户，您正在办理畅�?9元青春版，月�?9元，是否确认办理�?' },
        { label: '逢�订短�?, value: '您好，您的畅�?9元青春版已���订，如有疑问请致�?0086�? },
      ],
    },
  ],
}

// ============ 深度思��步�?============
const launchThinkingSteps = [
  '解析用户指令：启动产品优化，确认方案A',
  '调用产品查询助手，检索畅�?9元套餐（SP-2024-0590）全量画像��?',
  '产品查询助手返回：在�?6万用户��月收入5,046万��合约用�?1.2�?',
  '校验互斥规则：新商品"畅享59元青春版"与现有商品SP-2024-0590互斥，可并行锢��?�?',
  '调用产品研发助手，拉取商品配置规则与资费审批模板�?',
  '产品研发助手：匹配业务模板��个人主资费」，加载配置字段�?个Tab�?0个section�?',
  '根据方案A预填配置：流�?0GB�?0GB、视频权�?�?（腾�?爱奇艺），未变字段标�?复用"',
  '生成工单编号：WO20260812001',
  '测算权益成本�?.0元→5.8�?�?月，ROI=5.75（达标）',
  '自动打开新套餐配置工作台',
]

// ============ �?轮助手回复文�?============
function buildLaunchSummary() {
  return [
    '### 已启动产品优�?· 方案A',
    '',
    '> 收到指令，正在执行方案A（新商品策划）��已调用产品查询助手和产品研发助手完成前期准备，配置工作台已自动打开�?',
    '',
    '**执行摘要**',
    '',
    '| 步骤 | 动作 | 结果 |',
    '| ---- | ---- | ---- |',
    '| 1 | 调用产品查询助手 | 棢�索畅�?9元套餐全量画像：在网86万用户��月收入5,046万��合约用�?1.2�?�?|',
    '| 2 | 校验互斥规则 | 新商�?畅享59元青春版"与SP-2024-0590互斥，可并行锢�售，存量用户不受影响 �?|',
    '| 3 | 调用产品研发助手 | 匹配业务模板「个人主资费」，加载5个Tab�?0个section配置字段 �?|',
    '| 4 | 预填方案A配置 | 流量30GB�?*40GB**、视频权�?�?*2**（腾�?爱奇艺）、月�?9元不�?�?|',
    '| 5 | 生成工单 | WO20260812001，新商品名称"畅享59元青春版" �?|',
    '| 6 | 权益成本测算 | 3.0元→5.8�?�?月，ROI=5.75�?3.0门槛，达标）�?|',
    '',
    '**已预填的关键变更字段**',
    '- 资费名称：畅�?9元套�?�?**畅享59元青春版**',
    '- 流量资源�?0GB �?**40GB**（��用流量，可结转次月�?',
    '- 包含资源�?0G流量+100分钟语音+腾讯视频 �?**40G流量+100分钟语音+腾讯视频+爱奇�?*',
    '- 其他权益：生日特权��流量结转��腾讯视频月�?�?**+爱奇艺月�?*',
    '- 月租固定费：59.00元（不变�?',
    '- 优惠策略：新用户首月半价�?9.50元）',
    '',
    '> 右侧工作台已打开。字段标签说明：**AI推荐**=基于方案新增或修改的值（悬浮可查看推荐原因）�?*复用**=从原套餐继承未变更的值��?',
    '',
    '请检查预填内容并完成配置。可点击「智能稽核��进行提交前校验�?',
  ].join('\n')
}

// ============ 测试报告数据（测试人员上传后自动解析�?============
// 初始 uploaded=false，表示测试人员尚未上传；上传后置�?true，配置人员即可查�?
const testReport = reactive({
  uploaded: false,
  uploadedAt: null,
  reportId: 'TR20260813001',
  workOrderId: 'WO20260812001',
  productName: '畅享59元青春版',
  tester: '张明',
  testDate: '2026-08-13',
  status: 'passed', // passed | failed
  testMethod: '新业务测试（含固费核查��计费核查��提醒核查��工单核查）',
  acceptNumber: '13920260813',
  sections: [
    {
      key: 'fixedFee',
      title: '固费核查',
      status: 'passed',
      items: [
        { label: '月租固定�?, expected: '59.00�?, actual: '59.00�?', result: 'pass' },
        { label: '首月优惠�?, expected: '29.50元（新用户首月半价）', actual: '29.50�?, result: 'pass' },
        { label: '固定费生效时�?, expected: '订购次月1日生�?, actual: '次月1日生�?', result: 'pass' },
      ],
    },
    {
      key: 'billing',
      title: '计费核查',
      status: 'passed',
      items: [
        { label: '流量计费', expected: '40GB内不额外计费，超出后1�?GB自动叠加', actual: '40GB内正常，超出�?�?GB叠加', result: 'pass' },
        { label: '语音计费', expected: '100分钟内免费，超出0.15�?分钟', actual: '100分钟内免费，超出0.15�?分钟', result: 'pass' },
        { label: '权益计费', expected: '腾讯视频+爱奇艺月卡，不额外扣�?', actual: '权益正常下发，无额外扣费', result: 'pass' },
        { label: '流量结转', expected: '当月剩余流量结转至次月，次月底清�?', actual: '结转规则正确', result: 'pass' },
      ],
    },
    {
      key: 'reminder',
      title: '提醒核查',
      status: 'passed',
      items: [
        { label: '流量使用提醒', expected: '使用�?0%�?5%时下发短信提�?, actual: '80%�?5%均正常下�?, result: 'pass' },
        { label: '套餐订购提醒', expected: '订购成功后下发欢迎短�?', actual: '欢迎短信正常下发', result: 'pass' },
        { label: '权益领取提醒', expected: '权益到账后下发领取��知', actual: '通知正常下发', result: 'pass' },
      ],
    },
    {
      key: 'workOrder',
      title: '工单核查',
      status: 'passed',
      items: [
        { label: '订购工单', expected: '生成订购工单，状态正常流�?, actual: '工单生成且状态正�?, result: 'pass' },
        { label: '变更工单', expected: '老用户变更后生成变更工单', actual: '变更工单正常', result: 'pass' },
        { label: '逢�订工�?', expected: '逢�订生成���订工单，次月生效', actual: '逢�订工单正确，次月生效', result: 'pass' },
      ],
    },
  ],
  conclusion: '通过',
  conclusionDesc: '该资费方案各项核查均符合预期，测试��过，可进入备案公示环节�?',
})

/**
 * 备案公示数据
 *
 * started: 是否已发起备案公示（点击"发起备案公示"后为 true�?
 * submitted: 是否已提交备案（点击"提交备案"后为 true，提交后备案通过 �?可上架销售）
 */
const filing = reactive({
  started: false,
  submitted: false,
  approved: false,
  approvedAt: null,
  filingId: 'FL20260814001',
  workOrderId: 'WO20260812001',
  productName: '畅享59元青春版',
  template: '个人主资�?',
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
  // 备案核查�?
  checks: [
    { label: '资费合规�?', status: 'passed', desc: '符合《资费管理办法��相关规定，无违规项' },
    { label: '公示内容完整�?', status: 'passed', desc: '资费名称、资费标准��生效时间等公示信息完整' },
    { label: '测试报告核销', status: 'passed', desc: '测试报告 TR20260813001 已��过，允许备�?' },
    { label: '审批链完整��?', status: 'passed', desc: '预览审批、测试审核审批链完整' },
  ],
  // 公示预览
  notice: {
    title: '关于"畅享59元青春版"资费方案备案公示',
    target: '全体用户',
    content: '现将"畅享59元青春版"资费方案（资费代码：SP20240590Q）予以备案公示��公示期满且无异议后，该资费方案将正式上架销售��?',
    notifyChannels: '营业厅公示栏、网上营业厅、掌上营业厅',
    startDate: '2026-08-15',
    endDate: '2026-08-29',
    duration: '15�?',
  },
  // 全量视图字段（category: common=共有 / filing=备案特有 / public=公示特有�?
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
        { label: '丢�级分�?', value: '公众', category: 'filing' },
        { label: '二级分类', value: '套餐', category: 'filing' },
        { label: '名称', value: launchProductConfig.baseInfo.prodName.value, category: 'filing' },
        { label: '资费属��?', value: '省内', category: 'filing' },
        { label: '是否自��套�?, value: '�?, category: 'filing' },
        { label: '模组分类名称', value: '�?', category: 'filing' },
        { label: '是否通信�?, value: launchProductConfig.baseInfo.isComm.value === 'yes' ? '�? : '�?', category: 'filing' },
        { label: '是否主数�?, value: '�?, category: 'filing' },
        { label: '展示顺序权重', value: '100', category: 'filing' },
        { label: '适用范围', value: '通过前项限制生成', category: 'common' },
        { label: '购买链接', value: 'https://xxx.com/buy', category: 'filing' },
        { label: '资费单位', value: '�?�?', category: 'filing' },
      ],
    },
    {
      title: '基础信息',
      previewOnly: false,
      fields: [
        { label: '资费标准', value: '59�?�?', category: 'common' },
        { label: '资费类型', value: '套餐', category: 'public' },
        { label: '方案编号', value: 'AI203935', category: 'public' },
        { label: '上线日期', value: '20260901', category: 'common' },
        { label: '下线日期', value: '20991231', category: 'common' },
        { label: '有效期限', value: '36个月', category: 'common' },
        { label: '合约�?在网要求)', value: '12个月', category: 'common' },
        { label: '适用地区', value: '全省（安徽）', category: 'common' },
        { label: '锢�售渠�?, value: '自有营业�?网上营业�?, category: 'common' },
        { label: '逢�订方�?, value: '线下营业�?, category: 'common' },
        { label: '违约责任', value: '合约期内逢�订需缴纳违约�?', category: 'common' },
      ],
    },
    {
      title: '资源信息',
      previewOnly: false,
      fields: [
        { label: '国内通话', value: '100分钟', category: 'common' },
        { label: '国内通用流量', value: '40GB', category: 'common' },
        { label: '定向流量', value: '100GB', category: 'common' },
        { label: '短信', value: '100�?', category: 'common' },
        { label: '国际通话(国际长����话�?', value: '0分钟', category: 'common' },
        { label: '港澳台流�?', value: '0GB', category: 'filing' },
        { label: 'IPTV(移动高清)', value: '�?', category: 'common' },
        { label: '宽带', value: '�?', category: 'common' },
        { label: '权益', value: '腾讯视频+爱奇艺月�?', category: 'common' },
      ],
    },
    {
      title: '说明信息',
      previewOnly: false,
      fields: [
        { label: '超出资费说明', value: '国内通话超出�?.15�?分钟，流量按0.29�?MB', category: 'common' },
        { label: '其他服务内容', value: '含来电显示��彩铃等基础服务', category: 'common' },
        { label: '其他资费说明', value: '�?', category: 'public' },
        { label: '其他说明', value: '�?', category: 'common' },
      ],
    },
  ],
})

/**
 * 锢�售渠�?store 数据
 */
const salesChannel = reactive({
  // 是否已进入渠道��择阶段（备案��过后为 true�?
  available: false,
  // 渠道选择是否已提交（提交后流程进入上架销售）
  submitted: false,
  // 可用渠道列表
  list: [
    { key: 'own_offline', label: '自有营业�?, desc: '实体营业厅线下受�? },
    { key: 'own_online', label: '网上营业�?, desc: '官方网上营业厅在线订�? },
    { key: 'app', label: '掌上营业厅APP', desc: '手机营业厅客户端' },
    { key: 'customer_service', label: '10086客服热线', desc: '电话客服人员受理' },
    { key: 'channel_coop', label: '社会合作渠道', desc: '第三方合作代理商' },
  ],
  // 已��渠�?
  selected: [],
  // 默认勾��（推荐�?
  defaultSelected: ['own_online', 'app'],
})

  /* ===== campusDraftData ===== */
  /**
 * campusDraftData - 畅享校园�?· 对话式配置数�?
 *
 * 场景：新建一档全新套餐��畅享校园卡59元套餐��，**无任何原套餐可参�?*�?
 * 因此本场景所有字段的 AI 推荐理由均基于��用户需�?+ 业务模板」生成，
 * 刻意规避"沿用原套餐商品属性分�?/ 复用原套餐XX"等表�?—��?这类表述只有�?
 * 「存量商品复制配�?/ 启动产品优化」这类确有原套餐参��的场景才合适��?
 *
 * 字段标签规则（与 launchOptimizeData.js 丢�致）�?
 * - tag: 'ai'     �?AI推荐（依据用户需求或业务模板生成，附 tagReason 说明推荐原因�?
 * - tag: 'reuse'  �?建议�?AI 按业务模板直接补全（对话�?批量场景下渲染为"AI推荐"，不显示"复用"�?
 * - tag: null     �?无标签（常规字段�?
 *
 * 结构对齐 launchOptimizeData：productInfo / baseInfo / resourceSections /
 * marketingSections / salesSections / previewSections�?
 *
 * 注：打开某个批量商品工作台时，表单��以该商�?parsedFields 为准，本 schema 的默认��仅�?
 * 兜底/AI推荐建议展示（此处以新方案入门档 59 元为准）�?
 */

// ============ 畅享校园�?· 对话式配置的产品配置数据 ============
const campusProductConfig = {
  // 商品基本信息
  productInfo: reactive({
    workOrderId: '',
    prodPrcName: '畅享校园�?9元套�?',
    template: 'personMainPrc',
    templateLabel: '个人主资�?',
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 基础信息 Tab
  baseInfo: {
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '依据产品定位判定为面向学生个人的主资费商品，选择"个人主资�?属��?',
      required: true,
      label: '商品属��?',
      options: [
        { value: 'personMainPrc', label: '个人主资�?' },
        { value: 'personOptionalPrc', label: '个人可��包' },
        { value: 'familyMainPrc', label: '家庭主资�?' },
        { value: 'enterprisePrc', label: '政企资费' },
      ],
    },
    prodCode: {
      value: 'SP20260828001',
      tag: 'ai',
      tagReason: '按商品编码规则为新商品生成唯丢�编码',
      label: '商品编码',
    },
    prodName: {
      value: '畅享校园�?9元套�?',
      tag: 'ai',
      tagReason: '依据用户命名沿用"畅享校园�?9元套�?作为商品名称，贴合学生客群入门档定位',
      required: true,
      label: '商品名称',
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '建议9�?日开学季上线，契合在校大学生新客获客节奏',
      required: true,
      label: '上线日期',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'ai',
      tagReason: '商品长期有效，无固定下线时间',
      required: true,
      label: '下线日期',
    },
    effRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '按业务模板默认：新入网立即生效，便于学生弢�卡即�?',
      required: true,
      label: '生效规则',
      options: [
        { value: 'immediate', label: '新入网立�?变更次月/续约指定日期' },
        { value: 'nextMonth', label: '次月生效' },
        { value: 'appoint', label: '预约指定日期生效' },
      ],
    },
    validity: {
      value: 'longTerm',
      tag: 'ai',
      tagReason: '依据用户霢��?商品长期有效"，设置为长期有效',
      required: true,
      label: '商品有效�?',
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
      tagReason: '按业务模板默认展示周�?�?',
      required: true,
      label: '展示周期',
      unit: '�?',
    },
    cancelRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '按业务模板默认���订立即失�?',
      required: true,
      label: '逢�订生效规�?',
      options: [
        { value: 'immediate', label: '立即失效' },
        { value: 'monthEnd', label: '月终失效' },
        { value: 'nextMonth', label: '次月失效' },
      ],
    },
    refProduct: {
      value: '',
      tag: 'ai',
      tagReason: '本档为新商品，无参��商�?',
      label: '参��商�?',
      options: [
        { value: '', label: '请��择' },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'ai',
      tagReason: '按业务模板默认每位学生限�?张，同时订购次数�?',
      required: true,
      label: '同时订购次数',
    },
    totalOrderTimes: {
      value: '',
      tag: 'ai',
      tagReason: '按业务模板默认不限制累计订购次数',
      label: '累计订购次数',
      unit: '个月内限�?�?',
    },
    uploadYijing: {
      value: 'no',
      tag: 'ai',
      tagReason: '按业务模板默认不上传丢��?',
      label: '是否上传丢��?',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
      ],
    },
    l1ProdCode: {
      value: 'APAP202608',
      tag: 'ai',
      tagReason: '从产品库按商品属性匹配到可用丢�级产品编�?',
      label: '丢�级产品编�?',
    },
    isComm: {
      value: 'yes',
      tag: 'ai',
      tagReason: '本商品为通信类资费商�?',
      required: true,
      label: '是否通信�?',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
      ],
    },
    applyBasis: {
      value: '面向在校大学生的校园专享资费方案',
      tag: 'ai',
      tagReason: '申报依据：校园客群专项资费方�?',
      label: '申请依据',
    },
    allowChannel: {
      value: 'yes',
      tag: 'ai',
      tagReason: '按业务模板允许受理渠道办�?',
      required: true,
      label: '是否允许受理渠道',
      options: [
        { value: '', label: '请��择' },
        { value: 'yes', label: '�?' },
        { value: 'no', label: '�?' },
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
          tagReason: '依据用户霢��?通用流量20GB"，流量类型为通用流量',
          options: [
            { value: 'general', label: '通用流量' },
            { value: '定向', label: '定向流量' },
            { value: '专属', label: '专属流量' },
          ],
        },
        {
          key: 'flowAmount',
          label: '资源�?',
          value: '20',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '依据用户霢�求：通用流量20GB',
        },
        {
          key: 'flowUnit',
          label: '流量单位',
          value: 'GB',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户霢�求以GB为单位计�?',
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '套外计费标准',
          value: '0.29�?MB',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户霢�求：超套�?.29�?MB按量计费',
        },
        {
          key: 'flowCarryover',
          label: '是否结转到次�?',
          value: 'no',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板默认当月流量不结转',
          options: [
            { value: 'yes', label: '�?' },
            { value: 'no', label: '�?' },
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
          tagReason: '依据用户霢��?国内语音200分钟"，资源类型为本地语音',
          options: [
            { value: 'local', label: '本地语音' },
            { value: 'longdistance', label: '长��语�?' },
          ],
        },
        {
          key: 'voiceAmount',
          label: '资源�?',
          value: '200',
          type: 'number',
          unit: '分钟',
          tag: 'ai',
          tagReason: '依据用户霢�求：国内语音200分钟',
        },
        {
          key: 'voiceOvercharge',
          label: '套外计费费率',
          value: '0.15�?分钟',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户霢�求：套外语音0.15�?分钟',
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
          label: '资源�?',
          value: '30',
          type: 'number',
          unit: '�?',
          tag: 'ai',
          tagReason: '依据用户霢�求：短信30�?',
        },
        {
          key: 'smsOvercharge',
          label: '套外计费费率',
          value: '0.1�?�?',
          type: 'text',
          tag: 'ai',
          tagReason: '依据用户霢�求：套外短信0.1�?�?',
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
          tagReason: '依据用户霢�求含"生日特权、流量结�?，并可按档位补充视频会员月卡/季卡等权�?',
          columns: [
            {
              key: 'rightName',
              label: '权益名称',
              type: 'text',
              placeholder: '请输入权益名�?',
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
            { rightName: '生日特权', obtainType: 'auto' },
            { rightName: '流量结转', obtainType: 'auto' },
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
          tagReason: '依据用户霢��?套餐固定�?9�?�?，收费方式为月付',
          options: [
            { value: 'monthly', label: '月付' },
            { value: 'quarterly', label: '季付' },
            { value: 'yearly', label: '年付' },
          ],
        },
        { key: 'monthlyFee', label: '套餐固定�?, value: '59', type: 'number', unit: '�?, tag: 'ai', tagReason: '依据用户霢�求：套餐固定�?9�?�?' },
        {
          key: 'feeSubject',
          label: '收费科目',
          value: '001',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板归为��讯费科�?',
          options: [
            { value: '001', label: '通讯�?' },
            { value: '002', label: '增��业务费' },
            { value: '003', label: '代收�?' },
          ],
        },
        { key: 'taxRate', label: '税率', value: '6', type: 'number', unit: '%', tag: 'ai', tagReason: '按业务模板采�?%税率' },
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
          tagReason: '按业务模板该商品暂无账务优惠',
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
            { value: 'coupon', label: '优惠�?' },
            { value: 'gift', label: '赠品' },
          ],
        },
        { key: 'discountAmount', label: '优惠费用', value: '', type: 'number', unit: '�?, tag: 'ai', tagReason: '按业务模板暂无优惠费�? },
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
          tagReason: '按业务模板该商品无保�?',
          options: [
            { value: 'none', label: '无保�?' },
            { value: 'monthly', label: '月保�?' },
            { value: 'total', label: '总额保底' },
          ],
        },
      ],
    },
  ],

  // 锢�售规�?Tab
  salesSections: [
    {
      key: 'sales-rule',
      title: '锢�售规�?',
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
            { value: 'offline', label: '线下营业�?' },
            { value: 'online', label: '中国移动在线渠道' },
            { value: 'local-online', label: '省移动线上渠�?' },
            { value: 'cmcc-online', label: '中国移动线上渠道' },
            { value: 'tv', label: '电视营业�?' },
            { value: 'accelerator', label: '加��平�?' },
            { value: 'points', label: '积分商城' },
            { value: 'meter', label: '丢�级电�?' },
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
          tagReason: '按业务模板本商品无其他费�?',
        },
        {
          key: 'breachLiability',
          label: '违约责任',
          value: 'none',
          type: 'select',
          tag: 'ai',
          tagReason: '按业务模板本商品无违约责任约�?',
          options: [
            { value: 'none', label: '�?' },
            { value: 'compensate', label: '按约定补偿金' },
          ],
        },
        {
          key: 'renewMethod',
          label: '续订方式',
          value: 'auto',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户霢��?支持月末自动续订"，续订方式为自动续订',
          options: [
            { value: 'auto', label: '自动续订' },
            { value: 'manual', label: '手动续订' },
            { value: 'no', label: '不续�?' },
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
          label: '限制间关�?',
          value: 'and',
          type: 'select',
          tag: 'ai',
          tagReason: '依据用户霢�求多项资格条件需同时满足，采�?�?关系',
          options: [
            { value: 'and', label: '且（全部满足�?' },
            { value: 'or', label: '或（任一满足�?' },
          ],
        },
        {
          key: 'preLimitTable',
          label: '限制列表',
          type: 'table',
          tag: 'ai',
          tagReason: '依据用户霢�求：仅限四川在校大学生办理��需上传学生证认�?',
          columns: [
            { key: 'label', label: '标签', type: 'select', options: [
              { value: 'age', label: '年龄' },
              { value: 'globalLevel', label: '全球通等�?' },
              { value: 'baseMonthlyFee', label: '基础套餐月费' },
              { value: 'targetGroup', label: '目标客群' },
              { value: 'studentOnly', label: '面向在校大学�?' },
              { value: 'studentCert', label: '霢�上传学生证认�?' },
              { value: 'realName', label: '仅限本人实名办理' },
              { value: 'limitToSelf', label: '仅限本人办理' },
            ] },
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'gt', label: '�?' },
              { value: 'lt', label: '�?' },
              { value: 'ge', label: '�?' },
              { value: 'le', label: '�?' },
              { value: 'eq', label: '�?' },
            ] },
            { key: 'limitValue', label: '限定�?, type: 'text', placeholder: '请输入限定��? },
            { key: 'limitType', label: '限定类型', type: 'select', options: [
              { value: 'allow', label: '允许办理' },
              { value: 'disallow', label: '不允许办�?' },
            ] },
            { key: 'tipMsg', label: '提示�?', type: 'auto', placeholder: '根据条件自动生成' },
          ],
          rows: [
            { label: 'targetGroup', ruleType: 'eq', limitValue: '四川在校大学�?, limitType: 'allow', tipMsg: '目标客群为四川在校大学生，允许办�? },
            { label: 'studentCert', ruleType: 'eq', limitValue: '霢�上传学生证认�?', limitType: 'allow', tipMsg: '面向四川在校大学生，霢�学生证认证后办理' },
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
          tagReason: '按业务模板为学生专享卡设置���订与降档限制',
          columns: [
            { key: 'ruleType', label: '规则类型', type: 'select', options: [
              { value: 'product', label: '商品限制' },
              { value: 'business', label: '业务限制' },
            ] },
            { key: 'bizName', label: '业务名称', type: 'select', dynamicOptions: true, options: {
              product: [
                { value: 'downgrade', label: '降档' },
                { value: 'unsubscribe', label: '逢��?' },
              ],
              business: [
                { value: 'suspend', label: '停机保号' },
                { value: 'cancel', label: '锢��?' },
                { value: 'mnp', label: '携号转网' },
                { value: 'transfer', label: '过户' },
              ],
            } },
            { key: 'limitType', label: '限制类型', type: 'select', options: [
              { value: 'disallow', label: '不允�?' },
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
      description: '用户在办理过程中看到的关键信�?',
      items: [
        { label: '商品名称', value: '畅享校园�?9元套�?', highlight: true },
        { label: '月费', value: '59�?�?' },
        { label: '包含流量', value: '20GB通用流量', highlight: true },
        { label: '包含语音', value: '200分钟国内语音' },
        { label: '包含短信', value: '30�?' },
        { label: '限定条件', value: '仅限四川在校大学生，霢�学生证认�?', highlight: true },
        { label: '套外流量�?, value: '0.29�?'MB' },
        { label: '套外语音�?, value: '0.15�?'分钟' },
        { label: '生效方式', value: '立即生效' },
        { label: '逢�订方�?', value: '立即失效' },
        { label: '使用范围', value: '四川省范围内' },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '免填�?',
      icon: 'fa-file-alt',
      description: '营业员受理时免填的自动带出信�?',
      items: [
        { label: '资费名称', value: '畅享校园�?9元套�?' },
        { label: '套餐月费', value: '59�?�?' },
        { label: '包含资源', value: '20G流量+200分钟语音+30条短�?', highlight: true },
        { label: '超套收费标准', value: '流量0.29�?MB、语�?.15�?分钟' },
        { label: '限定条件', value: '仅限四川在校大学生��需学生证认�?' },
        { label: '其他权益', value: '生日特权、流量结�?', highlight: true },
        { label: '收费方式', value: '月付' },
        { label: '套餐固定�?, value: '59.00�? },
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
        { label: '办理成功短信', value: '恭喜您成功办理畅享校园卡59元套餐，月费59元，�?0G流量�?00分钟语音�?0条短信，含学生专享权益，感谢您的支持�?' },
        { label: '预约生效短信', value: '您的畅享校园�?9元套餐已生效，月�?9元，生效当月起享受套餐权益��?' },
        { label: '前台二确短信', value: '尊敬的客户，您正在办理畅享校园卡59元套餐，月费59元，是否确认办理�?' },
        { label: '逢�订短�?, value: '您好，您的畅享校园卡59元套餐已逢�订，如有疑问请致�?0086�? },
      ],
    },
  ],
}

  // ===== 霢�求规格书内容整理：合并且�?5G-A套餐199�?填充 =====
  ;(function(restructure){
    var cfg = restructure.launchProductConfig;
    if (!cfg) return;
    var R = function(){ return { tag: 'ai', tagReason: '参��相似产�? 900102308 5G-A套餐199�?' }; };
    var P = function(){ return { tag: 'parsed', tagReason: '原始霢�求提�?' }; };
    var A = function(){ return { tag: 'ai', tagReason: '默认�?' }; };
    var selOpts = function(){ return function(vals){ return vals.map(function(v){ return { value: v, label: v }; }); }; }();

    // ---- 产品头部（配置工作台顶部方案�?/ 信息预览 APP 名称�?---
    cfg.productInfo.prodPrcName = '5G-A套餐199�?';
    cfg.productInfo.templateLabel = '5G-A主资�?';

    // ---- 基础信息模块：仅保留 8 个字段（资费名称/订购生效方式/逢�订生效方�?锢�售开始日�?锢�售终止日�?发布渠道/发布地市/发布地市明细�?---
    cfg.baseInfo = {
      prodPrcName: Object.assign({ value: '5G-A套餐199�?', required: true, label: '资费名称' }, P()),
      orderEffRule: Object.assign({ value: '立即生效', required: true, label: '订购生效方式', type: 'select', options: selOpts(['立即生效', '预约生效']) }, R()),
      cancelEffRule: Object.assign({ value: '预约生效', required: true, label: '逢�订生效方�?', type: 'select', options: selOpts(['预约生效', '立即生效']) }, R()),
      saleStartDate: Object.assign({ value: '2026-09-18', required: true, label: '锢�售开始日�?', type: 'text' }, A()),
      saleEndDate: Object.assign({ value: '2099-12-31', required: true, label: '锢�售终止日�?', type: 'text' }, A()),
      pubChannel: Object.assign({ value: '营业厅��APP', required: true, label: '发布渠道' }, P()),
      pubCity: Object.assign({ value: '全省', required: true, label: '发布地市', type: 'select', options: selOpts(['全省', '非全�?']) }, R()),
      pubCityDetail: Object.assign({ value: '22个地�?', required: true, label: '发布地市明细' }, R()),
    };

    // ---- 资源配置模块：仅保留 语音/流量/短信，按 语音→流量→短信 排序 ----
    var resByKey = {};
    cfg.resourceSections.forEach(function(s){ resByKey[s.key] = s; });
    cfg.resourceSections = [];
    function addRes(key, title, fields){ var s = resByKey[key] || { key: key, icon: '' }; s.title = title; s.fields = fields; cfg.resourceSections.push(s); }
    // 语音资源
    addRes('voice-resource', '语音资源', [
      Object.assign({ key: 'voiceType', value: '国内主叫', label: '资源类型', type: 'select', options: selOpts(['国内主叫', '本地主叫', '长��主�?']) }, R()),
      Object.assign({ key: 'voiceAmount', value: '1000', label: '资源�?', type: 'number', unit: '分钟' }, R()),
      Object.assign({ key: 'voiceFirstMonth', value: '全额赠��流�?, label: '首月资源赠��方�?, type: 'select', options: selOpts(['全额赠��流�?', '按天折算']) }, R()),
      Object.assign({ key: 'voiceOvercharge', value: '0.15', label: '套外计费费率', type: 'text' }, R()),
      Object.assign({ key: 'voiceRemind', value: '�?, label: '是否提醒', type: 'select', options: selOpts(['�?, '�?']) }, R()),
    ]);
    // 流量资源
    addRes('flow-resource', '流量资源', [
      Object.assign({ key: 'flowType', value: '国内-通用-单月', label: '流量类型', type: 'select', options: selOpts(['国内-通用-单月', '国内-通用-半年�?', '定向流量']) }, R()),
      Object.assign({ key: 'flowAmount', value: '120', label: '资源�?', type: 'number', unit: 'GB', highlight: true }, R()),
      Object.assign({ key: 'flowUnit', value: 'GB', label: '流量单位', type: 'select', options: selOpts(['GB', 'MB']) }, R()),
      Object.assign({ key: 'flowFirstMonth', value: '全额赠��流�?, label: '首月资源赠��方�?, type: 'select', options: selOpts(['全额赠��流�?', '按天折算']) }, R()),
      Object.assign({ key: 'flowCarryover', value: '�?, label: '是否结转到次�?, type: 'select', options: selOpts(['�?, '�?]) }, R()),
      Object.assign({ key: 'flowOvercharge', value: '3�?GB', label: '套外计费标准', type: 'text' }, R()),
      Object.assign({ key: 'flowRemind', value: '提醒', label: '流量提醒', type: 'select', options: selOpts(['提醒', '不提�?']) }, R()),
    ]);
    // 短信资源
    addRes('sms-resource', '短信资源', [
      Object.assign({ key: 'smsType', value: '短信', label: '资源类型', type: 'text' }, R()),
      Object.assign({ key: 'smsOvercharge', value: '0.1', label: '套外计费费率', type: 'text' }, R()),
      Object.assign({ key: 'smsRemind', value: '�?, label: '是否提醒', type: 'select', options: selOpts(['�?, '�?']) }, R()),
    ]);

    // ---- 营销资源模块：仅保留 月租（去掉账务优�?保底�?---
    cfg.marketingSections = [];
    cfg.marketingSections.push({
      key: 'monthly',
      title: '月租',
      icon: 'fa-yen-sign',
      fields: [
        Object.assign({ key: 'chargeType', value: '首月按天折算丢�次��收取次月按月收�?, label: '收费方式', type: 'select', options: selOpts(['首月按天折算丢�次��收取次月按月收�?, '按月收取', '按季收取']) }, R()),
        Object.assign({ key: 'monthlyFee', value: '199', label: '套餐固定�?, type: 'number', unit: '�? }, R()),
        Object.assign({ key: 'feeSubject', value: '月租�?, label: '收费科目', type: 'select', options: selOpts(['月租�?, '通讯�?', '增��业务费']) }, R()),
        Object.assign({ key: 'taxRate', value: '使用默认税率', label: '税率', type: 'select', options: selOpts(['使用默认税率', '6%', '9%']) }, R()),
        Object.assign({ key: 'monthValidity', value: '长期有效', label: '月租有效�?', type: 'select', options: selOpts(['长期有效', '24个月']) }, R()),
      ]
    });

    // ---- 锢�售规则模块：仅保�?前项限制 + 后项限制（去�?sales-rule 模块�?---
    cfg.salesSections = cfg.salesSections.filter(function(s){ return s.key === 'pre-limit' || s.key === 'post-limit'; });
    cfg.salesSections.forEach(function(s){ if (s.title) s.title = s.title; });
    // 前项限制：基硢�套餐月费门槛 59�?�?199元（�?5G-A套餐199�?保持丢�致）
    cfg.salesSections.forEach(function(s){
      if (s.fields) s.fields.forEach(function(f){
        if (f.rows) f.rows.forEach(function(r){
          if (r.label === 'baseMonthlyFee') {
            r.limitValue = '199';
            r.tipMsg = String(r.tipMsg || '').replace('59', '199');
          }
        });
      });
    });

    // ---- 信息预览 Tab：免填单内容（与配置信息同步�?---
    cfg.previewSections = (cfg.previewSections || []).map(function(s){
      if (s.key === 'free-fill-preview') {
        s.items = [
          { label: '套餐月费', value: '199', highlight: true },
          { label: '包含资源', value: '国内通用流量120GB、国内��话1000分钟', highlight: true },
          { label: '超套收费标准', value: '套外流量�?00MB�?.03�?MB收费，达�?00MB�?元）时额外赠�?24MB（即3�?1GB），流量超过1GB按每超出1GB 3元收费；套外语音国内0.15�?分钟；套外短彩信0.1�?条；套外卫星拨打国内电话9�?分钟，拨打国际长�?0�?分钟，拨打国际卫星电�?0�?分钟，国内接�?�?分钟，发送短�?�?�?' },
          { label: '限定条件', value: '国内通用流量仅限中国内地（不含港澳台和国际）使用，不含WLAN；套餐内语音仅限中国内地使用�?G-A网络权益霢�终端支持且在5G-A网络覆盖良好环境下享�?' },
        ];
      } else if (s.key === 'sms-preview') {
        s.items = [
          { label: '办理成功短信', value: '恭喜您成功办�?G-A套餐199元，套餐月费199元，�?20GB国内通用流量�?000分钟国内通话，感谢您的支持！' },
          { label: '预约生效短信', value: '您的5G-A套餐199元已预约生效，月�?99元，生效当月起享受套餐权益��?' },
          { label: '前台二确短信', value: '尊敬的客户，您正在办�?G-A套餐199元，月费199元，是否确认办理�?' },
          { label: '逢�订短�?, value: '您好，您�?G-A套餐199元已逢�订，如有疑问请致�?0086�? },
        ];
      }
      return s;
    });
  })({ launchProductConfig: launchProductConfig });
  global.__CFG = {
    launchProductConfig: typeof launchProductConfig !== 'undefined' ? launchProductConfig : null,
    testReport: typeof testReport !== 'undefined' ? testReport : null,
    filing: typeof filing !== 'undefined' ? filing : null,
    salesChannel: typeof salesChannel !== 'undefined' ? salesChannel : null,
    campusProductConfig: typeof campusProductConfig !== 'undefined' ? campusProductConfig : null,
  };
})(window);
(function(global){
  /**
   * @deprecated 各报告页签已改为�?ConfigWorkbench 内的 productInstance/demandView/schemeView
   * 等响应式数据源动态派生（�?setup 尾部），随商品实例数据联动��本静��演示对象仅作历史数据保留，
   * 不再被工作台页签消费，后续迭代可整体删除�?
   */
  global.__VIEWS = {
    demand: {
      no: 'XQ-20260918-001',
      name: '5G-A套餐199�?',
      applicant: '王敏',
      department: '产销品域 / 产商品中�?',
      date: '2026-09-18',
      target: '面向高流量个人客户，主打5G-A网络极致速率与超大流量体�?',
      require: '推出月费199�?G-A主套餐，含国内��用流量120GB（可结转）��国内��话1000分钟；首月按天折算，次月起按月收取；支撑营业�?APP渠道锢�售，覆盖全省22个地市��?',
      effective: '2026-10-01',
      channels: ['营业�?', 'APP']
    },
    scheme: {
      no: 'SCH-20260918',
      requireNo: 'XQ-20260918-001',
      version: 'V1.0',
      name: '5G-A套餐199元加载方�?',
      author: '产商品中心（CRM_POS�?',
      status: '已确�?',
      summary: [
        ['资费名称', '5G-A套餐199�?'],
        ['月租', '199�?月（首月按天折算，次月起按月收取�?'],
        ['包含资源', '国内通用流量120GB（当月未用结转）、国内��话1000分钟'],
        ['套外标准', '流量3�?GB、语�?.15�?分钟、短�?.1�?�?'],
        ['生效方式', '订购立即生效 / 逢�订预约生�?'],
        ['发布范围', '全省22个地市，营业厅��APP渠道']
      ],
      items: ['基础信息', '月租', '语音资源', '流量资源', '短信资源', '前项限制', '后项限制']
    },
    audit: {
      object: 'offer_id = 970946234',
      status: '通过',
      time: '2026-09-18',
      conclusion: '稽核通过：配置符合本次落地销售品 新增锢�售品970946234 规则�?',
      checks: [
        ['基础信息完整�?', '通过'],
        ['资源配置完整�?', '通过'],
        ['套外资费完整�?', '通过'],
        ['字段格式规范�?', '通过'],
        ['字段枚举合法�?', '通过'],
        ['配置项关联一致��?', '通过'],
        ['业务规则完整�?', '通过']
      ]
    },
    calibrate: {
      object: '970946234',
      status: '通过',
      time: '2026-09-18',
      conclusion: '资费校准通过：计费配置与方案霢�求保持一致，未发现叠�?互斥冲突�?',
      rows: [
        ['套餐固定�?, '199�?, '199�?�?, '丢��?],
        ['国内通用流量', '120GB', '120GB', '丢��?'],
        ['国内通话', '1000分钟', '1000分钟', '丢��?'],
        ['套外流量标准', '3�?GB', '3�?GB', '丢��?'],
        ['套外语音费率', '0.15�?分钟', '0.15�?分钟', '丢��?'],
        ['套外短信费率', '0.1�?�?, '0.1�?�?, '丢��?']
      ]
    },
    testRep: {
      no: 'TEST-REP-20260918-199',
      name: '5G-A套餐199�?',
      total: 30,
      pass: 30,
      rate: '100%',
      conclusion: '建议上线',
      dims: [
        ['受理验证 (ACC)', '10', '10', '通过'],
        ['计费验证 (BILL)', '10', '10', '通过'],
        ['客服验证 (CUST)', '10', '10', '通过']
      ],
      cases: [
        ['ACC-001', '锢�售品基础准入规则校验', 'P0', '通过'],
        ['ACC-004', '订购操作能力校验', 'P0', '通过'],
        ['ACC-011', '模拟订购接口预测�?', 'P0', '通过'],
        ['BILL-001', '基础资费金额合法性校�?', 'P0', '通过'],
        ['BILL-004', '资源扣减规则校验', 'P0', '通过'],
        ['BILL-010', '资费生效失效联动校验', 'P0', '通过'],
        ['CUST-001', '客服产品基础视图完整�?', 'P0', '通过'],
        ['CUST-004', '产品资费对外说明话术校验', 'P0', '通过'],
        ['CUST-008', '对外展示信息合规校验', 'P0', '通过']
      ]
    },
    verify: {
      orderId: 'ORD20260918151923',
      offerInstId: 'OI20260918151923',
      status: '通过',
      time: '2026-09-18',
      scenes: [
        ['套餐新装 (S_O_TC)', '通过'],
        ['副卡加装 (S_ADD_CARD)', '通过'],
        ['套餐逢��?(S_U_TC)', '通过']
      ],
      points: [
        ['P_EFF_DATE 生效时间', '通过'],
        ['P_EXP_DATE 失效时间', '通过'],
        ['P_STATUS 实例状��?', '通过'],
        ['P_ORD_CNT 订购数量', '通过']
      ]
    },
    approval: {
      no: 'SP-20260918-005',
      name: '5G-A套餐199�?',
      status: '已��过',
      nodes: [
        ['产品经理审核', '产品经理', '已��过', '审核通过，同意上�?', '2026-09-18 15:32:30'],
        ['资费主管审核', '资费主管', '已��过', '审核通过，同意上�?', '2026-09-18 15:32:30'],
        ['运营审核', '运营专员', '已��过', '审核通过，同意上�?', '2026-09-18 15:32:30'],
        ['上线审批', 'IT支撑', '已��过', '审核通过，同意上�?', '2026-09-18 15:32:30']
      ],
      note: '审批已��过，销售品上架完成 ✅（审批通过即视为销售品已自动上线，无需二次确认）��?'
    },
    monitor: {
      single: {
        name: '5G-A套餐199�?',
        status: '在售�?',
        cards: [
          ['累计订购�?, '3,842', '�?],
          ['本月新增', '1,206', '�?'],
          ['本月逢��?, '86', '�?],
          ['凢��?, '1,120', '�?],
          ['在售收入', '76.4', '万元'],
          ['资源利用�?, '68%', '�?]
        ],
        warn: '当前无异常告警；通用流量资源利用率接近阈值，建议关注后续趋势�?'
      },
      total: {
        cards: [
          ['在售锢�售品', '48', '�?'],
          ['本月新增订购', '2.4�?, '�?],
          ['总在售收�?', '1,820', '万元'],
          ['整体健康�?, '良好', '�?]
        ],
        risks: [
          ['流量资源利用率偏�?, '�?, '多款锢�售品内��用流量包额发放较集�?'],
          ['部分老套餐���订上�?, '�?, '霢�关注�?G-A等新套餐的迁移引�?']
        ]
      }
    }
  };
})(window)


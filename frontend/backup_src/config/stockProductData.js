/**
 * stockProductData - 存量产品库（mock）
 *
 * 用于「存量产品复制配置」场景：用户通过产品编码/产品名称检索存量产品，
 * AI 返回符合条件的类似产品列表，点击「查看详情」打开该产品的只读配置档案，
 * 在档案工作台内点击「复制」即可将其配置复制为可编辑的新产品。
 *
 * 每个存量产品提供：
 *   code       产品编码（检索槽位：产品编码）
 *   name       产品名称（检索槽位：产品名称）
 *   templateLabel 业务模板
 *   summary    一行概要（列表展示）
 *   snapshot   { productInfo, values } —— 与配置档案 archiveSnapshot 同构，供只读展示与复制预填
 */

// 依据标准配置 schema（launchOptimizeData 字段 key）生成一份完整 values
const STANDARD_VALUES = {
  // 基础信息
  prodAttr: 'personMainPrc',
  prodCode: 'SP2024059001',
  prodName: '畅享59元套餐',
  onlineDate: '20250101000000',
  offlineDate: '20991231235959',
  effRule: 'immediate',
  validity: 'longTerm',
  displayCycle: '3',
  cancelRule: 'immediate',
  refProduct: '',
  orderTimes: '1',
  totalOrderTimes: '',
  uploadYijing: 'no',
  l1ProdCode: 'APAP00590',
  isComm: 'yes',
  applyBasis: '省公司2024年第089号文',
  allowChannel: 'yes',
  // 流量
  flowType: 'general',
  flowAmount: '40',
  flowUnit: 'GB',
  flowOvercharge: '0.29元/MB',
  flowCarryover: 'yes',
  // 语音
  voiceType: 'local',
  voiceAmount: '100',
  voiceOvercharge: '0.15元/分钟',
  // 短信
  smsAmount: '50',
  smsOvercharge: '0.1元/条',
  // 权益
  rightsTable: [
    { rightName: '视频会员月卡', obtainType: 'auto' },
    { rightName: '定向免流权益包', obtainType: 'manual' },
  ],
  // 营销
  chargeType: 'monthly',
  monthlyFee: '59.00',
  feeSubject: '001',
  taxRate: '6',
  discountCondition: '',
  discountType: 'cash',
  discountAmount: '',
  minConsumeType: 'none',
  // 销售规则
  channelType: ['offline', 'online'],
  applicableArea: ['sc'],
  otherFee: '0',
  breachLiability: 'none',
  renewMethod: 'auto',
  preLimitRelation: 'and',
  preLimitTable: [],
  postLimitTable: [],
}

// 依据 base 生成每档产品的完整 snapshot
function buildStock(code, name, templateLabel, overrides = {}, meta = {}) {
  const values = { ...STANDARD_VALUES, prodCode: code, prodName: name, ...overrides }
  return {
    code,
    name,
    templateLabel,
    summary: meta.summary || `${name}`,
    tags: meta.tags || [],
    snapshot: {
      productInfo: {
        workOrderId: code,
        prodPrcName: name,
        templateLabel,
        status: 'listed',
      },
      values,
      // 已上架存量产品的历史测试报告/备案公示/销售渠道快照（供只读档案页签展示）
      testReport: buildStockTestReport(code, name, values),
      filing: buildStockFiling(code, name, values),
      channel: buildStockChannel(code, name, values),
    },
  }
}

// 存量产品历史测试报告（全部已通过，数据与产品资费要素联动）
function buildStockTestReport(code, name, v) {
  const seq = code.slice(-4) || '0000'
  return {
    uploaded: true,
    uploadedAt: Date.now(),
    reportId: `TR20${seq}001`,
    workOrderId: code,
    productName: name,
    tester: '张明',
    testDate: '2026-08-13',
    status: 'passed',
    testMethod: '新业务测试（含固费核查、计费核查、提醒核查、工单核查）',
    acceptNumber: `13920${seq}`,
    sections: [
      {
        key: 'fixedFee',
        title: '固费核查',
        status: 'passed',
        items: [
          { label: '月租固定费', expected: `${v.monthlyFee}元`, actual: `${v.monthlyFee}元`, result: 'pass' },
          { label: '固定费生效时间', expected: '订购次月1日生效', actual: '次月1日生效', result: 'pass' },
        ],
      },
      {
        key: 'billing',
        title: '计费核查',
        status: 'passed',
        items: [
          { label: '流量计费', expected: `${v.flowAmount}${v.flowUnit}内不额外计费，超出后${v.flowOvercharge}自动叠加`, actual: `${v.flowAmount}${v.flowUnit}内正常，超出按${v.flowOvercharge}叠加`, result: 'pass' },
          { label: '语音计费', expected: `${v.voiceAmount}分钟内免费，超出${v.voiceOvercharge}`, actual: `${v.voiceAmount}分钟内免费，超出${v.voiceOvercharge}`, result: 'pass' },
          { label: '短信计费', expected: `${v.smsAmount}条内免费，超出${v.smsOvercharge}`, actual: '计费正常', result: 'pass' },
        ],
      },
      {
        key: 'reminder',
        title: '提醒核查',
        status: 'passed',
        items: [
          { label: '流量使用提醒', expected: '使用达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
          { label: '套餐订购提醒', expected: '订购成功后下发欢迎短信', actual: '欢迎短信正常下发', result: 'pass' },
        ],
      },
      {
        key: 'workOrder',
        title: '工单核查',
        status: 'passed',
        items: [
          { label: '订购工单', expected: '生成订购工单，状态正常流转', actual: '工单生成且状态正确', result: 'pass' },
          { label: '退订工单', expected: '退订生成退订工单，次月生效', actual: '退订工单正确，次月生效', result: 'pass' },
        ],
      },
    ],
    conclusion: '通过',
    conclusionDesc: `该资费方案（${name}）各项核查均符合预期，测试通过，已进入上架销售阶段。`,
  }
}

// 存量产品历史备案公示快照（已备案通过）
function buildStockFiling(code, name, v) {
  const seq = code.slice(-4) || '0000'
  return {
    started: true,
    submitted: true,
    approved: true,
    approvedAt: Date.now(),
    filingId: `FL20${seq}001`,
    workOrderId: code,
    productName: name,
    template: '个人主资费',
    productCode: v.prodCode,
    applicant: '产品配置专员',
    applyDate: '2026-08-14',
    info: {
      filingOrg: '产品研发中心',
      filingOfficer: '李芳',
      filingDate: '2026-08-14',
      effectiveDate: '2026-09-01',
      expireDate: '长期有效',
    },
    checks: [
      { label: '资费合规性', status: 'passed', desc: '符合《资费管理办法》相关规定，无违规项' },
      { label: '公示内容完整性', status: 'passed', desc: '资费名称、资费标准、生效时间等公示信息完整' },
      { label: '测试报告核销', status: 'passed', desc: `测试报告 TR20${seq}001 已通过，允许备案` },
      { label: '审批链完整性', status: 'passed', desc: '预览审批、测试审核审批链完整' },
    ],
    notice: {
      title: `关于"${name}"资费方案备案公示`,
      target: '全体用户',
      content: `现将"${name}"资费方案（资费代码：${v.prodCode}）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。`,
      notifyChannels: '营业厅公示栏、网上营业厅、掌上营业厅',
      startDate: '2026-08-15',
      endDate: '2026-08-29',
      duration: '15天',
    },
    sections: [
      {
        title: '套餐基本信息',
        previewOnly: false,
        fields: [
          { label: 'BOSSID', value: v.prodCode, category: 'filing' },
          { label: '名称', value: name, category: 'filing' },
          { label: '资费属性', value: '省内', category: 'filing' },
          { label: '适用范围', value: '通过前项限制生成', category: 'common' },
        ],
      },
      {
        title: '基础信息',
        previewOnly: false,
        fields: [
          { label: '资费标准', value: `${v.monthlyFee}元/月`, category: 'common' },
          { label: '资费类型', value: '套餐', category: 'public' },
          { label: '有效期限', value: '长期', category: 'common' },
          { label: '销售渠道', value: '自有营业厅/网上营业厅', category: 'common' },
        ],
      },
      {
        title: '资源信息',
        previewOnly: false,
        fields: [
          { label: '国内通话', value: `${v.voiceAmount}分钟`, category: 'common' },
          { label: '国内通用流量', value: `${v.flowAmount}${v.flowUnit}`, category: 'common' },
          { label: '短信', value: `${v.smsAmount}条`, category: 'common' },
          { label: '权益', value: (v.rightsTable || []).map((r) => r.rightName).join(' + ') || '无', category: 'common' },
        ],
      },
    ],
  }
}

// 存量产品历史销售渠道快照（已配置渠道）
function buildStockChannel(code, name, v) {
  const selected = v.channelType || ['offline', 'online']
  return {
    available: true,
    submitted: true,
    list: [
      { key: 'own_offline', label: '自有营业厅', desc: '实体营业厅线下受理' },
      { key: 'own_online', label: '网上营业厅', desc: '官方网上营业厅在线订购' },
      { key: 'app', label: '掌上营业厅APP', desc: '手机营业厅客户端' },
      { key: 'customer_service', label: '10086客服热线', desc: '电话客服人员受理' },
      { key: 'channel_coop', label: '社会合作渠道', desc: '第三方合作代理商' },
    ],
    selected: selected.includes('offline') && selected.includes('online')
      ? ['own_offline', 'own_online', 'app']
      : ['own_offline', 'own_online'],
  }
}

export const stockProducts = [
  buildStock('SP2024059001', '畅享59元套餐', '个人主资费', {
    flowAmount: '40',
    monthlyFee: '59.00',
    voiceAmount: '100',
  }, {
    summary: '59元/月 · 40GB通用流量 · 100分钟语音',
    tags: ['5G', '通用流量', '长期有效'],
  }),
  buildStock('SP2024061203', '动感地带59元套餐', '个人主资费', {
    flowAmount: '30',
    monthlyFee: '59.00',
    voiceAmount: '200',
    smsAmount: '20',
  }, {
    summary: '59元/月 · 30GB通用流量 · 200分钟语音 · 20条短信',
    tags: ['校园', '青年', '通用流量'],
  }),
  buildStock('SP2024070815', '畅享校园卡39元套餐', '个人主资费', {
    flowAmount: '10',
    monthlyFee: '39.00',
    voiceAmount: '100',
    smsAmount: '20',
    preLimitTable: [
      { label: 'targetGroup', ruleType: 'eq', limitValue: '在校大学生', limitType: 'allow', tipMsg: '目标客群为在校大学生，允许办理' },
      { label: 'studentCert', ruleType: 'eq', limitValue: '需上传学生证认证', limitType: 'allow', tipMsg: '面向在校大学生，需学生证认证后办理' },
    ],
  }, {
    summary: '39元/月 · 10GB通用流量 · 100分钟语音 · 20条短信',
    tags: ['校园卡', '学生', '通用流量'],
  }),
  buildStock('SP2024082002', '智享99元5G套餐', '政企资费', {
    flowAmount: '60',
    monthlyFee: '99.00',
    voiceAmount: '500',
    smsAmount: '100',
    prodAttr: 'enterprisePrc',
  }, {
    summary: '99元/月 · 60GB5G流量 · 500分钟语音',
    tags: ['5G', '政企', '高速'],
  }),
]

/**
 * 按检索条件过滤存量产品。
 * @param {{code?:string, name?:string}} cond
 * @returns {Array} 命中的产品（名称做包含/相似匹配）
 */
export function searchStockProducts({ code = '', name = '' } = {}) {
  const codeKw = (code || '').trim().toLowerCase()
  const nameKw = (name || '').trim().toLowerCase()
  return stockProducts.filter((p) => {
    let ok = true
    if (codeKw && !p.code.toLowerCase().includes(codeKw)) ok = false
    if (nameKw && !p.name.includes(nameKw)) ok = false
    return ok
  })
}

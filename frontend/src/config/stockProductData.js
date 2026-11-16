/**
 * stockProductData - 存量产品库（mock）· WeWalk 国际漫游数据卡
 *
 * 用于「存量产品复制配置」场景：用户通过产品编码/产品名称检索存量产品，
 * AI 返回符合条件的类似产品列表，点击「查看详情」打开该产品的只读配置档案，
 * 在档案工作台内点击「复制」即可将其配置复制为可编辑的新产品。
 *
 * 本库产品统一为 WeWalk-Data Only 系列（澳门.香港.台湾共用、Data Only、一次性、按天有效）：
 *   不同档位 = 有效期（1日/3日/15日/30日）+ 高速配额（800MB/日、1.5GB/日、3GB/日、总量GB）
 *   高速用尽后不限量限速。
 *
 * 每个存量产品提供：
 *   code       产品编码（检索槽位：产品编码）
 *   name       产品名称（检索槽位：产品名称）
 *   templateLabel 业务模板
 *   summary    一行概要（列表展示）
 *   snapshot   { productInfo, values } —— 与配置档案 archiveSnapshot 同构，供只读展示与复制预填
 */

// 依据标准配置 schema（launchOptimizeData 字段 key）生成一份完整 values（Data Only 数据卡）
const STANDARD_VALUES = {
  // 基础信息
  productType: 'main',
  cardType: 'data',
  networkType: '5g',
  openTemplate: 'person',
  roamTemplate: 'CMCC-ROAM-HK-TW',
  prodCode: 'SP-HK-TW-1D',
  prodName: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)',
  description: '',
  publishTenant: '租户1',
  prodAttr: 'personMainPrc',
  onlineDate: '20250101000000',
  offlineDate: '20991231235959',
  effRule: 'immediate',
  cancelRule: 'immediate',
  refProduct: '',
  l1ProdCode: 'APAPROAM',
  isComm: 'yes',
  applyBasis: '国际漫游产品2025年第012号文',
  allowChannel: 'yes',
  // 属性信息
  chargeType: 'once',
  validity: '1d',
  resetCycle: 'fixedDays',
  // 数据资源
  dataAmount: '800', // MB/日 高速配额（数字），unit 由 MB/GB 决定
  dataAmountUnit: 'MB',
  dataRoamArea: ['hk-mo-tw'],
  throttlePolicy: 'pcrf',
  throttleTemplate: [{ maxRate: '1Mbps', triggerAmount: '800MB', remark: '800MB高速用尽后限速1Mbps不限量' }],
  // 语音/短信：Data Only，无
  voiceAmount: '',
  smsAmount: '',
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
      filing: buildStockFiling(code, name, templateLabel, values),
      channel: buildStockChannel(code, name, values),
    },
  }
}

// 存量产品历史测试报告（全部已通过，数据与产品资费要素联动）
function buildStockTestReport(code, name, v) {
  const seq = code.replace(/[^0-9]/g, '').slice(-4) || '0001'
  const dataRes = `${v.dataAmount}${v.dataAmountUnit || 'MB'}/日高速`
  const reachDesc = `用尽后不限量限速`
  return {
    uploaded: true,
    uploadedAt: Date.now(),
    reportId: `TR20${seq}001`,
    workOrderId: code,
    productName: name,
    tester: '张明',
    testDate: '2026-08-13',
    status: 'passed',
    testMethod: '新业务测试（含资费核查、计费核查、提醒核查、工单核查）',
    acceptNumber: `13920${seq}`,
    sections: [
      {
        key: 'fixedFee',
        title: '资费核查',
        status: 'passed',
        items: [
          { label: '资费类型', expected: '一次性资费', actual: '一次性资费', result: 'pass' },
          { label: '有效期', expected: v.validityLabel || '自然天', actual: v.validityLabel || '自然天', result: 'pass' },
        ],
      },
      {
        key: 'billing',
        title: '计费核查',
        status: 'passed',
        items: [
          { label: '数据计费', expected: `${dataRes}，${reachDesc}`, actual: `${dataRes}，超出后限速不限量`, result: 'pass' },
          { label: '漫游计费', expected: '澳门.香港.台湾共用漫游同价，不加收漫游费', actual: '澳门.香港.台湾漫游计费正确', result: 'pass' },
        ],
      },
      {
        key: 'reminder',
        title: '提醒核查',
        status: 'passed',
        items: [
          { label: '流量使用提醒', expected: '高速用量达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
          { label: '订购提醒', expected: '订购成功后下发欢迎短信', actual: '欢迎短信正常下发', result: 'pass' },
        ],
      },
      {
        key: 'workOrder',
        title: '工单核查',
        status: 'passed',
        items: [
          { label: '订购工单', expected: '生成订购工单，状态正常流转', actual: '工单生成且状态正确', result: 'pass' },
          { label: '退订工单', expected: '退订生成退订工单，立即失效', actual: '退订工单正确，立即失效', result: 'pass' },
        ],
      },
    ],
    conclusion: '通过',
    conclusionDesc: `该资费方案（${name}）各项核查均符合预期，测试通过，已进入上架销售阶段。`,
  }
}

// 存量产品历史备案公示快照（已备案通过）
function buildStockFiling(code, name, templateLabel, v) {
  const seq = code.replace(/[^0-9]/g, '').slice(-4) || '0001'
  const dataRes = `${v.dataAmount}${v.dataAmountUnit || 'MB'}/日高速，用尽后不限量限速`
  return {
    started: true,
    submitted: true,
    approved: true,
    approvedAt: Date.now(),
    filingId: `FL20${seq}001`,
    workOrderId: code,
    productName: name,
    template: templateLabel,
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
          { label: '资费属性', value: '国际漫游', category: 'filing' },
          { label: '适用范围', value: '澳门.香港.台湾（共用）', category: 'common' },
        ],
      },
      {
        title: '基础信息',
        previewOnly: false,
        fields: [
          { label: '资费标准', value: '一次性资费', category: 'common' },
          { label: '资费类型', value: '主套餐·数据卡', category: 'public' },
          { label: '有效期限', value: v.validityLabel || '自然天', category: 'common' },
          { label: '销售渠道', value: '自有营业厅/网上营业厅', category: 'common' },
        ],
      },
      {
        title: '资源信息',
        previewOnly: false,
        fields: [
          { label: '高速配额', value: dataRes, category: 'common' },
          { label: '漫游地', value: '澳门.香港.台湾', category: 'common' },
          { label: '限速策略', value: 'PCRF（用尽后不限量限速）', category: 'common' },
          { label: '卡类型/网络', value: '5G数据卡（Data Only）', category: 'common' },
          { label: '语音/短信', value: '无（纯数据产品）', category: 'common' },
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
  buildStock('SP-HK-TW-1D', 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', '可选包·数据卡', {
    productType: 'addon',
    prodAttr: 'personAddonPrc',
    dataAmount: '800',
    dataAmountUnit: 'MB',
    validity: '1d',
    validityLabel: '1天',
  }, {
    summary: '1日不限量 · 800MB/日高速 · 用尽后限速不限量',
    tags: ['Data Only', '澳门.香港.台湾', '1日'],
  }),
  buildStock('SP-HK-TW-3D', 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', '个人主资费·数据卡', {
    dataAmount: '800',
    dataAmountUnit: 'MB',
    validity: '3d',
    validityLabel: '3天',
  }, {
    summary: '3日不限量 · 800MB/日高速 · 用尽后限速不限量',
    tags: ['Data Only', '澳门.香港.台湾', '3日'],
  }),
  buildStock('SP-HK-TW-15D', 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', '个人主资费·数据卡', {
    dataAmount: '15',
    dataAmountUnit: 'GB',
    validity: '15d',
    validityLabel: '15天',
  }, {
    summary: '15日不限量 · 15GB高速 · 用尽后限速不限量',
    tags: ['Data Only', '澳门.香港.台湾', '15日'],
  }),
  buildStock('SP-HK-TW-30D', 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', '个人主资费·数据卡', {
    dataAmount: '30',
    dataAmountUnit: 'GB',
    validity: '30d',
    validityLabel: '30天',
  }, {
    summary: '30日不限量 · 30GB高速 · 用尽后限速不限量',
    tags: ['Data Only', '澳门.香港.台湾', '30日'],
  }),
  buildStock('SP-HK-TW-30D-HIGH', 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', '个人主资费·数据卡', {
    dataAmount: '3',
    dataAmountUnit: 'GB',
    validity: '30d',
    validityLabel: '30天',
  }, {
    summary: '30日不限量 · 3GB/日高速 · 每日用尽后限速不限量',
    tags: ['Data Only', '澳门.香港.台湾', '30日', '高速'],
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
    if (nameKw && !p.name.toLowerCase().includes(nameKw)) ok = false
    return ok
  })
}

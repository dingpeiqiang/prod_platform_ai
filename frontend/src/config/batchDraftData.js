/**
 * batchDraftData - 文件导入批量配置 · 批量解析出的草稿产品数据
 *
 * 来源：《WeWalk套餐配置需求20230803(1).xlsx》第一个 Sheet（套餐配置需求202307010）解析出的
 * 3 个国际漫游数据卡产品（香港.台湾共用 1日 / 15日 / 30日 不限量）。
 * 字段口径对齐「配置需求表」的模板字段（產品編碼 / 產品名稱 / 漫遊地支撐 / 限速限分享規則 /
 * Service Name / Quota Name），每个草稿产品的 parsedFields 结构对齐「对话式配置」工作台
 * 的字段 key（见 launchOptimizeData.js）。
 *
 * 注意：workOrderId 为占位编号，实际导入时由 ChatPage 按「当天对话日期」重新生成
 *（WO + YYYYMMDD + 当日3位序号，与对话式配置规则一致），不必手工维护这里的日期。
 */

const roamPreLimit = [
  { label: 'targetGroup', ruleType: 'eq', limitValue: '出境用户（香港.台湾）', limitType: 'allow', tipMsg: '目标客群为赴香港.台湾的出境用户，允许办理' },
]

/**
 * 按「限速限分享規則」生成完整的 FUP 多档限速模版（限速模版表格行）。
 * @param {Array} tiers 每个档位：{ before: 触发上限描述, amount: 触发资源量, rate: 限速速率, share: 是否限分享 }
 */
const throttleTiers = (tiers) =>
  tiers.map((t) => ({
    maxRate: t.rate,
    triggerAmount: t.amount,
    remark: t.share ? `${t.before}限速${t.rate},限分享` : `${t.before}限速${t.rate}`,
  }))

// 与《WeWalk套餐配置需求20230803(1).xlsx》第一批 Sheet 的 Service/Quota 编号对应
// 1日800MB → 1777_Service_800MB_HK_TW / 1777_Quota_800MB_HK_TW
// 15日15GB → 1780_Service_15GB_HK_TW  / 1780_Quota_15GB_HK_TW
// 30日30GB → 1781_Service_30GB_HK_TW  / 1781_Quota_30GB_HK_TW

/**
 * 每个草稿产品：
 *   workOrderId  工单编号（来源 xlsx）
 *   prodName     产品名称
 *   summary      一行概要（列表展示）
 *   parsedFields 喂给工作台的字段（对话式配置 schema，Data Only 数据卡）
 */
export const batchDraftProducts = [
  {
    workOrderId: 'WO20260624001',
    prodCode: 'W23062000001',
    prodName: 'WeWalk-Data Only-香港.台湾共用1日不限量(800MB/日高速)',
    monthlyFee: '',
    dataAmount: '800',
    dataAmountUnit: 'MB',
    validity: '1d',
    summary: '1日不限量 · 800MB/日高速 · FUP每日多档限速 · 一次性资费',
    parsedFields: {
      productType: 'main',
      cardType: 'data',
      networkType: '5g',
      openTemplate: 'person',
      roamTemplate: 'CMCC-ROAM-HK-TW',
      prodCode: 'SP-HK-TW-1D',
      prodName: 'WeWalk-Data Only-香港.台湾共用1日不限量(800MB/日高速)',
      serviceName: '1777_Service_800MB_HK_TW',
      quotaName: '1777_Quota_800MB_HK_TW',
      publishTenant: '租户1',
      prodAttr: 'personMainPrc',
      effRule: 'immediate',
      cancelRule: 'immediate',
      l1ProdCode: 'APAPROAM',
      applyBasis: 'WeWalk套餐配置需求20230803',
      chargeType: 'once',
      validity: '1d',
      validityLabel: '1天',
      resetCycle: 'fixedDays',
      dataAmount: '800',
      dataAmountUnit: 'MB',
      dataRoamArea: ['hk-tw'],
      throttlePolicy: 'pcrf',
      throttleTemplate: throttleTiers([
        { before: '300MB前', amount: '300MB', rate: '14.4Mbps', share: false },
        { before: '300MB-600MB', amount: '600MB', rate: '5Mbps', share: false },
        { before: '600MB-800MB', amount: '800MB', rate: '1Mbps', share: false },
        { before: '800MB後', amount: '800MB以上', rate: '256Kbps', share: true },
      ]),
      channelType: ['offline', 'online'],
      applicableArea: ['hk-tw'],
      preLimitTable: roamPreLimit,
      voiceAmount: '',
      smsAmount: '',
    },
    tags: ['Data Only', '香港.台湾', '1日'],
  },
  {
    workOrderId: 'WO20260624002',
    prodCode: 'W23062000019',
    prodName: 'WeWalk-Data Only-香港.台湾共用15日不限量(15GB高速)',
    monthlyFee: '',
    dataAmount: '15',
    dataAmountUnit: 'GB',
    validity: '15d',
    summary: '15日不限量 · 15GB高速 · FUP多档限速 · 一次性资费',
    parsedFields: {
      productType: 'main',
      cardType: 'data',
      networkType: '5g',
      openTemplate: 'person',
      roamTemplate: 'CMCC-ROAM-HK-TW',
      prodCode: 'SP-HK-TW-15D',
      prodName: 'WeWalk-Data Only-香港.台湾共用15日不限量(15GB高速)',
      serviceName: '1780_Service_15GB_HK_TW',
      quotaName: '1780_Quota_15GB_HK_TW',
      publishTenant: '租户1',
      prodAttr: 'personMainPrc',
      effRule: 'immediate',
      cancelRule: 'immediate',
      l1ProdCode: 'APAPROAM',
      applyBasis: 'WeWalk套餐配置需求20230803',
      chargeType: 'once',
      validity: '15d',
      validityLabel: '15天',
      resetCycle: 'fixedDays',
      dataAmount: '15',
      dataAmountUnit: 'GB',
      dataRoamArea: ['hk-tw'],
      throttlePolicy: 'pcrf',
      throttleTemplate: throttleTiers([
        { before: '5GB前', amount: '5GB', rate: '14.4Mbps', share: false },
        { before: '5GB-8GB', amount: '8GB', rate: '7.2Mbps', share: false },
        { before: '8GB-15GB', amount: '15GB', rate: '3Mbps', share: true },
        { before: '15GB後', amount: '15GB以上', rate: '128Kbps', share: true },
      ]),
      channelType: ['offline', 'online'],
      applicableArea: ['hk-tw'],
      preLimitTable: roamPreLimit,
      voiceAmount: '',
      smsAmount: '',
    },
    tags: ['Data Only', '香港.台湾', '15日'],
  },
  {
    workOrderId: 'WO20260624003',
    prodCode: 'W23062000020',
    prodName: 'WeWalk-Data Only-香港.台湾共用30日不限量(30GB高速)',
    monthlyFee: '',
    dataAmount: '30',
    dataAmountUnit: 'GB',
    validity: '30d',
    summary: '30日不限量 · 30GB高速 · FUP多档限速 · 一次性资费',
    parsedFields: {
      productType: 'main',
      cardType: 'data',
      networkType: '5g',
      openTemplate: 'person',
      roamTemplate: 'CMCC-ROAM-HK-TW',
      prodCode: 'SP-HK-TW-30D',
      prodName: 'WeWalk-Data Only-香港.台湾共用30日不限量(30GB高速)',
      serviceName: '1781_Service_30GB_HK_TW',
      quotaName: '1781_Quota_30GB_HK_TW',
      publishTenant: '租户1',
      prodAttr: 'personMainPrc',
      effRule: 'immediate',
      cancelRule: 'immediate',
      l1ProdCode: 'APAPROAM',
      applyBasis: 'WeWalk套餐配置需求20230803',
      chargeType: 'once',
      validity: '30d',
      validityLabel: '30天',
      resetCycle: 'fixedDays',
      dataAmount: '30',
      dataAmountUnit: 'GB',
      dataRoamArea: ['hk-tw'],
      throttlePolicy: 'pcrf',
      throttleTemplate: throttleTiers([
        { before: '10GB前', amount: '10GB', rate: '14.4Mbps', share: false },
        { before: '10GB-15GB', amount: '15GB', rate: '7.2Mbps', share: false },
        { before: '15GB-30GB', amount: '30GB', rate: '3Mbps', share: true },
        { before: '30GB後', amount: '30GB以上', rate: '128Kbps', share: true },
      ]),
      channelType: ['offline', 'online'],
      applicableArea: ['hk-tw'],
      preLimitTable: roamPreLimit,
      voiceAmount: '',
      smsAmount: '',
    },
    tags: ['Data Only', '香港.台湾', '30日'],
  },
]

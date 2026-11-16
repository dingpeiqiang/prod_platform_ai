/**
 * batchDraftData - 文件导入批量配置 · 批量解析出的草稿产品数据
 *
 * 来源：《暑期促销活动方案.docx》解析出的 3 个产品（畅享校园卡 39/59/79 元套餐）。
 * 每个草稿产品的 parsedFields 结构对齐「对话式配置」工作台的字段 key（见 launchOptimizeData.js），
 * 未显式给出的字段由 AI 按业务模板自动补全（标注复用/AI推荐）。
 *
 * 注意：workOrderId 为占位编号，实际导入时由 ChatPage 按「当天对话日期」重新生成
 *（WO + YYYYMMDD + 当日3位序号，与对话式配置规则一致），不必手工维护这里的日期。
 */

const campusPreLimit = [
  { label: 'targetGroup', ruleType: 'eq', limitValue: '在校大学生', limitType: 'allow', tipMsg: '目标客群为在校大学生，允许办理' },
  { label: 'studentCert', ruleType: 'eq', limitValue: '需上传学生证认证', limitType: 'allow', tipMsg: '面向在校大学生，需学生证认证后办理' },
]

function rightsTable(names) {
  return (names || []).map((n) => ({ rightName: n, obtainType: 'auto' }))
}

/**
 * 每个草稿产品：
 *   workOrderId  工单编号（来源 docx）
 *   prodName     产品名称
 *   monthlyFee   套餐月费
 *   flowAmount   通用流量(GB)
 *   voiceAmount  本地语音(分钟)
 *   smsAmount    短信(条)
 *   rights       权益名称列表
 *   channelType  销售渠道
 *   parsedFields 喂给工作台的字段（对话式配置 schema）
 */
export const batchDraftProducts = [
  {
    workOrderId: 'WO20260624001',
    prodName: '畅享校园卡39元套餐',
    monthlyFee: '39',
    flowAmount: '10',
    voiceAmount: '100',
    smsAmount: '20',
    rights: ['生日特权', '流量结转'],
    channelType: ['offline'],
    summary: '39元/月 · 10GB通用流量 · 100分钟语音 · 20条短信',
    parsedFields: {
      prodName: '畅享校园卡39元套餐',
      prodCode: 'SP20260624001',
      chargeType: 'monthly',
      monthlyFee: '39',
      flowType: 'general',
      flowAmount: '10',
      flowUnit: 'GB',
      flowOvercharge: '0.29元/MB',
      voiceType: 'local',
      voiceAmount: '100',
      voiceOvercharge: '0.15元/分钟',
      smsAmount: '20',
      smsOvercharge: '0.1元/条',
      channelType: ['offline'],
      applicableArea: ['sc'],
      preLimitTable: campusPreLimit,
      rightsTable: rightsTable(['生日特权', '流量结转']),
      validity: 'longTerm',
      renewMethod: 'auto',
      orderTimes: '1',
    },
  },
  {
    workOrderId: 'WO20260624002',
    prodName: '畅享校园卡59元套餐',
    monthlyFee: '59',
    flowAmount: '20',
    voiceAmount: '100',
    smsAmount: '20',
    rights: ['生日特权', '流量结转', '视频会员'],
    channelType: ['offline'],
    summary: '59元/月 · 20GB通用流量 · 100分钟语音 · 20条短信',
    parsedFields: {
      prodName: '畅享校园卡59元套餐',
      prodCode: 'SP20260624002',
      chargeType: 'monthly',
      monthlyFee: '59',
      flowType: 'general',
      flowAmount: '20',
      flowUnit: 'GB',
      flowOvercharge: '0.29元/MB',
      voiceType: 'local',
      voiceAmount: '100',
      voiceOvercharge: '0.15元/分钟',
      smsAmount: '20',
      smsOvercharge: '0.1元/条',
      channelType: ['offline'],
      applicableArea: ['sc'],
      preLimitTable: campusPreLimit,
      rightsTable: rightsTable(['生日特权', '流量结转', '视频会员']),
      validity: 'longTerm',
      renewMethod: 'auto',
      orderTimes: '1',
    },
  },
  {
    workOrderId: 'WO20260624003',
    prodName: '畅享校园卡79元套餐',
    monthlyFee: '79',
    flowAmount: '30',
    voiceAmount: '300',
    smsAmount: '50',
    rights: ['生日特权', '流量结转', '视频会员'],
    channelType: ['offline'],
    summary: '79元/月 · 30GB通用流量 · 300分钟语音 · 50条短信',
    parsedFields: {
      prodName: '畅享校园卡79元套餐',
      prodCode: 'SP20260624003',
      chargeType: 'monthly',
      monthlyFee: '79',
      flowType: 'general',
      flowAmount: '30',
      flowUnit: 'GB',
      flowOvercharge: '0.29元/MB',
      voiceType: 'local',
      voiceAmount: '300',
      voiceOvercharge: '0.15元/分钟',
      smsAmount: '50',
      smsOvercharge: '0.1元/条',
      channelType: ['offline'],
      applicableArea: ['sc'],
      preLimitTable: campusPreLimit,
      rightsTable: rightsTable(['生日特权', '流量结转', '视频会员']),
      validity: 'longTerm',
      renewMethod: 'auto',
      orderTimes: '1',
    },
  },
]

/**
 * trafficOpsData - 流量运营统计分析数据层（mock）
 *
 * 对应「产品运营助手 · 流量运营分析」（ops.scene4）场景。
 *
 * 业务背景（联通国际批发→零售链路）：
 *   - 联通国际(平台) ──批发──> 代理商 ──零售──> 大市场客户(终端用户)
 *   - 平台与代理商按「流量用量」结算：代理商向平台买进流量（如 1000GB），
 *     实际卖出/用户使用的量即为用量，剩余越多，代理商分摊成本越低、利润越高。
 *   - 因此代理商倾向定制更高流量档套餐（如 1GB→2GB），提升剩余量、摊薄成本。
 *
 * 本数据层支持多视角统计：
 *   - 平台运营视角：整体批发流量池的用量、使用率、余量
 *   - 批发客户(代理商)视角：某代理商的采购/用量/成本/利润
 *   - 组织(代理商)维度、地区(地市)维度、套餐档位维度
 */

// ============ 平台运营视角：整体流量池总览 ============
export const platformOverview = {
  period: '2026年8月',
  totalBought: 12800, // 单位 GB，平台批发给全部代理商的总量
  totalUsed: 7346, // 已用 GB
  totalRemaining: 5454, // 剩余 GB
  overallUsageRate: 57.4, // 整体使用率 %
  boughtMoM: '+6.2%', // 采购量环比
  boughtYoY: '+4.8%', // 采购量同比
  usedMoM: '+8.9%',
  usedYoY: '+15.6%',
  remainingMoM: '-4.1%',
  remainingYoY: '-11.3%',
  usageRateMoM: '+1.4pt',
  usageRateYoY: '+4.2pt',
  activeAgents: 68, // 在售代理商数量
  activeRegions: 21, // 覆盖地市
  activeUsers: 86, // 活跃终端用户（万）
  summary:
    '平台批发流量整体使用率 57.4%，仍有 5454GB 余量；低使用率档位（≤40%）沉淀了较多未消化流量，是代理商量利结构改善的重点区间。',
}

// ============ 组织(代理商)维度：单个套餐的用量/使用率/余量 ============
export const agents = [
  {
    code: 'AG-001',
    name: '鑫联国际',
    region: '深圳',
    boughtGB: 3200,
    usedGB: 2216,
    remainingGB: 984,
    usageRate: 69.3,
    highUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)',
    highUsageRate: 96.8,
    lowUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
    lowUsageRate: 41.2,
    profitLevel: '良好',
    momPct: 8.2,
    status: '偏高',
  },
  {
    code: 'AG-002',
    name: '港通数码',
    region: '香港',
    boughtGB: 2600,
    usedGB: 1429,
    remainingGB: 1171,
    usageRate: 55.0,
    highUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)',
    highUsageRate: 92.1,
    lowUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
    lowUsageRate: 46.5,
    profitLevel: '中等',
    momPct: 4.6,
    status: '正常',
  },
  {
    code: 'AG-003',
    name: '华盛通讯',
    region: '广州',
    boughtGB: 2100,
    usedGB: 1198,
    remainingGB: 902,
    usageRate: 57.0,
    highUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)',
    highUsageRate: 94.3,
    lowUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
    lowUsageRate: 38.7,
    profitLevel: '中等',
    momPct: 6.1,
    status: '偏高',
  },
  {
    code: 'AG-004',
    name: '百川数据',
    region: '厦门',
    boughtGB: 1800,
    usedGB: 998,
    remainingGB: 802,
    usageRate: 55.4,
    highUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)',
    highUsageRate: 89.6,
    lowUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
    lowUsageRate: 44.1,
    profitLevel: '中等',
    momPct: 2.9,
    status: '正常',
  },
  {
    code: 'AG-005',
    name: '天翼环宇',
    region: '上海',
    boughtGB: 3100,
    usedGB: 1505,
    remainingGB: 1595,
    usageRate: 48.5,
    highUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)',
    highUsageRate: 97.2,
    lowUsagePackage: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
    lowUsageRate: 33.6,
    profitLevel: '偏低',
    momPct: -3.8,
    status: '正常',
  },
]

// ============ 漫游地区（套餐漫游地支撑，国家/地区级） ============
export const regions = [
  { name: '中国内地', agents: 3, boughtGB: 5200, usedGB: 3210, usageRate: 61.7, remainingGB: 1990, momPct: 8.6, status: '偏高', agentFlow: [ { code: 'AG-001', name: '鑫联国际', usedGB: 1600, usageRate: 62.5, momPct: 10.2 }, { code: 'AG-003', name: '华盛通讯', usedGB: 980, usageRate: 58.3, momPct: 5.1 }, { code: 'AG-005', name: '天翼环宇', usedGB: 630, usageRate: 46.7, momPct: 3.2 } ] },
  { name: '香港', agents: 3, boughtGB: 3400, usedGB: 2150, usageRate: 63.2, remainingGB: 1250, momPct: 6.2, status: '偏高', agentFlow: [ { code: 'AG-001', name: '鑫联国际', usedGB: 1200, usageRate: 64.9, momPct: 7.6 }, { code: 'AG-002', name: '港通数码', usedGB: 700, usageRate: 58.8, momPct: 4.4 }, { code: 'AG-004', name: '百川数据', usedGB: 250, usageRate: 45.5, momPct: 1.1 } ] },
  { name: '台湾', agents: 3, boughtGB: 2800, usedGB: 1720, usageRate: 61.4, remainingGB: 1080, momPct: 5.4, status: '正常', agentFlow: [ { code: 'AG-001', name: '鑫联国际', usedGB: 950, usageRate: 63.8, momPct: 6.3 }, { code: 'AG-002', name: '港通数码', usedGB: 520, usageRate: 56.5, momPct: 3.8 }, { code: 'AG-004', name: '百川数据', usedGB: 250, usageRate: 47.2, momPct: 1.5 } ] },
  { name: '澳门', agents: 2, boughtGB: 1500, usedGB: 860, usageRate: 57.3, remainingGB: 640, momPct: 4.1, status: '正常', agentFlow: [ { code: 'AG-001', name: '鑫联国际', usedGB: 520, usageRate: 61.9, momPct: 5.0 }, { code: 'AG-002', name: '港通数码', usedGB: 340, usageRate: 54.8, momPct: 2.6 } ] },
  { name: '新加坡', agents: 3, boughtGB: 2400, usedGB: 1230, usageRate: 51.2, remainingGB: 1170, momPct: 3.2, status: '正常', agentFlow: [ { code: 'AG-002', name: '港通数码', usedGB: 540, usageRate: 56.8, momPct: 3.9 }, { code: 'AG-004', name: '百川数据', usedGB: 410, usageRate: 50.6, momPct: 2.1 }, { code: 'AG-003', name: '华盛通讯', usedGB: 280, usageRate: 45.9, momPct: 1.8 } ] },
  { name: '马来西亚', agents: 2, boughtGB: 1800, usedGB: 860, usageRate: 47.8, remainingGB: 940, momPct: 2.4, status: '正常', agentFlow: [ { code: 'AG-003', name: '华盛通讯', usedGB: 430, usageRate: 51.8, momPct: 2.8 }, { code: 'AG-004', name: '百川数据', usedGB: 430, usageRate: 48.9, momPct: 1.9 } ] },
  { name: '泰国', agents: 2, boughtGB: 1900, usedGB: 890, usageRate: 46.8, remainingGB: 1010, momPct: 1.9, status: '正常', agentFlow: [ { code: 'AG-003', name: '华盛通讯', usedGB: 520, usageRate: 50.5, momPct: 2.4 }, { code: 'AG-001', name: '鑫联国际', usedGB: 370, usageRate: 45.7, momPct: 1.3 } ] },
  { name: '日本', agents: 2, boughtGB: 1600, usedGB: 700, usageRate: 43.8, remainingGB: 900, momPct: 1.4, status: '正常', agentFlow: [ { code: 'AG-004', name: '百川数据', usedGB: 380, usageRate: 47.5, momPct: 1.7 }, { code: 'AG-002', name: '港通数码', usedGB: 320, usageRate: 44.4, momPct: 1.0 } ] },
  { name: '韩国', agents: 2, boughtGB: 1200, usedGB: 500, usageRate: 41.7, remainingGB: 700, momPct: -0.8, status: '正常', agentFlow: [ { code: 'AG-004', name: '百川数据', usedGB: 300, usageRate: 45.5, momPct: 0.6 }, { code: 'AG-001', name: '鑫联国际', usedGB: 200, usageRate: 41.7, momPct: -2.1 } ] },
  { name: '英国', agents: 2, boughtGB: 1400, usedGB: 620, usageRate: 44.3, remainingGB: 780, momPct: 2.2, status: '正常', agentFlow: [ { code: 'AG-005', name: '天翼环宇', usedGB: 400, usageRate: 48.8, momPct: 2.9 }, { code: 'AG-002', name: '港通数码', usedGB: 220, usageRate: 40.7, momPct: 1.2 } ] },
  { name: '澳大利亚', agents: 2, boughtGB: 1100, usedGB: 460, usageRate: 41.8, remainingGB: 640, momPct: 1.1, status: '偏低', agentFlow: [ { code: 'AG-005', name: '天翼环宇', usedGB: 300, usageRate: 45.5, momPct: 1.5 }, { code: 'AG-004', name: '百川数据', usedGB: 160, usageRate: 38.1, momPct: 0.4 } ] },
  { name: '南非', agents: 1, boughtGB: 800, usedGB: 300, usageRate: 37.5, remainingGB: 500, momPct: -1.6, status: '偏低', agentFlow: [ { code: 'AG-005', name: '天翼环宇', usedGB: 300, usageRate: 37.5, momPct: -1.6 } ] },
]

// ============ 套餐档位维度：使用率 vs 成本/利润结构 ============
export const packages = [
  {
    name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)',
    capacityGB: 1,
    boughtGB: 1860,
    usedGB: 1781,
    usageRate: 95.8,
    remainingGB: 79,
    unitCost: 1.8, // 元/GB（代理商向平台采购价）
    costPerCard: 1.8, // 单卡成本
    orderCount: 4280, // 订购量（套餐订购笔数）
    userCount: 3860, // 去重个人用户数
    remark: '使用率极高，余量极少 → 代理商成本高、利润空间被压缩',
    suggestion: '建议定制 2GB 档：摊薄单卡成本、提升剩余量与毛利',
  },
  {
    name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)',
    capacityGB: 2,
    boughtGB: 1320,
    usedGB: 1150,
    usageRate: 87.1,
    remainingGB: 170,
    unitCost: 1.6,
    costPerCard: 3.2,
    orderCount: 3160,
    userCount: 2890,
    remark: '高使用率，剩余量有限',
    suggestion: '已进入高档定制替换 1GB 的阶段，适合重点推广',
  },
  {
    name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)',
    capacityGB: 3,
    boughtGB: 980,
    usedGB: 812,
    usageRate: 82.9,
    remainingGB: 168,
    unitCost: 1.5,
    costPerCard: 4.5,
    orderCount: 2450,
    userCount: 2230,
    remark: '中高使用率，结构健康',
    suggestion: '保持档位供给，稳步放量',
  },
  {
    name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
    capacityGB: 20,
    boughtGB: 2350,
    usedGB: 1088,
    usageRate: 46.3,
    remainingGB: 1262,
    unitCost: 1.1,
    costPerCard: 22,
    orderCount: 1690,
    userCount: 1540,
    remark: '中等使用率，剩余量充足',
    suggestion: '代理商利润较好，可加大铺货',
  },
  {
    name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
    capacityGB: 30,
    boughtGB: 4370,
    usedGB: 1720,
    usageRate: 39.4,
    remainingGB: 2650,
    unitCost: 0.9,
    costPerCard: 27,
    orderCount: 1380,
    userCount: 1310,
    remark: '使用率偏低，沉淀大量余量 → 代理商利润结构优',
    suggestion: '剩余量充裕、成本摊薄，是代理商利润主要来源',
  },
]

// ============ 流量使用趋势（近 6 个月，2月~7月） ============
export const trafficTrend = [
  { month: '2026-02', label: '2月', usedGB: 4780, boughtGB: 12000, usageRate: 39.8, momPct: 0 },
  { month: '2026-03', label: '3月', usedGB: 5120, boughtGB: 12150, usageRate: 42.1, momPct: 7.1 },
  { month: '2026-04', label: '4月', usedGB: 5510, boughtGB: 12300, usageRate: 44.8, momPct: 7.6 },
  { month: '2026-05', label: '5月', usedGB: 6020, boughtGB: 12500, usageRate: 48.2, momPct: 9.3 },
  { month: '2026-06', label: '6月', usedGB: 6680, boughtGB: 12680, usageRate: 52.7, momPct: 11.0 },
  { month: '2026-07', label: '7月', usedGB: 7346, boughtGB: 12800, usageRate: 57.4, momPct: 10.0 },
]

// ============ 流量运营趋势（近 12 个月 + 近 30 天） ============
export const trafficMonthly = [
  { month: '2025-08', label: '8月', usedGB: 2860, boughtGB: 10300, usageRate: 27.8, remainingGB: 7440, momPct: 0 },
  { month: '2025-09', label: '9月', usedGB: 3120, boughtGB: 10600, usageRate: 29.4, remainingGB: 7480, momPct: 0 },
  { month: '2025-10', label: '10月', usedGB: 3400, boughtGB: 11000, usageRate: 30.9, remainingGB: 7600, momPct: 0 },
  { month: '2025-11', label: '11月', usedGB: 3710, boughtGB: 11380, usageRate: 32.6, remainingGB: 7670, momPct: 0 },
  { month: '2025-12', label: '12月', usedGB: 4080, boughtGB: 11830, usageRate: 34.5, remainingGB: 7750, momPct: 0 },
  { month: '2026-01', label: '1月', usedGB: 4460, boughtGB: 12120, usageRate: 36.8, remainingGB: 7660, momPct: 0 },
  { month: '2026-02', label: '2月', usedGB: 4780, boughtGB: 12000, usageRate: 39.8, remainingGB: 7220, momPct: 0 },
  { month: '2026-03', label: '3月', usedGB: 5120, boughtGB: 12150, usageRate: 42.1, remainingGB: 7030, momPct: 7.1 },
  { month: '2026-04', label: '4月', usedGB: 5510, boughtGB: 12300, usageRate: 44.8, remainingGB: 6790, momPct: 7.6 },
  { month: '2026-05', label: '5月', usedGB: 6020, boughtGB: 12500, usageRate: 48.2, remainingGB: 6480, momPct: 9.3 },
  { month: '2026-06', label: '6月', usedGB: 6680, boughtGB: 12680, usageRate: 52.7, remainingGB: 6000, momPct: 11.0 },
  { month: '2026-07', label: '7月', usedGB: 7346, boughtGB: 12800, usageRate: 57.4, remainingGB: 5454, momPct: 10.0 },
]

export const trafficDaily = [
  { day: '06-29', label: '06-29', usedGB: 206, boughtGB: 300, usageRate: 68.7, remainingGB: 94, momPct: 0 },
  { day: '06-30', label: '06-30', usedGB: 214, boughtGB: 300, usageRate: 71.3, remainingGB: 86, momPct: 3.9 },
  { day: '07-01', label: '07-01', usedGB: 208, boughtGB: 300, usageRate: 69.3, remainingGB: 92, momPct: -2.8 },
  { day: '07-02', label: '07-02', usedGB: 222, boughtGB: 300, usageRate: 74.0, remainingGB: 78, momPct: 6.7 },
  { day: '07-03', label: '07-03', usedGB: 231, boughtGB: 300, usageRate: 77.0, remainingGB: 69, momPct: 4.1 },
  { day: '07-04', label: '07-04', usedGB: 219, boughtGB: 300, usageRate: 73.0, remainingGB: 81, momPct: -5.2 },
  { day: '07-05', label: '07-05', usedGB: 226, boughtGB: 300, usageRate: 75.3, remainingGB: 74, momPct: 3.2 },
  { day: '07-06', label: '07-06', usedGB: 238, boughtGB: 300, usageRate: 79.3, remainingGB: 62, momPct: 5.3 },
  { day: '07-07', label: '07-07', usedGB: 244, boughtGB: 300, usageRate: 81.3, remainingGB: 56, momPct: 2.5 },
  { day: '07-08', label: '07-08', usedGB: 230, boughtGB: 300, usageRate: 76.7, remainingGB: 70, momPct: -5.7 },
  { day: '07-09', label: '07-09', usedGB: 236, boughtGB: 300, usageRate: 78.7, remainingGB: 64, momPct: 2.6 },
  { day: '07-10', label: '07-10', usedGB: 251, boughtGB: 300, usageRate: 83.7, remainingGB: 49, momPct: 6.4 },
  { day: '07-11', label: '07-11', usedGB: 247, boughtGB: 300, usageRate: 82.3, remainingGB: 53, momPct: -1.6 },
  { day: '07-12', label: '07-12', usedGB: 240, boughtGB: 300, usageRate: 80.0, remainingGB: 60, momPct: -2.8 },
  { day: '07-13', label: '07-13', usedGB: 258, boughtGB: 300, usageRate: 86.0, remainingGB: 42, momPct: 7.5 },
  { day: '07-14', label: '07-14', usedGB: 266, boughtGB: 300, usageRate: 88.7, remainingGB: 34, momPct: 3.1 },
  { day: '07-15', label: '07-15', usedGB: 254, boughtGB: 300, usageRate: 84.7, remainingGB: 46, momPct: -4.5 },
  { day: '07-16', label: '07-16', usedGB: 261, boughtGB: 300, usageRate: 87.0, remainingGB: 39, momPct: 2.8 },
  { day: '07-17', label: '07-17', usedGB: 273, boughtGB: 300, usageRate: 91.0, remainingGB: 27, momPct: 4.6 },
  { day: '07-18', label: '07-18', usedGB: 268, boughtGB: 300, usageRate: 89.3, remainingGB: 32, momPct: -1.8 },
  { day: '07-19', label: '07-19', usedGB: 259, boughtGB: 300, usageRate: 86.3, remainingGB: 41, momPct: -3.4 },
  { day: '07-20', label: '07-20', usedGB: 272, boughtGB: 300, usageRate: 90.7, remainingGB: 28, momPct: 5.0 },
  { day: '07-21', label: '07-21', usedGB: 281, boughtGB: 300, usageRate: 93.7, remainingGB: 19, momPct: 3.3 },
  { day: '07-22', label: '07-22', usedGB: 276, boughtGB: 300, usageRate: 92.0, remainingGB: 24, momPct: -1.8 },
  { day: '07-23', label: '07-23', usedGB: 269, boughtGB: 300, usageRate: 89.7, remainingGB: 31, momPct: -2.5 },
  { day: '07-24', label: '07-24', usedGB: 285, boughtGB: 300, usageRate: 95.0, remainingGB: 15, momPct: 5.9 },
  { day: '07-25', label: '07-25', usedGB: 292, boughtGB: 300, usageRate: 97.3, remainingGB: 8, momPct: 2.5 },
  { day: '07-26', label: '07-26', usedGB: 284, boughtGB: 300, usageRate: 94.7, remainingGB: 16, momPct: -2.7 },
  { day: '07-27', label: '07-27', usedGB: 290, boughtGB: 300, usageRate: 96.7, remainingGB: 10, momPct: 2.1 },
  { day: '07-28', label: '07-28', usedGB: 300, boughtGB: 300, usageRate: 100.0, remainingGB: 0, momPct: 3.4 },
]

// ============ 联通国际视角：重点运营机会（流量异常 / 使用率异常 / 流量增长 / 资源利用） ============
export const platformFocus = [
  {
    key: 'flow', label: '流量异常',
    items: [
      { type: 'agent', name: '鑫联国际', region: '深圳', detail: '本月流量使用量 2216GB，较前周期大幅增长，需关注流量去向与承接能力', level: 'high', ask: '鑫联国际本月流量用量异常增长，请分析原因' },
      { type: 'region', name: '香港', detail: '漫游地使用量 2150GB、使用率 63.2%，环比 +6.2%，流量消化加快', level: 'mid', ask: '香港漫游地流量用量与使用率双升，请分析主要来源' },
    ],
  },
  {
    key: 'rate', label: '使用率异常',
    items: [
      { type: 'agent', name: '天翼环宇', region: '上海', detail: '整体使用率 48.5%，低于平台均值且环比下降，资源利用率偏低', level: 'mid', ask: '天翼环宇整体使用率偏低，分析原因并给出提升建议' },
      { type: 'region', name: '南非', detail: '漫游地使用率 37.5%，环比下降，低档套餐铺货后消化偏慢', level: 'low', ask: '南非漫游地使用率回落，分析是否涉及客户流失或套餐调整' },
    ],
  },
  {
    key: 'growth', label: '流量增长',
    items: [
      { type: 'region', name: '中国内地', detail: '连续多月保持增长，为平台主要增量来源', level: 'high', ask: '中国内地漫游地连续增长，分析增长驱动力与可持续性' },
      { type: 'agent', name: '鑫联国际', detail: '本月用量 2216GB，增长率居平台前列', level: 'mid', ask: '鑫联国际增长最快，分析其高增长套餐与规模承接能力' },
    ],
  },
  {
    key: 'resource', label: '资源利用',
    items: [
      { type: 'agent', name: '天翼环宇', detail: '剩余流量较高，资源利用率偏低，可加大高余量档铺货', level: 'low', ask: '天翼环宇剩余流量较高，分析铺货与资源利用机会' },
      { type: 'region', name: '澳大利亚', detail: '漫游地使用率 41.8%、余量充足，资源未充分变现', level: 'low', ask: '澳大利亚漫游地使用率偏低，评估资源利用与促销机会' },
    ],
  },
]

// ============ 代理商运营视角：客户 / 套餐结构 ============
// 该视角回答「我的客户和套餐经营得怎么样？」：客户 → 套餐 → 用户使用
export const agentCustomers = [
  {
    code: 'AG-001', name: '鑫联国际', region: '深圳',
    customerCount: 126, activeCustomer: 114, userCount: 42300,
    packageCount: 6, highLoadPkg: 2, optimizePkg: 3,
    topCustomers: [
      { name: '星河商旅', users: 8200, usedGB: 612, usageRate: 92, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '深港跨境', users: 7150, usedGB: 540, usageRate: 88, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '航旅通讯', users: 6300, usedGB: 468, usageRate: 74, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
      { name: '跨境云旅', users: 5480, usedGB: 392, usageRate: 71, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
      { name: '环球出行', users: 4720, usedGB: 288, usageRate: 61, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
    ],
  },
  {
    code: 'AG-002', name: '港通数码', region: '香港',
    customerCount: 98, activeCustomer: 90, userCount: 31800,
    packageCount: 5, highLoadPkg: 1, optimizePkg: 2,
    topCustomers: [
      { name: '香港自由行', users: 6800, usedGB: 501, usageRate: 90, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '口岸专线', users: 5900, usedGB: 420, usageRate: 82, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '港澳商旅', users: 4700, usedGB: 331, usageRate: 78, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
      { name: '湾区出行', users: 4100, usedGB: 270, usageRate: 66, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
      { name: '环球漫游', users: 3600, usedGB: 215, usageRate: 60, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
    ],
  },
  {
    code: 'AG-003', name: '华盛通讯', region: '广州',
    customerCount: 87, activeCustomer: 80, userCount: 28600,
    packageCount: 5, highLoadPkg: 1, optimizePkg: 2,
    topCustomers: [
      { name: '穗深跨境', users: 5600, usedGB: 420, usageRate: 91, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '穗港澳航', users: 4900, usedGB: 352, usageRate: 86, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '华南商旅', users: 4200, usedGB: 290, usageRate: 69, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
      { name: '南沙自贸', users: 3800, usedGB: 244, usageRate: 64, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
      { name: '珠江口岸', users: 3200, usedGB: 190, usageRate: 59, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
    ],
  },
  {
    code: 'AG-004', name: '百川数据', region: '厦门',
    customerCount: 72, activeCustomer: 65, userCount: 21900,
    packageCount: 4, highLoadPkg: 1, optimizePkg: 2,
    topCustomers: [
      { name: '闽南跨境', users: 4600, usedGB: 340, usageRate: 89, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '厦金航线', users: 4000, usedGB: 288, usageRate: 84, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
      { name: '海峡商旅', users: 3300, usedGB: 226, usageRate: 68, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '闽台出行', users: 2900, usedGB: 183, usageRate: 63, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
      { name: '东海岸', users: 2400, usedGB: 136, usageRate: 57, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
    ],
  },
  {
    code: 'AG-005', name: '天翼环宇', region: '上海',
    customerCount: 81, activeCustomer: 73, userCount: 24700,
    packageCount: 5, highLoadPkg: 1, optimizePkg: 2,
    topCustomers: [
      { name: '沪港直航', users: 5200, usedGB: 388, usageRate: 90, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '长三角商旅', users: 4500, usedGB: 315, usageRate: 82, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)' },
      { name: '虹桥枢纽', users: 3900, usedGB: 259, usageRate: 70, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
      { name: '浦东漫游', users: 3400, usedGB: 208, usageRate: 61, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)' },
      { name: '环沪出行', users: 2900, usedGB: 160, usageRate: 55, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)' },
    ],
  },
]

// ============ 代理商运营视角：单一代理商视角总览 ============
// 对应「代理商运营视角」下的客户/套餐/用户经营（以 AG-001 鑫联国际为单一代理身份）
export const agentViewOverview = {
  period: '2026年8月',
  code: 'AG-001',
  name: '鑫联国际',
  region: '深圳',
  totalCustomers: 126,
  activeCustomers: 114,
  totalUsers: 42300,
  packageCount: 5,
  highLoadPkg: 2,
  optimizePkg: 3,
  totalBought: 3200,
  totalUsed: 2216,
  totalRemaining: 984,
  overallUsageRate: 69.3,
  boughtMoM: '+5.1%',
  boughtYoY: '+3.9%',
  usedMoM: '+8.2%',
  usedYoY: '+14.4%',
  remainingMoM: '-4.6%',
  remainingYoY: '-12.8%',
  overallUsageRateMoM: '+1.7pt',
  overallUsageRateYoY: '+4.9pt',
  summary:
    '我本月采购流量 3200GB、已使用 2216GB，整体使用率 69.3%。其中 1GB/2GB 日租卡使用率高达 96.8%/87.1%，容量压力明显；建议把高使用个人用户迁移到 2GB/3GB 档，释放利润空间、降低单位流量成本。',
}

// ============ 代理商视角：单一代理商的客户套餐消费 ============
// 以 AG-001 鑫联国际为单一代理身份的「我的流量经营」
export const agentViewDetail = {
  code: 'AG-001',
  name: '鑫联国际',
  region: '深圳',
  customerCount: 126,
  activeCustomer: 114,
  userCount: 42300,
  boughtGB: 3200,
  usedGB: 2216,
  remainingGB: 984,
  usageRate: 69.3,
  // 大市场客户运营：流量使用量 / 使用率 / 剩余量 / 趋势 / 主要套餐
  customers: [
    { name: '星河商旅', usedGB: 612, usageRate: 92, remainingGB: 52, trend: 'up', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', users: 10120 },
    { name: '深港跨境', usedGB: 540, usageRate: 88, remainingGB: 74, trend: 'up', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', users: 8800 },
    { name: '航旅通讯', usedGB: 468, usageRate: 74, remainingGB: 164, trend: 'flat', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', users: 7200 },
    { name: '跨境云旅', usedGB: 392, usageRate: 71, remainingGB: 160, trend: 'flat', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', users: 5600 },
    { name: '环球出行', usedGB: 288, usageRate: 61, remainingGB: 184, trend: 'down', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', users: 4300 },
    { name: '粤港直通', usedGB: 205, usageRate: 52, remainingGB: 190, trend: 'down', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', users: 3100 },
  ],
  // 个人用户运营：代理商视角的客户为个人用户（用户即客户主体）。这里为代表性个人用户样本
  activeUserCount: 38650,
  personalUsers: [
    { name: '陈**', tel: '655***21', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', usedGB: 0.98, usageRate: 98, remainingGB: 0, trend: 'up', status: '高消耗' },
    { name: '林**', tel: '655***63', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', usedGB: 0.92, usageRate: 92, remainingGB: 0, trend: 'up', status: '高消耗' },
    { name: '王**', tel: '655***57', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', usedGB: 1.76, usageRate: 88, remainingGB: 0, trend: 'flat', status: '偏高' },
    { name: '李**', tel: '655***88', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', usedGB: 0.85, usageRate: 85, remainingGB: 0, trend: 'up', status: '偏高' },
    { name: '张**', tel: '655***34', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', usedGB: 1.62, usageRate: 81, remainingGB: 0, trend: 'flat', status: '偏高' },
    { name: '刘**', tel: '655***72', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', usedGB: 2.41, usageRate: 80, remainingGB: 0, trend: 'flat', status: '偏高' },
    { name: '赵**', tel: '655***09', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', usedGB: 2.02, usageRate: 67, remainingGB: 0, trend: 'flat', status: '正常' },
    { name: '孙**', tel: '655***46', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', usedGB: 1.28, usageRate: 64, remainingGB: 0, trend: 'down', status: '正常' },
    { name: '周**', tel: '655***18', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', usedGB: 1.66, usageRate: 55, remainingGB: 1, trend: 'down', status: '正常' },
    { name: '吴**', tel: '655***05', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', usedGB: 8.42, usageRate: 42, remainingGB: 11, trend: 'down', status: '低消耗' },
    { name: '郑**', tel: '655***91', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', usedGB: 9.18, usageRate: 31, remainingGB: 20, trend: 'down', status: '低消耗' },
    { name: '黄**', tel: '655***27', pkg: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', usedGB: 6.75, usageRate: 23, remainingGB: 23, trend: 'down', status: '低消耗' },
  ],
  // 子渠道运营：总代（一级）下的二级/三级代理（旅行社 / 公司 / 营业厅 等组织型渠道）
  subChannels: [
    { name: '航旅天下旅行社', level: 2, type: '旅行社', boughtGB: 620, usedGB: 572, usageRate: 92, remainingGB: 48, trend: 'up', status: '高负载' },
    { name: '跨境云旅科技', level: 2, type: '公司', boughtGB: 540, usedGB: 481, usageRate: 89, remainingGB: 59, trend: 'up', status: '偏高' },
    { name: '深港口岸营业厅', level: 3, type: '营业厅', boughtGB: 480, usedGB: 401, usageRate: 84, remainingGB: 79, trend: 'flat', status: '偏高' },
    { name: '环球商旅服务', level: 2, type: '旅行社', boughtGB: 380, usedGB: 296, usageRate: 78, remainingGB: 84, trend: 'flat', status: '正常' },
    { name: '宝安机场营业厅', level: 3, type: '营业厅', boughtGB: 360, usedGB: 265, usageRate: 74, remainingGB: 95, trend: 'flat', status: '正常' },
    { name: '粤港直通科技', level: 2, type: '公司', boughtGB: 300, usedGB: 195, usageRate: 65, remainingGB: 105, trend: 'down', status: '正常' },
    { name: '福田高铁营业厅', level: 3, type: '营业厅', boughtGB: 260, usedGB: 143, usageRate: 55, remainingGB: 117, trend: 'down', status: '低消耗' },
    { name: '山海跨境贸易', level: 3, type: '公司', boughtGB: 240, usedGB: 108, usageRate: 45, remainingGB: 132, trend: 'down', status: '低消耗' },
  ],
  // 套餐运营：客户数 / 使用量 / 使用率 / 剩余量 / 趋势 / 状态
  byPackage: [
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', customers: 4280, usedGB: 903, usageRate: 96.8, remainingGB: 30, trend: 'up', status: '高负载', statusLevel: 'high' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', customers: 3160, usedGB: 542, usageRate: 87.1, remainingGB: 80, trend: 'flat', status: '偏高', statusLevel: 'mid' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', customers: 2450, usedGB: 392, usageRate: 82.9, remainingGB: 81, trend: 'flat', status: '偏高', statusLevel: 'mid' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', customers: 1690, usedGB: 148, usageRate: 46.3, remainingGB: 172, trend: 'down', status: '调和', statusLevel: 'ok' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', customers: 1380, usedGB: 231, usageRate: 39.4, remainingGB: 399, trend: 'down', status: '利润池', statusLevel: 'ok' },
  ],
  insight:
    '鑫联国际整体使用率 69.3%，其中 1GB/2GB 日租卡使用率高达 96.8%/87.1%，容量压力明显、单卡成本高；建议将高使用用户迁移至 2GB/3GB 档，释放利润空间、降低单位流量成本。',
  // 套餐优化机会
  optimize: {
    highUsage: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', usageRate: 96.8, remainingGB: 30, customers: 4280, reason: '余量仅 30GB、单卡成本高，1GB 档高消耗个人用户占用量大，建议定向升档 2GB 摊薄成本' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', usageRate: 87.1, remainingGB: 80, customers: 3160, reason: '使用率偏高、余量有限，作为 1GB 升档承接档，需保持足量供给' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', usageRate: 82.9, remainingGB: 81, customers: 2450, reason: '中高使用率、余量偏紧，结构健康，重点保持供给稳定' },
    ],
    highRemaining: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', usageRate: 39.4, remainingGB: 399, customers: 1380, reason: '余量沉淀最大但单 GB 成本最低、毛利率 51.3%，是高利润来源，建议承接高使用个人用户升档' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', usageRate: 46.3, remainingGB: 172, customers: 1690, reason: '余量充足、成本摊薄佳，建议加大铺货以消化剩余量' },
    ],
    structure: '当前套餐结构中，1GB 使用率持续升高、30GB 长期低利用；建议重新评估 1GB/2GB/30GB 的容量结构，把 1GB 高使用个人用户向 2GB 迁移。',
    highRemainingReason: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速) 与 WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速) 余量沉淀较大、使用率偏低，说明大流量规格当前供给超过实际需求；建议向高使用个人用户定向推广大规格，避免长期占款。',
  },
  // 套餐详情下钻：点击套餐运营表中的某款套餐，进入单套餐的详情（基础信息 / 使用情况 / 客户结构 / AI 分析）
  pkgDetail: {
    'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)': {
      name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', code: 'TS-01', capacityGB: 1,
      boughtGB: 1860, usedGB: 1781, usageRate: 95.8, remainingGB: 79,
      unitCost: 1.8, costPerCard: 1.8, customerCount: 4280, userCount: 3860,
      customers: [
        { name: '星河商旅', users: 1520, usedGB: 612, usageRate: 95, share: '占比近半的高消耗客户' },
        { name: '深港跨境', users: 1230, usedGB: 540, usageRate: 93, share: '高频跨境出行，需求旺盛' },
        { name: '粤港直通', users: 1110, usedGB: 186, usageRate: 88, share: '客流集中，日卡短期消耗为主' },
      ],
      insight: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)使用率高达 96.8%、余量仅 30GB，单卡成本 1.8 元/GB，容量压力大、利润被压缩；建议对 1GB 日租卡高消耗个人用户定向迁移至 WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)，摊薄单卡成本、释放利润空间。',
      suggestion: '定制 2GB 档替换 1GB：摊薄单卡成本、提升剩余量与毛利。',
    },
    'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)': {
      name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', code: 'TS-02', capacityGB: 2,
      boughtGB: 1320, usedGB: 1150, usageRate: 87.1, remainingGB: 170,
      unitCost: 1.6, costPerCard: 3.2, customerCount: 3160, userCount: 2890,
      customers: [
        { name: '跨境云旅', users: 1180, usedGB: 392, usageRate: 89, share: '中高消耗，适合承接 1GB 升档' },
        { name: '粤港直通', users: 910, usedGB: 205, usageRate: 78, share: '日用为主，结构均衡' },
        { name: '深港跨境', users: 800, usedGB: 132, usageRate: 72, share: '次高消耗，保持档位' },
      ],
      insight: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)使用率 87.1%，是承接 1GB 升档的主力档位；可重点推广以摊薄单卡成本、改善 1GB 高使用个人用户的利润结构。',
      suggestion: '已进入高档定制替换 1GB 的阶段，适合重点推广。',
    },
    'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)': {
      name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', code: 'TS-03', capacityGB: 3,
      boughtGB: 980, usedGB: 812, usageRate: 82.9, remainingGB: 168,
      unitCost: 1.5, costPerCard: 4.5, customerCount: 2450, userCount: 2230,
      customers: [
        { name: '航旅通讯', users: 1270, usedGB: 468, usageRate: 82, share: '中高使用，结构健康' },
        { name: '环球出行', users: 960, usedGB: 288, usageRate: 70, share: '周卡周期匹配度较高' },
      ],
      insight: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)使用率 82.9%，中高区间、结构健康；保持档位供给、稳步放量即可，无需大幅调整。',
      suggestion: '保持档位供给，稳步放量。',
    },
    'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)': {
      name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', code: 'TS-04', capacityGB: 20,
      boughtGB: 2350, usedGB: 1088, usageRate: 46.3, remainingGB: 172,
      unitCost: 1.1, costPerCard: 22, customerCount: 1690, userCount: 1540,
      customers: [
        { name: '航旅通讯', users: 820, usedGB: 96, usageRate: 48, share: '长线需求，匹配 15 日周期' },
        { name: '跨境云旅', users: 720, usedGB: 52, usageRate: 44, share: '中量使用，可加大推广' },
      ],
      insight: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速) 使用率 46.3%、余量充足，成本摊薄佳（单卡 22 元）；代理商利润较好，可加大铺货以消化剩余量。',
      suggestion: '代理商利润较好，可加大铺货。',
    },
    'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)': {
      name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', code: 'TS-05', capacityGB: 30,
      boughtGB: 4370, usedGB: 1720, usageRate: 39.4, remainingGB: 399,
      unitCost: 0.9, costPerCard: 27, customerCount: 1380, userCount: 1310,
      customers: [
        { name: '跨境云旅', users: 720, usedGB: 106, usageRate: 42, share: '长线大流量，承接升档高潜' },
        { name: '环球出行', users: 590, usedGB: 125, usageRate: 38, share: '存量余量用户' },
      ],
      insight: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速) 使用率 39.4%、余量沉淀最大，但单 GB 成本最低（0.9 元）、毛利率高（51.3%），是利润主要来源；建议把高消耗个人用户向该档迁移，释放利润空间。',
      suggestion: '剩余量充裕、成本摊薄，是代理商利润主要来源。',
    },
  },
}

// ============ 代理商视角：某代理商的量利结构下钻 ============
export const agentProfitDetail = {
  code: 'AG-001',
  name: '鑫联国际',
  region: '深圳',
  boughtGB: 3200,
  usedGB: 2216,
  remainingGB: 984,
  usageRate: 69.3,
  buyCost: 8, // 元/GB 均价（代理商向平台采购）
  revenue: 423, // 万元（销售收入）
  cost: 256, // 万元（采购成本）
  profit: 167, // 万元（毛利）
  margin: 39.5, // 毛利率 %
  byPackage: [
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', boughtGB: 520, usedGB: 503, usageRate: 96.8, remainingGB: 17, profit: 18.2, marginOfPackage: 41.2, price: 2.6, unitCost: 1.8, gain: '高使用率压价 · 毛利承压' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', boughtGB: 460, usedGB: 392, usageRate: 85.2, remainingGB: 68, profit: 31.4, marginOfPackage: 43.6, price: 2.4, unitCost: 1.5, gain: '结构性价利较优 · 稳步放量' },
    { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', boughtGB: 1460, usedGB: 601, usageRate: 41.2, remainingGB: 859, profit: 89.6, marginOfPackage: 51.3, price: 1.8, unitCost: 0.9, gain: '低单位成本 · 利润池' },
  ],
  insight:
    '鑫联国际整体使用率 69.3% 处中高区间，但 WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)使用率 96.8%、余量仅 17GB，单卡成本高、利润被压缩；30日不限量档使用率 41.2%、余量 859GB，毛利率高达 51.3%，是主要利润来源。建议将 1GB 订单往 2GB 档迁移，提升整体毛利。',
}

// ============ 平台运营视角：异动归因数据层（ops.scene2 · 平台） ============
// 口径：平台批发流量池（12800GB / 已用 7346GB / 使用率 57.4%），数据与 platformOverview、packages、agents、regions 保持一致。
// 覆盖四类典型异动：套餐新增异动、收入下滑、地区退订率上升、代理商使用率异常。
export const platformAnomaly = {
  period: '2026年8月',
  // ① 套餐新增异动：哪个套餐新增掉得最多，由哪个地区/代理商贡献
  newOrder: {
    metric: '新增订购',
    current: 21000, // 本月新增订购（笔）
    previous: 28400, // 上月（笔）
    changePct: -26.1,
    turnPoint: '8月12日',
    items: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', code: 'TS-05', current: 1380, previous: 2860, changePct: -51.7, contribution: 54, reason: '30GB 高速档落后竞品（竞品已升至 50GB），长线出境客群转化下滑，代理商铺货意愿降低' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', code: 'TS-04', current: 1690, previous: 2450, changePct: -31.0, contribution: 26, reason: '15日中期档被竞品 20日档分流，香港.台湾主力客群出现替代' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', code: 'TS-02', current: 3160, previous: 3520, changePct: -10.2, contribution: 13, reason: '短途日卡基本盘稳定，小幅回落属正常波动与季节性因素' },
    ],
    byRegion: [
      { name: '香港', changePct: -33.5, contribution: 41 },
      { name: '台湾', changePct: -28.2, contribution: 27 },
      { name: '澳门', changePct: -21.6, contribution: 18 },
      { name: '新加坡', changePct: -12.4, contribution: 14 },
    ],
    byAgent: [
      { code: 'AG-001', name: '鑫联国际', region: '深圳', changePct: -29.8, contribution: 38 },
      { code: 'AG-002', name: '港通数码', region: '香港', changePct: -24.5, contribution: 25 },
      { code: 'AG-004', name: '百川数据', region: '厦门', changePct: -19.1, contribution: 16 },
      { code: 'AG-005', name: '天翼环宇', region: '上海', changePct: -14.7, contribution: 12 },
    ],
    rootCause: [
      { cause: '30GB 高速档落后竞品（竞品升至 50GB）', contribution: 46 },
      { cause: '15日中期档被竞品 20日档分流', contribution: 24 },
      { cause: '高价值渠道（港澳台专线）铺货收缩', contribution: 18 },
      { cause: '季节性淡季与竞品降价叠加', contribution: 12 },
    ],
    conclusion: '属**竞争性新增萎缩**：长线（30日/15日）高速档配置被竞品反超，香港.台湾地区、鑫联国际/港通数码两家代理商贡献了主要降幅；日卡基本盘（1日/3日）稳定，存量未流失。建议优先对 30GB/15GB 档做高速配额升级试点。',
  },
  // ② 收入下滑：平台整体收入下滑主要由哪些套餐/代理商造成
  revenue: {
    metric: '批发结算收入',
    current: 24280, // 本月（万元）
    previous: 26450, // 上月（万元）
    changePct: -8.2,
    items: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', current: 3726, previous: 6435, changePct: -42.1, contribution: 58, note: '低使用率档、毛利高，新增萎缩对收入拖累最直接' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', current: 3042, previous: 4212, changePct: -27.8, contribution: 25, note: '中期档被分流，单价与订购量双降' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', current: 3160, previous: 3344, changePct: -5.5, contribution: 9, note: '日卡基本盘，小幅回落' },
      { name: '其他档位', current: 14352, previous: 12459, changePct: 15.2, contribution: -22, note: '1日卡与 2GB 升档承接部分需求，形成对冲' },
    ],
    byAgent: [
      { code: 'AG-001', name: '鑫联国际', region: '深圳', current: 5130, previous: 6120, changePct: -16.2, contribution: 42 },
      { code: 'AG-002', name: '港通数码', region: '香港', current: 3980, previous: 4520, changePct: -11.9, contribution: 24 },
      { code: 'AG-005', name: '天翼环宇', region: '上海', current: 2810, previous: 3060, changePct: -8.2, contribution: 11 },
    ],
    conclusion: '收入下滑 **8.2%**，主要拖累来自 **30日不限量(30GB)**（贡献 58%）与 **15日不限量(15GB)**（25%）；代理商层面 **鑫联国际**（-16.2%）与 **港通数码**（-11.9%）合计贡献 66%。值得注意的是 1日卡与 2GB 升档收入同比 +15.2%，说明需求未消失而是向短途/低档迁移，属**结构性收入下滑**而非大盘失速。',
  },
  // ③ 地区退订率上升：哪个地区退订率上升最明显，集中在哪些套餐档位
  churn: {
    metric: '退订率',
    platformAvg: 3.6,
    items: [
      { name: '香港', currentRate: 6.8, previousRate: 4.1, changePt: 2.7, level: 'high', pkgFocus: '30日不限量(30GB)、15日不限量(15GB)', reason: '竞品 50GB 高速档上线后，长线客群转投竞品' },
      { name: '台湾', currentRate: 5.9, previousRate: 3.8, changePt: 2.1, level: 'high', pkgFocus: '15日不限量(15GB)', reason: '中期档被竞品 20日档分流，到期不续订增多' },
      { name: '澳门', currentRate: 4.2, previousRate: 3.4, changePt: 0.8, level: 'mid', pkgFocus: '3日不限量(800MB/日)', reason: '短途日卡正常波动' },
      { name: '新加坡', currentRate: 3.9, previousRate: 3.5, changePt: 0.4, level: 'low', pkgFocus: '1日不限量(800MB/日)', reason: '基本稳定' },
    ],
    conclusion: '退订率上升集中在 **香港（6.8%，+2.7pt）** 与 **台湾（5.9%，+2.1pt）**，均高于平台均值 3.6%，且都集中在 **30日/15日不限量** 长中期档——与新增萎缩的套餐、地区高度重合，指向同一根因：**该两地的长线高速档竞争力不足导致到期不续订**。日卡档位退订正常，无需干预。',
  },
  // ④ 使用率异常：使用率明显异常的代理商及量利结构问题
  usageRate: {
    metric: '整体使用率',
    platformAvg: 57.4,
    items: [
      { code: 'AG-001', name: '鑫联国际', region: '深圳', rate: 69.3, diffPt: 11.9, level: 'high', issue: '1日卡使用率 96.8%、余量仅 30GB，容量吃紧、单卡成本高，量利结构承压', suggestion: '推动高消耗客户升档至 2GB/3GB，摊薄单卡成本' },
      { code: 'AG-005', name: '天翼环宇', region: '上海', rate: 48.5, diffPt: -8.9, level: 'low', issue: '1日卡使用率 97.2% 但 30GB 档仅 33.6%，高低档失衡，大量余量沉淀占用资金', suggestion: '压缩 30GB 铺货、加大 1GB/2GB 供给，提升资源周转' },
      { code: 'AG-002', name: '港通数码', region: '香港', rate: 55.0, diffPt: -2.4, level: 'mid', issue: '3日卡使用率 92.1% 偏紧，15GB 档仅 46.5%，结构不均衡', suggestion: '关注 3日卡补给，适当引导 15GB 档促销' },
    ],
    conclusion: '两类异常突出：**鑫联国际** 使用率 69.3%（高于平台均值 11.9pt），1日卡接近满负荷，属"容量吃紧型"、单卡成本承压；**天翼环宇** 使用率 48.5%（低于均值 8.9pt），1日卡 97.2% 与 30GB 档 33.6% 严重失衡，属"高低档失衡型"、余量沉淀大。两者量利结构问题方向相反，需分类施策。',
  },
}

// ============ 代理商视角：异动归因数据层（ops.scene2 · 代理商） ============
// 口径：本代理商 AG-001 鑫联国际自购流量的经营数据，与 agentViewOverview / agentViewDetail 保持一致
// （采购 3200GB / 已用 2216GB / 使用率 69.3%）。覆盖四类典型异动：套餐退订率、大客户用量骤减、毛利下降、流失预警。
export const agentAnomaly = {
  period: '2026年8月',
  agent: { code: 'AG-001', name: '鑫联国际', region: '深圳' },
  // ① 我名下哪个套餐这个月退订率最高
  churn: {
    metric: '退订率',
    avgRate: 4.2,
    items: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', code: 'TS-05', rate: 7.9, previousRate: 4.6, changePt: 3.3, users: 1310, level: 'high', reason: '高速档 30GB 落后竞品 50GB，长线客户到期转投竞品；该档在我处为高毛利档，退订直接影响毛利' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', code: 'TS-04', rate: 6.1, previousRate: 4.0, changePt: 2.1, users: 1540, level: 'high', reason: '15日中期档被竞品 20日档分流，大湾区客群到期不续订增多' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', code: 'TS-02', rate: 3.4, previousRate: 3.2, changePt: 0.2, users: 2890, level: 'low', reason: '短途日卡基本盘稳定' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', code: 'TS-01', rate: 2.6, previousRate: 3.1, changePt: -0.5, users: 3860, level: 'low', reason: '高频刚需，退订率不升反降' },
    ],
    conclusion: '我名下退订率最高的是 **30日不限量(30GB)（7.9%，环比 +3.3pt）** 与 **15日不限量(15GB)（6.1%，+2.1pt）**，都属高毛利的长中期档，高于我整体均值 4.2%。两档退订均指向**高速配额竞争力不足**；而 1日/3日短途档退订率稳定甚至下降，说明客群基础与我的服务能力没问题。建议优先针对这两档做高速升级与到期挽留。',
  },
  // ② 我的一个大客户这个月用量骤减，是什么原因
  bigCustomerDrop: {
    metric: '大客户用量',
    customer: { name: '星河商旅', users: 1520, pkg: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', share: '占我 1日卡订购量约 40%' },
    current: 412, // 本月用量 GB
    previous: 612, // 上月 GB
    changePct: -32.7,
    usageRate: { current: 88, previous: 95 },
    factors: [
      { factor: '自建/改用竞品高频通道', contribution: 45, detail: '星河商旅 8 月中旬新增一条竞品 1日卡通道，分流了约 180GB 的跨境短途需求' },
      { factor: '暑期旺季结束、出行频次回落', contribution: 30, detail: '该客户主做港澳台短途商旅，8 月下旬团组量环比 -18%，属季节性' },
      { factor: '结算纠纷暂缓提货', contribution: 15, detail: '8 月 20 日一笔账期结算延后，客户短期减少了批量提货' },
      { factor: '套餐额度用尽导致的体验投诉', contribution: 10, detail: '1日卡 800MB/日高速用完限速，少数客户投诉，影响复购' },
    ],
    conclusion: '星河商旅本月用量 **412GB（环比 -32.7%）**，使用率从 95% 降到 88%。主因是 **竞品通道分流（45%）** 与 **暑期旺季结束（30%）** 叠加，另有 15% 为结算节奏影响——并非我的套餐或服务出了问题。建议：① 针对该客户定向提供"1日卡升 2GB"方案弥补限速体验；② 加快 8 月账期结算、锁定 Q4 批量提货；③ 对季节性下滑不做过激反应，重点守住通道份额。',
  },
  // ③ 我这个月毛利为什么少了，是哪个客户、哪个套餐所致
  profitDrop: {
    metric: '毛利',
    current: 148, // 本月（万元）
    previous: 167, // 上月（万元）
    changePct: -11.4,
    margin: { current: 36.8, previous: 39.5, changePt: -2.7 },
    byPackage: [
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', marginNow: 34.5, marginPrev: 41.2, contribution: 46, reason: '用量向该档集中、使用率升至 96.8%，余量被吃光、单卡成本 1.8 元无处摊薄，毛利率下压 6.7pt' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', marginNow: 50.1, marginPrev: 51.3, contribution: 22, reason: '本属高毛利利润池，但退订率上升、订购量减少（-18%），毛利总额贡献缩水' },
      { name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', marginNow: 44.0, marginPrev: 46.5, contribution: 18, reason: '中期档被竞品分流，单价与量齐降' },
    ],
    byCustomer: [
      { name: '星河商旅', contribution: 38, reason: '用量骤减 -32.7%，高毛利 1日卡需求下滑' },
      { name: '粤港直通', contribution: 20, reason: '用量偏低（52%），转向低毛利、低使用档位' },
      { name: '深港跨境', contribution: 14, reason: '用量稳定但结构向低档迁移' },
    ],
    conclusion: '我本月毛利 **148 万元（环比 -11.4%）**，毛利率降至 36.8%。主因有二：① **结构性**——需求过度向低毛利、高使用率的 **1日卡** 集中（贡献 46%），余量被吃光、单卡成本无处摊薄；② **高毛利档失血**——**30GB 利润池档** 退订增加、订购下滑，贡献了 22% 的毛利下滑。客户层面 **星河商旅** 影响最大（38%）。建议把 1日卡高消耗客户向 2GB/3GB 档迁移，既修复限速体验又回收毛利。',
  },
  // ④ 找出我手头流量用得最快、最可能流失的客户群体
  churnRisk: {
    metric: '流失风险',
    totalActive: 38650,
    items: [
      { name: '高频港澳短途商旅客户', users: 9120, usageRate: 95, pkgFocus: '1日不限量(800MB/日)', risk: 'high', signal: '使用率 95%、余量常年为 0，800MB/日高速用完即限速，是投诉与流失高发群体' },
      { name: '长线自由行客户', users: 6480, usageRate: 42, pkgFocus: '30日不限量(30GB)', risk: 'high', signal: '使用率仅 42% 但退订率达 7.9%，竞品 50GB 高速档上线后到期转投风险最高' },
      { name: '中期商务出差客户', users: 5300, usageRate: 47, pkgFocus: '15日不限量(15GB)', risk: 'mid', signal: '被竞品 20日档分流，续订意愿下降' },
      { name: '低值偶尔出行客户', users: 12400, usageRate: 31, pkgFocus: '30日不限量(3GB/日)', risk: 'low', signal: '用量低、粘性弱但流失损失小，可暂缓干预' },
    ],
    conclusion: '按"用得快 + 易流失"双重口径，最需优先干预的是两类：**高频港澳短途商旅客户（9,120 人）**——1日卡使用率 95%、余量常年为 0，限速体验直接触发投诉与流失；**长线自由行客户（6,480 人）**——用量虽低（42%）但退订率已达 7.9%，是竞品挖角的首要目标。中期商务客户（5,300 人）列为次优先级。建议：前者推"1日卡升 2GB"做体验挽回，后者做 30GB→50GB 高速升级的到期挽留。',
  },
}

// ============ 代理商视角：套餐组合优化方案（ops.scene3 · 代理商） ============
// 目标：通过档位组合调整提升整体毛利。口径与 agentViewDetail.byPackage / agentProfitDetail 一致。
export const agentOptimizePlan = {
  period: '2026年8月',
  agent: { code: 'AG-001', name: '鑫联国际', region: '深圳' },
  goal: '提高整体毛利',
  current: {
    margin: 36.8, // 当前整体毛利率 %
    profit: 148, // 当前月毛利（万元）
    usageRate: 69.3, // 整体使用率
    structureNote: '需求过度向低毛利、高使用率的 1日卡集中（订购占比约 38%），高毛利的 30GB 档却在退订、订购量下滑，导致"高毛利档失血 + 低毛利档过载"，整体毛利率被压到 36.8%。',
  },
  // 各档位价利结构（供组合优化用）
  tiers: [
    { key: 'TS-01', name: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)', label: '1日卡', orderShare: 38, margin: 34.5, usageRate: 96.8, unitCost: 1.8, role: '走量引流档', problem: '使用率 96.8%、余量被吃光，单卡成本 1.8 元无处摊薄，毛利率仅 34.5%，且限速投诉多' },
    { key: 'TS-02', name: 'WeWalk-Data Only-澳门.香港.台湾共用3日不限量(800MB/日高速)', label: '3日卡', orderShare: 24, margin: 40.6, usageRate: 87.1, unitCost: 1.6, role: '主力过渡档', problem: '使用率偏高（87.1%），承接 1GB 升档能力有限，需保持足量供给' },
    { key: 'TS-03', name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(3GB/日高速)', label: '30日3GB', orderShare: 18, margin: 43.6, usageRate: 82.9, unitCost: 1.5, role: '中毛利稳定档', problem: '结构健康，可作为主推承接档，放量空间大' },
    { key: 'TS-04', name: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', label: '15日15GB', orderShare: 9, margin: 46.5, usageRate: 46.3, unitCost: 1.1, role: '高毛利中长线档', problem: '使用率仅 46.3%、余量充足，被竞品 20日档分流，铺货不足' },
    { key: 'TS-05', name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', label: '30日30GB', orderShare: 11, margin: 51.3, usageRate: 39.4, unitCost: 0.9, role: '利润池档', problem: '毛利率最高（51.3%）但退订率 7.9%、订购量下滑，高毛利档失血' },
  ],
  // 组合优化动作（按优先级）
  actions: [
    { priority: 1, title: '把 1日卡高消耗客户向 2GB 升档（新建 TS-01H）', type: '档位升级',
      detail: '针对 1日卡使用率≥90% 的高频商旅客户（约 9,120 人），定向迁移至新建的"1日卡 2GB 高速"档：单卡流量翻倍、余量回升，既解决限速投诉，又把单卡成本从 1.8 元摊到 1.1 元左右。',
      effect: '预计转化 35% 高压客户，1日卡毛利率 34.5% → 41%，月增毛利约 6.5 万元' },
    { priority: 2, title: '重点铺货 15日15GB（TS-04）承接中长线需求', type: '组合补位',
      detail: '15日15GB 毛利率 46.5%、余量充足（使用率仅 46.3%），却因铺货不足被竞品 20日档分流。将 30日30GB 的部分配额转投该档，用中长线需求填补高毛利结构。',
      effect: '该档订购占比 9% → 15%，月增毛利约 4.2 万元' },
    { priority: 3, title: '30日30GB（TS-05）做到期挽留、稳住宅毛利基本盘', type: '留存提利',
      detail: '该档毛利率 51.3% 是利润池，但退订率 7.9%、订购量环比 -18%。对到期客户做高速配额升级（30GB→50GB）与续订权益，止住高毛利档失血。',
      effect: '退订率 7.9% → 5% 以内，月增毛利约 3.6 万元' },
    { priority: 4, title: '压缩低效的 1日卡裸档供给、提高 3日卡承接占比', type: '结构调优',
      detail: '1日卡裸档（不升档部分）毛利低、投诉多，逐步压缩其配额；引导需求向 3日卡（40.6%）与 30日3GB（43.6%）迁移，抬高整体毛利中枢。',
      effect: '整体毛利率 +1.5~2pt，属结构性改善' },
  ],
  // 优化后组合目标
  target: {
    structure: '1日卡(含2GB升档) 30% · 3日卡 26% · 30日3GB 20% · 15日15GB 15% · 30日30GB 9%',
    margin: 41.5, // 目标整体毛利率
    profit: 167, // 目标月毛利（万元）
    note: '通过"高压升档 + 中长线补位 + 高毛利档留存"，把整体毛利率从 36.8% 提升至约 41.5%，月毛利回升至 167 万元左右（回到上月水平并略有超出）。',
  },
  conclusion: '提升毛利的组合优化核心不是"多加几档"，而是**把需求从低毛利过载档（1日卡）引导到高毛利有空间档（15日15GB / 30日30GB）**，同时解决 1日卡的限速体验问题。四步动作合计预计月增毛利约 **14.3 万元**，整体毛利率 36.8% → 41.5%，且不牺牲短途走量基本盘。',
}

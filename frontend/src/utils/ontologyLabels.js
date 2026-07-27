/**
 * 异动归因 / 本体推理 — 业务可读中文词典与格式化
 */

const CLASS_CN = {
  Offering: '产商品',
  OfferingConfig: '配置方案草稿',
  PricingProduct: '产商品资费',
  ProductCategory: '产品品类',
  OfferCompatibility: '资费相容关系',
  ConfigScheme: '配置方案',
  ConfigChange: '配置变更',
  SalesPolicy: '销售策略',
  ReleaseScope: '发布范围',
  NetworkCapability: '网络能力',
  FamilyOfferPolicy: '家庭资费策略',
  ChargePlan: '固费收费方案',
  PreferentialPlan: '优惠方案',
  AccountPreferential: '账务优惠',
  FloorGuarantee: '保底优惠',
  CdrPreferential: '话单优惠',
  ResourceEntitlement: '资源权益',
  DataResource: '流量资源',
  VoiceResource: '语音资源',
  SmsResource: '短信资源',
  PrintNotice: '免填单告知',
  SmsNotice: '短信告知',
  ValueAddedEquity: '增值权益',
  BusinessConstraint: '业务约束',
  ComplianceRule: '合规规则',
  BusinessScene: '业务场景',
  CodeDictionary: '业务码表',
  Metric: '运营指标',
  Channel: '渠道',
  Promotion: '促销',
  Competitor: '竞品',
  UserBehavior: '用户行为',
  Behavior: '用户行为',
  TargetUser: '目标客群',
  User: '用户',
  MarketScope: '市场范围',
  BizScenario: '业务场景',
  Template: '模板',
  Element: '要素',
  Property: '属性',
  Price: '定价',
  RiskFeature: '风险特征',
  Risk: '风险',
  Rule: '规则',
  Relation: '关系',
  Shelf: '货架',
  Compliance: '合规',
  Issue: '问题',
}

const PREDICATE_CN = {
  soldOn: '销售于',
  hasMetric: '关联指标',
  participatesIn: '参与促销',
  competesWith: '竞争对标',
  influencedBy: '受影响于',
  relatedTo: '关联',
  orderDelta: '订购变化',
  contribRatio: '贡献占比',
  hasRiskFeature: '命中风险',
  suggestAction: '处置建议',
  mapsTo: '映射场景',
  generates: '生成配置',
  belongsTo: '所属场景',
  shelfDays: '在架天数',
  configuresProduct: '配置产商品',
  belongsToCategory: '属于品类',
  hasSalesPolicy: '含销售策略',
  hasReleaseScope: '含发布范围',
  hasNetworkCapability: '含网络能力',
  hasFamilyOfferPolicy: '含家庭策略',
  hasChargePlan: '含固费方案',
  hasPreferentialPlan: '含优惠方案',
  hasResourceEntitlement: '含资源权益',
  hasPrintNotice: '含免填单',
  hasSmsNotice: '含短信告知',
  hasValueAddedEquity: '含增值权益',
  hasOfferCompatibility: '含相容关系',
  hasConfigChange: '含变更意图',
  governedBy: '受合规治理',
  appliesScene: '适用场景',
  similarTo: '相似方案',
}

const RULE_CN = {
  'R-A01': '异动确认',
  'R-A02': '渠道归因',
  'R-A03': '促销归因',
  'R-A04': '竞品冲击',
  'R-A05': '行为变化',
  'R-B01': '高风险命中',
  'R-B02': '中风险命中',
  'R-B03': '中风险命中',
  'R-B04': '优胜劣汰',
  'R-B05': '风险复核',
  'R-C01': '场景默认补全',
  'R-C02': '品类模板补全',
  'R-C03': '互斥/优惠冲突',
  'R-C04': '附加依赖主资费',
  'R-C05': '高风险零固费',
  'R-C06': '必填缺失',
  'R-C07': '异常优惠漏洞',
  'R-C08': '合规通过',
  'R-C09': '固费上下限',
}

/** 演示样例实体 ID → 中文名 */
const ENTITY_CN = {
  'OF-HF-128': '家庭融合畅享128',
  'CH-HALL': '营业厅',
  'PR-HF-GIFT': '家庭融合加装礼',
  'CP-F120': '友商融合120',
  'OF-RISK-001': '风险样例商品',
  'OF-LOW-019': '低销样例商品',
}

const FIELD_CN = {
  name: '名称',
  productName: '产品名',
  _bucket: '分类',
  status: '状态',
  revenueGrowth: '收入增长',
  newUserMonth: '月新增',
  isZeroFee: '零资费',
  growth: '增长率',
  users: '用户数',
  productType: '产品类型',
  targetMarketSize: '目标市场规模',
  onlineMonths: '在售月数',
  annualSpend: '年消费',
  vipLevel: '会员等级',
  message: '说明',
  metricCode: '指标',
  offeringName: '产商品',
  offeringId: '产商品编码',
  offerName: '资费名称',
  fixedFeeAmount: '固费金额',
  monthlyFee: '月费',
  messageRootKey: '报文根键',
  categoryCode: '品类编码',
  categoryName: '品类名称',
  productLine: '产品线',
  channelScope: '销售渠道',
  regionScope: '发布地市',
  workOrderId: '需求工单',
  chargePlan: '固费方案',
  releaseScope: '发布范围',
}

export function classCn(className) {
  if (!className) return ''
  return CLASS_CN[className] || CLASS_CN[String(className).replace(/^.*[#/]/, '')] || ''
}

export function predicateCn(p) {
  if (p == null || p === '') return ''
  const key = String(p)
  if (PREDICATE_CN[key]) return PREDICATE_CN[key]
  // 已是中文则原样返回
  if (/[\u4e00-\u9fff]/.test(key)) return key
  return key
}

export function ruleCn(ruleId) {
  if (!ruleId) return ''
  return RULE_CN[ruleId] || ''
}

export function formatRule(ruleId) {
  if (!ruleId) return ''
  const cn = ruleCn(ruleId)
  return cn ? `${cn}（${ruleId}）` : String(ruleId)
}

export function entityCn(idOrLabel) {
  if (idOrLabel == null || idOrLabel === '') return ''
  const key = String(idOrLabel)
  if (ENTITY_CN[key]) return ENTITY_CN[key]
  return key
}

export function formatWeight(weight) {
  if (weight == null || Number.isNaN(Number(weight))) return '-'
  const n = Number(weight)
  const pct = n <= 1 ? Math.round(n * 100) : Math.round(n)
  return `${pct}%`
}

export function fieldCn(key) {
  if (!key) return ''
  return FIELD_CN[key] || key
}

/**
 * 三元组 →「主体 · 关系 · 客体」
 * @param {{ s?: any, p?: any, o?: any }} t
 */
export function formatTriple(t) {
  if (!t) return ''
  const s = entityCn(t.s)
  const p = predicateCn(t.p)
  let o = t.o
  if (typeof o === 'number') {
    if (t.p === 'orderDelta' || t.p === 'contribRatio' || Math.abs(o) <= 1) {
      o = `${Math.round(o * 100)}%`
    } else {
      o = String(o)
    }
  } else {
    o = entityCn(o)
  }
  return [s, p, o].filter(Boolean).join(' · ')
}

/**
 * 解析路径步骤：`OF-HF-128-soldOn->CH-HALL` 或已是人话
 */
export function formatPathStep(step) {
  if (!step) return ''
  const raw = String(step)
  const arrow = raw.match(/^(.+?)-([A-Za-z_]+)->(.+)$/)
  if (arrow) {
    return formatTriple({ s: arrow[1], p: arrow[2], o: arrow[3] })
  }
  if (raw.includes('->')) {
    const parts = raw.split('->').map((x) => x.trim())
    if (parts.length === 2) {
      const left = parts[0].match(/^(.+?)-([A-Za-z_]+)$/)
      if (left) return formatTriple({ s: left[1], p: left[2], o: parts[1] })
    }
  }
  return entityCn(raw)
}

/**
 * 将证据行对象格式化为可读摘要
 */
export function formatEvidenceRow(row, maxFields = 4) {
  if (!row || typeof row !== 'object') return String(row ?? '')
  if (row.message) return String(row.message)
  const name = row.name || row.productName || row.offeringName
  const prefer = ['name', 'productName', 'offeringName', 'status', 'revenueGrowth', 'growth', 'users', 'newUserMonth', 'isZeroFee', '_bucket']
  const parts = []
  if (name) parts.push(String(name))
  for (const key of prefer) {
    if (key === 'name' || key === 'productName' || key === 'offeringName') continue
    if (row[key] == null || row[key] === '') continue
    parts.push(`${fieldCn(key)}：${row[key]}`)
    if (parts.length >= maxFields) break
  }
  if (parts.length <= 1) {
    for (const [k, v] of Object.entries(row)) {
      if (v == null || v === '' || k.startsWith('_') && k !== '_bucket') continue
      if (['name', 'productName', 'offeringName'].includes(k)) continue
      parts.push(`${fieldCn(k)}：${v}`)
      if (parts.length >= maxFields) break
    }
  }
  return parts.join(' · ') || '—'
}

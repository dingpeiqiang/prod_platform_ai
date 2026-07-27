/**
 * 产商品本体前端展示辅助（推理链 / 预览文案）
 * 仅做结果可视化，不做业务推理兜底；推理请走 productOntologyApi。
 */

import { classCn, formatWeight, formatPathStep } from '../utils/ontologyLabels.js'

/** 字段中文名（演示展示用） */
const FIELD_CN = {
  offeringName: '商品名称',
  offeringType: '商品类型',
  bizScenario: '业务场景',
  targetUser: '目标用户',
  channelScope: '销售渠道',
  monthlyFee: '月费',
  oneTimeFee: '一次性费用',
  includeVoice: '语音',
  includeData: '流量',
  includeBroadband: '宽带',
  mutexGroup: '互斥组',
  dependOn: '依赖商品',
  hasContract: '是否合约',
  contractMonths: '协议期',
  discountPercent: '优惠折扣',
  repeatable: '可重复订购',
  basedOnTemplate: '配置模板',
  bindExistingMainPkg: '绑定在架主套餐',
  fillSources: '字段来源',
}

const REL_CN = {
  inScenario: '所属场景',
  forTargetUser: '面向客群',
  hasPricePlan: '定价方案',
  hasElement: '包含要素',
  basedOnTemplate: '基于模板',
  bindExisting: '绑定在架商品',
  mapsTo: '映射为',
  mutexGroup: '互斥组',
  冲突: '冲突于',
}

const VALUE_CN = {
  MAIN_PKG: '主套餐互斥组',
  ADDON: '附加包互斥组',
  create_offering_config: '创建/更新商品配置',
  scenario_default: '场景缺省',
  template: '模板推荐',
  user_said: '用户表述',
  OF_HF_128: '家庭融合畅享128',
  'OF-HF-128': '家庭融合畅享128',
}

function cnField(code) {
  return FIELD_CN[code] || code
}

function cnRel(code) {
  return REL_CN[code] || code
}

function cnVal(v) {
  if (v == null || v === '') return '—'
  const key = String(v)
  return VALUE_CN[key] || key
}

function localizeTriple(t) {
  if (!t) return t
  return {
    s: cnVal(t.s === 'draft' ? '当前草稿' : t.s),
    p: cnRel(t.p),
    o: cnVal(t.o),
  }
}

/** 下一步体验引导 */
export function nextStepHints(result) {
  if (!result) return []
  const issues = result.issues || []
  const draft = result.draft || {}
  if (issues.some((i) => i.field === 'offeringName')) {
    return ['就叫家庭融合畅享158']
  }
  if (issues.some((i) => i.ruleId === 'R-C03')) {
    return ['那不加128了，就单独上158']
  }
  if (result.compliancePass && draft.offeringName) {
    return ['生成配置草稿']
  }
  if (draft.bizScenario === '家庭融合' && !draft.bindExistingMainPkg) {
    return ['再绑一个畅享128主套餐一起卖']
  }
  return []
}

/**
 * 限制关系条数，避免链过长撑高面板
 */
function limitRelations(relations, max = 6) {
  if (!relations?.length || relations.length <= max) return relations || []
  // 优先保留：场景/模板/冲突/合规，其次要素
  const priority = (r) => {
    const p = r.p || ''
    if (/inScenario|basedOnTemplate|hasRelation|hasIssue|constrainedBy/.test(p)) return 0
    if (/hasPricePlan|forTargetUser/.test(p)) return 1
    if (/hasElement|suggestsDefault|suggestsTemplate/.test(p)) return 2
    return 3
  }
  return [...relations].sort((a, b) => priority(a) - priority(b)).slice(0, max)
}

/**
 * 本体推理链：本体类关系展开（对齐配置本体 ObjectProperty）
 */
export function buildOntologyChain(result) {
  if (!result) return null
  const draft = result.draft || {}
  const slots = result.slots || {}
  const inferred = result.inferredFields || []
  const issues = result.issues || []
  const rules = result.appliedRules || []

  const scenario = draft.bizScenario || slots.bizScenario || ''
  const template = draft.basedOnTemplate || ''
  const offeringName = draft.offeringName || '商品配置草稿'

  const high = issues.filter((i) => i.issueLevel === 'HIGH')
  const medium = issues.filter((i) => i.issueLevel !== 'HIGH')
  let resultStatus = 'pass'
  let resultLabel = '合规通过'
  if (high.length) {
    resultStatus = 'block'
    resultLabel = '合规阻断'
  } else if (medium.length) {
    resultStatus = 'warn'
    resultLabel = '待补全'
  }

  const ent = (id, label, className, classCn, extra = {}) => ({
    id,
    label,
    className,
    classCn,
    ...extra,
  })

  const cfg = ent('cfg', offeringName, 'OfferingConfig', '商品配置', {
    hub: true,
    status: resultStatus,
  })

  const relations = []
  const pushRel = (s, p, pCn, o, extra = {}) => {
    relations.push({
      id: `rel-${relations.length}`,
      s,
      p,
      pCn,
      o,
      rule: extra.rule || '',
      status: extra.status || 'idle',
      inferred: !!extra.inferred,
    })
  }

  if (scenario) {
    const scn = ent('scn', scenario, 'BizScenario', '业务场景')
    pushRel(cfg, 'inScenario', '所属场景', scn, { rule: 'R-C01' })

    if (template) {
      const tpl = ent('tpl', template, 'ConfigTemplate', '配置模板')
      pushRel(cfg, 'basedOnTemplate', '基于模板', tpl, { rule: 'R-C01' })
    }

    // 场景缺省要素（最多 1 条，保持紧凑）
    const defaults = inferred.filter((f) => f.fillSource === 'scenario_default' || f.fillSource === 'template')
    const elementFields = defaults.length
      ? defaults
      : [
          draft.includeVoice && { field: 'includeVoice', value: draft.includeVoice },
          draft.includeData && { field: 'includeData', value: draft.includeData },
          draft.includeBroadband && { field: 'includeBroadband', value: draft.includeBroadband },
        ].filter(Boolean)

    elementFields.slice(0, 1).forEach((f, i) => {
      const el = ent(
        `el-${f.field || i}`,
        `${cnField(f.field)} ${f.value}`,
        'ProductElement',
        '产品要素',
        { inferred: true },
      )
      pushRel(cfg, 'hasElement', '包含要素', el, {
        rule: f.rule || 'R-C01',
        inferred: true,
      })
    })
  }

  if (draft.monthlyFee != null && draft.monthlyFee !== '') {
    const price = ent('price', `月费 ${draft.monthlyFee} 元`, 'PricePlan', '商品定价')
    pushRel(cfg, 'hasPricePlan', '定价方案', price)
  }

  if (draft.targetUser) {
    const user = ent('user', draft.targetUser, 'TargetUser', '目标用户')
    pushRel(cfg, 'forTargetUser', '面向客群', user)
  }

  if (draft.mutexGroup) {
    const ruleNode = ent('mutex', cnVal(draft.mutexGroup) || draft.mutexGroup, 'ConfigRule', '配置规则')
    pushRel(cfg, 'constrainedBy', '受约束于', ruleNode, { rule: 'R-C03' })
  }

  if (draft.bindExistingMainPkg) {
    const shelf = ent(
      'shelf',
      cnVal(draft.bindExistingMainPkg),
      'GoodsRelation',
      '商品关系',
      { status: high.some((i) => i.ruleId === 'R-C03') ? 'block' : 'idle' },
    )
    pushRel(cfg, 'hasRelation', high.some((i) => i.ruleId === 'R-C03') ? '互斥冲突' : '关联商品', shelf, {
      rule: 'R-C03',
      status: high.some((i) => i.ruleId === 'R-C03') ? 'block' : 'idle',
    })
  }

  if (high.length || medium.length) {
    issues.slice(0, 3).forEach((issue, i) => {
      const iss = ent(
        `issue-${i}`,
        `${issue.ruleId} ${issue.issueType || issue.message || ''}`.trim(),
        'ComplianceIssue',
        '合规问题',
        { status: issue.issueLevel === 'HIGH' ? 'block' : 'warn' },
      )
      pushRel(cfg, 'hasIssue', '存在问题', iss, {
        rule: issue.ruleId,
        status: issue.issueLevel === 'HIGH' ? 'block' : 'warn',
      })
    })
  } else if (result.compliancePass) {
    const ok = ent('pass', '无 HIGH 冲突', 'ComplianceIssue', '合规结论', { status: 'pass' })
    pushRel(cfg, 'hasIssue', '合规状态', ok, { rule: 'R-C08', status: 'pass' })
  }

  const limited = limitRelations(relations, 6)

  // 兼容旧 hops 流式揭示：按关系逐步展开
  const hops = limited.map((r) => ({
    id: r.id,
    subject: r.s.label,
    subjectKind: r.s.className,
    predicate: r.pCn,
    object: r.o.label,
    objectKind: r.o.className,
    rule: r.rule,
    status: r.status,
  }))

  const triples = []
  issues.forEach((issue) => {
    if (issue.triples?.length) {
      triples.push(...issue.triples.map(localizeTriple))
    } else if (issue.ruleId === 'R-C03' && draft.bindExistingMainPkg) {
      triples.push(
        { s: offeringName, p: '互斥组', o: cnVal(draft.mutexGroup || 'MAIN_PKG') },
        { s: cnVal(draft.bindExistingMainPkg), p: '互斥组', o: '主套餐互斥组' },
        { s: offeringName, p: '冲突于', o: cnVal(draft.bindExistingMainPkg) },
      )
    }
  })

  return {
    relations: limited,
    hops,
    nodes: hops.map((h, i) => ({
      id: h.id,
      type: i === hops.length - 1 ? 'result' : 'ontology',
      label: h.object,
      detail: `${h.subject} —${h.predicate}→`,
      rule: h.rule,
      status: h.status || 'idle',
    })),
    edges: [],
    triples,
    hub: cfg,
    compliancePass: !!result.compliancePass,
    blocked: high.length > 0,
    summary: result.compliancePass ? '合规通过' : (high.length ? '已阻断' : '待补全'),
    appliedRules: rules,
  }
}

/**
 * 推理预览文案：解释本轮本体如何一步步得出结论
 */
export function buildOntologyPreview(result, chain) {
  if (!result) return null
  const draft = result.draft || {}
  const inferred = result.inferredFields || []
  const issues = result.issues || []
  const high = issues.filter((i) => i.issueLevel === 'HIGH')

  const lines = []
  lines.push(`场景「${draft.bizScenario || '未绑定'}」` + (draft.basedOnTemplate ? ` → 模板 ${draft.basedOnTemplate}` : ''))
  if (inferred.length) {
    lines.push(`补全 ${inferred.slice(0, 3).map((f) => `${cnField(f.field)}=${f.value}`).join('、')}`)
  }
  if (high.length) {
    lines.push(`冲突阻断：${high.map((i) => i.ruleId).join('、')}`)
  } else if (result.compliancePass) {
    lines.push('合规通过，可提交草稿')
  } else {
    lines.push(`待处理：${issues.map((i) => i.ruleId).join('、') || '见链上结论'}`)
  }

  const conclusion = high.length
    ? `冲突阻断：${high.map((i) => i.ruleId).join('、')}`
    : (result.compliancePass ? '合规通过，可提交草稿' : `待处理 ${issues.length || 0} 项`)
  const reasoningSteps = [
    {
      id: 'cfg-1',
      label: '场景映射',
      detail: `场景「${draft.bizScenario || '未绑定'}」` + (draft.basedOnTemplate ? ` → 模板 ${draft.basedOnTemplate}` : ''),
    },
  ]
  if (inferred.length) {
    reasoningSteps.push({
      id: 'cfg-2',
      label: '字段补全',
      detail: inferred.slice(0, 3).map((f) => `${cnField(f.field)}=${f.value}`).join('、'),
    })
  }
  reasoningSteps.push({
    id: 'cfg-3',
    label: '合规结论',
    detail: conclusion,
  })
  return {
    title: '本体推理链',
    path: (chain?.nodes || []).map((n) => n.label).join(' → '),
    narrative: lines.join('；') + '。',
    conclusion,
    reasoningSteps,
    steps: chain?.nodes || [],
    edges: [],
    triples: chain?.triples || [],
    compliancePass: !!result.compliancePass,
    blocked: high.length > 0,
    summary: chain?.summary || '',
  }
}

export function buildBatchOntologyPreview(batch, chain) {
  if (!batch?.items?.length) return null
  const conclusion = `生成 ${batch.total} 条草稿，通过 ${batch.passedCount} / 待修 ${batch.pendingCount}`
  return {
    title: '本体推理链',
    path: (chain?.nodes || []).map((n) => n.label).join(' → '),
    narrative:
      `场景映射 → 生成 ${batch.total} 条草稿 → 合规筛查（通过 ${batch.passedCount} / 待修 ${batch.pendingCount}）。`,
    conclusion,
    reasoningSteps: [
      { id: 'batch-1', label: '场景映射', detail: '文档要点映射业务场景' },
      { id: 'batch-2', label: '智读·文件配置', detail: `实例化 ${batch.total} 条配置草稿` },
      { id: 'batch-3', label: '合规筛查', detail: conclusion },
    ],
    steps: chain?.nodes || [],
    edges: [],
    triples: [],
    compliancePass: batch.pendingCount === 0,
    blocked: false,
    summary: chain?.summary || '',
  }
}

export function buildBatchOntologyChain(batch) {
  if (!batch?.items?.length) return null
  const items = batch.items
  const ent = (id, label, className, classCn, extra = {}) => ({
    id, label, className, classCn, ...extra,
  })
  const firstDraft = items[0]?.draft || {}
  const docLabel = firstDraft.offeringName
    ? `方案·${firstDraft.offeringName}`
    : `方案文档×${items.length}`
  const scnLabel = firstDraft.bizScenario || batch.scenario || '业务场景'
  const doc = ent('doc', docLabel, 'Document', '方案文档')
  const scn = ent('scn', scnLabel, 'BizScenario', '业务场景')
  const hub = ent('batch', `草稿×${items.length}`, 'OfferingConfig', '商品配置', { hub: true })

  const relations = [
    {
      id: 'rel-map',
      s: doc,
      p: 'mapsToScenario',
      pCn: '映射场景',
      o: scn,
      rule: 'R-D01',
      status: 'idle',
    },
  ]

  items.forEach((it, idx) => {
    const cfg = ent(
      `cfg-${idx}`,
      it.draft?.offeringName || `草稿${idx + 1}`,
      'OfferingConfig',
      '商品配置',
      { status: it.compliancePass ? 'pass' : 'warn' },
    )
    relations.push({
      id: `rel-cfg-${idx}`,
      s: scn,
      p: 'instantiates',
      pCn: '生成配置',
      o: cfg,
      rule: 'R-D03',
      status: it.compliancePass ? 'pass' : 'warn',
    })
  })

  const gate = ent(
    'gate',
    `通过 ${batch.passedCount} / 待修 ${batch.pendingCount}`,
    'ComplianceIssue',
    '合规清单',
    { status: batch.pendingCount > 0 ? 'warn' : 'pass' },
  )
  relations.push({
    id: 'rel-gate',
    s: hub,
    p: 'hasIssue',
    pCn: '合规汇总',
    o: gate,
    rule: 'R-D05',
    status: batch.pendingCount > 0 ? 'warn' : 'pass',
  })

  // hub 仅作中心展示：连接到场景
  relations.unshift({
    id: 'rel-hub',
    s: hub,
    p: 'inScenario',
    pCn: '所属场景',
    o: scn,
    rule: 'R-D02',
    status: 'idle',
  })

  const hops = relations.map((r) => ({
    id: r.id,
    subject: r.s.label,
    subjectKind: r.s.className,
    predicate: r.pCn,
    object: r.o.label,
    objectKind: r.o.className,
    rule: r.rule,
    status: r.status,
  }))

  return {
    relations,
    hops,
    hub,
    nodes: hops.map((h, i) => ({
      id: h.id,
      type: i === hops.length - 1 ? 'result' : 'ontology',
      label: h.object,
      detail: `${h.subject} —${h.predicate}→`,
      rule: h.rule,
      status: h.status || 'idle',
    })),
    edges: [],
    triples: [],
    compliancePass: batch.pendingCount === 0,
    blocked: false,
    summary: `一文多包 · 共 ${items.length} 条`,
  }
}

export function buildRootCauseOntologyChain(result) {
  if (!result?.paths?.length) return null
  const ent = (id, label, className, classCnLabel, extra = {}) => ({
    id, label, className, classCn: classCnLabel, ...extra,
  })
  const hub = ent('offering', result.offeringName || '目标商品', 'Offering', '产商品', { hub: true })
  const metric = ent('metric', result.anomalies?.[0]?.metricCode || '累计收入', 'Metric', '运营指标', { status: 'warn' })
  const relations = [
    {
      id: 'rel-metric',
      s: hub,
      p: 'hasMetric',
      pCn: '关联指标',
      o: metric,
      rule: 'R-A01',
      status: 'warn',
      pathRank: 0,
      shared: true,
    },
  ]
  result.paths.forEach((p, idx) => {
    const typeCnLabel = classCn(p.rootCauseType) || p.rootCauseType || '根因'
    const o = ent(
      `cause-${idx}`,
      p.name,
      p.rootCauseType,
      typeCnLabel,
      { status: idx === 0 ? 'warn' : 'idle' },
    )
    const predMap = {
      Channel: ['soldOn', '销售于'],
      Promotion: ['participatesIn', '参与促销'],
      Competitor: ['competesWith', '竞争对标'],
      UserBehavior: ['influencedBy', '受影响于'],
    }
    const [pCode, pCn] = predMap[p.rootCauseType] || ['relatedTo', '关联']
    relations.push({
      id: `rel-cause-${idx}`,
      s: hub,
      p: pCode,
      pCn,
      o,
      rule: p.ruleId,
      status: idx === 0 ? 'warn' : 'idle',
      pathRank: p.rank,
    })
  })
  const hops = relations.map((r) => ({
    id: r.id,
    subject: r.s.label,
    subjectKind: r.s.className,
    predicate: r.pCn,
    object: r.o.label,
    objectKind: r.o.className,
    rule: r.rule,
    status: r.status,
    pathRank: r.pathRank,
  }))
  const primary = result.paths[0]
  return {
    relations,
    hops,
    hub,
    nodes: hops.map((h, i) => ({
      id: h.id,
      type: i === hops.length - 1 ? 'result' : 'ontology',
      label: h.object,
      detail: `${h.subject} —${h.predicate}→`,
      rule: h.rule,
      status: h.status || 'idle',
    })),
    edges: [],
    triples: result.evidenceTriples || [],
    compliancePass: false,
    blocked: false,
    summary: `根因 Top${result.paths.length}`,
    conclusion: primary
      ? `主因是「${primary.name}」（${classCn(primary.rootCauseType) || primary.rootCauseType}），影响权重 ${formatWeight(primary.weight)}`
      : '',
  }
}

export function buildRootCauseOntologyPreview(result, chain) {
  if (!result) return null
  const primary = result.paths?.[0]
  const anomaly = result.anomalies?.[0]
  const pathOptions = (result.paths || []).map((p, idx) => ({
    rank: p.rank,
    name: p.name,
    type: p.rootCauseType,
    typeCn: classCn(p.rootCauseType) || p.rootCauseType || '',
    weight: p.weight,
    ruleId: p.ruleId,
    isPrimary: !!(p.isPrimary || p.rank === 1),
    relationIds: ['rel-metric', `rel-cause-${idx}`],
    steps: (p.path || []).map((s) => formatPathStep(s)).filter(Boolean),
    evidence: p.evidence || [],
  }))
  const reasoningSteps = [
    {
      id: 'rc-1',
      label: '异动确认',
      detail: anomaly?.message || '指标异动已确认',
      ruleId: anomaly?.ruleId || 'R-A01',
    },
    {
      id: 'rc-2',
      label: '多维下钻',
      detail: '按渠道 / 促销 / 竞品 / 行为定位异动集中维度',
    },
    {
      id: 'rc-3',
      label: '根因排序',
      detail: (result.paths || [])
        .map((p) => `${p.name} ${formatWeight(p.weight)}`)
        .join(' · '),
    },
  ]
  const conclusion = chain?.conclusion
    || (primary
      ? `主因是「${primary.name}」（${classCn(primary.rootCauseType) || primary.rootCauseType}），影响权重 ${formatWeight(primary.weight)}`
      : '')
  return {
    title: '归因解释链',
    path: (chain?.nodes || []).map((n) => n.label).join(' → '),
    narrative: reasoningSteps.map((s) => s.label).join(' → ')
      + `（${(result.paths || []).map((p) => p.name).join(' / ')}）。`,
    conclusion,
    reasoningSteps,
    pathOptions,
    steps: chain?.nodes || [],
    edges: [],
    triples: result.evidenceTriples || [],
    compliancePass: false,
    blocked: false,
    summary: chain?.summary || '',
  }
}

export function buildRiskAuditOntologyChain(result) {
  if (!result) return null
  const ent = (id, label, className, classCn, extra = {}) => ({
    id, label, className, classCn, ...extra,
  })
  const hub = ent('shelf', `在架×${result.scannedCount || 80}`, 'Offering', '产商品主体', { hub: true })
  const high = ent('high', `高风险 ${result.highCount || 0}`, 'RiskFeature', '风险特征', { status: 'block' })
  const mid = ent('mid', `中风险 ${result.mediumCount || 0}`, 'RiskFeature', '风险特征', { status: 'warn' })
  const delist = ent('delist', `建议下架 ${result.suggestDelistCount || 0}`, 'RiskFeature', '优胜劣汰', { status: 'warn' })
  const relations = [
    { id: 'rel-high', s: hub, p: 'hasRiskFeature', pCn: '命中高风险', o: high, rule: 'R-B01', status: 'block' },
    { id: 'rel-mid', s: hub, p: 'hasRiskFeature', pCn: '命中中风险', o: mid, rule: 'R-B03', status: 'warn' },
    { id: 'rel-delist', s: hub, p: 'suggestAction', pCn: '处置建议', o: delist, rule: 'R-B04', status: 'warn' },
  ]
  const hops = relations.map((r) => ({
    id: r.id,
    subject: r.s.label,
    subjectKind: r.s.className,
    predicate: r.pCn,
    object: r.o.label,
    objectKind: r.o.className,
    rule: r.rule,
    status: r.status,
  }))
  return {
    relations,
    hops,
    hub,
    nodes: hops.map((h, i) => ({
      id: h.id,
      type: i === hops.length - 1 ? 'result' : 'ontology',
      label: h.object,
      detail: `${h.subject} —${h.predicate}→`,
      rule: h.rule,
      status: h.status || 'idle',
    })),
    edges: [],
    triples: [],
    compliancePass: false,
    blocked: (result.highCount || 0) > 0,
    summary: `稽核 ${result.total} 项 · ${result.ruleVersion || ''}`,
  }
}

export function buildRiskAuditOntologyPreview(result, chain) {
  if (!result) return null
  const conclusion = `高风险 ${result.highCount || 0} · 中风险 ${result.mediumCount || 0} · 建议下架 ${result.suggestDelistCount || 0}`
  return {
    title: '本体推理链',
    path: (chain?.nodes || []).map((n) => n.label).join(' → '),
    narrative: `全量扫描 ${result.scannedCount || 80} 条 → 规则 R-B01~B05 → 高风险 ${result.highCount} / 中风险 ${result.mediumCount}。`,
    conclusion,
    reasoningSteps: [
      { id: 'risk-1', label: '全量扫描', detail: `在架商品 ${result.scannedCount || 80} 条` },
      { id: 'risk-2', label: '规则命中', detail: 'R-B01 ~ R-B05 批次推理' },
      { id: 'risk-3', label: '风险分层', detail: conclusion },
    ],
    steps: chain?.nodes || [],
    edges: [],
    triples: [],
    compliancePass: false,
    blocked: (result.highCount || 0) > 0,
    summary: chain?.summary || '',
  }
}


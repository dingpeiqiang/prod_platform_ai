/**
 * 本体 MVP 本地推理（前端兜底）
 * 后端不可用时仍可完整演示智聊 / 智读，保证客户可体验。
 */

import { classCn, formatWeight, formatPathStep } from '../utils/ontologyLabels.js'

const SCENARIOS = {
  家庭融合: {
    defaults: {
      includeVoice: '500分钟',
      includeData: '40GB',
      includeBroadband: '500M',
      offeringType: 'fusion',
      mutexGroup: 'MAIN_PKG',
      targetUser: '家庭',
    },
    templateId: 'TPL-HF-128',
  },
  校园体验: {
    defaults: {
      includeVoice: '200分钟',
      includeData: '20GB',
      offeringType: 'main_pkg',
      mutexGroup: 'MAIN_PKG',
      targetUser: '校园',
      monthlyFee: 59,
    },
    templateId: 'TPL-CAMPUS-59',
  },
  '5G个人主套餐': {
    defaults: {
      includeVoice: '500分钟',
      includeData: '30GB',
      offeringType: 'main_pkg',
      mutexGroup: 'MAIN_PKG',
      targetUser: '个人',
    },
    templateId: 'TPL-5G-99',
  },
}

const SHELF = {
  'OF-HF-128': {
    offeringId: 'OF-HF-128',
    offeringName: '家庭融合畅享128',
    mutexGroup: 'MAIN_PKG',
    offeringType: 'main_pkg',
  },
}

function empty(v) {
  return v === null || v === undefined || String(v).trim() === ''
}

function num(v, d = 0) {
  if (v === null || v === undefined || v === '') return d
  const n = Number(String(v).replace(/[^\d.-]/g, ''))
  return Number.isFinite(n) ? n : d
}

function truthy(v) {
  if (typeof v === 'boolean') return v
  return ['1', 'true', 'yes', 'y', '是'].includes(String(v || '').trim().toLowerCase())
}

export function parseSlotsLocal(text = '') {
  const slots = {}
  if (/家庭融合|家庭用户|融合套餐/.test(text)) {
    slots.bizScenario = '家庭融合'
    slots.targetUser = '家庭'
    slots.offeringType = 'fusion'
  } else if (/校园|大学生|迎新/.test(text)) {
    slots.bizScenario = '校园体验'
    slots.targetUser = '校园'
    slots.offeringType = 'main_pkg'
  } else if (/5G|5g/.test(text)) {
    slots.bizScenario = '5G个人主套餐'
    slots.targetUser = '个人'
    slots.offeringType = 'main_pkg'
  }

  const fee = text.match(/月费\s*(\d+(?:\.\d+)?)/) || text.match(/(\d+(?:\.\d+)?)\s*元/)
  if (fee) slots.monthlyFee = Number(fee[1])

  const bb = text.match(/(\d+)\s*[Mm]/)
  if (bb && (/宽带/.test(text) || /家庭/.test(text))) {
    slots.includeBroadband = `${bb[1]}M`
  }

  if (/全渠道/.test(text)) slots.channelScope = '全渠道'
  else if (/电渠/.test(text) && /厅店/.test(text)) slots.channelScope = '电渠+厅店'
  else if (/电渠/.test(text)) slots.channelScope = '仅电渠'
  if (/内部验证/.test(text)) slots.channelScope = '内部验证'

  const name = text.match(/(?:叫|名称[是为]?)\s*[「"]?([^「」"，。\s]+)[」"]?/)
  if (name) slots.offeringName = name[1]
  else if (/家庭融合畅享158/.test(text)) slots.offeringName = '家庭融合畅享158'

  if (/不加128|不加畅享128|不绑128|单独上|取消绑定|解除互斥/.test(text)) {
    slots.bindExistingMainPkg = ''
    slots.clearBindExisting = true
  } else if (/再绑|再加|一起上|畅享128|OF-HF-128/.test(text)) {
    slots.bindExistingMainPkg = 'OF-HF-128'
  }

  if (/无合约|没有合约/.test(text)) slots.hasContract = '0'
  if (/有合约|协议期|补协议/.test(text)) {
    slots.hasContract = '1'
    const m = text.match(/(\d+)\s*个?月/)
    if (m) slots.contractMonths = Number(m[1])
  }
  if (/可重复/.test(text)) slots.repeatable = 'true'
  if (/不可重复|不能重复/.test(text)) slots.repeatable = 'false'
  if (/0元|零元/.test(text)) slots.monthlyFee = 0

  return slots
}

export function inferFieldsLocal(slots = {}, draft = {}) {
  const result = { ...draft }
  const fillSources = {}
  const appliedRules = []

  if (slots.clearBindExisting) {
    result.bindExistingMainPkg = ''
    fillSources.bindExistingMainPkg = 'user_said'
  }

  Object.entries(slots).forEach(([key, value]) => {
    if (key === 'clearBindExisting') return
    if (key === 'bindExistingMainPkg') {
      result[key] = value
      if (!empty(value)) fillSources[key] = 'user_said'
      return
    }
    if (!empty(value)) {
      result[key] = value
      fillSources[key] = 'user_said'
    }
  })

  const scenario = result.bizScenario || slots.bizScenario
  const cfg = SCENARIOS[scenario] || { defaults: {} }
  const defaults = cfg.defaults || {}
  const offeringType = result.offeringType || slots.offeringType

  if (scenario === '家庭融合') {
    Object.entries(defaults).forEach(([field, val]) => {
      if (empty(result[field])) {
        result[field] = val
        fillSources[field] = 'scenario_default'
        appliedRules.push('R-C01')
      }
    })
  }

  const isCampus = result.targetUser === '校园' || scenario === '校园体验'
  if (isCampus && empty(result.monthlyFee) && offeringType !== 'addon') {
    result.monthlyFee = defaults.monthlyFee ?? 59
    fillSources.monthlyFee = 'template'
    appliedRules.push('R-C02')
    Object.entries(defaults).forEach(([field, val]) => {
      if (field !== 'monthlyFee' && empty(result[field])) {
        result[field] = val
        fillSources[field] = 'scenario_default'
      }
    })
  } else if (isCampus && offeringType === 'addon') {
    Object.entries(defaults).forEach(([field, val]) => {
      if (field === 'monthlyFee' || field === 'mutexGroup') return
      if (empty(result[field])) {
        result[field] = val
        fillSources[field] = 'scenario_default'
      }
    })
  }

  if (cfg.templateId && empty(result.basedOnTemplate)) {
    result.basedOnTemplate = cfg.templateId
    fillSources.basedOnTemplate = 'template'
  }
  if (empty(result.channelScope)) {
    result.channelScope = '全渠道'
    fillSources.channelScope = 'scenario_default'
  }
  if (empty(result.mutexGroup)) {
    result.mutexGroup = defaults.mutexGroup || 'MAIN_PKG'
    fillSources.mutexGroup = 'scenario_default'
  }

  result.fillSources = fillSources
  const inferredFields = Object.entries(fillSources)
    .filter(([, src]) => src === 'scenario_default' || src === 'template')
    .map(([field, src]) => ({
      field,
      value: result[field],
      fillSource: src,
      rule: src === 'scenario_default' ? 'R-C01' : 'R-C02',
    }))

  return {
    success: true,
    draft: result,
    inferredFields,
    appliedRules: [...new Set(appliedRules)],
    recommendedTemplates: cfg.templateId ? [cfg.templateId] : [],
  }
}

export function checkComplianceLocal(draft = {}) {
  const issues = []

  ;[
    ['offeringName', '商品名称'],
    ['monthlyFee', '月费'],
    ['targetUser', '目标用户'],
    ['channelScope', '销售渠道'],
  ].forEach(([code, name]) => {
    if (empty(draft[code])) {
      issues.push({
        ruleId: 'R-C06',
        issueType: '必填缺失',
        issueLevel: 'MEDIUM',
        field: code,
        message: `缺少必填字段：${name}`,
        evidence: [`${code}=empty`],
      })
    }
  })

  const mutexGroup = draft.mutexGroup || 'MAIN_PKG'
  const bindId = draft.bindExistingMainPkg
  if (bindId && SHELF[bindId]) {
    const existing = SHELF[bindId]
    if (
      existing.mutexGroup === mutexGroup &&
      ['main_pkg', 'fusion', null, undefined, ''].includes(draft.offeringType)
    ) {
      issues.push({
        ruleId: 'R-C03',
        issueType: '资费/关系冲突',
        issueLevel: 'HIGH',
        field: 'mutexGroup',
        message: `与在架商品 ${existing.offeringName}(${bindId}) 同属互斥组 ${mutexGroup}，不可同时上架`,
        evidence: [
          `draft—mutexGroup=${mutexGroup}`,
          `${bindId}—mutexGroup=${existing.mutexGroup}`,
        ],
        triples: [
          { s: '当前草稿', p: 'mutexGroup', o: mutexGroup },
          { s: bindId, p: 'mutexGroup', o: existing.mutexGroup },
          { s: '当前草稿', p: '冲突', o: existing.offeringName },
        ],
      })
    }
  }

  if (draft.offeringType === 'addon' && empty(draft.dependOn)) {
    issues.push({
      ruleId: 'R-C04',
      issueType: '规则漏洞',
      issueLevel: 'HIGH',
      field: 'dependOn',
      message: '附加包缺少依赖的主服务/宽带',
      evidence: ['offeringType=addon', 'dependOn=empty'],
    })
  }

  const monthly = num(draft.monthlyFee, -1)
  const oneTime = num(draft.oneTimeFee, 0)
  if (monthly === 0 && oneTime === 0 && !truthy(draft.hasContract)) {
    if (draft.channelScope !== '内部验证') {
      issues.push({
        ruleId: 'R-C05',
        issueType: '高风险资费',
        issueLevel: 'HIGH',
        field: 'monthlyFee',
        message: '月费/一次性费均为0且无合约，非权益赠送白名单',
        evidence: ['monthlyFee=0', 'oneTimeFee=0', 'hasContract=0'],
      })
    }
  }

  if (num(draft.discountPercent, -1) === 100 && truthy(draft.repeatable)) {
    issues.push({
      ruleId: 'R-C07',
      issueType: '异常优惠漏洞',
      issueLevel: 'HIGH',
      field: 'discountPercent',
      message: '优惠折扣100%且可重复订购，存在异常优惠漏洞',
      evidence: ['discountPercent=100', 'repeatable=true'],
    })
  }

  const hasHigh = issues.some((i) => i.issueLevel === 'HIGH')
  const requiredOk = !issues.some((i) => i.ruleId === 'R-C06')
  const compliancePass = !hasHigh && requiredOk

  return {
    success: true,
    issues,
    compliancePass,
    appliedRules: compliancePass ? ['R-C08'] : [...new Set(issues.map((i) => i.ruleId))],
    canSubmit: compliancePass,
  }
}

export function chatConfigureLocal(text, draft = null) {
  const slots = parseSlotsLocal(text)
  const infer = inferFieldsLocal(slots, draft || {})
  const compliance = checkComplianceLocal(infer.draft)
  return {
    success: true,
    intent: 'create_offering_config',
    slots,
    draft: infer.draft,
    inferredFields: infer.inferredFields,
    recommendedTemplates: infer.recommendedTemplates,
    issues: compliance.issues,
    compliancePass: compliance.compliancePass,
    appliedRules: [...new Set([...infer.appliedRules, ...compliance.appliedRules])],
    canSubmit: compliance.canSubmit,
    local: true,
  }
}

export function defaultCampusPackages() {
  return [
    {
      offeringName: '校园青春59',
      monthlyFee: 59,
      includeData: '20GB',
      includeVoice: '200分钟',
      targetUser: '校园',
      channelScope: '电渠+厅店',
      bizScenario: '校园体验',
      offeringType: 'main_pkg',
      hasContract: '1',
      contractMonths: 12,
      sourceExcerpt: '套餐A：校园青春59元；含20GB+200分钟；目标校园；电渠+厅店',
    },
    {
      offeringName: '校园体验0元流量包',
      monthlyFee: 0,
      includeData: '5GB',
      targetUser: '校园',
      channelScope: '全渠道',
      bizScenario: '校园体验',
      offeringType: 'addon',
      hasContract: '0',
      repeatable: 'true',
      discountPercent: 100,
      sourceExcerpt: '套餐B：校园体验0元流量包；无合约；可重复订购',
    },
    {
      offeringName: '校园融合加装包',
      targetUser: '校园',
      channelScope: '电渠+厅店',
      bizScenario: '校园体验',
      offeringType: 'addon',
      dependOn: '',
      sourceExcerpt: '套餐C：校园融合加装包；依赖宽带；未写月费',
    },
  ]
}

export function batchFromDocumentLocal() {
  const packages = defaultCampusPackages()
  const items = packages.map((pkg, idx) => {
    const infer = inferFieldsLocal({ ...pkg }, {})
    const compliance = checkComplianceLocal(infer.draft)
    return {
      index: idx + 1,
      sourceExcerpt: pkg.sourceExcerpt || '',
      draft: infer.draft,
      inferredFields: infer.inferredFields,
      issues: compliance.issues,
      compliancePass: compliance.compliancePass,
      status: compliance.compliancePass ? '通过' : '待修正',
      appliedRules: [...new Set([...infer.appliedRules, ...compliance.appliedRules, 'R-D01', 'R-D02', 'R-D04'])],
    }
  })
  const passed = items.filter((i) => i.compliancePass)
  return {
    success: true,
    total: items.length,
    passedCount: passed.length,
    pendingCount: items.length - passed.length,
    items,
    appliedRules: ['R-D01', 'R-D02', 'R-D03', 'R-D04', 'R-D05'],
    confirmableDrafts: passed.map((i) => ({
      index: i.index,
      offeringName: i.draft?.offeringName,
    })),
    local: true,
  }
}

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
      { id: 'batch-2', label: '批量生成', detail: `实例化 ${batch.total} 条配置草稿` },
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
  const doc = ent('doc', '校园迎新方案', 'Document', '方案文档')
  const scn = ent('scn', '校园体验', 'BizScenario', '业务场景')
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

/* ========== 运营助手本地兜底（与方案文档口径对齐） ========== */

let _localRiskRules = {
  ruleVersion: 'RiskRules-v1.2',
  zeroSalesShelfDays: 180,
}

const LOCAL_OPS_ROOT = {
  success: true,
  offeringId: 'OF-HF-128',
  offeringName: '家庭融合畅享128',
  anomalies: [
    { metricCode: '累计收入', metricDelta: -0.18, ruleId: 'R-A01', anomalyFlag: true, message: '累计收入环比 -18%' },
    { metricCode: '留存率', metricDeltaPp: -3.2, ruleId: 'R-A01', anomalyFlag: true, message: '留存率异动 -3.2pp' },
  ],
  paths: [
    {
      rank: 1,
      name: '营业厅',
      rootCauseType: 'Channel',
      weight: 0.42,
      ruleId: 'R-A02',
      isPrimary: true,
      evidence: ['订购量变化 -35%', '渠道贡献占比 42%'],
      path: ['OF-HF-128-hasMetric->累计收入', 'OF-HF-128-soldOn->CH-HALL'],
      drill: {
        orderDelta: -0.35,
        contribRatio: 0.42,
        trend: [
          { label: 'T-2', value: 100 },
          { label: 'T-1', value: 82 },
          { label: 'T0', value: 65 },
        ],
      },
    },
    {
      rank: 2,
      name: '家庭融合加装礼',
      rootCauseType: 'Promotion',
      weight: 0.31,
      ruleId: 'R-A03',
      evidence: ['5 日后到期', '历史带动订购占比 31%'],
      path: ['OF-HF-128-participatesIn->PR-HF-GIFT'],
    },
    {
      rank: 3,
      name: '友商融合120',
      rootCauseType: 'Competitor',
      weight: 0.18,
      ruleId: 'R-A04',
      evidence: ['月费低 20 元（约 15.6%）', '本地渗透率 +2.1pp'],
      path: ['OF-HF-128-competesWith->CP-F120'],
    },
  ],
  actionList: [
    '加强厅店专项激励',
    '检查厅店品类是否被下沉',
    '续期或替换促销模板',
    '评估团购/满减替代',
    '启动动态定价/优惠随意搭',
    '输出对标分析',
  ],
  workOrder: {
    title: '家庭融合畅享128产品优化工单草稿',
    offeringId: 'OF-HF-128',
    anomalySummary: '累计收入环比 -18%',
    actions: ['加强厅店专项激励', '续期或替换促销模板', '启动动态定价/优惠随意搭'],
    status: 'draft',
    source: 'ontology_rules',
  },
  evidenceTriples: [
    { s: 'OF-HF-128', p: 'soldOn', o: 'CH-HALL' },
    { s: 'CH-HALL', p: 'orderDelta', o: -0.35 },
    { s: 'CH-HALL', p: 'contribRatio', o: 0.42 },
    { s: 'OF-HF-128', p: 'participatesIn', o: 'PR-HF-GIFT' },
    { s: 'OF-HF-128', p: 'competesWith', o: 'CP-F120' },
  ],
  reportEvidence: {
    intent: 'root_cause_analysis',
    offeringId: 'OF-HF-128',
    anomaly: { metric: '累计收入', delta: -0.18 },
    rootCauses: [
      { type: 'Channel', name: '营业厅', score: 0.42, rule: 'R-A02' },
      { type: 'Promotion', name: '家庭融合加装礼', score: 0.31, rule: 'R-A03' },
      { type: 'Competitor', name: '友商融合120', score: 0.18, rule: 'R-A04' },
    ],
  },
  appliedRules: ['R-A01', 'R-A02', 'R-A03', 'R-A04', 'R-A05'],
  snapshotAt: new Date().toISOString(),
  graphScope: {
    center: 'OF-HF-128',
    nodes: ['Metric', 'Channel', 'Promotion', 'Competitor', 'UserBehavior', 'MarketScope'],
  },
  local: true,
}

function buildLocalRiskItems(zeroDays) {
  const items = [
    {
      offeringId: 'OF-RISK-001',
      offeringName: '校园体验流量包0元',
      state: '上架',
      monthlyFee: 0,
      oneTimeFee: 0,
      shelfDays: 45,
      salesCnt30d: 120,
      revenue30d: 0,
      hasContract: false,
      strategicTag: false,
      riskLevel: 'HIGH',
      riskScore: 97,
      urgent: true,
      suggestDelist: false,
      risks: [
        { ruleId: 'R-B01', feature: '零元资费', message: '月费与一次性费均为0且非权益赠送白名单' },
        { ruleId: 'R-B02', feature: '零元无合约在架', message: '零元资费已上架且无合约约束' },
        { ruleId: 'R-B05', feature: '预警升级', message: '高风险且上架超过30天未复核' },
      ],
      actions: ['建议立即下架或转验证渠道', '紧急复核'],
      evidenceTriples: [
        { s: 'OF-RISK-001', p: 'hasPricePlan', o: 'PP-0' },
        { s: 'PP-0', p: 'monthlyFee', o: 0 },
        { s: 'OF-RISK-001', p: 'hasContract', o: false },
      ],
      disposition: { defaultAction: '建议立即下架或转验证渠道', needConfirm: true },
    },
    {
      offeringId: 'OF-LOW-019',
      offeringName: '旧版彩铃包-2019',
      state: '上架',
      monthlyFee: 3,
      oneTimeFee: 0,
      shelfDays: 287,
      salesCnt30d: 0,
      revenue30d: 0,
      hasContract: false,
      strategicTag: false,
      riskLevel: 'MEDIUM',
      riskScore: 70,
      urgent: false,
      suggestDelist: true,
      risks: [
        { ruleId: 'R-B03', feature: '长期零销', message: `近30日销量0且在架287天（阈值>${zeroDays}）` },
        { ruleId: 'R-B04', feature: '低效产商品', message: '近90日收入贡献排名后5%且无战略标签' },
      ],
      actions: ['建议下架/归档', '纳入优胜劣汰池'],
      evidenceTriples: [
        { s: 'OF-LOW-019', p: 'salesCnt30d', o: 0 },
        { s: 'OF-LOW-019', p: 'shelfDays', o: 287 },
      ],
      disposition: { defaultAction: '建议下架/归档', needConfirm: false },
    },
  ]

  // 补齐演示口径：高风险13（8零元+5异常优惠）/ 中风险7 / 建议下架7
  for (let i = 2; i <= 8; i++) {
    items.push({
      offeringId: `OF-RISK-${String(i).padStart(3, '0')}`,
      offeringName: `体验测试流量包0元-${String(i).padStart(2, '0')}`,
      riskLevel: 'HIGH',
      riskScore: 92,
      urgent: true,
      suggestDelist: false,
      shelfDays: 35 + i * 3,
      salesCnt30d: 10,
      revenue30d: 0,
      monthlyFee: 0,
      oneTimeFee: 0,
      hasContract: false,
      risks: [
        { ruleId: 'R-B01', feature: '零元资费' },
        { ruleId: 'R-B02', feature: '零元无合约在架' },
      ],
      actions: ['建议立即下架或转验证渠道'],
      evidenceTriples: [],
      disposition: { defaultAction: '建议立即下架或转验证渠道', needConfirm: true },
    })
  }
  for (let i = 1; i <= 5; i++) {
    items.push({
      offeringId: `OF-DISC-${String(i).padStart(3, '0')}`,
      offeringName: `全额赠送可重复包-${String(i).padStart(2, '0')}`,
      riskLevel: 'HIGH',
      riskScore: 92,
      urgent: true,
      suggestDelist: false,
      risks: [{ ruleId: 'R-B01', feature: '异常全额赠送' }],
      actions: ['限售 + 复核优惠规则'],
      evidenceTriples: [],
      disposition: { defaultAction: '限售 + 复核优惠规则', needConfirm: true },
    })
  }
  for (let i = 1; i <= 6; i++) {
    items.push({
      offeringId: `OF-LOW-${String(i).padStart(3, '0')}`,
      offeringName: `旧版加装包-长期零销-${String(i).padStart(2, '0')}`,
      riskLevel: 'MEDIUM',
      riskScore: 70,
      suggestDelist: true,
      shelfDays: 190 + i * 14,
      salesCnt30d: 0,
      revenue30d: 0,
      risks: [{ ruleId: 'R-B03', feature: '长期零销' }],
      actions: ['建议下架/归档'],
      evidenceTriples: [],
      disposition: { defaultAction: '建议下架/归档', needConfirm: false },
    })
  }

  // 阈值收紧时追加演示样本
  if (zeroDays <= 90) {
    for (let i = 1; i <= 3; i++) {
      items.push({
        offeringId: `OF-LOW-T${String(i).padStart(2, '0')}`,
        offeringName: `旧版加装包-阈值演示-${String(i).padStart(2, '0')}`,
        state: '上架',
        monthlyFee: 6 + i,
        shelfDays: 100 + i * 12,
        salesCnt30d: 0,
        revenue30d: 2 + i,
        hasContract: false,
        strategicTag: false,
        riskLevel: 'MEDIUM',
        riskScore: 70,
        suggestDelist: true,
        risks: [{ ruleId: 'R-B03', feature: '长期零销', message: `在架${100 + i * 12}天（阈值>${zeroDays}）` }],
        actions: ['建议下架/归档'],
        evidenceTriples: [{ s: `OF-LOW-T${String(i).padStart(2, '0')}`, p: 'shelfDays', o: 100 + i * 12 }],
        disposition: { defaultAction: '建议下架/归档', needConfirm: false },
      })
    }
  }

  return items.sort((a, b) => b.riskScore - a.riskScore)
}

export function analyzeRootCauseLocal(offeringId = 'OF-HF-128') {
  return { ...LOCAL_OPS_ROOT, offeringId, local: true }
}

export function auditRisksLocal() {
  const zeroDays = _localRiskRules.zeroSalesShelfDays || 180
  const items = buildLocalRiskItems(zeroDays)
  const highCount = items.filter((i) => i.riskLevel === 'HIGH').length
  const mediumCount = items.filter((i) => i.riskLevel === 'MEDIUM').length
  const suggestDelistCount = items.filter((i) => i.suggestDelist).length
  return {
    success: true,
    total: items.length,
    scannedCount: 80,
    highCount,
    mediumCount,
    suggestDelistCount,
    items,
    appliedRules: ['R-B01', 'R-B02', 'R-B03', 'R-B04', 'R-B05'],
    ruleVersion: _localRiskRules.ruleVersion,
    riskRules: { ..._localRiskRules },
    coverageCompare: {
      manualSampleRate: 0.05,
      manualHitEstimate: 1,
      ruleFullCoverage: 1,
      ruleHitCount: items.length,
    },
    auditedAt: new Date().toISOString(),
    local: true,
  }
}

export function updateRiskRulesLocal(overrides = {}) {
  if (overrides.reset) {
    _localRiskRules = { ruleVersion: 'RiskRules-v1.2', zeroSalesShelfDays: 180 }
  } else {
    _localRiskRules = { ..._localRiskRules, ...overrides }
  }
  return { success: true, riskRules: { ..._localRiskRules }, local: true }
}

export function getOpsDashboardLocal() {
  const risk = auditRisksLocal()
  return {
    success: true,
    anomalyOfferingCount: 1,
    highRiskCount: risk.highCount,
    mediumRiskCount: risk.mediumCount,
    suggestDelistCount: risk.suggestDelistCount,
    shelfCount: 80,
    ruleVersion: _localRiskRules.ruleVersion,
    alerts: [
      { id: 'alert-hf-128', type: 'anomaly', tag: '异动', offeringId: 'OF-HF-128', text: 'OF-HF-128 累计收入环比 -18%' },
      { id: 'alert-risk', type: 'risk', tag: '风险', text: `高风险在架商品 ${risk.highCount} 个待处置` },
    ],
    local: true,
  }
}

export function buildRootCauseOntologyChain(result) {
  if (!result?.paths?.length) return null
  const ent = (id, label, className, classCnLabel, extra = {}) => ({
    id, label, className, classCn: classCnLabel, ...extra,
  })
  const hub = ent('offering', result.offeringName || '家庭融合畅享128', 'Offering', '产商品', { hub: true })
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


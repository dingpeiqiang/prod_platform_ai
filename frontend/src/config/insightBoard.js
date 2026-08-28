/**
 * 右侧「实时看板」结论提炼配置
 *
 * 设计初衷：展示当前会话的生产物 —— AI 每完成一轮对话，从该轮结构化结果
 * （msg.intentData / msg.queryResults / msg.batch / msg.evidence 等）提炼出
 * 与业务场景有价值的结论，按时间追加成卡片流。
 *
 * 提炼器签名：extract(msg) -> { metrics: [{ label, value, tone }], points: [string] } | null
 *   - 返回 null 表示该轮无可提炼结论（如欢迎语、澄清追问、失败回复），看板跳过
 *   - metrics 为关键指标（KPI 大数字），tone 取 'good' | 'warn' | 'bad' | 'neutral'
 *   - points 为一句话业务结论要点（纯文本，最多 3 条）
 */

const clampText = (v, max = 60) => {
  const s = String(v ?? '').trim()
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

const fmtInt = (n) => {
  const v = Number(n)
  return Number.isFinite(v) ? Math.round(v).toLocaleString('zh-CN') : '0'
}

const fmtPct = (v) => {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return `${(Number(v) * 100).toFixed(1)}%`
}

const parseNum = (v) => {
  if (v == null || v === '') return null
  const n = Number(String(v).replace(/%/g, '').trim())
  return Number.isFinite(n) ? n : null
}

/* ---------------- RD 场景提炼器 ---------------- */

const rdExtractors = {
  // 智聊·对话配置：草稿名 + 合规状态
  'rd.chat': (msg) => {
    const d = msg.intentData || {}
    const draft = d.draft || {}
    const name = draft.offerName || draft.offeringName || d.offeringName
    const fee = draft.fixedFeeAmount ?? draft.monthlyFee ?? d.monthlyFee
    const issues = d.issues || []
    const pass = d.compliancePass === true
    if (!name && !issues.length && d.compliancePass == null) return null
    return {
      metrics: [
        {
          label: '合规',
          value: pass ? '通过' : issues.length ? '待修正' : '—',
          tone: pass ? 'good' : issues.length ? 'warn' : 'neutral',
        },
        { label: '待处理', value: fmtInt(issues.length) },
      ],
      points: [
        name ? `草稿「${clampText(name, 24)}」${fee != null && fee !== '' ? ` · 月费 ${fee}` : ''}` : '',
        issues.length ? `命中 ${issues.length} 条规则：${clampText(issues.map((i) => i.ruleId || i.message || i).slice(0, 3).join('、'), 46)}` : '',
      ].filter(Boolean),
    }
  },

  // 智读·文件配置：批次通过/待修
  'rd.import': (msg) => {
    const batch = msg.batch || msg.fileRef?.counts
    if (!batch) return null
    const total = Number(batch.total ?? 0)
    const passed = Number(batch.passedCount ?? batch.passed ?? 0)
    const pending = Number(batch.pendingCount ?? batch.pending ?? 0)
    if (!total && !passed && !pending) return null
    return {
      metrics: [
        { label: '草稿', value: fmtInt(total) },
        { label: '通过', value: fmtInt(passed), tone: passed ? 'good' : 'neutral' },
        { label: '待修', value: fmtInt(pending), tone: pending ? 'warn' : 'neutral' },
      ],
      points: msg.fileRef?.fileName ? `来源：${clampText(msg.fileRef.fileName, 30)}` : '',
    }
  },

  // 智查·历史复用：命中方案数
  'rd.query': (msg) => {
    const results = msg.queryResults || msg.intentData?.results || []
    const count = msg.intentData?.count ?? results.length
    if (!count && !results.length) return null
    return {
      metrics: [
        { label: '命中方案', value: fmtInt(count) },
      ],
      points: [
        msg.intentData?.question ? `检索「${clampText(msg.intentData.question, 32)}」` : '',
        results.length ? '可点击消息卡「复制配置」一键开稿' : '',
      ].filter(Boolean),
    }
  },

  // 智检·合规校验：通过/阻断
  'rd.compliance': (msg) => {
    const d = msg.intentData || {}
    const r = d.result || d
    if (r.compliancePass == null && !(r.issues || []).length) return null
    const issues = r.issues || []
    const high = issues.filter((i) => i.issueLevel === 'HIGH')
    return {
      metrics: [
        {
          label: '结论',
          value: r.compliancePass ? '通过' : high.length ? '已阻断' : '待修正',
          tone: r.compliancePass ? 'good' : high.length ? 'bad' : 'warn',
        },
        { label: '命中规则', value: fmtInt(issues.length) },
      ],
      points: [
        (r.offeringName || r.draft?.offeringName) ? `对象：${clampText(r.offeringName || r.draft?.offeringName, 24)}` : '',
        issues.length ? clampText(issues.slice(0, 2).map((i) => i.message || i.ruleId || i).join('；'), 52) : '',
      ].filter(Boolean),
    }
  },

  // 多方案对比：推荐方案
  'rd.compare': (msg) => {
    const cmp = msg.intentData?.compareResult || msg.intentData
    const rec = cmp?.recommended || cmp?.recommendation || cmp?.recommendedScheme
    const comparisons = cmp?.comparisons || cmp?.schemes || cmp?.rows || []
    if (!rec && !comparisons.length) return null
    return {
      metrics: [
        { label: '方案', value: fmtInt(comparisons.length) },
        { label: '推荐', value: clampText(rec?.label || rec?.name || rec || '—', 12) },
      ],
      points: [
        rec?.compliancePass != null
          ? `推荐方案合规${rec.compliancePass ? '通过' : '需先修正'}`
          : '',
        cmp?.explanation ? clampText(String(cmp.explanation).replace(/\n+/g, ' '), 56) : '',
      ].filter(Boolean),
    }
  },
}

/* ---------------- Ops 场景提炼器 ---------------- */

const opsExtractors = {
  // 市场洞察：样本/平均增长/负增长/零资费
  'ops.query': (msg) => {
    const d = msg.intentData || {}
    const results = d.results || msg.queryResults || []
    const rows = results
      .map((r) => {
        const g = parseNum(r.growth ?? r.revenueGrowth ?? r.growthRate)
        if (g == null) return null
        return Math.abs(g) > 1 ? g / 100 : g
      })
      .filter((g) => g != null)
    const zeroFee = results.filter((r) => r.isZeroFee === true || r.isZeroFee === 'true' || r.isZeroFee === 1).length
    if (!results.length && !d.count) return null
    const avg = rows.length ? rows.reduce((a, b) => a + b, 0) / rows.length : null
    const neg = rows.filter((g) => g < 0).length
    return {
      metrics: [
        { label: '样本', value: fmtInt(d.count ?? results.length) },
        { label: '平均增长', value: fmtPct(avg), tone: avg != null && avg >= 0 ? 'good' : 'bad' },
        { label: '负增长', value: fmtInt(neg), tone: neg ? 'warn' : 'neutral' },
        ...(zeroFee ? [{ label: '零资费', value: fmtInt(zeroFee), tone: 'warn' }] : []),
      ],
      points: [
        d.question ? `查询「${clampText(d.question, 32)}」` : '',
        neg ? `${neg} 项负增长，可发起异动归因下钻` : rows.length ? '各项均处于正增长区间' : '',
      ].filter(Boolean),
    }
  },

  // 立项研判：结论 + 命中规则
  'ops.online': (msg) => {
    const d = msg.intentData || {}
    const v = d.verdict
    if (!v && !(d.triggeredRules || d.triggered_rules || []).length) return null
    const rules = d.triggeredRules || d.triggered_rules || []
    const map = { allow: '通过', deny: '拒绝', review: '待审' }
    return {
      metrics: [
        { label: '立项结论', value: map[v] || v || '—', tone: v === 'allow' ? 'good' : v === 'deny' ? 'bad' : v === 'review' ? 'warn' : 'neutral' },
        ...(rules.length ? [{ label: '命中规则', value: fmtInt(rules.length), tone: 'warn' }] : []),
      ],
      points: [
        d.policySetId ? `策略集 ${d.policySetId}` : '',
        d.reason ? clampText(String(d.reason), 56) : '',
      ].filter(Boolean),
    }
  },

  // 风险稽核：高/中/建议下架
  'ops.risk': (msg) => {
    const d = msg.intentData || {}
    const items = d.items || d.riskAudit?.items || []
    const high = d.highCount ?? d.riskAudit?.highCount ?? items.filter((i) => (i.riskLevel || '').toUpperCase() === 'HIGH').length
    const medium = d.mediumCount ?? d.riskAudit?.mediumCount ?? items.filter((i) => (i.riskLevel || '').toUpperCase() === 'MEDIUM').length
    const delist = d.suggestDelistCount ?? d.riskAudit?.suggestDelistCount ?? items.filter((i) => i.suggestDelist).length
    const scanned = d.scannedCount ?? d.riskAudit?.scannedCount ?? items.length
    if (!items.length && !scanned) return null
    return {
      metrics: [
        { label: '扫描', value: fmtInt(scanned) },
        { label: '高风险', value: fmtInt(high), tone: high ? 'bad' : 'good' },
        { label: '中风险', value: fmtInt(medium), tone: medium ? 'warn' : 'neutral' },
        { label: '建议下架', value: fmtInt(delist), tone: delist ? 'warn' : 'neutral' },
      ],
      points: [
        d.ruleVersion || d.riskAudit?.ruleVersion ? `规则版本 ${d.ruleVersion || d.riskAudit.ruleVersion}` : '',
        delist ? '可在对话中筛选建议下架项并发起处置' : '',
      ].filter(Boolean),
    }
  },

  // 运营监控：告警/高优/工单
  'ops.monitor': (msg) => {
    const d = msg.intentData || {}
    const alerts = d.alertItems || d.alerts?.items || []
    const total = d.alertCount ?? alerts.length
    const high = d.highPriorityCount ?? 0
    const openWo = d.openWorkOrderCount ?? 0
    if (total == null && !alerts.length) return null
    return {
      metrics: [
        { label: '告警', value: fmtInt(total) },
        { label: '高优先级', value: fmtInt(high), tone: high ? 'warn' : 'neutral' },
        { label: '进行中工单', value: fmtInt(openWo) },
      ],
      points: [
        alerts.length ? `最新告警：${clampText(alerts[0]?.offeringName || alerts[0]?.id || '', 20)} ${clampText(alerts[0]?.text || '', 32)}` : '暂无告警，各项指标平稳',
        high ? '可对高优先级告警发起智能归因' : '',
      ].filter(Boolean),
    }
  },

  // 异动归因：主因 + 贡献度
  'ops.reason': (msg) => {
    const d = msg.intentData || {}
    const root = d.rootCause || d
    const paths = root.paths || d.paths || []
    const anomalies = root.anomalies || d.anomalies || []
    if (!paths.length && !anomalies.length) return null
    const main = paths[0]
    const anomaly = anomalies[0]
    return {
      metrics: [
        { label: '根因路径', value: fmtInt(paths.length) },
        { label: '主因贡献', value: main?.weight != null ? fmtPct(main.weight) : '—', tone: 'neutral' },
        ...(main?.evidence?.length ? [{ label: '证据', value: fmtInt(main.evidence.length) }] : []),
      ],
      points: [
        main?.name ? `主因：${clampText(main.name, 28)}${main.ruleId ? `（${main.ruleId}）` : ''}` : '',
        anomaly?.message ? clampText(anomaly.message, 52) : '',
        d.target ? `对象：${clampText(d.target, 26)}` : '',
      ].filter(Boolean),
    }
  },
}

/* ---------------- 通用兜底提炼器 ---------------- */

/**
 * 无场景命中时，从消息的结构化产物做最低限度提炼：
 * - toolResults 完成的工具调用 → 「执行了什么」
 * - evidence 摘要 → 证据条数
 * - queryPlan 业务意图
 */
function genericExtractor(msg) {
  const parts = []
  const metrics = []
  const tools = (msg.toolResults || []).filter((t) => t.status === 'done')
  if (tools.length) {
    metrics.push({ label: '工具', value: fmtInt(tools.length) })
    parts.push(`已执行：${clampText(tools.map((t) => t.displayName || t.name).join('、'), 44)}`)
  }
  const ev = msg.evidence
  if (ev && (ev.count != null || (ev.items && ev.items.length))) {
    metrics.push({ label: '证据', value: fmtInt(ev.count ?? ev.items.length) })
  }
  const plan = msg.queryPlan
  if (plan?.intent && plan.intent !== 'CHAT') {
    parts.push(`业务意图：${clampText(plan.intent, 30)}`)
  }
  if (!metrics.length && !parts.length) return null
  return { metrics, points: parts }
}

/** 欢迎语 / 澄清 / 失败回复：明确跳过，不进看板 */
function shouldSkip(msg) {
  if (msg.sceneWelcome) return true
  if (msg.agentError) return true
  if (Array.isArray(msg.clarify) && msg.clarify.length && !msg.intentData) return true
  const text = String(msg.streamText || msg.content || '')
  if (!text && !msg.intentData && !msg.batch && !msg.queryResults?.length && !(msg.toolResults || []).length) return true
  return false
}

/* ---------------- 场景识别（复用原推导逻辑） ---------------- */

const RD_SCENE_MAP = {
  'rd.chat': 'rd.chat',
  'chat-generate': 'rd.chat',
  form: 'rd.chat',
  form_update: 'rd.chat',
  'rd.import': 'rd.import',
  'file-parse': 'rd.import',
  'rd.query': 'rd.query',
  query: 'rd.query',
  'rd.compliance': 'rd.compliance',
  compliance: 'rd.compliance',
  'rd.compare': 'rd.compare',
  compare: 'rd.compare',
}

const OPS_SCENE_MAP = {
  ops_rules: 'ops.rules',
  rules: 'ops.rules',
  ops_monitor: 'ops.monitor',
  root_cause: 'ops.reason',
  risk_audit: 'ops.risk',
  market_insight: 'ops.query',
  online_check: 'ops.online',
}

const OPS_INTENT_MAP = {
  product_ops_query: 'ops.query',
  product_ops_reason: 'ops.reason',
  product_ops_monitor: 'ops.monitor',
  product_ops_compare: 'rd.compare',
}

/**
 * 识别消息所属业务场景 key
 */
export function resolveSceneKey(msg) {
  const s = msg?.scene || msg?._scenario
  if (s && RD_SCENE_MAP[s]) return RD_SCENE_MAP[s]
  if (s && OPS_SCENE_MAP[s]) return OPS_SCENE_MAP[s]

  const intent = msg?.intentType || ''
  if (OPS_INTENT_MAP[intent]) return OPS_INTENT_MAP[intent]
  if (intent === 'product_ops_policy') {
    const d = msg?.intentData || {}
    if (d.expectationType === 'risk_audit' || d.riskAudit || Array.isArray(d.items)) return 'ops.risk'
    return 'ops.online'
  }
  if (intent === 'form' || intent === 'form_update') return 'rd.chat'
  if (intent === 'file-parse') return 'rd.import'
  return ''
}

/**
 * 场景 key → 看板徽标文案
 */
export const SCENE_LABELS = {
  'rd.chat': '智聊配置',
  'rd.import': '智读导入',
  'rd.query': '智查复用',
  'rd.compliance': '智检合规',
  'rd.compare': '方案对比',
  'ops.query': '市场洞察',
  'ops.online': '立项研判',
  'ops.monitor': '运营监控',
  'ops.reason': '异动归因',
  'ops.risk': '风险稽核',
  'ops.rules': '规则运营',
}

const TONE_ICONS = {
  'rd.chat': '\u{1F4DD}',
  'rd.import': '\u{1F4DA}',
  'rd.query': '\u{1F50D}',
  'rd.compliance': '\u2696\uFE0F',
  'rd.compare': '\u{1F504}',
  'ops.query': '\u{1F4C8}',
  'ops.online': '\u{1F6E1}\uFE0F',
  'ops.monitor': '\u{1F4CB}',
  'ops.reason': '\u{1F500}',
  'ops.risk': '\u{1F6AB}',
  'ops.rules': '\u{1F9EA}',
}

/**
 * 单条消息 → 看板结论卡片数据（无结论返回 null）
 * @returns {null | { id, sceneKey, sceneLabel, icon, timestamp, metrics, points, tone }}
 */
export function extractInsight(msg, index = 0) {
  if (!msg || msg.role !== 'assistant' || !msg.done) return null
  if (shouldSkip(msg)) return null

  const sceneKey = resolveSceneKey(msg)
  let result = null

  if (sceneKey && rdExtractors[sceneKey]) result = rdExtractors[sceneKey](msg)
  else if (sceneKey && opsExtractors[sceneKey]) result = opsExtractors[sceneKey](msg)
  if (!result) {
    // 兜底：ops.rules / 未知场景走通用提炼
    result = genericExtractor(msg)
  }
  if (!result) return null

  const metrics = (result.metrics || []).filter((m) => m && m.value != null && m.value !== '')
  const points = (result.points || []).filter(Boolean).slice(0, 3)
  if (!metrics.length && !points.length) return null

  const tone = metrics.find((m) => m.tone && m.tone !== 'neutral')?.tone || 'neutral'
  return {
    id: `insight_${msg.id || index}_${msg.timestamp || ''}`,
    sceneKey,
    sceneLabel: SCENE_LABELS[sceneKey] || '会话结论',
    icon: TONE_ICONS[sceneKey] || '\u{1F4CC}',
    timestamp: msg.timestamp || Date.now(),
    metrics,
    points,
    tone,
  }
}

/**
 * 全量消息 → 看板卡片流（按时间正序；面板内做倒序展示）
 */
export function extractInsights(messages = []) {
  const cards = []
  ;(messages || []).forEach((msg, idx) => {
    const card = extractInsight(msg, idx)
    if (card) cards.push(card)
  })
  return cards
}

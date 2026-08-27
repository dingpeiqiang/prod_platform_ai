/**
 * 右侧「场景感知汇总面板」卡片集配置（v3）
 *
 * 每个场景对应一套只读汇总卡片。卡片为描述符：
 *   { key, icon, title, hint, compute(summary) -> { value, sub, tone } }
 * 其中 `tone` 取值 '' | 'good' | 'warn' | 'bad' | 'neutral'，用于着色。
 * `summary` 由 SceneSummaryPanel 每次渲染时从响应式状态快照而来，故卡片实时、无需轮询。
 */

const toneOfStatus = (status) => {
  if (status === '通过' || status === 'pass' || status === 'submitted') return 'good'
  if (status === '待修正' || status === 'fail' || status === 'pending') return 'warn'
  return 'neutral'
}

/* ---------------- 通用小工具 ---------------- */

function countBy(list, predicate) {
  return (Array.isArray(list) ? list : []).filter(predicate).length
}

function fmtInt(n) {
  const v = Number(n)
  return Number.isFinite(v) ? Math.round(v).toLocaleString('zh-CN') : '0'
}

function pct(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return `${(Number(v) * 100).toFixed(1)}%`
}

/* ============================================================
 * RD 场景卡片集
 * ============================================================ */

const rdCards = {
  'rd.chat': [
    {
      key: 'draft-summary',
      icon: '\u{1F4DD}',
      title: '当前配置摘要',
      compute: (s) => {
        const p = s.currentProduct
        if (!p) return { value: '暂无草稿', tone: 'neutral' }
        const name = p.name || '未命名草稿'
        const fee = p.ontologyDraft?.monthlyFee ?? p.data?.monthlyFee
        return {
          value: name,
          sub: `月费${fee != null && fee !== '' ? fee : '-'} · ${p.ontologyDraft?.bizScenario || '—'}`,
          tone: 'good',
        }
      },
    },
    {
      key: 'compliance',
      icon: '\u{1F6E1}\uFE0F',
      title: '合规状态',
      compute: (s) => {
        const p = s.currentProduct
        if (!p) return { value: '—', tone: 'neutral' }
        const pass = p.compliancePass || p.auditStatus === 'pass'
        return {
          value: pass ? '合规通过' : '待修正',
          tone: pass ? 'good' : 'warn',
          sub: (p.issues || []).length ? `命中 ${p.issues.length} 条规则` : '未命中规则',
        }
      },
    },
    {
      key: 'draft-count',
      icon: '\u{1F4CB}',
      title: '本会话草稿',
      compute: (s) => {
        const passed = countBy(s.products, (p) => p.compliancePass && p.status !== 'submitted')
        const pending = countBy(s.products, (p) => !p.compliancePass && p.status !== 'submitted')
        const filed = countBy(s.products, (p) => p.status === 'submitted')
        return {
          value: `${s.products.length} 条`,
          sub: `通过 ${passed} / 待修 ${pending} / 已备案 ${filed}`,
          tone: passed ? 'good' : 'neutral',
        }
      },
    },
  ],

  'rd.import': [
    {
      key: 'batch',
      icon: '\u{1F4DA}',
      title: '智读批次清单',
      compute: (s) => ({
        value: `通过 ${s.batchCounts.passed} · 待修 ${s.batchCounts.pending} · 可入库 ${s.batchCounts.confirmable}`,
        sub: s.batchCounts.total ? `共 ${s.batchCounts.total} 条草稿` : '暂无批次',
        tone: s.batchCounts.passed ? 'good' : 'warn',
      }),
    },
    {
      key: 'batch-items',
      icon: '\u{1F9F0}',
      title: '各条目状态',
      compute: (s) => {
        const items = s.batchItems || []
        if (!items.length) return { value: '—', tone: 'neutral' }
        const rows = items
          .slice(0, 6)
          .map((it) => `${it.draft?.offeringName || it.name || '未命名'} · ${it.status || '—'}`)
        return {
          value: rows.join('\n'),
          tone: toneOfStatus(items.some((i) => i.status === '待修正') ? 'pending' : 'pass'),
          multiline: true,
        }
      },
    },
  ],

  'rd.query': [
    {
      key: 'query-count',
      icon: '\u{1F50D}',
      title: '命中结果',
      compute: (s) => ({
        value: `${fmtInt(s.queryCount)} 条`,
        sub: s.queryQuestion ? `「${s.queryQuestion}」` : '历史复用检索',
        tone: 'neutral',
      }),
    },
    {
      key: 'copied',
      icon: '\u{1F4CB}',
      title: '已复制草稿',
      compute: (s) => ({
        value: `${s.copiedCount} 条`,
        sub: '复制为草稿后可继续校验',
        tone: 'neutral',
      }),
    },
  ],

  'rd.compliance': [
    {
      key: 'compliance-result',
      icon: '\u{1F50D}',
      title: '最近校验结论',
      compute: (s) => {
        const last = s.lastCompliance
        if (!last) return { value: '—', tone: 'neutral' }
        return {
          value: last.pass ? '合规通过' : '未通过',
          tone: last.pass ? 'good' : 'warn',
          sub: (last.issues || []).length ? `命中 ${last.issues.length} 条规则` : '无规则命中',
        }
      },
    },
    {
      key: 'compliance-rules',
      icon: '\u{2696}\uFE0F',
      title: '命中规则 / 风险',
      compute: (s) => {
        const rules = (s.lastCompliance?.issues || []).slice(0, 5)
        if (!rules.length) return { value: '无风险项', tone: 'good' }
        return {
          value: rules.map((r) => (r.ruleId ? `${r.ruleId}${r.level ? `·${r.level}` : ''}` : r.message || r)).join('、'),
          tone: 'warn',
          multiline: true,
        }
      },
    },
  ],

  'rd.compare': [
    {
      key: 'compare-verdict',
      icon: '\u{1F504}',
      title: '推荐方案',
      compute: (s) => {
        const cmp = s.compareResult
        if (!cmp) return { value: '—', tone: 'neutral' }
        const rec = cmp.recommended || cmp.recommendation || cmp.recommendedScheme
        return {
          value: rec?.name || rec || '见对话详情',
          tone: 'good',
        }
      },
    },
    {
      key: 'compare-list',
      icon: '\u{1F4CA}',
      title: '方案对比',
      compute: (s) => {
        const list = (s.compareResult?.schemes || s.compareResult?.rows || []).slice(0, 6)
        if (!list.length) return { value: '—', tone: 'neutral' }
        return {
          value: list.map((r) => `${r?.name || '方案'} · ${r?.evaluation?.verdict || r?.verdict || '—'}`).join('\n'),
          tone: 'neutral',
          multiline: true,
        }
      },
    },
  ],

  // 会话常驻兜底
  session: [
    {
      key: 'config-summary',
      icon: '\u{1F4E6}',
      title: '本会话配置',
      compute: (s) => {
        const all = countBy(s.products, (p) => p.status !== 'submitted')
        const pending = countBy(s.products, (p) => p.status !== 'submitted' && !p.compliancePass)
        const pass = countBy(s.products, (p) => p.status !== 'submitted' && p.compliancePass)
        const filed = countBy(s.products, (p) => p.status === 'submitted')
        return {
          value: `${s.products.length} 条`,
          sub: `草稿 ${all} / 待审 ${pending} / 通过 ${pass} / 已备案 ${filed}`,
          tone: 'neutral',
        }
      },
    },
    {
      key: 'ref-docs',
      icon: '\u{1F4C1}',
      title: '引用文档',
      compute: (s) => ({
        value: `${s.fileRefs.length} 份`,
        sub: s.fileRefs.map((f) => f.fileName).join('、') || '尚未上传文档',
        tone: 'neutral',
      }),
    },
  ],
}

/* ============================================================
 * Ops 场景卡片集
 * ============================================================ */

const opsCards = {
  'ops.query': [
    {
      key: 'market-bucket',
      icon: '\u{1F50D}',
      title: '在售 / 风险分布',
      compute: (s) => ({
        value: `在售 ${fmtInt(s.queryCount)}`,
        sub: `负增长 ${s.negativeGrowth} · 零元资费 ${s.zeroFee}`,
        tone: s.negativeGrowth ? 'warn' : 'good',
      }),
    },
    {
      key: 'market-growth',
      icon: '\u{1F4C8}',
      title: '平均增长',
      compute: (s) => ({
        value: pct(s.avgGrowth),
        sub: s.negativeGrowth ? `${s.negativeGrowth} 项负增长` : '均处于正增长',
        tone: s.avgGrowth >= 0 ? 'good' : 'bad',
      }),
    },
  ],

  'ops.online': [
    {
      key: 'policy-verdict',
      icon: '\u{1F6E1}\uFE0F',
      title: '立项结论',
      compute: (s) => {
        const v = s.policy?.verdict
        const map = { allow: '通过', deny: '拒绝', review: '待审' }
        return {
          value: map[v] || v || '—',
          tone: v === 'allow' ? 'good' : v === 'deny' ? 'bad' : v === 'review' ? 'warn' : 'neutral',
        }
      },
    },
    {
      key: 'policy-rules',
      icon: '\u{2696}\uFE0F',
      title: '命中策略 / 触发规则',
      compute: (s) => {
        const rules = (s.policy?.triggeredRules || s.policy?.triggered_rules || []).slice(0, 5)
        if (!rules.length) return { value: '—', tone: 'neutral' }
        return {
          value: rules.map((r) => (typeof r === 'string' ? r : r.id || r.ruleId || r.name || r)).join('、'),
          tone: 'warn',
          multiline: true,
        }
      },
    },
  ],

  'ops.monitor': [
    {
      key: 'monitor-alerts',
      icon: '\u{1F4CA}',
      title: '告警概览',
      compute: (s) => ({
        value: `${fmtInt(s.monitor.total)} 条告警`,
        sub: `高优先级 ${fmtInt(s.monitor.highPriority)}`,
        tone: s.monitor.highPriority ? 'warn' : 'neutral',
      }),
    },
    {
      key: 'monitor-wo',
      icon: '\u{1F4E6}',
      title: '工单进度',
      compute: (s) => ({
        value: `${s.monitorWorkOrders.length} 张`,
        sub: `进行中 ${countBy(s.monitorWorkOrders, (w) => (w.status || '').toLowerCase() !== 'closed' && !/closed|done/.test(w.status))}`,
        tone: s.monitorWorkOrders.length ? 'neutral' : 'good',
      }),
    },
  ],

  'ops.reason': [
    {
      key: 'reason-main',
      icon: '\u{1F500}',
      title: '主因 / 次因',
      compute: (s) => {
        const paths = s.rootCause?.paths || []
        if (!paths.length) return { value: '—', tone: 'neutral' }
        const main = paths[0]
        return {
          value: main.name,
          sub: paths[1] ? `次因 ${paths[1].name}` : '仅识别到主因',
          tone: 'good',
        }
      },
    },
    {
      key: 'reason-evidence',
      icon: '\u{1F9EA}',
      title: '贡献度 / 证据',
      compute: (s) => {
        const paths = s.rootCause?.paths || []
        if (!paths.length) return { value: '—', tone: 'neutral' }
        const main = paths[0]
        return {
          value: main.weight != null ? pct(main.weight) : '—',
          sub: `证据 ${countBy(paths, (p) => (p.evidence || []).length)} 条 · 引用规则 ${(s.rootCause?.referencedRules || []).length} 条`,
          tone: 'neutral',
        }
      },
    },
  ],

  'ops.risk': [
    {
      key: 'risk-dist',
      icon: '\u{1F4CA}',
      title: '风险分布',
      compute: (s) => ({
        value: `高 ${fmtInt(s.risk.high)} · 中 ${fmtInt(s.risk.medium)} · 低 ${fmtInt(s.risk.low)}`,
        sub: `扫描 ${fmtInt(s.risk.scanned)} 条`,
        tone: s.risk.high ? 'bad' : 'good',
      }),
    },
    {
      key: 'risk-delist',
      icon: '\u{1F6AB}',
      title: '建议下架',
      compute: (s) => ({
        value: `${fmtInt(s.risk.suggestDelist)} 条`,
        sub: '可在对话中发起处置',
        tone: s.risk.suggestDelist ? 'warn' : 'neutral',
      }),
    },
  ],

  'ops.rules': [
    {
      key: 'rules-version',
      icon: '\u{2696}\uFE0F',
      title: '规则版本',
      compute: (s) => ({
        value: s.rules?.version || s.rules?.ruleVersion || '—',
        sub: 'R-A/B/C/D 风险阈值',
        tone: 'neutral',
      }),
    },
    {
      key: 'rules-override',
      icon: '\u{1F4CB}',
      title: '当前阈值覆盖',
      compute: (s) => {
        const overrides = s.rules?.overrides || s.rules?.thresholdOverrides || []
        if (!overrides.length) return { value: '默认阈值', tone: 'good' }
        return { value: `${overrides.length} 项已覆盖`, tone: 'warn' }
      },
    },
  ],

  session: [
    {
      key: 'ops-summary',
      icon: '\u{1F4E6}',
      title: '本会话活动',
      compute: (s) => ({
        value: `工单 ${s.monitorWorkOrders.length} · 告警 ${fmtInt(s.monitor.total)}`,
        sub: `归因 ${s.analysisCount} 次 · 稽核 ${s.risk.scanned ? '已执行' : '—'}`,
        tone: 'neutral',
      }),
    },
  ],
}

/* ---------------- 导出 ---------------- */

export const sceneSummaries = {
  rd: rdCards,
  ops: opsCards,
}

export const SCENE_TITLES = {
  'rd.chat': '智聊·对话配置',
  'rd.import': '智读·文件配置',
  'rd.query': '智查·历史复用',
  'rd.compliance': '智检·合规校验',
  'rd.compare': '多方案对比',
  'ops.query': '市场洞察',
  'ops.online': '立项研判',
  'ops.monitor': '运营监控',
  'ops.reason': '异动归因',
  'ops.risk': '风险稽核',
  'ops.rules': '规则运营',
  session: '会话常驻',
}

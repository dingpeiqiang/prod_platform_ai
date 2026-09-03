/**
 * opsFormat - 产品运营视图共享格式化工具（迁移自原型 OpsView.vue）
 */

/** 金额：千分位两位小数 */
export function fmt(n) {
  if (n === null || n === undefined) return '—'
  return Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 同比：带符号百分数 */
export function fmtPct(n) {
  if (n === null || n === undefined) return '—'
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}

/** 结构占比：value 为 0~1 小数，乘 100 显示，不带正负号 */
export function fmtRatio(n) {
  if (n === null || n === undefined) return '—'
  return (n * 100).toFixed(2) + '%'
}

/**
 * 同比涨跌配色类：通信领域惯例上涨红、下跌绿
 */
export function deltaCls(n) {
  if (n === null || n === undefined) return 'ov-yoy-flat'
  return n > 0 ? 'ov-yoy-up' : n < 0 ? 'ov-yoy-down' : 'ov-yoy-flat'
}

/** 结构占比条宽（0~1 → 百分比，最小 2% 保证可见） */
export function pctWidth(v) {
  return Math.min(100, Math.max(2, Math.round(v * 100))) + '%'
}

/** 健康等级配色类：健康绿 / 亚健康琥珀 / 不健康红 */
export function drillHealthCls(level) {
  return level === '不健康' ? 'ov-drill-health-warn' : level === '亚健康' ? 'ov-drill-health-mid' : 'ov-drill-health-ok'
}

/** 增减幅（字符串如 +18.3% / -35%）配色类 */
export function drillDeltaCls(n) {
  if (!n) return 'ov-flat'
  if (n.startsWith('-')) return 'ov-down'
  return 'ov-up'
}

/**
 * 四维评分：按 dimMeta 折算各维度得分与二级指标明细
 * dims 顺序对应 dimMeta：eff[5] / market[4] / quality[3] / life[3]
 */
export function computeDims(p, dimMeta) {
  if (!p || !p.dims) return []
  return dimMeta.map((d) => {
    const arr = p.dims[d.key] || []
    let sub = 0
    const items = d.items.map((it, i) => {
      const s = arr[i]?.s ?? 0
      sub += (s / 10) * it.full
      return { name: it.name, score: s, value: arr[i]?.v ?? '' }
    })
    const score = Math.round(sub)
    return { key: d.key, name: d.name, weight: d.weight, full: d.full, score, pct: Math.round((sub / d.full) * 100), items }
  })
}

/** 综合分 = 四维度得分之和 */
export function computeTotal(dims) {
  return dims.reduce((s, d) => s + d.score, 0)
}

/** 等级：健康≥85 / 亚健康 60≤x<85 / 不健康<60 */
export function computeLevel(total) {
  return total >= 85 ? '健康' : total >= 60 ? '亚健康' : '不健康'
}

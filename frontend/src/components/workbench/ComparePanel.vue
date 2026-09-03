<template>
  <div class="cmp">
    <header class="cmp-head">
      <span class="cmp-title">方案比对</span>
      <span v-if="items.length" class="cmp-sub">{{ items.length }} 个方案</span>
    </header>

    <div v-if="!items.length" class="cmp-empty">暂无比对数据</div>

    <template v-else>
      <!-- 资费要素对比表：better 列自动高亮 -->
      <div class="cmp-table-wrap">
        <table class="cmp-table">
          <thead>
            <tr>
              <th class="dim-col">对比项</th>
              <th v-for="(item, i) in items" :key="i" :class="{ 'rec-col': isRecommended(i) }">
                {{ item.offeringName || item.name || `方案${i + 1}` }}
                <span v-if="isRecommended(i)" class="rec-badge">推荐</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in tableRows" :key="row.label">
              <td class="dim-col">{{ row.label }}</td>
              <td
                v-for="(cell, i) in row.cells"
                :key="i"
                :class="{ better: row.betterIndex === i }"
              >
                {{ cell }}
                <span v-if="row.betterIndex === i" class="better-mark">✦</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 关键指标柱状图 -->
      <div v-if="chartOption" class="cmp-chart">
        <EChartsChart :option="chartOption" :height="220" />
      </div>

      <!-- 自动差异结论 -->
      <div class="cmp-conclusions">
        <div class="cmp-conclusions-title">差异结论</div>
        <p v-for="(line, i) in conclusions" :key="i" class="cmp-conclusion">{{ line }}</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EChartsChart from '../common/EChartsChart.vue'

const props = defineProps({
  /** compareResult：{ comparisons: [], recommended: {}, explanation } */
  compareResult: { type: Object, default: null },
})

const items = computed(() => {
  const list = props.compareResult?.comparisons
  return Array.isArray(list) ? list : []
})

const isRecommended = (i) => {
  const rec = props.compareResult?.recommended
  if (!rec || !items.value.length) return false
  const recId = rec.offeringId || rec.offeringName
  const cur = items.value[i]
  return recId && cur && (cur.offeringId === recId || cur.offeringName === recId)
}

const num = (v) => {
  const n = parseFloat(v)
  return Number.isFinite(n) ? n : null
}

/** 表行定义：label + 取值 + better 方向（min/max） */
const ROW_DEFS = [
  { label: '月费(元)', key: 'monthlyFee', better: 'min', fmt: (v) => v },
  { label: '年预估收益(元)', key: 'estimatedAnnualRevenue', better: 'max', fmt: (v) => (num(v) ? Math.round(num(v)).toLocaleString() : v) },
  { label: '目标用户', key: 'targetUser', better: null },
  { label: '销售渠道', key: 'channelScope', better: null },
  { label: '合规', key: 'compliancePass', better: 'max', fmt: (v) => (v === true || v === 'true' ? '通过' : '待处理') },
  { label: '状态', key: 'verdict', better: null, fmt: (v) => (v === 'allow' ? '允许上架' : v === 'deny' ? '暂不允许' : (v || '-')) },
]

const tableRows = computed(() => {
  if (!items.value.length) return []
  return ROW_DEFS.map((def) => {
    const rawValues = items.value.map((it) => it[def.key])
    const cells = rawValues.map((v) => {
      if (v == null || v === '') return '-'
      return def.fmt ? def.fmt(v) : String(v)
    })
    let betterIndex = -1
    if (def.better) {
      const nums = rawValues.map((v) => num(v)).filter((n) => n != null)
      if (nums.length >= 2) {
        const target = def.better === 'min' ? Math.min(...nums) : Math.max(...nums)
        betterIndex = rawValues.findIndex((v) => num(v) === target)
      }
    }
    return { label: def.label, cells, betterIndex }
  })
})

/** 柱状图：月费 + 年收益双指标 */
const chartOption = computed(() => {
  const list = items.value.filter((it) => num(it.monthlyFee) != null)
  if (list.length < 2) return null
  const names = list.map((it) => it.offeringName || '方案')
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['月费(元)', '年预估收益(元)'], textStyle: { fontSize: 11 } },
    grid: { left: 50, right: 16, top: 36, bottom: 24 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' },
    series: [
      {
        name: '月费(元)',
        type: 'bar',
        data: list.map((it) => num(it.monthlyFee)),
        itemStyle: { color: '#60a5fa', borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '年预估收益(元)',
        type: 'bar',
        data: list.map((it) => Math.round(num(it.estimatedAnnualRevenue) || 0)),
        itemStyle: { color: '#34d399', borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})

/** 自动差异结论（迁移自原型 QueryComparePanel 生成逻辑） */
const conclusions = computed(() => {
  const list = items.value
  if (list.length < 2) return []
  const out = []
  const fees = list.map((it) => num(it.monthlyFee)).filter((n) => n != null)
  if (fees.length >= 2) {
    const min = Math.min(...fees)
    const max = Math.max(...fees)
    const cheap = list.find((it) => num(it.monthlyFee) === min)
    const expensive = list.find((it) => num(it.monthlyFee) === max)
    if (max > min) {
      out.push(
        `费用差异：**${cheap?.offeringName}**（${min} 元/月）最低，较 ${expensive?.offeringName}（${max} 元/月）${max === min ? '' : `低 ${Math.round(((max - min) / max) * 100)}%`}${max > 0 && min / max <= 0.5 ? '，定价差达一倍以上' : ''}。`,
      )
    }
  }
  const revs = list.map((it) => ({ name: it.offeringName, v: num(it.estimatedAnnualRevenue) })).filter((r) => r.v != null)
  if (revs.length >= 2) {
    const best = revs.reduce((a, b) => (b.v > a.v ? b : a))
    out.push(`收益预期：**${best.name}** 年预估收益最高（${Math.round(best.v).toLocaleString()} 元）。`)
  }
  const passed = list.filter((it) => it.compliancePass === true || it.compliancePass === 'true')
  if (passed.length && passed.length < list.length) {
    const denied = list.filter((it) => !passed.includes(it)).map((it) => it.offeringName)
    out.push(`合规差异：${passed.map((it) => `**${it.offeringName}**`).join('、')} 合规通过；${denied.join('、')} 存在待处理项。`)
  }
  if (props.compareResult?.explanation) {
    out.push(props.compareResult.explanation)
  }
  return out.length ? out : ['各方案差异较小，可结合客群定位与渠道策略综合决策。']
})
</script>

<style scoped>
.cmp { display: flex; flex-direction: column; gap: 14px; height: 100%; overflow-y: auto; padding: 14px; box-sizing: border-box; }
.cmp-head { display: flex; align-items: baseline; gap: 8px; }
.cmp-title { font-size: 15px; font-weight: 700; color: #0f172a; }
.cmp-sub { font-size: 12px; color: #64748b; }
.cmp-empty { font-size: 13px; color: #94a3b8; text-align: center; padding: 40px 0; }

.cmp-table-wrap { overflow-x: auto; border: 1px solid #e2e8f0; border-radius: 12px; }
.cmp-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.cmp-table th, .cmp-table td { padding: 9px 12px; border-bottom: 1px solid #f1f5f9; text-align: left; vertical-align: middle; }
.cmp-table thead th { background: #f8fafc; font-weight: 700; color: #334155; white-space: nowrap; }
.cmp-table tbody tr:last-child td { border-bottom: none; }
.dim-col { color: #64748b; font-weight: 600; white-space: nowrap; width: 110px; }
.cmp-table td.better { background: #ecfdf5; color: #059669; font-weight: 700; }
.better-mark { font-size: 10px; margin-left: 4px; }
.rec-badge {
  display: inline-block; font-size: 10px; font-weight: 700;
  background: #2563eb; color: #fff; padding: 1px 7px; border-radius: 999px; margin-left: 6px;
}

.cmp-chart { border: 1px solid #e2e8f0; border-radius: 12px; padding: 10px; }

.cmp-conclusions {
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 12px; padding: 12px 14px;
  display: flex; flex-direction: column; gap: 6px;
}
.cmp-conclusions-title { font-size: 12px; font-weight: 700; color: #b45309; }
.cmp-conclusion { font-size: 12px; color: #78350f; line-height: 1.7; margin: 0; }
</style>

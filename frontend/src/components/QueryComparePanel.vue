<template>
  <div class="compare-panel" :style="{ width: panelWidth + 'px' }">
    <!-- 拖拽调宽 -->
    <div
      class="compare-panel-resizer"
      @mousedown="startResize"
      @touchstart.passive="startResize"
    >
      <div class="resizer-line"></div>
    </div>

    <div class="compare-panel-header">
      <div class="compare-panel-title-wrap">
        <svg class="compare-panel-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="7" height="18" rx="1" />
          <rect x="14" y="8" width="7" height="13" rx="1" />
        </svg>
        <span class="compare-panel-title">产品比对分析</span>
        <span class="compare-count">{{ items.length }} 个产品</span>
      </div>
      <button class="compare-panel-btn" @click="$emit('close')" title="关闭">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>

    <div class="compare-panel-body">
      <!-- 比对对象 -->
      <div class="compare-subjects">
        <div v-for="(it, idx) in items" :key="it.code" class="subject-chip" :style="{ borderColor: colors[idx % colors.length] }">
          <span class="subject-dot" :style="{ background: colors[idx % colors.length] }"></span>
          <span class="subject-name">{{ it.name }}</span>
          <span class="subject-tag" :class="it.from === 'draft' ? 'tag-draft' : 'tag-stock'">
            {{ it.from === 'draft' ? '在配草稿' : '存量在架' }}
          </span>
        </div>
      </div>

      <!-- 资费要素对比表 -->
      <div class="compare-section">
        <div class="section-title">资费要素对比</div>
        <table class="compare-table">
          <thead>
            <tr>
              <th class="col-field">对比项</th>
              <th v-for="(it, idx) in items" :key="it.code" class="col-item" :style="{ color: colors[idx % colors.length] }">{{ it.name }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in tableRows" :key="row.label">
              <td class="col-field">{{ row.label }}</td>
              <td v-for="(cell, idx) in row.cells" :key="idx" :class="{ best: row.better === idx }">{{ cell }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 资源量对比图 -->
      <div class="compare-section">
        <div class="section-title">资源量对比</div>
        <EChartsChart :option="chartOption" :height="280" />
      </div>

      <!-- 差异结论 -->
      <div class="compare-section">
        <div class="section-title">差异结论</div>
        <ul class="conclusion-list">
          <li v-for="(c, idx) in conclusions" :key="idx" v-html="c"></li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import EChartsChart from './EChartsChart.vue'

const props = defineProps({
  items: { type: Array, default: () => [] }, // buildCompareItem 归一化后的产品数组
})

defineEmits(['close'])

const colors = ['#6d28d9', '#0f766e', '#f59e0b', '#dc2626']

// 拖拽调宽：与配置面板一致，支持拖动调整面板宽度
const MIN_WIDTH = 400
const MAX_WIDTH = 900
const DEFAULT_WIDTH = 620
const panelWidth = ref(DEFAULT_WIDTH)
const resizing = ref(false)

const startResize = (e) => {
  e.preventDefault()
  resizing.value = true
  const startX = e.touches ? e.touches[0].clientX : e.clientX
  const startWidth = panelWidth.value

  const onMove = (ev) => {
    const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX
    const delta = startX - clientX
    panelWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta))
  }

  const onUp = () => {
    resizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onUp)
}

onUnmounted(() => {
  resizing.value = false
})

const flow = (it) => (it.flowGB ? `${Number(it.flowGB) % 1 === 0 ? Number(it.flowGB) : Number(it.flowGB).toFixed(1)}GB高速` : '--')
const pct = (v) => (v != null ? `${Number(v).toFixed(1)}%` : '—')
const gbNum = (v) => (v != null ? `${Number(v)}GB` : '—')
const validity = (it) => (it.validityLabel ? `${it.validityLabel}（${it.dataRoamArea || '香港.台湾'}）` : '--')
const network = (it) => (it.networkType ? (it.networkType === '5g' ? '5G' : it.networkType.toUpperCase()) : '--')
const throttle = (it) => it.flowOvercharge || 'PCRF限速'

const statusText = { draft: '草稿', submitted: '待审核', listed: '销售中', completed: '审批通过' }

const tableRows = computed(() => {
  const items = props.items || []
  if (!items.length) return []
  const flowVals = items.map((i) => i.flowGB).filter((v) => v > 0)
  const rows = [
    {
      label: '高速配额',
      cells: items.map(flow),
      better: flowVals.length > 1 ? items.findIndex((i) => i.flowGB === Math.max(...flowVals)) : -1,
    },
    {
      label: '有效期 / 漫游地',
      cells: items.map(validity),
      better: -1,
    },
    { label: '网络速率', cells: items.map(network), better: -1 },
    { label: '高速用尽后', cells: items.map(throttle), better: -1 },
    { label: '当前状态', cells: items.map((i) => statusText[i.status] || i.status || '-'), better: -1 },
  ]
  // 运营数据对比项：有任一套餐已上架并产生运营数据时才展示
  if (items.some((i) => i.hasOps)) {
    const rateVals = items.map((i) => i.opsUsageRate).filter((v) => v != null)
    const remainVals = items.map((i) => i.opsRemainingGB).filter((v) => v != null)
    const orderVals = items.map((i) => i.opsOrderCount).filter((v) => v != null)
    rows.push(
      {
        label: '订购量',
        cells: items.map((i) => (i.opsOrderCount != null ? i.opsOrderCount + ' 单' : '-')),
        better: orderVals.length >= 2 ? items.findIndex((i) => i.opsOrderCount === Math.max(...orderVals)) : -1,
      },
      {
        label: '去重用户',
        cells: items.map((i) => (i.opsUserCount != null ? i.opsUserCount + ' 人' : '-')),
        better: -1,
      },
      {
        label: '使用率',
        cells: items.map((i) => pct(i.opsUsageRate)),
        better: rateVals.length >= 2 ? items.findIndex((i) => i.opsUsageRate === Math.max(...rateVals)) : -1,
      },
      { label: '实际用量', cells: items.map((i) => gbNum(i.opsUsedGB)), better: -1 },
      {
        label: '剩余余量',
        cells: items.map((i) => gbNum(i.opsRemainingGB)),
        better: remainVals.length >= 2 ? items.findIndex((i) => i.opsRemainingGB === Math.max(...remainVals)) : -1,
      },
    )
  }
  return rows
})

const chartOption = computed(() => {
  const items = props.items || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0 },
      grid: { left: 48, right: 24, top: 40, bottom: 32 },
      xAxis: { type: 'category', data: ['高速配额(GB)', '有效期(天)', '剩余余量(GB)'] },
      yAxis: { type: 'value' },
      series: items.map((it, idx) => ({
        name: it.name,
        type: 'bar',
        barMaxWidth: 36,
        itemStyle: { color: colors[idx % colors.length], borderRadius: [4, 4, 0, 0] },
        data: [+(it.flowGB.toFixed(1)), +(it.validityDays || 0), it.opsRemainingGB || 0],
      })),
  }
})

const conclusions = computed(() => {
  const items = props.items || []
  if (items.length < 2) return ['请提供至少两个产品进行比对。']
  const [a, b] = items
  const list = []
  // 高速配额 / 有效期性价比
  if (a.flowGB > 0 || b.flowGB > 0) {
    const hi = a.flowGB >= b.flowGB ? a : b
    const lo = hi === a ? b : a
    const ratio = lo.flowGB > 0 ? (hi.flowGB / lo.flowGB).toFixed(1) : null
    let s = `<b>${hi.name}</b> 高速配额 ${hi.flowGB}GB，优于 <b>${lo.name}</b> 的 ${lo.flowGB}GB`
    if (ratio && Number(ratio) !== 1) s += `（约为 ${lo.name} 的 ${ratio} 倍）`
    if (a.validityLabel !== b.validityLabel) {
      s += `，有效期 ${a.validityLabel} vs ${b.validityLabel}`
    } else {
      s += `，有效期相同（${a.validityLabel}）`
    }
    list.push(s + '。')
  }
  // 漫游覆盖差异
  if (a.dataRoamArea !== b.dataRoamArea) {
    list.push(`漫游覆盖：<b>${a.name}</b> 支持「${a.dataRoamArea}」，<b>${b.name}</b> 支持「${b.dataRoamArea}」，覆盖更广者对出境游客吸引力更强。`)
  } else {
    list.push(`两产品漫游地一致（${a.dataRoamArea}），覆盖无差异。`)
  }
  // 运营数据差异（使用率/余量，体现资源配置与消化情况）
  if (a.hasOps && b.hasOps) {
    if (a.opsOrderCount != null && b.opsOrderCount != null && a.opsOrderCount !== b.opsOrderCount) {
      const more = a.opsOrderCount >= b.opsOrderCount ? a : b
      const less = more === a ? b : a
      const delta = Math.round(((more.opsOrderCount - less.opsOrderCount) / less.opsOrderCount) * 100)
      list.push(`订购量：<b>${more.name}</b> 订购达 <b>${more.opsOrderCount} 单</b>，比 <b>${less.name}</b>（${less.opsOrderCount} 单）高出 ${delta}%，覆盖客户更广、成交量更活跃。`)
    }
    if (a.opsUsageRate != null && b.opsUsageRate != null) {
      const high = a.opsUsageRate >= b.opsUsageRate ? a : b
      const low = high === a ? b : a
      list.push(`运营使用率：<b>${high.name}</b> 为 ${high.opsUsageRate}%，<b>${low.name}</b> 为 ${low.opsUsageRate}%，` + (high.opsUsageRate >= 80 ? '使用率高、余量承压，代理商成本偏高。' : high.opsUsageRate >= 50 ? '处于中等区间，结构较均衡。' : '使用率偏低、余量充裕，是代理商利润较好、可加大铺货的档位。'))
    }
    if (a.opsRemainingGB != null && b.opsRemainingGB != null && a.opsRemainingGB !== b.opsRemainingGB) {
      const more = a.opsRemainingGB >= b.opsRemainingGB ? a : b
      list.push(`剩余余量：<b>${more.name}</b> 沉淀 ${more.opsRemainingGB}GB 余量，资源配置更充裕。`)
    }
  } else if (a.hasOps || b.hasOps) {
    const withData = a.hasOps ? a : b
    const noData = a.hasOps ? b : a
    list.push(`运营数据：<b>${withData.name}</b> 已上架并有运营数据，<b>${noData.name}</b> 暂无，可待其上线后按实际使用率再行评估。`)
  }
  // 高速用尽后策略
  if (a.flowOvercharge !== b.flowOvercharge) {
    list.push(`限速策略：<b>${a.name}</b> 高速用尽后 ${a.flowOvercharge}，<b>${b.name}</b> 高速用尽后 ${b.flowOvercharge}。`)
  }
  // 草稿提示
  const draft = items.find((i) => i.from === 'draft')
  if (draft) {
    list.push(`<b>${draft.name}</b> 为在配草稿（尚未上架），上线后可补充实际运营数据再进行同期比对。`)
  }
  return list
})
</script>

<style scoped>
.compare-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
  position: relative;
}
/* 拖拽调宽 */
.compare-panel-resizer {
  position: absolute;
  top: 0;
  left: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}
.resizer-line {
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.15s ease;
  border-radius: 1px;
}
.compare-panel-resizer:hover .resizer-line {
  background: #3b82f6;
}
.compare-panel-resizer:active .resizer-line {
  background: #2563eb;
}
.compare-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #f0f0f2;
  flex-shrink: 0;
}
.compare-panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6d28d9;
}
.compare-panel-title {
  font-size: 14px;
  font-weight: 600;
}
.compare-count {
  font-size: 12px;
  color: #9ca3af;
  background: #f5f3ff;
  border-radius: 10px;
  padding: 1px 8px;
}
.compare-panel-btn {
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
}
.compare-panel-btn:hover {
  background: #f3f4f6;
  color: #374151;
}
.compare-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px 24px;
}
.compare-subjects {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.subject-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 5px 10px;
  font-size: 12px;
  background: #fafafa;
}
.subject-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.subject-name {
  font-weight: 600;
  color: #374151;
}
.subject-tag {
  font-size: 11px;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}
.tag-stock {
  color: #0f766e;
  background: #ecfdf5;
}
.tag-draft {
  color: #b45309;
  background: #fffbeb;
}
.compare-section {
  margin-bottom: 20px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #6d28d9;
  line-height: 16px;
}
.compare-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.compare-table th,
.compare-table td {
  border: 1px solid #f0f0f2;
  padding: 7px 10px;
  text-align: left;
  vertical-align: top;
}
.compare-table thead th {
  background: #faf5ff;
  font-weight: 600;
  white-space: nowrap;
}
.compare-table .col-field {
  width: 110px;
  color: #6b7280;
  white-space: nowrap;
}
.compare-table td.best {
  background: #f5f3ff;
  font-weight: 600;
  color: #6d28d9;
}
.conclusion-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  color: #4b5563;
  line-height: 1.9;
}
.conclusion-list b {
  color: #111827;
}
</style>

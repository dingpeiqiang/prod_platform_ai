<template>
  <div class="sparql-result-graph">
    <div v-if="!results || results.length === 0" class="empty-state">
      <el-empty description="暂无查询结果" />
    </div>
    <div v-else class="graph-container">
      <div class="graph-header">
        <span class="result-count">共 {{ results.length }} 条结果</span>
        <div class="chart-tabs" v-if="isTableData">
          <button
            v-for="tab in chartTabs"
            :key="tab.key"
            :class="['tab-btn', { active: activeTab === tab.key }]"
            @click="activeTab = tab.key"
          >{{ tab.label }}</button>
        </div>
        <el-button v-else size="small" @click="resetGraph">重置视图</el-button>
      </div>

      <!-- 三元组图谱 -->
      <svg v-if="!isTableData" ref="svgRef" class="graph-svg" />

      <!-- 表格数据图表 -->
      <div v-else class="chart-area">
        <div v-if="activeTab === 'bar'" class="bar-chart-wrap">
          <svg :viewBox="`0 0 ${barChartWidth} ${barChartHeight}`" class="chart-svg">
            <g v-for="(item, i) in barData" :key="i">
              <rect
                :x="barMarginLeft + i * barGroupWidth + barGroupPadding"
                :y="barChartHeight - barMarginBottom - item.scaledH"
                :width="barRectWidth"
                :height="item.scaledH"
                :fill="item.color"
                rx="3"
              />
              <text
                :x="barMarginLeft + i * barGroupWidth + barGroupPadding + barRectWidth / 2"
                :y="barChartHeight - barMarginBottom + 14"
                text-anchor="middle"
                class="bar-x-label"
              >{{ item.label }}</text>
              <text
                :x="barMarginLeft + i * barGroupWidth + barGroupPadding + barRectWidth / 2"
                :y="barChartHeight - barMarginBottom - item.scaledH - 6"
                text-anchor="middle"
                class="bar-value"
              >{{ item.value }}</text>
            </g>
            <line
              v-for="(tick, i) in yTicks"
              :key="'tick-' + i"
              :x1="barMarginLeft"
              :y1="barChartHeight - barMarginBottom - tick.scaled"
              :x2="barChartWidth - 20"
              :y2="barChartHeight - barMarginBottom - tick.scaled"
              stroke="#e5e7eb"
              stroke-dasharray="4,4"
            />
            <text
              v-for="(tick, i) in yTicks"
              :key="'tickLabel-' + i"
              :x="barMarginLeft - 8"
              :y="barChartHeight - barMarginBottom - tick.scaled + 4"
              text-anchor="end"
              class="bar-y-label"
            >{{ tick.label }}</text>
          </svg>
        </div>

        <div v-else-if="activeTab === 'pie'" class="pie-chart-wrap">
          <svg :viewBox="`0 0 ${pieSize} ${pieSize}`" class="chart-svg pie-svg">
            <g :transform="`translate(${pieSize / 2}, ${pieSize / 2})`">
              <path
                v-for="(slice, i) in pieData"
                :key="i"
                :d="slice.d"
                :fill="slice.color"
                stroke="#fff"
                stroke-width="2"
              />
              <text
                v-for="(slice, i) in pieData"
                :key="'label-' + i"
                :x="slice.labelX"
                :y="slice.labelY"
                text-anchor="middle"
                class="pie-label"
              >{{ slice.percent }}%</text>
            </g>
          </svg>
          <div class="pie-legend">
            <div v-for="(slice, i) in pieData" :key="i" class="legend-item">
              <span class="legend-dot" :style="{ background: slice.color }"></span>
              <span class="legend-text">{{ slice.rawLabel }} ({{ slice.count }})</span>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'table'" class="simple-table">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th v-for="col in displayColumns" :key="col">{{ columnLabel(col) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in results.slice(0, 20)" :key="idx">
                <td class="row-idx">{{ idx + 1 }}</td>
                <td v-for="col in displayColumns" :key="col">{{ row[col] ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'

const CHART_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#b37feb', '#36cfc9', '#ff85c0', '#ffc53d', '#73d13d']

const props = defineProps({
  results: { type: Array, default: () => [] },
  width: { type: Number, default: 800 },
  height: { type: Number, default: 400 },
})

const svgRef = ref(null)
const activeTab = ref('bar')

const chartTabs = [
  { key: 'bar', label: '柱状图' },
  { key: 'pie', label: '饼图' },
  { key: 'table', label: '表格' },
]

const isTripleFormat = (row) =>
  row.s || row.subject || row._subject ||
  row.p || row.predicate || row._predicate ||
  row.o || row.object || row._object

const isTableData = computed(() => {
  if (!props.results || props.results.length === 0) return false
  return !props.results.some(r => isTripleFormat(r))
})

const allColumns = computed(() => {
  if (!props.results || props.results.length === 0) return []
  return Object.keys(props.results[0])
})

const numericColumns = computed(() =>
  allColumns.value.filter(col =>
    props.results.some(row => typeof row[col] === 'number' || (!isNaN(parseFloat(row[col])) && row[col] !== ''))
  )
)

const displayColumns = computed(() => allColumns.value.filter(c => c !== 'url'))

const barTargetColumn = computed(() => numericColumns.value[0] || allColumns.value[0])
const pieTargetColumn = computed(() => {
  const nonNumeric = allColumns.value.find(c => !numericColumns.value.includes(c))
  return nonNumeric || allColumns.value[0]
})

const columnLabel = (col) => {
  const map = {
    productName: '产品名', productType: '产品类型', status: '状态', type: '类型',
    revenueGrowth: '收入增长', newUserMonth: '月新增', userChurnRate: '流失率',
    isZeroFee: '零资费', onlineMonths: '在售月数', price: '价格',
    targetMarketSize: '目标市场规模',
  }
  return map[col] || col
}

const barChartWidth = computed(() => Math.max(props.width, props.results.length * 80 + 100))
const barChartHeight = computed(() => props.height)
const barMarginLeft = 60
const barMarginBottom = 50
const barGroupWidth = computed(() => (barChartWidth.value - barMarginLeft - 20) / Math.min(props.results.length, 20))
const barGroupPadding = 4
const barRectWidth = computed(() => Math.max(barGroupWidth.value - barGroupPadding * 2, 12))

const barData = computed(() => {
  const col = barTargetColumn.value
  if (!col) return []
  const values = props.results.map(row => {
    const v = parseFloat(row[col])
    return isNaN(v) ? 0 : v
  })
  const maxVal = Math.max(...values, 1)
  const availableH = barChartHeight.value - barMarginBottom - 20
  return props.results.slice(0, 20).map((row, i) => {
    const val = values[i]
    const name = row.productName || row.name || row[col] || `#${i + 1}`
    return {
      label: String(name).length > 8 ? String(name).substring(0, 8) + '...' : String(name),
      value: val,
      scaledH: (val / maxVal) * availableH,
      color: CHART_COLORS[i % CHART_COLORS.length],
    }
  })
})

const yTicks = computed(() => {
  const maxVal = Math.max(...barData.value.map(d => d.value), 1)
  const availableH = barChartHeight.value - barMarginBottom - 20
  const tickCount = 4
  return Array.from({ length: tickCount + 1 }, (_, i) => {
    const val = (maxVal / tickCount) * i
    return {
      label: val % 1 === 0 ? String(val) : val.toFixed(2),
      scaled: (val / maxVal) * availableH,
    }
  })
})

const pieSize = 360
const pieRadius = 130

const pieData = computed(() => {
  const col = pieTargetColumn.value
  if (!col) return []
  const freq = {}
  props.results.forEach(row => {
    const val = String(row[col] ?? '未知')
    freq[val] = (freq[val] || 0) + 1
  })
  const entries = Object.entries(freq).sort((a, b) => b[1] - a[1])
  const total = entries.reduce((s, e) => s + e[1], 0) || 1
  let cumAngle = -Math.PI / 2
  return entries.map(([label, count], i) => {
    const sliceAngle = (count / total) * 2 * Math.PI
    const startAngle = cumAngle
    const endAngle = cumAngle + sliceAngle
    cumAngle = endAngle
    const midAngle = startAngle + sliceAngle / 2
    const largeArc = sliceAngle > Math.PI ? 1 : 0
    const x1 = pieRadius * Math.cos(startAngle)
    const y1 = pieRadius * Math.sin(startAngle)
    const x2 = pieRadius * Math.cos(endAngle)
    const y2 = pieRadius * Math.sin(endAngle)
    let d
    if (entries.length === 1) {
      d = `M0,${-pieRadius} A${pieRadius},${pieRadius} 0 1,1 -0.01,${-pieRadius} Z`
    } else {
      d = `M0,0 L${x1},${y1} A${pieRadius},${pieRadius} 0 ${largeArc},1 ${x2},${y2} Z`
    }
    const labelR = pieRadius * 0.65
    return {
      d,
      rawLabel: label,
      count,
      percent: Math.round((count / total) * 100),
      color: CHART_COLORS[i % CHART_COLORS.length],
      labelX: labelR * Math.cos(midAngle),
      labelY: labelR * Math.sin(midAngle),
    }
  })
})

const extractLabel = (iri) => {
  if (!iri) return ''
  const str = iri.replace(/["']/g, '')
  if (str.includes('#')) return str.split('#').pop()
  if (str.includes('/')) return str.split('/').pop()
  return str
}

const buildGraphData = (sparqlResults) => {
  const nodes = [], links = [], nodeMap = new Map()
  sparqlResults.forEach((row) => {
    const subject = row.s || row.subject || row._subject
    const predicate = row.p || row.predicate || row._predicate
    const objectVal = row.o || row.object || row._object
    if (subject && !nodeMap.has(subject)) {
      nodeMap.set(subject, { id: subject, label: extractLabel(subject), type: 'subject' })
      nodes.push(nodeMap.get(subject))
    }
    if (subject && predicate && objectVal) {
      if (!nodeMap.has(objectVal)) {
        const isLiteral = objectVal.startsWith('"') || objectVal.startsWith("'")
        nodeMap.set(objectVal, { id: objectVal, label: extractLabel(objectVal), type: isLiteral ? 'literal' : 'object' })
        nodes.push(nodeMap.get(objectVal))
      }
      links.push({ source: subject, target: objectVal, label: extractLabel(predicate) })
    }
  })
  return { nodes, links }
}

const renderGraph = (svg, graphData) => {
  const { width, height } = props
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`)
  svg.innerHTML = ''
  const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs')
  const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker')
  marker.setAttribute('id', 'arrowhead')
  marker.setAttribute('viewBox', '0 0 10 10')
  marker.setAttribute('refX', 10)
  marker.setAttribute('refY', 5)
  marker.setAttribute('markerWidth', 8)
  marker.setAttribute('markerHeight', 8)
  marker.setAttribute('orient', 'auto')
  const arrowPath = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  arrowPath.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z')
  arrowPath.setAttribute('fill', '#999')
  marker.appendChild(arrowPath)
  defs.appendChild(marker)
  svg.appendChild(defs)
  const centerX = width / 2, centerY = height / 2
  const radius = Math.min(width, height) * 0.35
  const positions = {}
  graphData.nodes.forEach((node, i) => {
    const angle = (2 * Math.PI * i) / graphData.nodes.length
    positions[node.id] = { x: centerX + radius * Math.cos(angle), y: centerY + radius * Math.sin(angle) }
  })
  graphData.links.forEach((link) => {
    const src = positions[link.source], tgt = positions[link.target]
    if (!src || !tgt) return
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line')
    line.setAttribute('x1', src.x); line.setAttribute('y1', src.y)
    line.setAttribute('x2', tgt.x); line.setAttribute('y2', tgt.y)
    line.setAttribute('stroke', '#999'); line.setAttribute('stroke-width', 1.5)
    line.setAttribute('marker-end', 'url(#arrowhead)')
    svg.appendChild(line)
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', (src.x + tgt.x) / 2)
    text.setAttribute('y', (src.y + tgt.y) / 2 - 6)
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('fill', '#666')
    text.setAttribute('font-size', '11')
    text.textContent = link.label
    svg.appendChild(text)
  })
  const colorMap = { subject: '#409eff', object: '#67c23a', literal: '#e6a23c' }
  graphData.nodes.forEach((node) => {
    const pos = positions[node.id]
    if (!pos) return
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
    circle.setAttribute('cx', pos.x); circle.setAttribute('cy', pos.y); circle.setAttribute('r', 24)
    circle.setAttribute('fill', colorMap[node.type] || '#909399')
    circle.setAttribute('stroke', '#fff'); circle.setAttribute('stroke-width', 2)
    svg.appendChild(circle)
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', pos.x); text.setAttribute('y', pos.y + 40)
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('fill', '#333'); text.setAttribute('font-size', '12')
    text.textContent = node.label
    svg.appendChild(text)
  })
}

const resetGraph = () => {
  if (svgRef.value) {
    renderGraph(svgRef.value, buildGraphData(props.results))
  }
}

watch(() => props.results, async (newResults) => {
  if (newResults && newResults.length > 0 && !isTableData.value && svgRef.value) {
    await nextTick()
    renderGraph(svgRef.value, buildGraphData(newResults))
  }
}, { deep: true })

onMounted(async () => {
  if (props.results && props.results.length > 0 && !isTableData.value && svgRef.value) {
    await nextTick()
    renderGraph(svgRef.value, buildGraphData(props.results))
  }
})
</script>

<style scoped>
.sparql-result-graph { width: 100%; }
.empty-state { display: flex; justify-content: center; align-items: center; min-height: 200px; }
.graph-container { width: 100%; overflow: auto; }
.graph-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; }
.result-count { font-size: 13px; color: #606266; }
.chart-tabs { display: flex; gap: 4px; }
.tab-btn { background: #f5f7fa; border: 1px solid #e4e7ed; color: #606266; cursor: pointer; padding: 4px 12px; border-radius: 6px; font-size: 12px; transition: all 0.2s; }
.tab-btn.active { background: #409eff; color: #fff; border-color: #409eff; }
.tab-btn:hover:not(.active) { background: #ecf5ff; color: #409eff; }
.graph-svg { width: 100%; min-height: 400px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; }
.chart-area { border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; padding: 16px; min-height: 400px; }
.chart-svg { width: 100%; }
.bar-x-label { fill: #606266; font-size: 11px; }
.bar-value { fill: #303133; font-size: 11px; font-weight: 600; }
.bar-y-label { fill: #909399; font-size: 10px; }
.pie-svg { max-width: 400px; margin: 0 auto; display: block; }
.pie-label { fill: #fff; font-size: 11px; font-weight: 600; pointer-events: none; }
.pie-legend { display: flex; flex-wrap: wrap; gap: 8px 16px; justify-content: center; margin-top: 12px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #606266; }
.legend-dot { width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0; }
.simple-table { overflow-x: auto; }
.simple-table table { width: 100%; border-collapse: collapse; font-size: 12px; }
.simple-table th { background: #f5f7fa; color: #909399; font-weight: 600; padding: 8px 10px; text-align: left; border-bottom: 2px solid #ebeef5; white-space: nowrap; }
.simple-table td { padding: 7px 10px; border-bottom: 1px solid #f2f3f5; color: #303133; white-space: nowrap; }
.simple-table tr:hover td { background: #f5f7fa; }
.row-idx { color: #909399; text-align: center; width: 36px; }
</style>

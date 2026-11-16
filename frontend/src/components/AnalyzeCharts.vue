<template>
  <div class="analyze-charts">
    <!-- 趋势层：双折线图 -->
    <div v-if="type === 'trend'" ref="chartEl" class="chart-box"></div>

    <!-- 结构层：双柱状图 -->
    <div v-else-if="type === 'structure'" class="chart-pair">
      <div ref="chartEl1" class="chart-box half"></div>
      <div ref="chartEl2" class="chart-box half"></div>
    </div>

    <!-- 竞品层：雷达图 -->
    <div v-else-if="type === 'competitor'" ref="chartEl" class="chart-box"></div>

    <!-- 根因层：饼图 -->
    <div v-else-if="type === 'rootcause'" ref="chartEl" class="chart-box"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  type: { type: String, required: true },
})

const chartEl = ref(null)
const chartEl1 = ref(null)
const chartEl2 = ref(null)
let charts = []

const trendOption = {
  title: { text: '近14天新增订购 vs 退订趋势', left: 'center', textStyle: { fontSize: 13, fontWeight: 600 } },
  tooltip: { trigger: 'axis' },
  legend: { data: ['新增订购', '退订'], bottom: 0, textStyle: { fontSize: 11 } },
  grid: { top: 40, left: 50, right: 20, bottom: 40 },
  xAxis: {
    type: 'category',
    data: ['7/15', '7/16', '7/17', '7/18', '7/19', '7/20', '7/21', '7/22', '7/23', '7/24', '7/25', '7/26', '7/27', '7/28'],
    axisLabel: { fontSize: 10, rotate: 35 },
  },
  yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
  series: [
    {
      name: '新增订购',
      type: 'line',
      data: [265, 258, 249, 237, 228, 215, 208, 195, 182, 170, 162, 175, 168, 177],
      smooth: true,
      itemStyle: { color: '#3b82f6' },
      lineStyle: { width: 2 },
      markLine: {
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#94a3b8' },
        data: [{ yAxis: 237, name: '前7日均值' }],
        label: { formatter: '前7日均值 237', fontSize: 10, color: '#64748b' },
      },
    },
    {
      name: '退订',
      type: 'line',
      data: [78, 81, 76, 79, 74, 82, 77, 82, 79, 85, 80, 76, 88, 84],
      smooth: true,
      itemStyle: { color: '#ef4444' },
      lineStyle: { width: 2 },
    },
  ],
}

const customerOption = {
  title: { text: '客群占比变化', left: 'center', textStyle: { fontSize: 12, fontWeight: 600 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['前30日', '近7日'], bottom: 0, textStyle: { fontSize: 10 } },
  grid: { top: 35, left: 45, right: 15, bottom: 35 },
  xAxis: {
    type: 'category',
    data: ['18-25岁', '26-35岁', '36-45岁', '46岁+'],
    axisLabel: { fontSize: 10 },
  },
  yAxis: { type: 'value', axisLabel: { fontSize: 10, formatter: '{value}%' }, max: 50 },
  series: [
    { name: '前30日', type: 'bar', data: [31, 41, 22, 6], itemStyle: { color: '#93c5fd' } },
    { name: '近7日', type: 'bar', data: [18, 43, 27, 12], itemStyle: { color: '#2563eb' } },
  ],
}

const channelOption = {
  title: { text: '渠道贡献变化', left: 'center', textStyle: { fontSize: 12, fontWeight: 600 } },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['前30日', '近7日'], bottom: 0, textStyle: { fontSize: 10 } },
  grid: { top: 35, left: 45, right: 15, bottom: 50 },
  xAxis: {
    type: 'category',
    data: ['APP', '抖音/快手', '线下营业厅', '第三方合作', '10086外呼'],
    axisLabel: { fontSize: 9, rotate: 20 },
  },
  yAxis: { type: 'value', axisLabel: { fontSize: 10, formatter: '{value}%' }, max: 55 },
  series: [
    { name: '前30日', type: 'bar', data: [48, 27, 18, 5, 2], itemStyle: { color: '#86efac' } },
    { name: '近7日', type: 'bar', data: [44, 18, 22, 10, 6], itemStyle: { color: '#16a34a' } },
  ],
}

const competitorOption = {
  title: { text: '竞品五维对比', left: 'center', textStyle: { fontSize: 13, fontWeight: 600 } },
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle: { fontSize: 10 } },
  radar: {
    indicator: [
      { name: '资费成本(越低越好)', max: 100 },
      { name: '高速配额(GB)', max: 100 },
      { name: '有效期(天)', max: 100 },
      { name: '漫游覆盖', max: 100 },
      { name: '网络速率', max: 100 },
    ],
    radius: '60%',
    splitNumber: 4,
    axisName: { fontSize: 10, color: '#64748b' },
  },
  series: [
    {
      type: 'radar',
      data: [
        { value: [60, 45, 50, 50, 80], name: '本产品', itemStyle: { color: '#3b82f6' }, areaStyle: { opacity: 0.15 } },
        { value: [60, 20, 5, 100, 60], name: '友商A（游讯1日卡）', itemStyle: { color: '#f59e0b' }, areaStyle: { opacity: 0.1 } },
        { value: [80, 25, 50, 50, 80], name: '友商B（NiceTrip 15日）', itemStyle: { color: '#ef4444' }, areaStyle: { opacity: 0.1 } },
        { value: [60, 100, 50, 0, 80], name: '友商C（大湾区伴游30日）', itemStyle: { color: '#10b981' }, areaStyle: { opacity: 0.1 } },
      ],
    },
  ],
}

const rootcauseOption = {
  title: { text: '根因贡献度拆解', left: 'center', textStyle: { fontSize: 13, fontWeight: 600 } },
  tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
  legend: { type: 'scroll', orient: 'vertical', right: 10, top: 'center', textStyle: { fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['35%', '60%'],
      center: ['38%', '55%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 10 },
      labelLine: { length: 8, length2: 6 },
      data: [
        { value: 50, name: '高速配额落后竞品均值29%', itemStyle: { color: '#ef4444' } },
        { value: 20, name: '年轻客群被截流', itemStyle: { color: '#f59e0b' } },
        { value: 15, name: '漫游覆盖受限', itemStyle: { color: '#fbbf24' } },
        { value: 10, name: '友商B降价竞争', itemStyle: { color: '#3b82f6' } },
        { value: 5, name: '其他(季节性等)', itemStyle: { color: '#94a3b8' } },
      ],
    },
  ],
}

const getOption = () => {
  switch (props.type) {
    case 'trend': return trendOption
    case 'competitor': return competitorOption
    case 'rootcause': return rootcauseOption
    default: return null
  }
}

const initChart = (el, option) => {
  if (!el || !option) return null
  const chart = echarts.init(el)
  chart.setOption(option)
  return chart
}

onMounted(async () => {
  await nextTick()
  if (props.type === 'structure') {
    charts.push(initChart(chartEl1.value, customerOption))
    charts.push(initChart(chartEl2.value, channelOption))
  } else {
    charts.push(initChart(chartEl.value, getOption()))
  }
  window.addEventListener('resize', handleResize)
})

const handleResize = () => {
  charts.forEach((c) => c && c.resize())
}

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((c) => c && c.dispose())
  charts = []
})
</script>

<style scoped>
.analyze-charts {
  width: 100%;
}

.chart-box {
  width: 100%;
  height: 280px;
  margin: 12px 0;
}

.chart-box.half {
  height: 240px;
}

.chart-pair {
  display: flex;
  gap: 12px;
  width: 100%;
}

.chart-pair .half {
  flex: 1;
  min-width: 0;
}
</style>

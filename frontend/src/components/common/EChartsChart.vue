<template>
  <div ref="chartRef" class="echarts-chart" :style="{ height: typeof height === 'number' ? height + 'px' : height }"></div>
</template>

<script setup>
/**
 * EChartsChart - ECharts 轻封装
 *
 * 迁移自 chat-skeleton 原型 EChartsChart.vue：
 * - option deep watch 重渲染
 * - ResizeObserver 自适应
 * - 零尺寸兜底（ensureSized）：面板/抽屉刚打开时容器无尺寸，nextTick + 延迟二次 resize
 * - 卸载时 dispose + 断开 observer
 */
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, default: null },
  height: { type: [Number, String], default: 260 },
})

const chartRef = ref(null)
let chart = null
let resizeObserver = null
let ensureTimer = null

function renderChart() {
  if (!chart || !props.option) return
  chart.setOption(props.option, true)
}

function resizeChart() {
  chart?.resize()
}

/** 零尺寸兜底：首次挂载时容器可能无尺寸，延迟二次 resize */
function ensureSized() {
  nextTick(() => {
    resizeChart()
    if (ensureTimer) clearTimeout(ensureTimer)
    ensureTimer = setTimeout(() => {
      resizeChart()
      renderChart()
    }, 80)
  })
}

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
  resizeObserver = new ResizeObserver(() => resizeChart())
  resizeObserver.observe(chartRef.value)
  ensureSized()
})

onUnmounted(() => {
  if (ensureTimer) clearTimeout(ensureTimer)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})

watch(() => props.option, () => {
  renderChart()
  ensureSized()
}, { deep: true })

defineExpose({ resize: resizeChart, getChart: () => chart })
</script>

<style scoped>
.echarts-chart { width: 100%; min-height: 120px; }
</style>

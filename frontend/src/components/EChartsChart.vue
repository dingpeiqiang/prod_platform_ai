<template>
  <div ref="chartEl" class="echarts-container" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: Number, default: 320 },
})

const chartEl = ref(null)
let chartInstance = null
let resizeObserver = null
let sizeTimer = null

const renderChart = () => {
  if (!chartEl.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartEl.value)
  }
  chartInstance.setOption(props.option, true)
}

const handleResize = () => {
  chartInstance?.resize()
}

// 若容器在初始化时尺寸为 0（例如面板刚打开/仍在布局），延迟重设以确保图表可见
const ensureSized = () => {
  nextTick(() => {
    chartInstance?.resize()
    sizeTimer = setTimeout(() => {
      chartInstance?.resize()
      renderChart()
    }, 80)
  })
}

onMounted(() => {
  nextTick(() => {
    renderChart()
    ensureSized()
    if (typeof ResizeObserver !== 'undefined' && chartEl.value) {
      resizeObserver = new ResizeObserver(handleResize)
      resizeObserver.observe(chartEl.value)
    }
  })
})

watch(
  () => props.option,
  () => renderChart(),
  { deep: true }
)

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  clearTimeout(sizeTimer)
  sizeTimer = null
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.echarts-container {
  width: 100%;
  min-height: 200px;
}
</style>

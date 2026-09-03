<template>
  <div class="ops-view">
    <!-- ============ 模块一：收入总览 ============ -->
    <OpsRevenueCards />

    <!-- ============ 模块二：重点产品运营 ============ -->
    <div class="ov-panel ov-prod-panel">
      <div class="ov-prod-titlebar">
        <div class="ov-prod-titlebar-left">
          <h3 class="ov-title">重点产品运营</h3>
          <button
            v-if="anomalyAlert.count > 0"
            type="button"
            class="ov-alert-badge"
            :title="`${anomalyAlert.count} 个产品处于异动状态，点击定位到 5G新通话 下的畅享59元5G套餐`"
            @click="onAlertLocate"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            <span class="ov-alert-count">{{ anomalyAlert.count }}</span>
          </button>
        </div>
        <div class="ov-prod-search">
          <input v-model="productKeyword" type="text" placeholder="搜索产品名称" autocomplete="off" />
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </div>
      </div>
      <div class="ov-prod-tabs">
        <button
          v-for="p in visibleProducts"
          :key="p.key"
          type="button"
          class="ov-prod-tab"
          :class="{ active: activeProduct === p.key }"
          @click="activeProduct = p.key"
        >{{ p.name }}</button>
        <div v-if="!visibleProducts.length" class="ov-prod-empty">未找到相关产品</div>
      </div>

      <div v-if="activeProduct" class="ov-prod-body">
        <template v-for="prod in activeProductData" :key="prod.key">
          <OpsProductSection :prod="prod" />

          <!-- 下钻至产品：仅 5G新通话 / 上架产品 显示，可折叠 -->
          <OpsDrillPanel
            v-if="drillableProduct(prod.key)"
            :ref="captureDrillWrap"
            :open="wxthDrillOpen"
            :drillKeys="drillKeys"
            :lookup="drillLookup"
            :highlightKey="highlightDrillKey"
            :activeKey="wxthDrillActive"
            :current="currentDrill"
            :dims="currentDrillDims"
            :total="currentDrillTotal"
            :level="currentDrillLevel"
            :productTotal="productTotal"
            :productLevel="productLevel"
            @toggle="wxthDrillOpen = !wxthDrillOpen"
            @select="(k) => (wxthDrillActive = k)"
            @open-rootcause="(k) => emit('open-rootcause', k)"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import OpsRevenueCards from './OpsRevenueCards.vue'
import OpsProductSection from './OpsProductSection.vue'
import OpsDrillPanel from './OpsDrillPanel.vue'
import {
  products, productKeys, wxthDrill, wxthDrillKeys, drillableProduct,
  dimMeta, opsDrillState,
} from './opsData.js'
import { computeDims, computeTotal, computeLevel } from './opsFormat.js'

const emit = defineEmits(['open-rootcause', 'update:drill'])

// ============ 交互状态 ============
const activeProduct = ref('jtkd')
const productKeyword = ref('')

const visibleProducts = computed(() => {
  const kw = (productKeyword.value || '').trim().toLowerCase()
  const list = productKeys.map((k) => products[k])
  if (!kw) return list
  return list.filter((p) => p.name.toLowerCase().includes(kw))
})

const activeProductData = computed(() => {
  const list = visibleProducts.value
  if (!list.length) return []
  if (!list.some((p) => p.key === activeProduct.value)) {
    activeProduct.value = list[0].key
  }
  return list.filter((p) => p.key === activeProduct.value)
})

// ============ 单产品下钻交互状态 ============
const wxthDrillOpen = ref(true)
const wxthDrillActive = ref(null)

// ============ 动态下钻套餐：当前工程暂无「已上架/销售中」工单落库链路 ============
// 原型中由 workOrderStore.archived 驱动动态纳入刚上架产品；
// 当前工程上架渠道数据未持久化，故仅保留静态十套餐，待后端补齐 salesAt 语义后再接入。
const dynamicDrillEntries = computed(() => [])

const drillEntries = computed(() => [
  ...wxthDrillKeys.map((k) => wxthDrill[k]),
  ...dynamicDrillEntries.value,
])

const drillLookup = computed(() => {
  const map = {}
  wxthDrillKeys.forEach((k) => { map[k] = wxthDrill[k] })
  dynamicDrillEntries.value.forEach((d) => { map[d.key] = d })
  return map
})

const drillKeys = computed(() => [
  ...wxthDrillKeys,
  ...dynamicDrillEntries.value.map((d) => d.key),
])

// ============ 异动预警（重点产品运营标题旁） ============
// 预警来源：5G新通话 下钻中处于"亚健康"状态的代表性套餐（排除刚上架、数据积累中的产品）
const anomalyAlert = computed(() => {
  const keys = wxthDrillKeys.filter(
    (k) => !drillLookup.value[k]?.launched && drillLookup.value[k]?.healthLevel === '亚健康'
  )
  return { count: keys.length, keys }
})
const highlightDrillKey = ref(null)
// 点击预警图标：切到 5G新通话 tab、展开下钻、定位并高亮"亚健康"套餐卡片
const onAlertLocate = () => {
  if (!anomalyAlert.value.keys.length) return
  const target = anomalyAlert.value.keys[0]
  activeProduct.value = 'wxth'
  wxthDrillOpen.value = true
  wxthDrillActive.value = null
  highlightDrillKey.value = target
  nextTick(() => {
    scrollToDrill()
    clearTimeout(onAlertLocate._t)
    onAlertLocate._t = setTimeout(() => {
      highlightDrillKey.value = null
    }, 3000)
  })
}

// 恢复运营视图时，需同时回到「5G新通话」业务 tab，否则下钻模块（v-if=drillableProduct）不显示
const restoreProductKey = opsDrillState.key
if (restoreProductKey) {
  wxthDrillActive.value = restoreProductKey
  activeProduct.value = 'wxth'
}

// 下钻模块元素引用（用于重新打开时自动定位滚动）
// captureDrillWrap 由模板 :ref 绑定（OpsDrillPanel 为组件，需取 $el）
let drillWrapEl = null
const captureDrillWrap = (el) => {
  drillWrapEl = el?.$el || el || null
}
// 异动定位滚动：仅在本视图自身的滚动容器内滚动，避免 scrollIntoView 联动外层祖先导致页面错位
const scrollToDrill = () => {
  if (!drillWrapEl) return
  const scroller = drillWrapEl.closest('.ops-view')
  if (!scroller) return
  const top = drillWrapEl.offsetTop - scroller.getBoundingClientRect().top - 8
  scroller.scrollTo({ top, behavior: 'smooth' })
}
onMounted(() => {
  if (restoreProductKey) {
    nextTick(scrollToDrill)
  }
})

watch(wxthDrillActive, (v) => {
  opsDrillState.key = v
  emit('update:drill', v)
})
// 切换业务 tab 时重置下钻状态
watch(activeProduct, () => {
  wxthDrillActive.value = null
})

const currentDrill = computed(() =>
  wxthDrillActive.value ? drillLookup.value[wxthDrillActive.value] || null : null
)
// 供套餐列表层与单产品层共用同一套分数，保证一致性
const productTotal = (key) => computeTotal(computeDims(drillLookup.value[key], dimMeta))
const productLevel = (key) => computeLevel(productTotal(key))

const currentDrillDims = computed(() => computeDims(currentDrill.value, dimMeta))
const currentDrillTotal = computed(() => computeTotal(currentDrillDims.value))
const currentDrillLevel = computed(() => computeLevel(currentDrillTotal.value))

defineExpose({ captureDrillWrap })
</script>

<style src="./opsView.css" scoped></style>

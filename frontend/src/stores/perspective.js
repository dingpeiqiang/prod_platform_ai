import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

const LS_PERSPECTIVE_KEY = 'ops.perspective.v1'

/**
 * 运营视角 store（产品运营助手全局视角）
 *
 * 定位：把「平台运营 / 代理商运营」提升为运营助手的全局视角切换——
 * 同一套数据底座，切换视角后运营视图的数据范围、口径与对话场景随之变化。
 *
 * - platform：联通国际（平台）视角 —— 管理代理商 / 地区 / 套餐
 * - agent：代理商（一级总代）视角 —— 管理个人用户 / 套餐 / 用量
 */

export const OPS_PERSPECTIVES = [
  {
    key: 'platform',
    label: '平台运营',
    short: '平台',
    desc: '联通国际视角：管理代理商 / 地区 / 套餐',
    defaultTab: 'trend',
  },
  {
    key: 'agent',
    label: '代理商运营',
    short: '代理商',
    desc: '代理商视角：管理个人用户 / 套餐 / 用量',
    defaultTab: 'customer',
  },
]

function loadPerspective() {
  try {
    const v = localStorage.getItem(LS_PERSPECTIVE_KEY)
    return OPS_PERSPECTIVES.some((p) => p.key === v) ? v : 'platform'
  } catch (e) {
    return 'platform'
  }
}

export const usePerspectiveStore = defineStore('perspective', () => {
  const opsPerspective = ref(loadPerspective())

  const opsPerspectiveMeta = computed(
    () => OPS_PERSPECTIVES.find((p) => p.key === opsPerspective.value) || OPS_PERSPECTIVES[0]
  )

  // 该视角下运营分析默认 Tab（平台→流量趋势 / 代理商→个人用户）
  const opsDefaultTab = computed(() => opsPerspectiveMeta.value.defaultTab)

  // 视角切换信号：递增，供需要"响应视角变化"的组件 watch（如重置 Tab、刷新对话场景）
  const opsPerspectiveSeq = ref(0)

  function setOpsPerspective(key) {
    if (!OPS_PERSPECTIVES.some((p) => p.key === key)) return
    if (opsPerspective.value === key) return
    opsPerspective.value = key
    opsPerspectiveSeq.value += 1
  }

  function toggleOpsPerspective() {
    setOpsPerspective(opsPerspective.value === 'platform' ? 'agent' : 'platform')
  }

  watch(opsPerspective, (v) => {
    try {
      localStorage.setItem(LS_PERSPECTIVE_KEY, v)
    } catch (e) { /* ignore quota/security errors */ }
  })

  return {
    opsPerspective,
    opsPerspectiveMeta,
    opsDefaultTab,
    opsPerspectiveSeq,
    setOpsPerspective,
    toggleOpsPerspective,
  }
})

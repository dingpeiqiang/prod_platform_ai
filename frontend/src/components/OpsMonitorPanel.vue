/**
 * 运营监控：异动告警 + 处置工单状态流转
 */
<template>
  <aside v-if="visible" class="ops-panel monitor-panel">
    <header class="ops-panel-head">
      <div>
        <h3>运营监控</h3>
        <p>
          <template v-if="tab === 'alerts'">异动告警 {{ items.length }} 条 · {{ formatTime(generatedAt) }}</template>
          <template v-else>
            工单 {{ filteredWorkOrders.length }} 条
            · 待处理 {{ counts.open || 0 }} / 处理中 {{ counts.in_progress || 0 }} / 完成 {{ counts.done || 0 }}
          </template>
        </p>
      </div>
      <button type="button" class="close-btn" @click="$emit('close')">×</button>
    </header>

    <div class="ops-panel-body">
      <section class="tabs">
        <button type="button" :class="{ active: tab === 'alerts' }" @click="tab = 'alerts'">告警</button>
        <button type="button" :class="{ active: tab === 'orders' }" @click="tab = 'orders'">处置工单</button>
      </section>

      <!-- 告警 Tab -->
      <template v-if="tab === 'alerts'">
        <section class="toolbar">
          <div class="filters">
            <button
              v-for="f in alertFilters"
              :key="f.key"
              type="button"
              :class="{ active: filter === f.key }"
              @click="filter = f.key"
            >
              {{ f.label }}
            </button>
          </div>
          <button type="button" class="re-btn" :disabled="loading" @click="$emit('refresh')">刷新</button>
        </section>

        <p v-if="loading" class="empty">告警加载中…</p>
        <p v-else-if="!filteredItems.length" class="empty">暂无异动告警</p>

        <section class="list">
          <article
            v-for="item in filteredItems"
            :key="item.id"
            class="alert-card"
            :class="{ active: activeId === item.id, high: item.severity === 'HIGH' }"
            @click="activeId = item.id"
          >
            <div class="alert-top">
              <span class="tag" :class="item.tag">{{ item.tag || '异动' }}</span>
              <span class="sev" :class="item.severity">{{ item.severity || 'MEDIUM' }}</span>
            </div>
            <strong>{{ item.offeringName || item.offeringId || '系统告警' }}</strong>
            <p class="text">{{ item.text }}</p>
            <p class="meta">
              <template v-if="item.metricCode">指标 {{ item.metricCode }} · </template>
              {{ formatTime(item.occurredAt) }}
            </p>
            <div class="row-actions">
              <button
                v-if="item.type === 'anomaly' && item.offeringId"
                type="button"
                class="primary-btn"
                @click.stop="$emit('analyze', item)"
              >
                智能归因
              </button>
              <button
                v-else-if="item.type === 'risk'"
                type="button"
                class="primary-btn"
                @click.stop="$emit('open-risk', item)"
              >
                打开风险稽核
              </button>
            </div>
          </article>
        </section>

        <section v-if="activeItem" class="detail">
          <h4>告警详情</h4>
          <ul class="kv">
            <li>商品：{{ activeItem.offeringName }}（{{ activeItem.offeringId || '—' }}）</li>
            <li>描述：{{ activeItem.text }}</li>
            <li v-if="activeItem.metricDelta != null">
              环比：{{ Math.round(Number(activeItem.metricDelta) * 100) }}%
            </li>
            <li>建议动作：{{ activeItem.actionText || '下钻归因' }}</li>
          </ul>
        </section>
      </template>

      <!-- 工单 Tab -->
      <template v-else>
        <section class="toolbar">
          <div class="filters">
            <button
              v-for="f in woFilters"
              :key="f.key"
              type="button"
              :class="{ active: woFilter === f.key }"
              @click="woFilter = f.key"
            >
              {{ f.label }}
              <small v-if="f.key !== 'all'">{{ counts[f.key] || 0 }}</small>
            </button>
          </div>
          <button type="button" class="re-btn" :disabled="woLoading" @click="reloadOrders">刷新</button>
        </section>

        <p v-if="woLoading" class="empty">工单加载中…</p>
        <p v-else-if="!filteredWorkOrders.length" class="empty">暂无处置工单</p>

        <section class="list">
          <article
            v-for="wo in filteredWorkOrders"
            :key="wo.workOrderId"
            class="wo-card"
            :class="{ active: activeWoId === wo.workOrderId }"
            @click="activeWoId = wo.workOrderId"
          >
            <div class="alert-top">
              <strong class="wo-id">{{ wo.workOrderId }}</strong>
              <span class="status-pill" :class="wo.status">{{ statusCn(wo.status) }}</span>
            </div>
            <p class="text">{{ wo.title }}</p>
            <p class="meta">{{ wo.offeringName || wo.offeringId }} · {{ formatTime(wo.createdAt) }}</p>
            <div class="row-actions" @click.stop>
              <button
                v-if="wo.status === 'open'"
                type="button"
                class="primary-btn"
                :disabled="busyId === wo.workOrderId"
                @click="advance(wo, 'in_progress')"
              >
                开始处理
              </button>
              <button
                v-if="wo.status === 'in_progress' || wo.status === 'open'"
                type="button"
                class="primary-btn"
                :disabled="busyId === wo.workOrderId"
                @click="advance(wo, 'done')"
              >
                完成闭环
              </button>
              <button
                v-if="wo.status === 'open' || wo.status === 'in_progress'"
                type="button"
                class="ghost-btn"
                :disabled="busyId === wo.workOrderId"
                @click="advance(wo, 'cancelled')"
              >
                取消
              </button>
              <button
                v-if="wo.status === 'done' || wo.status === 'cancelled'"
                type="button"
                class="ghost-btn"
                :disabled="busyId === wo.workOrderId"
                @click="advance(wo, 'open')"
              >
                重开
              </button>
            </div>
          </article>
        </section>

        <section v-if="activeWo" class="detail">
          <h4>工单详情</h4>
          <ul class="kv">
            <li>标题：{{ activeWo.title }}</li>
            <li>摘要：{{ activeWo.summary || '—' }}</li>
            <li>来源：{{ activeWo.source || '—' }}</li>
            <li v-if="activeWo.actions?.length">
              动作：{{ (activeWo.actions || []).join('；') }}
            </li>
            <li v-if="activeWo.statusHistory?.length">
              流转：
              {{
                activeWo.statusHistory
                  .map((h) => `${statusCn(h.from)}→${statusCn(h.to)}`)
                  .join(' · ')
              }}
            </li>
          </ul>
        </section>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { listWorkOrders, updateWorkOrderStatus } from '../services/productOntologyApi.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  result: { type: Object, default: null },
  workOrders: { type: Array, default: () => [] },
})

const emit = defineEmits(['close', 'refresh', 'analyze', 'open-risk', 'work-orders-updated'])

const tab = ref('alerts')
const filter = ref('all')
const woFilter = ref('all')
const activeId = ref('')
const activeWoId = ref('')
const localOrders = ref([])
const counts = ref({ open: 0, in_progress: 0, done: 0, cancelled: 0 })
const woLoading = ref(false)
const busyId = ref('')

const alertFilters = [
  { key: 'all', label: '全部' },
  { key: 'anomaly', label: '异动' },
  { key: 'HIGH', label: '高优先级' },
  { key: 'risk', label: '风险汇总' },
]

const woFilters = [
  { key: 'all', label: '全部' },
  { key: 'open', label: '待处理' },
  { key: 'in_progress', label: '处理中' },
  { key: 'done', label: '已完成' },
]

const items = computed(() => props.result?.items || props.result?.alerts || [])
const generatedAt = computed(() => props.result?.generatedAt || props.result?.lastAuditAt)

const filteredItems = computed(() => {
  const list = items.value
  if (filter.value === 'all') return list
  if (filter.value === 'HIGH') return list.filter((i) => i.severity === 'HIGH')
  return list.filter((i) => i.type === filter.value)
})

const activeItem = computed(() => items.value.find((i) => i.id === activeId.value))

const filteredWorkOrders = computed(() => {
  const list = localOrders.value
  if (woFilter.value === 'all') return list
  return list.filter((w) => w.status === woFilter.value)
})

const activeWo = computed(() => localOrders.value.find((w) => w.workOrderId === activeWoId.value))

watch(
  () => props.result,
  (r) => {
    const list = r?.items || r?.alerts || []
    activeId.value = list.find((i) => i.severity === 'HIGH')?.id || list[0]?.id || ''
  },
  { immediate: true },
)

watch(
  () => props.workOrders,
  (list) => {
    if (Array.isArray(list)) {
      localOrders.value = [...list]
      if (!activeWoId.value && list.length) activeWoId.value = list[0].workOrderId
    }
  },
  { immediate: true, deep: true },
)

watch(
  () => props.visible,
  (v) => {
    if (v) reloadOrders()
  },
)

function statusCn(s) {
  return (
    {
      open: '待处理',
      in_progress: '处理中',
      done: '已完成',
      cancelled: '已取消',
    }[s] || s || '—'
  )
}

function formatTime(iso) {
  if (!iso) return '刚刚'
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

async function reloadOrders() {
  woLoading.value = true
  try {
    const resp = await listWorkOrders()
    localOrders.value = resp?.items || []
    counts.value = resp?.counts || { open: 0, in_progress: 0, done: 0, cancelled: 0 }
    emit('work-orders-updated', localOrders.value)
  } catch (e) {
    console.warn('[OpsMonitor] reload orders failed', e)
  } finally {
    woLoading.value = false
  }
}

async function advance(wo, status) {
  if (!wo?.workOrderId || busyId.value) return
  busyId.value = wo.workOrderId
  try {
    const resp = await updateWorkOrderStatus(wo.workOrderId, status)
    const updated = resp?.workOrder || resp?.data?.workOrder
    if (updated) {
      const idx = localOrders.value.findIndex((w) => w.workOrderId === updated.workOrderId)
      if (idx >= 0) localOrders.value.splice(idx, 1, updated)
      else localOrders.value.unshift(updated)
      activeWoId.value = updated.workOrderId
    }
    await reloadOrders()
  } catch (e) {
    console.warn('[OpsMonitor] update status failed', e)
  } finally {
    busyId.value = ''
  }
}
</script>

<style scoped>
.ops-panel {
  width: 400px;
  flex-shrink: 0;
  border-left: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  max-height: 100%;
}
.ops-panel-head {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.ops-panel-head h3 {
  margin: 0;
  font-size: 15px;
}
.ops-panel-head p {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}
.close-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: #94a3b8;
}
.ops-panel-body {
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.tabs button {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 12px;
  cursor: pointer;
}
.tabs button.active {
  background: #0369a1;
  color: #fff;
  border-color: #0369a1;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}
.filters {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.filters button,
.re-btn,
.primary-btn,
.ghost-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}
.filters button.active {
  background: #0369a1;
  color: #fff;
  border-color: #0369a1;
}
.filters button small {
  margin-left: 4px;
  opacity: 0.85;
}
.primary-btn {
  background: #0f766e;
  color: #fff;
  border-color: #0f766e;
  margin-top: 6px;
}
.ghost-btn {
  margin-top: 6px;
  color: #64748b;
}
.primary-btn:disabled,
.ghost-btn:disabled,
.re-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 360px;
  overflow: auto;
}
.alert-card,
.detail,
.wo-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
}
.alert-card,
.wo-card {
  cursor: pointer;
}
.alert-card.active,
.alert-card:hover,
.wo-card.active,
.wo-card:hover {
  border-color: #0ea5e9;
}
.alert-card.high {
  border-left: 3px solid #dc2626;
}
.alert-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  align-items: center;
  gap: 8px;
}
.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #e0f2fe;
  color: #0369a1;
}
.tag.风险 {
  background: #fee2e2;
  color: #b91c1c;
}
.sev {
  font-size: 11px;
  color: #64748b;
}
.sev.HIGH {
  color: #b91c1c;
  font-weight: 600;
}
.wo-id {
  font-size: 12px;
  color: #0f766e;
}
.status-pill {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}
.status-pill.open {
  background: #ffedd5;
  color: #c2410c;
}
.status-pill.in_progress {
  background: #e0f2fe;
  color: #0369a1;
}
.status-pill.done {
  background: #dcfce7;
  color: #15803d;
}
.status-pill.cancelled {
  background: #f1f5f9;
  color: #64748b;
}
.text,
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #475569;
}
.meta {
  color: #94a3b8;
  font-size: 11px;
}
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.empty {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
.detail h4 {
  margin: 0 0 8px;
  font-size: 13px;
}
.kv {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  color: #334155;
}
</style>

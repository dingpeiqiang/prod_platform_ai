<template>
  <teleport to="body">
    <transition name="woc-slide">
      <div v-if="modelValue" class="woc-mask" @click.self="$emit('update:modelValue', false)">
        <div class="woc-panel">
          <header class="woc-head">
            <span class="woc-title">工单中心</span>
            <button class="woc-close" @click="$emit('update:modelValue', false)">✕</button>
          </header>

          <div class="woc-toolbar">
            <input
              v-model="keyword"
              class="woc-search"
              type="text"
              placeholder="搜索商品名称 / 工单号"
            />
            <div class="woc-tabs">
              <button
                v-for="tab in statusTabs"
                :key="tab.key"
                class="woc-tab"
                :class="{ active: activeTab === tab.key }"
                @click="activeTab = tab.key"
              >
                {{ tab.label }}
                <span v-if="tab.count" class="woc-tab-count">{{ tab.count }}</span>
              </button>
            </div>
          </div>

          <div class="woc-body">
            <div v-if="loading" class="woc-empty">加载中...</div>
            <div v-else-if="!filteredItems.length" class="woc-empty">暂无工单</div>
            <div
              v-for="wo in filteredItems"
              :key="woKey(wo)"
              class="wo-card"
              :class="{ expanded: expandedId === woKey(wo) }"
            >
              <button class="wo-row" @click="toggleExpand(wo)">
                <span class="wo-name">{{ wo.offeringName || wo.title || '未命名工单' }}</span>
                <span class="wo-id">{{ wo.workOrderId || wo.id || '-' }}</span>
                <span class="wo-status" :class="`st-${wo.status || 'pending'}`">{{ statusText(wo.status) }}</span>
              </button>
              <div v-if="expandedId === woKey(wo)" class="wo-detail">
                <div v-if="wo.summary" class="wo-line"><span class="wo-label">摘要</span><span>{{ wo.summary || '-' }}</span></div>
                <div class="wo-line"><span class="wo-label">创建时间</span><span>{{ formatTime(wo.createdAt) }}</span></div>
                <div class="wo-actions">
                  <button v-if="wo.offeringId" class="wo-btn" @click="$emit('view-archive', wo)">查看档案</button>
                  <button v-if="wo.offeringId" class="wo-btn" @click="$emit('open-metrics', wo)">运营指标</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { listWorkOrders } from '../../services/productOntologyApi.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'view-archive', 'open-metrics'])

const loading = ref(false)
const items = ref([])
const keyword = ref('')
const activeTab = ref('all')
const expandedId = ref(null)

const STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'processing', label: '进行中' },
  { key: 'completed', label: '已完成' },
  { key: 'cancelled', label: '已取消' },
]
const statusTabs = computed(() =>
  STATUS_TABS.map((tab) => ({
    ...tab,
    count: tab.key === 'all'
      ? items.value.length
      : items.value.filter((w) => (w.status || 'pending') === tab.key).length,
  })),
)

const filteredItems = computed(() => {
  let list = items.value
  if (activeTab.value !== 'all') {
    list = list.filter((w) => (w.status || 'pending') === activeTab.value)
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((w) =>
      [w.offeringName, w.title, w.workOrderId, w.offeringId]
        .some((v) => String(v || '').toLowerCase().includes(kw)),
    )
  }
  return list
})

watch(() => props.modelValue, async (open) => {
  if (!open) return
  loading.value = true
  try {
    const resp = await listWorkOrders()
    items.value = resp?.items || resp?.data?.items || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
})

function woKey(wo) {
  return wo.workOrderId || wo.id || Math.random()
}
function toggleExpand(wo) {
  const key = woKey(wo)
  expandedId.value = expandedId.value === key ? null : key
}
function statusText(status) {
  const map = {
    pending: '待处理', processing: '进行中', completed: '已完成',
    closed: '已关闭', cancelled: '已取消',
  }
  return map[status] || '待处理'
}
function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.woc-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  justify-content: flex-end;
}
.woc-panel {
  width: min(640px, 94vw);
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 32px rgba(15, 23, 42, 0.18);
}
.woc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}
.woc-title { font-size: 15px; font-weight: 700; color: #0f172a; }
.woc-close {
  border: none; background: transparent; color: #64748b;
  font-size: 14px; cursor: pointer; padding: 4px 8px; border-radius: 6px;
}
.woc-close:hover { background: #f1f5f9; color: #0f172a; }

.woc-toolbar { padding: 12px 20px 0; display: flex; flex-direction: column; gap: 10px; }
.woc-search {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
}
.woc-search:focus { outline: none; border-color: #60a5fa; }
.woc-tabs { display: flex; gap: 6px; overflow-x: auto; }
.woc-tab {
  border: 1px solid #e2e8f0; background: #fff; border-radius: 999px;
  padding: 5px 12px; font-size: 12px; color: #475569; cursor: pointer;
  display: inline-flex; align-items: center; gap: 4px; white-space: nowrap;
}
.woc-tab.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.woc-tab-count {
  font-size: 10px; background: rgba(37, 99, 235, 0.1); color: #2563eb;
  padding: 0 6px; border-radius: 999px; font-weight: 700;
}
.woc-tab.active .woc-tab-count { background: rgba(255, 255, 255, 0.25); color: #fff; }

.woc-body { flex: 1; overflow-y: auto; padding: 12px 20px 20px; display: flex; flex-direction: column; gap: 8px; }
.woc-empty { font-size: 13px; color: #94a3b8; text-align: center; padding: 48px 0; }

.wo-row {
  display: flex; align-items: center; gap: 10px;
  width: 100%; border: 1px solid #e2e8f0; background: #fff;
  border-radius: 12px; padding: 10px 14px; cursor: pointer; text-align: left;
}
.wo-row:hover { border-color: #93c5fd; background: #f8fafc; }
.wo-name { font-size: 13px; font-weight: 600; color: #0f172a; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wo-id { font-size: 11px; color: #64748b; font-family: monospace; flex-shrink: 0; }
.wo-status { font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 999px; flex-shrink: 0; }
.st-pending { background: #fffbeb; color: #b45309; }
.st-processing { background: #eff6ff; color: #2563eb; }
.st-completed { background: #ecfdf5; color: #059669; }
.st-closed, .st-cancelled { background: #f1f5f9; color: #64748b; }

.wo-detail { border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 12px 12px; padding: 10px 14px; display: flex; flex-direction: column; gap: 8px; background: #f8fafc; }
.wo-line { display: flex; gap: 10px; font-size: 12px; }
.wo-label { color: #94a3b8; flex-shrink: 0; width: 56px; }
.wo-line span:last-child { color: #334155; }
.wo-actions { display: flex; gap: 8px; padding-top: 4px; }
.wo-btn {
  border: 1px solid #cbd5e1; background: #fff; color: #334155;
  font-size: 12px; padding: 5px 12px; border-radius: 8px; cursor: pointer;
}
.wo-btn:hover { border-color: #93c5fd; background: #f0f9ff; }

.woc-slide-enter-active, .woc-slide-leave-active { transition: opacity 0.2s ease; }
.woc-slide-enter-active .woc-panel, .woc-slide-leave-active .woc-panel { transition: transform 0.24s ease; }
.woc-slide-enter-from, .woc-slide-leave-to { opacity: 0; }
.woc-slide-enter-from .woc-panel, .woc-slide-leave-to .woc-panel { transform: translateX(100%); }
</style>

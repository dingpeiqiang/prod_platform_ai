<template>
  <div class="workorder-wrap">
    <!-- 右上角工单中心图标 -->
    <button
      class="workorder-trigger"
      :class="{ active: open }"
      type="button"
      :title="`我的产品（${total} 条）`"
      @click="open = true"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
        <line x1="16" y1="13" x2="8" y2="13"/>
        <line x1="16" y1="17" x2="8" y2="17"/>
        <polyline points="10 9 9 9 8 9"/>
      </svg>
      <span v-if="total > 0" class="badge">{{ total > 99 ? '99+' : total }}</span>
    </button>

    <Teleport to="body">
      <Transition name="wo-mask-fade">
        <div v-if="open" class="wo-mask" @click.self="open = false"></div>
      </Transition>
      <Transition name="wo-slide">
        <aside v-if="open" class="wo-drawer">
          <header class="wo-header">
            <div class="wo-header-left">
              <span class="wo-title">我的产品</span>
              <span class="wo-sub">产品与工单 · 统一管理</span>
            </div>
            <button class="wo-close" @click="open = false" title="关闭">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </header>

          <div class="wo-toolbar">
            <div class="wo-search">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                v-model.trim="keyword"
                type="text"
                placeholder="搜索产品 / 工单号 / 模板 / 渠道"
                @keyup.enter="expandAllFiltered"
              />
              <button v-if="keyword" class="wo-search-clear" @click="keyword = ''" title="清空">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <span v-if="keyword" class="wo-search-count">共 {{ filteredArchived.length }} 条</span>
          </div>

          <div class="wo-body">
            <!-- 空状态 -->
            <div v-if="!sortedArchived.length" class="wo-empty">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              <p>暂无已归档工单</p>
              <span>完成套餐"上架销售"流程后，将在这里统一归档管理</span>
            </div>

            <!-- 无匹配结果 -->
            <div v-else-if="!filteredArchived.length" class="wo-empty">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <p>未找到匹配的工单</p>
              <span>换个关键词试试，支持产品名称、工单号、模板、渠道检索</span>
              <button class="wo-empty-reset" @click="keyword = ''">清空搜索</button>
            </div>

            <!-- 工单列表 -->
            <div v-else class="wo-list">
              <div
                v-for="order in filteredArchived"
                :key="order.id"
                class="wo-card"
                :class="{ offline: order.status === 'offline' }"
              >
                <div class="wo-card-head" @click="toggleExpand(order.id)">
                  <div class="wo-card-main">
                    <div class="wo-card-title-row">
                      <span class="wo-product">{{ order.productName }}</span>
                      <span class="wo-status" :class="order.status">{{ statusText(order.status) }}</span>
                    </div>
                    <div class="wo-woid">工单：{{ order.workOrderId }}</div>
                  </div>
                  <svg class="wo-chevron" :class="{ open: isExpanded(order.id) }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </div>

                <Transition name="wo-expand">
                  <div v-show="isExpanded(order.id)" class="wo-card-body">
                    <!-- 概览 -->
                    <div class="wo-ov-grid">
                      <div class="wo-ov-item">
                        <span class="wo-ov-label">{{ order.status === 'pending' ? '提交时间' : '模板' }}</span>
                        <span class="wo-ov-value">{{ order.status === 'pending' ? formatTime(order.salesAt) : order.templateLabel }}</span>
                      </div>
                      <div class="wo-ov-item">
                        <span class="wo-ov-label">{{ order.status === 'pending' ? '当前状态' : '上架时间' }}</span>
                        <span class="wo-ov-value">{{ order.status === 'pending' ? '待审批' : formatTime(order.salesAt) }}</span>
                      </div>
                      <div v-if="order.status !== 'pending'" class="wo-ov-item">
                        <span class="wo-ov-label">销售渠道</span>
                        <span class="wo-ov-value">{{ (order.channelList || []).join('、') || '—' }}</span>
                      </div>
                      <div v-else class="wo-ov-item">
                        <span class="wo-ov-label">审批进度</span>
                        <span class="wo-ov-value">审批中</span>
                      </div>
                    </div>

                    <!-- 只读配置快照 -->
                    <section v-if="order.snapshot && order.snapshot.length" class="wo-section">
                      <h4 class="wo-section-title">配置画像（只读）</h4>
                      <div class="wo-snapshot">
                        <div v-for="item in order.snapshot" :key="item.key" class="wo-snap-row">
                          <span class="wo-snap-label">{{ item.label }}</span>
                          <span class="wo-snap-value">{{ item.value }}</span>
                        </div>
                      </div>
                    </section>

                    <!-- 操作按钮 -->
                    <div class="wo-actions">
                      <button class="wo-btn primary" @click="openArchive(order)">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                          <polyline points="14 2 14 8 20 8"/>
                        </svg>
                        配置画像
                      </button>
                      <button
                        v-if="order.status === 'listed'"
                        class="wo-btn ghost"
                        @click="goMetrics(order)"
                      >
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <line x1="18" y1="20" x2="18" y2="10"/>
                          <line x1="12" y1="20" x2="12" y2="4"/>
                          <line x1="6" y1="20" x2="6" y2="14"/>
                        </svg>
                        产品运营指标
                      </button>
                      <button
                        v-if="order.status === 'listed'"
                        class="wo-btn danger is-disabled"
                        title="下架需走人工审批流程，暂不可操作"
                        @click="onOffline(order)"
                      >
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <line x1="18" y1="6" x2="6" y2="18"/>
                          <line x1="6" y1="6" x2="18" y2="18"/>
                        </svg>
                        下架
                      </button>
                      <button
                        v-else-if="order.status === 'offline'"
                        class="wo-btn secondary"
                        @click="onRestore(order)"
                      >
                        恢复上架
                      </button>
                      <span
                        v-else-if="order.status === 'pending'"
                        class="wo-pending-tip"
                      >待审批 · 审批通过后进入测试/备案/上架流程</span>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </div>
        </aside>
      </Transition>
    </Teleport>

    <!-- 只读工作台档案浮层：复用 ProductConfigPanel 展示某归档工单的完整配置（只读） -->
    <Teleport to="body">
      <Transition name="archive-fade">
        <div v-if="archiveOrder" class="archive-mask" @click.self="archiveOrder = null"></div>
      </Transition>
      <Transition name="archive-slide">
        <div v-if="archiveOrder" class="archive-wrap">
          <div class="archive-close-bar">
            <span class="archive-title">配置工作台档案（只读）</span>
            <button class="archive-close" @click="archiveOrder = null" title="关闭">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <ProductConfigPanel
            class="archive-panel"
            :archiveSnapshot="{ productInfo: archiveOrder.productInfo, values: archiveOrder.values, testReport: archiveOrder.testReport, filing: archiveOrder.filing, channel: archiveOrder.channel }"
            @close="archiveOrder = null"
          />
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkOrderStore } from '../stores/workOrder.js'
import { ElMessage } from 'element-plus'
import ProductConfigPanel from './ProductConfigPanel.vue'

const workOrderStore = useWorkOrderStore()
const { sortedArchived, total } = storeToRefs(workOrderStore)

// 与 workOrderStore 解耦：直接引用响应式数组，无需 storeToRefs
const open = ref(false)
const expandedId = ref(null)

// 打开产品运营视图中该工单对应的单产品视图（由 ChatPage 处理跨层跳转）
const emit = defineEmits(['open-product-metrics'])

// 工单查询：按 产品名称 / 工单号 / 模板 / 销售渠道 关键字过滤
const keyword = ref('')
const filteredArchived = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return sortedArchived.value
  return sortedArchived.value.filter((order) => {
    const haystack = [
      order.productName,
      order.workOrderId,
      order.templateLabel,
      (order.channelList || []).join(' '),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return haystack.includes(kw)
  })
})

// 按下回车时展开所有过滤命中的工单，便于快速查看
const expandAllFiltered = () => {
  if (filteredArchived.value.length === 1) {
    expandedId.value = filteredArchived.value[0].id
  }
}

// 正在查看完整工作台档案的工单（null = 未打开）
const archiveOrder = ref(null)
const openArchive = (order) => {
  archiveOrder.value = order
}

const isExpanded = (id) => expandedId.value === id
const toggleExpand = (id) => {
  expandedId.value = expandedId.value === id ? null : id
}

// 「产品运营指标」：先关闭工单中心抽屉（否则会遮住主工作区打开的运营视图），再通知 ChatPage 打开对应单产品视图
const goMetrics = (order) => {
  open.value = false
  emit('open-product-metrics', order)
}

const onOffline = (order) => {
  // 下架需走人工审批流程，暂不开放直接下架
  ElMessage.info(`【${order.productName}】下架需走人工审批流程，暂不可操作`)
}

const onRestore = (order) => {
  workOrderStore.setStatus(order.workOrderId, 'listed')
  ElMessage.success(`【${order.productName}】已恢复上架`)
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${p(d.getMonth() + 1)}/${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

// 工单状态文案：待审批 / 已上架 / 已下架
const statusText = (status) => {
  if (status === 'pending') return '待审批'
  return status === 'offline' ? '已下架' : '已上架'
}
</script>

<style scoped>
.workorder-wrap { display: inline-flex; }

.workorder-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s, color 0.15s;
}
.workorder-trigger:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #0f172a;
}
.workorder-trigger.active {
  border-color: #818cf8;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  color: #6366f1;
}

.workorder-trigger .badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #6366f1;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 999px;
  border: 2px solid #fff;
  line-height: 1;
}

/* 遮罩 */
.wo-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 90;
}
.wo-mask-fade-enter-active, .wo-mask-fade-leave-active { transition: opacity 0.2s ease; }
.wo-mask-fade-enter-from, .wo-mask-fade-leave-to { opacity: 0; }

/* 抽屉 */
.wo-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 460px;
  max-width: 92vw;
  background: #f8fafc;
  box-shadow: -16px 0 40px rgba(15, 23, 42, 0.18);
  z-index: 100;
  display: flex;
  flex-direction: column;
}
.wo-slide-enter-active, .wo-slide-leave-active { transition: transform 0.28s ease; }
.wo-slide-enter-from, .wo-slide-leave-to { transform: translateX(100%); }

.wo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.wo-title { font-size: 17px; font-weight: 700; color: #0f172a; }
.wo-sub { display: block; margin-top: 2px; font-size: 12px; color: #94a3b8; }
.wo-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}
.wo-close:hover { border-color: #cbd5e1; color: #0f172a; }

/* 查询工具栏 */
.wo-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
  background: #f8fafc;
}
.wo-search {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
}
.wo-search svg {
  position: absolute;
  left: 10px;
  color: #94a3b8;
  pointer-events: none;
}
.wo-search input {
  width: 100%;
  height: 34px;
  padding: 0 30px 0 32px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  color: #0f172a;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.wo-search input::placeholder { color: #a7b4c4; }
.wo-search input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}
.wo-search-clear {
  position: absolute;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.15s;
}
.wo-search-clear:hover { background: #f1f5f9; color: #475569; }
.wo-search-count {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.wo-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.wo-body::-webkit-scrollbar { width: 6px; }
.wo-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

/* 空状态 */
.wo-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #cbd5e1;
  gap: 10px;
}
.wo-empty p { font-size: 14px; font-weight: 600; color: #94a3b8; margin: 0; }
.wo-empty span { font-size: 12px; color: #cbd5e1; max-width: 260px; text-align: center; }
.wo-empty-reset {
  margin-top: 4px;
  padding: 6px 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}
.wo-empty-reset:hover { border-color: #3b82f6; color: #3b82f6; }

/* 工单卡片 */
.wo-list { display: flex; flex-direction: column; gap: 12px; }
.wo-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.wo-card:hover { border-color: #c7d7fe; box-shadow: 0 4px 16px rgba(99, 102, 241, 0.08); }
.wo-card.offline { opacity: 0.75; }
.wo-card.offline .wo-box-title { opacity: 0.8; }

.wo-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  cursor: pointer;
}
.wo-card-title-row { display: flex; align-items: center; gap: 8px; }
.wo-product { font-size: 15px; font-weight: 700; color: #0f172a; }
.wo-status {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}
.wo-status.listed { background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; }
.wo-status.offline { background: #f1f5f9; color: #64748b; border: 1px solid #e2e8f0; }
.wo-status.pending { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }
.wo-woid { margin-top: 4px; font-size: 12px; color: #94a3b8; }
.wo-chevron { color: #94a3b8; transition: transform 0.2s ease; flex-shrink: 0; }
.wo-chevron.open { transform: rotate(180deg); color: #6366f1; }

.wo-card-body { padding: 0 16px 16px; border-top: 1px solid #f1f5f9; }
.wo-expand-enter-active, .wo-expand-leave-active { transition: opacity 0.2s ease; }
.wo-expand-enter-from, .wo-expand-leave-to { opacity: 0; }

/* 概览 */
.wo-ov-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px 0;
}
.wo-ov-item { background: #f8fafc; border-radius: 10px; padding: 8px 10px; }
.wo-ov-label { display: block; font-size: 11px; color: #94a3b8; }
.wo-ov-value { display: block; margin-top: 3px; font-size: 13px; font-weight: 600; color: #334155; }

/* 配置档案 */
.wo-section { padding-top: 12px; }
.wo-section-title { font-size: 13px; font-weight: 700; color: #334155; margin: 0 0 8px; }
.wo-snapshot {
  background: #f8fafc;
  border-radius: 10px;
  padding: 4px 12px;
}
.wo-snap-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px dashed #e2e8f0;
  font-size: 12px;
}
.wo-snap-row:last-child { border-bottom: none; }
.wo-snap-label { color: #94a3b8; flex-shrink: 0; }
.wo-snap-value { color: #334155; font-weight: 600; text-align: right; }

/* 操作按钮 */
.wo-actions { display: flex; gap: 8px; padding-top: 14px; }
.wo-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 1;
  justify-content: center;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}
.wo-btn:hover { border-color: #cbd5e1; background: #f8fafc; }
.wo-btn.secondary { background: #eef2ff; border-color: #c7d2fe; color: #4338ca; }
.wo-btn.secondary:hover { background: #e0e7ff; }
.wo-btn.danger { background: #fef2f2; border-color: #fecaca; color: #dc2626; }
.wo-btn.danger:hover { background: #fee2e2; }
.wo-btn.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
  background: #f8fafc !important;
  border-color: #e2e8f0 !important;
  color: #94a3b8 !important;
}
.wo-btn.is-disabled:hover { background: #f8fafc !important; }
.wo-btn.ghost { background: #eff6ff; border-color: #bfdbfe; color: #2563eb; }
.wo-btn.ghost:hover { background: #dbeafe; }
.wo-btn.primary { background: #eef2ff; border-color: #c7d2fe; color: #4338ca; }
.wo-btn.primary:hover { background: #e0e7ff; }
.wo-pending-tip {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px dashed #fcd34d;
  background: #fffbeb;
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
  text-align: center;
}

/* 只读工作台档案浮层 */
.archive-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  z-index: 110;
}
.archive-fade-enter-active, .archive-fade-leave-active { transition: opacity 0.2s ease; }
.archive-fade-enter-from, .archive-fade-leave-to { opacity: 0; }

.archive-wrap {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 840px;
  max-width: 92vw;
  z-index: 120;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  box-shadow: -16px 0 40px rgba(15, 23, 42, 0.2);
}
.archive-slide-enter-active, .archive-slide-leave-active { transition: transform 0.28s ease; }
.archive-slide-enter-from, .archive-slide-leave-to { transform: translateX(100%); }

.archive-close-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.archive-title { font-size: 14px; font-weight: 700; color: #0f172a; }
.archive-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}
.archive-close:hover { border-color: #cbd5e1; color: #0f172a; }

.archive-wrap .archive-panel {
  flex: 1;
  min-height: 0;
  width: 100% !important;
}
</style>

<template>
  <div class="notification-wrap">
    <button
      class="notification-trigger"
      :class="{ active: open }"
      type="button"
      :title="titleText"
      @click.stop="onTrigger"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      <span v-if="showBadge" class="badge">{{ badgeText }}</span>
    </button>

    <transition name="notif-dropdown">
      <div v-if="open" class="notification-dropdown" @click.stop>
        <!-- 我的审批分区：仅当有审批工单时显示 -->
        <div v-if="approvalStore.sortedApprovals.length" class="notif-section approval-section">
          <div class="notif-header approval-header">
            <div class="notif-title-row">
              <span class="notif-title">我的审批</span>
              <span v-if="approvalStore.hasPending" class="notif-count">{{ approvalStore.pendingCount }} 项审批中</span>
            </div>
            <button class="notif-view-all" @click="onEnterConversation">查看全部</button>
          </div>
          <div class="notif-body">
            <button
              v-for="ap in approvalStore.sortedApprovals.slice(0, 4)"
              :key="ap.id"
              class="approval-item"
              @click="onOpenApproval(ap.id)"
            >
              <span class="approval-dot" :style="{ background: approvalStore.statusMeta(ap.status).color }"></span>
              <div class="approval-meta">
                <div class="approval-label">
                  <span class="approval-wo">{{ ap.workOrderId }}</span>
                  <span class="approval-status-tag" :style="{ color: approvalStore.statusMeta(ap.status).color, background: approvalStore.statusMeta(ap.status).color + '14' }">
                    {{ approvalStore.statusMeta(ap.status).label }}
                  </span>
                </div>
                <div class="approval-desc">{{ ap.productName }} · {{ ap.templateLabel }}</div>
                <div class="approval-time">提交于 {{ formatRelative(ap.submittedAt) }}</div>
              </div>
              <svg class="approval-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 今日待办分区 -->
        <div class="notif-section">
          <div class="notif-header">
            <div class="notif-title-row">
              <span class="notif-title">今日待办</span>
              <span v-if="hasTodoUnread" class="notif-count">{{ todoUnreadCount }} 项待处理</span>
            </div>
          </div>
          <div class="notif-body">
            <div class="notif-item urgent">
              <span class="notif-dot red"></span>
              <div class="notif-meta">
                <div class="notif-label">WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速) · 新增下滑35%</div>
                <div class="notif-desc">持续7天，累计影响约670单，月收入损失约11.8万元</div>
              </div>
            </div>
            <div class="notif-item">
              <span class="notif-dot yellow"></span>
              <div class="notif-meta">
                <div class="notif-label">WeWalk 15日不限量 · 澳门漫游覆盖待确认</div>
                <div class="notif-desc">友商已新增澳门漫游，需评估跟进补齐</div>
              </div>
            </div>
            <div class="notif-item">
              <span class="notif-dot green"></span>
              <div class="notif-meta">
                <div class="notif-label">WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速) · 资费字段未同步</div>
                <div class="notif-desc">工信部新模板要求补录</div>
              </div>
            </div>
          </div>
        </div>

        <div class="notif-footer">
          <button type="button" class="notif-action-btn" @click="onEnterConversation">
            进入对话查看详情
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useNotificationStore } from '../stores/notification.js'
import { useApprovalStore } from '../stores/approval.js'

const emit = defineEmits(['open-approval'])

const notifStore = useNotificationStore()
const approvalStore = useApprovalStore()

const open = ref(false)
const wrapEl = ref(null)

// 待办未读数（保持原有逻辑）
const todoUnreadCount = computed(() => notifStore.unreadCount)
const hasTodoUnread = computed(() => notifStore.hasUnread)

// badge 数 = 审批中工单数 + 待办未读数
const totalCount = computed(() => approvalStore.pendingCount + todoUnreadCount.value)
const showBadge = computed(() => totalCount.value > 0)
const badgeText = computed(() => {
  const n = totalCount.value
  return n > 99 ? '99+' : String(n)
})

const titleText = computed(() =>
  totalCount.value > 0 ? `${totalCount.value} 条待处理` : '暂无新消息'
)

const formatRelative = (t) => {
  if (!t) return ''
  const diff = Date.now() - t
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const onTrigger = () => {
  open.value = !open.value
}

const onEnterConversation = () => {
  open.value = false
  notifStore.trigger()
}

const onOpenApproval = (id) => {
  open.value = false
  emit('open-approval', id)
}

const handleDocumentClick = (e) => {
  if (!open.value) return
  const wrap = wrapEl.value
  if (wrap && !wrap.contains(e.target)) {
    open.value = false
  }
}

watch(open, (visible) => {
  if (visible) {
    nextTick(() => document.addEventListener('click', handleDocumentClick))
  } else {
    document.removeEventListener('click', handleDocumentClick)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<style scoped>
.notification-wrap {
  position: relative;
  display: inline-flex;
}

.notification-trigger {
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
.notification-trigger:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #0f172a;
}
.notification-trigger.active {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
  color: #2563eb;
}

.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 999px;
  border: 2px solid #fff;
  line-height: 1;
}

.notification-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 50;
  width: 360px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.notif-section + .notif-section { border-top: 1px solid #e2e8f0; }

.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #f8fafc, #eff6ff);
  border-bottom: 1px solid #e2e8f0;
}
.approval-header {
  background: linear-gradient(135deg, #f8fafc, #f0f9ff);
}
.notif-title-row { display: flex; align-items: center; gap: 8px; }
.notif-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.notif-count {
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
}
.notif-view-all {
  background: transparent;
  border: none;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.15s;
}
.notif-view-all:hover { background: rgba(37, 99, 235, 0.08); }

.notif-body {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 320px;
  overflow-y: auto;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  transition: background 0.15s;
}
.notif-item:hover { background: #f8fafc; }

.approval-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: none;
  background: transparent;
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}
.approval-item:hover { background: #f0f9ff; }

.approval-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.approval-meta { min-width: 0; flex: 1; }
.approval-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.approval-wo {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.02em;
}
.approval-status-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 999px;
  white-space: nowrap;
}
.approval-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.approval-time {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}
.approval-arrow {
  flex-shrink: 0;
  color: #cbd5e1;
  transition: transform 0.15s, color 0.15s;
}
.approval-item:hover .approval-arrow {
  transform: translateX(2px);
  color: #2563eb;
}

.notif-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
}
.notif-dot.red { background: #ef4444; }
.notif-dot.yellow { background: #f59e0b; }
.notif-dot.green { background: #10b981; }

.notif-meta { min-width: 0; flex: 1; }
.notif-label {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.4;
}
.notif-desc {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.notif-footer {
  padding: 8px;
  border-top: 1px solid #e2e8f0;
}
.notif-action-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.notif-action-btn:hover { background: #1d4ed8; }

.notif-dropdown-enter-active,
.notif-dropdown-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
  transform-origin: top right;
}
.notif-dropdown-enter-from,
.notif-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.96);
}
</style>

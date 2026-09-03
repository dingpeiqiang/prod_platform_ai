<template>
  <div ref="rootRef" class="notif-root">
    <button class="notif-btn" :title="`通知（${notifStore.badgeCount}）`" @click="toggle">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <span v-if="notifStore.badgeCount > 0" class="notif-badge">{{ badgeText }}</span>
    </button>

    <teleport to="body">
      <div v-if="open" class="notif-pop" :style="popStyle">
        <div class="notif-head">
          <span class="notif-title">消息通知</span>
          <button class="notif-clear" @click="notifStore.clearUnread()">全部已读</button>
        </div>

        <div class="notif-section">
          <div class="notif-section-title">我的审批</div>
          <div v-if="!notifStore.approvals.length" class="notif-empty">暂无审批任务</div>
          <button
            v-for="(item, idx) in notifStore.approvals.slice(0, 4)"
            :key="item.workOrderId || item.id || idx"
            class="notif-item"
            @click="onItemOpen({ type: 'approval', approval: item })"
          >
            <span class="notif-dot" :class="`dot-${item.status || 'pending'}`"></span>
            <span class="notif-item-main">
              <span class="notif-item-name">{{ item.offeringName || item.title || '配置审批' }}</span>
              <span class="notif-item-sub">{{ item.workOrderId || '-' }}</span>
            </span>
            <span class="notif-item-tag" :class="`tag-${item.status || 'pending'}`">{{ statusText(item.status) }}</span>
          </button>
        </div>

        <div class="notif-section">
          <div class="notif-section-title">今日待办</div>
          <div v-if="!notifStore.todos.length" class="notif-empty">今日暂无待办</div>
          <button
            v-for="todo in notifStore.todos"
            :key="todo.id"
            class="notif-item"
            :class="{ read: todo.read }"
            @click="onItemOpen({ type: 'todo', todo })"
          >
            <span class="notif-level" :class="`lv-${todo.level || 'remind'}`">{{ levelText(todo.level) }}</span>
            <span class="notif-item-main">
              <span class="notif-item-name">{{ todo.title }}</span>
              <span v-if="todo.action" class="notif-item-sub">{{ todo.action }}</span>
            </span>
            <span class="notif-item-go">处理 →</span>
          </button>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted, watch } from 'vue'
import { useNotificationStore } from '../../stores/notification.js'

const notifStore = useNotificationStore()

const props = defineProps({
  /** 外部信号（如导航按钮点击）：openSignal.seq 变化时打开 */
  openSignal: { type: Object, default: null },
})

const emit = defineEmits(['item-open'])

const rootRef = ref(null)
const open = ref(false)
const popStyle = ref({})

watch(() => props.openSignal?.seq, () => {
  if (props.openSignal) open.value = true
})

function toggle() {
  open.value = !open.value
  if (open.value) positionPop()
}

function positionPop() {
  const rect = rootRef.value?.getBoundingClientRect()
  if (!rect) return
  popStyle.value = {
    top: `${rect.bottom + 8}px`,
    right: `${Math.max(8, window.innerWidth - rect.right)}px`,
  }
}

/** 按需挂载 document 点击监听（迁移自原型 UserMenu 模式） */
function onDocClick(e) {
  if (!rootRef.value?.contains(e.target)) {
    open.value = false
  }
}
watch(open, (v) => {
  if (v) {
    document.addEventListener('click', onDocClick, { capture: true })
    notifStore.clearUnread()
  } else {
    document.removeEventListener('click', onDocClick, { capture: true })
  }
})
onUnmounted(() => document.removeEventListener('click', onDocClick, { capture: true }))

const badgeText = computed(() => (notifStore.badgeCount > 99 ? '99+' : String(notifStore.badgeCount)))

function statusText(status) {
  const map = { pending: '审批中', passed: '已通过', rejected: '已驳回' }
  return map[status] || '审批中'
}
function levelText(level) {
  const map = { urgent: '紧急', important: '重要', remind: '提醒' }
  return map[level] || '提醒'
}
function onItemOpen(payload) {
  emit('item-open', payload)
  open.value = false
}
</script>

<style scoped>
.notif-root { position: relative; display: inline-flex; }
.notif-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.notif-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.notif-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.notif-pop {
  position: fixed;
  z-index: 2100;
  width: 340px;
  max-height: 60vh;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.16);
  padding: 12px;
}
.notif-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; border-bottom: 1px solid #f1f5f9; }
.notif-title { font-size: 13px; font-weight: 700; color: #0f172a; }
.notif-clear { border: none; background: transparent; color: #2563eb; font-size: 12px; cursor: pointer; }

.notif-section { display: flex; flex-direction: column; gap: 6px; padding-top: 10px; }
.notif-section-title { font-size: 11px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.04em; }
.notif-empty { font-size: 12px; color: #cbd5e1; text-align: center; padding: 10px 0; }

.notif-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: #f8fafc;
  border-radius: 10px;
  padding: 8px 10px;
  cursor: pointer;
  text-align: left;
}
.notif-item:hover { background: #eff6ff; }
.notif-item.read { opacity: 0.55; }
.notif-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.notif-item-name { font-size: 12px; font-weight: 600; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notif-item-sub { font-size: 11px; color: #94a3b8; }
.notif-item-tag { font-size: 10px; font-weight: 600; padding: 2px 8px; border-radius: 999px; flex-shrink: 0; }
.tag-pending { background: #fffbeb; color: #b45309; }
.tag-passed { background: #ecfdf5; color: #059669; }
.tag-rejected { background: #fef2f2; color: #dc2626; }
.notif-item-go { font-size: 11px; color: #2563eb; flex-shrink: 0; }

.notif-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-pending { background: #f59e0b; }
.dot-passed { background: #22c55e; }
.dot-rejected { background: #ef4444; }

.notif-level { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 6px; flex-shrink: 0; }
.lv-urgent { background: #fef2f2; color: #dc2626; }
.lv-important { background: #fffbeb; color: #b45309; }
.lv-remind { background: #eff6ff; color: #2563eb; }
</style>

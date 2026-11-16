<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="modelValue" class="approval-mask" @click.self="onClose"></div>
    </Transition>
    <Transition name="drawer-slide">
      <aside v-if="modelValue && approval" class="approval-drawer">
        <header class="ad-header">
          <div class="ad-header-left">
            <div class="ad-wo-id">{{ approval.workOrderId }}</div>
            <div class="ad-product">{{ approval.productName }}</div>
          </div>
          <div class="ad-header-right">
            <span class="ad-status-tag" :style="{ color: statusMeta.color, background: statusMeta.color + '14', borderColor: statusMeta.color + '40' }">
              {{ statusMeta.label }}
            </span>
            <button class="ad-close" @click="onClose" title="关闭">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </header>

        <div class="ad-body">
          <!-- 概览信息 -->
          <section class="ad-section ad-overview">
            <div class="ad-overview-item">
              <span class="ad-ov-label">产品模板</span>
              <span class="ad-ov-value">{{ approval.templateLabel }}</span>
            </div>
            <div class="ad-overview-item">
              <span class="ad-ov-label">提交时间</span>
              <span class="ad-ov-value">{{ formatTime(approval.submittedAt) }}</span>
            </div>
            <div class="ad-overview-item">
              <span class="ad-ov-label">提交人</span>
              <span class="ad-ov-value">当前用户</span>
            </div>
            <div class="ad-overview-item">
              <span class="ad-ov-label">当前节点</span>
              <span class="ad-ov-value" :style="{ color: statusMeta.color, fontWeight: 600 }">{{ statusMeta.label }}</span>
            </div>
          </section>

          <!-- 审批时间线 -->
          <section class="ad-section">
            <h4 class="ad-section-title">审批进度</h4>
            <ol class="ad-timeline">
              <li
                v-for="(node, idx) in approval.timeline"
                :key="node.step"
                class="ad-tl-node"
                :class="['st-' + node.status]"
              >
                <div class="ad-tl-marker">
                  <span v-if="node.status === 'done'" class="ad-tl-dot done">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </span>
                  <span v-else-if="node.status === 'active'" class="ad-tl-dot active">
                    <span class="ad-tl-pulse"></span>
                  </span>
                  <span v-else class="ad-tl-dot pending"></span>
                  <span v-if="idx < approval.timeline.length - 1" class="ad-tl-line" :class="{ filled: node.status === 'done' }"></span>
                </div>
                <div class="ad-tl-content">
                  <div class="ad-tl-head">
                    <span class="ad-tl-label">{{ node.label }}</span>
                    <span v-if="node.time" class="ad-tl-time">{{ formatTime(node.time) }}</span>
                  </div>
                  <div v-if="node.operator" class="ad-tl-operator">处理人：{{ node.operator }}</div>
                  <div class="ad-tl-desc">{{ node.desc }}</div>
                </div>
              </li>
            </ol>
          </section>

          <!-- 操作区 -->
          <section v-if="!isTerminal" class="ad-section ad-actions">
            <button class="ad-btn secondary" @click="onUrge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              催办
            </button>
            <button class="ad-btn ghost" @click="onBackToChat">返回对话</button>
          </section>
          <section v-else class="ad-section ad-actions">
            <div class="ad-terminal-tip" :class="approval.status">
              <svg v-if="approval.status === 'approved'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              {{ approval.status === 'approved' ? '工单已通过，可继续后续测试流程' : '工单已驳回，请根据意见调整后重新提交' }}
            </div>
          </section>
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { useApprovalStore } from '../stores/approval.js'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  approvalId: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'back-to-chat'])

const approvalStore = useApprovalStore()

const approval = computed(() =>
  props.approvalId ? approvalStore.getById(props.approvalId) : null
)

const statusMeta = computed(() =>
  approval.value ? approvalStore.statusMeta(approval.value.status) : { label: '', color: '#64748b' }
)

const isTerminal = computed(() =>
  approval.value && (approval.value.status === 'approved' || approval.value.status === 'rejected')
)

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const onClose = () => emit('update:modelValue', false)

const onBackToChat = () => {
  emit('update:modelValue', false)
  emit('back-to-chat')
}

const onUrge = () => {
  ElMessage.info('已发送催办提醒给产品主管')
}
</script>

<style scoped>
.approval-mask {
  position: fixed; inset: 0;
  background: rgba(15, 23, 42, 0.28);
  z-index: 100;
}
.approval-drawer {
  position: fixed;
  top: 0; right: 0; bottom: 0;
  width: 420px;
  max-width: 92vw;
  background: #fff;
  z-index: 101;
  display: flex;
  flex-direction: column;
  box-shadow: -12px 0 40px rgba(15, 23, 42, 0.14);
}

.ad-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fafc, #eff6ff);
}
.ad-header-left { min-width: 0; flex: 1; }
.ad-wo-id {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.ad-product {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 4px;
}
.ad-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.ad-status-tag {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid;
  white-space: nowrap;
}
.ad-close {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  border: none; border-radius: 8px;
  background: transparent; color: #64748b;
  cursor: pointer; transition: all 0.15s;
}
.ad-close:hover { background: #e2e8f0; color: #0f172a; }

.ad-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.ad-section { display: flex; flex-direction: column; gap: 12px; }
.ad-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  letter-spacing: 0.02em;
}

.ad-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}
.ad-overview-item { display: flex; flex-direction: column; gap: 4px; }
.ad-ov-label { font-size: 11px; color: #94a3b8; font-weight: 600; }
.ad-ov-value { font-size: 13px; color: #0f172a; font-weight: 600; }

.ad-timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ad-tl-node {
  position: relative;
  display: flex;
  gap: 14px;
  padding-bottom: 22px;
}
.ad-tl-node:last-child { padding-bottom: 0; }

.ad-tl-marker {
  position: relative;
  flex-shrink: 0;
  width: 20px;
  display: flex;
  justify-content: center;
}
.ad-tl-dot {
  width: 20px; height: 20px;
  border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  z-index: 2;
}
.ad-tl-dot.done { background: #10b981; color: #fff; }
.ad-tl-dot.active {
  background: #fff;
  border: 2px solid #2563eb;
  position: relative;
}
.ad-tl-pulse {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #2563eb;
  animation: tl-pulse 1.4s ease-in-out infinite;
}
@keyframes tl-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.4; }
}
.ad-tl-dot.pending {
  background: #fff;
  border: 2px solid #cbd5e1;
}
.ad-tl-line {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 2px;
  height: calc(100% + 22px);
  background: #e2e8f0;
  z-index: 1;
}
.ad-tl-line.filled { background: #10b981; }

.ad-tl-content { min-width: 0; flex: 1; padding-top: 0; }
.ad-tl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.ad-tl-label {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.st-pending .ad-tl-label { color: #94a3b8; }
.ad-tl-time {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}
.ad-tl-operator {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}
.ad-tl-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  margin-top: 4px;
}

.ad-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.ad-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 9px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}
.ad-btn.secondary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.ad-btn.secondary:hover { background: #1d4ed8; }
.ad-btn.ghost {
  background: #fff;
  color: #475569;
  border-color: #e2e8f0;
}
.ad-btn.ghost:hover { background: #f8fafc; border-color: #cbd5e1; }

.ad-terminal-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  width: 100%;
}
.ad-terminal-tip.approved {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}
.ad-terminal-tip.rejected {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.drawer-fade-enter-active, .drawer-fade-leave-active { transition: opacity 0.22s ease; }
.drawer-fade-enter-from, .drawer-fade-leave-to { opacity: 0; }

.drawer-slide-enter-active, .drawer-slide-leave-active { transition: transform 0.26s cubic-bezier(0.22, 1, 0.36, 1); }
.drawer-slide-enter-from, .drawer-slide-leave-to { transform: translateX(100%); }
</style>

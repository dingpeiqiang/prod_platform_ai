<template>
  <el-drawer
    :model-value="modelValue"
    title="审批进度"
    size="420px"
    :append-to-body="true"
    @update:modelValue="$emit('update:modelValue', $event)"
  >
    <div v-if="approval" class="apd">
      <div class="apd-head">
        <span class="apd-name">{{ approval.offeringName || approval.title || '配置审批' }}</span>
        <span class="apd-tag" :class="`tag-${approval.status}`">{{ statusLabel }}</span>
      </div>
      <div v-if="approval.workOrderId" class="apd-wo">工单号：{{ approval.workOrderId }}</div>

      <div class="apd-timeline">
        <div
          v-for="(stage, i) in timelineStages"
          :key="i"
          class="apd-stage"
          :class="stage.state"
        >
          <span class="apd-dot"></span>
          <div class="apd-stage-body">
            <div class="apd-stage-name">{{ stage.label }}</div>
            <div v-if="stage.time" class="apd-stage-time">{{ stage.time }}</div>
          </div>
        </div>
      </div>

      <div v-if="approval.summary" class="apd-summary">
        <div class="apd-summary-title">审批概要</div>
        <div class="apd-summary-text">{{ approval.summary }}</div>
      </div>

      <div v-if="!isTerminal" class="apd-actions">
        <button class="apd-btn" :disabled="urging" @click="handleUrge">
          {{ urging ? '已催办' : '催办' }}
        </button>
      </div>
    </div>
    <div v-else class="apd-empty">暂无进行中的审批</div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 审批单：{ offeringName, workOrderId, status, createdAt, updatedAt, summary } */
  approval: { type: Object, default: null },
})

defineEmits(['update:modelValue'])

const urging = ref(false)

const STATUS_MAP = {
  pending: '审批中',
  passed: '已通过',
  rejected: '已驳回',
}
const statusLabel = computed(() => STATUS_MAP[props.approval?.status] || props.approval?.status || '审批中')

const isTerminal = computed(() => ['passed', 'rejected'].includes(props.approval?.status))

const timelineStages = computed(() => {
  const a = props.approval || {}
  const status = a.status || 'pending'
  return [
    { label: '提交申请', state: 'done', time: a.createdAt || '' },
    { label: '合规预检', state: 'done', time: a.createdAt || '' },
    { label: '人工审批', state: status === 'pending' ? 'active' : 'done', time: status !== 'pending' ? (a.updatedAt || '') : '' },
    {
      label: status === 'rejected' ? '已驳回' : '审批通过',
      state: status === 'rejected' ? 'fail' : (status === 'passed' ? 'done' : 'pending'),
      time: status !== 'pending' ? (a.updatedAt || '') : '',
    },
  ]
})

async function handleUrge() {
  urging.value = true
  ElMessage.success('已发送催办提醒')
  setTimeout(() => { urging.value = false }, 3000)
}
</script>

<style scoped>
.apd { display: flex; flex-direction: column; gap: 16px; }
.apd-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.apd-name { font-size: 15px; font-weight: 700; color: #0f172a; }
.apd-tag { font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 999px; flex-shrink: 0; }
.tag-pending { background: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.tag-passed { background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; }
.tag-rejected { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.apd-wo { font-size: 12px; color: #64748b; }

.apd-timeline { display: flex; flex-direction: column; }
.apd-stage { display: flex; gap: 12px; position: relative; padding-bottom: 20px; }
.apd-stage:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 2px;
  background: #e2e8f0;
}
.apd-stage.done:not(:last-child)::before { background: #22c55e; }
.apd-dot {
  width: 12px; height: 12px; border-radius: 50%;
  background: #e2e8f0; flex-shrink: 0; margin-top: 2px; z-index: 1;
}
.apd-stage.done .apd-dot { background: #22c55e; }
.apd-stage.active .apd-dot { background: #2563eb; animation: apd-pulse 1.6s ease-in-out infinite; }
.apd-stage.fail .apd-dot { background: #dc2626; }
.apd-stage-name { font-size: 13px; font-weight: 600; color: #334155; }
.apd-stage.pending .apd-stage-name { color: #94a3b8; font-weight: 400; }
.apd-stage-time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
@keyframes apd-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(37, 99, 235, 0); }
}

.apd-summary { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; }
.apd-summary-title { font-size: 12px; font-weight: 700; color: #334155; margin-bottom: 6px; }
.apd-summary-text { font-size: 12px; color: #64748b; line-height: 1.6; }

.apd-actions { display: flex; justify-content: flex-end; }
.apd-btn { padding: 8px 20px; border-radius: 8px; border: 1px solid #cbd5e1; background: #fff; color: #334155; font-size: 13px; cursor: pointer; }
.apd-btn:hover:not(:disabled) { border-color: #93c5fd; background: #f0f9ff; }
.apd-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.apd-empty { text-align: center; color: #94a3b8; font-size: 13px; padding: 40px 0; }
</style>

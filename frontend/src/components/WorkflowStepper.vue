<template>
  <div v-if="workflowStore.workflow" class="wf-stepper">
    <!-- 方案信息条 -->
    <div class="wf-info-bar">
      <div class="wf-info-left">
        <svg class="wf-info-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
        <span class="wf-product-name">{{ workflowStore.workflow.productName }}</span>
        <span v-if="workflowStore.workflow.workOrderId" class="wf-wo-id">{{ workflowStore.workflow.workOrderId }}</span>
      </div>
      <span class="wf-current-label">{{ currentStageLabel }}</span>
    </div>

    <!-- 阶段步骤条 -->
    <div class="wf-steps">
      <template v-for="(stage, idx) in workflowStore.stages" :key="stage.key">
        <div
          class="wf-step"
          :class="['st-' + stage.status, { 'is-last': idx === workflowStore.stages.length - 1 }]"
        >
          <div class="wf-step-marker">
            <span v-if="stage.status === 'done'" class="wf-step-dot done">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </span>
            <span v-else-if="stage.status === 'active'" class="wf-step-dot active">
              <span class="wf-step-pulse"></span>
            </span>
            <span v-else class="wf-step-dot pending">
              <span class="wf-step-num">{{ idx + 1 }}</span>
            </span>
            <span v-if="idx < workflowStore.stages.length - 1" class="wf-step-line" :class="{ filled: stage.status === 'done' }"></span>
          </div>
          <div class="wf-step-content">
            <span class="wf-step-label">{{ stage.label }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useWorkflowStore } from '../stores/workflow.js'

const workflowStore = useWorkflowStore()

const currentStageLabel = computed(() => {
  const stage = workflowStore.stages.find((s) => s.status === 'active')
  if (!stage) return workflowStore.currentStage === 'done' ? '流程已完成' : ''
  return stage.label + '中'
})
</script>

<style scoped>
.wf-stepper {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 14px 12px;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
  border-bottom: 1px solid #e2e8f0;
}

/* 方案信息条 */
.wf-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.wf-info-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.wf-info-icon {
  color: #3b82f6;
  flex-shrink: 0;
}
.wf-product-name {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-wo-id {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
  white-space: nowrap;
}
.wf-current-label {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 阶段步骤条 */
.wf-steps {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0;
}

.wf-step {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.wf-step-marker {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 20px;
}

.wf-step-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  transition: all 0.2s ease;
}
.wf-step-dot.done {
  background: #10b981;
  color: #fff;
}
.wf-step-dot.active {
  background: #fff;
  border: 2px solid #2563eb;
  width: 20px;
  height: 20px;
}
.wf-step-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2563eb;
  animation: wf-pulse 1.4s ease-in-out infinite;
}
@keyframes wf-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.4; }
}
.wf-step-dot.pending {
  background: #fff;
  border: 2px solid #cbd5e1;
  color: #94a3b8;
}
.wf-step-num {
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}

/* 连接线 */
.wf-step-line {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translateY(-50%);
  width: 100%;
  height: 2px;
  background: #e2e8f0;
  z-index: 1;
}
.wf-step-line.filled {
  background: #10b981;
}
/* 最后一个步骤不显示连接线 */
.wf-step.is-last .wf-step-line {
  display: none;
}

/* 步骤标签 */
.wf-step-content {
  text-align: center;
}
.wf-step-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  white-space: nowrap;
}
.st-done .wf-step-label {
  color: #10b981;
}
.st-active .wf-step-label {
  color: #2563eb;
  font-weight: 700;
}
.st-pending .wf-step-label {
  color: #94a3b8;
}
</style>

<template>
  <div class="audit-block">
    <div v-if="phase === 'running'" class="audit-running">
      <div v-for="(step, i) in steps" :key="i" class="audit-step" :class="{ active: i === stepIndex }">
        <span class="step-dot" :class="{ spin: i === stepIndex, done: i < stepIndex }"></span>
        <span class="step-text">{{ step }}</span>
      </div>
    </div>

    <div v-else-if="phase === 'done'" class="audit-result" :class="hasError ? 'fail' : 'pass'">
      <div class="audit-banner">
        {{ hasError ? '⚠️ 稽核发现问题，请处理后重试' : '✅ 智能稽核通过，可提交配置' }}
      </div>
      <div class="audit-items">
        <div v-for="(item, i) in results" :key="i" class="audit-item" :class="`item-${item.type}`">
          <span class="item-title">{{ item.title }}</span>
          <span class="item-desc">{{ item.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** idle | running | done */
  phase: { type: String, default: 'idle' },
  steps: { type: Array, default: () => [] },
  stepIndex: { type: Number, default: 0 },
  results: { type: Array, default: () => [] },
  hasError: { type: Boolean, default: false },
})
</script>

<style scoped>
.audit-block { padding: 12px 0; }
.audit-running { display: flex; flex-direction: column; gap: 8px; }
.audit-step { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #64748b; }
.audit-step.active { color: #2563eb; font-weight: 600; }
.step-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: #cbd5e1; flex-shrink: 0;
}
.step-dot.done { background: #22c55e; }
.step-dot.active { background: #2563eb; }
.step-dot.spin { animation: pulse 1s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.5; transform: scale(0.8); } }

.audit-result { display: flex; flex-direction: column; gap: 10px; }
.audit-banner { font-size: 13px; font-weight: 700; padding: 8px 12px; border-radius: 8px; }
.audit-result.pass .audit-banner { background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; }
.audit-result.fail .audit-banner { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }

.audit-items { display: flex; flex-direction: column; gap: 6px; }
.audit-item {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 10px; border-radius: 8px; background: #f8fafc; border: 1px solid #e2e8f0;
}
.item-title { font-size: 12px; font-weight: 600; color: #0f172a; }
.item-desc { font-size: 11px; color: #64748b; line-height: 1.5; }
.audit-item.item-error { border-color: #fecaca; background: #fff5f5; }
.audit-item.item-warning { border-color: #fde68a; background: #fffbeb; }
.audit-item.item-success { border-color: #a7f3d0; }
</style>

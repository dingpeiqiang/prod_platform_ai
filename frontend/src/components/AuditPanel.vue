<template>
  <ElDrawer
    v-model="visible"
    title="智能稽核"
    direction="rtl"
    size="420px"
    :with-header="true"
    :close-on-click-modal="false"
    @close="$emit('close')"
  >
    <template #header>
      <div class="panel-header">
        <h3>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 11l3 3L22 4"/>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
          </svg>
          智能稽核
        </h3>
        <span v-if="productName" class="product-name-tag">{{ productName }}</span>
      </div>
    </template>

    <!-- 进度阶段 -->
    <div v-if="phase === 'progress'" class="audit-progress">
      <div class="progress-icon">
        <svg class="spin" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
      </div>
      <p class="progress-title">{{ progressTitle }}</p>
      <div class="loading-dots">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <p class="progress-text">{{ progressText }}</p>

      <!-- 阶段步骤指示 -->
      <div class="stage-list">
        <div
          v-for="(stage, idx) in stages"
          :key="idx"
          class="stage-item"
          :class="{
            active: currentStageIndex === idx,
            done: currentStageIndex > idx
          }"
        >
          <span class="stage-icon">
            <svg v-if="currentStageIndex > idx" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <span v-else-if="currentStageIndex === idx" class="stage-dot"></span>
            <span v-else class="stage-circle"></span>
          </span>
          <span class="stage-label">{{ stage.label }}</span>
        </div>
      </div>
    </div>

    <!-- 结果阶段 -->
    <div v-else-if="phase === 'results'" class="audit-results">
      <div
        v-for="(item, idx) in results"
        :key="idx"
        class="audit-item"
        :class="item.type"
      >
        <div class="audit-icon">
          <svg v-if="item.type === 'error'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <svg v-else-if="item.type === 'warning'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </div>
        <div class="audit-text">
          <div class="audit-title">{{ item.title }}</div>
          <div class="audit-desc">{{ item.desc }}</div>
        </div>
      </div>

      <!-- 通过横幅 -->
      <div v-if="!hasError" class="audit-banner success">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
        <div>
          <div class="banner-title">配置提交成功</div>
          <div class="banner-desc">商品状态已更新为待审批</div>
        </div>
      </div>

      <!-- 失败提示 -->
      <div v-else class="audit-banner error">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <div>
          <div class="banner-title">稽核未通过</div>
          <div class="banner-desc">请修正上述错误项后重新提交配置</div>
        </div>
      </div>

      <!-- 关闭按钮 -->
      <div class="audit-footer">
        <button type="button" class="close-btn" @click="handleClose">关闭</button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="audit-empty">
      <p>暂未启动稽核，请点击顶栏「智能稽核」按钮</p>
    </div>
  </ElDrawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { ElDrawer } from 'element-plus';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  productName: { type: String, default: '' },
  results: { type: Array, default: () => [] },
  phase: { type: String, default: 'idle' },
  hasError: { type: Boolean, default: false }
});

const emit = defineEmits(['update:modelValue', 'close', 'audit-complete']);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const stages = [
  { label: '完整性校验' },
  { label: '业务规则校验' },
  { label: '短信合规检查' },
  { label: '结果汇总' }
];

const stageTextMap = [
  { title: '正在执行智能稽核...', text: '正在检查配置完整性...' },
  { title: '基础信息完整性校验', text: '正在检查必填字段...' },
  { title: '业务规则校验', text: '正在验证字段联动规则...' },
  { title: '短信模板合规性检查', text: '正在检查关键词合规性...' }
];

const currentStageIndex = ref(0);
const progressTitle = ref(stageTextMap[0].title);
const progressText = ref(stageTextMap[0].text);

let stageTimer = null;

watch(() => props.phase, (newPhase) => {
  if (stageTimer) {
    clearTimeout(stageTimer);
    stageTimer = null;
  }
  if (newPhase === 'progress') {
    currentStageIndex.value = 0;
    progressTitle.value = stageTextMap[0].title;
    progressText.value = stageTextMap[0].text;
    runProgressStages();
  } else if (newPhase === 'results') {
    if (!props.hasError) {
      emit('audit-complete', { passed: true, results: props.results });
    }
  }
});

const runProgressStages = () => {
  let idx = 0;
  const advance = () => {
    idx += 1;
    if (idx >= stageTextMap.length) {
      return;
    }
    currentStageIndex.value = idx;
    progressTitle.value = stageTextMap[idx].title;
    progressText.value = stageTextMap[idx].text;
    stageTimer = setTimeout(advance, 1500);
  };
  stageTimer = setTimeout(advance, 1500);
};

const handleClose = () => {
  visible.value = false;
  emit('close');
};
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.product-name-tag {
  font-size: 12px;
  padding: 2px 8px;
  background: #dbeafe;
  color: #3b82f6;
  border-radius: 4px;
  font-weight: 500;
}

/* 进度阶段 */
.audit-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  text-align: center;
}

.progress-icon {
  color: #3b82f6;
  margin-bottom: 16px;
}

.spin {
  animation: spin 1.4s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.progress-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin: 0 0 12px;
}

.loading-dots {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dot-pulse 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dot-pulse {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.progress-text {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  margin: 0 0 24px;
}

/* 阶段步骤 */
.stage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  max-width: 280px;
}

.stage-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 13px;
  color: var(--text-tertiary, #94a3b8);
  transition: all 0.2s;
}

.stage-item.active {
  background: #dbeafe;
  color: #3b82f6;
  font-weight: 500;
}

.stage-item.done {
  color: #10b981;
}

.stage-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stage-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dot-pulse 1.4s infinite;
}

.stage-circle {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid currentColor;
  opacity: 0.5;
}

.stage-label {
  flex: 1;
}

/* 结果阶段 */
.audit-results {
  padding: 16px 20px;
}

.audit-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  margin-bottom: 10px;
  align-items: flex-start;
}

.audit-item.error {
  background: #fef2f2;
  border-left: 3px solid #ef4444;
}

.audit-item.warning {
  background: #fffbeb;
  border-left: 3px solid #f59e0b;
}

.audit-item.success {
  background: #f0fdf4;
  border-left: 3px solid #10b981;
}

.audit-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.audit-item.error .audit-icon { color: #ef4444; }
.audit-item.warning .audit-icon { color: #f59e0b; }
.audit-item.success .audit-icon { color: #10b981; }

.audit-text {
  flex: 1;
  min-width: 0;
}

.audit-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin-bottom: 4px;
}

.audit-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  line-height: 1.5;
}

/* 横幅 */
.audit-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 10px;
  margin-top: 20px;
}

.audit-banner.success {
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  color: #10b981;
  border: 1px solid #bbf7d0;
}

.audit-banner.error {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  color: #ef4444;
  border: 1px solid #fecaca;
}

.banner-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin-bottom: 2px;
}

.banner-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

/* 关闭按钮 */
.audit-footer {
  text-align: center;
  margin-top: 20px;
}

.close-btn {
  padding: 10px 32px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.close-btn:hover {
  background: #2563eb;
}

/* 空状态 */
.audit-empty {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-tertiary, #94a3b8);
  font-size: 14px;
}

/* 响应式 */
@media (max-width: 768px) {
  .audit-progress {
    padding: 24px 16px;
  }

  .stage-list {
    max-width: 100%;
  }

  .audit-results {
    padding: 12px 16px;
  }
}
</style>

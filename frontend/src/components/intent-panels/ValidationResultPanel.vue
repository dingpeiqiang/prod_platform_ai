<template>
  <div class="validation-result-panel">
    <!-- 校验结果头部 -->
    <div class="result-header" :class="resultClass">
      <div class="header-icon">{{ resultIcon }}</div>
      <div class="header-text">
        <div class="header-title">{{ title }}</div>
        <div class="header-sub" v-if="subtitle">{{ subtitle }}</div>
      </div>
      <div class="header-badges">
        <el-tag v-if="passed" type="success" size="small">✅ 通过</el-tag>
        <el-tag v-else type="danger" size="small">❌ 未通过</el-tag>
        <el-tag v-if="warningCount > 0" type="warning" size="small">⚠️ {{ warningCount }} 条提示</el-tag>
      </div>
    </div>

    <!-- 错误列表 -->
    <div v-if="errorCount > 0" class="result-section errors">
      <div class="section-label">❌ 发现 {{ errorCount }} 个问题</div>
      <div
        v-for="(err, idx) in errors"
        :key="'err-' + idx"
        class="result-item error-item"
      >
        <div class="item-field">{{ err.fieldName || err.fieldCode || '字段' }}</div>
        <div class="item-reason">{{ err.reason }}</div>
      </div>
    </div>

    <!-- 警告列表 -->
    <div v-if="warningCount > 0" class="result-section warnings">
      <div class="section-label">⚠️ {{ warningCount }} 条提示</div>
      <div
        v-for="(warn, idx) in warnings"
        :key="'warn-' + idx"
        class="result-item warning-item"
      >
        <div class="item-field">{{ warn.fieldName || warn.fieldCode || '字段' }}</div>
        <div class="item-reason">{{ warn.reason }}</div>
      </div>
    </div>

    <!-- 通过时简洁提示 -->
    <div v-if="passed && errorCount === 0 && warningCount === 0" class="result-pass">
      <span>✅ 所有字段符合业务规则，表单填写正确。</span>
    </div>

    <!-- 操作按钮 -->
    <div v-if="!passed || warningCount > 0" class="result-actions">
      <el-button
        v-if="!passed && errorCount > 0"
        type="primary"
        size="small"
        @click="$emit('fix', errors)"
      >
        修改错误
      </el-button>
      <el-button
        v-if="warningCount > 0"
        type="info"
        size="small"
        plain
        @click="$emit('ignore-warnings')"
      >
        忽略提示
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 从 intent data 传入
  validationData: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['fix', 'ignore-warnings'])

// 提取数据
const passed = computed(() => props.validationData.passed !== false)
const errors = computed(() => props.validationData.errors || [])
const warnings = computed(() => props.validationData.warnings || [])
const errorCount = computed(() => errors.value.length)
const warningCount = computed(() => warnings.value.length)
const formName = computed(() => props.validationData.formName || props.validationData.formCode || '表单')

// UI 状态
const resultClass = computed(() => {
  if (!passed.value || errorCount.value > 0) return 'status-error'
  if (warningCount.value > 0) return 'status-warning'
  return 'status-pass'
})

const resultIcon = computed(() => {
  if (!passed.value || errorCount.value > 0) return '❌'
  if (warningCount.value > 0) return '⚠️'
  return '✅'
})

const title = computed(() => {
  if (!passed.value || errorCount.value > 0) return `「${formName.value}」校验未通过`
  if (warningCount.value > 0) return `「${formName.value}」存在 ${warningCount.value} 条提示`
  return `「${formName.value}」校验通过`
})

const subtitle = computed(() => {
  if (errorCount.value > 0) return `共 ${errorCount.value} 个错误`
  if (warningCount.value > 0) return `共 ${warningCount.value} 个提示`
  return ''
})
</script>

<style scoped>
.validation-result-panel {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 12px 14px;
  margin-top: 8px;
  font-size: 13px;
}

.result-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.result-header.status-error .header-icon { color: #f56c6c; }
.result-header.status-warning .header-icon { color: #e6a23c; }
.result-header.status-pass .header-icon { color: #67c23a; }

.header-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 1px;
}

.header-text {
  flex: 1;
  min-width: 0;
}

.header-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.header-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.header-badges {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.result-section {
  margin-bottom: 10px;
}

.section-label {
  font-weight: 600;
  font-size: 12.5px;
  margin-bottom: 6px;
  color: #606266;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  border-radius: 6px;
  margin-bottom: 4px;
}

.error-item {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.warning-item {
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
}

.item-field {
  font-weight: 600;
  font-size: 12px;
  color: #303133;
}

.item-reason {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.result-pass {
  text-align: center;
  color: #67c23a;
  padding: 6px 0;
  font-size: 13px;
}

.result-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}
</style>
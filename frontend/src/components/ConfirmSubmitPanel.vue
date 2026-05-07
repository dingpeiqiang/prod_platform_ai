<template>
  <div class="confirm-submit-panel">
    <div class="confirm-header">
      <span class="confirm-icon">📋</span>
      <span class="confirm-title">{{ formName }} - 确认提交</span>
    </div>

    <!-- AI 校验警告 -->
    <div v-if="aiErrors.length > 0" class="ai-errors">
      <div class="error-title">⚠️ AI 校验发现以下问题：</div>
      <div v-for="(err, idx) in aiErrors" :key="idx" class="error-item">
        • {{ err.fieldName }}: {{ err.reason }}
      </div>
    </div>

    <!-- 表单数据预览 -->
    <div class="form-preview">
      <div class="preview-title">提交内容：</div>
      <div v-for="(item, idx) in previewItems" :key="idx" class="preview-item">
        <span class="preview-label">{{ item.fieldName }}:</span>
        <span class="preview-value">{{ item.value }}</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="confirm-actions">
      <el-button @click="handleCancel" :disabled="loading">
        返回修改
      </el-button>
      <el-button type="primary" @click="handleConfirm" :loading="loading">
        确认提交
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  formName: {
    type: String,
    default: '表单'
  },
  fields: {
    type: Array,
    default: () => []
  },
  aiWarnings: {
    type: Array,
    default: () => []
  },
  aiErrors: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['confirm', 'cancel'])
const loading = ref(false)

// 生成预览数据
const previewItems = computed(() => {
  const items = []
  for (const [key, value] of Object.entries(props.data)) {
    if (value !== undefined && value !== null && value !== '') {
      const displayValue = Array.isArray(value) ? value.join(', ') : String(value)
      items.push({
        fieldName: key, // TODO: 尝试从 fields 映射中文名
        value: displayValue
      })
    }
  }
  return items
})

const handleConfirm = async () => {
  loading.value = true
  emit('confirm', props.data)
}

const handleCancel = () => {
  emit('cancel')
}
</script>

<style scoped>
.confirm-submit-panel {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  max-width: 400px;
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.confirm-icon {
  font-size: 20px;
}

.confirm-title {
  font-weight: 600;
  color: #303133;
}

.ai-errors {
  background: #fef0f0;
  border: 1px solid #fc8996;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.error-title {
  color: #c45656;
  font-weight: 500;
  margin-bottom: 8px;
}

.error-item {
  color: #c45656;
  font-size: 13px;
  line-height: 1.6;
}

.form-preview {
  margin-bottom: 16px;
}

.preview-title {
  color: #606266;
  font-weight: 500;
  margin-bottom: 8px;
}

.preview-item {
  display: flex;
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.preview-label {
  color: #909399;
  min-width: 100px;
  flex-shrink: 0;
}

.preview-value {
  color: #303133;
  word-break: break-all;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}
</style>
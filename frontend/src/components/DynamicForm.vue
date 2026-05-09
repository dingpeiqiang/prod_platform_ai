<template>
  <div class="dynamic-form">
    <h3>{{ schema.formName }}</h3>
    <el-form :model="localFormData" label-width="120px">
      <el-form-item
        v-for="field in schema.fields"
        :key="field.fieldCode"
        :label="field.fieldName"
        :required="field.required"
      >
        <template v-if="!field.hidden">
          <BaseField
            :field="field"
            :model-value="localFormData[field.fieldCode]"
            :disabled="field.disabled || isFormDisabled()"
            @update:model-value="(value) => localFormData[field.fieldCode] = value"
            @field-change="handleFieldChange"
          />
        </template>
      </el-form-item>
      
      <el-form-item>
        <el-button @click="handleCancel" :disabled="formSubmitted || formCancelled">
          取消
        </el-button>
        <el-button 
          type="primary" 
          native-type="button"
          @click="handleSubmit" 
          :loading="submitting"
          :disabled="formSubmitted || formCancelled"
        >
          {{ formSubmitted ? '已提交' : (formCancelled ? '已取消' : '提交表单') }}
        </el-button>
      </el-form-item>
      
      <div v-if="formSubmitted" class="submitted-hint">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="16 10 10 16 8 14"/>
        </svg>
        <span>此表单已提交，不可再次提交</span>
      </div>
      
      <div v-if="formCancelled" class="cancelled-hint">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
        <span>此表单已取消，不可操作</span>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BaseField from './fields/BaseField.vue'
import { validateField } from './../utils'

const props = defineProps({
  schema: {
    type: Object,
    required: true
  },
  formId: {
    type: String,
    default: ''
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  version: {
    type: Number,
    default: 1
  },
  formSubmitted: {
    type: Boolean,
    default: false
  },
  formCancelled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['field-change', 'form-submit', 'submit', 'cancel', 'ai-validation', 'confirm-submit'])

const localFormData = reactive({})
const isFormDisabled = () => props.formSubmitted || props.formCancelled
const submitting = ref(false)

watch(() => props.formData, (newData) => {
  const numberFields = new Set()
  if (props.schema && props.schema.fields) {
    props.schema.fields.forEach(f => {
      if (f.fieldType === 'number') numberFields.add(f.fieldCode)
    })
  }
  Object.keys(newData).forEach(key => {
    let val = newData[key]
    if (numberFields.has(key) && val !== null && val !== undefined && val !== '') {
      val = Number(val)
    }
    localFormData[key] = val
  })
}, { deep: true, immediate: true })

watch(() => props.schema, (newSchema) => {
  if (newSchema && newSchema.fields) {
    newSchema.fields.forEach(field => {
      if (field.value !== undefined && field.value !== null) {
        let val = field.value
        if (field.fieldType === 'number' && val !== '' && typeof val === 'string') {
          val = Number(val)
        }
        localFormData[field.fieldCode] = val
      } else if (field.defaultValue !== undefined && localFormData[field.fieldCode] === undefined) {
        let val = field.defaultValue
        if (field.fieldType === 'number' && val !== '' && typeof val === 'string') {
          val = Number(val)
        }
        localFormData[field.fieldCode] = val
      }
    })
  }
}, { immediate: true, deep: true })

const handleFieldChange = (fieldCode, value) => {
  emit('field-change', fieldCode, value)
}

const validateForm = () => {
  const errors = []
  props.schema.fields.forEach(field => {
    const fieldErrors = validateField(field, localFormData[field.fieldCode])
    errors.push(...fieldErrors)
  })
  return errors
}

const aiValidate = async () => {
  try {
    const response = await fetch('/api/v1/validation/llm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        form_code: props.schema.formCode,
        data: { ...localFormData }
      })
    })
    const result = await response.json()

    return {
      passed: result.valid !== false,
      errors: (result.errors || []).map(e => ({
        fieldName: e.field_name || e.field_code || '未知字段',
        reason: e.reason || e.message || String(e)
      })),
      warnings: (result.warnings || []).map(w => ({
        fieldName: w.field_name || w.field_code || '未知字段',
        reason: w.reason || w.message || String(w)
      }))
    }
  } catch (e) {
    console.error('[aiValidate] 校验失败:', e)
    return { passed: true, errors: [], warnings: [] }
  }
}

const handleCancel = () => {
  emit('cancel')
}

let _lastAiValidation = null

const handleSubmit = async () => {
  if (props.schema._preview) {
    const errors = validateForm()
    if (errors.length > 0) {
      ElMessage.error(errors[0])
      return
    }
    const summaryLines = []
    for (const field of props.schema.fields) {
      const val = localFormData[field.fieldCode]
      if (val !== undefined && val !== null && val !== '') {
        const displayVal = Array.isArray(val) ? val.join(', ') : String(val)
        summaryLines.push(`- **${field.fieldName}**: ${displayVal}`)
      }
    }
    ElMessageBox.alert(
      `<div style="text-align:left;line-height:2">${summaryLines.length ? summaryLines.join('<br>') : '未填写任何内容'}</div>`,
      '预览 - 表单填写结果',
      { dangerouslyUseHTMLString: true, title: '👁️ 预览模式' }
    )
    return
  }

  const errors = validateForm()
  if (errors.length > 0) {
    ElMessage.error(errors[0])
    return
  }

  emit('confirm-submit', {
    formId: props.formId,
    formCode: props.schema.formCode,
    formName: props.schema.formName,
    data: { ...localFormData },
    schema: props.schema
  })
}

const doSubmit = async () => {
  const API_BASE = '/api/v1'
  const url = `${API_BASE}/form/submit`

  submitting.value = true

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        formId: props.formId,
        data: { ...localFormData },
        version: props.version || 1
      })
    })

    const result = await response.json()

    if (result.success) {
      _lastAiValidation = null
      emit('submit', { ...localFormData }, props.formId)
      return { success: true, message: result.message }
    } else {
      return { success: false, message: result.message || '提交失败' }
    }
  } catch (e) {
    console.error('[doSubmit] 提交失败:', e)
    return { success: false, message: '提交失败: ' + e.message }
  } finally {
    submitting.value = false
  }
}

defineExpose({ doSubmit })
</script>

<style scoped>
.dynamic-form h3 {
  margin-bottom: var(--space-5);
  color: var(--text-primary);
  font-size: var(--font-size-xl);
}

.dynamic-form :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--border-default) inset !important;
  border-radius: var(--radius-md);
  transition: box-shadow var(--transition-fast);
}

.dynamic-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--border-strong) inset !important;
}

.dynamic-form :deep(.el-input__wrapper.is-focus),
.dynamic-form :deep(.el-select .el-input.is-focus .el-input__wrapper),
.dynamic-form :deep(.el-date-editor.is-active .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-primary-400) inset !important;
}

.dynamic-form :deep(.el-input__inner) {
  color: var(--text-primary);
}

.dynamic-form :deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary);
}

.dynamic-form :deep(.el-input-number) {
  width: 100%;
}

.dynamic-form :deep(.el-input-number .el-input__wrapper) {
  padding-left: var(--space-3);
  padding-right: var(--space-3);
}

.dynamic-form :deep(.el-date-editor) {
  width: 100% !important;
}

.dynamic-form :deep(.el-date-editor .el-input__wrapper) {
  width: 100%;
}

.dynamic-form :deep(.el-select) {
  width: 100%;
}

.dynamic-form :deep(.el-select .el-input__wrapper) {
  width: 100%;
}

.dynamic-form :deep(.el-radio-group),
.dynamic-form :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.dynamic-form :deep(.el-radio),
.dynamic-form :deep(.el-checkbox) {
  margin-right: 0;
}

.dynamic-form :deep(.el-radio__label),
.dynamic-form :deep(.el-checkbox__label) {
  color: var(--text-primary);
}

.dynamic-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.dynamic-form :deep(.el-form-item__label::before) {
  color: var(--color-error-500);
}

.dynamic-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-error-500) inset !important;
}

.submitted-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--color-success-50);
  border: 1px solid var(--color-success-100);
  border-radius: var(--radius-md);
  color: var(--color-success-700);
  font-size: var(--font-size-sm);
  margin-top: var(--space-2);
}

.cancelled-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--color-error-50);
  border: 1px solid var(--color-error-100);
  border-radius: var(--radius-md);
  color: var(--color-error-700);
  font-size: var(--font-size-sm);
  margin-top: var(--space-2);
}
</style>
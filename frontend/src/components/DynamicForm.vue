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
          <el-input
            v-if="field.fieldType === 'input'"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            :placeholder="'请输入' + field.fieldName"
            @input="handleInput(field.fieldCode, $event)"
          />
          
          <el-input-number
            v-else-if="field.fieldType === 'number'"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            :min="0"
            style="width: 100%"
            @change="handleInput(field.fieldCode, $event)"
          />
          
          <el-date-picker
            v-else-if="field.fieldType === 'date'"
            v-model="localFormData[field.fieldCode]"
            type="date"
            :disabled="field.disabled"
            :placeholder="'请选择' + field.fieldName"
            style="width: 100%"
            @change="handleInput(field.fieldCode, $event)"
            value-format="YYYY-MM-DD"
          />

          <el-date-picker
            v-else-if="field.fieldType === 'datetime'"
            v-model="localFormData[field.fieldCode]"
            type="datetime"
            :disabled="field.disabled"
            :placeholder="'请选择' + field.fieldName"
            style="width: 100%"
            @change="handleInput(field.fieldCode, $event)"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
          
          <!-- select: 有 options 正常渲染，无 options 降级为 input -->
          <el-select
            v-else-if="field.fieldType === 'select' && hasEnumOptions(field)"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            :placeholder="'请选择' + field.fieldName"
            style="width: 100%"
            @change="handleInput(field.fieldCode, $event)"
          >
            <el-option
              v-for="option in getFieldOptions(field)"
              :key="getOptionValue(option)"
              :label="getOptionLabel(option)"
              :value="getOptionValue(option)"
            />
          </el-select>

          <el-input
            v-else-if="field.fieldType === 'select' && !hasEnumOptions(field)"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            :placeholder="'请输入' + field.fieldName + '（枚举选项缺失，已自动切换为文本输入）'"
            @input="handleInput(field.fieldCode, $event)"
          />

          <!-- radio: 有 options 正常渲染，无 options 降级为 input -->
          <el-radio-group
            v-else-if="field.fieldType === 'radio' && hasEnumOptions(field)"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            @change="handleInput(field.fieldCode, $event)"
          >
            <el-radio
              v-for="option in getFieldOptions(field)"
              :key="getOptionValue(option)"
              :value="getOptionValue(option)"
            >
              {{ getOptionLabel(option) }}
            </el-radio>
          </el-radio-group>

          <!-- checkbox: 有 options 正常渲染，无 options 降级为 input -->
          <el-checkbox-group
            v-else-if="field.fieldType === 'checkbox' && hasEnumOptions(field)"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            @change="handleInput(field.fieldCode, $event)"
          >
            <el-checkbox
              v-for="option in getFieldOptions(field)"
              :key="getOptionValue(option)"
              :label="getOptionLabel(option)"
              :value="getOptionValue(option)"
            />
          </el-checkbox-group>

          <el-input
            v-else-if="field.fieldType === 'checkbox' && !hasEnumOptions(field)"
            v-model="localFormData[field.fieldCode]"
            type="textarea"
            :disabled="field.disabled"
            :placeholder="'请输入选项（枚举选项缺失，已切换为文本输入）'"
            @input="handleInput(field.fieldCode, $event)"
          />
          
          <el-input
            v-else-if="field.fieldType === 'textarea'"
            v-model="localFormData[field.fieldCode]"
            type="textarea"
            :rows="3"
            :disabled="field.disabled"
            :placeholder="'请输入' + field.fieldName"
            @input="handleInput(field.fieldCode, $event)"
          />

          <el-input
            v-else-if="field.fieldType === 'email'"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            placeholder="请输入邮箱地址"
            @input="handleInput(field.fieldCode, $event)"
          />

          <el-input
            v-else-if="field.fieldType === 'phone'"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            placeholder="请输入手机号"
            @input="handleInput(field.fieldCode, $event)"
          />

          <el-upload
            v-else-if="field.fieldType === 'file'"
            :disabled="field.disabled"
            :auto-upload="false"
            :on-change="(file) => handleFileChange(field.fieldCode, file)"
          >
            <el-button size="small" :disabled="field.disabled">点击上传</el-button>
          </el-upload>
          
          <div v-if="field.recommend && field.recommend.length > 0" class="recommend-tags">
            <span class="recommend-label">推荐：</span>
            <el-tooltip
              v-for="(rec, idx) in field.recommend"
              :key="idx"
              :content="normalizeRecommend(rec).reason || ''"
              :disabled="!normalizeRecommend(rec).reason"
              placement="top"
            >
              <el-tag
                class="recommend-tag"
                :class="'rec-source-' + normalizeRecommend(rec).source"
                @click="selectRecommend(field.fieldCode, rec)"
              >
                {{ normalizeRecommend(rec).label }}
                <span v-if="normalizeRecommend(rec).source === 'llm_rule'" class="rec-badge">AI</span>
              </el-tag>
            </el-tooltip>
          </div>
        </template>
      </el-form-item>
      
      <el-form-item>
        <el-button @click="handleCancel" :disabled="formSubmitted">
          取消
        </el-button>
        <el-button 
          type="primary" 
          native-type="button"
          @click="handleSubmit" 
          :loading="submitting"
          :disabled="formSubmitted"
        >
          {{ formSubmitted ? '已提交' : '提交表单' }}
        </el-button>
      </el-form-item>
      
      <!-- 表单已提交提示 -->
      <div v-if="formSubmitted" class="submitted-hint">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="16 10 10 16 8 14"/>
        </svg>
        <span>此表单已提交，不可再次提交</span>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

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
  }
})

const emit = defineEmits(['field-change', 'form-submit', 'submit', 'cancel', 'ai-validation', 'confirm-submit'])

const localFormData = reactive({})
const submitting = ref(false)

watch(() => props.formData, (newData) => {
  // 构建 fieldCode -> fieldType 的映射，用于类型转换
  const numberFields = new Set()
  if (props.schema && props.schema.fields) {
    props.schema.fields.forEach(f => {
      if (f.fieldType === 'number') numberFields.add(f.fieldCode)
    })
  }
  Object.keys(newData).forEach(key => {
    let val = newData[key]
    // number 类型字段将字符串转为数字，避免 ElInputNumber prop 类型校验失败
    if (numberFields.has(key) && val !== null && val !== undefined && val !== '') {
      val = Number(val)
    }
    localFormData[key] = val
  })
}, { deep: true, immediate: true })

watch(() => props.schema, (newSchema) => {
  if (newSchema && newSchema.fields) {
    newSchema.fields.forEach(field => {
      // 优先使用字段的 value 属性（用于外部更新）
      if (field.value !== undefined && field.value !== null) {
        let val = field.value
        // number 类型字段将字符串转为数字
        if (field.fieldType === 'number' && val !== '' && typeof val === 'string') {
          val = Number(val)
        }
        localFormData[field.fieldCode] = val
      }
      // 其次使用默认值
      else if (field.defaultValue !== undefined && localFormData[field.fieldCode] === undefined) {
        let val = field.defaultValue
        // number 类型字段将字符串转为数字
        if (field.fieldType === 'number' && val !== '' && typeof val === 'string') {
          val = Number(val)
        }
        localFormData[field.fieldCode] = val
      }
    })
  }
}, { immediate: true, deep: true })

const handleInput = (fieldCode, value) => {
  emit('field-change', fieldCode, value)
}

// 获取字段的选项列表：优先 enumConfig → 其次 options
const getFieldOptions = (field) => {
  if (field.enumConfig) {
    if (field.enumConfig.type === 'static' && Array.isArray(field.enumConfig.options)) {
      return field.enumConfig.options
    }
    // API 枚举暂时返回 fallback，后续可扩展为异步加载
    if (field.enumConfig.type === 'api') {
      const fallback = field.enumConfig.api?.fallback
      if (Array.isArray(fallback)) return fallback
    }
  }
  return field.options || []
}

// 获取选项的值
const getOptionValue = (option) => {
  return option.value
}

// 获取选项的标签
const getOptionLabel = (option) => {
  return option.label
}

// 判断枚举字段是否有可用选项（用于降级判断）
const hasEnumOptions = (field) => {
  return getFieldOptions(field).length > 0
}

const handleFileChange = (fieldCode, file) => {
  // 存储文件对象引用
  localFormData[fieldCode] = file.raw || file
  emit('field-change', fieldCode, file.raw || file)
}

const selectRecommend = (fieldCode, rec) => {
  const value = normalizeRecommend(rec).value
  localFormData[fieldCode] = value
  emit('field-change', fieldCode, value)
}

// 兼容字符串和对象两种推荐格式
const normalizeRecommend = (rec) => {
  if (typeof rec === 'string') {
    return { value: rec, label: rec, source: 'static', reason: '', confidence: 0 }
  }
  return {
    value: rec.value || '',
    label: rec.label || rec.value || '',  // ⚠️ 优先使用label，如果没有则用value
    source: rec.source || 'history',
    reason: rec.reason || '',
    confidence: rec.confidence || 0
  }
}

const validateForm = () => {
  const errors = []

  props.schema.fields.forEach(field => {
    if (field.required) {
      const value = localFormData[field.fieldCode]
      if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
        errors.push(`${field.fieldName} 是必填项`)
      }
    }

    // 通用格式即时校验（与业务无关的格式规则）
    if (field.fieldType && field.required !== false) {
      const value = localFormData[field.fieldCode]
      const isEmpty = value === undefined || value === null || value === ''
      if (!isEmpty) {
        if (field.fieldType === 'email') {
          if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(String(value))) {
            errors.push(`${field.fieldName} 格式不正确，请输入有效的邮箱地址`)
          }
        }
        if (field.fieldType === 'phone') {
          if (!/^1[3-9]\d{9}$/.test(String(value))) {
            errors.push(`${field.fieldName} 格式不正确，请输入有效的手机号`)
          }
        }
      }
    }
  })

  return errors
}

const aiValidate = async () => {
  try {
    // 调用 LLM 智能校验 API
    const response = await fetch('/api/v1/validation/llm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        form_code: props.schema.formCode,
        data: { ...localFormData }
      })
    })
    const result = await response.json()

    // 转换前端期望的格式
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

// AI 校验结果缓存
let _lastAiValidation = null

const handleSubmit = async () => {
  // 预览模式
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

  // 正式模式：前端校验
  const errors = validateForm()
  if (errors.length > 0) {
    ElMessage.error(errors[0])
    return
  }

  // 前端校验通过后，emit 数据到聊天窗口进行 AI 校验和确认
  emit('confirm-submit', {
    formId: props.formId,
    formCode: props.schema.formCode,
    formName: props.schema.formName,
    data: { ...localFormData }
  })
}

// 对外方法：执行真正的表单提交（由父组件在确认后调用）
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
</script>

<style scoped>
.dynamic-form h3 {
  margin-bottom: var(--space-5);
  color: var(--text-primary);
  font-size: var(--font-size-xl);
}

/* Element Plus 输入框样式覆盖 */
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

/* 数字输入框 */
.dynamic-form :deep(.el-input-number) {
  width: 100%;
}

.dynamic-form :deep(.el-input-number .el-input__wrapper) {
  padding-left: var(--space-3);
  padding-right: var(--space-3);
}

/* 日期选择器 */
.dynamic-form :deep(.el-date-editor) {
  width: 100% !important;
}

.dynamic-form :deep(.el-date-editor .el-input__wrapper) {
  width: 100%;
}

/* 选择器 */
.dynamic-form :deep(.el-select) {
  width: 100%;
}

.dynamic-form :deep(.el-select .el-input__wrapper) {
  width: 100%;
}

/* 单选框和复选框 */
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

/* 表单项标签 */
.dynamic-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.dynamic-form :deep(.el-form-item__label::before) {
  color: var(--color-error-500);
}

/* 表单验证错误状态 */
.dynamic-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--color-error-500) inset !important;
}

.recommend-tags {
  margin-top: var(--space-2);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.recommend-label {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.recommend-tag {
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
}

.recommend-tag:hover {
  transform: translateY(-1px);
}

/* 按来源区分颜色 */
.recommend-tag.rec-source-llm_rule {
  background-color: var(--color-primary-50);
  border-color: var(--color-primary-400);
  color: var(--color-primary-700);
}
.recommend-tag.rec-source-llm_rule:hover {
  background-color: var(--color-primary-500) !important;
  color: white !important;
}

.recommend-tag.rec-source-inference,
.recommend-tag.rec-source-history {
  background-color: var(--color-info-50);
  border-color: var(--color-info-500);
  color: var(--color-info-700);
}
.recommend-tag.rec-source-inference:hover,
.recommend-tag.rec-source-history:hover {
  background-color: var(--color-info-500) !important;
  color: white !important;
}

.recommend-tag.rec-source-static,
.recommend-tag.rec-source-context {
  background-color: var(--bg-tertiary);
  border-color: var(--border-strong);
  color: var(--text-secondary);
}
.recommend-tag.rec-source-static:hover,
.recommend-tag.rec-source-context:hover {
  background-color: var(--color-gray-500) !important;
  color: white !important;
}

.rec-badge {
  font-size: 10px;
  margin-left: 2px;
  padding: 0 3px;
  border-radius: var(--radius-full);
  background: var(--color-primary-500);
  color: white;
}

/* 已提交提示 */
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
</style>

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

const emit = defineEmits(['field-change', 'form-submit', 'submit', 'cancel'])

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
  /**
   * AI 校验已移除，直接返回通过
   */
  return { passed: true, errors: [], warnings: [] }
}

const handleCancel = () => {
  emit('cancel')
}

// AI 校验结果缓存（用于用户选择"忽略警告继续提交"时跳过重复校验）
let _lastAiValidation = null

const handleSubmit = async () => {
  // 预览模式：表单未部署，只做前端校验 + 本地预览
  if (props.schema._preview) {
    const errors = validateForm()
    if (errors.length > 0) {
      ElMessage.error(errors[0])
      return
    }
    // 预览模式下提交 = 展示填写结果摘要，不上传后端
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

  // 正式模式：完整提交流程
  // 第一步：前端即时校验（必填 + 通用格式）
  const errors = validateForm()
  
  if (errors.length > 0) {
    ElMessage.error(errors[0])
    return
  }
  
  submitting.value = true
  
  try {
    // 第二步：AI 业务规则校验（如果上次校验没被缓存跳过）
    if (!_lastAiValidation) {
      _lastAiValidation = await aiValidate()
    }
    const aiResult = _lastAiValidation

    // 显示 AI 校验 warnings（不阻塞提交）
    if (aiResult.warnings && aiResult.warnings.length > 0) {
      aiResult.warnings.forEach(w => {
        ElMessage.warning({
          message: `⚠️ ${w.fieldName}: ${w.reason}`,
          duration: 5000
        })
      })
    }

    // 显示 AI 校验 errors（阻塞提交，但允许用户选择忽略）
    if (aiResult.errors && aiResult.errors.length > 0) {
      submitting.value = false
      
      // 弹窗展示所有错误，用户可选择"仍然提交"或"返回修改"
      const errorDetails = aiResult.errors.map(e => `• ${e.fieldName}: ${e.reason}`).join('\n')
      
      try {
        await ElMessageBox.confirm(
          `AI 校验发现以下问题：\n${errorDetails}\n\n你可以返回修改，或忽略警告继续提交。`,
          'AI 校验结果',
          {
            confirmButtonText: '仍然提交',
            cancelButtonText: '返回修改',
            type: 'warning',
            dangerouslyUseHTMLString: false
          }
        )
        // 用户选择"仍然提交"
        _lastAiValidation = null  // 清除缓存
        submitting.value = true
        // 继续走提交流程
      } catch {
        // 用户选择"返回修改"
        _lastAiValidation = null
        return
      }
    }

    // 第三步：提交表单
    const API_BASE = '/api/v1'
    const url = `${API_BASE}/form/submit`
    
    console.log('[FormSubmit] 开始提交表单')
    console.log('[FormSubmit] URL:', url)
    console.log('[FormSubmit] formId:', props.formId)
    console.log('[FormSubmit] version:', props.version)
    console.log('[FormSubmit] data:', localFormData)
    
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        formId: props.formId,
        data: { ...localFormData },
        version: props.version || 1
      })
    })
    
    console.log('[FormSubmit] HTTP Status:', response.status)
    
    // 读取响应文本用于调试
    const responseText = await response.text()
    console.log('[FormSubmit] Response Text:', responseText)
    
    let result
    try {
      result = JSON.parse(responseText)
      console.log('[FormSubmit] Parsed Response:', result)
    } catch (e) {
      console.error('[FormSubmit] JSON Parse Error:', e)
      ElMessage.error('服务器返回了无效的响应格式')
      return
    }
    
    if (result.success) {
      _lastAiValidation = null  // 提交成功，清除缓存
      emit('submit', { ...localFormData }, props.formId)
    } else {
      ElMessage.error(result.message || '提交失败')
    }
  } catch (error) {
    console.error('[FormSubmit] Error:', error)
    ElMessage.error('提交失败，请重试: ' + error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dynamic-form h3 {
  margin-bottom: 20px;
  color: #303133;
  font-size: 18px;
}

.recommend-tags {
  margin-top: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.recommend-label {
  font-size: 12px;
  color: #909399;
}

.recommend-tag {
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.recommend-tag:hover {
  transform: translateY(-1px);
}

/* 按来源区分颜色 */
.recommend-tag.rec-source-llm_rule {
  background-color: #f0e6ff;
  border-color: #a855f7;
  color: #7c3aed;
}
.recommend-tag.rec-source-llm_rule:hover {
  background-color: #a855f7 !important;
  color: white !important;
}

.recommend-tag.rec-source-inference,
.recommend-tag.rec-source-history {
  background-color: #e6f4ff;
  border-color: #409eff;
  color: #2563eb;
}
.recommend-tag.rec-source-inference:hover,
.recommend-tag.rec-source-history:hover {
  background-color: #409eff !important;
  color: white !important;
}

.recommend-tag.rec-source-static,
.recommend-tag.rec-source-context {
  background-color: #f5f5f5;
  border-color: #c0c4cc;
  color: #606266;
}
.recommend-tag.rec-source-static:hover,
.recommend-tag.rec-source-context:hover {
  background-color: #909399 !important;
  color: white !important;
}

.rec-badge {
  font-size: 10px;
  margin-left: 2px;
  padding: 0 3px;
  border-radius: 8px;
  background: #a855f7;
  color: white;
}

/* 已提交提示 */
.submitted-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  color: #065f46;
  font-size: 13px;
  margin-top: 8px;
}
</style>

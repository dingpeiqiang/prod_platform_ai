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
          
          <el-select
            v-else-if="field.fieldType === 'select'"
            v-model="localFormData[field.fieldCode]"
            :disabled="field.disabled"
            :placeholder="'请选择' + field.fieldName"
            style="width: 100%"
            @change="handleInput(field.fieldCode, $event)"
          >
            <el-option
              v-for="option in (field.options || [])"
              :key="option"
              :label="option"
              :value="option"
            />
          </el-select>
          
          <el-input
            v-else-if="field.fieldType === 'textarea'"
            v-model="localFormData[field.fieldCode]"
            type="textarea"
            :rows="3"
            :disabled="field.disabled"
            :placeholder="'请输入' + field.fieldName"
            @input="handleInput(field.fieldCode, $event)"
          />
          
          <div v-if="field.recommend && field.recommend.length > 0" class="recommend-tags">
            <span class="recommend-label">推荐值：</span>
            <el-tag
              v-for="(rec, idx) in field.recommend"
              :key="idx"
              class="recommend-tag"
              @click="selectRecommend(field.fieldCode, rec)"
            >
              {{ rec }}
            </el-tag>
          </div>
        </template>
      </el-form-item>
      
      <el-form-item>
        <el-button @click="handleCancel">
          取消
        </el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          提交表单
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch, reactive } from 'vue'
import { ElMessage } from 'element-plus'

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
  }
})

const emit = defineEmits(['field-change', 'form-submit', 'submit', 'cancel'])

const localFormData = reactive({})
const submitting = ref(false)

watch(() => props.formData, (newData) => {
  Object.keys(newData).forEach(key => {
    localFormData[key] = newData[key]
  })
}, { deep: true, immediate: true })

watch(() => props.schema, (newSchema) => {
  if (newSchema && newSchema.fields) {
    newSchema.fields.forEach(field => {
      // 优先使用字段的 value 属性（用于外部更新）
      if (field.value !== undefined && field.value !== null) {
        localFormData[field.fieldCode] = field.value
      }
      // 其次使用默认值
      else if (field.defaultValue !== undefined && localFormData[field.fieldCode] === undefined) {
        localFormData[field.fieldCode] = field.defaultValue
      }
    })
  }
}, { immediate: true, deep: true })

const handleInput = (fieldCode, value) => {
  emit('field-change', fieldCode, value)
}

const selectRecommend = (fieldCode, value) => {
  localFormData[fieldCode] = value
  emit('field-change', fieldCode, value)
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
    
    if (field.rules && field.rules.length > 0) {
      field.rules.forEach(rule => {
        const value = localFormData[field.fieldCode]
        if (rule.rule_type === 'minLength' && value && value.length < rule.rule_value) {
          errors.push(rule.message)
        }
        if (rule.rule_type === 'pattern' && value && !new RegExp(rule.rule_value).test(value)) {
          errors.push(rule.message)
        }
        if (rule.rule_type === 'minimum' && value !== undefined && value !== null && Number(value) < rule.rule_value) {
          errors.push(rule.message)
        }
      })
    }
  })
  
  return errors
}

const handleCancel = () => {
  emit('cancel')
}

const handleSubmit = async () => {
  const errors = validateForm()
  
  if (errors.length > 0) {
    ElMessage.error(errors[0])
    return
  }
  
  submitting.value = true
  
  try {
    // 使用相对路径，通过 vite 代理转发
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
}

.recommend-tag:hover {
  background: #409eff;
  color: white;
}
</style>

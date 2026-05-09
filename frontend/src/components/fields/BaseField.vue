<template>
  <div class="base-field">
    <el-input
      v-if="fieldType === 'input'"
      :model-value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:model-value="handleInput"
    />

    <el-input-number
      v-else-if="fieldType === 'number'"
      :model-value="modelValue"
      :disabled="disabled"
      :min="0"
      style="width: 100%"
      @update:model-value="handleInput"
    />

    <el-date-picker
      v-else-if="fieldType === 'date'"
      :model-value="modelValue"
      type="date"
      :disabled="disabled"
      :placeholder="placeholder"
      style="width: 100%"
      @update:model-value="handleInput"
      value-format="YYYY-MM-DD"
    />

    <el-date-picker
      v-else-if="fieldType === 'datetime'"
      :model-value="modelValue"
      type="datetime"
      :disabled="disabled"
      :placeholder="placeholder"
      style="width: 100%"
      @update:model-value="handleInput"
      value-format="YYYY-MM-DD HH:mm:ss"
    />

    <el-select
      v-else-if="fieldType === 'select' && hasOptions"
      :model-value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder"
      style="width: 100%"
      @update:model-value="handleInput"
    >
      <el-option
        v-for="option in options"
        :key="getOptionValue(option)"
        :label="getOptionLabel(option)"
        :value="getOptionValue(option)"
      />
    </el-select>

    <el-input
      v-else-if="fieldType === 'select' && !hasOptions"
      :model-value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder + '（枚举选项缺失，已自动切换为文本输入）'"
      @update:model-value="handleInput"
    />

    <el-radio-group
      v-else-if="fieldType === 'radio' && hasOptions"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="handleInput"
    >
      <el-radio
        v-for="option in options"
        :key="getOptionValue(option)"
        :value="getOptionValue(option)"
      >
        {{ getOptionLabel(option) }}
      </el-radio>
    </el-radio-group>

    <el-checkbox-group
      v-else-if="fieldType === 'checkbox' && hasOptions"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="handleInput"
    >
      <el-checkbox
        v-for="option in options"
        :key="getOptionValue(option)"
        :label="getOptionLabel(option)"
        :value="getOptionValue(option)"
      />
    </el-checkbox-group>

    <el-input
      v-else-if="fieldType === 'checkbox' && !hasOptions"
      :model-value="modelValue"
      type="textarea"
      :disabled="disabled"
      :placeholder="'请输入选项（枚举选项缺失，已切换为文本输入）'"
      @update:model-value="handleInput"
    />

    <el-input
      v-else-if="fieldType === 'textarea'"
      :model-value="modelValue"
      type="textarea"
      :rows="3"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:model-value="handleInput"
    />

    <el-input
      v-else-if="fieldType === 'email'"
      :model-value="modelValue"
      :disabled="disabled"
      placeholder="请输入邮箱地址"
      @update:model-value="handleInput"
    />

    <el-input
      v-else-if="fieldType === 'phone'"
      :model-value="modelValue"
      :disabled="disabled"
      placeholder="请输入手机号"
      @update:model-value="handleInput"
    />

    <el-upload
      v-else-if="fieldType === 'file'"
      :disabled="disabled"
      :auto-upload="false"
      :on-change="(file) => handleFileChange(file)"
    >
      <el-button size="small" :disabled="disabled">点击上传</el-button>
    </el-upload>

    <div v-if="recommend && recommend.length > 0" class="recommend-tags">
      <span class="recommend-label">推荐：</span>
      <el-tooltip
        v-for="(rec, idx) in recommend"
        :key="idx"
        :content="normalizeRecommend(rec).reason || ''"
        :disabled="!normalizeRecommend(rec).reason"
        placement="top"
      >
        <el-tag
          class="recommend-tag"
          :class="'rec-source-' + normalizeRecommend(rec).source"
          @click="selectRecommend(rec)"
        >
          {{ normalizeRecommend(rec).label }}
          <span v-if="normalizeRecommend(rec).source === 'llm_rule'" class="rec-badge">AI</span>
        </el-tag>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { normalizeRecommend } from '../../utils'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Date, Array, Object],
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:model-value', 'field-change'])

const fieldType = computed(() => props.field.fieldType)
const fieldCode = computed(() => props.field.fieldCode)
const fieldName = computed(() => props.field.fieldName)
const recommend = computed(() => props.field.recommend || [])

const placeholder = computed(() => `请输入${fieldName.value}`)

const options = computed(() => {
  const field = props.field
  if (field.enumConfig) {
    if (field.enumConfig.type === 'static' && Array.isArray(field.enumConfig.options)) {
      return field.enumConfig.options
    }
    if (field.enumConfig.type === 'api') {
      const fallback = field.enumConfig.api?.fallback
      if (Array.isArray(fallback)) return fallback
    }
  }
  return field.options || []
})

const hasOptions = computed(() => options.value.length > 0)

const getOptionValue = (option) => option.value
const getOptionLabel = (option) => option.label

const handleInput = (value) => {
  emit('update:model-value', value)
  emit('field-change', fieldCode.value, value)
}

const handleFileChange = (file) => {
  const value = file.raw || file
  emit('update:model-value', value)
  emit('field-change', fieldCode.value, value)
}

const selectRecommend = (rec) => {
  const value = normalizeRecommend(rec).value
  emit('update:model-value', value)
  emit('field-change', fieldCode.value, value)
}
</script>

<style scoped>
.base-field {
  width: 100%;
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

.recommend-tag.rec-source-llm_rule {
  background-color: var(--color-primary-50);
  border-color: var(--color-primary-400);
  color: var(--color-primary-700);
}
.recommend-tag.rec-source-llm_rule:hover {
  background-color: var(--color-primary-500) !important;
  color: var(--text-inverse) !important;
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
  color: var(--text-inverse) !important;
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
  color: var(--text-inverse) !important;
}

.rec-badge {
  font-size: 10px;
  margin-left: 2px;
  padding: 0 3px;
  border-radius: var(--radius-full);
  background: var(--color-primary-500);
  color: var(--text-inverse);
}
</style>
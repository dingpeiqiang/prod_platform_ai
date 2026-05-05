/**
 * useFormState - 表单状态管理
 * 封装 currentFormId / currentFormSchema 状态
 */
import { ref, computed, watch } from 'vue'

export function useFormState(onStateChange) {
  const currentFormId = ref('')
  const currentFormSchema = ref(null)

  const hasForm = computed(() => !!currentFormSchema.value)
  const formCode = computed(() => currentFormSchema.value?.formCode || '')

  const setForm = (formId, formSchema) => {
    currentFormId.value = formId
    currentFormSchema.value = formSchema
  }

  const clearForm = () => {
    currentFormId.value = ''
    currentFormSchema.value = null
  }

  const updateFormSchema = (schema) => {
    currentFormSchema.value = schema
  }

  watch([currentFormId, currentFormSchema], () => {
    onStateChange?.({ formId: currentFormId.value, formSchema: currentFormSchema.value })
  })

  return {
    currentFormId,
    currentFormSchema,
    hasForm,
    formCode,
    setForm,
    clearForm,
    updateFormSchema
  }
}
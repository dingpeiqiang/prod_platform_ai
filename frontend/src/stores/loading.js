import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLoadingStore = defineStore('loading', () => {
  const isLoading = ref(false)
  const loadingText = ref('加载中...')

  const show = (text = '加载中...') => {
    isLoading.value = true
    loadingText.value = text
  }

  const hide = () => {
    isLoading.value = false
  }

  const updateText = (text) => {
    loadingText.value = text
  }

  return {
    isLoading,
    loadingText,
    show,
    hide,
    updateText
  }
})

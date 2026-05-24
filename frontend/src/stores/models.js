/**
 * models store - Pinia 状态管理
 * 管理可用模型列表，所有组件共享
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useModelsStore = defineStore('models', () => {
  // ── 状态 ──────────────────────────────────
  const models = ref([])  // 模型列表
  const loading = ref(false)  // 加载中
  const lastLoaded = ref(0)  // 最后加载时间（用于缓存）
  const loadError = ref(null)  // 加载错误

  // ── 计算属性 ──────────────────────────────────
  const modelOptions = computed(() => {
    return models.value.map(m => ({
      value: m.id,
      label: `${m.name} (${m.providerName || m.provider})`,
      raw: m
    }))
  })

  const hasModels = computed(() => models.value.length > 0)

  // ── 操作 ──────────────────────────────────
  /**
   * 加载可用模型列表
   * @param {boolean} force - 是否强制刷新（忽略缓存）
   */
  const loadModels = async (force = false) => {
    const now = Date.now()
    const cacheTime = 5 * 60 * 1000  // 缓存5分钟

    // 如果不是强制刷新，且5分钟内已加载过，则直接返回
    if (!force && hasModels.value && (now - lastLoaded.value) < cacheTime) {
      return models.value
    }

    if (loading.value) {
      return null
    }

    loading.value = true
    loadError.value = null

    try {
      const response = await fetch('/api/v1/chat/model/available')
      const result = await response.json()

      if (result.success && result.models) {
        models.value = result.models
        lastLoaded.value = now
        loadError.value = null
      } else {
        throw new Error(result.message || '加载模型列表失败')
      }
    } catch (e) {
      console.error('加载可用模型列表失败:', e)
      loadError.value = e.message || '加载失败'
    } finally {
      loading.value = false
    }

    return models.value
  }

  /**
   * 根据模型ID获取模型信息
   */
  const getModelById = (modelId) => {
    return models.value.find(m => m.id === modelId) || null
  }

  /**
   * 清空模型列表
   */
  const clearModels = () => {
    models.value = []
    lastLoaded.value = 0
    loadError.value = null
  }

  // ── 导出 ──────────────────────────────────
  return {
    models, loading, lastLoaded, loadError,
    modelOptions, hasModels,
    loadModels, getModelById, clearModels
  }
})


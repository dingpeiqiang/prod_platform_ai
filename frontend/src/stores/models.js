/**
 * models store - Pinia 状态管理
 * 管理可用模型列表，所有组件共享
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const PROVIDER_LABELS = {
  openai: 'OpenAI Compatible',
  azure: 'Azure OpenAI',
  custom: 'Custom OpenAI Compatible',
  local: 'Local / Mock'
}

function getProviderLabel(provider) {
  return PROVIDER_LABELS[provider] || provider || '自定义'
}

/**
 * 从 localStorage 读取兜底模型配置
 * 当后端接口不可用时，使用本地缓存的配置作为兜底
 */
function buildFallbackModels() {
  try {
    const raw = localStorage.getItem('chat_model_config')
    if (raw) {
      const cfg = JSON.parse(raw)
      if (cfg.model) {
        const provider = cfg.provider || 'custom'
        return [{
          id: `${provider}-${cfg.model}`,
          provider,
          providerName: getProviderLabel(provider),
          name: cfg.model,
          isDefault: true
        }]
      }
    }
  } catch {}
  return []
}

export const useModelsStore = defineStore('models', () => {
  // ── 状态 ──────────────────────────────────
  const models = ref([])  // 模型列表
  const loading = ref(false)  // 加载中
  const lastLoaded = ref(0)  // 最后加载时间（用于缓存）
  const loadError = ref(null)  // 加载错误
  let loadingPromise = null  // 进行中的加载请求（用于并发去重，避免丢弃刷新）

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

    // 并发去重：已有进行中的请求时复用同一个 promise，
    // 避免旧逻辑 `if (loading) return null` 把 force 刷新请求直接丢弃。
    if (loadingPromise) {
      return loadingPromise
    }

    loadingPromise = (async () => {
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
        // 接口失败且无缓存数据时，尝试从 localStorage 读取兜底配置
        if (models.value.length === 0) {
          models.value = buildFallbackModels()
        }
      } finally {
        loading.value = false
        loadingPromise = null
      }

      return models.value
    })()

    return loadingPromise
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

  /**
   * 手动添加模型配置到列表（用于保存配置后立即更新）
   */
  const addModelConfig = (config) => {
    const provider = config.provider || 'custom'
    const modelId = `${provider}-${config.model}`
    const existingIndex = models.value.findIndex(m => m.id === modelId)
    const newModel = {
      id: modelId,
      provider,
      providerName: getProviderLabel(provider),
      name: config.model,
      isDefault: true
    }
    if (existingIndex >= 0) {
      models.value[existingIndex] = newModel
    } else {
      models.value.unshift(newModel)
    }
    lastLoaded.value = Date.now()
  }

  // ── 导出 ──────────────────────────────────
  return {
    models, loading, lastLoaded, loadError,
    modelOptions, hasModels,
    loadModels, getModelById, clearModels, addModelConfig
  }
})


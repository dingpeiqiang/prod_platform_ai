<template>
  <div class="model-selector">
    <div class="selector-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" />
        <circle cx="12" cy="12" r="4" />
        <circle cx="12" cy="12" r="1" />
      </svg>
      <span class="selector-title">模型选择</span>
    </div>

    <div class="selector-desc">选择一个可用模型，列表最后一项可进入模型管理。</div>

    <div v-if="loading" class="loading-state">正在加载模型列表...</div>
    <div v-else class="model-list">
      <button
        v-for="model in models"
        :key="model.id"
        type="button"
        class="model-item"
        :class="{ active: model.id === selectedModelId, manage: model.manage }"
        @click="handleSelect(model)"
      >
        <div class="model-item-main">
          <span class="model-item-name">{{ model.name }}</span>
          <span class="model-item-provider">{{ model.providerName || model.provider || '' }}</span>
        </div>
        <span v-if="model.isDefault" class="model-badge">默认</span>
      </button>
    </div>

    <div v-if="statusMessage" class="status-message" :class="statusType">{{ statusMessage }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useModelsStore } from '@/stores/models.js'

const emit = defineEmits(['modelChange', 'open-model-config'])

const modelsStore = useModelsStore()
const loading = computed(() => modelsStore.loading)
const models = computed(() => [
  ...modelsStore.models,
  { id: '__manage__', name: '管理模型', providerName: '配置连接与参数', manage: true }
])
const selectedModelId = ref('')
const statusMessage = ref('')
const statusType = ref('')
const MODEL_ID_KEY = 'chat_selected_model_id'

const showStatus = (message, type = '') => {
  statusMessage.value = message
  statusType.value = type
  if (message) {
    setTimeout(() => {
      if (statusMessage.value === message) statusMessage.value = ''
    }, 1800)
  }
}

const loadModels = async (force = false) => {
  await modelsStore.loadModels(force)
  const storeModels = modelsStore.models
  const savedId = localStorage.getItem(MODEL_ID_KEY)
  const active = savedId && storeModels.some(m => m.id === savedId)
    ? savedId
    : (storeModels.find(m => m.isDefault)?.id || storeModels[0]?.id || '')
  selectedModelId.value = active && active !== '__manage__' ? active : (storeModels[0]?.id || '')
  if (modelsStore.loadError && storeModels.length > 0) {
    showStatus('模型列表加载失败，已显示本地缓存', 'warn')
  }
}

const handleSelect = (model) => {
  if (model.manage) {
    emit('open-model-config')
    return
  }
  selectedModelId.value = model.id
  localStorage.setItem(MODEL_ID_KEY, model.id)
  emit('modelChange', {
    provider: model.provider || 'custom',
    model: model.name,
    providerName: model.providerName || ''
  })
  showStatus(`已选择 ${model.name}`, 'success')
}

onMounted(loadModels)

watch(() => modelsStore.models, (newModels) => {
  if (newModels.length === 0) return
  const savedId = localStorage.getItem(MODEL_ID_KEY)
  const active = savedId && newModels.some(m => m.id === savedId)
    ? savedId
    : (newModels.find(m => m.isDefault)?.id || newModels[0]?.id || '')
  selectedModelId.value = active && active !== '__manage__' ? active : (newModels[0]?.id || '')
}, { deep: true })
</script>

<style scoped>
.model-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.selector-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.selector-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

.loading-state {
  padding: 12px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-secondary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-input);
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
  transition: all var(--transition-fast);
}

.model-item:hover {
  border-color: var(--color-primary-500);
  background: var(--bg-hover);
}

.model-item.active {
  border-color: var(--color-primary-500);
  background: rgba(91, 124, 250, 0.08);
}

.model-item.manage {
  background: linear-gradient(135deg, rgba(91, 124, 250, 0.08), rgba(14, 165, 233, 0.08));
}

.model-item-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.model-item-name {
  font-size: 13px;
  font-weight: 600;
}

.model-item-provider {
  font-size: 11px;
  color: var(--text-secondary);
}

.model-badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(91, 124, 250, 0.12);
  color: var(--color-primary-600);
}

.status-message {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.status-message.success {
  background: rgba(34, 197, 94, 0.1);
  color: var(--color-success);
}

.status-message.warn {
  background: rgba(245, 158, 11, 0.1);
  color: #b45309;
}

.status-message.error {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}
</style>

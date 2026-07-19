<template>
  <div class="app-container">
    <!-- 加载状态 -->
    <Loading :visible="isLoading" :text="loadingText" />
    
    <!-- 网络状态提示 -->
    <transition name="slide-down">
      <div v-if="!isOnline" class="network-banner offline">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="1" y1="1" x2="23" y2="23"/>
          <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39"/>
          <path d="M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/>
        </svg>
        <span>网络已断开</span>
        <button @click="checkNetwork">重连</button>
      </div>
      <div v-else-if="!isBackendOnline" class="network-banner warning">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
          <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
        </svg>
        <span>服务器连接异常，正在重连...</span>
      </div>
    </transition>

    <!-- 模型配置弹窗 -->
    <div
      v-if="showModelConfig"
      class="model-selector-overlay"
      @click="closeModelConfig"
    >
      <div class="model-selector-popup" @click.stop>
        <div class="popup-header">
          <span class="popup-title">模型配置</span>
          <button type="button" class="popup-close" @click="closeModelConfig" title="关闭">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="popup-content">
          <ModelSelector @model-change="handleModelChange" @open-model-config="openModelConfigManager" />
        </div>
      </div>
    </div>

    <!-- 模型管理弹窗挂到 body，避免聊天区层叠上下文遮挡 -->
    <Teleport to="body">
      <div
        v-if="showModelManager"
        class="model-selector-overlay"
        @click.self="closeModelManager"
      >
        <div class="model-manager-popup" role="dialog" aria-modal="true" aria-label="模型管理">
          <div class="popup-header">
            <span class="popup-title">模型配置</span>
            <button type="button" class="popup-close" @click="closeModelManager" title="关闭">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="popup-content manager-content">
            <div class="manager-intro">
              <div class="manager-title">模型配置</div>
              <div class="manager-desc">这里的配置会同步到会话输入框的模型选择与发送参数。</div>
            </div>

            <div class="manager-grid">
              <label class="manager-field">
                <span>提供方</span>
                <ElSelect v-model="modelForm.provider" placeholder="请选择提供方" style="width: 100%;">
                  <ElOption v-for="option in providerOptions" :key="option.value" :label="option.label" :value="option.value" />
                </ElSelect>
              </label>
              <label class="manager-field">
                <span>模型名称</span>
                <input v-model="modelForm.model" type="text" placeholder="例如 gpt-4o-mini" />
              </label>
              <label class="manager-field manager-span-2">
                <span>API Key</span>
                <input v-model="modelForm.api_key" type="password" placeholder="可留空，保存在本地" />
              </label>
              <label class="manager-field manager-span-2">
                <span>Base URL</span>
                <input v-model="modelForm.base_url" type="text" placeholder="https://api.example.com/v1" />
              </label>
              <label class="manager-field">
                <span>温度</span>
                <input v-model.number="modelForm.temperature" type="number" min="0" max="2" step="0.1" />
              </label>
              <label class="manager-field">
                <span>最大输出 tokens</span>
                <input v-model.number="modelForm.max_tokens" type="number" min="128" step="64" />
              </label>
            </div>

            <div class="manager-actions">
              <button type="button" class="secondary-btn" :disabled="testingModel || savingConfig" @click="resetModelForm">恢复默认</button>
              <button type="button" class="secondary-btn" :disabled="testingModel || savingConfig" @click="testModelConnection">
                {{ testingModel ? '测试中…' : '测试连接' }}
              </button>
              <button type="button" class="primary-btn" :disabled="testingModel || savingConfig" @click="saveModelConfigFromManager">
                {{ savingConfig ? '保存中…' : '保存配置' }}
              </button>
            </div>
            <div v-if="testResult" class="manager-test-result" :class="testResult.success ? 'test-ok' : 'test-fail'">
              <span class="test-result-icon">{{ testResult.success ? '✓' : '✗' }}</span>
              <span class="test-result-text">{{ testResult.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 登录页 -->
    <LoginScreen v-if="!userStore.isLoggedIn" />

    <!-- 主界面 -->
    <div class="main-layout">
      <!-- 左侧导航侧边栏 -->
      <Sidebar
        :sessions="sessionList"
        :activeId="activeSessionId"
        :isSidebarVisible="isSidebarVisible"
        :username="userStore.username"
        :activeAssistant="activeAssistant"
        @new-session="onNewSession"
        @switch-session="onSwitchSession"
        @delete-session="deleteSession"
        @pin-session="pinSession"
        @share-session="shareSession"
        @rename-session="renameSession"
        @logout="handleLogout"
        @toggle-sidebar="toggleSidebar"
        @theme-toggle="toggleTheme"
        @select-assistant="onSelectAssistant"
        @model-change="onModelChange"
      />

      <!-- 中间主聊天区 -->
      <div class="main-content">
        <!-- 聊天区域 -->
        <div v-if="activeSessionId" class="chat-area">
          <!-- 顶部标题栏 -->
          <div class="chat-header">
            <div class="header-left">
              <span class="session-title">{{ activeSessionTitle }}</span>
            </div>
            
            <div class="header-actions">
              <button
                class="header-btn"
                title="商品列表"
                @click="showProductListPanel = true"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="8" y1="6" x2="21" y2="6"/>
                  <line x1="8" y1="12" x2="21" y2="12"/>
                  <line x1="8" y1="18" x2="21" y2="18"/>
                  <line x1="3" y1="6" x2="3.01" y2="6"/>
                  <line x1="3" y1="12" x2="3.01" y2="12"/>
                  <line x1="3" y1="18" x2="3.01" y2="18"/>
                </svg>
                <span v-if="products.length" class="header-badge">{{ products.length }}</span>
              </button>
              <button
                class="header-btn"
                title="生成配置草稿 / 智能稽核"
                :disabled="!activeFormCard || !currentProduct || (activeFormCard.formCode === 'offering_config' && !activeFormCard.compliancePass)"
                @click="handleRunAudit"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                </svg>
              </button>
              <button class="header-btn" @click="clearContext" title="清空上下文">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
              <button class="header-btn" @click="shareSession(activeSessionId)" title="分享">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>
                  <polyline points="16 6 12 2 8 6"/>
                  <line x1="12" y1="2" x2="12" y2="15"/>
                </svg>
              </button>
              <button class="header-btn" @click="activeSessionId = ''" title="关闭对话">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="chat-body">
            <div class="chat-main">
              <!-- 消息列表 -->
              <ChatMessageList
                ref="messageListRef"
                :messages="currentMessages"
                :showWelcome="showWelcome"
                @suggest="handleSuggest"
                @regenerate="handleRegenerate"
                @form-card-click="handleFormCardClick"
                @query-result-click="handleQueryResultClick"
                @intent-action="handleIntentAction"
              />

              <!-- 智读·批量映射清单（三列：原文 | 映射 | 场景/模板） -->
              <BatchMappingPanel
                :visible="showBatchPanel"
                :items="batchItems"
                :active-id="currentProductId"
                @select="handleBatchSelect"
                @apply-fix="handleBatchFix"
                @confirm-pass="handleBatchConfirm"
                @close="showBatchPanel = false"
              />

              <!-- 输入区域：宽度对齐消息列，不延伸到右侧配置画布下 -->
              <ChatInput
                ref="chatInputRef"
                v-model="inputMessage"
                :disabled="isSending"
                :currentSkill="currentSkill"
                :assistant-mode="activeAssistant"
                :placeholder="currentSkill ? skillPlaceholderMap[currentSkill] || '描述你想做的事...' : '描述你想做的事...'"
                @send="handleSend"
                @stop="handleStop"
                @quick-action="handleQuickAction"
                @file-upload="handleFileUpload"
                @image-upload="handleImageUpload"
                @voice-record="handleVoiceRecord"
                @remove-skill="currentSkill = ''"
                @skill-select="applySkill"
                @open-model-config="openModelConfigManager"
              />
            </div>

            <!-- 本体配置画布 + 合规面板 -->
            <FormPanel
              v-if="activeFormCard"
              :form-schema="activeFormCard.formSchema"
              :form-id="activeFormCard.formId"
              :show-compliance="activeFormCard.formCode === 'offering_config' || !!activeFormCard.issues?.length || !!activeFormCard.inferredFields?.length"
              :require-compliance="activeFormCard.formCode === 'offering_config'"
              :issues="activeFormCard.issues || []"
              :compliance-pass="!!activeFormCard.compliancePass"
              :inferred-fields="activeFormCard.inferredFields || []"
              @field-change="handleProductFieldChange"
              @submit="handleGenerateDraft"
              @cancel="closeActiveForm"
            />

            <!-- 运营助手：根因路径面板 -->
            <OpsRootCausePanel
              :visible="showRootCausePanel"
              :result="rootCauseResult"
              @close="showRootCausePanel = false"
              @create-work-order="handleCreateWorkOrder"
            />

            <!-- 运营助手：风险稽核面板 -->
            <OpsRiskAuditPanel
              :visible="showRiskAuditPanel"
              :result="riskAuditResult"
              @close="showRiskAuditPanel = false"
              @re-audit="handleRiskReAudit"
            />
          </div>

          <ProductListPanel
            v-model="showProductListPanel"
            :products="products"
            :current-product-id="currentProductId"
            @select="handleProductSelect"
            @copy="handleProductCopy"
            @edit="handleProductSelect"
            @delete="handleProductDelete"
          />
          <AuditPanel
            v-model="showAuditPanel"
            :product-name="currentProduct?.name || ''"
            :results="auditResults"
            :phase="auditPhase"
            :has-error="auditResults.some(r => r.type === 'error')"
            @close="handleAuditClose"
          />
        </div>

        <!-- 管理台：仅挂已迁到 Spring Boot 的模块；workflow / mcp / kb / langchain 暂不暴露 -->
        <div v-else-if="activeManager" class="manager-area">
          <SceneManager v-if="activeManager === 'scene'" @go-back="closeManager" />
          <PromptManager v-else-if="activeManager === 'prompt'" @go-back="closeManager" />
          <OntologyManager v-else-if="activeManager === 'ontology'" @goBack="closeManager" />
        </div>

        <!-- 首页/欢迎页 -->
        <div v-else class="welcome-area">

          <DashboardHome
            :assistant-mode="activeAssistant"
            @send-message="onSendMessageFromHome"
            @switch-chat="onSwitchChat"
            @create-session="onNewSession"
            @launch-skill="handleHomeSkillLaunch"
            @guided-demo="handleGuidedDemo"
            @open-scene-manager="openSceneManager"
            @open-prompt-manager="openPromptManager"
            @open-ontology-manager="openOntologyManager"
            @open-model-config="openModelConfigManager"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import Sidebar from './components/Sidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInput from './components/ChatInput.vue'
import DashboardHome from './components/DashboardHome.vue'
import LoginScreen from './components/LoginScreen.vue'
import Loading from './components/Loading.vue'
import FormPanel from './components/FormPanel.vue'
import ProductListPanel from './components/ProductListPanel.vue'
import AuditPanel from './components/AuditPanel.vue'
import BatchMappingPanel from './components/BatchMappingPanel.vue'
import OpsRootCausePanel from './components/OpsRootCausePanel.vue'
import OpsRiskAuditPanel from './components/OpsRiskAuditPanel.vue'
import SceneManager from './components/SceneManager.vue'
import PromptManager from './components/PromptManager.vue'
import OntologyManager from './components/OntologyManager.vue'
import ModelSelector from './components/ModelSelector.vue'
import { useUserStore } from './stores/user'
import { useLoadingStore } from './stores/loading'
import { useModelsStore } from './stores/models.js'
import { useProductConfig } from './composables/useProductConfig.js'
import { mockProducts } from './data/productMockData.js'
import { createStreamingPlaceholder, playSimulatedReply, sleep } from './utils/simulateReply.js'
import { 
  createSession as apiCreateSession, 
  getSessions as apiGetSessions, 
  deleteSession as apiDeleteSession,
  sendMessage as apiSendMessage,
  updateSessionTitle as apiUpdateSessionTitle 
} from './services/chatApi.js'

const userStore = useUserStore()
const loadingStore = useLoadingStore()
const modelsStore = useModelsStore()
const { isLoading, loadingText } = storeToRefs(loadingStore)
const productConfig = useProductConfig()
const {
  products,
  currentProductId,
  currentProduct,
  showProductListPanel,
  showAuditPanel,
  auditResults,
  auditPhase,
  batchItems,
  showBatchPanel,
  showRootCausePanel,
  showRiskAuditPanel,
  rootCauseResult,
  riskAuditResult,
} = productConfig
const activeFormCard = ref(null)
const lastGeneratedDraft = ref(null)

// 状态
const sessions = ref([])
const activeSessionId = ref('')
const isSidebarVisible = ref(true)
const isOnline = ref(navigator.onLine)
const isBackendOnline = ref(true)
const inputMessage = ref('')
const isSending = ref(false)
const currentSkill = ref('')
const activeAssistant = ref(localStorage.getItem('active_assistant') || 'rd')
const skillLabelMap = { query: 'AI智查', file: '智读·批量生成', chat: '智聊·对话配置', ops: '运营助手' }
const skillPlaceholderMap = {
  query: 'AI智查：输入商品名称、编码或条件，例如：查近30天大学生套餐配置',
  file: '智读·批量生成：例如：导入校园迎新方案 / 确认通过项入库',
  chat: '智聊·对话配置：例如：给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
  ops: '运营助手：例如：分析家庭融合畅享128本月收入下滑原因 / 筛查所有在架的0元资费风险商品'
}
const messageListRef = ref(null)
const chatInputRef = ref(null)

const SESSIONS_KEY = 'chat_sessions'
const ACTIVE_SESSION_KEY = 'chat_active_session'

// 计算属性
const sessionList = computed(() => 
  [...sessions.value].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return b.updatedAt - a.updatedAt
  })
)

const activeSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.id === activeSessionId.value)
  return s?.title || '新对话'
})

const currentMessages = computed(() => {
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  return session?.messages || []
})

const showWelcome = computed(() => 
  !activeSessionId.value || currentMessages.value.length === 0
)

// 方法
const genId = () => `sess_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`

const loadSessions = () => {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (raw) {
      sessions.value = JSON.parse(raw).map(s => ({
        ...s,
        messages: s.messages || []
      }))
    }
  } catch (e) {
    console.error('加载会话失败:', e)
  }
}

const saveSessions = () => {
  try {
    const sessionsToSave = sessions.value.map(s => ({
      ...s,
      messages: s.messages.slice(-50) // 只保留最近50条
    }))
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessionsToSave))
  } catch (e) {
    console.error('保存会话失败:', e)
  }
}

const saveActiveSessionId = () => {
  try {
    localStorage.setItem(ACTIVE_SESSION_KEY, activeSessionId.value)
  } catch {}
}

const createLocalSession = () => {
  const now = Date.now()
  const s = {
    id: genId(),
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    messages: [],
    pinned: false
  }
  sessions.value.unshift(s)
  activeSessionId.value = s.id
  saveSessions()
  saveActiveSessionId()
  return s
}

const onNewSession = () => {
  activeSessionId.value = ''
  saveActiveSessionId()
}

const onSelectAssistant = (mode) => {
  if (!mode || (mode !== 'rd' && mode !== 'ops')) return
  activeAssistant.value = mode
  try {
    localStorage.setItem('active_assistant', mode)
  } catch {}
  activeSessionId.value = ''
  currentSkill.value = ''
  inputMessage.value = ''
  saveActiveSessionId()
  productConfig.resetState()
  activeFormCard.value = null
}

const onSwitchSession = (id) => {
  activeSessionId.value = id
  saveActiveSessionId()
  productConfig.resetState()
  activeFormCard.value = null
}

const deleteSession = async (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s?.dbSessionId) {
    await apiDeleteSession(s.dbSessionId)
  }
  sessions.value = sessions.value.filter(s => s.id !== id)
  saveSessions()
  if (activeSessionId.value === id) {
    activeSessionId.value = ''
    saveActiveSessionId()
  }
}

const pinSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) {
    s.pinned = !s.pinned
    s.updatedAt = Date.now()
    saveSessions()
  }
}

const renameSession = async (id, newTitle) => {
  const s = sessions.value.find(s => s.id === id)
  if (s) {
    s.title = newTitle
    s.updatedAt = Date.now()
    saveSessions()
    if (s.dbSessionId) {
      await apiUpdateSessionTitle(s.dbSessionId, newTitle)
    }
  }
}

const shareSession = (id) => {
  const s = sessions.value.find(s => s.id === id)
  if (s?.dbSessionId) {
    const shareUrl = `${window.location.origin}/chat/${s.dbSessionId}`
    navigator.clipboard.writeText(shareUrl).then(() => {
      alert('分享链接已复制')
    })
  }
}

const toggleSidebar = () => {
  isSidebarVisible.value = !isSidebarVisible.value
}

const toggleTheme = (isDark) => {
  document.documentElement.classList.toggle('dark', isDark)
}

const MODEL_CONFIG_KEY = 'chat_model_config'
const currentModelConfig = ref(null)

const loadModelConfig = async () => {
  const username = userStore.username
  // 优先从后端加载该用户的激活配置
  if (username) {
    try {
      const resp = await fetch(`/api/v1/llm-config/active/${encodeURIComponent(username)}`)
      const text = await resp.text()
      let result = null
      try {
        result = text ? JSON.parse(text) : null
      } catch (parseError) {
        throw new Error(`后端返回非 JSON 响应: ${parseError.message}`)
      }
      if (result?.success && result.config) {
        const cfg = result.config
        currentModelConfig.value = {
          provider: cfg.provider || 'custom',
          model: cfg.model || '',
          api_key: cfg.api_key || '',
          base_url: cfg.base_url || '',
          temperature: cfg.temperature ?? 0.3,
          max_tokens: cfg.max_tokens ?? 2048,
          thinking: !!cfg.thinking,
        }
        // 同步到 localStorage 作离线缓存
        try { localStorage.setItem(MODEL_CONFIG_KEY, JSON.stringify(currentModelConfig.value)) } catch {}
        return
      }
    } catch (e) {
      console.error('从后端加载模型配置失败，回退 localStorage:', e)
    }
  }
  // fallback：localStorage → 默认配置
  try {
    const raw = localStorage.getItem(MODEL_CONFIG_KEY)
    if (raw) {
      currentModelConfig.value = JSON.parse(raw)
      return
    }
    const response = await fetch('/api/v1/chat/model/default')
    const result = await response.json()
    if (result.success) {
      currentModelConfig.value = {
        provider: result.provider || result.config?.provider,
        model: result.model || result.config?.model,
        baseUrl: result.baseUrl || result.config?.baseUrl || result.config?.base_url,
      }
    }
  } catch (e) {
    console.error('加载模型配置失败:', e)
  }
}

const saveModelConfig = (config) => {
  try {
    localStorage.setItem(MODEL_CONFIG_KEY, JSON.stringify(config))
  } catch (e) {
    console.error('保存模型配置失败:', e)
  }
}

const onModelChange = (modelConfig) => {
  currentModelConfig.value = modelConfig
  saveModelConfig(modelConfig)
  try {
    const provider = modelConfig?.provider || 'custom'
    const model = modelConfig?.model
    if (model) {
      localStorage.setItem('chat_selected_model_id', `${provider}-${model}`)
    }
  } catch {}
  nextTick(() => chatInputRef.value?.reloadModels?.())
}

const handleModelChange = onModelChange

const showModelConfig = ref(false)
const showModelManager = ref(false)
const MODEL_DEBUG_PREFIX = '[ModelDebug][App]'

const modelDebug = (step, details = {}) => {
  console.log(`${MODEL_DEBUG_PREFIX} ${step}`, {
    timestamp: new Date().toISOString(),
    showModelConfig: showModelConfig.value,
    showModelManager: showModelManager.value,
    bodyDialogCount: typeof document === 'undefined'
      ? -1
      : document.body.querySelectorAll('.model-manager-popup').length,
    overlayCount: typeof document === 'undefined'
      ? -1
      : document.body.querySelectorAll('.model-selector-overlay').length,
    ...details,
  })
}

watch(showModelManager, (visible, previous) => {
  modelDebug('showModelManager state changed', { previous, visible })
  nextTick(() => {
    const dialog = document.body.querySelector('.model-manager-popup')
    const overlay = dialog?.closest('.model-selector-overlay')
    modelDebug('model manager DOM inspection after state change', {
      dialogExists: !!dialog,
      dialogConnected: !!dialog?.isConnected,
      dialogDisplay: dialog ? getComputedStyle(dialog).display : null,
      dialogVisibility: dialog ? getComputedStyle(dialog).visibility : null,
      dialogOpacity: dialog ? getComputedStyle(dialog).opacity : null,
      dialogZIndex: dialog ? getComputedStyle(dialog).zIndex : null,
      dialogRect: dialog?.getBoundingClientRect?.().toJSON?.(),
      overlayExists: !!overlay,
      overlayDisplay: overlay ? getComputedStyle(overlay).display : null,
      overlayVisibility: overlay ? getComputedStyle(overlay).visibility : null,
      overlayOpacity: overlay ? getComputedStyle(overlay).opacity : null,
      overlayZIndex: overlay ? getComputedStyle(overlay).zIndex : null,
      overlayRect: overlay?.getBoundingClientRect?.().toJSON?.(),
      activeElement: document.activeElement?.tagName,
    })
  })
})

const modelForm = ref({
  provider: 'custom',
  model: '',
  api_key: '',
  base_url: '',
  temperature: 0.3,
  max_tokens: 2048,
  thinking: false,
})

const providerOptions = [
  { label: 'OpenAI Compatible', value: 'openai' },
  { label: 'Azure OpenAI', value: 'azure' },
  { label: 'Custom OpenAI Compatible', value: 'custom' },
  { label: 'Local / Mock', value: 'local' },
]

// 模型配置弹窗的测试/保存状态
const testingModel = ref(false)
const savingConfig = ref(false)
const testResult = ref(null) // { success: boolean, message: string, latency_ms?: number }

const syncModelForm = (config = {}) => {
  modelForm.value = {
    provider: config.provider || 'custom',
    model: config.model || '',
    api_key: config.api_key || config.apiKey || '',
    base_url: config.base_url || config.baseUrl || '',
    temperature: config.temperature ?? 0.3,
    max_tokens: config.max_tokens || config.maxTokens || 2048,
    thinking: !!config.thinking,
  }
}

const openModelConfig = () => {
  showModelConfig.value = true
}

const closeModelConfig = () => {
  showModelConfig.value = false
}

const closeModelManager = (event) => {
  modelDebug('closeModelManager called', {
    eventType: event?.type,
    target: event?.target?.tagName,
    currentTarget: event?.currentTarget?.tagName,
  })
  showModelManager.value = false
}

const openModelConfigManager = (payload) => {
  modelDebug('openModelConfigManager handler entered', {
    payload,
    currentModelConfig: currentModelConfig.value,
    bodyConnected: typeof document !== 'undefined' && document.body.isConnected,
  })
  try {
    syncModelForm(currentModelConfig.value || {})
    modelDebug('model form synchronized', {
      provider: modelForm.value.provider,
      model: modelForm.value.model,
      hasApiKey: !!modelForm.value.api_key,
      baseUrl: modelForm.value.base_url,
    })
    showModelConfig.value = false
    showModelManager.value = true
    modelDebug('showModelManager assigned true')
    nextTick(() => {
      modelDebug('openModelConfigManager nextTick completed', {
        dialogHtml: document.body.querySelector('.model-manager-popup')?.outerHTML?.slice(0, 300),
      })
    })
  } catch (error) {
    console.error(`${MODEL_DEBUG_PREFIX} openModelConfigManager failed`, error)
  }
}

const resetModelForm = () => {
  syncModelForm(currentModelConfig.value || {})
}

const saveModelConfigFromManager = async () => {
  const username = userStore.username
  if (!username) {
    testResult.value = { success: false, message: '未登录，无法保存配置到后端' }
    return
  }
  savingConfig.value = true
  testResult.value = null
  const nextConfig = {
    ...modelForm.value,
    temperature: Number(modelForm.value.temperature) || 0,
    max_tokens: Number(modelForm.value.max_tokens) || 0,
    thinking: !!modelForm.value.thinking,
  }
  try {
    const resp = await fetch('/api/v1/llm-config/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        user_identifier: username,
        provider: nextConfig.provider || 'custom',
        model: nextConfig.model,
        api_key: nextConfig.api_key || null,
        base_url: nextConfig.base_url || null,
        temperature: nextConfig.temperature,
        max_tokens: nextConfig.max_tokens,
        thinking: nextConfig.thinking,
      }),
    })
    const result = await resp.json()
    if (result.success) {
      currentModelConfig.value = nextConfig
      saveModelConfig(nextConfig)
      const modelId = `${nextConfig.provider || 'custom'}-${nextConfig.model}`
      try {
        localStorage.setItem('chat_selected_model_id', modelId)
      } catch {}
      showModelManager.value = false
      testResult.value = null
      modelsStore.addModelConfig(nextConfig)
      nextTick(() => chatInputRef.value?.reloadModels?.())
    } else {
      testResult.value = { success: false, message: result.message || '保存失败' }
    }
  } catch (e) {
    testResult.value = { success: false, message: `保存失败: ${e.message || e}` }
  } finally {
    savingConfig.value = false
  }
}

const testModelConnection = async () => {
  testingModel.value = true
  testResult.value = null
  try {
    const resp = await fetch('/api/v1/llm-config/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: modelForm.value.provider || 'custom',
        model: modelForm.value.model || '',
        api_key: modelForm.value.api_key || null,
        base_url: modelForm.value.base_url || null,
      }),
    })

    const rawText = await resp.text()
    const result = rawText ? JSON.parse(rawText) : { success: false, message: '测试接口返回空响应' }
    testResult.value = {
      success: !!result.success,
      message: result.message || (result.success ? '连接成功' : '连接失败'),
      latency_ms: result.latency_ms,
    }
  } catch (e) {
    testResult.value = { success: false, message: `测试请求失败: ${e.message || e}` }
  } finally {
    testingModel.value = false
  }
}

const appendAssistantMessages = (session, msgs = []) => {
  msgs.forEach((m) => {
    session.messages.push({
      ...m,
      id: m.id || genId(),
      timestamp: m.timestamp || Date.now(),
      done: true,
    })
  })
  session.updatedAt = Date.now()
  saveSessions()
}

const applyFormCard = (formCard) => {
  if (!formCard) return
  activeFormCard.value = {
    ...formCard,
    issues: formCard.issues || [],
    compliancePass: !!formCard.compliancePass,
    inferredFields: formCard.inferredFields || [],
  }
}

const syncActiveFormFromProduct = () => {
  const product = currentProduct.value
  if (!product || !activeFormCard.value) return
  const formCard = productConfig.buildProductFormCard(product)
  applyFormCard(formCard)
}

const handleBatchSelect = (productId) => {
  const formCard = productConfig.selectProduct(productId)
  applyFormCard(formCard)
}

const findBatchItemByName = (keyword) =>
  batchItems.value.find((i) => (i.draft?.offeringName || '').includes(keyword))

const handleBatchFix = async ({ productId, fixKey }, options = {}) => {
  const keepSending = !!options.keepSending
  if (!keepSending) isSending.value = true
  try {
    const formCard = await productConfig.applyBatchFix(productId, fixKey)
    if (formCard) applyFormCard(formCard)
    const session = sessions.value.find(s => s.id === activeSessionId.value)
    const item = batchItems.value.find((i) => i.productId === productId)
    if (session && item) {
      const evidenceHint = (item.issues || [])
        .flatMap((iss) => iss.evidence || [])
        .slice(0, 3)
        .join(' · ')
      await playProductReply(session, {
        thinkingSteps: [
          { type: 'llm', content: `对人机修正项「${item.draft?.offeringName || productId}」应用修复：${fixKey}` },
          {
            type: 'ontology',
            title: '本体推理',
            content: item.compliancePass
              ? '重跑本体合规：结论通过，状态变为「通过」'
              : `重跑本体合规：仍有问题${evidenceHint ? `（${evidenceHint}）` : ''}，保持「待修正」`,
          },
        ],
        content: item.compliancePass
          ? `「${item.draft?.offeringName}」已修正并通过合规，可参与确认入库。`
          : `「${item.draft?.offeringName}」已重跑校验，仍待修正：${(item.issues || []).map((i) => i.ruleId).join('、') || '见合规面板'}。`,
        formCard,
      })
    }
  } finally {
    if (!keepSending) isSending.value = false
  }
}

const handleBatchConfirm = async (options = {}) => {
  const keepSending = !!options.keepSending
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (!session) return
  if (!keepSending) isSending.value = true
  try {
    const playbook = productConfig.confirmPassedDrafts()
    // 回写 draftId 到批量清单
    batchItems.value = batchItems.value.map((it) => {
      const p = products.value.find((x) => x.id === it.productId)
      return p?.draftId ? { ...it, draftId: p.draftId, status: p.status === 'submitted' ? '已入库' : it.status } : it
    })
    showBatchPanel.value = true
    await playProductReply(session, playbook)
  } finally {
    if (!keepSending) isSending.value = false
  }
}

/** 合规通过后生成可落库配置草稿 JSON */
const handleGenerateDraft = async () => {
  if (!currentProduct.value) return
  if (activeFormCard.value?.formCode === 'offering_config' && !activeFormCard.value.compliancePass) {
    return
  }
  productConfig.saveDraft()
  const draft = currentProduct.value.ontologyDraft || currentProduct.value.data || {}
  const draftId = `DRAFT-${Date.now().toString(36).toUpperCase()}-${Math.random().toString(36).slice(2, 5).toUpperCase()}`
  currentProduct.value.draftId = draftId
  currentProduct.value.status = 'submitted'
  currentProduct.value.auditStatus = 'pass'
  lastGeneratedDraft.value = { draftId, draft }

  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    await playProductReply(session, {
      thinkingSteps: [
        '核对 compliancePass=true（R-C08）',
        '序列化 OfferingConfig 草稿字段',
        `Mock 落库生成 draftId=${draftId}`,
      ],
      content:
        `已生成配置草稿 **\`${draftId}\`**，可导入产商品中心。\n\n` +
        '```json\n' +
        JSON.stringify(
          {
            draftId,
            offeringName: draft.offeringName,
            offeringType: draft.offeringType,
            bizScenario: draft.bizScenario,
            targetUser: draft.targetUser,
            channelScope: draft.channelScope,
            monthlyFee: draft.monthlyFee,
            includeVoice: draft.includeVoice,
            includeData: draft.includeData,
            includeBroadband: draft.includeBroadband,
            mutexGroup: draft.mutexGroup,
            basedOnTemplate: draft.basedOnTemplate,
            fillSources: draft.fillSources,
          },
          null,
          2,
        ) +
        '\n```\n\n冲突不是事后稽核，而是**配置当下被本体拦住**。',
    })
  }
  if (activeFormCard.value) {
    activeFormCard.value = { ...activeFormCard.value, status: 'submitted' }
  }
}

const handleRunAudit = async () => {
  if (!currentProduct.value) return
  // 配置本体场景：优先走「生成配置草稿」
  if (activeFormCard.value?.formCode === 'offering_config') {
    await handleGenerateDraft()
    return
  }
  showAuditPanel.value = true
  auditPhase.value = 'progress'
  auditResults.value = []
  await new Promise((r) => setTimeout(r, 4500))
  const { hasError } = await productConfig.runAudit()
  auditPhase.value = 'results'
  syncActiveFormFromProduct()
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session && !hasError) {
    await playProductReply(session, {
      thinkingSteps: [
        '汇总稽核结果',
        '核对 HIGH 级问题为空且必填齐全',
        '标记 compliancePass=true（R-C08）',
      ],
      content: '本体合规通过（R-C08），配置草稿可提交。右侧画布已同步为可提交状态。',
    })
  }
}

const scrollChat = () => {
  nextTick(() => messageListRef.value?.scrollToBottom(true))
}

const playProductReply = async (session, playbook = {}) => {
  const msg = createStreamingPlaceholder(genId)
  session.messages.push(msg)
  session.updatedAt = Date.now()
  scrollChat()

  await playSimulatedReply({
    msg,
    thinkingSteps: playbook.thinkingSteps || [],
    content: playbook.content || '',
    formCard: playbook.formCard || null,
    queryResults: playbook.queryResults || null,
    onTick: scrollChat,
  })

  if (playbook.nextSteps?.length) {
    msg.nextSteps = playbook.nextSteps
  }
  if (playbook.formCard) applyFormCard(playbook.formCard)
  if (playbook.showRootCausePanel) showRootCausePanel.value = true
  if (playbook.showRiskAuditPanel) showRiskAuditPanel.value = true
  if (playbook.rootCauseResult) rootCauseResult.value = playbook.rootCauseResult
  if (playbook.riskAuditResult) riskAuditResult.value = playbook.riskAuditResult
  session.updatedAt = Date.now()
  saveSessions()
  scrollChat()
  return msg
}

const handleProductScenario = async (scenario, text, attachments, session) => {
  if (scenario === 'query') {
    await playProductReply(session, productConfig.simulateQuery(text))
    return
  }
  if (scenario === 'file-parse') {
    const doc = attachments?.find((a) => /\.(docx|pdf|xlsx|doc|md)$/i.test(a.name || ''))
    const playbook = await productConfig.simulateFileParse(
      doc?.name || '校园迎新产商品方案_2026.md',
      doc?.size || 12 * 1024,
    )
    await playProductReply(session, playbook)
    return
  }
  if (scenario === 'confirm-batch') {
    await playProductReply(session, productConfig.confirmPassedDrafts())
    return
  }
  if (scenario === 'root-cause') {
    await playProductReply(session, await productConfig.runRootCauseAnalysis(text))
    return
  }
  if (scenario === 'risk-audit') {
    await playProductReply(session, await productConfig.runRiskAuditFlow())
    return
  }
  if (scenario === 'chat-generate') {
    await playProductReply(session, await productConfig.generateProductFromChat(text))
  }
}

// 消息处理
const handleSend = async ({ text, attachments, modelConfig }) => {
  if (!text.trim() && !attachments?.length) return

  let session = sessions.value.find(s => s.id === activeSessionId.value)
  if (!session) {
    session = createLocalSession()
  }
  if (currentSkill.value && !text.trim()) {
    const fallback = skillPlaceholderMap[currentSkill.value]
    if (fallback) {
      inputMessage.value = fallback
    }
  }

  const userMessage = {
    id: genId(),
    role: 'user',
    content: text,
    attachments: attachments || [],
    timestamp: Date.now()
  }
  session.messages.push(userMessage)
  session.updatedAt = Date.now()
  saveSessions()

  isSending.value = true
  try {
    // 本体 MVP / 产品配置场景拦截
    const docAttach = attachments?.find((a) => /\.(docx|pdf|xlsx|doc|md)$/i.test(a.name || ''))
    let scenario = productConfig.detectScenario(text)
    if (!scenario && docAttach) scenario = 'file-parse'
    if (!scenario && currentSkill.value === 'query') scenario = 'query'
    if (!scenario && currentSkill.value === 'file') scenario = 'file-parse'
    if (!scenario && currentSkill.value === 'chat') scenario = 'chat-generate'
    if (!scenario && currentSkill.value === 'ops') scenario = 'root-cause'

    if (scenario) {
      await handleProductScenario(scenario, text, attachments, session)
      if (session.title === '新对话' && text) {
        const newTitle = text.slice(0, 20) + (text.length > 20 ? '...' : '')
        renameSession(session.id, newTitle)
      }
      return
    }

    const effectiveModelConfig = modelConfig || currentModelConfig.value || null
    const response = await apiSendMessage({
      sessionId: session.dbSessionId,
      message: text,
      attachments,
      modelConfig: effectiveModelConfig
    })

    const aiMessage = {
      id: genId(),
      role: 'assistant',
      content: response?.content || response?.reply || '已收到您的消息。',
      timestamp: Date.now(),
      done: true
    }
    session.messages.push(aiMessage)
    saveSessions()

    if (session.title === '新对话' && text) {
      const newTitle = text.slice(0, 20) + (text.length > 20 ? '...' : '')
      renameSession(session.id, newTitle)
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    session.messages.push({
      id: genId(),
      role: 'assistant',
      content: `处理失败：${error.message || error}`,
      timestamp: Date.now(),
      done: true
    })
    saveSessions()
  } finally {
    isSending.value = false
    nextTick(() => messageListRef.value?.scrollToBottom(true))
  }
}

const handleStop = () => {
  // 停止生成
  isSending.value = false
}

const applySkill = (skill) => {
  currentSkill.value = skill || ''
}

const handleSuggest = async (content) => {
  if (content === '生成配置草稿') {
    await handleGenerateDraft()
    return
  }
  if (content === '确认通过项入库' || content === '确认通过项入库（Mock）') {
    await handleBatchConfirm()
    return
  }
  if (content === '生成产品优化工单草稿' || content === '打开证据链') {
    showRootCausePanel.value = true
    if (content === '生成产品优化工单草稿' && rootCauseResult.value?.workOrder) {
      await handleCreateWorkOrder(rootCauseResult.value.workOrder)
    }
    return
  }
  if (content === '查看OF-RISK-001证据' || content === '筛选建议下架' || content === '导出风险清单') {
    showRiskAuditPanel.value = true
    return
  }
  if (content === '零销阈值改为90天') {
    await handleRiskReAudit({ zeroSalesShelfDays: 90 })
    return
  }
  // 智读批量：下一步芯片直接落到对应修正动作
  const batchFixMap = {
    '补协议期12个月并取消可重复': { nameHint: '0元', fixKey: 'contract12' },
    '转内部验证渠道': { nameHint: '0元', fixKey: 'internal' },
    '确认月费19元': { nameHint: '加装', fixKey: 'fee19' },
    '补依赖：宽带主服务': { nameHint: '加装', fixKey: 'dependBb' },
  }
  const mapped = batchFixMap[content]
  if (mapped) {
    const item =
      findBatchItemByName(mapped.nameHint) ||
      batchItems.value.find((i) => !i.compliancePass)
    if (item) {
      await handleBatchFix({ productId: item.productId, fixKey: mapped.fixKey })
      return
    }
  }
  // 下一步芯片：直接发送，降低客户操作成本
  if (content && !isSending.value) {
    await handleSend({ text: content, attachments: [] })
    return
  }
  inputMessage.value = content
  chatInputRef.value?.focus()
}

const handleCreateWorkOrder = async (workOrder) => {
  const session = sessions.value.find((s) => s.id === activeSessionId.value)
  if (!session) return
  const actions = (workOrder?.actions || []).map((a) => `- ${a}`).join('\n')
  await playProductReply(session, {
    thinkingSteps: [
      { type: 'llm', content: '工单字段来自规则映射，不由大模型编造' },
    ],
    content:
      `已生成产品优化工单草稿（Mock，可回写产商品中心）：\n\n` +
      `**标题**：${workOrder?.title || '产品优化工单'}\n` +
      `**商品**：${workOrder?.offeringId}\n` +
      `**异动**：${workOrder?.anomalySummary || ''}\n\n` +
      `**动作清单**\n${actions}\n\n` +
      '状态：`draft` · 来源：`ontology_rules`',
    formCard: null,
  })
}

const handleRiskReAudit = async ({ zeroSalesShelfDays }, opts = {}) => {
  const session = sessions.value.find((s) => s.id === activeSessionId.value)
  if (!session) return
  const keep = !!opts.keepSending
  if (!keep) isSending.value = true
  try {
    await playProductReply(
      session,
      await productConfig.runRiskAuditFlow({ zeroSalesShelfDays }),
    )
  } finally {
    if (!keep) isSending.value = false
  }
}

/** 客户一键体验：自动演完整脚本，无需背话术 */
const isGuidedDemoRunning = ref(false)
const handleGuidedDemo = async ({ type }) => {
  if (isGuidedDemoRunning.value || isSending.value) return
  isGuidedDemoRunning.value = true
  isSending.value = true
  try {
    if (type === 'ops-rootcause' || type === 'ops-risk') {
      applySkill('ops')
      createLocalSession()
      const session = sessions.value.find(s => s.id === activeSessionId.value)
      if (!session) return

      if (type === 'ops-rootcause') {
        renameSession(session.id, '一键体验·异动根因分析')
        session.messages.push({
          id: genId(),
          role: 'assistant',
          content:
            '开始自动演示「运营指标异动智能根因分析」。\n\n' +
            '金句：**本体负责推理，大模型负责表达**。流程：异动告警 → 图谱遍历 → Top3 根因 → 报告 → 工单草稿。',
          done: true,
          timestamp: Date.now(),
        })
        saveSessions()
        await sleep(600)
        await pushUserAndReply(session, '分析家庭融合畅享128本月收入下滑原因', 'root-cause')
        await sleep(900)
        if (rootCauseResult.value?.workOrder) {
          await handleCreateWorkOrder(rootCauseResult.value.workOrder)
        }
        return
      }

      renameSession(session.id, '一键体验·风险稽核')
      session.messages.push({
        id: genId(),
        role: 'assistant',
        content:
          '开始自动演示「高风险产商品智能稽核与优胜劣汰」。\n\n' +
          '流程：一键全量稽核 → 0元高危下钻 → 长期零销减负 → 规则阈值可配置。',
        done: true,
        timestamp: Date.now(),
      })
      saveSessions()
      await sleep(600)
      await pushUserAndReply(session, '筛查所有在架的0元资费风险商品', 'risk-audit')
      await sleep(1000)
      // 演示高潮2：筛选建议下架 → OF-LOW-019；再收紧阈值证明规则可配置
      session.messages.push({
        id: genId(),
        role: 'assistant',
        content:
          '右侧可点「建议下架」查看优胜劣汰池（含 `OF-LOW-019` 旧版彩铃包）。\n' +
          '接下来把零销在架天数阈值 **180→90** 重新推理，清单会变多——证明规则可配置、非写死页面。',
        done: true,
        timestamp: Date.now(),
      })
      saveSessions()
      await sleep(800)
      await handleRiskReAudit({ zeroSalesShelfDays: 90 }, { keepSending: true })
      return
    }

    applySkill(type === 'file' ? 'file' : 'chat')
    createLocalSession()
    const session = sessions.value.find(s => s.id === activeSessionId.value)
    if (!session) return

    if (type === 'file') {
      renameSession(session.id, '一键体验·智读批量')
      session.messages.push({
        id: genId(),
        role: 'assistant',
        content:
          '开始自动演示「智读·批量生成」。\n\n' +
          '流程：导入方案 → 三列映射与批量合规 → **先入库通过项** → 人机修正待修正项并重跑 → 再入库。',
        done: true,
        timestamp: Date.now(),
      })
      saveSessions()
      await sleep(600)
      await pushUserAndReply(session, '帮我导入校园迎新方案', 'file-parse')
      await sleep(1000)

      // 高潮：先入库「仅通过项」，强调待修正不会入库
      showBatchPanel.value = true
      await handleBatchConfirm({ keepSending: true })
      await sleep(900)

      // 点开套餐 B：证据链 → 一键修正 → 重跑通过
      const pkgB = findBatchItemByName('0元') || findBatchItemByName('体验')
      if (pkgB && !pkgB.compliancePass) {
        handleBatchSelect(pkgB.productId)
        await sleep(700)
        await handleBatchFix({ productId: pkgB.productId, fixKey: 'contract12' }, { keepSending: true })
      }
      await sleep(800)

      // 套餐 C：补月费 + 补依赖
      const pkgC = findBatchItemByName('加装')
      if (pkgC && !pkgC.compliancePass) {
        handleBatchSelect(pkgC.productId)
        await sleep(500)
        await handleBatchFix({ productId: pkgC.productId, fixKey: 'fee19' }, { keepSending: true })
        await sleep(500)
        const stillC = batchItems.value.find((i) => i.productId === pkgC.productId)
        if (stillC && !stillC.compliancePass) {
          await handleBatchFix({ productId: pkgC.productId, fixKey: 'dependBb' }, { keepSending: true })
        }
      }
      await sleep(800)

      // 收束：修正后再次入库，展示新增 draftId
      await handleBatchConfirm({ keepSending: true })
      return
    }

    // 智聊完整脚本
    renameSession(session.id, '一键体验·智聊冲突拦截')
    const formCard = productConfig.createEmptyOfferingCanvas()
    applyFormCard(formCard)
    session.messages.push({
      id: genId(),
      role: 'assistant',
      content: '开始自动演示「智聊·对话配置」。右侧已打开空白画布与合规面板。大模型听懂人话，**本体负责填对字段、拦住冲突**。',
      done: true,
      timestamp: Date.now(),
    })
    saveSessions()
    await sleep(700)

    await pushUserAndReply(session, '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售', 'chat-generate')
    await sleep(800)
    await pushUserAndReply(session, '就叫家庭融合畅享158', 'chat-generate')
    await sleep(800)
    await pushUserAndReply(session, '再绑一个畅享128主套餐一起卖', 'chat-generate')
    await sleep(900)
    await pushUserAndReply(session, '那不加128了，就单独上158', 'chat-generate')
    await sleep(600)
    if (activeFormCard.value?.compliancePass) {
      await handleGenerateDraft()
    }
  } finally {
    isGuidedDemoRunning.value = false
    isSending.value = false
    scrollChat()
  }
}

const pushUserAndReply = async (session, text, scenario) => {
  session.messages.push({
    id: genId(),
    role: 'user',
    content: text,
    timestamp: Date.now(),
  })
  saveSessions()
  scrollChat()
  await handleProductScenario(scenario, text, [], session)
}

const handleHomeSkillLaunch = ({ skill, text }) => {
  applySkill(skill)
  createLocalSession()
  const guide = productConfig.getSkillGuideMessage(skill === 'ops' ? 'ops' : skill)
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    session.messages.push({
      id: genId(),
      role: 'assistant',
      content: guide,
      done: true,
      timestamp: Date.now()
    })
    saveSessions()
  }
  // MVP-1 开场：空白配置画布 + 合规面板
  if (skill === 'chat') {
    const formCard = productConfig.createEmptyOfferingCanvas()
    applyFormCard(formCard)
  }
  // MVP-2：收起旧批量面板，等待导入后展开
  if (skill === 'file') {
    showBatchPanel.value = false
    batchItems.value = []
  }
  if (skill === 'ops') {
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    activeFormCard.value = null
  }
  inputMessage.value = text || ''
  nextTick(() => chatInputRef.value?.focus())
}

const handleQuickAction = (action) => {
  if (!action) return
  if (action.key) {
    applySkill(action.key)
  }
  inputMessage.value = action.content || ''
  chatInputRef.value?.focus()
}

const handleSkillAction = async ({ text, skill }) => {
  if (skill) applySkill(skill)
  inputMessage.value = text || ''
  chatInputRef.value?.focus()
}

const handleRegenerate = (msg) => {
  // 重新生成消息
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    const index = session.messages.findIndex(m => m.id === msg.id)
    if (index > 0) {
      // 找到对应的问题重新发送
      const userMsg = session.messages[index - 1]
      handleSend({ text: userMsg.content, attachments: userMsg.attachments || [] })
    }
  }
}

const handleFileUpload = async (files) => {
  const list = Array.isArray(files) ? files : [files]
  const doc = list.find((f) => /\.(docx|pdf|xlsx|doc|md)$/i.test(f?.name || ''))
  if (!doc) return
  let session = sessions.value.find(s => s.id === activeSessionId.value)
  if (!session) session = createLocalSession()
  session.messages.push({
    id: genId(),
    role: 'user',
    content: `上传了文件：${doc.name}`,
    attachments: [{ name: doc.name, size: doc.size || 0 }],
    timestamp: Date.now()
  })
  saveSessions()
  isSending.value = true
  try {
    await handleProductScenario('file-parse', doc.name, [{ name: doc.name, size: doc.size || 0 }], session)
  } finally {
    isSending.value = false
    nextTick(() => messageListRef.value?.scrollToBottom(true))
  }
}

const handleImageUpload = (files) => {
  console.log('图片上传:', files)
}

const handleVoiceRecord = (attachment) => {
  console.log('语音录制:', attachment)
}

const handleFormCardClick = (msg) => {
  if (msg?.formCard) applyFormCard(msg.formCard)
}

const handleQueryResultClick = async (product) => {
  const index = mockProducts.findIndex((p) => p.id === product.id)
  const playbook = productConfig.prepareProduct(index >= 0 ? index : 0)
  if (!playbook) return
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (!session) return
  isSending.value = true
  try {
    await playProductReply(session, playbook)
  } finally {
    isSending.value = false
  }
}

const handleProductSelect = (id) => {
  const formCard = productConfig.selectProduct(id)
  applyFormCard(formCard)
  showProductListPanel.value = false
}

const handleProductCopy = (id) => {
  productConfig.copyProduct(id)
}

const handleProductDelete = (id) => {
  const formCard = productConfig.deleteProduct(id)
  activeFormCard.value = formCard
}

const handleProductFieldChange = (fieldCode, value) => {
  productConfig.updateFormField(fieldCode, value)
  if (activeFormCard.value?.formData) {
    activeFormCard.value.formData[fieldCode] = value
  }
  if (activeFormCard.value?.formSchema?.fields) {
    const field = activeFormCard.value.formSchema.fields.find((f) => f.fieldCode === fieldCode)
    if (field) field.value = value
  }
}

const closeActiveForm = () => {
  activeFormCard.value = null
}

const handleAuditClose = () => {
  showAuditPanel.value = false
  auditPhase.value = 'idle'
}

const handleIntentAction = (event) => {
  console.log('意图动作:', event)
}

const clearContext = () => {
  const session = sessions.value.find(s => s.id === activeSessionId.value)
  if (session) {
    session.messages = []
    saveSessions()
  }
  productConfig.resetState()
  activeFormCard.value = null
}

// 从首页发送消息
const onSendMessageFromHome = async (messageData) => {
  createLocalSession()
  const text = typeof messageData === 'string' ? messageData : messageData?.text
  const skill = typeof messageData === 'object' ? messageData?.skill : ''
  const modelConfig = typeof messageData === 'object' ? messageData?.modelConfig : null
  if (skill) applySkill(skill)
  if (text) {
    await handleSend({
      text,
      attachments: typeof messageData === 'object' ? (messageData.attachments || []) : [],
      modelConfig: modelConfig || currentModelConfig.value || null
    })
  }
}

const onSwitchChat = (sessionId) => {
  activeSessionId.value = sessionId
  saveActiveSessionId()
}

// 管理页面跳转
const activeManager = ref('')
const openSceneManager = () => { activeManager.value = 'scene' }
const openPromptManager = () => { activeManager.value = 'prompt' }
const openOntologyManager = () => { activeManager.value = 'ontology' }
const closeManager = () => { activeManager.value = '' }

// 登出
const handleLogout = () => {
  sessions.value = []
  activeSessionId.value = ''
  userStore.logout()
}

// 网络检查
const checkNetwork = () => {
  isOnline.value = navigator.onLine
  if (isOnline.value) {
    checkBackendOnline()
  }
}

const checkBackendOnline = async () => {
  try {
    const response = await fetch('/api/v1/health', { method: 'HEAD' })
    isBackendOnline.value = response.ok
  } catch {
    isBackendOnline.value = false
  }
}

// 生命周期
onMounted(() => {
  loadSessions()
  loadModelConfig()
  const savedActiveId = localStorage.getItem(ACTIVE_SESSION_KEY)
  if (savedActiveId && sessions.value.find(s => s.id === savedActiveId)) {
    activeSessionId.value = savedActiveId
  }
  
  window.addEventListener('online', () => isOnline.value = true)
  window.addEventListener('offline', () => isOnline.value = false)
  
  // 定期检查后端
  setInterval(checkBackendOnline, 30000)
})

onUnmounted(() => {
  window.removeEventListener('online', () => {})
  window.removeEventListener('offline', () => {})
})
</script>

<style>
/* 全局样式变量 */
:root {
  /* 主色调 - 天蓝色 */
  --primary-500: #3b82f6;
  --primary-600: #2563eb;
  --primary-100: #dbeafe;
  
  /* 背景色 */
  --bg-primary: #ffffff;
  --bg-secondary: #f8fafc;
  --bg-tertiary: #f1f5f9;
  
  /* 边框 */
  --border-light: #e2e8f0;
  --border-default: #cbd5e1;
  
  /* 文字 */
  --text-primary: #1e293b;
  --text-secondary: #64748b;
  --text-tertiary: #94a3b8;
  --text-inverse: #ffffff;
  
  /* 侧边栏 */
  --sidebar-bg: #f8fafc;
  --sidebar-width: 280px;
  
  /* 过渡 */
  --transition-fast: 0.15s ease;
  --transition-normal: 0.2s ease;
  
  /* 层级 */
  --z-dropdown: 100;
  --z-modal: 200;
}

/* 深色模式 */
:root.dark {
  --bg-primary: #0f172a;
  --bg-secondary: #1e293b;
  --bg-tertiary: #334155;
  
  --border-light: #334155;
  --border-default: #475569;
  
  --text-primary: #f1f5f9;
  --text-secondary: #cbd5e1;
  --text-tertiary: #94a3b8;
  
  --sidebar-bg: #0f172a;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  display: flex;
  height: 100vh;
  height: 100dvh;
  background: var(--bg-primary);
  color: var(--text-primary);
  overflow: hidden;
}

.app-container {
  display: flex;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.main-layout {
  display: flex;
  flex: 1;
  min-width: 0;
}

/* 网络状态条 */
.network-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
}

.network-banner.offline {
  background: #ef4444;
  color: white;
}

.network-banner.warning {
  background: #f59e0b;
  color: white;
}

.network-banner button {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.network-banner button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.spin {
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-primary);
}

/* 聊天区域 */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(99, 102, 241, 0.06), transparent 30%),
    var(--bg-primary);
}

.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
  gap: 0;
  padding: 0 14px 12px 14px;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 10px;
}

.chat-main > :deep(.chat-input-container) {
  margin-top: auto;
}

.chat-body :deep(.messages-container) {
  flex: 1;
  min-width: 0;
}

.header-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}

.header-btn {
  position: relative;
}

.header-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 聊天头部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-primary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.header-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

/* 模型选择弹窗 */
.model-selector-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.52);
  backdrop-filter: blur(6px);
  padding: 20px;
}

.model-selector-popup,
.model-manager-popup {
  width: min(560px, 100%);
  max-height: min(88vh, 760px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 18px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.26);
}

.model-manager-popup {
  width: min(720px, 100%);
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-light, #e2e8f0);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(248, 250, 252, 0.7));
}

.popup-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.popup-close {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary, #94a3b8);
  cursor: pointer;
}

.popup-close:hover {
  background: var(--bg-secondary, #f1f5f9);
  color: var(--text-primary, #1e293b);
}

.popup-content {
  padding: 16px 18px 18px;
  overflow: auto;
}

.manager-content {
  gap: 18px;
}

.manager-intro {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.manager-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.manager-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  line-height: 1.6;
}

.manager-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.manager-span-2 {
  grid-column: span 2;
}

.manager-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary, #475569);
}

.manager-field input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 10px;
  background: var(--bg-primary, #fff);
  color: var(--text-primary, #1e293b);
  outline: none;
}

.manager-field input:focus {
  border-color: rgba(91, 124, 250, 0.45);
  box-shadow: 0 0 0 3px rgba(91, 124, 250, 0.12);
}

.manager-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.manager-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.manager-test-result {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.manager-test-result.test-ok {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.manager-test-result.test-fail {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.manager-test-result .test-result-icon {
  font-weight: 700;
  flex-shrink: 0;
}

.manager-test-result .test-result-text {
  flex: 1;
  word-break: break-word;
}

.primary-btn,
.secondary-btn {
  height: 36px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 13px;
}

.primary-btn {
  background: var(--color-primary-600, #5b7cfa);
  color: #fff;
}

.secondary-btn {
  background: var(--bg-secondary, #f8fafc);
  color: var(--text-primary, #1e293b);
  border-color: var(--border-light, #e2e8f0);
}

.popup-content :deep(.model-selector) {
  padding: 0;
}

/* 欢迎区域 */
.manager-area {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--bg-primary, #fff);
}

.welcome-area {
  flex: 1;
  overflow-y: auto;
  width: 100%;
}

/* 过渡动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

/* 响应式 - 平板 */
@media (max-width: 1024px) {
  .chat-header {
    padding: 12px 20px;
  }

  .chat-body {
    padding: 0 10px 10px;
  }

  .session-title {
    font-size: 15px;
  }
}

/* 响应式 - 手机横屏/小平板 */
@media (max-width: 768px) {
  :root {
    --sidebar-width: 0px;
  }

  .network-banner {
    font-size: 12px;
    padding: 6px 12px;
  }

  .network-banner button {
    padding: 3px 10px;
    font-size: 11px;
  }

  .chat-body {
    padding: 0 8px 8px;
  }

  .chat-header {
    padding: 10px 16px;
    height: 52px;
  }

  .header-left {
    gap: 8px;
  }

  .session-title {
    font-size: 15px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 50vw;
  }

  .header-actions {
    gap: 6px;
  }

  .header-btn {
    width: 34px;
    height: 34px;
  }

  .header-btn svg {
    width: 16px;
    height: 16px;
  }
}

/* 响应式 - 手机 */
@media (max-width: 480px) {
  .network-banner {
    font-size: 11px;
    padding: 6px 10px;
    gap: 6px;
  }

  .network-banner svg {
    width: 12px;
    height: 12px;
  }

  .chat-body {
    padding: 0 6px 6px;
  }

  .chat-header {
    padding: 8px 12px;
    height: 48px;
  }

  .session-title {
    font-size: 14px;
    max-width: 45vw;
  }

  .header-btn {
    width: 32px;
    height: 32px;
  }

  .header-btn svg {
    width: 14px;
    height: 14px;
  }

  .main-content {
    min-width: 100%;
  }
}

/* 移动端触摸优化 */
@media (pointer: coarse) {
  .header-btn {
    min-height: 36px;
    min-width: 36px;
    -webkit-tap-highlight-color: transparent;
  }

  .header-btn:active {
    background: var(--bg-tertiary);
  }

  .network-banner button {
    min-height: 28px;
  }
}

/* iPhone X+ 刘海屏适配 */
@supports (padding-top: env(safe-area-inset-top)) {
  @media (max-width: 768px) {
    .network-banner {
      padding-top: calc(8px + env(safe-area-inset-top));
    }

    .chat-header {
      padding-top: calc(10px + env(safe-area-inset-top) * 0.5);
    }
  }

  @media (max-width: 480px) {
    .network-banner {
      padding-top: calc(6px + env(safe-area-inset-top));
    }
  }
}

/* 小高度屏幕优化 - 横屏手机 */
@media (max-height: 500px) and (orientation: landscape) {
  .chat-header {
    height: 44px;
    padding: 6px 12px;
  }

  .session-title {
    font-size: 13px;
  }

  .network-banner {
    position: relative;
    padding: 4px 8px;
    font-size: 11px;
  }
}

/* 平板横屏优化 */
@media (min-width: 769px) and (max-width: 1024px) and (orientation: landscape) {
  :root {
    --sidebar-width: 240px;
  }
}

/* 大屏幕优化 */
@media (min-width: 1400px) {
  .chat-header {
    padding: 16px 32px;
  }

  .session-title {
    font-size: 17px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .main-content,
  .chat-header {
    background: var(--bg-primary);
  }
}
</style>

<template>
  <div class="sidebar">
    <!-- 侧边栏头部 - 始终显示 -->
    <div class="sidebar-header" :class="{ 'collapsed': canShowSidebarToggle && !isSidebarVisible }">
      <!-- Logo - 始终显示，除非侧边栏收起 -->
      <div class="sidebar-logo" v-if="!canShowSidebarToggle || isSidebarVisible">
        <div class="logo-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <circle cx="12" cy="12" r="6"/>
            <circle cx="12" cy="12" r="2"/>
            <path d="M12 6a6 6 0 0 1 4 1.5"/>
            <path d="M12 6a6 6 0 0 0-4 1.5"/>
            <path d="M12 18a6 6 0 0 1 4-1.5"/>
            <path d="M12 18a6 6 0 0 0-4-1.5"/>
            <path d="M6 12a6 6 0 0 1 1.5 4"/>
            <path d="M6 12a6 6 0 0 0 1.5-4"/>
            <path d="M18 12a6 6 0 0 1-1.5 4"/>
            <path d="M18 12a6 6 0 0 0-1.5-4"/>
            <circle cx="7.5" cy="8.5" r="1"/>
            <circle cx="16.5" cy="8.5" r="1"/>
            <circle cx="7.5" cy="15.5" r="1"/>
            <circle cx="16.5" cy="15.5" r="1"/>
          </svg>
        </div>
        <span class="logo-text">产商品智能助手</span>
      </div>
      
      <!-- 切换按钮 - 在允许显示的视图中始终可见 -->
      <button 
        v-if="canShowSidebarToggle"
        class="sidebar-toggle-btn-inner"
        :class="{ 'expanded': isSidebarVisible }"
        @click="$emit('toggle-sidebar')"
        :title="isSidebarVisible ? '收起侧边栏' : '展开侧边栏'"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline :points="isSidebarVisible ? '15 18 9 12 15 6' : '9 18 15 12 9 6'"></polyline>
        </svg>
      </button>
    </div>

    <!-- 侧边栏内容（在聊天视图或侧边栏展开状态显示） -->
    <template v-if="!canShowSidebarToggle || isSidebarVisible">
    <button class="new-chat-btn" @click="$emit('new-session')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      新建对话
    </button>

    

    <div class="session-list" ref="sessionListRef">
      <div class="session-group-label" v-if="todaySessions.length">今天</div>
      <div
        v-for="s in todaySessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId, pinned: s.pinned }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg v-if="s.pinned" class="pinned-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
          <circle cx="12" cy="10" r="3"/>
        </svg>
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button 
          class="session-menu-btn" 
          @click.stop="toggleSessionMenu(s.id)" 
          title="更多操作"
          ref="sessionMenuBtnRef"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="1"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="7"/>
          </svg>
        </button>
        
        <!-- 会话操作菜单 -->
        <div 
          v-if="activeSessionMenu === s.id" 
          class="session-menu" 
          @click.stop
        >
          <div class="session-menu-inner">
            <button class="session-menu-item pin" @click.stop="handlePinSession(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
              <span>{{ s.pinned ? '取消置顶' : '置顶' }}</span>
            </button>
            <button class="session-menu-item share" @click.stop="$emit('share-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 16.08c-.76 0-1.44.3-1.96.72L8.92 12l7.12-4.8c.54.5 1.25.87 2.04.87 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .79.38 1.49.97 1.92L8.92 12l7.08 4.08c-.59.43-.97 1.13-.97 1.92 0 1.66 1.34 3 3 3s3-1.34 3-3-1.34-3-3-3z"/>
              </svg>
              <span>分享</span>
            </button>
            <button class="session-menu-item rename" @click.stop="handleRename(s)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              <span>重命名</span>
            </button>
            <button class="session-menu-item report" @click.stop="handleReport(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 9v2m0 4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/>
              </svg>
              <span>举报</span>
            </button>
            <div class="menu-divider"></div>
            <button class="session-menu-item danger" @click.stop="$emit('delete-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
              <span>删除</span>
            </button>
          </div>
        </div>
      </div>

      <div class="session-group-label" v-if="olderSessions.length">更早</div>
      <div
        v-for="s in olderSessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId, pinned: s.pinned }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg v-if="s.pinned" class="pinned-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
          <circle cx="12" cy="10" r="3"/>
        </svg>
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title || '新对话' }}</span>
        <button 
          class="session-menu-btn" 
          @click.stop="toggleSessionMenu(s.id)" 
          title="更多操作"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="12" cy="12" r="1"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="7"/>
          </svg>
        </button>
        
        <!-- 会话操作菜单 -->
        <div 
          v-if="activeSessionMenu === s.id" 
          class="session-menu" 
          @click.stop
        >
          <div class="session-menu-inner">
            <button class="session-menu-item pin" @click.stop="handlePinSession(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
              <span>{{ s.pinned ? '取消置顶' : '置顶' }}</span>
            </button>
            <button class="session-menu-item share" @click.stop="$emit('share-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 16.08c-.76 0-1.44.3-1.96.72L8.92 12l7.12-4.8c.54.5 1.25.87 2.04.87 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .79.38 1.49.97 1.92L8.92 12l7.08 4.08c-.59.43-.97 1.13-.97 1.92 0 1.66 1.34 3 3 3s3-1.34 3-3-1.34-3-3-3z"/>
              </svg>
              <span>分享</span>
            </button>
            <button class="session-menu-item rename" @click.stop="handleRename(s)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              <span>重命名</span>
            </button>
            <button class="session-menu-item report" @click.stop="handleReport(s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 9v2m0 4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/>
              </svg>
              <span>举报</span>
            </button>
            <div class="menu-divider"></div>
            <button class="session-menu-item danger" @click.stop="$emit('delete-session', s.id)">
              <svg class="menu-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
              <span>删除</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 加载更多提示 -->
      <div v-if="isLoading" class="loading-more">
        <svg class="loading-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle class="spin" cx="12" cy="12" r="10"/>
        </svg>
        <span>加载中...</span>
      </div>
      <!-- 还有更多提示 -->
      <div v-if="hasMore && !isLoading" class="load-more-tip" @click="loadMore">
        点击加载更多 ({{ props.sessions.length - displayedSessions.length }} 条)
      </div>
      
      <div class="empty-tip" v-if="!sessions.length">暂无历史对话</div>
    </div>

    <div class="sidebar-footer">
      <!-- 用户信息 -->
      <div class="user-info" @click="showUserMenu = !showUserMenu" ref="userInfoRef">
        <div class="user-avatar" :style="{ background: avatarColor }">{{ avatarText }}</div>
        <div class="user-detail">
          <span class="user-name">{{ username }}</span>
          <span class="user-status">已登录</span>
        </div>
        <svg class="menu-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      <!-- 用户操作菜单 -->
      <div class="user-menu" v-if="showUserMenu" @click.stop>
        <div class="user-menu-inner">
          <!-- 主题切换 -->
          <button class="menu-item theme-toggle-item" @click="toggleTheme">
            <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle v-if="isDark" cx="12" cy="12" r="5"/>
              <path v-else d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
              <line v-if="isDark" x1="12" y1="1" x2="12" y2="3"/>
              <line v-if="isDark" x1="12" y1="21" x2="12" y2="23"/>
              <line v-if="isDark" x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
              <line v-if="isDark" x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line v-if="isDark" x1="1" y1="12" x2="3" y2="12"/>
              <line v-if="isDark" x1="21" y1="12" x2="23" y2="12"/>
              <line v-if="isDark" x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
              <line v-if="isDark" x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
            <span class="menu-item-text">{{ isDark ? '切换亮色模式' : '切换暗色模式' }}</span>
          </button>
          
          <!-- 切换模型 -->
          <div class="model-switch-section">
            <button class="menu-item model-switch-item" @click="toggleModelSwitch">
              <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
              </svg>
              <span class="menu-item-text">切换模型</span>
              <span class="current-model">{{ currentModelName }}</span>
            </button>
            
            <!-- 快速切换模型列表 -->
            <div v-if="showModelSwitch" class="model-switch-list">
              <div
                v-for="model in availableModels"
                :key="model.id"
                class="model-switch-item"
                :class="{ active: isCurrentModel(model) }"
                @click="quickSwitchModel(model)"
              >
                <span class="model-provider">{{ model.providerName }}</span>
                <span class="model-name">{{ model.name }}</span>
              </div>
            </div>
          </div>
          
          <!-- 模型配置 -->
          <button class="menu-item model-config-item" @click="toggleModelConfig">
            <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <circle cx="12" cy="12" r="4"/>
              <circle cx="12" cy="12" r="1"/>
            </svg>
            <span class="menu-item-text">模型配置</span>
          </button>
          
          <!-- 分隔线 -->
          <div class="menu-divider"></div>
          
          <!-- 退出登录 -->
          <button class="menu-item logout-item" @click="doLogout">
            <svg class="menu-item-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span class="menu-item-text">退出登录</span>
          </button>
        </div>
      </div>

      <!-- 模型选择器弹窗 -->
      <div v-if="showModelSelector" class="model-selector-overlay" @click="showModelSelector = false">
        <div class="model-selector-popup" @click.stop>
          <div class="popup-header">
            <span class="popup-title">模型配置</span>
            <button class="popup-close" @click="showModelSelector = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="popup-content">
            <ModelSelector @model-change="handleModelChange" />
          </div>
        </div>
      </div>
    </div>
    </template>
  </div>
</template>

<script setup>import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useUserStore } from '../stores/user';
import { useTheme } from '../composables/useTheme';
import ModelSelector from './ModelSelector.vue';
const userStore = useUserStore();
const { isDark, toggleTheme } = useTheme();
const showUserMenu = ref(false);
const showModelSelector = ref(false);
const showModelSwitch = ref(false);
const activeSessionMenu = ref(null);
const userInfoRef = ref(null);
const sessionListRef = ref(null);
// 流式加载相关
const pageSize = ref(20); // 每页加载数量
const currentPage = ref(1); // 当前页码
const isLoading = ref(false); // 是否正在加载
// 计算当前显示的会话列表
const displayedSessions = computed(() => {
 const total = pageSize.value * currentPage.value;
 return props.sessions.slice(0, total);
});
// 是否还有更多数据
const hasMore = computed(() => {
 return displayedSessions.value.length < props.sessions.length;
});

const currentModel = ref(null)
const MODEL_CONFIG_KEY = 'chat_model_config'
const MODEL_HISTORY_KEY = 'chat_model_history'
const availableModels = ref([])

const currentModelName = computed(() => {
  if (currentModel.value) {
    return currentModel.value.name
  }
  return '未选择'
})

const isCurrentModel = (model) => {
  if (!currentModel.value) return false
  return currentModel.value.id === model.id
}

const username = computed(() => userStore.username)
const avatarText = computed(() => userStore.avatarText)
const avatarColor = computed(() => userStore.avatar)

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  isDashboardView: { type: Boolean, default: false },
  canShowSidebarToggle: { type: Boolean, default: true },
  isSidebarVisible: { type: Boolean, default: true }
})

// 调试：打印 sessions 数量
console.log('[Sidebar] sessions 数量:', props.sessions.length, 'activeId:', props.activeId)

const emit = defineEmits(['new-session', 'switch-session', 'delete-session', 'logout', 'pin-session', 'share-session', 'report-session', 'rename-session', 'toggle-sidebar', 'model-change'])

const handlePinSession = (sessionId) => {
  console.log('[Sidebar.vue] handlePinSession called with sessionId:', sessionId)
  emit('pin-session', sessionId)
}

const doLogout = () => {
  showUserMenu.value = false
  emit('logout')
}

const toggleSessionMenu = (sessionId) => {
  activeSessionMenu.value = activeSessionMenu.value === sessionId ? null : sessionId
}

const handleRename = (session) => {
  activeSessionMenu.value = null
  const newTitle = prompt('请输入新的对话名称:', session.title || '新对话')
  if (newTitle !== null) {
    emit('rename-session', session.id, newTitle.trim())
  }
}

const handleReport = (sessionId) => {
  activeSessionMenu.value = null
  emit('report-session', sessionId)
}

const handleModelChange = (modelConfig) => {
  emit('model-change', modelConfig)
  showModelSelector.value = false
  
  saveModelToHistory(modelConfig)
  loadAvailableModels()
  
  const model = availableModels.value.find(m => m.provider === modelConfig.provider && m.name === modelConfig.model)
  if (model) {
    currentModel.value = model
  } else {
    currentModel.value = {
      id: `${modelConfig.provider}-${modelConfig.model}`,
      provider: modelConfig.provider,
      providerName: modelConfig.provider === 'custom' ? '自定义' : modelConfig.provider,
      name: modelConfig.model
    }
  }
}

const toggleModelSelector = () => {
  showModelSelector.value = !showModelSelector.value
  showUserMenu.value = false
}

const toggleModelConfig = () => {
  toggleModelSelector()
}

const toggleModelSwitch = () => {
  showModelSwitch.value = !showModelSwitch.value
}

const quickSwitchModel = async (model) => {
  showModelSwitch.value = false
  
  const modelConfig = {
    provider: model.provider,
    model: model.name
  }
  
  try {
    const response = await fetch('/api/v1/chat/model/switch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(modelConfig)
    })
    
    const result = await response.json()
    
    if (result.success) {
      saveModelToHistory(modelConfig)
      currentModel.value = model
      emit('model-change', modelConfig)
    }
  } catch (error) {
    console.error('快速切换模型失败:', error)
  }
}

// 获取模型历史记录
const getModelHistory = () => {
  try {
    const raw = localStorage.getItem(MODEL_HISTORY_KEY)
    if (raw) {
      return JSON.parse(raw)
    }
  } catch (e) {
    console.error('读取模型历史失败:', e)
  }
  return []
}

// 保存模型到历史记录
const saveModelToHistory = (modelConfig) => {
  try {
    const history = getModelHistory()
    const existingIndex = history.findIndex(
      m => m.provider === modelConfig.provider && m.name === modelConfig.model
    )
    
    if (existingIndex >= 0) {
      history.splice(existingIndex, 1)
    }
    
    history.unshift({
      id: `${modelConfig.provider}-${modelConfig.model}`,
      provider: modelConfig.provider,
      providerName: modelConfig.provider === 'custom' ? '自定义' : modelConfig.provider,
      name: modelConfig.model
    })
    
    const maxHistory = 5
    const trimmedHistory = history.slice(0, maxHistory)
    
    localStorage.setItem(MODEL_HISTORY_KEY, JSON.stringify(trimmedHistory))
    return trimmedHistory
  } catch (e) {
    console.error('保存模型历史失败:', e)
    return []
  }
}

// 加载可用模型列表
const loadAvailableModels = async () => {
  try {
    const response = await fetch('/api/v1/chat/model/available')
    const result = await response.json()
    
    const history = getModelHistory()
    
    if (result.success && result.models) {
      const existingIds = new Set(history.map(m => m.id))
      result.models.forEach(model => {
        if (!existingIds.has(model.id)) {
          history.push(model)
        }
      })
    }
    
    availableModels.value = history
  } catch (e) {
    console.error('加载可用模型列表失败:', e)
    availableModels.value = getModelHistory()
  }
}

// 加载保存的模型配置
const loadSavedModel = async () => {
  try {
    // 先加载可用模型列表
    await loadAvailableModels()
    
    // 先尝试从 localStorage 加载
    const raw = localStorage.getItem(MODEL_CONFIG_KEY)
    if (raw) {
      const config = JSON.parse(raw)
      const model = availableModels.value.find(
        m => m.provider === config.provider && m.name === config.model
      )
      if (model) {
        currentModel.value = model
        return
      }
    }
    
    // 如果没有保存的配置或未匹配到列表，从后端获取系统默认配置
    const response = await fetch('/api/v1/chat/model/default')
    const result = await response.json()
    
    if (result.success) {
      const model = availableModels.value.find(
        m => m.provider === result.provider && m.name === result.model
      )
      if (model) {
        currentModel.value = model
      } else {
        // 如果系统默认模型不在列表中，显示自定义标识
        currentModel.value = {
          id: `${result.provider}-${result.model}`,
          provider: result.provider,
          providerName: result.provider === 'custom' ? '自定义' : result.provider,
          name: result.model
        }
      }
    }
  } catch (e) {
    console.error('加载保存的模型配置失败:', e)
  }
}

// 点击空白处关闭菜单
const handleClickOutside = (e) => {
  if (userInfoRef.value && !userInfoRef.value.contains(e.target)) {
    showUserMenu.value = false
  }
  activeSessionMenu.value = null
  showModelSwitch.value = false
}
onMounted(() => {
 document.addEventListener('click', handleClickOutside);
 loadSavedModel();
 // 添加滚动监听
 if (sessionListRef.value) {
 sessionListRef.value.addEventListener('scroll', handleScroll);
 }
});
onUnmounted(() => {
 document.removeEventListener('click', handleClickOutside);
 // 移除滚动监听
 if (sessionListRef.value) {
 sessionListRef.value.removeEventListener('scroll', handleScroll);
 }
});

const today = new Date().toDateString();
// 根据显示的会话列表计算今天和更早的会话
const todaySessions = computed(() => {
 return displayedSessions.value.filter(s => new Date(s.updatedAt).toDateString() === today);
});
const olderSessions = computed(() => {
 return displayedSessions.value.filter(s => new Date(s.updatedAt).toDateString() !== today);
});
// 滚动加载更多
const handleScroll = () => {
 if (!sessionListRef.value || isLoading.value || !hasMore.value)
 return;
 const { scrollTop, scrollHeight, clientHeight } = sessionListRef.value;
 // 当滚动到底部附近时加载更多（距离底部50px）
 if (scrollTop + clientHeight >= scrollHeight - 50) {
 loadMore();
 }
};
// 加载更多会话
const loadMore = async () => {
 isLoading.value = true;
 // 模拟加载延迟
 await new Promise(resolve => setTimeout(resolve, 300));
 currentPage.value++;
 isLoading.value = false;
};
// 监听 sessions 变化，重置分页
watch(() => props.sessions.length, () => {
 currentPage.value = 1;
});
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  height: 100vh;
  height: 100dvh;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  padding: var(--space-4) var(--space-3);
  overflow: hidden;
}

@media (max-width: 768px) {
  .sidebar {
    width: var(--sidebar-width-mobile);
    min-width: var(--sidebar-width-mobile);
    box-shadow: var(--shadow-sidebar);
    padding: var(--space-4);
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2) var(--space-2);
}

/* 当侧边栏收起时，让按钮居中 */
.sidebar-header.collapsed {
  justify-content: center;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  flex: 1;
}

.sidebar-toggle-btn-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--sidebar-border);
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  padding: 0;
  flex-shrink: 0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}

.sidebar-toggle-btn-inner:hover {
  background: var(--color-primary-500);
  color: var(--text-inverse);
  border-color: var(--color-primary-500);
  transform: scale(1.05);
  box-shadow: 0 4px 8px rgba(91, 124, 250, 0.3);
}

.sidebar-toggle-btn-inner:active {
  transform: scale(0.98);
}

.sidebar-header {
  min-height: 48px;
}

.logo-icon {
  width: 30px; height: 30px;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
}
.logo-text {
  color: var(--sidebar-text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.2px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 10px var(--space-3-5);
  background: var(--sidebar-hover-bg);
  border: 1px solid var(--sidebar-border);
  border-radius: var(--radius-lg);
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background var(--transition-fast), border-color var(--transition-fast);
  margin-bottom: var(--space-3-5);
}
.new-chat-btn:hover {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.15);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.session-list::-webkit-scrollbar { width: 3px; }
.session-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 2px; }

.session-group-label {
  font-size: var(--font-size-xs);
  color: var(--sidebar-text-muted);
  padding: 10px var(--space-3) var(--space-1);
  letter-spacing: 0.5px;
  font-weight: var(--font-weight-medium);
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 9px var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  position: relative;
  transition: background var(--transition-fast), color var(--transition-fast);
  margin-bottom: 1px;
}
.session-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
}
.session-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-text-primary);
}

.pinned-icon {
  flex-shrink: 0;
  color: var(--color-primary-500);
  opacity: 0.7;
}
.session-icon { flex-shrink: 0; opacity: 0.5; }
.session-item.active .session-icon { opacity: 0.8; }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-menu-btn {
  display: none;
  background: none;
  border: none;
  padding: 3px;
  cursor: pointer;
  color: var(--sidebar-text-muted);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  line-height: 1;
}
.session-menu-btn:hover { color: var(--sidebar-text-primary); background: var(--bg-tertiary); }
.session-item:hover .session-menu-btn { display: flex; }

.session-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  transform: translateX(0);
  z-index: var(--z-dropdown);
  animation: sessionMenuIn 0.15s cubic-bezier(.16,1,.3,1) both;
  min-width: 160px;
}

@keyframes sessionMenuIn {
  from { 
    opacity: 0; 
    transform: translateY(-8px) scale(0.95);
  }
  to   { 
    opacity: 1; 
    transform: translateY(0) scale(1);
  }
}

@keyframes menuItemIn {
  from {
    opacity: 0;
    transform: translateX(-8px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.session-menu-inner {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-1-5);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
}

.session-menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  width: 100%;
  padding: var(--space-2-5) var(--space-3);
  background: none;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  transition: all var(--transition-fast);
  text-align: left;
  position: relative;
  overflow: hidden;
}
.session-menu-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-primary-500);
  transform: scaleY(0);
  transition: transform var(--transition-fast);
}
.session-menu-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
  padding-left: var(--space-4);
}
.session-menu-item:hover::before {
  transform: scaleY(1);
}
.session-menu-item:active {
  transform: scale(0.98);
}

.session-menu-item .menu-icon {
  flex-shrink: 0;
  transition: all var(--transition-fast);
}
.session-menu-item:hover .menu-icon {
  transform: translateX(2px);
}

/* 置顶按钮 */
.session-menu-item.pin:hover {
  background: rgba(91, 124, 250, 0.1);
  color: var(--color-primary-500);
}
.session-menu-item.pin:hover::before {
  background: var(--color-primary-500);
}

/* 分享按钮 */
.session-menu-item.share:hover {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success-500);
}
.session-menu-item.share:hover::before {
  background: var(--color-success-500);
}

/* 重命名按钮 */
.session-menu-item.rename:hover {
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning-500);
}
.session-menu-item.rename:hover::before {
  background: var(--color-warning-500);
}

/* 举报按钮 */
.session-menu-item.report:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--color-error-500);
}
.session-menu-item.report:hover::before {
  background: var(--color-error-500);
}

/* 删除按钮 */
.session-menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error-500);
}
.session-menu-item.danger:hover::before {
  background: var(--color-error-500);
}

.menu-divider {
  height: 1px;
  background: var(--border-light);
  margin: var(--space-1-5) 0;
}

.empty-tip {
  text-align: center;
  color: var(--sidebar-text-muted);
  font-size: var(--font-size-sm);
  padding: var(--space-8) 0;
}

/* 加载更多相关样式 */
.loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) 0;
  color: var(--sidebar-text-muted);
  font-size: var(--font-size-sm);
}

.loading-icon .spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.load-more-tip {
  text-align: center;
  color: var(--color-primary-500);
  font-size: var(--font-size-sm);
  padding: var(--space-3) 0;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.load-more-tip:hover {
  color: var(--color-primary-400);
  text-decoration: underline;
}

.sidebar-footer {
  border-top: 1px solid var(--sidebar-border);
  padding-top: var(--space-2-5);
  margin-top: var(--space-2);
  position: relative;
}

.model-selector-container {
  margin-bottom: var(--space-3);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 9px var(--space-3);
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
  position: relative;
}
.user-info:hover { background: var(--sidebar-hover-bg); color: var(--sidebar-text-primary); }

.user-avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  flex-shrink: 0;
}

.user-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-status {
  font-size: var(--font-size-xs);
  color: var(--sidebar-text-muted);
}

.menu-arrow {
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}
.user-info:hover .menu-arrow { opacity: 0.8; }
.user-info.open .menu-arrow { transform: rotate(180deg); }

/* 用户操作菜单 */
.user-menu {
  position: absolute;
  bottom: calc(100% + 6px);
  left: var(--space-2);
  right: var(--space-2);
  z-index: var(--z-dropdown);
  animation: menuIn 0.15s cubic-bezier(.16,1,.3,1) both;
}

@keyframes menuIn {
  from { opacity: 0; transform: translateY(6px) scale(0.97); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.user-menu-inner {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-2);
  box-shadow: var(--shadow-xl);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  width: 100%;
  padding: var(--space-2-5) var(--space-3);
  background: none;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--sidebar-text-secondary);
  font-size: var(--font-size-sm);
  transition: background var(--transition-fast), color var(--transition-fast);
  text-align: left;
}
.menu-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-primary);
}

.menu-item-icon {
  flex-shrink: 0;
  opacity: 0.7;
}

.menu-item-text {
  flex: 1;
}

.menu-divider {
  height: 1px;
  background: var(--border-light);
  margin: var(--space-2) 0;
}

.logout-item:hover {
  background: rgba(239,68,68,0.1);
  color: var(--color-error-500);
}

.model-config-item:hover {
  background: rgba(99, 102, 241, 0.1);
  color: var(--color-primary-500);
}

.model-switch-section {
  position: relative;
}

.model-switch-item {
  justify-content: space-between;
}

.model-switch-item .current-model {
  font-size: var(--font-size-xs);
  color: var(--color-primary-500);
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-weight: var(--font-weight-medium);
}

.model-switch-list {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  padding: var(--space-1);
  z-index: var(--z-dropdown);
  animation: sessionMenuIn 0.15s cubic-bezier(.16,1,.3,1) both;
}

.model-switch-list .model-switch-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.model-switch-list .model-switch-item:hover {
  background: var(--sidebar-hover-bg);
}

.model-switch-list .model-switch-item.active {
  background: rgba(99, 102, 241, 0.1);
}

.model-switch-list .model-switch-item.active .model-name {
  color: var(--color-primary-500);
  font-weight: var(--font-weight-medium);
}

.model-provider {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.model-name {
  font-size: var(--font-size-sm);
  color: var(--sidebar-text-secondary);
}

/* 模型选择器弹窗 */
.model-selector-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.model-selector-popup {
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2xl);
  width: 90%;
  max-width: 400px;
  overflow: hidden;
  animation: popupIn 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes popupIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.popup-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.popup-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.popup-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.popup-content {
  padding: var(--space-4);
}

.popup-content :deep(.model-selector) {
  background: transparent;
  border: none;
  padding: 0;
  gap: var(--space-3);
}

.popup-content :deep(.selector-header) {
  display: none;
}

</style>

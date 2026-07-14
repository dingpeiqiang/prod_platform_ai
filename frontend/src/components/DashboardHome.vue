<template>
  <div class="dashboard-home">
    <div class="dashboard-shell">
      <section class="dashboard-main">
        <!-- 顶部欢迎区 -->
        <div class="welcome-area">
          <div class="welcome-content">
            <h1 class="welcome-title">有什么可以帮你的？</h1>
            <p class="welcome-subtitle">产商品智能助手，随时为你效劳</p>
          </div>
        </div>

        <!-- 欢迎卡片 - 来自 prodai-cfg-demo -->
        <div class="welcome-cards-area">
          <p class="welcome-cards-title">您好！我是产品智能配置助手，可以帮您快速完成商品配置。</p>
          <div class="welcome-cards-grid">
            <button type="button" class="welcome-card" @click="handleWelcomeCard('query')">
              <div class="welcome-card-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
              </div>
              <h4>AI智查</h4>
              <p>查询历史商品，快速复制配置</p>
            </button>
            <button type="button" class="welcome-card" @click="handleWelcomeCard('file')">
              <div class="welcome-card-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="12" y1="18" x2="12" y2="12"/>
                  <line x1="9" y1="15" x2="15" y2="15"/>
                </svg>
              </div>
              <h4>AI方案导入</h4>
              <p>上传文档，批量导入配置</p>
            </button>
            <button type="button" class="welcome-card" @click="handleWelcomeCard('chat')">
              <div class="welcome-card-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </div>
              <h4>对话式配置</h4>
              <p>自然语言描述需求，AI 生成配置</p>
            </button>
          </div>
        </div>

        <!-- 快捷建议 -->
        <div class="suggestions-area">
          <div class="suggestions-grid">
            <button
              v-for="s in suggestions"
              :key="s.key"
              class="suggestion-item"
              @click="handleSuggestion(s)"
            >
              <span class="suggestion-icon">
                <svg v-if="s.icon === 'form'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                <svg v-else-if="s.icon === 'calendar'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                  <line x1="16" y1="2" x2="16" y2="6"/>
                  <line x1="8" y1="2" x2="8" y2="6"/>
                  <line x1="3" y1="10" x2="21" y2="10"/>
                </svg>
                <svg v-else-if="s.icon === 'wallet'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 7h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"/>
                  <path d="M16 21V5a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2z"/>
                </svg>
                <svg v-else-if="s.icon === 'help'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </span>
              <span class="suggestion-text">{{ s.text }}</span>
            </button>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="bottom-input">
          <ChatInput
            ref="inputRef"
            v-model="inputText"
            :placeholder="placeholder"
            @send="handleSend"
            @quick-action="handleSuggestion"
          />
        </div>
      </section>

      <!-- 待办、快捷、预警侧边栏 -->
      <aside class="sidebar-widgets">
        <!-- 待办 -->
        <div class="widget-card">
          <div class="widget-header">
            <span class="widget-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <path d="M9 3v6h6"/>
              </svg>
            </span>
            <span class="widget-title">待办</span>
            <span class="widget-count">{{ pendingTodos.length }}</span>
          </div>
          <div class="widget-body">
            <div class="todo-input-row">
              <input
                v-model="newTodo"
                class="todo-input"
                placeholder="添加待办..."
                @keydown.enter="addTodo"
              />
              <button class="todo-add-btn" @click="addTodo">+</button>
            </div>
            <div class="todo-list">
              <div v-for="todo in todos" :key="todo.id" class="todo-item" :class="{ done: todo.done }">
                <input
                  type="checkbox"
                  :checked="todo.done"
                  @change="toggleTodo(todo.id)"
                />
                <span class="todo-text">{{ todo.text }}</span>
                <button class="todo-delete" @click="deleteTodo(todo.id)">×</button>
              </div>
              <div v-if="!todos.length" class="empty-tip">暂无待办</div>
            </div>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="widget-card">
          <div class="widget-header">
            <span class="widget-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
              </svg>
            </span>
            <span class="widget-title">快捷</span>
          </div>
          <div class="widget-body">
            <div class="shortcut-grid">
              <button
                v-for="sc in shortcuts"
                :key="sc.key"
                class="shortcut-btn"
                @click="handleShortcut(sc)"
              >
                <span class="shortcut-icon">
                  <svg v-if="sc.icon === 'form'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                    <polyline points="10 9 9 9 8 9"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'calendar'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                    <line x1="16" y1="2" x2="16" y2="6"/>
                    <line x1="8" y1="2" x2="8" y2="6"/>
                    <line x1="3" y1="10" x2="21" y2="10"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'wallet'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M20 7h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"/>
                    <path d="M16 21V5a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2z"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'chart'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="18" y1="20" x2="18" y2="10"/>
                    <line x1="12" y1="20" x2="12" y2="4"/>
                    <line x1="6" y1="20" x2="6" y2="16"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'file'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                    <polyline points="10 9 9 9 8 9"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'help'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"/>
                    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                </span>
                <span class="shortcut-label">{{ sc.label }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 预警 -->
        <div class="widget-card">
          <div class="widget-header">
            <span class="widget-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
            </span>
            <span class="widget-title">预警</span>
            <span v-if="alerts.length" class="widget-count alert">{{ alerts.length }}</span>
          </div>
          <div class="widget-body">
            <div class="alert-list">
              <div v-for="alert in alerts" :key="alert.id" class="alert-item">
                <span class="alert-tag" :class="alert.type">{{ alert.tag }}</span>
                <span class="alert-text">{{ alert.text }}</span>
                <button class="alert-dismiss" @click="dismissAlert(alert.id)">×</button>
              </div>
              <div v-if="!alerts.length" class="empty-tip">暂无预警</div>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import ChatInput from './ChatInput.vue'

const emit = defineEmits(['send-message', 'switch-chat', 'create-session', 'open-scene-manager', 'open-prompt-manager', 'open-ontology-manager', 'open-workflow-manager', 'open-mcp-manager', 'open-kb-manager'])

const inputRef = ref(null)
const inputText = ref('')
const newTodo = ref('')

// 快捷建议
const suggestions = [
  { key: 'help', icon: 'help', text: '我能为你做什么？' },
]

// 快捷入口
const shortcuts = [
  { key: 'scene', icon: 'chart', label: '场景管理' },
  { key: 'prompt', icon: 'file', label: '提示词管理' },
  // { key: 'tool', icon: 'chart', label: '工具管理' }, // 已迁移到 MCP 管理
  // { key: 'form', icon: 'file', label: '表单管理' }, // 已废弃，使用本体管理替代
  { key: 'ontology', icon: 'help', label: '本体管理' },
  { key: 'workflow', icon: 'chart', label: '工作流管理' },
  { key: 'mcp', icon: 'chart', label: 'MCP 管理' },
  { key: 'kb', icon: 'file', label: '知识库' },
]

// 预警列表
const alerts = ref([
  { id: 1, tag: '待审批', text: '销售订单 #1023 等待审批', type: 'warning' },
  { id: 2, tag: '超时', text: '报销单 #201 审批超时 2 天', type: 'danger' },
])

// 待办
const todos = ref([])
const TODOS_KEY = 'dashboard_todos'

const pendingTodos = computed(() => todos.value.filter(t => !t.done))

const loadTodos = () => {
  try {
    const raw = localStorage.getItem(TODOS_KEY)
    if (raw) todos.value = JSON.parse(raw)
  } catch {}
}

const saveTodos = () => {
  localStorage.setItem(TODOS_KEY, JSON.stringify(todos.value))
}

const addTodo = () => {
  const text = newTodo.value.trim()
  if (!text) return
  todos.value.push({ id: Date.now(), text, done: false })
  newTodo.value = ''
  saveTodos()
}

const toggleTodo = (id) => {
  const todo = todos.value.find(t => t.id === id)
  if (todo) {
    todo.done = !todo.done
    saveTodos()
  }
}

const deleteTodo = (id) => {
  todos.value = todos.value.filter(t => t.id !== id)
  saveTodos()
}

const dismissAlert = (id) => {
  alerts.value = alerts.value.filter(a => a.id !== id)
}

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

const placeholder = computed(() => {
  const tips = [
    '描述你想做的事...',
    '问问「我能为你做什么」',
  ]
  return tips[Math.floor(Math.random() * tips.length)]
})

const handleSend = (messageData) => {
  if (!messageData || (!messageData.text && (!messageData.attachments || messageData.attachments.length === 0))) return
  emit('send-message', messageData)
}

const handleSuggestion = (text) => {
  emit('send-message', text)
}

const handleWelcomeCard = (type) => {
  let text = ''
  switch (type) {
    case 'query':
      text = '我想查询历史商品'
      break
    case 'file':
      text = '我想导入配置方案'
      break
    case 'chat':
      text = '我要配置一个大学生套餐'
      break
  }
  emit('send-message', { text, skill: type })
}

const handleShortcut = (sc) => {
  if (sc.key === 'scene') {
    emit('open-scene-manager')
    return
  }
  if (sc.key === 'prompt') {
    emit('open-prompt-manager')
    return
  }
  // if (sc.key === 'tool') {
  //   emit('open-tool-manager') // 已迁移到 MCP 管理
  //   return
  // }
  // if (sc.key === 'form') {
  //   emit('open-form-manager') // 已废弃
  //   return
  // }
  if (sc.key === 'ontology') {
    emit('open-ontology-manager')
    return
  }
  if (sc.key === 'workflow') {
    emit('open-workflow-manager')
    return
  }
  if (sc.key === 'mcp') {
    emit('open-mcp-manager')
    return
  }
  if (sc.key === 'kb') {
    emit('open-kb-manager')
    return
  }
  const msg = shortcuts.find(s => s.key === sc.key)
  if (msg) {
    emit('send-message', `帮我填一个${sc.label}`)
  }
}

onMounted(() => {
  loadTodos()
  nextTick(() => inputRef.value?.focus())
})
</script>

<style scoped>
.dashboard-home {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: linear-gradient(180deg, var(--bg-secondary) 0%, var(--bg-primary) 100%);
}

.dashboard-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 24px;
  width: 100%;
  height: 100%;
  padding: 32px 40px;
  min-width: 0;
}

.dashboard-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow: hidden;
}

.dashboard-main > * {
  min-width: 0;
}

.dashboard-main {
  overflow-y: auto;
  padding-right: 4px;
}

.dashboard-main::-webkit-scrollbar,
.sidebar-widgets::-webkit-scrollbar {
  width: 6px;
}

.dashboard-main::-webkit-scrollbar-thumb,
.sidebar-widgets::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 999px;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
}

.welcome-title {
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.welcome-subtitle,
.welcome-cards-title {
  color: var(--text-secondary);
  line-height: 1.6;
}

.welcome-cards-area,
.suggestions-area,
.bottom-input {
  width: 100%;
}

.welcome-cards-area {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.welcome-cards-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.welcome-card,
.suggestion-item,
.widget-card,
.shortcut-btn,
.todo-input,
.alert-item {
  border: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
}

.welcome-card {
  text-align: left;
  border-radius: 18px;
  padding: 20px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.welcome-card:hover,
.suggestion-item:hover,
.shortcut-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
  border-color: rgba(99, 102, 241, 0.35);
}

.welcome-card-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-100);
  color: var(--primary-600);
}

.welcome-card h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.welcome-card p {
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.suggestions-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.suggestion-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 14px;
  cursor: pointer;
  color: var(--text-primary);
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.suggestion-text {
  white-space: nowrap;
}

.bottom-input {
  margin-top: auto;
  padding-bottom: 2px;
}

.sidebar-widgets {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 4px;
}

.widget-card {
  border-radius: 18px;
  padding: 16px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.widget-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
}

.widget-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  flex: 1;
}

.widget-count {
  background: var(--primary-500);
  color: var(--text-inverse);
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.widget-count.alert {
  background: #f87171;
}

.widget-body {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-input-row {
  display: flex;
  gap: 8px;
}

.todo-input {
  flex: 1;
  padding: 10px 12px;
  border-radius: 12px;
  outline: none;
}

.todo-input:focus {
  border-color: var(--primary-500);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.todo-add-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--primary-500);
  color: var(--text-inverse);
  border-radius: 12px;
  cursor: pointer;
  font-size: 18px;
}

.todo-list,
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
}

.todo-item.done .todo-text {
  text-decoration: line-through;
  color: var(--text-tertiary);
}

.todo-text,
.alert-text,
.shortcut-label {
  color: var(--text-primary);
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.shortcut-btn {
  border-radius: 14px;
  padding: 12px 10px;
  cursor: pointer;
}

.alert-item {
  border-radius: 12px;
  padding: 10px 12px;
}

@media (max-width: 1180px) {
  .dashboard-shell {
    grid-template-columns: minmax(0, 1fr) 240px;
    padding: 24px;
    gap: 18px;
  }
}

@media (max-width: 1024px) {
  .dashboard-shell {
    grid-template-columns: 1fr;
  }

  .sidebar-widgets {
    display: grid;
    grid-template-columns: repeat(3, minmax(220px, 1fr));
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 4px;
  }

  .widget-card {
    min-width: 220px;
  }
}

@media (max-width: 768px) {
  .dashboard-shell {
    padding: 18px 14px 14px;
    gap: 16px;
  }

  .welcome-cards-grid {
    grid-template-columns: 1fr;
  }

  .suggestions-grid {
    gap: 8px;
  }

  .suggestion-item {
    width: 100%;
    justify-content: flex-start;
  }

  .sidebar-widgets {
    display: flex;
    flex-direction: column;
    overflow-y: auto;
    overflow-x: hidden;
  }

  .widget-card {
    min-width: 0;
  }
}

@media (max-width: 480px) {
  .welcome-title {
    font-size: 24px;
  }

  .welcome-card,
  .widget-card {
    border-radius: 16px;
  }
}
</style>

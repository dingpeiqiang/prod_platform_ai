<template>
  <div class="dashboard-home">
    <!-- 顶部欢迎区 -->
    <div class="welcome-area">
      <div class="welcome-content">
        <h1 class="welcome-title">有什么可以帮你的？</h1>
        <p class="welcome-subtitle">AI 驱动的智能助手，随时为你效劳</p>
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
      <div class="chat-input-bar">
        <textarea
          ref="inputEl"
          v-model="inputText"
          :placeholder="placeholder"
          rows="1"
          @keydown.enter.exact.prevent="handleSend"
          @input="autoResize"
        />
        <button
          class="send-btn"
          :class="{ active: inputText.trim() }"
          :disabled="!inputText.trim()"
          @click="handleSend"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
      <p class="input-hint">内容由 AI 生成，仅供参考</p>
    </div>

    <!-- 待办、快捷、预警侧边栏 -->
    <div class="sidebar-widgets">
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
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const emit = defineEmits(['send-message', 'switch-chat', 'create-session'])

const inputEl = ref(null)
const inputText = ref('')
const newTodo = ref('')

// 快捷建议
const suggestions = [
  { key: 'sales', icon: 'form', text: '帮我填一个销售订单' },
  { key: 'leave', icon: 'calendar', text: '帮我填一个请假申请' },
  { key: 'expense', icon: 'wallet', text: '帮我填一个费用报销' },
  { key: 'help', icon: 'help', text: '你能做什么？' },
]

// 快捷入口
const shortcuts = [
  { key: 'sales', icon: 'form', label: '销售订单' },
  { key: 'leave', icon: 'calendar', label: '请假申请' },
  { key: 'expense', icon: 'wallet', label: '费用报销' },
  { key: 'report', icon: 'chart', label: '数据报告' },
  { key: 'meeting', icon: 'file', label: '会议纪要' },
  { key: 'help', icon: 'help', label: '帮助中心' },
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
    '试试「帮我填一个销售订单」',
    '问问「你能做什么」',
  ]
  return tips[Math.floor(Math.random() * tips.length)]
})

const handleSend = () => {
  const text = inputText.value.trim()
  if (!text) return
  emit('send-message', text)
  inputText.value = ''
  nextTick(() => {
    if (inputEl.value) {
      inputEl.value.style.height = 'auto'
      inputEl.value.focus()
    }
  })
}

const handleSuggestion = (s) => {
  emit('send-message', s.text)
}

const handleShortcut = (sc) => {
  const msg = shortcuts.find(s => s.key === sc.key)
  if (msg) {
    emit('send-message', `帮我填一个${sc.label}`)
  }
}

import { nextTick } from 'vue'

onMounted(() => {
  loadTodos()
  nextTick(() => inputEl.value?.focus())
})
</script>

<style scoped>
.dashboard-home {
  display: grid;
  grid-template-columns: 1fr 280px;
  grid-template-rows: auto auto 1fr;
  gap: 24px;
  height: 100%;
  padding: 40px 48px;
  background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%);
  overflow: hidden;
}

/* 顶部欢迎区 */
.welcome-area {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 20px;
}

.welcome-content {
  text-align: center;
}

.welcome-title {
  font-size: 36px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 12px;
  letter-spacing: -0.5px;
}

.welcome-subtitle {
  font-size: 16px;
  color: #888;
}

/* 快捷建议 */
.suggestions-area {
  grid-column: 1;
  grid-row: 2;
  display: flex;
  justify-content: center;
}

.suggestions-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 640px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: 14px;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all .2s;
  box-shadow: var(--shadow-sm);
}

.suggestion-item:hover {
  border-color: #818cf8;
  box-shadow: 0 4px 16px rgba(99,102,241,.15);
  transform: translateY(-2px);
}

.suggestion-icon {
  font-size: 18px;
}

.suggestion-text {
  white-space: nowrap;
}

/* 底部输入区 */
.bottom-input {
  grid-column: 1;
  grid-row: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 20px;
}

.chat-input-bar {
  width: 100%;
  max-width: 720px;
  background: var(--bg-elevated);
  border-radius: 24px;
  padding: 16px 20px;
  display: flex;
  align-items: flex-end;
  gap: 14px;
  box-shadow: var(--shadow-lg);
  border: 2px solid var(--border-light);
  transition: all .2s;
}

.chat-input-bar:focus-within {
  border-color: #818cf8;
  box-shadow: 0 8px 40px rgba(99,102,241,.2);
}

.chat-input-bar textarea {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  line-height: 1.6;
  resize: none;
  background: transparent;
  font-family: inherit;
  max-height: 120px;
  min-height: 48px;
  color: var(--text-primary);
}

.chat-input-bar textarea::placeholder {
  color: var(--text-tertiary);
}

.send-btn {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  border: none;
  background: #e8e8e8;
  color: #bbb;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .2s;
  flex-shrink: 0;
}

.send-btn.active {
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: var(--text-inverse);
  box-shadow: 0 4px 16px rgba(99,102,241,.4);
}

.send-btn.active:hover {
  transform: scale(1.05);
}

.send-btn:disabled {
  cursor: not-allowed;
}

.input-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 右侧小部件 */
.sidebar-widgets {
  grid-column: 2;
  grid-row: 1 / 4;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 4px;
}

.sidebar-widgets::-webkit-scrollbar {
  width: 4px;
}

.sidebar-widgets::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 2px;
}

.widget-card {
  background: var(--bg-elevated);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.widget-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
}

.widget-icon {
  font-size: 16px;
}

.widget-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  flex: 1;
}

.widget-count {
  background: #818cf8;
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.widget-count.alert {
  background: #f87171;
}

.widget-body {
  min-height: 80px;
}

/* 待办 */
.todo-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.todo-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  font-size: 13px;
  outline: none;
}

.todo-input:focus {
  border-color: #818cf8;
}

.todo-add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #818cf8;
  color: var(--text-inverse);
  border-radius: 8px;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  transition: background .2s;
}

.todo-add-btn:hover {
  background: #6366f1;
}

.todo-list {
  max-height: 120px;
  overflow-y: auto;
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

.todo-item input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #818cf8;
}

.todo-text {
  flex: 1;
  font-size: 13px;
  color: #333;
}

.todo-delete {
  background: none;
  border: none;
  color: #ccc;
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
}

.todo-delete:hover {
  color: #f87171;
}

/* 快捷入口 */
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.shortcut-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: #f8f8ff;
  border: 1px solid #eaeaea;
  border-radius: 10px;
  cursor: pointer;
  transition: all .2s;
}

.shortcut-btn:hover {
  background: #f0f0ff;
  border-color: #c7c7fa;
  transform: translateY(-1px);
}

.shortcut-icon {
  font-size: 18px;
}

.shortcut-label {
  font-size: 11px;
  color: #555;
}

/* 预警 */
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f9f9f9;
  border-radius: 8px;
  font-size: 12px;
}

.alert-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  flex-shrink: 0;
}

.alert-tag.warning {
  background: #fef3c7;
  color: #d97706;
}

.alert-tag.danger {
  background: #fee2e2;
  color: #dc2626;
}

.alert-text {
  flex: 1;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alert-dismiss {
  background: none;
  border: none;
  color: #ccc;
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
}

.alert-dismiss:hover {
  color: #999;
}

.empty-tip {
  text-align: center;
  color: #aaa;
  font-size: 12px;
  padding: 16px 0;
}

/* 响应式 */
@media (max-width: 1024px) {
  .dashboard-home {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto 1fr auto;
    padding: 24px;
    gap: 20px;
  }
  .welcome-area {
    grid-column: 1;
  }
  .suggestions-area {
    grid-column: 1;
  }
  .bottom-input {
    grid-column: 1;
  }
  .sidebar-widgets {
    grid-column: 1;
    grid-row: 4;
    flex-direction: row;
    overflow-x: auto;
    padding-right: 0;
  }
  .widget-card {
    min-width: 200px;
    flex-shrink: 0;
  }
}

@media (max-width: 768px) {
  .welcome-title {
    font-size: 28px;
  }
  .suggestions-grid {
    gap: 8px;
  }
  .suggestion-item {
    padding: 12px 16px;
    font-size: 13px;
  }
  .chat-input-bar {
    padding: 14px 16px;
  }
  .sidebar-widgets {
    flex-direction: column;
  }
  .widget-card {
    min-width: unset;
  }
}
</style>

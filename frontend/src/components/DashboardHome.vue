<template>
  <div class="dashboard-home">
    <div class="dashboard-shell">
      <section class="dashboard-main">
        <!-- 顶部欢迎区 -->
        <div class="welcome-area">
          <div class="welcome-content">
            <h1 class="welcome-title">有什么可以帮你的？</h1>
            <p class="welcome-subtitle">{{ welcomeSubtitle }}</p>
          </div>
          <div v-if="!isOpsMode" class="demo-launch-row">
            <button type="button" class="demo-launch-btn primary" @click="startGuidedDemo('chat')">
              ▶ 一键体验 · 智聊冲突拦截（约1分钟）
            </button>
            <button type="button" class="demo-launch-btn" @click="startGuidedDemo('file')">
              ▶ 一键体验 · 智读批量（导入→修正→入库）
            </button>
          </div>
          <div v-else class="demo-launch-row">
            <button type="button" class="demo-launch-btn primary" @click="startGuidedDemo('ops-rootcause')">
              ▶ 一键体验 · 异动根因分析（约1分钟）
            </button>
            <button type="button" class="demo-launch-btn" @click="startGuidedDemo('ops-risk')">
              ▶ 一键体验 · 风险稽核优胜劣汰
            </button>
          </div>
          <div v-if="isOpsMode" class="ops-kpi-row">
            <div class="ops-kpi">
              <b>{{ opsDash.anomalyOfferingCount ?? '-' }}</b>
              <span>今日异动商品</span>
            </div>
            <div class="ops-kpi high">
              <b>{{ opsDash.highRiskCount ?? '-' }}</b>
              <span>高风险在架</span>
            </div>
            <div class="ops-kpi med">
              <b>{{ opsDash.suggestDelistCount ?? '-' }}</b>
              <span>建议下架</span>
            </div>
            <div class="ops-kpi">
              <b>{{ opsDash.shelfCount ?? '-' }}</b>
              <span>在架扫描样本</span>
            </div>
          </div>
          <p v-if="isOpsMode" class="ops-kpi-meta">
            <template v-if="opsDash.loadError">看板加载失败：{{ opsDash.loadError }}</template>
            <template v-else>规则 {{ opsDash.ruleVersion || '—' }} · 本体负责推理，大模型负责表达</template>
          </p>
          <p class="demo-hint">
            {{ isOpsMode
              ? '不用背话术：点上面按钮自动演示；也可点告警卡片或下方场景自己动手试。本体负责推理，大模型负责表达。'
              : '不用背话术：点上面按钮会自动演完整闭环；也可点下方卡片自己动手试。' }}
          </p>
        </div>

        <!-- 技能场景 -->
        <div class="skill-scenarios-area">
          <p class="welcome-cards-title">{{ welcomeCardsTitle }}</p>
          <div class="skill-scenarios-grid">
            <button
              v-for="scenario in skillScenarios"
              :key="scenario.id || scenario.key"
              type="button"
              class="skill-scenario-card"
              @click="launchSkill(scenario)"
            >
              <div class="skill-scenario-icon">
                <svg v-if="scenario.key === 'query'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
                <svg v-else-if="scenario.key === 'file'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="12" y1="18" x2="12" y2="12"/>
                  <line x1="9" y1="15" x2="15" y2="15"/>
                </svg>
                <svg v-else-if="scenario.id === 'ops-risk'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                  <line x1="12" y1="9" x2="12" y2="13"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                <svg v-else-if="scenario.key === 'ops'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 3v18h18"/>
                  <path d="M18 17V9"/>
                  <path d="M13 17V5"/>
                  <path d="M8 17v-3"/>
                </svg>
                <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </div>
              <div class="skill-scenario-body">
                <h4>{{ scenario.title }}</h4>
                <p>{{ scenario.desc }}</p>
              </div>
            </button>
          </div>
        </div>

        <!-- 快捷建议 -->
        <div class="suggestions-area">
          <div class="suggestions-grid">
            <button
              v-for="s in suggestions"
              :key="s.id || s.key + s.text"
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
            :currentSkill="currentSkill"
            :assistant-mode="assistantMode"
            @send="handleSend"
            @quick-action="handleSuggestion"
            @skill-select="(skill) => (currentSkill = skill || '')"
            @remove-skill="currentSkill = ''"
            @open-model-config="emit('open-model-config', $event)"
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
                  <svg v-else-if="sc.icon === 'cpu'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="4" y="4" width="16" height="16" rx="2" ry="2"/>
                    <circle cx="12" cy="12" r="4"/>
                    <line x1="16" y1="12" x2="20" y2="12"/>
                    <line x1="4" y1="12" x2="8" y2="12"/>
                    <line x1="12" y1="16" x2="12" y2="20"/>
                    <line x1="12" y1="4" x2="12" y2="8"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'network'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"/>
                    <circle cx="12" cy="12" r="4"/>
                    <circle cx="12" cy="12" r="2"/>
                    <path d="M12 2v2"/>
                    <path d="M12 20v2"/>
                    <path d="m4.93 4.93 1.41 1.41"/>
                    <path d="m17.66 17.66 1.41 1.41"/>
                    <path d="M2 12h2"/>
                    <path d="M20 12h2"/>
                    <path d="m6.34 17.66-1.41 1.41"/>
                    <path d="m19.07 4.93-1.41 1.41"/>
                  </svg>
                  <svg v-else-if="sc.icon === 'docs'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
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
              <div
                v-for="alert in alerts"
                :key="alert.id"
                class="alert-item"
                :class="{ clickable: !!alert.actionText }"
                @click="onAlertClick(alert)"
              >
                <span class="alert-tag" :class="alert.type">{{ alert.tag }}</span>
                <span class="alert-text">{{ alert.text }}</span>
                <button class="alert-dismiss" @click.stop="dismissAlert(alert.id)">×</button>
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import ChatInput from './ChatInput.vue'
import { getOpsDashboard } from '../services/ontologyMvpApi.js'

const props = defineProps({
  assistantMode: { type: String, default: 'rd' }
})

const emit = defineEmits(['send-message', 'switch-chat', 'create-session', 'launch-skill', 'guided-demo', 'open-scene-manager', 'open-prompt-manager', 'open-inference-manager', 'open-api-doc', 'open-model-config'])

const startGuidedDemo = (type) => {
  emit('guided-demo', { type })
}

const inputRef = ref(null)
const inputText = ref('')
const currentSkill = ref('')
const newTodo = ref('')

const isOpsMode = computed(() => props.assistantMode === 'ops')
const opsDash = ref({
  anomalyOfferingCount: null,
  highRiskCount: null,
  suggestDelistCount: null,
  shelfCount: null,
  ruleVersion: '',
  demoMode: false,
  loadError: '',
})

const welcomeSubtitle = computed(() =>
  isOpsMode.value
    ? '产商品运营助手，指标异动与风险稽核随问随答'
    : '产商品研发助手，智能配置随时为你效劳'
)

const welcomeCardsTitle = computed(() =>
  isOpsMode.value
    ? '您好！我是产商品运营助手，可以帮您做指标异动根因分析与高风险商品稽核。'
    : '您好！我是产商品研发助手，可以帮您快速完成商品配置。'
)

const rdSkillScenarios = [
  { key: 'chat', title: '智聊·对话配置', desc: '说业务话，本体填字段、拦冲突', text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售' },
  { key: 'file', title: '智读·批量生成', desc: '方案文档一键映射为多套合规配置', text: '帮我导入校园迎新方案' },
  { key: 'query', title: 'AI智查', desc: '查询历史商品，快速复制配置', text: '查一下近30天大学生套餐配置' },
]

const opsSkillScenarios = [
  { id: 'ops-rootcause', key: 'ops', title: '指标异动根因', desc: '多跳关联推理，定位收入/留存异动原因', text: '分析家庭融合畅享128本月收入下滑原因' },
  { id: 'ops-risk', key: 'ops', title: '高风险商品稽核', desc: '零元资费、长期零销等风险识别与处置建议', text: '筛查所有在架的0元资费风险商品' },
]

const skillScenarios = computed(() =>
  isOpsMode.value ? opsSkillScenarios : rdSkillScenarios
)

const launchSkill = (scenario) => {
  if (!scenario) return
  emit('launch-skill', { skill: scenario.key, text: scenario.text })
  setSkillAndPrefill(scenario.key, scenario.text)
}

watch(() => props.assistantMode, () => {
  currentSkill.value = ''
  inputText.value = ''
})

// 快捷建议
const suggestions = computed(() =>
  isOpsMode.value
    ? [
        { id: 'sug-ops-1', key: 'ops', icon: 'help', text: '分析家庭融合畅享128本月收入下滑原因' },
        { id: 'sug-ops-2', key: 'ops', icon: 'wallet', text: '筛查所有在架的0元资费风险商品' },
      ]
    : [
        { id: 'sug-rd-1', key: 'help', icon: 'help', text: '我能为你做什么？' },
        { id: 'sug-rd-2', key: 'chat', icon: 'form', text: '给家庭用户做一个融合套餐，月费158' },
      ]
)

// 快捷入口 — 仅展示已迁到 Spring Boot 的管理台
const shortcuts = [
  { key: 'scene', icon: 'chart', label: '场景管理' },
  { key: 'prompt', icon: 'file', label: '提示词管理' },
  { key: 'inference', icon: 'cpu', label: '模型管理' },
  { key: 'api-doc', icon: 'docs', label: 'API 文档' },
  // 以下依赖未完整迁移的 API，暂不暴露入口：
  // workflow（高级 publish/execute）、mcp（外部工具 CRUD）、kb（import-dir）
]

// 预警列表
const rdAlerts = [
  { id: 1, tag: '待审批', text: '销售订单 #1023 等待审批', type: 'warning' },
  { id: 2, tag: '超时', text: '报销单 #201 审批超时 2 天', type: 'danger' },
]

// 运营告警不再硬编码 OF-HF-128；由 getOpsDashboard 按图谱事实填充
const alerts = ref([...rdAlerts])

async function loadOpsDashboard() {
  if (!isOpsMode.value) return
  try {
    const data = await getOpsDashboard()
    if (data?.success !== false) {
      opsDash.value = {
        anomalyOfferingCount: data.anomalyOfferingCount ?? null,
        highRiskCount: data.highRiskCount ?? null,
        suggestDelistCount: data.suggestDelistCount ?? null,
        shelfCount: data.shelfCount ?? null,
        ruleVersion: data.ruleVersion || '',
        demoMode: data.demoMode === true,
        loadError: '',
      }
      if (Array.isArray(data.alerts) && data.alerts.length) {
        alerts.value = data.alerts.map((a, idx) => ({
          id: a.id || idx + 1,
          tag: a.tag || (a.type === 'anomaly' ? '异动' : '风险'),
          text: a.text,
          type: a.type === 'anomaly' ? 'warning' : 'danger',
          actionText:
            a.actionText ||
            (a.type === 'anomaly'
              ? `分析${a.offeringName || a.offeringId || '该商品'}本月收入下滑原因`
              : '筛查所有在架的0元资费风险商品'),
        }))
      } else {
        alerts.value = []
      }
    }
  } catch (e) {
    alerts.value = []
    opsDash.value = {
      anomalyOfferingCount: null,
      highRiskCount: null,
      suggestDelistCount: null,
      shelfCount: null,
      ruleVersion: '',
      demoMode: false,
      loadError: e.message || '本体服务不可用',
    }
  }
}

watch(() => props.assistantMode, (mode) => {
  alerts.value = mode === 'ops' ? [] : [...rdAlerts]
  if (mode === 'ops') loadOpsDashboard()
}, { immediate: true })

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

const onAlertClick = (alert) => {
  if (!alert?.actionText) return
  emit('launch-skill', { skill: 'ops', text: alert.actionText })
  setSkillAndPrefill('ops', alert.actionText)
}

const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

const placeholder = computed(() =>
  isOpsMode.value
    ? '试试：分析家庭融合畅享128本月收入下滑原因...'
    : '描述你想做的事，或选择上方技能场景...'
)

const setSkillAndPrefill = (skill, text) => {
  currentSkill.value = skill || ''
  inputText.value = text || ''
  nextTick(() => inputRef.value?.focus())
}

const handleSend = (messageData) => {
  if (!messageData || (!messageData.text && (!messageData.attachments || messageData.attachments.length === 0))) return
  emit('send-message', messageData)
}

const handleSuggestion = (item) => {
  if (!item) return
  if (typeof item === 'string') {
    setSkillAndPrefill('', item)
    return
  }
  setSkillAndPrefill(item.key || '', item.text || '')
}

const handleWelcomeCard = (type) => {
  const scenario = skillScenarios.value.find(item => item.key === type)
  if (!scenario) return
  emit('launch-skill', { skill: scenario.key, text: scenario.text })
  setSkillAndPrefill(type, scenario.text || '')
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
  if (sc.key === 'inference') {
    emit('open-inference-manager')
    return
  }
  if (sc.key === 'api-doc') {
    emit('open-api-doc')
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
  // workflow / mcp / kb 入口已隐藏（后端未完整迁移）
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
.ops-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 8px;
}
.ops-kpi {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 10px;
  text-align: center;
}
.ops-kpi b {
  display: block;
  font-size: 22px;
  color: #0f172a;
  line-height: 1.2;
}
.ops-kpi span {
  font-size: 12px;
  color: #64748b;
}
.ops-kpi.high b {
  color: #dc2626;
}
.ops-kpi.med b {
  color: #7c3aed;
}
.ops-kpi-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}
@media (max-width: 900px) {
  .ops-kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
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

.demo-launch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}

.demo-launch-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #0f172a;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.demo-launch-btn.primary {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.25);
}

.demo-launch-btn:hover {
  transform: translateY(-1px);
}

.demo-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
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

.skill-scenarios-area,
.suggestions-area,
.bottom-input {
  width: 100%;
}

.skill-scenarios-area {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skill-scenarios-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.skill-scenario-card,
.suggestion-item,
.widget-card,
.shortcut-btn,
.todo-input,
.alert-item {
  border: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
}

.skill-scenario-card {
  text-align: left;
  border-radius: 18px;
  padding: 20px;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.skill-scenario-card:hover,
.suggestion-item:hover,
.shortcut-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
  border-color: rgba(99, 102, 241, 0.35);
}

.skill-scenario-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-100);
  color: var(--primary-600);
  flex-shrink: 0;
}

.skill-scenario-body h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.skill-scenario-body p {
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

.alert-item.clickable {
  cursor: pointer;
}

.alert-item.clickable:hover {
  border-color: #0ea5e9;
  background: #f0f9ff;
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

  .skill-scenarios-grid {
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

  .skill-scenario-card,
  .widget-card {
    border-radius: 16px;
  }
}
</style>

<template>
  <AssistantShell
    mode="ops"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    :context="contextItems"
    :panelOpen="panelOpen"
    :panelWidth="panelWidth"
    @update:panelWidth="onPanelWidthUpdate"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="loadSessions"
    @switch-session="onSwitchSession"
    @delete-session="onDeleteSession"
    @shortcut="onShortcut"
    @quick-action="onQuickAction"
    @open-model-config="onOpenModelConfig"
    @context-remove="onRemoveContextItem"
    @context-clear="onClearContextItems"
  >
    <ChatMessageList
      mode="ops"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
      @undo-action="onUndoAction"
      @clarify-submit="onClarifySubmit"
      @open-ops="onOpenOps"
    />

    <template #restore-bar>
      <transition name="restore-slide">
        <div v-if="restoreBar.visible" class="restore-bar">
          <span class="restore-badge">{{ restoreBar.label }}</span>
          <span class="restore-name">{{ restoreBar.productName }}已折叠，可一键恢复</span>
          <button type="button" class="restore-btn" @click="restorePanel">恢复运营视图</button>
          <button type="button" class="restore-close" @click="hideRestoreBar" title="关闭">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </transition>
    </template>

    <template #panel>
      <OpsView
        v-if="panelType === 'ops-view'"
        :key="panelKey"
        @open-rootcause="onOpenRootCause"
      />
      <div v-else class="ops-result-panel">
        <div class="ops-result-head">
          <span class="ops-result-title">{{ opsResultPanelTitle }}</span>
          <button type="button" class="ops-result-close" @click="closePanel" title="关闭面板">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="ops-result-body">
          <template v-if="panelType === 'monitor'">
            <div v-if="monitorLoading" class="ops-result-empty">加载中...</div>
            <template v-else>
              <div class="ops-kpi-row">
                <div class="ops-kpi">
                  <span class="ops-kpi-label">告警总数</span>
                  <span class="ops-kpi-value">{{ monitorResult?.total ?? monitorAlertItems.length }}</span>
                </div>
                <div class="ops-kpi">
                  <span class="ops-kpi-label">高优先级</span>
                  <span class="ops-kpi-value warn">{{ monitorResult?.highPriorityCount ?? monitorAlerts.filter((a) => a.severity === 'HIGH').length }}</span>
                </div>
                <div class="ops-kpi">
                  <span class="ops-kpi-label">在办工单</span>
                  <span class="ops-kpi-value">{{ monitorResult?.openWorkOrderCount ?? monitorWorkOrders.length }}</span>
                </div>
              </div>
              <div class="ops-table">
                <div class="ops-table-head">
                  <span>等级</span><span>商品</span><span>告警内容</span>
                </div>
                <div v-for="(a, i) in monitorAlerts" :key="i" class="ops-table-row">
                  <span class="ops-sev" :class="a.severity === 'HIGH' ? 'high' : 'mid'">{{ a.severity === 'HIGH' ? '高' : '中' }}</span>
                  <span class="ops-cell-name">{{ a.offeringName || a.id || '—' }}</span>
                  <span class="ops-cell-text">{{ a.text }}</span>
                </div>
                <div v-if="!monitorAlerts.length" class="ops-table-empty">暂无告警</div>
              </div>
              <div v-if="monitorWorkOrders.length" class="ops-wo-section">
                <div class="ops-section-title">处置工单</div>
                <div v-for="(w, i) in monitorWorkOrders" :key="i" class="ops-wo-item">
                  <span class="ops-wo-id">{{ w.workOrderId || w.work_order_id || '—' }}</span>
                  <span class="ops-wo-title">{{ w.title || '—' }}</span>
                </div>
              </div>
            </template>
          </template>

          <template v-else-if="panelType === 'root-cause'">
            <div v-if="!rootCauseResult" class="ops-result-empty">暂无根因分析结果</div>
            <template v-else>
              <div class="ops-section-title">异动结论</div>
              <p class="ops-para">{{ rootCauseResult.message || rootCauseResult.explanation || '—' }}</p>
              <div class="ops-section-title">根因路径</div>
              <div v-for="(p, i) in rootCausePaths" :key="i" class="ops-cause-item">
                <span class="ops-cause-rank">#{{ p.rank || i + 1 }}</span>
                <span class="ops-cause-name">{{ p.name }}</span>
                <span v-if="p.weight != null" class="ops-cause-weight">权重 {{ (p.weight * 100).toFixed(0) }}%</span>
              </div>
              <div v-if="rootCauseAnomalies.length" class="ops-section-title">异动明细</div>
              <div v-for="(a, i) in rootCauseAnomalies" :key="'a' + i" class="ops-alert-line">{{ a.message || a.text }}</div>
            </template>
          </template>

          <template v-else-if="panelType === 'risk-audit'">
            <div v-if="!riskAuditResult" class="ops-result-empty">暂无稽核结果</div>
            <template v-else>
              <div v-for="(it, i) in riskAuditItems" :key="i" class="ops-risk-item">
                <div class="ops-wo-title">{{ it.offeringName || it.offeringId || '—' }}</div>
                <div v-if="it.riskLevel" class="ops-sev" :class="it.riskLevel === 'HIGH' ? 'high' : 'mid'">{{ it.riskLevel === 'HIGH' ? '高风险' : it.riskLevel }}</div>
                <div class="ops-para">{{ it.summary || (it.actions || []).join('；') }}</div>
              </div>
              <div v-if="!riskAuditItems.length" class="ops-table-empty">未发现高风险商品</div>
            </template>
          </template>
        </div>
      </div>
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import OpsView from './ops/OpsView.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { usePanelSync } from '../composables/usePanelSync.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { genId } from '../utils/chatUtils.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeScene = ref(assistantModes.ops.defaultScene)

const router = useRouter()
const onOpenModelConfig = () => router.push('/model-config')

const {
  messages,
  streaming,
  sendAgentMessage,
  loadSessions,
  switchSession,
  newSession,
  deleteSession,
  sessionList,
  stop,
} = useChatStream()

const productConfig = useProductConfig()
const activeRootCauseRank = productConfig.activeRootCauseRank

provide('rootCauseActiveRank', activeRootCauseRank)

const config = assistantModes.ops

/** 右侧工作台面板（ops-view / monitor / root-cause / risk-audit） */
const {
  panelType,
  panelOpen,
  panelKey,
  panelWidth,
  restoreBar,
  openOpsViewPanel,
  openOpsResultPanel,
  closePanel,
  restorePanel,
  hideRestoreBar,
  resetPanelState,
} = usePanelSync()

const onPanelWidthUpdate = (w) => {
  panelWidth.value = w
}

const monitorResult = productConfig.monitorResult
const monitorWorkOrders = productConfig.monitorWorkOrders
const monitorLoading = productConfig.monitorLoading
const rootCauseResult = productConfig.rootCauseResult
const riskAuditResult = productConfig.riskAuditResult

const monitorAlerts = computed(() => {
  const items = monitorResult.value?.items || []
  return Array.isArray(items) ? items : []
})
const monitorAlertItems = computed(() => monitorAlerts.value)
const rootCausePaths = computed(() => {
  const r = rootCauseResult.value || {}
  const paths = r.paths || []
  return Array.isArray(paths) ? paths : []
})
const rootCauseAnomalies = computed(() => {
  const r = rootCauseResult.value || {}
  const items = r.anomalies || []
  return Array.isArray(items) ? items : []
})
const riskAuditItems = computed(() => {
  const r = riskAuditResult.value || {}
  const items = r.items || []
  return Array.isArray(items) ? items : []
})

const opsResultPanelTitle = computed(() => {
  const map = {
    monitor: '运营监控看板',
    'root-cause': '异动归因 · 根因分析',
    'risk-audit': '风险稽核结果',
  }
  return map[panelType.value] || '分析面板'
})

/** 欢迎页/消息流打开产品运营视图入口 */
const onOpenOps = () => {
  openOpsViewPanel()
}

/** 下钻「根因分析」：折叠运营视图 + 自动发起根因分析对话 */
const onOpenRootCause = (drillKey) => {
  const name = drillKey || '目标套餐'
  closePanel()
  const text = `对 5G新通话 下的「${name}」套餐做异动根因分析`
  if (!streaming.value) {
    sendAgentMessage({ text })
  } else {
    inputText.value = text
  }
}

/** 手动移除/清除的上下文键（避免自动派生又冒出来） */
const dismissedContextKeys = ref(new Set())

/** 会话上下文标签（ContextBar）：从最近一条助手消息的翻译层产物派生 */
const contextItems = computed(() => {
  const doneMsgs = messages.value.filter(m => m.role === 'assistant' && m.done)
  const last = doneMsgs[doneMsgs.length - 1]
  if (!last) return []
  const items = []

  const plan = last.queryPlan
  if (plan) {
    if (Array.isArray(plan.clarify) && plan.clarify.length) {
      for (const p of plan.clarify) {
        const key = `clarify:${p}`
        if (!dismissedContextKeys.value.has(key)) {
          items.push({ key, label: '待补充', value: paramLabelZh(p), type: 'intent', removable: true })
        }
      }
    }
    const params = plan.params || {}
    if (params.offering) {
      const key = 'entity:offering'
      if (!dismissedContextKeys.value.has(key)) {
        items.push({ key, label: '当前分析', value: String(params.offering), type: 'entity', removable: true })
      }
    }
    if (plan.intent && plan.intent !== 'CHAT') {
      const key = 'intent:plan'
      if (!dismissedContextKeys.value.has(key)) {
        items.push({ key, label: '业务意图', value: intentLabelZh(plan.intent), type: 'intent', removable: true })
      }
    }
  }
  return items
})

function paramLabelZh(key) {
  const map = {
    offering: '商品/套餐',
    ruleId: '规则编号',
    concept: '本体概念',
    question: '查询内容',
  }
  return map[key] || key
}

function intentLabelZh(intent) {
  const map = {
    SPARQL_QUERY: '数据查询',
    SWRL_INFER: '异动归因',
    RULE_EXPLAIN: '规则解释',
    ONTOLOGY_EXPLAIN: '概念解释',
    product_ops_query: '数据查询',
    product_ops_reason: '异动归因',
    product_ops_policy: '风险稽核',
    product_ops_monitor: '运营监控',
    product_ops_compare: '对比分析',
  }
  return map[intent] || intent
}

const onRemoveContextItem = (item) => {
  if (item?.key) {
    dismissedContextKeys.value = new Set([...dismissedContextKeys.value, item.key])
  }
}

const onClearContextItems = () => {
  dismissedContextKeys.value = new Set(contextItems.value.map(i => i.key))
}

/** 前端场景码 → 后端 ChatStream scene */
function scenarioToScene(scenario, fallback = '') {
  const map = {
    'ops-monitor': 'ops_monitor',
    'root-cause': 'root_cause',
    'risk-audit': 'risk_audit',
    'market-insight': 'market_insight',
    'online-check': 'online_check',
    compare: 'compare',
    query: 'market_insight',
  }
  if (map[scenario]) return map[scenario]
  return fallback || config.defaultScene
}

function isGuideOnlyRequest(text) {
  const t = String(text || '')
  return /使用指导|使用说明|操作步骤|怎么用|如何使用|使用手册|只输出使用说明|仅输出使用说明|只要使用说明|不要直接执行|不要执行|勿执行|不要生成配置结果|仅说明|只要说明/.test(t)
}

function resolveOpsScenario(text, scene = '') {
  // 使用说明/勿执行：不按关键词强制改写场景，保留 UI 当前 scene 作软提示
  if (isGuideOnlyRequest(text)) {
    const s = String(scene || '')
    if (s === 'ops_rules' || s === 'rules') return 'ops-rules'
    if (s === 'ops_monitor' || s === 'ops.monitor' || s === 'monitor') return 'ops-monitor'
    if (s === 'root_cause' || s === 'offering_ops_root_cause') return 'root-cause'
    if (s === 'risk_audit' || s === 'offering_ops_risk' || s === 'offering_ops_risk_audit') return 'risk-audit'
    if (s === 'market_insight' || s === 'market') return 'market-insight'
    if (s === 'online_check' || s === 'online') return 'online-check'
    if (s === 'compare' || s === 'compare_state' || s === 'what_if') return 'compare'
    return null
  }

  const s = String(scene || '')
  if (s === 'ops_rules' || s === 'rules') return 'ops-rules'
  if (s === 'ops_monitor' || s === 'ops.monitor' || s === 'monitor') return 'ops-monitor'
  if (s === 'root_cause' || s === 'offering_ops_root_cause') return 'root-cause'
  if (s === 'risk_audit' || s === 'offering_ops_risk' || s === 'offering_ops_risk_audit') return 'risk-audit'
  if (s === 'market_insight' || s === 'market') return 'market-insight'
  if (s === 'online_check' || s === 'online') return 'online-check'
  if (s === 'compare' || s === 'compare_state' || s === 'what_if') return 'compare'

  if (/规则运营|规则目录|风险阈值|热重载规则|打开规则/.test(text)) return 'ops-rules'
  if (/运营监控|告警列表|查看告警|监控看板|异动告警|打开运营监控/.test(text)) return 'ops-monitor'
  if (/根因|异动|离网|累计收入|归因|下滑原因|收入下滑/.test(text)) return 'root-cause'
  if (/稽核|零元资费|高风险|优胜劣汰|筛查.*在架|风险商品|下架建议/.test(text)) return 'risk-audit'
  if (/立项|上线门槛|新品.*套餐|能否通过审核|PS_PRODUCT_ONLINE/.test(text)) return 'online-check'
  if (/方案对比|多方案|对比.*元|资费对比|假设|推演|what.?if|方案A|方案B/.test(text)) return 'compare'
  if (/在售|增长趋势|市场洞察|竞品|增长指标|有哪些.*套餐|查一下.*套餐|查一下.*在售/.test(text)) {
    return 'market-insight'
  }
  return productConfig.detectScenario(text)
}

function openPanelsFromSseMessage(msg) {
  if (!msg) return
  const intent = msg.intentType || ''
  const data = msg.intentData || {}

  if (intent === 'product_ops_monitor') {
    if (productConfig.applyMonitorFromSse(data)) {
      openOpsResultPanel('monitor')
    }
    return
  }

  if (intent === 'product_ops_reason' && data.rootCause) {
    if (productConfig.applyRootCauseFromSse(data.rootCause)) {
      openOpsResultPanel('root-cause')
    }
    return
  }

  if (intent === 'product_ops_policy') {
    const expectation = data.expectationType || msg.action || ''
    if (expectation === 'risk_audit' || data.riskAudit || Array.isArray(data.items)) {
      if (productConfig.applyRiskAuditFromSse(data.riskAudit || data)) {
        openOpsResultPanel('risk-audit')
      }
    }
  }
}

function downloadJson(filename, payload) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 规则目录 → 对话内可读摘要（右侧面板移除后，目录以对话方式展示） */
function buildRulesDialogueText(catalog = {}) {
  const groups = catalog?.rules || catalog?.ruleGroups || catalog?.groups || []
  if (Array.isArray(groups) && groups.length) {
    return groups
      .map((g) => {
        const name = g.groupName || g.name || g.code || '规则组'
        const entries = Array.isArray(g.rules) ? g.rules : []
        return `- **${name}**：${entries.length ? entries.map((r) => r.code || r.ruleId || r.name || r).join('、') : '—'}`
      })
      .join('\n')
  }
  if (catalog?.ruleList) {
    return catalog.ruleList
      .map((r) => `- ${r.code || r.ruleId || r.name}：${r.desc || r.description || ''}`)
      .join('\n')
  }
  return '当前无规则目录数据，可在对话中直接说明要查看或调整的规则。'
}

onMounted(async () => {
  registerPostProcessor('product_ops_reason', (msg) => openPanelsFromSseMessage(msg))
  registerPostProcessor('product_ops_policy', (msg) => openPanelsFromSseMessage(msg))
  registerPostProcessor('product_ops_monitor', (msg) => openPanelsFromSseMessage(msg))

  historyLoading.value = true
  try {
    await loadSessions()
  } finally {
    historyLoading.value = false
  }
})

onUnmounted(() => {
  registerPostProcessor('product_ops_reason', null)
  registerPostProcessor('product_ops_policy', null)
  registerPostProcessor('product_ops_monitor', null)
})

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  if (!text || streaming.value) return
  inputText.value = ''
  const scene = payload?.scene || activeScene.value || config.defaultScene

  // 使用说明/勿执行：保留当前 scene 作软提示，禁止关键词强制改写为业务场景
  if (isGuideOnlyRequest(text)) {
    activeScene.value = scene
    await sendAgentMessage({ text })
    return
  }

  const scenario = resolveOpsScenario(text, scene)

  if (scenario === 'ops-rules') {
    activeScene.value = 'ops_rules'
    const catalog = await productConfig.openRulesPanel()
    const rulesText = buildRulesDialogueText(catalog || productConfig.opsRulesCatalog.value)
    messages.value = [
      ...messages.value,
      {
        id: genId(),
        role: 'user',
        type: 'chat',
        content: text,
        done: true,
        timestamp: Date.now(),
      },
      {
        id: genId(),
        role: 'assistant',
        type: 'chat',
        content:
          '已加载**规则运营**目录：\n\n' +
          rulesText +
          '\n\n需要调整风险阈值、查看变更审计或热重载 `ops_rules.json`，可直接用对话说明。',
        done: true,
        timestamp: Date.now(),
      },
    ]
    return
  }

  const streamScene = scenarioToScene(scenario, scene)
  activeScene.value = streamScene
  // 统一走翻译层入口（三阶架构：理解→执行→表达），scene 由后端理解层自行判定
  await sendAgentMessage({ text })
}

const onSuggest = (payload) => {
  if (!payload || streaming.value) return
  if (typeof payload === 'object' && (payload.openOpsView || payload.key === 'open-ops-view')) {
    onOpenOps()
    return
  }
  if (typeof payload === 'object' && (payload.guide || payload.autoSend || payload.welcome || payload.scene)) {
    showSceneWelcome(payload)
    return
  }
  const text = typeof payload === 'string' ? payload : payload?.text
  if (!text) return
  // nextSteps 快捷芯片：打开运营视图（原型 open-ops-view 语义）
  if (/打开运营视图|打开产品运营视图|产品运营视图/.test(String(text)) && String(text).length <= 12) {
    onOpenOps()
    return
  }
  inputText.value = text
}

/** CLARIFY 澄清补参结构化回传：带 params 重发「继续」 */
const onClarifySubmit = async ({ params }) => {
  if (!params || streaming.value) return
  await sendAgentMessage({ text: '继续', params })
}

/** 点击场景标签：本地展示欢迎信息（不请求模型） */
async function showSceneWelcome(item) {
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
  }
  if (item.scene === 'ops_rules') {
    await productConfig.openRulesPanel()
  }

  const welcome = buildSceneWelcome(item)
  inputText.value = ''
  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'assistant',
      type: 'chat',
      content: welcome.content,
      streamText: welcome.content,
      done: true,
      loading: false,
      timestamp: Date.now(),
      intentType: '',
      nextSteps: welcome.nextSteps || [],
      sceneWelcome: true,
      scene: item.scene || activeScene.value,
    },
  ]
}

const onShortcut = async (item) => {
  await showSceneWelcome(item)
}

/** 输入区快捷芯片 → 同场景欢迎信息 */
const onQuickAction = async (action) => {
  if (!action || streaming.value) return
  let scene = action.scene
  if (/市场洞察|在售|增长/.test(action.label || action.content || '')) scene = 'market_insight'
  else if (/立项|上线/.test(action.label || action.content || '')) scene = 'online_check'
  else if (/监控|告警/.test(action.label || action.content || '')) scene = 'ops_monitor'
  else if (/稽核|风险|零元/.test(action.label || action.content || '')) scene = 'risk_audit'
  else if (/归因|根因|异动/.test(action.label || action.content || '')) scene = 'root_cause'
  else if (/规则/.test(action.label || action.content || '')) scene = 'ops_rules'
  else if (action.key === 'ops') scene = action.label?.includes('稽核') ? 'risk_audit' : 'root_cause'

  const matched = config.sceneShortcuts.find((s) => s.scene === scene || s.label === action.label)
  await showSceneWelcome(matched || {
    label: action.label,
    scene: scene || activeScene.value,
    desc: action.desc || action.label,
    text: action.content || action.text || '',
  })
}

const onSwitchSession = async (sessionId) => {
  activeScene.value = config.defaultScene
  resetPanelState()
  historyLoading.value = true
  try {
    await switchSession(sessionId)
  } finally {
    historyLoading.value = false
  }
}

const onNewSession = () => {
  activeScene.value = config.defaultScene
  productConfig.resetState()
  resetPanelState()
  newSession()
}

/** 删除会话：调 useChatStream 删除（后端 + 列表刷新） */
const onDeleteSession = async (sid) => {
  if (!sid) return
  await deleteSession(sid)
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
    return
  }
  if (event.action === 'create_work_order' && event.payload) {
    onCreateWorkOrder(event.payload)
    return
  }
  if (event.action === 'create_risk_work_order' && event.payload) {
    onCreateRiskWorkOrder({ item: event.payload, ...event.payload })
    return
  }
  if (event.action === 're_audit' && event.payload?.text) {
    onReAudit(event.payload)
    return
  }
  if (event.action === 'undo' && event.payload?.actionId) {
    onUndoAction({ actionId: event.payload.actionId })
    return
  }
  if (event.action === 'export' && event.payload) {
    const name = `ops-${event.payload.intentType || 'export'}-${Date.now()}.json`
    downloadJson(name, event.payload)
  }
}

async function onCreateWorkOrder(wo) {
  try {
    const saved = await productConfig.submitWorkOrder({
      offeringId: wo?.offeringId,
      offeringName: wo?.offeringName,
      title: wo?.title,
      summary: wo?.anomalySummary || wo?.summary,
      actions: wo?.actions,
      rootCauses: wo?.rootCauses,
      source: 'root_cause',
    })
    const title = saved?.title || wo?.title || '产品优化工单'
    const id = saved?.workOrderId || ''
    messages.value = [
      ...messages.value,
      {
        id: genId(),
        role: 'assistant',
        type: 'chat',
        content:
          `已生成处置工单${id ? ` **${id}**` : ''}：**${title}**\n\n` +
          `${(saved?.actions || wo?.actions || []).map((a) => `- ${a}`).join('\n')}\n\n` +
          '工单状态已回写本体（dispositionStatus=work_order_open）。',
        done: true,
        timestamp: Date.now(),
        undoable: { state: 'executed', label: `生成工单 · ${title}`, actionId: null },
      },
    ]
  } catch (e) {
    messages.value = [
      ...messages.value,
      {
        id: genId(),
        role: 'assistant',
        type: 'chat',
        content: `工单生成失败：${e?.message || '请稍后重试'}`,
        done: true,
        timestamp: Date.now(),
      },
    ]
  }
}

async function onCreateRiskWorkOrder(payload) {
  const item = payload?.item || {}
  const hypo = payload?.hypo || null
  try {
    const saved = await productConfig.submitWorkOrder({
      offeringId: item.offeringId,
      offeringName: item.offeringName,
      title: `${item.offeringName || item.offeringId}风险处置工单`,
      summary: hypo?.summary || (item.actions || []).join('；'),
      actions: item.actions?.length
        ? item.actions
        : [item.disposition?.defaultAction || '启动风险处置'],
      source: 'risk_audit',
      hypoMode: payload?.mode,
      impacts: hypo?.impacts,
    })
    messages.value = [
      ...messages.value,
      {
        id: genId(),
        role: 'assistant',
        type: 'chat',
        content:
          `已生成风险处置工单 **${saved?.workOrderId || ''}**：${saved?.title || ''}\n\n` +
          `${(saved?.actions || []).map((a) => `- ${a}`).join('\n')}`,
        done: true,
        timestamp: Date.now(),
        undoable: {
          state: 'executed',
          label: `生成工单 · ${saved?.title || item.offeringName || ''}`,
          actionId: null,
        },
      },
    ]
  } catch (e) {
    messages.value = [
      ...messages.value,
      {
        id: genId(),
        role: 'assistant',
        type: 'chat',
        content: `风险工单生成失败：${e?.message || '请稍后重试'}`,
        done: true,
        timestamp: Date.now(),
      },
    ]
  }
}

async function onReAudit(payload) {
  const text = payload?.text || payload?.question || '按最新阈值重新稽核在架风险商品'
  if (/监控|告警/.test(text)) {
    activeScene.value = 'ops_monitor'
  } else {
    activeScene.value = 'risk_audit'
  }
  await sendAgentMessage({ text })
}

/** 对话内撤销已执行动作（v3.2 可逆操作） */
const onUndoAction = async ({ msg, actionId }) => {
  if (streaming.value || !actionId) return
  const result = await productConfig.undoAction(actionId)
  if (msg?.undoable) {
    msg.undoable.state = result.success ? 'reverted' : 'failed'
    if (result.success) msg.undoable.actionId = null
  }
  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'assistant',
      type: 'chat',
      content: result.success
        ? `已撤销：**${result.label}**。相关草稿/状态已回退。`
        : `撤销失败：${result.message || '请稍后重试'}。`,
      done: true,
      timestamp: Date.now(),
    },
  ]
}
</script>

<style scoped>
/* 还原条（restore bar）：运营视图折叠后保留上下文 */
.restore-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
}
.restore-badge {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}
.restore-name {
  flex: 1;
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.restore-btn {
  border: none;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.restore-btn:hover { background: #1d4ed8; }
.restore-close {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
}
.restore-close:hover { color: #0f172a; }
.restore-slide-enter-active, .restore-slide-leave-active { transition: all 0.24s ease; }
.restore-slide-enter-from, .restore-slide-leave-to { opacity: 0; transform: translateY(8px); }

/* 运营类结果面板（monitor / root-cause / risk-audit） */
.ops-result-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.ops-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.ops-result-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.ops-result-close {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
}
.ops-result-close:hover { background: #f1f5f9; color: #0f172a; }
.ops-result-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ops-result-empty {
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
.ops-kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.ops-kpi {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ops-kpi-label { font-size: 11px; color: #64748b; }
.ops-kpi-value { font-size: 20px; font-weight: 800; color: #0f172a; }
.ops-kpi-value.warn { color: #dc2626; }
.ops-table { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; }
.ops-table-head,
.ops-table-row {
  display: grid;
  grid-template-columns: 52px 1.1fr 2fr;
  gap: 8px;
  padding: 8px 12px;
  align-items: center;
}
.ops-table-head { background: #f8fafc; border-bottom: 1px solid #e2e8f0; font-size: 12px; font-weight: 600; color: #64748b; }
.ops-table-row { border-bottom: 1px solid #f1f5f9; font-size: 12.5px; }
.ops-table-row:last-child { border-bottom: none; }
.ops-table-empty { text-align: center; color: #94a3b8; font-size: 12.5px; padding: 16px; }
.ops-sev {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  padding: 2px 8px;
  width: fit-content;
}
.ops-sev.high { background: #fee2e2; color: #dc2626; }
.ops-sev.mid { background: #fef3c7; color: #b45309; }
.ops-cell-name {
  font-weight: 600;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ops-cell-text { color: #64748b; line-height: 1.5; }
.ops-wo-section { display: flex; flex-direction: column; gap: 8px; }
.ops-section-title { font-size: 13px; font-weight: 700; color: #334155; margin-top: 4px; }
.ops-wo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12.5px;
}
.ops-wo-id { font-family: Consolas, monospace; color: #2563eb; font-weight: 600; flex-shrink: 0; }
.ops-wo-title { color: #334155; font-weight: 600; }
.ops-para { font-size: 12.5px; color: #475569; line-height: 1.7; margin: 0; }
.ops-cause-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12.5px;
}
.ops-cause-rank { color: #7c3aed; font-weight: 800; flex-shrink: 0; }
.ops-cause-name { flex: 1; color: #1e293b; font-weight: 600; }
.ops-cause-weight { color: #64748b; font-size: 11.5px; flex-shrink: 0; }
.ops-alert-line {
  border-left: 3px solid #f59e0b;
  background: #fffbeb;
  padding: 8px 10px;
  font-size: 12.5px;
  color: #92400e;
  border-radius: 0 8px 8px 0;
  line-height: 1.6;
}
.ops-risk-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

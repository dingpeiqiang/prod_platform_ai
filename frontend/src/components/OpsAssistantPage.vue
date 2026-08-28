<template>
  <AssistantShell
    mode="ops"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    :context="contextItems"
    :summary-stats="summaryStats"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="loadSessions"
    @switch-session="onSwitchSession"
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
    />

    <template #right>
      <InsightBoard mode="ops" :messages="messages" :product-config="productConfig" />
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import InsightBoard from './InsightBoard.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
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
  sessionList,
  stop,
} = useChatStream()

const productConfig = useProductConfig()
const activeRootCauseRank = productConfig.activeRootCauseRank

provide('rootCauseActiveRank', activeRootCauseRank)

const config = assistantModes.ops

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

/** 移动端紧凑「会话汇总」状态条：关键计数实时聚合 */
const summaryStats = computed(() => {
  const mp = productConfig
  const wo = mp.monitorWorkOrders?.value || []
  const monitor = mp.monitorResult?.value
  const risk = mp.riskAuditResult?.value
  const analysis = messages.value.filter(
    (m) => m?.role === 'assistant' && (m.intentType === 'product_ops_reason' || m._scenario === 'root-cause'),
  ).length
  return [
    { label: '工单', value: `${wo.length}` },
    { label: '告警', value: `${monitor?.total ?? 0}`, tone: (monitor?.highPriorityCount || 0) ? 'warn' : 'neutral' },
    { label: '扫描', value: `${risk?.scannedCount ?? 0}` },
    { label: '归因', value: `${analysis}` },
  ]
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
    productConfig.applyMonitorFromSse(data)
    return
  }

  if (intent === 'product_ops_reason' && data.rootCause) {
    productConfig.applyRootCauseFromSse(data.rootCause)
    return
  }

  if (intent === 'product_ops_policy') {
    const expectation = data.expectationType || msg.action || ''
    if (expectation === 'risk_audit' || data.riskAudit || Array.isArray(data.items)) {
      productConfig.applyRiskAuditFromSse(data.riskAudit || data)
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
  if (typeof payload === 'object' && (payload.guide || payload.autoSend || payload.welcome || payload.scene)) {
    showSceneWelcome(payload)
    return
  }
  const text = typeof payload === 'string' ? payload : payload?.text
  if (!text) return
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
  newSession()
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

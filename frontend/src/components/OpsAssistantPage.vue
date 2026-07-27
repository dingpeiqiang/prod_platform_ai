<template>
  <AssistantShell
    mode="ops"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="loadSessions"
    @switch-session="onSwitchSession"
    @shortcut="onShortcut"
  >
    <ChatMessageList
      mode="ops"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
    />

    <template #right>
      <OpsRulesPanel
        v-if="showRulesPanel"
        :visible="showRulesPanel"
        :catalog="opsRulesCatalog"
        :loading="rulesLoading"
        @close="closeRulesPanel"
        @updated="onRulesUpdated"
      />
      <OpsMonitorPanel
        v-else-if="showMonitorPanel"
        :visible="showMonitorPanel"
        :loading="monitorLoading"
        :result="monitorResult"
        :work-orders="monitorWorkOrders"
        @close="closeMonitorPanel"
        @refresh="onRefreshMonitor"
        @analyze="onAnalyzeAlert"
        @open-risk="onOpenRiskFromMonitor"
        @work-orders-updated="onWorkOrdersUpdated"
      />
      <OpsRootCausePanel
        v-else-if="showRootCausePanel && rootCauseResult"
        :visible="showRootCausePanel"
        :result="rootCauseResult"
        :ontology-chain="rootCauseOntologyChain"
        v-model:active-rank="activeRootCauseRank"
        @close="closeRootCausePanel"
        @create-work-order="onCreateWorkOrder"
      />
      <OpsRiskAuditPanel
        v-else-if="showRiskAuditPanel && riskAuditResult"
        :visible="showRiskAuditPanel"
        :result="riskAuditResult"
        @close="closeRiskAuditPanel"
        @re-audit="onReAudit"
        @create-work-order="onCreateRiskWorkOrder"
      />
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, onMounted, onUnmounted, provide } from 'vue'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import OpsRootCausePanel from './OpsRootCausePanel.vue'
import OpsRiskAuditPanel from './OpsRiskAuditPanel.vue'
import OpsMonitorPanel from './OpsMonitorPanel.vue'
import OpsRulesPanel from './OpsRulesPanel.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
import { assistantModes } from '../config/assistantModes.js'
import { genId } from '../utils/chatUtils.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeScene = ref(assistantModes.ops.defaultScene)

const {
  messages,
  streaming,
  sendMessage,
  loadSessions,
  switchSession,
  newSession,
  sessionList,
  stop,
} = useChatStream()

const productConfig = useProductConfig()
const showRootCausePanel = productConfig.showRootCausePanel
const showRiskAuditPanel = productConfig.showRiskAuditPanel
const showMonitorPanel = productConfig.showMonitorPanel
const showRulesPanel = productConfig.showRulesPanel
const opsRulesCatalog = productConfig.opsRulesCatalog
const rulesLoading = productConfig.rulesLoading
const rootCauseResult = productConfig.rootCauseResult
const riskAuditResult = productConfig.riskAuditResult
const monitorResult = productConfig.monitorResult
const monitorWorkOrders = productConfig.monitorWorkOrders
const monitorLoading = productConfig.monitorLoading
const rootCauseOntologyChain = productConfig.rootCauseOntologyChain
const activeRootCauseRank = productConfig.activeRootCauseRank

provide('rootCauseActiveRank', activeRootCauseRank)

const config = assistantModes.ops

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

function resolveOpsScenario(text, scene = '') {
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
  const scenario = resolveOpsScenario(text, scene)

  if (scenario === 'ops-rules') {
    activeScene.value = 'ops_rules'
    await productConfig.openRulesPanel()
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
          '已打开右侧**规则运营**面板：可查看 R-A/B/C/D 目录、调整风险阈值覆盖、查看变更审计，或热重载 `ops_rules.json`。',
        done: true,
        timestamp: Date.now(),
      },
    ]
    return
  }

  const streamScene = scenarioToScene(scenario, scene)
  activeScene.value = streamScene
  await sendMessage({ text, scene: streamScene })
}

const onSuggest = (text) => {
  if (!text || streaming.value) return
  inputText.value = text
}

const onShortcut = async (item) => {
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
  }
  if (item.scene === 'ops_rules') {
    await productConfig.openRulesPanel()
    if (item.text) inputText.value = item.text
    return
  }
  if (item.text) {
    inputText.value = item.text
  }
}

const onSwitchSession = (sessionId) => {
  closeRootCausePanel()
  closeRiskAuditPanel()
  closeMonitorPanel()
  closeRulesPanel()
  activeScene.value = config.defaultScene
  switchSession(sessionId)
}

const onNewSession = () => {
  closeRootCausePanel()
  closeRiskAuditPanel()
  closeMonitorPanel()
  closeRulesPanel()
  activeScene.value = config.defaultScene
  productConfig.resetState()
  newSession()
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
    return
  }
  if (event.action === 'export' && event.payload) {
    const name = `ops-${event.payload.intentType || 'export'}-${Date.now()}.json`
    downloadJson(name, event.payload)
  }
}

function closeRootCausePanel() {
  showRootCausePanel.value = false
}

function closeRiskAuditPanel() {
  showRiskAuditPanel.value = false
}

function closeMonitorPanel() {
  showMonitorPanel.value = false
}

function closeRulesPanel() {
  showRulesPanel.value = false
}

function onRulesUpdated(catalog) {
  productConfig.applyRulesCatalog(catalog)
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
  activeScene.value = 'risk_audit'
  const text = payload?.question || '按最新阈值重新稽核在架风险商品'
  await sendMessage({ text, scene: 'risk_audit' })
}

async function onRefreshMonitor() {
  monitorLoading.value = true
  try {
    await productConfig.runOpsMonitorFlow({ silent: true })
  } finally {
    monitorLoading.value = false
  }
}

async function onAnalyzeAlert(alert) {
  const text = alert?.actionText || `分析${alert?.offeringName || ''}本月收入下滑原因`
  activeScene.value = 'root_cause'
  await sendMessage({ text, scene: 'root_cause' })
}

async function onOpenRiskFromMonitor() {
  activeScene.value = 'risk_audit'
  await sendMessage({ text: '筛查所有在架的0元资费风险商品', scene: 'risk_audit' })
}

function onWorkOrdersUpdated(list) {
  if (Array.isArray(list)) {
    productConfig.monitorWorkOrders.value = list
  }
}
</script>

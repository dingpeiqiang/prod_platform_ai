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
      <OpsRootCausePanel
        v-if="showRootCausePanel && rootCauseResult"
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
      />
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, onMounted, provide } from 'vue'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import OpsRootCausePanel from './OpsRootCausePanel.vue'
import OpsRiskAuditPanel from './OpsRiskAuditPanel.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { assistantModes } from '../config/assistantModes.js'
import { genId } from '../utils/chatUtils.js'
import { playSimulatedReply } from '../utils/simulateReply.js'

const inputText = ref('')
const historyLoading = ref(false)
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
const rootCauseResult = productConfig.rootCauseResult
const riskAuditResult = productConfig.riskAuditResult
const rootCauseOntologyChain = productConfig.rootCauseOntologyChain
const activeRootCauseRank = productConfig.activeRootCauseRank

provide('rootCauseActiveRank', activeRootCauseRank)

const config = assistantModes.ops

onMounted(async () => {
  historyLoading.value = true
  try {
    await loadSessions()
  } finally {
    historyLoading.value = false
  }
})

function resolveOpsScenario(text, scene = '') {
  const s = String(scene || '')
  if (s === 'root_cause' || s === 'offering_ops_root_cause') return 'root-cause'
  if (s === 'risk_audit' || s === 'offering_ops_risk') return 'risk-audit'
  return productConfig.detectScenario(text)
}

async function playOpsReply(playbook = {}) {
  const aiMsg = {
    id: genId(),
    role: 'assistant',
    type: 'chat',
    content: '',
    streamText: '',
    reasoning: [],
    showReasoning: true,
    loading: true,
    done: false,
    timestamp: Date.now(),
  }
  messages.value = [...messages.value, aiMsg]

  await playSimulatedReply({
    msg: aiMsg,
    thinkingSteps: playbook.thinkingSteps || [],
    content: playbook.content || '',
    onTick: () => {
      messages.value = [...messages.value]
    },
  })

  if (playbook.nextSteps?.length) {
    aiMsg.nextSteps = playbook.nextSteps
  }
  if (playbook.showRootCausePanel) {
    showRootCausePanel.value = true
    showRiskAuditPanel.value = false
  }
  if (playbook.showRiskAuditPanel) {
    showRiskAuditPanel.value = true
    showRootCausePanel.value = false
  }
  messages.value = [...messages.value]
}

async function runOpsScenario(text, scenario) {
  streaming.value = true
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
  ]

  try {
    let playbook
    if (scenario === 'root-cause') {
      playbook = await productConfig.runRootCauseAnalysis(text)
    } else if (scenario === 'risk-audit') {
      playbook = await productConfig.runRiskAuditFlow()
    } else {
      playbook = {
        thinkingSteps: ['未识别为本地运营演示场景，回退流式对话'],
        content: '正在转交后端处理…',
      }
      await playOpsReply(playbook)
      streaming.value = false
      sendMessage({ text, scene: config.defaultScene })
      return
    }
    await playOpsReply(playbook)
  } catch (e) {
    console.warn('[OpsAssistant] scenario failed:', e)
    await playOpsReply({
      thinkingSteps: ['运营分析失败'],
      content: `处理失败：${e?.message || '请稍后重试'}`,
    })
  } finally {
    streaming.value = false
  }
}

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  if (!text || streaming.value) return
  inputText.value = ''

  const scene = payload?.scene || config.defaultScene
  const scenario = resolveOpsScenario(text, scene)
  if (scenario === 'root-cause' || scenario === 'risk-audit') {
    await runOpsScenario(text, scenario)
    return
  }
  sendMessage({ text, scene })
}

const onSuggest = async (text) => {
  if (!text || streaming.value) return
  const scenario = resolveOpsScenario(text, config.defaultScene)
  if (scenario === 'root-cause' || scenario === 'risk-audit') {
    await runOpsScenario(text, scenario)
    return
  }
  sendMessage({ text, scene: config.defaultScene })
}

const onShortcut = async (item) => {
  if (!item?.text || streaming.value) return
  const scenario = resolveOpsScenario(item.text, item.scene)
  if (scenario === 'root-cause' || scenario === 'risk-audit') {
    await runOpsScenario(item.text, scenario)
    return
  }
  sendMessage({ text: item.text, scene: item.scene })
}

const onSwitchSession = (sessionId) => {
  closeRootCausePanel()
  closeRiskAuditPanel()
  switchSession(sessionId)
}

const onNewSession = () => {
  closeRootCausePanel()
  closeRiskAuditPanel()
  productConfig.resetState()
  newSession()
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
  }
}

function closeRootCausePanel() {
  showRootCausePanel.value = false
}

function closeRiskAuditPanel() {
  showRiskAuditPanel.value = false
}

function onCreateWorkOrder(wo) {
  const title = wo?.title || '产品优化工单草稿'
  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'assistant',
      type: 'chat',
      content: `已生成工单草稿：**${title}**\n\n${(wo?.actions || []).map((a) => `- ${a}`).join('\n')}`,
      done: true,
      timestamp: Date.now(),
    },
  ]
}

async function onReAudit(payload) {
  const playbook = await productConfig.runRiskAuditFlow(payload || {})
  await playOpsReply(playbook)
}
</script>

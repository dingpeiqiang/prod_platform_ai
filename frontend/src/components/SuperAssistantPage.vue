<template>
  <AssistantShell
    mode="super"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    :context="contextItems"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="loadSessions"
    @switch-session="onSwitchSession"
    @delete-session="onDeleteSession"
    @shortcut="onShortcut"
    @quick-action="onQuickAction"
    @open-model-config="onOpenModelConfig"
  >
    <ChatMessageList
      mode="super"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
      @undo-action="onUndoAction"
      @clarify-submit="onClarifySubmit"
      @query-result-click="onQueryResultClick"
      @compare-tray-add="onCompareTrayAdd"
    />
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { genId } from '../utils/chatUtils.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeScene = ref(assistantModes.super.defaultScene)

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
  sessionId,
  stop,
} = useChatStream()

const config = assistantModes.super

/** 会话上下文标签（ContextBar）：从最近一条助手消息的路由结果派生 */
const contextItems = computed(() => {
  const doneMsgs = messages.value.filter(m => m.role === 'assistant' && m.done)
  const last = doneMsgs[doneMsgs.length - 1]
  if (!last) return []
  const items = []
  const route = last.route
  if (route?.last_scene) {
    items.push({
      key: 'route:scene',
      label: '能力域',
      value: sceneLabelZh(route.last_scene) + (route.switched ? '（已切换）' : ''),
      type: 'intent',
      removable: false,
    })
  }
  const plan = last.queryPlan
  if (plan) {
    if (Array.isArray(plan.clarify) && plan.clarify.length) {
      for (const p of plan.clarify) {
        items.push({ key: `clarify:${p}`, label: '待补充', value: p, type: 'intent', removable: true })
      }
    }
    if (plan.intent && plan.intent !== 'CHAT') {
      items.push({ key: 'intent:plan', label: '业务意图', value: plan.intent, type: 'intent', removable: true })
    }
  }
  return items
})

function sceneLabelZh(scene) {
  const map = { rd: '研发', ops: '运营', query: '查询' }
  return map[scene] || scene
}

/** 超级助手触达的后端意图/工具：query 域结果卡片与比对（复用 query 页后处理） */
const SUPER_POST_INTENTS = [
  'RD_CONFIG_DISCOVER',
  'RD_SCHEME_COMPARE',
  'product_ops_query',
  'product_ops_compare',
]

onMounted(async () => {
  for (const intent of SUPER_POST_INTENTS) {
    registerPostProcessor(intent, null)
  }
  historyLoading.value = true
  try {
    await loadSessions()
  } finally {
    historyLoading.value = false
  }
})

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  if (!text || streaming.value) return
  inputText.value = ''
  // 统一走翻译层入口，scene=auto 交由后端 SuperAssistantRouter 自主路由
  await sendAgentMessage({ text, scene: 'auto' })
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
  await sendAgentMessage({ text: '继续', params, scene: 'auto' })
}

/** 点击场景标签：本地展示欢迎信息（不请求模型） */
async function showSceneWelcome(item) {
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
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
  if (/研发|配置|套餐|草稿|方案/.test(action.label || action.content || '')) scene = 'auto.rd'
  else if (/运营|归因|稽核|监控|洞察/.test(action.label || action.content || '')) scene = 'auto.ops'
  else if (/查询|问答|档案|比对|对比/.test(action.label || action.content || '')) scene = 'auto.query'
  const matched = config.sceneShortcuts.find((s) => s.scene === scene || s.label === action.label)
  await showSceneWelcome(matched || {
    label: action.label,
    scene: scene || activeScene.value,
    desc: action.desc || action.label,
    text: action.content || action.text || '',
  })
}

const onSwitchSession = async (targetSessionId) => {
  activeScene.value = config.defaultScene
  historyLoading.value = true
  try {
    await switchSession(targetSessionId)
  } finally {
    historyLoading.value = false
  }
}

const onNewSession = () => {
  activeScene.value = config.defaultScene
  newSession()
}

const onDeleteSession = async (sid) => {
  if (!sid) return
  await deleteSession(sid)
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
  }
}

/** 档案结果条目点击：引导切换到对应专属助手继续操作 */
async function onQueryResultClick() {
  ElMessageBox.alert(
    '超级助手已定位到该商品，可切换到「产品查询助手」查看详情或复制为草稿。',
    '能力域引导',
    { type: 'info' },
  )
}

function onCompareTrayAdd() {
  ElMessageBox.alert(
    '超级助手暂不支持对比清单，可切换到「产品查询助手」使用多商品比对。',
    '能力域引导',
    { type: 'info' },
  )
}

/** 对话内撤销（v3.2 可逆操作）：超级助手透传各域操作，占位提示 */
const onUndoAction = async () => {
  ElMessageBox.alert('请切换到对应专属助手执行撤销操作。', '撤销', { type: 'info' })
}
</script>

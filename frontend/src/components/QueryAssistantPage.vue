<template>
  <AssistantShell
    mode="query"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    :context="contextItems"
    :panelOpen="panelSync.panelOpen.value"
    :panelWidth="panelWidth"
    @update:panelWidth="panelWidth = $event"
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
      mode="query"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
      @undo-action="onUndoAction"
      @clarify-submit="onClarifySubmit"
      @query-result-click="onQueryResultClick"
    />

    <!-- 右侧面板：比对分析结果 -->
    <template #panel>
      <ComparePanel
        v-if="panelSync.panelType.value === 'compare'"
        :compareResult="productConfig.compareResult.value"
      />
    </template>

    <!-- 还原条：面板关闭后一键恢复 -->
    <template #restore-bar>
      <Transition name="restore-slide">
        <div v-if="panelSync.restoreBar.visible" class="restore-bar">
          <span class="restore-badge">比对分析</span>
          <span class="restore-name">{{ panelSync.restoreBar.productName || '多商品横向比对' }}</span>
          <button class="restore-btn" @click="panelSync.restorePanel()">恢复面板</button>
          <button class="restore-close" @click="panelSync.hideRestoreBar()">✕</button>
        </div>
      </Transition>
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import ComparePanel from './workbench/ComparePanel.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { usePanelSync } from '../composables/usePanelSync.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { copyAsDraft } from '../services/productOntologyApi.js'
import { saveMessage as saveChatMessage } from '../services/chatApi.js'
import { genId } from '../utils/chatUtils.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeScene = ref(assistantModes.query.defaultScene)
const panelWidth = ref(480)

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

const productConfig = useProductConfig()
const panelSync = usePanelSync()
const config = assistantModes.query

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
        items.push({ key, label: '当前查询', value: String(params.offering), type: 'entity', removable: true })
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
    concept: '本体概念',
    question: '查询内容',
  }
  return map[key] || key
}

function intentLabelZh(intent) {
  const map = {
    SPARQL_QUERY: '数据查询',
    ONTOLOGY_EXPLAIN: '概念解释',
    product_ops_query: '数据查询',
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

/** 意图后处理器注册键：覆盖 query 助手三类场景触达的后端意图/工具 */
const QUERY_POST_INTENTS = [
  'RD_CONFIG_DISCOVER',
  'RD_SCHEME_COMPARE',
  'product_ops_query',
  'product_ops_compare',
]

/** 意图后处理：智查/比对结果驱动消息槽位与右侧面板 */
function applyQueryToolToMsg(msg) {
  if (!msg || !Array.isArray(msg.toolResults)) return
  const done = msg.toolResults.filter((t) => t.status === 'done')
  for (const tool of done) {
    const out = tool.output || {}
    const name = tool.name || ''
    if (name === 'rd_config_discover') {
      // 档案调阅结果 → 商品列表卡片（条目点击 → query-result-click → 复制为草稿）
      const items = Array.isArray(out.items) ? out.items : []
      if (items.length) {
        msg.queryResults = items
          .map((it) => {
            const id = it.offering_id || it.offeringId || it.id || it.code
            const name2 = it.offering_name || it.offeringName || it.name
            if (!id && !name2) return null
            const fee = it.monthly_fee ?? it.monthlyFee
            const state = it.state || ''
            const category = it.category_name || it.categoryName || ''
            const parts = [fee != null ? `月费 ${fee} 元` : null, category, state].filter(Boolean)
            return {
              id: id || name2,
              code: id || '',
              name: name2 || id,
              desc: parts.join(' · '),
              offeringId: id,
            }
          })
          .filter(Boolean)
        messages.value = [...messages.value]
      }
    } else if (name === 'rd_scheme_compare') {
      applyQueryCompare(out)
    }
  }
}

/** 比对工具 output → compareResult + 打开右侧比对面板 */
function applyQueryCompare(out) {
  const result = {
    comparisons: Array.isArray(out.comparisons) ? out.comparisons : (out.config?.comparisons || []),
    recommended: out.recommended || out.config?.recommended || null,
    explanation: out.nl_answer || out.summary || out.config?.explanation || '',
  }
  productConfig.compareResult.value = result
  productConfig.showComparePanel.value = true
  panelSync.openComparePanel()
}

onMounted(async () => {
  for (const intent of QUERY_POST_INTENTS) {
    registerPostProcessor(intent, (msg) => applyQueryToolToMsg(msg))
  }
  historyLoading.value = true
  try {
    await loadSessions()
  } finally {
    historyLoading.value = false
  }
})

onUnmounted(() => {
  for (const intent of QUERY_POST_INTENTS) {
    registerPostProcessor(intent, null)
  }
})

/** scene 码 → 后端 ChatStream scene（query 三场景均由后端理解层自行判定，仅传软提示） */
function sceneToBackendScene(scene) {
  const map = {
    'query.ask': 'query',
    'query.archive': 'rd',
    'query.compare': 'compare',
  }
  return map[scene] || null
}

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  if (!text || streaming.value) return
  inputText.value = ''
  const scene = payload?.scene || activeScene.value || config.defaultScene
  // 统一走翻译层入口（三阶架构：理解→执行→表达），scene 作为软提示由后端理解层自行判定
  await sendAgentMessage({ text, scene: sceneToBackendScene(scene) })
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
  if (/问答|多少|资费|限制|规范|时限/.test(action.label || action.content || '')) scene = 'query.ask'
  else if (/档案|调阅|检索/.test(action.label || action.content || '')) scene = 'query.archive'
  else if (/比对|对比/.test(action.label || action.content || '')) scene = 'query.compare'
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
  panelSync.resetPanelState()
  newSession()
}

/** 删除会话：调 useChatStream 删除（后端 + 列表刷新） */
const onDeleteSession = async (sid) => {
  if (!sid) return
  await deleteSession(sid)
}

/** 档案调阅结果条目点击：复制为配置草稿（复用 copy-as-draft + 合规 + 复制即开单） */
async function onQueryResultClick(item) {
  if (!item || streaming.value) return
  const offeringId = item.offeringId || item.code || item.id
  if (!offeringId) {
    ElMessageBox.alert('该结果缺少商品编码，无法复制', '复制配置', { type: 'warning' })
    return
  }
  const name = item.name || offeringId
  let requirement = null
  try {
    const res = await ElMessageBox.prompt(
      `正在复制「${name}」（${offeringId}）为新配置草稿。请补充新方案的差异化需求（如改名、调资费、换客群等），可留空表示原样复制。`,
      '复制配置 · 补充需求',
      {
        type: 'info',
        confirmButtonText: '复制',
        cancelButtonText: '取消',
        inputPlaceholder: '例：改名为校园青春版，月费 29 元，面向大学生',
      },
    )
    requirement = String(res?.value || '').trim() || null
  } catch {
    return
  }
  streaming.value = true
  try {
    // 复制动作入会话历史（与 RD 页复制路径对齐），避免刷新后上下文断裂
    const actionText = `复制配置「${name}」（${offeringId}）为新草稿${requirement ? `；补充需求：${requirement}` : ''}`
    messages.value = [
      ...messages.value,
      { id: genId(), role: 'user', type: 'chat', content: actionText, done: true, timestamp: Date.now() },
    ]
    const sid = sessionId.value
    if (sid) {
      try {
        await saveChatMessage(sid, { role: 'user', content: actionText, contentType: 'text', done: true })
      } catch (e) {
        console.warn('[QueryAssistantPage] 复制配置用户消息落库失败:', e?.message || e)
      }
    }
    const thinkingSteps = [
      `选中检索结果「${name}」`,
      ...(requirement ? [`按补充需求调整：${requirement}`] : []),
      'copy_as_draft 深拷贝生成草稿',
      'evaluate_policy 执行 R-C* 合规校验',
      '复制即开单：创建配置工单',
    ]
    const result = await copyAsDraft(offeringId, item.name || null, sessionId.value, requirement)
    if (result?.success === false) {
      throw new Error(result.message || '复制失败')
    }
    const passLabel = result.compliancePass ? '✅ 合规通过' : '⚠️ 存在待处理项'
    const appliedNote = result.applied_requirements
      ? `已按补充需求调整：${Object.keys(result.applied_requirements).join('、')}。`
      : ''
    const content =
      `已将「${result.source_offering_name || offeringId}」复制为新草稿「${result.draft?.offeringName || ''}」。${appliedNote}${passLabel}\n\n` +
      '可切换到**研发助手**继续编辑该草稿并提交入库。'
    await playQueryReply({ thinkingSteps, content })
  } catch (e) {
    await playQueryReply({
      thinkingSteps: [`复制「${name}」失败`],
      content: `复制配置失败：${e.message || '本体服务不可用'}。请确认商品编码有效。`,
    })
  } finally {
    streaming.value = false
  }
}

/** 本地剧本回复：占位气泡 + 流式打字（与 RD 页 playProductReply 对齐的轻量版） */
async function playQueryReply(playbook = {}) {
  const aiMsg = {
    id: genId(),
    role: 'assistant',
    type: 'chat',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
  }
  messages.value = [...messages.value, aiMsg]

  const allSteps = playbook.thinkingSteps || []
  if (allSteps.length) {
    aiMsg.reasoning = allSteps.map((raw) =>
      typeof raw === 'string' ? { content: raw, result: raw } : raw,
    )
    aiMsg.showReasoning = true
  }
  aiMsg.loading = false
  messages.value = [...messages.value]

  const content = playbook.content || ''
  for (let i = 0; i < content.length; i += 12) {
    await new Promise((r) => setTimeout(r, 8))
    aiMsg.streamText = (aiMsg.streamText || '') + content.slice(i, i + 12)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  aiMsg.done = true
  aiMsg.loading = false
  if (playbook.nextSteps?.length) aiMsg.nextSteps = playbook.nextSteps
  messages.value = [...messages.value]

  // 剧本回复落库：保证历史会话 = 实际会话快照
  const sid = sessionId.value
  if (sid && aiMsg.content) {
    try {
      await saveChatMessage(sid, {
        role: 'assistant',
        content: aiMsg.content,
        contentType: 'chat',
        done: true,
        streamText: aiMsg.content,
        reasoning: aiMsg.reasoning || [],
      })
    } catch (e) {
      console.warn('[QueryAssistantPage] 本地剧本回复落库失败:', e?.message || e)
    }
  }
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
  }
}

/** 对话内撤销（v3.2 可逆操作）：查询助手暂无写操作，占位提示 */
const onUndoAction = async () => {
  ElMessageBox.alert('查询助手为只读助手，暂无可撤销的操作。', '撤销', { type: 'info' })
}
</script>

<style scoped>
.restore-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  font-size: 12px;
}
.restore-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f5f3ff;
  color: #6d28d9;
  font-weight: 600;
}
.restore-name {
  color: #334155;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.restore-btn {
  margin-left: auto;
  border: 1px solid #6d28d9;
  background: #6d28d9;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.restore-btn:hover { opacity: 0.85; }
.restore-close {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 12px;
  padding: 4px;
}
.restore-close:hover { color: #475569; }
.restore-slide-enter-active,
.restore-slide-leave-active { transition: transform 0.2s, opacity 0.2s; }
.restore-slide-enter-from,
.restore-slide-leave-to { transform: translateY(-100%); opacity: 0; }
</style>

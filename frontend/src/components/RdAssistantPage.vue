<template>
  <AssistantShell
    mode="rd"
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
    @quick-action="onQuickAction"
    @open-model-config="onOpenModelConfig"
  >
    <template #nav-actions>
      <button type="button" class="nav-product-btn" @click="showProductListPanel = true">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="8" y1="6" x2="21" y2="6"/>
          <line x1="8" y1="12" x2="21" y2="12"/>
          <line x1="8" y1="18" x2="21" y2="18"/>
          <line x1="3" y1="6" x2="3.01" y2="6"/>
          <line x1="3" y1="12" x2="3.01" y2="12"/>
          <line x1="3" y1="18" x2="3.01" y2="18"/>
        </svg>
        已配置商品
        <span class="count-badge">{{ products.length }}</span>
      </button>
    </template>

    <ChatMessageList
      mode="rd"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
      @form-card-click="onFormCardClick"
      @query-result-click="onQueryResultClick"
    />

    <ProductListPanel
      v-model="showProductListPanel"
      :products="products"
      :current-product-id="currentProductId"
      @select="handleProductSelect"
      @copy="handleProductCopy"
      @edit="handleProductSelect"
      @delete="handleProductDelete"
    />

    <template #right>
      <OpsRootCausePanel
        v-if="showRootCausePanel && rootCauseResult"
        :visible="showRootCausePanel"
        :result="rootCauseResult"
        :ontology-chain="rootCauseOntologyChain"
        v-model:active-rank="activeRootCauseRank"
        @close="showRootCausePanel = false"
        @create-work-order="onCreateWorkOrder"
      />
      <OpsRiskAuditPanel
        v-else-if="showRiskAuditPanel && riskAuditResult"
        :visible="showRiskAuditPanel"
        :result="riskAuditResult"
        @close="showRiskAuditPanel = false"
        @re-audit="onReAudit"
      />
      <ConfigTracePanel
        v-else-if="showConfigTracePanel"
        :visible="showConfigTracePanel"
        :trace-id="lastConfigTraceId"
        :steps="configTraceSteps"
        :explanation="configExplainText"
        @close="showConfigTracePanel = false"
      />
      <ConfigComparePanel
        v-else-if="showComparePanel && compareResult"
        :visible="showComparePanel"
        :result="compareResult"
        @close="showComparePanel = false"
      />
      <FormPanel
        v-else-if="activeFormCard"
        :form-schema="activeFormCard.formSchema"
        :form-id="activeFormCard.formId"
        :form-submitted="!!activeFormCard.formSubmitted"
        :show-compliance="
          activeFormCard.formCode === 'offering_config' ||
          !!activeFormCard.issues?.length ||
          !!activeFormCard.inferredFields?.length
        "
        :require-compliance="activeFormCard.formCode === 'offering_config'"
        :issues="activeFormCard.issues || []"
        :compliance-pass="!!activeFormCard.compliancePass"
        :inferred-fields="activeFormCard.inferredFields || []"
        @field-change="handleProductFieldChange"
        @confirm-submit="handleConfirmSubmit"
        @submit="handleConfirmSubmit"
        @cancel="closeActiveForm"
      />
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, onMounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import FormPanel from './FormPanel.vue'
import ProductListPanel from './ProductListPanel.vue'
import OpsRootCausePanel from './OpsRootCausePanel.vue'
import OpsRiskAuditPanel from './OpsRiskAuditPanel.vue'
import ConfigTracePanel from './ConfigTracePanel.vue'
import ConfigComparePanel from './ConfigComparePanel.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { checkCompliance } from '../services/productOntologyApi.js'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { ZHIDU_TEST_PROMPT } from '../data/zhiduTestDoc.js'
import { genId } from '../utils/chatUtils.js'
import { createStreamingPlaceholder } from '../utils/simulateReply.js'
import { sleep } from '../utils/simulateReply.js'
import {
  applyScheduleResults,
  createSchedule,
  revealOntologyChain,
  scenarioLabel,
  startScheduleTicker,
} from '../utils/thinkingSchedule.js'

/** 智读引导话术：点击后填入可解析演示正文，而非空指令 */
const ZHIDU_GUIDE_RE =
  /粘贴方案文档后说|请按文档内容生成配置草稿|上传家庭融合方案|按文档映射为配置草稿|演示：导入家庭融合方案/

const inputText = ref('')
const historyLoading = ref(false)
const activeFormCard = ref(null)

const router = useRouter()
const onOpenModelConfig = () => router.push('/model-config')
/** 侧边快捷场景码，发送时优先使用（如 rd.import） */
const activeScene = ref(assistantModes.rd.defaultScene)

const {
  messages,
  streaming,
  sendMessage,
  loadSessions,
  switchSession,
  newSession,
  sessionList,
  sessionId,
  stop,
} = useChatStream()

const productConfig = useProductConfig()
const products = productConfig.products
const currentProductId = productConfig.currentProductId
const currentProduct = productConfig.currentProduct
const showProductListPanel = productConfig.showProductListPanel
const showRootCausePanel = productConfig.showRootCausePanel
const showRiskAuditPanel = productConfig.showRiskAuditPanel
const showConfigTracePanel = productConfig.showConfigTracePanel
const showComparePanel = productConfig.showComparePanel
const compareResult = productConfig.compareResult
const lastConfigTraceId = productConfig.lastConfigTraceId
const configTraceSteps = productConfig.configTraceSteps
const configExplainText = productConfig.configExplainText
const rootCauseResult = productConfig.rootCauseResult
const riskAuditResult = productConfig.riskAuditResult
const rootCauseOntologyChain = productConfig.rootCauseOntologyChain
const activeRootCauseRank = productConfig.activeRootCauseRank
provide('rootCauseActiveRank', activeRootCauseRank)
const config = assistantModes.rd

onMounted(async () => {
  historyLoading.value = true
  try {
    await loadSessions()
    if (sessionId.value) {
      productConfig.setSessionContext({ sessionId: sessionId.value })
      await productConfig.loadPersistedDrafts(sessionId.value)
    }
  } finally {
    historyLoading.value = false
  }
})

function applyFormCard(formCard) {
  if (!formCard) return
  activeFormCard.value = {
    ...formCard,
    issues: formCard.issues || [],
    compliancePass: !!formCard.compliancePass,
    inferredFields: formCard.inferredFields || [],
    formSubmitted: !!formCard.formSubmitted,
  }
}

function closeActiveForm() {
  activeFormCard.value = null
}

async function refreshCompliance() {
  const product = currentProduct.value
  const draft = product?.ontologyDraft
  if (!draft || !activeFormCard.value) return null

  const result = await checkCompliance(draft)
  if (result?.success === false) {
    throw new Error(result.message || '合规校验失败')
  }
  product.compliancePass = !!result.compliancePass
  product.issues = result.issues || []
  product.auditStatus = result.compliancePass ? 'pass' : 'pending'
  product.name = draft.offeringName || product.name
  product.desc = `月费${draft.monthlyFee ?? '-'} | ${draft.bizScenario || ''} | ${draft.channelScope || ''}`

  activeFormCard.value.compliancePass = !!result.compliancePass
  activeFormCard.value.issues = result.issues || []
  activeFormCard.value.inferredFields = result.inferredFields || activeFormCard.value.inferredFields || []
  return result
}

async function handleProductFieldChange(fieldCode, value) {
  productConfig.updateFormField(fieldCode, value)
  if (activeFormCard.value?.formData) {
    activeFormCard.value.formData[fieldCode] = value
  }
  if (activeFormCard.value?.formSchema?.fields) {
    const field = activeFormCard.value.formSchema.fields.find((f) => f.fieldCode === fieldCode)
    if (field) field.value = value
  }
  if (activeFormCard.value?.formCode === 'offering_config') {
    await refreshCompliance()
  }
}

async function handleConfirmSubmit(payload) {
  const product = currentProduct.value
  if (!product) return

  // 同步确认提交时的表单值
  const data = payload?.data || payload
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    Object.entries(data).forEach(([key, value]) => {
      if (key === 'formId') return
      productConfig.updateFormField(key, value)
      if (activeFormCard.value?.formData) activeFormCard.value.formData[key] = value
      if (activeFormCard.value?.formSchema?.fields) {
        const field = activeFormCard.value.formSchema.fields.find((f) => f.fieldCode === key)
        if (field) field.value = value
      }
    })
  }

  if (activeFormCard.value?.formCode === 'offering_config') {
    const result = await refreshCompliance()
    if (!result?.compliancePass) return
  }

  productConfig.saveDraftLocal()
  productConfig.setSessionContext({ sessionId: sessionId.value })
  const resp = await productConfig.submitCurrentDraft(sessionId.value)
  if (resp?.success === false) {
    await playProductReply({
      thinkingSteps: ['提交备案被拒'],
      content: `提交失败：${resp.message || '合规未通过或服务异常'}`,
      formCard: activeFormCard.value,
    })
    return
  }

  const draft = product.ontologyDraft || product.data || {}
  const offeringId = resp.offeringId || product.offeringId
  const woId = resp.workOrder?.workOrderId || product.workOrderId || '-'
  product.name = draft.offeringName || product.name

  if (activeFormCard.value) {
    activeFormCard.value.formSubmitted = true
    activeFormCard.value.compliancePass = true
    activeFormCard.value.issues = []
  }

  showProductListPanel.value = true

  await playProductReply({
    thinkingSteps: [
      '核对 compliancePass=true（R-C08）',
      '沉淀 ConfigScheme 至事实图/本体',
      `生成资费备案工单 ${woId}`,
    ],
    content:
      `已完成提交闭环：\n\n` +
      `- 商品编码：\`${offeringId}\`\n` +
      `- 备案工单：\`${woId}\`\n` +
      `- 草稿 ID：\`${resp.draftId || product.draftId || '-'}\`\n\n` +
      '可在「已配置商品」查看；运营侧工单列表可跟进备案进度。\n\n' +
      '```json\n' +
      JSON.stringify(
        {
          offeringId,
          workOrderId: woId,
          draftId: resp.draftId,
          offeringName: draft.offeringName,
          monthlyFee: draft.monthlyFee ?? draft.fixedFeeAmount,
          status: 'filing',
        },
        null,
        2,
      ) +
      '\n```',
    formCard: {
      ...productConfig.buildProductFormCard(product),
      formSubmitted: true,
      compliancePass: true,
      issues: [],
    },
    nextSteps: ['查看审计追溯', '查一下近30天大学生套餐'],
  })
}

function handleProductSelect(id) {
  const formCard = productConfig.selectProduct(id)
  if (formCard) applyFormCard(formCard)
  showProductListPanel.value = false
}

function handleProductCopy(id) {
  const copied = productConfig.copyProduct(id)
  if (!copied) return
  const formCard = productConfig.selectProduct(copied.id)
  if (formCard) applyFormCard(formCard)
}

function handleProductDelete(id) {
  const formCard = productConfig.deleteProduct(id)
  if (formCard) applyFormCard(formCard)
  else closeActiveForm()
}

async function onQueryResultClick(item) {
  if (!item || streaming.value) return
  streaming.value = true
  try {
    // 本体智查结果：走 copy-as-draft + 合规；本地 mock 带 data 时同样优先后端
    const playbook = await productConfig.prepareProduct(item)
    if (playbook?.formCard) {
      applyFormCard(playbook.formCard)
    }
    if (playbook) {
      await playProductReply(playbook)
    }
  } finally {
    streaming.value = false
  }
}

/** 研发快捷场景 / 文案 → 本体演示剧本 */
function resolveProductScenario(text, scene = '') {
  const s = String(scene || '')
  if (
    s === 'rd.chat' ||
    s === 'offering_config_chat' ||
    s === 'rd_offering_config' ||
    s === 'offering_config'
  ) {
    return 'chat-generate'
  }
  if (s === 'rd.import' || s === 'offering_config_batch') {
    return 'file-parse'
  }
  if (s === 'rd.compare') {
    return 'compare'
  }
  if (s === 'rd.compliance' || s === 'offering_config_compliance') {
    return productConfig.detectScenario(text) || 'compliance'
  }
  // 智查快捷场景：优先按文案识别，避免示例「查一下…」落到对话配置
  if (s === 'market_insight' || s === 'rd.query') {
    return productConfig.detectScenario(text) || 'query'
  }
  return productConfig.detectScenario(text)
}

function tickMessages() {
  messages.value = [...messages.value]
}

function applyPlaybookSideEffects(aiMsg, playbook = {}) {
  if (playbook.nextSteps?.length) {
    aiMsg.nextSteps = playbook.nextSteps
  }
  if (playbook.formCard) {
    applyFormCard(playbook.formCard)
  }
  if (playbook.showRootCausePanel) {
    showRootCausePanel.value = true
    showRiskAuditPanel.value = false
    showConfigTracePanel.value = false
    closeActiveForm()
  }
  if (playbook.showRiskAuditPanel) {
    showRiskAuditPanel.value = true
    showRootCausePanel.value = false
    showConfigTracePanel.value = false
    closeActiveForm()
  }
  if (playbook.showConfigTracePanel || productConfig.showConfigTracePanel.value) {
    showConfigTracePanel.value = true
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    showComparePanel.value = false
  }
  if (playbook.showComparePanel || productConfig.showComparePanel.value) {
    showComparePanel.value = true
    showConfigTracePanel.value = false
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    closeActiveForm()
  }
  tickMessages()
}

/** 结果回填到同一条调度时间线，再流式输出正文（不再另起一套「思考过程」） */
async function finishProductReply(aiMsg, playbook = {}) {
  const allSteps = playbook.thinkingSteps || []
  aiMsg.reasoning = applyScheduleResults(aiMsg.reasoning || [], allSteps)
  aiMsg.showReasoning = true
  tickMessages()

  const onto = (aiMsg.reasoning || []).find((s) => s.type === 'ontology' && s.ontologyChain)
  if (onto?.id) {
    await revealOntologyChain(aiMsg, onto.id, { onTick: tickMessages, delay: 90 })
  }

  aiMsg.loading = false
  tickMessages()

  const content = playbook.content || ''
  for (let i = 0; i < content.length; i += 12) {
    await sleep(8)
    const chunk = content.slice(i, i + 12)
    aiMsg.streamText = (aiMsg.streamText || '') + chunk
    aiMsg.content = aiMsg.streamText
    tickMessages()
  }

  if (playbook.formCard) aiMsg.formCard = playbook.formCard
  if (playbook.queryResults) aiMsg.queryResults = playbook.queryResults
  aiMsg.done = true
  aiMsg.loading = false
  tickMessages()
  applyPlaybookSideEffects(aiMsg, playbook)
}

async function playProductReply(playbook = {}, scenario = 'chat-generate') {
  const aiMsg = createStreamingPlaceholder(genId)
  aiMsg._scenario = scenario
  aiMsg.reasoning = createSchedule(scenario)
  messages.value = [...messages.value, aiMsg]
  tickMessages()
  await sleep(280)
  await finishProductReply(aiMsg, {
    ...playbook,
    thinkingSteps: normalizePlaybookSteps(playbook.thinkingSteps, scenario),
  })
}

async function loadScenarioPlaybook(text, scenario, attachments = []) {
  productConfig.setSessionContext({ sessionId: sessionId.value })
  if (scenario === 'query') {
    return productConfig.simulateQuery(text)
  }
  if (scenario === 'file-parse') {
    return productConfig.simulateFileParse(text, attachments)
  }
  if (scenario === 'confirm-batch') {
    return productConfig.confirmPassedDrafts()
  }
  if (scenario === 'compare') {
    return productConfig.runCompareSchemes(text)
  }
  if (scenario === 'config-trace') {
    const result = await productConfig.loadConfigTrace()
    return {
      thinkingSteps: ['加载配置审计链路 get_trace', '生成业务说明 explain(audience=business)'],
      content:
        result.explanation ||
        '已打开右侧「配置审计追溯」面板。' +
          (result.traceId ? `\n\ntrace：\`${result.traceId}\`` : ''),
      formCard: null,
      showConfigTracePanel: true,
    }
  }
  if (scenario === 'compliance') {
    return productConfig.runComplianceCheck(text)
  }
  if (scenario === 'root-cause') {
    return productConfig.runRootCauseAnalysis(text)
  }
  if (scenario === 'risk-audit') {
    return productConfig.runRiskAuditFlow()
  }
  return productConfig.generateProductFromChat(text)
}

function toDisplayAttachments(attachments = []) {
  return (attachments || []).map((a) => ({
    type: a.type || 'file',
    name: a.name || '附件',
    size: a.size,
    preview: a.preview || null,
    url: a.url || null,
    fileId: a.fileId || null,
    uploadStatus: a.uploadStatus || null,
    duration: a.duration,
  }))
}

/** 将 playbook 步骤对齐到场景模板 id，保证与调度表同一条时间线 */
function normalizePlaybookSteps(thinkingSteps = [], scenario = 'chat-generate') {
  const schedule = createSchedule(scenario)
  const raw = (thinkingSteps || []).map((src) => {
    if (typeof src === 'string') {
      return { id: null, type: 'llm', content: src, result: src }
    }
    return { ...src, id: src.id || null }
  })
  const byId = new Map(raw.filter((s) => s.id).map((s) => [s.id, s]))
  const hasIntent = byId.has('intent')
  // 与模板等长 → 按下标对齐；短一截 → 视为缺意图步，对齐到 intent 之后
  const useIndex = !hasIntent && raw.length === schedule.length
  let bodyCursor = 0

  return schedule.map((tpl, i) => {
    let src = byId.get(tpl.id) || null
    if (!src && useIndex) {
      src = raw[i] || null
    } else if (!src && tpl.id === 'intent') {
      src = { result: scenarioLabel(scenario) }
    } else if (!src && !hasIntent) {
      src = raw[bodyCursor] || null
      bodyCursor += 1
    }
    if (!src) {
      return {
        id: tpl.id,
        type: tpl.type,
        title: tpl.title,
        content: tpl.content,
        result: tpl.id === 'intent' ? scenarioLabel(scenario) : null,
      }
    }
    return {
      id: tpl.id,
      type: src.type || tpl.type,
      title: src.title || tpl.title,
      content: src.content || tpl.content,
      result:
        src.result !== undefined && src.result !== null
          ? src.result
          : tpl.id === 'intent'
            ? scenarioLabel(scenario)
            : null,
      metadata: src.metadata || null,
      details: src.details || null,
      elapsed: src.elapsed,
      ontologyChain: src.ontologyChain || null,
      ontologyPreview: src.ontologyPreview || null,
    }
  })
}

async function runProductScenario(text, scenario, attachments = []) {
  streaming.value = true
  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'user',
      type: 'chat',
      content: text,
      attachments: toDisplayAttachments(attachments),
      done: true,
      timestamp: Date.now(),
    },
  ]

  const aiMsg = createStreamingPlaceholder(genId)
  aiMsg._scenario = scenario
  // 同一条「思考过程」：意图 + 场景模板调度表挂上，结果原地回填
  aiMsg.reasoning = createSchedule(scenario)
  messages.value = [...messages.value, aiMsg]
  tickMessages()

  const ticker = startScheduleTicker(aiMsg, {
    onTick: tickMessages,
    stepDelay: 380,
  })

  try {
    const playbook = await loadScenarioPlaybook(text, scenario, attachments)
    ticker.stop()
    const normalizedSteps = normalizePlaybookSteps(playbook.thinkingSteps, scenario)
    await finishProductReply(aiMsg, { ...playbook, thinkingSteps: normalizedSteps })
  } catch (e) {
    ticker.stop()
    console.warn('[RdAssistant] product scenario failed:', e)
    await finishProductReply(aiMsg, {
      thinkingSteps: normalizePlaybookSteps(
        [
          { id: 'intent', result: scenarioLabel(scenario) },
          { id: 'ontology', content: '本体配置推理失败', result: e?.message || '请稍后重试' },
        ],
        scenario,
      ),
      content: `处理失败：${e?.message || '请稍后重试'}`,
    })
  } finally {
    ticker.stop()
    streaming.value = false
  }
}

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  const attachments = payload?.attachments || []
  if ((!text && !attachments.length) || streaming.value) return
  // 有附件时必须全部上传成功才允许发送
  if (attachments.length && attachments.some((a) => a.uploadStatus && a.uploadStatus !== 'success')) {
    return
  }
  inputText.value = ''

  const scene = payload?.scene || activeScene.value || config.defaultScene
  const scenario = resolveProductScenario(text || '智读', scene)
  // 有附件时优先走智读·文件配置
  const effectiveScenario =
    attachments.length && (!scenario || scenario === 'chat-generate')
      ? 'file-parse'
      : scenario
  if (effectiveScenario) {
    await runProductScenario(
      text || `导入文档：${attachments[0]?.name || '方案'}`,
      effectiveScenario,
      attachments,
    )
    return
  }
  sendMessage({ text, scene, attachments })
}

const onSuggest = (payload) => {
  if (!payload || streaming.value) return
  // 欢迎页场景卡：直接展示场景欢迎信息
  if (typeof payload === 'object' && (payload.guide || payload.autoSend || payload.welcome || payload.scene)) {
    showSceneWelcome(payload)
    return
  }
  let text = typeof payload === 'string' ? payload : payload.text
  if (!text) return
  // 智读引导芯片：预填可解析的演示方案，避免用户发送空指令后被当成「已上传文档」
  if (ZHIDU_GUIDE_RE.test(text)) {
    text = ZHIDU_TEST_PROMPT
    activeScene.value = 'rd.import'
  }
  // 跟进建议 / 推荐话术：预填输入框
  inputText.value = text
  const scenario = resolveProductScenario(text, activeScene.value || config.defaultScene)
  if (scenario === 'file-parse') {
    activeScene.value = 'rd.import'
  } else if (scenario === 'query') {
    activeScene.value = 'rd.query'
  } else if (scenario === 'compliance') {
    activeScene.value = 'rd.compliance'
  } else if (scenario === 'chat-generate') {
    activeScene.value = 'rd.chat'
    productConfig.createEmptyOfferingCanvas()
  }
}

/** 点击场景标签：本地展示欢迎信息（不请求模型） */
function showSceneWelcome(item) {
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
  }
  if (item.scene === 'rd.chat' || item.label === '智聊·对话配置') {
    productConfig.createEmptyOfferingCanvas()
  }

  const welcome = buildSceneWelcome(item)
  if (welcome.placeholder) {
    // 仅更新占位提示感：把推荐首条放入输入框可选；按需求不回显写死业务消息，保持输入框清空
    inputText.value = ''
  } else {
    inputText.value = ''
  }

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

const onShortcut = (item) => {
  showSceneWelcome(item)
}

/** 输入区快捷芯片 → 同场景欢迎信息 */
const onQuickAction = (action) => {
  if (!action || streaming.value) return
  const sceneMap = {
    chat: 'rd.chat',
    file: 'rd.import',
    query: 'rd.query',
    compliance: 'rd.compliance',
  }
  const scene = sceneMap[action.key] || action.scene || activeScene.value
  const matched = config.sceneShortcuts.find((s) => s.scene === scene || s.label === action.label)
  showSceneWelcome(matched || {
    label: action.label,
    scene,
    desc: action.desc || action.label,
    text: action.content || action.text || '',
  })
}

const onFormCardClick = (msg) => {
  if (msg?.formCard) applyFormCard(msg.formCard)
}

const onSwitchSession = async (sid) => {
  closeActiveForm()
  await switchSession(sid)
  productConfig.resetState()
  productConfig.setSessionContext({ sessionId: sid || sessionId.value })
  await productConfig.loadPersistedDrafts(sid || sessionId.value)
}

const onNewSession = () => {
  closeActiveForm()
  showRootCausePanel.value = false
  showRiskAuditPanel.value = false
  activeScene.value = config.defaultScene
  productConfig.resetState()
  newSession()
  productConfig.setSessionContext({ sessionId: sessionId.value })
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    onSuggest(event.payload.text)
  }
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
  await playProductReply(playbook)
}
</script>

<style scoped>
.nav-product-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #0f172a;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.nav-product-btn:hover {
  border-color: #5eead4;
  background: #f0fdfa;
}
.count-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #0f766e;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
</style>

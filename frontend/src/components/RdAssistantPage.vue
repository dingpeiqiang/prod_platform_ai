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
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import FormPanel from './FormPanel.vue'
import ProductListPanel from './ProductListPanel.vue'
import OpsRootCausePanel from './OpsRootCausePanel.vue'
import OpsRiskAuditPanel from './OpsRiskAuditPanel.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { checkCompliance } from '../services/ontologyMvpApi.js'
import { assistantModes } from '../config/assistantModes.js'
import { genId } from '../utils/chatUtils.js'
import { createStreamingPlaceholder, playSimulatedReply } from '../utils/simulateReply.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeFormCard = ref(null)
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
  stop,
} = useChatStream()

const productConfig = useProductConfig()
const products = productConfig.products
const currentProductId = productConfig.currentProductId
const currentProduct = productConfig.currentProduct
const showProductListPanel = productConfig.showProductListPanel
const showRootCausePanel = productConfig.showRootCausePanel
const showRiskAuditPanel = productConfig.showRiskAuditPanel
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

  productConfig.saveDraft()
  const draft = product.ontologyDraft || product.data || {}
  const draftId = `DRAFT-${Date.now().toString(36).toUpperCase()}-${Math.random().toString(36).slice(2, 5).toUpperCase()}`
  product.draftId = draftId
  product.status = 'submitted'
  product.auditStatus = 'pass'
  product.compliancePass = true
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
      '序列化 OfferingConfig 草稿字段',
      `Mock 生成草稿 draftId=${draftId}`,
    ],
    content:
      `已生成配置草稿 **\`${draftId}\`**，可在「已配置商品」中查看。\n\n` +
      '```json\n' +
      JSON.stringify(
        {
          draftId,
          offeringName: draft.offeringName,
          offeringType: draft.offeringType,
          bizScenario: draft.bizScenario,
          targetUser: draft.targetUser,
          monthlyFee: draft.monthlyFee,
          includeBroadband: draft.includeBroadband,
          channelScope: draft.channelScope,
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

function onQueryResultClick(item) {
  if (!item) return
  if (item.data || item.name) {
    const newProduct = {
      id: 'P' + Date.now(),
      name: item.name || '历史商品草稿',
      code: item.code || `NEW${Date.now()}`,
      desc: item.desc || '',
      template: item.template,
      status: 'draft',
      auditStatus: 'pending',
      data: JSON.parse(JSON.stringify(item.data || {})),
    }
    products.value.push(newProduct)
    currentProductId.value = newProduct.id
    productConfig.syncFormFromProduct(newProduct)
    applyFormCard(productConfig.buildProductFormCard(newProduct))
    return
  }
  const playbook = productConfig.prepareProduct(0)
  if (playbook?.formCard) applyFormCard(playbook.formCard)
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
    closeActiveForm()
  }
  if (playbook.showRiskAuditPanel) {
    showRiskAuditPanel.value = true
    showRootCausePanel.value = false
    closeActiveForm()
  }
  tickMessages()
}

/** 已有占位气泡时续播剧本（保留请求中的进度 thinking） */
async function finishProductReply(aiMsg, playbook = {}) {
  const allSteps = playbook.thinkingSteps || []
  // 进度已在请求期间展示，结果阶段只补本体链，避免再叠一层慢速 thinking
  const ontologySteps = allSteps.filter((s) => typeof s === 'object' && s.type === 'ontology')
  const thinkingSteps = ontologySteps.length
    ? ontologySteps
    : allSteps.slice(-1)

  await playSimulatedReply({
    msg: aiMsg,
    thinkingSteps,
    content: playbook.content || '',
    formCard: playbook.formCard || null,
    queryResults: playbook.queryResults || null,
    onTick: tickMessages,
    thinkDelay: 60,
    typeDelay: 3,
    preserveReasoning: true,
    skipChainReveal: true,
  })
  applyPlaybookSideEffects(aiMsg, playbook)
}

async function playProductReply(playbook = {}) {
  const aiMsg = createStreamingPlaceholder(genId)
  messages.value = [...messages.value, aiMsg]
  await finishProductReply(aiMsg, playbook)
}

async function loadScenarioPlaybook(text, scenario, attachments = []) {
  if (scenario === 'query') {
    return productConfig.simulateQuery(text)
  }
  if (scenario === 'file-parse') {
    return productConfig.simulateFileParse(text, attachments)
  }
  if (scenario === 'confirm-batch') {
    return productConfig.confirmPassedDrafts()
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

const SCENARIO_PROGRESS = {
  query: ['正在解析查询条件...', '检索本体图谱...'],
  'file-parse': ['正在读取方案文档...', '抽取套餐结构...'],
  'confirm-batch': ['正在确认批量草稿...'],
  compliance: ['正在定位套餐信息...', '执行配置合规规则...'],
  'root-cause': ['正在分析异动指标...', '本体归因推理中...'],
  'risk-audit': ['正在筛查风险规则...', '聚合稽核结果...'],
  'chat-generate': ['正在理解配置诉求...', '调用本体配置服务...'],
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
      done: true,
      timestamp: Date.now(),
    },
  ]

  const aiMsg = createStreamingPlaceholder(genId)
  const progressSteps = SCENARIO_PROGRESS[scenario] || SCENARIO_PROGRESS['chat-generate']
  // 阶段只追加一次；等待秒数原地刷新，避免刷屏
  aiMsg.reasoning = [{ type: 'llm', content: progressSteps[0] }]
  messages.value = [...messages.value, aiMsg]

  let progressIdx = 0
  const startedAt = Date.now()
  const heartbeat = setInterval(() => {
    const sec = ((Date.now() - startedAt) / 1000).toFixed(1)
    const list = [...(aiMsg.reasoning || [])]
    if (progressIdx < progressSteps.length - 1) {
      progressIdx += 1
      list.push({ type: 'llm', content: progressSteps[progressIdx] })
    } else {
      const waitingIdx = list.findIndex((s) => s._waiting)
      const waitingText = `本体推理进行中（${sec}s）...`
      if (waitingIdx >= 0) {
        list[waitingIdx] = { ...list[waitingIdx], content: waitingText }
      } else {
        list.push({ type: 'llm', content: waitingText, _waiting: true })
      }
    }
    aiMsg.reasoning = list
    tickMessages()
  }, 700)

  try {
    const playbook = await loadScenarioPlaybook(text, scenario, attachments)
    clearInterval(heartbeat)
    // 去掉等待行，再续播本体结果
    aiMsg.reasoning = (aiMsg.reasoning || []).filter((s) => !s._waiting)
    tickMessages()
    await finishProductReply(aiMsg, playbook)
  } catch (e) {
    clearInterval(heartbeat)
    aiMsg.reasoning = (aiMsg.reasoning || []).filter((s) => !s._waiting)
    console.warn('[RdAssistant] product scenario failed:', e)
    await finishProductReply(aiMsg, {
      thinkingSteps: ['本体配置推理失败'],
      content: `处理失败：${e?.message || '请稍后重试'}`,
    })
  } finally {
    clearInterval(heartbeat)
    streaming.value = false
  }
}

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  const attachments = payload?.attachments || []
  if ((!text && !attachments.length) || streaming.value) return
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
  sendMessage({ text, scene })
}

const onSuggest = (text) => {
  // 生产：场景卡只填入输入框，由用户确认后发送
  if (!text || streaming.value) return
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

const onShortcut = (item) => {
  // 生产：快捷场景只进入/预填，不自动发送示例话术
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
  }
  if (item.text) {
    inputText.value = item.text
  }
  if (item.scene === 'rd.chat' || item.label === '智聊·对话配置' || item.label === '智聊配置' || item.label === '对话配置') {
    productConfig.createEmptyOfferingCanvas()
  }
}

const onFormCardClick = (msg) => {
  if (msg?.formCard) applyFormCard(msg.formCard)
}

const onSwitchSession = (sessionId) => {
  closeActiveForm()
  switchSession(sessionId)
}

const onNewSession = () => {
  closeActiveForm()
  showRootCausePanel.value = false
  showRiskAuditPanel.value = false
  activeScene.value = config.defaultScene
  productConfig.resetState()
  newSession()
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

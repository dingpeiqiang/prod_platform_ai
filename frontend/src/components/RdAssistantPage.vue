<template>
  <AssistantShell
    mode="rd"
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
      :active-form-card="activeFormCard"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
      @undo-action="onUndoAction"
      @form-card-click="onFormCardClick"
      @form-submit="handleConfirmSubmit"
      @form-cancel="closeActiveForm"
      @form-field-change="handleInlineFieldChange"
      @form-ai-validation="handleAiValidation"
      @form-confirm-submit="handleConfirmSubmit"
      @form-close="closeActiveForm"
      @batch-confirm="handleBatchConfirm"
      @batch-fix="handleBatchFix"
      @batch-delete="handleBatchDelete"
      @query-result-click="onQueryResultClick"
      @trace-click="onTraceClick"
      @clarify-submit="onClarifySubmit"
      @file-ref-click="onFileRefClick"
    />

    <ProductListPanel
      v-model="showProductListPanel"
      :products="products"
      :current-product-id="currentProductId"
      @select="handleProductSelect"
      @preview="handleProductPreview"
      @copy="handleProductCopy"
      @edit="handleProductSelect"
      @delete="handleProductDelete"
    />

    <ProductPreviewDrawer v-model="showProductPreview" :product="previewProductData" />

    <template #right>
      <InsightBoard mode="rd" :messages="messages" :product-config="productConfig" />
    </template>
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import InsightBoard from './InsightBoard.vue'
import ProductListPanel from './ProductListPanel.vue'
import ProductPreviewDrawer from './ProductPreviewDrawer.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
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
} from '../utils/thinkingSchedule.js'

/** 智读引导话术：点击后填入可解析演示正文，而非空指令 */
const ZHIDU_GUIDE_RE =
  /粘贴方案文档后说|请按文档内容生成配置草稿|上传家庭融合方案|按文档映射为配置草稿|演示：导入家庭融合方案/

const inputText = ref('')
const historyLoading = ref(false)
const activeFormCard = ref(null)
/** 已配置商品只读预览（不进入编辑态） */
const showProductPreview = ref(false)
const previewProductData = ref(null)

const router = useRouter()
const onOpenModelConfig = () => router.push('/model-config')
/** 侧边快捷场景码，发送时优先使用（如 rd.import） */
const activeScene = ref(assistantModes.rd.defaultScene)

const {
  messages,
  streaming,
  sendAgentMessage,
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
const activeRootCauseRank = productConfig.activeRootCauseRank
provide('rootCauseActiveRank', activeRootCauseRank)
const config = assistantModes.rd

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
  const products = mp.products?.value || []
  const batchItems = mp.batchItems?.value || []
  const passed = batchItems.filter((i) => i.compliancePass || i.status === '通过').length
  const pending = batchItems.filter((i) => !(i.compliancePass || i.status === '通过')).length
  return [
    { label: '草稿', value: `${products.length}` },
    { label: '通过', value: `${passed}`, tone: 'good' },
    { label: '待修', value: `${pending}`, tone: pending ? 'warn' : 'neutral' },
    { label: '文档', value: `${messages.value.filter((m) => m.fileRef).length}` },
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

const RD_POST_INTENTS = [
  'RD_CONFIG_CHAT',
  'RD_FILE_PARSE',
  'RD_COMPLIANCE',
  'RD_CONFIG_DISCOVER',
  'RD_SCHEME_COMPARE',
]

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
  for (const intent of RD_POST_INTENTS) {
    registerPostProcessor(intent, (msg) => applyRdToolToPanels(msg))
  }
})

onUnmounted(() => {
  for (const intent of RD_POST_INTENTS) {
    registerPostProcessor(intent, null)
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

/** 内联表单字段变更：同步 productConfig 与当前激活表单 schema 的字段值 */
const handleInlineFieldChange = (fieldCode, value) => {
  handleProductFieldChange(fieldCode, value)
}

/** 内联表单 AI 补全回调：本实现暂仅刷新合规（回显字段已在 schema 更新） */
const handleAiValidation = async (data) => {
  if (activeFormCard.value?.formCode === 'offering_config' && data) {
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
    traceId: productConfig.lastConfigTraceId.value,
    _undoable: resp.actionId
      ? {
          actionId: resp.actionId,
          state: 'executed',
          label: `入库 · ${draft.offeringName || product.name}`,
          undoLabel: '撤销入库',
        }
      : null,
  })
}

function handleProductSelect(id) {
  const formCard = productConfig.selectProduct(id)
  if (formCard) applyFormCard(formCard)
  showProductListPanel.value = false
}

/** 只读预览已配置商品：不切换当前编辑态 */
function handleProductPreview(id) {
  const preview = productConfig.previewProduct(id)
  if (!preview) return
  previewProductData.value = preview
  showProductPreview.value = true
}

function handleProductCopy(id) {
  const copied = productConfig.copyProduct(id)
  if (!copied) return
  const formCard = productConfig.selectProduct(copied.id)
  if (formCard) applyFormCard(formCard)
}

function handleProductDelete(id) {
  const formCard = productConfig.deleteProduct(id)
  if (previewProductData.value?.id === id) {
    showProductPreview.value = false
    previewProductData.value = null
  }
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

/** 配置审计追溯：get_trace + explain，in-message 回放审计链路 */
async function handleConfigTrace(traceId = null) {
  if (streaming.value) return
  streaming.value = true
  try {
    const playbook = await productConfig.openConfigTrace(traceId)
    await playProductReply(playbook, 'config-trace')
  } finally {
    streaming.value = false
  }
}

const onTraceClick = ({ traceId } = {}) => handleConfigTrace(traceId || null)

/**
 * 翻译层 RD 消息完成后，依据工具 output 驱动研发侧边面板（OfferingCanvas / 批次 / 对比等）。
 * <p>
 * 后端 RD 工具 output 已随 tool 事件带出完整结构化数据（draft / items / comparisons /
 * recommended / batch / compliance_pass），此处仅消费已返回的结构，不再重复调用后端。
 */
function applyRdToolToPanels(msg) {
  if (!msg || !Array.isArray(msg.toolResults)) return
  const done = msg.toolResults.filter((t) => t.status === 'done')
  for (const tool of done) {
    const out = tool.output || {}
    const name = tool.name || ''
    if (name === 'rd_config_chat') {
      attachFormCardToMsg(msg, applyRdConfigDraft(out.draft || out.config?.draft))
    } else if (name === 'rd_compliance') {
      attachFormCardToMsg(msg, applyRdCompliance(out.draft || out.config?.draft, out.compliance_pass, out.issues || out.config?.issues))
    } else if (name === 'rd_file_parse') {
      applyRdFileParse(out.items, out.batch)
    } else if (name === 'rd_scheme_compare') {
      applyRdSchemeCompare(out)
    }
  }
}

/**
 * 将表单卡挂载到消息（内联预览渲染前提：msg.formCard）。
 * 与本地剧本路径 aiMsg.formCard = playbook.formCard 对齐，否则消息流不出现配置预览。
 */
function attachFormCardToMsg(msg, formCard) {
  if (!formCard) return
  msg.formCard = formCard
  messages.value = [...messages.value]
}

/** 从配置草稿构建 product 并激活（对齐 useProductConfig 内部 addProductAndActivate 形状） */
function buildRdProductFromDraft(draft) {
  const d = (draft && typeof draft === 'object') ? draft : {}
  const name = d.offerName || d.offeringName || '配置方案草稿'
  const fee = d.fixedFeeAmount ?? d.monthlyFee
  const existingIdx = productConfig.products.value.findIndex((p) => p.ontologyDraft === d)
  const id = existingIdx >= 0
    ? productConfig.products.value[existingIdx].id
    : (productConfig.currentProduct.value?.ontologyDraft === d ? productConfig.currentProduct.value.id : 'P' + Date.now())
  return {
    id,
    name,
    desc: `固费${fee ?? '-'} | ${d.bizScenario || ''} | ${d.channelScope || ''}`,
    status: 'draft',
    auditStatus: 'pending',
    compliancePass: false,
    issues: [],
    inferredFields: d.inferredFields || [],
    ontologyDraft: d,
    data: { ...d },
  }
}

function applyRdConfigDraft(draft) {
  if (!draft || typeof draft !== 'object') return null
  const product = buildRdProductFromDraft(draft)
  const existingIdx = productConfig.products.value.findIndex((p) => p.id === product.id)
  if (existingIdx >= 0) {
    productConfig.products.value[existingIdx] = product
  } else {
    productConfig.products.value.push(product)
  }
  productConfig.currentProductId.value = product.id
  productConfig.syncFormFromProduct(product)
  const formCard = productConfig.buildProductFormCard(product)
  applyFormCard(formCard)
  return formCard
}

function applyRdCompliance(draft, compliancePass, issues) {
  const resumed = draft && typeof draft === 'object'
  if (!resumed) return null
  const product = buildRdProductFromDraft(draft)
  product.compliancePass = compliancePass === true
  product.issues = Array.isArray(issues) ? issues : []
  product.auditStatus = product.compliancePass ? 'pass' : 'pending'
  const existingIdx = productConfig.products.value.findIndex((p) => p.ontologyDraft === draft || p.id === product.id)
  if (existingIdx >= 0) {
    productConfig.products.value[existingIdx] = product
  } else {
    productConfig.products.value.push(product)
  }
  productConfig.currentProductId.value = product.id
  productConfig.syncFormFromProduct(product)
  const formCard = productConfig.buildProductFormCard(product)
  formCard.compliancePass = product.compliancePass
  formCard.issues = product.issues
  applyFormCard(formCard)
  return formCard
}

function applyRdFileParse(items, batch) {
  const list = Array.isArray(items) ? items : (batch?.items || [])
  if (!list.length) return
  productConfig.batchItems.value = list.map((i, idx) => ({
    ...(i && typeof i === 'object' ? i : { name: String(i) }),
    productId: i?.clientId || i?.id || 'B' + idx,
    status: i?.status || (i?.compliancePass ? '通过' : '待修'),
  }))
  productConfig.showBatchPanel.value = true
}

function applyRdSchemeCompare(out) {
  const result = {
    comparisons: Array.isArray(out.comparisons) ? out.comparisons : (out.config?.comparisons || []),
    recommended: out.recommended || out.config?.recommended || null,
    explanation: out.nl_answer || out.summary || out.config?.explanation || '',
  }
  productConfig.compareResult.value = result
  productConfig.showComparePanel.value = true
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
  if (playbook._undoable) {
    aiMsg.undoable = playbook._undoable
  }
  if (playbook.batch) {
    aiMsg.batch = playbook.batch
    aiMsg.batchItems = (productConfig.batchItems.value || []).slice()
  }
  if (playbook.fileRef) {
    aiMsg.fileRef = playbook.fileRef
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
  if (playbook.traceId) aiMsg.traceId = playbook.traceId
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

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  const attachments = payload?.attachments || []
  if ((!text && !attachments.length) || streaming.value) return
  // 有附件时必须全部上传成功才允许发送
  if (attachments.length && attachments.some((a) => a.uploadStatus && a.uploadStatus !== 'success')) {
    return
  }
  inputText.value = ''

  // 长对话引用消解（L0）：无新附件、且为跨轮引用命令时，重设最近批次作用域
  if (!attachments.length && isReferenceCommand(text)) {
    const refMsg = findLastRefMsg()
    if (refMsg) {
      await handleReferenceContinue(text, refMsg)
      return
    }
  }

  const scene = payload?.scene || activeScene.value || config.defaultScene
  const scenario = resolveProductScenario(text || '智读', scene)
  // 有附件时优先走智读·文件配置
  const effectiveScenario =
    attachments.length && (!scenario || scenario === 'chat-generate')
      ? 'file-parse'
      : scenario
  // 配置审计追溯：本地直调 get_trace/explain，无需后端翻译层工具
  if (!attachments.length && effectiveScenario === 'config-trace') {
    await handleConfigTrace()
    return
  }
  // 统一走翻译层：研发助手所有核心场景均由后端翻译层判定意图并执行对应 RD 工具
  const params = {}
  if (effectiveScenario === 'file-parse' && attachments.length) {
    const fileIds = attachments
      .map((a) => a.fileId || a.file_id || '')
      .filter(Boolean)
    if (fileIds.length) {
      // 多文档并行：全部已上传 file_id 一次性交给 rd_file_parse
      params.file_ids = fileIds.join(',')
      params.file_id = fileIds[0]
      const named = attachments.find((a) => a.name || a.file_name)
      if (named) params.file_name = named.name || named.file_name
    }
  }
  await sendAgentMessage({
    text: text || `导入文档：${attachments[0]?.name || '方案'}`,
    scene: 'rd',
    params,
  })
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
  if (scenario === 'config-trace') {
    // 审计追溯建议：一键直达追溯回放
    handleConfigTrace()
    return
  }
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

/** CLARIFY 澄清补参结构化回传：带 params 重发「继续」 */
const onClarifySubmit = async ({ params }) => {
  if (!params || streaming.value) return
  await sendAgentMessage({ text: '继续', scene: 'rd', params })
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

/** 长对话引用消解（L0）：跨轮续接的引用短语（限文件/批次记忆锚） */
const REFERENCE_RE =
  /这份|刚才那批|那批|这批|当前批次|上一(份|批)|之前(导入|上传|解析)的|基于这份|继续分析|再分析一遍|重新分析/

/** 判断文本是否像完整方案正文（如演示话术），避免把粘贴正文误判为引用命令 */
function looksLikePlanContentLocal(text = '') {
  const t = String(text || '').trim()
  if (!t) return false
  const signals = [
    /月费|资费|定价|固定费/,
    /\d+\s*元/,
    /\d+\s*GB|\d+\s*G\b|流量/,
    /分钟|语音|通话/,
    /宽带|\d+\s*M(?:bps)?/i,
    /套餐[A-Za-z0-9甲乙丙丁一二三四五六七八九十]|套餐名称|商品名称|offering/i,
    /目标客群|客群|渠道|合约|订购|互斥|依赖/,
  ]
  const hits = signals.filter((re) => re.test(t)).length
  if (hits >= 2) return true
  return t.length >= 80 && hits >= 1
}

/** 是否为跨轮引用命令（需命中引用短语且非完整方案正文） */
function isReferenceCommand(text = '') {
  if (!text) return false
  if (!REFERENCE_RE.test(text)) return false
  return !looksLikePlanContentLocal(text)
}

/** 向上查找最近一条携带 fileRef / batch 的助手消息（对话记忆锚） */
function findLastRefMsg() {
  const msgs = messages.value
  for (let i = msgs.length - 1; i >= 0; i -= 1) {
    const m = msgs[i]
    if (m?.role === 'assistant' && (m.fileRef || m.batch)) return m
  }
  return null
}

/** 从 productConfig.batchItems 汇总当前批次快照 */
function batchSnapshotFromItems() {
  const items = (productConfig.batchItems.value || []).slice()
  return {
    total: items.length,
    passedCount: items.filter((i) => i.compliancePass || i.status === '通过').length,
    pendingCount: items.filter((i) => !(i.compliancePass || i.status === '通过')).length,
    confirmable: items.filter((i) => i.compliancePass || i.status === '通过'),
  }
}

/** 跨轮续接：把最近引用文档/批次重新设为当前作用域，可直接延续修正/入库 */
async function handleReferenceContinue(text = '', refMsg = null) {
  if (streaming.value) return
  streaming.value = true
  try {
    const name = refMsg?.fileRef?.fileName || (refMsg?.batch ? '当前批次' : '该文档')
    const summary = batchSnapshotFromItems()
    const showBatch = refMsg?.batch && summary.total
    await playProductReply({
      thinkingSteps: [
        '检测到跨轮引用（文件/批次记忆锚）',
        '向上消解最近 fileRef / batch，无需重复上传',
        showBatch ? '批次仍在会话作用域，可直接延续操作' : '当前无批次草稿，可继续智读导入',
      ],
      content:
        `已消解引用「${name}」，延续上一轮上下文：\n\n` +
        (summary.total
          ? `当前批次：通过 ${summary.passedCount} · 待修 ${summary.pendingCount} · 可入库 ${summary.confirmable.length}\n`
          : '（当前无批次草稿，可继续智读导入文档）\n') +
        (text.includes('继续') || /继续分析|再次分析|重新分析/.test(text)
          ? '\n已为您重新载入该批次进行分析，可对下方卡片继续修正 / 合格项入库。'
          : ''),
      formCard: null,
      batch: showBatch ? refMsg.batch : null,
    }, 'file-parse')
  } finally {
    streaming.value = false
  }
}

/** 点击「已引用文档」锚：聚焦该文档/批次，给出跨轮续接入口 */
async function onFileRefClick(msg) {
  if (!msg) return
  await handleReferenceContinue('引用聚焦', msg)
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

/** 对话内撤销已执行动作（v3.2 可逆操作）：调用 composable 回退，追加回执并标记已撤销 */
const onUndoAction = async ({ msg, actionId }) => {
  if (streaming.value || !actionId) return
  const result = await productConfig.undoAction(actionId)
  if (msg?.undoable) {
    msg.undoable.state = result.success ? 'reverted' : 'failed'
    if (result.success) msg.undoable.actionId = null
  }
  tickMessages()
  const label = msg?.undoable?.label || (result.success ? '该动作' : result.message)
  await playProductReply({
    thinkingSteps: [result.success ? '回退已执行动作，草稿/状态已恢复' : '撤销失败，动作保留'],
    content: result.success
      ? `已撤销：**${label}**。相关草稿/状态已回退，可在「已配置商品」中核对。`
      : `撤销失败：${result.message || '请稍后重试'}。`,
    formCard: null,
  })
}

/** 同步消息内批次快照：让内联卡片随 productConfig.batchItems 实时刷新 */
function refreshBatchSnapshot(msg, summary) {
  if (!msg) return
  if (summary) msg.batch = summary
  msg.batchItems = (productConfig.batchItems.value || []).slice()
  const items = msg.batchItems
  if (items.length && msg.batch) {
    msg.batch = {
      ...msg.batch,
      total: items.length,
      passedCount: items.filter((i) => i.compliancePass || i.status === '通过').length,
      pendingCount: items.filter((i) => !(i.compliancePass || i.status === '通过')).length,
      confirmableDrafts: items.filter((i) => i.compliancePass || i.status === '通过'),
    }
  }
  tickMessages()
}

/** 批量配置：确认通过项入库（对话中完成闭环） */
async function handleBatchConfirm(msg) {
  if (!msg?.batch || streaming.value) return
  streaming.value = true
  try {
    const playbook = await productConfig.confirmPassedDrafts()
    refreshBatchSnapshot(msg, playbook.batch)
    if (playbook.formCard) applyFormCard(playbook.formCard)
    await playProductReply(playbook, 'confirm-batch')
  } finally {
    streaming.value = false
  }
}

/** 单条待修项的预设修正：按规则映射到 applyBatchFix 的 fixKey */
function fixesForItem(item) {
  const rules = new Set((item.issues || []).map((i) => i.ruleId))
  const fixes = []
  if (rules.has('R-C05') || rules.has('R-C07')) {
    fixes.push('contract12')
    fixes.push('internal')
  }
  if (rules.has('R-C06') && (item.issues || []).some((i) => i.field === 'monthlyFee')) {
    fixes.push('fee19')
  }
  if (rules.has('R-C04')) {
    fixes.push('dependBb')
  }
  return fixes
}

/** 批量配置：逐条修正待修项（应用预设修正并重跑合规），全部完成后对话回执 */
async function handleBatchFix(msg) {
  if (!msg?.batch || streaming.value) return
  streaming.value = true
  try {
    const items = (productConfig.batchItems.value || []).slice()
    const pending = items.filter((i) => !(i.compliancePass || i.status === '通过'))
    const fixedLines = []
    for (const item of pending) {
      if (!item.productId) continue
      const product = productConfig.products.value.find((p) => p.id === item.productId)
      const fixes = fixesForItem(item)
      let applied = 0
      for (const key of fixes) {
        if (product) await productConfig.applyBatchFix(item.productId, key)
        applied += 1
        if (productConfig.batchItems.value.find((b) => b.productId === item.productId)?.compliancePass) break
      }
      const cur = productConfig.batchItems.value.find((b) => b.productId === item.productId)
      const ok = cur?.compliancePass || cur?.status === '通过'
      fixedLines.push(
        `- **${cur?.draft?.offeringName || item.draft?.offeringName || '未命名'}** → ${ok ? `✅ 已修正，合规通过${applied ? `（套用 ${applied} 项预设修正）` : ''}` : '仍待修正，可在编辑后重跑'}`
          + (cur?.issues?.length && !ok ? `（剩余：${cur.issues.map((i) => i.ruleId).join('、')}）` : ''),
      )
    }
    refreshBatchSnapshot(msg)
    const summary = productConfig.batchItems.value
    const pendingLeft = summary.filter((i) => !(i.compliancePass || i.status === '通过')).length
    const confirmable = summary.filter((i) => i.compliancePass || i.status === '通过').length
    await playProductReply({
      thinkingSteps: [
        '逐条修正待修项（补协议期/转内部验证/补月费/补依赖宽带）',
        '逐条重跑合规校验',
        `修正后：可入库 ${confirmable}，仍待修正 ${pendingLeft}`,
      ],
      content:
        `已完成批量修正：\n${fixedLines.join('\n') || '- 无待修正项'}\n\n` +
        `当前**可入库 ${confirmable}** 条，仍**待修正 ${pendingLeft}** 条。` +
        (confirmable ? '\n\n可点击「确认通过项入库」完成备案闭环。' : ''),
      formCard: null,
    })
  } finally {
    streaming.value = false
  }
}

/** 批量配置：删除某条草稿（对话确认） */
async function handleBatchDelete({ msg, item }) {
  if (!msg?.batch || !item?.productId || streaming.value) return
  const target = productConfig.products.value.find((p) => p.id === item.productId)
  const isFiled = !!target?.draftId || item.status === '已备案'
  const name = item.draft?.offeringName || item.name || item.offeringName || '该条草稿'
  const confirmed = await ElMessageBox.confirm(
    isFiled
      ? `「${name}」已生成备案草稿（draftId：${target?.draftId || '-'}），删除仅移除本地草稿；如需撤销上架请走「下线/停售」流程。确认删除？`
      : `确认删除草稿「${name}」？该操作不可恢复。`,
    '删除草稿',
    { type: isFiled ? 'warning' : 'info', confirmButtonText: '删除', cancelButtonText: '取消' },
  ).catch(() => false)
  if (confirmed !== true) return

  streaming.value = true
  try {
    productConfig.deleteProduct(item.productId)
    refreshBatchSnapshot(msg)
    await playProductReply({
      thinkingSteps: ['删除草稿并同步批次清单'],
      content: `已删除草稿「${name}」` + (isFiled ? '（遗留的备案工单不受影响，可在运营侧跟进）' : '') + '。\n\n当前批次清单已同步更新。',
      formCard: null,
    })
  } finally {
    streaming.value = false
  }
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

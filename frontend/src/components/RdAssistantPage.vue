<template>
  <AssistantShell
    mode="rd"
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
    @shortcut="onShortcut"
    @quick-action="onQuickAction"
    @open-model-config="onOpenModelConfig"
    @context-remove="onRemoveContextItem"
    @context-clear="onClearContextItems"
  >
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
      @session-workorder-select="onSessionWorkOrderSelect"
@workorder-preview="({ wo }) => onWorkOrderPreview(wo)"
@workorder-edit="({ wo }) => onWorkOrderEdit(wo)"
@workorder-submit="({ wo }) => onWorkOrderSubmit(wo)"
@workorder-copy="({ wo }) => onWorkOrderCopy(wo)"
@workorder-delete="({ wo }) => onWorkOrderDelete(wo)"
    />

    <ProductPreviewDrawer v-model="showProductPreview" :product="previewProductData" />

    <!-- 工单草稿编辑抽屉（右侧）：点击工单条目/编辑/复制时打开 -->
    <WorkOrderDrawer
      v-model="showWorkOrderDrawer"
      :card="activeFormCard"
      @form-submit="handleConfirmSubmit"
      @form-cancel="closeActiveForm"
      @form-field-change="handleInlineFieldChange"
      @form-ai-validation="handleAiValidation"
      @form-confirm-submit="handleConfirmSubmit"
      @close="closeWorkOrderDrawer"
    />
  </AssistantShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import ProductPreviewDrawer from './ProductPreviewDrawer.vue'
import WorkOrderDrawer from './WorkOrderDrawer.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { useProductConfig } from '../composables/useProductConfig.js'
import { registerPostProcessor } from '../composables/useIntentRegistry.js'
import { checkCompliance, copyAsDraft, listWorkOrders } from '../services/productOntologyApi.js'
import { saveMessage as saveChatMessage } from '../services/chatApi.js'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { genId, sleep, createStreamingPlaceholder } from '../utils/chatUtils.js'
import { normalizeThinkingStep } from '../utils/normalizeThinkingStep.js'
import { draftToFormData } from '../data/productMockData.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeFormCard = ref(null)
/** 已配置商品只读预览（不进入编辑态） */
const showProductPreview = ref(false)
const previewProductData = ref(null)
/** 工单草稿编辑抽屉（右侧；复用 activeFormCard 表单卡） */
const showWorkOrderDrawer = ref(false)

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
const currentProduct = productConfig.currentProduct
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
  'RD_DRAFT_MANAGE',
]

/** 拉取当前会话的商品配置工单列表（后端按 session_id 过滤），返回 items 数组 */
async function loadSessionWorkOrders(sid = sessionId.value, opts = {}) {
  if (!sid) return []
  try {
    const resp = await listWorkOrders({ sessionId: sid, ...opts })
    const list = resp?.items || resp?.data?.items || []
    // 双重保险：只保留属于当前会话的工单，防止后端未过滤/参数退化时全局数据冒充会话数据
    return list.filter((wo) => !wo?.sessionId || wo.sessionId === sid)
  } catch {
    return []
  }
}

/** 把会话工单列表（会话级共享视图）挂到指定助手消息（内联跟随回复展示），并按商品名关联本地草稿 id 供操作使用 */
async function attachWorkOrdersToMsg(aiMsg, sid = sessionId.value) {
  if (!aiMsg) return
  const items = await loadSessionWorkOrders(sid)
  if (items.length) {
    // 关联本地草稿：优先按工单 payload.draftId（`P${draftId}` 稳定约定）/ productId 对齐，
    // 名称匹配兜底（历史工单无 draftId 时）；工单卡操作（预览/编辑/删除）据此定位 product
    const norm = (s) => String(s || '').trim()
    const productList = productConfig.products.value || []
    for (const wo of items) {
      const draftId = wo.draftId ?? wo.draft_id
      let matched = draftId
        ? productList.find((p) => p.id === `P${draftId}` || String(p.draftId || '') === String(draftId))
        : null
      if (!matched && wo.productId) {
        matched = productList.find((p) => p.id === wo.productId)
      }
      if (!matched) {
        matched =
          productList.find(
            (p) => norm(p.name) && (norm(p.name) === norm(wo.offeringName) || norm(wo.title || '').startsWith(norm(p.name))),
          ) ||
          // 草稿名为默认值（「配置方案草稿/商品配置草稿」）且工单无商品名时，关联唯一/当前草稿
          productList.find((p) => /^(配置方案草稿|商品配置草稿)$/.test(norm(p.name))) ||
          productList.find((p) => p.id === productConfig.currentProductId.value)
      }
      wo.productId = matched?.id || wo.productId || null
    }
    aiMsg.workOrders = items
    messages.value = [...messages.value]
  }
}
/** 工单卡操作：预览草稿（只读抽屉；关联不上本地草稿时给出提示） */
function onWorkOrderPreview(wo) {
  if (!wo?.productId) {
    ElMessageBox.alert('该工单暂无可预览的本地草稿（草稿可能已被删除）', '预览草稿', { type: 'warning' })
    return
  }
  handleProductPreview(wo.productId)
}

/** 工单卡操作：编辑草稿（激活编辑态并打开右侧抽屉表单） */
function onWorkOrderEdit(wo) {
  if (!wo?.productId) {
    ElMessageBox.alert('该工单暂无可编辑的本地草稿（草稿可能已被删除）', '编辑草稿', { type: 'warning' })
    return
  }
  const formCard = productConfig.selectProduct(wo.productId)
  if (formCard) applyFormCard(formCard)
  showWorkOrderDrawer.value = true
}

/** 工单卡操作：提交入库（发会话消息 → 翻译层 rd_draft_manage submit，合规 → 沉淀 → 生成工单闭环） */
function onWorkOrderSubmit(wo) {
  if (!wo || streaming.value) return
  const woId = wo.workOrderId || wo.id || ''
  const name = wo.offeringName || wo.title || '该配置草稿'
  sendAgentMessage({
    text: `提交工单「${name}」（${woId}）关联的配置草稿，走合规入库闭环`,
    scene: 'rd',
    params: { action: 'submit', work_order_id: woId },
  })
}

/** 工单卡操作：复制草稿（弹补充需求输入 → 确认后发会话消息 → 翻译层 rd_draft_manage 执行，全量记录操作） */
async function onWorkOrderCopy(wo) {
  if (!wo || streaming.value) return
  const woId = wo.workOrderId || wo.id || ''
  const name = wo.offeringName || wo.title || '该配置草稿'
  let prompt = null
  try {
    const res = await ElMessageBox.prompt(
      `正在复制工单「${name}」（${woId}）。请补充新方案的差异化需求（如改名、调资费、换客群等），可留空表示原样复制。`,
      '复制配置 · 补充需求',
      {
        type: 'info',
        confirmButtonText: '发送复制消息',
        cancelButtonText: '取消',
        inputPlaceholder: '例：改名为校园青春版，月费 29 元，面向大学生',
      },
    )
    prompt = String(res?.value || '').trim()
  } catch {
    return
  }
  // 统一凭工单号定位：草稿在工单 payload 中关联（后端按 work_order_id 反查），不再传 draft_id/client_id；
  // 补充需求并入话术，由翻译层在复制后按 prompt 修正字段
  const requirement = prompt ? `，并按以下需求调整：${prompt}` : ''
  sendAgentMessage({
    text: `复制工单「${name}」（${woId}）对应的配置草稿，生成副本${requirement}`,
    scene: 'rd',
    params: { action: 'copy', work_order_id: woId, question: `复制「${name}」${prompt ? `；补充需求：${prompt}` : ''}` },
  })
}
/** 工单卡操作：删除（发会话消息 → 翻译层 rd_draft_manage 执行，全量记录操作） */
async function onWorkOrderDelete(wo) {
  if (!wo || streaming.value) return
  const woId = wo.workOrderId || wo.id || ''
  const name = wo.offeringName || wo.title || '该条草稿'
  let confirmed = true
  try {
    confirmed = await ElMessageBox.confirm(
      `确认删除工单「${name}」关联的配置草稿？该操作将记录到会话历史。`,
      '删除草稿',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    confirmed = false
  }
  if (confirmed !== 'confirm') return
  // 统一凭工单号定位：后端按 work_order_id 反查关联草稿并删除，同步取消工单
  await sendAgentMessage({
    text: `删除工单「${name}」（${woId}）关联的配置草稿`,
    scene: 'rd',
    params: { action: 'delete', work_order_id: woId },
  })
}

/** 点击工单卡条目：以商品编码发起智查，续接查看该商品 */
async function onSessionWorkOrderSelect(wo) {
  if (!wo?.offeringId || streaming.value) return
  inputText.value = ''
  activeScene.value = 'rd.query'
  await sendAgentMessage({
    text: `查一下商品 ${wo.offeringId} 的配置与工单状态`,
    scene: 'rd',
    params: { offering: wo.offeringId },
  })
}

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
    registerPostProcessor(intent, async (msg) => {
      applyRdToolToPanels(msg)
      // 每轮 RD 回复结束后：若本会话已产生商品配置工单，内联挂到该条回复后
      await attachWorkOrdersToMsg(msg)
    })
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

function closeWorkOrderDrawer() {
  showWorkOrderDrawer.value = false
  closeActiveForm()
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
      thinkingSteps: ['提交被拒'],
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

  await playProductReply({
    thinkingSteps: [
      '核对 compliancePass=true（R-C08）',
      '沉淀 ConfigScheme 至事实图/本体',
      `生成配置工单 ${woId}`,
    ],
    content:
      `已完成提交闭环：\n\n` +
      `- 商品编码：\`${offeringId}\`\n` +
      `- 配置工单：\`${woId}\`\n\n` +
      '可在上方「商品配置工单」卡查看与编辑；运营侧工单列表可跟进进度。\n\n' +
      '```json\n' +
      JSON.stringify(
        {
          offeringId,
          workOrderId: woId,
          offeringName: draft.offeringName,
          monthlyFee: draft.monthlyFee ?? draft.fixedFeeAmount,
          status: 'submitted',
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

/** 只读预览已配置商品：不切换当前编辑态 */
function handleProductPreview(id) {
  const preview = productConfig.previewProduct(id)
  if (!preview) return
  previewProductData.value = preview
  showProductPreview.value = true
}

/** 智查结果卡片操作：复制配置（弹补充需求输入 → 直调 copy-as-draft API 按需求修正副本字段 + 后端复制即开单，消息流挂工单卡）。
 * 不走翻译层——「复制」语义无对应 AgentTool，LLM 会误派 rd_compliance 报「缺少待校验的配置草稿」。
 * 回复统一走 playProductReply（占位气泡 + 流式打字 + 工单卡挂载 + 落库），
 * 修复原先手工拼 aiMsg 仅内存 push、不发消息也不落库，刷新/切换会话后复制回执消失的问题。 */
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
    // 复制动作入会话历史：与工单卡复制路径（pushUserMessage + persistTurn）对齐，
    // 否则该轮操作只存在于内存，刷新后上下文断裂（不知道草稿从何复制而来）
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
        console.warn('[RdAssistantPage] 复制配置用户消息落库失败:', e?.message || e)
      }
    }
    const thinkingSteps = [
      `选中智查结果「${name}」`,
      ...(requirement ? [`按补充需求调整：${requirement}`] : []),
      'copy_as_draft 深拷贝生成草稿',
      'evaluate_policy 执行 R-C* 合规校验',
      '复制即开单：创建配置工单',
    ]
    const result = await copyAsDraft(offeringId, item.name || null, sessionId.value, requirement)
    if (result?.success === false) {
      throw new Error(result.message || '复制失败')
    }
    // 本地同步草稿（工单卡操作按钮依赖 productId 关联）
    syncProductFromDraft(result.draft || {})
    const passLabel = result.compliancePass ? '✅ 合规通过' : '⚠️ 存在待处理项'
    const appliedNote = result.applied_requirements
      ? `已按补充需求调整：${Object.keys(result.applied_requirements).join('、')}。`
      : ''
    let content =
      `已将「${result.source_offering_name || offeringId}」复制为新草稿「${result.draft?.offeringName || ''}」。${appliedNote}${passLabel}。`
    // playProductReply 内部会挂工单卡并落库；先探测开单结果以追加兜底文案
    const workOrders = await loadSessionWorkOrders(sessionId.value)
    if (!workOrders.length) {
      content += '\n\n（工单创建失败，可稍后在工单列表重试）'
    }
    await playProductReply({ thinkingSteps, content, formCard: null })
  } catch (e) {
    await playProductReply({
      thinkingSteps: [`复制「${name}」失败`],
      content: `复制配置失败：${e.message || '本体服务不可用'}。请确认商品编码有效。`,
      formCard: null,
    })
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
    await playProductReply(playbook)
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
      // 新开配置工单同样标记为最近操作对象：下一轮工单卡刷新后该条目高亮
      const newWo = String(out?.workOrderId || out?.work_order_id || out?.workOrder?.workOrderId || '')
      if (newWo) msg.lastActedWoId = newWo
    } else if (name === 'rd_compliance') {
      attachFormCardToMsg(msg, applyRdCompliance(out.draft || out.config?.draft, out.compliance_pass, out.issues || out.config?.issues))
    } else if (name === 'rd_file_parse') {
      const woCount = applyRdFileParse(out.items, out.batch, out)
      if (woCount) {
        msg.fileParseWorkOrderCount = woCount
      }
      // 文档记忆锚：文件名/IDs 从本轮用户消息附件还原（后端 tool input 视图隐藏 file_ids），
      // 供跨轮引用（「继续修正这份方案」）与「已引用文档」锚展示
      const snapshot = batchSnapshotFromItems()
      const upAttachments = findLastUserAttachments(msg)
      const fileIds = (upAttachments || []).map((a) => a.fileId).filter(Boolean)
      if (fileIds.length || upAttachments.length) {
        msg.fileRef = {
          fileName: upAttachments[0]?.name || upAttachments[0]?.fileName || fileIds[0] || '方案文档',
          fileId: fileIds[0] || null,
          fileIds,
          counts: {
            passed: snapshot.passedCount,
            pending: snapshot.pendingCount,
            confirmable: snapshot.confirmable.length,
          },
        }
      }
    } else if (name === 'rd_scheme_compare') {
      applyRdSchemeCompare(out)
    } else if (name === 'rd_config_discover') {
      // 智查结果 → 商品列表卡片（ChatMessageList msg.queryResults 槽位）：
      // 条目点击/复制按钮 → query-result-click → prepareProduct（copy-as-draft + 合规）
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
            const parts = [
              fee != null ? `月费 ${fee} 元` : null,
              category,
              state,
            ].filter(Boolean)
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
    } else if (name === 'rd_draft_manage') {
      applyRdDraftManage(out)
      // 操作回执高亮：提交/复制/删除命中的工单条目在工单卡上高亮提示（问题3：提交成功无明显提示）
      const actedWo = String(out?.work_order_id || '')
      if (actedWo) msg.lastActedWoId = actedWo
    }
  }
}

/** 草稿管理（删除/复制）工具回执：同步本地草稿数组（工单卡由 attachWorkOrdersToMsg 统一刷新）。
 * 多候选/歧义确认由理解层 LLM 判定（CONFIRM 候选卡片 → 用户点选 → 以完整话术重发），此处不硬编码选择逻辑。 */
function applyRdDraftManage(out) {
  const action = String(out?.action || '')
  if (action === 'copy' && out?.draft) {
    // 后端已落库副本：本地同步草稿数组即可。工单卡展示由 attachWorkOrdersToMsg 统一刷新
    // （副本已开工单），不激活表单卡——复制回执应呈「商品配置工单」卡片而非表单预览。
    syncProductFromDraft(out.draft)
  } else if (action === 'delete' && out?.work_order_id) {
    // 删除回执按工单号对齐本地草稿（product.workOrderId 在 loadPersistedDrafts 恢复时写入）
    const woId = String(out.work_order_id)
    const victim = productConfig.products.value.find((p) => String(p.workOrderId || '') === woId)
    if (victim) productConfig.deleteProduct(victim.id)
  }
}

/** 静默同步草稿到本地 product 数组（不激活、不弹表单卡、不改变当前选中项）。 */
function syncProductFromDraft(draft) {
  if (!draft || typeof draft !== 'object') return null
  const product = buildRdProductFromDraft(draft)
  const existingIdx = productConfig.products.value.findIndex((p) => p.id === product.id)
  if (existingIdx >= 0) {
    productConfig.products.value[existingIdx] = product
  } else {
    productConfig.products.value.push(product)
  }
  return product
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
  // 稳定标识：优先用后端草稿主键（draftId）/ clientId，与 loadPersistedDrafts 生成的
  // id 对齐（item.clientId || `P${item.draftId}`），避免历史回放时按时间戳新建重复 product
  const stableId = d.draftId ? `P${d.draftId}` : (d.clientId || d.client_id || '')
  const id = existingIdx >= 0
    ? productConfig.products.value[existingIdx].id
    : (productConfig.currentProduct.value?.ontologyDraft === d && productConfig.currentProduct.value.id)
      || stableId
      || 'P' + Date.now()
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

function applyRdFileParse(items, batch, out = {}) {
  const list = Array.isArray(items) ? items : (batch?.items || [])
  if (!list.length) return
  // 批次条目建为本地 product：工单卡 hasActions 依赖 productId/formCard，
  // 智读此前只建 batchItems 不建 product，导致工单卡不渲染操作按钮（预览/编辑/提交/复制/删除）
  const woList = Array.isArray(out.workOrders) ? out.workOrders : []
  const newProducts = []
  const batchRows = list.map((i, idx) => {
    const src = i && typeof i === 'object' ? i : { name: String(i) }
    const draft = src.draft && typeof src.draft === 'object' ? src.draft : {}
    const stableId = src.draftId
      ? `P${src.draftId}`
      : (src.clientId || src.client_id || draft.clientId || 'B' + idx)
    return {
      ...src,
      draftId: src.draftId ?? src.draft_id,
      clientId: src.clientId ?? src.client_id,
      productId: stableId,
      status: src.status || (src.compliancePass ? '通过' : '待修'),
      _draft: draft,
    }
  })
  for (const item of batchRows) {
    const draft = item._draft
    if (!draft || !Object.keys(draft).length) continue
    const product = {
      id: item.productId,
      name: draft.offerName || draft.offeringName || `配置草稿${item.index || ''}`,
      desc: `月费${draft.monthlyFee ?? draft.fixedFeeAmount ?? '-'} | ${item.status || 'draft'}`,
      status: 'draft',
      auditStatus: item.compliancePass ? 'pass' : 'pending',
      compliancePass: !!item.compliancePass,
      issues: Array.isArray(item.issues) ? item.issues : [],
      inferredFields: item.inferredFields || [],
      ontologyDraft: draft,
      data: draftToFormData(draft),
      draftId: item.draftId,
      offeringId: draft.offeringId || draft.offerId || '',
      workOrderId: item.workOrderId || '',
      persisted: !!(item.draftId || item.clientId),
    }
    const existingIdx = productConfig.products.value.findIndex((p) => p.id === product.id)
    if (existingIdx >= 0) {
      productConfig.products.value[existingIdx] = {
        ...productConfig.products.value[existingIdx],
        ...product,
      }
    } else {
      productConfig.products.value.push(product)
      newProducts.push(product)
    }
  }
  if (newProducts.length) {
    // 激活首条新草稿（与智聊 applyRdConfigDraft 行为对齐），供编辑/提交直接定位
    productConfig.currentProductId.value = newProducts[0].id
    productConfig.syncFormFromProduct(newProducts[0])
  }
  productConfig.batchItems.value = batchRows.map(({ _draft, ...rest }) => rest)
  // 解析即开单（方案A）：后端已为每条草稿（含合规未通过）落库并创建配置工单（source=rd_file_parse），
  // 此处把 draftId/workOrderId 回填到本地草稿数组与批次条目，工单卡操作（预览/编辑/提交）凭其定位
  const woByItem = new Map()
  for (const item of batchRows) {
    if (item.workOrderId) woByItem.set(item.productId, item.workOrderId)
  }
  for (const item of productConfig.batchItems.value) {
    if (!item.workOrderId && woByItem.has(item.productId)) {
      item.workOrderId = woByItem.get(item.productId)
    }
    const product = productConfig.products.value.find((p) => p.id === item.productId)
    if (product) {
      if (item.workOrderId) product.workOrderId = item.workOrderId
      if (item.draftId) product.draftId = item.draftId
      if (item.draftId || item.clientId) product.persisted = true
    }
  }
  productConfig.showBatchPanel.value = true
  return woList.length
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

/** 研发快捷场景 / 文案 → 本体场景路由 */
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

/** 真实结果直接写入思考时间线（不经场景模板调度），再流式输出正文 */
async function finishProductReply(aiMsg, playbook = {}) {
  const allSteps = playbook.thinkingSteps || []
  if (allSteps.length) {
    aiMsg.reasoning = allSteps.map((raw) =>
      typeof raw === 'string' ? normalizeThinkingStep({ content: raw, result: raw }) : normalizeThinkingStep(raw),
    )
  }
  aiMsg.showReasoning = true
  tickMessages()

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

/**
 * 本地剧本路径回复落库（去旧留新：与翻译层路径 persistTurn 对齐）。
 * <p>
 * playProductReply 产出的助手消息（含 thinkingSteps / formCard / queryResults）原先只在内存，
 * 刷新/切换会话后消失，造成「保存或加载不完整」。此处按与 chatApi.buildMessageMetadata
 * 相同的键约定持久化（reasoning_full / formCard / stream_text），历史回放可完整还原。
 * 落库失败不影响对话主流程（仅 console.warn）。
 */
async function persistProductReply(aiMsg, playbook = {}) {
  const sid = sessionId.value
  if (!sid || !aiMsg?.content) return
  try {
    await saveChatMessage(sid, {
      role: 'assistant',
      content: aiMsg.content,
      contentType: 'chat',
      done: true,
      streamText: aiMsg.content,
      reasoning: aiMsg.reasoning || [],
      formCard: playbook.formCard || aiMsg.formCard || undefined,
      queryPlan: aiMsg.queryPlan || undefined,
    })
  } catch (e) {
    console.warn('[RdAssistantPage] 本地剧本回复落库失败:', e?.message || e)
  }
}

async function playProductReply(playbook = {}) {
  const aiMsg = createStreamingPlaceholder(genId)
  messages.value = [...messages.value, aiMsg]
  tickMessages()
  await finishProductReply(aiMsg, playbook)
  // 回放完成后：本会话有工单产生时，把工单列表内联挂到该条回复后
  await attachWorkOrdersToMsg(aiMsg)
  // 剧本回复落库：保证历史会话 = 实际会话快照（刷新/切换后消息不丢失）
  await persistProductReply(aiMsg, playbook)
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
    // 附件元数据随用户消息展示（气泡附件 + 落库），跨轮引用锚可消解
    attachments: attachments.map((a) => ({
      id: a.id,
      type: a.type || 'file',
      name: a.name,
      size: a.size,
      fileId: a.fileId || a.file_id || null,
      fileName: a.fileName || a.name,
      uploadStatus: a.uploadStatus,
    })),
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

/** 判断文本是否像完整方案正文（如粘贴的套餐文档），避免把粘贴正文误判为引用命令 */
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

/** 向上查找指定助手消息之前最近一条带附件的用户消息（fileRef 还原文件名/IDs 用） */
function findLastUserAttachments(beforeMsg = null) {
  const msgs = messages.value
  const stopIdx = beforeMsg ? msgs.findIndex((m) => m.id === beforeMsg.id) : msgs.length
  for (let i = (stopIdx >= 0 ? stopIdx : msgs.length) - 1; i >= 0; i -= 1) {
    const m = msgs[i]
    if (m?.role === 'user' && Array.isArray(m.attachments) && m.attachments.length) {
      return m.attachments
    }
  }
  return []
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
    // 引用续接的用户消息也进消息流并落库：与翻译层路径 pushUserMessage 对齐，
    // 否则该轮 user 消息只存在于内存，刷新后上下文断裂（用户话术丢失）
    if (text && text !== '引用聚焦') {
      messages.value = [
        ...messages.value,
        { id: genId(), role: 'user', type: 'chat', content: text, done: true, timestamp: Date.now() },
      ]
      const sid = sessionId.value
      if (sid) {
        try {
          await saveChatMessage(sid, { role: 'user', content: text, contentType: 'text', done: true })
        } catch (e) {
          console.warn('[RdAssistantPage] 引用续接用户消息落库失败:', e?.message || e)
        }
      }
    }
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
    })
  } finally {
    streaming.value = false
  }
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
    await playProductReply(playbook)
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
        (confirmable ? '\n\n可点击「确认通过项入库」完成入库闭环。' : ''),
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
  const isFiled = item.status === '已入库' || target?.status === 'submitted'
  const name = item.draft?.offeringName || item.name || item.offeringName || '该条草稿'
  const confirmed = await ElMessageBox.confirm(
    isFiled
      ? `「${name}」已提交入库，删除仅移除本地草稿；如需撤销上架请走「下线/停售」流程。确认删除？`
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
      content: `已删除草稿「${name}」` + (isFiled ? '（遗留的工单不受影响，可在运营侧跟进）' : '') + '。\n\n当前批次清单已同步更新。',
      formCard: null,
    })
  } finally {
    streaming.value = false
  }
}
</script>

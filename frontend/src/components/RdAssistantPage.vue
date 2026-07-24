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
import { playSimulatedReply } from '../utils/simulateReply.js'

const inputText = ref('')
const historyLoading = ref(false)
const activeFormCard = ref(null)

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
  return productConfig.detectScenario(text)
}

async function playProductReply(playbook = {}) {
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
    formCard: playbook.formCard || null,
    queryResults: playbook.queryResults || null,
    onTick: () => {
      messages.value = [...messages.value]
    },
  })

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
  messages.value = [...messages.value]
}

async function runProductScenario(text, scenario) {
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
    if (scenario === 'query') {
      playbook = productConfig.simulateQuery(text)
    } else if (scenario === 'file-parse') {
      playbook = await productConfig.simulateFileParse('校园迎新产商品方案_2026.md', 12 * 1024)
    } else if (scenario === 'confirm-batch') {
      playbook = productConfig.confirmPassedDrafts()
    } else if (scenario === 'root-cause') {
      playbook = await productConfig.runRootCauseAnalysis()
    } else if (scenario === 'risk-audit') {
      playbook = await productConfig.runRiskAuditFlow()
    } else {
      playbook = await productConfig.generateProductFromChat(text)
    }
    await playProductReply(playbook)
  } catch (e) {
    console.warn('[RdAssistant] product scenario failed:', e)
    await playProductReply({
      thinkingSteps: ['本体配置推理失败'],
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
  const scenario = resolveProductScenario(text, scene)
  if (scenario) {
    await runProductScenario(text, scenario)
    return
  }
  sendMessage({ text, scene })
}

const onSuggest = async (text) => {
  if (!text || streaming.value) return
  const scenario = resolveProductScenario(text, config.defaultScene)
  if (scenario) {
    await runProductScenario(text, scenario)
    return
  }
  sendMessage({ text, scene: config.defaultScene })
}

const onShortcut = async (item) => {
  if (!item?.text || streaming.value) return
  const scenario = resolveProductScenario(item.text, item.scene)
  if (scenario) {
    await runProductScenario(item.text, scenario)
    return
  }
  sendMessage({ text: item.text, scene: item.scene })
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

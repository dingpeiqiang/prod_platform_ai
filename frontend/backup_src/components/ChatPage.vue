<template>
  <AssistantShell
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionsForView"
    :sessionsLoading="false"
    :activeScene="activeScene"
    :open-draft-signal="draftOpenSeq"
    :skill-signal="skillSignal"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="() => {}"
    @switch-session="onSwitchSession"
    @delete-session="onDeleteSession"
    @shortcut="onShortcut"
    @quick-action="onQuickAction"
    @open-approval="onOpenApproval"
    @open-product-metrics="onOpenProductMetrics"
    @open-draft="onOpenDraft"
  >
    <ChatMessageList
      :mode="mode"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @regenerate="onRegenerate"
      @todo-action="onTodoAction"
      @open-detail="onOpenDetail"
      @open-ops="onOpenOps"
      @fill-input="onFillInput"
      @action="onMessageAction"
    />

    <template #right>
      <WorkflowStepper v-if="configPanel.visible || detailPanel.visible" />
      <ProductConfigPanel
        v-if="configPanel.visible"
        :key="configPanelKey"
        ref="configPanelRef"
        :parsed-fields="configPanel.parsedFields"
        :work-order-id="configPanel.workOrderId"
        :batch-context="batchContext"
        :schema-config="configPanel.schemaConfig"
        :archive-snapshot="configPanel.archiveSnapshot"
        @close="closeConfigPanel"
        @submitted="onConfigSubmitted"
        @copy-init="onCopyInit"
        @copy-rename="onCopyRename"
        @dialogue-rename="onDialogueRename"
        @submit-filing="onSubmitFiling"
        @submit-channel="onSubmitChannel"
        @go-prev="onBatchGoPrev"
        @go-next="onBatchGoNext"
        @batch-submitted="onBatchSubmitted"
      />
      <DetailPanel
        v-else-if="detailPanel.visible"
        :title="detailPanel.title"
        :content="detailPanel.content"
        :blocks="detailPanel.blocks"
        @close="onDetailClose"
        @open-rootcause="onOpenRootCause"
        @update:drill="opsDrillActive = $event"
      />
      <!-- 查询助手·比对分析面板（query.scene3）：多产品资费要素横向比对 -->
      <QueryComparePanel
        v-else-if="comparePanel.visible"
        :items="comparePanel.items"
        @close="comparePanel = { visible: false, items: [] }"
      />
    </template>

    <template #input-bar>
      <Transition name="restore-slide">
        <div v-if="showConfigRestoreBar" class="config-restore-bar" @click="reopenConfigPanel">
          <div class="restore-bar-left">
            <svg class="restore-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
            <span class="restore-label">配置工作台已收起</span>
            <span class="restore-product">{{ configPanelProductName }}</span>
            <span class="restore-badge" :class="restoreBadgeClass">{{ restoreBadgeText }}</span>
          </div>
          <button class="restore-action">
            <span>恢复工作台</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
      </Transition>
      <Transition name="restore-slide">
        <div v-if="showOpsRestoreBar" class="ops-restore-bar" @click="reopenOpsPanel">
          <div class="restore-bar-left">
            <svg class="restore-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 3v18h18"/>
              <path d="M7 14l4-4 4 2 5-6"/>
            </svg>
            <span class="restore-label">产品运营视图已收起</span>
            <span class="restore-product">畅享59元5G套餐</span>
          </div>
          <button class="restore-action">
            <span>恢复运营视图</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
      </Transition>
    </template>
  </AssistantShell>

  <ApprovalDrawer
    v-model="approvalDrawer.visible"
    :approvalId="approvalDrawer.id"
    @back-to-chat="onDrawerBackToChat"
  />
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import DetailPanel from './DetailPanel.vue'
import QueryComparePanel from './QueryComparePanel.vue'
import { opsDrillState } from './OpsView.vue'
import ProductConfigPanel from './ProductConfigPanel.vue'
import ApprovalDrawer from './ApprovalDrawer.vue'
import WorkflowStepper from './WorkflowStepper.vue'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { buildAnalyzeFullBlocks, buildAnalyzeFullText } from '../config/analyzeFullData.js'
import { buildOptimizeFullBlocks, buildOptimizeFullText } from '../config/optimizeFullData.js'
import { launchThinkingSteps, buildLaunchSummary, launchProductConfig } from '../config/launchOptimizeData.js'
import { batchDraftProducts } from '../config/batchDraftData.js'
import { campusProductConfig } from '../config/campusDraftData.js'
import { searchStockProducts } from '../config/stockProductData.js'
import {
  knowledgeBase,
  searchKnowledge,
  classifyQuestion,
  extractProductFromText,
  extractCompareProducts,
  buildCompareItem,
} from '../config/queryAssistantData.js'
import { useWorkbenchStore } from '../stores/workbench.js'
import { useDraftStore } from '../stores/draft.js'
import { useNotificationStore } from '../stores/notification.js'
import { useApprovalStore } from '../stores/approval.js'
import { useWorkflowStore } from '../stores/workflow.js'
import { useTestReportStore } from '../stores/testReport.js'
import { useFilingStore } from '../stores/filingStore.js'
import { useWorkOrderStore } from '../stores/workOrder.js'
import { testReport } from '../config/launchOptimizeData.js'
import { filing, salesChannel } from '../config/launchOptimizeData.js'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'

const inputText = ref('')
const streaming = ref(false)
const messages = ref([])
const activeScene = ref(null)

const workbenchStore = useWorkbenchStore()
const { activeAssistant, sessionsForActive } = storeToRefs(workbenchStore)

const notifStore = useNotificationStore()
const { triggerId: notifTriggerId } = storeToRefs(notifStore)

const approvalStore = useApprovalStore()
const workflowStore = useWorkflowStore()
const testReportStore = useTestReportStore()
const { triggerId: testReportTriggerId } = storeToRefs(testReportStore)
const filingStore = useFilingStore()
const { triggerId: filingTriggerId } = storeToRefs(filingStore)

const workOrderStore = useWorkOrderStore()
workOrderStore.seedIfEmpty()

const draftStore = useDraftStore()

const detailPanel = ref({ visible: false, title: '', content: '' })

const configPanel = ref({ visible: false, parsedFields: null, workOrderId: '', draftId: '', schemaConfig: null, archiveSnapshot: null, archiveWorkOrderId: '', copyWorkOrderId: '', copyDraftName: '' })
const configPanelRef = ref(null)
const configSubmitted = ref(false)

// 查询助手·比对分析面板状态（query.scene3）：items 为 buildCompareItem 归一化产品数组
const comparePanel = ref({ visible: false, items: [] })

// 查询助手·上下文记忆：最近一次问答/调阅涉及的产品，
// 支持"该产品/它/这个产品"等代词追问（如问完"智享99包含多少流量"后发"调阅该产品的配置档案"）
const lastQueryProduct = ref(null)
const PRONOUN_PAT = /(该产品|该套餐|这个产品|这个套餐|此产品|此套餐|它|其|上面(的|提到)?|刚才(的|提到)?|刚刚(的|提到)?)/

// 对话式配置（rd.scene2）专用工单号：在该场景内工作台/审批/AI回复保持一致，且不读取产品运营助手全局工单号
const dialogueWorkOrderId = ref('')
// 每次打开配置工作台递增，用于强制 ProductConfigPanel 重新挂载，避免产品运营助手(launch)残留状态污染对话式配置
const configPanelOpenSeq = ref(0)
// 面板 key：launch（无 parsedFields）与每次对话式/批量打开都不同，保证干净挂载全新初始化
const configPanelKey = computed(() =>
  configPanel.value.parsedFields
    ? `dialogue-${configPanel.value.draftId || ''}-${configPanel.value.workOrderId || dialogueWorkOrderId.value || 'new'}-${configPanelOpenSeq.value}`
    : configPanel.value.archiveSnapshot
      ? `stock-${configPanel.value.archiveWorkOrderId || 'archive'}-${configPanelOpenSeq.value}`
      : `launch`
)
// 当前是否为研发助手·对话式配置场景（工作台以 parsedFields 打开）
const inDialogueScene = computed(() => !!configPanel.value.parsedFields)
// 当前是否为「文件导入批量配置」的草稿产品工作台（batchMode 与 parsedFields 同时成立）
// 仅当当前草稿记录 source==='batch' 时为批量模式；对话式配置草稿（source==='dialog'）不显示上/下一个批量导航
const inBatchDraftScene = computed(() => {
  const id = configPanel.value.draftId
  if (!id) return false
  const d = draftStore.findById(id)
  return !!d && d.source === 'batch'
})

// ========== 批量配置 · 草稿工作台导航 ==========
// 当前打开草稿产品在草稿列表中的位置（用于工作台内 上/下一个 切换）
const currentDraftIdx = computed(() => {
  const id = configPanel.value.draftId
  if (!id) return -1
  return draftStore.draftList.findIndex((d) => d.id === id)
})
// 传给工作台的批量上下文：显示 第x/共y 个 + 是否有上/下一个
const batchContext = computed(() => {
  if (!inBatchDraftScene.value) return null
  const idx = currentDraftIdx.value
  const total = draftStore.draftList.length
  return {
    index: idx >= 0 ? idx + 1 : 1,
    total,
    hasPrev: idx > 0,
    hasNext: idx >= 0 && idx < total - 1,
    draftId: configPanel.value.draftId,
  }
})

// 打开指定草稿产品的配置工作台
function openDraftWorkbench(draft) {
  if (!draft) return
  configPanelOpenSeq.value += 1
  // 校园类全新产品（批量导入的畅享校园卡等）使用场景专属配置数据，避免把"沿用原套餐"带入新建场景
  const campusProduct = /青春卡|校园卡|学生卡/.test(draft.prodName || '')
  configPanel.value = {
    visible: true,
    parsedFields: draft.parsedFields || {},
    workOrderId: draft.workOrderId,
    draftId: draft.id,
    schemaConfig: campusProduct ? campusProductConfig : null,
  }
  configPanelHasOpened.value = true
  draftStore.setActive(draft.id)
  workflowStore.initWorkflow({
    workOrderId: draft.workOrderId,
    productName: draft.prodName,
    templateLabel: launchProductConfig.productInfo.templateLabel,
  })
}

// 从「我的草稿」抽屉恢复某草稿对应的工作台
function onOpenDraft(draft) {
  if (!draft) return
  openDraftWorkbench(draft)
}

// 工作台内切换 上一个/下一个 草稿产品
function onBatchGo(delta) {
  const idx = currentDraftIdx.value
  const list = draftStore.draftList
  if (idx < 0) return
  const nextIdx = idx + delta
  if (nextIdx < 0 || nextIdx >= list.length) return
  openDraftWorkbench(list[nextIdx])
}
const onBatchGoPrev = () => onBatchGo(-1)
const onBatchGoNext = () => onBatchGo(1)

// 批量草稿提交后：把当前草稿标记为已提交，并自动跳转到下一个草稿产品的工作台
function onBatchSubmitted() {
  const draftId = configPanel.value.draftId
  if (!draftId) return
  draftStore.markSubmitted(draftId)
  // 打开下一个仍未提交的草稿（按当前列表顺序向后找）
  const list = draftStore.draftList
  if (list.length > 0) {
    openDraftWorkbench(list[0])
  } else {
    // 全部提交完成
    ElMessage.success('全部草稿产品已提交，进入审批阶段')
  }
}



const configPanelHasOpened = ref(false)

// 「我的草稿」抽屉外部打开信号（如点击对话里的「查看我的草稿」按钮时自增触发）
const draftOpenSeq = ref(0)

// 产品运营视图收起标记：点击「根因分析」进入对话后折叠，可经输入框下方还原条一键恢复
const opsPanelCollapsed = ref(false)
// 产品运营视图当前下钻选中的套餐 key（用于恢复时还原到关闭前的单产品视图）
const opsDrillActive = ref(null)

// 记录面板回传的用户实际配置值（formData），用于归档工单"查看完整工作台档案"只读回看
const lastConfigValues = ref(null)

const approvalDrawer = ref({ visible: false, id: '' })

// 当前场景对应的工作单号：对话式配置(rd.scene2)用自己那份，产品运营助手用全局 launch 单号
const sceneWorkOrderId = computed(() => {
  if (configPanel.value.parsedFields) return configPanel.value.workOrderId || dialogueWorkOrderId.value || ''
  return launchProductConfig.productInfo.workOrderId
})

// 最新一条与当前配置工作台关联的审批工单（按场景单号匹配，避免跨场景串扰）
const currentApproval = computed(() => {
  if (!configSubmitted.value) return null
  const wo = sceneWorkOrderId.value
  if (!wo) return null
  return approvalStore.sortedApprovals.find((a) => a.workOrderId === wo) || null
})

// 审批是否已通过（工单终态为 approved）
const approvalPassed = computed(() => currentApproval.value?.status === 'approved')

const restoreBadgeText = computed(() => {
  if (!configSubmitted.value) return '草稿'
  if (testReport.uploaded) return '待备案公示'
  if (approvalPassed.value) return '测试中'
  return '待审批'
})

const restoreBadgeClass = computed(() => {
  if (!configSubmitted.value) return ''
  if (testReport.uploaded) return 'tested'
  return approvalPassed.value ? 'testing' : 'submitted'
})

// 配置工作台产品名称：对话式配置用解析出的名称（未命名时为占位），否则用全局默认
const configPanelProductName = computed(() => {
  if (configPanel.value.parsedFields?.prodName) return configPanel.value.parsedFields.prodName
  if (configPanel.value.parsedFields) return '校园套餐（待命名）'
  return launchProductConfig.productInfo.prodPrcName
})

// ========== 历史对话：每轮自动快照 + 面板状态恢复 ==========
// 记录上一次快照时的消息数量，避免同一轮被重复快照
let lastSnapshotMsgCount = 0

// 采集当前配置工作台状态快照（消息之外的面板状态）
function capturePanelSnapshot() {
  return {
    productStatus: launchProductConfig.productInfo.status,
    configPanelOpen: configPanel.value.visible,
    configSubmitted: configSubmitted.value,
    testReportUploaded: testReport.uploaded,
    filingStarted: filing.started,
    filingSubmitted: filing.submitted,
    filingApproved: filing.approved,
    salesAvailable: salesChannel.available,
    salesSubmitted: salesChannel.submitted,
    salesSelected: (salesChannel.selected || []).slice(),
    workflow: workflowStore.workflow ? JSON.parse(JSON.stringify(workflowStore.workflow)) : null,
  }
}

// 把当前会话的（累积）对话写入左侧一条历史记录，随每轮对话实时更新
function snapshotRoundToHistory() {
  if (messages.value.length === 0) return
  // 仅当本轮新增/变化了消息时才落库（避免重复）
  if (messages.value.length === lastSnapshotMsgCount) return
  lastSnapshotMsgCount = messages.value.length
  const snapshot = capturePanelSnapshot()
  workbenchStore.upsertActiveSession({
    // 未选助手时归入兜底分组，保证"直接对话"也能产生历史记录
    assistantKey: activeAssistant.value || 'default',
    messages: messages.value.map((m) => ({ ...m })),
    snapshot,
  })
}

// 每完成一轮 AI 回复（streaming 由 true → false）后，保存一条历史记录
watch(streaming, (val) => {
  if (!val) snapshotRoundToHistory()
})

// 恢复某个 session 的面板状态（聊天消息在 onSwitchSession 中恢复）
function restorePanelState(snapshot) {
  if (!snapshot) return
  const { productStatus, configPanelOpen, configSubmitted: openSubmitted, testReportUploaded, filingStarted, filingSubmitted, filingApproved, salesAvailable, salesSubmitted, salesSelected, workflow } = snapshot

  // 恢复各响应式状态对象
  launchProductConfig.productInfo.status = productStatus || 'draft'
  testReport.uploaded = !!testReportUploaded
  filing.started = !!filingStarted
  filing.submitted = !!filingSubmitted
  filing.approved = !!filingApproved
  salesChannel.available = !!salesAvailable
  salesChannel.submitted = !!salesSubmitted
  salesChannel.selected.splice(0, salesChannel.selected.length, ...(salesSelected || []))

  // 恢复流程 stepper
  if (workflow) {
    try {
      workflowStore.workflow = workflow
    } catch (e) { /* ignore */ }
  }

  // 恢复配置工作台打开状态
  configPanel.value.visible = !!configPanelOpen
  configSubmitted.value = !!openSubmitted
  configPanelHasOpened.value = !!configPanelOpen

  // 面板打开时同步"已提交"状态，使底部操作栏随流程阶段切换
  // 仅 draft 为未提交（显示 保存草稿/智能稽核/提交配置），其余阶段均已提交
  if (configPanel.value.visible) {
    const isSubmitted = !!openSubmitted && (productStatus || 'draft') !== 'draft'
    nextTick(() => {
      if (configPanelRef.value && typeof configPanelRef.value.setSubmitted === 'function') {
        configPanelRef.value.setSubmitted(isSubmitted)
      }
    })
  }
}

// 流程是否已走完（上架销售完成 → 归档）。归档后不再以"配置工作台"折叠概念存在，
// 而是进入右上角"工单中心"统一管理
const isWorkflowComplete = computed(
  () => launchProductConfig.productInfo.status === 'listed'
)

// 收集当前方案的配置快照，作为归档工单的只读档案（概要行）
function buildArchiveSnapshot() {
  const src = launchProductConfig
  const rows = []
  if (src.baseInfo) {
    Object.entries(src.baseInfo).forEach(([key, f]) => {
      rows.push({ key, label: f.label || key, value: f.value })
    })
  }
  return rows
}

// 对话式配置提交后，生成"待审批"记录的只读概览（概要行）
function buildPendingSnapshot(values) {
  const v = values || {}
  const row = (key, label, val) => (val === undefined || val === '' || val === null ? null : { key, label, value: val })
  const rows = [
    row('prodName', '产品名称', v.prodName),
    row('monthlyFee', '套餐月费', v.monthlyFee !== undefined ? `${v.monthlyFee}元/月` : undefined),
    row('flowAmount', '通用流量', v.flowAmount !== undefined ? `${v.flowAmount}${v.flowUnit || 'GB'}` : undefined),
    row('voiceAmount', '本地语音', v.voiceAmount !== undefined ? `${v.voiceAmount}分钟` : undefined),
    row('smsAmount', '短信', v.smsAmount !== undefined ? `${v.smsAmount}条` : undefined),
    row('validity', '有效期', v.validity),
    row('renewMethod', '续订方式', v.renewMethod),
  ].filter(Boolean)
  if (!rows.length) {
    rows.push({ key: 'pending', label: '状态', value: '待审批' })
  }
  return rows
}

// 收集当前方案的完整表单值（含表格行），用于归档工单"查看完整工作台档案"只读回看
function buildArchiveValues() {
  const src = launchProductConfig
  const values = {}
  ;[
    src.baseInfo,
    ...(src.resourceSections || []).map((s) => s.fields),
    ...(src.marketingSections || []).map((s) => s.fields),
    ...(src.salesSections || []).map((s) => s.fields),
  ].forEach((fields) => {
    ;(fields || []).forEach((f) => {
      if (f.type === 'table') {
        values[f.key] = (f.rows || []).map((r) => ({ ...r }))
      } else {
        values[f.key] = f.value
      }
    })
  })
  return values
}

// 上架销售完成：把当前方案归档为一条历史工单（进入工单中心）
function archiveCompletedWorkOrder(values) {
  const wo = launchProductConfig.productInfo.workOrderId
  // 已归档过则不重复
  if (workOrderStore.getByWorkOrderId(wo)) return
  workOrderStore.addArchived({
    workOrderId: wo,
    productName: launchProductConfig.productInfo.prodPrcName,
    templateLabel: launchProductConfig.productInfo.templateLabel,
    channelList: (salesChannel.selected || []).map((k) => {
      const ch = (salesChannel.list || []).find((c) => c.key === k)
      return ch ? ch.label : k
    }),
    salesAt: Date.now(),
    status: 'listed',
    snapshot: buildArchiveSnapshot(),
    // 优先用面板回传的用户实际配置值；未回传时回退到全局预填值
    values: values || lastConfigValues.value || buildArchiveValues(),
    // 三份历史快照（深拷贝）：让归档工单的"配置档案"展示该工单自身的数据
    testReport: deepClone(testReport),
    filing: deepClone(filing),
    channel: deepClone(salesChannel),
  })
}

// 深拷贝可序列化对象（去除 reactive 响应式代理）
function deepClone(obj) {
  try {
    return JSON.parse(JSON.stringify(obj))
  } catch (e) {
    return obj
  }
}

const closeConfigPanel = () => {
  configPanel.value = { visible: false, parsedFields: configPanel.value.parsedFields, draftId: configPanel.value.draftId }
  // 流程已走完（上架销售完成）：归档到工单中心，不显示"配置工作台已收起"折叠条
  if (isWorkflowComplete.value) {
    archiveCompletedWorkOrder()
    const name = launchProductConfig.productInfo.prodPrcName
    ElMessage.success(`${name} 已完成上架，已归档至右上角「工单中心」`)
  }
}

// 依据当前产品状态同步配置工作台的"已提交"（归档只读）状态：
// 只要流程已从草稿进入任何后续阶段（已提交/审批/测试/备案/渠道/上架列表），
// 配置工作台即应为只读归档态，底部显示已提交/归档信息，而非常规配置按钮
function syncPanelSubmitted() {
  nextTick(() => {
    if (configPanelRef.value && typeof configPanelRef.value.setSubmitted === 'function') {
      const isSubmitted = !!configSubmitted.value && (launchProductConfig.productInfo.status || 'draft') !== 'draft'
      configPanelRef.value.setSubmitted(isSubmitted)
    }
  })
}

const reopenConfigPanel = () => {
  const draftId = configPanel.value.draftId
  configPanel.value = { visible: true, parsedFields: configPanel.value.parsedFields, draftId }
  configPanelHasOpened.value = true
  // 流程已推进（非草稿）时，保持"已提交"标记，使恢复后的工作台为只读归档态
  if ((launchProductConfig.productInfo.status || 'draft') !== 'draft') {
    configSubmitted.value = true
  }
  syncPanelSubmitted()
}

// 当天工单号前缀：WO + YYYYMMDD
function dailyWorkOrderPrefix() {
  const now = new Date()
  const ymd =
    String(now.getFullYear()) +
    String(now.getMonth() + 1).padStart(2, '0') +
    String(now.getDate()).padStart(2, '0')
  return 'WO' + ymd
}

// 统一收集当天已存在的同前缀工单号（历史会话快照 + 草稿 + 已归档工单），
// 供对话式配置与文件批量导入共用同一序号空间，保证无论先后顺序都不会冲突。
function collectDailyWorkOrderIds(prefix) {
  const used = new Set()
  const collect = (id) => { if (id && String(id).startsWith(prefix)) used.add(String(id)) }
  workbenchStore.sessions.forEach((s) => {
    const w = s.snapshot && (s.snapshot.workOrderId || (s.messages || []).find((m) => m.workOrderId)?.workOrderId)
    collect(w)
  })
  draftStore.drafts.forEach((d) => collect(d.workOrderId))
  workOrderStore.archived.forEach((a) => collect(a.workOrderId))
  return used
}

// 生成 count 个连续工单号（WO + YYYYMMDD + 当日序号，如 WO20260827001）
function buildDailyWorkOrderIds(count = 1) {
  const prefix = dailyWorkOrderPrefix()
  const used = collectDailyWorkOrderIds(prefix)
  let seq = used.size
  const ids = []
  for (let i = 0; i < count; i++) {
    seq += 1
    ids.push(prefix + String(seq).padStart(3, '0'))
  }
  return ids
}

// 对话式配置单个工单号（与批量共用同一序号空间，避免被批量导入覆盖）
function buildDailyWorkOrderId() {
  return buildDailyWorkOrderIds(1)[0]
}

// 批量导入：按当天日期生成 count 个连续工单号（与对话式配置共用同一序号空间）
function buildDailyBatchWorkOrderIds(count) {
  return buildDailyWorkOrderIds(count)
}

// 对话式配置草稿的资费概要（用于「我的草稿」卡片展示）
function buildDialogueDraftSummary(parsed) {
  const parts = []
  if (parsed.monthlyFee) parts.push(`${parsed.monthlyFee}元/月`)
  if (parsed.flowAmount) {
    const type = parsed.flowType === 'directional' ? '定向流量' : '通用流量'
    parts.push(`${parsed.flowAmount}${parsed.flowUnit || 'GB'}${type}`)
  }
  if (parsed.voiceAmount) parts.push(`${parsed.voiceAmount}分钟语音`)
  if (parsed.smsAmount) parts.push(`${parsed.smsAmount}条短信`)
  return parts.join(' · ') || '待完善资费配置'
}

  // 存量复制：复制按钮触发后登记「我的草稿」（source='copy'），并记录复制工单，供提交时登记「我的产品」
  const onCopyInit = (payload) => {
    if (!payload?.workOrderId) return
    configPanel.value.copyWorkOrderId = payload.workOrderId
    configPanel.value.copyDraftName = payload.prodName || '未命名产品'
    draftStore.upsertDraft({
      workOrderId: payload.workOrderId,
      prodName: payload.prodName,
      summary: payload.summary || '',
      parsedFields: null,
      source: 'copy',
    })
  }

  // 复制模式下用户编辑产品名后同步「我的草稿」中的名称（提交前改名也能显示最新值）
  const onCopyRename = (name) => {
    if (!name || !configPanel.value.copyWorkOrderId) return
    configPanel.value.copyDraftName = name
    const d = draftStore.drafts.find((x) => x.workOrderId === configPanel.value.copyWorkOrderId)
    if (d) d.prodName = name
  }

  // 对话式配置下用户在工作台内命名/改名后，实时同步流程条/草稿/解析快照（提交前即可联动展示）
  const onDialogueRename = (name) => {
    if (!name || !configPanel.value.parsedFields) return
    configPanel.value.parsedFields.prodName = name
    if (workflowStore.workflow) workflowStore.workflow.productName = name
    const woId = configPanel.value.workOrderId || dialogueWorkOrderId.value
    if (woId) {
      const d = draftStore.drafts.find((x) => x.workOrderId === woId)
      if (d) d.prodName = name
    }
  }

const onConfigSubmitted = (payload) => {
  configSubmitted.value = true
  if (payload?.values) lastConfigValues.value = payload.values

  // 场景区分：产品研发助手对话式配置(parsedFields)仅到"审批"（autoAdvance=false），
  // 产品运营助手(launch)保持原有模拟审批自动通过流程（autoAdvance 默认 true）
  // 存量产品复制配置(autoAdvance=false)同样仅到"审批"，与对话式配置一致
  const isDialogueSubmit = !!configPanel.value.parsedFields
  const isCopySubmit = !!configPanel.value.copyWorkOrderId

  // 对话式配置使用工作台已生成/回传的本场景工单号（当天日期构成）；否则沿用回传值或按当天日期生成
  const workOrderId = isCopySubmit
    ? configPanel.value.copyWorkOrderId
    : (isDialogueSubmit && (payload?.workOrderId || configPanel.value.workOrderId || dialogueWorkOrderId.value))
      || payload?.workOrderId
      || buildDailyWorkOrderId()

  // 对话式配置下产品名以面板提交回传的实际名称为准（用户可能在工作台内手动命名/改名）
  const submittedName = String(payload?.productName || '').trim()
  const dialogueProductName = submittedName || configPanel.value.parsedFields?.prodName || '校园套餐（待命名）'
  // 同步回 parsedFields 快照，保证收起工作台还原条等处的名称一致
  if (isDialogueSubmit && submittedName && configPanel.value.parsedFields) {
    configPanel.value.parsedFields.prodName = submittedName
  }
  // 复制品产品名：优先取用户在面板内实际编辑后的产品名（提交时回传），保证改名后各处同步
  const copyProductName = (isCopySubmit && submittedName)
    ? submittedName
    : (configPanel.value.copyDraftName || '未命名产品（复制）')

  // 写入审批 store（对话式配置名称与工作台一致）
  const approval = approvalStore.createApproval({
    workOrderId,
    productName: isDialogueSubmit ? dialogueProductName : isCopySubmit ? copyProductName : launchProductConfig.productInfo.prodPrcName,
    templateLabel: launchProductConfig.productInfo.templateLabel,
    autoAdvance: !isDialogueSubmit && !isCopySubmit,
  })

  // 对话式配置/存量复制（rd.scene2）提交后，在"我的产品"中登记一条待审批记录（状态 pending）
  // 仅产品研发助手的对话式配置与存量复制场景生效；产品运营助手（launch-optimize）沿用原有流程，不新增记录
  if (configPanel.value.parsedFields || isCopySubmit) {
    workOrderStore.addArchived({
      workOrderId,
      productName: isCopySubmit ? copyProductName : dialogueProductName,
      templateLabel: launchProductConfig.productInfo.templateLabel,
      channelList: [],
      salesAt: Date.now(),
      status: 'pending',
      snapshot: buildPendingSnapshot(payload?.values),
      values: payload?.values || null,
    })
  }

  // 对话式配置/存量复制的草稿在提交后不再属于"草稿"，从「我的草稿」移除（保留在「我的产品」待审批记录中）
  if (isDialogueSubmit && configPanel.value.draftId) {
    draftStore.markSubmitted(configPanel.value.draftId)
  }
  if (isCopySubmit) {
    const copyDraft = draftStore.drafts.find((d) => d.workOrderId === configPanel.value.copyWorkOrderId)
    if (copyDraft) draftStore.markSubmitted(copyDraft.id)
  }

  // 配置已提交，将工单号写入流程并推进到预览审批
  workflowStore.setWorkOrderId(workOrderId)
  // 同步产品名到顶部流程条（对话式/复制模式改名后以面板实际名称为准）
  if (workflowStore.workflow && (isDialogueSubmit || isCopySubmit)) {
    workflowStore.workflow.productName = isDialogueSubmit ? dialogueProductName : copyProductName
  }
  workflowStore.advanceTo('approval')

  const replyText =
    `已帮您提交，配置已进入审批阶段。\n` +
    (isCopySubmit && copyProductName ? `产品名称：${copyProductName}\n` : '') +
    `工单编号：${workOrderId}\n` +
    `如需关注审批进度，可以点击右上角通知图标查看实时状态，或在历史会话中检索该工单。`

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    workOrderId,
    thinkingSteps: [
      '接收配置面板提交信号…',
      '校验稽核结果与必填字段…',
      `生成审批工单：${workOrderId}`,
      '推送审批进度通知',
    ],
    thinkingActive: true,
    followUpActions: [
      { key: 'track', label: '查看审批进度', text: `查看工单 ${workOrderId} 审批进度` },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  ;(async () => {
    startThinking()
    await sleep(400)
    aiMsg.loading = false
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()

    for (let i = 0; i < replyText.length; i += 6) {
      if (cancelled) break
      await sleep(24)
      aiMsg.streamText = replyText.slice(0, i + 6)
      aiMsg.content = aiMsg.streamText
      messages.value = [...messages.value]
    }
    if (!cancelled) {
      aiMsg.streamText = replyText
      aiMsg.content = replyText
    }
    aiMsg.done = true
    aiMsg.loading = false
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
  })()
}

const onOpenDetail = (payload) => {
  if (!payload) return
  detailPanel.value = {
    visible: true,
    title: payload.title || '详情',
    content: payload.content || '',
    blocks: payload.blocks || [],
  }
}

const closeDetail = () => {
  detailPanel.value = { visible: false, title: '', content: '' }
}

// 当前详情面板是否产品运营视图
const isOpsPanel = () =>
  detailPanel.value.visible &&
  (detailPanel.value.blocks || []).some((b) => b.type === 'ops-dashboard')

// 详情面板关闭按钮：产品运营视图默认折叠（可经输入框下方还原条恢复），其余详情直接关闭
// 无对话时直接关闭，不折叠展示还原条；有对话时才保留"折叠 + 还原条"便于恢复
const onDetailClose = () => {
  if (isOpsPanel()) {
    opsPanelCollapsed.value = messages.value.length > 0
  }
  closeDetail()
}

// 运营助手：在右侧详情面板展示产品运营视图
// 仅当选中「产品运营助手」时才允许打开，其他助手（工作台/研发/查询）不可展示
const onOpenOps = () => {
  if ((activeAssistant.value || 'rd') !== 'ops') return
  opsPanelCollapsed.value = false
  detailPanel.value = {
    visible: true,
    title: '产品运营视图',
    content: '',
    blocks: [{ type: 'ops-dashboard', data: null, drill: opsDrillActive.value }],
  }
}

const mode = computed(() => activeAssistant.value || 'rd')
const config = computed(() => assistantModes[activeAssistant.value] || assistantModes.rd)

const showConfigRestoreBar = computed(() =>
  configPanelHasOpened.value && !configPanel.value.visible && !isWorkflowComplete.value
)

// 产品运营视图还原条：点击「根因分析」折叠后显示，仅限产品运营助手可见
const showOpsRestoreBar = computed(
  () => opsPanelCollapsed.value && (activeAssistant.value || 'rd') === 'ops'
)

// 恢复产品运营视图
const reopenOpsPanel = () => {
  opsPanelCollapsed.value = false
  onOpenOps()
}

// 传给 Shell 的历史会话：选中助手时显示该助手的历史；未选助手时显示"直接对话"兜底分组的历史
const sessionsForView = computed(() => {
  if (activeAssistant.value) {
    return sessionsForActive.value.map((s) => ({
      session_id: s.id,
      title: s.title,
      updated_at: s.updated_at,
    }))
  }
  const fallbackKey = 'default'
  return workbenchStore.sessions
    .filter((s) => s.assistantKey === fallbackKey)
    .sort((a, b) => (b.updated_at || 0) - (a.updated_at || 0))
    .map((s) => ({
      session_id: s.id,
      title: s.title,
      updated_at: s.updated_at,
    }))
})

let stopFn = null
const genId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const thinkStart = ref(0)

function startThinking() {
  thinkStart.value = Date.now()
}

function endThinking() {
  const ms = thinkStart.value ? Date.now() - thinkStart.value : 0
  return (ms / 1000).toFixed(1)
}

function buildThinkingSteps(context) {
  const { type, userText, scene, action, item } = context

  if (type === 'notification') {
    return [
      '检索当前用户身份与权限范围…',
      '拉取今日待办事项列表，共发现 3 项待处理任务',
      '按紧急程度排序：异动 > 合约 > 合规',
      '计算各项任务的影响范围与损失金额',
      '生成待办播报摘要',
    ]
  }

  if (type === 'todo-action') {
    const actionKey = action?.key
    const itemName = item?.content || ''
    const baseSteps = [
      `识别用户操作：${action?.label || '处理'} → ${itemName}`,
      '调用产品查询助手，检索相关产品档案与历史数据…',
    ]
    if (actionKey === 'analyze') {
      return [
        ...baseSteps,
        '调用产品运营助手，拉取近 7 天新增订购趋势数据',
        '比对同期竞品套餐促销动作',
        '归因分析：流量落后 + 年轻客群流失 + 渠道被截流',
        '计算收入损失：约 670 单 × 59 元 ≈ 3.9 万元/月',
        '生成异动分析报告',
      ]
    }
    if (actionKey === 'renew') {
      return [
        ...baseSteps,
        '调取合约信息：到期日 8/15，关联 3 款产品',
        '从腾讯侧获取最新续约报价（涨幅 5%）',
        '检查关联产品权益文案差异',
        '生成续约处理建议',
      ]
    }
    if (actionKey === 'fill') {
      return [
        ...baseSteps,
        '比对工信部新模板字段要求与当前填写情况',
        '识别缺失字段：资费类型、收费标准、生效规则',
        '生成待补录字段清单',
      ]
    }
    return [...baseSteps, '生成处理建议']
  }

  if (type === 'scene-welcome') {
    return [
      `识别场景：${scene || '通用'}`,
      '加载场景配置与推荐流程…',
      '生成场景引导文案',
    ]
  }

  // 默认：用户文本输入
  const text = userText || ''
  const assistantName = config.value.navTitle
  return [
    `解析用户输入："${text.slice(0, 30)}${text.length > 30 ? '…' : ''}"`,
    `匹配当前助手上下文：${assistantName}`,
    '检索相关知识库与产品数据…',
    '组织回复内容',
  ]
}

/**
 * 切换助手时：
 * 1. 把当前对话归档到原助手的历史
 * 2. 清空当前消息 + 输入框
 */
watch(activeAssistant, (newKey, oldKey) => {
  // 切换助手：结束当前活动会话，下次对话创建新记录
  workbenchStore.startNewSession()
  messages.value = []
  lastSnapshotMsgCount = 0
  inputText.value = ''
  // 切换助手时关闭/复位配置工作台，避免上一个助手（如产品运营助手 launch）残留的工作台污染另一助手的对话场景
  configPanel.value = { visible: false }
  configPanelHasOpened.value = false
  configSubmitted.value = false
  dialogueWorkOrderId.value = ''
  activeScene.value = newKey ? config.value.defaultScene : null
  // 产品运营视图仅限「产品运营助手」可见：切换到其他助手（工作台/研发/查询）时关闭
  const isOpsPanel =
    detailPanel.value.visible &&
    (detailPanel.value.blocks || []).some((b) => b.type === 'ops-dashboard')
  if (isOpsPanel && (newKey || 'rd') !== 'ops') {
    closeDetail()
  }
  opsPanelCollapsed.value = false
  opsDrillActive.value = null
  opsDrillState.reset()
}, { flush: 'post' })

/**
 * 消息通知触发：点击右上角消息按钮后，AI 主动发起一段待办播报。
 */
watch(notifTriggerId, (n) => {
  if (n === 0) return
  showNotificationReply()
})

/**
 * 审批通过后：
 * 1. 资费流程推进到"测试审核"阶段
 * 2. 配置工作台产品状态置为"测试中"
 * 3. AI 回复告知用户已进入测试阶段
 */
watch(approvalPassed, (passed) => {
  if (!passed) return
  // 仅产品运营助手(launch)的审批会自动通过(autoAdvance=true)，走到此处后
  // 恢复原有模拟推进：进入"测试审核"阶段、工作台状态置"测试中"、AI 告知、启动测试模拟。
  // 产品研发助手·对话式配置(autoAdvance=false)的审批永远停留在"审批中"，不会触发此分支。
  // 场景隔离：当前对话若为研发助手对话式配置(rd.scene2)，不注入产品运营助手的审批推进回复。
  if (inDialogueScene.value) return
  workflowStore.onApprovalPassed(launchProductConfig.productInfo.workOrderId)
  launchProductConfig.productInfo.status = 'testing'
  showApprovalPassedReply()
  testReportStore.startSimulate()
})

/**
 * 测试报告到达（测试人员上传后自动解析）：
 * 1. 配置工作台产品状态从"测试中"更新为"测试完成"
 * 2. 资费流程推进到"备案公示"阶段
 * 3. AI 主动回复告知配置人员报告已可查看，并引导到测试报告 Tab
 */
watch(testReportTriggerId, (n) => {
  if (n === 0) return
  // 场景隔离：当前对话若为研发助手对话式配置(rd.scene2)，不注入产品运营助手的测试/备案推进回复
  if (inDialogueScene.value) return
  launchProductConfig.productInfo.status = 'tested'
  // 测试审核完成，待发起备案公示（流程保持在测试审核阶段）
  workflowStore.onTestPassed(launchProductConfig.productInfo.workOrderId)
  showTestReportArrivedReply()
  // 若配置工作台当前已打开（测试中页签），报告到达时自动切换到测试报告页签
  if (configPanel.value.visible) {
    reopenDetailAfterTick(() => {
      if (configPanelRef.value && typeof configPanelRef.value.showTestReport === 'function') {
        configPanelRef.value.showTestReport()
      }
    })
  }
})

async function showApprovalPassedReply() {
  if (streaming.value) {
    await new Promise((resolve) => {
      const unwatch = watch(streaming, (val) => {
        if (!val) { unwatch(); resolve() }
      })
    })
  }

  const workOrderId = launchProductConfig.productInfo.workOrderId
  const productName = launchProductConfig.productInfo.prodPrcName

  startThinking()
  const thinkingSteps = [
    '接收审批工单状态变更信号…',
    `校验工单 ${workOrderId} 审批结果…`,
    '审批通过，更新资费流程状态：预览审批 → 测试审核',
    '同步配置工作台产品状态为"测试中"',
    '生成阶段切换通知',
  ]

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    workOrderId,
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  const reply = [
    `**${productName}** 的审批工单 ${workOrderId} 已通过主管审批。`,
    '',
    '资费流程已推进至 **测试审核** 阶段，配置工作台状态已更新为"测试中"。',
    '',
    '下一步等待测试人员进行逻辑测试和业务验证，测试完成后会上传测试报告，届时您可在配置工作台查看报告详情及验收结论。',
  ].join('\n')

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < reply.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = reply.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = reply
    aiMsg.content = reply
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

async function showTestReportArrivedReply() {
  if (streaming.value) {
    await new Promise((resolve) => {
      const unwatch = watch(streaming, (val) => {
        if (!val) { unwatch(); resolve() }
      })
    })
  }

  const workOrderId = testReport.workOrderId
  const productName = testReport.productName

  startThinking()
  const thinkingSteps = [
    '接收测试报告上传信号…',
    `校验报告 ${testReport.reportId} 与工单 ${workOrderId} 匹配性…`,
    '自动解析报告：固费核查、计费核查、提醒核查、工单核查',
    `验收结论：${testReport.conclusion}（${testReport.status === 'passed' ? '通过' : '未通过'}）`,
    '同步配置工作台产品状态为"测试完成"',
    '更新资费流程状态：测试审核 → 备案公示',
    '生成测试报告到达通知',
  ]

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    workOrderId,
    thinkingSteps,
    thinkingActive: true,
    followUpActions: [
      { key: 'view-report', label: '查看测试报告', text: '查看测试报告' },
      { key: 'start-filing', label: '发起备案公示', text: '发起备案公示' },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  const reply = [
    `**${productName}**（工单 ${workOrderId}）的测试已完成，测试人员已上传测试报告并自动解析。`,
    '',
    `**验收结论：${testReport.conclusion}**（测试${testReport.status === 'passed' ? '通过' : '未通过'}）`,
    '',
    `报告包含**测试方式**（${testReport.testMethod}）、**受理号码**（${testReport.acceptNumber}），以及**固费核查、计费核查、提醒核查、工单核查**四项核查明细。`,
    '',
    '您可在**配置工作台 → 测试报告**页签中查看完整报告详情。',
  ].join('\n')

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < reply.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = reply.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = reply
    aiMsg.content = reply
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

async function showNotificationReply() {
  if (streaming.value) return

  startThinking()
  const thinkingSteps = buildThinkingSteps({ type: 'notification' })

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    todoItems,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  const reply = buildNotificationReply()
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < reply.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = reply.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = reply
    aiMsg.content = reply
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

function buildNotificationReply() {
  return [
    '欢迎回来。今日待办共 **3** 项，已按紧急程度排序。',
    '',
    '最紧急的是 **畅享59元套餐的新增异动** ——持续 7 天，累计影响新增订购约 **670 单**，折合月收入损失约 **3.9 万元**。我已经为您准备好了详细分析，需要现在进入吗？',
  ].join('\n')
}

const todoItems = [
  {
    id: 'todo-1',
    level: 'urgent',
    levelLabel: '🔴 紧急',
    type: '异动',
    content: '畅享59元套餐 · 新增下滑35%（已持续7天）',
    actions: [
      { key: 'analyze', label: '分析' },
    ],
  },
  {
    id: 'todo-2',
    level: 'important',
    levelLabel: '🟡 重要',
    type: '合约',
    content: '腾讯视频权益包 · 8/15到期（关联3款产品）',
    actions: [
      { key: 'renew', label: '续约处理' },
    ],
  },
  {
    id: 'todo-3',
    level: 'reminder',
    levelLabel: '🟢 提醒',
    type: '合规',
    content: '畅享59元套餐 · 资费字段未同步工信部新模板',
    actions: [
      { key: 'fill', label: '补录' },
    ],
  },
]

function buildTodoActionReply(item, action) {
  const map = {
    analyze: [
      `### 畅享59元套餐 · 新增异动分析`,
      '',
      '**异动概览**',
      '- 持续时间：7 天',
      '- 累计影响新增订购：约 670 单',
      '- 折合月收入损失：约 3.9 万元',
      '- 下滑幅度：35%',
      '',
      '**初步归因**',
      '1. 竞品同价位套餐上线了"首月半价"促销，分流明显',
      '2. 本套餐在线营销素材未更新，转化率下降约 18%',
      '3. 渠道侧推荐话术未同步最新权益亮点',
      '',
      '**建议措施**',
      '- 短期：上线限时赠礼活动（3 天内），对冲竞品促销',
      '- 中期：更新营销素材与渠道话术（1 周内）',
      '- 长期：评估套餐资费/权益调整方案',
    ],
    optimize: [
      `### 启动优化方案：畅享59元套餐`,
      '',
      '已为您生成优化预案，核心动作如下：',
      '',
      '| 序号 | 动作 | 负责团队 | 预计上线 |',
      '| ---- | ---- | ---- | ---- |',
      '| 1 | 上线限时赠礼活动 | 运营组 | T+1 |',
      '| 2 | 更新在线营销素材 | 设计组 | T+2 |',
      '| 3 | 同步渠道推荐话术 | 渠道组 | T+3 |',
      '',
      '确认后我将创建对应工单并分派。',
    ],
    renew: [
      `### 腾讯视频权益包 · 续约处理`,
      '',
      '**合约信息**',
      '- 到期日期：8/15',
      '- 关联产品：3 款',
      '- 当前续约报价：已从腾讯侧获取最新报价单',
      '',
      '**待确认事项**',
      '1. 是否接受新报价（较上期涨幅 5%）',
      '2. 关联产品的权益文案是否需要同步更新',
      '3. 续约后是否需要做用户侧公告',
    ],
    fill: [
      `### 资费字段补录：畅享59元套餐`,
      '',
      '工信部新模板要求补录以下字段：',
      '',
      '| 字段 | 当前值 | 要求值 | 状态 |',
      '| ---- | ---- | ---- | ---- |',
      '| 资费类型 | - | 套餐资费 | 待补录 |',
      '| 收费标准 | 59元/月 | 59元/月 | 待补录 |',
      '| 生效规则 | - | 自然月生效 | 待补录 |',
      '',
      '确认后我将提交补录申请。',
    ],
  }
  return map[action.key] || `已处理"${action.label}"操作。`
}

function buildAnalyzeSummary() {
  return [
    '### 畅享59元5G套餐 · 新增异动分析',
    '',
    '> 这不是产品衰退，也不是用户大规模流失，而是一起典型的 **"竞争性新增萎缩"** ——产品在流量配置上被竞品反超，导致年轻新客转化效率下降。在网老用户基本稳定，问题出在获客端。',
    '',
    '**异动概览**',
    '- 健康评分：**72分**（↓9，跌破80分警戒线），其中"新增转化"仅62分',
    '- 近7日新增日均 **177单**，对比前7日 **237单**，降幅 **25.3%**',
    '- 退订日均82单，环比+5.1%，在正常范围内，**存量基本盘未垮**',
    '- 转折点出现在7月20日之后，降幅从3%放大到6%以上',
    '',
    '**核心发现**',
    '1. **流量额度落后竞品29%**：本品30GB vs 竞品均值42.5GB，三家竞品中最低',
    '2. **年轻客群流失**：18-25岁占比从31%跌至18%，日均从73单降至32单',
    '3. **信息流渠道被截流**：抖音/快手渠道占比从27%降至18%，竞品出价提高22%',
    '4. **竞品7月集体升级**：友商A（动感青春版）升流量至40GB、友商B（联通流量王59）降至49元、友商C（电信星卡59）升流量至50GB',
    '',
    '**根因贡献度**',
    '| 根因 | 贡献度 |',
    '| ---- | ------ |',
    '| 流量额度落后竞品均值29% | **50%** |',
    '| 年轻客群在信息流渠道被截流 | 20% |',
    '| 视频权益少1个 | 15% |',
    '| 友商B降价至49元 | 10% |',
    '| 其他（季节性等） | 5% |',
    '',
    '属于**可优化型异动**——通过产品配置调整和渠道投放策略，可快速修复。',
    '',
    '分析完成。接下来是否进入决策建议环节，给出具体的优化方案？',
  ].join('\n')
}

function buildOptimizeSummary() {
  return [
    '### 畅享59元套餐 · 优化决策方案',
    '',
    '> 基于四维分析结论，已形成两类决策方向。**方案A为当前推荐路径**。',
    '',
    '**方案A：新产品策划（⭐ 推荐）**',
    '- 核心策略：在59元档位基础上策划新产品（SP-2024-0591），与现有产品并行销售',
    '- 流量：30GB → **40GB**（追平友商A（动感青春版））',
    '- 视频权益：1个 → **2个**（腾讯+爱奇艺）',
    '- 月费不变（59元），新产品需走资费审批流程',
    '- 存量用户不受影响，新老产品互斥',
    '',
    '**量化收益预测**',
    '- 日均新增：177单 → **380~420单**（+203~243单）',
    '- 月新增用户：5,310户 → **11,400~12,600户**',
    '- **月净增收益：+28.1~33.3万元**',
    '',
    '**风险评估**：6项中3项🟢低风险、3项🟡中低风险，整体可控',
    '',
    '**方案B：产品下线（⚠️ 当前不推荐）**',
    '- 在网用户86万、月收入超5000万、合约用户超31万',
    '- 高退出成本、高执行风险、低可行性，不满足下线三个必要条件',
    '',
    '请确认决策方向：启动产品优化 / 启动产品下线 / 暂不处理 / 我有问题需要确认',
  ].join('\n')
}

async function mockReply(userText) {
  startThinking()
  const thinkingSteps = buildThinkingSteps({ type: 'user-input', userText })

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(300)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    return
  }

  const reply = buildMockReply(userText)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < reply.length; i += 4) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = reply.slice(0, i + 4)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = reply
    aiMsg.content = reply
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
}

function buildMockReply(userText) {
  const assistantName = config.value.navTitle
  return [
    `你刚才对**${assistantName}**说了：**${userText}**`,
    '',
    '这是一条 mock 回复。要接入真实模型，请修改 `ChatPage.vue` 中的 `mockReply` 函数。',
    '',
    '你可以：',
    '- 在 `src/config/assistantModes.js` 中修改场景配置',
    '- 顶部切换不同助手，体验不同场景',
    '',
    '```js',
    '// 示例：接入真实模型',
    'async function reply(text) {',
    '  const res = await fetch("/api/chat", {',
    '    method: "POST",',
    '    body: JSON.stringify({ text, assistant: mode }),',
    '  })',
    '  return res.json()',
    '}',
    '```',
  ].join('\n')
}

const stop = () => {
  if (stopFn) stopFn()
  streaming.value = false
}

const onSend = async (payload) => {
  const text = (payload?.text || inputText.value || '').trim()
  const attachments = payload?.attachments || []
  if ((!text && !attachments.length) || streaming.value) return
  inputText.value = ''

  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'user',
      content: text,
      attachments: attachments.map((a) => ({
        type: a.type || 'file',
        name: a.name || '附件',
        preview: a.preview || null,
        url: a.url || null,
        duration: a.duration,
      })),
      done: true,
      timestamp: Date.now(),
    },
  ]

  // 文本快捷命令：直接推进资费流程的动作（这些动作内部用 streamAssistantReply 自行管理 streaming）
  const filingKeywords = ['发起备案公示', '发起备案', '开始备案']
  const submitFilingKeywords = ['提交备案', '提交备案公示']
  const channelKeywords = ['提交销售渠道', '提交渠道', '选择销售渠道']
  if (filingKeywords.some((k) => text.includes(k))) {
    await onStartFiling()
    return
  } else if (submitFilingKeywords.some((k) => text.includes(k))) {
    await onSubmitFiling()
    return
  } else if (channelKeywords.some((k) => text.includes(k))) {
    await onSubmitChannel()
    return
  }

  streaming.value = true
  try {
    // 资费配置·文件导入批量配置：选中"文件导入批量配置"技能并上传附件时，
    // AI 自动解析附件中的多个产品 → 生成草稿目录写入「我的草稿」，并打开第一个草稿的工作台
    const isBatchImportIntent =
      payload?.tariffSkill === 'batch' && attachments.length > 0
    if (isBatchImportIntent) {
      await streamBatchImportReply(text, attachments)
      return
    }
    // 资费配置·存量产品复制配置：选中"存量产品复制配置"技能后，
    // 用户在检索槽位（产品编码/产品名称）填写条件并发送，AI 检索返回类似产品列表
    const isStockCopyIntent =
      payload?.tariffSkill === 'copy' ||
      (activeScene.value === 'rd.scene2' &&
        /(检索|查询|查找).*(存量|历史|已有).*产品/.test(text) &&
        /(复制|模板)/.test(text))
    if (isStockCopyIntent) {
      await streamStockCopyReply(text, payload?.copySearch)
      return
    }
    // 资费配置·对话式配置·增量更新：当对话式配置工作台已打开（parsedFields 存在）时，
    // 用户可通过追加对话（如"产品叫xxx"/"月费改成xxx"/"流量改60GB"）直接修改面板中对应字段，
    // AI 解析增量指令 → 更新工作台字段并回复解析确认。
    if (configPanel.value.visible && configPanel.value.parsedFields) {
      const fieldUpdate = parseFieldUpdate(text)
      if (fieldUpdate) {
        await streamFieldUpdateReply(text, fieldUpdate)
        return
      }
    }
    // 产品查询助手（query.*）：智能问答 / 档案调阅 / 比对分析 三个场景统一分流。
    // 命中条件：处于查询助手任一场景，或（任意场景下）消息明确是查询类意图。
    const isQueryIntent =
      (activeScene.value || '').startsWith('query.') ||
      (/^(查一下|查询|调阅|查看|对比|比较|帮我查|帮我查一下)/.test(text) && !isTariffLikeText(text))
    if (isQueryIntent) {
      await dispatchQueryIntent(text)
      return
    }
    // 资费配置·对话式配置：用户输入资费需求，AI 解析字段并打开工作台。
    // 命中条件：显式选中"对话式配置"技能（该技能仅在研发助手·资费配置场景显示）；
    // 或在资费配置场景下消息内容明显是资费配置需求（含套餐+月费/流量/语音/短信要素）。
    const isTariffConfigIntent =
      payload?.tariffSkill === 'dialog' ||
      (activeScene.value === 'rd.scene2' &&
        (/(月费|月租|固定费)/.test(text) &&
          /(配置|套餐)/.test(text) &&
          /(流量|语音|短信|分钟|GB|MB)/.test(text)))
    if (isTariffConfigIntent) {
      await streamTariffConfigReply(text)
    } else {
      // 异动归因分析：产品运营助手·异动归因分析场景下的"帮我分析…异动"提问，走完整的多维根因分析回复
      const isOpsAnalyzeIntent =
        activeScene.value === 'ops.scene2' && /(异动|归因|根因)/.test(text) && /(分析|归因)/.test(text)
      if (isOpsAnalyzeIntent) {
        await streamOpsAnalyzeReply(text)
      } else {
        const optimizeKeywords = ['优化方案', '给出具体的优化方案', '针对上面的分析结果']
        const launchKeywords = ['启动产品优化', '启动产品下线', '暂不处理', '我有问题需要确认']
        // 运营数据分析：产品运营助手·运营数据分析场景（ops.scene1）下的指标/趋势/涨跌提问，走真实数据分析回复
        const isOpsTrendIntent =
          activeScene.value === 'ops.scene1' &&
          !optimizeKeywords.some((k) => text.includes(k)) &&
          !launchKeywords.some((k) => text.includes(k)) &&
          /(活跃|订购|收入|涨|跌|趋势|走势|下降|上升|原因|解读|分析|对比)/.test(text)
        if (isOpsTrendIntent) {
          await streamOpsTrendReply(text)
        } else if (launchKeywords.some((k) => text.includes(k))) {
          await streamLaunchOptimizeReply(text)
        } else if (optimizeKeywords.some((k) => text.includes(k))) {
          await streamOptimizeReply(text)
        } else {
          await mockReply(text)
        }
      }
    }
  } finally {
    streaming.value = false
    stopFn = null
  }
}

const onSuggest = (payload) => {
  if (!payload || streaming.value) return

  if (typeof payload === 'object' && (payload.autoSend || payload.welcome || payload.scene)) {
    showSceneWelcome(payload)
    return
  }

  let text = typeof payload === 'string' ? payload : payload.text
  if (!text) return
  inputText.value = text
  // 资费配置场景：欢迎消息里的推荐话术点击后，自动勾选输入框上方对应技能
  autoSelectSkillByText(text)
}

// ── 资费配置(rd.scene2)：快捷回复/推荐话术 → 自动勾选对应技能 ──
// 信号对象 { key, seq }：seq 递增保证重复点击同一话术也能重新触发 watch
const skillSignal = ref(null)
let skillSignalSeq = 0
function emitSkillSignal(key) {
  if (!key) return
  skillSignalSeq += 1
  skillSignal.value = { key, seq: skillSignalSeq }
}

// 按话术内容判断应勾选的技能（与 onSend 中的意图判定关键字保持一致）
function autoSelectSkillByText(text) {
  if (activeScene.value !== 'rd.scene2' || !text) return
  if (/附件|批量/.test(text)) emitSkillSignal('batch')
  else if (/存量|历史|复制/.test(text)) emitSkillSignal('copy')
  else if (/月费|月租|固定费|套餐/.test(text)) emitSkillSignal('dialog')
}

function showSceneWelcome(item) {
  if (!item || streaming.value) return
  if (item.scene) {
    activeScene.value = item.scene
  }

  const welcome = buildSceneWelcome(item)
  inputText.value = ''

  const thinkingSteps = buildThinkingSteps({ type: 'scene-welcome', scene: item.scene || item.label })
  startThinking()

  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'assistant',
      content: welcome.content,
      streamText: welcome.content,
      done: true,
      loading: false,
      timestamp: Date.now(),
      nextSteps: welcome.nextSteps || [],
      scene: item.scene || activeScene.value,
      thinkingSteps,
      thinkingActive: false,
      thinkingDuration: endThinking(),
    },
  ]
}

const onShortcut = (item) => {
  showSceneWelcome(item)
}

const onQuickAction = (action) => {
  if (!action || streaming.value) return
  const matched = config.value.sceneShortcuts.find(
    (s) => s.label === action.label || s.scene === action.scene
  )
  showSceneWelcome(matched || {
    label: action.label,
    scene: action.scene || activeScene.value,
    desc: action.desc || action.label,
    text: action.content || action.text || '',
  })
}

const onRegenerate = (msg) => {
  if (streaming.value) return
  const idx = messages.value.findIndex((m) => m.id === msg.id)
  if (idx === -1) return
  const prevUserMsg = messages.value.slice(0, idx).reverse().find((m) => m.role === 'user')
  messages.value = messages.value.slice(0, idx)
  if (prevUserMsg) {
    onSend({ text: prevUserMsg.content })
  }
}

const onNewSession = () => {
  // 新建会话：结束当前活动会话，下次对话将创建一条新的历史记录
  workbenchStore.startNewSession()
  // 隐藏/复位配置工作台与详情面板
  configPanel.value = { visible: false }
  configPanelHasOpened.value = false
  configSubmitted.value = false
  dialogueWorkOrderId.value = ''
  detailPanel.value = { visible: false, title: '', content: '' }
  opsPanelCollapsed.value = false
  opsDrillActive.value = null
  opsDrillState.reset()
  messages.value = []
  lastSnapshotMsgCount = 0
  inputText.value = ''
  activeScene.value = activeAssistant.value ? config.value.defaultScene : null
}

const onTodoAction = (payload) => {
  if (streaming.value) return
  const { item, action } = payload || {}
  if (!item || !action) return

  if (action.key === 'analyze') {
    runAnalyzeFlow(item, action)
    return
  }

  const replyText = buildTodoActionReply(item, action)
  const thinkingSteps = buildThinkingSteps({ type: 'todo-action', item, action })
  streamAssistantReply(replyText, thinkingSteps)
}

// 畅享59元套餐的多维根因分析流程（紧急异动「分析」按钮 / 运营视图「根因分析」按钮共用）
// 关闭详情面板回到对话区，注入用户消息 + 联想推导 + 流式回复，并附带完整分析报告
const runAnalyzeFlow = (item, action) => {
  if (streaming.value) return
  closeDetail()
  const userText = '帮我分析一下畅享59元套餐的新增异动'
  messages.value = [
    ...messages.value,
    {
      id: genId(),
      role: 'user',
      content: userText,
      done: true,
      timestamp: Date.now(),
    },
  ]
  const replyText = buildAnalyzeSummary()
  const thinkingSteps = buildThinkingSteps({
    type: 'todo-action',
    item,
    action,
  })
  streamAssistantReply(replyText, thinkingSteps, {
    detailCard: {
      title: '畅享59元套餐 · 多维根因分析（完整版）',
      content: buildAnalyzeFullText(),
      blocks: buildAnalyzeFullBlocks(),
    },
    followUpActions: [
      { key: 'supplement', label: '补充更多分析线索', text: '补充更多分析线索' },
      { key: 'optimize', label: '针对上面的分析结果，给出具体的优化方案', text: '针对上面的分析结果，给出具体的优化方案' },
    ],
  })
}

// 异动归因分析（用户消息触发版）：在 onSend 已把 streaming 置为 true 的情况下，
// 复位 streaming 以通过 streamAssistantReply 的防重入判断，输出与分析按钮一致的多维根因分析
async function streamOpsAnalyzeReply(text) {
  streaming.value = false
  closeDetail()
  const item = { name: '畅享59元5G套餐' }
  const thinkingSteps = buildThinkingSteps({
    type: 'todo-action',
    item,
    action: { key: 'analyze', label: '分析' },
  })
  await streamAssistantReply(buildAnalyzeSummary(), thinkingSteps, {
    detailCard: {
      title: '畅享59元套餐 · 多维根因分析（完整版）',
      content: buildAnalyzeFullText(),
      blocks: buildAnalyzeFullBlocks(),
    },
    followUpActions: [
      { key: 'supplement', label: '补充更多分析线索', text: '补充更多分析线索' },
      { key: 'optimize', label: '针对上面的分析结果，给出具体的优化方案', text: '针对上面的分析结果，给出具体的优化方案' },
    ],
  })
}

// 运营数据分析（ops.scene1）：针对指标/趋势/涨跌提问，输出基于运营看板数据的趋势解读与归因
// 支持识别到具体产品（家庭宽带/智能组网/移动云盘/视频彩铃/咪咕视频/5G新通话/来电管家/大屏会员/智享99/畅享59）时给出产品级分析
async function streamOpsTrendReply(text) {
  streaming.value = false
  closeDetail()

  const t = text || ''
  const prodMap = {
    家庭宽带: { income: '40,787万', yoy: '+2.66%', t: '家庭产品', added: '当月新增8.90万（同比**-36.5%**）', active: '活跃率83.09%', special: '新增承压但收入稳健，偏存量贡献' },
    智能组网: { income: '5,395万', yoy: '+5.96%', t: '家庭产品', added: '当月新增9.01万（同比**-41.1%**）', active: '活跃率89.28%', special: '收入增速领先，但新增端明显放缓' },
    移动云盘: { income: '3,844万', yoy: '+0.61%', t: '数字内容', added: '当月新增34.70万（同比**-16.3%**）', active: '付费活跃率2.46%', special: '规模大、收入平稳，付费转化偏低' },
    视频彩铃: { income: '2,826万', yoy: '-22.55%', t: '数字内容', added: '当月新增承压', active: '——', special: '收入同比明显下滑，需重点排查' },
    咪咕视频: { income: '2,475万', yoy: '-22.98%', t: '数字内容', added: '当月新增承压', active: '——', special: '收入同比明显下滑，需重点排查' },
    '5G新通话': { income: '122万', yoy: '+1775.8%', t: '新业务', added: '当月新增11.2万（同比**+18.3%**）', active: '付费活跃率8.42%', special: '高增长新业务，势能强劲' },
    来电管家: { income: '1,106万', yoy: '-2.35%', t: '功能业务', added: '当月新增平稳', active: '——', special: '收入小幅回落，基本盘稳定' },
    大屏会员: { income: '1,749万', yoy: '-5.41%', t: '数字内容', added: '当月新增平稳', active: '——', special: '收入温和下滑，关注续费' },
  }
  const nameHits = ['家庭宽带', '智能组网', '移动云盘', '视频彩铃', '咪咕视频', '5G新通话', '来电管家', '大屏会员']
  const hit = nameHits.find((n) => t.includes(n))

  let replyText
  let thinkingSteps

  if (hit) {
    const p = prodMap[hit]
    // 收入走势解读
    const incUp = p.yoy.startsWith('+')
    const detail = [
      `### 运营数据分析 · ${hit} 收入走势`,
      '',
      `> 已围绕「${t}」调取 ${hit} 近30天运营数据，结合产品运营看板完成收入趋势解读与归因。`,
      '',
      '**本期收入（月度·同比）**',
      '| 指标 | 数值 | 同比 |',
      '| ---- | ---- | ---- |',
      `| 报表收入 | ${p.income} | **${p.yoy}** |`,
      `| 新增 | ${p.added.split('（')[0]} | — |`,
      `| 活跃 | ${p.active} | — |`,
      '',
      '**趋势解读**',
      incUp
        ? `- ${hit} 收入同比 **${p.yoy}**，处于**上升通道**，属${p.t}里的增长亮点。`
        : `- ${hit} 收入同比 **${p.yoy}**，处于**下滑区间**，需重点跟进归因与止损动作。`,
      `- **结构归因**：${p.added}；${p.special}。`,
      `- **健康度**：整体${incUp ? '偏健康、增长动能在线' : '承压，存在回落风险'}，存量质量与续费是观察重点。`,
      '',
      '**建议**',
      incUp
        ? '乘势扩大投放与叠加权益，把增长动能转化为规模与收入；同步抓好新客质量，防止高增长伴随退订。'
        : '优先做竞品对标与资费/权益调优，堵住获客与续费下滑点；存量侧稳定服务、控制离网。',
      '',
      '如需对具体产品做进一步下钻归因，可继续追问。',
    ].join('\n')
    replyText = detail
    thinkingSteps = [
      `解析运营数据分析请求："${t}"`,
      `锁定关注产品：${hit}`,
      '拉取该产品收入/新增/活跃近30天趋势…',
      '输出收入走势解读与归因建议',
    ]
  } else if (/智享|畅享/.test(t)) {
    replyText = [
      '### 运营数据分析 · 套餐收入走势',
      '',
      `> 已围绕「${t}」调取套餐近30天运营数据，完成收入走势解读与归因。`,
      '',
      '**本期关键指标（环比）**',
      '| 指标 | 本期 | 环比 | 判定 |',
      '| ---- | ---- | ---- | ---- |',
      '| 报表收入 | 3,420万元 | **-3.1%** | ⚠️ 小幅回落 |',
      '| 新增订购 | 18.7万 | **-5.4%** | ⚠️ 承压 |',
      '| 退订率 | 1.42% | +0.2pp | ✅ 稳定 |',
      '',
      '**主要归因**',
      '1. **收入下滑主因是新增端放缓**：5G套餐获客受竞品流量升级冲击，高价值新客转化下降。',
      '2. **存量基本盘稳固**：退订率稳定、在网老用户收入贡献托底，未出现大规模流失。',
      '3. **结构性机会**：1000M/高价值档位用户占比提升，客单价有上行空间。',
      '',
      '**建议**：重点修复新增转化——对标竞品升级流量档位，加大高价值渠道投放；存量侧稳定续费即可。',
      '',
      '可对重点产品（家庭宽带 / 智能组网 / 移动云盘）做进一步下钻。',
    ].join('\n')
    thinkingSteps = [
      `解析运营数据分析请求："${t}"`,
      '识别为套餐级收入走势分析',
      '拉取近30天收入/新增/退订趋势…',
      '生成收入走势解读与归因结论',
    ]
  } else {
    replyText = [
      '### 运营数据分析 · 趋势解读',
      '',
      `> 已围绕「${t}」调取近30天运营数据，结合当期运营看板完成趋势解读与归因。`,
      '',
      '**本周关键指标（环比）**',
      '| 指标 | 本期 | 环比 | 判定 |',
      '| ---- | ---- | ---- | ---- |',
      '| 活跃用户 | 1,286万 | **-2.8%** | ⚠️ 小幅下滑 |',
      '| 新增订购 | 45.2万 | **-4.1%** | ⚠️ 承压 |',
      '| 报表收入 | 4.79亿元 | +0.6% | ✅ 企稳 |',
      '',
      '**主要归因**',
      '1. **新增端承压**：家庭宽带当月新增8.90万（同比**-36.5%**）、智能组网9.01万（同比**-41.1%**）、移动云盘34.70万（同比**-16.3%**），三线新增同步放缓，主因是竞品资费升级与获客渠道获客成本上升。',
      '2. **活跃略降但存量稳固**：家庭宽带活跃率83.09%、移动云盘付费活跃2.46%，整体仍处健康区间，未现离网潮（T+3离网率仅1.57%）。',
      '3. **收入底盘稳定**：尽管新增下滑，报表收入同比仍+2.66%，说明存量贡献托底作用明显。',
      '',
      '**建议**：重点修复新增转化——针对竞品流量升级做对标调优，并优化高价值渠道投放；存量侧维持服务稳定性、控制离网率即可。',
      '',
      '如需对某个重点产品（家庭宽带 / 智能组网 / 移动云盘）做下钻分析，可继续追问。',
    ].join('\n')
    thinkingSteps = [
      `解析运营数据分析请求："${t}"`,
      '拉取近30天活跃/新增/收入趋势数据…',
      '对比产品运营看板：家庭宽带 / 智能组网 / 移动云盘',
      '生成趋势解读与归因结论',
    ]
  }

  await streamAssistantReply(replyText, thinkingSteps, {
    followUpActions: [
      { key: 'open-ops-view', label: '打开运营视图', text: '打开运营视图' },
      { key: 'ops-drill-broadband', label: '下钻家庭宽带', text: '解读一下家庭宽带的新增走势' },
      { key: 'ops-drill-clouddisk', label: '下钻移动云盘', text: '解读一下移动云盘的活跃走势' },
    ],
  })
}

// 产品运营视图：亚健康套餐「根因分析」按钮 → 进入对话场景触发畅享59元套餐分析
// drillKey 为当前选中的套餐 key（畅享59），用于折叠后恢复运营视图仍停留该单产品视图
const onOpenRootCause = (drillKey) => {
  const item = { name: '畅享59元5G套餐' }
  const action = { key: 'analyze', label: '分析' }
  // 记录当前单产品视图像，进入对话场景分析，并把产品运营视图折叠收起（可经还原条恢复）
  opsDrillActive.value = drillKey || 'tscy'
  opsPanelCollapsed.value = true
  runAnalyzeFlow(item, action)
}

// 工单中心「产品运营指标」按钮 → 打开产品运营视图中该工单对应的单产品视图
// 真实上架产品（非 seed）动态下钻 key 为 'wo_'+workOrderId，定位到"刚上架、数据积累中"占位单产品；
// 种子工单中仅"畅享59元5G套餐"对应硬编码下钻套餐 tscy（健康视图）；
// 其余种子工单（畅享39元青春版）不在 5G新通话 下钻列表内，退回产品运营总览。
const onOpenProductMetrics = (order) => {
  workbenchStore.selectAssistant('ops')
  opsPanelCollapsed.value = false
  // 配置工作台与产品运营视图共用右侧插槽（v-if / v-else-if 互斥），打开运营视图前先让出右侧面板，
  // 否则运营视图会被配置工作台"遮住"而显示不出来（表现为重叠、无反应）。
  // 直接置 visible=false，避免触发 closeConfigPanel 的归档副作用。
  configPanel.value = { visible: false }
  const woid = order?.workOrderId
  let drillKey = null
  if (woid) {
    const w = workOrderStore.archived.find((x) => x.workOrderId === woid)
    if (w) {
      drillKey = w.seeded ? (w.productName === '畅享59元5G套餐' ? 'tscy' : null) : 'wo_' + woid
    }
  }
  // 延迟到 post 刷新之后设置下钻状态：切换助手触发的 watch(activeAssistant) 会在 post 阶段
  // 清空 opsDrillActive/opsDrillState，需在其之后再写入，避免被覆盖
  nextTick(() => {
    opsDrillActive.value = drillKey
    opsDrillState.key = drillKey
    onOpenOps()
  })
}

const onFillInput = (text) => {
  if (!text) return
  inputText.value = text
}

// followUpAction 直接执行类：track → 打开审批抽屉；audit → 触发配置面板提交
const onMessageAction = ({ action, msg }) => {
  if (action.key === 'track') {
    const woid = msg?.workOrderId
    if (!woid) return
    const ap = approvalStore.getByWorkOrderId(woid)
    if (ap) {
      approvalDrawer.value = { visible: true, id: ap.id }
    }
    return
  }
  if (action.key === 'audit') {
    // 与点击配置面板"提交配置"按钮效果一致：触发智能稽核弹窗 → 通过后提交审批
    if (configPanel.value.visible && configPanelRef.value) {
      configPanelRef.value.submit()
    }
  }
  if (action.key === 'view-report') {
    // 打开配置工作台并切换到测试报告页签
    configPanel.value = { visible: true }
    configPanelHasOpened.value = true
    reopenDetailAfterTick(() => {
      if (configPanelRef.value && typeof configPanelRef.value.showTestReport === 'function') {
        configPanelRef.value.showTestReport()
      }
    })
  }
  if (action.key === 'start-filing') {
    // 发起备案公示：推进到备案公示阶段，在工作台展示备案/公示预览
    onStartFiling()
  }
  if (action.key === 'submit-filing') {
    // 提交备案：启动备案通过模拟
    onSubmitFiling()
  }
  if (action.key === 'view-channel') {
    // 打开配置工作台并切换到销售渠道页签
    configPanel.value = { visible: true }
    configPanelHasOpened.value = true
    reopenDetailAfterTick(() => {
      if (configPanelRef.value && typeof configPanelRef.value.showChannel === 'function') {
        configPanelRef.value.showChannel()
      }
    })
  }
  if (action.key === 'submit-channel') {
    // 提交销售渠道：流程推进到上架销售
    onSubmitChannel()
  }
  if (action.key === 'open-drafts') {
    // 打开「我的草稿」抽屉，查看文件批量导入解析出的待配置产品
    draftOpenSeq.value++
  }
  if (action.key?.startsWith('view-stock-detail-')) {
    // 查看存量产品只读配置档案（可进一步点「复制」生成新产品）
    const code = action.key.replace('view-stock-detail-', '')
    const product = searchStockProducts({ code })[0]
    if (!product) return
    const pInfo = product.snapshot?.productInfo || {}
    configPanelOpenSeq.value += 1
    configPanel.value = {
      visible: true,
      parsedFields: null,
      workOrderId: '',
      draftId: '',
      schemaConfig: null,
      archiveSnapshot: product.snapshot,
      archiveWorkOrderId: product.code,
    }
    configPanelHasOpened.value = true
    // 初始化流程，使配置工作台顶部的流程步骤条正常展示
    workflowStore.initWorkflow({
      workOrderId: pInfo.workOrderId || product.code,
      productName: pInfo.prodPrcName || product.name,
      templateLabel: pInfo.templateLabel || launchProductConfig.productInfo.templateLabel,
    })
    // 同步一条助手回复，说明已打开只读档案
    sendStockDetailReply(product)
  }
  if (action.key?.startsWith('open-query-archive-') || action.key?.startsWith('open-draft-archive-')) {
    // 产品问答快捷回复：一键调阅该产品的配置档案（存量走只读档案，草稿走对话式档案）
    const p = action.archiveProduct
    if (!p) return
    const full = findQueryableProduct(p.code) || p
    openQueryArchive(full)
  }
  if (action.key?.startsWith('compare-remove-')) {
    // 比对面板：一键去掉某个产品（少于 2 个时收起比对面板）
    const code = action.key.replace('compare-remove-', '')
    const cur = comparePanel.value.items || []
    if (cur.length) {
      const next = cur.filter((i) => i.code !== code)
      if (next.length >= 2) comparePanel.value = { visible: true, items: next }
      else comparePanel.value.visible = false
    }
  }
  if (action.key === 'open-ops-view') {
    // 运营数据分析：一键打开右侧产品运营视图看板
    if ((activeAssistant.value || 'rd') === 'ops') onOpenOps()
  }
}

// 打开存量产品只读档案工作台后，同步一条助手说明回复
async function sendStockDetailReply(product) {
  const p = product.snapshot?.productInfo || {}
  const values = product.snapshot?.values || {}
  await streamAssistantReply(
    `已为您打开存量产品 **${product.name}**（${product.code}）的只读配置档案。\n\n- 业务模板：${product.templateLabel || '-'}\n- 月租：${values.monthlyFee ? `${values.monthlyFee}元` : '-'} · 流量：${values.flowAmount || '-'}${values.flowUnit || ''} · 语音：${values.voiceAmount || '-'}分钟\n- 当前状态：${p.status || '-'}\n\n当前为**只读模式**。若需基于该产品复制生成新套餐，请点击档案工作台内的 **「复制」** 按钮，AI 将复制其配置并转为可编辑的新产品。`,
    [
      `接收查看存量产品 ${product.name} 配置详情的请求…`,
      '读取存量产品配置档案…',
      '打开只读配置工作台',
      '生成档案说明回复',
    ]
  )
}
async function onStartFiling() {
  if (streaming.value) return
  const workOrderId = launchProductConfig.productInfo.workOrderId
  const productName = launchProductConfig.productInfo.prodPrcName
  // 推进资费流程到备案公示阶段（备案公示 active，等待提交）
  workflowStore.advanceTo('filing')
  filing.started = true
  launchProductConfig.productInfo.status = 'filing'
  // 打开配置工作台并切换到备案公示页签
  configPanel.value = { visible: true }
  configPanelHasOpened.value = true
  reopenDetailAfterTick(() => {
    if (configPanelRef.value && typeof configPanelRef.value.showFiling === 'function') {
      configPanelRef.value.showFiling()
    }
  })
  await streamAssistantReply(
    `已为您**发起备案公示**，资费方案 **${productName}**（工单 ${workOrderId}）已进入备案公示阶段。\n\n您可在**配置工作台 → 备案公示**页签查看备案信息与公示预览，确认无误后点击**提交备案**。`,
    [
      '接收"发起备案公示"请求…',
      `校验方案 ${productName} 已完成测试审核…`,
      '生成备案信息与公示预览，同步资费流程：测试审核 → 备案公示',
      '打开配置工作台备案公示页签',
      '生成备案公示发起回复',
    ]
  )
}

async function onSubmitFiling() {
  if (streaming.value) return
  const workOrderId = launchProductConfig.productInfo.workOrderId
  const productName = launchProductConfig.productInfo.prodPrcName
  // 提交备案：标记已提交，启动备案通过模拟
  filing.submitted = true
  launchProductConfig.productInfo.status = 'filing'
  filingStore.startSimulate()
  await streamAssistantReply(
    `**备案已提交**，资费方案 **${productName}**（工单 ${workOrderId}）正在等待备案审核通过。\n\n备案通过后，您可在配置工作台选择**销售渠道**，完成渠道配置后即可上架销售。`,
    [
      '接收"提交备案"请求…',
      `校验 ${productName} 备案信息与公示预览…`,
      '提交备案并提交备案审核',
      '生成备案提交回复',
    ]
  )
}

// 备案通过（自动模拟）：备案公示 → 待选择销售渠道
watch(filingTriggerId, (n) => {
  if (n === 0) return
  // 场景隔离：当前对话若为研发助手对话式配置(rd.scene2)，不推进产品运营助手的备案/渠道流程，也不覆盖其工作台
  if (inDialogueScene.value) return
  const workOrderId = launchProductConfig.productInfo.workOrderId
  workflowStore.onFilingApproved(workOrderId)
  salesChannel.available = true
  // 初始化默认勾选的渠道（推荐）
  salesChannel.selected.splice(0, salesChannel.selected.length, ...salesChannel.defaultSelected)
  launchProductConfig.productInfo.status = 'channel'
  showFilingApprovedReply()
  // 打开配置工作台并切换到销售渠道页签
  configPanel.value = { visible: true }
  configPanelHasOpened.value = true
  reopenDetailAfterTick(() => {
    if (configPanelRef.value && typeof configPanelRef.value.showChannel === 'function') {
      configPanelRef.value.showChannel()
    }
  })
})

async function showFilingApprovedReply() {
  if (streaming.value) {
    await new Promise((resolve) => {
      const unwatch = watch(streaming, (val) => {
        if (!val) { unwatch(); resolve() }
      })
    })
  }
  const workOrderId = launchProductConfig.productInfo.workOrderId
  const productName = launchProductConfig.productInfo.prodPrcName
  startThinking()
  const thinkingSteps = [
    '接收备案通过信号…',
    `校验备案 ${filing.filingId} 与工单 ${workOrderId} 匹配性…`,
    '备案审核通过，更新资费流程状态：备案公示 → 待选择销售渠道',
    '同步配置工作台产品状态为"待选择销售渠道"',
    '打开配置工作台销售渠道页签',
    '生成备案通过回复',
  ]
  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    workOrderId,
    thinkingSteps,
    thinkingActive: true,
    followUpActions: [
      { key: 'view-channel', label: '选择销售渠道', text: '选择销售渠道' },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  const reply = [
    `**${productName}**（工单 ${workOrderId}）的备案已审核**通过**。`,
    '',
    '备案公示流程已完成，资费流程已更新为"待选择销售渠道"。',
    '',
    '请在**配置工作台 → 销售渠道**页签中选择该资费的销售渠道，完成渠道配置后即可上架销售。',
  ].join('\n')

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < reply.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = reply.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  if (!cancelled) {
    aiMsg.streamText = reply
    aiMsg.content = reply
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

async function onSubmitChannel(payload) {
  if (streaming.value) return
  const workOrderId = launchProductConfig.productInfo.workOrderId
  const productName = launchProductConfig.productInfo.prodPrcName
  // 记录面板回传的用户实际配置值
  if (payload?.values) lastConfigValues.value = payload.values
  // 提交销售渠道：流程推进到上架销售
  workflowStore.advanceTo('listing')
  salesChannel.submitted = true
  launchProductConfig.productInfo.status = 'listed'
  // 上架销售完成：归档到工单中心（多套餐统一管理），不再作为可编辑的配置工作台
  archiveCompletedWorkOrder(payload?.values)
  await streamAssistantReply(
    `**销售渠道配置已完成**，资费方案 **${productName}**（工单 ${workOrderId}）的渠道（${salesChannel.selected.join('、')}）已生效。\n\n资费流程已推进至**上架销售**阶段，方案已正式上架。`,
    [
      '接收"提交销售渠道"请求…',
      `校验 ${productName} 销售渠道配置…`,
      '提交销售渠道，同步资费流程：备案公示 → 上架销售',
      '生成渠道完成回复',
    ]
  )
}

function reopenDetailAfterTick(fn) {
  setTimeout(fn, 50)
}

// 通知下拉里点击工单 → 打开审批抽屉
const onOpenApproval = (id) => {
  approvalDrawer.value = { visible: true, id }
}

const onDrawerBackToChat = () => {
  // 抽屉关闭后无需额外操作，对话区已在背后
}

async function streamAssistantReply(text, thinkingSteps, options = {}) {
  if (streaming.value) return

  startThinking()
  const steps = thinkingSteps || [
    '分析用户请求…',
    '检索相关数据…',
    '生成回复内容',
  ]

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps: steps,
    thinkingActive: true,
    detailCard: options.detailCard || null,
    followUpActions: options.followUpActions || null,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(300)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < text.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = text.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = text
    aiMsg.content = text
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

async function streamOptimizeReply(userText) {
  startThinking()
  const thinkingSteps = [
    '解析用户请求：基于多维根因分析结论，生成优化决策方案',
    '调用产品研发助手，拉取产品配置规则与资费审批门槛…',
    '调用产品研发助手，测算新产品收益预测与成本核算…',
    '调用产品查询助手，校验互斥规则与竞品最新配置…',
    '生成方案A（新产品策划）：流量40GB + 2权益，追平竞品A',
    '测算量化收益：月净增收益 28.1~33.3万元',
    '评估方案B（产品下线）可行性：高退出成本，不推荐',
    '生成风险评估矩阵：3项低风险 + 3项中低风险',
    '输出决策建议报告',
  ]

  const replyText = buildOptimizeSummary()

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    detailCard: {
      title: '畅享59元套餐 · 优化决策方案（完整版）',
      content: buildOptimizeFullText(),
      blocks: buildOptimizeFullBlocks(),
    },
    followUpActions: [
      { key: 'launch-optimize', label: '启动产品优化（推荐）', text: '启动产品优化' },
      { key: 'offline', label: '启动产品下线', text: '启动产品下线' },
      { key: 'postpone', label: '暂不处理，3天后再次提醒', text: '暂不处理，3天后再次提醒' },
      { key: 'confirm', label: '我有问题需要确认', text: '我有问题需要确认' },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

async function streamLaunchOptimizeReply(userText) {
  startThinking()

  const replyText = buildLaunchSummary()

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps: launchThinkingSteps,
    thinkingActive: true,
    followUpActions: [
      { key: 'audit', label: '智能稽核并提交', text: '智能稽核并提交' },
      { key: 'adjust', label: '调整配置字段', text: '调整配置字段' },
      { key: 'back', label: '返回优化决策', text: '返回优化决策' },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(500)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null

  if (!cancelled) {
    detailPanel.value = { visible: false, title: '', content: '' }
    // 方案已确认，初始化资费流程（design 完成，config 进行中）
    workflowStore.initWorkflow({
      workOrderId: launchProductConfig.productInfo.workOrderId,
      productName: launchProductConfig.productInfo.prodPrcName,
      templateLabel: launchProductConfig.productInfo.templateLabel,
    })
    await sleep(300)
    configPanelOpenSeq.value += 1
    configPanel.value = { visible: true }
    configPanelHasOpened.value = true
  }
}

// ========== 资费配置·对话式配置 ==========
// 从用户资费需求文本中解析出配置字段（仅解析能识别的字段，其余交由"一键智填"补全）
function parseTariffRequest(text) {
  const parsed = {}

  // 套餐名称：仅当用户明确给出产品名（含"版/卡"等命名特征或带引号）时才提取，
  // 避免把"配置一档校园套餐"中的套餐类别误当作产品名称
  const nameMatch = text.match(/["“']([^"”'，。,.!?；;：]{2,12}?)["”']/) ||
                    text.match(/([^\s，。,.!?；;：:\d]{2,10}?套餐?青春版)/) ||
                    text.match(/([^\s，。,.!?；;：:\d]{2,10}?青春卡)/) ||
                    text.match(/([^\s，。,.!?；;：:\d]{2,10}?学生卡)/)
  if (nameMatch) parsed.prodName = nameMatch[1]

  // 月费：xx元/月 或 月费xx元
  let m = text.match(/月(?:费|费是|租)?[\s是]*(\d+(?:\.\d+)?)\s*元\/月/)
  if (!m) m = text.match(/月费[\s是]*(\d+(?:\.\d+)?)\s*元/)
  if (!m) m = text.match(/(\d+(?:\.\d+)?)\s*元\/月/)
  if (m) parsed.monthlyFee = m[1]

  // 通用流量
  m = text.match(/通用流量\s*(\d+(?:\.\d+)?)\s*(GB|MB|G|M)/i)
  if (m) {
    parsed.flowType = 'general'
    parsed.flowAmount = m[1]
    parsed.flowUnit = (m[2] === 'G' ? 'GB' : m[2] === 'M' ? 'MB' : m[2].toUpperCase())
  }

  // 流量单位默认（若只写了"xxG/xxMB"而未带"通用"）
  if (!parsed.flowUnit) {
    m = text.match(/流量?\s*是?\s*(\d+(?:\.\d+)?)\s*(GB|MB|G|M)/i)
    if (m) {
      parsed.flowType = 'general'
      parsed.flowAmount = m[1]
      parsed.flowUnit = (m[2] === 'G' ? 'GB' : m[2] === 'M' ? 'MB' : m[2].toUpperCase())
    }
  }

  // 本地语音
  m = text.match(/本地语音\s*(\d+(?:\.\d+)?)\s*分钟/)
  if (m) {
    parsed.voiceType = 'local'
    parsed.voiceAmount = m[1]
  } else {
    m = text.match(/语音?\s*是?\s*(\d+(?:\.\d+)?)\s*分钟/)
    if (m) {
      parsed.voiceType = 'local'
      parsed.voiceAmount = m[1]
    }
  }

  // 短信
  m = text.match(/短信\s*(\d+(?:\.\d+)?)\s*条/)
  if (m) parsed.smsAmount = m[1]

  // ===== 套外/超套计费规则（按单位区分流量/语音/短信） =====
  // 流量超套："超套后1元/GB" "超出后流量0.02元/MB" "超套0.29元/MB"
  m = text.match(/(?:超套|超出|套外)[^。；;]{0,12}?(\d+(?:\.\d+)?)\s*元\s*(?:\/)?\s*\d*\s*(GB|MB|G|M)/i)
  if (m) parsed.flowOvercharge = `${m[1]}元/${(m[2] === 'G' ? 'GB' : m[2] === 'M' ? 'MB' : m[2].toUpperCase())}`
  // 语音超套："语音0.15元/分钟" "超出后0.15元/分钟"
  m = text.match(/(?:超套|超出|套外|语音)[^。；;]{0,12}?(\d+(?:\.\d+)?)\s*元\s*(?:\/)?\s*\d*\s*分钟/)
  if (m) parsed.voiceOvercharge = `${m[1]}元/分钟`
  // 短信超套："短信0.1元/条" "超套0.1元/条"
  m = text.match(/(?:超套|超出|套外|短信)[^。；;]{0,12}?(\d+(?:\.\d+)?)\s*元\s*(?:\/)?\s*\d*\s*条/)
  if (m) parsed.smsOvercharge = `${m[1]}元/条`

  // ===== 合约期 / 有效期 =====
  m = text.match(/合约期\s*(\d{2})\s*个月/)
  if (m) parsed.validity = `${m[1]}m`
  else if (/长期有效|长期(?:期)?有效/.test(text)) parsed.validity = 'longTerm'
  // 续订方式
  if (/自动续订/.test(text)) parsed.renewMethod = 'auto'
  else if (/手动续订/.test(text)) parsed.renewMethod = 'manual'

  // ===== 限办张数 → 同时订购次数 =====
  m = text.match(/(?:限办|限购|仅限|最多)\s*(\d+)\s*[张个]/)
  if (m) parsed.orderTimes = m[1]

  // ===== 前项限制（认证/办理条款，按文本命中情况聚合多条规则） =====
  const preLimitRows = []
  if (/面向在校大学生|仅限在校大学生|在校大学生开放|学生/.test(text)) {
    preLimitRows.push({
      label: 'targetGroup',
      ruleType: 'eq',
      limitValue: '在校大学生',
      limitType: 'allow',
      tipMsg: '目标客群为在校大学生，允许办理',
    })
  }
  if (/学生证.*认证|上传学生证|学历认证/.test(text)) {
    preLimitRows.push({
      label: 'studentCert',
      ruleType: 'eq',
      limitValue: '需上传学生证认证',
      limitType: 'allow',
      tipMsg: '面向在校大学生，需学生证认证后办理',
    })
  }
  if (/本人实名|仅限本人|实名办理/.test(text)) {
    preLimitRows.push({
      label: 'realName',
      ruleType: 'eq',
      limitValue: '仅限本人实名',
      limitType: 'allow',
      tipMsg: '仅限本人实名办理',
    })
  }
  if (preLimitRows.length) parsed.preLimitTable = preLimitRows

  return parsed
}

// ========== 资费配置·存量产品复制配置 ==========
// 用户在检索槽位（产品编码/产品名称）填写条件并发送后，AI 从存量产品库检索符合条件的产品，
// 返回类似产品列表；每个产品提供"查看详情"，点击后打开该产品的只读配置档案（可复制）
async function streamStockCopyReply(userText, search = null) {
  startThinking()
  const codeKw = (search?.code || '').trim()
  const nameKw = (search?.name || '').trim()
  // 未从槽位带出时，尝试从自由文本解析检索条件
  const textCode = !codeKw ? (userText.match(/SP[\w-]*/i)?.[0] || '') : codeKw
  const textName = !nameKw ? (userText.match(/[“"']([^“”"']{2,20})[”"']/)?.[1] || '') : nameKw
  const results = searchStockProducts({ code: textCode, name: textName })

  const condDesc =
    [codeKw ? `产品编码：${codeKw}` : '', nameKw ? `产品名称：${nameKw}` : ''].filter(Boolean).join('，') ||
    '（未限定，展示全部存量产品）'

  const thinkingSteps = [
    `接收「存量产品复制配置」检索请求："${userText}"`,
    codeKw || nameKw ? `解析检索条件：${[codeKw ? `编码=${codeKw}` : '', nameKw ? `名称=${nameKw}` : ''].filter(Boolean).join('，')}` : '未限定条件，检索全部存量产品',
    `在存量产品库中匹配，共命中 ${results.length} 个产品`,
    '整理产品概要并为每个产品生成「查看详情」入口',
  ]

  const rows = results.length
    ? results
        .map((p, i) => `| ${i + 1} | ${p.code} | ${p.name} | ${p.summary} | [查看详情](#view-${i}) |`)
        .join('\n')
    : '| （无匹配） | - | - | - | - |'

  const replyText = [
    '### 存量产品复制配置 · 检索结果',
    '',
    `> 已按检索条件（${condDesc}）在存量产品库中检索，共匹配 **${results.length}** 个类似产品。`,
    '',
    '**检索到的存量产品**',
    '| # | 产品编码 | 产品名称 | 资费概要 | 操作 |',
    '| ---- | ---- | ---- | ---- | ---- |',
    rows,
    '',
    results.length
      ? '**下一步**\n- 点击下方某产品的 **「查看详情」**，可打开该产品的只读配置档案并查看完整配置。\n- 如需基于某存量产品复制新套餐，请在档案工作台内点击 **「复制」**，即可在其基础上修改生成新产品。'
      : '**未找到匹配产品**\n- 请调整产品编码或产品名称后重新检索。',
  ].join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    stockResults: results,
    followUpActions: results.map((p) => ({
      key: `view-stock-detail-${p.code}`,
      label: `查看详情 · ${p.name}`,
      productCode: p.code,
      text: `查看 ${p.name}（${p.code}）的配置详情`,
    })),
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(500)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

// ========== 资费配置·文件导入批量配置 ==========
// 解析附件（如《暑期促销活动方案.docx》）中的多个产品，写入「我的草稿」，并打开第一个草稿工作台
async function streamBatchImportReply(userText, attachments) {
  startThinking()
  const fileName = attachments?.[0]?.name || '配置文件'

  // 写入草稿 store（幂等：同工单不重复）。工单号按「当天对话日期」生成（WO+YYYYMMDD+当日序号），
  // 覆盖配置文件里预留的固定编号，与对话式配置的工单号生成规则保持一致。
  const dailyIds = buildDailyBatchWorkOrderIds(batchDraftProducts.length)
  const products = batchDraftProducts.map((p, i) => ({
    ...p,
    workOrderId: dailyIds[i] || p.workOrderId,
    source: 'batch',
  }))
  const added = draftStore.batchImport(products)

  const thinkingSteps = [
    '接收「文件导入批量配置」请求…',
    `解析附件《${fileName}》：识别校园促销活动方案…`,
    '逐条抽取产品结构化要素：月费 / 流量 / 语音 / 短信 / 限定条件…',
    `共解析出 ${added.length} 个产品，写入「我的草稿」`,
    '生成产品目录与配置工作台入口…',
    '自动打开第一个草稿产品的工作台',
  ]

  const rows = added
    .map((p, i) => `| ${i + 1} | ${p.prodName} | ${p.workOrderId} | ${p.summary || '—'} | 草稿 |`)
    .join('\n')

  const replyText = [
    '### 文件导入批量配置 · 解析结果',
    '',
    `> 已解析附件《${fileName}》，共识别出 **${added.length}** 个待配置产品，均已生成到「我的草稿」，并已打开第一个产品的工作台。`,
    '',
    '**解析产品目录**',
    '| # | 产品 | 工单编号 | 资费概要 | 状态 |',
    '| ---- | ---- | ---- | ---- | ---- |',
    rows,
    '',
    '**下一步**',
    '- 在右侧工作台逐个完善资费配置，点击「上一个 / 下一个」可快速切换草稿产品',
    '- 也可点击右上角 **「我的草稿」** 查看全部草稿，随时进入任一产品的工作台',
    '- 配置完成后点击「提交配置」，提交一个后会自动跳转到下一个草稿产品',
  ].join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    followUpActions: [
      { key: 'open-drafts', label: '查看我的草稿', text: '打开我的草稿' },
    ],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(500)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null

  if (!cancelled && draftStore.draftList.length > 0) {
    // 打开第一个草稿产品的工作台
    openDraftWorkbench(draftStore.draftList[0])
  }
}

// 对话式配置·字段增量指令解析：面板已打开时，解析用户追加的"产品叫xxx/月费改xxx/流量改60GB"等指令，
// 返回 { key, value, label }（或含多个资源字段的 {key, value} 列表）。无法识别时返回 null。
function parseFieldUpdate(text) {
  const fields = []
  // 产品名称
  const nameMatch = text.match(/(?:产品(?:名(?:称)?)?|名称)(?:叫|名为|改(?:成|为)|调(?:整)?(?:成|为)|更新(?:成|为)|是)[:：]?\s*([^\s，。,.!?？；;:]{2,20})/)
  if (nameMatch) fields.push({ key: 'prodName', value: nameMatch[1].trim(), label: '产品名称' })
  // 月费/月租：仅"改/调/变更"等明确变更指令才命中（普通"月费是99元"不当作增量更新）
  let m = text.match(/(?:月费|月租)(?:改(?:成|为)|调(?:整)?(?:成|为)|更新(?:成|为)|变(?:成|更))?(?:是|为)?[:：]?\s*(\d+(?:\.\d+)?)\s*元/)
  if (m) fields.push({ key: 'monthlyFee', value: m[1], label: '套餐固定费(元)' })
  // 通用流量（含单位）
  m = text.match(/(?:流量)(?:改(?:成|为)|调(?:整)?(?:成|为)|更新(?:成|为)|变(?:成|更)|增(?:加|至)|增(?:至|到))?(?:是|为)?[:：]?\s*(\d+(?:\.\d+)?)\s*(GB|MB|G|M)/i)
  if (m) {
    fields.push({ key: 'flowAmount', value: m[1], label: '流量资源量' })
    fields.push({ key: 'flowUnit', value: (m[2] === 'G' ? 'GB' : m[2] === 'M' ? 'MB' : m[2].toUpperCase()), label: '流量单位' })
  }
  // 本地语音
  m = text.match(/(?:语音)(?:改(?:成|为)|调(?:整)?(?:成|为)|更新(?:成|为)|变(?:成|更)|增(?:加|至))?(?:是|为)?[:：]?\s*(\d+(?:\.\d+)?)\s*分钟/)
  if (m) fields.push({ key: 'voiceAmount', value: m[1], label: '语音资源量(分钟)' })
  // 短信
  m = text.match(/(?:短信)(?:改(?:成|为)|调(?:整)?(?:成|为)|更新(?:成|为)|变(?:成|更)|增(?:加|至))?(?:是|为)?[:：]?\s*(\d+(?:\.\d+)?)\s*条/)
  if (m) fields.push({ key: 'smsAmount', value: m[1], label: '短信资源量(条)' })

  // 避免增量解析与初次资费需求混淆：初次需求（"月费是99元"）在面板未打开前走 streamTariffConfigReply；
  // 此处仅当命中上述明确的"改/增/调"指令或"产品叫/名称"命名意图时才返回，避免把普通需求误判为更新。
  const isExplicitUpdate = /(改(?:成|为)?|调到?|调整为?|更新(?:成|为)?|名称|产品名|叫)/.test(text)
  return (fields.length > 0 && isExplicitUpdate) ? fields : null
}

// 对话式配置·增量字段更新回复：解析成功后更新已打开面板的字段，并流式回复解析确认。
async function streamFieldUpdateReply(userText, fieldUpdates) {
  startThinking()

  const thinkingSteps = [
    '识别对话式配置工作台处于打开状态',
    `解析增量指令："${userText}"`,
    `提取待更新字段：${fieldUpdates.map((f) => f.label).join('、')}`,
    '实时写入配置工作台对应字段并标注"已解析"',
  ]

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(400)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  // 写入面板对应字段（经由 configPanelRef 调用组件方法实时更新 formData）
  if (!cancelled && configPanelRef.value && typeof configPanelRef.value.applyFieldUpdate === 'function') {
    fieldUpdates.forEach((f) => {
      configPanelRef.value.applyFieldUpdate(f.key, f.value)
      // 同步 parsedFields 快照，保证产品名/流程等联动一致
      if (configPanel.value.parsedFields) configPanel.value.parsedFields[f.key] = f.value
      // 产品名称变更：同步顶部流程 stepper / 草稿 / 审批等展示名称
      if (f.key === 'prodName' && workflowStore.workflow) workflowStore.workflow.productName = f.value
      // 产品名称变更：同步「我的草稿」与「我的产品」中该工单的名称，保证跨入口一致
      if (f.key === 'prodName' && configPanel.value.workOrderId) {
        const d = draftStore.drafts.find((x) => x.workOrderId === configPanel.value.workOrderId)
        if (d) d.prodName = f.value
        const wo = workOrderStore.getByWorkOrderId(configPanel.value.workOrderId)
        if (wo) wo.productName = f.value
      }
    })
  }

  const rows = fieldUpdates
    .map((f) => `| ${f.label} | \`${f.value}\` |`)
    .join('\n')

  const replyText = [
    '### 对话式配置 · 字段已更新',
    '',
    '> 已根据您的指令「' + userText + '」实时更新配置工作台中的以下字段：',
    '',
    '| 字段 | 更新值 |',
    '| ---- | ---- |',
    rows,
    '',
    '配置工作台已同步，请继续核对或补充其他内容。',
  ].join('\n')

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(22)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

// ========== 产品查询助手（query.*）：智能问答 / 档案调阅 / 比对分析 ==========
// 判定文本是否像"资费配置需求"（用于查询意图的负向排除：配置需求不应被查询分支截胡）
function isTariffLikeText(text) {
  return /(月费|月租|固定费)/.test(text) && /(配置|配个|套餐)/.test(text) && /(流量|语音|短信|分钟|GB|MB)/.test(text)
}

// 跨场景串联：把研发助手中配置的草稿产品（对话式/批量/复制）归一化为可查询产品列表
function getQueryableDrafts() {
  return (draftStore.drafts || [])
    .filter((d) => d.status === 'draft' && d.prodName && d.prodName !== '未命名产品')
    .map((d) => ({
      name: d.prodName,
      code: d.workOrderId,
      from: 'draft',
      tags: d.summary ? [] : [],
      summary: d.summary || '',
      snapshot: null,
      parsedFields: d.parsedFields || {},
      parsedAt: d.parsedAt || 0,
    }))
}

// 按名称/编码在 存量+草稿 范围内查找产品（返回统一描述对象，未找到返回 null）
function findQueryableProduct(nameOrCode) {
  const kw = (nameOrCode || '').trim().toLowerCase()
  if (!kw) return null
  const stock = searchStockProducts({ name: nameOrCode })[0] || searchStockProducts({ code: nameOrCode })[0]
  if (stock) {
    return { name: stock.name, code: stock.code, from: 'stock', snapshot: stock.snapshot, parsedFields: null, summary: stock.summary, tags: stock.tags, templateLabel: stock.templateLabel }
  }
  const d = getQueryableDrafts().find(
    (p) => p.name.toLowerCase().includes(kw) || kw.includes(p.name.toLowerCase()) || p.code.toLowerCase() === kw
  )
  if (d) return d
  return null
}

// 查询意图统一分发：制度问答 → 知识库；产品问答 → 档案卡；档案调阅 → 只读档案面板；比对 → 比对面板
async function dispatchQueryIntent(text) {
  const t = text || ''
  // onSend 已把 streaming 置为 true；此处各分流函数会自行接管 streaming（含 guidance 用的 streamAssistantReply 带防重入判断），先复位以免 guidance 被静默跳过
  streaming.value = false
  // 上下文指代：文本中是"该产品/它/这个产品"等代词且无具体产品名时，继承最近一次问答的产品
  const resolveProduct = () => {
    const hit = extractProductFromText(t, getQueryableDrafts())
    if (hit) return hit
    if (PRONOUN_PAT.test(t) && lastQueryProduct.value) {
      const full = findQueryableProduct(lastQueryProduct.value.code)
      if (full) return { name: full.name, code: full.code, from: full.from }
    }
    return null
  }
  // 0.5) 比对编辑：当比对面板已打开且已有 ≥2 个产品时，支持"换其中1个/叠加/去掉"产品
  if (comparePanel.value.visible && (comparePanel.value.items?.length || 0) >= 2) {
    const editAction = classifyCompareEdit(t)
    if (editAction) {
      await streamCompareEditReply(t)
      return
    }
  }
  // 1) 比对意图：明确"对比/比较"且能识别出至少 2 个产品
  if (/(对比|比较|相比|比对)/.test(t)) {
    const extra = getQueryableDrafts()
    let hits = extractCompareProducts(t, extra)
    // 代词补位：如"对比它和畅享59元套餐"，把上下文产品补入比对列表
    if (hits.length === 1 && PRONOUN_PAT.test(t) && lastQueryProduct.value) {
      const ctx = findQueryableProduct(lastQueryProduct.value.code)
      if (ctx && !hits.some((h) => h.code === ctx.code)) {
        hits = [{ name: ctx.name, code: ctx.code, from: ctx.from }, ...hits]
      }
    }
    if (hits.length >= 2) {
      await streamQueryCompareReply(t, hits)
      return
    }
    if (hits.length === 1) {
      // 只识别出 1 个：提示补一个产品
      await streamAssistantReply(
        `我识别到待比对产品 **${hits[0].name}**，还需要 1 个产品才能生成比对视图。\n\n可参考：\n- 存量产品：畅享59元套餐、动感地带59元套餐、畅享校园卡39元套餐、智享99元5G套餐\n- 或你在研发助手中配置的草稿产品\n\n请补充另一个产品名称，我将立即生成横向比对。`,
        [
          `解析比对请求："${t}"`,
          `已识别 1 个产品：${hits[0].name}`,
          '比对至少需要 2 个产品，向用户补充询问',
        ]
      )
      return
    }
  }
  // 2) 档案调阅意图：查一下/调阅 + 产品名/编码
  const isArchiveIntent = /(调阅|档案|详情|基础信息|基础资料|配置档案)/.test(t) ||
    (/^(查一下|查询|查看)/.test(t) && /(配置|报告|测试|备案|渠道)/.test(t))
  if (isArchiveIntent) {
    const product = resolveProduct()
    if (product) {
      const full = findQueryableProduct(product.code)
      if (full) {
        await openQueryArchive(full)
        return
      }
    }
    // 想调阅但没指明产品：若指"我最近/我的草稿/我配置的"，优先列出用户自己在配草稿
    const drafts = getQueryableDrafts()
    const draftRows = drafts.map((d) => `- ${d.name}（${d.code}，在配草稿）`).join('\n')
    if (/(最近配置|我配置的|我的草稿|我最近|我自己的)/.test(t) || (/我/.test(t) && /配置/.test(t))) {
      await streamAssistantReply(
        drafts.length
          ? [
              '你最近在研发助手配置的套餐（在配草稿）如下：',
              '',
              draftRows,
              '',
              '可直接回复草稿名称，我来帮你调阅配置档案或继续编辑。',
            ].join('\n')
          : [
              '你目前还没有已保存的草稿套餐。',
              '',
              '可先在研发助手中配置一个资费套餐（月费/流量/语音/短信等），或在下方输入任意存量套餐名称查询：',
              '',
              '**存量产品**',
              '- 畅享59元套餐（SP2024059001）',
              '- 动感地带59元套餐（SP2024061203）',
              '- 畅享校园卡39元套餐（SP2024070815）',
              '- 智享99元5G套餐（SP2024082002）',
            ].join('\n'),
        [`解析档案调阅请求："${t}"`, /(最近配置|我配置的|我的草稿)/.test(t) ? '识别为用户自己的草稿套餐，列出在配草稿清单' : '未识别到具体产品，列出可查产品清单']
      )
      return
    }
    await streamAssistantReply(
      [
        '请告诉我要调阅的产品名称或编码，当前可查：',
        '',
        '**存量产品**',
        '- 畅享59元套餐（SP2024059001）',
        '- 动感地带59元套餐（SP2024061203）',
        '- 畅享校园卡39元套餐（SP2024070815）',
        '- 智享99元5G套餐（SP2024082002）',
        drafts.length ? '\n**在配草稿（研发助手配置）**\n' + draftRows : '',
      ].join('\n'),
      [`解析档案调阅请求："${t}"`, '未识别到具体产品，列出可查产品清单']
    )
    return
  }
  // 2.5) 产品档案子项问答：测试报告结论/备案公示/销售渠道等针对档案具体子项的提问
  const subItem = classifyArchiveSubItem(t)
  if (subItem) {
    const product = resolveProduct()
    if (product) {
      await streamProductDetailReply(t, product)
      return
    }
  }
  // 3) 制度问答（RAG 风格带引用来源）
  const qType = classifyQuestion(t)
  if (qType === 'policy') {
    await streamKnowledgeReply(t)
    return
  }
  // 4) 产品要素问答（档案卡摘要回复）
  if (qType === 'profile' || qType === 'status') {
    const product = resolveProduct()
    if (product) {
      await streamProductQaReply(t, product)
      return
    }
  }
  // 5) 兜底：通用问答（尝试知识库，无命中则通用提示）
  const kbHits = searchKnowledge(t)
  if (kbHits.length) {
    await streamKnowledgeReply(t)
    return
  }
  await streamAssistantReply(
    `暂未从问题中识别出可查询的产品或制度条目。\n\n**你可以这样问**\n- 智享99元5G套餐包含多少流量？\n- 备案公示的时限是怎么规定的？\n- 调阅"动感地带59元套餐"的配置档案`,
    [`解析查询问题："${t}"`, '未命中产品库与知识库，返回引导提示']
  )
}

// 判别用户是否在针对"产品档案子项"（测试报告/备案公示/销售渠道）提问，返回子项类型或 null
function classifyArchiveSubItem(text) {
  const t = text || ''
  const testPat = /(测试报告|测试结论|测试(了|的)?结果|测试是否通过|测试通过)/i
  const filingPat = /(备案公示|备案(是否|已)?(通过|完成)|备案状态|是否已备案|公示(完成|通过)?)/i
  const channelPat = /(销售渠道|上架渠道|(哪些|什么|哪里)(渠道|地方|途径)|渠道(有哪些|是否开通)|在哪.*(买|购买|订购)|怎么.*(订购|购买))/i
  // 测试报告/备案/渠道子项关键词，须同时具备"具体档案内容"语义，避免与要素问答冲突
  if (testPat.test(t)) return 'test'
  if (filingPat.test(t)) return 'filing'
  if (channelPat.test(t)) return 'channel'
  return null
}

// 产品档案子项问答：从产品自身 snapshot 的测试报告/备案公示/销售渠道中取数回答
async function streamProductDetailReply(question, product) {
  startThinking()
  const full = findQueryableProduct(product.code)
  lastQueryProduct.value = { name: (full || product).name, code: (full || product).code, from: (full || product).from }
  const isDraft = full?.from === 'draft'
  const snap = full?.snapshot || {}
  const subItem = classifyArchiveSubItem(question) || 'test'

  const thinkingSteps = [
    `解析问题："${question}"`,
    `识别产品：${product.name}（${isDraft ? '在配草稿' : '存量产品'}）`,
    isDraft ? '产品尚未进入测试/备案阶段，无法读取档案子项' : `读取「${subItem === 'test' ? '测试报告' : subItem === 'filing' ? '备案公示' : '销售渠道'}」档案数据…`,
    '生成要点式回答',
  ]

  const chLabels = { test: '测试报告', filing: '备案公示', channel: '销售渠道' }
  let replyText

  if (isDraft) {
    replyText = [
      `### ${product.name} · ${chLabels[subItem]}`,
      '',
      `> 产品「${product.name}」（${product.code}）目前在配草稿阶段（未上架），**尚未进入${subItem === 'test' ? '测试' : subItem === 'filing' ? '备案公示' : '渠道配置'}环节**，因此暂无对应档案数据。`,
      '',
      '可先在研发助手中完成资费配置与草稿提交，待其进入测试、备案并上架后，此处的测试报告 / 备案公示 / 销售渠道信息即可调阅。',
    ].filter(Boolean).join('\n')
  } else {
    if (subItem === 'test') {
      const tr = snap.testReport || {}
      const sectionRows = (tr.sections || []).map((s) => `- **${s.title}**：${s.status === 'passed' ? '通过' : (s.status === 'failed' ? '未通过' : s.status)}${(s.items || []).map((it) => `（${it.label}：实际 ${it.actual}）`).join('')}`).join('\n')
      replyText = [
        `### ${product.name} · 测试报告`,
        '',
        `> 已从「${product.name}」（${product.code}）档案中读取历史测试报告（编号 ${tr.reportId || '--'}）。`,
        '',
        `**测试结论**：**${tr.conclusion || tr.status || (tr.status === 'passed' ? '通过' : '--')}**`,
        '',
        `**结论说明**：${tr.conclusionDesc || (tr.status === 'passed' ? '各项核查均符合预期，测试通过，已进入上架销售阶段。' : '--')}`,
        '',
        `- 测试人：${tr.tester || '--'} · 测试日期：${tr.testDate || '--'}`,
        `- 测试方法：${tr.testMethod || '--'}`,
        `- 验收单号：${tr.acceptNumber || '--'}`,
        '',
        '**核查明细**',
        (sectionRows || '- 无核查明细'),
      ].join('\n')
    } else if (subItem === 'filing') {
      const fl = snap.filing || {}
      const info = fl.info || {}
      const checkRows = (fl.checks || []).map((c) => `- **${c.label}**：${c.status === 'passed' ? '通过' : c.status}（${c.desc || ''}）`).join('\n')
      replyText = [
        `### ${product.name} · 备案公示`,
        '',
        `> 已从「${product.name}」（${product.code}）档案中读取备案公示记录（编号 ${fl.filingId || '--'}）。`,
        '',
        `**备案状态**：**${fl.approved ? '已备案通过' : (fl.submitted ? '已提交待审' : '未提交')}**`,
        '',
        `- 备案机构：${info.filingOrg || '--'} · 备案人：${info.filingOfficer || '--'}`,
        `- 备案日期：${info.filingDate || fl.applyDate || '--'}`,
        `- 生效日期：${info.effectiveDate || '--'} · 有效期：${info.expireDate || '--'}`,
        '',
        '**合规审查**',
        (checkRows || '- 无审查记录'),
      ].join('\n')
    } else {
      const ch = snap.channel || {}
      const labels = (ch.list || []).filter((c) => (ch.selected || []).includes(c.key)).map((c) => `- **${c.label}**：${c.desc || ''}`)
      replyText = [
        `### ${product.name} · 销售渠道`,
        '',
        `> 已从「${product.name}」（${product.code}）档案中读取销售渠道配置。`,
        '',
        `**渠道状态**：**${ch.available ? '已开通' : '未开通'}**`,
        '',
        '**已开通渠道**',
        (labels.length ? labels.join('\n') : '- 暂无开通渠道'),
      ].join('\n')
    }
  }

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    // 快捷回复：一键调阅完整配置档案
    followUpActions: isDraft
      ? [{ key: 'open-draft-archive-' + encodeURIComponent(full.code), label: '调阅该产品的配置档案', text: `调阅「${full.name}」的配置档案`, archiveProduct: { name: full.name, code: full.code, from: 'draft', parsedFields: full.parsedFields, snapshot: null, summary: full.summary } }]
      : [{ key: 'open-query-archive-' + encodeURIComponent(full.code), label: '调阅该产品的完整配置档案', text: `调阅「${full.name}」的配置档案`, archiveProduct: { name: full.name, code: full.code, from: 'stock', snapshot: full.snapshot, parsedFields: null, summary: full.summary, templateLabel: full.templateLabel } }],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(420)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

// 制度知识问答：RAG 风格，回答附引用来源与条文摘录
async function streamKnowledgeReply(question) {
  startThinking()
  const hits = searchKnowledge(question)
  const top = hits[0] || null

  const thinkingSteps = top
    ? [
        `解析问题："${question}"`,
        `在制度知识库（${knowledgeCount} 篇文档）中检索…`,
        `命中文档：《${top.title}》（相关度 ${Math.min(99, 60 + top.score * 8)}%）`,
        '摘录相关条款并生成带引用来源的回答',
      ]
    : [
        `解析问题："${question}"`,
        `在制度知识库（${knowledgeCount} 篇文档）中检索…`,
        '未命中相关文档',
      ]

  const replyText = top
    ? [
        `### 制度问答 · ${top.title}`,
        '',
        `> **${top.summary}**`,
        '',
        '**相关条款**',
        ...top.detail.map((d) => `- ${d}`),
        '',
        '---',
        `📎 引用来源：《${top.title}》 · ${top.source} · 更新于 ${top.updatedAt}（文档编号 ${top.id}）`,
      ].join('\n')
    : [
        '### 制度问答',
        '',
        `未在知识库中找到与「${question}」直接相关的条文。当前知识库覆盖：`,
        '',
        '- 资费产品备案公示管理规范',
        '- 产品命名与编码规则',
        '- 资费产品测试审核工作指引',
        '- 产品上下线流程与生命周期管理',
        '- 校园市场资费产品专项管理要求',
      ].join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(420)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

const knowledgeCount = knowledgeBase.length

// 产品要素问答：返回迷你档案卡摘要（月费/流量/语音/状态等）
async function streamProductQaReply(question, product) {
  startThinking()
  const full = findQueryableProduct(product.code)
  // 记录上下文：后续"该产品/它/调阅该产品的配置档案"等代词追问可继承
  lastQueryProduct.value = { name: (full || product).name, code: (full || product).code, from: (full || product).from }
  const v = full ? (full.parsedFields || full.snapshot?.values || {}) : {}
  const isDraft = full?.from === 'draft'
  const statusText = isDraft ? '在配草稿（未上架）' : (full?.snapshot?.productInfo?.status === 'listed' ? '销售中' : '已上架')

  const thinkingSteps = [
    `解析问题："${question}"`,
    `识别产品：${product.name}（${isDraft ? '在配草稿' : '存量产品'}）`,
    '读取产品档案中的资费要素…',
    '生成要点式回答',
  ]

  const rows = [
    ['月费', v.monthlyFee ? `${v.monthlyFee}元/月` : '--'],
    ['通用流量', v.flowAmount ? `${v.flowAmount}${v.flowUnit || 'GB'}` : '--'],
    ['语音', v.voiceAmount ? `${v.voiceAmount}分钟` : '--'],
    ['短信', v.smsAmount ? `${v.smsAmount}条` : '--'],
    ['流量套外', v.flowOvercharge || '--'],
    ['目标客群', (v.preLimitTable || []).find((r) => r.label === 'targetGroup')?.limitValue || '公众客户'],
    ['当前状态', statusText],
  ]
    .map(([k, val]) => `| ${k} | ${val} |`)
    .join('\n')

  const replyText = [
    `### ${product.name} · 要点速览`,
    '',
    `> 已从${isDraft ? '在配草稿' : '存量产品档案'}中检索「${product.name}」（${product.code}）的要素信息：`,
    '',
    '| 要素 | 值 |',
    '| ---- | ---- |',
    rows,
    '',
    isDraft
      ? '该产品尚未上架，如需查看完整配置或继续编辑，可切换到研发助手在「我的草稿」中打开。'
      : '如需查看完整配置档案（含测试报告/备案公示/销售渠道），可点击下方按钮直接调阅。',
  ].join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    // 快捷回复：一键调阅该产品的配置档案（无需手动复制话术）
    followUpActions: isDraft
      ? [{ key: 'open-draft-archive-' + encodeURIComponent(full.code), label: '调阅该产品的配置档案', text: `调阅「${full.name}」的配置档案`, archiveProduct: { name: full.name, code: full.code, from: 'draft', parsedFields: full.parsedFields, snapshot: null, summary: full.summary } }]
      : [{ key: 'open-query-archive-' + encodeURIComponent(full.code), label: '调阅该产品的配置档案', text: `调阅「${full.name}」的配置档案`, archiveProduct: { name: full.name, code: full.code, from: 'stock', snapshot: full.snapshot, parsedFields: null, summary: full.summary, templateLabel: full.templateLabel } }],
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(420)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

// 档案调阅：打开只读配置档案面板（复用 ProductConfigPanel archiveSnapshot 模式）
async function openQueryArchive(product) {
  startThinking()
  const isDraft = product.from === 'draft'
  // 记录上下文：后续"该产品/它"等代词追问可继承
  lastQueryProduct.value = { name: product.name, code: product.code, from: product.from }
  const thinkingSteps = [
    `解析档案调阅请求：${product.name}（${product.code}）`,
    isDraft ? '定位研发助手在配草稿档案…' : '定位存量产品历史档案…',
    '加载档案五页签：基础信息 / 资源配置 / 测试报告 / 备案公示 / 销售渠道',
    '打开只读档案工作台',
  ]
  const replyText = [
    `### 档案调阅 · ${product.name}`,
    '',
    `> 已定位${isDraft ? '在配草稿' : '存量产品'}档案并打开只读工作台（编号 ${product.code}）。`,
    '',
    product.summary ? `**资费概要**：${product.summary}` : '',
    '',
    '**档案内容**',
    '- 基础信息：命名/编码/有效期/生效规则等',
    '- 资源配置：流量 / 语音 / 短信 / 权益',
    '- 测试报告：固费核查 / 计费核查 / 提醒核查 / 工单核查',
    '- 备案公示与销售渠道历史记录',
    '',
    '可在右侧页签中切换查看。',
  ].filter(Boolean).join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(420)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null

  if (!cancelled) {
    // 打开只读档案（存量走 snapshot；草稿以 parsedFields 呈现为对话式只读档案）
    configPanelOpenSeq.value += 1
    if (product.snapshot) {
      configPanel.value = {
        visible: true,
        parsedFields: null,
        workOrderId: '',
        draftId: '',
        schemaConfig: null,
        archiveSnapshot: product.snapshot,
        archiveWorkOrderId: product.code,
      }
      const pInfo = product.snapshot.productInfo || {}
      workflowStore.initWorkflow({
        workOrderId: pInfo.workOrderId || product.code,
        productName: pInfo.prodPrcName || product.name,
        templateLabel: pInfo.templateLabel || launchProductConfig.productInfo.templateLabel,
      })
      // 存量在架产品档案：全生命周期已完成（方案设计→…→上架销售全部 done），
      // 避免流程条停留在"产品配置中"让档案看起来像未完成的草稿
      workflowStore.advanceTo('listing')
      if (workflowStore.workflow) {
        workflowStore.workflow.workOrderId = pInfo.workOrderId || product.code
      }
    } else {
      // 草稿档案：以对话式配置面板呈现（只读演示按解析字段展示）
      configPanel.value = {
        visible: true,
        parsedFields: product.parsedFields || {},
        workOrderId: product.code,
        draftId: '',
        schemaConfig: /青春卡|校园卡|学生卡/.test(product.name) ? campusProductConfig : null,
        archiveSnapshot: null,
      }
      workflowStore.initWorkflow({
        workOrderId: product.code,
        productName: product.name,
        templateLabel: launchProductConfig.productInfo.templateLabel,
      })
    }
    configPanelHasOpened.value = true
  }
}

// 判别是否在对"已打开的比对面板"执行编辑：换产品 / 叠加产品 / 去掉产品
function classifyCompareEdit(text) {
  const t = text || ''
  if (/(换掉|换成|替换|更换|换出|换一下|换一个|换其中|替换成|把.+换|改为)/.test(t)) return 'replace'
  if (/(去掉|移除|删掉|剔除|不比较|不要|取消)/.test(t)) return 'remove'
  if (/(再|又)?(加|叠加|补充|加入|多比较|一起比|追加)/.test(t)) return 'add'
  return null
}

// 按文本出现位置匹配所有产品（存量全名 + 草稿），返回 [{name, code, from, pos}]
function matchProductsInText(text, extra) {
  const t = text || ''
  const all = [
    ...searchStockProducts({}).map((p) => ({ name: p.name, code: p.code, from: 'stock' })),
    ...(extra || []).map((p) => ({ name: p.name, code: p.code, from: p.from || 'draft' })),
  ].sort((a, b) => b.name.length - a.name.length)
  const seen = new Set()
  const out = []
  for (const p of all) {
    const idx = t.indexOf(p.name)
    if (idx >= 0 && !seen.has(p.code)) {
      seen.add(p.code)
      out.push({ name: p.name, code: p.code, from: p.from, pos: idx })
    }
  }
  return out.sort((a, b) => a.pos - b.pos)
}

// 生成单个产品的 compare item（复用比对面板的归一化结构）
function buildOneCompareItem(h) {
  const full = findQueryableProduct(h.code)
  if (!full) return null
  const detail = full.parsedFields
    ? { parsedFields: full.parsedFields, status: 'draft' }
    : { values: full.snapshot?.values || {}, status: full.snapshot?.productInfo?.status, tags: full.tags }
  return buildCompareItem(full.name, full.code, full.from, detail)
}

// 比对编辑：基于当前比对面板，执行"换掉其中1个 / 叠加1个 / 去掉1个"，并刷新比对面板
async function streamCompareEditReply(question) {
  startThinking()
  const t = question || ''
  const cur = comparePanel.value.items || []
  const extra = getQueryableDrafts()
  const matched = matchProductsInText(t, extra)
  const action = classifyCompareEdit(t) || 'replace'
  const stocks = searchStockProducts({})
  const draftNames = getQueryableDrafts().map((d) => d.name + '（' + d.code + '）')

  // 目标产品：文本中出现的产品（取位置靠后的作为"新加入"，位置靠前的作为"被换掉"）
  // "把智享99换成畅享59"：被换掉=智享99(前)，新加入=畅享59(后)；"换成畅享59"无前项则默认换掉最后1个
  let incoming = null
  let outgoing = null
  if (action === 'replace') {
    if (matched.length >= 2) {
      outgoing = matched[0]
      incoming = matched[matched.length - 1]
    } else if (matched.length === 1) {
      incoming = matched[0]
      outgoing = null // 未指明换掉谁，默认换最后1个
    }
  } else if (action === 'remove') {
    if (matched.length >= 1) outgoing = matched[0]
  } else if (action === 'add') {
    const hit = extractProductFromText(t, extra)
    if (hit) incoming = hit
  }

  const curNames = cur.map((i) => i.name + '（' + i.code + '）')
  const thinkingSteps = [
    `解析比对编辑请求："${t}"`,
    `当前比对面板：${curNames.join('、')}`,
    action === 'replace' ? (incoming ? `拟换入产品：${incoming.name}` : '需从文本识别要换入的产品') : action === 'remove' ? (outgoing ? `拟去掉产品：${outgoing.name}` : '需从文本识别要去掉的产品') : '拟叠加新的产品',
    '重建比对数据集合并刷新比对面板',
  ]

  let nextItems = [...cur]
  let replyText

  if (action === 'replace') {
    if (incoming && nextItems.some((i) => i.code === incoming.code)) {
      replyText = `**${incoming.name}** 已在当前比对列表中，无需重复添加。`
    } else if (incoming) {
      const target = nextItems.find((i) => outgoing ? i.code === outgoing.code : false)
      if (outgoing && !target) {
        replyText = `未在当前比对列表中找到 **${outgoing.name}**，可考虑：\n- 直接说「换成 ${incoming.name}」换掉最后1个产品\n- 或说「叠加 ${incoming.name}」加入第 ${nextItems.length + 1} 个产品。`
      } else {
        const idx = outgoing ? nextItems.findIndex((i) => i.code === outgoing.code) : nextItems.length - 1
        const replaced = nextItems[idx]
        const newItem = buildOneCompareItem(incoming)
        if (newItem) nextItems[idx] = newItem
        replyText = `已将 **${replaced ? replaced.name : '最后1个产品'}** 替换为 **${incoming.name}**（${incoming.code}），比对视图已刷新。`
      }
    } else {
      replyText = `请告诉我要换入哪个产品，例如：\n- 「把 ${cur[cur.length - 1]?.name || '某产品'} 换成畅享59元套餐」\n- 「换成 ${stocks[0]?.name}」\n\n当前可选的存量产品：${stocks.map((s) => s.name).join('、')}${draftNames.length ? '；草稿产品：' + draftNames.join('、') : ''}`
    }
  } else if (action === 'remove') {
    if (outgoing) {
      const idx = nextItems.findIndex((i) => i.code === outgoing.code)
      if (idx < 0) {
        replyText = `当前比对列表中不含 **${outgoing.name}**。当前产品：${curNames.join('、')}`
      } else {
        nextItems.splice(idx, 1)
        replyText = `已从比对中移除 **${outgoing.name}**。${nextItems.length < 2 ? '\n\n只剩 1 个产品，比对视图已收起。' : ''}`
      }
    } else {
      replyText = `请告诉我要去掉哪个产品，当前比对：${curNames.join('、')}`
    }
  } else {
    if (incoming) {
      if (nextItems.some((i) => i.code === incoming.code)) {
        replyText = `**${incoming.name}** 已在当前比对列表中。`
      } else {
        const newItem = buildOneCompareItem(incoming)
        if (newItem) {
          nextItems.push(newItem)
          replyText = `已叠加 **${incoming.name}**（${incoming.code}），现共比对 ${nextItems.length} 个产品。`
        } else {
          replyText = `未能定位产品 **${incoming.name}**，请确认名称或编码。`
        }
      }
    } else {
      replyText = `请告诉我要叠加哪个产品，例如「叠加 ${stocks[0]?.name}」。\n\n当前可选的存量产品：${stocks.map((s) => s.name).join('、')}${draftNames.length ? '；草稿产品：' + draftNames.join('、') : ''}`
    }
  }

  if (nextItems.length >= 2) {
    comparePanel.value = { visible: true, items: nextItems }
  } else {
    comparePanel.value.visible = false
  }

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(360)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(18)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null
}

// 比对分析：生成多产品比对回复并打开比对面板
async function streamQueryCompareReply(question, hits) {
  startThinking()
  const extra = getQueryableDrafts()
  const items = hits
    .map((h) => {
      const full = findQueryableProduct(h.code)
      if (!full) return null
      const detail = full.parsedFields
        ? { parsedFields: full.parsedFields, status: 'draft' }
        : { values: full.snapshot?.values || {}, status: full.snapshot?.productInfo?.status, tags: full.tags }
      return buildCompareItem(full.name, full.code, full.from, detail)
    })
    .filter(Boolean)

  const thinkingSteps = [
    `解析比对请求："${question}"`,
    `识别出 ${items.length} 个待比对产品：${items.map((i) => i.name).join('、')}`,
    '统一抽取资费要素（月费/流量/语音/短信/客群/渠道）…',
    '生成比对表格与资源量对比图，输出差异结论',
  ]

  const names = items.map((i) => i.name).join('、')
  const replyText = [
    '### 比对分析 · 结果',
    '',
    `> 已对 ${items.length} 个产品完成横向比对：**${names}**，比对视图已在右侧打开。`,
    '',
    '**比对内容**',
    '- 资费要素对比表（月费/流量/语音/套外费率/客群/渠道，优势项高亮）',
    '- 资源量柱状对比图',
    '- 差异结论摘要（定位差异 / 性价比 / 竞争关系提示）',
    '',
    '可在右侧继续追问或要求叠加其他产品。',
  ].join('\n')

  // 比对编辑快捷按钮：去掉（直接执行）+ 换掉/叠加（回填输入框，便于补充产品名）
  const compareActions = [
    ...items.map((it) => ({
      key: `compare-remove-${it.code}`,
      label: `去掉 ${it.name}`,
      compareProduct: { name: it.name, code: it.code, from: it.from },
      text: `去掉 ${it.name}`,
    })),
    { key: 'compare-add', label: '叠加产品', text: '叠加 ' },
    ...items.map((it) => ({
      key: `compare-swap-${it.code}`,
      label: `换掉 ${it.name}`,
      text: `把${it.name}换成 `,
    })),
  ]

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
    followUpActions: compareActions,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true
  let cancelled = false
  stopFn = () => { cancelled = true }
  await sleep(420)
  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()
  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(20)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null

  if (!cancelled && items.length >= 2) {
    comparePanel.value = { visible: true, items }
  }
}

async function streamTariffConfigReply(userText) {
  startThinking()
  const parsed = parseTariffRequest(userText)

  const fieldLabels = {
    prodName: '产品名称', monthlyFee: '套餐固定费(元)',
    flowType: '流量类型', flowAmount: '流量资源量', flowUnit: '流量单位',
    voiceType: '语音类型', voiceAmount: '语音资源量',
    smsAmount: '短信资源量',
    flowOvercharge: '流量套外计费', voiceOvercharge: '语音套外费率', smsOvercharge: '短信套外费率',
    validity: '有效期', renewMethod: '续订方式', orderTimes: '同时订购次数',
    preLimitTable: '前项限制（学生认证/实名）',
  }
  const parsedEntries = Object.entries(parsed)

  const thinkingSteps = [
    `解析用户资费需求："${userText}"`,
    '识别套餐名称与资费要素：月费、流量、语音、短信…',
    `共解析出 ${parsedEntries.length} 个字段`,
    '匹配配置工作台字段key，初始化对话式配置工作台…',
    '未提及字段将由AI按业务模板自动补全（标注复用/AI推荐）',
    '打开配置工作台并完成自动填写',
  ]

  // 已解析字段表
  const rows = parsedEntries
    .map(([k, v]) => {
      const val = Array.isArray(v) ? (v[0]?.tipMsg || '已设置') : v
      return `| ${fieldLabels[k] || k} | \`${val}\` |`
    })
    .join('\n')

  const replyText = [
    '### 对话式配置 · 字段解析结果',
    '',
    `> 已根据您的需求「${userText}」完成资费字段解析，共识别 ${parsedEntries.length} 项，配置工作台已自动打开。`,
    '',
    '**已解析字段**',
    '| 字段 | 解析值 |',
    '| ---- | ---- |',
    rows || '| （暂未识别到明确字段） | - |',
    '',
    '**待自动补全字段**',
    '> 其余资费字段（生效规则、上线/下线日期、渠道类型、销售规则等）已由 AI 依据业务模板自动补全，并在工作台中标注 **复用** 或 **AI推荐** 标签。',
    '',
    '请核对解析结果与自动补全内容后提交。',
  ].join('\n')

  const aiMsg = {
    id: genId(),
    role: 'assistant',
    content: '',
    streamText: '',
    loading: true,
    done: false,
    timestamp: Date.now(),
    thinkingSteps,
    thinkingActive: true,
  }
  messages.value = [...messages.value, aiMsg]
  streaming.value = true

  let cancelled = false
  stopFn = () => { cancelled = true }

  await sleep(500)
  if (cancelled) {
    aiMsg.loading = false
    aiMsg.done = true
    aiMsg.thinkingActive = false
    aiMsg.thinkingDuration = endThinking()
    messages.value = [...messages.value]
    streaming.value = false
    stopFn = null
    return
  }

  aiMsg.loading = false
  aiMsg.thinkingActive = false
  aiMsg.thinkingDuration = endThinking()

  for (let i = 0; i < replyText.length; i += 6) {
    if (cancelled) break
    await sleep(24)
    aiMsg.streamText = replyText.slice(0, i + 6)
    aiMsg.content = aiMsg.streamText
    messages.value = [...messages.value]
  }

  if (!cancelled) {
    aiMsg.streamText = replyText
    aiMsg.content = replyText
  }
  aiMsg.done = true
  aiMsg.loading = false
  messages.value = [...messages.value]
  streaming.value = false
  stopFn = null

  if (!cancelled) {
    // 打开对话式配置工作台，仅预填已解析字段；同时生成本场景专属工单号，保证工作台与后续审批/AI回复一致
    const workOrderId = buildDailyWorkOrderId()
    dialogueWorkOrderId.value = workOrderId
    const draft = draftStore.upsertDialogueDraft({
      workOrderId,
      prodName: parsed.prodName || '校园套餐（待命名）',
      summary: buildDialogueDraftSummary(parsed),
      parsedFields: parsed,
    })
    configPanelOpenSeq.value += 1
    // 校园青春卡等全新产品（无原套餐参考）使用场景专属配置数据；其余对话式配置回落到默认 schema
    const campusProduct = /青春卡|校园卡|学生卡/.test(parsed.prodName || '')
    configPanel.value = {
      visible: true,
      parsedFields: parsed,
      workOrderId,
      draftId: draft ? draft.id : '',
      schemaConfig: campusProduct ? campusProductConfig : null,
    }
    configPanelHasOpened.value = true
    // 用对话式套餐自己的名称/工单号初始化流程 stepper，避免沿用产品运营助手(launch)的「畅享59元青春版」
    workflowStore.initWorkflow({
      workOrderId,
      productName: parsed.prodName || '校园套餐（待命名）',
      templateLabel: launchProductConfig.productInfo.templateLabel,
    })
  }
}

const onSwitchSession = async (sid) => {
  // 从历史会话恢复：找到对应 session，加载其消息
  const session = workbenchStore.sessions.find((s) => s.id === sid)
  if (!session) return
  const targetKey = session.assistantKey
  // 兜底分组（未选助手产生的直接对话）不是真实助手，恢复时对应"未选助手"态
  const targetIsRealAssistant = targetKey !== 'default'
  let switched = false
  // 切换助手（或从某个助手切回"未选助手"）
  if (targetIsRealAssistant && activeAssistant.value !== targetKey) {
    workbenchStore.selectAssistant(targetKey)
    switched = true
  } else if (!targetIsRealAssistant && activeAssistant.value) {
    workbenchStore.clearAssistant()
    switched = true
  }
  if (switched) {
    // 等待 activeAssistant 的 watch（post）先结束当前活动会话并清空消息
    await nextTick()
    // 清空逻辑已触发，重置快照计数
    lastSnapshotMsgCount = 0
  }
  messages.value = (session.messages || []).slice()
  lastSnapshotMsgCount = messages.value.length
  // 把当前活动会话指向该条记录，后续对话继续更新它
  workbenchStore.setActiveSession(session.id)
  // 恢复面板状态（聊天消息 + 配置工作台状态）
  restorePanelState(session.snapshot)
}

const onDeleteSession = (sid) => {
  workbenchStore.removeSession(sid)
  // 若删除的是当前活动会话，则结束它并复位面板
  if (workbenchStore.activeSessionId === sid) {
    workbenchStore.startNewSession()
    configPanel.value = { visible: false }
    configPanelHasOpened.value = false
    configSubmitted.value = false
    detailPanel.value = { visible: false, title: '', content: '' }
    messages.value = []
    lastSnapshotMsgCount = 0
    inputText.value = ''
  }
}
</script>

<style scoped>
.config-restore-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  margin-bottom: 8px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f0f7ff 0%, #eef2ff 100%);
  border: 1px solid #c7d7fe;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}
.config-restore-bar:hover {
  border-color: #818cf8;
  background: linear-gradient(135deg, #e0edff 0%, #e0e7ff 100%);
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.12);
}
.restore-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.restore-icon {
  color: #6366f1;
  flex-shrink: 0;
}
.restore-label {
  font-size: 13px;
  color: #6366f1;
  font-weight: 600;
  white-space: nowrap;
}
.restore-product {
  font-size: 13px;
  color: #1e293b;
  font-weight: 700;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.restore-badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.restore-badge.submitted {
  background: #fef3c7;
  color: #b45309;
  border-color: #fde68a;
}
.restore-badge.passed {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.restore-badge.testing {
  background: #dbeafe;
  color: #1d4ed8;
  border-color: #bfdbfe;
}
.restore-badge.tested {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.restore-action {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}
.restore-action:hover {
  background: rgba(99, 102, 241, 0.1);
}
.restore-action svg {
  transition: transform 0.2s ease;
}
.config-restore-bar:hover .restore-action svg {
  transform: translateX(2px);
}

/* 产品运营视图还原条 */
.ops-restore-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  margin-bottom: 8px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f5f3ff 0%, #eef2ff 100%);
  border: 1px solid #ddd6fe;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}
.ops-restore-bar:hover {
  border-color: #a78bfa;
  background: linear-gradient(135deg, #ede9fe 0%, #e0e7ff 100%);
  box-shadow: 0 2px 12px rgba(139, 92, 246, 0.12);
}
.ops-restore-bar:hover .restore-action svg {
  transform: translateX(2px);
}

.restore-slide-enter-active,
.restore-slide-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}
.restore-slide-enter-from,
.restore-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin-bottom: 0;
  border-width: 0;
}
.restore-slide-enter-to,
.restore-slide-leave-from {
  opacity: 1;
  max-height: 60px;
}
</style>

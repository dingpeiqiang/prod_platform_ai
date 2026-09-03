/**
 * usePanelSync - 右侧工作台面板状态中枢
 *
 * 迁移自 chat-skeleton 原型 ChatPage.vue 的面板编排逻辑，拆分为 composable：
 * - 面板开合与类型路由（config/compare/ops 等后续扩展）
 * - 还原条（restore bar）状态：面板关闭后保留上下文，可一键恢复
 * - 智能稽核驱动（复用 useProductConfig.runAudit）
 * - 六阶段工作流联动（提交 → 审批 → 通过）
 * - 面板 key 强制重挂载：draftId/workOrderId/seq 组合，杜绝表单状态残留
 */
import { ref, computed, reactive } from 'vue'
import { useProductConfig } from './useProductConfig.js'
import { useProductWorkflowStore } from '../stores/productWorkflow.js'

/** 还原条展示的状态徽标文案 */
const RESTORE_STATUS_LABELS = {
  draft: '草稿',
  passed: '稽核通过',
  submitted: '待审批',
  test: '测试中',
  filing: '待备案公示',
  channel: '待选择渠道',
}

/** 面板类型默认宽度（px）：ops 类宽面板参考原型 OPS_WIDTH=900 */
const PANEL_WIDTHS = {
  config: 480,
  compare: 480,
  'ops-view': 900,
  monitor: 720,
  'root-cause': 720,
  'risk-audit': 720,
}

export function usePanelSync() {
  const productConfig = useProductConfig()
  const workflow = useProductWorkflowStore()

  /** 当前打开的面板类型：null | 'config' | 'compare' | 'ops-view' | 'monitor' | 'root-cause' | 'risk-audit' */
  const panelType = ref(null)
  /** 面板 key：product.id + seq，切换场景时强制重挂载 */
  const panelKeySeq = ref(0)
  const panelKey = computed(() => {
    const pid = productConfig.currentProductId.value || 'none'
    return `${pid}-${panelKeySeq.value}`
  })
  /** 面板宽度：随类型切换给出默认值，拖宽后由页面层持久化覆盖 */
  const panelWidth = ref(PANEL_WIDTHS[panelType.value] || 480)

  /** 还原条：面板关闭后保留快照描述 */
  const restoreBar = reactive({
    visible: false,
    status: 'draft',
    label: '',
    productName: '',
  })

  const panelOpen = computed(() => panelType.value !== null)

  /** 捕获还原条状态描述 */
  function captureRestoreStatus() {
    const product = productConfig.currentProduct.value
    if (!product) return 'draft'
    if (product.status === 'submitted') return 'submitted'
    if (product.compliancePass) return 'passed'
    return 'draft'
  }

  function showRestoreBar() {
    const product = productConfig.currentProduct.value
    restoreBar.visible = true
    restoreBar.status = captureRestoreStatus()
    restoreBar.productName = product?.name || ''
    restoreBar.label = RESTORE_STATUS_LABELS[restoreBar.status] || '草稿'
  }

  function hideRestoreBar() {
    restoreBar.visible = false
  }

  /** 打开配置工作台面板 */
  function openConfigPanel({ remount = true } = {}) {
    if (remount) panelKeySeq.value += 1
    panelType.value = 'config'
    restoreBar.visible = false
  }

  /** 打开比对面板（compare 数据由 useProductConfig.compareResult 提供） */
  function openComparePanel() {
    panelType.value = 'compare'
    panelWidth.value = PANEL_WIDTHS.compare
    restoreBar.visible = false
  }

  /** 打开产品运营视图（宽面板，原型 OPS_WIDTH=900） */
  function openOpsViewPanel({ remount = true } = {}) {
    if (remount) panelKeySeq.value += 1
    panelType.value = 'ops-view'
    panelWidth.value = PANEL_WIDTHS['ops-view']
    restoreBar.visible = false
  }

  /** 打开运营类结果面板（monitor/root-cause/risk-audit），由 SSE 后处理器驱动 */
  function openOpsResultPanel(type) {
    if (!PANEL_WIDTHS[type]) return
    panelType.value = type
    panelWidth.value = PANEL_WIDTHS[type]
    restoreBar.visible = false
  }

  /** 关闭面板（保留还原条） */
  function closePanel() {
    if (panelType.value === 'config') {
      showRestoreBar()
    } else if (panelType.value === 'ops-view') {
      showOpsRestoreBar()
    }
    panelType.value = null
  }

  /** 运营视图还原条：记录关闭时下钻选中的套餐 */
  function showOpsRestoreBar() {
    restoreBar.visible = true
    restoreBar.status = 'ops-view'
    restoreBar.label = '运营视图'
    restoreBar.productName = '产品运营视图'
  }

  /** 从还原条恢复面板 */
  function restorePanel() {
    if (restoreBar.status === 'ops-view') {
      openOpsViewPanel({ remount: false })
      return
    }
    if (restoreBar.status === 'submitted' || restoreBar.label) {
      openConfigPanel({ remount: false })
    } else {
      openConfigPanel()
    }
  }
  /** 对话驱动的字段级增量更新：面板已打开时只更新字段，不重建 */
  function applyFieldUpdate(fieldCode, value, meta = {}) {
    if (panelType.value !== 'config') {
      openConfigPanel()
      return
    }
    panelApi.value?.applyFieldUpdate?.(fieldCode, value, meta)
  }

  /** 面板组件 ref（页面层通过 :ref 绑定回填） */
  const panelApi = ref(null)
  function setPanelApi(api) {
    panelApi.value = api
  }

  /** 智能稽核：走 useProductConfig.runAudit（真实后端 checkCompliance） */
  const auditing = ref(false)
  async function runSmartAudit() {
    if (auditing.value) return null
    auditing.value = true
    try {
      const result = await productConfig.runAudit()
      return result
    } finally {
      auditing.value = false
    }
  }

  /** 提交配置：走真实 submitCurrentDraft，联动工作流推进（审批阶段） */
  const submitting = ref(false)
  async function submitConfig(sessionId) {
    if (submitting.value) return null
    submitting.value = true
    try {
      productConfig.saveDraftLocal()
      const product = productConfig.currentProduct.value
      if (product) {
        workflow.setProduct({
          productName: product.ontologyDraft?.offerName || product.ontologyDraft?.offeringName || product.name,
          workOrderId: product.workOrderId || '',
        })
      }
      if (!workflow.active) {
        workflow.initWorkflow({ productName: workflow.productName, workOrderId: workflow.workOrderId })
      }
      const resp = await productConfig.submitCurrentDraft(sessionId)
      if (resp?.success !== false) {
        const woId = resp?.workOrder?.workOrderId || resp?.workOrder?.work_order_id
        if (woId) {
          workflow.setProduct({ workOrderId: woId })
          workflow.advanceTo('approve')
        }
      }
      return resp
    } finally {
      submitting.value = false
    }
  }

  /** 审批通过回调（由后端事件/轮询触发）：推进工作流并激活 awaiting 悬停态 */
  function onApprovalPassed() {
    workflow.onApprovalPassed()
  }

  /** 会话切换/重置 */
  function resetPanelState() {
    panelType.value = null
    panelWidth.value = 480
    restoreBar.visible = false
    workflow.reset()
  }

  return {
    panelType,
    panelOpen,
    panelKey,
    panelWidth,
    restoreBar,
    panelApi,
    setPanelApi,
    openConfigPanel,
    openComparePanel,
    openOpsViewPanel,
    openOpsResultPanel,
    closePanel,
    restorePanel,
    hideRestoreBar,
    applyFieldUpdate,
    auditing,
    runSmartAudit,
    submitting,
    submitConfig,
    onApprovalPassed,
    resetPanelState,
  }
}

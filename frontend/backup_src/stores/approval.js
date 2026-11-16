import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'

/**
 * 审批工单 store
 *
 * 职责：
 * 1. 维护已提交的审批工单列表（由 ProductConfigPanel 提交时写入）
 * 2. 每个工单带状态时间线：submitted → approving → approved
 *    注：配置面板在提交前已完成智能稽核并通过，提交后直接进入主管审批，
 *        不再包含"稽核"环节；审批最终结果为通过。
 * 3. 模拟状态定时推进：提交后逐步走完审批流程
 * 4. 通知 badge 数量 = 处于"审批中"（未终态）的工单数
 *
 * 工单结构：
 *   {
 *     id, workOrderId, productName, template, submittedAt,
 *     status: 'approving' | 'approved',
 *     timeline: [{ step, status, time, operator, desc }],
 *     timer: 内部定时器句柄（不暴露）
 *   }
 */
export const useApprovalStore = defineStore('approval', () => {
  const approvals = ref([])

  // 处于审批中的工单数（非终态）
  const pendingCount = computed(() =>
    approvals.value.filter((a) => a.status === 'approving').length
  )

  const hasPending = computed(() => pendingCount.value > 0)

  // 按提交时间倒序
  const sortedApprovals = computed(() =>
    [...approvals.value].sort((a, b) => b.submittedAt - a.submittedAt)
  )

  const STATUS_META = {
    approving: { label: '审批中',   color: '#2563eb', tone: 'blue' },
    approved:  { label: '审批通过', color: '#10b981', tone: 'green' },
  }

  function statusMeta(status) {
    return STATUS_META[status] || STATUS_META.approving
  }

  /**
   * 创建一条审批工单，并启动模拟推进定时器。
   * 提交前智能稽核已通过，故工单初始状态直接为"审批中"。
   *
   * autoAdvance：是否自动模拟推进到"审批通过"。
   *   - 默认 true：产品运营助手(launch)保持原有模拟审批流程（审批自动通过）。
   *   - false：产品研发助手·对话式配置(rd.scene2)只到"审批中"，不自动通过，
   *     由其"我的审批"保持在待审批状态。
   * @param {Object} payload { workOrderId, productName, templateLabel, autoAdvance }
   */
  function createApproval({ workOrderId, productName, templateLabel, autoAdvance = true }) {
    const now = Date.now()
    const approval = reactive({
      id: 'ap_' + now.toString(36) + Math.random().toString(36).slice(2, 6),
      workOrderId: workOrderId || ('WO' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + String(Math.floor(Math.random() * 9000 + 1000))),
      productName: productName || '未命名产品',
      templateLabel: templateLabel || '个人主资费',
      submittedAt: now,
      autoAdvance,
      status: 'approving',
      timeline: [
        {
          step: 'submitted',
          label: '配置已提交',
          status: 'done',
          time: now,
          operator: '当前用户',
          desc: '配置面板提交（智能稽核已通过），生成审批工单',
        },
        {
          step: 'approving',
          label: '主管审批',
          status: 'active',
          time: null,
          operator: '产品主管',
          desc: '人工复核配置内容与上线影响',
        },
        {
          step: 'approved',
          label: '审批完成',
          status: 'pending',
          time: null,
          operator: '',
          desc: '工单流转完成',
        },
      ],
      _timer: null,
    })
    approvals.value = [approval, ...approvals.value]
    // 仅"自动推进"场景启动模拟审批定时器；对话式配置(autoAdvance=false)停在审批中
    if (approval.autoAdvance !== false) {
      scheduleNext(approval)
    }
    return approval
  }

  /**
   * 模拟推进：约 8~12 秒后审批通过。
   */
  function scheduleNext(approval) {
    const STEP_INTERVAL = 9000
    approval._timer = setTimeout(() => advance(approval), STEP_INTERVAL)
  }

  function advance(approval) {
    const now = Date.now()
    if (approval.status === 'approving') {
      // 主管审批完成 → 通过
      const t = approval.timeline.find((x) => x.step === 'approving')
      t.status = 'done'
      t.time = now
      const finalStep = approval.timeline.find((x) => x.step === 'approved')
      finalStep.status = 'done'
      finalStep.time = now
      finalStep.label = '审批通过'
      finalStep.operator = '产品主管'
      finalStep.desc = '配置符合规范，工单已通过，可进入测试流程'
      approval.status = 'approved'
      approval._timer = null
    }
  }

  function getById(id) {
    return approvals.value.find((a) => a.id === id)
  }

  function getByWorkOrderId(woid) {
    return approvals.value.find((a) => a.workOrderId === woid)
  }

  /**
   * 清理定时器（组件卸载时调用，避免内存泄漏）
   */
  function dispose() {
    approvals.value.forEach((a) => {
      if (a._timer) {
        clearTimeout(a._timer)
        a._timer = null
      }
    })
  }

  return {
    approvals,
    pendingCount,
    hasPending,
    sortedApprovals,
    statusMeta,
    createApproval,
    getById,
    getByWorkOrderId,
    dispose,
  }
})

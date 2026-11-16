import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 资费方案流程 store
 *
 * 职责：
 * 1. 维护单个方案的资费全生命周期流程状态
 * 2. 三阶段固定顺序：方案设计 → 产品配置 → 审批上架
 * 3. 阶段流转由各业务动作触发：
 *    - 方案确认 → design.done + config.active
 *    - 配置提交审批 → config.done + approval.active
 *    - 审批通过 → approval.done（终态，即完成上架）
 *
 * 方案流程结构：
 *   {
 *     id, workOrderId, productName, templateLabel,
 *     currentStage: 'design' | 'config' | 'approval' | 'done',
 *     stages: [{ key, label, status: 'done'|'active'|'pending', enteredAt, completedAt }],
 *   }
 */
export const useWorkflowStore = defineStore('workflow', () => {
  // 流程定义（固定顺序）
  const STAGE_DEFS = [
    { key: 'design',   label: '方案设计', short: '设计' },
    { key: 'config',   label: '产品配置', short: '配置' },
    { key: 'approval', label: '审批上架', short: '上架' },
  ]

  // 当前方案流程（单方案演示）
  const workflow = ref(null)

  const currentStage = computed(() => workflow.value?.currentStage || null)

  const stages = computed(() => workflow.value?.stages || [])

  const activeStageIndex = computed(() => {
    if (!workflow.value) return -1
    return STAGE_DEFS.findIndex((s) => s.key === workflow.value.currentStage)
  })

  /**
   * 初始化方案流程（方案确认时调用）
   * 初始状态：design 已完成，config 进行中
   * 注意：此时配置尚未提交，workOrderId 留空，待提交审批时再写入
   */
  function initWorkflow({ workOrderId, productName, templateLabel }) {
    const now = Date.now()
    workflow.value = {
      id: 'wf_' + now.toString(36),
      workOrderId: '',
      productName: productName || '未命名产品',
      templateLabel: templateLabel || '个人主资费',
      currentStage: 'config',
      stages: STAGE_DEFS.map((def, idx) => ({
        ...def,
        status: idx === 0 ? 'done' : idx === 1 ? 'active' : 'pending',
        enteredAt: idx <= 1 ? now : null,
        completedAt: idx === 0 ? now : null,
      })),
    }
    return workflow.value
  }

  /**
   * 设置工单号（配置提交审批时调用）
   */
  function setWorkOrderId(workOrderId) {
    if (!workflow.value || !workOrderId) return
    workflow.value.workOrderId = workOrderId
  }

  /**
   * 推进到指定阶段，自动把中间阶段标记为 done
   */
  function advanceTo(stageKey) {
    if (!workflow.value) return
    const targetIdx = STAGE_DEFS.findIndex((s) => s.key === stageKey)
    if (targetIdx < 0) return
    const now = Date.now()
    workflow.value.stages.forEach((stage, idx) => {
      if (idx < targetIdx) {
        if (stage.status !== 'done') {
          stage.status = 'done'
          stage.completedAt = now
        }
      } else if (idx === targetIdx) {
        stage.status = 'active'
        if (!stage.enteredAt) stage.enteredAt = now
      } else {
        stage.status = 'pending'
      }
    })
    workflow.value.currentStage = stageKey
    workflow.value.awaiting = null
  }

  /**
   * 标记当前阶段完成，并推进到下一阶段
   */
  function completeCurrentStage() {
    if (!workflow.value) return
    const curIdx = STAGE_DEFS.findIndex((s) => s.key === workflow.value.currentStage)
    if (curIdx < 0) return
    const now = Date.now()
    workflow.value.stages[curIdx].status = 'done'
    workflow.value.stages[curIdx].completedAt = now
    if (curIdx < STAGE_DEFS.length - 1) {
      const next = workflow.value.stages[curIdx + 1]
      next.status = 'active'
      next.enteredAt = now
      workflow.value.currentStage = next.key
    } else {
      workflow.value.currentStage = 'done'
    }
  }

  /**
   * 审批通过联动：审批上架阶段完成 → 流程进入终态（审批上架完成）
   */
  function onApprovalPassed(workOrderId) {
    if (!workflow.value) return
    if (workOrderId && workflow.value.workOrderId !== workOrderId) return
    if (workflow.value.currentStage === 'approval') {
      completeCurrentStage() // approval 为末阶段 → 进入 done 终态
    }
  }

  function getStageMeta(key) {
    return STAGE_DEFS.find((s) => s.key === key) || null
  }

  function reset() {
    workflow.value = null
  }

  return {
    STAGE_DEFS,
    workflow,
    currentStage,
    stages,
    activeStageIndex,
    initWorkflow,
    setWorkOrderId,
    advanceTo,
    completeCurrentStage,
    onApprovalPassed,
    getStageMeta,
    reset,
  }
})

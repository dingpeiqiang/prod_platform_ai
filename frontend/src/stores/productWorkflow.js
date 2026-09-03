/**
 * productWorkflow - 商品全生命周期六阶段工作流引擎
 *
 * 迁移自 chat-skeleton 原型 stores/workflow.js，适配真实工程：
 * 阶段推进由 SSE 意图流（RD_CONFIG_CHAT / 提交 / 审批 / 测试 / 备案 / 上架）驱动，
 * awaiting 悬停态表达「上一阶段已完成、等待人工动作才推进」的真实业务节奏。
 */
import { defineStore } from 'pinia'

export const STAGE_DEFS = [
  { key: 'design',  label: '方案设计' },
  { key: 'config',  label: '商品配置' },
  { key: 'approve', label: '预览审批' },
  { key: 'test',    label: '测试审核' },
  { key: 'filing',  label: '备案公示' },
  { key: 'launch',  label: '上架销售' },
]

/** awaiting 悬停态 → 需要的人工动作文案 */
export const AWAITING_LABELS = {
  filing: '待发起备案公示',
  launch: '待选择销售渠道',
}

function makeStages() {
  return STAGE_DEFS.map((def, i) => ({
    ...def,
    index: i,
    status: 'pending', // pending | active | done | awaiting
  }))
}

export const useProductWorkflowStore = defineStore('productWorkflow', {
  state: () => ({
    active: false,
    productName: '',
    workOrderId: '',
    stages: makeStages(),
    /** 各阶段扩展信息（审批单号、测试报告、备案号、渠道等） */
    extras: {},
  }),

  getters: {
    currentIndex: (state) => state.stages.findIndex((s) => s.status === 'active' || s.status === 'awaiting'),
    currentStage() {
      const idx = this.currentIndex
      return idx >= 0 ? this.stages[idx] : null
    },
    /** 流程条展示用：含 awaiting 悬停标签 */
    displayStages: (state) => state.stages.map((s) => ({
      ...s,
      awaitingLabel: s.status === 'awaiting' ? (AWAITING_LABELS[s.key] || '') : '',
    })),
    isTerminal: (state) => state.stages.every((s) => s.status === 'done'),
  },

  actions: {
    /** 初始化工作流（方案确认后调用） */
    initWorkflow({ productName = '', workOrderId = '' } = {}) {
      this.active = true
      this.productName = productName
      this.workOrderId = workOrderId
      this.stages = makeStages()
      this.extras = {}
      // 初始即进入第一/第二阶段：方案设计视作已完成，进入商品配置
      this.advanceTo('config')
    },

    /** 重置 */
    reset() {
      this.active = false
      this.productName = ''
      this.workOrderId = ''
      this.stages = makeStages()
      this.extras = {}
    },

    _indexOf(key) {
      return this.stages.findIndex((s) => s.key === key)
    },

    /** 推进到指定阶段：自动补齐中间态（之前全部置 done） */
    advanceTo(key) {
      const idx = this._indexOf(key)
      if (idx < 0) return
      this.stages.forEach((s, i) => {
        if (i < idx) s.status = 'done'
        else if (i === idx) s.status = 'active'
        else s.status = 'pending'
      })
    },

    /** 当前阶段完成；nextKey 为空或 auto=false 时进入 awaiting 悬停态 */
    completeCurrentStage({ nextKey = null, awaiting = false } = {}) {
      const idx = this.currentIndex
      if (idx < 0) return
      this.stages[idx].status = 'done'
      if (nextKey) {
        const nIdx = this._indexOf(nextKey)
        if (nIdx >= 0) {
          if (awaiting) {
            this.stages[nIdx].status = 'awaiting'
          } else {
            this.stages[nIdx].status = 'active'
          }
        }
      }
    },

    /** awaiting 悬停态 → 正式激活（人工动作完成后调用） */
    activateAwaiting(key) {
      const s = this.stages.find((x) => x.key === key)
      if (s && s.status === 'awaiting') s.status = 'active'
    },

    /** 阶段完成即悬停在下一阶段（等待人工动作） */
    onApprovalPassed({ testReport = null } = {}) {
      if (!this.active) return
      this.completeCurrentStage({ nextKey: 'test', awaiting: true })
      if (testReport) this.extras.testReport = testReport
    },

    /** 测试通过 → 悬停在备案公示，等待「发起备案」动作 */
    onTestPassed() {
      if (!this.active) return
      this.completeCurrentStage({ nextKey: 'filing', awaiting: true })
    },

    /** 备案通过 → 悬停在上架销售，等待「选择销售渠道」动作 */
    onFilingApproved({ filingNo = '' } = {}) {
      if (!this.active) return
      this.completeCurrentStage({ nextKey: 'launch', awaiting: true })
      if (filingNo) this.extras.filingNo = filingNo
    },

    /** 上架完成，全流程闭环 */
    onLaunched({ channel = '' } = {}) {
      if (!this.active) return
      this.completeCurrentStage({})
      if (channel) this.extras.channel = channel
    },

    setProduct({ productName, workOrderId }) {
      if (productName !== undefined) this.productName = productName
      if (workOrderId !== undefined) this.workOrderId = workOrderId
    },
  },
})

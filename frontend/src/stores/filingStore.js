import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { filing } from '../config/launchOptimizeData.js'

/**
 * 备案公示 store
 *
 * 职责：
 * 1. 点击"提交备案"后，模拟延迟一段时间备案通过
 * 2. 备案通过后标记 filing.approved = true，触发通知信号，使"销售渠道"阶段可用
 *
 * 说明：本 store 只负责配置人员侧的流程推进，不涉及审批人员端界面。
 */
export const useFilingStore = defineStore('filing', () => {
  // 是否已开始模拟（避免重复启动）
  const scheduling = ref(false)
  // 备案通过触发信号（每次 +1，ChatPage 监听变化）
  const triggerId = ref(0)
  let timer = null

  // 是否已备案通过
  const approved = computed(() => filing.approved)
  // 是否提交过备案但尚未通过（审批中）
  const pendingApproval = computed(() => filing.submitted && !filing.approved)

  /**
   * 启动模拟：提交备案后调用，延迟若干秒模拟备案通过
   */
  function startSimulate(delay = 6000) {
    if (scheduling.value || filing.approved) return
    scheduling.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      filing.approved = true
      filing.approvedAt = Date.now()
      scheduling.value = false
      timer = null
      // 触发通知信号
      triggerId.value++
    }, delay)
  }

  function dispose() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    scheduling.value = false
  }

  return {
    scheduling,
    triggerId,
    approved,
    pendingApproval,
    startSimulate,
    dispose,
  }
})

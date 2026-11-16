import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { testReport } from '../config/launchOptimizeData.js'

/**
 * 测试报告 store
 *
 * 职责：
 * 1. 模拟测试人员在审批通过后，延迟一段时间上传测试报告
 * 2. 上传后标记 testReport.uploaded = true，触发配置人员侧的通知与展示
 * 3. 提供 triggerId 供 ChatPage 监听，生成 AI 主动消息告知配置人员
 *
 * 说明：本 store 只负责配置人员侧的"接收与展示"，不涉及测试人员端操作界面。
 */
export const useTestReportStore = defineStore('testReport', () => {
  // 是否已开始模拟（避免重复启动）
  const scheduling = ref(false)
  // 报告到达触发信号（每次 +1，ChatPage 监听变化）
  const triggerId = ref(0)
  // 内部定时器句柄
  let timer = null

  // 是否已上传（直接代理到 testReport.uploaded）
  const uploaded = computed(() => testReport.uploaded)

  // 是否正在测试中（已开始模拟但尚未上传）
  const testing = computed(() => scheduling.value && !testReport.uploaded)

  /**
   * 启动模拟：审批通过后调用，延迟 16 秒模拟测试人员完成测试并上传报告
   */
  function startSimulate(delay = 16000) {
    if (scheduling.value || testReport.uploaded) return
    scheduling.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      // 模拟测试人员上传报告
      testReport.uploaded = true
      testReport.uploadedAt = Date.now()
      scheduling.value = false
      timer = null
      // 触发通知信号
      triggerId.value++
    }, delay)
  }

  /**
   * 清理定时器（组件卸载时调用）
   */
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
    uploaded,
    testing,
    startSimulate,
    dispose,
  }
})

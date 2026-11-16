import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 消息通知 store
 *
 * 职责：
 * 1. 维护未读消息数（badge）
 * 2. 提供触发信号，ChatPage 监听后生成 AI 主动消息
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(3)
  const triggerId = ref(0)

  const hasUnread = computed(() => unreadCount.value > 0)

  function trigger() {
    triggerId.value++
    unreadCount.value = 0
  }

  function setCount(n) {
    unreadCount.value = Math.max(0, n)
  }

  return {
    unreadCount,
    hasUnread,
    triggerId,
    trigger,
    setCount,
  }
})

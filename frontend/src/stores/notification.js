/**
 * notification - 通知中心信号 store
 *
 * 迁移自 chat-skeleton 原型 stores/notification.js：
 * 极简信号模式——triggerId 自增驱动页面注入 AI 主动消息，unreadCount 供徽标展示。
 * 后续接入真实后端消息推送时，仅需替换触发源，消费端结构不变。
 */
import { defineStore } from 'pinia'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    /** 通知触发信号：自增 id，页面 watch 后注入 AI 消息 */
    triggerId: 0,
    /** 最近一次触发的通知上下文（审批/待办详情） */
    lastTrigger: null,
    /** 未读通知数 */
    unreadCount: 0,
    /** 我的审批（对接真实工单/审批数据；暂由页面侧回填） */
    approvals: [],
    /** 今日待办：{ id, level: urgent|important|remind, title, action, ts } */
    todos: [],
  }),

  getters: {
    pendingApprovalCount: (state) =>
      state.approvals.filter((a) => a.status === 'pending').length,
    badgeCount() {
      return this.pendingApprovalCount + this.unreadCount
    },
  },

  actions: {
    /** 触发「进入对话查看详情」：页面 watch triggerId 注入 AI 消息 */
    trigger(payload = null) {
      this.triggerId += 1
      this.lastTrigger = payload
      if (payload?.todoId) {
        this.markTodoRead(payload.todoId)
      }
    },

    clearUnread() {
      this.unreadCount = 0
    },

    markTodoRead(todoId) {
      const todo = this.todos.find((t) => t.id === todoId)
      if (todo) todo.read = true
      this.unreadCount = Math.max(0, this.todos.filter((t) => !t.read).length)
    },

    setApprovals(list) {
      this.approvals = Array.isArray(list) ? list : []
    },

    setTodos(list) {
      this.todos = Array.isArray(list) ? list.map((t) => ({ ...t, read: t.read ?? false })) : []
      this.unreadCount = this.todos.filter((t) => !t.read).length
    },

    reset() {
      this.triggerId = 0
      this.lastTrigger = null
      this.unreadCount = 0
      this.approvals = []
      this.todos = []
    },
  },
})

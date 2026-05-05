/**
 * useIntentRegistry - 前端意图注册器
 * 管理 SSE 事件类型 → 面板组件 + 事件处理器的映射
 */
import { reactive } from 'vue'

// ── 事件处理器注册表 ──────────────────────────────────
const _eventHandlers = reactive({})

// ── 意图后处理器注册表 ──────────────────────────────────
const _postProcessors = reactive({})

/**
 * 注册 SSE 事件处理器
 * @param {string} intentType - 意图类型
 * @param {Function} handler - 事件处理函数 (data, msg) => void
 * @param {object} [options] - 可选配置
 */
export function registerEventHandler(intentType, handler, options = {}) {
  _eventHandlers[intentType] = {
    handler,
    panel: options.panel || null
  }
}

/**
 * 注册意图后处理器
 * @param {string} intentType - 意图类型
 * @param {Function} processor - 后处理函数 (msg, intentData) => void | Promise
 */
export function registerPostProcessor(intentType, processor) {
  _postProcessors[intentType] = processor
}

/**
 * 查找 SSE 事件处理器
 */
export function getEventHandler(intentType) {
  return _eventHandlers[intentType]?.handler || null
}

/**
 * 查找面板组件
 */
export function getEventPanel(intentType) {
  return _eventHandlers[intentType]?.panel || null
}

/**
 * 查找意图后处理器
 */
export function getPostProcessor(intentType) {
  return _postProcessors[intentType] || null
}

export function listEventTypes() {
  return Object.keys(_eventHandlers)
}

export function listIntentTypes() {
  return Object.keys(_postProcessors)
}
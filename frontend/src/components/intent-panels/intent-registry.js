/**
 * 前端意图注册器
 * 管理 SSE 事件类型 → 面板组件 + 事件处理器的映射
 */

// ── 事件处理器注册表 ──────────────────────────────────
// key: SSE event type (如 'config', 'delete_form', 'manage_history')
// value: { handler: Function, panel: Component | null }
const _eventHandlers = new Map()

// ── 意图后处理器注册表 ──────────────────────────────
// key: intentType (如 'form', 'form_update', 'configure')
// value: Function(msg, intentData)
const _postProcessors = new Map()

/**
 * 注册 SSE 事件处理器
 * @param {string} eventType - SSE 事件类型
 * @param {Function} handler - 事件处理函数 (data, msg) => void
 * @param {object} [options] - 可选配置
 * @param {import('vue').Component} [options.panel] - 对应的面板组件
 */
export function registerEventHandler(eventType, handler, options = {}) {
  _eventHandlers.set(eventType, {
    handler,
    panel: options.panel || null
  })
}

/**
 * 注册意图后处理器
 * @param {string} intentType - 意图类型
 * @param {Function} processor - 后处理函数 (msg, intentData) => void | Promise
 */
export function registerPostProcessor(intentType, processor) {
  _postProcessors.set(intentType, processor)
}

/**
 * 查找 SSE 事件处理器
 * @param {string} eventType
 * @returns {Function|null}
 */
export function getEventHandler(eventType) {
  const entry = _eventHandlers.get(eventType)
  return entry ? entry.handler : null
}

/**
 * 查找面板组件
 * @param {string} eventType
 * @returns {import('vue').Component|null}
 */
export function getEventPanel(eventType) {
  const entry = _eventHandlers.get(eventType)
  return entry ? entry.panel : null
}

/**
 * 查找意图后处理器
 * @param {string} intentType
 * @returns {Function|null}
 */
export function getPostProcessor(intentType) {
  return _postProcessors.get(intentType) || null
}

/**
 * 获取所有已注册的事件类型
 * @returns {string[]}
 */
export function listEventTypes() {
  return [..._eventHandlers.keys()]
}

/**
 * 获取所有已注册的意图类型
 * @returns {string[]}
 */
export function listIntentTypes() {
  return [..._postProcessors.keys()]
}

/**
 * useIntentRegistry - 前端意图注册器
 * 管理 SSE 事件类型 → 面板组件 + 事件处理器的映射
 */
import { reactive } from 'vue'
import ChatPanel from '../components/intent-panels/ChatPanel.vue'
import FormIntentPanel from '../components/intent-panels/FormIntentPanel.vue'
import DeleteResultPanel from '../components/intent-panels/DeleteResultPanel.vue'
import HistoryPanel from '../components/intent-panels/HistoryPanel.vue'
import ValidationResultPanel from '../components/intent-panels/ValidationResultPanel.vue'
import ProductOpsPanel from '../components/intent-panels/ProductOpsPanel.vue'

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
  if (processor == null) {
    delete _postProcessors[intentType]
    return
  }
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

export function listIntentPanels() {
  return Object.entries(_eventHandlers)
    .filter(([, entry]) => entry.panel !== null)
    .map(([type]) => type)
}

export function listIntentTypes() {
  return Object.keys(_postProcessors)
}

// ── 默认面板注册 ──────────────────────────────────────
// 注册所有意图类型的面板组件，确保 IntentPanel.vue 能正确渲染
registerEventHandler('chat', (data, msg) => {
  // 聊天意图由流式文本渲染，无需额外处理
}, { panel: ChatPanel })

registerEventHandler('form', (data, msg) => {
  // 表单意图由 InlineFormEditor 在消息流内联渲染，此处为占位
}, { panel: FormIntentPanel })

registerEventHandler('configure', (data, msg) => {
  // 配置意图由流式文本渲染
}, { panel: ChatPanel })

registerEventHandler('validate', (data, msg) => {
  // 校验意图由 ValidationResultPanel 渲染
}, { panel: ValidationResultPanel })

registerEventHandler('delete_form', (data, msg) => {
  // 删除意图由 DeleteResultPanel 渲染
}, { panel: DeleteResultPanel })

registerEventHandler('manage_history', (data, msg) => {
  // 历史管理由 HistoryPanel 渲染
}, { panel: HistoryPanel })

registerEventHandler('form_update', (data, msg) => {
  // 表单更新由 FormIntentPanel 渲染
}, { panel: FormIntentPanel })

registerEventHandler('product_ops_query', (data, msg) => {
  // 市场洞察由 ProductOpsPanel 渲染
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_policy', (data, msg) => {
  // 立项研判由 ProductOpsPanel 渲染
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_reason', (data, msg) => {
  // 异动归因由 ProductOpsPanel 渲染
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_compare', (data, msg) => {
  // 假设分析由 ProductOpsPanel 渲染
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_monitor', (data, msg) => {
  // 运营监控由 ProductOpsPanel 渲染
}, { panel: ProductOpsPanel })
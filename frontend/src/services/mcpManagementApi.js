import { request } from './httpClient.js'

const BASE = '/mcp-management'

/**
 * 获取 MCP 工具列表
 * @param {string|null} category - 可选的分类过滤
 */
export async function listTools(category = null) {
  const params = category ? { category } : {}
  return request(`${BASE}/tools`, { params })
}

/**
 * 获取 MCP 工具整体统计
 */
export async function getStats() {
  return request(`${BASE}/stats`)
}

/**
 * 测试 MCP 工具执行
 * @param {string} toolName - 工具名称
 * @param {Object} args - 工具参数
 */
export async function testTool(toolName, args = {}) {
  return request(`${BASE}/tools/${toolName}/test`, { method: 'POST', data: args })
}

/**
 * 获取工具执行日志
 * @param {string|null} toolName - 可选的工具名称过滤
 * @param {number} limit - 日志数量限制
 */
export async function getLogs(toolName = null, limit = 100) {
  const params = { limit }
  if (toolName) params.tool_name = toolName
  return request(`${BASE}/logs`, { params })
}

/**
 * 获取 MCP 工具分类列表
 */
export async function getCategories() {
  return request(`${BASE}/categories`)
}

/**
 * 获取外部工具列表
 */
export async function getExternalTools() {
  return request(`${BASE}/external-tools`)
}

/**
 * 获取单个外部工具详情
 * @param {string} toolName - 工具名称
 */
export async function getExternalTool(toolName) {
  return request(`${BASE}/external-tools/${toolName}`)
}

/**
 * 创建外部工具
 * @param {Object} toolData - 工具数据
 */
export async function createExternalTool(toolData) {
  return request(`${BASE}/external-tools`, { method: 'POST', data: toolData })
}

/**
 * 更新外部工具
 * @param {string} toolName - 工具名称
 * @param {Object} toolData - 工具数据
 */
export async function updateExternalTool(toolName, toolData) {
  return request(`${BASE}/external-tools/${toolName}`, { method: 'PUT', data: toolData })
}

/**
 * 删除外部工具
 * @param {string} toolName - 工具名称
 */
export async function deleteExternalTool(toolName) {
  return request(`${BASE}/external-tools/${toolName}`, { method: 'DELETE' })
}

/**
 * 切换外部工具启用状态
 * @param {string} toolName - 工具名称
 * @param {boolean} enabled - 是否启用
 */
export async function toggleExternalTool(toolName, enabled) {
  return request(`${BASE}/external-tools/${toolName}/toggle`, { method: 'POST', data: { enabled } })
}

/**
 * 解析 OpenAPI 规范并预览工具定义（不保存）
 * @param {string} specContent - OpenAPI 规范内容
 */
export async function parseOpenAPISpec(specContent) {
  return request(`${BASE}/external-tools/parse`, { method: 'POST', data: { spec_content: specContent } })
}

/**
 * 从 OpenAPI 规范导入外部工具
 * @param {string} specContent - OpenAPI 规范内容
 * @param {object} options - 导入选项
 */
export async function importExternalTools(specContent, options = {}) {
  return request(`${BASE}/external-tools/import`, {
    method: 'POST',
    data: {
      spec_content: specContent,
      category: options.category || 'external',
      is_enabled: options.isEnabled !== undefined ? options.isEnabled : true
    }
  })
}

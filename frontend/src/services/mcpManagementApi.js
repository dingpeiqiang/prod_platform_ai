import axios from 'axios'

const BASE_URL = '/api/v1/mcp-management'

/**
 * 获取 MCP 工具列表
 * @param {string|null} category - 可选的分类过滤
 */
export async function listTools(category = null) {
  const params = category ? { category } : {}
  const res = await axios.get(`${BASE_URL}/tools`, { params })
  return res.data
}

/**
 * 获取 MCP 工具整体统计
 */
export async function getStats() {
  const res = await axios.get(`${BASE_URL}/stats`)
  return res.data
}

/**
 * 测试 MCP 工具执行
 * @param {string} toolName - 工具名称
 * @param {Object} args - 工具参数
 */
export async function testTool(toolName, args = {}) {
  const res = await axios.post(`${BASE_URL}/tools/${toolName}/test`, args)
  return res.data
}

/**
 * 获取工具执行日志
 * @param {string|null} toolName - 可选的工具名称过滤
 * @param {number} limit - 日志数量限制
 */
export async function getLogs(toolName = null, limit = 100) {
  const params = { limit }
  if (toolName) params.tool_name = toolName
  
  const res = await axios.get(`${BASE_URL}/logs`, { params })
  return res.data
}

/**
 * 获取 MCP 工具分类列表
 */
export async function getCategories() {
  const res = await axios.get(`${BASE_URL}/categories`)
  return res.data
}

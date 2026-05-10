/**
 * 场景管理 API 服务
 */

const API_BASE = '/api/v1'

/**
 * 获取枚举选项
 */
export async function getSceneEnums() {
  const resp = await fetch(`${API_BASE}/scenes/enums`)
  return await resp.json()
}

/**
 * 获取场景列表
 */
export async function listScenes(isActive) {
  const url = isActive !== undefined
    ? `${API_BASE}/scenes?isActive=${isActive}`
    : `${API_BASE}/scenes`
  const resp = await fetch(url)
  return await resp.json()
}

/**
 * 获取单个场景详情
 */
export async function getScene(sceneCode) {
  const resp = await fetch(`${API_BASE}/scenes/${encodeURIComponent(sceneCode)}`)
  return await resp.json()
}

/**
 * 创建场景
 */
export async function createScene(sceneData) {
  const resp = await fetch(`${API_BASE}/scenes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sceneData)
  })
  return await resp.json()
}

/**
 * 更新场景
 */
export async function updateScene(sceneCode, sceneData) {
  const resp = await fetch(`${API_BASE}/scenes/${encodeURIComponent(sceneCode)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sceneData)
  })
  return await resp.json()
}

/**
 * 删除场景
 */
export async function deleteScene(sceneCode) {
  const resp = await fetch(`${API_BASE}/scenes/${encodeURIComponent(sceneCode)}`, {
    method: 'DELETE'
  })
  return await resp.json()
}

/**
 * 切换场景启用状态
 */
export async function toggleScene(sceneCode) {
  const resp = await fetch(`${API_BASE}/scenes/${encodeURIComponent(sceneCode)}/toggle`, {
    method: 'PATCH'
  })
  return await resp.json()
}

/**
 * 测试场景识别
 */
export async function testSceneRecognition(userInput) {
  const resp = await fetch(`${API_BASE}/scenes/test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userInput })
  })
  return await resp.json()
}

/**
 * 获取场景统计
 */
export async function getSceneStats() {
  const resp = await fetch(`${API_BASE}/scenes/stats/summary`)
  return await resp.json()
}

/**
 * 列出场景提示词
 */
export async function listScenePrompts() {
  const resp = await fetch(`${API_BASE}/scenes/prompts/list`)
  return await resp.json()
}

/**
 * 获取场景提示词内容
 */
export async function getScenePrompt(promptName) {
  const resp = await fetch(`${API_BASE}/scenes/prompts/${encodeURIComponent(promptName)}`)
  return await resp.json()
}

/**
 * 保存场景提示词
 */
export async function saveScenePrompt(promptFile, content) {
  const resp = await fetch(`${API_BASE}/scenes/prompts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ promptFile, content })
  })
  return await resp.json()
}

/**
 * 删除场景提示词
 */
export async function deleteScenePrompt(promptName) {
  const resp = await fetch(`${API_BASE}/scenes/prompts/${encodeURIComponent(promptName)}`, {
    method: 'DELETE'
  })
  return await resp.json()
}

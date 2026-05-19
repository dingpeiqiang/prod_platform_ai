import { get, post, put, del, patch } from './httpClient'

export async function listScenesTree(isActive) {
  return await get('scenes/tree', {
    params: { isActive }
  })
}

export async function listScenes(isActive) {
  return await get('scenes', {
    params: { isActive }
  })
}

export async function getScene(sceneCode) {
  return await get(`scenes/${encodeURIComponent(sceneCode)}`)
}

export async function createScene(sceneData) {
  return await post('scenes', sceneData, {
    loadingText: '创建场景中...'
  })
}

export async function updateScene(sceneCode, sceneData) {
  return await put(`scenes/${encodeURIComponent(sceneCode)}`, sceneData, {
    loadingText: '更新场景中...'
  })
}

export async function deleteScene(sceneCode) {
  return await del(`scenes/${encodeURIComponent(sceneCode)}`, {
    loadingText: '删除场景中...'
  })
}

export async function toggleScene(sceneCode) {
  return await patch(`scenes/${encodeURIComponent(sceneCode)}/toggle`, {}, {
    loadingText: '切换状态中...'
  })
}

export async function testSceneRecognition(userInput) {
  return await post('scenes/test', { userInput }, {
    loadingText: '识别中...'
  })
}

export async function getSceneStats() {
  return await get('scenes/stats/summary')
}

export async function listScenePrompts() {
  return await get('scenes/prompts/list')
}

export async function getScenePrompt(promptName) {
  return await get(`scenes/prompts/${encodeURIComponent(promptName)}`)
}

export async function saveScenePrompt(promptFile, content) {
  return await post('scenes/prompts', { promptFile, content }, {
    loadingText: '保存提示词中...'
  })
}

export async function deleteScenePrompt(promptName) {
  return await del(`scenes/prompts/${encodeURIComponent(promptName)}`, {
    loadingText: '删除提示词中...'
  })
}
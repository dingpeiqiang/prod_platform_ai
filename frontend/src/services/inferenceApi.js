import axios from 'axios'

const BASE_URL = '/api/v1/llm-config'

export async function getUserConfigs(userId) {
  try {
    const res = await axios.get(`${BASE_URL}/list/${userId}`)
    return res.data
  } catch (e) {
    return { success: false, data: [], message: e.message }
  }
}

export async function saveConfig(config) {
  const res = await axios.post(`${BASE_URL}/save`, config)
  return res.data
}

export async function getActiveConfig(userId) {
  const res = await axios.get(`${BASE_URL}/active/${encodeURIComponent(userId)}`)
  return res.data
}

export async function testConfig(config) {
  const res = await axios.post(`${BASE_URL}/test`, config)
  return res.data
}

export async function deleteConfig(configId) {
  try {
    const res = await axios.delete(`${BASE_URL}/${configId}`)
    return res.data
  } catch (e) {
    return { success: false, message: e.message }
  }
}

export async function activateConfig(userId, configId) {
  try {
    const res = await axios.post(`${BASE_URL}/activate`, {
      user_identifier: userId,
      config_id: configId
    })
    return res.data
  } catch (e) {
    return { success: false, message: e.message }
  }
}

export async function getDefaultModel() {
  const res = await axios.get('/api/v1/chat/model/default')
  return res.data
}
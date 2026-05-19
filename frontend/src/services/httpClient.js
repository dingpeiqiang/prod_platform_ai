import axios from 'axios'

const API_BASE = '/api/v1'

let loadingCount = 0

async function showLoading(text) {
  loadingCount++
  if (loadingCount === 1) {
    const { useLoadingStore } = await import('../stores/loading.js')
    const loadingStore = useLoadingStore()
    loadingStore.show(text)
  }
}

async function hideLoading() {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    const { useLoadingStore } = await import('../stores/loading.js')
    const loadingStore = useLoadingStore()
    loadingStore.hide()
  }
}

const apiClient = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.request.use(
  async (config) => {
    if (config.showLoading !== false) {
      await showLoading(config.loadingText || '加载中...')
    }
    return config
  },
  async (error) => {
    await hideLoading()
    return Promise.reject(error)
  }
)

apiClient.interceptors.response.use(
  async (response) => {
    await hideLoading()
    return response.data
  },
  async (error) => {
    await hideLoading()
    let errorMessage = '请求失败'
    if (error.response) {
      errorMessage = error.response.data?.message || error.response.data?.error || `HTTP Error: ${error.response.status}`
    } else if (error.message) {
      errorMessage = error.message
    }
    console.error('[httpClient] Request failed:', errorMessage)
    return Promise.reject(new Error(errorMessage))
  }
)

export async function request(url, options = {}) {
  const {
    method = 'GET',
    headers = {},
    data,
    params,
    showLoading = true,
    loadingText = '加载中...',
    baseURL = API_BASE
  } = options

  return await apiClient({
    url,
    method,
    headers,
    data,
    params,
    showLoading,
    loadingText,
    baseURL
  })
}

export function get(url, options = {}) {
  return request(url, { ...options, method: 'GET' })
}

export function post(url, data, options = {}) {
  return request(url, { ...options, method: 'POST', data })
}

export function put(url, data, options = {}) {
  return request(url, { ...options, method: 'PUT', data })
}

export function patch(url, data, options = {}) {
  return request(url, { ...options, method: 'PATCH', data })
}

export function del(url, options = {}) {
  return request(url, { ...options, method: 'DELETE' })
}

export default {
  request,
  get,
  post,
  put,
  patch,
  delete: del,
  axios: apiClient
}
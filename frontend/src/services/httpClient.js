import axios from 'axios'
import { useLoadingStore } from '@/stores/loading.js'

const API_BASE = '/api/v1'

let loadingCount = 0
let loadingStore = null

function getLoadingStore() {
  if (!loadingStore) {
    loadingStore = useLoadingStore()
  }
  return loadingStore
}

async function showLoading(text) {
  loadingCount++
  if (loadingCount === 1) {
    const store = getLoadingStore()
    store.show(text)
  }
}

async function hideLoading() {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    const store = getLoadingStore()
    store.hide()
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
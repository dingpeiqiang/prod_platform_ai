import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useLoadingStore } from '@/stores/loading.js'

const API_BASE = '/api/v1'

let loadingCount = 0
let loadingStore = null

// 全局错误去重：同一文案 3 秒内只弹一次，避免并发请求刷屏
let lastToast = { message: '', at: 0 }
const TOAST_DEDUPE_MS = 3000

function showGlobalError(message) {
  const now = Date.now()
  if (message === lastToast.message && now - lastToast.at < TOAST_DEDUPE_MS) {
    return
  }
  lastToast = { message, at: now }
  ElMessage.error(message)
}

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
    'Content-Type': 'application/json',
    'Accept': 'application/json;charset=utf-8'
  },
  responseType: 'json',
  responseEncoding: 'utf8'
})

apiClient.interceptors.request.use(
  async (config) => {
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      // axios 默认 Content-Type: application/json 会导致 Spring 拒绝 multipart
      // 必须彻底删除，让运行时自动带 boundary
      const headers = config.headers
      if (headers) {
        if (typeof headers.delete === 'function') {
          headers.delete('Content-Type')
          headers.delete('content-type')
        } else {
          delete headers['Content-Type']
          delete headers['content-type']
        }
        if (typeof headers.set === 'function') {
          // axios 约定：false 表示不设置该头
          headers.set('Content-Type', false)
        }
      }
    }
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
    // 统一兜底提示：除非请求显式声明 silentError，页面级 catch 不再各自静默
    if (error.config?.silentError !== true) {
      showGlobalError(errorMessage)
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
    silentError = false,
    baseURL = API_BASE
  } = options

  const finalHeaders = { ...headers }
  // FormData 须由浏览器自动带 boundary，不能沿用默认 application/json
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    delete finalHeaders['Content-Type']
    delete finalHeaders['content-type']
  }

  return await apiClient({
    url,
    method,
    headers: finalHeaders,
    data,
    params,
    showLoading,
    loadingText,
    silentError,
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
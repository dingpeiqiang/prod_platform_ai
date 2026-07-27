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
import { useLoadingStore } from '../stores/loading'

const API_BASE = '/api/v1'

let loadingCount = 0
const loadingStore = useLoadingStore()

function showLoading(text) {
  loadingCount++
  if (loadingCount === 1) {
    loadingStore.show(text)
  }
}

function hideLoading() {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    loadingStore.hide()
  }
}

export async function request(url, options = {}) {
  const { 
    method = 'GET', 
    headers = {}, 
    body, 
    params, 
    showLoading: showLoadingOpt = true,
    loadingText = '加载中...',
    baseURL = API_BASE 
  } = options

  const fullUrl = new URL(url.startsWith('/') ? url : `${baseURL}/${url}`)
  
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        fullUrl.searchParams.set(key, value)
      }
    })
  }

  const defaultHeaders = {
    'Content-Type': 'application/json',
    ...headers
  }

  const fetchOptions = {
    method,
    headers: defaultHeaders
  }

  if (body) {
    fetchOptions.body = typeof body === 'string' ? body : JSON.stringify(body)
  }

  if (showLoadingOpt) {
    showLoading(loadingText)
  }

  try {
    const response = await fetch(fullUrl.toString(), fetchOptions)
    
    if (!response.ok) {
      let errorMessage = `HTTP Error: ${response.status}`
      try {
        const errorData = await response.json()
        errorMessage = errorData.message || errorData.error || errorMessage
      } catch {}
      
      throw new Error(errorMessage)
    }

    try {
      return await response.json()
    } catch {
      return response.text()
    }
  } catch (error) {
    console.error('[httpClient] Request failed:', error)
    throw error
  } finally {
    if (showLoadingOpt) {
      hideLoading()
    }
  }
}

export function get(url, options = {}) {
  return request(url, { ...options, method: 'GET' })
}

export function post(url, body, options = {}) {
  return request(url, { ...options, method: 'POST', body })
}

export function put(url, body, options = {}) {
  return request(url, { ...options, method: 'PUT', body })
}

export function patch(url, body, options = {}) {
  return request(url, { ...options, method: 'PATCH', body })
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
  delete: del
}
/**
 * authFetch - 带 Authorization 头的 fetch 封装
 *
 * 工作流执行引擎、流式聊天（SSE）等场景无法走 axios httpClient，
 * 统一使用本封装附带认证 token，保证后端 JwtAuthFilter 校验通过。
 */
let _tokenProvider = null
let _unauthorizedHandler = null

export function setAuthFetchTokenProvider(provider) {
  _tokenProvider = provider
}

export function setAuthFetchUnauthorizedHandler(handler) {
  _unauthorizedHandler = handler
}

function buildHeaders(headers) {
  const merged = { ...headers }
  if (_tokenProvider) {
    const token = _tokenProvider()
    if (token) {
      merged['Authorization'] = `Bearer ${token}`
    }
  }
  return merged
}

export async function authFetch(url, options = {}) {
  const { headers, ...rest } = options
  const response = await fetch(url, { ...rest, headers: buildHeaders(headers) })
  if (response.status === 401 && _unauthorizedHandler) {
    _unauthorizedHandler()
  }
  return response
}

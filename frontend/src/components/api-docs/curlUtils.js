/**
 * cURL 与请求对象互转工具。
 *
 * 请求对象结构（与后端 api-workspace 约定一致）：
 * {
 *   method: 'GET',
 *   url: 'http://localhost:6174/api/v1/xxx',
 *   headers: [{ name, value, enabled }],
 *   params: [{ name, value, enabled }],
 *   body: '' | '{...}',
 *   body_type: 'none' | 'json' | 'text' | 'form',
 * }
 */

/** 把请求对象导出为可粘贴执行的 cURL 命令。 */
export function toCurl(req) {
  const method = (req.method || 'GET').toUpperCase()
  const parts = [`curl -X ${method}`]

  const headers = (req.headers || []).filter((h) => h.enabled !== false && h.name)
  for (const h of headers) {
    parts.push(`  -H ${shellQuote(`${h.name}: ${h.value ?? ''}`)}`)
  }

  if (req.body_type === 'form') {
    for (const pair of parseFormBody(req.body)) {
      parts.push(`  --data-urlencode ${shellQuote(`${pair.name}=${pair.value}`)}`)
    }
  } else if (req.body && req.body_type !== 'none') {
    if (req.body_type === 'json' && !headers.some((h) => h.name.toLowerCase() === 'content-type')) {
      parts.push(`  -H ${shellQuote('Content-Type: application/json')}`)
    }
    parts.push(`  -d ${shellQuote(req.body)}`)
  }

  parts.push(`  ${shellQuote(buildUrlWithParams(req))}`)
  return parts.join(' \\\n')
}

/** 解析 cURL 命令为请求对象；解析失败抛出异常。 */
export function fromCurl(command) {
  const tokens = tokenize(command)
  if (!tokens.length) {
    throw new Error('cURL 命令为空')
  }

  const req = { method: 'GET', url: '', headers: [], params: [], body: '', body_type: 'none' }
  let hasData = false

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    switch (token) {
      case 'curl':
        break
      case '-X':
      case '--request':
        req.method = (tokens[++i] || 'GET').toUpperCase()
        break
      case '-H':
      case '--header': {
        const raw = tokens[++i] || ''
        const idx = raw.indexOf(':')
        if (idx >= 0) {
          req.headers.push({
            name: raw.slice(0, idx).trim(),
            value: raw.slice(idx + 1).trim(),
            enabled: true,
          })
        }
        break
      }
      case '-d':
      case '--data':
      case '--data-raw':
      case '--data-binary':
        req.body = tokens[++i] || ''
        hasData = true
        break
      case '--data-urlencode': {
        const raw = tokens[++i] || ''
        if (!req.body) req.body = raw
        else req.body += `&${raw}`
        hasData = true
        req.body_type = 'form'
        break
      }
      case '-u':
      case '--user': {
        const cred = tokens[++i] || ''
        req.headers.push({
          name: 'Authorization',
          value: `Basic ${btoa(cred)}`,
          enabled: true,
        })
        break
      }
      case '-b':
      case '--cookie':
        req.headers.push({ name: 'Cookie', value: tokens[++i] || '', enabled: true })
        break
      case '--url':
        req.url = tokens[++i] || ''
        break
      case '-F':
      case '--form':
        break
      default:
        if (token.startsWith('-')) break
        if (!req.url) req.url = token
    }
  }

  if (hasData) {
    req.body_type = req.body_type === 'form' ? 'form' : detectBodyType(req.body)
    if (req.method === 'GET') req.method = 'POST'
  }

  // 拆分 URL 中的查询串到 params
  const qIdx = req.url.indexOf('?')
  if (qIdx >= 0) {
    const query = req.url.slice(qIdx + 1)
    req.url = req.url.slice(0, qIdx)
    req.params = query
      .split('&')
      .filter(Boolean)
      .map((pair) => {
        const eq = pair.indexOf('=')
        return {
          name: eq >= 0 ? decodeURIComponent(pair.slice(0, eq)) : decodeURIComponent(pair),
          value: eq >= 0 ? decodeURIComponent(pair.slice(eq + 1)) : '',
          enabled: true,
        }
      })
  }

  return req
}

/** 根据 body 文本推断 body_type。 */
export function detectBodyType(body) {
  if (!body) return 'none'
  try {
    JSON.parse(body)
    return 'json'
  } catch {
    return 'text'
  }
}

/** 拼接带查询参数的完整 URL。 */
export function buildUrlWithParams(req) {
  const base = (req.url || '').trim()
  const qs = (req.params || [])
    .filter((p) => p.enabled !== false && p.name)
    .map((p) => `${encodeURIComponent(p.name)}=${encodeURIComponent(p.value ?? '')}`)
    .join('&')
  if (!qs) return base
  return base.includes('?') ? `${base}&${qs}` : `${base}?${qs}`
}

/** 解析 x-www-form-urlencoded 文本为键值对。 */
function parseFormBody(body) {
  if (!body) return []
  return body
    .split('&')
    .filter(Boolean)
    .map((pair) => {
      const eq = pair.indexOf('=')
      return {
        name: eq >= 0 ? pair.slice(0, eq) : pair,
        value: eq >= 0 ? pair.slice(eq + 1) : '',
      }
    })
}

/** shell 单引号转义。 */
function shellQuote(value) {
  return `'${String(value).replace(/'/g, `'\\''`)}'`
}

/**
 * 将 cURL 命令按 shell 规则切分为 token，支持单引号、双引号、反斜杠续行与行内转义。
 */
function tokenize(command) {
  const tokens = []
  let current = ''
  let quote = null
  let hasToken = false
  const text = String(command).replace(/\\\r?\n/g, ' ')

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quote === "'") {
      if (ch === "'") quote = null
      else current += ch
      continue
    }
    if (quote === '"') {
      if (ch === '"') {
        quote = null
      } else if (ch === '\\' && i + 1 < text.length) {
        current += text[++i]
      } else {
        current += ch
      }
      continue
    }
    if (ch === "'" || ch === '"') {
      quote = ch
      hasToken = true
    } else if (/\s/.test(ch)) {
      if (hasToken) {
        tokens.push(current)
        current = ''
        hasToken = false
      }
    } else if (ch === '\\' && i + 1 < text.length) {
      current += text[++i]
      hasToken = true
    } else {
      current += ch
      hasToken = true
    }
  }
  if (hasToken) tokens.push(current)
  return tokens
}

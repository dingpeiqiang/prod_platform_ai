/**
 * 前端工具函数模块
 * 提供项目中常用的工具函数，消除代码重复
 */

/**
 * 日期处理工具
 */

export function parseDate(dateStr, formats = ['%Y-%m-%d', '%Y/%m/%d', '%Y-%m-%d %H:%M:%S']) {
  for (const fmt of formats) {
    try {
      const parts = dateStr.match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/)
      if (parts) {
        const [, year, month, day, hour = 0, minute = 0, second = 0] = parts.map(Number)
        return new Date(year, month - 1, day, hour, minute, second)
      }
    } catch (e) {
      continue
    }
  }
  return null
}

export function formatDate(date, fmt = 'YYYY-MM-DD') {
  if (!(date instanceof Date)) return ''
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  
  return fmt
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

export function convertDateString(dateStr) {
  if (!dateStr || typeof dateStr !== 'string') return dateStr
  
  const today = new Date()
  const lower = dateStr.trim().toLowerCase()
  
  const mappings = {
    '今天': 0, '今日': 0,
    '明天': 1, '明日': 1,
    '后天': 2,
    '昨天': -1, '昨日': -1,
    '前天': -2
  }
  
  if (mappings[lower] !== undefined) {
    const result = new Date(today)
    result.setDate(today.getDate() + mappings[lower])
    return formatDate(result)
  }
  
  const parsed = parseDate(dateStr)
  return parsed ? formatDate(parsed) : dateStr
}

/**
 * JSON 处理工具
 */

export function safeParseJson(text) {
  if (!text) return null
  
  const trimmed = text.trim()
  
  if (trimmed.startsWith('```')) {
    const firstNl = trimmed.indexOf('\n')
    let content = firstNl !== -1 ? trimmed.slice(firstNl + 1) : trimmed
    if (content.endsWith('```')) {
      content = content.slice(0, -3)
    }
    text = content.trim()
  }
  
  try {
    return JSON.parse(text)
  } catch (e) {
    const start = text.indexOf('{')
    const end = text.lastIndexOf('}')
    if (start !== -1 && end !== -1) {
      try {
        return JSON.parse(text.slice(start, end + 1))
      } catch (e2) {
        return null
      }
    }
    return null
  }
}

export function fixJsonNewlines(jsonStr) {
  const result = []
  let inString = false
  let escapeNext = false
  
  for (const ch of jsonStr) {
    if (escapeNext) {
      result.push(ch)
      escapeNext = false
      continue
    }
    if (ch === '\\') {
      result.push(ch)
      escapeNext = true
      continue
    }
    if (ch === '"' && !escapeNext) {
      inString = !inString
      result.push(ch)
      continue
    }
    if (inString && (ch === '\n' || ch === '\r')) {
      result.push('\\n')
    } else {
      result.push(ch)
    }
  }
  
  return result.join('')
}

export function stripJsonComments(text) {
  return text
    .split('\n')
    .filter(line => !line.trim().startsWith('//'))
    .join('\n')
    .replace(/```json?/g, '')
    .replace(/```/g, '')
    .trim()
}

/**
 * 字符串处理工具
 */

export function truncate(text, maxLen = 200, suffix = '...') {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + suffix : text
}

export function normalizeString(text) {
  return text ? String(text).trim().toLowerCase() : ''
}

export function extractNumbers(text) {
  if (!text) return []
  const matches = text.match(/(\d+(?:\.\d+)?)/g)
  return matches ? matches.map(Number) : []
}

/**
 * 验证工具
 */

export function isEmail(email) {
  if (!email) return false
  const pattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  return pattern.test(email)
}

export function isPhone(phone) {
  if (!phone) return false
  const pattern = /^1[3-9]\d{9}$/
  return pattern.test(String(phone).trim())
}

export function isValidDate(dateStr) {
  return parseDate(dateStr) !== null
}

/**
 * 对象/数组工具
 */

export function deepGet(obj, keys, defaultValue = null) {
  if (!obj || typeof obj !== 'object') return defaultValue
  
  const keyList = Array.isArray(keys) ? keys : keys.split('.')
  let result = obj
  
  for (const key of keyList) {
    if (result === null || result === undefined) return defaultValue
    if (Array.isArray(result)) {
      const index = parseInt(key, 10)
      result = result[index]
    } else {
      result = result[key]
    }
  }
  
  return result !== undefined ? result : defaultValue
}

export function mergeDicts(...dicts) {
  const result = {}
  for (const dict of dicts) {
    if (typeof dict === 'object' && dict !== null) {
      Object.assign(result, dict)
    }
  }
  return result
}

export function removeNullValues(obj) {
  if (typeof obj !== 'object' || obj === null) return obj
  
  const result = {}
  for (const [key, value] of Object.entries(obj)) {
    if (value !== null && value !== undefined) {
      result[key] = value
    }
  }
  return result
}

export function dedupeArray(arr, keyFn) {
  if (!Array.isArray(arr)) return []
  
  const seen = new Set()
  return arr.filter(item => {
    const key = keyFn ? keyFn(item) : item
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

/**
 * 表单工具
 */

export function validateField(field, value) {
  const errors = []
  
  if (field.required) {
    const isEmpty = value === undefined || value === null || value === '' || 
                   (Array.isArray(value) && value.length === 0)
    if (isEmpty) {
      errors.push(`${field.fieldName} 是必填项`)
    }
  }
  
  if (field.fieldType === 'email' && value) {
    if (!isEmail(value)) {
      errors.push(`${field.fieldName} 格式不正确`)
    }
  }
  
  if (field.fieldType === 'phone' && value) {
    if (!isPhone(value)) {
      errors.push(`${field.fieldName} 格式不正确`)
    }
  }
  
  if (field.fieldType === 'number' && value) {
    if (isNaN(Number(value))) {
      errors.push(`${field.fieldName} 必须是数字`)
    }
  }
  
  return errors
}

export function normalizeRecommend(rec) {
  if (typeof rec === 'string') {
    return { value: rec, label: rec, source: 'static', reason: '', confidence: 0 }
  }
  return {
    value: rec.value || '',
    label: rec.label || rec.value || '',
    source: rec.source || 'history',
    reason: rec.reason || '',
    confidence: rec.confidence || 0
  }
}

/**
 * 通用工具
 */

export function generateId(prefix = 'id', length = 12) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = prefix + '_'
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

export function debounce(fn, delay = 300) {
  let timer = null
  return function(...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

export function throttle(fn, limit = 300) {
  let inThrottle = false
  return function(...args) {
    if (!inThrottle) {
      fn.apply(this, args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}
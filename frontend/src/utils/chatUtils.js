export const genId = () => `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

export const stepIcon = (type) => {
  const icons = {
    'thinking': '💭',
    'success': '✅',
    'error': '❌',
    'warning': '⚠️',
    'info': 'ℹ️',
    'loading': '⏳',
    'done': '✅',
    'default': '📋'
  }
  return icons[type] || icons.default
}

export const formatStepResult = (result) => {
  if (!result) return ''
  try {
    return JSON.stringify(result, null, 2)
  } catch {
    return String(result)
  }
}

export const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    const marked = window.marked || (() => ({ parse: (t) => t }))
    let html = marked.parse(text)
    html = html.replace(/<script[^>]*>.*?<\/script>/gi, '')
    html = html.replace(/\son\w+\s*=\s*["'][^"']*["']/gi, '')
    return html
  } catch (error) {
    console.error('Markdown 解析错误:', error)
    return text
  }
}

export const formatMarkdownText = (text) => {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br/>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
}

export const formatTime = (timestamp) => {
  if (!timestamp) return ''
  try {
    const d = new Date(timestamp)
    return d.toLocaleString('zh-CN', { 
      month: '2-digit', 
      day: '2-digit', 
      hour: '2-digit', 
      minute: '2-digit' 
    })
  } catch { 
    return timestamp 
  }
}

export const getFormStatusText = (status) => {
  const statusMap = {
    'filling': '填写中',
    'submitted': '已提交',
    'cancelled': '已取消',
    'draft': '草稿',
    'pending': '待审核',
    'approved': '已通过',
    'rejected': '已拒绝'
  }
  return statusMap[status] || status
}

export const formatVersionTime = (ts) => {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    return d.toLocaleString('zh-CN', { 
      month: '2-digit', 
      day: '2-digit', 
      hour: '2-digit', 
      minute: '2-digit' 
    })
  } catch { 
    return ts 
  }
}
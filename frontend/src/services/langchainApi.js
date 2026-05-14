const BASE = '/api/v1/langchain'

export async function chat(message, options = {}) {
  try {
    console.log('[langchainApi] chat called with:', message)
    const resp = await fetch(`${BASE}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        ...options
      })
    })
    console.log('[langchainApi] chat response status:', resp.status)
    const data = await resp.json()
    console.log('[langchainApi] chat response data:', data)
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] chat failed:', e)
    return { success: false, error: e.message }
  }
}

export async function recognizeIntent(userInput) {
  try {
    const resp = await fetch(`${BASE}/intent/recognize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ user_input: userInput })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] recognizeIntent failed:', e)
    return { success: false, error: e.message }
  }
}

export async function recognizeForm(userInput) {
  try {
    const resp = await fetch(`${BASE}/form/recognize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ user_input: userInput })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] recognizeForm failed:', e)
    return { success: false, error: e.message }
  }
}

export async function extractFields(userInput, formCode) {
  try {
    const resp = await fetch(`${BASE}/form/extract`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ user_input: userInput, form_code: formCode })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] extractFields failed:', e)
    return { success: false, error: e.message }
  }
}

export async function validateForm(formData, formCode) {
  try {
    const resp = await fetch(`${BASE}/form/validate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ form_data: formData, form_code: formCode })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] validateForm failed:', e)
    return { success: false, error: e.message }
  }
}

export async function runFormAgent(userInput, options = {}) {
  try {
    const resp = await fetch(`${BASE}/agent/form`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        user_input: userInput,
        ...options
      })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] runFormAgent failed:', e)
    return { success: false, error: e.message }
  }
}

export async function runTaskAgent(taskType, taskData) {
  try {
    const resp = await fetch(`${BASE}/agent/task`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ task_type: taskType, input_data: taskData })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] runTaskAgent failed:', e)
    return { success: false, error: e.message }
  }
}

export async function runFormWorkflow(userInput, options = {}) {
  try {
    const resp = await fetch(`${BASE}/workflow/form`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        user_input: userInput,
        ...options
      })
    })
    const data = await resp.json()
    if (!resp.ok) {
      return { success: false, error: data }
    }
    return { success: true, ...data }
  } catch (e) {
    console.warn('[langchainApi] runFormWorkflow failed:', e)
    return { success: false, error: e.message }
  }
}

export async function healthCheck() {
  try {
    const resp = await fetch(`${BASE}/health`)
    const data = await resp.json()
    return { success: resp.ok, ...data }
  } catch (e) {
    console.warn('[langchainApi] healthCheck failed:', e)
    return { success: false, error: e.message }
  }
}
<template>
  <div class="api-tester">
    <!-- 请求服务器地址 -->
    <div class="test-row">
      <label class="test-label">服务器</label>
      <input v-model="baseUrl" type="text" class="test-input url-input" placeholder="http://localhost:6174" />
      <span class="final-url" :title="finalUrl">{{ finalUrl }}</span>
    </div>

    <!-- Header 参数 -->
    <div class="test-section" v-if="headerParams.length || true">
      <div class="section-head">
        <h4 class="test-section-title">Header 参数</h4>
        <button type="button" class="mini-btn" @click="addHeader">+ 添加</button>
      </div>
      <div v-for="(h, i) in headerRows" :key="i" class="kv-row">
        <input v-model="h.enabled" type="checkbox" class="kv-check" />
        <input v-model="h.name" type="text" class="test-input kv-key" placeholder="Header 名" />
        <input v-model="h.value" type="text" class="test-input kv-value" placeholder="值" />
        <button type="button" class="kv-remove" @click="headerRows.splice(i, 1)">×</button>
      </div>
      <div v-if="!headerRows.length" class="kv-empty">暂无 Header（Authorization 将由登录态自动注入）</div>
    </div>

    <!-- Path/Query 参数 -->
    <div class="test-section" v-if="paramRows.length">
      <h4 class="test-section-title">请求参数</h4>
      <div v-for="p in paramRows" :key="p.name + p.in" class="kv-row">
        <input v-model="p.enabled" type="checkbox" class="kv-check" :disabled="p.required" :title="p.required ? '必填参数' : ''" />
        <span class="kv-name" :title="p.description">{{ p.name }}</span>
        <span class="kv-in">{{ p.in }}</span>
        <input v-model="p.value" type="text" class="test-input kv-value" :placeholder="p.required ? '必填' : '可选'" />
      </div>
    </div>

    <!-- 请求体 -->
    <div class="test-section" v-if="api.requestBody">
      <div class="section-head">
        <h4 class="test-section-title">请求体 JSON</h4>
        <div class="section-tools">
          <button type="button" class="mini-btn" @click="formatBody">格式化</button>
          <button type="button" class="mini-btn" @click="fillExample">生成示例</button>
        </div>
      </div>
      <textarea
        v-model="requestBody"
        class="body-editor"
        spellcheck="false"
        rows="8"
        placeholder="请求体 JSON"
      />
      <div v-if="bodyError" class="body-error">{{ bodyError }}</div>
    </div>

    <!-- 发送 -->
    <div class="test-actions">
      <button type="button" class="send-btn" :disabled="sending" @click="send">
        <span v-if="sending" class="spinner" />
        {{ sending ? '请求中…' : '发送请求' }}
      </button>
      <button v-if="response" type="button" class="reset-btn" @click="reset">清空响应</button>
    </div>

    <!-- 响应 -->
    <div v-if="response" class="response-section">
      <div class="response-head">
        <h4 class="test-section-title">响应结果</h4>
        <div class="response-meta">
          <span class="status-pill" :class="response.ok ? 'ok' : 'bad'">
            {{ response.status }} {{ response.statusText }}
          </span>
          <span class="latency">{{ response.timeMs }} ms</span>
        </div>
      </div>
      <pre class="response-body">{{ response.text }}</pre>
    </div>

    <div v-if="sendError" class="response-section">
      <div class="response-head">
        <h4 class="test-section-title">请求失败</h4>
      </div>
      <pre class="response-body error">{{ sendError }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useUserStore } from '../../stores/user.js'

const props = defineProps({
  api: { type: Object, required: true },
  schemas: { type: Object, default: () => ({}) },
})

const userStore = useUserStore()

const baseUrl = ref(window.location.origin)
const headerRows = ref([])
const paramRows = ref([])
const requestBody = ref('')
const bodyError = ref('')
const sending = ref(false)
const response = ref(null)
const sendError = ref(null)

const headerParams = computed(() => props.api.parameters?.filter((p) => p.in === 'header') || [])

const finalUrl = computed(() => {
  let path = props.api.path
  for (const p of paramRows.value.filter((x) => x.in === 'path')) {
    path = path.replace(`{${p.name}}`, encodeURIComponent(p.value || `{${p.name}}`))
  }
  const qs = paramRows.value
    .filter((x) => x.in === 'query' && x.enabled && x.value !== '')
    .map((x) => `${encodeURIComponent(x.name)}=${encodeURIComponent(x.value)}`)
    .join('&')
  return `${baseUrl.value.replace(/\/$/, '')}${path}${qs ? '?' + qs : ''}`
})

function initRows() {
  headerRows.value = []
  // 默认带 Authorization（登录态），可编辑
  headerRows.value.push({
    name: 'Authorization',
    value: userStore.token ? `Bearer ${userStore.token}` : '',
    enabled: !!userStore.token,
  })
  paramRows.value = (props.api.parameters || []).map((p) => ({
    name: p.name,
    in: p.in,
    required: p.required,
    description: p.description,
    type: p.type,
    value: '',
    enabled: !!p.required,
  }))
  requestBody.value = props.api.requestBody ? JSON.stringify(buildExample(props.api.requestBody, {}), null, 2) : ''
  bodyError.value = ''
  response.value = null
  sendError.value = null
}

watch(() => props.api.uid, initRows, { immediate: false })
onMounted(initRows)

// 根据 OpenAPI schema 生成示例 JSON
function buildExample(schema, seen, depth = 0) {
  if (!schema || depth > 6) return null
  if (schema.example !== undefined) return schema.example
  if (schema.default !== undefined) return schema.default
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop()
    if (seen[name]) return null
    const next = { ...seen, [name]: true }
    const def = props.schemas[name]
    return def ? buildExample(def, next, depth) : null
  }
  switch (schema.type) {
    case 'object': {
      const obj = {}
      for (const [k, v] of Object.entries(schema.properties || {})) {
        obj[k] = buildExample(v, seen, depth + 1)
      }
      return obj
    }
    case 'array':
      return [buildExample(schema.items, seen, depth + 1)]
    case 'integer':
    case 'number':
      return 0
    case 'boolean':
      return false
    case 'string':
      return schema.enum?.[0] ?? ''
    default:
      return null
  }
}

function fillExample() {
  if (props.api.requestBody) {
    requestBody.value = JSON.stringify(buildExample(props.api.requestBody, {}), null, 2)
  }
}

function formatBody() {
  try {
    requestBody.value = JSON.stringify(JSON.parse(requestBody.value), null, 2)
    bodyError.value = ''
  } catch (e) {
    bodyError.value = `JSON 格式错误: ${e.message}`
  }
}

watch(requestBody, () => {
  if (!requestBody.value.trim()) { bodyError.value = ''; return }
  try { JSON.parse(requestBody.value); bodyError.value = '' } catch { /* 编辑中不提示 */ }
})

function addHeader() {
  headerRows.value.push({ name: '', value: '', enabled: true })
}

function reset() {
  response.value = null
  sendError.value = null
}

async function send() {
  bodyError.value = ''
  response.value = null
  sendError.value = null

  let body = null
  if (props.api.requestBody) {
    try {
      body = JSON.parse(requestBody.value)
    } catch (e) {
      bodyError.value = `请求体 JSON 格式错误: ${e.message}`
      return
    }
  }

  const headers = {}
  for (const h of headerRows.value) {
    if (h.enabled && h.name.trim()) headers[h.name.trim()] = h.value
  }
  // 请求参数中的 header 也合并
  for (const p of paramRows.value.filter((x) => x.in === 'header' && x.enabled && x.value)) {
    headers[p.name] = p.value
  }

  const url = finalUrl.value
  const start = performance.now()
  sending.value = true
  try {
    const res = await fetch(url, {
      method: props.api.method,
      headers,
      body: body !== null ? JSON.stringify(body) : undefined,
    })
    const text = await res.text()
    let pretty = text
    try {
      pretty = JSON.stringify(JSON.parse(text), null, 2)
    } catch { /* 非 JSON 保持原文 */ }
    response.value = {
      status: res.status,
      statusText: res.statusText,
      ok: res.ok,
      timeMs: Math.round(performance.now() - start),
      text: pretty,
    }
  } catch (e) {
    sendError.value = `${e.message}\n\n请检查：\n1. 后端服务是否已启动 (${baseUrl.value})\n2. 服务器地址是否跨域（需与前端同源或后端已配置 CORS）`
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.api-tester { max-width: 860px; }
.test-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.test-label {
  font-size: 12.5px;
  color: #64748b;
  font-weight: 600;
  flex-shrink: 0;
}
.test-input {
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12.5px;
  color: #0f172a;
  outline: none;
  font-family: 'JetBrains Mono', Consolas, monospace;
  transition: border-color 0.15s;
  background: #fff;
}
.test-input:focus { border-color: #7c3aed; }
.url-input { width: 240px; }
.final-url {
  flex: 1;
  min-width: 200px;
  font-size: 11.5px;
  color: #64748b;
  font-family: 'JetBrains Mono', Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f8fafc;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px dashed #e2e8f0;
}
.test-section { margin-bottom: 20px; }
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.section-tools { display: flex; gap: 6px; }
.test-section-title {
  margin: 0;
  font-size: 12.5px;
  font-weight: 700;
  color: #334155;
}
.mini-btn {
  padding: 3px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s;
}
.mini-btn:hover { border-color: #a78bfa; color: #6d28d9; background: #f5f3ff; }
.kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.kv-check { accent-color: #7c3aed; cursor: pointer; }
.kv-name {
  min-width: 120px;
  font-size: 12px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #5b21b6;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kv-in {
  flex-shrink: 0;
  font-size: 10.5px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 1px 7px;
  border-radius: 5px;
}
.kv-value { flex: 1; min-width: 140px; }
.kv-remove {
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: #94a3b8;
  font-size: 15px;
  cursor: pointer;
  border-radius: 6px;
  flex-shrink: 0;
}
.kv-remove:hover { background: #fef2f2; color: #b91c1c; }
.kv-empty {
  font-size: 11.5px;
  color: #94a3b8;
  padding: 6px 2px;
}
.body-editor {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  outline: none;
  resize: vertical;
  transition: border-color 0.15s;
}
.body-editor:focus { border-color: #7c3aed; }
.body-error {
  margin-top: 6px;
  font-size: 12px;
  color: #b91c1c;
  background: #fef2f2;
  padding: 6px 10px;
  border-radius: 7px;
}
.test-actions {
  display: flex;
  gap: 10px;
  margin: 22px 0;
}
.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 26px;
  border: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.send-btn:hover:not(:disabled) { box-shadow: 0 4px 14px rgba(124, 46, 217, 0.35); transform: translateY(-1px); }
.send-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.reset-btn {
  padding: 9px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}
.reset-btn:hover { border-color: #cbd5e1; color: #334155; }
.spinner {
  width: 13px;
  height: 13px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.response-section { margin-top: 4px; }
.response-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.response-meta { display: flex; align-items: center; gap: 10px; }
.status-pill {
  font-size: 11.5px;
  font-weight: 700;
  padding: 3px 12px;
  border-radius: 999px;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.status-pill.ok { background: #ecfdf5; color: #047857; }
.status-pill.bad { background: #fef2f2; color: #b91c1c; }
.latency { font-size: 12px; color: #64748b; }
.response-body {
  margin: 0;
  padding: 14px 16px;
  background: #0f172a;
  border-radius: 10px;
  color: #86efac;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  overflow: auto;
  max-height: 480px;
  white-space: pre-wrap;
  word-break: break-all;
}
.response-body.error { color: #fca5a5; }
</style>

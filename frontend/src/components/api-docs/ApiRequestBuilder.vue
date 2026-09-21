<template>
  <div class="api-builder">
    <!-- 左：请求编辑区 -->
    <div class="builder-main">
      <!-- 请求行 -->
      <div class="request-line">
        <select v-model="method" class="method-select" :class="method.toLowerCase()">
          <option v-for="m in METHODS" :key="m" :value="m">{{ m }}</option>
        </select>
        <input
          v-model="url"
          type="text"
          class="url-input"
          placeholder="https://localhost:6174/api/v1/health"
          @keyup.enter="send"
        />
        <button type="button" class="send-btn" :disabled="sending" @click="send">
          <span v-if="sending" class="spinner" />
          {{ sending ? '请求中…' : '发送' }}
        </button>
        <button type="button" class="save-btn" @click="openSaveDialog">保存</button>
      </div>

      <div class="final-url" :title="finalUrl">{{ finalUrl }}</div>

      <!-- Tab 区 -->
      <div class="req-tabs">
        <button
          v-for="tab in REQ_TABS"
          :key="tab.key"
          type="button"
          class="req-tab"
          :class="{ active: activeReqTab === tab.key }"
          @click="activeReqTab = tab.key"
        >
          {{ tab.label }}
          <span v-if="tab.key === 'headers' && enabledHeaderCount" class="tab-count">{{ enabledHeaderCount }}</span>
          <span v-if="tab.key === 'params' && enabledParamCount" class="tab-count">{{ enabledParamCount }}</span>
        </button>
      </div>

      <div class="req-tab-body">
        <!-- Headers -->
        <div v-if="activeReqTab === 'headers'" class="kv-table">
          <div v-for="(h, i) in headers" :key="i" class="kv-row">
            <input v-model="h.enabled" type="checkbox" class="kv-check" />
            <input v-model="h.name" type="text" class="kv-input kv-key" placeholder="Header 名" />
            <input v-model="h.value" type="text" class="kv-input kv-val" placeholder="值" />
            <button type="button" class="kv-remove" @click="headers.splice(i, 1)">×</button>
          </div>
          <button type="button" class="add-row-btn" @click="headers.push({ name: '', value: '', enabled: true })">
            + 添加 Header
          </button>
        </div>

        <!-- Params -->
        <div v-else-if="activeReqTab === 'params'" class="kv-table">
          <div v-for="(p, i) in paramsList" :key="i" class="kv-row">
            <input v-model="p.enabled" type="checkbox" class="kv-check" />
            <input v-model="p.name" type="text" class="kv-input kv-key" placeholder="参数名" />
            <input v-model="p.value" type="text" class="kv-input kv-val" placeholder="值" />
            <button type="button" class="kv-remove" @click="paramsList.splice(i, 1)">×</button>
          </div>
          <button type="button" class="add-row-btn" @click="paramsList.push({ name: '', value: '', enabled: true })">
            + 添加参数
          </button>
        </div>

        <!-- Body -->
        <div v-else-if="activeReqTab === 'body'">
          <div class="body-type-switch">
            <label v-for="bt in BODY_TYPES" :key="bt.value" class="radio-item">
              <input v-model="bodyType" type="radio" :value="bt.value" />
              <span>{{ bt.label }}</span>
            </label>
            <div class="body-tools" v-if="bodyType !== 'none'">
              <button type="button" class="mini-btn" @click="formatBody">格式化</button>
            </div>
          </div>
          <textarea
            v-if="bodyType !== 'none'"
            v-model="body"
            class="body-editor"
            spellcheck="false"
            :placeholder="bodyType === 'form' ? 'key1=value1&key2=value2' : '请求体内容'"
          />
          <div v-if="bodyError" class="body-error">{{ bodyError }}</div>
        </div>

        <!-- cURL 导入 -->
        <div v-else-if="activeReqTab === 'curl'">
          <p class="curl-hint">粘贴 cURL 命令，自动解析为请求（方法 / 地址 / Header / 参数 / 请求体）。</p>
          <textarea
            v-model="curlInput"
            class="body-editor"
            spellcheck="false"
            rows="8"
            placeholder="curl -X POST 'http://localhost:6174/api/v1/xxx' -H 'Content-Type: application/json' -d '{...}'"
          />
          <div v-if="curlError" class="body-error">{{ curlError }}</div>
          <div class="curl-actions">
            <button type="button" class="mini-btn primary" @click="importCurl">解析并导入</button>
            <button type="button" class="mini-btn" @click="copyCurl">导出当前请求为 cURL</button>
          </div>
        </div>
      </div>

      <!-- 响应区 -->
      <div class="response-area">
        <div v-if="!response && !sendError" class="response-empty">点击「发送」查看响应结果</div>

        <template v-else>
          <div class="response-bar">
            <div class="response-meta">
              <span v-if="response" class="status-pill" :class="response.ok ? 'ok' : 'bad'">
                {{ response.status }} {{ response.statusText }}
              </span>
              <span v-if="response" class="meta-item">{{ response.timeMs }} ms</span>
              <span v-if="response" class="meta-item">{{ response.sizeText }}</span>
            </div>
            <div class="response-actions">
              <button type="button" class="mini-btn" @click="copyResponse">复制响应</button>
              <button type="button" class="mini-btn" @click="downloadResponse">下载</button>
              <button type="button" class="mini-btn" @click="reset">清空</button>
            </div>
          </div>

          <div v-if="response" class="resp-tabs">
            <button
              v-for="tab in RESP_TABS"
              :key="tab.key"
              type="button"
              class="req-tab"
              :class="{ active: activeRespTab === tab.key }"
              @click="activeRespTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <template v-if="response">
            <pre v-if="activeRespTab === 'body'" class="response-body">{{ response.pretty }}</pre>
            <div v-else-if="activeRespTab === 'headers'" class="resp-headers">
              <div v-for="(v, k) in response.headers" :key="k" class="resp-header-row">
                <span class="resp-header-key">{{ k }}</span>
                <span class="resp-header-val">{{ v }}</span>
              </div>
            </div>
            <pre v-else class="response-body raw">{{ response.raw }}</pre>
          </template>

          <pre v-if="sendError" class="response-body error">{{ sendError }}</pre>
        </template>
      </div>
    </div>

    <!-- 右：集合 / 历史 -->
    <aside class="builder-side">
      <div class="side-tabs">
        <button
          type="button"
          class="side-tab"
          :class="{ active: sideTab === 'saved' }"
          @click="sideTab = 'saved'"
        >
          集合
        </button>
        <button
          type="button"
          class="side-tab"
          :class="{ active: sideTab === 'history' }"
          @click="switchHistory"
        >
          历史
        </button>
      </div>

      <div class="side-body">
        <!-- 集合 -->
        <template v-if="sideTab === 'saved'">
          <div class="side-search">
            <input v-model="savedKeyword" type="text" class="search-input" placeholder="搜索已保存请求" />
          </div>
          <div v-if="!filteredSaved.length" class="side-empty">暂无已保存请求</div>
          <div v-for="group in savedGroups" :key="group.name" class="saved-group">
            <div class="saved-group-head">{{ group.name }} ({{ group.items.length }})</div>
            <div
              v-for="item in group.items"
              :key="item.id"
              class="saved-item"
              @click="loadSaved(item)"
            >
              <span class="method-badge mini" :class="(item.method || 'get').toLowerCase()">
                {{ item.method }}
              </span>
              <span class="saved-item-name" :title="item.url">{{ item.name }}</span>
              <button type="button" class="saved-item-del" title="删除" @click.stop="removeSaved(item)">×</button>
            </div>
          </div>
        </template>

        <!-- 历史 -->
        <template v-else>
          <div class="side-toolbar">
            <span class="side-toolbar-text">{{ history.length }} 条记录 · {{ historyGroups.length }} 个接口</span>
            <button type="button" class="mini-btn" :disabled="!history.length" @click="clearAllHistory">清空</button>
          </div>
          <div v-if="!history.length" class="side-empty">暂无请求历史</div>
          <div v-for="group in historyGroups" :key="group.endpoint" class="saved-group">
            <div class="saved-group-head hist-group-head" @click="toggleHistoryGroup(group.endpoint)">
              <span class="hist-caret" :class="{ open: isHistoryGroupOpen(group.endpoint) }">▸</span>
              <span class="hist-endpoint" :title="group.endpoint">{{ group.endpoint }}</span>
              <span class="hist-group-meta">{{ group.methods }} · {{ group.items.length }}</span>
            </div>
            <template v-if="isHistoryGroupOpen(group.endpoint)">
              <div
                v-for="item in group.items"
                :key="item.id"
                class="saved-item"
                @click="loadHistory(item)"
              >
                <span class="method-badge mini" :class="(item.method || 'get').toLowerCase()">
                  {{ item.method }}
                </span>
                <span class="saved-item-name" :title="item.url">{{ item.timeLabel }}</span>
                <span class="hist-status" :class="item.success ? 'ok' : 'bad'">
                  {{ item.status || 'ERR' }}
                </span>
                <button type="button" class="mini-btn" title="导出详情" @click.stop="exportItem(item)">导出</button>
              </div>
            </template>
          </div>
        </template>
      </div>
    </aside>

    <!-- 保存对话框 -->
    <div v-if="saveDialog.visible" class="modal-mask" @click.self="saveDialog.visible = false">
      <div class="modal">
        <h3 class="modal-title">保存请求</h3>
        <label class="modal-field">
          <span class="modal-label">请求名称</span>
          <input v-model="saveDialog.name" type="text" class="modal-input" placeholder="如：健康检查" />
        </label>
        <label class="modal-field">
          <span class="modal-label">所属集合</span>
          <input
            v-model="saveDialog.collectionName"
            type="text"
            class="modal-input"
            list="collection-options"
            placeholder="default"
          />
          <datalist id="collection-options">
            <option v-for="c in collections" :key="c" :value="c" />
          </datalist>
        </label>
        <label class="modal-field">
          <span class="modal-label">备注</span>
          <input v-model="saveDialog.description" type="text" class="modal-input" placeholder="可选" />
        </label>
        <div class="modal-actions">
          <button type="button" class="mini-btn" @click="saveDialog.visible = false">取消</button>
          <button type="button" class="mini-btn primary" :disabled="!saveDialog.name.trim()" @click="confirmSave">
            保存
          </button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useUserStore } from '../../stores/user.js'
import {
  listSavedRequests,
  listCollections,
  createSavedRequest,
  updateSavedRequest,
  deleteSavedRequest,
  listHistory,
  createHistory,
  clearHistory,
  deleteHistory,
  exportHistoryDetail,
} from '../../services/apiWorkspaceApi.js'
import { toCurl, fromCurl, buildUrlWithParams, detectBodyType } from './curlUtils.js'

const METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']
const REQ_TABS = [
  { key: 'headers', label: 'Headers' },
  { key: 'params', label: 'Params' },
  { key: 'body', label: 'Body' },
  { key: 'curl', label: 'cURL' },
]
const RESP_TABS = [
  { key: 'body', label: 'Body' },
  { key: 'headers', label: 'Headers' },
  { key: 'raw', label: 'Raw' },
]
const BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'json', label: 'JSON' },
  { value: 'text', label: 'Text' },
  { value: 'form', label: 'Form' },
]

const userStore = useUserStore()

const method = ref('GET')
const url = ref('')
const headers = ref([])
const paramsList = ref([])
const body = ref('')
const bodyType = ref('none')
const bodyError = ref('')
const curlInput = ref('')
const curlError = ref('')

const activeReqTab = ref('headers')
const activeRespTab = ref('body')
const sending = ref(false)
const response = ref(null)
const sendError = ref(null)

const sideTab = ref('saved')
const savedList = ref([])
const history = ref([])
const collections = ref([])
const savedKeyword = ref('')

const saveDialog = reactive({
  visible: false,
  name: '',
  collectionName: 'default',
  description: '',
  editingId: null,
})

let toastTimer = null
const toast = ref('')

const enabledHeaderCount = computed(() => headers.value.filter((h) => h.enabled && h.name).length)
const enabledParamCount = computed(() => paramsList.value.filter((p) => p.enabled && p.name).length)

const finalUrl = computed(() =>
  buildUrlWithParams({ url: url.value, params: paramsList.value }) || '请输入请求地址'
)

const filteredSaved = computed(() => {
  const kw = savedKeyword.value.trim().toLowerCase()
  if (!kw) return savedList.value
  return savedList.value.filter(
    (r) => (r.name && r.name.toLowerCase().includes(kw)) || (r.url && r.url.toLowerCase().includes(kw))
  )
})

const savedGroups = computed(() => {
  const map = new Map()
  for (const item of filteredSaved.value) {
    const name = item.collection_name || 'default'
    if (!map.has(name)) map.set(name, [])
    map.get(name).push(item)
  }
  return Array.from(map, ([name, items]) => ({ name, items }))
})

/** 提取请求 URL 的接口路径（去掉查询串）作为分组键。 */
function normalizeEndpoint(url = '') {
  const raw = String(url || '').trim()
  const qIdx = raw.indexOf('?')
  return qIdx >= 0 ? raw.slice(0, qIdx) : raw
}

/** 由 createdAt 渲染为时长友好的时间标签。 */
function historyTimeLabel(createdAt) {
  if (!createdAt) return ''
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return String(createdAt)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 请求历史按接口路径分组（倒序加载，组内保持原顺序）。 */
const historyGroups = computed(() => {
  const map = new Map()
  for (const raw of history.value) {
    const item = { ...raw, timeLabel: historyTimeLabel(raw.created_at) }
    const endpoint = normalizeEndpoint(item.url) || '(未知地址)'
    if (!map.has(endpoint)) map.set(endpoint, [])
    map.get(endpoint).push(item)
  }
  return Array.from(map, ([endpoint, items]) => {
    const methods = Array.from(new Set(items.map((i) => i.method || 'GET'))).join('/')
    return { endpoint, methods, items }
  }).sort((a, b) => (a.endpoint < b.endpoint ? -1 : 1))
})

const openHistoryGroups = ref(new Set())

function isHistoryGroupOpen(endpoint) {
  return openHistoryGroups.value.has(endpoint)
}

function toggleHistoryGroup(endpoint) {
  const next = new Set(openHistoryGroups.value)
  if (next.has(endpoint)) next.delete(endpoint)
  else next.add(endpoint)
  openHistoryGroups.value = next
}

function showToast(message) {
  toast.value = message
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 2200)
}

/** 应用一个请求对象到编辑器。 */
function applyRequest(req) {
  method.value = (req.method || 'GET').toUpperCase()
  url.value = req.url || ''
  headers.value = ensureAuthHeader(normalizeKv(req.headers))
  paramsList.value = normalizeKv(req.params)
  body.value = req.body || ''
  bodyType.value = req.body_type || detectBodyType(req.body)
  bodyError.value = ''
  response.value = null
  sendError.value = null
}

function normalizeKv(list) {
  if (!Array.isArray(list)) return []
  return list
    .filter((x) => x && (x.name !== undefined || x.key !== undefined))
    .map((x) => ({
      name: x.name ?? x.key ?? '',
      value: x.value ?? '',
      enabled: x.enabled !== false,
    }))
}

/** 确保 Authorization Header 存在（登录态自动注入）。 */
function ensureAuthHeader(list) {
  const result = [...list]
  if (userStore.token && !result.some((h) => h.name.toLowerCase() === 'authorization')) {
    result.unshift({ name: 'Authorization', value: `Bearer ${userStore.token}`, enabled: true })
  }
  return result
}

// ---------------- 发送请求 ----------------
async function send() {
  bodyError.value = ''
  response.value = null
  sendError.value = null

  if (!url.value.trim()) {
    showToast('请先填写请求地址')
    return
  }

  const requestHeaders = {}
  for (const h of headers.value) {
    if (h.enabled && h.name.trim()) requestHeaders[h.name.trim()] = h.value
  }

  let finalBody
  if (bodyType.value === 'form' || bodyType.value === 'text') {
    finalBody = body.value
    if (bodyType.value === 'form' && !requestHeaders['Content-Type']) {
      requestHeaders['Content-Type'] = 'application/x-www-form-urlencoded'
    }
  } else if (bodyType.value === 'json' && body.value.trim()) {
    try {
      JSON.parse(body.value)
    } catch (e) {
      bodyError.value = `JSON 格式错误: ${e.message}`
      return
    }
    finalBody = body.value
    if (!requestHeaders['Content-Type']) requestHeaders['Content-Type'] = 'application/json'
  }

  const targetUrl = buildUrlWithParams({ url: url.value, params: paramsList.value })
  const start = performance.now()
  sending.value = true
  try {
    const res = await fetch(targetUrl, {
      method: method.value,
      headers: requestHeaders,
      body: ['GET', 'HEAD'].includes(method.value) ? undefined : finalBody,
    })
    const text = await res.text()
    let pretty = text
    try {
      pretty = JSON.stringify(JSON.parse(text), null, 2)
    } catch {
      /* 非 JSON 保持原文 */
    }
    const respHeaders = {}
    res.headers.forEach((v, k) => {
      respHeaders[k] = v
    })
    response.value = {
      status: res.status,
      statusText: res.statusText,
      ok: res.ok,
      timeMs: Math.round(performance.now() - start),
      sizeText: formatSize(new Blob([text]).size),
      pretty,
      raw: text,
      headers: respHeaders,
    }
    activeRespTab.value = 'body'
    recordHistory({ status: res.status, success: res.ok, duration: response.value.timeMs, respBody: text })
  } catch (e) {
    sendError.value = `${e.message}\n\n请检查：\n1. 后端服务是否已启动\n2. 请求地址是否跨域（需同源或后端已配置 CORS）`
    recordHistory({ status: null, success: false, duration: Math.round(performance.now() - start), error: e.message })
  } finally {
    sending.value = false
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/** 记录请求历史（异步写后端，失败不阻塞）。 */
async function recordHistory({ status, success, duration, respBody, error }) {
  try {
    await createHistory({
      method: method.value,
      url: buildUrlWithParams({ url: url.value, params: paramsList.value }),
      headers: headers.value.filter((h) => h.enabled && h.name),
      params: paramsList.value.filter((p) => p.enabled && p.name),
      body: body.value,
      body_type: bodyType.value,
      status,
      success,
      duration_ms: duration,
      response_body: respBody || null,
      error_message: error || null,
    })
    if (sideTab.value === 'history') await refreshHistory()
  } catch {
    /* 历史记录失败静默处理 */
  }
}

// ---------------- 集合 / 历史 ----------------
async function refreshSaved() {
  try {
    const res = await listSavedRequests()
    savedList.value = res?.data || []
  } catch {
    savedList.value = []
  }
}

async function refreshHistory() {
  try {
    const res = await listHistory(100)
    history.value = res?.data || []
  } catch {
    history.value = []
  }
}

async function refreshCollections() {
  try {
    const res = await listCollections()
    collections.value = res?.data || []
  } catch {
    collections.value = []
  }
}

function switchHistory() {
  sideTab.value = 'history'
  refreshHistory()
}

function loadSaved(item) {
  applyRequest(item)
  saveDialog.editingId = item.id
  showToast(`已载入：${item.name}`)
}

function loadHistory(item) {
  applyRequest(item)
  showToast('已回填历史请求')
}

async function exportItem(item) {
  try {
    await exportHistoryDetail(item.id, `api-call-${item.id}.txt`)
    showToast('已导出调用详情')
  } catch (e) {
    showToast(`导出失败：${e.message}`)
  }
}

function openSaveDialog() {
  saveDialog.name = saveDialog.name || `${method.value} ${truncatePath(url.value)}`
  saveDialog.visible = true
  refreshCollections()
}

async function confirmSave() {
  const payload = {
    name: saveDialog.name.trim(),
    collection_name: saveDialog.collectionName.trim() || 'default',
    description: saveDialog.description.trim(),
    method: method.value,
    url: url.value,
    headers: headers.value.filter((h) => h.enabled && h.name),
    params: paramsList.value.filter((p) => p.enabled && p.name),
    body: body.value,
    body_type: bodyType.value,
  }
  try {
    if (saveDialog.editingId) {
      await updateSavedRequest(saveDialog.editingId, payload)
    } else {
      const res = await createSavedRequest(payload)
      saveDialog.editingId = res?.data?.id || null
    }
    saveDialog.visible = false
    await refreshSaved()
    showToast('已保存')
  } catch (e) {
    showToast(`保存失败：${e.message}`)
  }
}

async function removeSaved(item) {
  try {
    await deleteSavedRequest(item.id)
    await refreshSaved()
    showToast('已删除')
  } catch (e) {
    showToast(`删除失败：${e.message}`)
  }
}

async function clearAllHistory() {
  try {
    await clearHistory()
    history.value = []
    showToast('已清空历史')
  } catch (e) {
    showToast(`清空失败：${e.message}`)
  }
}

// ---------------- cURL ----------------
function importCurl() {
  curlError.value = ''
  if (!curlInput.value.trim()) {
    curlError.value = '请粘贴 cURL 命令'
    return
  }
  try {
    const req = fromCurl(curlInput.value)
    applyRequest(req)
    showToast('cURL 已导入')
  } catch (e) {
    curlError.value = `解析失败: ${e.message}`
  }
}

async function copyCurl() {
  const text = toCurl({
    method: method.value,
    url: url.value,
    headers: headers.value,
    params: paramsList.value,
    body: body.value,
    body_type: bodyType.value,
  })
  await copyText(text, 'cURL 已复制')
}

async function copyResponse() {
  if (!response.value) return
  await copyText(response.value.raw, '响应已复制')
}

function downloadResponse() {
  if (!response.value) return
  const blob = new Blob([response.value.raw], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `response-${Date.now()}.txt`
  a.click()
  URL.revokeObjectURL(a.href)
}

async function copyText(text, okMessage) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    showToast(okMessage)
  } catch {
    showToast('复制失败，请手动复制')
  }
}

function formatBody() {
  try {
    body.value = JSON.stringify(JSON.parse(body.value), null, 2)
    bodyError.value = ''
  } catch (e) {
    bodyError.value = `JSON 格式错误: ${e.message}`
  }
}

function reset() {
  response.value = null
  sendError.value = null
}

function truncatePath(value) {
  if (!value) return ''
  try {
    const u = new URL(value)
    return u.pathname
  } catch {
    return value.slice(0, 40)
  }
}

onMounted(() => {
  headers.value = ensureAuthHeader([])
  paramsList.value = [{ name: '', value: '', enabled: true }]
  refreshSaved()
  refreshCollections()
})

defineExpose({ applyRequest })
</script>

<style scoped>
.api-builder {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.builder-main {
  flex: 1;
  min-width: 0;
}
.builder-side {
  width: 300px;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
  position: sticky;
  top: 0;
}

/* 请求行 */
.request-line {
  display: flex;
  gap: 8px;
  align-items: center;
}
.method-select {
  width: 110px;
  flex-shrink: 0;
  padding: 9px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  background: #f8fafc;
  cursor: pointer;
  outline: none;
}
.method-select:focus { border-color: #7c3aed; }
.method-select.post { color: #1d4ed8; }
.method-select.put { color: #b45309; }
.method-select.delete { color: #b91c1c; }
.method-select.patch { color: #7e22ce; }
.url-input {
  flex: 1;
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  font-size: 13px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #0f172a;
  outline: none;
  transition: border-color 0.15s;
}
.url-input:focus { border-color: #7c3aed; }
.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 22px;
  border: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}
.send-btn:hover:not(:disabled) { box-shadow: 0 4px 14px rgba(124, 46, 217, 0.35); transform: translateY(-1px); }
.send-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.save-btn {
  padding: 9px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.save-btn:hover { border-color: #a78bfa; color: #6d28d9; background: #f5f3ff; }
.spinner {
  width: 13px;
  height: 13px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.final-url {
  margin-top: 8px;
  font-size: 11.5px;
  color: #64748b;
  font-family: 'JetBrains Mono', Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f8fafc;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px dashed #e2e8f0;
}

/* Tabs */
.req-tabs, .resp-tabs {
  display: flex;
  gap: 2px;
  margin-top: 16px;
  border-bottom: 1px solid #e2e8f0;
}
.req-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 0;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.15s;
}
.req-tab:hover { color: #0f172a; }
.req-tab.active { color: #6d28d9; border-bottom-color: #7c3aed; font-weight: 600; }
.tab-count {
  font-size: 10.5px;
  background: #ede9fe;
  color: #6d28d9;
  padding: 1px 6px;
  border-radius: 999px;
}
.req-tab-body { padding: 14px 0; }

/* KV 表 */
.kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.kv-check { accent-color: #7c3aed; cursor: pointer; }
.kv-input {
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #0f172a;
  outline: none;
  transition: border-color 0.15s;
}
.kv-input:focus { border-color: #7c3aed; }
.kv-key { width: 220px; flex-shrink: 0; }
.kv-val { flex: 1; min-width: 120px; }
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
.add-row-btn {
  margin-top: 4px;
  padding: 6px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}
.add-row-btn:hover { border-color: #a78bfa; color: #6d28d9; background: #f5f3ff; }

/* Body */
.body-type-switch {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.radio-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: #475569;
  cursor: pointer;
}
.radio-item input { accent-color: #7c3aed; }
.body-tools { margin-left: auto; }
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
  min-height: 160px;
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

/* cURL */
.curl-hint { font-size: 12.5px; color: #64748b; margin: 0 0 10px; }
.curl-actions { display: flex; gap: 8px; margin-top: 10px; }

/* 响应 */
.response-area { margin-top: 8px; }
.response-empty {
  padding: 40px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  background: #fff;
  border: 1px dashed #e2e8f0;
  border-radius: 12px;
}
.response-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 8px;
}
.response-meta { display: flex; align-items: center; gap: 12px; }
.status-pill {
  font-size: 12px;
  font-weight: 700;
  padding: 3px 12px;
  border-radius: 999px;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.status-pill.ok { background: #ecfdf5; color: #047857; }
.status-pill.bad { background: #fef2f2; color: #b91c1c; }
.meta-item { font-size: 12px; color: #64748b; }
.response-actions { display: flex; gap: 6px; }
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
  max-height: 520px;
  white-space: pre-wrap;
  word-break: break-all;
}
.response-body.raw { color: #cbd5e1; }
.response-body.error { color: #fca5a5; }
.resp-headers { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; }
.resp-header-row {
  display: flex;
  gap: 12px;
  padding: 7px 12px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
}
.resp-header-row:last-child { border-bottom: 0; }
.resp-header-key {
  width: 220px;
  flex-shrink: 0;
  color: #5b21b6;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
}
.resp-header-val { color: #475569; word-break: break-all; }

/* 侧栏 */
.side-tabs {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
}
.side-tab {
  flex: 1;
  padding: 11px;
  border: 0;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.side-tab.active { color: #6d28d9; border-bottom-color: #7c3aed; }
.side-body { max-height: 640px; overflow-y: auto; padding: 10px; }
.side-search { margin-bottom: 8px; }
.search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12.5px;
  outline: none;
  background: #f8fafc;
}
.search-input:focus { border-color: #7c3aed; background: #fff; }
.side-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.side-toolbar-text { font-size: 12px; color: #94a3b8; }
.side-empty {
  padding: 28px 12px;
  text-align: center;
  font-size: 12.5px;
  color: #94a3b8;
}
.saved-group { margin-bottom: 10px; }
.saved-group-head {
  font-size: 11.5px;
  font-weight: 700;
  color: #64748b;
  padding: 4px 6px;
}
.saved-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.saved-item:hover { background: #f5f3ff; }
.saved-item-name {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.saved-item-del {
  width: 20px;
  height: 20px;
  border: 0;
  background: transparent;
  color: #cbd5e1;
  font-size: 14px;
  cursor: pointer;
  border-radius: 5px;
  flex-shrink: 0;
}
.saved-item-del:hover { background: #fef2f2; color: #b91c1c; }
.hist-group-head {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  border-radius: 6px;
  user-select: none;
}
.hist-group-head:hover { background: #f1f5f9; }
.hist-caret {
  flex-shrink: 0;
  font-size: 9px;
  color: #94a3b8;
  transition: transform 0.15s;
  display: inline-block;
}
.hist-caret.open { transform: rotate(90deg); }
.hist-endpoint {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
  font-size: 11px;
  color: #334155;
}
.hist-group-meta {
  flex-shrink: 0;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  font-weight: 600;
  color: #94a3b8;
}
.hist-status {
  font-size: 10.5px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 999px;
  flex-shrink: 0;
}
.hist-status.ok { background: #ecfdf5; color: #047857; }
.hist-status.bad { background: #fef2f2; color: #b91c1c; }

/* method 徽标 */
.method-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 800;
  font-family: 'JetBrains Mono', Consolas, monospace;
  padding: 2px 6px;
  border-radius: 5px;
}
.method-badge.mini { font-size: 9px; padding: 1px 5px; min-width: 34px; text-align: center; }
.method-badge.get { background: #ecfdf5; color: #047857; }
.method-badge.post { background: #eff6ff; color: #1d4ed8; }
.method-badge.put { background: #fffbeb; color: #b45309; }
.method-badge.delete { background: #fef2f2; color: #b91c1c; }
.method-badge.patch { background: #faf5ff; color: #7e22ce; }
.method-badge.head, .method-badge.options { background: #f1f5f9; color: #475569; }

/* mini 按钮 */
.mini-btn {
  padding: 5px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.mini-btn:hover:not(:disabled) { border-color: #a78bfa; color: #6d28d9; background: #f5f3ff; }
.mini-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.mini-btn.primary {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  border-color: #7c3aed;
  color: #fff;
  font-weight: 600;
}
.mini-btn.primary:hover:not(:disabled) { box-shadow: 0 3px 10px rgba(124, 46, 217, 0.3); }

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  width: 420px;
  background: #fff;
  border-radius: 14px;
  padding: 22px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
}
.modal-title { margin: 0 0 16px; font-size: 16px; font-weight: 700; color: #0f172a; }
.modal-field { display: block; margin-bottom: 14px; }
.modal-label { display: block; font-size: 12.5px; color: #64748b; margin-bottom: 5px; }
.modal-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 11px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  outline: none;
}
.modal-input:focus { border-color: #7c3aed; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }

/* Toast */
.toast {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  background: #0f172a;
  color: #fff;
  font-size: 13px;
  padding: 9px 20px;
  border-radius: 9px;
  z-index: 1100;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.3);
}

@media (max-width: 1100px) {
  .api-builder { flex-direction: column; }
  .builder-side { width: 100%; position: static; }
}
</style>

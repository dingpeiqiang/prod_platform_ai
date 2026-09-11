<template>
  <div class="api-docs-page">
    <AdminNavBar title="接口管理 · API 文档" />

    <div class="page-body">
      <!-- 左侧：接口导航树 -->
      <aside class="side-panel">
        <div class="side-search">
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索接口路径 / 描述"
            class="search-input"
          />
        </div>
        <div class="side-stats">
          <span>{{ groups.length }}</span> 个模块 · <span>{{ totalApis }}</span> 个接口
        </div>
        <nav class="side-tree">
          <div v-for="group in groups" :key="group.tag" class="tree-group">
            <button type="button" class="group-header" @click="toggleGroup(group.tag)">
              <svg
                class="group-arrow"
                :class="{ collapsed: collapsedGroups.has(group.tag) }"
                width="12" height="12" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"
              >
                <polyline points="6 9 12 15 18 9" />
              </svg>
              <span class="group-name">{{ group.tagName }}</span>
              <span class="group-count">{{ group.apis.length }}</span>
            </button>
            <div v-show="!collapsedGroups.has(group.tag)" class="group-apis">
              <button
                v-for="api in group.apis"
                :key="api.uid"
                type="button"
                class="api-item"
                :class="{ active: selected?.uid === api.uid }"
                @click="selectApi(api)"
              >
                <span class="method-badge" :class="api.method.toLowerCase()">{{ api.method }}</span>
                <span class="api-path" :title="api.path">{{ api.path }}</span>
              </button>
            </div>
          </div>
          <div v-if="groups.length === 0" class="side-empty">
            {{ loading ? '加载中…' : '未找到匹配接口' }}
          </div>
        </nav>
      </aside>

      <!-- 右侧：接口详情 -->
      <main class="detail-panel">
        <template v-if="selected">
          <header class="detail-header">
            <div class="detail-title-row">
              <span class="method-badge large" :class="selected.method.toLowerCase()">{{ selected.method }}</span>
              <h2 class="detail-path">{{ selected.path }}</h2>
            </div>
            <p class="detail-desc">{{ selected.summary || '暂无接口描述' }}</p>
            <div class="detail-meta">
              <span class="meta-chip" v-if="selected.tag">{{ selected.tag }}</span>
              <span class="meta-chip" v-if="selected.operationId">{{ selected.operationId }}</span>
              <span class="meta-chip dep" v-if="selected.deprecated">已废弃</span>
            </div>
            <div class="detail-actions">
              <button type="button" class="action-btn primary" @click="activeTab = 'test'">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polygon points="5 3 19 12 5 21 5 3" />
                </svg>
                在线测试
              </button>
              <div class="tab-switch">
                <button
                  v-for="tab in tabs"
                  :key="tab.key"
                  type="button"
                  class="tab-btn"
                  :class="{ active: activeTab === tab.key }"
                  @click="activeTab = tab.key"
                >
                  {{ tab.label }}
                </button>
              </div>
            </div>
          </header>

          <section class="detail-body">
            <!-- 参数/响应文档 -->
            <template v-if="activeTab === 'doc'">
              <div class="doc-section" v-if="selected.parameters?.length">
                <h3 class="section-title">请求参数</h3>
                <table class="param-table">
                  <thead>
                    <tr>
                      <th style="width: 160px">参数名</th>
                      <th style="width: 90px">位置</th>
                      <th style="width: 90px">类型</th>
                      <th style="width: 60px">必填</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in selected.parameters" :key="p.name + p.in">
                      <td class="param-name">{{ p.name }}</td>
                      <td><span class="loc-chip">{{ p.in }}</span></td>
                      <td class="param-type">{{ p.type }}</td>
                      <td>{{ p.required ? '<span class="req">必填</span>' : '否' }}</td>
                      <td class="param-desc">{{ p.description || '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="doc-section" v-if="requestSchema">
                <h3 class="section-title">请求体 Schema</h3>
                <SchemaTable :schema="requestSchema" :schemas="schemas" />
              </div>

              <div class="doc-section" v-if="responseSchema">
                <h3 class="section-title">响应体 Schema</h3>
                <SchemaTable :schema="responseSchema" :schemas="schemas" />
              </div>

              <div class="doc-section" v-if="!selected.parameters?.length && !requestSchema && !responseSchema">
                <div class="doc-empty">该接口无参数与响应 Schema 定义</div>
              </div>
            </template>

            <!-- 在线测试 -->
            <template v-else>
              <ApiTester :api="selected" :schemas="schemas" />
            </template>
          </section>
        </template>

        <div v-else class="detail-placeholder">
          <div class="placeholder-icon">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="16 18 22 12 16 6" />
              <polyline points="8 6 2 12 8 18" />
            </svg>
          </div>
          <p class="placeholder-title">{{ loading ? '正在加载接口文档…' : '从左侧选择一个接口' }}</p>
          <p class="placeholder-desc">支持查看参数文档、Schema 结构与在线调试</p>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import AdminNavBar from './AdminNavBar.vue'
import ApiTester from './api-docs/ApiTester.vue'

const router = useRouter()

// SchemaTable：递归渲染 OpenAPI schema 的字段表格（含中文描述）
const SchemaTable = (props) => {
  const rows = resolveSchemaRows(props.schema, props.schemas, 0, new Set())
  if (!rows.length) return null
  return h('table', { class: 'param-table nested' }, [
    h('thead', [h('tr', [
      h('th', { style: 'width:200px' }, '字段'),
      h('th', { style: 'width:110px' }, '类型'),
      h('th', { style: 'width:60px' }, '必填'),
      h('th', {}, '说明'),
    ])]),
    h('tbody', rows.map((r) => h('tr', [
      h('td', { class: 'param-name', style: `padding-left:${18 + r.depth * 18}px` },
        r.depth > 0 ? [h('span', { class: 'tree-indent' }, '└ '), r.field] : r.field),
      h('td', { class: 'param-type' }, r.type),
      h('td', {}, r.required ? h('span', { class: 'req' }, '必填') : '否'),
      h('td', { class: 'param-desc' }, r.description || '—'),
    ]))),
  ])
}
SchemaTable.props = { schema: Object, schemas: Object }

function resolveSchemaRows(schema, schemas, depth, seen) {
  if (!schema || depth > 4 || seen.size > 20) return []
  let target = schema
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop()
    if (seen.has(name)) return []
    const next = new Set(seen)
    next.add(name)
    const def = schemas?.[name]
    if (!def) return []
    return resolveSchemaRows(def, schemas, depth, next).map((r) =>
      depth === 0 ? r : { ...r, field: r.depth === depth ? r.field : r.field })
  }
  if (target.type === 'object' || target.properties) {
    const required = target.required || []
    return Object.entries(target.properties || {}).flatMap(([field, sub]) => {
      const rows = [{ field, depth, type: schemaType(sub), required: required.includes(field), description: sub.description || '' }]
      return rows.concat(resolveSchemaRows(sub, schemas, depth + 1, seen))
    })
  }
  if (target.type === 'array' && target.items) {
    return resolveSchemaRows(target.items, schemas, depth, seen)
  }
  if (target.type === 'object') return []
  return [{ field: '(root)', depth, type: schemaType(target), required: false, description: target.description || '' }]
}

function schemaType(s) {
  if (!s) return '—'
  if (s.$ref) return s.$ref.split('/').pop()
  if (s.type === 'array') return `${schemaType(s.items)}[]`
  return s.type === 'object' ? 'object' : (s.enum ? `enum: ${s.enum.join('/')}` : (s.type || '—'))
}

const tabs = [
  { key: 'doc', label: '接口文档' },
  { key: 'test', label: '在线测试' },
]

const loading = ref(true)
const keyword = ref('')
const specs = ref(null)
const activeTab = ref('doc')
const selected = ref(null)
const collapsedGroups = reactive(new Set())

const schemas = computed(() => specs.value?.components?.schemas || {})

const allApis = computed(() => {
  if (!specs.value?.paths) return []
  const list = []
  for (const [path, methods] of Object.entries(specs.value.paths)) {
    for (const [method, op] of Object.entries(methods)) {
      if (!['get', 'post', 'put', 'delete', 'patch'].includes(method)) continue
      list.push({
        uid: `${method}:${path}`,
        method: method.toUpperCase(),
        path,
        summary: op.summary || op.description || '',
        description: op.description || '',
        tag: op.tags?.[0] || '未分组',
        operationId: op.operationId || '',
        deprecated: !!op.deprecated,
        op,
        parameters: (op.parameters || []).map((p) => ({
          name: p.name,
          in: p.in,
          type: p.schema?.type || (p.schema?.$ref ? p.schema.$ref.split('/').pop() : 'string'),
          required: !!p.required,
          description: p.description || '',
        })),
        requestBody: op.requestBody?.content?.['application/json']?.schema || null,
        responses: op.responses || {},
      })
    }
  }
  return list
})

const filteredApis = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return allApis.value
  return allApis.value.filter((a) =>
    a.path.toLowerCase().includes(kw) ||
    (a.summary && a.summary.toLowerCase().includes(kw)) ||
    (a.tag && a.tag.toLowerCase().includes(kw)))
})

const groups = computed(() => {
  const map = new Map()
  for (const api of filteredApis.value) {
    if (!map.has(api.tag)) map.set(api.tag, [])
    map.get(api.tag).push(api)
  }
  return Array.from(map, ([tag, apis]) => ({
    tag,
    tagName: specs.value?.tags?.find((t) => t.name === tag)?.description || tag,
    apis,
  }))
})

const totalApis = computed(() => allApis.value.length)

const requestSchema = computed(() => selected.value?.requestBody)
const responseSchema = computed(() => {
  const ok = selected.value?.responses?.['200'] || selected.value?.responses?.default
  return ok?.content?.['application/json']?.schema || ok?.content?.['*/*']?.schema || null
})

function toggleGroup(tag) {
  if (collapsedGroups.has(tag)) collapsedGroups.delete(tag)
  else collapsedGroups.add(tag)
}

function selectApi(api) {
  selected.value = api
  activeTab.value = 'doc'
}

onMounted(async () => {
  try {
    const res = await fetch('/v3/api-docs')
    specs.value = await res.json()
  } catch (e) {
    console.error('加载 OpenAPI 文档失败', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.api-docs-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f1f5f9;
}
.page-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* ---------- 左侧面板 ---------- */
.side-panel {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  min-height: 0;
}
.side-search {
  padding: 14px 14px 8px;
}
.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  font-size: 13px;
  color: #0f172a;
  outline: none;
  background: #f8fafc;
  transition: border-color 0.15s, background 0.15s;
}
.search-input:focus {
  border-color: #7c3aed;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.08);
}
.side-stats {
  padding: 0 16px 10px;
  font-size: 12px;
  color: #94a3b8;
}
.side-stats span { color: #6d28d9; font-weight: 700; }
.side-tree {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 8px 20px;
}
.tree-group { margin-bottom: 2px; }
.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 700;
  color: #334155;
  text-align: left;
}
.group-header:hover { background: #f8fafc; }
.group-arrow { color: #94a3b8; transition: transform 0.15s; }
.group-arrow.collapsed { transform: rotate(-90deg); }
.group-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.group-count {
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 1px 7px;
  border-radius: 999px;
}
.group-apis { padding: 0 0 4px 10px; }
.api-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  margin-bottom: 1px;
}
.api-item:hover { background: #f5f3ff; }
.api-item.active { background: #ede9fe; }
.api-path {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #475569;
  font-family: 'JetBrains Mono', Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.api-item.active .api-path { color: #5b21b6; font-weight: 600; }
.side-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 12.5px;
  color: #94a3b8;
}

/* ---------- method 徽标 ---------- */
.method-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 800;
  font-family: 'JetBrains Mono', Consolas, monospace;
  padding: 2px 6px;
  border-radius: 5px;
  letter-spacing: 0.02em;
}
.method-badge.get { background: #ecfdf5; color: #047857; }
.method-badge.post { background: #eff6ff; color: #1d4ed8; }
.method-badge.put { background: #fffbeb; color: #b45309; }
.method-badge.delete { background: #fef2f2; color: #b91c1c; }
.method-badge.patch { background: #faf5ff; color: #7e22ce; }
.method-badge.large { font-size: 12px; padding: 4px 10px; border-radius: 7px; }

/* ---------- 右侧详情 ---------- */
.detail-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
.detail-header {
  padding: 24px 32px 0;
}
.detail-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-path {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  font-family: 'JetBrains Mono', Consolas, monospace;
  word-break: break-all;
}
.detail-desc {
  margin: 10px 0 0;
  font-size: 13.5px;
  color: #475569;
  line-height: 1.7;
}
.detail-meta {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}
.meta-chip {
  font-size: 11.5px;
  color: #6d28d9;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  padding: 2px 10px;
  border-radius: 999px;
}
.meta-chip.dep {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}
.detail-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 18px 0 0;
  padding-bottom: 0;
  border-bottom: 1px solid #e2e8f0;
}
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 10px;
}
.action-btn.primary {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  border-color: #7c3aed;
  color: #fff;
  font-weight: 600;
}
.action-btn.primary:hover { box-shadow: 0 4px 14px rgba(124, 46, 217, 0.35); transform: translateY(-1px); }
.tab-switch { display: flex; gap: 4px; }
.tab-btn {
  padding: 10px 16px;
  border: 0;
  background: transparent;
  color: #64748b;
  font-size: 13.5px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.15s;
}
.tab-btn:hover { color: #0f172a; }
.tab-btn.active {
  color: #6d28d9;
  border-bottom-color: #7c3aed;
  font-weight: 600;
}
.detail-body {
  padding: 24px 32px 40px;
}

/* ---------- 文档表格 ---------- */
.doc-section { margin-bottom: 28px; }
.section-title {
  margin: 0 0 10px;
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: #7c3aed;
  border-radius: 2px;
}
.param-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  font-size: 12.5px;
}
.param-table th {
  text-align: left;
  padding: 9px 12px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11.5px;
  font-weight: 600;
  border-bottom: 1px solid #e2e8f0;
}
.param-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: top;
}
.param-table tr:last-child td { border-bottom: 0; }
.param-name {
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #5b21b6;
  font-weight: 600;
  word-break: break-all;
}
.tree-indent { color: #cbd5e1; }
.param-type { color: #0369a1; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 11.5px; }
.param-desc { color: #64748b; line-height: 1.6; }
.loc-chip {
  font-size: 11px;
  color: #475569;
  background: #f1f5f9;
  padding: 1px 8px;
  border-radius: 5px;
}
.req {
  color: #b91c1c;
  font-size: 11.5px;
  font-weight: 600;
}
.doc-empty {
  padding: 24px;
  text-align: center;
  color: #94a3b8;
  font-size: 12.5px;
  background: #fff;
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
}

/* ---------- 空状态 ---------- */
.detail-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #94a3b8;
}
.placeholder-icon { color: #cbd5e1; }
.placeholder-title { margin: 8px 0 0; font-size: 15px; font-weight: 600; color: #64748b; }
.placeholder-desc { margin: 0; font-size: 12.5px; }

/* 覆盖 scoped 限制，让递归子组件样式生效 */
:deep(.param-table.nested) { margin-top: 6px; }

@media (max-width: 900px) {
  .side-panel { width: 240px; }
  .detail-header, .detail-body { padding-left: 18px; padding-right: 18px; }
}
</style>

<template>
  <div class="ontology-manager">
    <header class="ontology-header">
      <div>
        <div class="header-eyebrow">本体管理</div>
        <h1>Schema 可视化与关系网络</h1>
        <p>管理本体 Schema、导入 TTL 文件、可视化展示实体关系网络</p>
      </div>
      <div class="header-actions">
        <el-button @click="goBack"><ArrowLeft /> 返回</el-button>
        <el-button type="primary" @click="loadAll"><Refresh /> 刷新数据</el-button>
      </div>
    </header>

    <el-row :gutter="20" class="ontology-workspace">
      <el-col :xs="24" :lg="4">
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">功能导航</span>
          </template>
          <div class="nav-section">
            <div class="nav-title">本体管理</div>
            <div class="nav-items">
              <button class="nav-item" :class="{ active: activeTab === 'onto-overview' }" @click="activeTab = 'onto-overview'"><span class="nav-icon">📊</span><span class="nav-label">本体概览</span></button>
              <button class="nav-item" :class="{ active: activeTab === 'onto-import' }" @click="activeTab = 'onto-import'"><span class="nav-icon">📥</span><span class="nav-label">TTL导入</span></button>
              <button class="nav-item" :class="{ active: activeTab === 'onto-visual' }" @click="activeTab = 'onto-visual'"><span class="nav-icon">🗺️</span><span class="nav-label">可视化预览</span></button>
              <button class="nav-item" :class="{ active: activeTab === 'onto-classes' }" @click="activeTab = 'onto-classes'"><span class="nav-icon">🏷️</span><span class="nav-label">类结构管理</span></button>
              <button class="nav-item" :class="{ active: activeTab === 'onto-instances' }" @click="activeTab = 'onto-instances'"><span class="nav-icon">🧩</span><span class="nav-label">实例管理</span></button>
              <button class="nav-item" :class="{ active: activeTab === 'onto-sparql' }" @click="activeTab = 'onto-sparql'"><span class="nav-icon">🔎</span><span class="nav-label">SPARQL 查询</span></button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="20">
        <div v-if="activeTab === 'onto-overview'" class="tab-content">
          <div class="tab-header">
            <h2>本体概览</h2>
            <span class="tab-desc">本体库统计信息和结构总览</span>
          </div>
          <div class="stats-grid">
            <el-card class="stat-card">
              <div class="stat-value">{{ ontoStats.classCount }}</div>
              <div class="stat-label">类 (Class)</div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-value">{{ ontoStats.propertyCount }}</div>
              <div class="stat-label">属性 (Property)</div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-value">{{ ontoStats.instanceCount }}</div>
              <div class="stat-label">实例 (Instance)</div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-value">{{ graphData.edgeCount }}</div>
              <div class="stat-label">关系边 (Edge)</div>
            </el-card>
          </div>
          <div style="display: flex; gap: 20px; margin-top: 20px;">
            <el-card class="info-card" style="flex: 1;">
              <template #header><span class="card-title">本体类列表</span></template>
              <div v-if="ontoStats.classes?.length" class="schema-list">
                <el-tag v-for="cls in ontoStats.classes" :key="cls" size="small" class="schema-tag">{{ cls }}</el-tag>
              </div>
              <div v-else class="empty-schema">暂无类</div>
            </el-card>
            <el-card class="info-card" style="flex: 1;">
              <template #header><span class="card-title">本体属性列表</span></template>
              <div v-if="ontoStats.properties?.length" class="schema-list">
                <el-tag v-for="prop in ontoStats.properties" :key="prop" size="small" class="schema-tag">{{ prop }}</el-tag>
              </div>
              <div v-else class="empty-schema">暂无属性</div>
            </el-card>
          </div>
        </div>

        <div v-else-if="activeTab === 'onto-import'" class="tab-content">
          <div class="tab-header">
            <h2>TTL导入</h2>
            <span class="tab-desc">导入 TTL 格式的本体文件，支持合并和替换两种模式</span>
          </div>
          <el-card class="form-card">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span class="card-title">TTL内容</span>
                <label class="file-upload-label">
                  <el-button type="primary" size="small">📁 选择文件</el-button>
                  <input type="file" accept=".ttl,.rdf,.owl" class="file-input" @change="handleFileSelect" />
                </label>
              </div>
            </template>
            <el-form label-width="80px">
              <el-form-item label="导入模式">
                <el-radio-group v-model="importMode">
                  <el-radio label="merge">合并模式（追加到现有本体）</el-radio>
                  <el-radio label="replace">替换模式（清空现有本体）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="TTL内容">
                <el-input v-model="ttlContent" type="textarea" :rows="12" placeholder="请输入TTL格式的本体内容..." style="font-family: monospace; font-size: 13px;" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleImportTtl" :loading="importing">🚀 导入本体</el-button>
                <el-button @click="ttlContent = ''">清空</el-button>
              </el-form-item>
            </el-form>
          </el-card>
          <div v-if="importResult" class="result-card">
            <div class="result-header">
              <span class="result-title">导入结果</span>
              <el-tag :type="importResult.success !== false ? 'success' : 'danger'" size="large">{{ importResult.success !== false ? '导入成功' : '导入失败' }}</el-tag>
            </div>
            <div class="result-body" v-if="importResult.success !== false">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="导入模式">{{ importResult.mode === 'replace' ? '替换模式' : '合并模式' }}</el-descriptions-item>
                <el-descriptions-item label="新增三元组">{{ importResult.addedTriples || 0 }}</el-descriptions-item>
                <el-descriptions-item label="总三元组">{{ importResult.totalTriples || 0 }}</el-descriptions-item>
                <el-descriptions-item label="消息">{{ importResult.message || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <div v-else class="result-body">
              <p style="color: #ef4444;">{{ importResult.message || '导入失败' }}</p>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'onto-visual'" class="tab-content">
          <div class="tab-header">
            <h2>Schema 可视化与关系网络</h2>
            <span class="tab-desc">以图形方式展示本体 Schema 结构和实体间的关系网络</span>
          </div>
          <div style="height: calc(100vh - 280px);">
            <SchemaGraph />
          </div>
        </div>

        <div v-else-if="activeTab === 'onto-classes'" class="tab-content">
          <div class="tab-header">
            <h2>类结构管理</h2>
            <span class="tab-desc">管理本体类和属性定义</span>
          </div>
          <div style="display: flex; gap: 20px;">
            <el-card class="info-card" style="flex: 1;">
              <template #header><span class="card-title">已有类</span></template>
              <div v-if="ontoStats.classes?.length" class="class-list">
                <div v-for="cls in ontoStats.classes" :key="cls" class="class-item">
                  <span class="class-icon">📝</span>
                  <span class="class-name">{{ cls }}</span>
                </div>
              </div>
              <div v-else class="empty-schema">暂无类</div>
            </el-card>
            <el-card class="info-card" style="flex: 1;">
              <template #header><span class="card-title">已有属性</span></template>
              <div v-if="ontoStats.properties?.length" class="class-list">
                <div v-for="prop in ontoStats.properties" :key="prop" class="class-item">
                  <span class="class-icon">🔆</span>
                  <span class="class-name">{{ prop }}</span>
                </div>
              </div>
              <div v-else class="empty-schema">暂无属性</div>
            </el-card>
          </div>
          <el-card class="form-card" style="margin-top: 20px;">
            <template #header><span class="card-title">创建新类</span></template>
            <el-form :model="newClassForm" label-width="80px">
              <el-form-item label="类名"><el-input v-model="newClassForm.name" placeholder="例如: Order" /></el-form-item>
              <el-form-item><el-button type="primary" @click="handleAddClass" :loading="addingClass"><Plus /> 创建类</el-button></el-form-item>
            </el-form>
          </el-card>
          <el-card class="form-card" style="margin-top: 16px;">
            <template #header><span class="card-title">创建新属性</span></template>
            <el-form :model="newPropertyForm" label-width="80px">
              <el-form-item label="属性名"><el-input v-model="newPropertyForm.name" placeholder="例如: orderAmount" /></el-form-item>
              <el-form-item><el-button type="primary" @click="handleAddProperty" :loading="addingProperty"><Plus /> 创建属性</el-button></el-form-item>
            </el-form>
          </el-card>
        </div>

        <div v-else-if="activeTab === 'onto-instances'" class="tab-content">
          <div class="tab-header">
            <h2>实例管理</h2>
            <span class="tab-desc">浏览、创建、编辑和删除本体实例</span>
          </div>
          <el-card class="form-card">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span class="card-title">实例列表</span>
                <el-button type="primary" size="small" @click="showAddInstanceDialog = true"><Plus /> 新建实例</el-button>
              </div>
            </template>
            <el-table :data="Array.isArray(instances) ? instances : []" style="width: 100%" v-loading="loadingInstances" max-height="400">
              <el-table-column prop="uri" label="URI" min-width="200" />
              <el-table-column prop="entityType" label="类型" width="120" />
              <el-table-column label="属性" min-width="200">
                <template #default="{ row }">
                  <el-tag v-for="(val, key) in row" :key="key" size="small" style="margin: 2px" v-if="!['uri','entityId','entityType','source'].includes(key)">{{ key }}={{ val }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" @click="editInstance(row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="handleDeleteInstance(row.uri)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <div v-else-if="activeTab === 'onto-sparql'" class="tab-content">
          <div class="tab-header">
            <h2>SPARQL 查询控制台</h2>
            <span class="tab-desc">编写和执行 SPARQL 查询语句</span>
          </div>
          <el-card class="form-card">
            <el-form label-width="80px">
              <el-form-item label="查询语句">
                <el-input v-model="sparqlQueryText" type="textarea" :rows="6" placeholder="SELECT ?entity ?vipLevel WHERE { ?entity a &lt;http://example.org/Customer&gt; . ?entity vipLevel ?vipLevel }" style="font-family: monospace;" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSparqlQuery" :loading="queryingSparql"><Search /> 执行查询</el-button>
                <el-button @click="sparqlQueryText = ''">清空</el-button>
              </el-form-item>
            </el-form>
          </el-card>
          <div v-if="sparqlResult" class="result-card">
            <div class="result-header">
              <span class="result-title">查询结果</span>
              <span class="result-meta">返回 {{ sparqlResult.length }} 行</span>
            </div>
            <div class="result-body">
              <el-table :data="sparqlResult" style="width: 100%" max-height="400" v-if="sparqlResult.length">
                <el-table-column v-for="col in sparqlResultColumns" :key="col" :prop="col" :label="col" min-width="150" />
              </el-table>
              <div v-else class="empty-result">查询结果为空</div>
            </div>
          </div>
        </div>

        <div v-else class="tab-content tab-empty-safe">
          <div class="empty-state-card">
            <div class="empty-state-title">请选择一个功能模块</div>
            <div class="empty-state-desc">请从左侧导航栏选择需要使用的功能</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="showAddInstanceDialog" title="新建实例" width="500px">
      <el-form :model="newInstanceForm" label-width="80px">
        <el-form-item label="URI"><el-input v-model="newInstanceForm.uri" placeholder="http://example.org/Customer_Wang" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newInstanceForm.type" style="width: 100%;" placeholder="选择类型">
            <el-option v-for="cls in ontoStats.classes" :key="cls" :label="cls" :value="cls" />
          </el-select>
        </el-form-item>
        <el-form-item label="属性">
          <div class="triple-list">
            <div v-for="(prop, index) in newInstanceForm.props" :key="index" class="triple-row">
              <el-input v-model="prop.key" placeholder="属性名" style="width: 120px;" />
              <el-input v-model="prop.value" placeholder="值" style="width: 150px;" />
              <el-button type="danger" size="small" @click="newInstanceForm.props.splice(index, 1)" v-if="newInstanceForm.props.length > 1"><Minus /></el-button>
            </div>
          </div>
          <el-button type="primary" size="small" @click="newInstanceForm.props.push({ key: '', value: '' })" style="margin-top: 8px;"><Plus /> 添加属性</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddInstanceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddInstance" :loading="addingInstance">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditInstanceDialog" title="编辑实例" width="500px">
      <el-form :model="editInstanceForm" label-width="80px">
        <el-form-item label="URI"><el-input v-model="editInstanceForm.uri" disabled /></el-form-item>
        <el-form-item label="属性">
          <div class="triple-list">
            <div v-for="(prop, index) in editInstanceForm.props" :key="index" class="triple-row">
              <el-input v-model="prop.key" placeholder="属性名" style="width: 120px;" />
              <el-input v-model="prop.value" placeholder="值" style="width: 150px;" />
              <el-button type="danger" size="small" @click="editInstanceForm.props.splice(index, 1)" v-if="editInstanceForm.props.length > 1"><Minus /></el-button>
            </div>
          </div>
          <el-button type="primary" size="small" @click="editInstanceForm.props.push({ key: '', value: '' })" style="margin-top: 8px;"><Plus /> 添加属性</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditInstanceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateInstance" :loading="updatingInstance">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Plus, Minus, Search, Refresh } from '@element-plus/icons-vue'
import { getOntologyStats, addOntologyClass, addOntologyProperty, getAllInstances, addInstance, updateInstance, deleteInstance, sparqlQuery, importTtl } from '../services/ontologyReasoningApi.js'
import SchemaGraph from './SchemaGraph.vue'

const emit = defineEmits(['go-back'])
const goBack = () => emit('go-back')

const activeTab = ref('onto-overview')

const ontoStats = reactive({ classCount: 0, propertyCount: 0, instanceCount: 0, classes: [], properties: [] })
const instances = ref([])
const loadingInstances = ref(false)
const addingClass = ref(false)
const addingProperty = ref(false)
const addingInstance = ref(false)
const updatingInstance = ref(false)
const queryingSparql = ref(false)

const showAddInstanceDialog = ref(false)
const showEditInstanceDialog = ref(false)

const newClassForm = reactive({ name: '' })
const newPropertyForm = reactive({ name: '' })
const newInstanceForm = reactive({ uri: '', type: '', props: [{ key: '', value: '' }] })
const editInstanceForm = reactive({ uri: '', props: [] })

const sparqlQueryText = ref('')
const sparqlResult = ref(null)
const sparqlResultColumns = computed(() => !sparqlResult.value?.length ? [] : Object.keys(sparqlResult.value[0]))

const ttlContent = ref('')
const importMode = ref('merge')
const importing = ref(false)
const importResult = ref(null)

const graphData = ref({ nodes: [], edges: [], classCount: 0, propertyCount: 0, instanceCount: 0, edgeCount: 0 })

const loadOntoStats = async () => {
  const stats = await getOntologyStats()
  ontoStats.classCount = stats.classCount || 0
  ontoStats.propertyCount = stats.propertyCount || 0
  ontoStats.instanceCount = stats.instanceCount || 0
  ontoStats.classes = Array.isArray(stats.classes) ? stats.classes : []
  ontoStats.properties = Array.isArray(stats.properties) ? stats.properties : []
}

const loadInstances = async () => {
  loadingInstances.value = true
  try {
    const result = await getAllInstances()
    instances.value = Array.isArray(result) ? result : (Array.isArray(result?.instances) ? result.instances : [])
  } finally {
    loadingInstances.value = false
  }
}

const loadAll = async () => {
  await Promise.all([loadOntoStats(), loadInstances()])
  ElMessage.success('数据刷新完成')
}

const handleAddClass = async () => {
  if (!newClassForm.name.trim()) {
    ElMessage.warning('请输入类名')
    return
  }
  addingClass.value = true
  try {
    const res = await addOntologyClass(newClassForm.name.trim())
    if (res.success !== false) {
      ElMessage.success(res.message || '创建成功')
      newClassForm.name = ''
      await loadOntoStats()
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch {
    ElMessage.error('创建失败')
  } finally {
    addingClass.value = false
  }
}

const handleAddProperty = async () => {
  if (!newPropertyForm.name.trim()) {
    ElMessage.warning('请输入属性名')
    return
  }
  addingProperty.value = true
  try {
    const res = await addOntologyProperty(newPropertyForm.name.trim())
    if (res.success !== false) {
      ElMessage.success(res.message || '创建成功')
      newPropertyForm.name = ''
      await loadOntoStats()
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch {
    ElMessage.error('创建失败')
  } finally {
    addingProperty.value = false
  }
}

const handleAddInstance = async () => {
  if (!newInstanceForm.uri.trim()) {
    ElMessage.warning('请输入 URI')
    return
  }
  addingInstance.value = true
  try {
    const facts = {}
    newInstanceForm.props.forEach(p => {
      if (p.key.trim()) facts[p.key.trim()] = p.value
    })
    const res = await addInstance(newInstanceForm.uri.trim(), newInstanceForm.type, facts)
    if (res.success !== false) {
      ElMessage.success(res.message || '创建成功')
      showAddInstanceDialog.value = false
      newInstanceForm.uri = ''
      newInstanceForm.type = ''
      newInstanceForm.props = [{ key: '', value: '' }]
      await loadInstances()
      await loadOntoStats()
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch {
    ElMessage.error('创建失败')
  } finally {
    addingInstance.value = false
  }
}

const editInstance = (row) => {
  editInstanceForm.uri = row.uri || ''
  const props = []
  Object.entries(row).forEach(([key, val]) => {
    if (!['uri', 'entityId', 'entityType', 'source'].includes(key)) {
      props.push({ key, value: String(val) })
    }
  })
  editInstanceForm.props = props.length ? props : [{ key: '', value: '' }]
  showEditInstanceDialog.value = true
}

const handleUpdateInstance = async () => {
  updatingInstance.value = true
  try {
    const facts = {}
    editInstanceForm.props.forEach(p => {
      if (p.key.trim()) facts[p.key.trim()] = p.value
    })
    const res = await updateInstance(editInstanceForm.uri, facts)
    if (res.success !== false) {
      ElMessage.success(res.message || '更新成功')
      showEditInstanceDialog.value = false
      await loadInstances()
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } catch {
    ElMessage.error('更新失败')
  } finally {
    updatingInstance.value = false
  }
}

const handleDeleteInstance = async (uri) => {
  try {
    await ElMessageBox.confirm(`确定删除实例 ${uri} 吗？`, '确认删除', { type: 'warning' })
    const res = await deleteInstance(uri)
    if (res.success !== false) {
      ElMessage.success(res.message || '删除成功')
      await loadInstances()
      await loadOntoStats()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
  }
}

const handleSparqlQuery = async () => {
  if (!sparqlQueryText.value.trim()) {
    ElMessage.warning('请输入 SPARQL 查询语句')
    return
  }
  queryingSparql.value = true
  try {
    const res = await sparqlQuery(sparqlQueryText.value)
    sparqlResult.value = res.results || []
    if (sparqlResult.value.length) {
      ElMessage.success(`查询完成，返回 ${sparqlResult.value.length} 行`)
    } else {
      ElMessage.info('查询结果为空')
    }
  } catch {
    ElMessage.error('查询失败')
  } finally {
    queryingSparql.value = false
  }
}

const handleImportTtl = async () => {
  if (!ttlContent.value.trim()) {
    ElMessage.warning('请输入 TTL 内容或选择文件')
    return
  }
  importing.value = true
  try {
    const res = await importTtl(ttlContent.value, importMode.value === 'replace')
    importResult.value = res
    if (res.success !== false) {
      ElMessage.success(res.message || '导入成功')
      await loadOntoStats()
      await loadInstances()
    } else {
      ElMessage.error(res.message || '导入失败')
    }
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

const handleFileSelect = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    ttlContent.value = String(e.target?.result || '')
  }
  reader.readAsText(file)
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.ontology-manager { padding: 24px; background: linear-gradient(180deg, #f8faff 0%, #ffffff 100%); min-height: 100%; }
.ontology-header { display: flex; justify-content: space-between; gap: 24px; padding: 24px; border-radius: 20px; background: #0f172a; color: #fff; margin-bottom: 20px; }
.header-eyebrow { color: #93c5fd; text-transform: uppercase; letter-spacing: .12em; font-size: 12px; margin-bottom: 8px; }
.ontology-header h1 { margin: 0 0 8px; font-size: 28px; }
.ontology-header p { margin: 0; max-width: 600px; color: rgba(255,255,255,.78); line-height: 1.8; }
.header-actions { display: flex; gap: 12px; align-items: flex-start; }

.sidebar-card { border-radius: 18px; }
.sidebar-title { font-weight: 600; }
.nav-section { margin-top: 16px; }
.nav-title { font-size: 12px; color: #64748b; font-weight: 600; text-transform: uppercase; letter-spacing: .08em; margin-bottom: 8px; }
.nav-items { display: flex; flex-direction: column; gap: 4px; }
.nav-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: transparent; border: none; border-radius: 8px; cursor: pointer; transition: all .2s; text-align: left; font-size: 14px; color: #475569; }
.nav-item:hover { background: #f3f4f6; }
.nav-item.active { background: #eff6ff; color: #1d4ed8; font-weight: 500; }
.nav-icon { font-size: 16px; }
.nav-label { flex: 1; }

.tab-content { min-height: 500px; }
.tab-header { margin-bottom: 16px; }
.tab-header h2 { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
.tab-desc { color: #64748b; font-size: 14px; }

.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.stat-card { border-radius: 12px; text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; color: #1d4ed8; }
.stat-label { color: #64748b; margin-top: 4px; font-size: 13px; }

.info-card { border-radius: 12px; }
.card-title { font-weight: 600; font-size: 14px; }
.form-card { border-radius: 12px; }

.schema-list { display: flex; flex-wrap: wrap; gap: 8px; }
.schema-tag { background: #f0f9ff; color: #0369a1; }

.class-list { display: flex; flex-direction: column; gap: 8px; }
.class-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: #f9fafb; border-radius: 8px; }
.class-icon { font-size: 16px; }
.class-name { font-family: monospace; font-size: 13px; color: #374151; }

.file-upload-label { position: relative; overflow: hidden; cursor: pointer; }
.file-input { position: absolute; top: 0; left: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }

.result-card { margin-top: 16px; padding: 16px; background: #f9fafb; border-radius: 12px; }
.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.result-title { font-weight: 600; font-size: 14px; }
.result-meta { color: #64748b; font-size: 13px; }
.result-body { font-size: 14px; line-height: 1.6; }

.empty-schema { color: #9ca3af; text-align: center; padding: 24px; font-size: 14px; }
.empty-result { color: #9ca3af; text-align: center; padding: 24px; font-size: 14px; }

.tab-empty-safe { display: flex; align-items: center; justify-content: center; }
.empty-state-card { text-align: center; padding: 48px; }
.empty-state-title { font-size: 18px; font-weight: 600; color: #374151; margin-bottom: 8px; }
.empty-state-desc { color: #64748b; font-size: 14px; }

.triple-list { display: flex; flex-direction: column; gap: 8px; }
.triple-row { display: flex; align-items: center; gap: 8px; }

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .ontology-manager { padding: 14px; }
  .ontology-header { flex-direction: column; }
  .stats-grid { grid-template-columns: 1fr; }
}
</style>

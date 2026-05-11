<template>
  <div class="ontology-manager">
    <div class="header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1 class="page-title">本体管理</h1>
      </div>
      <el-button type="primary" @click="openCreateModal">
        <el-icon><Plus /></el-icon>
        新建本体
      </el-button>
    </div>

    <div class="main-content">
      <div class="list-section">
        <div class="list-header">
          <h3>本体列表</h3>
        </div>
        <div class="list-search">
          <el-select v-model="filterCategory" @change="loadOntologies" placeholder="选择分类" clearable size="small" style="width: 100%">
            <el-option value="">全部分类</el-option>
            <el-option v-for="cat in categories" :key="cat.code" :value="cat.code" :label="cat.name" />
          </el-select>
        </div>
        <div class="list-container">
          <el-empty v-if="loading" description="加载中..." />
          <template v-else>
            <div v-if="ontologies.length === 0" class="empty-state">
              <el-empty description="暂无本体数据" />
            </div>
            <div v-else class="ontology-list">
              <div
                v-for="ontology in ontologies"
                :key="ontology.ontologyCode"
                class="ontology-item"
                :class="{ active: selectedCode === ontology.ontologyCode }"
                @click="selectOntology(ontology)"
              >
                <div class="item-header">
                  <span class="item-name">{{ ontology.ontologyName }}</span>
                  <div class="item-status">
                    <el-tag :type="ontology.isActive ? 'success' : 'info'" size="small">
                      {{ ontology.isActive ? '启用' : '禁用' }}
                    </el-tag>
                    <el-tag type="info" size="small">v{{ ontology.version }}</el-tag>
                  </div>
                </div>
                <div class="item-code">{{ ontology.ontologyCode }}</div>
                <div class="item-desc">{{ ontology.description || '暂无描述' }}</div>
                <div class="item-meta">
                  <span class="meta-item">
                    <el-icon><Box /></el-icon>
                    {{ ontology.entities?.length || 0 }} 个实体
                  </span>
                  <span class="meta-item">{{ formatDate(ontology.updatedAt) }}</span>
                </div>
                <div class="item-actions">
                  <el-button size="small" @click.stop="toggleActive(ontology)">
                    {{ ontology.isActive ? '停用' : '启用' }}
                  </el-button>
                  <el-button size="small" type="danger" @click.stop="confirmDelete(ontology)">
                    删除
                  </el-button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="detail-section">
        <div v-if="loading" class="loading-detail">
          <el-empty description="加载中..." />
        </div>
        <template v-else>
          <div v-if="!selectedOntology" class="empty-state">
            <div class="empty-content">
              <el-icon size="64"><Document /></el-icon>
              <p class="empty-text">请从左侧选择一个本体查看详情</p>
            </div>
          </div>
          <div v-else-if="editingData && editingData.ontologyCode" class="ontology-detail">
            <div class="detail-header">
              <div class="header-title">
                <el-icon size="32"><Box /></el-icon>
                <h2>{{ selectedOntology.ontologyName }}</h2>
              </div>
              <div class="header-actions">
                <el-button @click="resetEditing" :disabled="saving">
                  <el-icon><RefreshLeft /></el-icon>
                  重置
                </el-button>
                <el-button type="primary" @click="saveOntology" :disabled="saving || !hasChanges" :loading="saving">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
              </div>
            </div>

            <el-tabs v-model="activeTab" class="detail-tabs">
              <el-tab-pane label="基本信息" name="basic">
                <el-card class="info-card">
                  <el-form :model="editingData" label-width="100px">
                    <el-form-item label="名称">
                      <el-input v-model="editingData.ontologyName" placeholder="请输入名称" />
                    </el-form-item>
                    <el-form-item label="编码">
                      <el-input v-model="editingData.ontologyCode" disabled placeholder="编码" />
                    </el-form-item>
                    <el-form-item label="分类">
                      <el-select v-model="editingData.category" placeholder="选择分类" style="width: 100%">
                        <el-option v-for="cat in categories" :key="cat.code" :value="cat.code" :label="cat.name" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="描述">
                      <el-input v-model="editingData.description" type="textarea" :rows="3" placeholder="请输入描述" />
                    </el-form-item>
                  </el-form>
                </el-card>
              </el-tab-pane>

              <el-tab-pane label="实体结构" name="entities">
                <el-card class="info-card">
                  <template #header>
                    <div class="card-header">
                      <span class="card-title">实体列表</span>
                      <el-button type="primary" size="small" @click="addEntity">
                        <el-icon><Plus /></el-icon>
                        新增实体
                      </el-button>
                    </div>
                  </template>
                  <div v-if="!editingData.entities || editingData.entities.length === 0" class="empty-entities">
                    <el-empty description="暂无实体，点击上方按钮新增" />
                  </div>
                  <el-collapse v-else accordion>
                    <el-collapse-item v-for="(entity, entityIndex) in editingData.entities" :key="entityIndex">
                      <template #title>
                        <div class="entity-title">
                          <span class="entity-name">{{ entity.entityName || '未命名实体' }}</span>
                          <span class="entity-code">{{ entity.entityCode || '无编码' }}</span>
                          <span class="entity-count">{{ entity.fields?.length || 0 }} 个字段</span>
                          <div class="entity-actions">
                            <el-button size="small" @click.stop="addField(entity)">
                              <el-icon><Plus /></el-icon>
                            </el-button>
                            <el-button size="small" type="danger" @click.stop="removeEntity(entityIndex)">
                              <el-icon><Delete /></el-icon>
                            </el-button>
                          </div>
                        </div>
                      </template>
                      <div class="entity-content">
                        <el-form :model="entity" label-width="80px" size="small" style="margin-bottom: 16px;">
                          <el-row :gutter="16">
                            <el-col :span="12">
                              <el-form-item label="编码">
                                <el-input v-model="entity.entityCode" placeholder="实体编码" />
                              </el-form-item>
                            </el-col>
                            <el-col :span="12">
                              <el-form-item label="名称">
                                <el-input v-model="entity.entityName" placeholder="实体名称" />
                              </el-form-item>
                            </el-col>
                          </el-row>
                        </el-form>
                        <div class="fields-section">
                          <div class="fields-header">
                            <span>字段列表</span>
                            <el-button type="primary" size="small" @click="addField(entity)">
                              <el-icon><Plus /></el-icon>
                              新增字段
                            </el-button>
                          </div>
                          <el-table v-if="entity.fields && entity.fields.length > 0" :data="entity.fields" size="small" border>
                            <el-table-column prop="fieldCode" label="编码" width="150">
                              <template #default="{ row }">
                                <el-input v-model="row.fieldCode" size="small" placeholder="编码" />
                              </template>
                            </el-table-column>
                            <el-table-column prop="fieldName" label="名称" width="150">
                              <template #default="{ row }">
                                <el-input v-model="row.fieldName" size="small" placeholder="名称" />
                              </template>
                            </el-table-column>
                            <el-table-column prop="fieldType" label="类型" width="120">
                              <template #default="{ row }">
                                <el-select v-model="row.fieldType" size="small" @change="handleTypeChange(row)" style="width: 100%">
                                  <el-option value="string">字符串</el-option>
                                  <el-option value="input">文本</el-option>
                                  <el-option value="textarea">多行</el-option>
                                  <el-option value="number">数字</el-option>
                                  <el-option value="integer">整数</el-option>
                                  <el-option value="boolean">布尔</el-option>
                                  <el-option value="date">日期</el-option>
                                  <el-option value="datetime">日期时间</el-option>
                                  <el-option value="email">邮箱</el-option>
                                  <el-option value="phone">手机</el-option>
                                  <el-option value="enum">枚举</el-option>
                                  <el-option value="object">对象</el-option>
                                  <el-option value="array">数组</el-option>
                                </el-select>
                              </template>
                            </el-table-column>
                            <el-table-column prop="required" label="必填" width="80">
                              <template #default="{ row }">
                                <el-switch v-model="row.required" size="small" />
                              </template>
                            </el-table-column>
                            <el-table-column prop="ruleDescription" label="规则描述">
                              <template #default="{ row }">
                                <el-input v-model="row.ruleDescription" size="small" placeholder="规则描述" />
                              </template>
                            </el-table-column>
                            <el-table-column label="操作" width="180" fixed="right">
                              <template #default="{ row, $index }">
                                <el-button v-if="row.fieldType === 'object' || row.fieldType === 'array'" size="small" @click="toggleFieldExpanded(row)">
                                  {{ row.expanded ? '收起' : '展开' }}
                                </el-button>
                                <el-button size="small" type="danger" @click="removeField(entity, $index)">
                                  <el-icon><Delete /></el-icon>
                                </el-button>
                              </template>
                            </el-table-column>
                          </el-table>
                          <el-empty v-else description="暂无字段" />

                          <template v-for="(field, fieldIndex) in (entity.fields || [])" :key="fieldIndex">
                            <div v-if="field && field.expanded" class="expanded-field">
                              <el-divider>{{ field.fieldName || field.fieldCode || '字段' }} 配置</el-divider>
                              <div v-if="field.fieldType === 'object'" class="object-config">
                                <div class="config-header">
                                  <span>对象属性</span>
                                  <el-button size="small" @click="addObjectProperty(field)">
                                    <el-icon><Plus /></el-icon>
                                    添加属性
                                  </el-button>
                                </div>
                                <el-table v-if="field.properties && field.properties.length > 0" :data="field.properties" size="small" border>
                                  <el-table-column prop="fieldCode" label="编码" width="150">
                                    <template #default="{ row }">
                                      <el-input v-model="row.fieldCode" size="small" placeholder="编码" />
                                    </template>
                                  </el-table-column>
                                  <el-table-column prop="fieldName" label="名称" width="150">
                                    <template #default="{ row }">
                                      <el-input v-model="row.fieldName" size="small" placeholder="名称" />
                                    </template>
                                  </el-table-column>
                                  <el-table-column prop="fieldType" label="类型" width="120">
                                    <template #default="{ row }">
                                      <el-select v-model="row.fieldType" size="small" style="width: 100%">
                                        <el-option value="string">字符串</el-option>
                                        <el-option value="number">数字</el-option>
                                        <el-option value="boolean">布尔</el-option>
                                        <el-option value="object">对象</el-option>
                                      </el-select>
                                    </template>
                                  </el-table-column>
                                  <el-table-column prop="required" label="必填" width="80">
                                    <template #default="{ row }">
                                      <el-switch v-model="row.required" size="small" />
                                    </template>
                                  </el-table-column>
                                  <el-table-column label="操作" width="80" fixed="right">
                                    <template #default="{ $index }">
                                      <el-button size="small" type="danger" @click="removeObjectProperty(field, $index)">
                                        <el-icon><Delete /></el-icon>
                                      </el-button>
                                    </template>
                                  </el-table-column>
                                </el-table>
                                <el-empty v-else description="暂无属性" />
                              </div>
                              <div v-if="field.fieldType === 'array'" class="array-config">
                                <div class="config-header">
                                  <span>数组项配置</span>
                                </div>
                                <el-form :model="(field.items || {})" label-width="80px" size="small" style="margin-bottom: 16px;">
                                  <el-form-item label="项类型">
                                    <el-select v-model="field.items.fieldType" size="small" style="width: 200px;">
                                      <el-option value="string">字符串</el-option>
                                      <el-option value="number">数字</el-option>
                                      <el-option value="integer">整数</el-option>
                                      <el-option value="boolean">布尔</el-option>
                                      <el-option value="object">对象</el-option>
                                    </el-select>
                                  </el-form-item>
                                </el-form>
                                <div v-if="field.items && field.items.fieldType === 'object'" class="array-object-config">
                                  <div class="config-header">
                                    <span>对象属性</span>
                                    <el-button size="small" @click="addArrayItemProperty(field)">
                                      <el-icon><Plus /></el-icon>
                                      添加属性
                                    </el-button>
                                  </div>
                                  <el-table v-if="field.items.properties && field.items.properties.length > 0" :data="field.items.properties" size="small" border>
                                    <el-table-column prop="fieldCode" label="编码" width="150">
                                      <template #default="{ row }">
                                        <el-input v-model="row.fieldCode" size="small" placeholder="编码" />
                                      </template>
                                    </el-table-column>
                                    <el-table-column prop="fieldName" label="名称" width="150">
                                      <template #default="{ row }">
                                        <el-input v-model="row.fieldName" size="small" placeholder="名称" />
                                      </template>
                                    </el-table-column>
                                    <el-table-column prop="fieldType" label="类型" width="120">
                                      <template #default="{ row }">
                                        <el-select v-model="row.fieldType" size="small" style="width: 100%">
                                          <el-option value="string">字符串</el-option>
                                          <el-option value="number">数字</el-option>
                                          <el-option value="boolean">布尔</el-option>
                                          <el-option value="object">对象</el-option>
                                        </el-select>
                                      </template>
                                    </el-table-column>
                                    <el-table-column prop="required" label="必填" width="80">
                                      <template #default="{ row }">
                                        <el-switch v-model="row.required" size="small" />
                                      </template>
                                    </el-table-column>
                                    <el-table-column label="操作" width="80" fixed="right">
                                      <template #default="{ $index }">
                                        <el-button size="small" type="danger" @click="removeArrayItemProperty(field, $index)">
                                          <el-icon><Delete /></el-icon>
                                        </el-button>
                                      </template>
                                    </el-table-column>
                                  </el-table>
                                  <el-empty v-else description="暂无属性" />
                                </div>
                              </div>
                            </div>
                          </template>
                        </div>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </el-card>
              </el-tab-pane>

              <el-tab-pane label="JSON 配置" name="json">
                <el-card class="info-card">
                  <template #header>
                    <span class="card-title">JSON 配置</span>
                  </template>
                  <el-input
                    v-model="entitiesJson"
                    type="textarea"
                    :rows="20"
                    placeholder="JSON格式的本体配置"
                  />
                  <div class="json-hint">在此处直接编辑JSON，更改会同步到上面的表单中</div>
                </el-card>
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
      </div>
    </div>

    <el-dialog
      v-model="createDialogVisible"
      title="新建本体"
      width="500px"
    >
      <el-form :model="createData" label-width="80px">
        <el-form-item label="编码" required>
          <el-input v-model="createData.ontologyCode" placeholder="例如：customer_service" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createData.ontologyName" placeholder="例如：客户服务" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="createData.category" placeholder="选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.code" :value="cat.code" :label="cat.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createData.description" type="textarea" :rows="3" placeholder="简单描述用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, ArrowLeft, Box, Document, RefreshLeft, Check } from '@element-plus/icons-vue'
import { useLoadingStore } from '../stores/loading'
import * as ontologyApi from '../services/ontologyApi'

const emit = defineEmits(['go-back'])
const goBack = () => { emit('go-back') }

const loadingStore = useLoadingStore()

const loading = ref(false)
const saving = ref(false)
const ontologies = ref([])
const categories = ref([])
const filterCategory = ref('')
const selectedCode = ref(null)
const selectedOntology = ref(null)
const editingData = ref({})
const originalData = ref({})
const createDialogVisible = ref(false)
const createData = ref({})
const activeTab = ref('basic')

const hasChanges = computed(() => {
  if (!selectedOntology.value) return false
  return JSON.stringify(editingData.value) !== JSON.stringify(originalData.value)
})

const entitiesJson = computed({
  get() {
    if (editingData.value.entities) {
      return JSON.stringify(editingData.value.entities, null, 2)
    }
    return ''
  },
  set(val) {
    try {
      const parsed = JSON.parse(val)
      if (Array.isArray(parsed)) {
        editingData.value.entities = parsed
      } else {
        ElMessage.warning('JSON 格式错误：需要是数组格式')
      }
    } catch (e) {
      // 忽略解析错误，用户正在输入
    }
  }
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN')
}

const loadCategories = async () => {
  try {
    const res = await ontologyApi.getOntologyCategories()
    if (res.success) categories.value = res.data
  } catch (e) {
    console.error('加载分类失败:', e)
  }
}

const loadOntologies = async () => {
  loading.value = true
  try {
    const res = await ontologyApi.listOntologies(filterCategory.value || undefined, undefined)
    if (res.success) ontologies.value = res.data || []
    else ElMessage.error(res.message || '加载本体列表失败')
  } catch (e) {
    ElMessage.error('加载本体列表失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

const selectOntology = async (ontology) => {
  if (hasChanges.value) {
    try {
      await ElMessageBox.confirm('当前有未保存的更改，切换将丢失更改。是否继续？', '确认切换', {
        confirmButtonText: '继续',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
  }

  loadingStore.show('加载本体详情...')
  try {
    const res = await ontologyApi.getOntology(ontology.ontologyCode)
    if (res.success) {
      const data = res.data
      // 确保数据结构正确初始化
      if (!data.entities) {
        data.entities = []
      }
      data.entities.forEach(entity => {
        if (!entity.fields) {
          entity.fields = []
        }
        entity.fields.forEach(field => {
          field.expanded = false
          if (field.fieldType === 'object' && !field.properties) {
            field.properties = []
          }
          if (field.fieldType === 'array' && !field.items) {
            field.items = { fieldType: 'string', properties: [] }
          }
        })
      })
      selectedCode.value = ontology.ontologyCode
      selectedOntology.value = data
      editingData.value = {
        ...data,
        entities: JSON.parse(JSON.stringify(data.entities))
      }
      originalData.value = {
        ...data,
        entities: JSON.parse(JSON.stringify(data.entities))
      }
      activeTab.value = 'basic'
    } else {
      ElMessage.error(res.message || '加载本体详情失败')
    }
  } catch (e) {
    ElMessage.error('加载本体详情失败')
    console.error(e)
  } finally {
    loadingStore.hide()
  }
}

const resetEditing = () => {
  if (!originalData.value) return
  editingData.value = {
    ...originalData.value,
    entities: originalData.value.entities ? JSON.parse(JSON.stringify(originalData.value.entities)) : []
  }
  ElMessage.success('已重置')
}

const validateOntology = (data, isCreate = false) => {
  if (!data.ontologyName?.trim()) {
    ElMessage.warning('请输入名称')
    return false
  }
  if (isCreate && !data.ontologyCode?.trim()) {
    ElMessage.warning('请输入编码')
    return false
  }
  return true
}

const saveOntology = async () => {
  if (!validateOntology(editingData.value)) return
  
  saving.value = true
  try {
    const res = await ontologyApi.updateOntology(selectedCode.value, editingData.value)
    if (res.success) {
      ElMessage.success('保存成功')
      const savedData = res.data
      if (savedData.entities) {
        savedData.entities.forEach(entity => {
          if (entity.fields) {
            entity.fields.forEach(field => {
              field.expanded = false
            })
          }
        })
      }
      originalData.value = {
        ...savedData,
        entities: savedData.entities ? JSON.parse(JSON.stringify(savedData.entities)) : []
      }
      await loadOntologies()
      const updatedOntology = ontologies.value.find(o => o.ontologyCode === selectedCode.value)
      if (updatedOntology) selectedOntology.value = savedData
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
    console.error(e)
  } finally {
    saving.value = false
  }
}

const openCreateModal = () => {
  createData.value = {
    ontologyCode: '',
    ontologyName: '',
    description: '',
    category: 'general',
    entities: []
  }
  createDialogVisible.value = true
}

const confirmCreate = async () => {
  if (!validateOntology(createData.value, true)) return
  
  saving.value = true
  try {
    const res = await ontologyApi.createOntology(createData.value)
    if (res.success) {
      ElMessage.success('创建成功')
      createDialogVisible.value = false
      await loadOntologies()
      await selectOntology(res.data)
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建失败')
    console.error(e)
  } finally {
    saving.value = false
  }
}

const confirmDelete = async (ontology) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除本体「${ontology.ontologyName}」吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteOntology(ontology)
  } catch (e) {
    // 用户取消，不处理
  }
}

const deleteOntology = async (ontology) => {
  try {
    loadingStore.show('删除中...')
    const res = await ontologyApi.deleteOntology(ontology.ontologyCode)
    if (res.success) {
      ElMessage.success('删除成功')
      if (selectedCode.value === ontology.ontologyCode) {
        selectedCode.value = null
        selectedOntology.value = null
        editingData.value = {}
        originalData.value = {}
      }
      await loadOntologies()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
    console.error(e)
  } finally {
    loadingStore.hide()
  }
}

const toggleActive = async (ontology) => {
  try {
    const res = await ontologyApi.toggleOntology(ontology.ontologyCode)
    if (res.success) {
      ElMessage.success(res.data.isActive ? '已启用' : '已停用')
      await loadOntologies()
      if (selectedCode.value === ontology.ontologyCode) {
        selectedOntology.value = res.data
        editingData.value.isActive = res.data.isActive
        originalData.value.isActive = res.data.isActive
      }
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
    console.error(e)
  }
}

const addEntity = () => {
  if (!editingData.value.entities) editingData.value.entities = []
  editingData.value.entities.push({
    entityCode: '',
    entityName: '',
    fields: []
  })
}

const removeEntity = (index) => {
  editingData.value.entities.splice(index, 1)
}

const addField = (entity) => {
  if (!entity.fields) entity.fields = []
  entity.fields.push({
    fieldCode: '',
    fieldName: '',
    fieldType: 'string',
    required: false,
    ruleDescription: '',
    expanded: false,
    properties: [],
    items: { fieldType: 'string', properties: [] }
  })
}

const removeField = (entity, index) => {
  entity.fields.splice(index, 1)
}

const handleTypeChange = (field) => {
  if (field.fieldType === 'object' && !field.properties) {
    field.properties = []
  }
  if (field.fieldType === 'array' && !field.items) {
    field.items = { fieldType: 'string', properties: [] }
  }
}

const toggleFieldExpanded = (field) => {
  field.expanded = !field.expanded
  if (field.expanded) {
    if (field.fieldType === 'object' && !field.properties) {
      field.properties = []
    }
    if (field.fieldType === 'array' && !field.items) {
      field.items = { fieldType: 'string', properties: [] }
    }
  }
}

const addObjectProperty = (field) => {
  if (!field.properties) field.properties = []
  field.properties.push({
    fieldCode: '',
    fieldName: '',
    fieldType: 'string',
    required: false,
    ruleDescription: ''
  })
}

const removeObjectProperty = (field, index) => {
  field.properties.splice(index, 1)
}

const addArrayItemProperty = (field) => {
  if (!field.items) field.items = { fieldType: 'object', properties: [] }
  if (!field.items.properties) field.items.properties = []
  field.items.properties.push({
    fieldCode: '',
    fieldName: '',
    fieldType: 'string',
    required: false,
    ruleDescription: ''
  })
}

const removeArrayItemProperty = (field, index) => {
  if (field.items && field.items.properties) {
    field.items.properties.splice(index, 1)
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadOntologies()])
})
</script>

<style scoped>
.ontology-manager {
  height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  overflow: hidden;
}

.list-section {
  width: 360px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 16px;
  border-bottom: 1px solid #eee;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
}

.list-search {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.list-container {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.empty-state {
  padding: 40px 0;
}

.ontology-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ontology-item {
  padding: 16px;
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #f9f9f9;
}

.ontology-item:hover {
  background: #f0f0f0;
}

.ontology-item.active {
  border-color: var(--el-color-primary);
  background: #ecf5ff;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
}

.item-status {
  display: flex;
  gap: 8px;
}

.item-code {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}

.item-desc {
  font-size: 14px;
  color: #666;
  margin-bottom: 12px;
}

.item-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
  margin-bottom: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-actions {
  display: flex;
  gap: 8px;
}

.detail-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow-y: auto;
  padding: 20px;
}

.loading-detail {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-content {
  text-align: center;
  color: #999;
}

.empty-text {
  font-size: 14px;
  margin-top: 16px;
}

.ontology-detail {
  height: 100%;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.info-card {
  margin-bottom: 16px;
}

.detail-tabs {
  margin-top: 20px;
}

.entity-title {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.entity-name {
  font-weight: 600;
}

.entity-code {
  color: #999;
  font-size: 13px;
}

.entity-count {
  color: #666;
  font-size: 13px;
}

.entity-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.entity-content {
  padding-top: 8px;
}

.fields-section {
  margin-top: 16px;
}

.fields-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.expanded-field {
  margin-top: 24px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.object-config,
.array-config,
.array-object-config {
  margin-top: 16px;
}

.json-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #999;
}

.empty-entities {
  padding: 40px 0;
}
</style>

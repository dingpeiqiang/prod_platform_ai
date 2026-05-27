<template>
    <div class="generic-manager">
        <div class="top-bar">
            <button class="back-btn" @click="goBack">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M19 12H5M12 19l-7-7 7-7"/>
                </svg>
                返回首页
            </button>
            <h2>{{ title }}</h2>
            <div class="header-actions">
                <input 
                    v-model="searchKeyword" 
                    placeholder="搜索名称、编码..." 
                    class="search-input"
                    @keyup.enter="loadItems"
                />
                <select v-model="filterCategory" @change="loadItems" class="filter-select">
                    <option value="">全部分类</option>
                    <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                </select>
                <select v-model="filterStatus" @change="loadItems" class="filter-select">
                    <option :value="null">全部状态</option>
                    <option :value="true">已启用</option>
                    <option :value="false">已停用</option>
                </select>
                <select v-model="sortBy" @change="loadItems" class="filter-select">
                    <option value="createdAt">创建时间</option>
                    <option value="updatedAt">更新时间</option>
                    <option value="executionCount">执行次数</option>
                </select>
                <select v-model="sortOrder" @change="loadItems" class="filter-select">
                    <option value="desc">降序</option>
                    <option value="asc">升序</option>
                </select>
                <button class="primary-btn" @click="openCreateModal">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    新建{{ itemType }}
                </button>
            </div>
        </div>

        <div v-if="!useExternalEditor" class="toolbar">
            <button 
                type="success" 
                class="toolbar-btn"
                :disabled="selectedItems.length === 0"
                @click="batchEnable"
            >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="22 11.08 12 19.08 2 11.08"></polyline>
                    <path d="M21.73 7.92a2 2 0 0 1 0 2.16L12 17.93l-9.73-7.95a2 2 0 0 1 0-2.16L12 4.07l9.73 3.85z"></path>
                </svg>
                批量启用 ({{ selectedItems.length }})
            </button>
            <button 
                type="warning" 
                class="toolbar-btn"
                :disabled="selectedItems.length === 0"
                @click="batchDisable"
            >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
                批量停用
            </button>
        </div>

        <div class="content-wrapper">
            <div class="items-grid">
                <div
                    v-for="item in items" :key="item[codeField]"
                    class="item-card"
                    :class="{ active: selectedCode === item[codeField], selected: selectedItems.includes(item[codeField]) }"
                    @click="selectItem(item)"
                >
                    <div v-if="!useExternalEditor" class="item-checkbox">
                        <input 
                            type="checkbox" 
                            :checked="selectedItems.includes(item[codeField])"
                            @click.stop="toggleSelectItem(item[codeField])"
                        />
                    </div>
                    <div class="item-header">
                        <span class="item-category">{{ getCategoryName(item.category) }}</span>
                        <div class="item-status">
                            <span v-if="item.isActive" class="active-badge">启用</span>
                            <span v-else class="disabled-badge">停用</span>
                            <span class="item-version">v{{ item.version }}</span>
                        </div>
                    </div>
                    <h3 class="item-name">{{ item[nameField] }}</h3>
                    <span class="item-code">{{ item[codeField] }}</span>
                    <p class="item-desc">{{ item.description || '暂无描述' }}</p>
                    <div class="item-meta">
                        <span>{{ formatDate(item.updatedAt) }}</span>
                        <span v-if="item.executionCount !== undefined" class="execution-count">
                            执行 {{ item.executionCount }} 次
                        </span>
                    </div>
                    <div v-if="!useExternalEditor" class="item-actions">
                        <button class="small-btn" @click.stop="viewHistory(item)">版本历史</button>
                        <button class="small-btn" @click.stop="toggleActive(item)">
                            {{ item.isActive ? '停用' : '启用' }}
                        </button>
                        <button class="small-btn danger" @click.stop="deleteItem(item)">删除</button>
                    </div>
                    <div v-if="useExternalEditor" class="item-actions-external">
                        <button class="small-btn" @click.stop="viewHistory(item)">版本历史</button>
                        <button class="small-btn" @click.stop="toggleActive(item)">
                            {{ item.isActive ? '停用' : '启用' }}
                        </button>
                        <button class="small-btn edit-btn" @click.stop="openEditor(item)">编辑</button>
                        <button class="small-btn danger" @click.stop="deleteItem(item)">删除</button>
                    </div>
                </div>
                <div v-if="items.length === 0" class="empty-state">
                    <div class="empty-icon">📝</div>
                    <h3>还没有{{ itemType }}数据</h3>
                    <p>点击"新建{{ itemType }}"来创建第一个！</p>
                </div>
            </div>

            <div v-if="selectedItem && !showModal && !useExternalEditor" class="editor-panel">
            <div class="editor-header">
                <div class="editor-title">
                    <h3>{{ selectedItem[nameField] }}</h3>
                    <span class="editor-code">{{ selectedItem[codeField] }}</span>
                </div>
                <div class="editor-actions">
                    <button class="btn-primary" @click="saveItem" :disabled="saving">
                        {{ saving ? '保存中...' : '保存' }}
                    </button>
                </div>
            </div>

            <div class="editor-content">
                <div class="form-grid">
                    <div class="form-item">
                        <label>名称 *</label>
                        <input v-model="editingData[nameField]" placeholder="输入名称" />
                    </div>
                    <div class="form-item" v-if="showCategory">
                        <label>分类</label>
                        <select v-model="editingData.category">
                            <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                        </select>
                    </div>
                </div>
                <div class="form-item full">
                    <label>描述</label>
                    <input v-model="editingData.description" placeholder="输入描述" />
                </div>

                <div class="section" v-if="showEntities">
                    <div class="section-title">
                        实体结构
                        <button class="small-btn" @click="addEntity">+ 新增实体</button>
                    </div>
                    <div class="json-editor">
                        <textarea v-model="entitiesText" rows="15" placeholder="输入JSON格式的实体结构..."></textarea>
                    </div>
                </div>

                <div class="section" v-if="showTools">
                    <div class="section-title">
                        工具配置
                        <button class="small-btn" @click="addParameter">+ 新增参数</button>
                    </div>
                    <div class="form-grid">
                        <div class="form-item">
                            <label>工具类型</label>
                            <select v-model="editingData.toolType">
                                <option value="custom">自定义</option>
                                <option value="http">HTTP</option>
                                <option value="mcp">MCP</option>
                            </select>
                        </div>
                        <div class="form-item">
                            <label>端点 / 处理函数</label>
                            <input v-model="editingData.endpoint" placeholder="Endpoint或Handler" />
                        </div>
                    </div>
                    <div class="json-editor">
                        <label>参数定义 (JSON)</label>
                        <textarea v-model="parametersText" rows="8" placeholder="输入JSON格式的参数..."></textarea>
                    </div>
                </div>
            </div>
            </div> <!-- editor-panel end -->
        </div> <!-- content-wrapper end -->

        <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
            <div class="modal" @click.stop>
                <div class="modal-header">
                    <h3>{{ isEdit ? '编辑' : '新建' }}{{ itemType }}</h3>
                    <button class="close-btn" @click="closeModal">×</button>
                </div>
                <div class="modal-body">
                    <div class="form-item">
                        <label>编码 *</label>
                        <input v-model="createData.code" placeholder="例如：customer_service" :disabled="isEdit" />
                    </div>
                    <div class="form-item">
                        <label>名称 *</label>
                        <input v-model="createData.name" placeholder="例如：客户服务" />
                    </div>
                    <div class="form-item" v-if="showCategory">
                        <label>分类</label>
                        <select v-model="createData.category">
                            <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                        </select>
                    </div>
                    <div class="form-item">
                        <label>描述</label>
                        <input v-model="createData.description" placeholder="简单描述用途" />
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn" @click="closeModal">取消</button>
                    <button class="btn btn-primary" @click="confirmCreate">确定</button>
                </div>
            </div>
        </div>

        <!-- 版本历史弹窗 -->
        <div v-if="showHistoryModal" class="modal-overlay" @click.self="closeHistoryModal">
            <div class="modal large-modal" @click.stop>
                <div class="modal-header">
                    <h3>版本历史 - {{ currentHistoryItem?.[nameField] }}</h3>
                    <button class="close-btn" @click="closeHistoryModal">×</button>
                </div>
                <div class="modal-body">
                    <div v-if="currentHistoryList && currentHistoryList.length > 0">
                        <table class="history-table">
                            <thead>
                                <tr>
                                    <th>版本号</th>
                                    <th>状态</th>
                                    <th>变更说明</th>
                                    <th>操作人</th>
                                    <th>操作时间</th>
                                    <th>操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="history in currentHistoryList" :key="history.version">
                                    <td>v{{ history.version }}</td>
                                    <td>
                                        <span :class="history.isActive ? 'active-badge' : 'disabled-badge'">
                                            {{ history.isActive ? '启用' : '停用' }}
                                        </span>
                                    </td>
                                    <td>{{ history.changeNote || '无' }}</td>
                                    <td>{{ history.createdBy || '系统' }}</td>
                                    <td>{{ formatDate(history.createdAt) }}</td>
                                    <td>
                                        <button class="small-btn" @click="rollbackToVersion(history)">回滚</button>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <div v-else class="empty-state">
                        暂无版本历史
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn" @click="closeHistoryModal">关闭</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
    title: { type: String, default: '管理' },
    itemType: { type: String, default: '项目' },
    codeField: { type: String, default: 'code' },
    nameField: { type: String, default: 'name' },
    categoryField: { type: String, default: 'category' },
    apiService: { type: Object, required: true },
    showCategory: { type: Boolean, default: true },
    showEntities: { type: Boolean, default: false },
    showTools: { type: Boolean, default: false },
    useExternalEditor: { type: Boolean, default: false }
})

const emit = defineEmits(['goBack', 'edit-item'])
const goBack = () => { emit('goBack') }

const loading = ref(false)
const saving = ref(false)
const items = ref([])
const categories = ref([])
const filterCategory = ref('')
const filterStatus = ref(null)
const searchKeyword = ref('')
const sortBy = ref('createdAt')
const sortOrder = ref('desc')
const selectedCode = ref(null)
const selectedItems = ref([])
const selectedItem = ref(null)
const editingData = ref({})
const entitiesText = ref('')
const parametersText = ref('')

const showModal = ref(false)
const showHistoryModal = ref(false)
const isEdit = ref(false)
const createData = ref({})
const currentHistoryItem = ref(null)
const currentHistoryList = ref([])

const getCategoryName = (code) => {
    const cat = categories.value.find(c => c.code === code)
    return cat?.name || code
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return d.toLocaleString('zh-CN')
}

const loadCategories = async () => {
    if (props.apiService.getCategories) {
        const res = await props.apiService.getCategories()
        if (res.success) categories.value = res.data
    }
}

const loadItems = async () => {
    loading.value = true
    const params = {
        keyword: searchKeyword.value || undefined,
        category: filterCategory.value || undefined,
        isActive: filterStatus.value,
        sortBy: sortBy.value,
        sortOrder: sortOrder.value
    }
    const res = await props.apiService.list(filterCategory.value || undefined, filterStatus.value)
    if (res.success) items.value = res.data || []
    loading.value = false
}

const openEditor = (item) => {
    emit('edit-item', item[props.codeField])
}

const selectItem = async (item) => {
    if (props.useExternalEditor) {
        emit('edit-item', item[props.codeField])
        return
    }
    
    selectedCode.value = item[props.codeField]
    selectedItems.value = []
    const res = await props.apiService.get(item[props.codeField])
    if (res.success) {
        selectedItem.value = res.data
        editingData.value = { ...res.data }
        if (props.showEntities && res.data.entities) {
            entitiesText.value = JSON.stringify(res.data.entities, null, 2)
        }
        if (props.showTools && res.data.parameters) {
            parametersText.value = JSON.stringify(res.data.parameters, null, 2)
        }
    }
}

const toggleSelectItem = (code) => {
    const index = selectedItems.value.indexOf(code)
    if (index > -1) {
        selectedItems.value.splice(index, 1)
    } else {
        selectedItems.value.push(code)
    }
}

const saveItem = async () => {
    if (!editingData.value[props.nameField]?.trim()) {
        ElMessage.warning('请输入名称')
        return
    }
    
    saving.value = true
    try {
        const data = { ...editingData.value }
        if (props.showEntities) {
            try { data.entities = JSON.parse(entitiesText.value) } catch (e) {}
        }
        if (props.showTools) {
            try { data.parameters = JSON.parse(parametersText.value) } catch (e) {}
        }
        
        const res = await props.apiService.update(selectedCode.value, data)
        if (res.success) {
            ElMessage.success('保存成功')
            loadItems()
            selectItem(res.data)
        } else {
            ElMessage.error(res.message || '保存失败')
        }
    } catch(e) {
        ElMessage.error('保存失败')
    } finally {
        saving.value = false
    }
}

const openCreateModal = () => {
    if (props.useExternalEditor) {
        emit('edit-item', null)
        return
    }
    isEdit.value = false
    createData.value = { code: '', name: '', description: '', category: 'general' }
    showModal.value = true
}

const confirmCreate = async () => {
    if (!createData.value.code?.trim() || !createData.value.name?.trim()) {
        ElMessage.warning('请输入编码和名称')
        return
    }
    saving.value = true
    try {
        const createPayload = {}
        createPayload[props.codeField] = createData.value.code
        createPayload[props.nameField] = createData.value.name
        createPayload.description = createData.value.description
        if (props.showCategory) createPayload[props.categoryField] = createData.value.category
        
        const res = await props.apiService.create(createPayload)
        if (res.success) {
            ElMessage.success('创建成功')
            showModal.value = false
            loadItems()
            selectItem(res.data)
        } else {
            ElMessage.error(res.message)
        }
    } catch(e) {
        ElMessage.error('创建失败')
    } finally {
        saving.value = false
    }
}

const deleteItem = async (item) => {
    try {
        await ElMessageBox.confirm(`确定要删除${props.itemType}「${item[props.nameField]}」吗？`, '确认删除', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        const res = await props.apiService.delete(item[props.codeField])
        if (res.success) {
            ElMessage.success('删除成功')
            if (selectedCode.value === item[props.codeField]) {
                selectedCode.value = null
                selectedItem.value = null
            }
            loadItems()
        } else {
            ElMessage.error(res.message)
        }
    } catch(e) {
        if (e !== 'cancel') ElMessage.error('删除失败')
    }
}

const toggleActive = async (item) => {
    const res = await props.apiService.toggle(item[props.codeField])
    if (res.success) {
        ElMessage.success(res.data.isActive ? '已启用' : '已停用')
        loadItems()
        if (selectedCode.value === item[props.codeField]) {
            selectItem(res.data)
        }
    }
}

const batchEnable = async () => {
    try {
        await ElMessageBox.confirm(`确定要启用选中的 ${selectedItems.value.length} 个${props.itemType}吗？`, '确认批量启用', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        
        let successCount = 0
        for (const code of selectedItems.value) {
            const res = await props.apiService.toggle(code)
            if (res.success && res.data.isActive) successCount++
        }
        
        ElMessage.success(`批量启用完成，成功 ${successCount} 个`)
        selectedItems.value = []
        loadItems()
    } catch(e) {
        if (e !== 'cancel') ElMessage.error('批量启用失败')
    }
}

const batchDisable = async () => {
    try {
        await ElMessageBox.confirm(`确定要停用选中的 ${selectedItems.value.length} 个${props.itemType}吗？`, '确认批量停用', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        
        let successCount = 0
        for (const code of selectedItems.value) {
            const res = await props.apiService.toggle(code)
            if (res.success && !res.data.isActive) successCount++
        }
        
        ElMessage.success(`批量停用完成，成功 ${successCount} 个`)
        selectedItems.value = []
        loadItems()
    } catch(e) {
        if (e !== 'cancel') ElMessage.error('批量停用失败')
    }
}

const viewHistory = async (item) => {
    currentHistoryItem.value = item
    if (props.apiService.getHistory) {
        const res = await props.apiService.getHistory(item[props.codeField])
        if (res.success) {
            currentHistoryList.value = res.data
        }
    }
    showHistoryModal.value = true
}

const rollbackToVersion = async (history) => {
    try {
        await ElMessageBox.confirm(`确定要将${props.itemType}回滚到版本 ${history.version} 吗？`, '确认回滚', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        
        if (props.apiService.rollback) {
            const res = await props.apiService.rollback(history.workflowCode, history.version)
            if (res.success) {
                ElMessage.success('回滚成功')
                closeHistoryModal()
                loadItems()
            } else {
                ElMessage.error(res.message)
            }
        }
    } catch(e) {
        if (e !== 'cancel') ElMessage.error('回滚失败')
    }
}

const closeHistoryModal = () => {
    showHistoryModal.value = false
    currentHistoryItem.value = null
    currentHistoryList.value = []
}

const addEntity = () => {
    try {
        let entities = entitiesText.value ? JSON.parse(entitiesText.value) : []
        entities.push({ entityCode: '', entityName: '', fields: [] })
        entitiesText.value = JSON.stringify(entities, null, 2)
    } catch(e) {
        ElMessage.warning('请先确保JSON格式正确')
    }
}

const addParameter = () => {
    try {
        let params = parametersText.value ? JSON.parse(parametersText.value) : []
        params.push({ name: '', type: 'string', description: '' })
        parametersText.value = JSON.stringify(params, null, 2)
    } catch(e) {
        ElMessage.warning('请先确保JSON格式正确')
    }
}

const closeModal = () => { showModal.value = false }

onMounted(() => {
    loadCategories()
    loadItems()
})
</script>

<style scoped>
.generic-manager {
    padding: 24px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--bg-primary);
    gap: 12px;
    overflow-y: auto;
}

.top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    background: var(--bg-elevated);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-sm);
    border: 1px solid var(--border-light);
    flex-wrap: wrap;
    gap: 8px;
}

.back-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-default);
    background: var(--bg-elevated);
    cursor: pointer;
    font-size: 13px;
    color: var(--text-secondary);
}

.back-btn:hover {
    background: var(--bg-secondary);
    color: var(--text-primary);
}

.top-bar h2 {
    margin: 0;
    font-size: 16px;
    color: var(--text-primary);
    flex: 1;
    text-align: center;
}

.header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
}

.search-input {
    padding: 6px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-default);
    background: var(--bg-elevated);
    color: var(--text-primary);
    min-width: 150px;
    font-size: 13px;
}

.filter-select {
    padding: 6px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-default);
    background: var(--bg-elevated);
    color: var(--text-primary);
    font-size: 13px;
}

.primary-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 14px;
    background: #3b82f6;
    color: white;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-size: 13px;
}

.toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 16px;
    background: var(--bg-elevated);
    border-radius: var(--radius-md);
    border: 1px solid var(--border-light);
    flex-wrap: wrap;
    gap: 8px;
}

.toolbar-left, .toolbar-right {
    display: flex;
    gap: 8px;
    align-items: center;
}

.toolbar-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 10px;
    border-radius: 4px;
    border: none;
    cursor: pointer;
    font-size: 12px;
}

.toolbar-btn[type="success"] {
    background: #dcfce7;
    color: #16a34a;
}

.toolbar-btn[type="warning"] {
    background: #fef3c7;
    color: #d97706;
}

.toolbar-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.content-wrapper {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.items-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, 280px);
    gap: 12px;
    justify-content: flex-start;
}

.item-card {
    background: var(--bg-elevated);
    padding: 16px;
    border-radius: var(--radius-md);
    border: 1px solid var(--border-light);
    cursor: pointer;
    box-shadow: none;
    transition: all var(--transition-fast);
    position: relative;
    display: flex;
    flex-direction: column;
    height: 220px;
}

.item-card:hover {
    border-color: var(--color-primary-400);
    box-shadow: var(--shadow-sm);
}

.item-card.active {
    border-color: var(--color-primary-500);
    background: rgba(91, 124, 250, 0.05);
}

.item-card.selected {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.08);
}

.item-checkbox {
    position: absolute;
    top: 10px;
    right: 10px;
}

.item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.item-category {
    padding: 2px 8px;
    border-radius: var(--radius-full);
    font-size: 11px;
    background: var(--bg-tertiary);
    color: var(--text-secondary);
}

.item-status {
    display: flex;
    gap: 6px;
    align-items: center;
}

.active-badge {
    padding: 2px 6px;
    border-radius: 8px;
    background: #10b981;
    color: white;
    font-size: 10px;
}

.disabled-badge {
    padding: 2px 6px;
    border-radius: 8px;
    background: #9ca3af;
    color: white;
    font-size: 10px;
}

.item-version {
    color: #9ca3af;
    font-size: 11px;
}

.item-name {
    font-size: 15px;
    font-weight: 500;
    margin: 0 0 4px 0;
    color: var(--text-primary);
}

.item-code {
    font-size: 12px;
    color: #6b7280;
    margin-bottom: 6px;
    display: block;
    font-family: monospace;
}

.item-desc {
    font-size: 12px;
    color: #6b7280;
    margin: 0 0 8px 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.5;
    min-height: 36px;
}

.item-meta {
    display: flex;
    gap: 12px;
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 8px;
    justify-content: space-between;
}

.execution-count {
    padding: 1px 5px;
    background: #f3f4f6;
    border-radius: 4px;
    font-size: 10px;
}

.item-actions, .item-actions-external {
    display: flex;
    gap: 6px;
    padding-top: 10px;
    border-top: 1px solid #f3f4f6;
    flex-wrap: wrap;
    margin-top: auto;
}

.small-btn {
    padding: 4px 10px;
    background: #f3f4f6;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 11px;
    transition: background 0.2s;
}

.small-btn:hover {
    background: #e5e7eb;
}

.small-btn.edit-btn {
    background: #dbeafe;
    color: #1d4ed8;
}

.small-btn.edit-btn:hover {
    background: #bfdbfe;
}

.small-btn.danger {
    background: #fee2e2;
    color: #dc2626;
}

.small-btn.danger:hover {
    background: #fecaca;
}

.empty-state {
    grid-column: 1 / -1;
    text-align: center;
    padding: 40px;
    color: #9ca3af;
}

.empty-icon {
    font-size: 40px;
    margin-bottom: 12px;
}

.editor-panel {
    background: white;
    border-radius: 12px;
    padding: 24px;
    margin-top: 12px;
    box-shadow: var(--shadow-sm);
    border: 1px solid var(--border-light);
}

.editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e5e7eb;
}

.editor-content {
    gap: 24px;
}

.section {
    margin-bottom: 24px;
}

.section-title {
    font-weight: 600;
    margin-bottom: 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.form-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
}

.form-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.form-item.full {
    grid-column: span 2;
}

.form-item label {
    font-size: 13px;
    font-weight: 500;
}

.form-item input,
.form-item select,
.form-item textarea {
    padding: 10px 12px;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
}

.json-editor textarea {
    width: 100%;
    padding: 12px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    font-family: monospace;
}

.modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}

.modal {
    background: white;
    border-radius: 12px;
    width: 500px;
    max-height: 90vh;
    overflow: auto;
}

.modal.large-modal {
    width: 800px;
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px;
    border-bottom: 1px solid #e5e7eb;
}

.close-btn {
    border: none;
    background: none;
    font-size: 24px;
    cursor: pointer;
}

.modal-body {
    padding: 20px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding: 20px;
    border-top: 1px solid #e5e7eb;
}

.btn {
    padding: 10px 20px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    cursor: pointer;
    background: white;
}

.btn-primary {
    background: #3b82f6;
    color: white;
    border: none;
}

.history-table {
    width: 100%;
    border-collapse: collapse;
}

.history-table th,
.history-table td {
    padding: 12px;
    text-align: left;
    border-bottom: 1px solid #e5e7eb;
}

.history-table th {
    background: #f9fafb;
    font-weight: 600;
    font-size: 13px;
}
</style>
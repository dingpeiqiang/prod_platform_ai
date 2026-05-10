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
                <select v-model="filterCategory" @change="loadItems" class="filter-select">
                    <option value="">全部分类</option>
                    <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                </select>
                <button class="primary-btn" @click="openCreateModal">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    新建{{ itemType }}
                </button>
            </div>
        </div>

        <div class="items-grid">
            <div
                v-for="item in items" :key="item[codeField]"
                class="item-card"
                :class="{ active: selectedCode === item[codeField] }"
                @click="selectItem(item)"
            >
                <div class="item-header">
                    <span class="item-category">{{ getCategoryName(item.category) }}</span>
                    <div class="item-status">
                        <span v-if="item.isActive" class="active-badge">启用</span>
                        <span class="item-version">v{{ item.version }}</span>
                    </div>
                </div>
                <h3 class="item-name">{{ item[nameField] }}</h3>
                <p class="item-desc">{{ item.description || '暂无描述' }}</p>
                <div class="item-meta">
                    <span>{{ formatDate(item.updatedAt) }}</span>
                </div>
                <div class="item-actions">
                    <button class="small-btn" @click.stop="toggleActive(item)">
                        {{ item.isActive ? '停用' : '启用' }}
                    </button>
                    <button class="small-btn danger" @click.stop="deleteItem(item)">删除</button>
                </div>
            </div>
            <div v-if="items.length === 0" class="empty-state">
                <div class="empty-icon">📝</div>
                <h3>还没有{{ itemType }}数据</h3>
                <p>点击"新建{{ itemType }}"来创建第一个！</p>
            </div>
        </div>

        <div v-if="selectedItem && !showModal" class="editor-panel">
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
        </div>

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
    showTools: { type: Boolean, default: false }
})

const emit = defineEmits(['goBack'])
const goBack = () => { emit('goBack') }

const loading = ref(false)
const saving = ref(false)
const items = ref([])
const categories = ref([])
const filterCategory = ref('')
const selectedCode = ref(null)
const selectedItem = ref(null)
const editingData = ref({})
const entitiesText = ref('')
const parametersText = ref('')

const showModal = ref(false)
const isEdit = ref(false)
const createData = ref({})

const getCategoryName = (code) => {
    const cat = categories.value.find(c => c.code === code)
    return cat?.name || code
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN')
}

const loadCategories = async () => {
    if (props.apiService.getCategories) {
        const res = await props.apiService.getCategories()
        if (res.success) categories.value = res.data
    }
}

const loadItems = async () => {
    loading.value = true
    const res = await props.apiService.list(filterCategory.value || undefined, true)
    if (res.success) items.value = res.data || []
    loading.value = false
}

const selectItem = async (item) => {
    selectedCode.value = item[props.codeField]
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
    background: #f5f7fa;
}

.top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 24px;
    background: #fff;
    border-radius: 12px;
    margin-bottom: 20px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.back-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    border-radius: 8px;
    border: 1px solid #e5e7eb;
    background: white;
    cursor: pointer;
    font-size: 14px;
}

.top-bar h2 {
    margin: 0;
    font-size: 18px;
}

.header-actions {
    display: flex;
    gap: 12px;
    align-items: center;
}

.filter-select {
    padding: 8px 12px;
    border-radius: 6px;
    border: 1px solid #e5e7eb;
}

.primary-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 18px;
    background: #3b82f6;
    color: white;
    border: none;
    border-radius: 8px;
    cursor: pointer;
}

.items-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
    margin-bottom: 20px;
}

.item-card {
    background: white;
    padding: 20px;
    border-radius: 12px;
    border: 2px solid transparent;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    transition: all 0.2s;
}

.item-card:hover {
    border-color: #3b82f6;
    transform: translateY(-2px);
}

.item-card.active {
    border-color: #3b82f6;
    background: #eff6ff;
}

.item-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
}

.item-category {
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: #e5e7eb;
}

.item-status {
    display: flex;
    gap: 8px;
    align-items: center;
}

.active-badge {
    padding: 2px 8px;
    border-radius: 10px;
    background: #10b981;
    color: white;
    font-size: 11px;
}

.item-version {
    color: #9ca3af;
    font-size: 12px;
}

.item-name {
    font-size: 16px;
    margin: 0 0 8px 0;
}

.item-desc {
    font-size: 13px;
    color: #6b7280;
    margin: 0 0 12px 0;
}

.item-meta {
    display: flex;
    gap: 12px;
    font-size: 12px;
    color: #9ca3af;
    margin-bottom: 12px;
}

.item-actions {
    display: flex;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid #f3f4f6;
}

.small-btn {
    padding: 6px 12px;
    background: #f3f4f6;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-size: 12px;
}

.small-btn.danger {
    background: #fee2e2;
    color: #dc2626;
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
</style>

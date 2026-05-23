<template>
  <div class="external-tool-manager">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input 
        v-model="searchKeyword" 
        placeholder="搜索工具名称或描述"
        prefix-icon="Search"
        style="width: 300px"
        clearable
      />
      <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 150px">
        <el-option label="全部状态" value="" />
        <el-option label="已启用" :value="true" />
        <el-option label="已禁用" :value="false" />
      </el-select>
      <el-button type="success" @click="showImportModal = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: middle; margin-right: 4px;">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
          <polyline points="17 8 12 3 7 8"></polyline>
          <line x1="12" y1="3" x2="12" y2="15"></line>
        </svg>
        导入工具
      </el-button>
      <el-button type="primary" @click="$emit('create')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: middle; margin-right: 4px;">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        </svg>
        新建外部工具
      </el-button>
    </div>

    <!-- 工具列表 -->
    <el-table :data="filteredTools" stripe style="width: 100%">
      <el-table-column prop="tool_name" label="工具名称" width="200">
        <template #default="{ row }">
          <strong>{{ row.tool_name }}</strong>
          <el-tag v-if="row.is_enabled" size="small" type="success" style="margin-left: 8px;">启用</el-tag>
          <el-tag v-else size="small" type="info" style="margin-left: 8px;">禁用</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
      
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.category || '未分类' }}</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="调用统计" width="150">
        <template #default="{ row }">
          <div class="stats-cell">
            <div>总调用: {{ row.total_calls || 0 }}</div>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="editTool(row)">编辑</el-button>
          <el-button size="small" type="success" @click="testTool(row)">测试</el-button>
          <el-button 
            size="small" 
            :type="row.is_enabled ? 'warning' : 'success'"
            @click="toggleTool(row)"
          >
            {{ row.is_enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="deleteTool(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="filteredTools.length === 0" class="empty-state">
      <p>暂无外部工具，点击"新建外部工具"添加</p>
    </div>

    <!-- 导入工具弹窗 -->
    <el-dialog
      v-model="showImportModal"
      title="导入外部工具"
      width="70%"
      top="5vh"
    >
      <OpenAPIImporter
        @close="showImportModal = false"
        @import-success="handleImportSuccess"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage, ElMessageBox } from 'element-plus'
import OpenAPIImporter from './OpenAPIImporter.vue'

const emit = defineEmits(['refresh', 'create', 'edit', 'test'])

const searchKeyword = ref('')
const filterStatus = ref('')
const showImportModal = ref(false)

const externalTools = ref([])

const filteredTools = computed(() => {
  let result = externalTools.value
  
  if (filterStatus.value !== '') {
    const status = filterStatus.value === 'true' || filterStatus.value === true
    result = result.filter(t => t.is_enabled === status)
  }
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(t => 
      t.tool_name.toLowerCase().includes(keyword) ||
      t.description?.toLowerCase().includes(keyword)
    )
  }
  
  return result
})

onMounted(() => {
  loadExternalTools()
})

const loadExternalTools = async () => {
  try {
    const res = await mcpApi.getExternalTools()
    if (res.success) {
      externalTools.value = res.tools || []
    }
  } catch (e) {
    console.error('加载外部工具失败:', e)
    ElMessage.error('加载外部工具失败')
  }
}

const editTool = (tool) => {
  emit('edit', tool)
}

const testTool = (tool) => {
  emit('test', tool)
}

const toggleTool = async (tool) => {
  try {
    await ElMessageBox.confirm(
      `确定要${tool.is_enabled ? '禁用' : '启用'}工具 "${tool.tool_name}" 吗？`,
      '提示',
      { type: 'warning' }
    )
    
    await mcpApi.toggleExternalTool(tool.tool_name, !tool.is_enabled)
    
    ElMessage.success(`${tool.is_enabled ? '禁用' : '启用'}成功`)
    loadExternalTools()
    emit('refresh')
    
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败: ' + e.message)
    }
  }
}

const deleteTool = async (tool) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除工具 "${tool.tool_name}" 吗？此操作不可恢复！`,
      '警告',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    
    await mcpApi.deleteExternalTool(tool.tool_name)
    
    ElMessage.success('删除成功')
    loadExternalTools()
    emit('refresh')
    
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

const handleImportSuccess = () => {
  showImportModal.value = false
  loadExternalTools()
  emit('refresh')
  ElMessage.success('工具导入成功')
}
</script>

<style scoped>
.external-tool-manager {
  width: 100%;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.stats-cell {
  font-size: 12px;
  line-height: 1.5;
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: #909399;
}
</style>
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
      <el-button type="primary" @click="showCreateDialog = true">
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
      
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="editTool(row)">编辑</el-button>
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

    <!-- 新建/编辑对话框 -->
    <el-dialog 
      v-model="showCreateDialog" 
      :title="editingTool ? '编辑外部工具' : '新建外部工具'"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form :model="toolForm" label-width="120px">
        <el-form-item label="工具名称" required>
          <el-input v-model="toolForm.tool_name" placeholder="唯一标识，如：get_weather" />
        </el-form-item>
        
        <el-form-item label="工具编码">
          <el-input v-model="toolForm.tool_code" placeholder="可选编码" />
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input v-model="toolForm.description" type="textarea" :rows="2" placeholder="工具功能描述" />
        </el-form-item>
        
        <el-form-item label="分类">
          <el-select v-model="toolForm.category" placeholder="选择分类" style="width: 100%">
            <el-option label="external" value="external" />
            <el-option label="api" value="api" />
            <el-option label="integration" value="integration" />
            <el-option label="custom" value="custom" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="是否启用">
          <el-switch v-model="toolForm.is_enabled" />
        </el-form-item>
        
        <el-divider>API 配置</el-divider>
        
        <el-form-item label="请求方法">
          <el-select v-model="toolForm.config.method" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="URL" required>
          <el-input v-model="toolForm.config.url" placeholder="https://api.example.com/v1/endpoint" />
        </el-form-item>
        
        <el-form-item label="Headers (JSON)">
          <el-input 
            v-model="headersJson" 
            type="textarea" 
            :rows="3"
            placeholder='{"Authorization": "Bearer {{TOKEN}}"}'
          />
        </el-form-item>
        
        <el-form-item label="Params (JSON)">
          <el-input 
            v-model="paramsJson" 
            type="textarea" 
            :rows="3"
            placeholder='{"key": "{{API_KEY}}", "q": "{{city}}"}'
          />
        </el-form-item>
        
        <el-form-item label="Body (JSON, POST/PUT)">
          <el-input 
            v-model="bodyJson" 
            type="textarea" 
            :rows="4"
            placeholder='{"query": "{{search_query}}"}'
          />
        </el-form-item>
        
        <el-form-item label="超时时间(秒)">
          <el-input-number v-model="toolForm.config.timeout_seconds" :min="1" :max="300" />
        </el-form-item>
        
        <el-form-item label="重试次数">
          <el-input-number v-model="toolForm.config.retry_count" :min="0" :max="5" />
        </el-form-item>
        
        <el-divider>输入 Schema</el-divider>
        
        <el-form-item label="Input Schema (JSON)">
          <el-input 
            v-model="inputSchemaJson" 
            type="textarea" 
            :rows="6"
            placeholder='{
  "type": "object",
  "properties": {
    "city": {"type": "string"}
  },
  "required": ["city"]
}'
          />
        </el-form-item>
        
        <el-divider>输出映射</el-divider>
        
        <el-form-item label="Output Mapping (JSON)">
          <el-input 
            v-model="outputMappingJson" 
            type="textarea" 
            :rows="4"
            placeholder='{
  "temperature": "$.current.temp_c",
  "condition": "$.current.condition.text"
}'
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTool" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage, ElMessageBox } from 'element-plus'

const emit = defineEmits(['refresh'])

const searchKeyword = ref('')
const filterStatus = ref('')
const showCreateDialog = ref(false)
const editingTool = ref(null)
const saving = ref(false)

const externalTools = ref([])

const toolForm = ref({
  tool_name: '',
  tool_code: '',
  description: '',
  category: 'external',
  is_enabled: true,
  config: {
    method: 'GET',
    url: '',
    timeout_seconds: 30,
    retry_count: 0
  }
})

const headersJson = ref('{}')
const paramsJson = ref('{}')
const bodyJson = ref('')
const inputSchemaJson = ref('{}')
const outputMappingJson = ref('{}')

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

watch(showCreateDialog, (val) => {
  if (!val) {
    // 关闭对话框时重置表单
    resetForm()
  }
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
  editingTool.value = tool
  toolForm.value = {
    tool_name: tool.tool_name,
    tool_code: tool.tool_code || '',
    description: tool.description || '',
    category: tool.category || 'external',
    is_enabled: tool.is_enabled,
    config: tool.config || {
      method: 'GET',
      url: '',
      timeout_seconds: 30,
      retry_count: 0
    }
  }
  
  headersJson.value = JSON.stringify(tool.config?.headers || {}, null, 2)
  paramsJson.value = JSON.stringify(tool.config?.params || {}, null, 2)
  bodyJson.value = tool.config?.body ? JSON.stringify(tool.config.body, null, 2) : ''
  inputSchemaJson.value = JSON.stringify(tool.input_schema || {}, null, 2)
  outputMappingJson.value = JSON.stringify(tool.output_mapping || {}, null, 2)
  
  showCreateDialog.value = true
}

const resetForm = () => {
  editingTool.value = null
  toolForm.value = {
    tool_name: '',
    tool_code: '',
    description: '',
    category: 'external',
    is_enabled: true,
    config: {
      method: 'GET',
      url: '',
      timeout_seconds: 30,
      retry_count: 0
    }
  }
  headersJson.value = '{}'
  paramsJson.value = '{}'
  bodyJson.value = ''
  inputSchemaJson.value = '{}'
  outputMappingJson.value = '{}'
}

const saveTool = async () => {
  // 验证必填字段
  if (!toolForm.value.tool_name || !toolForm.value.config.url) {
    ElMessage.warning('请填写工具名称和 URL')
    return
  }
  
  // 解析 JSON
  let headers, params, body, inputSchema, outputMapping
  try {
    headers = headersJson.value ? JSON.parse(headersJson.value) : {}
    params = paramsJson.value ? JSON.parse(paramsJson.value) : {}
    body = bodyJson.value ? JSON.parse(bodyJson.value) : null
    inputSchema = inputSchemaJson.value ? JSON.parse(inputSchemaJson.value) : {}
    outputMapping = outputMappingJson.value ? JSON.parse(outputMappingJson.value) : {}
  } catch (e) {
    ElMessage.error('JSON 格式错误: ' + e.message)
    return
  }
  
  saving.value = true
  
  try {
    const toolData = {
      ...toolForm.value,
      config: {
        ...toolForm.value.config,
        headers,
        params,
        ...(body && { body })
      },
      input_schema: inputSchema,
      output_mapping: outputMapping
    }
    
    if (editingTool.value) {
      await mcpApi.updateExternalTool(editingTool.value.tool_name, toolData)
    } else {
      await mcpApi.createExternalTool(toolData)
    }
    
    ElMessage.success(editingTool.value ? '更新成功' : '创建成功')
    showCreateDialog.value = false
    emit('refresh')
    loadExternalTools()
    
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
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

:deep(.el-divider__text) {
  font-weight: 600;
  color: #303133;
}
</style>

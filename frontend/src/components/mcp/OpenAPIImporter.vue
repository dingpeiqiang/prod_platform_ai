<template>
  <div class="openapi-importer">
    <div class="modal-header">
      <h3>从 OpenAPI 规范导入外部工具</h3>
      <button class="close-btn" @click="$emit('close')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>
    </div>

    <div class="modal-body">
      <div class="step-container" :class="{ 'has-preview': parsedTools.length > 0 }">
        <!-- 步骤1: 输入OpenAPI规范 -->
        <div v-if="currentStep === 0" class="step-panel">
          <!-- 规范说明 -->
          <div class="spec-guide">
            <div class="guide-header">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="16" x2="12" y2="12"></line>
                <line x1="12" y1="8" x2="12.01" y2="8"></line>
              </svg>
              <span>规范要求说明</span>
            </div>
            <div class="guide-content">
              <div class="guide-section">
                <h4>支持的规范版本</h4>
                <ul>
                  <li><strong>OpenAPI 3.0 / 3.1</strong> - 推荐使用，功能最完善</li>
                  <li><strong>Swagger 2.0</strong> - 兼容支持</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>文件格式</h4>
                <ul>
                  <li><strong>JSON</strong> - .json 格式文件</li>
                  <li><strong>YAML</strong> - .yaml 或 .yml 格式文件</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>必填字段</h4>
                <ul>
                  <li><code>openapi</code> 或 <code>swagger</code> - 版本标识</li>
                  <li><code>paths</code> - API 路径定义</li>
                  <li><code>servers</code> 或 <code>host</code> - 服务器地址</li>
                </ul>
              </div>
              <div class="guide-section">
                <h4>示例格式</h4>
                <pre class="code-example">openapi: 3.0.0
info:
  title: 示例API
  version: 1.0.0
servers:
  - url: https://api.example.com/v1
paths:
  /users:
    get:
      operationId: getUsers
      summary: 获取用户列表
      responses:
        '200':
          description: 成功响应</pre>
              </div>
            </div>
          </div>

          <el-form :model="formData" label-width="120px">
            <el-form-item label="导入方式">
              <el-radio-group v-model="importMethod">
                <el-radio value="file">上传文件</el-radio>
                <el-radio value="content">粘贴内容</el-radio>
              </el-radio-group>
            </el-form-item>

            <!-- 文件上传 -->
            <el-form-item v-if="importMethod === 'file'" label="选择文件">
              <el-upload
                class="upload-demo"
                action=""
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleFileChange"
                accept=".json,.yaml,.yml"
              >
                <el-button size="small" type="primary">点击选择文件</el-button>
                <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
              </el-upload>
            </el-form-item>

            <!-- 内容输入 -->
            <el-form-item v-if="importMethod === 'content'" label="规范内容">
              <el-input
                v-model="formData.specContent"
                type="textarea"
                :rows="12"
                placeholder="请粘贴 OpenAPI 规范内容（JSON 或 YAML 格式）"
              />
            </el-form-item>

            <!-- 分类选择 -->
            <el-form-item label="工具分类">
              <el-select v-model="formData.category" placeholder="选择分类">
                <el-option label="外部API工具" value="external" />
                <el-option label="表单工具" value="form" />
                <el-option label="知识库工具" value="kb" />
                <el-option label="通用工具" value="general" />
              </el-select>
            </el-form-item>

            <!-- 是否启用 -->
            <el-form-item label="导入后启用">
              <el-switch v-model="formData.isEnabled" />
            </el-form-item>
          </el-form>

          <div class="action-buttons">
            <el-button
              type="primary"
              @click="parseSpec"
              :loading="isParsing"
              :disabled="!canParse"
            >
              解析规范
            </el-button>
          </div>
        </div>

        <!-- 步骤2: 预览和确认 -->
        <div v-if="currentStep === 1" class="step-panel">
          <div class="preview-header">
            <h4>解析结果预览</h4>
            <p class="hint">共解析出 {{ parsedTools.length }} 个工具</p>
          </div>

          <div class="tools-preview">
            <el-table :data="parsedTools" stripe style="width: 100%">
              <el-table-column prop="tool_name" label="工具名称" width="200" />
              <el-table-column prop="description" label="描述" show-overflow-tooltip />
              <el-table-column label="方法" width="80">
                <template #default="{ row }">
                  <el-tag :type="getMethodTagType(row.config?.method)">
                    {{ row.config?.method }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="路径" width="300" show-overflow-tooltip>
                <template #default="{ row }">{{ row.config?.url }}</template>
              </el-table-column>
              <el-table-column label="参数数量" width="100">
                <template #default="{ row }">
                  {{ Object.keys(row.input_schema?.properties || {}).length }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-checkbox
                    v-model="selectedTools[row.tool_name]"
                    :checked="selectedTools[row.tool_name] !== false"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="preview-actions">
            <el-button @click="currentStep = 0">上一步</el-button>
            <el-button
              type="primary"
              @click="doImport"
              :loading="isImporting"
            >
              确认导入
            </el-button>
          </div>
        </div>
      </div>

      <!-- 导入结果 -->
      <div v-if="importResult" class="result-panel">
        <div :class="['result-header', importResult.success ? 'success' : 'error']">
          <svg v-if="importResult.success" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <span>{{ importResult.success ? '导入成功' : '导入失败' }}</span>
        </div>

        <div class="result-content" v-if="importResult.success">
          <p>{{ importResult.message }}</p>
          
          <div v-if="importResult.created && importResult.created.length > 0" class="result-section">
            <h5>成功创建的工具：</h5>
            <ul>
              <li v-for="tool in importResult.created" :key="tool.tool_name">{{ tool.tool_name }}</li>
            </ul>
          </div>

          <div v-if="importResult.skipped && importResult.skipped.length > 0" class="result-section">
            <h5>跳过的工具（已存在）：</h5>
            <ul>
              <li v-for="item in importResult.skipped" :key="item.tool_name">{{ item.tool_name }}</li>
            </ul>
          </div>

          <div v-if="importResult.failed && importResult.failed.length > 0" class="result-section">
            <h5>创建失败的工具：</h5>
            <ul>
              <li v-for="item in importResult.failed" :key="item.tool_name">
                {{ item.tool_name }} - {{ item.reason }}
              </li>
            </ul>
          </div>
        </div>

        <div v-else class="result-content">
          <p>{{ importResult.error }}</p>
        </div>

        <div class="result-actions">
          <el-button type="primary" @click="reset">继续导入</el-button>
          <el-button @click="$emit('close')">关闭</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['close', 'import-success'])

const currentStep = ref(0)
const isParsing = ref(false)
const isImporting = ref(false)
const importResult = ref(null)
const importMethod = ref('content')
const selectedFile = ref(null)

const formData = reactive({
  specContent: '',
  category: 'external',
  isEnabled: true
})

const parsedTools = ref([])
const selectedTools = ref({})

const canParse = computed(() => {
  if (importMethod.value === 'content') {
    return formData.specContent.trim().length > 0
  }
  return selectedFile.value !== null
})

watch(importMethod, () => {
  selectedFile.value = null
})

const handleFileChange = (file) => {
  selectedFile.value = file
  
  const reader = new FileReader()
  reader.onload = (e) => {
    formData.specContent = e.target.result
  }
  reader.readAsText(file.raw)
}

const parseSpec = async () => {
  if (!canParse.value) {
    ElMessage.warning('请提供 OpenAPI 规范内容')
    return
  }

  isParsing.value = true
  
  try {
    const res = await mcpApi.parseOpenAPISpec(formData.specContent)
    
    if (res.success) {
      parsedTools.value = res.tools
      selectedTools.value = {}
      res.tools.forEach(tool => {
        selectedTools.value[tool.tool_name] = true
      })
      currentStep.value = 1
    } else {
      ElMessage.error(res.error || '解析失败')
    }
  } catch (e) {
    ElMessage.error('解析失败: ' + e.message)
  } finally {
    isParsing.value = false
  }
}

const getMethodTagType = (method) => {
  const types = {
    'GET': 'success',
    'POST': 'primary',
    'PUT': 'warning',
    'DELETE': 'danger',
    'PATCH': 'info'
  }
  return types[method] || 'default'
}

const doImport = async () => {
  const toolsToImport = parsedTools.value.filter(tool => selectedTools.value[tool.tool_name])
  
  if (toolsToImport.length === 0) {
    ElMessage.warning('请至少选择一个工具')
    return
  }

  isImporting.value = true
  
  try {
    const res = await mcpApi.importExternalTools(formData.specContent, {
      category: formData.category,
      isEnabled: formData.isEnabled
    })
    
    importResult.value = res
    
    if (res.success) {
      emit('import-success')
    }
  } catch (e) {
    importResult.value = {
      success: false,
      error: '导入失败: ' + e.message
    }
  } finally {
    isImporting.value = false
  }
}

const reset = () => {
  importResult.value = null
  currentStep.value = 0
  parsedTools.value = []
  selectedTools.value = {}
  formData.specContent = ''
  selectedFile.value = null
}
</script>

<style scoped>
.openapi-importer {
  width: 100%;
  max-height: calc(100vh - 200px);
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f5f7fa;
  color: #606266;
}

.modal-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.step-container {
  transition: all 0.3s;
}

.step-panel {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 规范说明样式 */
.spec-guide {
  background: linear-gradient(135deg, #f0f5ff 0%, #faf5ff 100%);
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  margin-bottom: 20px;
  overflow: hidden;
}

.guide-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: white;
  font-weight: 600;
  font-size: 14px;
}

.guide-content {
  padding: 16px;
}

.guide-section {
  margin-bottom: 16px;
}

.guide-section:last-child {
  margin-bottom: 0;
}

.guide-section h4 {
  margin: 0 0 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 6px;
}

.guide-section h4::before {
  content: '';
  width: 4px;
  height: 14px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 2px;
}

.guide-section ul {
  margin: 0;
  padding-left: 20px;
}

.guide-section li {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.8;
  margin-bottom: 4px;
}

.guide-section li:last-child {
  margin-bottom: 0;
}

.guide-section code {
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  color: #374151;
}

.code-example {
  background: #1f2937;
  color: #e5e7eb;
  padding: 12px 16px;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
}

.file-name {
  margin-left: 12px;
  color: #606266;
  font-size: 13px;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.preview-header {
  margin-bottom: 16px;
}

.preview-header h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 600;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.tools-preview {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.preview-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.result-panel {
  animation: fadeIn 0.3s ease;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-weight: 600;
  font-size: 16px;
}

.result-header.success {
  background: #f0fdf4;
  color: #16a34a;
}

.result-header.error {
  background: #fef2f2;
  color: #dc2626;
}

.result-content {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.result-content p {
  margin: 0 0 12px 0;
}

.result-section {
  margin-bottom: 12px;
}

.result-section:last-child {
  margin-bottom: 0;
}

.result-section h5 {
  margin: 0 0 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.result-section ul {
  margin: 0;
  padding-left: 20px;
}

.result-section li {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.result-section li:last-child {
  margin-bottom: 0;
}

.result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

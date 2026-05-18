<template>
  <div class="tool-tester">
    <div class="tester-layout">
      <!-- 左侧：工具选择和参数配置 -->
      <div class="config-panel">
        <h3>选择工具</h3>
        <el-select 
          v-model="selectedToolName" 
          placeholder="选择要测试的工具" 
          style="width: 100%"
          filterable
        >
          <el-option 
            v-for="tool in tools" 
            :key="tool.name" 
            :label="`${tool.name} - ${tool.description}`"
            :value="tool.name"
          />
        </el-select>

        <div v-if="currentTool" class="tool-info">
          <h4>工具信息</h4>
          <p><strong>描述:</strong> {{ currentTool.description }}</p>
          <p><strong>分类:</strong> {{ getCategoryName(currentTool.metadata?.category) }}</p>
          
          <h4>参数 Schema</h4>
          <pre class="schema-preview">{{ JSON.stringify(currentTool.inputSchema, null, 2) }}</pre>
        </div>

        <h4>输入参数 (JSON)</h4>
        <el-input
          v-model="argumentsJson"
          type="textarea"
          :rows="10"
          placeholder='{"param1": "value1", "param2": 123}'
        />

        <el-button 
          type="primary" 
          @click="runTest" 
          :loading="testing" 
          style="margin-top: 16px; width: 100%"
        >
          执行测试
        </el-button>
      </div>

      <!-- 右侧：执行结果 -->
      <div class="result-panel">
        <h3>执行结果</h3>
        
        <div v-if="testResult" class="result-content">
          <div class="result-meta">
            <el-tag :type="testResult.success ? 'success' : 'danger'">
              {{ testResult.success ? '成功' : '失败' }}
            </el-tag>
            <span class="execution-time">耗时: {{ testResult.execution_time_ms }} ms</span>
          </div>

          <div v-if="testResult.error" class="error-message">
            <strong>错误信息:</strong>
            <pre>{{ testResult.error }}</pre>
          </div>

          <div class="result-data">
            <strong>返回结果:</strong>
            <pre>{{ formatResult(testResult.result || testResult) }}</pre>
          </div>
        </div>

        <div v-else class="empty-result">
          <p>👈 选择工具并配置参数后，点击"执行测试"</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage } from 'element-plus'

const props = defineProps({
  tools: {
    type: Array,
    default: () => []
  },
  selectedTool: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['test-complete'])

const selectedToolName = ref('')
const argumentsJson = ref('{}')
const testing = ref(false)
const testResult = ref(null)

const currentTool = computed(() => {
  return props.tools.find(t => t.name === selectedToolName.value)
})

watch(() => props.selectedTool, (tool) => {
  if (tool) {
    selectedToolName.value = tool.name
    // 根据 schema 生成默认参数
    argumentsJson.value = generateDefaultParams(tool.inputSchema)
  }
}, { immediate: true })

const runTest = async () => {
  if (!selectedToolName.value) {
    ElMessage.warning('请选择工具')
    return
  }

  let args
  try {
    args = JSON.parse(argumentsJson.value)
  } catch (e) {
    ElMessage.error('参数 JSON 格式错误')
    return
  }

  testing.value = true
  testResult.value = null

  try {
    const res = await mcpApi.testTool(selectedToolName.value, args)
    testResult.value = res
    
    if (res.success) {
      ElMessage.success('测试成功')
    } else {
      ElMessage.error(`测试失败: ${res.error}`)
    }
    
    emit('test-complete')
  } catch (e) {
    ElMessage.error(`请求失败: ${e.message}`)
  } finally {
    testing.value = false
  }
}

const generateDefaultParams = (schema) => {
  if (!schema || !schema.properties) return '{}'
  
  const params = {}
  for (const [key, prop] of Object.entries(schema.properties)) {
    if (prop.type === 'string') params[key] = ''
    else if (prop.type === 'number' || prop.type === 'integer') params[key] = 0
    else if (prop.type === 'boolean') params[key] = false
    else if (prop.type === 'array') params[key] = []
    else if (prop.type === 'object') params[key] = {}
  }
  
  return JSON.stringify(params, null, 2)
}

const formatResult = (data) => {
  try {
    return JSON.stringify(data, null, 2)
  } catch (e) {
    return String(data)
  }
}

const getCategoryName = (category) => {
  const categoryNames = {
    form: '表单工具',
    kb: '知识库工具',
    llm: 'LLM 工具',
    system: '系统工具',
    tariff: '资费工具',
    general: '通用工具'
  }
  return categoryNames[category] || category
}
</script>

<style scoped>
.tool-tester {
  width: 100%;
  height: 100%;
}

.tester-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: 100%;
  overflow-y: auto;
}

.config-panel, .result-panel {
  background: #f9fafb;
  padding: 16px;
  border-radius: 8px;
  max-height: calc(100vh - 250px);
  overflow-y: auto;
}

.config-panel h3, .result-panel h3 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 15px;
  color: #303133;
}

.config-panel h4 {
  margin-top: 16px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}

.tool-info {
  margin-top: 12px;
  padding: 10px;
  background: white;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.tool-info p {
  margin: 6px 0;
  font-size: 12px;
  color: #606266;
}

.schema-preview {
  background: #282c34;
  color: #abb2bf;
  padding: 10px;
  border-radius: 6px;
  font-size: 11px;
  overflow-x: auto;
  max-height: 180px;
  overflow-y: auto;
}

.result-content {
  margin-top: 12px;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.execution-time {
  font-size: 12px;
  color: #909399;
}

.error-message {
  margin-bottom: 12px;
  padding: 10px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
}

.error-message strong {
  color: #f56c6c;
  font-size: 12px;
}

.error-message pre {
  margin: 6px 0 0 0;
  color: #f56c6c;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-data strong {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: #606266;
}

.result-data pre {
  background: white;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  font-size: 11px;
  overflow-x: auto;
  max-height: 350px;
  overflow-y: auto;
}

.empty-result {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 250px;
  color: #909399;
  font-size: 13px;
}
</style>

<template>
  <div class="external-tool-create">
    <div class="page-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <h2>{{ isEdit ? '编辑外部工具' : '新增外部工具' }}</h2>
    </div>

    <div class="wizard-container">
      <div class="steps-indicator">
        <div 
          v-for="(step, index) in steps" 
          :key="step.id"
          class="step-item"
          :class="{ 
            active: currentStep === index, 
            completed: currentStep > index,
            disabled: currentStep < index
          }"
        >
          <div class="step-number">
            <svg v-if="currentStep > index" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            <span v-else>{{ index + 1 }}</span>
          </div>
          <span class="step-label">{{ step.label }}</span>
        </div>
      </div>

      <div class="wizard-content">
        <div v-if="currentStep === 0" class="step-panel">
          <el-form :model="formData" label-width="140px" class="form-container">
            <el-form-item label="工具代码" required>
              <el-input 
                v-model="formData.tool_code" 
                placeholder="请输入工具代码"
              />
            </el-form-item>
            
            <el-form-item label="工具名称" required>
              <el-input 
                v-model="formData.tool_name" 
                placeholder="请输入工具名称"
              />
            </el-form-item>
            
            <el-form-item label="工具描述">
              <el-input 
                v-model="formData.description" 
                type="textarea" 
                :rows="2"
                placeholder="请输入工具描述"
              />
            </el-form-item>
            
            <div class="form-row">
              <el-form-item label="工具类型" required>
                <el-radio-group v-model="formData.tool_type">
                  <el-radio value="url">url</el-radio>
                </el-radio-group>
              </el-form-item>
              
              <el-form-item label="接口协议" required>
                <el-radio-group v-model="formData.protocol">
                  <el-radio value="http">http</el-radio>
                </el-radio-group>
              </el-form-item>
            </div>
            
            <el-form-item label="请求方式" required>
              <el-radio-group v-model="formData.request_method">
                <el-radio value="post">post</el-radio>
                <el-radio value="get">get</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="接口地址" required>
              <el-input 
                v-model="formData.url" 
                placeholder="请输入接口地址"
              />
            </el-form-item>
            
            <el-form-item label="鉴权信息">
              <el-input 
                v-model="formData.auth_info" 
                type="textarea" 
                :rows="2"
                placeholder="请输入鉴权信息"
              />
            </el-form-item>
            
            <el-form-item label="鉴权类型">
              <el-radio-group v-model="formData.auth_type">
                <el-radio value="none">无鉴权</el-radio>
                <el-radio value="static">静态鉴权</el-radio>
                <el-radio value="dynamic">动态鉴权</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="是否归纳总结" required>
              <el-radio-group v-model="formData.need_summary">
                <el-radio value="true">是</el-radio>
                <el-radio value="false">否</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="工具提示词">
              <el-input 
                v-model="formData.prompt" 
                type="textarea" 
                :rows="3"
                placeholder="请输入工具提示词，可以使用{question}方式引用用户输入问题，{tool_content}引用工具返回结果"
              />
            </el-form-item>
          </el-form>
        </div>

        <div v-if="currentStep === 1" class="step-panel">
          <div class="param-header">
            <span class="param-label">新增参数</span>
          </div>
          
          <div class="param-table-wrapper">
            <table class="param-table">
              <thead>
                <tr>
                  <th><span class="required">*</span>参数英文名称</th>
                  <th><span class="required">*</span>参数描述</th>
                  <th>数据类型</th>
                  <th>是否必填</th>
                  <th>枚举值</th>
                  <th>默认值</th>
                  <th>操作</th>
                  <th>新增子项</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="param in inputParams" :key="param.id">
                  <ParamRow
                    :param="param"
                    :level="0"
                    @add-child="addInputChild"
                    @remove="removeInputRow"
                    @type-change="onInputTypeChange"
                  />
                </template>
              </tbody>
            </table>
          </div>
          
          <button type="button" class="btn-add-root-param" @click="addRootInputParam()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            新增根参数
          </button>
        </div>

        <div v-if="currentStep === 2" class="step-panel">
          <div class="param-header">
            <span class="param-label">新增子项</span>
          </div>
          
          <div class="param-table-wrapper">
            <table class="param-table">
              <thead>
                <tr>
                  <th><span class="required">*</span>参数英文名称</th>
                  <th><span class="required">*</span>参数描述</th>
                  <th>数据类型</th>
                  <th>是否必填</th>
                  <th>操作</th>
                  <th>新增子项</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="param in outputParams" :key="param.id">
                  <ParamRow
                    :param="param"
                    :level="0"
                    :is-output="true"
                    @add-child="addOutputChild"
                    @remove="removeOutputRow"
                    @type-change="onOutputTypeChange"
                  />
                </template>
              </tbody>
            </table>
          </div>
          
          <button type="button" class="btn-add-root-param" @click="addRootOutputParam()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            新增根参数
          </button>
        </div>
      </div>

      <div class="wizard-footer">
        <button 
          v-if="currentStep > 0" 
          class="btn btn-secondary" 
          @click="prevStep"
        >
          上一步
        </button>
        
        <div v-if="currentStep < steps.length - 1">
          <button class="btn btn-primary" @click="nextStep">
            保存并下一步
          </button>
        </div>
        
        <button 
          v-else 
          class="btn btn-primary" 
          @click="saveTool"
          :loading="saving"
        >
          保存
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import * as mcpApi from '@/services/mcpManagementApi'
import { ElMessage } from 'element-plus'
import ParamRow from './ParamRow.vue'

const props = defineProps({
  editTool: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['go-back', 'created', 'updated'])

const steps = [
  { id: 'basic', label: '填写基本信息' },
  { id: 'input', label: '配置输入参数' },
  { id: 'output', label: '配置输出参数' }
]

const currentStep = ref(0)
const saving = ref(false)

const isEdit = computed(() => !!props.editTool)

const formData = reactive({
  tool_code: '',
  tool_name: '',
  description: '',
  tool_type: 'url',
  protocol: 'http',
  request_method: 'post',
  url: '',
  auth_info: '',
  auth_type: 'none',
  need_summary: 'false',
  prompt: ''
})

const loadEditData = async () => {
  if (!props.editTool) return
  
  try {
    const res = await mcpApi.getExternalTool(props.editTool.tool_name)
    if (res.success && res.tool) {
      const tool = res.tool
      formData.tool_code = tool.tool_code || ''
      formData.tool_name = tool.tool_name || ''
      formData.description = tool.description || ''
      formData.tool_type = tool.tool_type || 'url'
      formData.protocol = tool.protocol || 'http'
      formData.request_method = tool.request_method || 'post'
      formData.url = tool.url || ''
      formData.auth_info = tool.auth_info || ''
      formData.auth_type = tool.auth_type || 'none'
      formData.need_summary = String(tool.need_summary || false)
      formData.prompt = tool.prompt || ''
      
      inputParams.value = schemaToParams(tool.input_schema || {})
      outputParams.value = schemaToParams(tool.output_schema || {})
    }
  } catch (e) {
    ElMessage.error('加载工具信息失败: ' + e.message)
  }
}

const schemaToParams = (schema) => {
  const params = []
  
  const buildParams = (props, required = [], parentId = null, isRoot = true) => {
    const result = []
    
    for (const [name, prop] of Object.entries(props || {})) {
      const param = {
        id: isRoot ? (name === 'ROOT' ? 'input-root' : generateId()) : generateId(),
        name: name,
        description: prop.description || '',
        enum_values: prop.enum ? prop.enum.join(', ') : '',
        data_type: prop.type || 'string',
        required: required.includes(name),
        prompt: prop.prompt || false,
        default_value: prop.default || '',
        parentId: parentId,
        children: [],
        expanded: false
      }
      
      if (prop.type === 'object' && prop.properties) {
        param.children = buildParams(prop.properties, prop.required || [], param.id, false)
        param.expanded = param.children.length > 0
      } else if (prop.type === 'array' && prop.items && prop.items.properties) {
        param.children = buildParams(prop.items.properties, prop.items.required || [], param.id, false)
        param.expanded = param.children.length > 0
      }
      
      result.push(param)
    }
    
    return result
  }
  
  if (schema.properties) {
    params.push(...buildParams(schema.properties, schema.required || [], null, true))
  }
  
  if (params.length === 0) {
    params.push({
      id: 'input-root',
      name: 'ROOT',
      description: 'ROOT',
      data_type: 'object',
      required: false,
      prompt: false,
      parentId: null,
      children: [],
      expanded: true
    })
  }
  
  return params
}

watch(() => props.editTool, () => {
  loadEditData()
}, { immediate: true })

onMounted(() => {
  if (props.editTool) {
    loadEditData()
  }
})

const inputParams = ref([
  { 
    id: 'input-root',
    name: 'ROOT', 
    description: 'ROOT', 
    enum_values: '', 
    data_type: 'object', 
    required: false, 
    prompt: false,
    default_value: '',
    parentId: null,
    children: [],
    expanded: true
  }
])

const outputParams = ref([
  { 
    id: 'output-root',
    name: 'ROOT', 
    description: 'ROOT', 
    data_type: 'object', 
    required: false,
    prompt: false,
    parentId: null,
    children: [],
    expanded: true
  }
])

const generateId = () => {
  return 'param-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
}

const addRootInputParam = () => {
  const newParam = {
    id: generateId(),
    name: '',
    description: '',
    enum_values: '',
    data_type: 'string',
    required: false,
    prompt: false,
    default_value: '',
    parentId: null,
    children: [],
    expanded: false
  }
  inputParams.value = [...inputParams.value, newParam]
}

const addInputChild = (parent) => {
  if (!parent.children) parent.children = []
  parent.children.push({
    id: generateId(),
    name: '',
    description: '',
    enum_values: '',
    data_type: 'string',
    required: false,
    prompt: false,
    default_value: '',
    parentId: parent.id,
    children: [],
    expanded: false
  })
  parent.expanded = true
}

const removeInputRow = (param) => {
  const removeFromArray = (arr, targetId) => {
    const index = arr.findIndex(item => item.id === targetId)
    if (index > -1) {
      arr.splice(index, 1)
      return true
    }
    for (const item of arr) {
      if (item.children && removeFromArray(item.children, targetId)) {
        return true
      }
    }
    return false
  }
  removeFromArray(inputParams.value, param.id)
}

const onInputTypeChange = (param) => {
  if (param.data_type !== 'object' && param.data_type !== 'array') {
    param.children = []
  }
}

const addRootOutputParam = () => {
  const newParam = {
    id: generateId(),
    name: '',
    description: '',
    data_type: 'string',
    required: false,
    prompt: false,
    parentId: null,
    children: [],
    expanded: false
  }
  outputParams.value = [...outputParams.value, newParam]
}

const addOutputChild = (parent) => {
  if (!parent.children) parent.children = []
  parent.children.push({
    id: generateId(),
    name: '',
    description: '',
    data_type: 'string',
    required: false,
    prompt: false,
    parentId: parent.id,
    children: [],
    expanded: false
  })
  parent.expanded = true
}

const removeOutputRow = (param) => {
  const removeFromArray = (arr, targetId) => {
    const index = arr.findIndex(item => item.id === targetId)
    if (index > -1) {
      arr.splice(index, 1)
      return true
    }
    for (const item of arr) {
      if (item.children && removeFromArray(item.children, targetId)) {
        return true
      }
    }
    return false
  }
  removeFromArray(outputParams.value, param.id)
}

const onOutputTypeChange = (param) => {
  if (param.data_type !== 'object' && param.data_type !== 'array') {
    param.children = []
  }
}

const nextStep = () => {
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const buildSchema = (params) => {
  const properties = {}
  const required = []
  
  const buildProperty = (param) => {
    const prop = {
      type: param.data_type,
      description: param.description || '',
      prompt: param.prompt || false
    }
    
    if (param.enum_values) {
      prop.enum = param.enum_values.split(',').map(v => v.trim()).filter(v => v)
    }
    
    if (param.data_type === 'object' && param.children && param.children.length > 0) {
      const childProps = {}
      const childRequired = []
      
      param.children.forEach(child => {
        childProps[child.name] = buildProperty(child)
        if (child.required) {
          childRequired.push(child.name)
        }
      })
      
      prop.properties = childProps
      if (childRequired.length > 0) {
        prop.required = childRequired
      }
    }
    
    if (param.data_type === 'array') {
      if (param.children && param.children.length > 0) {
        prop.items = buildProperty(param.children[0])
      } else {
        prop.items = { type: 'string' }
      }
    }
    
    return prop
  }
  
  params.forEach(param => {
    if (param.name) {
      properties[param.name] = buildProperty(param)
      if (param.required) {
        required.push(param.name)
      }
    }
  })
  
  return {
    type: 'object',
    properties,
    required
  }
}

const saveTool = async () => {
  if (!formData.tool_code || !formData.tool_name || !formData.url) {
    ElMessage.warning('请填写必填字段')
    return
  }
  
  saving.value = true
  
  try {
    const toolData = {
      tool_code: formData.tool_code,
      tool_name: formData.tool_name,
      description: formData.description,
      tool_type: formData.tool_type,
      protocol: formData.protocol,
      request_method: formData.request_method,
      url: formData.url,
      auth_info: formData.auth_info,
      auth_type: formData.auth_type,
      need_summary: formData.need_summary === 'true',
      prompt: formData.prompt,
      input_schema: buildSchema(inputParams.value),
      output_schema: buildSchema(outputParams.value)
    }
    
    if (isEdit.value) {
      await mcpApi.updateExternalTool(props.editTool.tool_name, toolData)
      ElMessage.success('更新成功')
      emit('updated')
    } else {
      await mcpApi.createExternalTool(toolData)
      ElMessage.success('创建成功')
      emit('created')
    }
    
    emit('go-back')
    
  } catch (e) {
    ElMessage.error(isEdit.value ? '更新失败: ' + e.message : '创建失败: ' + e.message)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.external-tool-create {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.required {
  color: #ef4444;
  margin-right: 4px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #606266;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.wizard-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 160px);
}

.steps-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 48px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #f0f5ff 0%, #faf5ff 100%);
  border-radius: 12px 12px 0 0;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-weight: 600;
  font-size: 14px;
  transition: all 0.25s ease;
}

.step-item.active .step-number {
  background: linear-gradient(135deg, #5b7cfa, #9333ea);
  color: white;
  transform: scale(1.08);
  box-shadow: 0 3px 8px rgba(91, 124, 250, 0.25);
}

.step-item.completed .step-number {
  background: #22c55e;
  color: white;
}

.step-label {
  font-size: 13px;
  color: #909399;
  transition: color 0.25s ease;
}

.step-item.active .step-label,
.step-item.completed .step-label {
  color: #303133;
  font-weight: 500;
}

.wizard-content {
  padding: 20px;
  flex: 1;
  overflow-y: auto;
}

.step-panel {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.form-container {
  max-width: 650px;
}

.form-row {
  display: flex;
  gap: 20px;
}

.form-row .el-form-item {
  flex: 1;
}

.param-header {
  margin-bottom: 12px;
}

.param-label {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.param-table-wrapper {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: auto;
  max-height: 500px;
  background: white;
}

.param-table-wrapper + .custom-btn {
  margin-top: 12px;
  position: sticky;
  bottom: 16px;
  display: block;
  width: fit-content;
  margin-left: 0;
}

.param-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1000px;
}

.param-table th {
  background: #f8f9fa;
  text-align: left;
  padding: 10px 12px;
  font-weight: 500;
  color: #606266;
  font-size: 13px;
  border-bottom: 1px solid #ebeef5;
  white-space: nowrap;
  vertical-align: middle;
}

.param-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  vertical-align: middle;
  background: white;
  text-align: left;
}

.param-table td :deep(.el-input),
.param-table td :deep(.el-select) {
  width: 100%;
  margin: 0;
}

.param-table td :deep(.el-input__wrapper),
.param-table td :deep(.el-select__wrapper) {
  margin: 0;
  padding: 0;
  justify-content: flex-start;
}

.param-table td :deep(.el-input__inner),
.param-table td :deep(.el-select__placeholder) {
  text-align: left;
}

.param-table td :deep(.el-switch) {
  margin: 0;
}

.param-table td :deep(.el-button) {
  margin: 0;
}

.param-table tr:hover td {
  background-color: #fafbfc;
}

.add-btn {
  margin-top: 20px;
}

.custom-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.custom-btn-primary {
  background: linear-gradient(135deg, #5b7cfa, #9333ea);
  color: white;
}

.custom-btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(91, 124, 250, 0.35);
}

.custom-btn-primary:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 3px 10px rgba(91, 124, 250, 0.25);
}

.step-panel {
  animation: fadeIn 0.3s ease;
  padding-bottom: 70px;
}

.btn-add-root-param {
  position: relative;
  z-index: 10;
  margin-top: 14px;
  background: linear-gradient(135deg, #5b7cfa, #9333ea);
  color: white;
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}

.btn-add-root-param:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(91, 124, 250, 0.3);
}

.btn-add-root-param:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(91, 124, 250, 0.2);
}

.wizard-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fafbfc;
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.btn {
  padding: 9px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: linear-gradient(135deg, #5b7cfa, #9333ea);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(91, 124, 250, 0.3);
}

.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-secondary {
  background: white;
  color: #606266;
  border: 1px solid #e4e7ed;
}

.btn-secondary:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

:deep(.el-radio-group) {
  display: flex;
  gap: 28px;
}

:deep(.el-switch) {
  margin: 0;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  font-size: 14px;
  color: #606266;
}

:deep(.el-form-item__label.is-required::before) {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

:deep(.el-input),
:deep(.el-select) {
  width: 100%;
}

:deep(.el-textarea) {
  width: 100%;
}

:deep(.el-textarea__inner) {
  min-height: 80px;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  font-size: 14px;
}
</style>
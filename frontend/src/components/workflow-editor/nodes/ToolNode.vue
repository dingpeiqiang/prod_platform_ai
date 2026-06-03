<template>
  <div class="node tool-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🔌</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>

    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ toolDisplayName }}</span>
      <span class="compact-hint">双击配置</span>
    </div>

    <div v-if="!compact || configMode" class="node-body">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-text">加载工具中...</span>
      </div>

      <!-- 工具选择器 -->
      <div v-else class="tool-selector">
        <!-- 分类选择 -->
        <select v-model="localCategory" @change="onCategoryChange" class="node-select category-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ getCategoryDisplayName(cat) }}</option>
        </select>

        <!-- 工具选择 -->
        <select v-model="localToolName" @change="onToolChange" class="node-select">
          <option value="">选择 MCP 工具</option>
          <optgroup v-for="(tools, cat) in groupedTools" :key="cat" :label="getCategoryDisplayName(cat)">
            <option v-for="tool in tools" :key="tool.name" :value="tool.name">
              {{ tool.name }}
            </option>
          </optgroup>
        </select>
      </div>

      <!-- 工具描述 -->
      <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>

      <!-- 高级配置 -->
      <div v-if="localToolName" class="advanced-panel">
        <!-- 输入参数配置 -->
        <div class="config-section collapsible-section">
          <div class="section-header">
            <button @click="toggleSection('inputs')" class="section-toggle-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
              <span>输入参数</span>
            </button>
            <div class="header-actions">
              <button class="help-btn" title="配置输入参数，根据工具的入参自动生成">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
            </div>
          </div>
          <div v-if="expandedSections.inputs" class="section-content">
            <div class="params-container">
              <!-- 参数表头 -->
              <div class="param-header-row">
                <span class="param-col param-code">参数编码</span>
                <span class="param-col param-name">参数名称</span>
                <span class="param-col param-type">参数类型</span>
                <span class="param-col param-required">是否必填</span>
                <span class="param-col param-source">取值来源</span>
                <span class="param-col param-content">取值内容</span>
              </div>
              <!-- 参数行 -->
              <div
                v-for="(param, index) in localParams"
                :key="index"
                class="param-row"
              >
                <span class="param-col param-code">{{ param.name }}</span>
                <span class="param-col param-name">{{ param.description || param.title || param.name }}</span>
                <span class="param-col param-type">{{ getTypeDisplayName(param.schemaType) }}</span>
                <span class="param-col param-required">{{ param.required ? '是' : '否' }}</span>
                <select v-model="param.sourceType" @change="handleSourceTypeChange(index)" class="param-col param-source-select">
                  <option value="input">输入</option>
                  <option value="ref">引用</option>
                </select>
                <div class="param-col param-content">
                  <input
                    v-if="param.sourceType !== 'ref'"
                    v-model="param.value"
                    @input="emitUpdate"
                    :placeholder="getParamPlaceholder(param.schemaType)"
                    class="param-value-input"
                  />
                  <VariableCascader
                    v-else
                    v-model="param.refValue"
                    :available-variables="availableVariables"
                    placeholder="请选择变量"
                    class="param-value-cascader"
                    @change="(val) => handleCascaderChange(index, val)"
                  />
                </div>
              </div>
              <div v-if="localParams.length === 0" class="no-params-hint">
                此工具无需参数
              </div>
            </div>
          </div>
        </div>

        <!-- 输出参数配置 -->
        <div class="config-section collapsible-section">
          <div class="section-header">
            <button @click="toggleSection('outputs')" class="section-toggle-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
              <span>输出参数</span>
            </button>
            <div class="header-actions">
              <button class="help-btn" title="配置输出参数，选择工具出参节点">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
              <button @click.stop="addOutputParam" class="add-param-btn" title="添加输出参数">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
            </div>
          </div>
          <div v-if="expandedSections.outputs" class="section-content">
            <div v-if="outputSchemaProperties && Object.keys(outputSchemaProperties).length > 0" class="output-schema-info">
              <div class="schema-label">工具输出字段：</div>
              <div class="schema-fields">
                <span
                  v-for="(prop, name) in outputSchemaProperties"
                  :key="name"
                  class="schema-field"
                  :class="{ selected: isOutputSelected(name) }"
                  @click="toggleOutputField(name)"
                >
                  {{ name }}
                  <span class="field-type">{{ prop.type || 'any' }}</span>
                </span>
              </div>
            </div>
            <div v-else class="no-output-hint">
              此工具未定义输出schema
            </div>
            
            <div class="output-mappings">
              <div class="section-subtitle">输出映射</div>
              <template v-for="(mapping, index) in localOutputMappings" :key="index">
                <div v-if="mapping" class="output-mapping-item">
                  <select v-model="mapping.source" @change="emitUpdate" class="output-source-select">
                    <option value="">选择源字段</option>
                    <option v-for="(prop, name) in outputSchemaProperties" :key="name" :value="name">
                      {{ name }}
                    </option>
                  </select>
                  <input v-model="mapping.target" @input="emitUpdate" placeholder="目标变量名" class="output-target-input"/>
                  <select v-model="mapping.type" @change="emitUpdate" class="output-type-select">
                    <option value="string">string</option>
                    <option value="number">number</option>
                    <option value="boolean">boolean</option>
                    <option value="object">object</option>
                    <option value="array">array</option>
                  </select>
                  <button @click="removeOutputMapping(index)" class="action-btn delete-btn" title="删除">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>

        <!-- 执行配置 -->
        <div class="config-section collapsible-section">
          <div class="section-header">
            <button @click="toggleSection('execution')" class="section-toggle-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.execution }">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
              <span>执行配置</span>
            </button>
          </div>
          <div v-if="expandedSections.execution" class="section-content">
            <div class="timeout-row">
              <label>超时时间</label>
              <input
                v-model.number="localTimeout"
                @input="emitUpdate"
                type="number"
                min="1"
                max="600"
                class="timeout-input"
              />
              <span class="timeout-unit">秒</span>
            </div>

            <label class="checkbox-label">
              <input v-model="localAsync" @change="emitUpdate" type="checkbox" />
              <span>异步执行</span>
            </label>

            <label class="checkbox-label">
              <input v-model="localSilent" @change="emitUpdate" type="checkbox" />
              <span>静默模式（不输出日志）</span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { nodeDisplayProps } from './nodeDisplayProps.js'
import { useNodeAnchorMode } from './useHandlePosition.js'
import VariableCascader from '../VariableCascader.vue'
import * as mcpApi from '@/services/mcpManagementApi'

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  availableVariables: {
    type: Array,
    default: () => []
  },
  ...nodeDisplayProps
})

const { targetPosition, sourcePosition } = useNodeAnchorMode(props)

const emit = defineEmits(['update'])

// MCP 工具数据
const mcpTools = ref([])
const mcpToolMap = ref({})
const loading = ref(false)
const showAdvanced = ref(false)

// 展开状态
const expandedSections = ref({
  inputs: true,
  outputs: true,
  execution: true
})

// 选择状态（对应后端 toolType 字段）
const localCategory = ref('')
const localToolName = ref(props.data.tool_type || props.data.tool_name || '')
const localParams = ref([])
const localOutputMappings = ref((props.data.outputMappings && Array.isArray(props.data.outputMappings)) ? props.data.outputMappings : [])
const localTimeout = ref(props.data.timeout || 60)
const localAsync = ref(props.data.isAsync || false)
const localSilent = ref(props.data.silent || false)

// 分类
const categories = computed(() => {
  const cats = [...new Set(mcpTools.value.map(t => t.metadata?.category || 'general'))]
  return cats.sort()
})

// 按分类分组
const groupedTools = computed(() => {
  const filtered = localCategory.value
    ? mcpTools.value.filter(t => (t.metadata?.category || 'general') === localCategory.value)
    : mcpTools.value

  const groups = {}
  for (const tool of filtered) {
    const cat = tool.metadata?.category || 'general'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(tool)
  }
  return groups
})

// 当前选中的工具定义
const selectedTool = computed(() => {
  return mcpToolMap.value[localToolName.value] || null
})

// 显示名称
const toolDisplayName = computed(() => {
  if (!localToolName.value) return '未选择工具'
  const tool = mcpToolMap.value[localToolName.value]
  return tool?.name || localToolName.value
})

// 输出schema属性
const outputSchemaProperties = computed(() => {
  if (!selectedTool.value) return {}
  const schema = selectedTool.value.output_schema || selectedTool.value.outputSchema || selectedTool.value.schema || {}
  const properties = schema.properties || {}
  return properties
})

// 加载 MCP 工具列表
const loadMCPTools = async () => {
  loading.value = true
  try {
    const res = await mcpApi.listTools()
    if (res.success) {
      mcpTools.value = res.tools || []
      // 构建 name -> tool 映射
      const map = {}
      for (const t of mcpTools.value) {
        map[t.name] = t
      }
      mcpToolMap.value = map

      // 如果已有选中工具且在列表中找到，同步参数
      if (localToolName.value && map[localToolName.value]) {
        syncParamsFromSchema(map[localToolName.value])
      }
    }
  } catch (e) {
    console.error('加载 MCP 工具失败:', e)
  } finally {
    loading.value = false
  }
}

// 分类显示名
const categoryNames = {
  form: '表单工具',
  kb: '知识库工具',
  llm: 'LLM 工具',
  system: '系统工具',
  tariff: '资费工具',
  workflow: '工作流工具',
  general: '通用工具',
  external: '外部工具'
}

const getCategoryDisplayName = (cat) => categoryNames[cat] || cat

// 分类切换时清空工具选择
const onCategoryChange = () => {
  localToolName.value = ''
  localParams.value = []
  emitUpdate()
}

// 工具切换时从 schema 生成参数行
const onToolChange = () => {
  const tool = mcpToolMap.value[localToolName.value]
  if (tool) {
    syncParamsFromSchema(tool)
  } else {
    localParams.value = []
  }
  emitUpdate()
}

// 根据 input_schema 同步参数行
const syncParamsFromSchema = (tool) => {
  const schema = tool.input_schema || tool.inputSchema || tool.schema || {}
  const properties = schema.properties || {}
  const requiredFields = schema.required || []

  // 保留已有值，只在必要时新增/删除行
  const existing = {}
  for (const p of localParams.value) {
    existing[p.name] = p
  }

  localParams.value = Object.entries(properties).map(([name, prop]) => {
    const schemaType = prop.type || 'string'
    const inferType = (t) => {
      if (t === 'number' || t === 'integer') return 'number'
      if (t === 'boolean') return 'boolean'
      if (t === 'array') return 'array'
      if (t === 'object') return 'object'
      return 'string'
    }

    const existingParam = existing[name]
    return {
      name,
      title: prop.title || '',
      description: prop.description || '',
      schemaType,
      type: existingParam?.type || inferType(schemaType),
      required: requiredFields.includes(name),
      sourceType: existingParam?.sourceType || 'constant',
      value: existingParam?.value || prop.default || '',
      refValue: existingParam?.refValue || ''
    }
  })
}

// 切换展开状态
const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section]
}

// 处理参数类型变化
const handleParamTypeChange = (index) => {
  const param = localParams.value[index]
  if (param.type === 'variable') {
    param.value = ''
  } else {
    param.refValue = ''
  }
  emitUpdate()
}

// 获取类型显示名称
const getTypeDisplayName = (type) => {
  const typeNames = {
    string: '字符串',
    number: '数字',
    integer: '整数',
    boolean: '布尔值',
    array: '数组',
    object: '对象',
    null: '空值',
    any: '任意'
  }
  return typeNames[type] || type
}

// 处理取值来源变化
const handleSourceTypeChange = (index) => {
  const param = localParams.value[index]
  if (param.sourceType === 'ref') {
    param.value = ''
  } else {
    param.refValue = ''
  }
  emitUpdate()
}

// 处理级联选择变化
const handleCascaderChange = (index, value) => {
  const param = localParams.value[index]
  if (param) {
    param.refValue = value || ''
    emitUpdate()
  }
}

// 添加输出参数
const addOutputParam = () => {
  localOutputMappings.value.push({ source: '', target: '', type: 'string' })
  emitUpdate()
}

// 删除输出映射
const removeOutputMapping = (index) => {
  localOutputMappings.value.splice(index, 1)
  emitUpdate()
}

// 检查输出字段是否被选中
const isOutputSelected = (name) => {
  return localOutputMappings.value.some(m => m.source === name)
}

// 切换输出字段选中状态
const toggleOutputField = (name) => {
  const index = localOutputMappings.value.findIndex(m => m.source === name)
  if (index >= 0) {
    localOutputMappings.value.splice(index, 1)
  } else {
    localOutputMappings.value.push({ source: name, target: name, type: 'string' })
  }
  emitUpdate()
}

const getParamPlaceholder = (type) => {
  switch (type) {
    case 'string': return '字符串值或 {{变量名}}'
    case 'number': return '数字'
    case 'boolean': return 'true/false'
    case 'array': return '[...]'
    case 'object': return '{...}'
    default: return '参数值'
  }
}

const emitUpdate = () => {
  // 转换为 params 对象（key-value 形式）供后端使用
  const params = {}
  for (const p of localParams.value) {
    if (p.name) {
      params[p.name] = p.type === 'variable' ? p.refValue : p.value
    }
  }

  // 过滤有效的输出映射
  const outputMappings = localOutputMappings.value.filter(m => m.source && m.target)

  emit('update', props.data.id, {
    tool_type: localToolName.value,
    tool_name: localToolName.value,
    params,
    outputMappings,
    timeout: localTimeout.value,
    isAsync: localAsync.value,
    silent: localSilent.value
  })
}

watch(() => props.data, (d) => {
  localToolName.value = d.tool_type || d.tool_name || ''
  localTimeout.value = d.timeout || 60
  localAsync.value = d.isAsync || false
  localSilent.value = d.silent || false
  localOutputMappings.value = (d.outputMappings && Array.isArray(d.outputMappings)) ? d.outputMappings : []

  // 同步参数
  if (localToolName.value && mcpToolMap.value[localToolName.value]) {
    const tool = mcpToolMap.value[localToolName.value]
    syncParamsFromSchema(tool)
    
    // 如果有已保存的参数值，覆盖默认值
    if (d.params && Object.keys(d.params).length > 0) {
      for (const param of localParams.value) {
        if (d.params[param.name] !== undefined) {
          param.value = d.params[param.name]
        }
      }
    }
  }
}, { deep: true })

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value
}

onMounted(() => {
  loadMCPTools()
})
</script>

<style scoped>
.tool-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 200px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.tool-node.selected {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.15);
}

.tool-node.is-compact {
  min-width: 180px;
}

.node-compact-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compact-summary {
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-hint {
  font-size: 10px;
  color: #94a3b8;
}

.tool-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: white;
  flex: 1;
}

.advanced-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.advanced-toggle:hover,
.advanced-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.node-body {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.loading-state {
  padding: 12px;
  text-align: center;
}

.loading-text {
  font-size: 12px;
  color: #94a3b8;
}

.tool-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-select {
  font-size: 11px;
}

.node-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  background: white;
}

.node-select:focus {
  outline: none;
  border-color: #8b5cf6;
}

.tool-desc {
  font-size: 11px;
  color: #64748b;
  padding: 6px 8px;
  background: #f8fafc;
  border-radius: 4px;
  line-height: 1.4;
  max-height: 60px;
  overflow-y: auto;
}

.advanced-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.params-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.param-header-row {
  display: flex;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 10px;
  color: #64748b;
}

.param-row {
  display: flex;
  align-items: center;
  border-top: 1px solid #f1f5f9;
}

.param-row:nth-child(odd):not(.param-header-row) {
  background: #fafafa;
}

.param-col {
  padding: 4px 6px;
  display: flex;
  align-items: center;
  min-height: 28px;
  flex-shrink: 0;
}

.param-code {
  width: 75px;
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-name {
  width: 90px;
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-type {
  width: 55px;
  font-size: 11px;
  color: #475569;
  text-align: center;
}

.param-required {
  width: 50px;
  font-size: 11px;
  color: #475569;
  text-align: center;
}

.param-source {
  width: 75px;
  font-size: 11px;
  color: #475569;
}

.param-source-select {
  width: 100%;
  min-width: 60px;
  max-width: 80px;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 10px;
  background: white;
  color: #475569;
  box-sizing: border-box;
}

.param-content {
  flex: 1;
  min-width: 100px;
}

.param-value-input {
  width: 100%;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 11px;
  background: white;
}

.param-value-cascader {
  width: 100%;
}

.no-params-hint {
  font-size: 11px;
  color: #94a3b8;
  padding: 4px 0;
  text-align: center;
}

.timeout-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.timeout-row label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.timeout-input {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.timeout-unit {
  font-size: 11px;
  color: #64748b;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
  margin-bottom: 4px;
}

.checkbox-label input {
  width: 14px;
  height: 14px;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #8b5cf6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #7c3aed !important;
}

/* 配置区域样式 */
.config-section {
  margin-bottom: 8px;
}

.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #f8fafc;
  cursor: pointer;
  transition: background 0.2s;
}

.section-header:hover {
  background: #f1f5f9;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
}

.section-toggle-btn svg {
  width: 14px;
  height: 14px;
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.help-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: help;
  border-radius: 50%;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #e2e8f0;
  color: #64748b;
}

.add-param-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: #8b5cf6;
  color: white;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #7c3aed;
  box-shadow: 0 2px 4px rgba(139, 92, 246, 0.3);
}

.section-content {
  padding: 10px;
  animation: slideDown 0.2s ease;
}

.section-subtitle {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
  margin-bottom: 6px;
}

/* 输出schema信息 */
.output-schema-info {
  margin-bottom: 10px;
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
}

.schema-label {
  font-size: 10px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.schema-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.schema-field {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.schema-field:hover {
  border-color: #8b5cf6;
}

.schema-field.selected {
  background: #ede9fe;
  border-color: #8b5cf6;
  color: #7c3aed;
}

.field-type {
  font-size: 10px;
  color: #94a3b8;
  font-style: italic;
}

.no-output-hint {
  font-size: 11px;
  color: #94a3b8;
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
  text-align: center;
}

/* 输出映射 */
.output-mappings {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
}

.output-mapping-item {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-bottom: 4px;
}

.output-source-select,
.output-target-input,
.output-type-select {
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.output-source-select {
  width: 100px;
}

.output-target-input {
  flex: 1;
  min-width: 80px;
}

.output-type-select {
  width: 70px;
}

.action-btn {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f1f5f9;
}

.action-btn.delete-btn:hover {
  background: #fee2e2;
  color: #dc2626;
}

.param-value-cascader {
  flex: 1;
  font-size: 11px;
}
</style>